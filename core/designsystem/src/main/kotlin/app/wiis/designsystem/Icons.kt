package app.wiis.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * Original line drawings of console shapes (no makers' logos), drawn on a 24-unit grid.
 * They mirror consoleIcon() in design/workbench.html shape for shape.
 */
enum class ConsoleShape { Clamshell, Portrait, Landscape, Tablet, Cartridge, Cube, Slim, Lid, Stack, Pad }

fun consoleShapeFor(systemId: String): ConsoleShape = when (systemId) {
    "nds", "n3ds" -> ConsoleShape.Clamshell
    "gb", "gbc" -> ConsoleShape.Portrait
    "gba", "psp" -> ConsoleShape.Landscape
    "switch" -> ConsoleShape.Tablet
    "nes", "snes", "n64", "genesis" -> ConsoleShape.Cartridge
    "gc" -> ConsoleShape.Cube
    "wii", "wiiu", "ps2", "xbox360" -> ConsoleShape.Slim
    "psx" -> ConsoleShape.Lid
    "all" -> ConsoleShape.Stack
    else -> ConsoleShape.Pad
}

/** Drawing helpers in 24-unit grid coordinates. */
private class Grid(val scope: DrawScope, val color: Color, strokeUnits: Float) {
    val u = scope.size.minDimension / 24f
    val stroke = Stroke(width = strokeUnits * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        scope.drawRoundRect(color, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u), style = stroke)

    fun fillRect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        scope.drawRoundRect(color, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u))

    fun circle(cx: Float, cy: Float, r: Float) = scope.drawCircle(color, r * u, Offset(cx * u, cy * u), style = stroke)

    fun dot(cx: Float, cy: Float, r: Float) = scope.drawCircle(color, r * u, Offset(cx * u, cy * u))

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        scope.drawLine(color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), stroke.width, StrokeCap.Round)

    fun arc(cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float) =
        scope.drawArc(color, startDeg, sweepDeg, false, Offset((cx - r) * u, (cy - r) * u), Size(2 * r * u, 2 * r * u), style = stroke)
}

@Composable
fun ConsoleIcon(systemId: String, size: Dp, color: Color, modifier: Modifier = Modifier, strokeUnits: Float = 1.7f) {
    val shape = consoleShapeFor(systemId)
    Canvas(modifier.size(size)) {
        with(Grid(this, color, strokeUnits)) {
            when (shape) {
                ConsoleShape.Clamshell -> { rect(4f, 2.5f, 16f, 9f, 2f); rect(7f, 4.5f, 10f, 5f, 0.8f); rect(4f, 13f, 16f, 8.5f, 2f); rect(8f, 14.8f, 8f, 4.6f, 0.8f) }
                ConsoleShape.Portrait -> { rect(6f, 2f, 12f, 20f, 2.2f); rect(8f, 4f, 8f, 7f, 0.8f); line(9.5f, 14.5f, 9.5f, 17.5f); line(8f, 16f, 11f, 16f); circle(15f, 15.2f, 0.9f); circle(16.6f, 17.2f, 0.9f) }
                ConsoleShape.Landscape -> { rect(2f, 7f, 20f, 11f, 4f); rect(8f, 9f, 8f, 7f, 0.8f); line(4.8f, 12.5f, 7.2f, 12.5f); line(6f, 11.3f, 6f, 13.7f); circle(18.2f, 11.6f, 0.9f); circle(19.6f, 13.8f, 0.9f) }
                ConsoleShape.Tablet -> { rect(5.5f, 6f, 13f, 12f, 1.2f); rect(2f, 6f, 3.5f, 12f, 1.75f); rect(18.5f, 6f, 3.5f, 12f, 1.75f); circle(3.75f, 9f, 0.7f); circle(20.25f, 13.5f, 0.7f) }
                ConsoleShape.Cartridge -> { rect(3f, 11f, 18f, 8.5f, 2f); rect(8f, 5f, 8f, 6f, 1f); line(6f, 15.5f, 9f, 15.5f); line(15f, 15.5f, 18f, 15.5f) }
                ConsoleShape.Cube -> { rect(4.5f, 4.5f, 15f, 15f, 3f); circle(12f, 12f, 3.6f) }
                ConsoleShape.Slim -> { rect(8f, 2f, 8f, 20f, 2f); line(10f, 6f, 14f, 6f); circle(12f, 17.5f, 1.2f) }
                ConsoleShape.Lid -> { rect(3f, 7f, 18f, 12f, 2f); circle(10f, 13f, 3.6f); line(15.5f, 11f, 18f, 11f); line(15.5f, 14f, 18f, 14f) }
                ConsoleShape.Stack -> { rect(7f, 3f, 13f, 13f, 2.5f); rect(4f, 8f, 13f, 13f, 2.5f) }
                ConsoleShape.Pad -> { rect(2.5f, 8f, 19f, 9f, 4.5f); line(6.5f, 12.5f, 9.5f, 12.5f); line(8f, 11f, 8f, 14f); circle(15.5f, 11.6f, 0.9f); circle(17.6f, 13.6f, 0.9f) }
            }
        }
    }
}

