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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import app.wiis.launcher.system.SettingsPanel
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
import app.wiis.launcher.ui.CoverMosaic
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
    fun openSystem(id: String)
    fun closeView(): Boolean
    fun openCollection(id: String)
    fun focusCollection(entry: CollectionEntry)
    fun keepCollection(id: String)
    fun renameCollection(id: String, name: String)
    fun hideCollection(id: String)
    fun addToCollection(id: String, game: Game)
    fun removeFromCollection(id: String, game: Game)
    fun createCollection(name: String, game: Game)
    fun openSettings(page: String?)
    fun closeSettings()
    fun settingsPage(page: String)
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

    // Below Home. Defaults keep the screenshot tests' stubs short.
    fun launchFrom(game: Game, key: String) = launchGame(game)
    fun openCollectionFrom(id: String, key: String) = openCollection(id)
    fun focusBoardTile(tile: BoardTile) = Unit
    fun openShortcut(ref: String) = Unit
    fun shuffleRandom() = Unit
    fun boardPickUp(id: Int, sticky: Boolean) = Unit
    fun boardStep(dx: Int, dy: Int): Boolean = false
    fun boardDrop() = Unit
    fun boardEndEdit() = Unit
    fun boardResize(id: Int): Boolean = false
    fun boardRemove(id: Int) = Unit
    fun boardAdd(kind: String, ref: String, before: Int?, at: Pair<Int, Int>?): Boolean = false
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
            override fun openSystem(id: String) = vm.openSystem(id)
            override fun closeView() = vm.closeView()
            override fun openCollection(id: String) = vm.openCollection(id)
            override fun focusCollection(entry: CollectionEntry) = vm.focusCollection(entry)
            override fun keepCollection(id: String) = vm.keepCollection(id)
            override fun renameCollection(id: String, name: String) = vm.renameCollection(id, name)
            override fun hideCollection(id: String) = vm.hideCollection(id)
            override fun addToCollection(id: String, game: Game) = vm.addToCollection(id, game)
            override fun removeFromCollection(id: String, game: Game) = vm.removeFromCollection(id, game)
            override fun createCollection(name: String, game: Game) = vm.createCollection(name, game)
            override fun openSettings(page: String?) = vm.openSettings(page)
            override fun closeSettings() = vm.closeSettings()
            override fun settingsPage(page: String) = vm.settingsPage(page)
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
            override fun launchFrom(game: Game, key: String) = vm.launchFrom(game, key)
            override fun openCollectionFrom(id: String, key: String) = vm.openCollection(id, back = key)
            override fun focusBoardTile(tile: BoardTile) = vm.focusBoardTile(tile)
            override fun openShortcut(ref: String) = vm.openShortcut(ref)
            override fun shuffleRandom() = vm.shuffleRandom()
            override fun boardPickUp(id: Int, sticky: Boolean) = vm.boardPickUp(id, sticky)
            override fun boardStep(dx: Int, dy: Int) = vm.boardStep(dx, dy)
            override fun boardDrop() = vm.boardDrop()
            override fun boardEndEdit() = vm.boardEndEdit()
            override fun boardResize(id: Int) = vm.boardResize(id)
            override fun boardRemove(id: Int) = vm.boardRemove(id)
            override fun boardAdd(kind: String, ref: String, before: Int?, at: Pair<Int, Int>?) = vm.boardAdd(kind, ref, before, at)
        }
    }
    HeroHomeScreen(state, status, actions) { page, height -> SettingsPanel(page, vm::settingsPage, onPickRomFolder = onPickFolder, height = height) }

    state.picker?.let { p -> EmulatorPickerDialog(p, onPick = { vm.launchGame(p.game, it) }, onDismiss = vm::dismissPicker) }
    state.dockPicker?.let { p -> DockPickerDialog(p, state.dock.getOrNull(p.slot), onPick = vm::chooseDockApp, onDismiss = vm::dismissDockPicker) }
}

