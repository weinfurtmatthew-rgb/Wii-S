package app.wiis.launcher.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import app.wiis.designsystem.ConsoleGlyph
import app.wiis.launcher.ui.DotsBackground
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.focusProperties
import app.wiis.launcher.ui.WindowSpec
import app.wiis.launcher.ui.itemShown
import app.wiis.launcher.ui.rowShown
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wiis.designsystem.BatteryIcon
import app.wiis.designsystem.ConsoleIcon
import app.wiis.designsystem.LocalUiFeedback
import app.wiis.designsystem.LocalWiis
import app.wiis.designsystem.SectionIcon
import app.wiis.designsystem.UiEvent
import app.wiis.designsystem.WifiIcon
import app.wiis.designsystem.WiisStyle
import app.wiis.designsystem.WiisTile
import app.wiis.designsystem.bleed
import app.wiis.designsystem.boxShadow
import app.wiis.designsystem.cssLinearGradient
import app.wiis.designsystem.cssLineHeight
import app.wiis.designsystem.glass
import app.wiis.designsystem.glassBox
import app.wiis.designsystem.lineBox
import app.wiis.designsystem.mix
import app.wiis.designsystem.wiisBackground
import app.wiis.emulator.Game
import app.wiis.launcher.formatPlaytime
import app.wiis.launcher.graph
import app.wiis.launcher.system.SystemScreen
import app.wiis.launcher.ui.AppIcon
import app.wiis.launcher.ui.ArtBackground
import app.wiis.launcher.ui.BgSubject
import app.wiis.launcher.ui.ClockText
import app.wiis.launcher.ui.Hint
import app.wiis.launcher.ui.HintBar
import app.wiis.launcher.ui.rememberArt
import app.wiis.launcher.ui.ConsoleMark
import app.wiis.launcher.ui.Cover
import app.wiis.launcher.ui.SystemRing
import app.wiis.system.AppEntry
import app.wiis.system.DeviceStatus
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import androidx.compose.animation.core.AnimationVector1D
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart

/**
 * The hero layout (Draft 3): art background, frosted glass, "Jump back in" card beside a grid of
 * systems, a recent-games row, a caption naming what is focused, and the navigation dock along the
 * bottom. Every size, colour and font comes from the theme, measured to match the workbench.
 */

/** What the hero layout asks for. The view model answers on the device; tests pass a stub. */
interface HeroActions {
    fun selectSection(section: Section)
    fun cycleSection(delta: Int)
    fun onStart()
    fun onSelect()
    fun openSystem(id: String)
    fun closeSystem(): Boolean
    fun focusGame(game: Game)
    fun focusSystem(entry: SystemEntry)
    fun focusApp(app: AppEntry)
    fun launchGame(game: Game)
    fun openPicker(game: Game)
    fun togglePin(game: Game, fromRow: Boolean)
    fun launchApp(app: AppEntry)
    fun openDockPicker(slot: Int)
    fun focusApplied()
    fun quietMoves()
    fun pickFolder()
    fun rescan()
}

@Composable
fun HeroHomeRoute(vm: HomeViewModel, onPickFolder: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val graph = LocalContext.current.graph
    val status by graph.systemStatus.status.collectAsStateWithLifecycle()
    val actions = remember(vm, graph) {
        object : HeroActions {
            override fun selectSection(section: Section) = vm.selectSection(section)
            override fun cycleSection(delta: Int) = vm.cycleSection(delta)
            override fun onStart() = vm.onStart()
            override fun onSelect() = vm.onSelect()
            override fun openSystem(id: String) = vm.openSystem(id)
            override fun closeSystem() = vm.closeSystem()
            override fun focusGame(game: Game) = vm.focusGame(game.systemId, game)
            override fun focusSystem(entry: SystemEntry) = vm.focusSystem(entry)
            override fun focusApp(app: AppEntry) = vm.focusApp(app)
            override fun launchGame(game: Game) = vm.launchGame(game)
            override fun openPicker(game: Game) = vm.openPicker(game)
            override fun togglePin(game: Game, fromRow: Boolean) = vm.togglePin(game, fromRow)
            override fun launchApp(app: AppEntry) = vm.launchApp(app)
            override fun openDockPicker(slot: Int) = vm.openDockPicker(slot)
            override fun focusApplied() = vm.heroFocusConsumed()
            override fun quietMoves() = graph.sounds.quietMoves()
            override fun pickFolder() = onPickFolder()
            override fun rescan() = vm.rescan()
        }
    }
    HeroHomeScreen(state, status, actions) { SystemScreen(onPickRomFolder = onPickFolder, requestedPage = state.systemPage, onPageShown = vm::systemPageShown) }

    state.picker?.let { p -> EmulatorPickerDialog(p, onPick = { vm.launchGame(p.game, it) }, onDismiss = vm::dismissPicker) }
    state.dockPicker?.let { p -> DockPickerDialog(p, state.dock.getOrNull(p.slot), onPick = vm::chooseDockApp, onDismiss = vm::dismissDockPicker) }
}

/** What has focus, as far as the caption and the background care. */
private sealed interface Focused {
    data object None : Focused
    data class Hero(val game: Game) : Focused
    data class System(val entry: SystemEntry) : Focused
    data class Pinned(val game: Game) : Focused
    data class InGrid(val game: Game) : Focused
    data class Nav(val section: Section) : Focused
    data class DockApp(val app: AppEntry) : Focused
    data class AppTile(val app: AppEntry) : Focused
    data object Other : Focused
}

private data class BodyKey(val section: Section, val view: HomeView)

/** CSS `ease-out`, used by the workbench for section changes. */
private val CssEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

