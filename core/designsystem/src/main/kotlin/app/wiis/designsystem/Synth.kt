package app.wiis.designsystem

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Deterministic sound renderer. The Design Workbench runs the same algorithm in JavaScript,
 * sample for sample, so a sound auditioned there is the sound the launcher plays.
 * Keep the two in step: any change here must be mirrored in design/workbench.html (synth()).
 */
object Synth {
    const val SAMPLE_RATE = 44_100

    fun render(spec: SynthSpec): FloatArray {
        val notes = spec.notes.ifEmpty { listOf(440f) }
        val noteLen = spec.noteMs.coerceAtLeast(1) / 1000.0
        val hold = notes.size * noteLen
        val decay = spec.decayMs.coerceAtLeast(0) / 1000.0
        val total = hold + decay
        val count = (total * SAMPLE_RATE).roundToInt().coerceAtLeast(1)
        val attack = spec.attackMs.coerceAtLeast(0) / 1000.0
        val noise = spec.noise.toDouble().coerceIn(0.0, 1.0)
        val alpha = 1 - exp(-2 * PI * spec.lowpass.toDouble().coerceIn(20.0, 20_000.0) / SAMPLE_RATE)
        val volume = spec.volume.toDouble().coerceIn(0.0, 1.0)

        val out = FloatArray(count)
        var phase = 0.0
        var lp = 0.0
        var seed = 0x2545F491
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val index = min(notes.size - 1, floor(t / noteLen).toInt())
            val freq = notes[index] * 2.0.pow(spec.sweep * (t / total) / 12.0)
            phase += freq / SAMPLE_RATE
            phase -= floor(phase)
            val osc = when (spec.wave) {
                "sine" -> sin(2 * PI * phase)
                "square" -> (if (phase < 0.5) 1.0 else -1.0) * 0.6
                "saw" -> (2 * phase - 1) * 0.6
                else -> 1 - 4 * abs(phase - 0.5) // triangle
            }
            seed = seed xor (seed shl 13)
            seed = seed xor (seed ushr 17)
            seed = seed xor (seed shl 5)
            val white = (seed.toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0 * 2 - 1
            val raw = osc * (1 - noise) + white * noise
            val env = when {
                t < attack -> t / attack
                t < hold -> 1.0
                decay > 0 -> exp(-5 * (t - hold) / decay)
                else -> 0.0
            }
            lp += alpha * (raw * env - lp)
            out[i] = (lp * volume).toFloat()
        }
        return out
    }

    /** 16-bit mono PCM WAV, ready for SoundPool. */
    fun wav(samples: FloatArray): ByteArray {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples.size * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples.size * 2)
        }
        return ByteArrayOutputStream(44 + samples.size * 2).apply {
            write(header.array()); write(data.array())
        }.toByteArray()
    }
}
