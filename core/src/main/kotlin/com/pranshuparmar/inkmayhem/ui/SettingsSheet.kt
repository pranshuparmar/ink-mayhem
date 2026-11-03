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

/**
 * Settings: a bottom sheet of rows (volume sliders, then checkboxes) with "How to play",
 * "Tune your pen" and "Done".
 */
class SettingsSheet(
    private val fonts: Fonts,
    private val settings: Settings,
    debugBuild: Boolean,
    private val onRecalibrate: () -> Unit,
    private val onHowToPlay: () -> Unit,
    private val onChanged: () -> Unit,
    /** Debug builds that record input: "Share input recording". */
    private val onShareRecording: (() -> Unit)? = null,
    /** The sound slider was let go: a blip at the new volume. */
    private val onSoundSet: () -> Unit = {},
) {
    private abstract class Row(val title: String) {
        val box = Box()
    }

    private class Option(title: String, val detail: String, val get: () -> Boolean, val set: (Boolean) -> Unit) : Row(title)

    /** A volume, 0 (off) to 1, dragged along a track on the row's right. */
    private class Slider(title: String, val get: () -> Float, val set: (Float) -> Unit) : Row(title) {
        val track = Box()
        var shown = -1f
        var detail = ""
    }

    var open = false
        private set

    private val sound = Slider("Sound effects", { settings.soundVolume }, { settings.soundVolume = it })
    private val music = Slider("Music", { settings.musicVolume }, { settings.musicVolume = it })

    private val options: List<Row> = buildList {
        add(sound)
        add(music)
        add(Option("Dot's remarks", "Little speech bubbles now and then", { settings.remarks }, { settings.remarks = it }))
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

    /** In two columns "Share input recording" joins the buttons' row instead of taking its own. */
    private var shareInRow = false

    fun layout(text: Text, f: Frame) {
        var rowH = ROW_H
        val n = options.size
        fun shareH(cols: Int) = if (onShareRecording != null && cols == 1) 42f + 10f else 0f
        fun height(rows: Int, cols: Int) = 20f + rows * rowH + (rows - 1) * 4f + 4f + 10f + shareH(cols) + 42f + f.safeB + 18f
        // too tall for the screen (landscape, debug rows): the options go into two columns
        val cols = if (height(n, 1) > f.hDp - f.safeT && f.wDp >= 640f) 2 else 1
        shareInRow = cols == 2 && onShareRecording != null
        val rows = (n + cols - 1) / cols
        // still too tall: the rows tighten up
        val room = f.hDp - f.safeT
        if (height(rows, cols) > room) rowH = (rowH - (height(rows, cols) - room) / rows).coerceAtLeast(MIN_ROW_H)
        val h = height(rows, cols)
        val w = minOf(f.wDp, if (cols == 2) 760f else 420f) // .sheet has no padding: edge to edge on phones
        card.set((f.wDp - w) / 2f, f.hDp - h, w, h)
        val colW = (card.w - 40f - (cols - 1) * COL_GAP) / cols
        options.forEachIndexed { i, o ->
            o.box.set(card.x + 20f + (i % cols) * (colW + COL_GAP), card.y + 20f + (i / cols) * (rowH + 4f), colW, rowH)
            if (o is Slider) {
                val tw = minOf(170f, o.box.w * 0.48f)
                o.track.set(o.box.right - tw - 12f, o.box.y, tw, o.box.h)
            }
        }
        var y = card.y + 20f + rows * (rowH + 4f)
        if (onShareRecording != null && !shareInRow) {
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
        if (shareInRow) {
            val shareW = text.width(font, SHARE) + 32f
            share.set(howTo.x - 8f - shareW, y + 10f, shareW, 42f)
        }
    }

    /** Consumes every sample while open. */
    fun onSample(s: PenSample, x: Float, y: Float) {
        when (s.phase) {
            PenPhase.DOWN -> {
                if (!card.contains(x, y)) {
                    hide() // tapping the dimmed page closes the sheet
                    return
                }
                val t = targetAt(x, y)
                press.down(s.pointerId, t)
                if (t is Slider) slide(t, x)
            }
            PenPhase.MOVE -> (press.target as? Slider)?.let { if (s.pointerId == press.pointer) slide(it, x) }
            PenPhase.UP -> if (press.target is Slider && s.pointerId == press.pointer) {
                val t = press.target as Slider
                slide(t, x)
                press.cancel()
                if (t === sound && settings.sound) onSoundSet()
            } else when (val t = press.up(s.pointerId, targetAt(x, y))) {
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

    /** The slider follows the pen; near the left end it snaps to off. */
    private fun slide(t: Slider, x: Float) {
        val u = ((x - t.track.x) / t.track.w).coerceIn(0f, 1f)
        val v = if (u < 0.04f) 0f else kotlin.math.round(u * 20f) / 20f
        if (v != t.get()) {
            t.set(v)
            onChanged()
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
            when (o) {
                is Option -> Widgets.checkbox(shapes, pal, o.box.x + 2f, o.box.y + (o.box.h - 20f) / 2f, o.get())
                is Slider -> {
                    speaker(shapes, pal, o.box.x + 2f, o.box.y + o.box.h / 2f, o.get() > 0f, o === music)
                    val v = o.get()
                    val cy = o.track.y + o.track.h / 2f
                    val x0 = o.track.x
                    val x1 = o.track.right
                    shapes.color(pal[Tok.RULE]).roundRect(x0, cy - 3f, x1 - x0, 6f, 3f)
                    if (v > 0f) shapes.color(pal[Tok.ACCENT]).roundRect(x0, cy - 3f, (x1 - x0) * v, 6f, 3f)
                    val kx = x0 + (x1 - x0) * v
                    shapes.color(pal[Tok.LINE]).circle(kx, cy, 11f)
                    shapes.color(if (press.isPressed(o)) pal[Tok.ACCENT] else pal[Tok.UI]).circle(kx, cy, 10f)
                }
            }
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
            val ty = o.box.y + (o.box.h - 35f) / 2f
            text.line(title, o.title, x, ty, 19f, pal[Tok.UI_FG])
            val d = when (o) {
                is Option -> o.detail
                is Slider -> {
                    val v = o.get()
                    if (v != o.shown) {
                        o.shown = v
                        o.detail = if (v <= 0f) "Off" else "${kotlin.math.round(v * 100f).toInt()} %"
                    }
                    o.detail
                }
                else -> ""
            }
            text.line(detail, d, x, ty + 19f, 16f, pal[Tok.MUTED])
        }
        val font = fonts.get(Face.UI_BOLD, 14f)
        Widgets.buttonLabel(text, font, pal, howTo, HOW_TO_PLAY, primary = false, enabled = true)
        if (onShareRecording != null) Widgets.buttonLabel(text, font, pal, share, SHARE, primary = false, enabled = true)
        Widgets.buttonLabel(text, font, pal, recal, RECALIBRATE, primary = false, enabled = true)
        Widgets.buttonLabel(text, font, pal, done, "Done", primary = true, enabled = true)
    }

    /** A loudspeaker (or a note, for music) where a checkbox would be; struck through when off. */
    private fun speaker(shapes: Shapes, pal: Palette, x: Float, cy: Float, on: Boolean, note: Boolean) {
        val c = if (on) pal[Tok.UI_FG] else pal[Tok.MUTED]
        shapes.color(c)
        if (note) {
            shapes.circle(x + 6f, cy + 5f, 4f)
            shapes.circle(x + 15f, cy + 3f, 4f)
            shapes.rect(x + 8.6f, cy - 8f, 1.8f, 13f)
            shapes.rect(x + 17.6f, cy - 10f, 1.8f, 13f)
            shapes.capsule(x + 9.5f, cy - 8f, x + 18.5f, cy - 10f, 2.6f)
        } else {
            shapes.rect(x + 1f, cy - 3.5f, 5f, 7f)
            shapes.triangle(x + 5f, cy, x + 12f, cy - 8f, x + 12f, cy + 8f)
            shapes.rect(x + 5f, cy - 3.5f, 3f, 7f)
            if (on) {
                shapes.arc(x + 12f, cy, 5f, 1.6f, -0.9f, 0.9f)
                shapes.arc(x + 12f, cy, 9f, 1.6f, -0.9f, 0.9f)
            }
        }
        if (!on) shapes.color(pal[Tok.BEAM]).capsule(x, cy + 9f, x + 20f, cy - 9f, 2f)
    }

    companion object {
        private const val ROW_H = 10f + 19f + 16f + 10f
        private const val MIN_ROW_H = 40f
        private const val SHARE = "Share input recording"
        private const val COL_GAP = 16f
        const val HOW_TO_PLAY = "How to play"
        const val RECALIBRATE = "Tune your pen"
    }
}