@Composable
fun HeroHomeScreen(
    state: HomeUiState,
    status: DeviceStatus,
    actions: HeroActions,
    systemContent: @Composable () -> Unit,
) {
    val style = LocalWiis.current
    val spec = style.spec
    val feedback = LocalUiFeedback.current

    var focused by remember { mutableStateOf<Focused>(Focused.None) }
    // null = follow the Jump back in game, as the workbench does when an app has focus.
    var subject by remember { mutableStateOf<BgSubject?>(null) }
    val background = subject ?: state.hero?.let { BgSubject.OfGame(it) } ?: BgSubject.Plain
    val onFocused: (Focused) -> Unit = { f ->
        focused = f
        when (f) {
            is Focused.Hero -> { subject = BgSubject.OfGame(f.game); actions.focusGame(f.game) }
            is Focused.Pinned -> { subject = BgSubject.OfGame(f.game); actions.focusGame(f.game) }
            is Focused.InGrid -> { subject = BgSubject.OfGame(f.game); actions.focusGame(f.game) }
            is Focused.System -> { subject = BgSubject.OfSystem(f.entry.id); actions.focusSystem(f.entry) }
            is Focused.DockApp -> { subject = null; actions.focusApp(f.app) }
            is Focused.AppTile -> { subject = null; actions.focusApp(f.app) }
            else -> Unit
        }
    }

    BackHandler(enabled = state.view is HomeView.InSystem || state.section != Section.Games) {
        feedback.emit(UiEvent.Back)
        if (!actions.closeSystem()) actions.selectSection(Section.Games)
    }

    val pad = spec.spacing.screenPadding
    val dockNav = spec.nav.style == "dock"
    val nameplate = dockNav && spec.nav.captionPlace == "nameplate"
    val captionInHeader = dockNav && spec.nav.captionPlace == "header"
    val info = captionFor(focused, state, style)
    val topPad = (pad * 0.6f).roundToInt()
    val presses = remember { ShoulderPresses() }

    Box(
        Modifier
            .fillMaxSize()
            // Global buttons work from anywhere on the top screen.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.ButtonL1 -> { presses.l1++; feedback.emit(UiEvent.Tab); actions.cycleSection(-1); true }
                    Key.ButtonR1 -> { presses.r1++; feedback.emit(UiEvent.Tab); actions.cycleSection(+1); true }
                    Key.ButtonStart -> { feedback.emit(UiEvent.Tab); actions.onStart(); true }
                    Key.ButtonSelect -> { feedback.emit(UiEvent.Tab); actions.onSelect(); true }
                    // X pins the highlighted game to the bottom row, or unpins it.
                    Key.ButtonX -> {
                        val game = gameOf(focused)
                        if (game == null || state.section != Section.Games) false else {
                            feedback.emit(if (state.isPinned(game)) UiEvent.Back else UiEvent.Select)
                            actions.togglePin(game, fromRow = focused is Focused.Pinned)
                            true
                        }
                    }
                    else -> false
                }
            },
    ) {
        when (spec.colors.backgroundStyle) {
            "art", "artLight" -> ArtBackground(background, Modifier.fillMaxSize())
            "dots" -> DotsBackground(background, Modifier.fillMaxSize())
            else -> Box(Modifier.fillMaxSize().wiisBackground(style))
        }

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(start = pad.dp, end = pad.dp, top = topPad.dp, bottom = (if (dockNav) 10 else topPad).dp),
        ) {
            HeroHeader(
                state = state,
                status = status,
                dockNav = dockNav,
                actions = actions,
                onFocused = onFocused,
                caption = if (captionInHeader) ({ Caption(focused, state, inHeader = true) }) else null,
                nameplate = if (nameplate) ({ Nameplate(info, focused, it) }) else null,
            )
            Spacer(Modifier.height((spec.spacing.headerGap * 0.75f).roundToInt().dp))
            HeroBody(state, actions, onFocused, systemContent, Modifier.weight(1f).fillMaxWidth())
            state.message?.let { text ->
                Text(
                    text,
                    style = style.body.copy(color = style.glassText),
                    modifier = Modifier.padding(top = 10.dp).fillMaxWidth().glassBox(style).padding(14.dp),
                )
            }
            if (!captionInHeader && !nameplate) {
                Box(Modifier.fillMaxWidth().heightIn(min = 26.dp).padding(top = 6.dp), contentAlignment = Alignment.CenterStart) {
                    Caption(focused, state, inHeader = false)
                }
            }
            if (nameplate) {
                DockRow(info.hints, Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    NavDock(state, actions, onFocused, presses)
                }
            } else if (dockNav) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                    NavDock(state, actions, onFocused, presses)
                }
            }
        }
    }
}

/**
 * The dock with the button hints after it. The dock's left edge lines up with the content above
 * and never moves, whatever the hints say; the hints follow its right edge. A hint that doesn't
 * fit is left out whole, along with the ones after it.
 */
@Composable
private fun DockRow(hints: List<Pair<String, String>>, modifier: Modifier, dock: @Composable () -> Unit) {
    val shown = if (LocalWiis.current.spec.hints.show) hints else emptyList()
    Layout({ dock(); shown.forEach { (button, label) -> Hint(button, label) } }, modifier) { measurables, constraints ->
        val dockP = measurables[0].measure(constraints.copy(minWidth = 0))
        val width = constraints.maxWidth
        val placed = ArrayList<Pair<Placeable, Int>>()
        var x = dockP.width + DOCK_HINTS_GAP.roundToPx()
        for (m in measurables.drop(1)) {
            val p = m.measure(Constraints())
            if (x + p.width > width) break
            placed += p to x
            x += p.width + HINTS_GAP.roundToPx()
        }
        val height = maxOf(dockP.height, placed.maxOfOrNull { it.first.height } ?: 0)
        layout(width, height) {
            dockP.place(0, (height - dockP.height) / 2)
            placed.forEach { (p, px) -> p.place(px, (height - p.height) / 2) }
        }
    }
}

/** Between the dock and its first hint, and between hints. */
private val DOCK_HINTS_GAP = 18.dp
private val HINTS_GAP = 14.dp

// ---- Header ---------------------------------------------------------------------------------

@Composable
private fun HeroHeader(
    state: HomeUiState,
    status: DeviceStatus,
    dockNav: Boolean,
    actions: HeroActions,
    onFocused: (Focused) -> Unit,
    caption: (@Composable () -> Unit)?,
    nameplate: (@Composable (Modifier) -> Unit)? = null,
) {
    val style = LocalWiis.current
    val header = style.spec.header
    if (nameplate != null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (header.showWordmark) {
                    Text(header.wordmark, style = style.wordmark, maxLines = 1, softWrap = false, modifier = Modifier.lineBox(style.spec.type.wordmarkSize.sp))
                }
                Spacer(Modifier.weight(1f))
                if (header.statusPill) StatusPill(status) else ClockText()
            }
            nameplate(Modifier)
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (header.showWordmark) {
            Text(header.wordmark, style = style.wordmark, maxLines = 1, softWrap = false, modifier = Modifier.lineBox(style.spec.type.wordmarkSize.sp))
        }
        val afterWordmark = if (header.showWordmark) 14 + 10 else 0
        if (dockNav) {
            if (caption != null) {
                Box(Modifier.weight(1f).padding(start = (afterWordmark + 14).dp, end = (14 + 10).dp)) { caption() }
            } else {
                Spacer(Modifier.weight(1f))
            }
        } else {
            Spacer(Modifier.width(afterWordmark.dp))
            TabsPill(state.section, actions, onFocused)
            Spacer(Modifier.weight(1f))
        }
        if (header.statusPill) StatusPill(status) else ClockText()
    }
}

