package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.input.PenTuning
import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.physics.TestNatives
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** World 1: star trails, Blots, doodle boxes and the Glob, the face, and the first page's tuning gates. */
class World1Test {
    private fun stage(build: StageBuilder.() -> Unit) = StageDef(9, 9, "Test", Theme.PAGE, 3.4f, build)

    private fun <T> playing(p: StagePlay, block: (StagePlay) -> T): T = try {
        block(p)
    } finally {
        p.dispose()
    }

    @Test
    fun `the first page's solution stomps a Blot, becomes a Glob, flattens the next and takes the stars`() = playing(StagePlay(Stages.FIRST_PAGE)) { p ->
        var bold = false
        assertEquals(GameState.CLEAR, p.play(240f) { if (p.sim.runner.bold) bold = true }, p.summary)
        assertEquals(2, p.sim.squashed, p.summary)
        assertTrue(bold, "caught the marker cap")
        assertTrue("Stomp! +${GameSim.BLOT_POINTS}" in p.events.pops, p.summary)
        assertTrue("Flattened! +${GameSim.BLOT_POINTS}" in p.events.pops, p.summary)
        val total = Stages.FIRST_PAGE.layout.items.count { it.kind == ItemKind.STAR }
        assertTrue(p.sim.starsTaken >= total * 3 / 4, "stars ${p.sim.starsTaken} of $total")
        assertEquals(p.sim.starsTaken, p.events.stars)
    }

    @Test
    fun `touching a Blot on the ground costs a life`() = playing(StagePlay(stage {
        start(10f)
        blot(x + 8f)
        flat(40f)
        finish()
    }, solve = false)) { p ->
        assertEquals(GameState.DEAD, p.play(30f), p.summary)
        assertEquals(DeathKind.BLOT, p.sim.death?.kind)
        assertEquals(Mood.OUCH, p.sim.mood)
    }

    @Test
    fun `a spring bounce squashes a Blot`() = playing(StagePlay(stage {
        start(10f)
        flat(40f)
        blot(28f)
        spring(19.9f, 20.7f, level, lead = 4f)
        finish()
    })) { p ->
        assertEquals(GameState.CLEAR, p.play(40f), p.summary)
        assertEquals(1, p.sim.squashed)
        assertTrue(Sfx.SQUASH in p.events.sounds)
    }

    @Test
    fun `a beam dropped on a Blot squashes it`() = playing(StagePlay(stage {
        start(10f)
        flat(40f)
        blot(30f)
        // drawn in the air above it while it still sleeps: nothing holds a beam there, so it falls
        solve(29.1f, 17.4f, 30.9f, 17.4f, Ink.BEAM, lead = 17f)
        finish()
    })) { p ->
        p.play(12f)
        assertEquals(1, p.sim.squashed, p.summary)
        assertTrue("Beam drop! +${GameSim.BLOT_POINTS}" in p.events.pops, p.summary)
        assertFalse(p.sim.death?.kind == DeathKind.BLOT, p.summary)
    }

    @Test
    fun `the marker cap makes the runner a Glob, which rolls Blots flat and survives spikes once`() = playing(StagePlay(stage {
        start(10f)
        flat(70f)
        box(16.5f, up = 2.6f)
        spring(16f, 16.8f, level, lead = 4f)
        blot(36f)
        spikes(46f, level, 1.2f)
        spikes(62f, level, 1.2f)
        finish()
    })) { p ->
        var boldAt = Float.NaN
        var lostAt = Float.NaN
        val st = p.play(40f) {
            if (p.sim.runner.bold && boldAt.isNaN()) boldAt = p.x
            if (!boldAt.isNaN() && !p.sim.runner.bold && lostAt.isNaN()) lostAt = p.x
        }
        assertTrue(boldAt < 36f, "a Glob before the Blot (at $boldAt)")
        assertEquals(1, p.sim.squashed, "rolled flat: ${p.events.pops}")
        assertTrue(lostAt > 44f && lostAt < 48f, "the first spikes shrank the Glob (at $lostAt)")
        assertEquals(GameState.DEAD, st, p.summary)
        assertEquals(DeathKind.SPIKED, p.sim.death?.kind)
        assertTrue(p.x > 60f, "the second spikes killed it: ${p.summary}")
        assertTrue(Sfx.POP in p.events.sounds && Sfx.BOLD in p.events.sounds && Sfx.SHRINK in p.events.sounds)
    }

