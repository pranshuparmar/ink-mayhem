package com.pranshuparmar.inkmayhem.input

/**
 * The pressure a stroke settles at, which decides its one ink: the median of its normalized
 * samples after the pen has landed ([LAND_S]) and, once the stroke is finished, before it
 * lifts ([LIFT_S]). Pen Lab measured a player taking 110–150 ms to reach their steady pressure
 * after touchdown and 50–70 ms to let go: a third of a quick rope stroke, and enough to give
 * almost every stroke a light tip under per-segment ink. A median also rides out the wobble
 * and drift along a stroke.
 *
 * One instance is reused for every stroke; nothing allocates per sample.
 */
class SteadyPressure {
    private val hist = IntArray(BINS)
    private var counted = 0
    private val ts = DoubleArray(CAPACITY)
    private val ps = FloatArray(CAPACITY)
    private var n = 0
    private var t0 = Double.NaN
    private var cached = Float.NaN
    private var overflow = false

    /** Starts a new stroke. */
    fun reset() {
        hist.fill(0)
        counted = 0
        n = 0
        overflow = false
        t0 = Double.NaN
        cached = Float.NaN
    }

    /** One pen sample: [t] in seconds, [p] normalized 0..1. */
    fun add(t: Double, p: Float) {
        if (t0.isNaN()) t0 = t
        if (n < CAPACITY) {
            ts[n] = t
            ps[n] = p
            n++
        } else {
            overflow = true
        }
        if (t - t0 >= LAND_S) {
            // the landing window has just passed: its samples stay out
            hist[bin(p)]++
            counted++
            cached = Float.NaN
        }
    }

    /** Whether the pen has been down long enough for [value] to stop being provisional. */
    val settled: Boolean get() = counted >= MIN_SAMPLES

    /** While drawing: the steady pressure so far, or [provisional] until the pen has landed. */
    fun value(provisional: Float): Float {
        if (!settled) return provisional
        if (cached.isNaN()) cached = medianOf(hist, counted)
        return cached
    }

    /**
     * The stroke is finished: its steady pressure without the landing and lifting. A stroke too
     * short for both windows uses the middle 70 % of its samples, or all of them.
     */
    fun finish(provisional: Float): Float {
        if (n == 0) return provisional
        if (overflow) return if (counted > 0) medianOf(hist, counted) else provisional
        val tEnd = ts[n - 1]
        var k = n - 1
        while (k >= 0 && tEnd - ts[k] < LIFT_S) {
            if (ts[k] - t0 >= LAND_S) {
                hist[bin(ps[k])]--
                counted--
            }
            k--
        }
        cached = Float.NaN
        if (counted >= MIN_SAMPLES) return medianOf(hist, counted)
        // short: the middle of what there is
        hist.fill(0)
        val cut = (n * 0.15f).toInt()
        var m = 0
        for (i in cut until n - cut) {
            hist[bin(ps[i])]++
            m++
        }
        return if (m > 0) medianOf(hist, m) else provisional
    }

    private fun bin(p: Float): Int = (p.coerceIn(0f, 1f) * (BINS - 1) + 0.5f).toInt()

    private fun medianOf(h: IntArray, total: Int): Float {
        val target = (total - 1) / 2
        var seen = 0
        for (i in h.indices) {
            seen += h[i]
            if (seen > target) return i / (BINS - 1).toFloat()
        }
        return 1f
    }

    companion object {
        /** The pen landing: samples this long after touchdown are left out. */
        const val LAND_S = 0.15
        /** The pen lifting: the last samples this long before pen-up are left out of the finish. */
        const val LIFT_S = 0.07
        const val MIN_SAMPLES = 5
        /** Histogram resolution: 0.001 of normalized pressure. */
        private const val BINS = 1001
        /** About 8 s at 480 samples a second; past it only the histogram keeps counting. */
        private const val CAPACITY = 4096
    }
}