@Composable
private fun StatusPill(status: DeviceStatus) {
    val style = LocalWiis.current
    val type = style.spec.type
    val text = style.clock.copy(fontSize = (type.clockSize * 0.8f).roundToInt().sp, color = style.glassText)
    Row(
        Modifier.glassBox(style, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WifiIcon(16.dp, style.glassText, connected = status.online)
        BatteryIcon(18.dp, style.glassText, status.batteryPercent)
        status.batteryPercent?.let { Text("$it%", style = text, maxLines = 1) }
        ClockText(text)
    }
}

/** Header tabs (nav style "tabs"): a glass pill with L1 and R1 at its ends. */
@Composable
private fun TabsPill(current: Section, actions: HeroActions, onFocused: (Focused) -> Unit) {
    val style = LocalWiis.current
    val header = style.spec.header
    Row(
        Modifier.glassBox(style, CircleShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ButtonBadge("L1")
        Section.entries.forEach { s ->
            val active = s == current
            val color = if (active) style.tabActiveText else style.tabIdleText
            WiisTile(
                onClick = { actions.selectSection(s) },
                modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused(Focused.Nav(s)) },
                shape = RoundedCornerShape(min(style.spec.shape.tabRadius, 40).dp),
                fill = if (active) style.tabActiveFill else Color.Transparent,
                fillFocused = if (active) style.tabActiveFill else navFocusFill(),
                clickSound = UiEvent.Tab,
                ring = false,
                elevated = false,
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (header.tabIcons) SectionIcon(sectionIconId(s), 16.dp, color)
                    Text(sectionLabel(s, style), style = style.tab.copy(color = color), maxLines = 1)
                }
            }
        }
        ButtonBadge("R1")
    }
}

// ---- Caption --------------------------------------------------------------------------------

private data class CaptionInfo(val label: String, val title: String, val meta: String, val color: Color, val hints: List<Pair<String, String>>)

/** Names the focused item in a pill at the top centre: title, then the system or game count. */
@Composable
private fun Nameplate(info: CaptionInfo, focused: Focused, modifier: Modifier) {
    val style = LocalWiis.current
    val ty = style.spec.type
    val sub = when (focused) {
        is Focused.Nav, is Focused.DockApp, is Focused.AppTile -> ""
        else -> info.meta.ifEmpty { info.label }
    }
    val visible = style.spec.layout.focusCaption && info.title.isNotEmpty()
    Row(
        modifier
            .alpha(if (visible) 1f else 0f)
            .widthIn(max = 420.dp)
            .glassBox(style, CircleShape)
            .padding(horizontal = 20.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            info.title,
            style = TextStyle(fontFamily = style.fonts.display, fontWeight = FontWeight(ty.tabWeight.coerceIn(100, 900)), fontSize = ty.tileTitleSize.sp, color = style.glassText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).alignByBaseline().lineBox((ty.tileTitleSize * 1.2f).sp),
        )
        if (sub.isNotEmpty()) {
            Text(sub, style = style.label.copy(color = style.glassTextMuted), maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
        }
    }
}

@Composable
private fun Caption(focused: Focused, state: HomeUiState, inHeader: Boolean) {
    val style = LocalWiis.current
    val spec = style.spec
    val info = captionFor(focused, state, style)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (spec.layout.focusCaption && (info.label.isNotEmpty() || info.title.isNotEmpty())) {
                val labelColor = when (spec.shelf.titleColor) {
                    "system" -> info.color
                    "text" -> style.text
                    else -> style.textMuted
                }
                when (spec.shelf.marker) {
                    "dot" -> Box(Modifier.size(9.dp).background(info.color, CircleShape))
                    "bar" -> Box(Modifier.size(4.dp, 16.dp).background(info.color, RoundedCornerShape(2.dp)))
                }
                Text(style.shelfTitleText(info.label), style = style.shelfTitle.copy(color = labelColor), maxLines = 1, softWrap = false)
                Text(
                    info.title,
                    style = style.tileTitle.copy(color = style.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (info.meta.isNotEmpty() && !inHeader) Text(info.meta, style = style.label, maxLines = 1, softWrap = false)
            }
        }
        HintBar(info.hints, fill = false)
    }
}

private fun captionFor(f: Focused, state: HomeUiState, style: WiisStyle): CaptionInfo {
    val spec = style.spec
    fun systemName(id: String) = state.systems.firstOrNull { it.id == id }?.name ?: id
    // A, Y and X on any game; the dock leaves out whole hints from the end when they don't fit.
    fun play(game: Game) = listOf("A" to "Play", "Y" to "Emulator", "X" to if (state.isPinned(game)) "Unpin" else "Pin")
    return when (f) {
        is Focused.Hero -> CaptionInfo("Last played", f.game.title, systemName(f.game.systemId), style.systemColor(f.game.systemId), play(f.game))
        is Focused.System -> CaptionInfo("System", f.entry.name, gamesCount(f.entry.count), systemTint(f.entry.id, style), listOf("A" to "Open"))
        is Focused.Pinned -> CaptionInfo("Pinned", f.game.title, systemName(f.game.systemId), style.systemColor(f.game.systemId), play(f.game))
        is Focused.InGrid -> CaptionInfo(systemName(f.game.systemId), f.game.title, "", style.systemColor(f.game.systemId), play(f.game) + ("B" to "Back"))
        is Focused.Nav -> CaptionInfo(if (spec.nav.style == "dock") "Go to" else "Tabs", sectionLabel(f.section, style), "", style.accent, listOf("A" to "Open", "L1/R1" to "Switch"))
        is Focused.DockApp -> CaptionInfo("App", f.app.label, "", style.accent, listOf("A" to "Open", "Y" to "Change"))
        is Focused.AppTile -> CaptionInfo("App", f.app.label, "", style.accent, listOf("A" to "Open"))
        Focused.Other, Focused.None -> CaptionInfo("", "", "", style.accent, listOf("A" to "Select", "B" to "Home"))
    }
}

// ---- Body -----------------------------------------------------------------------------------

@Composable
private fun HeroBody(
    state: HomeUiState,
    actions: HeroActions,
    onFocused: (Focused) -> Unit,
    systemContent: @Composable () -> Unit,
    modifier: Modifier,
) {
    val motion = LocalWiis.current.spec.motion
    AnimatedContent(
        targetState = BodyKey(state.section, if (state.section == Section.Games) state.view else HomeView.Home),
        modifier = modifier,
        transitionSpec = {
            val ms = motion.tabTransitionMs.coerceIn(0, 2_000)
            val forward = if (targetState.section != initialState.section) {
                targetState.section.ordinal > initialState.section.ordinal
            } else {
                targetState.view is HomeView.InSystem
            }
            // As in the workbench: the new page slides in 16% and fades up; the old one goes at once.
            val enter = when {
                motion.reduceMotion || motion.tabTransition == "none" || ms == 0 -> EnterTransition.None
                motion.tabTransition == "fade" -> fadeIn(tween(ms, easing = CssEaseOut))
                else -> slideInHorizontally(tween(ms, easing = CssEaseOut)) { w -> ((if (forward) 0.16f else -0.16f) * w).roundToInt() } +
                    fadeIn(tween(ms, easing = CssEaseOut))
            }
            (enter togetherWith fadeOut(tween(0))) using SizeTransform(clip = false)
        },
        label = "heroBody",
    ) { key ->
        when (key.section) {
            Section.Games -> when (val view = key.view) {
                HomeView.Home -> GamesHome(state, actions, onFocused)
                is HomeView.InSystem -> SystemGames(view.systemId, state, actions, onFocused)
            }
            Section.Apps -> GlassApps(state.apps, actions, onFocused)
            Section.System -> Box(Modifier.fillMaxSize().onFocusEvent { if (it.hasFocus) onFocused(Focused.Other) }) { systemContent() }
        }
    }
}

@Composable
private fun GamesHome(state: HomeUiState, actions: HeroActions, onFocused: (Focused) -> Unit) {
    val other = Modifier.onFocusEvent { if (it.hasFocus) onFocused(Focused.Other) }
    when (val lib = state.library) {
        LibraryStatus.NoFolder -> Box(other) { CenterAction("Pick the folder that holds your ROMs.", "Choose ROM folder", actions::pickFolder) }
        is LibraryStatus.Scanning -> CenterText("Scanning… ${lib.found} games found")
        LibraryStatus.Ready -> {
            val hero = state.hero
            if (hero == null) {
                Box(other) {
                    CenterAction("No games found. The System screen lists the files that were skipped and why.", "Choose another folder", actions::pickFolder)
                }
            } else {
                HomePage(hero, state, actions, onFocused)
            }
        }
    }
}

/** Focus room the workbench leaves around a tile of [size] dp: growth, lift, bob and 4 dp. */
private fun room(style: WiisStyle, size: Int): Int {
    val m = style.spec.motion
    return ceil(size * (m.focusScale - 1f) / 2f).toInt() + m.focusLift + m.idleBobAmplitude + 4
}

@Composable
private fun HomePage(hero: Game, state: HomeUiState, actions: HeroActions, onFocused: (Focused) -> Unit) {
    val style = LocalWiis.current
    val spec = style.spec
    val l = spec.layout
    val gap = spec.spacing.tileGap
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val innerW = floor(maxWidth.value).toInt()
        val rows = max(1, l.systemRows)
        val cols = max(1, l.systemColumns)
        val sysH = l.systemTileHeight
        val blockH = rows * sysH + (rows - 1) * gap
        val heroW = min(l.heroWidth, innerW - 160).coerceAtLeast(120)
        val roomS = room(style, max(140, sysH))
        val gridW = (innerW - heroW - gap - roomS).coerceAtLeast(60)
        val cellW = floor((gridW - (cols - 1) * gap) / cols.toFloat()).toInt().coerceAtLeast(40)

        Column(verticalArrangement = Arrangement.spacedBy(gap.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap.dp), verticalAlignment = Alignment.Top) {
                HeroCard(hero, state.heroPlayedMillis, state.systems, heroW.dp, blockH.dp, state.heroFocus, actions, onFocused)
                SystemsGrid(state, cols, rows, gridW.dp, blockH.dp, cellW.dp, sysH.dp, roomS.dp, actions, onFocused)
            }
            if (l.showRecent) PinnedRow(state.pinned, innerW, state.heroFocus, actions, onFocused)
        }
    }
}

