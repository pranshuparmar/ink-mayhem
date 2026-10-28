package com.pranshuparmar.inkmayhem

import com.badlogic.gdx.Preferences
import com.pranshuparmar.inkmayhem.game.StageStore
import com.pranshuparmar.inkmayhem.input.PenCalibration

/**
 * Persisted settings, calibration and best score (libGDX Preferences "ink-mayhem"). Values are
 * cached in memory because the game reads several of them every frame; writes go straight through.
 */
class Settings(private val prefs: Preferences) : StageStore {
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

    /** Debug builds only: record each session's input for replays. On by default; read at launch. */
    var recordInput: Boolean = prefs.getBoolean(RECORD_INPUT, true)
        set(v) { field = v; put { putBoolean(RECORD_INPUT, v) } }

    /** The first-run tutorial was finished or skipped; it stays available from settings. */
    var tutorialDone: Boolean = prefs.getBoolean("tutorialDone", false)
        set(v) { field = v; put { putBoolean("tutorialDone", v) } }

    /**
     * The player's pen tuning (from their rope, spring and beam lines), or null if they never
     * tuned it. Older installs kept one number ("cal", the POC's signature median): that still
     * works, as a straight-line mapping, until they tune again.
     */
    var calibration: PenCalibration? = when {
        prefs.contains("cal.rope") -> runCatching {
            PenCalibration(prefs.getFloat("cal.rope"), prefs.getFloat("cal.spring"), prefs.getFloat("cal.top"))
        }.getOrNull()
        prefs.contains("cal") -> runCatching { PenCalibration.linear(prefs.getFloat("cal")) }.getOrNull()
        else -> null
    }
        set(v) {
            field = v
            penTuned = v != null
            put {
                remove("cal")
                if (v == null) {
                    remove("cal.rope"); remove("cal.spring"); remove("cal.top")
                } else {
                    putFloat("cal.rope", v.ropeEdge); putFloat("cal.spring", v.springEdge); putFloat("cal.top", v.top)
                }
            }
        }

    /** Whether pen tuning has been done or skipped (an old one-number calibration doesn't count). */
    var penTuned: Boolean = calibration != null && prefs.contains("cal.rope")
        private set

    var best: Int = prefs.getInteger("best", 0)
        set(v) { field = v; put { putInteger("best", v) } }

    // ---- stage results: best stars and score per stage (never lowered) ----

    override fun stars(id: String): Int = prefs.getInteger("stars.$id", 0)

    override fun best(id: String): Int = prefs.getInteger("best.$id", 0)

    override fun record(id: String, stars: Int, score: Int) = put {
        putInteger("stars.$id", maxOf(stars(id), stars))
        putInteger("best.$id", maxOf(best(id), score))
    }

    private inline fun put(block: Preferences.() -> Unit) {
        prefs.block()
        prefs.flush()
    }

    companion object {
        const val RECORD_INPUT = "recordInput"
    }
}
