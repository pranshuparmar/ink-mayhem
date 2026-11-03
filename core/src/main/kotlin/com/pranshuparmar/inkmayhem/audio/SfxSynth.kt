package com.pranshuparmar.inkmayhem.audio

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The game's sound effects, synthesized offline as 16-bit mono PCM. Deterministic (seeded
 * noise), so the committed WAVs can be regenerated and checked byte for byte.
 */
object SfxSynth {
    const val RATE = 44100

    enum class Wave { SINE, SQUARE, TRIANGLE, SAWTOOTH }

    /**
     * A tone: an oscillator whose frequency ramps exponentially from [freq] to [freq] × [slide]
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

    /** A crack: white noise through a high-pass at [hp] Hz, gain ramping from [vol] to 0.0005 over 0.09 s. */
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
     * [SCRATCH_REF] so the playback volume maps 0..1 onto the scratch's gain range. Filtered twice so the
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
    /** Peak scratch gain (0.09 × 1.5); the loop is pre-scaled by it so volume 1 = that gain. */
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
        "anchor", "hop", "boing", "boulder_boing", "snap", "erase", "rumble", "pickup", "pickup_high",
        "featherweight", "combo_lost", "death", "scratch",
        "stage_start", "checkpoint", "clear", "one_up", "game_over", "star", "life_lost", "squash", "pop", "bold", "shrink",
        "gold", "power_end", "boss_hit", "boss_down",
    ) + (2..9).map { "chime_$it" } + (0..7).map { "star_$it" }

    /**
     * Notes ([freq] Hz, [dur] s) one after another, each a [tone] with its own decay: the short
     * chiptune jingles of stage play (all original).
     */
    fun melody(wave: Wave, vol: Double, vararg notes: Pair<Double, Double>): FloatArray {
        var len = 0.0
        for ((_, d) in notes) len += d
        val out = FloatArray(((len + 0.05) * RATE).roundToInt())
        var at = 0
        for ((f, d) in notes) {
            val t = tone(f, d, wave, vol)
            for (i in t.indices) if (at + i < out.size) out[at + i] += t[i]
            at += (d * RATE).roundToInt()
        }
        return out
    }

    // Ink Rush's notes (A minor) for the jingles cut from it
    private const val A3 = 220.0
    private const val A4 = 440.0
    private const val B4 = 493.88
    private const val C5_ = 523.25
    private const val D5 = 587.33
    private const val E5_ = 659.25
    private const val GS4 = 415.3
    private const val G5_ = 783.99
    private const val A5_ = 880.0
    private const val CS6 = 1108.73
    private const val E6_ = 1318.51
    private const val A6 = 1760.0
    /** Ink Rush's eighth note at 168 bpm. */
    private const val E8 = 60.0 / 168 / 2

    private const val C4 = 261.63
    private const val E4 = 329.63
    private const val G4 = 392.0
    private const val C5 = 523.25
    private const val E5 = 659.25
    private const val G5 = 783.99
    private const val A5 = 880.0
    private const val C6 = 1046.5
    private const val E6 = 1318.5
    private const val G6 = 1568.0
    private const val C7 = 2093.0

    /** Every sound file the game loads, by name. */
    fun all(): Map<String, FloatArray> = linkedMapOf(
        "anchor" to tone(520.0, 0.06, Wave.SQUARE, 0.04),
        "hop" to tone(330.0, 0.08, Wave.SINE, 0.05, 1.4),
        "boing" to tone(220.0, 0.22, Wave.SINE, 0.14, 2.6),
        "boulder_boing" to tone(150.0, 0.3, Wave.SINE, 0.14, 2.2),
        "snap" to crack(0.22, 900.0, seed = 1),
        "erase" to crack(0.08, 3000.0, seed = 2),
        "rumble" to tone(90.0, 0.5, Wave.SAWTOOTH, 0.06, 0.8),
        "pickup" to tone(784.0, 0.12, Wave.SINE, 0.1, 1.25),
        "pickup_high" to tone(990.0, 0.12, Wave.SINE, 0.1, 1.25),
        "featherweight" to tone(1046.0, 0.18, Wave.SINE, 0.1, 1.3),
        "combo_lost" to tone(200.0, 0.2, Wave.TRIANGLE, 0.08, 0.7),
        "death" to tone(140.0, 0.35, Wave.TRIANGLE, 0.18, 0.5),
        "scratch" to scratchLoop(seed = 3),
        // Ink Rush's first bar: the motif every jingle is cut from
        "stage_start" to melody(Wave.SQUARE, 0.05, A4 to E8, A4 to E8, C5_ to E8, A4 to E8, E5_ to E8 * 2.5),
        "checkpoint" to melody(Wave.TRIANGLE, 0.1, E5 to 0.08, A5 to 0.22),
        // the motif climbs to the top and lands bright (a major third over A minor)
        "clear" to melody(Wave.SQUARE, 0.05, A4 to E8, C5_ to E8, E5_ to E8, A5_ to E8 * 2, G5_ to E8, A5_ to E8, CS6 to E8 * 4),
        "one_up" to melody(Wave.SQUARE, 0.045, A5_ to 0.06, C6 to 0.06, E6_ to 0.06, A6 to 0.22),
        // the motif's last bars, slowed and falling
        "game_over" to melody(Wave.TRIANGLE, 0.11, E5_ to 0.22, C5_ to 0.22, A4 to 0.22, A3 to 0.6),
        "life_lost" to melody(Wave.TRIANGLE, 0.1, E5_ to 0.13, D5 to 0.13, C5_ to 0.13, B4 to 0.13, GS4 to 0.26, A4 to 0.42),
        "star" to tone(E6, 0.18, Wave.SINE, 0.09, 1.2),
        "squash" to tone(180.0, 0.16, Wave.TRIANGLE, 0.16, 0.45),
        "pop" to tone(520.0, 0.14, Wave.SQUARE, 0.04, 1.8),
        "bold" to melody(Wave.SQUARE, 0.045, A4 to 0.05, C5_ to 0.05, E5_ to 0.05, A5_ to 0.05, C6 to 0.05, E6_ to 0.16),
        "shrink" to melody(Wave.SQUARE, 0.045, E6_ to 0.05, C6 to 0.05, A5_ to 0.05, E5_ to 0.05, A4 to 0.16),
        // worlds: a gold star sparkles up an A major arpeggio; a power-up runs out with a falling
        // pair; a boss hit is a low thump; a boss defeat is the clear fanfare's last bar, slower
        "gold" to melody(Wave.SQUARE, 0.045, A5_ to 0.05, CS6 to 0.05, E6_ to 0.05, A6 to 0.05, CS6 * 2 to 0.05, E6_ * 2 to 0.2),
        "power_end" to melody(Wave.TRIANGLE, 0.1, E5_ to 0.09, A4 to 0.2),
        "boss_hit" to tone(110.0, 0.25, Wave.SQUARE, 0.1, 0.55),
        "boss_down" to melody(Wave.SQUARE, 0.05, E5_ to 0.12, A5_ to 0.12, CS6 to 0.12, E6_ to 0.12, A6 to 0.5),
    ).apply {
        // combo chime: 660 + combo × 40 Hz, one file per combo level 2..9
        for (c in 2..9) put("chime_$c", tone(660.0 + c * 40.0, 0.1, Wave.SINE, 0.08, 1.2))
        // star trails: each star in a streak rings a step higher up Ink Rush's A minor pentatonic
        val steps = doubleArrayOf(A5_, C6, D5 * 2, E6_, G5_ * 2, A6, C6 * 2, D5 * 4)
        for (k in 0..7) put("star_$k", tone(steps[k], 0.11, Wave.SQUARE, 0.035, 1.02))
    }

    /** 16-bit PCM mono WAV. */
    fun wav(samples: FloatArray, rate: Int = RATE): ByteArray {
        val data = samples.size * 2
        val out = ByteArrayOutputStream(44 + data)
        fun i32(v: Int) { out.write(v and 0xFF); out.write(v shr 8 and 0xFF); out.write(v shr 16 and 0xFF); out.write(v shr 24 and 0xFF) }
        fun i16(v: Int) { out.write(v and 0xFF); out.write(v shr 8 and 0xFF) }
        out.write("RIFF".toByteArray()); i32(36 + data); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(1); i32(rate); i32(rate * 2); i16(2); i16(16)
        out.write("data".toByteArray()); i32(data)
        for (s in samples) i16((s.coerceIn(-1f, 1f) * 32767f).roundToInt())
        return out.toByteArray()
    }
}
