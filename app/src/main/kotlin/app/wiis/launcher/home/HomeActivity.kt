package app.wiis.launcher.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import android.hardware.display.DisplayManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.wiis.launcher.CompanionStatus
import app.wiis.launcher.companion.CompanionPresentation
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.wiis.launcher.CrashLog
import app.wiis.launcher.CrashScreen
import app.wiis.launcher.WiisApplication
import app.wiis.launcher.graph
import app.wiis.launcher.ui.WiisRoot
import app.wiis.system.RefreshRate

class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels {
        viewModelFactory { initializer { HomeViewModel(graph) } }
    }

    /** A crash report is showing instead of the launcher. */
    private var reporting = false

    /** The bottom screen, when shown as a window ("window" method). */
    private var bottom: CompanionPresentation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashLog.pending(this)
        if (report != null || !(application as WiisApplication).isRunning) {
            reporting = true
            CrashScreen.show(this, report ?: "Wii-S couldn't start. Close it and open it again.")
            return
        }
        enableEdgeToEdge()
        graph.perf.refreshHz = RefreshRate.preferHighest(this)
        graph.perf.attach(this)
        setContent {
            WiisRoot { HomeScreen(viewModel) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                graph.bottomMethod.collect { method ->
                    if (method == "window") showBottom() else {
                        hideBottom()
                        graph.companionPlacer.ensurePlaced(force = true, from = this@HomeActivity)
                    }
                }
            }
        }
    }

    private fun showBottom() {
        if (CrashLog.bottomScreenPaused(this) || bottom?.isShowing == true) return
        val placer = graph.companionPlacer
        val id = placer.secondaryDisplayId() ?: return placer.note("window: no second display found")
        val display = getSystemService(DisplayManager::class.java).getDisplay(id) ?: return placer.note("window: display $id is gone")
        runCatching { CompanionPresentation(this, display).also { it.show() } }
            .onSuccess {
                bottom = it
                placer.note("window shown on display $id")
                graph.shared.companionAttached(CompanionStatus(id, "C (window)", System.currentTimeMillis()))
            }
            .onFailure { placer.note("window refused on display $id: ${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun hideBottom() {
        bottom?.let { runCatching { it.dismiss() } }
        if (bottom != null && graph.shared.companion.value?.mode?.startsWith("C") == true) graph.shared.companionAttached(null)
        bottom = null
    }

    override fun onStop() {
        // Keep the window during a game that leaves the bottom screen free; hide it for anything else.
        if (!reporting && graph.shared.session.value?.usesBothScreens != false) hideBottom()
        super.onStop()
    }

    override fun onDestroy() {
        if (!reporting) hideBottom()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (reporting) return
        graph.perf.onResume(this)
        graph.sounds.quietMoves(600)
        viewModel.onReturnedHome()
        graph.companionPlacer.ensurePlacedFromHome(this) { lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
    }

    override fun onPause() {
        if (!reporting) graph.perf.onPause()
        super.onPause()
    }
}
