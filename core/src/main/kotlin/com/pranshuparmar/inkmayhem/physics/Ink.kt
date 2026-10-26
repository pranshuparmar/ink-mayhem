package com.pranshuparmar.inkmayhem.physics

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

        /** Stroke thickness in metres: t = 0.06 + 0.39 p. */
        fun thickness(p: Float): Float = T_MIN + (T_MAX - T_MIN) * p.coerceIn(0f, 1f)
    }
}