/** What has focus, as far as the caption and the background care. */
internal sealed interface Focused {
    data object None : Focused
    data class Hero(val game: Game) : Focused
    data class System(val entry: SystemEntry) : Focused
    data class Pinned(val game: Game) : Focused
    data class InGrid(val game: Game) : Focused
    data class Nav(val section: Section) : Focused
    data class DockApp(val app: AppEntry) : Focused
    data class AppTile(val app: AppEntry) : Focused
    data class Collection(val entry: CollectionEntry) : Focused
    /** A tile below Home, with what it shows. */
    data class Tile(val tile: BoardTile, val game: Game? = null, val app: AppEntry? = null, val collection: CollectionEntry? = null, val title: String = "") : Focused
    /** An empty cell below Home while arranging ("+"). */
    data class Cell(val col: Int, val row: Int) : Focused
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
    settingsContent: @Composable (page: String, height: Dp) -> Unit,
) {
    val style = LocalWiis.current
    val spec = style.spec
    val feedback = LocalUiFeedback.current
    // Popups over the screen: the Select menu, Add to collection, naming a collection. The settings
    // popup is the view model's (Start, or the bottom screen asking for a page).
    var menu by remember { mutableStateOf<Focused?>(null) }
    var picking by remember { mutableStateOf<Game?>(null) }
    var naming by remember { mutableStateOf<Naming?>(null) }
    var adding by remember { mutableStateOf<AddTarget?>(null) }
    val popup = menu != null || picking != null || naming != null || adding != null || state.settings != null
    val content = remember { FocusRequester() }
    var contentFocused by remember { mutableStateOf(false) }
    // Focus goes back where it was when the last popup closes, unless something took it already
    // (a tile just added, the neighbour of one just removed).
    var hadPopup by remember { mutableStateOf(false) }
    LaunchedEffect(popup) {
        if (popup) { hadPopup = true; return@LaunchedEffect }
        if (!hadPopup) return@LaunchedEffect
        withFrameNanos { }
        if (contentFocused) return@LaunchedEffect
        if (!content.restoreFocusedChild()) runCatching { content.requestFocus() }
    }
    fun openPopup(open: () -> Unit) {
        if (!popup) content.saveFocusedChild()
        open()
    }

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
            is Focused.Collection -> { subject = f.entry.games.firstOrNull()?.let { BgSubject.OfGame(it) }; actions.focusCollection(f.entry) }
            is Focused.Tile -> {
                subject = (f.game ?: f.collection?.games?.firstOrNull())?.let { BgSubject.OfGame(it) }
                actions.focusBoardTile(f.tile)
            }
            is Focused.Cell -> { subject = null; actions.focusBoardTile(BoardTile(0, Board.GAP)) }
            else -> Unit
        }
    }

    BackHandler(enabled = !popup && (state.view != HomeView.Home || state.section != Section.Games)) {
        feedback.emit(UiEvent.Back)
        if (!actions.closeView()) actions.selectSection(Section.Games)
    }

    val pad = spec.spacing.screenPadding
    val dockNav = spec.nav.style == "dock"
    val nameplate = dockNav && spec.nav.captionPlace == "nameplate"
    val captionInHeader = dockNav && spec.nav.captionPlace == "header"
    val info = captionFor(focused, state, style)
    val topPad = (pad * 0.6f).roundToInt()
    val presses = remember { ShoulderPresses() }
    val nav = remember { VerticalNav() }
    val editing = state.board.edit
    SideEffect {
        // The element the view model names may take focus on the page that isn't showing.
        nav.targetKey = state.heroFocus?.key
        // Arranging ends when focus leaves the page below Home (to Home or the dock).
        nav.onFocus = { spot -> if (spot.page != 1 && editing != null) actions.boardEndEdit() }
    }

    CompositionLocalProvider(LocalVerticalNav provides nav) {
    Box(
        Modifier
            .fillMaxSize()
            // Global buttons work from anywhere on the top screen.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (popup) {
                    // In a popup: Start toggles the settings, Select closes the menu; the rest stays inside.
                    return@onPreviewKeyEvent when (e.key) {
                        Key.ButtonStart -> { feedback.emit(UiEvent.Tab); menu = null; picking = null; naming = null; adding = null; actions.onStart(); true }
                        Key.ButtonSelect -> { if (menu != null) { feedback.emit(UiEvent.Back); menu = null }; true }
                        Key.ButtonL1, Key.ButtonR1, Key.ButtonX -> true
                        else -> false
                    }
                }
                // While a tile is carried, the page below Home has the buttons; Select and X wait.
                if (editing?.carrying != null && (e.key == Key.ButtonSelect || e.key == Key.ButtonX)) return@onPreviewKeyEvent true
                if (editing != null && e.key in listOf(Key.ButtonL1, Key.ButtonR1, Key.ButtonStart)) actions.boardEndEdit()
                when (e.key) {
                    Key.ButtonL1 -> { presses.l1++; feedback.emit(UiEvent.Tab); actions.cycleSection(-1); true }
                    Key.ButtonR1 -> { presses.r1++; feedback.emit(UiEvent.Tab); actions.cycleSection(+1); true }
                    Key.ButtonStart -> { feedback.emit(UiEvent.Tab); openPopup { actions.onStart() }; true }
                    // Select: what you can do with the highlighted item, and the settings at the bottom.
                    Key.ButtonSelect -> {
                        feedback.emit(UiEvent.Select)
                        when (focused) {
                            is Focused.Nav, Focused.Other, Focused.None -> openPopup { actions.openSettings(null) }
                            else -> openPopup { menu = focused }
                        }
                        true
                    }
                    // X pins the highlighted game to the bottom row, or unpins it (not while arranging).
                    Key.ButtonX -> {
                        val game = gameOf(focused)
                        if (editing != null) true else if (game == null || state.section != Section.Games) false else {
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
                .padding(start = pad.dp, end = pad.dp, top = topPad.dp, bottom = (if (dockNav) 10 else topPad).dp)
                .focusRequester(content)
                .onFocusChanged { contentFocused = it.hasFocus }
                .focusGroup(),
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
            HeroBody(
                state, actions, onFocused,
                onAdd = { target -> openPopup { adding = target } },
                onSettings = { page -> openPopup { actions.openSettings(page) } },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
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

        menu?.let { target ->
            val (title, subtitle) = menuTitle(target, state)
            QuickMenu(
                title, subtitle,
                menuFor(
                    target, state, actions,
                    close = { menu = null },
                    pick = { g -> menu = null; picking = g },
                    name = { n -> menu = null; naming = n },
                    pin = { g -> feedback.emit(if (state.isPinned(g)) UiEvent.Back else UiEvent.Select); actions.togglePin(g, fromRow = target is Focused.Pinned) },
                    add = { t -> menu = null; adding = t },
                    error = { feedback.emit(UiEvent.Error) },
                ),
                onDismiss = { feedback.emit(UiEvent.Back); menu = null },
            )
        }
        picking?.let { game ->
            CollectionPicker(
                game.title,
                state.collections,
                onPick = { c -> feedback.emit(UiEvent.Select); actions.addToCollection(c.id, game); picking = null },
                onNew = { picking = null; naming = Naming("New collection", "") { name -> actions.createCollection(name, game) } },
                onDismiss = { feedback.emit(UiEvent.Back); picking = null },
            )
        }
        naming?.let { n ->
            NameEntry(n.title, n.initial, onSave = { name -> feedback.emit(UiEvent.Select); n.save(name); naming = null }, onDismiss = { feedback.emit(UiEvent.Back); naming = null })
        }
        adding?.let { target ->
            AddTilePicker(
                state,
                onPick = { kind, ref -> feedback.emit(UiEvent.Select); adding = null; actions.boardAdd(kind, ref, target.before, target.at) },
                onDismiss = { feedback.emit(UiEvent.Back); adding = null },
            )
        }
        state.settings?.let { page ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val height = (maxHeight.value - 56 - 36).toInt().coerceAtLeast(240)
                PopupLayer(onDismiss = { feedback.emit(UiEvent.Back); actions.closeSettings() }, width = (maxWidth - 48.dp).coerceAtMost(800.dp)) {
                    settingsContent(page, height.dp)
                }
            }
        }
    }
    }
}

/** Naming a collection: the popup's title, the name to start from, and what keeps it. */
private class Naming(val title: String, val initial: String, val save: (String) -> Unit)

private fun menuTitle(f: Focused, state: HomeUiState): Pair<String, String> {
    fun systemName(id: String) = state.systems.firstOrNull { it.id == id }?.name ?: id
    return when (f) {
        is Focused.Hero -> f.game.title to systemName(f.game.systemId)
        is Focused.Pinned -> f.game.title to systemName(f.game.systemId)
        is Focused.InGrid -> f.game.title to systemName(f.game.systemId)
        is Focused.System -> f.entry.name to gamesCount(f.entry.count)
        is Focused.Collection -> f.entry.name to (gamesCount(f.entry.games.size) + if (f.entry.suggested) " · suggested" else "")
        is Focused.DockApp -> f.app.label to "App in the dock"
        is Focused.AppTile -> f.app.label to "App"
        is Focused.Tile -> f.title.ifEmpty { tileKindName(f.tile) } to "Tile below Home · ${sizeLabel(f.tile.size)}"
        is Focused.Cell -> "Empty space" to "Below Home"
        else -> "Wii-S" to ""
    }
}

/** What Select offers for [f], with the button that does it directly; "Wii-S settings" always last. */
private fun menuFor(
    f: Focused,
    state: HomeUiState,
    actions: HeroActions,
    close: () -> Unit,
    pick: (Game) -> Unit,
    name: (Naming) -> Unit,
    pin: (Game) -> Unit,
    add: (AddTarget) -> Unit,
    error: () -> Unit,
): List<MenuEntry> {
    val game = gameOf(f)
    val random = f is Focused.Tile && f.tile.kind == Board.RANDOM
    val inCollection = (state.view as? HomeView.InCollection)?.let { v -> state.collections.firstOrNull { it.id == v.id } }
    val items = buildList {
        if (game != null) {
            add(MenuEntry(if (state.isPinned(game)) "Unpin from Home" else "Pin to Home", "X") { close(); pin(game) })
            add(MenuEntry("Add to collection…") { pick(game) })
            if (inCollection != null && f is Focused.InGrid) add(MenuEntry("Remove from ${inCollection.name}") { close(); actions.removeFromCollection(inCollection.id, game) })
            add(MenuEntry("Emulator…", if (random) null else "Y") { close(); actions.openPicker(game) })
            if (random) add(MenuEntry("Another game", "Y") { actions.shuffleRandom() })
        }
        when (f) {
            is Focused.System -> add(MenuEntry("Open", "A") { close(); actions.openSystem(f.entry.id) })
            is Focused.Collection -> {
                val c = f.entry
                add(MenuEntry("Open", "A") { close(); actions.openCollection(c.id) })
                if (c.suggested) add(MenuEntry("Keep") { close(); actions.keepCollection(c.id) })
                add(MenuEntry("Rename…") { name(Naming("Rename collection", c.name) { n -> actions.renameCollection(c.id, n) }) })
                add(MenuEntry(if (c.id.startsWith("s:")) "Hide" else "Delete") { close(); actions.hideCollection(c.id) })
            }
            is Focused.DockApp -> {
                add(MenuEntry("Open", "A") { close(); actions.launchApp(f.app) })
                val slot = state.dock.indexOf(f.app)
                if (slot >= 0) add(MenuEntry("Change app…", "Y") { close(); actions.openDockPicker(slot) })
            }
            is Focused.AppTile -> add(MenuEntry("Open", "A") { close(); actions.launchApp(f.app) })
            is Focused.Tile -> {
                // The tile as it is now (the size changes while the menu is open).
                val t = state.board.tiles.firstOrNull { it.id == f.tile.id && !Board.isGap(it) } ?: f.tile
                when (t.kind) {
                    Board.APP -> f.app?.let { a -> add(MenuEntry("Open", "A") { close(); actions.launchApp(a) }) }
                    Board.COLLECTION -> f.collection?.let { c -> add(MenuEntry("Open", "A") { close(); actions.openCollectionFrom(c.id, HomeViewModel.boardKey(t.id)) }) }
                    Board.SHORTCUT -> add(MenuEntry("Open", "A") { close(); actions.openShortcut(t.ref) })
                    Board.ACHIEVEMENTS -> add(MenuEntry("RetroAchievements settings", if (state.board.achievements is RecentUnlocks.NotSetUp) "A" else null) { close(); actions.openSettings("ra") })
                }
                add(MenuEntry("Move", lineAbove = isNotEmpty()) { close(); actions.boardPickUp(t.id, sticky = false) })
                if (Board.sizesFor(t.kind).size > 1 || t.size !in Board.sizesFor(t.kind)) {
                    add(MenuEntry("Size: ${sizeLabel(t.size)}") { if (!actions.boardResize(t.id)) error() })
                }
                add(MenuEntry("Remove") { close(); actions.boardRemove(t.id) })
                add(MenuEntry("Add a tile here…") { add(AddTarget(before = t.id)) })
            }
            is Focused.Cell -> add(MenuEntry("Add a tile here…", "A") { add(AddTarget(at = f.col to f.row)) })
            else -> Unit
        }
    }
    return items + MenuEntry("Wii-S settings", "Start", lineAbove = items.isNotEmpty()) { close(); actions.openSettings(null) }
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
        is Focused.AppTile -> CaptionInfo("App", f.app.label, "", style.accent, listOf("A" to "Open", "Select" to "Menu"))
        is Focused.Collection -> CaptionInfo(
            "Collection", f.entry.name, gamesCount(f.entry.games.size) + if (f.entry.suggested) " · suggested" else "",
            style.accent, listOf("A" to "Open", "Select" to "Menu"),
        )
        is Focused.Tile -> tileCaption(f, state, style)
        is Focused.Cell -> CaptionInfo(
            "Below Home", if (f.col < 0) "This page is empty" else "Empty space", "", style.accent,
            if (state.board.edit?.sticky == true) listOf("A" to "Add", "B" to "Done") else listOf("A" to "Add"),
        )
        Focused.Other, Focused.None -> CaptionInfo("", "", "", style.accent, listOf("A" to "Select", "B" to "Home"))
    }
}

