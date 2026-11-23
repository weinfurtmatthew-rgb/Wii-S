package app.wiis.launcher.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wiis.designsystem.LocalWiis
import app.wiis.designsystem.WiisTile
import app.wiis.designsystem.glassBox
import app.wiis.launcher.ui.ActionTile

/**
 * A popup over the dimmed top screen: the Select menu, the settings, small pickers. Focus stays
 * inside it until it closes (B, or [onDismiss] from the caller); the first item takes focus.
 */
@Composable
fun PopupLayer(onDismiss: () -> Unit, width: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val style = LocalWiis.current
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (style.glassIsDark) 0.55f else 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier
                .width(width)
                // B closes it here, before Android turns B into Back (and in tests, which have no Back).
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Back || e.key == Key.Escape)) { onDismiss(); true } else false
                }
                // Focus doesn't leave the popup: moving past its edge stays on the last item.
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup()
                .glassBox(style, RoundedCornerShape(style.spec.shape.dialogRadius.coerceAtMost(28).dp))
                .padding(18.dp),
        ) { content() }
    }
}

/** One line of the Select menu: what it does, the button that does it directly, and whether a line goes above it. */
data class MenuEntry(val label: String, val button: String? = null, val lineAbove: Boolean = false, val run: () -> Unit)

/**
 * Select on anything: what you can do with it (pin, add to a collection, pick an emulator…), then
 * "Wii-S settings" at the bottom, as in iiSU's quick settings.
 */
@Composable
fun QuickMenu(title: String, subtitle: String, entries: List<MenuEntry>, onDismiss: () -> Unit) {
    val style = LocalWiis.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    PopupLayer(onDismiss, 380.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PopupTitle(title, subtitle)
            entries.forEachIndexed { i, entry ->
                if (entry.lineAbove) Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(style.glassText.copy(alpha = 0.12f)))
                MenuRow(entry.label, entry.button, if (i == 0) Modifier.focusRequester(first) else Modifier) { entry.run() }
            }
        }
    }
}

@Composable
fun PopupTitle(title: String, subtitle: String) {
    val style = LocalWiis.current
    val ty = style.spec.type
    Column(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 10.dp)) {
        Text(
            title,
            style = TextStyle(fontFamily = style.fonts.display, fontWeight = FontWeight(ty.tabWeight.coerceIn(100, 900)), fontSize = (ty.tileTitleSize + 2).sp, color = style.glassText),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotEmpty()) Text(subtitle, style = style.label.copy(color = style.glassTextMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A row in a popup list: the light highlight when focused, like the dock; the button's hint on the
 * right, or a muted [detail]; an icon before the label when [leading] is given.
 */
@Composable
fun MenuRow(
    label: String,
    button: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    detail: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val style = LocalWiis.current
    val shape = RoundedCornerShape(14.dp)
    WiisTile(
        onClick = onClick,
        shape = shape,
        fill = if (selected) style.glassText.copy(alpha = 0.08f) else Color.Transparent,
        fillFocused = style.glassText.copy(alpha = 0.14f),
        ring = false,
        elevated = false,
        grow = false,
        modifier = modifier.fillMaxWidth().height(44.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { leading(); Box(Modifier.width(10.dp)) }
            Text(
                label,
                style = TextStyle(fontFamily = style.fonts.body, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, fontSize = 15.sp, color = style.glassText),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (detail != null) Text(detail, style = style.label.copy(color = style.glassTextMuted), maxLines = 1, modifier = Modifier.padding(start = 10.dp))
            if (button != null) HintBadgeOnly(button)
        }
    }
}

/** Add to collection…: your collections and the series Wii-S offers, then a new one. */
@Composable
fun CollectionPicker(gameTitle: String, collections: List<CollectionEntry>, onPick: (CollectionEntry) -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    PopupLayer(onDismiss, 400.dp) {
        Column {
            PopupTitle("Add to collection", gameTitle)
            Column(Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MenuRow("New collection…", null, Modifier.focusRequester(first), onClick = onNew)
                collections.forEach { c ->
                    MenuRow(if (c.suggested) "${c.name} (suggested)" else c.name, null) { onPick(c) }
                }
            }
        }
    }
}

/** Name a collection: the Android keyboard opens on the field; Save or the keyboard's Done keeps it. */
@Composable
fun NameEntry(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val style = LocalWiis.current
    var name by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { field.requestFocus() } }
    PopupLayer(onDismiss, 420.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PopupTitle(title, "")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                singleLine = true,
                textStyle = style.body.copy(color = style.glassText),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(name) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = style.glassText, unfocusedTextColor = style.glassText,
                    focusedBorderColor = style.glassText, unfocusedBorderColor = style.glassTextMuted, cursorColor = style.glassText,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(field),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Save", onClick = { onSave(name) }, emphasized = true)
                ActionTile("Cancel", onClick = onDismiss)
            }
        }
    }
}

/** The badge of a button hint without its label, for the end of a menu row. */
@Composable
private fun HintBadgeOnly(button: String) {
    val style = LocalWiis.current
    Box(
        Modifier.height(22.dp).background(style.hintColor(button), if (button.length == 1) CircleShape else RoundedCornerShape(7.dp)).padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(button, style = style.label.copy(color = style.hintBadgeText), maxLines = 1)
    }
}
