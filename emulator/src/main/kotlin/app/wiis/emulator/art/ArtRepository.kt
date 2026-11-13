package app.wiis.emulator.art

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.LruCache
import app.wiis.emulator.EmulatorRegistry
import app.wiis.emulator.Game
import app.wiis.emulator.SystemDef
import app.wiis.emulator.SystemDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** One place box art can come from. Returns image bytes, or null when it has nothing for this game. */
interface ArtProvider {
    val id: String
    val label: String
    suspend fun find(game: Game, system: SystemDef?): ByteArray?
}

@Serializable
data class ArtIndex(
    /** game uri -> provider id that supplied the cached image. */
    val found: Map<String, String> = emptyMap(),
    /** game uri -> epoch millis of the last miss (retried after [ArtRepository.RETRY_MISS_MS]). */
    val missed: Map<String, Long> = emptyMap(),
)

data class ArtStatus(
    val running: Boolean = false,
    val checked: Int = 0,
    val total: Int = 0,
    val found: Int = 0,
    val byProvider: Map<String, Int> = emptyMap(),
    val missingBySystem: Map<String, Int> = emptyMap(),
    val lastError: String? = null,
)

/**
 * Box art: providers are tried in order, the first hit is cached on disk, and decoded bitmaps are
 * kept in a small memory cache at tile size. Misses are remembered so the network isn't asked
 * again for a week.
 */
