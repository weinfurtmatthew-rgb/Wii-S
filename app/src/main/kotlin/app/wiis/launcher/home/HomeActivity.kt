package app.wiis.launcher.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
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
