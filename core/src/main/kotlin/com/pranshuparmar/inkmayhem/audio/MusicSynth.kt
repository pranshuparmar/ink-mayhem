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
 * The game's tunes, synthesized like the sound effects: "Ink Rush", the signature theme (World 1,
 * an original eight-bar chiptune in A minor at 168 bpm the player chose from three in the design
 * pitch), a tune for each later world, and one for boss fights. Each is one seamless loop of mono
 * 16-bit PCM, deterministic so the committed WAVs can be checked byte for byte.
 */
object MusicSynth {
    /** Lower than the effects' 44.1 kHz: the loop has to stay small enough to play as a sound. */
    const val RATE = 22050

    enum class Lead { SQUARE, PULSE, TRIANGLE }

    /** Root and fifth on the quarters (Ink Rush), root and fifth on the halves, or driving octave eighths. */
    enum class Bass { QUARTERS, HALVES, EIGHTHS }

    /** Kick, snare and hats (Ink Rush); a soft kick and rim; a march with snare rolls; four on the floor. */
    enum class Drums { STANDARD, SOFT, MARCH, DRIVING }

    /**
     * A tune: [lead] notes over eight bars of [chords] ("Am", "Bb", "F#"...: the bass and the
     * arpeggio follow them). [echo] repeats the lead [echo] eighths later, fading by [echoGain].
     */
    class Theme(
        val bpm: Double,
        val lead: List<Pair<String, Int>>,
        val chords: List<String>,
        val leadWave: Lead = Lead.SQUARE,
        val bass: Bass = Bass.QUARTERS,
        val drums: Drums = Drums.STANDARD,
        val cutoff: Double = 3200.0,
        val echo: Int = 0,
        val echoGain: Double = 0.0,
        val arp: Boolean = false,
    ) {
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

    /** World 2, the Cellar: D minor at 120 bpm, a hollow pulse lead that echoes off the walls, slow bass, soft drums. */
    val CELLAR = Theme(
        bpm = 120.0,
        lead = listOf(
            "D5" to 2, "R" to 1, "F5" to 1, "A4" to 2, "R" to 2,
            "Bb4" to 2, "R" to 1, "D5" to 1, "F5" to 3, "E5" to 1,
            "E5" to 2, "R" to 1, "G5" to 1, "C5" to 2, "R" to 1, "D5" to 1,
            "E5" to 3, "C#5" to 1, "A4" to 4,
            "D5" to 1, "F5" to 1, "A5" to 2, "G5" to 1, "F5" to 1, "E5" to 2,
            "F5" to 1, "D5" to 1, "Bb4" to 2, "R" to 2, "A4" to 1, "Bb4" to 1,
            "G4" to 1, "Bb4" to 1, "D5" to 2, "C5" to 1, "Bb4" to 1, "A4" to 2,
            "C#5" to 2, "E5" to 2, "D5" to 4,
        ),
        chords = listOf("Dm", "Bb", "C", "A", "Dm", "Bb", "Gm", "A"),
        leadWave = Lead.PULSE, bass = Bass.HALVES, drums = Drums.SOFT, cutoff = 2400.0, echo = 3, echoGain = 0.38,
    )

    /** World 3, Up High: C major at 150 bpm, a flute-like triangle lead soaring over sparkling arpeggios. */
    val SKY = Theme(
        bpm = 150.0,
        lead = listOf(
            "E5" to 2, "G5" to 2, "C6" to 3, "B5" to 1,
            "D6" to 2, "B5" to 2, "G5" to 4,
            "A5" to 2, "C6" to 2, "E6" to 3, "D6" to 1,
            "C6" to 2, "A5" to 2, "F5" to 4,
            "G5" to 1, "A5" to 1, "G5" to 1, "E5" to 1, "C5" to 2, "E5" to 2,
            "D5" to 1, "E5" to 1, "G5" to 2, "B5" to 2, "D6" to 2,
            "C6" to 2, "A5" to 1, "C6" to 1, "F6" to 3, "E6" to 1,
            "D6" to 2, "B5" to 2, "G5" to 2, "R" to 2,
        ),
        chords = listOf("C", "G", "Am", "F", "C", "G", "F", "G"),
        leadWave = Lead.TRIANGLE, arp = true, cutoff = 5000.0,
    )

    /** World 4, the Castle: E minor at 140 bpm, a march with snare rolls and a driving bass. */
    val CASTLE = Theme(
        bpm = 140.0,
        lead = listOf(
            "E5" to 1, "E5" to 1, "E5" to 1, "G5" to 1, "B5" to 2, "A5" to 1, "G5" to 1,
            "E5" to 1, "E5" to 1, "E5" to 1, "G5" to 1, "C6" to 2, "B5" to 1, "A5" to 1,
            "A5" to 2, "C6" to 2, "E6" to 2, "D6" to 1, "C6" to 1,
            "B5" to 3, "A5" to 1, "D#5" to 2, "F#5" to 2,
            "G5" to 2, "F#5" to 1, "E5" to 1, "B4" to 2, "E5" to 2,
            "C5" to 1, "E5" to 1, "G5" to 2, "C6" to 2, "B5" to 1, "A5" to 1,
            "A5" to 2, "F#5" to 1, "D5" to 1, "A5" to 2, "C6" to 2,
            "B5" to 2, "A5" to 1, "G5" to 1, "F#5" to 2, "D#5" to 2,
        ),
        chords = listOf("Em", "C", "Am", "B", "Em", "C", "D", "B"),
        bass = Bass.EIGHTHS, drums = Drums.MARCH, cutoff = 2800.0,
    )

    /** World 5, Midnight Ink: C minor at 176 bpm, a dark pulse lead with a short echo over four-on-the-floor. */
    val MIDNIGHT = Theme(
        bpm = 176.0,
        lead = listOf(
            "C5" to 1, "R" to 1, "C5" to 1, "Eb5" to 1, "G5" to 1, "R" to 1, "F5" to 1, "Eb5" to 1,
            "Ab4" to 1, "R" to 1, "C5" to 1, "Eb5" to 1, "Ab5" to 2, "G5" to 1, "F5" to 1,
            "Bb4" to 1, "R" to 1, "D5" to 1, "F5" to 1, "Bb5" to 2, "Ab5" to 1, "G5" to 1,
            "G5" to 3, "F5" to 1, "D5" to 2, "Bb4" to 2,
            "C6" to 1, "Bb5" to 1, "G5" to 1, "Eb5" to 1, "C5" to 1, "Eb5" to 1, "G5" to 1, "C6" to 1,
            "Ab5" to 2, "G5" to 1, "F5" to 1, "Eb5" to 2, "C5" to 2,
            "F5" to 1, "Ab5" to 1, "C6" to 2, "Bb5" to 1, "Ab5" to 1, "G5" to 1, "F5" to 1,
            "G5" to 2, "D5" to 1, "F5" to 1, "B4" to 2, "D5" to 2,
        ),
        chords = listOf("Cm", "Ab", "Bb", "Gm", "Cm", "Ab", "Fm", "G"),
        leadWave = Lead.PULSE, bass = Bass.EIGHTHS, drums = Drums.DRIVING, cutoff = 3000.0, echo = 1, echoGain = 0.25,
    )

    /** Boss fights: A minor (Ink Rush's key) at 184 bpm, racing arpeggios over a driving beat. */
    val BOSS = Theme(
        bpm = 184.0,
        lead = listOf(
            "A4" to 1, "C5" to 1, "E5" to 1, "A5" to 1, "G5" to 1, "E5" to 1, "C5" to 1, "E5" to 1,
            "F4" to 1, "A4" to 1, "C5" to 1, "F5" to 1, "E5" to 1, "C5" to 1, "A4" to 1, "C5" to 1,
            "G4" to 1, "B4" to 1, "D5" to 1, "G5" to 1, "F5" to 1, "D5" to 1, "B4" to 1, "D5" to 1,
            "G#4" to 1, "B4" to 1, "E5" to 1, "G#5" to 1, "B5" to 2, "G#5" to 2,
            "A5" to 2, "E5" to 1, "A5" to 1, "C6" to 2, "B5" to 1, "A5" to 1,
            "F5" to 2, "C5" to 1, "F5" to 1, "A5" to 2, "G5" to 1, "F5" to 1,
            "D5" to 1, "F5" to 1, "A5" to 1, "D6" to 1, "C6" to 1, "A5" to 1, "F5" to 1, "A5" to 1,
            "B5" to 2, "G#5" to 2, "E5" to 2, "B4" to 2,
        ),
        chords = listOf("Am", "F", "G", "E", "Am", "F", "Dm", "E"),
        bass = Bass.EIGHTHS, drums = Drums.DRIVING, cutoff = 3000.0,
    )

    private val ROOT = mapOf("C" to 48, "D" to 50, "E" to 52, "F" to 53, "G" to 55, "A" to 57, "B" to 59)

    /** A chord's bass root (MIDI, octave 3) and whether it's minor: "Bb" → 58, false. */
    private fun chordRoot(c: String): Int {
        var r = ROOT.getValue(c.substring(0, 1))
        if (c.length > 1 && c[1] == '#') r++
        if (c.length > 1 && c[1] == 'b') r--
        return r
    }

    private fun chordMinor(c: String): Boolean = c.endsWith("m")

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
            if (name != "R") {
                val f = hz(midi(name))
                when (theme.leadWave) {
                    Lead.SQUARE -> square(lead, f, pos * e, len * e * 0.92, 0.13)
                    Lead.PULSE -> square(lead, f, pos * e, len * e * 0.92, 0.13, duty = 0.25)
                    Lead.TRIANGLE -> triangle(lead, f, pos * e, len * e * 0.92, 0.3)
                }
            }
            pos += len
        }
        for ((bi, c) in theme.chords.withIndex()) {
            val root = chordRoot(c)
            when (theme.bass) {
                // root and fifth on the quarters
                Bass.QUARTERS -> for (q in 0 until 4) {
                    val note = if (q % 2 == 0) root else root + 7
                    triangle(buf, hz(note), bi * bar + q * 2 * e, e * 1.7, 0.32)
                }
                Bass.HALVES -> for (h in 0 until 2) triangle(buf, hz(if (h == 0) root else root + 7), bi * bar + h * 4 * e, e * 3.6, 0.34)
                Bass.EIGHTHS -> for (i in 0 until 8) triangle(buf, hz(if (i % 2 == 0) root else root + 12), bi * bar + i * e, e * 0.85, 0.28)
            }
            if (theme.arp) {
                // chord tones up and down an octave above the lead's home, quietly
                val third = if (chordMinor(c)) 3 else 4
                val steps = intArrayOf(0, third, 7, 12, 7, third, 0, third)
                for (i in 0 until 8) square(buf, hz(root + 24 + steps[i]), bi * bar + i * e, e * 0.6, 0.035, duty = 0.125)
            }
        }
        drums(buf, theme, e, bar)
        // fold the tails onto the start so the loop is seamless; the lead's square wave is softened
        // by a low-pass (3.2 kHz for Ink Rush, like the pitch page)
        val leadLoop = fold(lead, n)
        onePoleLowpass(leadLoop, theme.cutoff)
        if (theme.echo > 0) echo(leadLoop, (theme.echo * e * RATE).roundToInt(), theme.echoGain)
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

