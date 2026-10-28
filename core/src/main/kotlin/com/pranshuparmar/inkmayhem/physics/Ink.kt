package com.pranshuparmar.inkmayhem.physics

import kotlin.math.max
import kotlin.math.min

/** What pressure turns ink into. [weight] orders the kinds for par judging (rope < spring < beam). */
enum class Ink(val weight: Int, val label: String) {
    ROPE(0, "rope"), SPRING(1, "spring"), BEAM(2, "beam");

    companion object {
        /** Normalized-pressure thresholds (InkCore C.ROPE_MAX / C.SPRING_MAX). */
        const val ROPE_MAX = 0.33f
        const val SPRING_MAX = 0.62f
        const val T_MIN = 0.06f
        const val T_MAX = 0.45f

        fun of(p: Float): Ink = if (p < ROPE_MAX) ROPE else if (p < SPRING_MAX) SPRING else BEAM

        fun ofWeight(w: Int): Ink = entries[w]

        /** [p] kept inside [ink]'s band, so a one-ink stroke still thickens and thins with the pen. */
        fun clampTo(p: Float, ink: Ink): Float = when (ink) {
            ROPE -> min(p, ROPE_MAX - EDGE)
            SPRING -> p.coerceIn(ROPE_MAX, SPRING_MAX - EDGE)
            BEAM -> max(p, SPRING_MAX)
        }

        private const val EDGE = 1e-4f

        /** Stroke thickness in metres: t = 0.06 + 0.39 p. */
        fun thickness(p: Float): Float = T_MIN + (T_MAX - T_MIN) * p.coerceIn(0f, 1f)
    }
}
