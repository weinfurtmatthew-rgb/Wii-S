package app.wiis.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display

/**
 * Hardware spike: records how the device exposes its screens, and whether Android would hand the
 * secondary display to our SECONDARY_HOME activity (mode A) or something else owns it.
 */
class DisplayProbe(private val context: Context) {

    data class DisplaySnapshot(
        val id: Int,
        val name: String,
        val widthPx: Int,
        val heightPx: Int,
        val densityDpi: Int,
        val refreshHz: Float,
        val flags: List<String>,
        val state: String,
        val supportedModes: List<String>,
    )

    fun displays(): List<DisplaySnapshot> {
        val dm = context.getSystemService(DisplayManager::class.java)
        return dm.displays.map { d ->
            val mode = d.mode
            val windowContext = context.createDisplayContext(d)
            DisplaySnapshot(
                id = d.displayId,
                name = d.name,
                widthPx = mode.physicalWidth,
                heightPx = mode.physicalHeight,
                densityDpi = windowContext.resources.configuration.densityDpi,
                refreshHz = d.refreshRate,
                flags = decodeFlags(d.flags),
                state = when (d.state) {
                    Display.STATE_ON -> "ON"
                    Display.STATE_OFF -> "OFF"
                    Display.STATE_DOZE -> "DOZE"
                    else -> "STATE_${d.state}"
                },
                supportedModes = d.supportedModes.map {
                    "${it.physicalWidth}x${it.physicalHeight}@${"%.0f".format(it.refreshRate)}" + if (it.modeId == mode.modeId) "*" else ""
                },
            )
        }
    }

    /** Which packages answer CATEGORY_SECONDARY_HOME, and who is the default HOME. */
    fun homeResolution(): String = buildString {
        val pm = context.packageManager
        val home = pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        appendLine("Default HOME: ${home?.activityInfo?.let { "${it.packageName}/${it.name}" } ?: "none (chooser)"}")
        val secondary = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_SECONDARY_HOME),
            0,
        )
        appendLine("SECONDARY_HOME candidates:")
        secondary.forEach { appendLine("  ${it.activityInfo.packageName}/${it.activityInfo.name}") }
        val configPkg = runCatching {
            val id = context.resources.getIdentifier("config_secondaryHomePackage", "string", "android")
            if (id != 0) context.resources.getString(id) else null
        }.getOrNull()
        appendLine("config_secondaryHomePackage: ${configPkg ?: "(not readable)"}")
    }

    fun deviceSummary(): String =
        "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}, build ${Build.DISPLAY}"

    private fun decodeFlags(flags: Int): List<String> = buildList {
        if (flags and Display.FLAG_SECURE != 0) add("SECURE")
        if (flags and Display.FLAG_SUPPORTS_PROTECTED_BUFFERS != 0) add("PROTECTED_BUFFERS")
        if (flags and Display.FLAG_PRIVATE != 0) add("PRIVATE")
        if (flags and Display.FLAG_PRESENTATION != 0) add("PRESENTATION")
        if (flags and Display.FLAG_ROUND != 0) add("ROUND")
        // Hidden flag: system decorations (home, nav) allowed on this display.
        if (flags and (1 shl 6) != 0) add("SHOULD_SHOW_SYSTEM_DECORATIONS")
        if (flags and (1 shl 7) != 0) add("TRUSTED")
        add("raw=0x${flags.toString(16)}")
    }
}