// ---- Jump back in card ----------------------------------------------------------------------

@Composable
private fun HeroCard(
    game: Game,
    playedMillis: Long,
    systems: List<SystemEntry>,
    width: Dp,
    height: Dp,
    target: HeroFocus?,
    actions: HeroActions,
    onFocused: (Focused) -> Unit,
) {
    val style = LocalWiis.current
    val spec = style.spec
    val radius = spec.glass.radius
    val shape = RoundedCornerShape(radius.dp)
    val color = style.systemColor(game.systemId)
    val sizePx = with(LocalDensity.current) { maxOf(width, height).roundToPx() }
    WiisTile(
        onClick = { actions.launchGame(game) },
        onSecondary = { actions.openPicker(game) },
        clickSound = null, // the launch reports Launch or Error once the outcome is known
        shape = shape,
        ringRadius = radius.dp,
        modifier = Modifier
            .size(width, height)
            .focusTarget(HomeViewModel.KEY_HERO, target, actions)
            .onFocusChanged { if (it.isFocused) onFocused(Focused.Hero(game)) },
    ) {
        Box(Modifier.fillMaxSize()) {
            Cover(game, sizePx, color)
            val meta = listOfNotNull(
                if (spec.hero.showSystem) systems.firstOrNull { it.id == game.systemId }?.name ?: game.systemId else null,
                if (spec.hero.showPlaytime && playedMillis > 0) "Played ${formatPlaytime(playedMillis)}" else null,
            ).joinToString(" · ")
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .fillMaxWidth()
                    .glassBox(style, RoundedCornerShape(max(8, radius - 8).dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    val titleSize = (spec.type.tileTitleSize * 1.2f).roundToInt()
                    Text(
                        game.title,
                        style = TextStyle(
                            fontFamily = style.fonts.display,
                            fontWeight = FontWeight(spec.type.wordmarkWeight.coerceIn(100, 900)),
                            fontSize = titleSize.sp,
                            color = style.glassText,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.lineBox((titleSize * 1.15f).sp),
                    )
                    if (meta.isNotEmpty()) {
                        // As in the workbench (white-space: nowrap), the line may run past its box rather than clip.
                        Text(
                            meta,
                            style = style.label.copy(color = style.glassTextMuted),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                PromptChip(spec.hero.prompt)
            }
            SystemRing(color, shape)
            ConsoleMark(game.systemId)
        }
    }
}

/** "(A) Jump back in": the dark chip on the card's glass panel. */
@Composable
private fun PromptChip(prompt: String) {
    val style = LocalWiis.current
    val size = style.spec.type.labelSize + 1
    Row(
        Modifier.background(style.hintBadge, CircleShape).padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).background(style.hintBadgeText, CircleShape), contentAlignment = Alignment.Center) {
            Text("A", style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, lineHeight = 11.sp, color = style.hintBadge))
        }
        Text(
            prompt,
            style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, color = style.hintBadgeText),
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ---- Systems grid ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
private class EdgeSpec(private val leadPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val before = offset - leadPx
        val after = offset + size - (containerSize - leadPx)
        return when {
            before < 0f -> before
            after > 0f -> after
            else -> 0f
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private class CenterSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - (containerSize - size) / 2f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SystemsGrid(
    state: HomeUiState,
    cols: Int,
    rows: Int,
    width: Dp,
    height: Dp,
    cellW: Dp,
    cellH: Dp,
    room: Dp,
    actions: HeroActions,
    onFocused: (Focused) -> Unit,
) {
    val style = LocalWiis.current
    val gap = style.spec.spacing.tileGap.dp
    val density = LocalDensity.current
    // Whole rows only: the grid shows [rows] rows; moving past them scrolls one whole row in. The
    // scroll area is larger by the focus room on every side so a growing tile isn't cut, but
    // nothing of a row outside the window is drawn there (not even its shadow).
    val roomPx = with(density) { room.toPx() }
    val tilePx = with(density) { cellH.roundToPx() }
    val pitch = tilePx + with(density) { gap.roundToPx() }
    val windowPx = rows * pitch - (pitch - tilePx)
    val spec = remember(roomPx) { EdgeSpec(roomPx) }
    val scroll = rememberScrollState()
    var inside by remember { mutableStateOf(false) }
    fun shown(row: Int): Boolean {
        val top = row * pitch
        return top < scroll.value + windowPx - 1 && top + tilePx > scroll.value + 1
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        Column(
            Modifier
                .size(width, height)
                .bleed(room, room)
                .onFocusChanged { inside = it.hasFocus }
                .verticalScroll(scroll)
                .padding(room),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            state.systems.chunked(cols).forEachIndexed { i, row ->
                val targeted = row.any { HomeViewModel.sysKey(it.id) == state.heroFocus?.key }
                Row(
                    Modifier
                        .graphicsLayer { alpha = if (shown(i)) 1f else 0f }
                        // From outside the grid only the rows it shows take focus, so Up from the
                        // pinned row or the dock never lands on a row out of sight.
                        .focusProperties { canFocus = inside || targeted || shown(i) },
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    row.forEach { entry -> SystemTile(entry, state.allPreview, state.systemSamples[entry.id], cellW, cellH, state.heroFocus, actions, onFocused) }
                }
            }
        }
    }
}

@Composable
private fun SystemTile(
    entry: SystemEntry,
    allPreview: List<String>,
    sample: Game?,
    w: Dp,
    h: Dp,
    target: HeroFocus?,
    actions: HeroActions,
    onFocused: (Focused) -> Unit,
) {
    val style = LocalWiis.current
    val spec = style.spec
    val st = spec.systemTile
    val g = spec.glass
    val color = systemTint(entry.id, style)
    val shape = RoundedCornerShape(g.radius.dp)
    val tint = st.tint.coerceIn(0f, 1f)
    if (st.style == "xmb") {
        XmbTile(entry, sample, w, h, target, actions, onFocused)
        return
    }
    WiisTile(
        onClick = { actions.openSystem(entry.id) },
        shape = shape,
        fill = style.glassFill,
        fillFocused = style.glassFill,
        ringRadius = g.radius.dp,
        modifier = Modifier
            .size(w, h)
            .focusTarget(HomeViewModel.sysKey(entry.id), target, actions)
            .onFocusChanged { if (it.isFocused) onFocused(Focused.System(entry)) },
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().drawBehind { drawRect(cssLinearGradient(135f, size, listOf(color.copy(alpha = tint), color.copy(alpha = tint * 0.3f)))) })
            Box(Modifier.padding(start = 12.dp, top = 10.dp)) {
                if (entry.id == HomeModel.ALL) {
                    MiniCovers(allPreview, min(h.value * 0.24f, 26f).roundToInt())
                } else if (st.style == "line") {
                    ConsoleIcon(entry.id, min(h.value * 0.46f, w.value * 0.4f).roundToInt().dp, mix(color, Color.Black, 0.3f), strokeUnits = 1.6f)
                }
            }
            if (st.showCount) {
                Text(
                    "${entry.count}",
                    style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = style.hintBadgeText),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 9.dp, end = 10.dp)
                        .background(style.hintBadge, CircleShape)
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                        .lineBox(11.sp),
                )
            }
            val nameSize = max(12, min(15, spec.type.tabSize - 3))
            Text(
                entry.name,
                style = TextStyle(
                    fontFamily = style.fonts.display,
                    fontWeight = FontWeight(spec.type.tabWeight.coerceIn(100, 900)),
                    fontSize = nameSize.sp,
                    color = style.glassText,
                ).cssLineHeight(1.1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 10.dp, bottom = 9.dp),
            )
            if (g.borderWidth > 0f) Box(Modifier.matchParentSize().border(g.borderWidth.dp, style.glassBorder, shape))
        }
    }
}

/** Desaturate and lift contrast 1.25×, like the workbench's `grayscale(1) contrast(1.25)` for faded art. */
private val FadedArt = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.266f, 0.894f, 0.09f, 0f, -32f,
            0.266f, 0.894f, 0.09f, 0f, -32f,
            0.266f, 0.894f, 0.09f, 0f, -32f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/**
 * XMB icon (as iiSU calls them): the system's own art, zoomed into its middle (past titles and
 * logos), desaturated and faint, a wash of the console colour, one bold drawing with a gradient,
 * and a gradient edge. No text: the nameplate names the focused system.
 */
@Composable
private fun XmbTile(entry: SystemEntry, sample: Game?, w: Dp, h: Dp, target: HeroFocus?, actions: HeroActions, onFocused: (Focused) -> Unit) {
    val style = LocalWiis.current
    val g = style.spec.glass
    val color = systemTint(entry.id, style)
    val shape = RoundedCornerShape(g.radius.dp)
    val tint = style.spec.systemTile.tint.coerceIn(0f, 1f)
    val px = with(LocalDensity.current) { maxOf(w, h).roundToPx() }
    val glyphColors = if (style.glassIsDark) listOf(mix(color, Color.White, 0.45f), mix(color, Color.White, 0.08f))
    else listOf(mix(color, Color.White, 0.15f), mix(color, Color.Black, 0.2f))
    WiisTile(
        onClick = { actions.openSystem(entry.id) },
        shape = shape,
        fill = style.glassFill,
        fillFocused = style.glassFill,
        ringRadius = g.radius.dp,
        modifier = Modifier
            .size(w, h)
            .focusTarget(HomeViewModel.sysKey(entry.id), target, actions)
            .onFocusChanged { if (it.isFocused) onFocused(Focused.System(entry)) },
    ) {
        Box(Modifier.fillMaxSize()) {
            rememberArt(sample, px)?.let { art ->
                Image(
                    art.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = FadedArt,
                    modifier = Modifier.matchParentSize().graphicsLayer {
                        scaleX = 2.2f
                        scaleY = 2.2f
                        transformOrigin = TransformOrigin(0.5f, 0.58f)
                        alpha = 0.16f + 0.2f * tint
                    },
                )
            }
            Box(Modifier.matchParentSize().drawBehind { drawRect(cssLinearGradient(135f, size, listOf(color.copy(alpha = 0.26f * tint), color.copy(alpha = 0.08f * tint)))) })
            ConsoleGlyph(
                entry.id, (minOf(w.value, h.value) * 0.56f).roundToInt().dp, color,
                Modifier.align(Alignment.Center), strokeUnits = 2.1f, brush = Brush.linearGradient(glyphColors),
            )
            Box(Modifier.matchParentSize().border(2.dp, Brush.linearGradient(listOf(mix(color, Color.White, 0.35f), mix(color, Color.Black, 0.1f))), shape))
        }
    }
}

/** The All games tile's four little squares, in the colours of recently played systems. */
@Composable
private fun MiniCovers(systemIds: List<String>, mini: Int) {
    val style = LocalWiis.current
    val shape = RoundedCornerShape(6.dp)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        systemIds.take(4).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                pair.forEach { id ->
                    val c = style.systemColor(id)
                    Box(
                        Modifier
                            .size(mini.dp)
                            .boxShadow(shape, 1.dp, 3.dp, Color.Black.copy(alpha = 0.25f))
                            .clip(shape)
                            .drawBehind { drawRect(cssLinearGradient(135f, size, listOf(mix(c, Color.White, 0.25f), mix(c, Color.Black, 0.35f)))) },
                    )
                }
            }
        }
    }
}

