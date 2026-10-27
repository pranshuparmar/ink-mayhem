package com.pranshuparmar.inkmayhem

import com.badlogic.gdx.Preferences

/**
 * Persisted settings, calibration and best score (libGDX Preferences "ink-mayhem"). Values are
 * cached in memory because the game reads several of them every frame; writes go straight through.
 */
class Settings(private val prefs: Preferences) {
    var sound: Boolean = prefs.getBoolean("sound", true)
        set(v) { field = v; put { putBoolean("sound", v) } }

    /** "Colour ink by type": rope green, spring amber, beam red. On by default (the POC's was off). */
    var tint: Boolean = prefs.getBoolean("tint", true)
        set(v) { field = v; put { putBoolean("tint", v) } }

    /** "Pen readout": the debug overlay. */
    var penReadout: Boolean = prefs.getBoolean("penReadout", false)
        set(v) { field = v; put { putBoolean("penReadout", v) } }

    /** Debug builds only: fingers and mouse draw at a fixed pressure. */
    var allowFinger: Boolean = prefs.getBoolean("allowFinger", false)
        set(v) { field = v; put { putBoolean("allowFinger", v) } }

    var haptics: Boolean = prefs.getBoolean("haptics", true)
        set(v) { field = v; put { putBoolean("haptics", v) } }

    /** The first-run tutorial was finished or skipped; it stays available from settings. */
    var tutorialDone: Boolean = prefs.getBoolean("tutorialDone", false)
        set(v) { field = v; put { putBoolean("tutorialDone", v) } }

    /** Median raw calibration pressure, or null if the player never calibrated or skipped. */
    var calibration: Float? = if (prefs.contains("cal")) prefs.getFloat("cal") else null
        set(v) { field = v; put { if (v == null) remove("cal") else putFloat("cal", v) } }

    var best: Int = prefs.getInteger("best", 0)
        set(v) { field = v; put { putInteger("best", v) } }

    private inline fun put(block: Preferences.() -> Unit) {
        prefs.block()
        prefs.flush()
    }
}
