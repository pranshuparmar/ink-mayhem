package com.pranshuparmar.inkmayhem.android

import android.animation.ValueAnimator
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.pranshuparmar.inkmayhem.Platform
import com.pranshuparmar.inkmayhem.input.PenQueue

class AndroidPlatform(private val activity: AndroidLauncher, override val penQueue: PenQueue) : Platform {

    override val isDebugBuild: Boolean = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override val density: Float get() = activity.resources.displayMetrics.density

    override val description: String = "Android ${Build.VERSION.RELEASE} · ${Build.MODEL}"

    @Volatile private var dark = isNight(activity.resources.configuration)
    @Volatile private var reducedMotion = !ValueAnimator.areAnimatorsEnabled()

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            activity.getSystemService(Vibrator::class.java)
        }

    override fun isDarkMode(): Boolean = dark

    override fun isReducedMotion(): Boolean = reducedMotion

    override fun vibrate(millis: Int, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(millis.toLong(), amplitude.coerceIn(1, 255)))
    }

    fun onConfigurationChanged(config: Configuration) {
        dark = isNight(config)
    }

    fun onResume() {
        reducedMotion = !ValueAnimator.areAnimatorsEnabled()
    }

    private fun isNight(config: Configuration): Boolean =
        (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}