// ---- Recent row and game tiles --------------------------------------------------------------

/**
 * The bottom row: games pinned with X, newest first. Whole tiles only: as many as fit between the
 * left padding and the screen edge (less the focus room); moving past the last one scrolls one
 * whole tile in. With nothing pinned yet it shows one card saying how to pin.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedRow(games: List<Game>, innerW: Int, target: HeroFocus?, actions: HeroActions, onFocused: (Focused) -> Unit) {
    val style = LocalWiis.current
    val spec = style.spec
    val size = spec.layout.recentTileSize
    if (games.isEmpty()) {
        PinHintCard(size.dp)
        return
    }
    val pad = spec.spacing.screenPadding
    val gap = spec.spacing.tileGap
    val roomR = room(style, size)
    val fit = max(1, (innerW + pad - roomR + gap) / (size + gap))
    val windowW = fit * (size + gap) - gap
    val density = LocalDensity.current
    val padPx = with(density) { pad.dp.roundToPx() }
    val windowPx = with(density) { fit * (size.dp.roundToPx() + gap.dp.roundToPx()) - gap.dp.roundToPx() }
    val rowState = rememberLazyListState()
    var inside by remember { mutableStateOf(false) }
    LaunchedEffect(target?.token) {
        val i = target?.let { t -> games.indexOfFirst { HomeViewModel.pinKey(it) == t.key } } ?: -1
        if (i >= 0 && !rowState.itemShown(i, windowPx)) {
            rowState.scrollToItem(if (i < rowState.firstVisibleItemIndex) i else max(0, i - fit + 1))
        }
    }
    val window = remember(padPx, windowPx) { WindowSpec(padPx.toFloat(), windowPx.toFloat()) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides window) {
        LazyRow(
            state = rowState,
            // The row spans the screen edge to edge (it scrolls under the side padding); the end
            // padding makes its last scroll position a whole tile too.
            modifier = Modifier.fillMaxWidth().height(size.dp).bleed(pad.dp, roomR.dp).onFocusChanged { inside = it.hasFocus },
            contentPadding = PaddingValues(start = pad.dp, end = (innerW + 2 * pad - pad - windowW).coerceAtLeast(0).dp, top = roomR.dp, bottom = roomR.dp),
            horizontalArrangement = Arrangement.spacedBy(gap.dp),
        ) {
            itemsIndexed(games, key = { _, g -> g.uri }, contentType = { _, _ -> "game" }) { i, game ->
                val key = HomeViewModel.pinKey(game)
                ArtTile(
                    game, size, key, target, actions,
                    Modifier
                        .graphicsLayer { alpha = if (rowState.itemShown(i, windowPx)) 1f else 0f }
                        .focusProperties { canFocus = inside || target?.key == key || rowState.itemShown(i, windowPx) },
                ) { onFocused(Focused.Pinned(game)) }
            }
        }
    }
}

/** The bottom row before anything is pinned: one card that says how. */
@Composable
private fun PinHintCard(height: Dp) {
    val style = LocalWiis.current
    val spec = style.spec
    Row(
        Modifier
            .height(height)
            .glassBox(style, RoundedCornerShape(spec.glass.radius.dp))
            .padding(start = 22.dp, end = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(style.hintColor("X"), CircleShape), contentAlignment = Alignment.Center) {
            Text("X", style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 17.sp, color = style.hintBadgeText))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "Pin your games here",
                style = TextStyle(
                    fontFamily = style.fonts.display,
                    fontWeight = FontWeight(spec.type.tabWeight.coerceIn(100, 900)),
                    fontSize = spec.type.tileTitleSize.sp,
                    color = style.glassText,
                ),
                maxLines = 1,
            )
            Text("Press X on any game to add it to this row.", style = style.label.copy(color = style.glassTextMuted), maxLines = 1)
        }
    }
}

