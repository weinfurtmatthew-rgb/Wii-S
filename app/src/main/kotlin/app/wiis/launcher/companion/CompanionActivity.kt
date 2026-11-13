package app.wiis.launcher.companion

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wiis.designsystem.LocalWiisTokens
import app.wiis.designsystem.WiisTheme
import app.wiis.launcher.CompanionStatus
import app.wiis.launcher.Focus
import app.wiis.launcher.SharedState
import app.wiis.launcher.formatPlaytime
import app.wiis.launcher.graph
import app.wiis.launcher.home.AppIcon

/**
 * Bottom screen. On the Thor it is placed by [CompanionPlacer] (mode B); the SECONDARY_HOME filter
 * stays for devices whose second display supports system decorations (mode A).
 *
 * The window is non-focusable: touch works, but it never takes key focus, so the controller
 * always drives the top screen even right after the companion is (re)placed.
 */
class CompanionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        app.wiis.system.RefreshRate.preferHighest(this)
        setContent { WiisTheme { CompanionScreen(graph.shared) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    // Visible = attached. If something covers it (an emulator using both screens), the placer
    // brings it back the next time the top-screen home resumes.
    override fun onStart() {
        super.onStart()
        val mode = if (intent.getBooleanExtra(EXTRA_SELF_PLACED, false)) "B (self-placed)" else "A (SECONDARY_HOME)"
        graph.shared.companionAttached(CompanionStatus(display.displayId, mode, System.currentTimeMillis()))
    }

    override fun onStop() {
        if (!isChangingConfigurations) graph.shared.companionAttached(null)
        super.onStop()
    }

    companion object {
        private const val EXTRA_SELF_PLACED = "self_placed"

        /** Mode B: put the companion on [displayId] ourselves. */
        fun launchOnDisplay(context: Context, displayId: Int) {
            val intent = Intent(context, CompanionActivity::class.java)
                .putExtra(EXTRA_SELF_PLACED, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = android.app.ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            context.startActivity(intent, options.toBundle())
        }
    }
}

@Composable
private fun CompanionScreen(shared: SharedState) {
    val focus by shared.focus.collectAsStateWithLifecycle()
    val session by shared.session.collectAsStateWithLifecycle()
    val companion by shared.companion.collectAsStateWithLifecycle()
    val tokens = LocalWiisTokens.current

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val s = session
        if (s != null) {
            Card {
                Text("NOW PLAYING", style = MaterialTheme.typography.labelMedium, color = tokens.accent)
                Text(s.game.title, style = MaterialTheme.typography.headlineMedium)
                Text("on ${s.emulatorName}", style = MaterialTheme.typography.bodyMedium, color = tokens.muted)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Quick controls (save state, pause, screenshot) arrive in a later iteration for emulators that support them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.muted,
                )
            }
        } else {
            when (val f = focus) {
                is Focus.OnGame -> Card {
                    Text(f.game.title, style = MaterialTheme.typography.headlineMedium)
                    Text(f.game.fileName, style = MaterialTheme.typography.bodyMedium, color = tokens.muted)
                    Spacer(Modifier.height(8.dp))
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val total = ctx.graph.playtime.totalMillis(f.game.uri)
                    Text(
                        if (total > 0) "Played ${formatPlaytime(total)}" else "Not played yet",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("A: play  ·  Y: choose emulator", style = MaterialTheme.typography.labelMedium, color = tokens.muted)
                }
                is Focus.OnApp -> Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(f.app, size = 56)
                        Spacer(Modifier.padding(8.dp))
                        Text(f.app.label, style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Focus.None -> Card {
                    Text("Wii-S", style = MaterialTheme.typography.headlineMedium, color = tokens.accent)
                    Text("Highlight a game on the top screen to see it here.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            companion?.let { "Bottom screen · display ${it.displayId} · mode ${it.mode}" } ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = tokens.muted,
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(LocalWiisTokens.current.tileSurface, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}
