package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.physics.TestNatives
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ink is solid for the runner while it is being drawn, not only once the pen lifts. */
class WetInkTest {

    /**
     * Draws along y = [y] from [x0] towards [x1] at [penSpeed] m/s, one sample per tick, starting
     * once the runner passes [startAt]; the pen stays down until [liftAt] (runner x).
     */
    private class Held(
        val sb: Sandbox, val x0: Float, val x1: Float, val y: Float, val p: Float,
        val startAt: Float, val liftAt: Float, val penSpeed: Float = 7f, val yEnd: Float = y,
    ) {
        private fun yAt(x: Float) = y + (yEnd - y) * (x - x0) / (x1 - x0)

        var x = Float.NaN
        var lifted = false

        fun tick() {
            if (lifted) return
            if (x.isNaN()) {
                if (sb.runnerX < startAt) return
                x = x0
                sb.penDown(x, yAt(x), p)
                return
            }
            if (sb.runnerX >= liftAt || sb.sim.state != GameState.RUN) {
                sb.penUp()
                lifted = true
                return
            }
            if (x < x1) {
                x = minOf(x1, x + penSpeed / 60f)
                sb.penMove(x, yAt(x), p)
            }
        }
    }

    private fun groundY(p: Float) = Sandbox.LEVEL - (Ink.thickness(p) / 2f + 0.04f)

    @Test
    fun `the runner crosses a gap on a stroke that is still being drawn`() {
        val sb = Sandbox(gap = 4.5f, runnerX = -6f, speed = 4.7f)
        try {
            val p = InkBot.PRESSURE.getValue(Ink.ROPE)
            // the pen starts at the edge just as the runner gets there, and stays down
            val pen = Held(sb, -0.4f, 4.9f, groundY(p), p, startAt = -0.9f, liftAt = 7f)
            var crossedHeld = false
            sb.run(4f) {
                pen.tick()
                if (!pen.lifted && sb.runnerX > 5f) crossedHeld = true
            }
            assertNull(sb.sim.death, "died: ${sb.sim.death?.reason}")
            assertTrue(crossedHeld, "the runner didn't cross before the pen lifted")
            assertTrue(sb.runnerX > 7f)
        } finally {
            sb.dispose()
        }
    }

    @Test
    fun `lifting the pen with the runner on the ink builds the real stroke under it`() {
        val sb = Sandbox(gap = 4.5f, runnerX = -6f, speed = 4.7f)
        try {
            val p = InkBot.PRESSURE.getValue(Ink.ROPE)
            val pen = Held(sb, -0.4f, 4.9f, groundY(p), p, startAt = -1.2f, liftAt = 2f)
            var wetSeen = 0
            sb.run(4f) {
                pen.tick()
                wetSeen = maxOf(wetSeen, sb.sim.ink.wetPieces)
            }
            assertTrue(wetSeen > 10, "only $wetSeen wet pieces")
            assertEquals(0, sb.sim.ink.wetPieces)
            assertEquals(1, sb.sim.ink.strokes.size)
            assertNull(sb.sim.death, "died: ${sb.sim.death?.reason}")
            assertTrue(sb.runnerX > 6f)
        } finally {
            sb.dispose()
        }
    }

    @Test
    fun `a held rope is no stronger than a finished one past 6_8 m`() {
        val sb = Sandbox(gap = 9f, runnerX = -6f, speed = 4.7f)
        try {
            val p = InkBot.PRESSURE.getValue(Ink.ROPE)
            val pen = Held(sb, -0.4f, 9.4f, groundY(p), p, startAt = -2.5f, liftAt = 100f)
            sb.run(5f) { pen.tick() }
            assertEquals("Fell in", sb.sim.death?.reason)
            assertTrue(GameSim.TOO_LONG_FOR_ROPE in sb.events.toasts)
        } finally {
            sb.dispose()
        }
    }

    @Test
    fun `a wet spring bounces the runner`() {
        val sb = Sandbox(gap = 4.5f, runnerX = -12f, speed = 4.7f)
        try {
            val p = InkBot.PRESSURE.getValue(Ink.SPRING)
            val pen = Held(sb, -6f, -5.2f, groundY(p), p, startAt = -9f, liftAt = 100f)
            var bouncedHeld = false
            sb.run(3f) {
                pen.tick()
                if (Sfx.BOING in sb.events.sounds && !pen.lifted) bouncedHeld = true
            }
            assertTrue(bouncedHeld, "no bounce while the pen was down")
        } finally {
            sb.dispose()
        }
    }

    @Test
    fun `boulders fall through wet ink`() {
        val sb = Sandbox(gap = 4.5f, runnerX = -40f, speed = 3.4f)
        try {
            val p = InkBot.PRESSURE.getValue(Ink.BEAM)
            val pen = Held(sb, -0.4f, 4.9f, groundY(p), p, startAt = -100f, liftAt = 100f, penSpeed = 60f)
            repeat(60) { pen.tick() }
            assertTrue(sb.sim.ink.wetPieces > 0)
            val bo = sb.boulder(-5f, 5f)
            sb.run(3f) { pen.tick() }
            assertTrue(bo.body.position.y > Sandbox.LEVEL + 2f, "boulder at y = ${bo.body.position.y}")
        } finally {
            sb.dispose()
        }
    }

    @Test
    fun `a hazard crossed while the pen is still down is judged`() {
        val sb = Sandbox(gap = 4.5f, runnerX = -6f, speed = 4.7f)
        try {
            sb.sim.hazards += Hazard(HazardType.GAP_M, 0f, 4.5f)
            val p = InkBot.PRESSURE.getValue(Ink.ROPE)
            val pen = Held(sb, -0.4f, 4.9f, groundY(p), p, startAt = -1f, liftAt = 100f)
            sb.run(4f) { pen.tick() }
            assertTrue(sb.runnerX > 6f && !pen.lifted, "runner at ${sb.runnerX}, pen lifted: ${pen.lifted}")
            assertEquals(listOf("Clean ×2"), sb.events.pops)
        } finally {
            sb.dispose()
        }
    }

    @Test
    fun `ink begun in mid-air isn't solid while it's drawn`() {
        val sb = Sandbox(gap = 4.5f, runnerX = -6f, speed = 4.7f)
        try {
            val p = InkBot.PRESSURE.getValue(Ink.BEAM)
            val pen = Held(sb, 0.5f, 4f, Sandbox.LEVEL - 2f, p, startAt = -100f, liftAt = 100f, penSpeed = 30f)
            repeat(30) { pen.tick() }
            assertEquals(0, sb.sim.ink.wetPieces)
        } finally {
            sb.dispose()
        }
    }

    /** A rope ramp pinned only at the near edge: held, it's a rigid launch ramp; it mustn't score. */
    @Test
    fun `crossing on half-drawn ink scores nothing`() {
        val sb = Sandbox(gap = 8f, runnerX = -6f, speed = 5f)
        try {
            sb.sim.hazards += Hazard(HazardType.GAP_L, 0f, 8f)
            val p = InkBot.PRESSURE.getValue(Ink.ROPE)
            val pen = Held(sb, -0.4f, 5.6f, groundY(p), p, startAt = -2f, liftAt = 100f, penSpeed = 12f, yEnd = Sandbox.LEVEL - 2.4f)
            sb.run(5f) { pen.tick() }
            assertTrue(sb.events.pops.none { it.startsWith("Clean") || it.startsWith("Featherweight") }, "pops: ${sb.events.pops}")
        } finally {
            sb.dispose()
        }
    }

    companion object {
        init {
            TestNatives.ensure()
        }
    }
}
