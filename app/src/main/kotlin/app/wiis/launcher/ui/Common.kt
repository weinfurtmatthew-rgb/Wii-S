package app.wiis.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wiis.designsystem.LocalWiis
import app.wiis.designsystem.WiisTheme
import app.wiis.designsystem.WiisTile
import app.wiis.emulator.Game
import app.wiis.launcher.graph
import app.wiis.launcher.sound.AppFeedback
import app.wiis.system.AppEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Applies the active theme (colours, fonts, sounds, haptics) to everything inside. */
@Composable
fun WiisRoot(content: @Composable () -> Unit) {
    val graph = LocalContext.current.graph
    val spec by graph.themes.spec.collectAsStateWithLifecycle()
    val fonts by graph.fonts.fonts.collectAsStateWithLifecycle()
    val view = LocalView.current
    val feedback = remember(view, spec.haptics) { AppFeedback(graph.sounds, view, spec.haptics) }
    val images = remember(graph) { GraphImageSource(graph) }
    CompositionLocalProvider(LocalImageSource provides images) {
        WiisTheme(spec = spec, fonts = fonts, feedback = feedback, content = content)
    }
}

/** Box art for [game], or nothing (returns false) when none is cached yet. Reloads when art arrives. */
@Composable
fun GameArt(game: Game, sizePx: Int, fit: ContentScale, modifier: Modifier = Modifier): Boolean {
    val bmp = rememberArt(game, sizePx) ?: return false
    Image(bmp.asImageBitmap(), contentDescription = game.title, contentScale = fit, modifier = modifier)
    return true
}

/** Box art for [game] at about [sizePx], reloading when the art arrives later; null while there is none. */
@Composable
fun rememberArt(game: Game?, sizePx: Int): Bitmap? {
    val images = LocalImageSource.current
    val uri = game?.uri
    var version by remember(uri) { mutableIntStateOf(0) }
    LaunchedEffect(uri, images) { if (uri != null) images.artUpdates.filter { it == uri }.collect { version++ } }
    val bitmap by produceState<Bitmap?>(null, uri, version, sizePx, images) { value = game?.let { images.art(it, sizePx) } }
    return bitmap
}

@Composable
fun AppIcon(app: AppEntry, size: Int = 64) {
    val images = LocalImageSource.current
    val bitmap by produceState<Bitmap?>(null, app.key, images) { value = images.icon(app) }
    val bmp = bitmap
    if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size.dp))
    else Spacer(Modifier.size(size.dp))
}

@Composable
fun ClockText(textStyle: TextStyle = LocalWiis.current.clock) {
    Text(rememberClockText(), style = textStyle, maxLines = 1)
}

/** The time as the theme formats it, updated on the minute. */
@Composable
fun rememberClockText(): String {
    val header = LocalWiis.current.spec.header
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000)
        }
    }
    val time = SimpleDateFormat(if (header.clock24h) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(now))
    return if (header.showDate) "${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(now))}  $time" else time
}

/** Controller legend, styled by the theme's hint tokens. */
@Composable
fun HintBar(hints: List<Pair<String, String>>, modifier: Modifier = Modifier, fill: Boolean = true) {
    val style = LocalWiis.current
    val tokens = style.spec.hints
    if (!tokens.show || hints.isEmpty()) return
    val arrangement = when (tokens.align) {
        "start" -> Arrangement.spacedBy(18.dp, Alignment.Start)
        "center" -> Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)
        else -> Arrangement.spacedBy(18.dp, Alignment.End)
    }
    Row(if (fill) modifier.fillMaxWidth() else modifier, horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
        hints.forEach { (button, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val shape = when {
                    tokens.badgeShape == "square" -> RoundedCornerShape(3.dp)
                    tokens.badgeShape == "rounded" || button.length > 1 -> RoundedCornerShape(7.dp)
                    else -> CircleShape
                }
                Box(
                    Modifier
                        .height(22.dp)
                        .then(if (button.length == 1) Modifier.width(22.dp) else Modifier)
                        .background(style.hintColor(button), shape)
                        .padding(horizontal = if (button.length == 1) 0.dp else 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(button, style = style.label.copy(color = style.hintBadgeText))
                }
                Spacer(Modifier.width(6.dp))
                Text(label, style = style.label)
            }
        }
    }
}

@Composable
fun MessageBar(text: String, modifier: Modifier = Modifier) {
    val style = LocalWiis.current
    Text(
        text,
        style = style.body,
        modifier = modifier
            .fillMaxWidth()
            .background(style.messageBar, style.cardShape)
            .padding(14.dp),
    )
}

/** A text button for menus and settings: a tile with a label. */
@Composable
fun ActionTile(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    val style = LocalWiis.current
    WiisTile(
        onClick = onClick,
        modifier = modifier,
        shape = style.cardShape,
        fill = if (emphasized) style.accent else style.tile,
        fillFocused = if (emphasized) style.accent else style.tileFocused,
        elevated = false,
    ) {
        Text(
            label,
            style = style.body.copy(color = if (emphasized) style.tabActiveText else style.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
        )
    }
}

fun formatLastPlayed(millis: Long, now: Long = System.currentTimeMillis()): String {
    if (millis <= 0) return "Never"
    val days = ((now - millis) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "Today"
        days == 1 -> "Yesterday"
        days < 7 -> "$days days ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
    }
}
