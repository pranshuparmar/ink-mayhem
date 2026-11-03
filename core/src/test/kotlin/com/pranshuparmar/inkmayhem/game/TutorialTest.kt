package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.game.Tutorial.Lesson
import com.pranshuparmar.inkmayhem.game.Tutorial.Phase
import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.physics.TestNatives
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The tutorial's lessons, played through the same pen path as the game. */
class TutorialTest {
    private val events = RecordingEvents()
    private val sim = GameSim(seed = 1, events = events, vwMetres = 12f, vhMetres = 26.7f)
    private val tut = Tutorial(sim)
    private var t = 0.0

    @AfterEach
    fun dispose() = sim.dispose()

    private fun run(seconds: Float, until: () -> Boolean = { false }) {
        repeat((seconds * 60).toInt()) {
            if (until()) return
            sim.realTime += 1.0 / 60
            tut.tick(1f / 60f)
        }
    }

    private fun line(x0: Float, x1: Float, y: Float, p: Float, erase: Boolean = false) {
        val n = ((x1 - x0) / 0.05f + 1e-3f).toInt() + 1
        for (i in 0 until n) {
            val x = x0 + i * 0.05f
            t += 1.0 / 240
            if (i == 0) tut.penDown(5, x, y, p, erase, t) else tut.penMove(5, x, y, p, t)
        }
        tut.penUp(5)
    }

    private fun onGround(x0: Float, x1: Float, ink: Ink) {
        val p = StageBuilder.PRESSURE.getValue(ink)
        line(x0, x1, Tutorial.LEVEL - (Ink.thickness(p) / 2f + 0.04f), p)
    }

    /** Edge to edge at ground level, the way the bot (and most players) bridge a gap. */
    private fun bridge(x0: Float, x1: Float, ink: Ink) = line(x0, x1, Tutorial.LEVEL - 0.05f, StageBuilder.PRESSURE.getValue(ink))

    private fun waitForNext(lesson: Lesson) = run(Tutorial.PASS_PAUSE + 0.5f) { tut.lesson == lesson && tut.phase == Phase.TRYING }

    /** One of the touch lesson's lines along its guide, settling at raw pressure [raw]. */
    private fun touch(raw: Float, x0: Float = Tutorial.TOUCH_X0, x1: Float = Tutorial.TOUCH_X1, p: Float = 0.45f) {
        val n = ((x1 - x0) / 0.05f + 1e-3f).toInt() + 1
        for (i in 0 until n) {
            t += 1.0 / 240
            if (i == 0) tut.penDown(5, x0 + i * 0.05f, Tutorial.LEVEL - 0.3f, p, false, t) else tut.penMove(5, x0 + i * 0.05f, Tutorial.LEVEL - 0.3f, p, t)
        }
        tut.penUp(5, raw)
    }

    @Test
    fun `the tutorial starts with the touch lesson, asking for a light line`() {
        tut.start()
        assertEquals(Lesson.TOUCH, tut.lesson)
        assertEquals(0, tut.touchStep)
        assertTrue(tut.showGuide)
        assertEquals(Ink.ROPE, tut.guideInk)
    }

    @Test
    fun `light, normal and hard lines tune the pen and pass the touch lesson`() {
        tut.start()
        touch(0.05f)
        assertEquals(1, tut.touchStep)
        assertEquals(Ink.SPRING, tut.guideInk)
        touch(0.12f)
        assertEquals(2, tut.touchStep)
        touch(0.3f)
        assertEquals(Phase.PASSED, tut.phase, tut.feedback)
        val cal = tut.calibration!!
        assertEquals(Ink.ROPE, Ink.of(cal.normalize(0.05f)))
        assertEquals(Ink.SPRING, Ink.of(cal.normalize(0.12f)))
        assertEquals(Ink.BEAM, Ink.of(cal.normalize(0.3f)))
        waitForNext(Lesson.ROPE)
        assertEquals(Lesson.ROPE, tut.lesson)
        assertEquals(null, sim.forcedInk)
    }

    @Test
    fun `each touch line is the ink it asks for, whatever the pressure, and is wiped`() {
        tut.start()
        touch(0.05f, p = 0.9f)
        assertEquals(Ink.ROPE, sim.lastStrokeInk)
        assertTrue(sim.ink.strokes.isEmpty())
        touch(0.12f, p = 0.1f)
        assertEquals(Ink.SPRING, sim.lastStrokeInk)
    }

    @Test
    fun `a touch line no harder than the last is asked for again`() {
        tut.start()
        touch(0.1f)
        touch(0.1f)
        assertEquals(1, tut.touchStep)
        assertTrue(!tut.good && tut.feedback.isNotEmpty())
    }

    @Test
    fun `a short touch line is asked for again`() {
        tut.start()
        touch(0.05f, x1 = Tutorial.TOUCH_X0 + 1f)
        assertEquals(0, tut.touchStep)
        assertTrue(!tut.good && tut.feedback.isNotEmpty())
    }

