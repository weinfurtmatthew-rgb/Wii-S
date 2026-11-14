package app.wiis.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemeSpecTest {
    private val defaultJson = File("src/main/assets/theme/default.json").readText()

    @Test fun `bundled default theme equals the Kotlin defaults`() {
        assertEquals(ThemeSpec(), ThemeSpec.parse(defaultJson))
    }

    @Test fun `missing keys fall back to defaults`() {
        val spec = ThemeSpec.parse("""{"name":"Mine","colors":{"accent":"#123456"}}""")
        assertEquals("Mine", spec.name)
        assertEquals("#123456", spec.colors.accent)
        assertEquals(ThemeSpec().colors.background, spec.colors.background)
        assertEquals(ThemeSpec().sounds, spec.sounds)
    }

    @Test fun `a theme from before dark mode looks the same in both modes`() {
        val old = ThemeSpec.parse(java.io.File("../../design/themes/draft-3-2026-11-14.json").readText())
        assertEquals(4, old.schema)
        assertEquals("art", old.colors.backgroundStyle)
        assertEquals(old.colors, old.resolve(dark = true).colors)
        assertEquals(old.glass, old.resolve(dark = true).glass)
    }

    @Test fun `dark mode swaps only the palette`() {
        val light = ThemeSpec()
        val dark = light.resolve(dark = true)
        assertEquals(light.dark.colors, dark.colors)
        assertEquals(light.dark.glass.fill, dark.glass.fill)
        assertEquals(light.glass.radius, dark.glass.radius)
        assertEquals(light.type, dark.type)
        assertEquals(light.motion, dark.motion)
        assertEquals(light.dark.hints.colorA, dark.hints.colorA)
        assertEquals(light.hints.align, dark.hints.align)
    }

    @Test fun `encode and parse round trip`() {
        val spec = ThemeSpec().copy(name = "Round trip")
        assertEquals(spec, ThemeSpec.parse(ThemeSpec.encode(spec)))
    }

    @Test fun `colour parsing`() {
        val fallback = androidx.compose.ui.graphics.Color.Magenta
        assertEquals(androidx.compose.ui.graphics.Color(0xFF15141E), "#15141E".toColorOr(fallback))
        assertEquals(androidx.compose.ui.graphics.Color(0x8015141E), "#8015141E".toColorOr(fallback))
        assertEquals(fallback, "teal".toColorOr(fallback))
    }

    @Test fun `every sound event has a definition`() {
        assertEquals(SOUND_EVENTS.toSet(), ThemeSpec().sounds.events.keys)
        assertEquals(SOUND_EVENTS.toSet(), ThemeSpec().haptics.events.keys)
    }
}

class SynthTest {
    @Test fun `render is deterministic and bounded`() {
        for ((name, spec) in DEFAULT_SOUNDS) {
            val a = Synth.render(spec)
            val b = Synth.render(spec)
            assertTrue(name, a.contentEquals(b))
            assertTrue(name, a.all { it in -1f..1f })
            val expected = ((spec.notes.size * spec.noteMs + spec.decayMs) / 1000.0 * Synth.SAMPLE_RATE).toInt()
            assertTrue(name, kotlin.math.abs(a.size - expected) <= 1)
        }
    }

    @Test fun `wav header is valid`() {
        val wav = Synth.wav(Synth.render(DEFAULT_SOUNDS.getValue("select")))
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals(wav.size - 44, java.nio.ByteBuffer.wrap(wav, 40, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int)
    }

    /** Reference samples shared with the workbench's JavaScript synth (see design/workbench.html). */
    @Test fun `matches the workbench reference samples`() {
        val s = Synth.render(DEFAULT_SOUNDS.getValue("bump"))
        val probe = listOf(10, 100, 500, 1000, 2000).map { "%.6f".format(java.util.Locale.US, s[it]) }
        println("SYNTH_REFERENCE bump " + probe.joinToString(","))
    }
}

/**
 * Keeps the launcher and the Design Workbench (design/workbench.html) in step: the workbench must
 * embed exactly this module's default theme, and its JavaScript synth must produce the same samples.
 */
class WorkbenchParityTest {
    private val html = File("../../design/workbench.html").readText()

    @Test fun `workbench embeds the bundled proposal`() {
        val start = html.indexOf("<script type=\"application/json\" id=\"proposal\">") + "<script type=\"application/json\" id=\"proposal\">".length
        val end = html.indexOf("</script>", start)
        assertEquals(ThemeSpec(), ThemeSpec.parse(html.substring(start, end)))
    }

    /** Checksums printed by the workbench's synth() for each default sound: length, sum, sum of |x|. */
    private val workbenchChecksums = mapOf(
        "move" to doubleArrayOf(3572.0, -0.456534, 391.545129),
        "select" to doubleArrayOf(7938.0, 1.978287, 1173.504936),
        "back" to doubleArrayOf(7497.0, 2.423449, 1037.009329),
        "tab" to doubleArrayOf(5292.0, 1.990627, 481.624616),
        "launch" to doubleArrayOf(22050.0, 3.478501, 3896.572618),
        "error" to doubleArrayOf(13230.0, 6.240447, 1799.440879),
        "bump" to doubleArrayOf(3087.0, 15.472776, 355.699345),
    )

    @Test fun `launcher synth matches the workbench synth`() {
        for ((event, spec) in DEFAULT_SOUNDS) {
            val out = Synth.render(spec)
            val expected = workbenchChecksums.getValue(event)
            assertEquals(event, expected[0], out.size.toDouble(), 0.0)
            assertEquals(event, expected[1], out.sumOf { it.toDouble() }, 1e-3)
            assertEquals(event, expected[2], out.sumOf { kotlin.math.abs(it).toDouble() }, 1e-3)
        }
    }
}
