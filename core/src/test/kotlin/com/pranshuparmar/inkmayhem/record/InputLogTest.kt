package com.pranshuparmar.inkmayhem.record

import com.pranshuparmar.inkmayhem.input.PenPhase
import com.pranshuparmar.inkmayhem.input.PenQueue
import com.pranshuparmar.inkmayhem.input.PenSample
import com.pranshuparmar.inkmayhem.input.ToolType
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Recordings read back to exactly what was written, and replay frame by frame. */
class InputLogTest {
    private fun sample(phase: PenPhase, x: Float, p: Float) = PenSample().apply {
        this.phase = phase
        toolType = ToolType.STYLUS
        pointerId = 3
        this.x = x
        y = 1234.5678f
        pressure = p
        tiltX = -12.25f
        tiltY = 40f
        buttons = 2
        timeNanos = 987_654_321_012L
        historical = true
    }

    private fun read(text: String) = InputLog.read(StringReader(text).buffered())

    @Test
    fun `everything reads back bit for bit`() {
        val out = StringWriter()
        val r = InputRecorder(out)
        r.meta("app", "0.1.0-debug (7)")
        r.pref("cal.rope", 0.0123f)
        r.pref("tutorialDone", true)
        r.pref("best", 412)
        r.pref("name", "two words\nand a newline")
        r.resize(1440, 3120, 3.5f)
        r.flags(dark = true, reduced = false)
        r.insets(0, 145, 0, 63)
        r.frame(0.008333334f, 1.0000000001)
        r.sample(sample(PenPhase.DOWN, 0.1f, 0.0761234f))
        r.event("stroke kept")
        r.sample(sample(PenPhase.UP, Float.MIN_VALUE, 1e-7f))
        r.probe("ENDLESS RUN 1.5 20.25 0 1")
        r.pause()
        r.resume()
        r.back()
        r.close()

        val log = read(out.toString())
        assertEquals("0.1.0-debug (7)", log.meta["app"])
        val prefs = MemoryPreferences.of(log.prefs)
        assertEquals(0.0123f, prefs.getFloat("cal.rope"))
        assertTrue(prefs.getBoolean("tutorialDone"))
        assertEquals(412, prefs.getInteger("best"))
        assertEquals("two words and a newline", prefs.getString("name"))

        val steps = log.steps
        assertEquals(1440, assertIs<InputLog.Step.Resize>(steps[0]).w)
        assertTrue(assertIs<InputLog.Step.Flags>(steps[1]).dark)
        assertEquals(145, assertIs<InputLog.Step.Insets>(steps[2]).t)
        val f = assertIs<InputLog.Step.Frame>(steps[3])
        assertEquals(0.008333334f, f.dt)
        assertEquals(1.0000000001, f.now)
        assertEquals(2, f.samples.size, "an event between samples doesn't end the frame")
        assertEquals(listOf("stroke kept"), f.events)
        assertEquals("ENDLESS RUN 1.5 20.25 0 1", f.probe)
        val a = f.samples[0]
        assertEquals(PenPhase.DOWN, a.phase)
        assertEquals(ToolType.STYLUS, a.toolType)
        assertEquals(0.0761234f.toRawBits(), a.pressure.toRawBits())
        assertEquals(1234.5678f, a.y)
        assertEquals(-12.25f, a.tiltX)
        assertEquals(987_654_321_012L, a.timeNanos)
        assertTrue(a.historical)
        assertEquals(Float.MIN_VALUE, f.samples[1].x)
        assertEquals(1e-7f, f.samples[1].pressure)
        assertEquals(listOf(InputLog.Step.Pause, InputLog.Step.Resume, InputLog.Step.Back), steps.drop(4))
    }

    @Test
    fun `flags and insets are written only when they change`() {
        val out = StringWriter()
        val r = InputRecorder(out)
        repeat(3) {
            r.flags(dark = false, reduced = false)
            r.insets(0, 0, 0, 0)
            r.frame(0.016f, it.toDouble())
        }
        r.flags(dark = true, reduced = false)
        val lines = out.toString().lines()
        assertEquals(1, lines.count { it.startsWith("D ") } - 1, "once at the start, once on the change")
        assertEquals(1, lines.count { it.startsWith("I ") })
        assertEquals(3, lines.count { it.startsWith("F ") })
    }

    @Test
    fun `a replay applies the steps in order and queues each frame's samples`() {
        val out = StringWriter()
        val r = InputRecorder(out)
        r.resize(800, 1600, 2f)
        r.insets(1, 2, 3, 4)
        r.frame(0.016f, 0.5)
        r.sample(sample(PenPhase.DOWN, 10f, 0.2f))
        r.sample(sample(PenPhase.MOVE, 11f, 0.3f))
        r.back()
        r.frame(0.017f, 0.517)
        r.close()
        val frames = ReplayFrames()
        val queue = PenQueue()
        val replay = Replay(read(out.toString()), frames, queue)
        val calls = ArrayList<String>()
        val target = object : ReplayTarget {
            override fun resize(w: Int, h: Int, density: Float) { calls += "resize $w $h $density" }
            override fun pause() { calls += "pause" }
            override fun resume() { calls += "resume" }
            override fun back() { calls += "back" }
        }
        val first = replay.next(target)
        assertEquals(listOf("resize 800 1600 2.0"), calls)
        assertEquals(0.5, frames.now)
        assertEquals(4, frames.insetB)
        assertEquals(2, first!!.samples.size)
        val got = PenSample()
        val xs = ArrayList<Float>()
        queue.drain(got) { xs += it.x }
        assertEquals(listOf(10f, 11f), xs)
        replay.next(target)
        assertEquals("back", calls.last())
        assertEquals(0.017f, frames.dt)
        assertEquals(1, replay.frameIndex)
        assertNull(replay.next(target))
    }

    @Test
    fun `a failing disk ends the recording instead of the game`() {
        val broken = object : Writer() {
            var calls = 0
            override fun write(cbuf: CharArray, off: Int, len: Int) {
                if (++calls > 2) throw IOException("disk full")
            }
            override fun flush() = throw IOException("disk full")
            override fun close() = Unit
        }
        val r = InputRecorder(broken)
        r.frame(0.016f, 0.0)
        r.frame(0.016f, 0.1)
        assertTrue(r.full)
        r.frame(0.016f, 0.2)
        r.flush()
        r.close()
    }

    @Test
    fun `a recording stops at its size limit`() {
        val out = StringWriter()
        val r = InputRecorder(out, maxChars = 200)
        repeat(100) { r.frame(0.016f, it.toDouble()) }
        assertTrue(r.full)
        assertTrue(out.toString().length < 300)
        assertTrue(out.toString().trimEnd().endsWith("E recording full: stopped"))
    }

    @Test
    fun `other files and other versions are refused`() {
        assertFailsWith<IllegalArgumentException> { read("hello\n") }
        assertFailsWith<IllegalArgumentException> { read("INKREC 99\n") }
    }
}
