package app.wiis.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import app.wiis.designsystem.LocalWiis
import app.wiis.designsystem.dotGrid
import app.wiis.designsystem.mix
import app.wiis.emulator.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** What the art background shows: a game's art, a system's colour, or the plain theme gradient. */
@Immutable
sealed interface BgSubject {
    data class OfGame(val game: Game) : BgSubject
    data class OfSystem(val systemId: String) : BgSubject
    data object Plain : BgSubject
}

/**
 * The art background from the workbench: the theme gradient, then the subject's art (blurred and a
 * little more saturated) or a soft pool of its system colour, then a dark veil of `colors.artDim`.
 *
 * The blur is computed once per game on a small copy (see [Backdrop]) and drawn scaled up, so the
 * screen costs no more per frame than a plain image. The previous picture stays up until the next
 * one is ready, so moving fast never flashes the plain gradient.
 */
@Composable
fun ArtBackground(subject: BgSubject, modifier: Modifier = Modifier) {
    val style = LocalWiis.current
    val colors = style.spec.colors
    val images = LocalImageSource.current
    BoxWithConstraints(modifier) {
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        val blurPx = with(LocalDensity.current) { colors.artBlur.coerceAtLeast(0).dp.toPx() }
        val gameUri = (subject as? BgSubject.OfGame)?.game?.uri
        var artVersion by remember(gameUri) { mutableIntStateOf(0) }
        LaunchedEffect(gameUri, images) { if (gameUri != null) images.artUpdates.filter { it == gameUri }.collect { artVersion++ } }

        var look by remember { mutableStateOf<Look>(Look.Plain) }
        LaunchedEffect(subject, artVersion, wPx, hPx, blurPx, style) {
            look = when (subject) {
                is BgSubject.OfGame -> {
                    val key = "${subject.game.uri}|$wPx|$hPx|$blurPx"
                    val ready = Backdrop.cached(key) ?: images.art(subject.game, ART_SOURCE_PX)?.let { art ->
                        withContext(Dispatchers.Default) { Backdrop.render(art, wPx, hPx, blurPx) }.also { Backdrop.remember(key, it) }
                    }
                    if (ready != null) Look.Art(ready) else Look.Pool(style.systemColor(subject.game.systemId))
                }
                is BgSubject.OfSystem -> Look.Pool(if (subject.systemId == "all") style.accent else style.systemColor(subject.systemId))
                BgSubject.Plain -> Look.Plain
            }
        }

        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(style.backgroundTop, style.background))))
        when (val l = look) {
            is Look.Art -> Image(
                l.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Low,
                modifier = Modifier.fillMaxSize(),
            )
            is Look.Pool -> Box(
                Modifier.fillMaxSize().drawBehind {
                    // CSS radial-gradient(circle at 30% 30%, …): radius reaches the farthest corner.
                    drawRect(
                        Brush.radialGradient(
                            listOf(mix(l.color, Color.White, 0.15f), mix(l.color, Color.Black, 0.55f)),
                            center = Offset(size.width * 0.3f, size.height * 0.3f),
                            radius = hypot(size.width * 0.7f, size.height * 0.7f),
                        ),
                    )
                },
            )
            Look.Plain -> Unit
        }
        val dim = colors.artDim.coerceIn(0f, 1f)
        // "artLight" washes the art with the background colour instead of darkening it.
        val veil = if (colors.backgroundStyle == "artLight") style.background else Color.Black
        if (dim > 0f) Box(Modifier.fillMaxSize().background(veil.copy(alpha = dim)))
    }
}

/**
 * The "dots" background from the workbench: the theme gradient, two soft pools of the focused
 * item's colour (top right and bottom left) and a fine dot grid in the text colour.
 */
@Composable
fun DotsBackground(subject: BgSubject, modifier: Modifier = Modifier) {
    val style = LocalWiis.current
    val color = when (subject) {
        is BgSubject.OfGame -> style.systemColor(subject.game.systemId)
        is BgSubject.OfSystem -> if (subject.systemId == "all") style.accent else style.systemColor(subject.systemId)
        BgSubject.Plain -> style.accent
    }
    val alpha = (style.spec.colors.glowStrength * 2f).coerceIn(0f, 0.6f)
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(style.backgroundTop, style.background)))
            .drawBehind {
                // CSS radial-gradient(circle at x y, c 0, transparent p%): p% of the distance to the farthest corner.
                fun pool(cx: Float, cy: Float, stop: Float, a: Float) {
                    val center = Offset(size.width * cx, size.height * cy)
                    val far = hypot(maxOf(center.x, size.width - center.x), maxOf(center.y, size.height - center.y))
                    drawRect(Brush.radialGradient(listOf(color.copy(alpha = a), color.copy(alpha = 0f)), center = center, radius = (far * stop).coerceAtLeast(1f)))
                }
                pool(0.92f, 0.06f, 0.44f, alpha)
                pool(0.04f, 0.98f, 0.40f, alpha * 0.75f)
            }
            .dotGrid(style.text),
    )
}

private sealed interface Look {
    class Art(val bitmap: Bitmap) : Look
    data class Pool(val color: Color) : Look
    data object Plain : Look
}

/** Art is decoded this small for the background: the blur removes all detail anyway. */
private const val ART_SOURCE_PX = 256

object Backdrop {
    /** Blur strength kept in the small copy; the copy is shrunk so the blur is about this many pixels. */
    private const val SMALL_SIGMA = 6f

    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun cached(key: String): Bitmap? = cache.get(key)
    fun remember(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }

    /**
     * Mirrors the workbench: the art cover-fitted to the screen enlarged by twice the blur on every
     * side, saturation ×[saturation], Gaussian blur of [blurPx] (CSS `blur()` is a standard
     * deviation), then the screen-sized middle. Returned small, to be drawn stretched to the screen.
     */
    fun render(src: Bitmap, screenW: Int, screenH: Int, blurPx: Float, saturation: Float = 1.25f): Bitmap {
        val w = screenW.coerceAtLeast(1)
        val h = screenH.coerceAtLeast(1)
        val shrink = if (blurPx > 0f) max(1f, blurPx / SMALL_SIGMA) else max(1f, w / 480f)
        val margin = 2f * blurPx
        val bw = ceil((w + 2 * margin) / shrink).toInt().coerceAtLeast(1)
        val bh = ceil((h + 2 * margin) / shrink).toInt().coerceAtLeast(1)
        val box = createBitmap(bw, bh)
        val scale = max(bw / src.width.toFloat(), bh / src.height.toFloat())
        val dw = src.width * scale
        val dh = src.height * scale
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) })
        }
        Canvas(box).drawBitmap(src, null, RectF((bw - dw) / 2f, (bh - dh) / 2f, (bw + dw) / 2f, (bh + dh) / 2f), paint)
        if (blurPx > 0f) {
            val pixels = IntArray(bw * bh)
            box.getPixels(pixels, 0, bw, 0, 0, bw, bh)
            Blur.gaussian(pixels, bw, bh, blurPx / shrink)
            box.setPixels(pixels, 0, bw, 0, 0, bw, bh)
        }
        val m = (margin / shrink).roundToInt().coerceIn(0, (minOf(bw, bh) - 1) / 2)
        val cw = (w / shrink).roundToInt().coerceIn(1, bw - 2 * m)
        val ch = (h / shrink).roundToInt().coerceIn(1, bh - 2 * m)
        return Bitmap.createBitmap(box, m, m, cw, ch)
    }
}
