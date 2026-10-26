package com.pranshuparmar.inkmayhem.input

/** Calibrated pressure mapping from the POC. */
object Pressure {
    /** Used when the player skips calibration. */
    const val DEFAULT_CAL = 0.32f
    const val CAL_MIN = 0.08f
    const val CAL_MAX = 0.9f
    /** Natural writing pressure lands here: mid-spring. */
    const val TARGET = 0.45f
    /** Fingers and the mouse (debug only) draw at this fixed normalized pressure. */
    const val FINGER = 0.45f
    /** The calibration pad accepts "Done" after this many samples. */
    const val CAL_MIN_SAMPLES = 30
    /** Signature samples lighter than this are ignored. */
    const val CAL_SAMPLE_FLOOR = 0.02f
    /** Pressure used for calibration-pad samples drawn with a finger (debug only). */
    const val CAL_FINGER_RAW = 0.32f

    fun normalize(raw: Float, cal: Float?): Float {
        val c = if (cal != null && cal > 0f) cal else DEFAULT_CAL
        return (TARGET * raw / c).coerceIn(0f, 1f)
    }

    /** Median of a signature, clamped to the accepted calibration range. */
    fun calibrate(samples: FloatArray, count: Int): Float {
        val sorted = samples.copyOf(count).also { it.sort() }
        return sorted[count / 2].coerceIn(CAL_MIN, CAL_MAX)
    }
}
