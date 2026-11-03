package com.pranshuparmar.inkmayhem.game

import com.badlogic.gdx.files.FileHandle
import com.pranshuparmar.inkmayhem.Settings
import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.record.MemoryPreferences
import com.pranshuparmar.inkmayhem.render.PageAlbum
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Doodle mode, stickers, the jukebox, Ask Dot's hints, lifetime numbers and the album. */
class ExtrasTest {
    private fun settings() = Settings(MemoryPreferences())

    // ---------------------------------------------------------------- Doodle mode

    @Test
    fun `no switches is the normal game`() {
        val p = StagePlay(Stages.FIRST_PAGE)
        Doodle.apply(0, p.sim)
        assertFalse(p.sim.unlimitedInk)
        assertFalse(p.sim.keepBold)
        assertEquals(0, p.sim.keptPowers)
        assertEquals(1f, Doodle.speed(0))
        p.dispose()
    }

    @Test
    fun `doodle switches hold their powers for the whole run`() {
        val p = StagePlay(Stages.FIRST_PAGE)
        val mask = Doodle.ENDLESS_INK.bit or Doodle.BOLD.bit or Doodle.STAR_NIB.bit or Doodle.GOLD_NIB.bit
        Doodle.apply(mask, p.sim)
        p.sim.loadStage(Stages.FIRST_PAGE, fromCheckpoint = false)
        p.sim.runnerHeld = false
        assertTrue(p.sim.unlimitedInk)
        assertTrue(p.sim.runner.bold, "a Glob from the start")
        assertTrue(p.sim.powered(ItemKind.STAR_NIB))
        assertTrue(p.sim.powered(ItemKind.GOLD_NIB))
        assertFalse(p.sim.powered(ItemKind.CHALK))
        assertEquals(0.5f, Doodle.speed(Doodle.HALF_SPEED.bit))
        // the scripted solution still clears the stage under these rules
        assertEquals(GameState.CLEAR, p.play(), p.summary)
        assertTrue(p.sim.runner.bold, "still a Glob at the end")
        p.dispose()
    }

    @Test
    fun `a held highlighter shows what's hidden from the start`() {
        val def = Stages.ALL.first { d -> d.layout.items.any { it.hidden } }
        val p = StagePlay(def)
        assertTrue(p.sim.stars.any { it.hidden } || p.sim.golds.any { it.hidden })
        Doodle.apply(Doodle.HIGHLIGHTER.bit, p.sim)
        p.sim.loadStage(def, fromCheckpoint = false)
        assertFalse(p.sim.stars.any { it.hidden } || p.sim.golds.any { it.hidden })
        p.dispose()
    }

    // ---------------------------------------------------------------- stickers

    @Test
    fun `a fresh save has no stickers`() {
        val s = settings()
        assertEquals(0L, Sticker.earnedFrom(s, s.stats))
    }

    @Test
    fun `stickers follow from the save`() {
        val s = settings()
        Stages.WORLDS[0].stages.forEach { s.record(it.id, 3, 1000, 0b111) }
        val m = Sticker.earnedFrom(s, s.stats)
        assertTrue(Sticker.FIRST_CLEAR.on(m))
        assertTrue(Sticker.WORLD_GOLD.on(m), "every gold star in world 1")
        assertFalse(Sticker.ALL_GOLD.on(m))
        assertFalse(Sticker.ALL_BOSSES.on(m))
        Stages.ALL.forEach { s.record(it.id, 3, 1000, 0b111) }
        val all = Sticker.earnedFrom(s, s.stats)
        for (k in listOf(Sticker.ALL_BOSSES, Sticker.ALL_CLEAR, Sticker.ALL_STARS, Sticker.ALL_GOLD)) assertTrue(k.on(all), k.name)
        assertEquals(listOf(Sticker.ALL_BOSSES, Sticker.ALL_CLEAR, Sticker.ALL_STARS, Sticker.ALL_GOLD), Sticker.fresh(m, all))
    }

    @Test
    fun `stickers from the lifetime numbers`() {
        val s = settings()
        repeat(999) { s.stats.stroke(Ink.ROPE, 1f) }
        assertFalse(Sticker.THOUSAND.on(Sticker.earnedFrom(s, s.stats)))
        s.stats.stroke(Ink.BEAM, 1f)
        s.stats.hint()
        val m = Sticker.earnedFrom(s, s.stats)
        assertTrue(Sticker.THOUSAND.on(m))
        assertTrue(Sticker.ASKED.on(m))
        assertFalse(Sticker.SECOND_THOUGHTS.on(m))
    }

    @Test
    fun `sixteen stickers fit the saved mask`() {
        assertTrue(Sticker.entries.size <= 63)
        assertEquals(Sticker.entries.size, Sticker.entries.map { it.bit }.toSet().size)
    }

    // ---------------------------------------------------------------- the jukebox

    @Test
    fun `tunes unlock with their bosses`() {
        val s = settings()
        val ink = Jukebox.TRACKS.first { it.world == 1 }
        val boss = Jukebox.TRACKS.first { it.world == 0 }
        assertEquals(6, Jukebox.TRACKS.size)
        assertFalse(Jukebox.unlocked(ink, s))
        assertFalse(Jukebox.unlocked(boss, s))
        assertEquals("cellar", Jukebox.choose(ink.name, "cellar", s), "a locked pick plays the world's own")
        s.record(Stages.WORLDS[0].stages.last().id, 1, 100, 0)
        assertTrue(Jukebox.unlocked(ink, s))
        assertTrue(Jukebox.unlocked(boss, s))
        assertFalse(Jukebox.unlocked(Jukebox.TRACKS.first { it.world == 2 }, s))
        assertEquals(ink.name, Jukebox.choose(ink.name, "cellar", s))
        assertEquals("cellar", Jukebox.choose("", "cellar", s), "no pick: the world's own")
        assertEquals("cellar", Jukebox.choose("no-such-tune", "cellar", s))
    }

