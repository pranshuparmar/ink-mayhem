package com.pranshuparmar.inkmayhem.input

/** Pressure constants; the mapping itself is [PenCalibration]. */
object Pressure {
    /** The POC's default calibration: this raw pressure lands at [TARGET] (used when the pen is never tuned). */
    const val DEFAULT_CAL = 0.32f
    /** The POC's one-number calibration put natural writing here: mid-spring. */
    const val TARGET = 0.45f
    /** Fingers and the mouse (debug only) draw at this fixed normalized pressure. */
    const val FINGER = 0.45f
    /** Raw pressure used for tuning lines drawn with a finger (debug only). */
    const val CAL_FINGER_RAW = 0.32f
}
