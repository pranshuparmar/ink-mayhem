package com.pranshuparmar.inkmayhem

import com.pranshuparmar.inkmayhem.input.PenCalibration
import com.pranshuparmar.inkmayhem.record.MemoryPreferences
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
        val s = Settings(MemoryPreferences())
        assertNull(s.calibration)
        assertFalse(s.penTuned)
    }

    @Test
    fun `an old calibration still maps, but the pen is asked to be tuned once`() {
        val prefs = MemoryPreferences().apply { putFloat("cal", 0.5f) }
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
        val prefs = MemoryPreferences()
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
        val prefs = MemoryPreferences().apply { putFloat("cal.rope", 0.5f); putFloat("cal.spring", 0.1f); putFloat("cal.top", 0.2f) }
        assertNull(Settings(prefs).calibration)
        assertFalse(Settings(prefs).penTuned)
    }

    @Test
    fun `input recording is on until turned off`() {
        val prefs = MemoryPreferences()
        assertTrue(Settings(prefs).recordInput)
        Settings(prefs).recordInput = false
        assertFalse(Settings(prefs).recordInput)
    }
}
