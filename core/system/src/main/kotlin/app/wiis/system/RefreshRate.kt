package app.wiis.system

import android.app.Activity

/**
 * The Thor reported 60 Hz during the spike. Ask for the fastest mode at the current resolution
 * while this window is visible; Android drops back to its own policy when the window goes away.
 */
object RefreshRate {
    fun preferHighest(activity: Activity): Float? {
        val display = activity.display ?: return null
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return null
        if (best.modeId != current.modeId) {
            activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = best.modeId }
        }
        return best.refreshRate
    }
}