@Composable
private fun ArtTile(game: Game, size: Int, key: String, target: HeroFocus?, actions: HeroActions, modifier: Modifier = Modifier, onFocus: () -> Unit) {
    val style = LocalWiis.current
    val radius = min(style.spec.shape.tileRadius, (size / 4f).roundToInt())
    val shape = RoundedCornerShape(radius.dp)
    val color = style.systemColor(game.systemId)
    val sizePx = with(LocalDensity.current) { size.dp.roundToPx() }
    WiisTile(
        onClick = { actions.launchGame(game) },
        onSecondary = { actions.openPicker(game) },
        clickSound = null,
        shape = shape,
        ringRadius = radius.dp,
        modifier = modifier
            .size(size.dp)
            .focusTarget(key, target, actions)
            .onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Box(Modifier.fillMaxSize()) {
            Cover(game, sizePx, color, titleSize = max(11, (style.spec.type.tileTitleSize * size / 160f).roundToInt()))
            SystemRing(color, shape)
            ConsoleMark(game.systemId)
        }
    }
}

// ---- One system's games ---------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SystemGames(systemId: String, state: HomeUiState, actions: HeroActions, onFocused: (Focused) -> Unit) {
    val style = LocalWiis.current
    val spec = style.spec
    val size = spec.layout.gridTileSize
    val gap = spec.spacing.tileGap.dp
    val roomS = room(style, size)
    val games = state.viewGames
    val name = state.systems.firstOrNull { it.id == systemId }?.name ?: if (systemId == HomeModel.ALL) HomeModel.ALL_NAME else systemId
    val gridState = rememberLazyGridState()
    val target = state.heroFocus
    LaunchedEffect(target?.token) {
        val i = target?.let { t -> games.indexOfFirst { HomeViewModel.gridKey(it) == t.key } } ?: -1
        if (i >= 0) gridState.scrollToItem(i)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.height(36.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ButtonBadge("B")
            if (systemId != HomeModel.ALL) {
                Box(Modifier.size(32.dp).glass(style, CircleShape), contentAlignment = Alignment.Center) {
                    ConsoleIcon(systemId, 20.dp, mix(style.systemColor(systemId), Color.Black, 0.15f))
                }
            }
            Text(
                name,
                style = TextStyle(
                    fontFamily = style.fonts.display,
                    fontWeight = FontWeight(spec.type.wordmarkWeight.coerceIn(100, 900)),
                    fontSize = (spec.type.wordmarkSize * 0.8f).roundToInt().sp,
                    color = style.text,
                ),
                maxLines = 1,
            )
            Text(gamesCount(games.size), style = style.label, maxLines = 1)
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            WholeRowsGrid(games, { it.uri }, size.dp, size.dp, roomS.dp, gridState, Modifier, { HomeViewModel.gridKey(it) == target?.key }) { game, whole ->
                ArtTile(game, size, HomeViewModel.gridKey(game), target, actions, whole) { onFocused(Focused.InGrid(game)) }
            }
        }
    }
}

// ---- Apps -----------------------------------------------------------------------------------

