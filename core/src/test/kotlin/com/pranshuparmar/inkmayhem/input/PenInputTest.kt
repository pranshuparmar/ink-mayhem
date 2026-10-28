package com.pranshuparmar.inkmayhem.input

import org.junit.jupiter.api.Test
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import com.pranshuparmar.inkmayhem.physics.Ink

class PenInputTest {

    @Test
    fun `queue delivers every sample in order across threads`() {
        val q = PenQueue(1024)
        val total = 200_000
        val producer = thread {
            var i = 0
            while (i < total) {
                val phase = if (i % 2 == 0) PenPhase.MOVE else PenPhase.HOVER
                if (q.offer(i.toFloat(), -i.toFloat(), (i % 100) / 100f, 1f, 2f, i and 1, phase, ToolType.STYLUS, 3, i.toLong())) i++
            }
        }
        val s = PenSample()
        var next = 0
        val deadline = System.nanoTime() + 20_000_000_000L
        while (next < total && System.nanoTime() < deadline) {
            q.drain(s) {
                assertEquals(next.toFloat(), it.x)
                assertEquals(-next.toFloat(), it.y)
                assertEquals(next.toLong(), it.timeNanos)
                assertEquals(next and 1, it.buttons)
                assertEquals(if (next % 2 == 0) PenPhase.MOVE else PenPhase.HOVER, it.phase)
                next++
            }
        }
        producer.join()
        assertEquals(total, next)
    }

    @Test
    fun `a full queue rejects new samples instead of overwriting`() {
        val q = PenQueue(4)
        repeat(4) { assertTrue(q.offer(it.toFloat(), 0f, 0f, 0f, 0f, 0, PenPhase.MOVE, ToolType.STYLUS, 0, 0)) }
        assertFalse(q.offer(9f, 0f, 0f, 0f, 0f, 0, PenPhase.MOVE, ToolType.STYLUS, 0, 0))
        assertEquals(1L, q.dropped)
        val s = PenSample()
        assertTrue(q.poll(s))
        assertEquals(0f, s.x)
    }

    @Test
    fun `the one-number calibration is the POC's straight line`() {
        // natural writing pressure (the calibration) lands mid-spring
        assertEquals(0.45f, PenCalibration.linear(0.5f).normalize(0.5f), 1e-5f)
        assertEquals(0.45f, PenCalibration.DEFAULT.normalize(0.32f), 1e-5f) // never tuned
        assertEquals(0.2f * 0.45f / 0.32f, PenCalibration.DEFAULT.normalize(0.2f), 1e-5f)
        assertEquals(1f, PenCalibration.linear(0.2f).normalize(1f))
        assertEquals(0f, PenCalibration.linear(0.2f).normalize(0f))
    }

    @Test
    fun `a player's three lines land in the middle of their own bands`() {
        // a light hand: writing presses 0.07, a light sketch 0.03, hard 0.2 (an S Pen user's numbers)
        val cal = PenCalibration.fromLines(0.03f, 0.07f, 0.2f)
        assertEquals(Ink.ROPE, Ink.of(cal.normalize(0.03f)))
        assertEquals(Ink.SPRING, Ink.of(cal.normalize(0.07f)))
        assertEquals(Ink.BEAM, Ink.of(cal.normalize(0.2f)))
        assertEquals(Ink.ROPE_MAX, cal.normalize(cal.ropeEdge), 1e-5f)
        assertEquals(Ink.SPRING_MAX, cal.normalize(cal.springEdge), 1e-5f)
        assertEquals(1f, cal.normalize(1f))
        var last = -1f
        for (i in 0..100) {
            val p = cal.normalize(i / 100f)
            assertTrue(p >= last, "monotonic at ${i / 100f}")
            last = p
        }
    }

    @Test
    fun `tuning takes a rope, a spring and a beam, each harder than the last`() {
        val t = PenTuning()
        fun line(p: Float) = FloatArray(40) { p }
        assertEquals(PenTuning.Verdict.TOO_SHORT, t.submit(line(0.03f), 40, 0.3f))
        assertEquals(PenTuning.Verdict.TOO_SHORT, t.submit(line(0.03f), 5, 0.9f))
        assertEquals(PenTuning.Verdict.ACCEPTED, t.submit(line(0.03f), 40, 0.9f))
        assertEquals(PenTuning.Step.SPRING, t.step)
        assertEquals(PenTuning.Verdict.NOT_HEAVIER, t.submit(line(0.032f), 40, 0.9f), "barely harder than the rope")
        assertEquals(PenTuning.Verdict.ACCEPTED, t.submit(line(0.07f), 40, 0.9f))
        assertEquals(PenTuning.Verdict.NOT_HEAVIER, t.submit(line(0.07f), 40, 0.9f))
        assertEquals(PenTuning.Verdict.ACCEPTED, t.submit(line(0.2f), 40, 0.9f))
        assertEquals(PenTuning.Step.CHECK, t.step)
        val cal = assertNotNull(t.calibration)
        assertEquals(Ink.SPRING, Ink.of(cal.normalize(0.07f)))
        t.restart()
        assertEquals(PenTuning.Step.ROPE, t.step)
        assertNull(t.calibration)
    }

    @Test
    fun `a pen that tops out still gets its beam`() {
        val t = PenTuning()
        fun line(p: Float) = FloatArray(40) { p }
        t.submit(line(0.5f), 40, 0.9f)
        t.submit(line(0.9f), 40, 0.9f)
        assertEquals(PenTuning.Verdict.ACCEPTED, t.submit(line(1f), 40, 0.9f))
    }

    @Test
    fun `a line's pressure ignores its landing and lifting`() {
        // ramps at both ends, a steady 0.1 in the middle
        val raw = FloatArray(40) { i -> if (i < 6) 0.01f * i else if (i >= 34) 0.3f else 0.1f }
        assertEquals(0.1f, PenTuning.lineMedian(raw, 40), 1e-6f)
        assertTrue(PenTuning.lineMedian(raw, 6).isNaN(), "too few samples")
    }
}
