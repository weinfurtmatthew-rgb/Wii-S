package app.wiis.launcher.companion

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import app.wiis.launcher.CrashLog
import app.wiis.launcher.SharedState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mode B (chosen by the M0 spike). The Thor exposes its bottom screen as a PRESENTATION display
 * without system decorations, so Android never starts a SECONDARY_HOME there. The launcher places
 * the companion itself instead:
 *  - when the top-screen home resumes (boot, Home press, return from a game), and once more a
 *    moment later if something else took the bottom screen in between,
 *  - when a secondary display appears or turns back on (screen toggle, wake).
 *
 * It deliberately does NOT re-place the companion the moment it is covered, so it never fights
 * the user or an emulator that drives both screens (melonDS, Azahar).
 */
class CompanionPlacer(private val context: Context, private val shared: SharedState, private val windowMethod: () -> Boolean = { false }) {

    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var lastAttemptAt = 0L

    /** What the last placement did, for System → Report. */
    @Volatile var lastAttempt: String = "not tried yet"
        private set

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = ensurePlaced()
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY &&
                displayManager.getDisplay(displayId)?.state == Display.STATE_ON
            ) ensurePlaced()
        }
    }

    fun start() {
        displayManager.registerDisplayListener(listener, main)
    }

    /**
     * The bottom screen's id. Never hard-coded: it was 4 on the spike device and can change. A
     * public presentation display that is on wins over any other secondary display.
     */
    fun secondaryDisplayId(): Int? {
        val others = displayManager.displays.filter { it.displayId != Display.DEFAULT_DISPLAY && it.flags and Display.FLAG_PRIVATE == 0 }
        return (
            others.firstOrNull { it.flags and Display.FLAG_PRESENTATION != 0 && it.state != Display.STATE_OFF }
                ?: others.firstOrNull { it.state != Display.STATE_OFF }
                ?: others.firstOrNull()
            )?.displayId
    }

    /** [from] is the activity asking (the top-screen home); starting from it is the most reliable. */
    fun ensurePlaced(force: Boolean = false, from: Context? = null) {
        if (CrashLog.bottomScreenPaused(context)) return note("off until the next update (chosen on the crash report)")
        if (windowMethod()) return // Home shows it as a window (CompanionPresentation).
        if (!force) {
            if (shared.companion.value != null) return
            if (shared.session.value?.usesBothScreens == true) return
            // Debounce: display callbacks arrive in bursts and the activity takes a moment to start.
            val now = SystemClock.uptimeMillis()
            if (now - lastAttemptAt < DEBOUNCE_MS) return
            lastAttemptAt = now
        }
        val id = secondaryDisplayId() ?: return note("no second display found")
        if (displayManager.getDisplay(id)?.state == Display.STATE_OFF) return note("display $id is off")
        runCatching { CompanionActivity.launchOnDisplay(from ?: context, id) }
            .onSuccess { note("asked Android to start it on display $id") }
            .onFailure { note("Android refused display $id: ${it.javaClass.simpleName}: ${it.message}") }
    }

    /**
     * Called when the home resumes: place now, and look again shortly after. Another app's bottom
     * screen (the Thor's own, or another launcher's) can come up just after ours and cover it.
     */
    fun ensurePlacedFromHome(home: Context, stillHome: () -> Boolean) {
        ensurePlaced(from = home)
        main.postDelayed({
            if (stillHome() && shared.companion.value == null && shared.session.value == null) ensurePlaced(force = true, from = home)
        }, RECHECK_MS)
    }

    fun note(what: String) {
        lastAttempt = "${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())} $what"
    }

    private companion object {
        const val DEBOUNCE_MS = 1_500L
        const val RECHECK_MS = 2_500L
    }
}
