package com.pranshuparmar.inkmayhem

import com.pranshuparmar.inkmayhem.record.MemoryPreferences
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Saves carry a version, and old ones are brought up to date once. */
class SaveVersionTest {
    @Test
    fun `a fresh install is stamped with the current version`() {
        val prefs = MemoryPreferences()
        Settings(prefs)
        assertEquals(Settings.SAVE_VERSION, prefs.getInteger(Settings.SAVE_VERSION_KEY, 0))
    }

    @Test
    fun `a save from before the five worlds loses the old 1-2 to 1-4 results, keeping 1-1 and the rest`() {
        val prefs = MemoryPreferences().apply {
            putInteger("stars.1-1", 3); putInteger("best.1-1", 1200)
            putInteger("stars.1-2", 2); putInteger("best.1-2", 900)
            putInteger("stars.1-4", 1); putInteger("best.1-4", 400)
            putFloat("cal.rope", 0.1f); putFloat("cal.spring", 0.3f); putFloat("cal.top", 0.6f)
            putBoolean("music", false)
        }
        val s = Settings(prefs)
        assertEquals(3, s.stars("1-1"))
        assertEquals(1200, s.best("1-1"))
        assertEquals(0, s.stars("1-2"))
        assertEquals(0, s.best("1-2"))
        assertEquals(0, s.stars("1-4"))
        assertEquals(true, s.penTuned)
        assertEquals(0f, s.musicVolume)
        assertEquals(1f, s.soundVolume)
    }

    @Test
    fun `an unversioned save from the five worlds keeps everything`() {
        val prefs = MemoryPreferences().apply {
            putInteger("stars.1-2", 2); putInteger("gold.1-2", 5); putInteger("stars.3-1", 1)
        }
        val s = Settings(prefs)
        assertEquals(2, s.stars("1-2"))
        assertEquals(5, s.gold("1-2"))
        assertEquals(1, s.stars("3-1"))
    }

    @Test
    fun `a versioned save is left alone`() {
        val prefs = MemoryPreferences().apply {
            putInteger(Settings.SAVE_VERSION_KEY, Settings.SAVE_VERSION)
            putInteger("stars.1-2", 2)
        }
        assertEquals(2, Settings(prefs).stars("1-2"))
    }

    @Test
    fun `volumes are kept between 0 and 1 and survive a restart`() {
        val prefs = MemoryPreferences()
        val s = Settings(prefs)
        s.musicVolume = 0.4f
        s.soundVolume = 7f
        val back = Settings(prefs)
        assertEquals(0.4f, back.musicVolume)
        assertEquals(1f, back.soundVolume)
        back.soundVolume = 0f
        assertEquals(false, Settings(prefs).sound)
    }
}
