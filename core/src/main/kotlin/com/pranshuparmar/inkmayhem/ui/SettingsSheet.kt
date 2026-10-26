package com.pranshuparmar.inkmayhem.ui

import com.pranshuparmar.inkmayhem.Settings
import com.pranshuparmar.inkmayhem.input.PenPhase
import com.pranshuparmar.inkmayhem.input.PenSample
import com.pranshuparmar.inkmayhem.render.Face
import com.pranshuparmar.inkmayhem.render.Fonts
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok

/** POC #sheet: a bottom sheet of checkbox rows with "Recalibrate pressure" and "Done". */
class SettingsSheet(
    private val fonts: Fonts,
    private val settings: Settings,
    debugBuild: Boolean,
    private val onRecalibrate: () -> Unit,
    private val onChanged: () -> Unit,
) {
    private class Option(val title: String, val detail: String, val get: () -> Boolean, val set: (Boolean) -> Unit) {
        val box = Box()
    }

    var open = false
        private set

    private val options = buildList {
        add(Option("Sound", "Pen scratch, snaps, bounces", { settings.sound }, { settings.sound = it }))
        add(Option("Colour ink by type", "Rope green, spring amber, beam red", { settings.tint }, { settings.tint = it }))
        add(Option("Pen readout", "Pointer type, pressure, buttons, hover rate", { settings.penReadout }, { settings.penReadout = it }))
        add(Option("Haptics", "A short buzz on snaps and crashes", { settings.haptics }, { settings.haptics = it }))
        if (debugBuild) {
            add(Option("Allow finger and mouse", "Testing only. Fingers draw fixed spring ink.", { settings.allowFinger }, { settings.allowFinger = it }))
        }
    }
    private val card = Box()
    private val recal = Box()
    private val done = Box()
    private val press = Press()

    fun show() {
        open = true
        press.cancel()
    }

    fun hide() {
        open = false
        press.cancel()
    }

    fun layout(text: Text, f: Frame) {
        val rowH = 10f + 19f + 16f + 10f
        val n = options.size
        val h = 20f + n * rowH + (n - 1) * 4f + 4f + 10f + 42f + f.safeB + 18f
        card.set((f.wDp - f.cardW) / 2f + (f.padL - f.padR) / 2f, f.hDp - h, f.cardW, h)
        var y = card.y + 20f
        for (o in options) {
            o.box.set(card.x + 20f, y, card.w - 40f, rowH)
            y += rowH + 4f
        }
        val font = fonts.get(Face.UI_BOLD, 14f)
        val doneW = text.width(font, "Done") + 32f
        val recalW = text.width(font, "Recalibrate pressure") + 32f
        done.set(card.right - 20f - doneW, y + 10f, doneW, 42f)
        recal.set(done.x - 8f - recalW, y + 10f, recalW, 42f)
    }

    /** Consumes every sample while open. */
    fun onSample(s: PenSample, x: Float, y: Float) {
        when (s.phase) {
            PenPhase.DOWN -> {
                if (!card.contains(x, y)) {
                    hide() // tapping the dimmed page closes the sheet
                    return
                }
                press.down(s.pointerId, targetAt(x, y))
            }
            PenPhase.UP -> when (val t = press.up(s.pointerId, targetAt(x, y))) {
                done -> hide()
                recal -> {
                    hide()
                    onRecalibrate()
                }
                is Option -> {
                    t.set(!t.get())
                    onChanged()
                }
            }
            PenPhase.CANCEL -> press.cancel()
            else -> Unit
        }
    }

    private fun targetAt(x: Float, y: Float): Any? {
        if (done.contains(x, y)) return done
        if (recal.contains(x, y)) return recal
        return options.firstOrNull { it.box.contains(x, y) }
    }

    fun drawShapes(shapes: Shapes, pal: Palette, f: Frame) {
        shapes.color(pal[Tok.PAPER], 0.45f).rect(0f, 0f, f.wDp, f.hDp)
        Widgets.card(shapes, pal, card, topOnly = true)
        for (o in options) {
            shapes.color(pal[Tok.LINE]).rect(o.box.x, o.box.bottom - 1f, o.box.w, 1f)
            Widgets.checkbox(shapes, pal, o.box.x + 2f, o.box.y + (o.box.h - 20f) / 2f, o.get())
        }
        Widgets.button(shapes, pal, recal, primary = false, enabled = true, pressed = press.isPressed(recal))
        Widgets.button(shapes, pal, done, primary = true, enabled = true, pressed = press.isPressed(done))
    }

    fun drawText(text: Text, pal: Palette) {
        val title = fonts.get(Face.UI, 15f)
        val detail = fonts.get(Face.UI, 12.5f)
        for (o in options) {
            val x = o.box.x + 2f + 20f + 12f
            text.line(title, o.title, x, o.box.y + 10f, 19f, pal[Tok.UI_FG])
            text.line(detail, o.detail, x, o.box.y + 29f, 16f, pal[Tok.MUTED])
        }
        val font = fonts.get(Face.UI_BOLD, 14f)
        Widgets.buttonLabel(text, font, pal, recal, "Recalibrate pressure", primary = false, enabled = true)
        Widgets.buttonLabel(text, font, pal, done, "Done", primary = true, enabled = true)
    }
}
