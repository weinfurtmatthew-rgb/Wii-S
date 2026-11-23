package app.wiis.launcher.home

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wiis.designsystem.LocalUiFeedback
import app.wiis.designsystem.LocalWiis
import app.wiis.designsystem.SectionIcon
import app.wiis.designsystem.TileIcon
import app.wiis.designsystem.UiEvent
import app.wiis.designsystem.WiisStyle
import app.wiis.designsystem.WiisTile
import app.wiis.designsystem.glassBox
import app.wiis.emulator.Game
import app.wiis.emulator.ra.RaRecent
import app.wiis.launcher.formatPlaytime
import app.wiis.launcher.ui.AppIcon
import app.wiis.launcher.ui.ConsoleMark
import app.wiis.launcher.ui.Cover
import app.wiis.launcher.ui.CoverMosaic
import app.wiis.launcher.ui.LocalImageSource
import app.wiis.launcher.ui.SystemRing
import app.wiis.system.AppEntry
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.format.TextStyle as DayStyle
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where a new tile goes: before a tile, or into an empty cell; neither: the first place it fits. */
internal data class AddTarget(val before: Int? = null, val at: Pair<Int, Int>? = null)

/** Something on the page that takes focus: a tile, or an empty cell while arranging. */
private data class BoardItem(val key: String, val col: Int, val row: Int, val w: Int, val h: Int)

/**
 * The neighbour of [from] in [dir] on the page, null at its edge (where Up goes back to Home and
 * Down to the dock). Sideways: the next element that shares a row. Up and down: the next one that
 * shares a column, the most overlap first; failing that, the nearest one in the next row.
 */
private fun boardNeighbour(items: List<BoardItem>, from: BoardItem, dir: FocusDirection): BoardItem? {
    fun rows(b: BoardItem) = min(from.row + from.h, b.row + b.h) - max(from.row, b.row)
    fun cols(b: BoardItem) = min(from.col + from.w, b.col + b.w) - max(from.col, b.col)
    val others = items.filter { it !== from }
    return when (dir) {
        FocusDirection.Right -> others.filter { it.col >= from.col + from.w && rows(it) > 0 }
            .minWithOrNull(compareBy<BoardItem> { it.col }.thenByDescending { rows(it) }.thenBy { it.row })
        FocusDirection.Left -> others.filter { it.col + it.w <= from.col && rows(it) > 0 }
            .minWithOrNull(compareByDescending<BoardItem> { it.col + it.w }.thenByDescending { rows(it) }.thenBy { it.row })
        FocusDirection.Down -> others.filter { it.row >= from.row + from.h }.let { below ->
            below.filter { cols(it) > 0 }.minWithOrNull(compareBy<BoardItem> { it.row }.thenByDescending { cols(it) }.thenBy { it.col })
                ?: below.minWithOrNull(compareBy<BoardItem> { it.row }.thenBy { abs(it.col - from.col) })
        }
        FocusDirection.Up -> others.filter { it.row + it.h <= from.row }.let { above ->
            above.filter { cols(it) > 0 }.minWithOrNull(compareByDescending<BoardItem> { it.row + it.h }.thenByDescending { cols(it) }.thenBy { it.col })
                ?: above.minWithOrNull(compareByDescending<BoardItem> { it.row + it.h }.thenBy { abs(it.col - from.col) })
        }
        else -> null
    }
}

/** What a tile shows: its game, app or collection, a name, or why it can't show it. */
private data class Resolved(val game: Game? = null, val app: AppEntry? = null, val collection: CollectionEntry? = null, val title: String = "", val missing: String? = null)

