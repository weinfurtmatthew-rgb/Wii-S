package app.wiis.emulator

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Game(
    /** Stable key: the document URI. */
    val uri: String,
    val authority: String,
    val documentId: String,
    val title: String,
    val fileName: String,
    val systemId: String,
    val sizeBytes: Long,
)

@Serializable
data class LibrarySnapshot(val rootUri: String, val scannedAtMillis: Long, val games: List<Game>)

/** Persists the last scan as JSON so the library is instant on cold start. Room comes later if queries need it. */
class LibraryStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): LibrarySnapshot? = runCatching {
        if (file.exists()) json.decodeFromString(LibrarySnapshot.serializer(), file.readText()) else null
    }.getOrNull()

    fun save(snapshot: LibrarySnapshot) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(LibrarySnapshot.serializer(), snapshot))
        tmp.renameTo(file)
    }
}

/**
 * Walks a SAF tree with raw DocumentsContract queries (one query per directory; much faster
 * than DocumentFile, which issues a query per property).
 */
class RomScanner(
    private val resolver: ContentResolver,
    private val detector: SystemDetector,
    private val maxDepth: Int = 5,
    private val maxFiles: Int = 50_000,
) {
    fun scan(treeUri: Uri, onProgress: (Int) -> Unit = {}): List<Game> {
        val authority = treeUri.authority ?: return emptyList()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val games = ArrayList<Game>()
        var visited = 0

        fun walk(docId: String, folders: List<String>, depth: Int) {
            if (depth > maxDepth || visited >= maxFiles) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            resolver.query(children, PROJECTION, null, null, null)?.use { c ->
                while (c.moveToNext() && visited < maxFiles) {
                    visited++
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)
                    if (name.startsWith('.')) continue
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walk(id, folders + name, depth + 1)
                    } else {
                        val system = detector.detect(folders, name) ?: continue
                        games += Game(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString(),
                            authority = authority,
                            documentId = id,
                            title = cleanTitle(name),
                            fileName = name,
                            systemId = system.id,
                            sizeBytes = c.getLong(3),
                        )
                    }
                }
            }
            onProgress(games.size)
        }

        walk(rootId, emptyList(), 0)
        return games.sortedWith(compareBy({ it.systemId }, { it.title.lowercase() }))
    }

    companion object {
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )

        /** "Super Game (USA) [!].sfc" -> "Super Game" */
        fun cleanTitle(fileName: String): String =
            fileName.substringBeforeLast('.')
                .replace(Regex("""\s*[\(\[][^)\]]*[\)\]]"""), "")
                .replace('_', ' ')
                .trim()
                .ifEmpty { fileName }
    }
}
