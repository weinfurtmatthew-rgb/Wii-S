package app.wiis.launcher.companion

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.wiis.designsystem.ConsoleIcon
import app.wiis.designsystem.glassBox
import app.wiis.designsystem.mix
import app.wiis.launcher.ui.rememberArt
import app.wiis.system.AppEntry
import kotlin.math.hypot
import kotlin.math.roundToInt
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.wiis.emulator.ra.RaResult
import app.wiis.launcher.stats.GameStats
import app.wiis.launcher.stats.Stats
import app.wiis.launcher.stats.SystemStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
import app.wiis.launcher.CrashLog
import app.wiis.launcher.CrashScreen
import app.wiis.launcher.WiisApplication
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

    /** A crash report is up on the top screen; this screen only points to it. */
    private var reporting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        if (CrashLog.bottomScreenPaused(this)) {
            // Turned off from the crash report until the next update.
            reporting = true
            finish()
            return
        }
        if (CrashLog.pending(this) != null || !(application as WiisApplication).isRunning) {
            reporting = true
            CrashScreen.showOnBottom(this)
            return
        }
        enableEdgeToEdge()
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
        if (reporting) return
        val mode = if (intent.getBooleanExtra(EXTRA_SELF_PLACED, false)) "B (self-placed)" else "A (SECONDARY_HOME)"
        // Robolectric's activities have no display; on a device this always succeeds.
        val displayId = runCatching { display.displayId }.getOrDefault(-1)
        graph.shared.companionAttached(CompanionStatus(displayId, mode, System.currentTimeMillis()))
    }

    override fun onStop() {
        if (!reporting && !isChangingConfigurations) graph.shared.companionAttached(null)
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

    if (tokens.layout == "artDock") {
        val hero by graph.shared.hero.collectAsStateWithLifecycle()
        val dock by graph.dock.collectAsStateWithLifecycle()
        ArtDockScreen(
            focus = focus,
            session = session,
            hero = hero,
            dock = dock,
            onApp = graph.apps::launch,
            onDrawer = graph.shared::requestApps,
            systemName = { id -> graph.registry.system(id)?.name ?: id },
        )
        return
    }

    val subject: CompanionSubject? = when {
        session != null -> CompanionSubject.OfGame(session!!.game, session)
        else -> when (val f = focus) {
            is Focus.OnGame -> CompanionSubject.OfGame(f.game, null)
            is Focus.OnSystem -> CompanionSubject.OfSystem(f.id, f.name, f.count)
            else -> null
        }
    }
    val pages = remember(tokens.showStats, tokens.showAchievements) {
        listOfNotNull(CompanionPage.Details, CompanionPage.Stats.takeIf { tokens.showStats }, CompanionPage.Achievements.takeIf { tokens.showAchievements })
    }
    // One pager for every game and system, so the page you're on stays put while focus moves.
    val pagerState = rememberPagerState { pages.size }

    Box(
        Modifier
            .fillMaxSize()
            .wiisBackground(style, solidOverride = if (tokens.background == "color") style.companionBackground else null)
            .safeDrawingPadding(),
    ) {
        if (subject != null) {
            CompanionPager(pages, pagerState, tokens.showClock) { page ->
                when (page) {
                    CompanionPage.Details -> Box(Modifier.fillMaxSize()) { DetailsFor(subject, style) }
                    CompanionPage.Stats -> StatsFor(subject, style)
                    CompanionPage.Achievements -> AchievementsFor(subject, style, visible = pages.getOrNull(pagerState.currentPage) == CompanionPage.Achievements)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (tokens.showClock) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ClockText() }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (val f = focus) {
                        is Focus.OnApp -> Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(f.app, size = 72)
                            Spacer(Modifier.width(16.dp))
                            Text(f.app.label, style = style.heading)
                        }
                        else -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(style.spec.header.wordmark, style = style.wordmark)
                            Text("Highlight a game on the top screen to see it here.", style = style.label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsFor(subject: CompanionSubject, style: WiisStyle) {
    val graph = LocalContext.current.graph
    when (subject) {
        is CompanionSubject.OfGame -> if (subject.session != null) NowPlaying(subject.session, style) else GameDetails(subject.game, style)
        is CompanionSubject.OfSystem -> {
            val games = remember(subject.id) { graph.currentGames().filter { subject.id == "all" || it.systemId == subject.id } }
            val played = games.count { graph.playtime.lastPlayed(it.uri) > 0 }
            val emulator = graph.launcher.choicesFor(subject.id).firstOrNull()?.let { graph.registry.emulator(it.emulator)?.name }
            SystemDetailsPage(subject.id, subject.name, games.size, played, if (subject.id == "all") null else emulator ?: "None set up")
        }
    }
}

@Composable
private fun StatsFor(subject: CompanionSubject, style: WiisStyle) {
    val graph = LocalContext.current.graph
    when (subject) {
        is CompanionSubject.OfGame -> {
            val game = subject.game
            val stats by produceState<GameStats?>(null, game.uri) {
                value = withContext(Dispatchers.IO) {
                    Stats.game(game.uri, graph.sessions.all(), graph.playtime.totalMillis(game.uri), graph.playtime.lastPlayed(game.uri), System.currentTimeMillis())
                }
            }
            stats?.let { GameStatsPage(it, style.systemColor(game.systemId)) }
        }
        is CompanionSubject.OfSystem -> {
            val stats by produceState<SystemStats?>(null, subject.id) {
                value = withContext(Dispatchers.IO) {
                    Stats.system(subject.id, graph.currentGames(), graph.sessions.all(), graph.playtime::totalMillis, System.currentTimeMillis())
                }
            }
            val color = if (subject.id == "all") style.accent else style.systemColor(subject.id)
            stats?.let { SystemStatsPage(it, if (subject.id == "all") "all systems" else subject.name, color) }
        }
    }
}

/** Loads only while the page is showing, after focus has settled, so moving fast doesn't call the API for every tile. */
@Composable
private fun AchievementsFor(subject: CompanionSubject, style: WiisStyle, visible: Boolean) {
    val graph = LocalContext.current.graph
    var retry by remember { mutableIntStateOf(0) }
    var result by remember(subject) { mutableStateOf<RaResult?>(null) }
    LaunchedEffect(subject, visible, retry) {
        if (!visible) return@LaunchedEffect
        delay(350)
        result = null
        result = when (subject) {
            is CompanionSubject.OfGame -> graph.ra.forGame(subject.game)
            is CompanionSubject.OfSystem -> graph.ra.forSystem(subject.id)
        }
    }
    val color = when (subject) {
        is CompanionSubject.OfGame -> style.systemColor(subject.game.systemId)
        is CompanionSubject.OfSystem -> if (subject.id == "all") style.accent else style.systemColor(subject.id)
    }
    AchievementsPage(result, color, onSetUp = { graph.shared.requestSystemPage("ra") }, onRetry = { retry++ })
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

/**
 * Bottom-screen layout "artDock": the focused game's cover across the whole screen (or the
 * highlighted system), a clock pill, and a glass dock of apps. Touch only: this window never
 * takes controller focus.
 */
@Composable
private fun ArtDockScreen(
    focus: Focus,
    session: Session?,
    hero: Game?,
    dock: List<AppEntry>,
    onApp: (AppEntry) -> Unit,
    onDrawer: () -> Unit,
    systemName: (String) -> String,
) {
    val style = LocalWiis.current
    val c = style.spec.companion
    val type = style.spec.type
    // As in the workbench: what's playing, else the highlighted system or game, else the last played.
    val game: Game? = session?.game ?: (focus as? Focus.OnGame)?.game ?: if (focus is Focus.OnSystem) null else hero
    val systemId = if (session == null) (focus as? Focus.OnSystem)?.id else null

    Box(Modifier.fillMaxSize().background(style.background)) {
        when {
            systemId != null -> Pool(style.systemColorOrAccent(systemId)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    ConsoleIcon(systemId, if (systemId == "all") 150.dp else 170.dp, Color.White.copy(alpha = 0.92f), strokeUnits = 1.2f)
                    Text(
                        if (systemId == "all") "All games" else systemName(systemId),
                        style = TextStyle(fontFamily = style.fonts.display, fontWeight = FontWeight(type.wordmarkWeight.coerceIn(100, 900)), fontSize = 30.sp, color = Color.White),
                    )
                }
            }
            game != null -> {
                val px = with(LocalDensity.current) { 538.dp.roundToPx() }
                val art = rememberArt(game, px)
                if (art != null) {
                    Image(art.asImageBitmap(), contentDescription = game.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Pool(style.systemColor(game.systemId)) {
                        Text(
                            game.title,
                            style = TextStyle(fontFamily = style.fonts.display, fontWeight = FontWeight(type.wordmarkWeight.coerceIn(100, 900)), fontSize = 34.sp, color = Color.White, textAlign = TextAlign.Center),
                            modifier = Modifier.padding(40.dp),
                        )
                    }
                }
            }
        }
        val pillText = style.clock.copy(fontSize = (type.clockSize * 0.8f).roundToInt().sp, color = style.glassText)
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (session != null) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp).glassBox(style, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val bold = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("● NOW PLAYING", style = bold.copy(color = style.accent), maxLines = 1)
                    Text(session.game.title, style = bold.copy(color = style.glassText), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (c.showClock) {
                Box(Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 12.dp).glassBox(style, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    ClockText(pillText)
                }
            }
            if (c.showHints && game != null && session == null && systemId == null) {
                HintBar(listOf("A" to "Play", "Y" to "Details"), Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 14.dp, bottom = if (c.dock) 74.dp else 14.dp))
            }
            if (c.dock) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).glassBox(style, CircleShape).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (c.showDrawer) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(style.glassText.copy(alpha = 0.08f)).clickable(onClick = onDrawer),
                            contentAlignment = Alignment.Center,
                        ) {
                            DrawerGlyph(20.dp, style.glassText)
                        }
                    }
                    dock.take(c.dockApps.coerceAtLeast(0)).forEach { app ->
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { onApp(app) }, contentAlignment = Alignment.Center) { AppIcon(app, 40) }
                    }
                }
            }
        }
    }
}

/** A soft pool of [color] (CSS radial-gradient at 35% 30%) with [content] centred on it. */
@Composable
private fun Pool(color: Color, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(mix(color, Color.White, 0.2f), mix(color, Color.Black, 0.5f)),
                    center = Offset(size.width * 0.35f, size.height * 0.3f),
                    radius = hypot(size.width * 0.65f, size.height * 0.7f),
                ),
            )
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Nine dots: the app-drawer button. */
@Composable
private fun DrawerGlyph(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val u = this.size.minDimension / 24f
        for (r in 0 until 3) for (col in 0 until 3) drawCircle(color, 1.6f * u, Offset((6 + col * 6) * u, (6 + r * 6) * u))
    }
}

private fun WiisStyle.systemColorOrAccent(id: String): Color = if (id == "all") accent else systemColor(id)
