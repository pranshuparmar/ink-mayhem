package com.pranshuparmar.inkmayhem.ui

import com.badlogic.gdx.utils.Align
import com.pranshuparmar.inkmayhem.input.PenPhase
import com.pranshuparmar.inkmayhem.input.PenSample
import com.pranshuparmar.inkmayhem.input.Pressure
import com.pranshuparmar.inkmayhem.render.Face
import com.pranshuparmar.inkmayhem.render.Fonts
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok
import java.util.Locale

/**
 * Pressure calibration (POC #cal, which asked for a signature): the player writes "Ink Mayhem"
 * over a faint guide, and the median raw pressure becomes the calibration, so their everyday
 * writing pressure lands mid-spring. It runs on first launch and from "Recalibrate".
 */
class CalibrationOverlay(
    private val fonts: Fonts,
    private val onDone: (Float) -> Unit,
    private val onSkip: () -> Unit,
) {
    var open = false
        private set

    private val card = Box()
    private val pad = Box()
    private val clear = Box()
    private val skip = Box()
    private val done = Box()
    private val press = Press()
    private val intro = Flow(22.5f).setText(
        { fonts.get(Face.UI, 15f) }, Tok.MUTED,
        "Write it the way you normally write, so the game learns your pressure. Your everyday pressure makes a spring; lighter makes rope, harder makes a beam.",
    )

    private var samples = FloatArray(4096)
    private var sampleCount = 0
    /** Written ink as (x0, y0, x1, y1, width) in pad-relative dp. */
    private var ink = FloatArray(4096 * 5)
    private var inkCount = 0
    private var capturePointer = -1
    private var lastX = 0f
    private var lastY = 0f
    private var status = WAITING
    private var statusOverride: String? = null

    fun show() {
        open = true
        clearPad()
    }

    private fun clearPad() {
        sampleCount = 0
        inkCount = 0
        capturePointer = -1
        statusOverride = null
        updateStatus()
    }

    private var statusDirty = true

    /** The status line is rebuilt at most once per frame (the median sorts every sample). */
    private fun updateStatus() {
        statusDirty = true
    }

    private fun rebuildStatus() {
        if (!statusDirty) return
        statusDirty = false
        status = if (sampleCount == 0) WAITING else {
            val sorted = samples.copyOf(sampleCount).also { it.sort() }
            "$sampleCount samples · median pressure ${String.format(Locale.US, "%.3f", sorted[sampleCount / 2])}"
        }
    }

    fun layout(text: Text, f: Frame) {
        val inner = f.cardW - 40f
        val h = 20f + 32f + 12f + intro.layout(text, inner) + 12f + 150f + 12f + 16f + 12f + 42f + 20f
        card.set((f.wDp - f.cardW) / 2f + (f.padL - f.padR) / 2f, (f.hDp - h) / 2f, f.cardW, h)
        val padTop = card.y + 20f + 32f + 12f + intro.height + 12f
        pad.set(card.x + 20f, padTop, inner, 150f)
        val font = fonts.get(Face.UI_BOLD, 14f)
        val by = pad.bottom + 12f + 16f + 12f
        val doneW = text.width(font, "Done") + 32f
        val skipW = text.width(font, "Skip") + 32f
        val clearW = text.width(font, "Clear") + 32f
        done.set(card.right - 20f - doneW, by, doneW, 42f)
        skip.set(done.x - 8f - skipW, by, skipW, 42f)
        clear.set(skip.x - 8f - clearW, by, clearW, 42f)
    }

    private val canFinish: Boolean get() = sampleCount >= Pressure.CAL_MIN_SAMPLES

    /** Same as the Skip button (Android back). */
    fun skip() {
        open = false
        onSkip()
    }

    /** Consumes every sample while open. [allowed] = pen, or finger with the debug setting. */
    fun onSample(s: PenSample, x: Float, y: Float, allowed: Boolean) {
        when (s.phase) {
            PenPhase.DOWN -> {
                if (pad.contains(x, y)) {
                    if (!allowed) {
                        statusOverride = "That was a finger. Use the S Pen."
                        return
                    }
                    statusOverride = null
                    capturePointer = s.pointerId
                    lastX = x - pad.x
                    lastY = y - pad.y
                    return
                }
                press.down(s.pointerId, targetAt(x, y))
            }
            PenPhase.MOVE -> {
                if (s.pointerId != capturePointer) return
                val px = x - pad.x
                val py = y - pad.y
                val p = if (s.toolType.isPen) s.pressure else Pressure.CAL_FINGER_RAW
                if (p > Pressure.CAL_SAMPLE_FLOOR) addSample(p)
                addInk(lastX, lastY, px, py, 1f + p * 7f)
                lastX = px
                lastY = py
                updateStatus()
            }
            PenPhase.UP, PenPhase.CANCEL -> {
                if (s.pointerId == capturePointer) {
                    capturePointer = -1
                    return
                }
                if (s.phase == PenPhase.CANCEL) {
                    press.cancel()
                    return
                }
                when (press.up(s.pointerId, targetAt(x, y))) {
                    clear -> clearPad()
                    skip -> skip()
                    done -> if (canFinish) {
                        open = false
                        onDone(Pressure.calibrate(samples, sampleCount))
                    }
                }
            }
            else -> Unit
        }
    }

    private fun addSample(p: Float) {
        if (sampleCount == samples.size) samples = samples.copyOf(samples.size * 2)
        samples[sampleCount++] = p
    }

    private fun addInk(x0: Float, y0: Float, x1: Float, y1: Float, w: Float) {
        if (inkCount * 5 + 5 > ink.size) ink = ink.copyOf(ink.size * 2)
        val i = inkCount * 5
        ink[i] = x0; ink[i + 1] = y0; ink[i + 2] = x1; ink[i + 3] = y1; ink[i + 4] = w
        inkCount++
    }

    private fun targetAt(x: Float, y: Float): Any? = when {
        clear.contains(x, y) -> clear
        skip.contains(x, y) -> skip
        done.contains(x, y) -> done
        else -> null
    }

    fun drawShapes(shapes: Shapes, pal: Palette, f: Frame) {
        shapes.color(pal[Tok.PAPER], 0.7f).rect(0f, 0f, f.wDp, f.hDp)
        Widgets.card(shapes, pal, card)
        // the writing pad: paper, dashed graphite border, a dashed baseline
        shapes.color(pal[Tok.PAPER]).roundRect(pad.x, pad.y, pad.w, pad.h, 10f)
        shapes.color(pal[Tok.GRAPHITE])
        dashedRect(shapes, pad, 4f, 4f)
        var x = pad.x + 16f
        val by = pad.bottom - 34f
        while (x < pad.right - 16f) {
            shapes.rect(x, by - 0.5f, minOf(4f, pad.right - 16f - x), 1f)
            x += 8f
        }
        shapes.color(pal[Tok.INK])
        for (i in 0 until inkCount) {
            val k = i * 5
            shapes.capsule(
                pad.x + ink[k].coerceIn(0f, pad.w), pad.y + ink[k + 1].coerceIn(0f, pad.h),
                pad.x + ink[k + 2].coerceIn(0f, pad.w), pad.y + ink[k + 3].coerceIn(0f, pad.h), ink[k + 4],
            )
        }
        Widgets.button(shapes, pal, clear, primary = false, enabled = true, pressed = press.isPressed(clear))
        Widgets.button(shapes, pal, skip, primary = false, enabled = true, pressed = press.isPressed(skip))
        Widgets.button(shapes, pal, done, primary = true, enabled = canFinish, pressed = press.isPressed(done))
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
        var y = card.y + 20f
        text.line(fonts.get(Face.HAND_BOLD, 32f), TITLE, x, y, 32f, pal[Tok.INK])
        y += 32f + 12f
        intro.draw(text, pal, x, y)
        // the phrase to copy, faint, sitting on the baseline
        val guide = fonts.get(Face.HAND, GUIDE_SIZE)
        text.draw(guide, PHRASE, pad.x + pad.w / 2f, pad.bottom - 34f - guide.capHeight - 2f, pal[Tok.GRAPHITE], GUIDE_ALPHA, Align.center)
        rebuildStatus()
        text.line(fonts.get(Face.MONO, 11f), statusOverride ?: status, x, pad.bottom + 12f, 16f, pal[Tok.MUTED])
        val font = fonts.get(Face.UI_BOLD, 14f)
        Widgets.buttonLabel(text, font, pal, clear, "Clear", primary = false, enabled = true)
        Widgets.buttonLabel(text, font, pal, skip, "Skip", primary = false, enabled = true)
        Widgets.buttonLabel(text, font, pal, done, "Done", primary = true, enabled = canFinish)
    }

    fun invalidate() = intro.invalidate()

    companion object {
        private const val WAITING = "Waiting for the S Pen…"
        const val PHRASE = "Ink Mayhem"
        const val TITLE = "Write “Ink Mayhem”"
        private const val GUIDE_SIZE = 46f
        private const val GUIDE_ALPHA = 0.22f
    }
}
