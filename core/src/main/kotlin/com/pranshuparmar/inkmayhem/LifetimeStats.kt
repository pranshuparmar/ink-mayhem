package com.pranshuparmar.inkmayhem

import com.badlogic.gdx.Preferences
import com.pranshuparmar.inkmayhem.physics.Ink

/**
 * Lifetime numbers for the Notebook's stats page: strokes by ink and how much was drawn, erases,
 * lives lost, the best combo, stages cleared and played. Counted in memory as play goes and
 * written to the preferences now and then ([Settings.saveStats]), so drawing a stroke never
 * waits on storage. Only ever read for display: nothing in a run depends on them.
 */
class LifetimeStats(prefs: Preferences) {
    val strokes = IntArray(3) { prefs.getInteger(KEY_STROKES + Ink.entries[it].label, 0) }
    /** Metres of ink drawn, all inks. */
    var metres: Float = prefs.getFloat(KEY_METRES, 0f)
        private set
    var erases: Int = prefs.getInteger(KEY_ERASES, 0)
        private set
    var livesLost: Int = prefs.getInteger(KEY_LIVES, 0)
        private set
    var bestCombo: Int = prefs.getInteger(KEY_COMBO, 1)
        private set
    var clears: Int = prefs.getInteger(KEY_CLEARS, 0)
        private set
    var starts: Int = prefs.getInteger(KEY_STARTS, 0)
        private set
    var hintsAsked: Int = prefs.getInteger(KEY_HINTS, 0)
        private set
    var pagesTorn: Int = prefs.getInteger(KEY_PAGES, 0)
        private set

    /** Something changed since the last write. */
    var dirty = false
        private set

    val totalStrokes: Int get() = strokes.sum()

    fun stroke(ink: Ink, length: Float) {
        strokes[ink.weight]++
        if (length.isFinite() && length > 0f) metres += length
        dirty = true
    }

    fun erased() { erases++; dirty = true }
    fun lifeLost() { livesLost++; dirty = true }
    fun cleared() { clears++; dirty = true }
    fun started() { starts++; dirty = true }
    fun hint() { hintsAsked++; dirty = true }
    fun pageTorn() { pagesTorn++; dirty = true }

    /** Returns true when [combo] is a new best. */
    fun combo(combo: Int): Boolean {
        if (combo <= bestCombo) return false
        bestCombo = combo
        dirty = true
        return true
    }

    fun writeTo(p: Preferences) {
        for (i in strokes.indices) p.putInteger(KEY_STROKES + Ink.entries[i].label, strokes[i])
        p.putFloat(KEY_METRES, metres)
        p.putInteger(KEY_ERASES, erases)
        p.putInteger(KEY_LIVES, livesLost)
        p.putInteger(KEY_COMBO, bestCombo)
        p.putInteger(KEY_CLEARS, clears)
        p.putInteger(KEY_STARTS, starts)
        p.putInteger(KEY_HINTS, hintsAsked)
        p.putInteger(KEY_PAGES, pagesTorn)
        dirty = false
    }

    private companion object {
        const val KEY_STROKES = "stat.strokes."
        const val KEY_METRES = "stat.metres"
        const val KEY_ERASES = "stat.erases"
        const val KEY_LIVES = "stat.livesLost"
        const val KEY_COMBO = "stat.bestCombo"
        const val KEY_CLEARS = "stat.clears"
        const val KEY_STARTS = "stat.starts"
        const val KEY_HINTS = "stat.hints"
        const val KEY_PAGES = "stat.pages"
    }
}
