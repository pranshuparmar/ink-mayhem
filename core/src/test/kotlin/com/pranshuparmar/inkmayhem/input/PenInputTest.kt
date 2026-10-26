package com.pranshuparmar.inkmayhem.input

import org.junit.jupiter.api.Test
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `pressure normalizes against the calibration`() {
        // Natural writing pressure (the calibration) lands mid-spring.
        assertEquals(0.45f, Pressure.normalize(0.5f, 0.5f), 1e-6f)
        assertEquals(0.45f, Pressure.normalize(0.32f, null), 1e-6f) // default calibration
        assertEquals(1f, Pressure.normalize(1f, 0.2f))
        assertEquals(0f, Pressure.normalize(0f, 0.2f))
    }

    @Test
    fun `calibration is the clamped median`() {
        assertEquals(0.3f, Pressure.calibrate(floatArrayOf(0.5f, 0.1f, 0.3f), 3))
        assertEquals(0.08f, Pressure.calibrate(floatArrayOf(0.01f, 0.02f, 0.03f), 3))
        assertEquals(0.9f, Pressure.calibrate(floatArrayOf(0.95f, 0.99f, 1f), 3))
    }
}
