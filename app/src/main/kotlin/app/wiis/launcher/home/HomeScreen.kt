package app.wiis.launcher.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wiis.designsystem.LocalUiFeedback
import app.wiis.designsystem.LocalWiis
import app.wiis.designsystem.UiEvent
import app.wiis.designsystem.WiisStyle
import app.wiis.designsystem.WiisTile
import app.wiis.designsystem.wiisBackground
import app.wiis.launcher.system.SystemScreen
import app.wiis.launcher.ui.ActionTile
import app.wiis.launcher.ui.AppIcon
import app.wiis.launcher.ui.ClockText
import app.wiis.launcher.ui.GameTile
import app.wiis.launcher.ui.HintBar
import app.wiis.launcher.ui.MessageBar
import app.wiis.system.AppEntry

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val feedback = LocalUiFeedback.current
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { vm.onFolderPicked(it) }
    LaunchedEffect(vm) { vm.events.collect(feedback::emit) }
    if (LocalWiis.current.spec.layout.games == "hero") {
        HeroHomeRoute(vm, onPickFolder = { pickFolder.launch(null) })
    } else {
        ClassicHome(vm, onPickFolder = { pickFolder.launch(null) })
    }
}

/** The shelves and grid layouts (Draft 1), still selectable with `layout.games`. */
@Composable
private fun ClassicHome(vm: HomeViewModel, onPickFolder: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val style = LocalWiis.current
    val feedback = LocalUiFeedback.current

    BackHandler(enabled = state.section != Section.Games) {
        feedback.emit(UiEvent.Back)
        vm.selectSection(Section.Games)
    }

    val pad = style.spec.spacing.screenPadding.dp
    Box(Modifier.fillMaxSize().wiisBackground(style)) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = pad, vertical = (pad.value * 0.6f).dp)
                // Global buttons work from anywhere on the top screen.
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.ButtonL1 -> { feedback.emit(UiEvent.Tab); vm.cycleSection(-1); true }
                        Key.ButtonR1 -> { feedback.emit(UiEvent.Tab); vm.cycleSection(+1); true }
                        // These older layouts keep their settings as the third page (the hero layout has the popup).
                        Key.ButtonStart -> { feedback.emit(UiEvent.Tab); vm.selectSection(Section.Collections); true }
                        Key.ButtonSelect -> { feedback.emit(UiEvent.Tab); vm.selectSection(if (state.section == Section.Apps) Section.Games else Section.Apps); true }
                        else -> false
                    }
                },
        ) {
            Header(state.section, onSelect = vm::selectSection)
            Spacer(Modifier.height(style.spec.spacing.headerGap.dp))
            SectionContent(state, vm, onPickFolder = onPickFolder, modifier = Modifier.weight(1f))
            state.message?.let { MessageBar(it, Modifier.padding(top = 10.dp)) }
            HintBar(hintsFor(state.section), Modifier.padding(top = 10.dp))
        }
    }

    state.picker?.let { picker -> EmulatorPickerDialog(picker, onPick = { vm.launchGame(picker.game, it) }, onDismiss = vm::dismissPicker) }
}

private fun hintsFor(section: Section): List<Pair<String, String>> = when (section) {
    Section.Games -> listOf("A" to "Play", "Y" to "Emulator", "L1/R1" to "Tabs", "Select" to "Apps", "Start" to "System")
    Section.Apps -> listOf("A" to "Open", "B" to "Games", "L1/R1" to "Tabs", "Select" to "Games", "Start" to "System")
    Section.Collections -> listOf("A" to "Select", "B" to "Games", "L1/R1" to "Tabs")
}

@Composable
private fun SectionContent(state: HomeUiState, vm: HomeViewModel, onPickFolder: () -> Unit, modifier: Modifier) {
    val motion = LocalWiis.current.spec.motion
    AnimatedContent(
        targetState = state.section,
        modifier = modifier,
        transitionSpec = {
            val ms = motion.tabTransitionMs.coerceIn(0, 2_000)
            val forward = targetState.ordinal > initialState.ordinal
            when {
                motion.reduceMotion || motion.tabTransition == "none" || ms == 0 ->
                    ContentTransform(EnterTransition.None, ExitTransition.None)
                motion.tabTransition == "fade" -> fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))
                else -> (slideInHorizontally(tween(ms)) { w -> if (forward) w / 6 else -w / 6 } + fadeIn(tween(ms))) togetherWith
                    (slideOutHorizontally(tween(ms)) { w -> if (forward) -w / 6 else w / 6 } + fadeOut(tween(ms)))
            }
        },
        label = "section",
    ) { section ->
        when (section) {
            Section.Games -> GamesPane(state, vm, onPickFolder)
            Section.Apps -> AppsPane(state.apps, vm)
            Section.Collections -> SystemScreen(onPickRomFolder = onPickFolder)
        }
    }
}

