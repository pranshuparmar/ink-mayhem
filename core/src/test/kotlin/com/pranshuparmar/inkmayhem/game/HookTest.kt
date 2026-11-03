package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A stroke end hooks only where its ink touches a surface; one drawn in the air isn't held. */
class HookTest {
    private val sb = Sandbox(gap = 3.2f, runnerX = -12f, speed = 3.4f)

    @AfterEach
    fun dispose() = sb.sim.dispose()

    private val rope = StageBuilder.PRESSURE.getValue(Ink.ROPE)
    private val beam = StageBuilder.PRESSURE.getValue(Ink.BEAM)

    @Test
    fun `a bridge drawn on the edges hooks at both ends`() {
        sb.line(-0.3f, 3.5f, Sandbox.LEVEL - 0.05f, rope)
        assertEquals(2, sb.sim.ink.strokes.last().anchors.size)
    }

    @Test
    fun `a rope drawn above the edges isn't held, and falls`() {
        sb.sim.runnerHeld = true
        sb.line(-0.3f, 3.5f, Sandbox.LEVEL - 0.45f, rope) // a looser reach (any ground within anchorR) would have hooked it
        val s = sb.sim.ink.strokes.last()
        assertEquals(0, s.anchors.size)
        val y0 = s.segs[s.segs.size / 2].group!!.body!!.position.y
        sb.run(0.5f)
        assertTrue(s.segs[s.segs.size / 2].group!!.body!!.position.y > y0 + 0.5f, "it fell")
    }

    @Test
    fun `one end on an edge holds and the other hangs`() {
        sb.line(-0.3f, 2.6f, Sandbox.LEVEL - 0.05f, rope) // stops short of the far edge
        assertEquals(1, sb.sim.ink.strokes.last().anchors.size)
    }

    @Test
    fun `a thick beam reaches further, as far as its ink`() {
        // a beam's ink is ~0.37 m thick: drawn 0.25 m up, its edge still touches the ground
        sb.line(-0.3f, 3.5f, Sandbox.LEVEL - 0.25f, beam)
        assertEquals(2, sb.sim.ink.strokes.last().anchors.size)
        sb.line(-0.3f, 3.5f, Sandbox.LEVEL - 0.6f, beam)
        assertEquals(0, sb.sim.ink.strokes.last().anchors.size)
    }

    @Test
    fun `while drawing, the pen's end shows whether it would hook`() {
        sb.penDown(-0.3f, Sandbox.LEVEL - 0.05f, rope)
        for (i in 1..40) sb.penMove(-0.3f + i * 0.05f, Sandbox.LEVEL - 0.05f, rope)
        assertTrue(!sb.sim.tipHooks, "over the gap")
        for (i in 41..80) sb.penMove(-0.3f + i * 0.05f, Sandbox.LEVEL - 0.05f, rope)
        assertTrue(sb.sim.tipHooks, "on the far edge")
        sb.penUp()
    }
}
