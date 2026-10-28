package com.pranshuparmar.inkmayhem.input

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A stroke's one pressure: what it settles at, not how the pen lands or lifts. */
class SteadyPressureTest {
    private val s = SteadyPressure()
    private val rate = 480.0

    /** Pen Lab's shape: a 150 ms landing ramp, a steady middle, a 70 ms lift. */
    private fun stroke(from: Float, steady: Float, midS: Double, to: Float = from) {
        var t = 0.0
        val landN = (0.15 * rate).toInt()
        for (i in 0 until landN) { s.add(t, from + (steady - from) * i / landN); t += 1 / rate }
        repeat((midS * rate).toInt()) { s.add(t, steady); t += 1 / rate }
        val liftN = (0.07 * rate).toInt()
        for (i in 0 until liftN) { s.add(t, steady + (to - steady) * (i + 1) / liftN); t += 1 / rate }
    }

    @Test
    fun `the landing and the lift are left out`() {
        s.reset()
        stroke(from = 0.05f, steady = 0.45f, midS = 0.25, to = 0.02f)
        assertEquals(0.45f, s.finish(0f), 0.002f)
    }

    @Test
    fun `while drawing it is provisional until the pen has landed`() {
        s.reset()
        var t = 0.0
        repeat(40) { s.add(t, 0.1f); t += 1 / rate } // 83 ms: still landing
        assertFalse(s.settled)
        assertEquals(0.7f, s.value(provisional = 0.7f))
        repeat(60) { s.add(t, 0.5f); t += 1 / rate }
        assertTrue(s.settled)
        assertEquals(0.5f, s.value(provisional = 0.7f), 0.002f)
    }

    @Test
    fun `a wobble or a drift along the stroke takes its middle`() {
        s.reset()
        var t = 0.0
        // spring pressure wandering 0.40..0.58 and drifting up, as on Pen Lab's steady line
        for (i in 0 until 400) { s.add(t, 0.40f + 0.12f * i / 400 + 0.06f * kotlin.math.sin(i * 0.3f)); t += 1 / rate }
        val p = s.finish(0f)
        assertTrue(p in 0.43f..0.49f, "steady $p")
    }

    @Test
    fun `a stroke too short for both windows uses its middle`() {
        s.reset()
        var t = 0.0
        val ps = floatArrayOf(0.05f, 0.2f, 0.3f, 0.3f, 0.3f, 0.3f, 0.3f, 0.25f, 0.1f, 0.02f)
        for (p in ps) { s.add(t, p); t += 0.01 }
        assertEquals(0.3f, s.finish(0f), 0.002f)
    }

    @Test
    fun `a very long stroke still has a pressure`() {
        s.reset()
        var t = 0.0
        repeat(6000) { s.add(t, 0.8f); t += 1 / rate }
        assertEquals(0.8f, s.finish(0f), 0.002f)
        s.reset()
        assertEquals(0.3f, s.finish(0.3f), 0f, "nothing drawn: the provisional value")
    }
}
