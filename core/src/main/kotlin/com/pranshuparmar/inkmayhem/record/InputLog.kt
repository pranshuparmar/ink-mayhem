package com.pranshuparmar.inkmayhem.record

import com.pranshuparmar.inkmayhem.input.PenPhase
import com.pranshuparmar.inkmayhem.input.PenSample
import com.pranshuparmar.inkmayhem.input.ToolType
import java.io.BufferedReader
import java.io.IOException
import java.io.Writer

/*
 * An input recording: everything the game read from outside itself, one line per record, so a
 * session can be replayed exactly (same frames, same samples, same clock) and read by a person.
 *
 *   INKREC 1                      header
 *   M key value                   about the recording (app version, device, world seed)
 *   K type key value              a preference at launch (type b, i, l, f or s)
 *   R w h density                 the screen was resized (pixels)
 *   I left top right bottom       safe-area insets changed (pixels)
 *   D dark reduced                system dark theme / reduced motion changed (0 or 1)
 *   F dt now                      a frame began: its step (s) and clock (s since launch)
 *   S phase tool pointer x y pressure tiltX tiltY buttons timeNanos historical
 *                                 a pen sample the frame handled
 *   P / U / B                     pause, resume, the Android back key
 *   C text                        a probe after the frame (where the runner is), to spot drift
 *   E text                        something that happened, for people (replays skip these)
 *
 * Floats are written in their shortest exact form, so they read back to the same bits.
 */

/** Writes a recording. Not thread-safe: the game calls it from the render thread only. */
class InputRecorder(private val out: Writer, private val maxChars: Long = MAX_CHARS) {
    private val sb = StringBuilder(256)
    private var buf = CharArray(512)
    private var written = 0L
    /** Stops writing once the file reaches [maxChars] (a session left running for hours) or a write fails. */
    var full = false
        private set
    private var insets = IntArray(4) { -1 }
    private var dark = -1
    private var reduced = -1

    init {
        safely { out.write("INKREC $VERSION\n") }
    }

    fun meta(key: String, value: String) = line('M'.code) { append(key).append(' ').append(clean(value)) }

    /** A preference at launch, as the platform stores it. */
    fun pref(key: String, value: Any?) {
        val type = when (value) {
            is Boolean -> 'b'
            is Int -> 'i'
            is Long -> 'l'
            is Float -> 'f'
            null -> return
            else -> 's'
        }
        line('K'.code) { append(type).append(' ').append(key).append(' ').append(clean(value.toString())) }
    }

    fun resize(w: Int, h: Int, density: Float) = line('R'.code) { append(w).append(' ').append(h).append(' ').append(density) }

    /** Written only when they change. */
    fun insets(l: Int, t: Int, r: Int, b: Int) {
        if (insets[0] == l && insets[1] == t && insets[2] == r && insets[3] == b) return
        insets[0] = l; insets[1] = t; insets[2] = r; insets[3] = b
        line('I'.code) { append(l).append(' ').append(t).append(' ').append(r).append(' ').append(b) }
    }

    /** Written only when they change. */
    fun flags(dark: Boolean, reduced: Boolean) {
        val d = if (dark) 1 else 0
        val r = if (reduced) 1 else 0
        if (d == this.dark && r == this.reduced) return
        this.dark = d
        this.reduced = r
        line('D'.code) { append(d).append(' ').append(r) }
    }

    fun frame(dt: Float, now: Double) = line('F'.code) { append(dt).append(' ').append(now) }

    fun sample(s: PenSample) = line('S'.code) {
        append(s.phase.name).append(' ').append(s.toolType.name).append(' ').append(s.pointerId).append(' ')
        append(s.x).append(' ').append(s.y).append(' ').append(s.pressure).append(' ')
        append(s.tiltX).append(' ').append(s.tiltY).append(' ').append(s.buttons).append(' ')
        append(s.timeNanos).append(' ').append(if (s.historical) 1 else 0)
    }

    fun pause() = line('P'.code) {}
    fun resume() = line('U'.code) {}
    fun back() = line('B'.code) {}
    fun probe(text: String) = line('C'.code) { append(text) }
    fun event(text: String) = line('E'.code) { append(clean(text)) }

    fun flush() = safely { out.flush() }

    fun close() = safely { out.close() }

    /** Builds one line in the reused builder and writes it without allocating. */
    private inline fun line(tag: Int, body: StringBuilder.() -> Unit) {
        if (full) return
        sb.setLength(0)
        sb.append(tag.toChar())
        if (tag != 'P'.code && tag != 'U'.code && tag != 'B'.code) sb.append(' ')
        sb.body()
        sb.append('\n')
        val n = sb.length
        if (buf.size < n) buf = CharArray(n * 2)
        sb.getChars(0, n, buf, 0)
        safely {
            out.write(buf, 0, n)
            written += n
            if (written > maxChars) {
                out.write("E recording full: stopped\n")
                out.flush()
                full = true
            }
        }
    }