class ArtRepository(
    context: Context,
    private val registry: EmulatorRegistry,
    private val scope: CoroutineScope,
) {
    private val dir = File(context.filesDir, "art").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val memory = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    @Volatile private var index: ArtIndex = loadIndex()
    @Volatile var providers: List<ArtProvider> = emptyList()

    private val _status = MutableStateFlow(ArtStatus())
    val status: StateFlow<ArtStatus> = _status.asStateFlow()

    /** Emits a game uri whenever new art lands, so visible tiles can reload. */
    private val _updates = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val updates: SharedFlow<String> = _updates.asSharedFlow()

    private var prefetchJob: Job? = null

    fun hasArt(game: Game): Boolean = index.found.containsKey(game.uri)

    /** Decoded art at roughly [targetPx] on its longest side, or null if none is cached. */
    suspend fun load(game: Game, targetPx: Int): Bitmap? {
        val key = "${game.uri}@$targetPx"
        memory.get(key)?.let { return it }
        val file = fileFor(game)
        if (!file.exists()) return null
        return withContext(Dispatchers.IO) {
            decodeSampled(file, targetPx)?.also { memory.put(key, it) }
        }
    }

    /** Fetches art for every game that has none yet. Safe to call repeatedly. */
    fun prefetch(games: List<Game>, force: Boolean = false) {
        if (prefetchJob?.isActive == true) return
        val active = providers
        if (active.isEmpty()) return
        prefetchJob = scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val todo = games.filter { g ->
                !fileFor(g).exists() && (force || (index.missed[g.uri]?.let { now - it > RETRY_MISS_MS } ?: true))
            }
            _status.value = _status.value.copy(running = true, checked = 0, total = todo.size, lastError = null)
            val gate = Semaphore(PARALLEL)
            val jobs = todo.map { game ->
                launch {
                    gate.withPermit {
                        val system = registry.system(game.systemId)
                        var hit: Pair<String, ByteArray>? = null
                        for (provider in active) {
                            val bytes = runCatching { provider.find(game, system) }
                                .onFailure { e -> _status.value = _status.value.copy(lastError = "${provider.label}: ${e.message}") }
                                .getOrNull()
                            if (bytes != null && bytes.isNotEmpty()) { hit = provider.id to bytes; break }
                        }
                        synchronized(this@ArtRepository) {
                            index = if (hit != null) {
                                val file = fileFor(game)
                                file.parentFile?.mkdirs()
                                file.writeBytes(hit.second)
                                index.copy(found = index.found + (game.uri to hit.first), missed = index.missed - game.uri)
                            } else {
                                index.copy(missed = index.missed + (game.uri to System.currentTimeMillis()))
                            }
                            _status.value = _status.value.copy(checked = _status.value.checked + 1)
                        }
                        if (hit != null) _updates.tryEmit(game.uri)
                    }
                }
            }
            jobs.forEach { it.join() }
            saveIndex()
            _status.value = summarize(games).copy(running = false, lastError = _status.value.lastError)
        }
    }

    fun refreshSummary(games: List<Game>) {
        if (prefetchJob?.isActive != true) _status.value = summarize(games).copy(lastError = _status.value.lastError)
    }

    /** Forget every miss so the next prefetch asks all providers again (e.g. after adding an art folder). */
    fun clearMisses() {
        synchronized(this) { index = index.copy(missed = emptyMap()) }
        saveIndex()
    }

    fun clearAll() {
        prefetchJob?.cancel()
        dir.listFiles()?.forEach { it.deleteRecursively() }
        dir.mkdirs()
        memory.evictAll()
        synchronized(this) { index = ArtIndex() }
        saveIndex()
    }

    private fun summarize(games: List<Game>): ArtStatus {
        val found = games.filter { index.found.containsKey(it.uri) && fileFor(it).exists() }
        return ArtStatus(
            running = false,
            checked = games.size,
            total = games.size,
            found = found.size,
            byProvider = found.groupingBy { index.found.getValue(it.uri) }.eachCount(),
            missingBySystem = games.filterNot { it in found }.groupingBy { it.systemId }.eachCount(),
        )
    }

    private fun fileFor(game: Game) = File(dir, "${game.systemId}/${sha1(game.uri)}.img")

    private fun loadIndex(): ArtIndex = runCatching {
        if (indexFile.exists()) json.decodeFromString(ArtIndex.serializer(), indexFile.readText()) else ArtIndex()
    }.getOrDefault(ArtIndex())

    private fun saveIndex() {
        val text = json.encodeToString(ArtIndex.serializer(), index)
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(text)
        tmp.renameTo(indexFile)
    }

    companion object {
        const val RETRY_MISS_MS = 7L * 24 * 60 * 60 * 1000
        private const val PARALLEL = 3

        fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        fun decodeSampled(file: File, targetPx: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
            return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}

/**
 * libretro's thumbnail server: free, no account. Matches on the exact No-Intro / Redump name,
 * which is how most ROM sets (including yours) are named.
 * https://thumbnails.libretro.com/<collection>/Named_Boxarts/<name>.png
 */
class LibretroThumbnails(private val kind: String = "Named_Boxarts") : ArtProvider {
    override val id = "libretro"
    override val label = "libretro thumbnails"

    override suspend fun find(game: Game, system: SystemDef?): ByteArray? {
        val playlist = system?.libretroPlaylist ?: return null
        for (name in candidateNames(game.fileName)) {
            val url = "$BASE/${encode(playlist)}/$kind/${encode(name)}.png"
            httpGet(url)?.let { return it }
        }
        return null
    }

    companion object {
        const val BASE = "https://thumbnails.libretro.com"

        /** libretro replaces these characters with "_" in thumbnail file names. */
        private val RESERVED = Regex("""[&*/:`<>?\\|"]""")

        fun thumbnailName(raw: String): String = RESERVED.replace(raw, "_")

        /** Exact name first, then without [tags], (Rev x), (vX.Y) and (Disc n). */
        fun candidateNames(fileName: String): List<String> {
            val base = fileName.substringBeforeLast('.')
            val cleaned = base
                .replace(Regex("""\s*\[[^\]]*]"""), "")
                .replace(Regex("""\s*\((Rev|v)\s?[\w.]+\)""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s*\(Disc \d+\)""", RegexOption.IGNORE_CASE), "")
                .trim()
            return listOf(base, cleaned).map(::thumbnailName).distinct()
        }

        fun encode(segment: String): String = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
    }
}

/**
 * Your own images: any folder you pick (for example ES-DE's downloaded_media). An image whose
 * file name matches a ROM's name (without extension) is used for that ROM. If several systems use
 * the same name, an image inside a folder named after the system wins.
 */
class LocalArtFolder(private val resolver: ContentResolver, private val treeUri: Uri) : ArtProvider {
    override val id = "folder"
    override val label = "your art folder"

    private data class Entry(val uri: Uri, val folders: List<String>)

    @Volatile private var index: Map<String, List<Entry>>? = null

    override suspend fun find(game: Game, system: SystemDef?): ByteArray? {
        val entries = (index ?: buildIndex().also { index = it })[key(game.fileName.substringBeforeLast('.'))] ?: return null
        val preferred = entries.firstOrNull { e ->
            system != null && e.folders.any { f ->
                val n = SystemDetector.normalize(f)
                n == SystemDetector.normalize(system.id) || system.folders.any { SystemDetector.normalize(it) == n }
            }
        } ?: entries.first()
        return resolver.openInputStream(preferred.uri)?.use { it.readBytes() }
    }

    private fun buildIndex(): Map<String, List<Entry>> {
        val out = HashMap<String, MutableList<Entry>>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        fun walk(docId: String, folders: List<String>, depth: Int) {
            if (depth > 6) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            resolver.query(children, PROJECTION, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walk(id, folders + name, depth + 1)
                    } else if (name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS) {
                        out.getOrPut(key(name.substringBeforeLast('.'))) { mutableListOf() } +=
                            Entry(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), folders)
                    }
                }
            }
        }
        walk(rootId, emptyList(), 0)
        return out
    }

    private fun key(name: String) = name.lowercase()

    companion object {
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
    }
}

/** GET returning the body for 200, null for 404/410; throws on other failures so they're reported. */
internal fun httpGet(url: String, userAgent: String = "Wii-S launcher"): ByteArray? {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 15_000
        setRequestProperty("User-Agent", userAgent)
    }
    try {
        return when (conn.responseCode) {
            200 -> conn.inputStream.use { it.readBytes() }
            404, 410 -> null
            else -> throw java.io.IOException("HTTP ${conn.responseCode}")
        }
    } finally {
        conn.disconnect()
    }
}