private fun resolve(t: BoardTile, state: HomeUiState): Resolved = when (t.kind) {
    Board.APP -> state.apps.firstOrNull { it.key == t.ref }?.let { Resolved(app = it, title = it.label) } ?: Resolved(missing = "App not installed")
    Board.GAME -> state.board.games[t.ref]?.let { Resolved(game = it, title = it.title) } ?: Resolved(missing = "Game not found")
    Board.RANDOM -> state.board.random?.let { Resolved(game = it, title = it.title) } ?: Resolved(missing = "No games yet")
    Board.LAST_PLAYED -> state.hero?.let { Resolved(game = it, title = it.title) } ?: Resolved(missing = "Nothing played yet")
    Board.COLLECTION -> state.collections.firstOrNull { it.id == t.ref }?.let { Resolved(collection = it, title = it.name) } ?: Resolved(missing = "Collection not found")
    Board.SHORTCUT -> if (t.ref.startsWith("emu:")) {
        state.apps.firstOrNull { it.component.packageName == t.ref.removePrefix("emu:") }?.let { Resolved(app = it, title = it.label) } ?: Resolved(missing = "Emulator not installed")
    } else {
        Resolved(title = shortcutName(t.ref))
    }
    else -> Resolved(title = tileKindName(t))
}

internal fun shortcutName(ref: String) = when (ref) {
    "wifi" -> "Wi-Fi"
    "bluetooth" -> "Bluetooth"
    "display" -> "Display"
    "sound" -> "Sound"
    "settings" -> "Android settings"
    else -> ref
}

/**
 * The page below Home: tiles in sizes 1×1, 2×1 and 2×2 on a grid that fills the page. A opens a
 * tile; hold A to arrange (A picks a tile up or puts it down, the D-pad moves it, Y changes its
 * size, "+" in an empty cell adds one, B is done); Select has the same in a menu. B goes back up
 * to Home.
 */
@Composable
internal fun BoardPage(
    state: HomeUiState,
    actions: HeroActions,
    onFocused: (Focused) -> Unit,
    onAdd: (AddTarget) -> Unit,
    onSettings: (String) -> Unit,
) {
    val style = LocalWiis.current
    val spec = style.spec
    val feedback = LocalUiFeedback.current
    val nav = LocalVerticalNav.current
    val board = state.board
    val edit = board.edit
    val cols = board.cols
    val rows = board.rows
    val placed = remember(board.tiles, cols, rows) { Board.layout(board.tiles, cols, rows) }
    val free = remember(board.tiles, cols, rows, edit?.sticky) { if (edit?.sticky == true) Board.freeCells(board.tiles, cols, rows) else emptyList() }
    val items = remember(placed, free) {
        placed.map { BoardItem(HomeViewModel.boardKey(it.tile.id), it.col, it.row, it.w, it.h) } + free.map { (c, r) -> BoardItem(HomeViewModel.cellKey(c, r), c, r, 1, 1) }
    }
    val within = { key: String ->
        { dir: FocusDirection -> items.firstOrNull { it.key == key }?.let { boardNeighbour(items, it, dir) }?.let { nav?.requester(it.key) } }
    }
    // The key-up after the press that put a tile down or ended arranging does nothing more.
    var swallow by remember { mutableStateOf<Key?>(null) }
    val carrying = edit?.carrying
    val moveMs = if (spec.motion.reduceMotion) 0 else (spec.motion.pageSlideMs * 0.6f).roundToInt().coerceIn(0, 1_200)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp && e.key == swallow) { swallow = null; return@onPreviewKeyEvent true }
                val first = e.nativeKeyEvent.repeatCount == 0
                if (carrying != null) {
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent e.key in CARRY_KEYS
                    fun step(dx: Int, dy: Int) = feedback.emit(if (actions.boardStep(dx, dy)) UiEvent.Move else UiEvent.Bump)
                    when (e.key) {
                        Key.DirectionLeft -> step(-1, 0)
                        Key.DirectionRight -> step(1, 0)
                        Key.DirectionUp -> step(0, -1)
                        Key.DirectionDown -> step(0, 1)
                        Key.ButtonA, Key.ButtonB -> if (first) { feedback.emit(if (e.key == Key.ButtonA) UiEvent.Select else UiEvent.Back); actions.boardDrop(); swallow = e.key }
                        Key.ButtonY -> if (first) feedback.emit(if (actions.boardResize(carrying)) UiEvent.Select else UiEvent.Bump)
                        else -> return@onPreviewKeyEvent false
                    }
                    return@onPreviewKeyEvent true
                }
                if (e.type != KeyEventType.KeyDown || e.key != Key.ButtonB || !first) return@onPreviewKeyEvent false
                when {
                    edit?.sticky == true -> { feedback.emit(UiEvent.Back); actions.boardEndEdit() }
                    // B below Home: back up to Home, where focus was.
                    nav?.goTo(0) == true -> feedback.emit(UiEvent.Back)
                    else -> return@onPreviewKeyEvent false
                }
                swallow = Key.ButtonB
                true
            },
    ) {
        val gap = spec.spacing.tileGap.toFloat()
        val cellW = (maxWidth.value - (cols - 1) * gap) / cols
        val cellH = (maxHeight.value - (rows - 1) * gap) / rows
        val geometry = Geometry(cellW, cellH, gap)
        if (placed.isEmpty() && edit?.sticky != true) {
            EmptyBoard(geometry, state, actions, onFocused, onAdd)
            return@BoxWithConstraints
        }
        free.forEach { (c, r) ->
            key(HomeViewModel.cellKey(c, r)) { PlusCell(c, r, geometry, state, actions, onFocused, onAdd, within(HomeViewModel.cellKey(c, r))) }
        }
        // The carried tile last, so it is drawn over the others.
        placed.sortedBy { it.tile.id == carrying }.forEach { p ->
            key(p.tile.id) { BoardTileView(p, geometry, moveMs, state, actions, onFocused, onSettings, within(HomeViewModel.boardKey(p.tile.id))) }
        }
    }
}

