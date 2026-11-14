package app.wiis.launcher.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.wiis.designsystem.LocalWiis
import app.wiis.designsystem.WiisTile
import app.wiis.emulator.EmulatorChoice
import app.wiis.launcher.ui.ActionTile
import app.wiis.launcher.ui.AppIcon
import app.wiis.system.AppEntry

/** Y on a game: which emulator to play it with. */
@Composable
fun EmulatorPickerDialog(picker: EmulatorPicker, onPick: (EmulatorChoice) -> Unit, onDismiss: () -> Unit) {
    val style = LocalWiis.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = style.surface,
        shape = style.dialogShape,
        title = { Text("Play ${picker.game.title} with…", style = style.heading.copy(fontSize = style.tileTitle.fontSize * 1.2f)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                picker.options.forEach { opt ->
                    ActionTile(
                        label = if (opt.installed) opt.name else "${opt.name} (not installed)",
                        onClick = { onPick(opt.choice) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { ActionTile("Cancel", onClick = onDismiss) },
    )
}

/** Y on a dock app: pick the app for that spot. Focus starts on the app already there. */
@Composable
fun DockPickerDialog(picker: DockPicker, current: AppEntry?, onPick: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    val style = LocalWiis.current
    val start = remember { FocusRequester() }
    val startKey = current?.key ?: picker.options.firstOrNull()?.key
    LaunchedEffect(Unit) { runCatching { start.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = style.surface,
        shape = style.dialogShape,
        title = { Text("App for this spot", style = style.heading.copy(fontSize = style.tileTitle.fontSize * 1.2f)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 260.dp),
            ) {
                items(picker.options, key = { it.key }) { app ->
                    WiisTile(
                        onClick = { onPick(app) },
                        elevated = false,
                        modifier = if (app.key == startKey) Modifier.focusRequester(start) else Modifier,
                    ) {
                        Column(Modifier.padding(10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(app, 44)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                app.label,
                                style = style.label.copy(color = style.text),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { ActionTile("Cancel", onClick = onDismiss) },
    )
}
