package app.wiis.launcher.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.wiis.designsystem.WiisTheme
import app.wiis.launcher.graph

class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels {
        viewModelFactory { initializer { HomeViewModel(graph) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WiisTheme { HomeScreen(viewModel) }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onReturnedHome()
    }
}