private val CARRY_KEYS = setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown, Key.ButtonA, Key.ButtonB, Key.ButtonY)

/** Cell sizes in dp, for placing tiles. */
private class Geometry(val cellW: Float, val cellH: Float, val gap: Float) {
    fun x(col: Int) = (col * (cellW + gap)).dp
    fun y(row: Int) = (row * (cellH + gap)).dp
    fun w(cells: Int) = (cells * cellW + (cells - 1) * gap).dp
    fun h(cells: Int) = (cells * cellH + (cells - 1) * gap).dp
}

/** Smooth, no bounce, like the dock's glide; instant with reduce motion. */
private fun glide(ms: Int): AnimationSpec<Dp> {
    if (ms <= 0) return snap()
    val w = 5.8f / (ms / 1000f)
    return spring(dampingRatio = 1f, stiffness = w * w)
}

/** A tile's place on the page; when it moves or changes size it glides there. */
@Composable
private fun Modifier.boardPlace(x: Dp, y: Dp, w: Dp, h: Dp, ms: Int): Modifier {
    val ax by animateDpAsState(x, glide(ms), label = "tileX")
    val ay by animateDpAsState(y, glide(ms), label = "tileY")
    val aw by animateDpAsState(w, glide(ms), label = "tileW")
    val ah by animateDpAsState(h, glide(ms), label = "tileH")
    return offset(ax, ay).size(aw, ah)
}

