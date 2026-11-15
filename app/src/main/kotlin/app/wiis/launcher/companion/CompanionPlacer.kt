package app.wiis.launcher.companion

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import app.wiis.launcher.SharedState

/**
 * Mode B (chosen by the M0 spike). The Thor exposes its bottom screen as a PRESENTATION display
 * without system decorations, so Android never starts a SECONDARY_HOME there. The launcher places
 * the companion itself instead:
 *  - when the top-screen home resumes (boot, Home press, return from a game),
 *  - when a secondary display appears or turns back on (screen toggle, wake).
 *
 * It deliberately does NOT re-place the companion the moment it is covered, so it never fights
 * the user or an emulator that drives both screens (melonDS, Azahar).
 */
class CompanionPlacer(private val context: Context, private val shared: SharedState) {

    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private var lastAttemptAt = 0L

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
        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
    }

    /** The bottom screen's id. Never hard-coded: it was 4 on the spike device and can change. */
    fun secondaryDisplayId(): Int? =
        displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }?.displayId

    fun ensurePlaced(force: Boolean = false) {
        if (app.wiis.launcher.CrashLog.bottomScreenPaused(context)) return
        if (!force) {
            if (shared.companion.value != null) return
            if (shared.session.value?.usesBothScreens == true) return
            // Debounce: display callbacks arrive in bursts and the activity takes a moment to start.
            val now = SystemClock.uptimeMillis()
            if (now - lastAttemptAt < DEBOUNCE_MS) return
            lastAttemptAt = now
        }
        val id = secondaryDisplayId() ?: return
        if (displayManager.getDisplay(id)?.state == Display.STATE_OFF) return
        runCatching { CompanionActivity.launchOnDisplay(context, id) }
    }

    private companion object {
        const val DEBOUNCE_MS = 1_500L
    }
}