/** Icons for the three sections: "games" (home), "apps" and "system". */
@Composable
fun SectionIcon(sectionId: String, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        with(Grid(this, color, 1.8f)) {
            when (sectionId) {
                "games" -> {
                    val p = Path().apply {
                        moveTo(4f * u, 11.5f * u); lineTo(12f * u, 4.5f * u); lineTo(20f * u, 11.5f * u); lineTo(20f * u, 20f * u)
                        lineTo(14.5f * u, 20f * u); lineTo(14.5f * u, 14.5f * u); lineTo(9.5f * u, 14.5f * u); lineTo(9.5f * u, 20f * u)
                        lineTo(4f * u, 20f * u); close()
                    }
                    scope.drawPath(p, color, style = stroke)
                }
                "apps" -> { rect(4f, 4f, 6.5f, 6.5f, 1.6f); rect(13.5f, 4f, 6.5f, 6.5f, 1.6f); rect(4f, 13.5f, 6.5f, 6.5f, 1.6f); rect(13.5f, 13.5f, 6.5f, 6.5f, 1.6f) }
                // Collections: a card in front of another, the back one's edge showing above and to the right.
                "collections" -> {
                    rect(3.5f, 8.5f, 12.5f, 12f, 2f)
                    val back = Path().apply {
                        moveTo(7.5f * u, 8.5f * u); lineTo(7.5f * u, 6.2f * u)
                        quadraticTo(7.5f * u, 4f * u, 9.7f * u, 4f * u); lineTo(18.3f * u, 4f * u)
                        quadraticTo(20.5f * u, 4f * u, 20.5f * u, 6.2f * u); lineTo(20.5f * u, 14.3f * u)
                        quadraticTo(20.5f * u, 16.5f * u, 18.3f * u, 16.5f * u); lineTo(16f * u, 16.5f * u)
                    }
                    scope.drawPath(back, color, style = stroke)
                }
                else -> { line(5f, 7f, 19f, 7f); line(5f, 12f, 19f, 12f); line(5f, 17f, 19f, 17f); dot(9f, 7f, 2f); dot(15f, 12f, 2f); dot(10f, 17f, 2f) }
            }
        }
    }
}

@Composable
fun WifiIcon(size: Dp, color: Color, connected: Boolean, modifier: Modifier = Modifier) {
    val c = if (connected) color else color.copy(alpha = color.alpha * 0.35f)
    Canvas(modifier.size(size)) {
        with(Grid(this, c, 2f)) {
            arc(12f, 19f, 13f, -135f, 90f)
            arc(12f, 19f, 8.5f, -135f, 90f)
            arc(12f, 19f, 4f, -135f, 90f)
            dot(12f, 19f, 1.2f)
        }
    }
}

@Composable
fun BatteryIcon(size: Dp, color: Color, percent: Int?, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        with(Grid(this, color, 1.6f)) {
            rect(2.5f, 7.5f, 17f, 9f, 2f)
            line(21.5f, 10.5f, 21.5f, 13.5f)
            val level = (percent ?: 0).coerceIn(0, 100) / 100f
            if (level > 0f) fillRect(4.5f, 9.5f, 13f * level, 5f, 1f)
        }
    }
}

/**
 * Bold, simplified console shapes for XMB icons and corner tabs (original drawings, no makers'
 * logos). Same shapes and path data as consoleGlyph() in design/workbench.html, 24-unit grid.
 * [brush] (a gradient) wins over [color] when given.
 */