/** A tile below Home: what it is and what the buttons do (while arranging, how to arrange). */
private fun tileCaption(f: Focused.Tile, state: HomeUiState, style: WiisStyle): CaptionInfo {
    val t = f.tile
    val edit = state.board.edit
    val color = f.game?.let { style.systemColor(it.systemId) } ?: style.accent
    val meta = when {
        f.game != null -> state.systems.firstOrNull { it.id == f.game.systemId }?.name ?: f.game.systemId
        f.collection != null -> gamesCount(f.collection.games.size)
        t.kind == Board.WEEK -> state.board.week.sum().let { if (it > 0) formatPlaytime(it) else "Nothing yet" }
        t.kind == Board.ACHIEVEMENTS -> when (val a = state.board.achievements) {
            is RecentUnlocks.Ready -> if (a.items.isEmpty()) "None lately" else "${a.items.size} lately"
            RecentUnlocks.NotSetUp -> "Not signed in"
            else -> ""
        }
        else -> ""
    }
    val hints = when {
        edit?.carrying == t.id -> listOf("D-pad" to "Move", "Y" to "Size", "A" to "Drop")
        edit?.sticky == true -> listOf("A" to "Pick up", "Y" to "Size", "B" to "Done")
        else -> buildList {
            when (t.kind) {
                Board.GAME, Board.LAST_PLAYED -> if (f.game != null) { add("A" to "Play"); add("Y" to "Emulator"); add("X" to if (state.isPinned(f.game)) "Unpin" else "Pin") }
                Board.RANDOM -> if (f.game != null) { add("A" to "Play"); add("Y" to "Another") }
                Board.APP, Board.COLLECTION, Board.SHORTCUT -> if (f.app != null || f.collection != null || t.kind == Board.SHORTCUT) add("A" to "Open")
                Board.ACHIEVEMENTS -> if (state.board.achievements is RecentUnlocks.NotSetUp) add("A" to "Set up")
            }
            add("Hold A" to "Arrange")
            add("Select" to "Menu")
        }
    }
    // The nameplate shows the meta, else the label: a widget's label isn't its own name again.
    val label = if (t.kind in Board.WIDGETS && t.kind != Board.LAST_PLAYED && t.kind != Board.RANDOM) "Widget" else tileKindName(t)
    return CaptionInfo(label, f.title.ifEmpty { tileKindName(t) }, meta, color, hints)
}