@Composable
private fun BoardTileView(
    p: PlacedTile,
    g: Geometry,
    moveMs: Int,
    state: HomeUiState,
    actions: HeroActions,
    onFocused: (Focused) -> Unit,
    onSettings: (String) -> Unit,
    within: (FocusDirection) -> FocusRequester?,
) {
    val style = LocalWiis.current
    val spec = style.spec
    val feedback = LocalUiFeedback.current
    val t = p.tile
    val key = HomeViewModel.boardKey(t.id)
    val r = resolve(t, state)
    val edit = state.board.edit
    val carried = edit?.carrying == t.id
    val radius = spec.glass.radius
    val shape = RoundedCornerShape(radius.dp)
    val w = g.w(p.w)
    val h = g.h(p.h)
    val plays = t.kind in PLAY_KINDS
    WiisTile(
        onClick = {
            if (edit?.sticky == true) actions.boardPickUp(t.id, sticky = true)
            else when (t.kind) {
                Board.GAME, Board.RANDOM, Board.LAST_PLAYED -> r.game?.let { actions.launchFrom(it, key) }
                Board.APP -> r.app?.let { actions.launchApp(it) }
                Board.SHORTCUT -> actions.openShortcut(t.ref)
                Board.COLLECTION -> r.collection?.let { actions.openCollectionFrom(it.id, key) }
                Board.ACHIEVEMENTS -> if (state.board.achievements is RecentUnlocks.NotSetUp) onSettings("ra")
            }
        },
        onSecondary = when {
            edit != null -> ({ if (!actions.boardResize(t.id)) feedback.emit(UiEvent.Bump) })
            t.kind == Board.RANDOM -> ({ actions.shuffleRandom() })
            plays && r.game != null -> ({ actions.openPicker(r.game) })
            else -> null
        },
        onLongClick = if (edit == null) ({ feedback.emit(UiEvent.Select); actions.boardPickUp(t.id, sticky = true) }) else null,
        clickSound = if (edit == null && plays && r.game != null) null else UiEvent.Select,
        shape = shape,
        fill = style.glassFill,
        fillFocused = style.glassFill,
        ringRadius = radius.dp,
        modifier = Modifier
            .boardPlace(g.x(p.col), g.y(p.row), w, h, moveMs)
            .navSpot(key, "board", page = 1, within = within)
            .focusTarget(key, state.heroFocus, actions)
            .onFocusChanged { if (it.isFocused) onFocused(Focused.Tile(t, r.game, r.app, r.collection, r.title)) }
            .graphicsLayer {
                // Picked up: it lifts off the page while it moves.
                if (carried) {
                    scaleX = 1.05f
                    scaleY = 1.05f
                    translationY = -6.dp.toPx()
                    shadowElevation = 18.dp.toPx()
                    this.shape = shape
                }
            },
    ) {
        Box(Modifier.fillMaxSize().clip(shape)) {
            val px = with(LocalDensity.current) { maxOf(w, h).roundToPx() }
            when {
                r.missing != null -> Missing(t, r.missing)
                t.kind == Board.APP -> AppFace(r.app!!, p.w > 1)
                t.kind == Board.GAME -> GameFace(r.game!!, px, p, null, shape)
                t.kind == Board.RANDOM -> GameFace(r.game!!, px, p, "Random", shape, icon = "dice")
                t.kind == Board.LAST_PLAYED -> GameFace(r.game!!, px, p, "Last played", shape, played = state.heroPlayedMillis)
                t.kind == Board.COLLECTION -> CollectionFace(r.collection!!, px, p)
                t.kind == Board.SHORTCUT -> ShortcutFace(t.ref, r.app, p.w > 1)
                t.kind == Board.CLOCK -> ClockFace(p)
                t.kind == Board.WEEK -> WeekFace(state.board.week, state.board.today, p)
                t.kind == Board.ACHIEVEMENTS -> AchievementsFace(state.board.achievements, p)
            }
            // Arranging: every tile shows it can move.
            if (edit?.sticky == true && !carried) Box(Modifier.matchParentSize().dashed(style.glassText.copy(alpha = 0.45f), radius.toFloat()))
            if (spec.glass.borderWidth > 0f) Box(Modifier.matchParentSize().border(spec.glass.borderWidth.dp, style.glassBorder, shape))
        }
    }
}

private val PLAY_KINDS = setOf(Board.GAME, Board.RANDOM, Board.LAST_PLAYED)

/** A dashed outline just inside the edge: tiles while arranging, and empty cells. */
private fun Modifier.dashed(color: Color, radius: Float): Modifier = drawBehind {
    val inset = 2.dp.toPx()
    drawRoundRect(
        color,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = CornerRadius((radius.dp.toPx() - inset).coerceAtLeast(0f)),
        style = Stroke(width = 1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))),
    )
}

/** An empty cell while arranging: "+" adds a tile there. */
@Composable
private fun PlusCell(
    col: Int,
    row: Int,
    g: Geometry,
    state: HomeUiState,
    actions: HeroActions,
    onFocused: (Focused) -> Unit,
    onAdd: (AddTarget) -> Unit,
    within: (FocusDirection) -> FocusRequester?,
) {
    val style = LocalWiis.current
    val radius = style.spec.glass.radius
    val key = HomeViewModel.cellKey(col, row)
    WiisTile(
        onClick = { onAdd(AddTarget(at = col to row)) },
        shape = RoundedCornerShape(radius.dp),
        fill = style.glassFill.copy(alpha = style.glassFill.alpha * 0.35f),
        fillFocused = style.glassFill.copy(alpha = style.glassFill.alpha * 0.6f),
        ringRadius = radius.dp,
        elevated = false,
        modifier = Modifier
            .offset(g.x(col), g.y(row))
            .size(g.w(1), g.h(1))
            .navSpot(key, "board", page = 1, within = within)
            .focusTarget(key, state.heroFocus, actions)
            .onFocusChanged { if (it.isFocused) onFocused(Focused.Cell(col, row)) },
    ) {
        Box(Modifier.fillMaxSize().dashed(style.glassText.copy(alpha = 0.35f), radius.toFloat()), contentAlignment = Alignment.Center) {
            TileIcon("plus", 26.dp, style.glassTextMuted, strokeUnits = 2f)
        }
    }
}