@Composable
fun ConsoleGlyph(systemId: String, size: Dp, color: Color, modifier: Modifier = Modifier, strokeUnits: Float = 2.2f, brush: Brush? = null) {
    val shape = consoleShapeFor(systemId)
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(width = strokeUnits * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val paint: Brush = brush ?: androidx.compose.ui.graphics.SolidColor(color)
        fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
            drawRoundRect(paint, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u), style = stroke)
        fun circle(cx: Float, cy: Float, r: Float) = drawCircle(paint, r * u, Offset(cx * u, cy * u), style = stroke)
        fun path(d: String) {
            val p = PathParser().parsePathString(d).toPath()
            p.transform(androidx.compose.ui.graphics.Matrix().apply { scale(u, u) })
            drawPath(p, paint, style = stroke)
        }
        when (shape) {
            ConsoleShape.Clamshell -> { rect(4.5f, 3f, 15f, 8f, 2.2f); rect(5.5f, 13.5f, 13f, 7.5f, 2.2f) }
            ConsoleShape.Portrait -> { rect(6.5f, 2.5f, 11f, 19f, 2.5f); rect(9f, 5.5f, 6f, 5.5f, 1f) }
            ConsoleShape.Landscape -> { rect(2.5f, 7f, 19f, 10.5f, 4f); rect(8.5f, 9.5f, 7f, 5.5f, 1f) }
            ConsoleShape.Tablet -> { rect(6.5f, 5.5f, 11f, 13f, 1.5f); path("M6.5 5.5H5A2.5 2.5 0 0 0 2.5 8v8A2.5 2.5 0 0 0 5 18.5h1.5M17.5 5.5H19A2.5 2.5 0 0 1 21.5 8v8a2.5 2.5 0 0 1-2.5 2.5h-1.5") }
            ConsoleShape.Cartridge -> { path("M6.5 4.5h11v6h3v9h-17v-9h3z"); path("M9.5 7.5h5") }
            ConsoleShape.Cube -> { rect(4f, 4f, 16f, 16f, 3.5f); circle(12f, 12f, 3.8f) }
            ConsoleShape.Slim -> { rect(8f, 2.5f, 8f, 19f, 2.2f); path("M10.5 6.5h3") }
            ConsoleShape.Lid -> { rect(3f, 6.5f, 18f, 12.5f, 2.2f); circle(12f, 12.75f, 3.6f) }
            ConsoleShape.Stack -> { rect(3.5f, 8.5f, 12f, 12f, 2.6f); path("M8.5 8.5V5.6c0-1.2.9-2.1 2.1-2.1h7.8c1.2 0 2.1.9 2.1 2.1v7.8c0 1.2-.9 2.1-2.1 2.1h-2.9") }
            ConsoleShape.Pad -> { rect(2.5f, 8f, 19f, 9.5f, 4.75f); path("M7.5 10.75v4M5.5 12.75h4M15.5 11.5h.01M18 13.75h.01") }
        }
    }
}


/**
 * Line icons for the tiles below Home (original drawings on the 24-unit grid, the same path data
 * as tileIcon() in design/workbench.html): "wifi", "bluetooth", "display", "sound", "settings",
 * "clock", "week", "trophy", "dice", "play", "plus", "code" (HTML widgets), "widget" (Android widgets).
 */
val TILE_ICON_PATHS: Map<String, String> = mapOf(
    "wifi" to "M3.2 9.2a12.5 12.5 0 0 1 17.6 0M6.3 12.4a8 8 0 0 1 11.4 0M9.4 15.6a3.6 3.6 0 0 1 5.2 0M12 19.2h.01",
    "bluetooth" to "M7 7.5l10 9-5 4.5V3l5 4.5-10 9",
    "display" to "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8M12 2.8v2.2M12 19v2.2M2.8 12H5M19 12h2.2M5.5 5.5l1.6 1.6M16.9 16.9l1.6 1.6M5.5 18.5l1.6-1.6M16.9 7.1l1.6-1.6",
    "sound" to "M4 9.5h3.5L12 5.5v13l-4.5-4H4zM15.5 9a4.2 4.2 0 0 1 0 6M18.2 6.4a8 8 0 0 1 0 11.2",
    "settings" to "M4 7h8.5M16.5 7H20M4 17h3.5M11.5 17H20M14.5 5v4M9.5 15v4",
    "clock" to "M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17M12 7.5V12l3 2",
    "week" to "M5.5 19.5v-5M10 19.5v-9M14.5 19.5v-7M19 19.5V6",
    "trophy" to "M8 4.5h8v5a4 4 0 0 1-8 0zM8 6.5H5.5a2.5 2.5 0 0 0 2.6 4M16 6.5h2.5a2.5 2.5 0 0 1-2.6 4M12 13.5v3.5M8.5 19.5h7",
    "dice" to "M8 4.5h8a3.5 3.5 0 0 1 3.5 3.5v8a3.5 3.5 0 0 1-3.5 3.5H8A3.5 3.5 0 0 1 4.5 16V8A3.5 3.5 0 0 1 8 4.5zM9 9h.01M12 12h.01M15 15h.01",
    "play" to "M8.5 5.8v12.4a1 1 0 0 0 1.5.9l9.6-6.2a1 1 0 0 0 0-1.7L10 4.9a1 1 0 0 0-1.5.9z",
    "plus" to "M12 6v12M6 12h12",
    "code" to "M8.5 8l-4 4 4 4M15.5 8l4 4-4 4M13.5 5.5l-3 13",
    "widget" to "M4 4h6.5v6.5H4zM13.5 4H20v6.5h-6.5zM4 13.5h6.5V20H4zM16.75 13.5v6.5M13.5 16.75H20",
)

@Composable
fun TileIcon(id: String, size: Dp, color: Color, modifier: Modifier = Modifier, strokeUnits: Float = 1.8f) {
    val d = TILE_ICON_PATHS[id] ?: TILE_ICON_PATHS.getValue("settings")
    val path = androidx.compose.runtime.remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val p = Path().apply { addPath(path) }
        p.transform(androidx.compose.ui.graphics.Matrix().apply { scale(u, u) })
        drawPath(p, color, style = Stroke(width = strokeUnits * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
