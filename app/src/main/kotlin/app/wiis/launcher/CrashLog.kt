package app.wiis.launcher

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash so the next start can show it instead of crashing again. Wii-S is the Home
 * app, and the Thor has no easy way to read Android's own crash log without a computer.
 */
object CrashLog {
    private const val NAME = "last-crash.txt"

    private fun file(context: Context) = File(context.filesDir, NAME)

    /** Records any uncaught error, then lets Android handle it as usual. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, error, thread) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(context: Context, error: Throwable, thread: Thread? = Thread.currentThread()) {
        file(context).writeText(report(context, error, thread))
    }

    fun report(context: Context, error: Throwable, thread: Thread?): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            appendLine("Wii-S $version on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine(time + (thread?.let { ", thread \"${it.name}\"" } ?: ""))
            appendLine()
            append(trace)
        }
    }

    /** The report from the last crash, until it is dismissed. */
    fun pending(context: Context): String? = file(context).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) {
        file(context).delete()
    }

    // ---- "Start without the bottom screen": until a newer version is installed ----

    private fun versionCode(context: Context): Long =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode }.getOrDefault(-1L)

    fun pauseBottomScreen(context: Context) {
        context.getSharedPreferences("wiis", Context.MODE_PRIVATE).edit().putLong(KEY_BOTTOM_PAUSED, versionCode(context)).commit()
    }

    fun bottomScreenPaused(context: Context): Boolean =
        context.getSharedPreferences("wiis", Context.MODE_PRIVATE).getLong(KEY_BOTTOM_PAUSED, -2L) == versionCode(context)

    private const val KEY_BOTTOM_PAUSED = "bottom_screen_paused_for_version"

    /** Saves the report as a text file in Downloads, so it can be attached anywhere. Returns its name. */
    fun saveToDownloads(context: Context, report: String): String {
        val name = "Wii-S crash report ${SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US).format(Date())}.txt"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Couldn't create the file")
        context.contentResolver.openOutputStream(uri)?.use { it.write(report.toByteArray()) } ?: error("Couldn't write the file")
        return name
    }
}