/** No tiles at all: one card that says how to add some. */
@Composable
private fun EmptyBoard(g: Geometry, state: HomeUiState, actions: HeroActions, onFocused: (Focused) -> Unit, onAdd: (AddTarget) -> Unit) {
    val style = LocalWiis.current
    val spec = style.spec
    val key = "board:empty"
    WiisTile(
        onClick = { onAdd(AddTarget()) },
        shape = RoundedCornerShape(spec.glass.radius.dp),
        fill = style.glassFill,
        fillFocused = style.glassFill,
        ringRadius = spec.glass.radius.dp,
        modifier = Modifier
            .size(g.w(min(3, state.board.cols)), g.h(1))
            .navSpot(key, "board", page = 1, within = { null })
            .focusTarget(key, state.heroFocus, actions)
            .onFocusChanged { if (it.isFocused) onFocused(Focused.Cell(-1, -1)) },
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(style.hintBadge, CircleShape), contentAlignment = Alignment.Center) { TileIcon("plus", 22.dp, style.hintBadgeText, strokeUnits = 2.4f) }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Add tiles to this page", style = titleStyle(style, spec.type.tileTitleSize), maxLines = 1)
                Text("Apps, games, collections, widgets and shortcuts. Press A.", style = style.label.copy(color = style.glassTextMuted), maxLines = 2)
            }
        }
    }
}

private fun titleStyle(style: WiisStyle, size: Int) = TextStyle(
    fontFamily = style.fonts.display,
    fontWeight = FontWeight(style.spec.type.tabWeight.coerceIn(100, 900)),
    fontSize = size.sp,
    color = style.glassText,
)

// ---- Faces -------------------------------------------------------------------------------------

