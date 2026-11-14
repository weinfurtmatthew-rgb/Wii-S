package app.wiis.launcher.theme

import android.content.Context
import android.net.Uri
import app.wiis.designsystem.ThemeSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The active theme: an imported theme.json (exported from the Design Workbench) if there is one,
 * otherwise the built-in proposal in assets/theme/default.json.
 */
class ThemeRepository(private val context: Context) {
    private val file = File(context.filesDir, "theme.json")

    private val _spec = MutableStateFlow(load())
    val spec: StateFlow<ThemeSpec> = _spec.asStateFlow()

    val isImported: Boolean get() = file.exists()

    fun builtIn(): ThemeSpec =
        ThemeSpec.parse(context.assets.open("theme/default.json").bufferedReader().use { it.readText() })

    private fun load(): ThemeSpec =
        if (file.exists()) runCatching { ThemeSpec.parse(file.readText()) }.getOrElse { builtIn() } else builtIn()

    /** Reads, validates and activates a theme file. The previous theme stays if the file is invalid. */
    fun import(uri: Uri): Result<ThemeSpec> = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            require(bytes.size < MAX_BYTES) { "That file is too large to be a theme." }
            String(bytes)
        } ?: error("Couldn't open that file.")
        val spec = ThemeSpec.parse(text)
        require(spec.schema <= SUPPORTED_SCHEMA) { "This theme needs a newer version of Wii-S." }
        file.writeText(ThemeSpec.encode(spec))
        _spec.value = spec
        spec
    }

    fun reset() {
        file.delete()
        _spec.value = builtIn()
    }

    private companion object {
        const val MAX_BYTES = 1_000_000
        const val SUPPORTED_SCHEMA = 3
    }
}
