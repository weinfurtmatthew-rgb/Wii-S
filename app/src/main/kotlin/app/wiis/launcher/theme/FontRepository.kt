package app.wiis.launcher.theme

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.wiis.designsystem.TypeTokens
import app.wiis.designsystem.WiisFonts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the theme's Google Fonts once (TrueType files, cached in app storage) so any family
 * picked in the workbench works on the device. Until a family arrives, or if it can't be fetched
 * (offline, unknown name), the device font is used.
 */
class FontRepository(context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "fonts")

    private val _fonts = MutableStateFlow(WiisFonts())
    val fonts: StateFlow<WiisFonts> = _fonts.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private var job: Job? = null
    private var requested: Pair<String, String>? = null

    fun ensure(type: TypeTokens) {
        val key = type.displayFont to type.bodyFont
        val displayWeights = setOf(type.wordmarkWeight, type.tabWeight, type.shelfTitleWeight)
        val bodyWeights = setOf(type.tileTitleWeight, type.labelWeight, type.clockWeight, 400, 500)
        if (key == requested && job?.isActive == true) return
        requested = key
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            val sameFamily = type.displayFont.equals(type.bodyFont, ignoreCase = true)
            val display = family(type.displayFont, if (sameFamily) displayWeights + bodyWeights else displayWeights)
            val body = if (sameFamily) display else family(type.bodyFont, bodyWeights)
            _fonts.value = WiisFonts(display.first ?: FontFamily.Default, body.first ?: FontFamily.Default)
            _status.value = listOf(type.displayFont to display.second, type.bodyFont to body.second)
                .distinctBy { it.first.lowercase() }
                .joinToString("  ·  ") { "${it.first}: ${it.second}" }
        }
    }

    private fun family(name: String, weights: Set<Int>): Pair<FontFamily?, String> {
        if (name.isBlank() || name.equals("System", ignoreCase = true)) return null to "device font"
        val folder = File(dir, name.replace(Regex("[^A-Za-z0-9]"), "_")).apply { mkdirs() }
        val wanted = weights.map { it.coerceIn(100, 900) / 100 * 100 }.toSortedSet()
        if (wanted.any { !File(folder, "$it.ttf").exists() }) {
            runCatching { download(name, wanted, folder) }
                .onFailure { if (folder.listFiles().isNullOrEmpty()) return null to "couldn't download (${it.message})" }
        }
        val files = folder.listFiles { f -> f.extension == "ttf" }.orEmpty()
            .mapNotNull { f -> f.nameWithoutExtension.toIntOrNull()?.let { w -> Font(f, FontWeight(w)) } }
        return if (files.isEmpty()) null to "not found on Google Fonts" else FontFamily(files) to "${files.size} weights"
    }

    private fun download(name: String, weights: Set<Int>, folder: File) {
        val family = name.trim().replace(' ', '+')
        val css = get("https://fonts.googleapis.com/css2?family=$family:wght@${weights.joinToString(";")}")
            ?: get("https://fonts.googleapis.com/css2?family=$family")
            ?: error("unknown family")
        for ((weight, url) in parseCss(css)) {
            val target = File(folder, "$weight.ttf")
            if (!target.exists()) getBytes(url)?.let { target.writeBytes(it) }
        }
    }

    private fun get(url: String): String? = getBytes(url)?.let { String(it) }

    private fun getBytes(url: String): ByteArray? {
        // An unrecognised user agent makes Google Fonts serve plain TrueType, which Android loads directly.
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "Wii-S launcher")
        }
        try {
            return if (conn.responseCode == 200) conn.inputStream.use { it.readBytes() } else null
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /**
         * weight -> TrueType URL; the last face per weight wins (the Latin subset comes last).
         *
         * Plain text matching, no regular expressions: Android compiles patterns with ICU, which
         * rejects some that the JVM accepts. An unescaped "}" here made 0.2.0 to 0.4.1 crash on
         * start on the Thor while every test passed.
         */
        fun parseCss(css: String): Map<Int, String> {
            val out = LinkedHashMap<Int, String>()
            var from = 0
            while (true) {
                val at = css.indexOf("@font-face", from).takeIf { it >= 0 } ?: break
                val open = css.indexOf('{', at).takeIf { it >= 0 } ?: break
                val close = css.indexOf('}', open).takeIf { it >= 0 } ?: break
                from = close + 1
                val body = css.substring(open + 1, close)
                val weight = declaration(body, "font-weight")?.takeWhile(Char::isDigit)?.toIntOrNull() ?: continue
                val src = declaration(body, "src") ?: continue
                if ("format('truetype')" !in src && "format(\"truetype\")" !in src) continue
                val url = src.substringAfter("url(", "").substringBefore(")").trim().trim('\'', '"')
                if (url.isNotEmpty()) out[weight] = url
            }
            return out
        }

        /** The value of "name: value;" in a CSS block, trimmed; null when the block doesn't set it. */
        private fun declaration(body: String, name: String): String? {
            var i = body.indexOf("$name:")
            while (i >= 0) {
                if (i == 0 || body[i - 1].isWhitespace() || body[i - 1] == ';' || body[i - 1] == '{') {
                    return body.substring(i + name.length + 1).substringBefore(';').trim()
                }
                i = body.indexOf("$name:", i + 1)
            }
            return null
        }
    }
}
