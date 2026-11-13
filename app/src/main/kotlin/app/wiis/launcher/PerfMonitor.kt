package app.wiis.launcher

import android.app.Activity
import android.os.Process
import android.os.SystemClock
import androidx.core.view.OneShotPreDrawListener
import androidx.metrics.performance.JankStats

/**
 * On-device performance numbers for the System tab, so budgets can be checked on the Thor
 * without a computer. Complements (doesn't replace) the Macrobenchmark module.
 *
 *  - cold start: process start → first top-screen frame
 *  - resume → frame: HomeActivity.onResume → next frame (the launcher's share of "Home press →
 *    interactive"; the system's window transition is on top of this)
 *  - jank: JankStats over every frame the top screen has drawn this process
 */
class PerfMonitor {
    @Volatile var coldStartMs: Long? = null
        private set
    var refreshHz: Float? = null

    private val resumeSamples = ArrayDeque<Long>()
    @Volatile private var frames = 0L
    @Volatile private var jankFrames = 0L
    private var jankStats: JankStats? = null

    fun attach(activity: Activity) {
        val decor = activity.window.decorView
        if (coldStartMs == null) {
            OneShotPreDrawListener.add(decor) {
                if (coldStartMs == null) coldStartMs = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
            }
        }
        jankStats = JankStats.createAndTrack(activity.window) { frame ->
            frames++
            if (frame.isJank) jankFrames++
        }
    }

    fun onResume(activity: Activity) {
        jankStats?.isTrackingEnabled = true
        val start = SystemClock.uptimeMillis()
        OneShotPreDrawListener.add(activity.window.decorView) {
            synchronized(resumeSamples) {
                resumeSamples.addLast(SystemClock.uptimeMillis() - start)
                while (resumeSamples.size > 20) resumeSamples.removeFirst()
            }
        }
    }

    fun onPause() {
        jankStats?.isTrackingEnabled = false
    }

    fun report(): String = buildString {
        appendLine("== Performance (top screen) ==")
        appendLine("Refresh rate requested: ${refreshHz?.let { "%.0f Hz".format(it) } ?: "n/a"}")
        appendLine("Cold start → first frame: ${coldStartMs?.let { "$it ms" } ?: "n/a"}  (budget 500 ms)")
        val samples = synchronized(resumeSamples) { resumeSamples.toList().sorted() }
        if (samples.isNotEmpty()) {
            appendLine("Resume → frame: median ${samples[samples.size / 2]} ms, worst ${samples.last()} ms over ${samples.size}  (budget 150 ms)")
        }
        val f = frames
        if (f > 0) appendLine("Janky frames: $jankFrames / $f (${"%.2f".format(100.0 * jankFrames / f)}%)  (budget < 1%)")
    }
}