    /** A recording must never take the game down: a write error just ends it. */
    private inline fun safely(io: () -> Unit) {
        try {
            io()
        } catch (e: IOException) {
            full = true
        }
    }

    companion object {
        const val VERSION = 1
        /** About 40 MB: an hour or so of drawing. */
        const val MAX_CHARS = 40_000_000L

        private fun clean(s: String): String =
            if (s.indexOf('\n') < 0 && s.indexOf('\r') < 0) s else s.replace("\r", "").replace('\n', ' ')
    }
}

/** A recording, read back: what it says about itself and its steps in order. */
class InputLog(val meta: Map<String, String>, val prefs: List<Pref>, val steps: List<Step>) {
    class Pref(val type: Char, val key: String, val value: String)

    sealed class Step {
        class Resize(val w: Int, val h: Int, val density: Float) : Step()
        class Insets(val l: Int, val t: Int, val r: Int, val b: Int) : Step()
        class Flags(val dark: Boolean, val reduced: Boolean) : Step()
        /** A frame with the samples it handled, what happened in it, and the probe after it. */
        class Frame(val dt: Float, val now: Double) : Step() {
            val samples = ArrayList<PenSample>()
            val events = ArrayList<String>()
            var probe: String? = null
        }
        object Pause : Step()
        object Resume : Step()
        object Back : Step()
        /** An event outside any frame (before the first, or between callbacks). */
        class Event(val text: String) : Step()
    }

    val frames: Int get() = steps.count { it is Step.Frame }

    companion object {
        fun read(reader: BufferedReader): InputLog {
            val header = reader.readLine()
            require(header != null && header.startsWith("INKREC ")) { "not an Ink Mayhem recording" }
            val version = header.substringAfter(' ').trim().toIntOrNull()
            require(version == InputRecorder.VERSION) { "recording version $version, this build reads ${InputRecorder.VERSION}" }
            val meta = LinkedHashMap<String, String>()
            val prefs = ArrayList<Pref>()
            val steps = ArrayList<Step>()
            var frame: Step.Frame? = null
            var lineNo = 1
            while (true) {
                val line = reader.readLine() ?: break
                lineNo++
                if (line.isEmpty()) continue
                val rest = if (line.length > 2) line.substring(2) else ""
                try {
                    when (line[0]) {
                        'M' -> meta[rest.substringBefore(' ')] = rest.substringAfter(' ', "")
                        'K' -> {
                            val p = rest.split(' ', limit = 3)
                            prefs += Pref(p[0][0], p[1], p.getOrElse(2) { "" })
                        }
                        'R' -> {
                            val p = rest.split(' ')
                            steps += Step.Resize(p[0].toInt(), p[1].toInt(), p[2].toFloat())
                            frame = null
                        }
                        'I' -> {
                            val p = rest.split(' ')
                            steps += Step.Insets(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt())
                            frame = null
                        }
                        'D' -> {
                            val p = rest.split(' ')
                            steps += Step.Flags(p[0] == "1", p[1] == "1")
                            frame = null
                        }
                        'F' -> {
                            val p = rest.split(' ')
                            val f = Step.Frame(p[0].toFloat(), p[1].toDouble())
                            steps += f
                            frame = f
                        }
                        'S' -> {
                            val f = frame ?: error("a sample outside a frame")
                            f.samples += parseSample(rest)
                        }
                        'C' -> frame?.probe = rest
                        'E' -> frame?.events?.add(rest) ?: steps.add(Step.Event(rest))
                        'P' -> { steps += Step.Pause; frame = null }
                        'U' -> { steps += Step.Resume; frame = null }
                        'B' -> { steps += Step.Back; frame = null }
                        else -> Unit // unknown records from newer builds are skipped
                    }
                } catch (e: RuntimeException) {
                    throw IllegalArgumentException("line $lineNo: ${e.message}: $line", e)
                }
            }
            return InputLog(meta, prefs, steps)
        }

        private fun parseSample(rest: String): PenSample {
            val p = rest.split(' ')
            return PenSample().apply {
                phase = PenPhase.valueOf(p[0])
                toolType = ToolType.valueOf(p[1])
                pointerId = p[2].toInt()
                x = p[3].toFloat()
                y = p[4].toFloat()
                pressure = p[5].toFloat()
                tiltX = p[6].toFloat()
                tiltY = p[7].toFloat()
                buttons = p[8].toInt()
                timeNanos = p[9].toLong()
                historical = p[10] == "1"
            }
        }
    }
}