@Composable
private fun Missing(t: BoardTile, text: String) {
    val style = LocalWiis.current
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        SectionIcon(if (t.kind == Board.APP || t.kind == Board.SHORTCUT) "apps" else "collections", 22.dp, style.glassTextMuted)
        Text(text, style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AppFace(app: AppEntry, wide: Boolean) {
    val style = LocalWiis.current
    if (wide) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 52)
            Column(Modifier.weight(1f)) {
                Text(app.label, style = titleStyle(style, 15), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("App", style = style.label.copy(color = style.glassTextMuted, fontSize = 12.sp), maxLines = 1)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(app, 46)
            Text(app.label, style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = style.glassText), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * A game's cover. Small: the cover alone. Wide: the cover beside its name. Large: the cover with
 * a glass strip naming it. [label] ("Random", "Last played") sits in a chip at the top.
 */
@Composable
private fun GameFace(game: Game, px: Int, p: PlacedTile, label: String?, shape: RoundedCornerShape, icon: String? = null, played: Long = 0) {
    val style = LocalWiis.current
    val color = style.systemColor(game.systemId)
    val wide = p.w > p.h
    Box(Modifier.fillMaxSize()) {
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().aspectSquare()) {
                    Cover(game, px, color)
                    ConsoleMark(game.systemId)
                }
                Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
                    if (label != null) Text(label, style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = 1)
                    Text(game.title, style = titleStyle(style, 15), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (played > 0) Text("Played ${formatPlaytime(played)}", style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = 1)
                }
            }
        } else {
            Cover(game, px, color, titleSize = if (p.w == 1) 11 else null)
            ConsoleMark(game.systemId)
            if (p.h > 1) {
                Column(
                    Modifier.align(Alignment.BottomStart).padding(10.dp).fillMaxWidth().glassBox(style, RoundedCornerShape(max(8, style.spec.glass.radius - 8).dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(game.title, style = titleStyle(style, 15), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(label, if (played > 0) "Played ${formatPlaytime(played)}" else null).joinToString(" · ")
                    if (meta.isNotEmpty()) Text(meta, style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = 1)
                }
            } else if (label != null) {
                Chip(label, icon, Modifier.align(Alignment.TopStart).padding(7.dp))
            }
        }
        SystemRing(color, shape)
    }
}

/** A small dark chip with an icon: "Random" on a small random-game tile. */
@Composable
private fun Chip(text: String, icon: String?, modifier: Modifier) {
    val style = LocalWiis.current
    Row(modifier.background(style.hintBadge, CircleShape).padding(horizontal = 7.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) TileIcon(icon, 12.dp, style.hintBadgeText, strokeUnits = 2.4f)
        Text(text, style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, color = style.hintBadgeText), maxLines = 1)
    }
}

/** Square from the height it is given (the cover beside a wide tile's text). */
private fun Modifier.aspectSquare(): Modifier = layout { measurable, constraints ->
    val side = constraints.maxHeight
    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(side, side))
    layout(side, side) { placeable.place(0, 0) }
}

@Composable
private fun CollectionFace(entry: CollectionEntry, px: Int, p: PlacedTile) {
    val style = LocalWiis.current
    if (p.w > p.h) {
        Row(Modifier.fillMaxSize()) {
            CoverMosaic(entry.games, px, Modifier.fillMaxHeight().aspectSquare())
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
                Text("Collection", style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = 1)
                Text(entry.name, style = titleStyle(style, 15), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(gamesCount(entry.games.size), style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = 1)
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            CoverMosaic(entry.games, px, Modifier.weight(1f).fillMaxWidth())
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(entry.name, style = titleStyle(style, if (p.w > 1) 14 else 12), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (p.h > 1) Text(gamesCount(entry.games.size), style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ShortcutFace(ref: String, app: AppEntry?, wide: Boolean) {
    val style = LocalWiis.current
    val name = app?.label ?: shortcutName(ref)
    val detail = if (app != null) "Emulator" else "Android settings".takeIf { ref != "settings" } ?: "Everything else"
    @Composable
    fun Icon(size: Int) = if (app != null) AppIcon(app, size) else TileIcon(ref, size.dp, style.glassText)
    if (wide) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (app != null) 48 else 32)
            Column(Modifier.weight(1f)) {
                Text(name, style = titleStyle(style, 15), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = style.label.copy(color = style.glassTextMuted, fontSize = 12.sp), maxLines = 1)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (app != null) 44 else 30)
            Text(name, style = TextStyle(fontFamily = style.fonts.body, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = style.glassText), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The time, updated on the minute; the date under it on bigger tiles. */
@Composable
private fun ClockFace(p: PlacedTile) {
    val style = LocalWiis.current
    val header = style.spec.header
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000)
        }
    }
    val date = Date(now)
    val time = SimpleDateFormat(if (header.clock24h) "HH:mm" else "h:mm", Locale.getDefault()).format(date)
    val ampm = if (header.clock24h) "" else SimpleDateFormat("a", Locale.getDefault()).format(date)
    val big = p.h > 1
    val size = when { big -> 52; p.w > 1 -> 36; else -> 24 }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(time, style = titleStyle(style, size).copy(fontWeight = FontWeight(style.spec.type.wordmarkWeight.coerceIn(100, 900))), maxLines = 1, softWrap = false)
            if (ampm.isNotEmpty() && p.w > 1) Text(ampm, style = style.label.copy(color = style.glassTextMuted), maxLines = 1, modifier = Modifier.padding(start = 6.dp, bottom = (size * 0.18f).dp))
        }
        if (p.w > 1) {
            val pattern = if (big) "EEEE, d MMMM" else "EEE, d MMM"
            Text(SimpleDateFormat(pattern, Locale.getDefault()).format(date), style = style.label.copy(color = style.glassTextMuted), maxLines = 1)
        }
    }
}

/** This week's play: the total, and a bar per day, Monday to Sunday, today's the darkest. */
@Composable
private fun WeekFace(days: List<Long>, today: Int, p: PlacedTile) {
    val style = LocalWiis.current
    val total = days.sum()
    val totalText = if (total <= 0) "Nothing yet" else formatPlaytime(total)
    val big = p.h > 1
    @Composable
    fun Bars(modifier: Modifier, letters: Boolean) {
        val most = max(days.maxOrNull() ?: 0L, 30 * 60_000L)
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (big) 8.dp else 5.dp), verticalAlignment = Alignment.Bottom) {
            (0 until 7).forEach { i ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    val share = (days.getOrNull(i) ?: 0L).toFloat() / most
                    val color = if (i == today) style.glassText else style.glassText.copy(alpha = 0.22f)
                    Box(Modifier.weight(1f, fill = false).fillMaxWidth().fillMaxHeight(share.coerceIn(0.04f, 1f)).background(color, RoundedCornerShape(3.dp)))
                    if (letters) {
                        Text(
                            DayOfWeek.of(i + 1).getDisplayName(DayStyle.NARROW, Locale.getDefault()),
                            style = style.label.copy(color = if (i == today) style.glassText else style.glassTextMuted, fontSize = 10.sp),
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
    if (p.w == 1) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("This week", style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = 1)
            Text(totalText, style = titleStyle(style, 14), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Bars(Modifier.fillMaxWidth().weight(1f), letters = false)
        }
    } else if (!big) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
                Text("This week", style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = 1)
                Text(totalText, style = titleStyle(style, 17), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Bars(Modifier.weight(1f).fillMaxHeight(), letters = false)
        }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("This week", style = style.label.copy(color = style.glassTextMuted, fontSize = 12.sp), maxLines = 1)
            Text(totalText, style = titleStyle(style, 22), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Bars(Modifier.fillMaxWidth().weight(1f), letters = true)
        }
    }
}

/** The latest unlocks with their badges, or how to set RetroAchievements up. */
@Composable
private fun AchievementsFace(state: RecentUnlocks, p: PlacedTile) {
    val style = LocalWiis.current
    val big = p.h > 1
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(if (big) 8.dp else 4.dp, if (big) Alignment.Top else Alignment.CenterVertically)) {
        if (big || p.w == 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TileIcon("trophy", 16.dp, style.glassTextMuted)
                Text(if (p.w == 1) "Unlocks" else "Recent achievements", style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = 1)
            }
        }
        when (state) {
            RecentUnlocks.Loading -> Text("Loading…", style = style.label.copy(color = style.glassTextMuted), maxLines = 1)
            RecentUnlocks.NotSetUp -> {
                if (!big && p.w > 1) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TileIcon("trophy", 26.dp, style.glassText)
                    Column {
                        Text("RetroAchievements", style = titleStyle(style, 14), maxLines = 1)
                        Text("Press A to sign in", style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = 1)
                    }
                } else {
                    Text("Sign in to RetroAchievements", style = titleStyle(style, 14), maxLines = 2)
                    Text("Press A to set it up in Wii-S settings.", style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = 2)
                }
            }
            is RecentUnlocks.Failed -> Text(state.message, style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = if (big) 4 else 2, overflow = TextOverflow.Ellipsis)
            is RecentUnlocks.Ready -> {
                if (state.items.isEmpty()) {
                    Text("Nothing unlocked in the last 30 days", style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = 2)
                } else if (p.w == 1) {
                    Text("${state.items.size}", style = titleStyle(style, 24), maxLines = 1)
                } else {
                    state.items.take(if (big) 3 else 1).forEach { Unlock(it, if (big) 36 else 40, label = if (big) null else "Latest achievement") }
                }
            }
        }
    }
}

@Composable
private fun Unlock(a: RaRecent, badge: Int, label: String? = null) {
    val style = LocalWiis.current
    val images = LocalImageSource.current
    val px = with(LocalDensity.current) { badge.dp.roundToPx() }
    val bitmap by produceState<android.graphics.Bitmap?>(null, a.badgeUrl, images) { value = images.remote(a.badgeUrl, px) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(badge.dp).clip(RoundedCornerShape(8.dp)).background(style.glassText.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
                ?: TileIcon("trophy", (badge * 0.55f).dp, style.glassTextMuted)
        }
        Column(Modifier.weight(1f)) {
            if (label != null) Text(label, style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = 1)
            Text(a.title, style = titleStyle(style, 13), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(a.gameTitle, "${a.points} pts").filter { it.isNotBlank() }.joinToString(" · "), style = style.label.copy(color = style.glassTextMuted, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---- Adding a tile -----------------------------------------------------------------------------

private data class AddEntry(val kind: String, val ref: String, val label: String, val detail: String? = null, val icon: (@Composable () -> Unit)? = null)

/** "Add a tile here…": a kind of tile, then which one. B goes back a step. */
@Composable
internal fun AddTilePicker(state: HomeUiState, onPick: (kind: String, ref: String) -> Unit, onDismiss: () -> Unit) {
    val style = LocalWiis.current
    var category by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(category) { withFrameNanos { }; runCatching { first.requestFocus() } }
    val systems = remember(state.systems) { state.systems.associate { it.id to it.name } }
    fun entries(id: String): List<AddEntry> = when (id) {
        "apps" -> state.apps.sortedBy { it.label.lowercase() }.map { a -> AddEntry(Board.APP, a.key, a.label, icon = { AppIcon(a, 26) }) }
        "games" -> state.games.map { g -> AddEntry(Board.GAME, g.uri, g.title, systems[g.systemId] ?: g.systemId) }
        "collections" -> state.collections.map { c -> AddEntry(Board.COLLECTION, c.id, c.name, gamesCount(c.games.size) + if (c.suggested) " · suggested" else "") }
        "widgets" -> listOf(
            AddEntry(Board.CLOCK, "", "Clock", "Time and date", icon = { TileIcon("clock", 22.dp, style.glassText) }),
            AddEntry(Board.LAST_PLAYED, "", "Last played", "Jump back in", icon = { TileIcon("play", 22.dp, style.glassText) }),
            AddEntry(Board.WEEK, "", "This week", "Play time per day", icon = { TileIcon("week", 22.dp, style.glassText) }),
            AddEntry(Board.ACHIEVEMENTS, "", "Recent achievements", "RetroAchievements", icon = { TileIcon("trophy", 22.dp, style.glassText) }),
            AddEntry(Board.RANDOM, "", "Random game", "Y for another", icon = { TileIcon("dice", 22.dp, style.glassText) }),
        )
        "shortcuts" -> Board.SHORTCUTS.map { r -> AddEntry(Board.SHORTCUT, r, shortcutName(r), "Android settings".takeIf { r != "settings" }, icon = { TileIcon(r, 22.dp, style.glassText) }) } +
            state.apps.filter { it.component.packageName in state.emulators }.sortedBy { it.label.lowercase() }
                .map { a -> AddEntry(Board.SHORTCUT, "emu:${a.component.packageName}", a.label, "Emulator", icon = { AppIcon(a, 26) }) }
        else -> emptyList()
    }
    val categories = listOf("apps" to "Apps", "games" to "Games", "collections" to "Collections", "widgets" to "Widgets", "shortcuts" to "Shortcuts")
    PopupLayer(onDismiss = { if (category != null) category = null else onDismiss() }, width = 440.dp) {
        Column {
            val open = category
            if (open == null) {
                PopupTitle("Add a tile", "Below Home")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEachIndexed { i, (id, label) ->
                        val n = entries(id).size
                        MenuRow("$label…", null, if (i == 0) Modifier.focusRequester(first) else Modifier, detail = if (id == "widgets" || id == "shortcuts") null else "$n") { category = id }
                    }
                }
            } else {
                val list = entries(open)
                PopupTitle(categories.first { it.first == open }.second, "B to go back")
                if (list.isEmpty()) {
                    MenuRow("Nothing here yet. Back", null, Modifier.focusRequester(first)) { category = null }
                } else {
                    LazyColumn(Modifier.heightIn(max = 272.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(list, key = { _, e -> e.kind + ":" + e.ref }) { i, e ->
                            MenuRow(e.label, null, if (i == 0) Modifier.focusRequester(first) else Modifier, detail = e.detail, leading = e.icon) { onPick(e.kind, e.ref) }
                        }
                    }
                }
            }
        }
    }
}