/** What a tile below Home is, in a word or two. */
internal fun tileKindName(t: BoardTile): String = when (t.kind) {
    Board.APP -> "App"
    Board.GAME -> "Game"
    Board.COLLECTION -> "Collection"
    Board.SHORTCUT -> if (t.ref.startsWith("emu:")) "Emulator" else "Shortcut"
    Board.CLOCK -> "Clock"
    Board.LAST_PLAYED -> "Last played"
    Board.WEEK -> "This week"
    Board.ACHIEVEMENTS -> "Recent achievements"
    Board.RANDOM -> "Random game"
    else -> "Tile"
}

internal fun sizeLabel(size: String) = when (size) {
    "2x1" -> "2 × 1"
    "2x2" -> "2 × 2"
    else -> "1 × 1"
}

// ---- Body -----------------------------------------------------------------------------------

@Composable
private fun HeroBody(
    state: HomeUiState,
    actions: HeroActions,
    onFocused: (Focused) -> Unit,
    onAdd: (AddTarget) -> Unit,
    onSettings: (String) -> Unit,
    modifier: Modifier,
) {
    val motion = LocalWiis.current.spec.motion
    AnimatedContent(
        targetState = BodyKey(state.section, if (state.section == Section.Apps) HomeView.Home else state.view),
        modifier = modifier,
        transitionSpec = {
            val ms = motion.tabTransitionMs.coerceIn(0, 2_000)
            val forward = if (targetState.section != initialState.section) {
                targetState.section.ordinal > initialState.section.ordinal
            } else {
                targetState.view != HomeView.Home
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
                is HomeView.InSystem -> SystemGames(view.systemId, state, actions, onFocused)
                // A collection opened from its tile below Home.
                is HomeView.InCollection -> CollectionGames(view.id, state, actions, onFocused)
                else -> GamesHome(state, actions, onFocused, onAdd, onSettings)
            }
            Section.Apps -> GlassApps(state.apps, actions, onFocused)
            Section.Collections -> when (val view = key.view) {
                is HomeView.InCollection -> CollectionGames(view.id, state, actions, onFocused)
                else -> CollectionsHome(state, actions, onFocused)
            }
        }
    }
}

@Composable
private fun GamesHome(state: HomeUiState, actions: HeroActions, onFocused: (Focused) -> Unit, onAdd: (AddTarget) -> Unit, onSettings: (String) -> Unit) {
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
                val target = state.heroFocus?.key.orEmpty()
                TwoPages(
                    startBelow = target.startsWith("board:") || target.startsWith("cell:"),
                    home = { HomePage(hero, state, actions, onFocused) },
                    below = { BoardPage(state, actions, onFocused, onAdd, onSettings) },
                )
            }
        }
    }
}

