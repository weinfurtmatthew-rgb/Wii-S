package app.wiis.launcher.ui

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Gaussian blur on ARGB pixels, approximated by three box blurs (accurate to a few percent and
 * linear in image size whatever the radius). Edges repeat the border pixel. Pure Kotlin so it is
 * unit-tested on the JVM and gives the same result on every device.
 */
object Blur {

    /** Blurs [pixels] (row-major, [w] × [h]) in place with standard deviation [sigma] pixels. */
    fun gaussian(pixels: IntArray, w: Int, h: Int, sigma: Float) {
        require(pixels.size >= w * h) { "pixel buffer is smaller than $w × $h" }
        if (sigma < 0.5f || w <= 0 || h <= 0) return
        val tmp = IntArray(w * h)
        for (size in boxSizes(sigma)) {
            val r = (size - 1) / 2
            if (r <= 0) continue
            boxHorizontal(pixels, tmp, w, h, r)
            boxVertical(tmp, pixels, w, h, r)
        }
    }

    /** Widths of [n] successive box blurs whose combined variance matches a Gaussian of [sigma]. */
    fun boxSizes(sigma: Float, n: Int = 3): IntArray {
        val ideal = sqrt(12.0 * sigma * sigma / n + 1)
        var lower = floor(ideal).toInt()
        if (lower % 2 == 0) lower--
        val upper = lower + 2
        val m = ((12.0 * sigma * sigma - n * lower * lower - 4.0 * n * lower - 3.0 * n) / (-4.0 * lower - 4)).roundToInt()
        return IntArray(n) { if (it < m) lower else upper }
    }

    private fun boxHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        val half = div / 2
        for (y in 0 until h) {
            val row = y * w
            var a = 0; var red = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val p = src[row + i.coerceIn(0, w - 1)]
                a += p ushr 24; red += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = pack((a + half) / div, (red + half) / div, (g + half) / div, (b + half) / div)
                val out = src[row + (x - r).coerceIn(0, w - 1)]
                val inn = src[row + (x + r + 1).coerceIn(0, w - 1)]
                a += (inn ushr 24) - (out ushr 24)
                red += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }

    private fun boxVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        val half = div / 2
        for (x in 0 until w) {
            var a = 0; var red = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val p = src[i.coerceIn(0, h - 1) * w + x]
                a += p ushr 24; red += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = pack((a + half) / div, (red + half) / div, (g + half) / div, (b + half) / div)
                val out = src[(y - r).coerceIn(0, h - 1) * w + x]
                val inn = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                a += (inn ushr 24) - (out ushr 24)
                red += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }

    private fun pack(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
}
