package com.pranshuparmar.inkmayhem.ui

import kotlin.math.max
import kotlin.math.min

/** Where centred cards sit: the viewport minus the POC's 16 dp (or safe-area) padding. */
class Frame(var wDp: Float = 0f, var hDp: Float = 0f, var safeL: Float = 0f, var safeT: Float = 0f, var safeR: Float = 0f, var safeB: Float = 0f) {
    val padL: Float get() = max(16f, safeL)
    val padR: Float get() = max(16f, safeR)
    val cardW: Float get() = min(wDp - padL - padR, 420f)
}
