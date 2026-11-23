package app.wiis.emulator.art

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.wiis.emulator.Game
import app.wiis.emulator.SystemDef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * SteamGridDB (steamgriddb.com): community art for games on every platform, including the ones
 * libretro doesn't cover. Needs your free API key (steamgriddb.com → Preferences → API). Finds the
 * game by its title, then takes the best-rated portrait cover (600×900, 342×482 or 660×930),
 * leaving out NSFW and joke art. The key is only ever sent to steamgriddb.com; the image itself
 * comes from SteamGridDB's image host without it.
 */
class SteamGridDb(
    private val key: () -> String?,
    private val get: (url: String, key: String?) -> ByteArray? = ::steamGridGet,
    private val shrink: (ByteArray) -> ByteArray = ::shrinkCover,
) : ArtProvider {
    override val id = "steamgriddb"
    override val label = "SteamGridDB"

    override suspend fun find(game: Game, system: SystemDef?): ByteArray? {
        val apiKey = key()?.takeIf { it.isNotBlank() } ?: return null
        val term = searchTerm(game.title.ifBlank { game.fileName.substringBeforeLast('.') })
        if (term.isEmpty()) return null
        val found = parseGames(get("$API/search/autocomplete/${encode(term)}", apiKey) ?: return null)
        val match = bestMatch(term, found) ?: return null
        val grids = parseImages(get("$API/grids/game/${match.id}?$GRID_QUERY", apiKey) ?: return null)
        val cover = grids.firstOrNull() ?: return null
        val bytes = get(cover.url, null) ?: return null
        return runCatching { shrink(bytes) }.getOrDefault(bytes)
    }

    /** Checks a key with one search; the message to show when it fails. */
    fun test(apiKey: String): Result<Int> = runCatching {
        parseGames(get("$API/search/autocomplete/${encode("Tetris")}", apiKey) ?: throw IOException("No answer")).size
    }

    data class SgdbGame(val id: Int, val name: String, val verified: Boolean)
    data class SgdbImage(val id: Int, val url: String, val score: Double, val width: Int, val height: Int)

    companion object {
        const val API = "https://www.steamgriddb.com/api/v2"

        /** Portrait covers, still images, nothing adult or joking; best-rated first is SteamGridDB's own order. */
        const val GRID_QUERY = "dimensions=600x900,342x482,660x930&types=static&nsfw=false&humor=false&epilepsy=false"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun encode(segment: String): String = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

        /** The title without region tags, version marks or a trailing ", The". */
        fun searchTerm(title: String): String {
            var t = title
                .replace(Regex("""\s*\([^)]*\)"""), "")
                .replace(Regex("""\s*\[[^]]*]"""), "")
                .replace('_', ' ')
                .trim()
            Regex("""^(.*),\s*(The|A|An)$""", RegexOption.IGNORE_CASE).matchEntire(t)?.let { m -> t = "${m.groupValues[2]} ${m.groupValues[1]}" }
            return t.replace(Regex("""\s+"""), " ").trim()
        }

        /** Letters and digits only, "&" as "and", lower case: how two names are compared. */
        fun normalize(name: String): String = name.lowercase()
            .replace("&", "and")
            .replace(Regex("""[^\p{L}\p{N}]+"""), "")
            .removePrefix("the")

        /**
         * The search result that is this game: the same name, a verified one first; else the first
         * result if its name starts with ours or ours with its (a subtitle on one side). Nothing
         * rather than another game's art.
         */
        fun bestMatch(term: String, games: List<SgdbGame>): SgdbGame? {
            val want = normalize(term)
            if (want.isEmpty()) return null
            games.filter { normalize(it.name) == want }.let { same -> (same.firstOrNull { it.verified } ?: same.firstOrNull())?.let { return it } }
            val first = games.firstOrNull() ?: return null
            val got = normalize(first.name)
            return first.takeIf { got.length >= 3 && (got.startsWith(want) || want.startsWith(got)) }
        }

        /** `{"success":true,"data":[{"id":…,"name":…,"verified":…}]}`; a failure's errors become the exception's message. */
        fun parseGames(body: ByteArray): List<SgdbGame> = data(body).mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            SgdbGame(o.int("id") ?: return@mapNotNull null, o.str("name") ?: return@mapNotNull null, (o["verified"] as? JsonPrimitive)?.booleanOrNull ?: false)
        }

        /** `{"success":true,"data":[{"id":…,"url":…,"score":…,"width":…,"height":…}]}`. */
        fun parseImages(body: ByteArray): List<SgdbImage> = data(body).mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            SgdbImage(
                id = o.int("id") ?: 0,
                url = o.str("url") ?: return@mapNotNull null,
                score = (o["score"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
                width = o.int("width") ?: 0,
                height = o.int("height") ?: 0,
            )
        }

        private fun data(body: ByteArray): JsonArray {
            val root = json.parseToJsonElement(body.toString(Charsets.UTF_8)) as? JsonObject ?: throw IOException("SteamGridDB sent something unexpected")
            if ((root["success"] as? JsonPrimitive)?.booleanOrNull == false) {
                throw IOException((root["errors"] as? JsonArray)?.joinToString { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() } ?: "SteamGridDB said no")
            }
            return root["data"] as? JsonArray ?: JsonArray(emptyList())
        }

        private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
        private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
    }
}

/**
 * GET for SteamGridDB: with [key] as a bearer token for its API (never for anything else), without
 * one for its images. The body for 200; null for 404; otherwise an error worth showing.
 */
internal fun steamGridGet(url: String, key: String?): ByteArray? {
    if (key != null) require(url.startsWith(SteamGridDb.API + "/")) { "The SteamGridDB key only goes to steamgriddb.com" }
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", "Wii-S launcher")
        if (key != null) setRequestProperty("Authorization", "Bearer $key")
    }
    try {
        return when (val code = conn.responseCode) {
            200 -> conn.inputStream.use { it.readBytes() }
            404 -> null
            401, 403 -> throw IOException("SteamGridDB didn't accept the API key")
            429 -> throw IOException("SteamGridDB is busy (too many requests); try again later")
            else -> throw IOException("HTTP $code")
        }
    } finally {
        conn.disconnect()
    }
}

/** Big covers (over 400 KB) are kept as JPEG at their size, so a large library doesn't fill the storage. */
internal fun shrinkCover(bytes: ByteArray): ByteArray {
    if (bytes.size <= 400_000) return bytes
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    return ByteArrayOutputStream().use { out ->
        bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
        bmp.recycle()
        out.toByteArray()
    }.takeIf { it.size < bytes.size } ?: bytes
}