    @Test
    fun `a pen that can't tell its pressures apart moves on untuned after a few tries`() {
        tut.start()
        touch(0.1f)
        repeat(Tutorial.TOUCH_RETRIES) { touch(0.1f) }
        assertEquals(1, tut.touchStep)
        touch(0.1f)
        assertEquals(2, tut.touchStep)
        touch(0.1f)
        assertEquals(Phase.PASSED, tut.phase)
        assertEquals(null, tut.calibration)
    }

    @Test
    fun `the runner waits through the touch lesson`() {
        tut.start()
        val x = sim.runner.body.position.x
        touch(0.05f)
        run(3f)
        assertEquals(x, sim.runner.body.position.x, 0.05f)
        assertTrue(!tut.running)
    }

    @Test
    fun `the runner waits until a stroke is drawn`() {
        tut.start(Lesson.ROPE.ordinal)
        val x = sim.runner.body.position.x
        run(4f)
        assertEquals(x, sim.runner.body.position.x, 0.01f)
        assertEquals(GameState.RUN, sim.state)
        assertEquals(Phase.TRYING, tut.phase)
    }

    @Test
    fun `a rope bridge passes the rope lesson`() {
        tut.start(Lesson.ROPE.ordinal)
        bridge(0f, Tutorial.ROPE_GAP, Ink.ROPE)
        run(5f) { tut.phase == Phase.PASSED }
        assertEquals(Phase.PASSED, tut.phase)
        assertTrue(tut.good)
        assertEquals(listOf("Clean ×2"), events.pops)
    }

    @Test
    fun `a beam in the rope lesson asks for a lighter touch, then is accepted`() {
        tut.start(Lesson.ROPE.ordinal)
        repeat(Tutorial.HEAVY_RETRIES) {
            bridge(0f, Tutorial.ROPE_GAP, Ink.BEAM)
            run(5f) { tut.phase != Phase.TRYING }
            assertEquals(Phase.FAILED, tut.phase)
            assertTrue("lighter" in tut.feedback, tut.feedback)
            run(Tutorial.FAIL_PAUSE + 0.5f) { tut.phase == Phase.TRYING }
            assertTrue(sim.ink.strokes.isEmpty(), "the scene is set up again")
        }
        bridge(0f, Tutorial.ROPE_GAP, Ink.BEAM)
        run(5f) { tut.phase != Phase.TRYING }
        assertEquals(Phase.PASSED, tut.phase)
    }

    @Test
    fun `falling in the rope lesson resets it with a hint`() {
        tut.start(Lesson.ROPE.ordinal)
        bridge(0f, Tutorial.ROPE_GAP - 0.7f, Ink.ROPE) // reaches the guide's end, not the far edge
        run(5f) { tut.phase != Phase.TRYING }
        assertEquals(Phase.FAILED, tut.phase)
        assertTrue(tut.feedback.startsWith("Fell in"), tut.feedback)
        run(Tutorial.FAIL_PAUSE + 0.5f) { tut.phase == Phase.TRYING }
        assertEquals(Lesson.ROPE, tut.lesson)
        assertEquals(GameState.RUN, sim.state)
        assertTrue(sim.runnerHeld)
    }

    @Test
    fun `a stroke that can't do the lesson's job is wiped, and the runner keeps waiting`() {
        tut.start(Lesson.ROPE.ordinal)
        val x = sim.runner.body.position.x
        line(-1f, -0.2f, 18f, 0.2f) // a dab in the air, like pressing in place
        assertTrue(sim.ink.strokes.isEmpty())
        assertTrue(tut.feedback.startsWith("That one missed"), tut.feedback)
        run(3f)
        assertTrue(sim.runnerHeld)
        assertEquals(x, sim.runner.body.position.x, 0.01f)
        bridge(0f, Tutorial.ROPE_GAP, Ink.ROPE)
        assertEquals("", tut.feedback, "a good stroke clears the hint")
        run(5f) { tut.phase != Phase.TRYING }
        assertEquals(Phase.PASSED, tut.phase)
    }

    @Test
    fun `a beam drawn above the gap is wiped instead of letting the runner fall`() {
        tut.start(Lesson.BEAM.ordinal)
        line(0f, Tutorial.BEAM_GAP, Tutorial.LEVEL - 3f, StageBuilder.PRESSURE.getValue(Ink.BEAM))
        assertTrue(sim.ink.strokes.isEmpty())
        run(3f)
        assertTrue(sim.runnerHeld)
        assertEquals(Phase.TRYING, tut.phase)
    }

    @Test
    fun `a spring off the mark is wiped`() {
        tut.start(Lesson.SPRING.ordinal)
        onGround(1f, 2f, Ink.SPRING) // on top of the wall
        assertTrue(sim.ink.strokes.isEmpty())
        assertTrue("dotted mark" in tut.feedback, tut.feedback)
    }

    @Test
    fun `a spring passes the spring lesson`() {
        tut.start(Lesson.SPRING.ordinal)
        onGround(-3.2f, -2.4f, Ink.SPRING)
        run(6f) { tut.phase != Phase.TRYING }
        assertEquals(Phase.PASSED, tut.phase, tut.feedback)
        assertEquals(1, sim.bounces)
    }

