package app.wiis.designsystem

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.offset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Frosted-glass surface: the theme's glass colour at its opacity, with a bright edge.
 * The art background behind it is already blurred, so translucency reads as frosted glass
 * without a per-panel backdrop blur (which Android doesn't offer for in-window content).
 */
fun Modifier.glass(style: WiisStyle, shape: Shape = style.glassShape): Modifier {
    val g = style.spec.glass
    return background(style.glassFill, shape)
        .then(if (g.borderWidth > 0f) Modifier.border(g.borderWidth.dp, style.glassBorder, shape) else Modifier)
}

/**
 * [glass] for a box that takes its size from its content (a pill, a dock, a panel). As in the
 * workbench (CSS border-box), the border adds to the box's size instead of covering the content.
 */
fun Modifier.glassBox(style: WiisStyle, shape: Shape = style.glassShape): Modifier =
    glass(style, shape).padding(style.spec.glass.borderWidth.coerceAtLeast(0f).dp)

/**
 * CSS `line-height: <multiplier>` between lines of a wrapped text (for a single line, see [lineBox]).
 */
fun TextStyle.cssLineHeight(multiplier: Float): TextStyle =
    copy(lineHeight = multiplier.em, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None))

/**
 * CSS `line-height` for one line of text: the box is [lineHeight] tall and the text's own box is
 * centred in it, overflowing when the font is taller, exactly as a browser lays out one line.
 */
fun Modifier.lineBox(lineHeight: TextUnit): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val h = lineHeight.toPx().roundToInt()
    layout(placeable.width, h) { placeable.place(0, (h - placeable.height) / 2) }
}

/**
 * Lets a clipping container (a scrolling grid or row) draw [horizontal]/[vertical] beyond its
 * layout bounds, so a focused tile can grow and lift without being cut off at the edges.
 * Pair it with the same amount of inner padding.
 */
fun Modifier.bleed(horizontal: Dp, vertical: Dp): Modifier = layout { measurable, constraints ->
    val h = horizontal.roundToPx()
    val v = vertical.roundToPx()
    val placeable = measurable.measure(constraints.offset(2 * h, 2 * v))
    layout((placeable.width - 2 * h).coerceAtLeast(0), (placeable.height - 2 * v).coerceAtLeast(0)) {
        placeable.place(-h, -v)
    }
}

/** The workbench's `mix()`: straight per-channel blend in sRGB (Compose's lerp works in Oklab, which shifts hues). */
fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/** CSS `linear-gradient(<angle>deg, …)` for a box of [size]: same direction and gradient-line length as a browser. */
fun cssLinearGradient(angleDeg: Float, size: Size, colors: List<Color>): Brush {
    val rad = Math.toRadians(angleDeg.toDouble())
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    return Brush.linearGradient(colors, start = center - Offset(dx, dy) * half, end = center + Offset(dx, dy) * half)
}

/** CSS `box-shadow: 0 [offsetY] [blur] [color]`: drawn only outside [shape], never under it. */
fun Modifier.boxShadow(shape: Shape, offsetY: Dp, blur: Dp, color: Color): Modifier = drawBehind {
    drawBoxShadow(shape, offsetY.toPx(), blur.toPx(), color)
}

/** Draws a CSS-style outer shadow of [shape] (blur radius [blurPx] in CSS terms, i.e. sigma = blurPx / 2). */
fun DrawScope.drawBoxShadow(shape: Shape, offsetYPx: Float, blurPx: Float, color: Color) {
    val sigma = blurPx / 2f
    // Skia's blur radius r has sigma 0.57735r + 0.5.
    val radius = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.5f)
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBoxShadow)) }
    val paint = Paint().asFrameworkPaint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
    }
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.clipPath(path, ClipOp.Difference)
        canvas.translate(0f, offsetYPx)
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
        canvas.restore()
    }
}
