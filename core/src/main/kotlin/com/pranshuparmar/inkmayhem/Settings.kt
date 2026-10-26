package com.pranshuparmar.inkmayhem

import com.badlogic.gdx.Preferences

/** Persisted settings, calibration and best score (libGDX Preferences "ink-mayhem"). */
class Settings(private val prefs: Preferences) {
    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) = put { putBoolean("sound", v) }

    /** "Colour ink by type": rope green, spring amber, beam red. */
    var tint: Boolean
        get() = prefs.getBoolean("tint", false)
        set(v) = put { putBoolean("tint", v) }

    /** "Pen readout": the debug overlay. */
    var penReadout: Boolean
        get() = prefs.getBoolean("penReadout", false)
        set(v) = put { putBoolean("penReadout", v) }

    /** Debug builds only: fingers and mouse draw at a fixed pressure. */
    var allowFinger: Boolean
        get() = prefs.getBoolean("allowFinger", false)
        set(v) = put { putBoolean("allowFinger", v) }

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) = put { putBoolean("haptics", v) }

    /** Median raw signature pressure, or null if the player never calibrated or skipped. */
    var calibration: Float?
        get() = if (prefs.contains("cal")) prefs.getFloat("cal") else null
        set(v) = put { if (v == null) remove("cal") else putFloat("cal", v) }

    var best: Int
        get() = prefs.getInteger("best", 0)
        set(v) = put { putInteger("best", v) }

    private inline fun put(block: Preferences.() -> Unit) {
        prefs.block()
        prefs.flush()
    }
}
