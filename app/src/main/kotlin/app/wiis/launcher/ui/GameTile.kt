package app.wiis.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.wiis.designsystem.LocalWiis
import app.wiis.designsystem.WiisStyle
import app.wiis.designsystem.WiisTile
import app.wiis.emulator.Game
import app.wiis.launcher.graph

/** Short system names for badges. */
fun systemShortName(systemId: String): String = when (systemId) {
    "nds" -> "DS"
    "n3ds" -> "3DS"
    "gc" -> "GC"
    "wiiu" -> "WII U"
    "genesis" -> "GENESIS"
    "psx" -> "PS1"
    "xbox360" -> "360"
    else -> systemId.uppercase()
}

@Composable
fun GameTile(
    game: Game,
    inRecent: Boolean,
    focusToken: Long?,
    onFocused: () -> Unit,
    onLaunch: () -> Unit,
    onPicker: () -> Unit,
    onFocusApplied: () -> Unit,
) {
    val style = LocalWiis.current
    val shape = style.spec.shape
    val graph = LocalContext.current.graph
    val requester = remember { FocusRequester() }
    if (focusToken != null) {
        LaunchedEffect(focusToken) {
            withFrameNanos { } // wait until the tile is laid out
            graph.sounds.quietMoves()
            runCatching { requester.requestFocus() }
            onFocusApplied()
        }
    }
    WiisTile(
        onClick = onLaunch,
        onSecondary = onPicker,
        clickSound = null, // the launch reports Launch or Error once the outcome is known
        modifier = Modifier
            .width(shape.tileWidth.dp)
            .height(shape.tileHeight.dp)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocused() },
    ) { focused ->
        TileFace(game, inRecent, focused)
    }
}

@Composable
fun TileFace(game: Game, inRecent: Boolean, focused: Boolean) {
    val style = LocalWiis.current
    val tile = style.spec.tile
    val systemColor = style.systemColor(game.systemId)
    val density = LocalDensity.current
    val sizePx = with(density) { maxOf(style.spec.shape.tileWidth, style.spec.shape.tileHeight).dp.roundToPx() }
    val fit = if (tile.artFit == "cover") ContentScale.Crop else ContentScale.Fit
    val showBadge = tile.systemBadge == "always" || (tile.systemBadge == "recent" && inRecent)
    val titleAlpha by animateFloatAsState(
        targetValue = when (tile.titleVisibility) {
            "always" -> 1f
            "focused" -> if (focused) 1f else 0f
            else -> 0f
        },
        animationSpec = tween(160),
        label = "titleAlpha",
    )

    Box(Modifier.fillMaxSize()) {
        when (tile.artStyle) {
            "title" -> TitleOnly(game, style, showBadge, systemColor)
            "card" -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f).placeholder(style, systemColor), contentAlignment = Alignment.Center) {
                    val hasArt = GameArt(game, sizePx, fit, Modifier.fillMaxSize().padding(if (fit == ContentScale.Fit) 8.dp else 0.dp))
                    if (!hasArt) PlaceholderTitle(game, style)
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(game.title, style = style.tileTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            else -> Box(Modifier.fillMaxSize().placeholder(style, systemColor), contentAlignment = Alignment.Center) {
                val hasArt = GameArt(game, sizePx, fit, Modifier.fillMaxSize().padding(if (fit == ContentScale.Fit) 10.dp else 0.dp))
                if (!hasArt) {
                    PlaceholderTitle(game, style)
                } else if (titleAlpha > 0.01f) {
                    TitleScrim(game, style, Modifier.align(Alignment.BottomStart).alpha(titleAlpha))
                }
            }
        }
        SystemStrip(style, systemColor)
        if (showBadge && tile.artStyle != "title") {
            SystemBadge(game.systemId, style, systemColor, Modifier.align(Alignment.TopStart).padding(10.dp))
        }
    }
}

private fun Modifier.placeholder(style: WiisStyle, systemColor: Color): Modifier =
    if (style.spec.tile.placeholder == "system") {
        background(Brush.linearGradient(listOf(lerp(systemColor, style.tile, 0.35f), lerp(systemColor, Color.Black, 0.45f))))
    } else {
        this
    }

@Composable
private fun PlaceholderTitle(game: Game, style: WiisStyle) {
    Text(
        game.title,
        style = style.tileTitle,
        textAlign = TextAlign.Center,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun TitleScrim(game: Game, style: WiisStyle, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
            .padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = 12.dp),
    ) {
        Text(game.title, style = style.tileTitle.copy(color = Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TitleOnly(game: Game, style: WiisStyle, showBadge: Boolean, systemColor: Color) {
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text(game.title, style = style.tileTitle, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBadge) SystemBadge(game.systemId, style, systemColor)
            if (style.spec.tile.showFileType) Text(game.fileName.substringAfterLast('.').uppercase(), style = style.label)
        }
    }
}

@Composable
private fun BoxScope.SystemStrip(style: WiisStyle, color: Color) {
    val size = style.spec.shape.systemStripSize.dp
    val m = when (style.spec.shape.systemStrip) {
        "top" -> Modifier.align(Alignment.TopCenter).fillMaxWidth().height(size)
        "bottom" -> Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(size)
        "left" -> Modifier.align(Alignment.CenterStart).fillMaxHeight().width(size)
        else -> return
    }
    Box(m.background(color))
}

@Composable
fun SystemBadge(systemId: String, style: WiisStyle, color: Color, modifier: Modifier = Modifier) {
    Text(
        systemShortName(systemId),
        style = style.label.copy(color = if (color.luminance() > 0.55f) Color(0xFF15141E) else Color.White),
        modifier = modifier.background(color, style.badgeShape).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
