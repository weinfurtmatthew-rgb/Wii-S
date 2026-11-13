package app.wiis.launcher.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.wiis.launcher.graph
import app.wiis.launcher.ui.WiisRoot
import app.wiis.system.RefreshRate

class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels {
        viewModelFactory { initializer { HomeViewModel(graph) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        graph.perf.refreshHz = RefreshRate.preferHighest(this)
        graph.perf.attach(this)
        setContent {
            WiisRoot { HomeScreen(viewModel) }
        }
    }

    override fun onResume() {
        super.onResume()
        graph.perf.onResume(this)
        graph.sounds.quietMoves(600)
        viewModel.onReturnedHome()
        graph.companionPlacer.ensurePlaced()
    }

    override fun onPause() {
        graph.perf.onPause()
        super.onPause()
    }
}