@Composable
private fun GlassApps(apps: List<AppEntry>, actions: HeroActions, onFocused: (Focused) -> Unit) {
    val style = LocalWiis.current
    val spec = style.spec
    val roomA = room(style, 136)
    val first = remember { FocusRequester() }
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) {
            withFrameNanos { }
            actions.quietMoves()
            runCatching { first.requestFocus() }
        }
    }
    val shape = RoundedCornerShape(spec.glass.radius.dp)
    val gridState = rememberLazyGridState()
    // Same tile positions as the workbench, with side room so a growing tile isn't cut off.
    WholeRowsGrid(apps, { it.key }, 136.dp, 120.dp, roomA.dp, gridState, Modifier.bleed(roomA.dp, 0.dp), { false }) { app, whole ->
        WiisTile(
            onClick = { actions.launchApp(app) },
            shape = shape,
            fill = style.glassFill,
            fillFocused = style.glassFill,
            ringRadius = spec.glass.radius.dp,
            modifier = whole
                .size(136.dp, 120.dp)
                .then(if (app == apps.first()) Modifier.focusRequester(first) else Modifier)
                .onFocusChanged { if (it.isFocused) onFocused(Focused.AppTile(app)) },
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(app, 52)
                    Text(
                        app.label,
                        style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = style.glassText),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (spec.glass.borderWidth > 0f) Box(Modifier.matchParentSize().border(spec.glass.borderWidth.dp, style.glassBorder, shape))
            }
        }
    }
}

/**
 * A grid of [cellW]×[cellH] tiles that shows whole rows only: as many as fit completely, one more
 * scrolling in (a whole row) when focus moves past them. Nothing of the rows outside the window is
 * drawn, and from outside the grid only the rows it shows take focus. [tile] gets the modifier
 * that does this for each tile; [targeted] says whether the view model asked to focus an item.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> WholeRowsGrid(
    items: List<T>,
    key: (T) -> Any,
    cellW: Dp,
    cellH: Dp,
    room: Dp,
    state: LazyGridState,
    modifier: Modifier,
    targeted: (T) -> Boolean,
    tile: @Composable (T, Modifier) -> Unit,
) {
    val gap = LocalWiis.current.spec.spacing.tileGap.dp
    val density = LocalDensity.current
    var inside by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tilePx = with(density) { cellH.roundToPx() }
        val gapPx = with(density) { gap.roundToPx() }
        val roomPx = with(density) { room.roundToPx() }
        val rows = max(1, (constraints.maxHeight - 2 * roomPx + gapPx) / (tilePx + gapPx))
        val heightPx = rows * (tilePx + gapPx) - gapPx + 2 * roomPx
        val edge = remember(roomPx) { EdgeSpec(roomPx.toFloat()) }
        CompositionLocalProvider(LocalBringIntoViewSpec provides edge) {
            LazyVerticalGrid(
                state = state,
                columns = GridCells.FixedSize(cellW),
                contentPadding = PaddingValues(room),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { heightPx.toDp() })
                    .then(modifier)
                    .onFocusChanged { inside = it.hasFocus },
            ) {
                itemsIndexed(items, key = { _, item -> key(item) }) { i, item ->
                    tile(
                        item,
                        Modifier
                            .graphicsLayer { alpha = if (state.rowShown(i)) 1f else 0f }
                            .focusProperties { canFocus = inside || targeted(item) || state.rowShown(i) },
                    )
                }
            }
        }
    }
}

// ---- Navigation dock ------------------------------------------------------------------------

/** The text colour at 12%: fill of a focused, inactive navigation button (light and dark). */
@Composable
private fun navFocusFill(): Color = LocalWiis.current.text.copy(alpha = 0.12f)

/** Shoulder-button presses, so the L1/R1 badges can dip like keys. */
@Stable
private class ShoulderPresses {
    var l1 by mutableIntStateOf(0)
    var r1 by mutableIntStateOf(0)
}

/**
 * The dock's moving parts, drawn under its items: the dark pill on the current section and the
 * light highlight on the focused item. Each glides to its next place, smooth with no bounce. Every
 * new target interrupts the running glide and keeps its speed, so a target that moves while a
 * label unfolds is followed without stalling.
 */
@Stable
private class Glider(private val scope: CoroutineScope) {
    private val left = Animatable(0f)
    private val top = Animatable(0f)
    private val right = Animatable(0f)
    private val bottom = Animatable(0f)
    private val alpha = Animatable(0f)
    private var placed by mutableStateOf(false)

    /** Glides to [target]; appears in place when it was hidden, or when [glide] is off. */
    fun show(target: Rect, ms: Int, glide: Boolean) {
        val appear = !placed || alpha.value < 0.02f || !glide || ms <= 0
        val spec = smooth((ms * 0.6f).toInt())
        fun go(a: Animatable<Float, AnimationVector1D>, to: Float) =
            scope.launch(start = CoroutineStart.UNDISPATCHED) { if (appear) a.snapTo(to) else a.animateTo(to, spec) }
        go(left, target.left); go(top, target.top); go(right, target.right); go(bottom, target.bottom)
        placed = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) { alpha.animateTo(1f, if (ms <= 0) snap() else tween(ms * 2 / 3)) }
    }

    fun hide(ms: Int) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { alpha.animateTo(0f, if (ms <= 0) snap() else tween(ms * 2 / 3)) }
    }

    fun DrawScope.draw(color: Color) {
        val a = alpha.value
        if (!placed || a <= 0f) return
        val h = bottom.value - top.value
        drawRoundRect(color.copy(alpha = color.alpha * a), Offset(left.value, top.value), Size(right.value - left.value, h), CornerRadius(h / 2f))
    }
}

/** Smooth, no bounce: a critically damped spring that settles in about [ms]. */
private fun smooth(ms: Int): AnimationSpec<Float> {
    val w = 5.8f / (ms.coerceAtLeast(1) / 1000f)
    return spring(dampingRatio = 1f, stiffness = w * w)
}

