package com.pranshuparmar.inkmayhem.audio

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Offline re-creation of the POC's WebAudio sounds as 16-bit mono PCM. Deterministic (seeded
 * noise), so the committed WAVs can be regenerated and checked byte for byte.
 */
object SfxSynth {
    const val RATE = 44100

    enum class Wave { SINE, SQUARE, TRIANGLE, SAWTOOTH }

    /**
     * POC tone(): an oscillator whose frequency ramps exponentially from [freq] to [freq] × [slide]
     * and whose gain ramps exponentially from [vol] to 0.0005 over [dur]; stopped at dur + 0.03 s.
     */
    fun tone(freq: Double, dur: Double, wave: Wave, vol: Double, slide: Double = 1.0): FloatArray {
        val n = ((dur + 0.03) * RATE).roundToInt()
        val out = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val u = (t / dur).coerceAtMost(1.0)
            val f = freq * slide.pow(u)
            val g = vol * (0.0005 / vol).pow(u)
            val s = when (wave) {
                Wave.SINE -> sin(2 * PI * phase)
                Wave.SQUARE -> if (phase < 0.5) 1.0 else -1.0
                Wave.TRIANGLE -> 1.0 - 4.0 * kotlin.math.abs(phase - 0.5)
                Wave.SAWTOOTH -> 2.0 * phase - 1.0
            }
            out[i] = (s * g).toFloat()
            phase += f / RATE
            phase -= kotlin.math.floor(phase)
        }
        return out
    }

    /** POC crack(): white noise through a high-pass at [hp] Hz, gain ramping from [vol] to 0.0005 over 0.09 s. */
    fun crack(vol: Double, hp: Double, seed: Int): FloatArray {
        val n = (0.1 * RATE).roundToInt()
        val rnd = Random(seed)
        val noise = FloatArray(n) { rnd.nextFloat() * 2f - 1f }
        val filtered = Biquad.highpass(hp, 10.0.pow(1.0 / 20)).run(noise) // WebAudio Q = 1 dB
        for (i in 0 until n) {
            val u = (i.toDouble() / RATE / 0.09).coerceAtMost(1.0)
            filtered[i] = (filtered[i] * vol * (0.0005 / vol).pow(u)).toFloat()
        }
        return filtered
    }

    /**
     * The pen-scratch loop: one second of noise band-passed at [SCRATCH_CENTER] Hz (Q 0.9), scaled by
     * [SCRATCH_REF] so the playback volume maps 0..1 onto the POC's gain range. Filtered twice so the
     * filter state at the loop point is continuous.
     */
    fun scratchLoop(seed: Int): FloatArray {
        val n = RATE
        val rnd = Random(seed)
        val noise = FloatArray(n) { rnd.nextFloat() * 2f - 1f }
        val bp = Biquad.bandpass(SCRATCH_CENTER, 0.9)
        bp.run(noise.copyOf())
        val out = bp.run(noise)
        for (i in 0 until n) out[i] *= SCRATCH_REF
        return out
    }

    /** Base frequency of the scratch loop; playback pitch = target frequency / this. */
    const val SCRATCH_CENTER = 3000.0
    /** Peak POC scratch gain (0.09 × 1.5); the loop is pre-scaled by it so volume 1 = that gain. */
    const val SCRATCH_REF = 0.135f

    /** RBJ biquad, as WebAudio's BiquadFilterNode. */
    class Biquad(private val b0: Double, private val b1: Double, private val b2: Double, private val a1: Double, private val a2: Double) {
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

        fun run(input: FloatArray): FloatArray {
            for (i in input.indices) {
                val x = input[i].toDouble()
                val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = x; y2 = y1; y1 = y
                input[i] = y.toFloat()
            }
            return input
        }

        companion object {
            fun highpass(f0: Double, q: Double): Biquad {
                val w = 2 * PI * f0 / RATE
                val alpha = sin(w) / (2 * q)
                val a0 = 1 + alpha
                return Biquad((1 + cos(w)) / 2 / a0, -(1 + cos(w)) / a0, (1 + cos(w)) / 2 / a0, -2 * cos(w) / a0, (1 - alpha) / a0)
            }

            /** Constant 0 dB peak gain band-pass. */
            fun bandpass(f0: Double, q: Double): Biquad {
                val w = 2 * PI * f0 / RATE
                val alpha = sin(w) / (2 * q)
                val a0 = 1 + alpha
                return Biquad(alpha / a0, 0.0, -alpha / a0, -2 * cos(w) / a0, (1 - alpha) / a0)
            }
        }
    }

    /** File names (without .wav) the game loads. */
    val NAMES: List<String> = listOf(
        "anchor", "hop", "boing", "boulder_boing", "snap", "erase", "speed_up", "rumble", "pickup", "pickup_high",
        "featherweight", "combo_lost", "death", "scratch",
    ) + (2..9).map { "chime_$it" }

    /** Every sound file the game loads, by name. */
    fun all(): Map<String, FloatArray> = linkedMapOf(
        "anchor" to tone(520.0, 0.06, Wave.SQUARE, 0.04),
        "hop" to tone(330.0, 0.08, Wave.SINE, 0.05, 1.4),
        "boing" to tone(220.0, 0.22, Wave.SINE, 0.14, 2.6),
        "boulder_boing" to tone(150.0, 0.3, Wave.SINE, 0.14, 2.2),
        "snap" to crack(0.22, 900.0, seed = 1),
        "erase" to crack(0.08, 3000.0, seed = 2),
        "speed_up" to tone(660.0, 0.12, Wave.SINE, 0.08, 1.5),
        "rumble" to tone(90.0, 0.5, Wave.SAWTOOTH, 0.06, 0.8),
        "pickup" to tone(784.0, 0.12, Wave.SINE, 0.1, 1.25),
        "pickup_high" to tone(990.0, 0.12, Wave.SINE, 0.1, 1.25),
        "featherweight" to tone(1046.0, 0.18, Wave.SINE, 0.1, 1.3),
        "combo_lost" to tone(200.0, 0.2, Wave.TRIANGLE, 0.08, 0.7),
        "death" to tone(140.0, 0.35, Wave.TRIANGLE, 0.18, 0.5),
        "scratch" to scratchLoop(seed = 3),
    ).apply {
        // combo chime: 660 + combo × 40 Hz, one file per combo level 2..9
        for (c in 2..9) put("chime_$c", tone(660.0 + c * 40.0, 0.1, Wave.SINE, 0.08, 1.2))
    }

    /** 16-bit PCM mono WAV. */
    fun wav(samples: FloatArray): ByteArray {
        val data = samples.size * 2
        val out = ByteArrayOutputStream(44 + data)
        fun i32(v: Int) { out.write(v and 0xFF); out.write(v shr 8 and 0xFF); out.write(v shr 16 and 0xFF); out.write(v shr 24 and 0xFF) }
        fun i16(v: Int) { out.write(v and 0xFF); out.write(v shr 8 and 0xFF) }
        out.write("RIFF".toByteArray()); i32(36 + data); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16)
        out.write("data".toByteArray()); i32(data)
        for (s in samples) i16((s.coerceIn(-1f, 1f) * 32767f).roundToInt())
        return out.toByteArray()
    }
}
