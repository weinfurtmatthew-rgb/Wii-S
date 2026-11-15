package app.wiis.launcher.companion

import android.app.Presentation
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.wiis.launcher.ui.WiisRoot

/**
 * The bottom screen as a presentation window owned by the top-screen home ("window" method).
 *
 * Android draws presentation windows above every app's activities on that display. Other
 * dual-screen launchers on the Thor may keep one on the bottom screen, which hides an activity
 * placed there (the "app" method, [CompanionActivity]); a presentation of our own, shown last, sits
 * on top of theirs. It never takes key focus, so the controller keeps driving the top screen.
 */
class CompanionPresentation(private val owner: ComponentActivity, display: Display) : Presentation(owner, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent { WiisRoot { CompanionScreen() } }
        }
        setContentView(view)
        // Compose looks for these from the window's root view.
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(owner)
            it.setViewTreeSavedStateRegistryOwner(owner)
            it.setViewTreeViewModelStoreOwner(owner)
        }
    }
}
