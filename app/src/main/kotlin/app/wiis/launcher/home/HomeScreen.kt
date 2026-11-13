package app.wiis.launcher.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wiis.designsystem.LocalWiisTokens
import app.wiis.designsystem.WiisTile
import app.wiis.emulator.Game
import app.wiis.launcher.graph
import app.wiis.launcher.spike.SpikeScreen
import app.wiis.system.AppEntry
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { vm.onFolderPicked(it) }

    BackHandler(enabled = state.section != Section.Games) { vm.selectSection(Section.Games) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 28.dp, vertical = 16.dp)
            // Global buttons work from anywhere on the top screen.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.ButtonL1 -> { vm.cycleSection(-1); true }
                    Key.ButtonR1 -> { vm.cycleSection(+1); true }
                    Key.ButtonStart -> { vm.onStart(); true }
                    Key.ButtonSelect -> { vm.onSelect(); true }
                    else -> false
                }
            },
    ) {
        Header(state.section, onSelect = vm::selectSection)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f)) {
            when (state.section) {
                Section.Games -> GamesPane(state, vm, onPickFolder = { pickFolder.launch(null) })
                Section.Apps -> AppsPane(state.apps, vm)
                Section.Spike -> SpikeScreen(onPickFolder = { pickFolder.launch(null) })
            }
        }
        state.message?.let { MessageBar(it) }
        HintBar(state.section)
    }

    state.picker?.let { picker ->
        AlertDialog(
            onDismissRequest = vm::dismissPicker,
            title = { Text("Play ${picker.game.title} with…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    picker.options.forEach { opt ->
                        WiisTile(onClick = { vm.launchGame(picker.game, opt.choice) }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (opt.installed) opt.name else "${opt.name} (not installed)",
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::dismissPicker) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(current: Section, onSelect: (Section) -> Unit) {
    val first = remember { FocusRequester() }
    // Fallback focus so the controller always has something; a game tile takes over when shelves load.
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Wii-S", style = MaterialTheme.typography.headlineMedium, color = LocalWiisTokens.current.accent)
        Spacer(Modifier.size(12.dp))
        Section.entries.forEachIndexed { i, section ->
            WiisTile(
                onClick = { onSelect(section) },
                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
            ) {
                Text(
                    section.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (section == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Clock()
    }
}

@Composable
private fun Clock() {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000)
        }
    }
    Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun GamesPane(state: HomeUiState, vm: HomeViewModel, onPickFolder: () -> Unit) {
    when (val lib = state.library) {
        LibraryStatus.NoFolder -> CenterAction("Pick the folder that holds your ROMs", "Choose ROM folder", onPickFolder)
        is LibraryStatus.Scanning -> CenterText("Scanning… ${lib.found} games found")
        LibraryStatus.Ready -> {
            if (state.shelves.isEmpty()) {
                CenterAction(
                    "No games found. The System tab lists files that were skipped and why.",
                    "Choose another folder",
                    onPickFolder,
                )
                return
            }
            val columnState = rememberLazyListState()
            val target = state.focusTarget
            LaunchedEffect(target?.token) {
                val index = target?.let { t -> state.shelves.indexOfFirst { it.id == t.shelfId } } ?: -1
                if (index >= 0) columnState.scrollToItem(index)
            }
            LazyColumn(
                state = columnState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(state.shelves.size, key = { state.shelves[it].id }, contentType = { "shelf" }) { i ->
                    ShelfRow(state.shelves[i], target, vm)
                }
                item(key = "rescan", contentType = "action") {
                    WiisTile(onClick = vm::rescan) { Text("Rescan library", Modifier.padding(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ShelfRow(shelf: Shelf, target: FocusTarget?, vm: HomeViewModel) {
    val rowState = rememberLazyListState()
    val targetIndex = target
        ?.takeIf { it.shelfId == shelf.id }
        ?.let { t -> shelf.games.indexOfFirst { it.uri == t.gameUri } }
        ?.takeIf { it >= 0 }
    LaunchedEffect(target?.token) { if (targetIndex != null) rowState.scrollToItem(targetIndex) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "${shelf.title}  ·  ${shelf.games.size}",
            style = MaterialTheme.typography.titleMedium,
            color = LocalWiisTokens.current.muted,
        )
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            // Room for the focus scale-up so tiles at the edges aren't clipped.
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) {
            itemsIndexed(shelf.games, key = { _, g -> g.uri }, contentType = { _, _ -> "game" }) { i, game ->
                GameTile(
                    game = game,
                    showSystem = shelf.id == "recent",
                    focusToken = if (i == targetIndex) target?.token else null,
                    vm = vm,
                )
            }
        }
    }
}

@Composable
private fun GameTile(game: Game, showSystem: Boolean, focusToken: Long?, vm: HomeViewModel) {
    val requester = remember { FocusRequester() }
    if (focusToken != null) {
        LaunchedEffect(focusToken) {
            withFrameNanos { } // wait until the tile is laid out
            runCatching { requester.requestFocus() }
            vm.focusConsumed()
        }
    }
    WiisTile(
        onClick = { vm.launchGame(game) },
        onSecondary = { vm.openPicker(game) },
        modifier = Modifier
            .width(230.dp)
            .height(140.dp)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) vm.focusGame(game) },
    ) {
        Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(game.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                if (showSystem) game.systemId.uppercase() else game.fileName.substringAfterLast('.').uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = LocalWiisTokens.current.muted,
            )
        }
    }
}

@Composable
private fun AppsPane(apps: List<AppEntry>, vm: HomeViewModel) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(132.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(apps, key = { it.key }, contentType = { "app" }) { app ->
            WiisTile(
                onClick = { vm.launchApp(app) },
                modifier = Modifier.onFocusChanged { if (it.isFocused) vm.focusApp(app) },
            ) {
                Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(app)
                    Spacer(Modifier.height(8.dp))
                    Text(app.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun AppIcon(app: AppEntry, size: Int = 64) {
    val repo = LocalContext.current.graph.apps
    val bitmap by produceState<android.graphics.Bitmap?>(null, app.key) { value = repo.icon(app) }
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size.dp))
    } else {
        Spacer(Modifier.size(size.dp))
    }
}

/** Always-visible controller legend; changes with the section. */
@Composable
private fun HintBar(section: Section) {
    val hints = when (section) {
        Section.Games -> listOf("A" to "Play", "Y" to "Emulator", "L1/R1" to "Tabs", "Select" to "Apps", "Start" to "System")
        Section.Apps -> listOf("A" to "Open", "B" to "Games", "L1/R1" to "Tabs", "Select" to "Games", "Start" to "System")
        Section.Spike -> listOf("A" to "Select", "B" to "Games", "L1/R1" to "Tabs")
    }
    val tokens = LocalWiisTokens.current
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEach { (button, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    button,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .background(tokens.muted, if (button.length == 1) CircleShape else RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = tokens.muted)
            }
        }
    }
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CenterAction(text: String, action: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))
        WiisTile(onClick = onClick) { Text(action, Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) }
    }
}

@Composable
private fun MessageBar(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(14.dp),
    )
}
