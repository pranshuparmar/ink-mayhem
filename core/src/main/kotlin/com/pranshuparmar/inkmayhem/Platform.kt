package com.pranshuparmar.inkmayhem

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.pranshuparmar.inkmayhem.input.PenQueue
import java.io.File

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

    /** Where settings, calibration and scores are kept. A replay substitutes the recorded ones. */
    fun preferences(): Preferences = Gdx.app.getPreferences("ink-mayhem")

    /** Version name for recordings, e.g. "0.1.0-debug (42)". */
    val appVersion: String get() = ""

    /** Debug builds: a new file to record this launch's input into, or null not to record. */
    fun newRecording(): File? = null

    /** Debug builds: put a recording where the player can reach it and offer to share it. */
    fun shareRecording(file: File) = Unit
}
