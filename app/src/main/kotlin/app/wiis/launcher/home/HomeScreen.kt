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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
            // Shoulder buttons page between sections from anywhere on the screen.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.ButtonL1 -> { vm.cycleSection(-1); true }
                    Key.ButtonR1 -> { vm.cycleSection(+1); true }
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
                    section.name,
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
        is LibraryStatus.Ready -> {
            if (lib.games.isEmpty()) {
                CenterAction("No games found. Put ROMs in folders named after the system (snes, psp, ps2…).", "Choose another folder", onPickFolder)
                return
            }
            val grouped = remember(lib.games) { lib.games.groupBy { it.systemId } }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(200.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                grouped.forEach { (systemId, games) ->
                    item(key = "h:$systemId", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                        Text(
                            state.systemNames[systemId] ?: systemId,
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalWiisTokens.current.muted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(games, key = { it.uri }, contentType = { "game" }) { game ->
                        GameTile(game, vm)
                    }
                }
                item(key = "rescan", contentType = "action") {
                    WiisTile(onClick = vm::rescan) { Text("Rescan library", Modifier.padding(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun GameTile(game: Game, vm: HomeViewModel) {
    WiisTile(
        onClick = { vm.launchGame(game) },
        onSecondary = { vm.openPicker(game) },
        modifier = Modifier
            .height(120.dp)
            .onFocusChanged { if (it.isFocused) vm.focusGame(game) },
    ) {
        Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(game.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(game.fileName.substringAfterLast('.').uppercase(), style = MaterialTheme.typography.labelMedium, color = LocalWiisTokens.current.muted)
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