    private fun drums(buf: FloatArray, theme: Theme, e: Double, bar: Double) {
        val rnd = Random(7)
        for (bi in theme.chords.indices) {
            for (i in 0 until 8) {
                val t = bi * bar + i * e
                when (theme.drums) {
                    // kick on 1 and 3, snare on 2 and 4, hats on the off-beats
                    Drums.STANDARD -> {
                        if (i % 2 == 1) noiseHit(buf, t, 0.04, 0.18, highpass = 7000.0, rnd = rnd)
                        if (i == 0 || i == 4) kick(buf, t, 0.75)
                        if (i == 2 || i == 6) noiseHit(buf, t, 0.12, 0.35, bandpass = 1800.0, rnd = rnd)
                    }
                    // a soft kick on 1, a rim tick on 3, quiet hats
                    Drums.SOFT -> {
                        if (i % 2 == 1) noiseHit(buf, t, 0.03, 0.08, highpass = 7000.0, rnd = rnd)
                        if (i == 0) kick(buf, t, 0.5)
                        if (i == 4) noiseHit(buf, t, 0.03, 0.14, bandpass = 2600.0, rnd = rnd)
                    }
                    // a march: snare rolls into every fourth bar's end
                    Drums.MARCH -> {
                        if (i % 2 == 1) noiseHit(buf, t, 0.04, 0.12, highpass = 7000.0, rnd = rnd)
                        if (i == 0 || i == 4) kick(buf, t, 0.7)
                        if (i == 2 || i == 6) noiseHit(buf, t, 0.12, 0.33, bandpass = 1800.0, rnd = rnd)
                        if (bi % 4 == 3 && (i == 5 || i == 7)) {
                            noiseHit(buf, t, 0.06, 0.22, bandpass = 1800.0, rnd = rnd)
                            noiseHit(buf, t + e / 2, 0.06, 0.26, bandpass = 1800.0, rnd = rnd)
                        }
                    }
                    // four on the floor, snare on 2 and 4, hats on every eighth
                    Drums.DRIVING -> {
                        noiseHit(buf, t, 0.035, if (i % 2 == 1) 0.16 else 0.08, highpass = 7000.0, rnd = rnd)
                        if (i % 2 == 0) kick(buf, t, 0.7)
                        if (i == 2 || i == 6) noiseHit(buf, t, 0.12, 0.33, bandpass = 1800.0, rnd = rnd)
                    }
                }
            }
        }
    }

