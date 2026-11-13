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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wiis.designsystem.LocalWiis
import app.wiis.designsystem.WiisStyle
import app.wiis.designsystem.wiisBackground
import app.wiis.emulator.Game
import app.wiis.launcher.CompanionStatus
import app.wiis.launcher.Focus
import app.wiis.launcher.Session
import app.wiis.launcher.formatPlaytime
import app.wiis.launcher.graph
import app.wiis.launcher.ui.AppIcon
import app.wiis.launcher.ui.ClockText
import app.wiis.launcher.ui.GameArt
import app.wiis.launcher.ui.HintBar
import app.wiis.launcher.ui.SystemBadge
import app.wiis.launcher.ui.WiisRoot
import app.wiis.launcher.ui.formatLastPlayed

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
        setContent { WiisRoot { CompanionScreen() } }
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
private fun CompanionScreen() {
    val graph = LocalContext.current.graph
    val style = LocalWiis.current
    val tokens = style.spec.companion
    val focus by graph.shared.focus.collectAsStateWithLifecycle()
    val session by graph.shared.session.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .wiisBackground(style, solidOverride = if (tokens.background == "color") style.companionBackground else null),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (tokens.showClock) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ClockText() }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val s = session
                when {
                    s != null -> NowPlaying(s, style)
                    else -> when (val f = focus) {
                        is Focus.OnGame -> GameDetails(f.game, style)
                        is Focus.OnApp -> Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(f.app, size = 72)
                            Spacer(Modifier.width(16.dp))
                            Text(f.app.label, style = style.heading)
                        }
                        Focus.None -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(style.spec.header.wordmark, style = style.wordmark)
                            Text("Highlight a game on the top screen to see it here.", style = style.label)
                        }
                    }
                }
            }
            if (tokens.showHints && session == null && focus is Focus.OnGame) {
                HintBar(listOf("A" to "Play", "Y" to "Emulator"))
            }
        }
    }
}

@Composable
private fun GameDetails(game: Game, style: WiisStyle) {
    val tokens = style.spec.companion
    when (tokens.layout) {
        "minimal" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(game.title, style = style.heading, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(10.dp))
            SystemBadge(game.systemId, style, style.systemColor(game.systemId))
        }
        "info" -> Column(
            Modifier.fillMaxWidth().background(style.surface, style.cardShape).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(game.title, style = style.heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Facts(game, style)
        }
        else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            CoverPanel(game, style, Modifier.fillMaxHeight().width(220.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                SystemBadge(game.systemId, style, style.systemColor(game.systemId))
                Text(game.title, style = style.heading, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Facts(game, style)
            }
        }
    }
}

@Composable
private fun CoverPanel(game: Game, style: WiisStyle, modifier: Modifier) {
    val color = style.systemColor(game.systemId)
    val px = with(LocalDensity.current) { 300.dp.roundToPx() }
    Box(
        modifier
            .clip(style.cardShape)
            .background(Brush.linearGradient(listOf(lerp(color, style.tile, 0.35f), lerp(color, Color.Black, 0.45f)))),
        contentAlignment = Alignment.Center,
    ) {
        val hasArt = GameArt(game, px, ContentScale.Fit, Modifier.fillMaxSize().padding(12.dp))
        if (!hasArt) Text(game.title, style = style.tileTitle, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun Facts(game: Game, style: WiisStyle) {
    val graph = LocalContext.current.graph
    val tokens = style.spec.companion
    val total = graph.playtime.totalMillis(game.uri)
    val last = graph.playtime.lastPlayed(game.uri)
    if (tokens.showPlaytime) Fact("Played", if (total > 0) formatPlaytime(total) else "Not yet", style)
    if (tokens.showLastPlayed) Fact("Last played", formatLastPlayed(last), style)
    if (tokens.showEmulator) {
        val choice = graph.launcher.choicesFor(game.systemId).firstOrNull()
        val name = choice?.let { graph.registry.emulator(it.emulator)?.name }
        Fact("Emulator", name ?: "None set up", style)
    }
}

@Composable
private fun Fact(label: String, value: String, style: WiisStyle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = style.label, modifier = Modifier.width(110.dp))
        Text(value, style = style.body)
    }
}

@Composable
private fun NowPlaying(s: Session, style: WiisStyle) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        if (style.spec.companion.layout == "art") CoverPanel(s.game, style, Modifier.fillMaxHeight().width(220.dp))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(style.accent, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("NOW PLAYING", style = style.label.copy(color = style.accent))
            }
            Text(s.game.title, style = style.heading, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("on ${s.emulatorName}", style = style.body.copy(color = style.textMuted))
        }
    }
}