    @Test
    fun `stars taken in a row ring notes a step higher each, up to the eighth`() = playing(StagePlay(stage {
        start(10f)
        stars(14f, 10)
        flat(30f)
        finish()
    }, solve = false)) { p ->
        p.play(10f)
        assertEquals(10, p.sim.starsTaken)
        val notes = p.events.sounds.indices.filter { p.events.sounds[it] == Sfx.STAR_TRAIL }.map { p.events.variants[it] }
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 7, 7), notes)
        // a metre is a point at combo 1; the rest is the stars
        val startX = p.def.layout.startX
        assertEquals(10.0 * GameSim.STAR_POINTS, p.sim.score - (p.sim.dist - startX), 0.5)
    }

    @Test
    fun `every fifty stars is an extra life`() {
        val sim = GameSim(seed = 1, vwMetres = 12f, vhMetres = 26.7f)
        try {
            val s = StageSession(sim, StageSessionTest.MapStore())
            s.start(Stages.FIRST_PAGE)
            var lives = 0
            repeat(GameSim.STARS_PER_LIFE * 2) { if (s.addStar()) lives++ }
            assertEquals(2, lives)
            assertEquals(StageSession.START_LIVES + 2, s.lives)
            s.start(Stages.FIRST_PAGE, freshLives = true)
            assertEquals(0, s.stars)
        } finally {
            sim.dispose()
        }
    }

    private val gateStage = stage {
        start(10f)
        hint(Ink.BEAM, x - 1f, x + 7.6f, x - 0.1f, level - 0.05f, x + 7.7f, level - 0.05f, tunes = true)
        openGap(7.6f, HazardType.GAP_L)
        flat(20f)
        finish()
    }

    /** A light stroke across the beam gate, drawn as the runner nears it. */
    private fun lightStroke(p: StagePlay) {
        p.play(1.5f)
        var t = 0.0
        val y = 20f - 0.05f
        for (i in 0..156) {
            t += 1.0 / 240
            val x = 9.9f + i * 0.05f
            if (i == 0) p.sim.penDown(3, x, y, 0.2f, erase = false, t = t) else p.sim.penMove(3, x, y, 0.2f, t)
        }
        p.sim.penUp(3)
    }

    @Test
    fun `while the pen is untuned a stroke at a tuning gate is the gate's ink`() = playing(StagePlay(gateStage, solve = false)) { p ->
        p.sim.gateTuning = true
        lightStroke(p)
        assertEquals(Ink.BEAM, p.sim.lastStrokeInk)
        assertEquals(Ink.BEAM, p.sim.lastGate?.ink)
        assertEquals(GameState.CLEAR, p.play(20f), p.summary)
    }

    @Test
    fun `once tuned the same light stroke is rope, too weak for the chasm`() = playing(StagePlay(gateStage, solve = false)) { p ->
        lightStroke(p)
        assertEquals(Ink.ROPE, p.sim.lastStrokeInk)
        assertEquals(null, p.sim.lastGate)
        assertEquals(GameState.DEAD, p.play(20f), p.summary)
    }

    /** A stroke at constant pressure along a straight line, through the real pen path. */
    private fun line(p: StagePlay, x0: Float, y0: Float, x1: Float, y1: Float, pressure: Float) {
        val n = maxOf(2, (kotlin.math.hypot(x1 - x0, y1 - y0) / 0.05f).toInt())
        for (i in 0..n) {
            tPen += 1.0 / 240
            val x = x0 + (x1 - x0) * i / n
            val y = y0 + (y1 - y0) * i / n
            if (i == 0) p.sim.penDown(3, x, y, pressure, erase = false, t = tPen) else p.sim.penMove(3, x, y, pressure, tPen)
        }
        p.sim.penUp(3)
    }

    private var tPen = 100.0

    @Test
    fun `on the first run the runner waits at each new thing until a stroke that works is drawn`() = playing(StagePlay(Stages.FIRST_PAGE, solve = false)) { p ->
        p.sim.learning = true
        p.sim.gateTuning = true
        val waits = p.sim.gates.filter { !it.waitX.isNaN() }
        assertEquals(5, waits.size, "rope gap, spring wall, beam chasm, Blot, box")
        val rope = waits[0]
        p.play(20f)
        assertEquals(GameState.RUN, p.sim.state, p.summary)
        assertEquals(rope, p.sim.waitingAt)
        assertTrue(kotlin.math.abs(p.x - rope.waitX) < 0.6f && p.x < rope.x0 + 1f, "stopped before the gap at ${p.x}, wait ${rope.waitX}")
        assertTrue(GameSim.TIP_IDLE in p.events.toasts, "nudged after waiting: ${p.events.toasts}")
        // a line drawn in the air over the gap would fall: it's wiped with a tip, ink back, and the runner keeps waiting
        val ink = p.sim.inkLeft
        line(p, rope.gx0, rope.gy0 - 1.5f, rope.gx1, rope.gy1 - 1.5f, 0.2f)
        assertEquals(GameSim.TIP_BRIDGE, p.events.toasts.last())
        assertEquals(ink, p.sim.inkLeft, 0.01f)
        assertTrue(p.sim.ink.strokes.isEmpty())
        p.play(3f)
        assertEquals(rope, p.sim.waitingAt)
        // edge to edge on the ground releases it; the spring wall stops it again
        line(p, rope.gx0, rope.gy0, rope.gx1, rope.gy1, 0.2f)
        assertTrue(rope.done)
        p.play(20f)
        assertEquals(GameState.RUN, p.sim.state, p.summary)
        assertEquals(waits[1], p.sim.waitingAt, p.summary)
        assertTrue(p.x > rope.x1 && p.x < waits[1].waitX + 0.6f, "at the spring wall: ${p.x}")
        // a spring drawn up the wall's face is wiped; one on the dotted line works
        val wall = waits[1]
        line(p, wall.x1 - 1.1f, wall.gy0, wall.x1 - 1.1f, wall.gy0 - 2f, 0.47f)
        assertEquals(GameSim.TIP_PAD, p.events.toasts.last())
        line(p, wall.gx0, wall.gy0, wall.gx1, wall.gy1, 0.47f)
        assertTrue(wall.done)
        p.play(20f)
        assertEquals(waits[2], p.sim.waitingAt, p.summary)
    }

    @Test
    fun `a first run played by the bot clears the page, waiting at each new thing`() = playing(StagePlay(Stages.FIRST_PAGE)) { p ->
        p.sim.learning = true
        p.sim.gateTuning = true
        val waited = HashSet<Gate>()
        assertEquals(GameState.CLEAR, p.play(300f) { p.sim.waitingAt?.let { waited += it } }, p.summary)
        assertEquals(5, waited.size)
        assertEquals(2, p.sim.squashed, p.summary)
        assertEquals(Stages.FIRST_PAGE.layout.checkpoints.size, p.events.checkpoints)
    }

    @Test
    fun `once the page has been cleared the runner doesn't wait`() = playing(StagePlay(Stages.FIRST_PAGE, solve = false)) { p ->
        p.sim.gateTuning = true
        assertEquals(GameState.DEAD, p.play(20f), p.summary)
        assertEquals(DeathKind.FELL, p.sim.death?.kind)
    }

    @Test
    fun `the gates tune only from pressures that rise`() {
        assertTrue(PenTuning.rising(0.1f, 0.2f, 0.4f))
        assertFalse(PenTuning.rising(0.2f, 0.21f, 0.4f))
        assertFalse(PenTuning.rising(0.3f, 0.2f, 0.4f))
        assertFalse(PenTuning.rising(0f, 0.2f, 0.4f))
    }

    companion object {
        init {
            TestNatives.ensure()
        }
    }

    @Test
    fun `the runner's face goes wide-eyed at an edge with nothing ahead`() = playing(StagePlay(Stages.FIRST_PAGE, solve = false)) { p ->
        val moods = HashSet<Mood>()
        p.play(20f) { moods += p.sim.mood }
        assertTrue(Mood.WORRIED in moods, "$moods")
        assertEquals(DeathKind.FELL, p.sim.death?.kind)
    }
}
