package com.pranshuparmar.inkmayhem.input

import com.pranshuparmar.inkmayhem.physics.Ink
import kotlin.math.sqrt

/**
 * Maps raw pen pressure to the game's 0..1 pressure. Built from the player's own rope, spring
 * and beam lines: the mapping is piecewise linear, putting [Ink.ROPE_MAX] at [ropeEdge] (between
 * their rope and their spring), [Ink.SPRING_MAX] at [springEdge] (between spring and beam) and 1
 * at [top], so each of their three pressures lands in the middle of its band.
 */
class PenCalibration(val ropeEdge: Float, val springEdge: Float, val top: Float) {
    init {
        require(ropeEdge > 0f && springEdge > ropeEdge && top > springEdge) { "edges must rise: $ropeEdge $springEdge $top" }
    }

    fun normalize(raw: Float): Float {
        val p = when {
            raw <= 0f -> 0f
            raw < ropeEdge -> Ink.ROPE_MAX * raw / ropeEdge
            raw < springEdge -> Ink.ROPE_MAX + (Ink.SPRING_MAX - Ink.ROPE_MAX) * (raw - ropeEdge) / (springEdge - ropeEdge)
            else -> Ink.SPRING_MAX + (1f - Ink.SPRING_MAX) * (raw - springEdge) / (top - springEdge)
        }
        return p.coerceIn(0f, 1f)
    }

    companion object {
        /**
         * From the median raw pressure of the player's rope, spring and beam lines (rising). The
         * band edges sit at the geometric means between them (pressure differences feel like
         * ratios), and the beam line lands halfway up the beam band.
         */
        fun fromLines(rope: Float, spring: Float, beam: Float): PenCalibration {
            val r = sqrt(rope * spring)
            val s = sqrt(spring * beam)
            return PenCalibration(r, s, beam + (beam - s))
        }

        /** A one-number calibration (that raw pressure lands at 0.45): a straight line. */
        fun linear(cal: Float): PenCalibration {
            val unit = cal / Pressure.TARGET // raw pressure that maps to 1
            return PenCalibration(Ink.ROPE_MAX * unit, Ink.SPRING_MAX * unit, unit)
        }

        /** Used until the player tunes their pen, or if they skip. */
        val DEFAULT = linear(Pressure.DEFAULT_CAL)
    }
}

/**
 * The pen-tuning steps: the player draws a rope (light), a spring (normal) and a beam (hard) line
 * along a guide, and each is checked before the next. Pure logic; the overlay draws it.
 */
class PenTuning {
    enum class Step(val ink: Ink?) { ROPE(Ink.ROPE), SPRING(Ink.SPRING), BEAM(Ink.BEAM), CHECK(null) }

    enum class Verdict { ACCEPTED, TOO_SHORT, NOT_HEAVIER }

    var step = Step.ROPE
        private set
    /** Median raw pressure of the accepted rope, spring and beam lines. */
    val medians = FloatArray(3)
    /** Known once the beam line is accepted. */
    var calibration: PenCalibration? = null
        private set

    fun restart() {
        step = Step.ROPE
        medians.fill(0f)
        calibration = null
    }

    /**
     * One finished line: its raw pressures in order ([n] of [raw]) and its length as a fraction of
     * the guide. Accepted lines move to the next step.
     */
    fun submit(raw: FloatArray, n: Int, lengthFraction: Float): Verdict {
        val ink = step.ink ?: return Verdict.ACCEPTED
        val m = lineMedian(raw, n)
        if (lengthFraction < MIN_LENGTH || m.isNaN()) return Verdict.TOO_SHORT
        val i = ink.weight
        if (i > 0 && !heavier(m, medians[i - 1], if (i == 1) MIN_STEP_SPRING else MIN_STEP_BEAM)) return Verdict.NOT_HEAVIER
        medians[i] = m
        step = Step.entries[i + 1]
        if (step == Step.CHECK) calibration = PenCalibration.fromLines(medians[0], medians[1], medians[2])
        return Verdict.ACCEPTED
    }

    companion object {
        /** Whether a line at raw pressure [m] is pressed enough harder than one at [before]. */
        fun heavier(m: Float, before: Float, minStep: Float): Boolean =
            (m >= before * MIN_RATIO && m - before >= minStep) || (m >= SATURATED && m > before)

        /** Whether three raw pressures rise enough apart, as the tuning steps require, to tune from. */
        fun rising(rope: Float, spring: Float, beam: Float): Boolean =
            rope > FLOOR && heavier(spring, rope, MIN_STEP_SPRING) && heavier(beam, spring, MIN_STEP_BEAM)

        /** A line must run at least this much of the guide. */
        const val MIN_LENGTH = 0.5f
        /** Each line must press at least this much harder than the one before (ratio and raw step). */
        const val MIN_RATIO = 1.2f
        const val MIN_STEP_SPRING = 0.004f
        const val MIN_STEP_BEAM = 0.008f
        /** Pens that top out: a beam at full pressure counts if it's harder at all. */
        const val SATURATED = 0.98f
        /** Samples lighter than this are the pen landing or lifting. */
        const val FLOOR = 0.001f
        const val MIN_SAMPLES = 8

        /** Median of a line's pressure, ignoring its first and last 15 % (landing and lifting). */
        fun lineMedian(raw: FloatArray, n: Int): Float {
            var k = 0
            val kept = FloatArray(n)
            val cut = (n * 0.15f).toInt()
            for (i in cut until n - cut) if (raw[i] > FLOOR) kept[k++] = raw[i]
            if (k < MIN_SAMPLES) return Float.NaN
            kept.sort(0, k)
            return kept[k / 2]
        }
    }
}