    @Test
    fun `rope on the ground in the spring lesson gets stuck with a hint`() {
        tut.start(Lesson.SPRING.ordinal)
        onGround(-3.2f, -2.4f, Ink.ROPE)
        run(8f) { tut.phase != Phase.TRYING }
        assertEquals(Phase.FAILED, tut.phase)
        assertTrue(tut.feedback.startsWith("Stuck"), tut.feedback)
    }

    @Test
    fun `rope over the long gap snaps, with a hint to press harder`() {
        tut.start(Lesson.BEAM.ordinal)
        bridge(0f, Tutorial.BEAM_GAP, Ink.ROPE)
        run(6f) { tut.phase != Phase.TRYING }
        assertEquals(Phase.FAILED, tut.phase)
        assertTrue(tut.feedback.startsWith("Rope snaps"), tut.feedback)
    }

    @Test
    fun `a beam passes the beam lesson`() {
        tut.start(Lesson.BEAM.ordinal)
        bridge(0f, Tutorial.BEAM_GAP, Ink.BEAM)
        run(6f) { tut.phase != Phase.TRYING }
        assertEquals(Phase.PASSED, tut.phase, tut.feedback)
    }

    @Test
    fun `a beam drawn on top of the ground makes a step, with a hint`() {
        tut.start(Lesson.BEAM.ordinal)
        onGround(-0.4f, Tutorial.BEAM_GAP + 0.4f, Ink.BEAM)
        run(6f) { tut.phase != Phase.TRYING }
        assertEquals(Phase.FAILED, tut.phase)
        assertTrue(tut.feedback.startsWith("Stuck. Thick ink"), tut.feedback)
    }

    @Test
    fun `erasing the post passes the erase lesson, and drawing instead gets a hint`() {
        tut.start(Lesson.ERASE.ordinal)
        assertEquals(1, sim.ink.strokes.size)
        run(1f)
        onGround(-1f, -0.5f, Ink.ROPE)
        assertTrue("side button" in tut.feedback, tut.feedback)
        run(2f)
        assertTrue(sim.runnerHeld, "the runner waits for the post to go")
        t += 0.01
        tut.penDown(6, Tutorial.POST_X, Tutorial.LEVEL - 1.2f, 0.5f, erase = true, t = t)
        tut.penUp(6)
        assertEquals(1, sim.ink.strokes.size, "only the player's rope is left")
        run(5f) { tut.phase != Phase.TRYING }
        assertEquals(Phase.PASSED, tut.phase, tut.feedback)
    }

    @Test
    fun `playing every lesson right finishes the tutorial`() {
        tut.start()
        touch(0.05f)
        touch(0.12f)
        touch(0.3f)
        waitForNext(Lesson.ROPE)
        bridge(0f, Tutorial.ROPE_GAP, Ink.ROPE)
        run(5f) { tut.phase != Phase.TRYING }
        waitForNext(Lesson.SPRING)
        onGround(-3.2f, -2.4f, Ink.SPRING)
        run(6f) { tut.phase != Phase.TRYING }
        waitForNext(Lesson.BEAM)
        bridge(0f, Tutorial.BEAM_GAP, Ink.BEAM)
        run(6f) { tut.phase != Phase.TRYING }
        waitForNext(Lesson.ERASE)
        tut.penDown(6, Tutorial.POST_X, Tutorial.LEVEL - 1.2f, 0.5f, erase = true, t = 99.0)
        tut.penUp(6)
        run(5f) { tut.phase != Phase.TRYING }
        assertEquals(Phase.PASSED, tut.phase, tut.feedback)
        run(Tutorial.PASS_PAUSE + 0.5f)
        assertEquals(Phase.FINISHED, tut.phase)
        assertTrue(events.deaths.isEmpty(), "deaths: ${events.deaths.map { it.reason }}")
    }

    @Test
    fun `a spring under the waiting runner doesn't launch it`() {
        tut.start(Lesson.ERASE.ordinal) // the runner waits for the post, whatever is drawn
        val x = sim.runner.body.position.x
        onGround(x - 0.7f, x + 0.7f, Ink.SPRING)
        run(2f)
        assertEquals(0, sim.bounces)
        assertEquals(x, sim.runner.body.position.x, 0.3f)
    }

    @Test
    fun `no erase hint once the post is gone`() {
        tut.start(Lesson.ERASE.ordinal)
        tut.penDown(6, Tutorial.POST_X, Tutorial.LEVEL - 1.2f, 0.5f, erase = true, t = 1.0)
        tut.penUp(6)
        onGround(-1f, -0.5f, Ink.ROPE)
        assertEquals("", tut.feedback)
    }

    @Test
    fun `skip ends the tutorial`() {
        tut.start(Lesson.SPRING.ordinal)
        tut.skip()
        assertEquals(Phase.FINISHED, tut.phase)
    }

    companion object {
        init {
            TestNatives.ensure()
        }
    }
}
