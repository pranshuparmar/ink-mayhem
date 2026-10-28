package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.physics.TestNatives
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Each stroke is one ink, from the pressure it settles at (Pen Lab: 12 of 12 aimed strokes right). */
class OneInkStrokeTest {
    private val sim = GameSim(seed = 1, events = RecordingEvents(), vwMetres = 12f, vhMetres = 26.7f)
    private var t = 0.0

    init {
        sim.loadSandbox(listOf(floatArrayOf(-40f, 20f, 80f)), runnerX = -6f)
        sim.runnerHeld = true
        sim.unlimitedInk = true
    }

    @AfterEach
    fun dispose() = sim.dispose()

    /** Draws along y = 18 from x = -2 at 480 samples a second; [p] gives the pressure at a time. */
    private fun draw(seconds: Double, p: (Double) -> Float) {
        val n = (seconds * 480).toInt()
        for (i in 0..n) {
            val u = i / 480.0
            val x = -2f + 4f * i / n
            if (i == 0) sim.penDown(1, x, 18f, p(u), erase = false, t = t + u)
            else sim.penMove(1, x, 18f, p(u), t = t + u)
        }
        t += seconds + 1
    }

    @Test
    fun `a spring stroke that lands and lifts lightly is all spring`() {
        // 150 ms landing from rope pressure, steady spring, a 60 ms lift back to rope
        draw(0.5) { u -> if (u < 0.15) 0.05f + 3f * u.toFloat() else if (u > 0.44) 0.45f - 6f * (u - 0.44).toFloat() else 0.48f }
        assertEquals(Ink.SPRING, sim.drawingInk)
        sim.penUp(1)
        val s = sim.ink.strokes.last()
        assertTrue(s.segs.all { it.kind == Ink.SPRING }, "kinds: ${s.segs.map { it.kind }.toSet()}")
        assertEquals(Ink.SPRING.weight, s.heaviest)
        assertEquals(Ink.SPRING, sim.lastStrokeInk)
    }

    @Test
    fun `pressure creeping up along a stroke doesn't turn its end into beam`() {
        // spring that drifts past the beam edge (0.62) near its end, as Pen Lab's steady lines did
        draw(0.9) { u -> 0.45f + 0.25f * (u / 0.9).toFloat() }
        sim.penUp(1)
        assertTrue(sim.ink.strokes.last().segs.all { it.kind == Ink.SPRING })
    }

    @Test
    fun `while drawing the stroke shows the ink it is heading for`() {
        val n = 480
        for (i in 0..n) {
            val u = i / 480.0
            val p = if (u < 0.15) 0.1f else 0.8f
            if (i == 0) sim.penDown(1, -2f + 4f * i / n, 18f, p, erase = false, t = u) else sim.penMove(1, -2f + 4f * i / n, 18f, p, t = u)
            if (i == 30) assertEquals(Ink.ROPE, sim.drawingInk, "landing: what the pen presses now")
        }
        assertEquals(Ink.BEAM, sim.drawingInk)
        sim.penUp(1)
        assertTrue(sim.ink.strokes.last().segs.all { it.kind == Ink.BEAM })
    }

    @Test
    fun `thickness still follows the pen within the ink`() {
        draw(0.6) { u -> if (u < 0.3) 0.66f else 0.95f }
        sim.penUp(1)
        val segs = sim.ink.strokes.last().segs
        assertTrue(segs.all { it.kind == Ink.BEAM })
        assertTrue(segs.last().t > segs.first().t + 0.05f, "thin start, thick end: ${segs.first().t} .. ${segs.last().t}")
    }

    companion object {
        init {
            TestNatives.ensure()
        }
    }
}
