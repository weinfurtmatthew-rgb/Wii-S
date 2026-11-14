package app.wiis.launcher.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class BlurTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun red(p: Int) = (p shr 16) and 0xFF

    @Test fun `three boxes match the Gaussian's variance`() {
        // Odd widths only, so small blurs land up to ~10% off; the background blurs at about 6 px.
        for ((sigma, tolerance) in listOf(2f to 0.1, 6f to 0.04, 13.8f to 0.04)) {
            val sizes = Blur.boxSizes(sigma)
            assertEquals(3, sizes.size)
            assertTrue(sizes.all { it % 2 == 1 })
            // A box of width w has variance (w² - 1) / 12; three of them should add up to sigma².
            val variance = sizes.sumOf { (it * it - 1) / 12.0 }
            assertEquals(sigma.toDouble(), sqrt(variance), sigma * tolerance)
        }
    }

    @Test fun `a flat image stays flat`() {
        val px = IntArray(40 * 30) { gray(120) }
        Blur.gaussian(px, 40, 30, 6f)
        assertTrue(px.all { it == gray(120) })
    }

    @Test fun `a bright dot spreads evenly in every direction`() {
        val w = 61
        val px = IntArray(w * w) { gray(0) }
        px[30 * w + 30] = gray(255)
        Blur.gaussian(px, w, w, 4f)
        val centre = red(px[30 * w + 30])
        assertTrue(centre in 1 until 255)
        for (d in 1..8) {
            val right = red(px[30 * w + 30 + d])
            assertEquals(right, red(px[30 * w + 30 - d]))
            assertEquals(right, red(px[(30 + d) * w + 30]))
            assertEquals(right, red(px[(30 - d) * w + 30]))
        }
        assertTrue(red(px[30 * w + 31]) <= centre)
    }

    @Test fun `blurring keeps the overall brightness`() {
        val w = 50
        val h = 40
        val px = IntArray(w * h) { i -> gray(if ((i / w) in 10..29 && (i % w) in 10..39) 200 else 40) }
        val before = px.sumOf { red(it).toLong() }
        Blur.gaussian(px, w, h, 5f)
        val after = px.sumOf { red(it).toLong() }
        assertTrue("brightness drifted: $before -> $after", abs(after - before) < before * 0.01)
    }

    @Test fun `tiny sigma leaves pixels untouched`() {
        val px = IntArray(9) { gray(it * 20) }
        val copy = px.copyOf()
        Blur.gaussian(px, 3, 3, 0.2f)
        assertArrayEquals(copy, px)
    }
}
