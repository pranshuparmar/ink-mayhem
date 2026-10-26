package com.pranshuparmar.inkmayhem

import com.pranshuparmar.inkmayhem.input.PenQueue

/** Everything the core needs from the host OS. Implemented by the Android and desktop launchers. */
interface Platform {
    /** Debug builds expose the "Allow finger and mouse" setting. */
    val isDebugBuild: Boolean

    /** Pen samples produced by the platform input thread, drained by the core on the GL thread. */
    val penQueue: PenQueue

    /** Pixels per dp (CSS px in the POC). Safe-area insets come from Gdx.graphics.getSafeInset*. */
    val density: Float

    /** Short label for the pen readout, e.g. "Android 16 · SM-S938B". */
    val description: String

    /** System dark theme. Polled every frame, so it must be cheap. */
    fun isDarkMode(): Boolean

    /** System "remove animations" / reduced-motion setting. Polled, so it must be cheap. */
    fun isReducedMotion(): Boolean

    /** Short haptic pulse; [amplitude] is 1..255. */
    fun vibrate(millis: Int, amplitude: Int)
}
