package app.wiis.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wiis.designsystem.ConsoleGlyph
import app.wiis.designsystem.ConsoleIcon
import app.wiis.designsystem.LocalWiis
import app.wiis.designsystem.cssLinearGradient
import app.wiis.designsystem.glass
import app.wiis.designsystem.mix
import app.wiis.emulator.Game
import kotlin.math.roundToInt

// The parts of a game card, shared by the top screen's tiles and the bottom screen's cover.

/** Box art cropped to fill, or the system-colour panel (with the title when [titleSize] is set). */
@Composable
internal fun BoxScope.Cover(game: Game, sizePx: Int, color: Color, titleSize: Int? = null) {
    val style = LocalWiis.current
    val art = rememberArt(game, sizePx)
    if (art != null) {
        Image(art.asImageBitmap(), contentDescription = game.title, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    } else {
        Box(
            Modifier
                .matchParentSize()
                .drawBehind { drawRect(cssLinearGradient(135f, size, listOf(mix(color, Color.White, 0.2f), mix(color, Color.Black, 0.45f)))) }
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (titleSize != null) {
                Text(
                    game.title,
                    style = TextStyle(
                        fontFamily = style.fonts.body,
                        fontWeight = FontWeight(style.spec.type.tileTitleWeight.coerceIn(100, 900)),
                        fontSize = titleSize.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The always-on ring in the console's colour (`tile.systemRing` dp). */
@Composable
internal fun BoxScope.SystemRing(color: Color, shape: RoundedCornerShape) {
    val ring = LocalWiis.current.spec.tile.systemRing
    if (ring > 0) Box(Modifier.matchParentSize().border(ring.dp, color, shape))
}

/** The console mark in the top-right corner: a line drawing on a glass disc, or a name badge. */
@Composable
internal fun BoxScope.ConsoleMark(systemId: String) {
    val style = LocalWiis.current
    val tile = style.spec.tile
    val color = style.systemColor(systemId)
    when (tile.consoleIcon) {
        "none" -> Unit
        "badge" -> SystemBadge(systemId, style, color, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
        // iiSU-style: the frame's top-left corner grows into a tab holding the console in white.
        "tab" -> {
            val box = tile.consoleIconSize + 10
            Box(
                Modifier.align(Alignment.TopStart).size(box.dp).background(color, RoundedCornerShape(bottomEnd = (box * 0.42f).dp)),
                contentAlignment = Alignment.Center,
            ) {
                ConsoleGlyph(systemId, (box * 0.64f).roundToInt().dp, Color.White, strokeUnits = 2.4f)
            }
        }
        else -> Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 6.dp)
                .size((tile.consoleIconSize + 8).dp)
                .glass(style, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ConsoleIcon(systemId, tile.consoleIconSize.dp, mix(color, Color.Black, 0.15f))
        }
    }
}