@Composable
private fun Header(current: Section, onSelect: (Section) -> Unit) {
    val style = LocalWiis.current
    val header = style.spec.header
    val first = remember { FocusRequester() }
    // Fallback focus so the controller always has something; a game tile takes over when shelves load.
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (header.showWordmark) {
            Text(header.wordmark, style = style.wordmark)
            Spacer(Modifier.width(14.dp))
        }
        Section.entries.forEachIndexed { i, section ->
            val label = when (section) {
                Section.Games -> header.tabGames
                Section.Apps -> header.tabApps
                Section.Collections -> "Settings"
            }
            TabChip(label, active = section == current, onClick = { onSelect(section) }, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier)
        }
        Spacer(Modifier.weight(1f))
        ClockText()
    }
}

@Composable
private fun TabChip(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val style = LocalWiis.current
    val tabStyle = style.spec.shape.tabStyle
    val filled = tabStyle == "pill" && active
    WiisTile(
        onClick = onClick,
        modifier = modifier,
        shape = style.tabShape,
        fill = if (filled) style.tabActiveFill else Color.Transparent,
        fillFocused = if (filled) style.tabActiveFill else style.tileFocused,
        clickSound = UiEvent.Tab,
        elevated = false,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val color = when {
                filled -> style.tabActiveText
                active -> style.accent
                else -> style.tabIdleText
            }
            Text(label, style = style.tab.copy(color = color))
            if (tabStyle == "underline") {
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .width(28.dp)
                        .height(3.dp)
                        .background(if (active) style.accent else Color.Transparent, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun GamesPane(state: HomeUiState, vm: HomeViewModel, onPickFolder: () -> Unit) {
    val style = LocalWiis.current
    when (val lib = state.library) {
        LibraryStatus.NoFolder -> CenterAction("Pick the folder that holds your ROMs.", "Choose ROM folder", onPickFolder)
        is LibraryStatus.Scanning -> CenterText("Scanning… ${lib.found} games found")
        LibraryStatus.Ready -> when {
            state.shelves.isEmpty() -> CenterAction(
                "No games found. The System screen lists the files that were skipped and why.",
                "Choose another folder",
                onPickFolder,
            )
            style.spec.layout.games == "grid" -> GamesGrid(state, vm)
            else -> GamesShelves(state, vm)
        }
    }
}

/** Room around tiles for the focus ring, scale-up and lift, so nothing is clipped at a row's edges. */
@Composable
private fun focusRoom(style: WiisStyle): Pair<Int, Int> {
    val s = style.spec
    val grow = ((maxOf(s.shape.tileWidth, s.shape.tileHeight) * (s.motion.focusScale - 1f)) / 2f).toInt()
    val ring = s.shape.focusRingWidth + s.shape.focusRingGap
    val horizontal = ring + grow + 4
    val vertical = ring + grow + s.motion.focusLift + s.motion.idleBobAmplitude + 4
    return horizontal to vertical
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rowAnchorSpec(anchor: String, leadPx: Float): BringIntoViewSpec? = remember(anchor, leadPx) {
    when (anchor) {
        "start" -> object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - leadPx
        }
        "center" -> object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                offset - (containerSize - size) / 2f
        }
        else -> null
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GamesShelves(state: HomeUiState, vm: HomeViewModel) {
    val style = LocalWiis.current
    val columnState = rememberLazyListState()
    val target = state.focusTarget
    LaunchedEffect(target?.token) {
        val index = target?.let { t -> state.shelves.indexOfFirst { it.id == t.shelfId } } ?: -1
        if (index >= 0) columnState.scrollToItem(index)
    }
    val (roomH, roomV) = focusRoom(style)
    val leadPx = with(LocalDensity.current) { roomH.dp.toPx() }
    val anchorSpec = rowAnchorSpec(style.spec.layout.scrollAnchor, leadPx)

    LazyColumn(
        state = columnState,
        verticalArrangement = Arrangement.spacedBy(style.spec.spacing.shelfGap.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items(state.shelves.size, key = { state.shelves[it].id }, contentType = { "shelf" }) { i ->
            val shelf = state.shelves[i]
            val rowState = rememberLazyListState()
            val targetIndex = target
                ?.takeIf { it.shelfId == shelf.id }
                ?.let { t -> shelf.games.indexOfFirst { it.uri == t.gameUri } }
                ?.takeIf { it >= 0 }
            LaunchedEffect(target?.token) { if (targetIndex != null) rowState.scrollToItem(targetIndex) }

            Column(verticalArrangement = Arrangement.spacedBy(style.spec.spacing.shelfTitleGap.dp)) {
                ShelfTitle(shelf)
                val row = @Composable {
                    LazyRow(
                        state = rowState,
                        horizontalArrangement = Arrangement.spacedBy(style.spec.spacing.tileGap.dp),
                        contentPadding = PaddingValues(horizontal = roomH.dp, vertical = roomV.dp),
                    ) {
                        itemsIndexed(shelf.games, key = { _, g -> g.uri }, contentType = { _, _ -> "game" }) { index, game ->
                            GameTile(
                                game = game,
                                inRecent = shelf.id == HomeViewModel.RECENT_ID,
                                focusToken = if (index == targetIndex) target?.token else null,
                                onFocused = { vm.focusGame(shelf.id, game) },
                                onLaunch = { vm.launchGame(game) },
                                onPicker = { vm.openPicker(game) },
                                onFocusApplied = vm::focusConsumed,
                            )
                        }
                    }
                }
                if (anchorSpec != null) CompositionLocalProvider(LocalBringIntoViewSpec provides anchorSpec) { row() } else row()
            }
        }
        item(key = "rescan", contentType = "action") {
            ActionTile("Rescan library", onClick = vm::rescan, modifier = Modifier.padding(start = roomH.dp))
        }
    }
}

@Composable
private fun GamesGrid(state: HomeUiState, vm: HomeViewModel) {
    val style = LocalWiis.current
    val gridState = rememberLazyGridState()
    val target = state.focusTarget
    val (roomH, roomV) = focusRoom(style)
    // Flattened index of the target, counting one header per shelf.
    val targetIndex = remember(state.shelves, target) {
        var index = 0
        var found = -1
        for (shelf in state.shelves) {
            index++ // header
            val i = shelf.games.indexOfFirst { target != null && it.uri == target.gameUri && shelf.id == target.shelfId }
            if (i >= 0) { found = index + i; break }
            index += shelf.games.size
        }
        found
    }
    LaunchedEffect(target?.token) { if (targetIndex >= 0) gridState.scrollToItem(targetIndex) }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive((style.spec.shape.tileWidth + style.spec.spacing.tileGap).dp),
        contentPadding = PaddingValues(horizontal = roomH.dp, vertical = roomV.dp),
        horizontalArrangement = Arrangement.spacedBy(style.spec.spacing.tileGap.dp),
        verticalArrangement = Arrangement.spacedBy(style.spec.spacing.tileGap.dp + roomV.dp / 2),
    ) {
        state.shelves.forEach { shelf ->
            item(key = "h:${shelf.id}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") { ShelfTitle(shelf) }
            items(shelf.games, key = { "${shelf.id}:${it.uri}" }, contentType = { "game" }) { game ->
                GameTile(
                    game = game,
                    inRecent = shelf.id == HomeViewModel.RECENT_ID,
                    focusToken = if (target != null && target.shelfId == shelf.id && target.gameUri == game.uri) target.token else null,
                    onFocused = { vm.focusGame(shelf.id, game) },
                    onLaunch = { vm.launchGame(game) },
                    onPicker = { vm.openPicker(game) },
                    onFocusApplied = vm::focusConsumed,
                )
            }
        }
    }
}

@Composable
private fun ShelfTitle(shelf: Shelf) {
    val style = LocalWiis.current
    val tokens = style.spec.shelf
    val systemColor = style.systemColor(shelf.id)
    val color = when (tokens.titleColor) {
        "system" -> systemColor
        "text" -> style.text
        else -> style.textMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = focusRoom(style).first.dp)) {
        when (tokens.marker) {
            "dot" -> Box(Modifier.size(10.dp).background(systemColor, CircleShape))
            "bar" -> Box(Modifier.width(4.dp).height(18.dp).background(systemColor, RoundedCornerShape(2.dp)))
        }
        if (tokens.marker != "none") Spacer(Modifier.width(10.dp))
        Text(style.shelfTitleText(shelf.title), style = style.shelfTitle.copy(color = color))
        if (tokens.showCount) {
            Spacer(Modifier.width(10.dp))
            Text("${shelf.games.size}", style = style.label)
        }
    }
}

@Composable
private fun AppsPane(apps: List<AppEntry>, vm: HomeViewModel) {
    val style = LocalWiis.current
    val first = remember { FocusRequester() }
    LaunchedEffect(apps.isNotEmpty()) { if (apps.isNotEmpty()) runCatching { first.requestFocus() } }
    val (roomH, roomV) = focusRoom(style)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(136.dp),
        contentPadding = PaddingValues(horizontal = roomH.dp, vertical = roomV.dp),
        horizontalArrangement = Arrangement.spacedBy(style.spec.spacing.tileGap.dp),
        verticalArrangement = Arrangement.spacedBy(style.spec.spacing.tileGap.dp),
    ) {
        items(apps, key = { it.key }, contentType = { "app" }) { app ->
            WiisTile(
                onClick = { vm.launchApp(app) },
                modifier = Modifier
                    .then(if (app == apps.first()) Modifier.focusRequester(first) else Modifier)
                    .onFocusChanged { if (it.isFocused) vm.focusApp(app) },
            ) {
                Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(app)
                    Spacer(Modifier.height(8.dp))
                    Text(app.label, style = style.label.copy(color = style.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun CenterText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = LocalWiis.current.body)
    }
}

@Composable
internal fun CenterAction(text: String, action: String, onClick: () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = LocalWiis.current.body)
        Spacer(Modifier.height(20.dp))
        ActionTile(action, onClick = onClick, modifier = Modifier.focusRequester(requester), emphasized = true)
    }
}
