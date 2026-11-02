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

/** POC #sheet: a bottom sheet of checkbox rows with "How to play", "Tune your pen" and "Done". */
class SettingsSheet(
    private val fonts: Fonts,
    private val settings: Settings,
    debugBuild: Boolean,
    private val onRecalibrate: () -> Unit,
    private val onHowToPlay: () -> Unit,
    private val onChanged: () -> Unit,
    /** Debug builds that record input: "Share input recording". */
    private val onShareRecording: (() -> Unit)? = null,
) {
    private class Option(val title: String, val detail: String, val get: () -> Boolean, val set: (Boolean) -> Unit) {
        val box = Box()
    }

    var open = false
        private set

    private val options = buildList {
        add(Option("Sound", "Pen scratch, snaps, bounces", { settings.sound }, { settings.sound = it }))
        add(Option("Music", "Each world's theme, and the title tune", { settings.music }, { settings.music = it }))
        add(Option("Colour ink by type", "Rope green, spring amber, beam red", { settings.tint }, { settings.tint = it }))
        add(Option("Pen readout", "Pointer type, pressure, buttons, hover rate", { settings.penReadout }, { settings.penReadout = it }))
        add(Option("Haptics", "A short buzz on snaps and crashes", { settings.haptics }, { settings.haptics = it }))
        if (debugBuild) {
            add(Option("Allow finger and mouse", "Testing only. Fingers draw fixed spring ink.", { settings.allowFinger }, { settings.allowFinger = it }))
            add(Option("Record input", "Debug only. Saves sessions for replays.", { settings.recordInput }, { settings.recordInput = it }))
        }
    }
    private val card = Box()
    private val howTo = Box()
    private val share = Box()
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
        val shareH = if (onShareRecording != null) 42f + 10f else 0f
        fun height(rows: Int) = 20f + rows * rowH + (rows - 1) * 4f + 4f + 10f + shareH + 42f + f.safeB + 18f
        // too tall for the screen (landscape, debug rows): the options go into two columns
        val cols = if (height(n) > f.hDp - f.safeT && f.wDp >= 640f) 2 else 1
        val rows = (n + cols - 1) / cols
        val h = height(rows)
        val w = minOf(f.wDp, if (cols == 2) 760f else 420f) // .sheet has no padding: edge to edge on phones
        card.set((f.wDp - w) / 2f, f.hDp - h, w, h)
        val colW = (card.w - 40f - (cols - 1) * COL_GAP) / cols
        options.forEachIndexed { i, o ->
            o.box.set(card.x + 20f + (i % cols) * (colW + COL_GAP), card.y + 20f + (i / cols) * (rowH + 4f), colW, rowH)
        }
        var y = card.y + 20f + rows * (rowH + 4f)
        if (onShareRecording != null) {
            share.set(card.x + 20f, y + 10f, card.w - 40f, 42f)
            y += 42f + 10f
        } else {
            share.set(0f, 0f, 0f, 0f)
        }
        val font = fonts.get(Face.UI_BOLD, 14f)
        val doneW = text.width(font, "Done") + 32f
        val recalW = text.width(font, RECALIBRATE) + 32f
        val howToW = text.width(font, HOW_TO_PLAY) + 32f
        done.set(card.right - 20f - doneW, y + 10f, doneW, 42f)
        recal.set(done.x - 8f - recalW, y + 10f, recalW, 42f)
        howTo.set(recal.x - 8f - howToW, y + 10f, howToW, 42f)
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
                howTo -> {
                    hide()
                    onHowToPlay()
                }
                share -> {
                    hide()
                    onShareRecording?.invoke()
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
        if (howTo.contains(x, y)) return howTo
        if (onShareRecording != null && share.contains(x, y)) return share
        return options.firstOrNull { it.box.contains(x, y) }
    }

    fun drawShapes(shapes: Shapes, pal: Palette, f: Frame) {
        shapes.color(pal[Tok.PAPER], 0.55f).rect(0f, 0f, f.wDp, f.hDp)
        Widgets.card(shapes, pal, card, topOnly = true)
        for (o in options) {
            shapes.color(pal[Tok.LINE]).rect(o.box.x, o.box.bottom - 1f, o.box.w, 1f)
            Widgets.checkbox(shapes, pal, o.box.x + 2f, o.box.y + (o.box.h - 20f) / 2f, o.get())
        }
        Widgets.button(shapes, pal, howTo, primary = false, enabled = true, pressed = press.isPressed(howTo))
        if (onShareRecording != null) Widgets.button(shapes, pal, share, primary = false, enabled = true, pressed = press.isPressed(share))
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
        Widgets.buttonLabel(text, font, pal, howTo, HOW_TO_PLAY, primary = false, enabled = true)
        if (onShareRecording != null) Widgets.buttonLabel(text, font, pal, share, "Share input recording", primary = false, enabled = true)
        Widgets.buttonLabel(text, font, pal, recal, RECALIBRATE, primary = false, enabled = true)
        Widgets.buttonLabel(text, font, pal, done, "Done", primary = true, enabled = true)
    }

    companion object {
        private const val COL_GAP = 16f
        const val HOW_TO_PLAY = "How to play"
        const val RECALIBRATE = "Tune your pen"
    }
}