@Composable
private fun NavDock(state: HomeUiState, actions: HeroActions, onFocused: (Focused) -> Unit, presses: ShoulderPresses) {
    val style = LocalWiis.current
    val nav = style.spec.nav
    val ms = if (style.spec.motion.reduceMotion) 0 else nav.motionMs.coerceIn(0, 2000)
    val density = LocalDensity.current
    val apps = state.dock.take(nav.apps.coerceAtLeast(0))

    // Where each item sits in the dock, for the pill and the highlight to glide to.
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    val items = remember { HashMap<String, LayoutCoordinates>() }
    val dock = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun measure(key: String) {
        val d = dock[0] ?: return
        val c = items[key] ?: return
        if (d.isAttached && c.isAttached) bounds[key] = d.localBoundingBoxOf(c, clipBounds = false)
    }
    fun Modifier.track(key: String) = onPlaced { items[key] = it; measure(key) }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    fun focus(key: String, focused: Boolean) { if (focused) focusedKey = key else if (focusedKey == key) focusedKey = null }

    val scope = rememberCoroutineScope()
    val pill = remember(scope) { Glider(scope) }
    val highlight = remember(scope) { Glider(scope) }
    val pillAt = bounds["nav:${state.section.name}"]
    LaunchedEffect(pillAt) { pillAt?.let { pill.show(it, ms, glide = nav.pillMotion != "jump") } }
    val halo = with(density) { 4.dp.toPx() }
    val highlightAt = focusedKey?.takeIf { nav.focusGlide }?.let { key -> bounds[key]?.let { if (key.startsWith("app:")) it.inflate(halo) else it } }
    LaunchedEffect(highlightAt) { if (highlightAt != null) highlight.show(highlightAt, ms, glide = true) else highlight.hide(ms) }

    // Rises into place whenever Home opens or comes back.
    val rise = remember { Animatable(0f) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (nav.riseIn && ms > 0) scope.launch { rise.snapTo(1f); rise.animateTo(0f, tween(ms * 6 / 5, easing = LinearOutSlowInEasing)) }
    }
    val riseBy = with(density) { 24.dp.toPx() }

    val pillColor = style.tabActiveFill
    val highlightColor = navFocusFill()
    Row(
        Modifier
            .graphicsLayer { translationY = rise.value * riseBy; alpha = 1f - rise.value }
            .glassBox(style, CircleShape)
            .onPlaced { dock[0] = it; items.keys.forEach(::measure) }
            .drawBehind {
                with(highlight) { draw(highlightColor) }
                with(pill) { draw(pillColor) }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ButtonBadge("L1", presses.l1)
        Section.entries.forEach { s ->
            val key = "nav:${s.name}"
            NavButton(s, s == state.section, state.heroFocus, actions, onFocused, ms, Modifier.track(key)) { focus(key, it) }
        }
        ButtonBadge("R1", presses.r1)
        if (apps.isNotEmpty()) Box(Modifier.padding(horizontal = 4.dp).size(1.dp, 28.dp).background(style.glassText.copy(alpha = 0.18f)))
        apps.forEachIndexed { slot, app ->
            val key = "app:$slot"
            WiisTile(
                onClick = { actions.launchApp(app) },
                onSecondary = { actions.openDockPicker(slot) },
                shape = CircleShape,
                fill = Color.Transparent,
                fillFocused = if (nav.focusGlide) Color.Transparent else navFocusFill(),
                ring = false,
                elevated = false,
                grow = false,
                modifier = Modifier
                    .size(40.dp)
                    .track(key)
                    .focusTarget(HomeViewModel.dockKey(slot), state.heroFocus, actions)
                    .onFocusChanged {
                        focus(key, it.isFocused)
                        if (it.isFocused) onFocused(Focused.DockApp(app))
                    },
            ) {
                AppIcon(app, 40)
            }
        }
    }
}

@Composable
private fun NavButton(
    section: Section,
    active: Boolean,
    target: HeroFocus?,
    actions: HeroActions,
    onFocused: (Focused) -> Unit,
    ms: Int,
    modifier: Modifier,
    onFocus: (Boolean) -> Unit,
) {
    val style = LocalWiis.current
    val nav = style.spec.nav
    val showLabel = nav.labels == "all" || (nav.labels == "active" && active)
    val color by animateColorAsState(if (active) style.tabActiveText else style.tabIdleText, tween(ms), label = "navText")
    val pad by animateDpAsState(if (showLabel) 14.dp else 10.dp, tween(ms), label = "navPad")
    WiisTile(
        onClick = { actions.selectSection(section) },
        shape = CircleShape,
        // The dock draws the dark pill and the light highlight; they glide between buttons.
        fill = Color.Transparent,
        fillFocused = if (nav.focusGlide || active) Color.Transparent else navFocusFill(),
        clickSound = UiEvent.Tab,
        ring = false,
        elevated = false,
        grow = false,
        modifier = modifier
            .focusTarget(HomeViewModel.navKey(section), target, actions)
            .onFocusChanged {
                onFocus(it.isFocused)
                if (it.isFocused) onFocused(Focused.Nav(section))
            },
    ) {
        Row(Modifier.padding(horizontal = pad, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionIcon(sectionIconId(section), 18.dp, color)
            // The label folds away on the old section and unfolds on the new one.
            AnimatedVisibility(
                visible = showLabel,
                enter = fadeIn(tween(ms)) + expandHorizontally(tween(ms), expandFrom = Alignment.Start),
                exit = fadeOut(tween(ms * 2 / 3)) + shrinkHorizontally(tween(ms), shrinkTowards = Alignment.Start),
            ) {
                Text(sectionLabel(section, style), style = style.tab.copy(color = color), maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 7.dp))
            }
        }
    }
}

/** The small dark key badges: L1, R1, B. L1 and R1 dip like keys when pressed ([presses] counts them). */
@Composable
private fun ButtonBadge(label: String, presses: Int = 0) {
    val style = LocalWiis.current
    val dip = remember { Animatable(0f) }
    val on = style.spec.nav.badgePress && !style.spec.motion.reduceMotion
    LaunchedEffect(presses) {
        if (presses > 0 && on) {
            dip.animateTo(1f, tween(70))
            dip.animateTo(0f, tween(150))
        }
    }
    Text(
        label,
        style = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, color = style.hintBadgeText),
        maxLines = 1,
        modifier = Modifier
            .graphicsLayer {
                val s = 1f - 0.16f * dip.value
                scaleX = s
                scaleY = s
                translationY = dip.value * 1.5.dp.toPx()
            }
            .background(style.hintBadge, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 4.dp).lineBox(10.sp),
    )
}

// ---- Helpers --------------------------------------------------------------------------------

private fun sectionIconId(section: Section) = when (section) {
    Section.Games -> "games"
    Section.Apps -> "apps"
    Section.System -> "system"
}

private fun sectionLabel(section: Section, style: WiisStyle) = when (section) {
    Section.Games -> style.spec.header.tabGames
    Section.Apps -> style.spec.header.tabApps
    Section.System -> style.spec.header.tabSystem
}

private fun systemTint(id: String, style: WiisStyle): Color = if (id == HomeModel.ALL) style.accent else style.systemColor(id)

private fun gamesCount(n: Int) = if (n == 1) "1 game" else "$n games"

private fun HomeUiState.isPinned(game: Game) = pinned.any { it.uri == game.uri }

private fun gameOf(f: Focused): Game? = when (f) {
    is Focused.Hero -> f.game
    is Focused.Pinned -> f.game
    is Focused.InGrid -> f.game
    else -> null
}

/**
 * Takes focus when the view model names [key] (a new [HeroFocus.token] each time). Programmatic
 * focus is quiet: the move sound is for the player's own moves.
 */
@Composable
private fun Modifier.focusTarget(key: String, target: HeroFocus?, actions: HeroActions): Modifier {
    val requester = remember { FocusRequester() }
    if (target != null && target.key == key) {
        LaunchedEffect(target.token) {
            withFrameNanos { } // wait until laid out
            actions.quietMoves()
            runCatching { requester.requestFocus() }
            actions.focusApplied()
        }
    }
    return focusRequester(requester)
}
