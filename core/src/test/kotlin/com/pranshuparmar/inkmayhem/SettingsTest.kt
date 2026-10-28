package com.pranshuparmar.inkmayhem

import com.badlogic.gdx.Preferences
import com.pranshuparmar.inkmayhem.input.PenCalibration
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pen tuning is stored, read back, and an old one-number calibration carries over. */
class SettingsTest {
    @Test
    fun `a fresh install hasn't tuned the pen`() {
        val s = Settings(MapPrefs())
        assertNull(s.calibration)
        assertFalse(s.penTuned)
    }

    @Test
    fun `an old calibration still maps, but the pen is asked to be tuned once`() {
        val prefs = MapPrefs().apply { putFloat("cal", 0.5f) }
        val s = Settings(prefs)
        assertEquals(0.45f, assertNotNull(s.calibration).normalize(0.5f), 1e-5f)
        assertFalse(s.penTuned)
        s.calibration = s.calibration // what skipping does
        assertTrue(s.penTuned)
        assertFalse(prefs.contains("cal"))
        assertTrue(Settings(prefs).penTuned)
    }

    @Test
    fun `a tuning survives a restart`() {
        val prefs = MapPrefs()
        Settings(prefs).calibration = PenCalibration.fromLines(0.03f, 0.07f, 0.2f)
        val back = assertNotNull(Settings(prefs).calibration)
        val cal = PenCalibration.fromLines(0.03f, 0.07f, 0.2f)
        assertEquals(cal.ropeEdge, back.ropeEdge)
        assertEquals(cal.springEdge, back.springEdge)
        assertEquals(cal.top, back.top)
        assertTrue(Settings(prefs).penTuned)
    }

    @Test
    fun `a broken tuning reads as never tuned`() {
        val prefs = MapPrefs().apply { putFloat("cal.rope", 0.5f); putFloat("cal.spring", 0.1f); putFloat("cal.top", 0.2f) }
        assertNull(Settings(prefs).calibration)
        assertFalse(Settings(prefs).penTuned)
    }
}

/** An in-memory [Preferences]. */
private class MapPrefs : Preferences {
    private val m = HashMap<String, Any>()
    override fun putBoolean(key: String, v: Boolean) = apply { m[key] = v }
    override fun putInteger(key: String, v: Int) = apply { m[key] = v }
    override fun putLong(key: String, v: Long) = apply { m[key] = v }
    override fun putFloat(key: String, v: Float) = apply { m[key] = v }
    override fun putString(key: String, v: String) = apply { m[key] = v }
    override fun put(vals: Map<String, *>) = apply { vals.forEach { (k, v) -> if (v != null) m[k] = v } }
    override fun getBoolean(key: String) = getBoolean(key, false)
    override fun getInteger(key: String) = getInteger(key, 0)
    override fun getLong(key: String) = getLong(key, 0L)
    override fun getFloat(key: String) = getFloat(key, 0f)
    override fun getString(key: String) = getString(key, "")
    override fun getBoolean(key: String, d: Boolean) = m[key] as? Boolean ?: d
    override fun getInteger(key: String, d: Int) = m[key] as? Int ?: d
    override fun getLong(key: String, d: Long) = m[key] as? Long ?: d
    override fun getFloat(key: String, d: Float) = m[key] as? Float ?: d
    override fun getString(key: String, d: String) = m[key] as? String ?: d
    override fun get(): Map<String, *> = m
    override fun contains(key: String) = key in m
    override fun clear() = m.clear()
    override fun remove(key: String) { m.remove(key) }
    override fun flush() = Unit
}
