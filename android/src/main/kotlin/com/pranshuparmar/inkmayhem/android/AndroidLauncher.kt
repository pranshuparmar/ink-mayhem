package com.pranshuparmar.inkmayhem.android

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.MotionEvent
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidGraphics
import com.pranshuparmar.inkmayhem.InkMayhemGame
import com.pranshuparmar.inkmayhem.input.PenQueue

/**
 * Hosts the libGDX game. All touch and hover events are intercepted here and routed through
 * [AndroidPenInput] instead of libGDX's own Input, which drops tool type, hover and stylus buttons.
 */
class AndroidLauncher : AndroidApplication() {
    private val penQueue = PenQueue()
    private lateinit var platform: AndroidPlatform
    private var pen: AndroidPenInput? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platform = AndroidPlatform(this, penQueue)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useWakelock = true
            renderUnderCutout = true
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            numSamples = 4
            depth = 0
        }
        initialize(InkMayhemGame(platform), config)
        pen = AndroidPenInput(penQueue, (getGraphics() as AndroidGraphics).view)
        preferHighestRefreshRate()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val p = pen ?: return super.dispatchTouchEvent(ev)
        p.onTouch(ev)
        return true
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val p = pen ?: return super.dispatchGenericMotionEvent(ev)
        return p.onGenericMotion(ev) || super.dispatchGenericMotionEvent(ev)
    }

    override fun onConfigurationChanged(config: Configuration) {
        // Rotation, size and dark-mode changes arrive here instead of recreating the activity
        // (see android:configChanges), so a run survives rotating the device.
        super.onConfigurationChanged(config)
        platform.onConfigurationChanged(config)
    }

    override fun onResume() {
        super.onResume()
        if (::platform.isInitialized) platform.onResume()
    }

    /** Ask for the fastest mode at the current resolution (120 Hz on Galaxy Ultras). */
    private fun preferHighestRefreshRate() {
        val display: Display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display ?: return
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        if (best.modeId != current.modeId) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        }
    }
}
