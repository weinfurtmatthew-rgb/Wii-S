package app.wiis.launcher.companion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/**
 * The bottom-screen pages' icons: original line drawings on a 24-unit grid, the same as uiIcon()
 * and trophySvg() in design/workbench.html.
 */
enum class PageIcon { Details, Stats, Trophy, Clock, Play, Calendar, Repeat, Games, Trend, History, Lock, Check, Star, Flag, Chevron }

private class Drawing(val strokes: List<Path> = emptyList(), val fills: List<Path> = emptyList(), val dots: List<Triple<Float, Float, Float>> = emptyList())

private fun p(d: String): Path = PathParser().parsePathString(d).toPath()

/** Parsed once: drawing a frame never parses path text. */
private val DRAWINGS: Map<PageIcon, Drawing> by lazy {
    mapOf(
        PageIcon.Details to Drawing(listOf(p("M8 4h8a4 4 0 0 1 4 4v8a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4V8a4 4 0 0 1 4-4z"), p("M12 11v5.2")), dots = listOf(Triple(12f, 7.9f, 1.1f))),
        PageIcon.Stats to Drawing(listOf(p("M6 19.5v-7M12 19.5v-14M18 19.5v-10"))),
        PageIcon.Trophy to Drawing(listOf(p("M7.5 4.5h9v4.8a4.5 4.5 0 0 1-9 0z"), p("M7.5 6.5H5.2a2.6 2.6 0 0 0 2.6 4M16.5 6.5h2.3a2.6 2.6 0 0 1-2.6 4"), p("M12 13.8v3.4M8.8 20h6.4"))),
        PageIcon.Clock to Drawing(listOf(p("M12 3.5a8.5 8.5 0 1 1 0 17a8.5 8.5 0 1 1 0-17z"), p("M12 7.6V12l3 1.9"))),
        PageIcon.Play to Drawing(fills = listOf(p("M8.5 5.8v12.4a.7.7 0 0 0 1.05.6l9.9-6.2a.7.7 0 0 0 0-1.2l-9.9-6.2a.7.7 0 0 0-1.05.6z"))),
        PageIcon.Calendar to Drawing(listOf(p("M7 5.5h10a3 3 0 0 1 3 3v8.5a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V8.5a3 3 0 0 1 3-3z"), p("M4 10.2h16M8.5 3.5v4M15.5 3.5v4"))),
        PageIcon.Repeat to Drawing(listOf(p("M16.5 3.8 19.6 7l-3.1 3.2"), p("M19.4 7H9.2A5.2 5.2 0 0 0 4 12.2"), p("M7.5 20.2 4.4 17l3.1-3.2"), p("M4.6 17h10.2a5.2 5.2 0 0 0 5.2-5.2"))),
        PageIcon.Games to Drawing(listOf(p("M7.75 7h8.5a5.25 5.25 0 0 1 0 10.5h-8.5a5.25 5.25 0 0 1 0-10.5z"), p("M7.3 10.6v3.6M5.5 12.4h3.6")), dots = listOf(Triple(15.4f, 11.2f, 1f), Triple(17.6f, 13.6f, 1f))),
        PageIcon.Trend to Drawing(listOf(p("M4 17.5 9.2 12l3.8 3 7-8"), p("M15.2 7H20v4.8"))),
        PageIcon.History to Drawing(listOf(p("M4.6 12a7.6 7.6 0 1 0 2.2-5.4"), p("M4.4 4.6v3.9h3.9"), p("M12 8.2V12l2.8 1.7"))),
        PageIcon.Lock to Drawing(listOf(p("M7.9 10.8h8.2a2.4 2.4 0 0 1 2.4 2.4v4.4a2.4 2.4 0 0 1-2.4 2.4H7.9a2.4 2.4 0 0 1-2.4-2.4v-4.4a2.4 2.4 0 0 1 2.4-2.4z"), p("M8.5 10.8V8.3a3.5 3.5 0 0 1 7 0v2.5"))),
        PageIcon.Check to Drawing(listOf(p("M12 3.5a8.5 8.5 0 1 1 0 17a8.5 8.5 0 1 1 0-17z"), p("M8.6 12.3l2.3 2.3 4.6-4.9"))),
        PageIcon.Star to Drawing(listOf(p("M12 3.8l2.5 5.1 5.6.8-4.05 3.95.95 5.6L12 16.6l-5 2.65.95-5.6L3.9 9.7l5.6-.8z"))),
        PageIcon.Flag to Drawing(listOf(p("M6.5 20.5V4"), p("M6.5 4.6h10.8l-2.2 3.9 2.2 3.9H6.5"))),
        PageIcon.Chevron to Drawing(listOf(p("M7.5 10l4.5 4.5 4.5-4.5"))),
    )
}

private val TROPHY_CUP by lazy { p("M7 3.8h10v5.6a5 5 0 0 1-10 0z") }
private val TROPHY_HANDLES by lazy { p("M7.2 5.8H4.6a2.9 2.9 0 0 0 3.2 4.7M16.8 5.8h2.6a2.9 2.9 0 0 1-3.2 4.7") }
private val TROPHY_STEM by lazy { p("M10.6 14h2.8v3.4h-2.8z") }
private val TROPHY_SHINE by lazy { p("M9.4 5.6v3.6a2.6 2.6 0 0 0 1.1 2.1") }

private inline fun DrawScope.onGrid(block: DrawScope.() -> Unit) = scale(size.minDimension / 24f, pivot = Offset.Zero, block = block)

@Composable
fun PageIconView(icon: PageIcon, size: Dp, color: Color, modifier: Modifier = Modifier, strokeUnits: Float = 2.2f) {
    Canvas(modifier.size(size)) {
        val d = DRAWINGS.getValue(icon)
        onGrid {
            val stroke = Stroke(width = strokeUnits, cap = StrokeCap.Round, join = StrokeJoin.Round)
            d.strokes.forEach { drawPath(it, color, style = stroke) }
            d.fills.forEach { drawPath(it, color, style = Fill) }
            d.dots.forEach { (x, y, r) -> drawCircle(color, r, Offset(x, y)) }
        }
    }
}

/** A filled trophy in a rarity colour (gold, silver or bronze). */
@Composable
fun TrophyIcon(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        onGrid {
            drawPath(TROPHY_CUP, color)
            drawPath(TROPHY_HANDLES, color, style = Stroke(width = 1.9f, cap = StrokeCap.Round))
            drawPath(TROPHY_STEM, color)
            drawRoundRect(color, Offset(7.6f, 17.6f), Size(8.8f, 2.8f), CornerRadius(1.1f))
            drawPath(TROPHY_SHINE, Color.White.copy(alpha = 0.45f), style = Stroke(width = 1.3f, cap = StrokeCap.Round))
        }
    }
}
