package app.wiis.emulator.ra

import android.content.ContentResolver
import android.net.Uri
import app.wiis.emulator.EmulatorRegistry
import app.wiis.emulator.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.util.zip.ZipInputStream

data class RaCredentials(val user: String, val key: String)

/** What the RetroAchievements page shows. */
sealed interface RaResult {
    data object NotSetUp : RaResult
    /** The system has no RetroAchievements support (or none in this launcher's map). */
    data object Unsupported : RaResult
    /** No set matches this ROM. [hash] is its fingerprint when one could be computed. */
    data class NoMatch(val hash: String?) : RaResult
    data class Failed(val message: String) : RaResult
    data class OfGame(val progress: RaGameProgress) : RaResult
    data class OfSystem(val consoleName: String, val games: List<RaCompletionEntry>) : RaResult
}

class RaHttpException(val code: Int) : IOException("HTTP $code")

/**
 * RetroAchievements for the bottom screen: matches a ROM to its set (by fingerprint, else by
 * title), then reads the player's progress. The Web API key stays on the device and is only sent
 * to retroachievements.org. Console game lists are cached for a week, matches until the ROM is
 * re-matched, and progress for five minutes.
 */
class RaRepository(
    private val resolver: ContentResolver,
    private val registry: EmulatorRegistry,
    private val dir: File,
    private val credentials: () -> RaCredentials?,
    private val fetch: (String) -> String = ::httpText,
) {
    private val mutex = Mutex()
    private val matches = mutableMapOf<String, Pair<Int, Long>>()
    private var matchesLoaded = false
    private val progress = mutableMapOf<Int, Pair<Long, RaGameProgress>>()
    private var completion: Pair<Long, List<RaCompletionEntry>>? = null

    init { dir.mkdirs() }

    fun consoleId(systemId: String): Int? = registry.system(systemId)?.raConsoleId

    suspend fun forGame(game: Game): RaResult = withContext(Dispatchers.IO) {
        val creds = credentials() ?: return@withContext RaResult.NotSetUp
        val console = consoleId(game.systemId) ?: return@withContext RaResult.Unsupported
        runCatching {
            val (id, hash) = match(game, console, creds)
            if (id <= 0) return@runCatching RaResult.NoMatch(hash)
            RaResult.OfGame(progressFor(id, creds))
        }.getOrElse { RaResult.Failed(describe(it)) }
    }

    /** Everything earned on a console ("all": on every console). */
    suspend fun forSystem(systemId: String): RaResult = withContext(Dispatchers.IO) {
        val creds = credentials() ?: return@withContext RaResult.NotSetUp
        val console = if (systemId == "all") null else consoleId(systemId) ?: return@withContext RaResult.Unsupported
        runCatching {
            val all = completionFor(creds)
            val games = all.filter { console == null || it.consoleId == console }
                .sortedByDescending { it.mostRecentAwardedDate ?: "" }
            RaResult.OfSystem(registry.system(systemId)?.name ?: "all systems", games)
        }.getOrElse { RaResult.Failed(describe(it)) }
    }

    /** Checks a username and key; returns the profile on success. */
    suspend fun test(creds: RaCredentials): Result<RaProfile> = withContext(Dispatchers.IO) {
        runCatching { RaParse.profile(fetch(url("API_GetUserProfile.php", creds, "u" to creds.user))) }
    }

    /** Drops cached progress, e.g. after signing in as someone else. Matches stay valid. */
    fun clearProgress() {
        progress.clear()
        completion = null
    }

    // ---- matching ----

    private suspend fun match(game: Game, console: Int, creds: RaCredentials): Pair<Int, String?> = mutex.withLock {
        loadMatches()
        matches[game.uri]?.let { (id, at) -> if (id > 0 || System.currentTimeMillis() - at < WEEK) return id to null }
        val list = gameList(console, creds)
        val hash = runCatching { fingerprint(game) }.getOrNull()
        val hit = hash?.let { RaTitles.byHash(it, list) } ?: RaTitles.match(game.title, game.fileName, list)
        val id = hit?.id ?: 0
        matches[game.uri] = id to System.currentTimeMillis()
        saveMatches()
        id to hash
    }

    private fun fingerprint(game: Game): String? {
        if (game.systemId !in RaHash.SUPPORTED) return null
        val uri = Uri.parse(game.uri)
        if (game.fileName.endsWith(".zip", ignoreCase = true)) {
            ZipInputStream(resolver.openInputStream(uri) ?: return null).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: return null
                    if (entry.isDirectory) continue
                    val bytes = zip.readBytes()
                    if (bytes.size > MAX_ZIPPED) return null
                    return RaHash.ofBuffer(game.systemId, bytes)
                }
            }
        }
        if (game.fileName.substringAfterLast('.').lowercase() in setOf("7z", "rar")) return null
        val random = RaHash.RandomAccess { offset, length ->
            resolver.openFileDescriptor(uri, "r")!!.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                    val buf = ByteBuffer.allocate(length)
                    var pos = offset
                    while (buf.hasRemaining()) { val n = ch.read(buf, pos); if (n <= 0) break; pos += n }
                    buf.array().copyOf(buf.position())
                }
            }
        }
        return RaHash.ofFile(game.systemId, game.sizeBytes, { resolver.openInputStream(uri) ?: throw IOException("Can't open ${game.fileName}") }, random)
    }

    private fun gameList(console: Int, creds: RaCredentials): List<RaGameListEntry> {
        val file = File(dir, "games-$console.json")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < WEEK
        val text = if (fresh) file.readText() else fetch(url("API_GetGameList.php", creds, "i" to console, "h" to 1, "f" to 1)).also { file.writeText(it) }
        return RaParse.gameList(text)
    }

    private fun loadMatches() {
        if (matchesLoaded) return
        matchesLoaded = true
        val file = File(dir, "matches.tsv")
        if (!file.exists()) return
        file.forEachLine { line ->
            val p = line.split('\t')
            if (p.size == 3) matches[p[0]] = (p[1].toIntOrNull() ?: 0) to (p[2].toLongOrNull() ?: 0L)
        }
    }

    private fun saveMatches() {
        File(dir, "matches.tsv").writeText(matches.entries.joinToString("\n") { (uri, v) -> "$uri\t${v.first}\t${v.second}" })
    }

    // ---- progress ----

    private fun progressFor(id: Int, creds: RaCredentials): RaGameProgress {
        progress[id]?.let { (at, p) -> if (System.currentTimeMillis() - at < FIVE_MINUTES) return p }
        val p = RaParse.gameProgress(fetch(url("API_GetGameInfoAndUserProgress.php", creds, "g" to id, "u" to creds.user)))
        progress[id] = System.currentTimeMillis() to p
        return p
    }

    private fun completionFor(creds: RaCredentials): List<RaCompletionEntry> {
        completion?.let { (at, c) -> if (System.currentTimeMillis() - at < FIVE_MINUTES) return c }
        val c = RaParse.completion(fetch(url("API_GetUserCompletionProgress.php", creds, "u" to creds.user, "c" to 500)))
        completion = System.currentTimeMillis() to c
        return c
    }

    private fun describe(e: Throwable): String = when {
        e is RaHttpException && (e.code == 401 || e.code == 403) -> "RetroAchievements didn't accept the username or Web API key. Check them in System → RetroAchievements."
        e is RaHttpException && e.code == 429 -> "RetroAchievements is busy. Try again in a minute."
        e is RaHttpException -> "RetroAchievements answered with an error (${e.code}). Try again later."
        e is java.net.UnknownHostException || e is java.net.SocketTimeoutException -> "Can't reach RetroAchievements. Check the Wi-Fi."
        else -> "Couldn't load achievements: ${e.message ?: e::class.java.simpleName}"
    }

    companion object {
        private const val WEEK = 7L * 24 * 60 * 60 * 1000
        private const val FIVE_MINUTES = 5L * 60 * 1000
        private const val MAX_ZIPPED = 64 * 1024 * 1024
        private const val API = "https://retroachievements.org/API/"

        fun url(endpoint: String, creds: RaCredentials, vararg params: Pair<String, Any>): String =
            API + endpoint + "?y=" + enc(creds.key) + params.joinToString("") { (k, v) -> "&$k=" + enc(v.toString()) }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    }
}

/** GET returning the body; throws [RaHttpException] on a non-200 answer. */
fun httpText(url: String): String {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", "Wii-S launcher")
    }
    try {
        val code = conn.responseCode
        if (code != 200) throw RaHttpException(code)
        return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    } finally {
        conn.disconnect()
    }
}