    /** Repeats [x] [delay] samples later, three times fading by [gain] each, wrapping round the loop. */
    private fun echo(x: FloatArray, delay: Int, gain: Double) {
        val dry = x.copyOf()
        val n = x.size
        var g = gain
        for (tap in 1..3) {
            val d = (delay * tap) % n
            for (i in 0 until n) x[i] += (dry[(i - d + n) % n] * g).toFloat()
            g *= gain
        }
    }

    private fun fold(buf: FloatArray, n: Int): FloatArray = FloatArray(n) { i -> buf[i] + if (i < buf.size - n) buf[n + i] else 0f }

    private fun envelope(t: Double, dur: Double, attack: Double, release: Double): Double = when {
        t < attack -> t / attack
        t < dur - release -> 1.0 - 0.2 * (t - attack) / maxOf(1e-6, dur - release - attack)
        t < dur -> 0.8 * (dur - t) / release
        else -> 0.0
    }

    /** A pulse wave, high for [duty] of each cycle (0.5: a square). */
    private fun square(buf: FloatArray, f: Double, start: Double, dur: Double, vol: Double, duty: Double = 0.5) {
        val i0 = (start * RATE).roundToInt()
        val n = (dur * RATE).roundToInt()
        var phase = 0.0
        for (k in 0 until n) {
            val i = i0 + k
            if (i >= buf.size) break
            val s = if (phase < duty) 1.0 else -1.0
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
    fun all(): Map<String, FloatArray> = THEMES.mapValuesTo(linkedMapOf()) { render(it.value) }

    /** Every tune by its file name: a world's (World.music) or the boss fights'. */
    val THEMES: Map<String, Theme> = linkedMapOf(
        "ink_rush" to INK_RUSH, "cellar" to CELLAR, "sky" to SKY, "castle" to CASTLE, "midnight" to MIDNIGHT, "boss" to BOSS,
    )

    val NAMES: List<String> = THEMES.keys.toList()

    /** 16-bit PCM mono WAV at [RATE]. */
    fun wav(samples: FloatArray): ByteArray = SfxSynth.wav(samples, RATE)
}
