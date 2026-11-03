package com.pranshuparmar.inkmayhem.ui

import com.badlogic.gdx.utils.Align
import com.pranshuparmar.inkmayhem.input.PenCalibration
import com.pranshuparmar.inkmayhem.input.PenPhase
import com.pranshuparmar.inkmayhem.input.PenSample
import com.pranshuparmar.inkmayhem.input.PenTuning
import com.pranshuparmar.inkmayhem.input.SteadyPressure
import com.pranshuparmar.inkmayhem.input.Pressure
import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.render.Face
import com.pranshuparmar.inkmayhem.render.Fonts
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok
import kotlin.math.hypot
import kotlin.math.max

/**
 * Tuning the pen: the player draws a rope (light), a spring
 * (normal) and a beam (hard) along a guide, the way they'll draw in play, and each line sets
 * where their own pressure changes ink. A last free-drawing step colours their ink live and
 * lights up each kind as they find it, so they can check it before Done. Runs on first launch
 * and from "Recalibrate".
 */
class CalibrationOverlay(
    private val fonts: Fonts,
    private val onDone: (PenCalibration) -> Unit,
    private val onSkip: () -> Unit,
) {
    var open = false
        private set

    private val tuning = PenTuning()
    private val card = Box()
    private val pad = Box()
    private val skip = Box()
    private val again = Box()
    private val done = Box()
    private val press = Press()
    private val chips = Array(3) { Box() }
    private val task = Flow(21f)
    private var taskFor: PenTuning.Step? = null

    // the line being drawn: raw pressures and pad-relative points
    private var raw = FloatArray(512)
    private var rawCount = 0
    private var length = 0f
    private var capturePointer = -1
    private var lastX = 0f
    private var lastY = 0f
    private var smoothed = Float.NaN
    private var liveInk: Ink? = null
    /** The check step: each stroke is one ink, from the pressure it settles at, as in the game. */
    private val steady = SteadyPressure()
    private var strokeFrom = 0

    /** Drawn ink as (x0, y0, x1, y1, width, ink weight) in pad-relative dp. */
    private var ink = FloatArray(2048 * 6)
    private var inkCount = 0
    /** The check step: each segment's normalized pressure, so a recoloured stroke can rethicken. */
    private var inkP = FloatArray(2048)
    private val seen = BooleanArray(3)

    private var hint = ""
    private var hintTone = Tok.MUTED

    /** Told what the player drew and how it was judged (input recordings note it). */
    var log: ((String) -> Unit)? = null

    fun show() {
        log?.invoke("tuning opened")
        open = true
        tuning.restart()
        seen.fill(false)
        clearPad()
        hint(GUIDE, Tok.MUTED)
    }

    /** Same as the Skip button (Android back). */
    fun skip() {
        log?.invoke("tuning skipped at ${tuning.step.name}")
        open = false
        onSkip()
    }

    private fun clearPad() {
        inkCount = 0
        rawCount = 0
        length = 0f
        capturePointer = -1
        liveInk = null
    }

    private fun hint(s: String, tone: Tok) {
        hint = s
        hintTone = tone
    }

    fun layout(text: Text, f: Frame) {
        val step = tuning.step
        if (taskFor != step) {
            taskFor = step
            val bold = { fonts.get(Face.UI_BOLD, 15f) }
            val reg = { fonts.get(Face.UI, 15f) }
            when (step) {
                PenTuning.Step.ROPE -> task.set(Flow.Run(bold, Tok.UI_FG, "Draw a rope."), Flow.Run(reg, Tok.MUTED, "Barely touch the glass, like a light sketch."))
                PenTuning.Step.SPRING -> task.set(Flow.Run(bold, Tok.UI_FG, "Draw a spring."), Flow.Run(reg, Tok.MUTED, "Press the way you write."))
                PenTuning.Step.BEAM -> task.set(Flow.Run(bold, Tok.UI_FG, "Draw a beam."), Flow.Run(reg, Tok.MUTED, "Press hard."))
                PenTuning.Step.CHECK -> task.set(Flow.Run(bold, Tok.UI_FG, "Try it."), Flow.Run(reg, Tok.MUTED, "Draw a few lines: light, normal and hard. Each line becomes one ink."))
            }
        }
        val inner = f.cardW - 40f
        val swatch = if (step.ink != null) 56f else 0f
        val h = 20f + 32f + 10f + max(TASK_H, task.layout(text, inner - swatch)) + 12f + PAD_H + 10f + 30f + 12f + 42f + 20f
        card.set((f.wDp - f.cardW) / 2f + (f.padL - f.padR) / 2f, (f.hDp - h) / 2f, f.cardW, h)
        val padTop = card.y + 20f + 32f + 10f + max(TASK_H, task.height) + 12f
        pad.set(card.x + 20f, padTop, inner, PAD_H)
        val font = fonts.get(Face.UI_BOLD, 14f)
        val by = card.bottom - 20f - 42f
        val doneW = text.width(font, "Done") + 32f
        val againW = text.width(font, "Start over") + 32f
        val skipW = text.width(font, "Skip") + 32f
        done.set(card.right - 20f - doneW, by, doneW, 42f)
        again.set(done.x - 8f - againW, by, againW, 42f)
        skip.set(card.x + 20f, by, skipW, 42f)
        var x = card.x + 20f
        val cy = pad.bottom + 10f + 2f
        val chipFont = fonts.get(Face.UI_BOLD, 13f)
        for (i in 0 until 3) {
            val w = text.width(chipFont, INKS[i].label) + 26f
            chips[i].set(x, cy, w, 26f)
            x += w + 8f
        }
    }

    /** Consumes every sample while open. [allowed] = pen, or finger with the debug setting. */
    fun onSample(s: PenSample, x: Float, y: Float, allowed: Boolean) {
        when (s.phase) {
            PenPhase.DOWN -> {
                if (pad.contains(x, y)) {
                    if (!allowed) {
                        hint("That was a finger. Use the S Pen.", Tok.BEAM)
                        return
                    }
                    if (tuning.step != PenTuning.Step.CHECK) clearPad()
                    capturePointer = s.pointerId
                    lastX = x - pad.x
                    lastY = y - pad.y
                    rawCount = 0
                    length = 0f
                    smoothed = Float.NaN
                    steady.reset()
                    strokeFrom = inkCount
                    sampleTime = s.timeNanos / 1e9
                    addRaw(pressureOf(s))
                    return
                }
                press.down(s.pointerId, targetAt(x, y))
            }
            PenPhase.MOVE -> {
                if (s.pointerId != capturePointer) return
                val px = (x - pad.x).coerceIn(0f, pad.w)
                val py = (y - pad.y).coerceIn(0f, pad.h)
                val p = pressureOf(s)
                sampleTime = s.timeNanos / 1e9
                addRaw(p)
                val seg = hypot(px - lastX, py - lastY)
                length += seg
                addSegment(px, py, p)
                lastX = px
                lastY = py
            }
            PenPhase.UP, PenPhase.CANCEL -> {
                if (s.pointerId == capturePointer) {
                    capturePointer = -1
                    liveInk = null
                    if (s.phase == PenPhase.UP) {
                        finishCheckStroke()
                        finishLine()
                    }
                    return
                }
                if (s.phase == PenPhase.CANCEL) {
                    press.cancel()
                    return
                }
                when (press.up(s.pointerId, targetAt(x, y))) {
                    skip -> skip()
                    again -> {
                        log?.invoke("tuning started over")
                        tuning.restart()
                        seen.fill(false)
                        clearPad()
                        hint(GUIDE, Tok.MUTED)
                    }
                    done -> tuning.calibration?.let {
                        log?.invoke("tuning done: rope < ${it.ropeEdge}, spring < ${it.springEdge}, top ${it.top}")
                        open = false
                        onDone(it)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun pressureOf(s: PenSample) = if (s.toolType.isPen) s.pressure else Pressure.CAL_FINGER_RAW

    private fun addRaw(p: Float) {
        if (rawCount == raw.size) raw = raw.copyOf(raw.size * 2)
        raw[rawCount++] = p
    }

    /** One drawn segment: the task's ink while tuning; the player's own ink, live, when checking. */
    private fun addSegment(px: Float, py: Float, p: Float) {
        val cal = tuning.calibration
        val weight: Int
        val width: Float
        var pn = 0f
        if (cal == null) {
            val k = tuning.step.ink ?: Ink.SPRING
            weight = k.weight
            width = Ink.thickness(TASK_P[weight]) * DP_PER_M
        } else {
            val n = cal.normalize(p)
            smoothed = if (smoothed.isNaN()) n else smoothed * 0.7f + n * 0.3f
            pn = smoothed
            steady.add(sampleTime, n)
            val k = Ink.of(steady.value(smoothed))
            if (k != liveInk) recolour(k)
            liveInk = k
            weight = k.weight
            width = Ink.thickness(Ink.clampTo(smoothed, k)) * DP_PER_M
        }
        if (inkCount * 6 + 6 > ink.size) {
            if (ink.size >= MAX_INK * 6) return
            ink = ink.copyOf(ink.size * 2)
            inkP = inkP.copyOf(inkP.size * 2)
        }
        inkP[inkCount] = pn
        val i = inkCount * 6
        ink[i] = lastX; ink[i + 1] = lastY; ink[i + 2] = px; ink[i + 3] = py; ink[i + 4] = width; ink[i + 5] = weight.toFloat()
        inkCount++
    }

    /** The check step's stroke so far takes [k]: one ink per stroke. */
    private fun recolour(k: Ink) {
        for (s in strokeFrom until inkCount) {
            ink[s * 6 + 4] = Ink.thickness(Ink.clampTo(inkP[s], k)) * DP_PER_M
            ink[s * 6 + 5] = k.weight.toFloat()
        }
    }

    /** The check step: the finished stroke settles on its one ink, which then counts as found. */
    private fun finishCheckStroke() {
        if (tuning.calibration == null || inkCount == strokeFrom || smoothed.isNaN()) return
        val k = Ink.of(steady.finish(smoothed))
        recolour(k)
        // a tap or a press in place doesn't count: the line has to go somewhere
        if (length >= MIN_CHECK_LENGTH) seen[k.weight] = true
    }

    private var sampleTime = 0.0

    private fun finishLine() {
        val step = tuning.step
        if (step == PenTuning.Step.CHECK) return
        val fraction = length / max(1f, pad.w - 2f * GUIDE_INSET)
        val v = tuning.submit(raw, rawCount, fraction)
        log?.let {
            it("tuning ${step.name} line: median ${PenTuning.lineMedian(raw, rawCount)} raw, $rawCount samples, " +
                "${(fraction * 100).toInt()}% of the guide -> ${v.name}")
        }
        when (v) {
            PenTuning.Verdict.ACCEPTED -> {
                clearPad()
                hint(DONE_HINT[step.ordinal], Tok.ROPE)
            }
            PenTuning.Verdict.TOO_SHORT -> hint("Draw the whole line, from one end of the guide to the other.", Tok.BEAM)
            PenTuning.Verdict.NOT_HEAVIER -> hint(
                if (step == PenTuning.Step.SPRING) "That was as light as your rope. Press the way you write." else "Press harder than your spring.",
                Tok.BEAM,
            )
        }
    }

    private fun targetAt(x: Float, y: Float): Any? = when {
        skip.contains(x, y) -> skip
        again.contains(x, y) && tuning.step != PenTuning.Step.ROPE -> again
        done.contains(x, y) && tuning.step == PenTuning.Step.CHECK -> done
        else -> null
    }

    fun drawShapes(shapes: Shapes, pal: Palette, f: Frame) {
        shapes.color(pal[Tok.PAPER], 0.7f).rect(0f, 0f, f.wDp, f.hDp)
        Widgets.card(shapes, pal, card)
        val step = tuning.step
        step.ink?.let { k ->
            val t = Ink.thickness(TASK_P[k.weight]) * DP_PER_M
            shapes.color(pal[tok(k)]).roundRect(card.x + 20f, card.y + 20f + 32f + 10f + 12f - t / 2f, 44f, t, t / 2f)
        }
        // the pad: paper, a dashed border, and the guide to draw along
        shapes.color(pal[Tok.PAPER]).roundRect(pad.x, pad.y, pad.w, pad.h, 10f)
        shapes.color(pal[Tok.GRAPHITE])
        dashedRect(shapes, pad, 4f, 4f)
        if (step != PenTuning.Step.CHECK) {
            val gy = pad.y + pad.h / 2f
            var x = pad.x + GUIDE_INSET
            val end = pad.right - GUIDE_INSET
            shapes.color(pal[tok(step.ink ?: Ink.SPRING)], 0.45f)
            while (x < end - 10f) {
                shapes.circle(x, gy, 1.8f)
                x += 9f
            }
            shapes.triangle(end - 10f, gy - 6f, end, gy, end - 10f, gy + 6f)
        }
        for (i in 0 until inkCount) {
            val k = i * 6
            shapes.color(pal[tok(INKS[ink[k + 5].toInt()])]).capsule(
                pad.x + ink[k], pad.y + ink[k + 1], pad.x + ink[k + 2], pad.y + ink[k + 3], ink[k + 4],
            )
        }
        // in the check, the ink under the pen is named next to it
        val live = liveInk
        if (live != null && capturePointer >= 0) {
            val lx = pad.x + lastX + 14f
            val ly = pad.y + lastY - 34f
            shapes.color(pal[tok(live)]).roundRect(lx, ly, LABEL_W, 22f, 11f)
        }
        if (step == PenTuning.Step.CHECK) {
            for (i in 0 until 3) {
                val b = chips[i]
                shapes.color(pal[tok(INKS[i])])
                if (seen[i]) shapes.roundRect(b.x, b.y, b.w, b.h, b.h / 2f) else shapes.roundRectOutline(b.x, b.y, b.w, b.h, b.h / 2f, 1.5f)
            }
        }
        Widgets.button(shapes, pal, skip, primary = false, enabled = true, pressed = press.isPressed(skip))
        Widgets.button(shapes, pal, again, primary = false, enabled = step != PenTuning.Step.ROPE, pressed = press.isPressed(again))
        Widgets.button(shapes, pal, done, primary = true, enabled = step == PenTuning.Step.CHECK, pressed = press.isPressed(done))
    }

    private fun dashedRect(shapes: Shapes, b: Box, dash: Float, gap: Float) {
        var x = b.x + 10f
        while (x < b.right - 10f) {
            val w = minOf(dash, b.right - 10f - x)
            shapes.rect(x, b.y, w, 1f)
            shapes.rect(x, b.bottom - 1f, w, 1f)
            x += dash + gap
        }
        var y = b.y + 10f
        while (y < b.bottom - 10f) {
            val h = minOf(dash, b.bottom - 10f - y)
            shapes.rect(b.x, y, 1f, h)
            shapes.rect(b.right - 1f, y, 1f, h)
            y += dash + gap
        }
        shapes.arc(b.x + 10f, b.y + 10f, 9.5f, 1f, Math.PI.toFloat(), 1.5f * Math.PI.toFloat())
        shapes.arc(b.right - 10f, b.y + 10f, 9.5f, 1f, 1.5f * Math.PI.toFloat(), 2f * Math.PI.toFloat())
        shapes.arc(b.right - 10f, b.bottom - 10f, 9.5f, 1f, 0f, 0.5f * Math.PI.toFloat())
        shapes.arc(b.x + 10f, b.bottom - 10f, 9.5f, 1f, 0.5f * Math.PI.toFloat(), Math.PI.toFloat())
    }

    fun drawText(text: Text, pal: Palette) {
        val x = card.x + 20f
        val y = card.y + 20f
        val step = tuning.step
        text.line(fonts.get(Face.HAND_BOLD, 32f), TITLE, x, y, 32f, pal[Tok.INK])
        text.line(fonts.get(Face.MONO_MEDIUM, 11f), STEP_LABEL[step.ordinal], card.right - 20f, y, 32f, pal[Tok.MUTED], 1f, Align.right)
        val swatch = if (step.ink != null) 56f else 0f
        task.draw(text, pal, x + swatch, y + 32f + 10f)
        if (step == PenTuning.Step.CHECK) {
            val chipFont = fonts.get(Face.UI_BOLD, 13f)
            for (i in 0 until 3) {
                val b = chips[i]
                text.line(chipFont, INKS[i].label, b.x + b.w / 2f, b.y, b.h, if (seen[i]) pal[Tok.PAPER] else pal[tok(INKS[i])], 1f, Align.center)
            }
            if (seen.all { it }) {
                text.line(fonts.get(Face.UI_BOLD, 13f), "All three. Tap Done.", card.right - 20f, pad.bottom + 10f, 30f, pal[Tok.ROPE], 1f, Align.right)
            }
        } else {
            text.line(fonts.get(Face.UI, 13f), hint, x, pad.bottom + 10f, 30f, pal[hintTone])
        }
        val live = liveInk
        if (live != null && capturePointer >= 0) {
            text.line(fonts.get(Face.UI_BOLD, 12f), live.label, pad.x + lastX + 14f + LABEL_W / 2f, pad.y + lastY - 34f, 22f, pal[Tok.PAPER], 1f, Align.center)
        }
        val font = fonts.get(Face.UI_BOLD, 14f)
        Widgets.buttonLabel(text, font, pal, skip, "Skip", primary = false, enabled = true)
        Widgets.buttonLabel(text, font, pal, again, "Start over", primary = false, enabled = step != PenTuning.Step.ROPE)
        Widgets.buttonLabel(text, font, pal, done, "Done", primary = true, enabled = step == PenTuning.Step.CHECK)
    }

    fun invalidate() {
        task.invalidate()
        taskFor = null
    }

    companion object {
        const val TITLE = "Tune your pen"
        private const val GUIDE = "Draw along the dotted line, left to right."
        private val DONE_HINT = arrayOf("Nice rope. Now a spring.", "Good spring. Now a beam.", "Solid beam. Now try them all.")
        private val STEP_LABEL = arrayOf("STEP 1 OF 3", "STEP 2 OF 3", "STEP 3 OF 3", "CHECK")
        private val INKS = Ink.entries.toTypedArray()
        /** Ink thickness shown for each task's line (the middle of its band). */
        private val TASK_P = floatArrayOf(0.2f, 0.47f, 0.8f)
        private const val PAD_H = 150f
        /** Room for two lines of task, so the pad doesn't move between steps. */
        private const val TASK_H = 44f
        private const val GUIDE_INSET = 24f
        private const val DP_PER_M = 30f
        private const val LABEL_W = 58f
        private const val MIN_CHECK_LENGTH = 40f
        private const val MAX_INK = 16384

        private fun tok(k: Ink) = when (k) {
            Ink.ROPE -> Tok.ROPE
            Ink.SPRING -> Tok.SPRING
            Ink.BEAM -> Tok.BEAM
        }
    }
}
