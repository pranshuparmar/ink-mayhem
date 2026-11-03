package com.pranshuparmar.inkmayhem

import com.badlogic.gdx.Preferences
import com.pranshuparmar.inkmayhem.game.StageStore
import com.pranshuparmar.inkmayhem.input.PenCalibration

/**
 * Persisted settings, calibration and best score (libGDX Preferences "ink-mayhem"). Values are
 * cached in memory because the game reads several of them every frame; writes go straight through.
 */
class Settings(private val prefs: Preferences) : StageStore {
    init {
        migrate()
    }

    /**
     * Sound effects' and music's volume, 0 (off) to 1. Older installs had on/off switches
     * ("sound", "music"), which carry over as full or off.
     */
    var soundVolume: Float = volume("soundVolume", "sound")
        set(v) { field = v.coerceIn(0f, 1f); put { putFloat("soundVolume", field) } }

    var musicVolume: Float = volume("musicVolume", "music")
        set(v) { field = v.coerceIn(0f, 1f); put { putFloat("musicVolume", field) } }

    val sound: Boolean get() = soundVolume > 0f
    val music: Boolean get() = musicVolume > 0f

    private fun volume(key: String, oldSwitch: String): Float = when {
        prefs.contains(key) -> prefs.getFloat(key, 1f).coerceIn(0f, 1f)
        else -> if (prefs.getBoolean(oldSwitch, true)) 1f else 0f
    }

    /** "Colour ink by type": rope green, spring amber, beam red. On by default. */
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

    /** Dot's remarks: little speech bubbles now and then. */
    var remarks: Boolean = prefs.getBoolean("remarks", true)
        set(v) { field = v; put { putBoolean("remarks", v) } }

    /** Dot has introduced itself (on the very first launch). */
    var metDot: Boolean = prefs.getBoolean("metDot", false)
        set(v) { field = v; put { putBoolean("metDot", v) } }

    /** Debug builds only: record each session's input for replays. On by default; read at launch. */
    var recordInput: Boolean = prefs.getBoolean(RECORD_INPUT, true)
        set(v) { field = v; put { putBoolean(RECORD_INPUT, v) } }

    /** The first-run tutorial was finished or skipped; it stays available from settings. */
    var tutorialDone: Boolean = prefs.getBoolean("tutorialDone", false)
        set(v) { field = v; put { putBoolean("tutorialDone", v) } }

    /**
     * The player's pen tuning (from their rope, spring and beam lines), or null if they never
     * tuned it. Early builds kept one number ("cal", a signature's median pressure): that still
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

    // ---- stage results: best stars and score per stage (never lowered) ----

    override fun stars(id: String): Int = prefs.getInteger("stars.$id", 0)

    override fun best(id: String): Int = prefs.getInteger("best.$id", 0)

    override fun gold(id: String): Int = prefs.getInteger("gold.$id", 0)

    override fun record(id: String, stars: Int, score: Int, gold: Int) = put {
        putInteger("stars.$id", maxOf(stars(id), stars))
        putInteger("best.$id", maxOf(best(id), score))
        putInteger("gold.$id", gold(id) or gold)
    }

    private inline fun put(block: Preferences.() -> Unit) {
        prefs.block()
        prefs.flush()
    }

    /**
     * Brings a save up to [SAVE_VERSION] once, then stamps it. Version 0 is any save from before
     * saves were versioned: one from before the five worlds, when 1-2 to 1-4 were three other
     * stages (Cellar, Up High, Castle), has no gold-star keys (every clear since writes one), and
     * its results for those three are dropped so they don't show on the stages there now.
     */
    private fun migrate() {
        val from = prefs.getInteger(SAVE_VERSION_KEY, 0)
        if (from >= SAVE_VERSION) return
        put {
            if (from < 1 && prefs.get().keys.none { it.startsWith("gold.") }) {
                for (id in OLD_WORLD_1) {
                    remove("stars.$id")
                    remove("best.$id")
                }
            }
            putInteger(SAVE_VERSION_KEY, SAVE_VERSION)
        }
    }

    companion object {
        const val RECORD_INPUT = "recordInput"
        /** The save's format: bump it (and teach [migrate] the step) whenever saved data changes meaning. */
        const val SAVE_VERSION = 1
        const val SAVE_VERSION_KEY = "saveVersion"
        /** Stage ids that held other stages before the five worlds. */
        private val OLD_WORLD_1 = listOf("1-2", "1-3", "1-4")
    }
}