/** Between Home and the page below while they slide: never seen at rest. */
private val PAGE_GAP = 32.dp

/**
 * Home and the page below it, one whole page each. Down from Home's bottom row slides Home up and
 * the page below in; Up from its top row slides them back. The page that isn't showing isn't
 * drawn; while they slide, nothing is drawn past the body and the focus room around it. Two dots
 * at the right edge say which page shows.
 */
@Composable
private fun TwoPages(startBelow: Boolean, home: @Composable () -> Unit, below: @Composable () -> Unit) {
    val nav = LocalVerticalNav.current
    val style = LocalWiis.current
    val spec = style.spec
    val ms = if (spec.motion.reduceMotion) 0 else spec.motion.pageSlideMs.coerceIn(0, 2_000)
    val pages = remember { PageState(if (startBelow) 1 else 0) }
    DisposableEffect(nav, pages) {
        nav?.pages = pages
        onDispose { if (nav?.pages === pages) nav.pages = null }
    }
    val slide = remember { Animatable(pages.page.toFloat()) }
    LaunchedEffect(pages.page) {
        val to = pages.page.toFloat()
        if (ms == 0) slide.snapTo(to) else slide.animateTo(to, smooth(ms))
    }
    val density = LocalDensity.current
    val gapPx = with(density) { PAGE_GAP.roundToPx() }
    val sidePx = with(density) { spec.spacing.screenPadding.dp.toPx() }
    // While sliding, a little room above and below for the focused tile, but clear of the header and the dock.
    val roomPx = with(density) { 8.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        Layout(
            content = {
                Box(Modifier.graphicsLayer { alpha = if (slide.value < 0.999f) 1f else 0f }) { home() }
                Box(Modifier.graphicsLayer { alpha = if (slide.value > 0.001f) 1f else 0f }) { below() }
            },
            modifier = Modifier.fillMaxSize().drawWithContent {
                if (slide.isRunning) clipRect(-sidePx, -roomPx, size.width + sidePx, size.height + roomPx) { this@drawWithContent.drawContent() } else drawContent()
            },
        ) { measurables, constraints ->
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val fixed = Constraints.fixed(w, h)
            val a = measurables[0].measure(fixed)
            val b = measurables[1].measure(fixed)
            layout(w, h) {
                val step = h + gapPx
                val off = (slide.value * step).roundToInt()
                a.place(0, -off)
                b.place(0, step - off)
            }
        }
        if (spec.layout.pageDots) PageDots(slide.value, Modifier.align(Alignment.CenterEnd).offset(x = ((spec.spacing.screenPadding + 6) / 2f).dp))
    }
}

/** Which page shows, as two dots on top of each other; the dot fills as its page slides in. */
@Composable
private fun PageDots(progress: Float, modifier: Modifier) {
    val color = LocalWiis.current.text
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1f - progress, progress).forEach { on ->
            Box(Modifier.size(6.dp).background(color.copy(alpha = 0.22f + 0.5f * on.coerceIn(0f, 1f)), CircleShape))
        }
    }
}

/** Focus room the workbench leaves around a tile of [size] dp: growth, lift, bob and 4 dp. */
internal fun room(style: WiisStyle, size: Int): Int {
    val m = style.spec.motion
    return ceil(size * (m.focusScale - 1f) / 2f).toInt() + m.focusLift + m.idleBobAmplitude + 4
}

/**
 * Cover size inside a system: the theme's (layout.gridTileSize), or smaller so that two whole rows
 * and their focus room fit in [heightPx]. 132 dp covers leave room for one row on the Thor.
 */
