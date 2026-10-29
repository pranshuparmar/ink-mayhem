package com.pranshuparmar.inkmayhem.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The game's signature theme, "Ink Rush", synthesized like the sound effects: an original
 * eight-bar chiptune in A minor at 168 bpm (square lead, triangle bass, noise drums). Rendered
 * as one seamless loop of mono 16-bit PCM, deterministic so the committed WAV can be checked
 * byte for byte. The player chose it from three candidates in the design pitch.
 */
object MusicSynth {
    /** Lower than the effects' 44.1 kHz: the loop has to stay small enough to play as a sound. */
    const val RATE = 22050

    class Theme(val bpm: Double, val lead: List<Pair<String, Int>>, val chords: List<String>) {
        /** One eighth note in seconds. */
        val eighth: Double get() = 60.0 / bpm / 2.0
        /** Samples in one loop of the eight bars. */
        val loopSamples: Int get() = (64 * eighth * RATE).roundToInt()
    }

    /** Notes as (name, eighths); "R" rests. Each bar is 8 eighths; [Theme.chords] give the bass roots. */
    val INK_RUSH = Theme(
        bpm = 168.0,
        lead = listOf(
            "A4" to 1, "A4" to 1, "C5" to 1, "A4" to 1, "E5" to 2, "D5" to 1, "C5" to 1,
            "D5" to 1, "D5" to 1, "F5" to 1, "D5" to 1, "G5" to 2, "F5" to 1, "E5" to 1,
            "E5" to 1, "E5" to 1, "G5" to 1, "E5" to 1, "A5" to 2, "G5" to 1, "E5" to 1,
            "F5" to 2, "E5" to 2, "D5" to 2, "E5" to 2,
            "A4" to 1, "A4" to 1, "C5" to 1, "A4" to 1, "E5" to 2, "D5" to 1, "C5" to 1,
            "D5" to 1, "D5" to 1, "F5" to 1, "D5" to 1, "A5" to 2, "G5" to 1, "F5" to 1,
            "E5" to 2, "C5" to 1, "A4" to 1, "B4" to 2, "G#4" to 2,
            "A4" to 4, "R" to 4,
        ),
        chords = listOf("A", "D", "E", "F", "A", "D", "E", "A"),
    )

    private val ROOT = mapOf("C" to 48, "D" to 50, "E" to 52, "F" to 53, "G" to 55, "A" to 57, "B" to 59)