    @Test
    fun `every boss has a world's tune and a name`() {
        for (t in Jukebox.TRACKS) {
            assertTrue(t.title.isNotBlank())
            assertTrue(Jukebox.lockedHint(t).startsWith("Beat"))
        }
    }

    // ---------------------------------------------------------------- Ask Dot

    @Test
    fun `hints are the next strokes still ahead`() {
        val sol = Stages.FIRST_PAGE.layout.solution
        assertTrue(sol.size >= 3)
        val start = Hints.ahead(sol, Stages.FIRST_PAGE.layout.startX)
        assertEquals(2, start.size)
        assertEquals(sol.minBy { it.atX }, start.first())
        // past a stroke's end, it's no longer shown
        val first = start.first()
        val later = Hints.ahead(sol, first.xs.max() + 0.5f)
        assertFalse(first in later)
        // nothing past the last stroke
        assertTrue(Hints.ahead(sol, sol.maxOf { it.xs.max() } + 1f).isEmpty())
    }

    @Test
    fun `hints only reach so far ahead`() {
        val sol = Stages.FIRST_PAGE.layout.solution
        val x = sol.minOf { it.atX } - Hints.REACH - 5f
        assertTrue(Hints.ahead(sol, x).isEmpty())
    }

    // ---------------------------------------------------------------- lifetime numbers

    @Test
    fun `lifetime numbers are kept once saved`() {
        val prefs = MemoryPreferences()
        val s = Settings(prefs)
        s.stats.stroke(Ink.SPRING, 2.5f)
        s.stats.stroke(Ink.BEAM, 1.5f)
        s.stats.erased()
        s.stats.lifeLost()
        assertTrue(s.stats.combo(4))
        assertFalse(s.stats.combo(3))
        assertTrue(s.stats.dirty)
        assertEquals(0, Settings(prefs).stats.totalStrokes, "not written yet")
        s.saveStats()
        assertFalse(s.stats.dirty)
        val back = Settings(prefs).stats
        assertEquals(2, back.totalStrokes)
        assertEquals(1, back.strokes[Ink.SPRING.weight])
        assertEquals(4f, back.metres, 1e-4f)
        assertEquals(1, back.erases)
        assertEquals(1, back.livesLost)
        assertEquals(4, back.bestCombo)
    }

    @Test
    fun `the new switches default on and doodling off`() {
        val s = settings()
        assertTrue(s.shake)
        assertTrue(s.dotHints)
        assertEquals("", s.jukebox)
        assertEquals(0, s.doodle)
        assertFalse(s.endingSeen)
        assertEquals(0L, s.stickers)
    }

    @Test
    fun `the theme follows the phone unless one is picked`() {
        val prefs = MemoryPreferences()
        val s = Settings(prefs)
        assertEquals(Settings.THEME_AUTO, s.theme)
        assertTrue(s.dark(systemDark = true))
        assertFalse(s.dark(systemDark = false))
        s.theme = Settings.THEME_DARK
        assertTrue(Settings(prefs).dark(systemDark = false), "dark is kept")
        s.theme = Settings.THEME_LIGHT
        assertFalse(Settings(prefs).dark(systemDark = true))
        s.theme = "purple"
        assertEquals(Settings.THEME_AUTO, Settings(prefs).theme)
    }

    // ---------------------------------------------------------------- the album

    @Test
    fun `pictures are named by kind and time, newest first`() {
        assertEquals("ink-mayhem-result-1-3-42.png", PageAlbum.name("result 1-3", 42L))
        assertEquals(42L, PageAlbum.stamp("ink-mayhem-result-1-3-42.png"))
        val dir = FileHandle(Files.createTempDirectory("album").toFile())
        val album = PageAlbum { dir }
        for (t in listOf(5L, 30L, 12L)) dir.child(PageAlbum.name("page", t)).writeString("x", false)
        dir.child("something-else.png").writeString("x", false)
        assertEquals(listOf(30L, 12L, 5L), album.list().map { PageAlbum.stamp(it.name()) })
        val f = album.list().first()
        album.thumb(f).writeString("t", false)
        album.delete(f)
        assertFalse(f.exists())
        assertFalse(album.thumb(f).exists())
        assertEquals(2, album.list().size)
        dir.deleteDirectory()
    }

    @Test
    fun `the album keeps the newest few`() {
        val dir = FileHandle(Files.createTempDirectory("album").toFile())
        val album = PageAlbum { dir }
        for (t in 1L..(PageAlbum.MAX + 5L)) dir.child(PageAlbum.name("page", t)).writeString("x", false)
        album.prune()
        val left = album.list()
        assertEquals(PageAlbum.MAX, left.size)
        assertEquals(PageAlbum.MAX + 5L, PageAlbum.stamp(left.first().name()))
        assertEquals(6L, PageAlbum.stamp(left.last().name()))
        dir.deleteDirectory()
    }
}