private fun twoRowCover(style: WiisStyle, heightPx: Int, density: Float): Int {
    fun px(dp: Int) = (dp * density).roundToInt()
    val gap = style.spec.spacing.tileGap
    var size = style.spec.layout.gridTileSize
    while (size > 72 && 2 * px(size) + px(gap) + 2 * px(room(style, size)) > heightPx) size--
    return size
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
            .navSpot(HomeViewModel.KEY_HERO, "hero", page = 0)
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
                val enabled = { inside || targeted || shown(i) }
                Row(
                    Modifier.graphicsLayer { alpha = if (shown(i)) 1f else 0f },
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    row.forEachIndexed { c, entry ->
                        // From outside the grid only the rows it shows take focus, so Up from the
                        // pinned row or the dock never lands on a row out of sight.
                        val spot = Modifier.navSpot(HomeViewModel.sysKey(entry.id), "systems", enabled, page = 0) { i to c }
                        SystemTile(entry, state.allPreview, state.systemSamples[entry.id], cellW, cellH, state.heroFocus, actions, onFocused, spot)
                    }
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
    modifier: Modifier = Modifier,
) {
    val style = LocalWiis.current
    val spec = style.spec
    val st = spec.systemTile
    val g = spec.glass
    val color = systemTint(entry.id, style)
    val shape = RoundedCornerShape(g.radius.dp)
    val tint = st.tint.coerceIn(0f, 1f)
    if (st.style == "xmb") {
        XmbTile(entry, sample, w, h, target, actions, onFocused, modifier)
        return
    }
    WiisTile(
        onClick = { actions.openSystem(entry.id) },
        shape = shape,
        fill = style.glassFill,
        fillFocused = style.glassFill,
        ringRadius = g.radius.dp,
        modifier = modifier
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
private fun XmbTile(entry: SystemEntry, sample: Game?, w: Dp, h: Dp, target: HeroFocus?, actions: HeroActions, onFocused: (Focused) -> Unit, modifier: Modifier = Modifier) {
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
        modifier = modifier
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
                val enabled = { inside || target?.key == key || rowState.itemShown(i, windowPx) }
                ArtTile(
                    game, size, key, target, actions,
                    Modifier
                        .graphicsLayer { alpha = if (rowState.itemShown(i, windowPx)) 1f else 0f }
                        .navSpot(key, "pins", enabled, page = 0),
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
    val games = state.viewGames
    val name = state.systems.firstOrNull { it.id == systemId }?.name ?: if (systemId == HomeModel.ALL) HomeModel.ALL_NAME else systemId
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
        GamesGrid(games, state, actions, onFocused)
    }
}

/** A collection's games, like a system's: the two-row grid, B back to its card. */
@Composable
private fun CollectionGames(id: String, state: HomeUiState, actions: HeroActions, onFocused: (Focused) -> Unit) {
    val style = LocalWiis.current
    val spec = style.spec
    val entry = state.collections.firstOrNull { it.id == id }
    val games = state.viewGames
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.height(36.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ButtonBadge("B")
            Box(Modifier.size(32.dp).glass(style, CircleShape), contentAlignment = Alignment.Center) {
                SectionIcon("collections", 20.dp, style.glassText)
            }
            Text(
                entry?.name.orEmpty(),
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
        GamesGrid(games, state, actions, onFocused)
    }
}

/** The two-row grid of covers under a system's or a collection's header. */
@Composable
private fun ColumnScope.GamesGrid(games: List<Game>, state: HomeUiState, actions: HeroActions, onFocused: (Focused) -> Unit) {
    val style = LocalWiis.current
    val gridState = rememberLazyGridState()
    val target = state.heroFocus
    LaunchedEffect(target?.token) {
        val i = target?.let { t -> games.indexOfFirst { HomeViewModel.gridKey(it) == t.key } } ?: -1
        if (i >= 0) gridState.scrollToItem(i)
    }
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
        // Whole rows only, and at least two of them: the covers shrink from the theme's size just enough.
        val size = twoRowCover(style, constraints.maxHeight, LocalDensity.current.density)
        WholeRowsGrid(games, { HomeViewModel.gridKey(it) }, "games", size.dp, size.dp, room(style, size).dp, gridState, Modifier, { HomeViewModel.gridKey(it) == target?.key }) { game, whole ->
            ArtTile(game, size, HomeViewModel.gridKey(game), target, actions, whole) { onFocused(Focused.InGrid(game)) }
        }
    }
}

// ---- Collections --------------------------------------------------------------------------

/**
 * The Collections section: a card per collection (four of its covers, its name and count), yours
 * first, then the series Wii-S offers, marked "Suggested". Two whole rows, like the other grids.
 */
@Composable
private fun CollectionsHome(state: HomeUiState, actions: HeroActions, onFocused: (Focused) -> Unit) {
    val style = LocalWiis.current
    val spec = style.spec
    if (state.collections.isEmpty()) {
        Box(Modifier.fillMaxSize().onFocusEvent { if (it.hasFocus) onFocused(Focused.Other) }, contentAlignment = Alignment.TopStart) {
            Column(
                Modifier.padding(top = 8.dp).widthIn(max = 520.dp).glassBox(style, RoundedCornerShape(spec.glass.radius.dp)).padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("No collections yet", style = TextStyle(fontFamily = style.fonts.display, fontWeight = FontWeight(spec.type.tabWeight.coerceIn(100, 900)), fontSize = spec.type.tileTitleSize.sp, color = style.glassText))
                Text(
                    "Wii-S looks for series in your games' titles, like Sonic or Pokémon, and offers them here. To make your own, press Select on a game and choose Add to collection.",
                    style = style.label.copy(color = style.glassTextMuted),
                )
            }
        }
        return
    }
    val gridState = rememberLazyGridState()
    val target = state.heroFocus
    LaunchedEffect(target?.token) {
        val i = target?.let { t -> state.collections.indexOfFirst { HomeViewModel.colKey(it.id) == t.key } } ?: -1
        if (i >= 0) gridState.scrollToItem(i)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gap = spec.spacing.tileGap
        val density = LocalDensity.current.density
        // Four cards across; two rows of them fit with their focus room.
        val width = ((maxWidth.value - 2 * room(style, 160) - 3 * gap) / 4f).toInt().coerceAtLeast(120)
        val pad = max(room(style, width), gap + 1)
        val height = ((constraints.maxHeight / density - gap - 2 * pad) / 2f).toInt().coerceIn(96, 160)
        WholeRowsGrid(state.collections, { HomeViewModel.colKey(it.id) }, "collections", width.dp, height.dp, room(style, width).dp, gridState, Modifier, { HomeViewModel.colKey(it.id) == target?.key }) { entry, whole ->
            CollectionCard(entry, width.dp, height.dp, target, actions, whole) { onFocused(Focused.Collection(entry)) }
        }
    }
}

/** A collection: four covers edge to edge, a glass strip with its name and count; "Suggested" until you keep it. */
@Composable
private fun CollectionCard(entry: CollectionEntry, w: Dp, h: Dp, target: HeroFocus?, actions: HeroActions, modifier: Modifier, onFocus: () -> Unit) {
    val style = LocalWiis.current
    val spec = style.spec
    val radius = spec.glass.radius
    val shape = RoundedCornerShape(radius.dp)
    val key = HomeViewModel.colKey(entry.id)
    val px = with(LocalDensity.current) { w.roundToPx() }
    WiisTile(
        onClick = { actions.openCollection(entry.id) },
        shape = shape,
        fill = style.glassFill,
        fillFocused = style.glassFill,
        ringRadius = radius.dp,
        modifier = modifier
            .size(w, h)
            .focusTarget(key, target, actions)
            .onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Column(Modifier.fillMaxSize()) {
            // Its covers above, the name and count below; nothing overlaps.
            CoverMosaic(entry.games, px, Modifier.weight(1f).fillMaxWidth())
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text(
                    entry.name,
                    style = TextStyle(fontFamily = style.fonts.display, fontWeight = FontWeight(spec.type.tabWeight.coerceIn(100, 900)), fontSize = 14.sp, color = style.glassText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(gamesCount(entry.games.size), style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = 1)
            }
        }
        Box(Modifier.fillMaxSize()) {
            if (entry.suggested) {
                Text(
                    "Suggested",
                    style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, color = style.hintBadgeText),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(style.hintBadge, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (spec.glass.borderWidth > 0f) Box(Modifier.matchParentSize().border(spec.glass.borderWidth.dp, style.glassBorder, shape))
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
    WholeRowsGrid(apps, { "app:${it.key}" }, "apps", 136.dp, 120.dp, roomA.dp, gridState, Modifier.bleed(roomA.dp, 0.dp), { false }) { app, whole ->
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
    key: (T) -> String,
    group: String,
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
        // The rows just outside the window reach 1 px into the grid's area, so they are laid out
        // (still not drawn): Up and Down find the tile in the same column there instead of the one
        // Compose lays out first (the end of the row above).
        val padPx = max(roomPx, gapPx + 1)
        val edge = remember(padPx) { EdgeSpec(padPx.toFloat()) }
        val pad = with(density) { padPx.toDp() }
        CompositionLocalProvider(LocalBringIntoViewSpec provides edge) {
            LazyVerticalGrid(
                state = state,
                columns = GridCells.FixedSize(cellW),
                contentPadding = PaddingValues(start = room, end = room, top = pad, bottom = pad),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { heightPx.toDp() })
                    .then(modifier)
                    .bleed(0.dp, with(density) { (padPx - roomPx).toDp() })
                    .onFocusChanged { inside = it.hasFocus },
            ) {
                itemsIndexed(items, key = { _, item -> key(item) }) { i, item ->
                    val enabled = { inside || targeted(item) || state.rowShown(i) }
                    tile(
                        item,
                        Modifier
                            .graphicsLayer { alpha = if (state.rowShown(i)) 1f else 0f }
                            .navSpot(key(item), group, enabled) { state.cellOf(i) },
                    )
                }
            }
        }
    }
}

/** (row, column) of lazy-grid item [index] while it is laid out. */
private fun LazyGridState.cellOf(index: Int): Pair<Int, Int>? =
    layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.let { it.row to it.column }

// ---- Navigation dock ------------------------------------------------------------------------

// ---- Up and Down ----------------------------------------------------------------------------

/** Which page of Home shows: 0 Home, 1 the page below it. The two pages keep it; focus moves it. */
@Stable
internal class PageState(initial: Int) {
    var page by mutableIntStateOf(initial)
}

/**
 * Up and Down on the top screen. Inside a grid, focus goes to the next row: the same column, or
 * the row's last tile when it is shorter, so a row out of sight slides in from any column.
 * Otherwise it goes to the nearest element above or below that overlaps the focused one sideways,
 * the most overlap first, then the leftmost. Compose's own search would rather take a closer
 * element off to the side: from the right end of the pinned row, Up went to the far-left column.
 * Left and Right keep Compose's search. Elements join with [navSpot].
 *
 * Home has a page below it. An element on a page takes focus only while its page shows (or when
 * the view model names it); past the edge of its page, focus crosses to the other page, which
 * slides in. The dock belongs to every page and leads to the one showing.
 */
@Stable
internal class VerticalNav {
    class Spot(val key: String, val group: String, val requester: FocusRequester) {
        var bounds = Rect.Zero
        var enabled: () -> Boolean = { true }
        var cell: () -> Pair<Int, Int>? = { null }
        /** 0: on Home; 1: on the page below; -1: on every page (the dock, the header). */
        var page = -1
    }

    private val spots = HashMap<String, Spot>()
    private val lastOnPage = HashMap<Int, String>()

    /** Set by the two pages while they are on screen. */
    var pages: PageState? = null
    val page: Int get() = pages?.page ?: 0

    /** The key the view model asked to focus: it may take focus on the page that isn't showing. */
    var targetKey: String? = null

    /** Called whenever an element takes focus. */
    var onFocus: (Spot) -> Unit = {}

    fun add(spot: Spot) { spots[spot.key] = spot }
    fun remove(spot: Spot) { if (spots[spot.key] === spot) spots.remove(spot.key) }
    fun requester(key: String): FocusRequester? = spots[key]?.requester

    fun focusable(s: Spot): Boolean = (s.page < 0 || s.page == page || s.key == targetKey) && s.enabled()

    fun focused(spot: Spot) {
        if (spot.page >= 0) {
            pages?.page = spot.page
            lastOnPage[spot.page] = spot.key
        }
        onFocus(spot)
    }

    /** Rule 1: the next row of the same grid. */
    private fun inRow(from: Spot, down: Boolean): Spot? {
        val (row, column) = from.cell() ?: return null
        val want = row + if (down) 1 else -1
        val inRow = spots.values.mapNotNull { s -> s.takeIf { it !== from && it.group == from.group }?.cell()?.takeIf { it.first == want }?.let { s to it.second } }
        if (inRow.isEmpty()) return null
        return (inRow.firstOrNull { it.second == column } ?: inRow.filter { it.second < column }.maxByOrNull { it.second } ?: inRow.minBy { it.second }).first
    }

    /** Rule 2: the nearest element of another group above or below, overlapping sideways. */
    private fun nearest(from: Spot, down: Boolean, accept: (Spot) -> Boolean): Spot? {
        val f = from.bounds
        var best: Spot? = null
        var bestGap = 0f
        var bestOverlap = 0f
        for (s in spots.values) {
            if (s === from || s.group == from.group || !s.enabled() || !accept(s)) continue
            val b = s.bounds
            val overlap = min(b.right, f.right) - max(b.left, f.left)
            if (overlap < 1f) continue
            val gap = if (down) b.top - f.bottom else f.top - b.bottom
            if (gap < -1f) continue
            val better = best == null || gap < bestGap - 1f ||
                (gap <= bestGap + 1f && (overlap > bestOverlap + 1f || (overlap >= bestOverlap - 1f && b.left < best.bounds.left)))
            if (better) { best = s; bestGap = gap; bestOverlap = overlap }
        }
        return best
    }

    fun next(from: Spot, down: Boolean): FocusRequester {
        inRow(from, down)?.let { return it.requester }
        val to = if (from.page >= 0) nearest(from, down) { it.page == from.page } ?: nearest(from, down) { it.page < 0 }
        else nearest(from, down) { it.page < 0 || it.page == page }
        return to?.requester ?: FocusRequester.Default
    }

    /**
     * Down from Home's bottom row or Up from the top of the page below: where to go on the other
     * page. Back where focus last was there, if that is still above or below; else the nearest.
     */
    fun across(from: Spot, down: Boolean): Spot? {
        if (from.page < 0 || inRow(from, down) != null || nearest(from, down) { it.page == from.page } != null) return null
        val other = from.page + if (down) 1 else -1
        val back = lastOnPage[other]?.let { spots[it] }?.takeIf { s ->
            s.page == other && s.enabled() && min(s.bounds.right, from.bounds.right) - max(s.bounds.left, from.bounds.left) >= 1f
        }
        return back ?: nearest(from, down) { it.page == other }
    }

    /** B below Home: back to [page], where focus last was there (else its first element). */
    fun goTo(page: Int): Boolean {
        val s = lastOnPage[page]?.let { spots[it] }?.takeIf { it.page == page && it.enabled() }
            ?: spots.values.filter { it.page == page && it.enabled() }.minByOrNull { it.bounds.top * 100_000f + it.bounds.left }
            ?: return false
        pages?.page = page
        return runCatching { s.requester.requestFocus() }.isSuccess
    }
}

internal val LocalVerticalNav = staticCompositionLocalOf<VerticalNav?> { null }

/**
 * Joins [VerticalNav] as [key] in [group]. [enabled]: may focus come here from another group (a
 * hidden row may not); [page]: the page of Home it is on (-1: every page); [within]: the element
 * next to it in a direction inside its own layout (the page below Home lays out its tiles itself),
 * null at the layout's edge; [cell]: (row, column) inside a grid.
 */
@Composable
internal fun Modifier.navSpot(
    key: String,
    group: String,
    enabled: () -> Boolean = { true },
    page: Int = -1,
    within: ((FocusDirection) -> FocusRequester?)? = null,
    cell: () -> Pair<Int, Int>? = { null },
): Modifier {
    val nav = LocalVerticalNav.current ?: return this
    val requester = remember(nav, key) { FocusRequester() }
    val spot = remember(nav, key, group) { VerticalNav.Spot(key, group, requester) }
    spot.enabled = enabled
    spot.cell = cell
    spot.page = page
    DisposableEffect(nav, spot) {
        nav.add(spot)
        onDispose { nav.remove(spot) }
    }
    return this
        .focusRequester(requester)
        // Where it is laid out, not where a scroll clips it or a lift moves it.
        .onGloballyPositioned { spot.bounds = it.findRootCoordinates().localBoundingBoxOf(it, clipBounds = false) }
        // Past the edge of its page: the other page slides in and its nearest element takes focus.
        .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown || (e.key != Key.DirectionDown && e.key != Key.DirectionUp)) return@onPreviewKeyEvent false
            val down = e.key == Key.DirectionDown
            if (within?.invoke(if (down) FocusDirection.Down else FocusDirection.Up) != null) return@onPreviewKeyEvent false
            val to = nav.across(spot, down) ?: return@onPreviewKeyEvent false
            nav.pages?.page = to.page
            runCatching { to.requester.requestFocus() }
            true
        }
        .onFocusChanged { if (it.isFocused) nav.focused(spot) }
        .focusProperties {
            // Only ever off (a hidden row, the page that isn't showing): otherwise Compose decides, so
            // in touch mode tiles stay unfocusable as usual. Forcing it on there made the launch walk
            // in LaunchSmokeTest spin for minutes.
            if (!nav.focusable(spot)) canFocus = false
            up = within?.invoke(FocusDirection.Up) ?: nav.next(spot, down = false)
            down = within?.invoke(FocusDirection.Down) ?: nav.next(spot, down = true)
            if (within != null) {
                left = within(FocusDirection.Left) ?: FocusRequester.Cancel
                right = within(FocusDirection.Right) ?: FocusRequester.Cancel
            }
        }
}

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
internal fun smooth(ms: Int): AnimationSpec<Float> {
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
            NavButton(s, s == state.section, state.heroFocus, actions, onFocused, ms, Modifier.track(key).navSpot(key, "dock")) { focus(key, it) }
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
                    .navSpot(HomeViewModel.dockKey(slot), "dock")
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
    Section.Collections -> "collections"
}

private fun sectionLabel(section: Section, style: WiisStyle) = when (section) {
    Section.Games -> style.spec.header.tabGames
    Section.Apps -> style.spec.header.tabApps
    Section.Collections -> style.spec.header.tabCollections
}

private fun systemTint(id: String, style: WiisStyle): Color = if (id == HomeModel.ALL) style.accent else style.systemColor(id)

internal fun gamesCount(n: Int) = if (n == 1) "1 game" else "$n games"

internal fun HomeUiState.isPinned(game: Game) = pinned.any { it.uri == game.uri }

private fun gameOf(f: Focused): Game? = when (f) {
    is Focused.Hero -> f.game
    is Focused.Pinned -> f.game
    is Focused.InGrid -> f.game
    is Focused.Tile -> f.game
    else -> null
}

/**
 * Takes focus when the view model names [key] (a new [HeroFocus.token] each time). Programmatic
 * focus is quiet: the move sound is for the player's own moves.
 */
@Composable
internal fun Modifier.focusTarget(key: String, target: HeroFocus?, actions: HeroActions): Modifier {
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