    fun midi(name: String): Int {
        val base = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11).getValue(name[0])
        val sharp = if (name.length == 3 && name[1] == '#') 1 else if (name.length == 3 && name[1] == 'b') -1 else 0
        return 12 * (name.last().digitToInt() + 1) + base + sharp
    }

    fun hz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** The theme as one loop: what runs past its end is folded back onto its start. */
    fun render(theme: Theme): FloatArray {
        val n = theme.loopSamples
        val buf = FloatArray(n + RATE) // a second of tail for the last notes and drums
        val lead = FloatArray(n + RATE)
        val e = theme.eighth
        val bar = 8 * e
        var pos = 0
        for ((name, len) in theme.lead) {
            if (name != "R") square(lead, hz(midi(name)), pos * e, len * e * 0.92, 0.13)
            pos += len
        }
        // bass: root and fifth on the quarters
        for ((bi, c) in theme.chords.withIndex()) {
            val root = ROOT.getValue(c)
            for (q in 0 until 4) {
                val note = if (q % 2 == 0) root else root + 7
                triangle(buf, hz(note), bi * bar + q * 2 * e, e * 1.7, 0.32)
            }
        }
        // drums: kick on 1 and 3, snare on 2 and 4, hats on the off-beats
        val rnd = Random(7)
        for (bi in theme.chords.indices) {
            for (i in 0 until 8) {
                val t = bi * bar + i * e
                if (i % 2 == 1) noiseHit(buf, t, 0.04, 0.18, highpass = 7000.0, rnd = rnd)
                if (i == 0 || i == 4) kick(buf, t, 0.75)
                if (i == 2 || i == 6) noiseHit(buf, t, 0.12, 0.35, bandpass = 1800.0, rnd = rnd)
            }
        }
        // fold the tails onto the start so the loop is seamless; the lead's square wave is softened
        // by a 3.2 kHz low-pass, like the pitch page
        val leadLoop = fold(lead, n)
        onePoleLowpass(leadLoop, 3200.0)
        val out = fold(buf, n)
        for (i in 0 until n) out[i] += leadLoop[i]
        // master: as loud as the effects' jingles, with headroom
        var peak = 0f
        for (v in out) peak = maxOf(peak, kotlin.math.abs(v))
        val gain = if (peak > 0f) PEAK / peak else 1f
        for (i in out.indices) out[i] *= gain
        return out
    }

    private const val PEAK = 0.8f

    private fun fold(buf: FloatArray, n: Int): FloatArray = FloatArray(n) { i -> buf[i] + if (i < buf.size - n) buf[n + i] else 0f }

    private fun envelope(t: Double, dur: Double, attack: Double, release: Double): Double = when {
        t < attack -> t / attack
        t < dur - release -> 1.0 - 0.2 * (t - attack) / maxOf(1e-6, dur - release - attack)
        t < dur -> 0.8 * (dur - t) / release
        else -> 0.0
    }

    private fun square(buf: FloatArray, f: Double, start: Double, dur: Double, vol: Double) {
        val i0 = (start * RATE).roundToInt()
        val n = (dur * RATE).roundToInt()
        var phase = 0.0
        for (k in 0 until n) {
            val i = i0 + k
            if (i >= buf.size) break
            val s = if (phase < 0.5) 1.0 else -1.0
            buf[i] += (s * vol * envelope(k.toDouble() / RATE, dur, 0.004, 0.05)).toFloat()
            phase += f / RATE
            phase -= kotlin.math.floor(phase)
        }
    }

    private fun triangle(buf: FloatArray, f: Double, start: Double, dur: Double, vol: Double) {
        val i0 = (start * RATE).roundToInt()
        val n = (dur * RATE).roundToInt()
        var phase = 0.0
        for (k in 0 until n) {
            val i = i0 + k
            if (i >= buf.size) break
            val s = 1.0 - 4.0 * kotlin.math.abs(phase - 0.5)
            buf[i] += (s * vol * envelope(k.toDouble() / RATE, dur, 0.004, 0.04)).toFloat()
            phase += f / RATE
            phase -= kotlin.math.floor(phase)
        }
    }

    /** A sine whose pitch drops 140 → 45 Hz in 0.12 s, decaying over 0.16 s. */
    private fun kick(buf: FloatArray, start: Double, vol: Double) {
        val i0 = (start * RATE).roundToInt()
        val n = (0.18 * RATE).roundToInt()
        var phase = 0.0
        for (k in 0 until n) {
            val i = i0 + k
            if (i >= buf.size) break
            val t = k.toDouble() / RATE
            val f = 140.0 * (45.0 / 140.0).pow((t / 0.12).coerceAtMost(1.0))
            val g = vol * exp(ln(0.001 / vol) * (t / 0.16).coerceAtMost(1.0))
            buf[i] += (sin(2 * PI * phase) * g).toFloat()
            phase += f / RATE
        }
    }

    /** Filtered noise with an exponential decay: hats (high-pass) and the snare (band-pass). */
    private fun noiseHit(buf: FloatArray, start: Double, len: Double, vol: Double, rnd: Random, highpass: Double = 0.0, bandpass: Double = 0.0) {
        val i0 = (start * RATE).roundToInt()
        val n = (len * RATE).roundToInt()
        val noise = FloatArray(n) { rnd.nextFloat() * 2f - 1f }
        val filtered = if (highpass > 0.0) Biquad.highpass(highpass, 0.707).run(noise) else Biquad.bandpass(bandpass, 0.8).run(noise)
        for (k in 0 until n) {
            val i = i0 + k
            if (i >= buf.size) break
            val g = vol * exp(ln(0.001 / vol) * (k.toDouble() / n))
            buf[i] += (filtered[k] * g).toFloat()
        }
    }

    private fun onePoleLowpass(x: FloatArray, cutoff: Double) {
        val a = exp(-2.0 * PI * cutoff / RATE)
        var y = 0.0
        // run twice so the filter state at the loop point matches its start
        for (pass in 0 until 2) for (i in x.indices) {
            y = (1 - a) * x[i] + a * y
            if (pass == 1) x[i] = y.toFloat()
        }
    }

    /** RBJ biquad at this file's [RATE]. */
    private class Biquad(private val b0: Double, private val b1: Double, private val b2: Double, private val a1: Double, private val a2: Double) {
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

            fun bandpass(f0: Double, q: Double): Biquad {
                val w = 2 * PI * f0 / RATE
                val alpha = sin(w) / (2 * q)
                val a0 = 1 + alpha
                return Biquad(alpha / a0, 0.0, -alpha / a0, -2 * cos(w) / a0, (1 - alpha) / a0)
            }
        }
    }

    /** Every music file the game loads, by name (under music/). */
    fun all(): Map<String, FloatArray> = linkedMapOf("ink_rush" to render(INK_RUSH))

    val NAMES: List<String> = listOf("ink_rush")

    /** 16-bit PCM mono WAV at [RATE]. */
    fun wav(samples: FloatArray): ByteArray = SfxSynth.wav(samples, RATE)
}
