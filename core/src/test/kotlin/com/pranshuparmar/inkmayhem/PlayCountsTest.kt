package com.pranshuparmar.inkmayhem

import com.pranshuparmar.inkmayhem.record.MemoryPreferences
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Anonymous play counts: off means silent, each event once a visit, clean URLs, never a throw. */
class PlayCountsTest {
    private val sent = mutableListOf<String>()
    private var on = true
    private val counts = PlayCounts("1.0.1 (3)", enabled = { on }) { url, _ -> sent += url }

    private fun paths() = sent.map { it.substringAfter("?p=").substringBefore('&') }

    @Test
    fun `nothing is sent while switched off or on a build that doesn't count`() {
        on = false
        counts.opened()
        counts.track("stage-1-1-start")
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `an event counts once a visit`() {
        counts.track("stage-1-1-clear")
        counts.track("stage-1-1-clear")
        counts.track("stage-1-2-clear")
        assertEquals(listOf("stage-1-1-clear", "stage-1-2-clear"), paths())
    }

    @Test
    fun `an event held back while off is counted once switched on`() {
        on = false
        counts.track("game-over")
        on = true
        counts.track("game-over")
        assertEquals(listOf("game-over"), paths())
    }

    @Test
    fun `an open counts the app and its version`() {
        counts.opened()
        assertEquals(listOf("app-open", "version-1.0.1"), paths())
        assertEquals("InkMayhem/1.0.1 (Android)", counts.userAgent)
    }

    @Test
    fun `the url is the site's count endpoint with an event path`() {
        counts.track("ending")
        assertEquals("https://ink-mayhem.goatcounter.com/count?p=ending&e=true", sent.single())
    }

    @Test
    fun `names are reduced to safe path characters`() {
        assertEquals("stage-2-3-clear", PlayCounts.clean("Stage 2/3 clear"))
        assertEquals("version-1.0.1", PlayCounts.clean("version-1.0.1"))
        assertEquals("a-b", PlayCounts.clean("a?b&"))
        assertNull(PlayCounts.clean("  ?? "))
        assertEquals(80, PlayCounts.clean("x".repeat(200))!!.length)
        assertEquals("1.0.1", PlayCounts.cleanVersion("1.0.1 (3)"))
        assertEquals("unknown", PlayCounts.cleanVersion(""))
    }

    @Test
    fun `an empty or unusable name sends nothing`() {
        counts.track("")
        counts.track("&&&")
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a failing sender or switch never throws into the game`() {
        val broken = PlayCounts("1.0.1", enabled = { true }) { _, _ -> error("offline") }
        broken.opened()
        broken.track("stage-1-1-start")
        val badSwitch = PlayCounts("1.0.1", enabled = { throw IllegalStateException("no settings") }) { _, _ -> }
        badSwitch.track("app-open")
    }

    @Test
    fun `a short trip away is the same visit, a long one is a new visit`() {
        counts.opened()
        counts.track("stage-1-1-start")
        counts.away(0L)
        counts.back(PlayCounts.NEW_VISIT_MS - 1)
        assertEquals(3, sent.size)
        counts.away(1_000_000L)
        counts.back(1_000_000L + PlayCounts.NEW_VISIT_MS)
        assertEquals(listOf("app-open", "version-1.0.1", "stage-1-1-start", "app-open", "version-1.0.1"), paths())
        counts.track("stage-1-1-start")
        assertEquals(6, sent.size)
    }

    @Test
    fun `coming back without having gone away isn't a new visit`() {
        counts.opened()
        counts.back(PlayCounts.NEW_VISIT_MS * 10)
        assertEquals(2, sent.size)
    }

    @Test
    fun `play counts are on by default and the switch is kept`() {
        val prefs = MemoryPreferences()
        val s = Settings(prefs)
        assertTrue(s.playCounts)
        s.playCounts = false
        assertFalse(Settings(prefs).playCounts)
    }
}
