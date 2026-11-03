package com.pranshuparmar.inkmayhem.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok

/** A mutable rectangle in dp, y down. */
class Box(var x: Float = 0f, var y: Float = 0f, var w: Float = 0f, var h: Float = 0f) {
    fun set(x: Float, y: Float, w: Float, h: Float): Box {
        this.x = x; this.y = y; this.w = w; this.h = h
        return this
    }

    operator fun contains(p: Pair<Float, Float>) = contains(p.first, p.second)
    fun contains(px: Float, py: Float) = px >= x && px <= x + w && py >= y && py <= y + h
    val right: Float get() = x + w
    val bottom: Float get() = y + h
}

/**
 * Word-wrapped rich text (runs of bold and coloured words) with CSS-style line height. Layout is
 * cached per width so drawing a static card every frame doesn't allocate.
 */
class Flow(private val lineHeight: Float) {
    class Run(val font: () -> BitmapFont, val color: Tok, val text: String)

    private class Word(val run: Run, val text: String) {
        var x = 0f
        var line = 0
    }

    private var runs: List<Run> = emptyList()
    private val words = ArrayList<Word>()
    private var laidOutWidth = -1f
    var height = 0f
        private set
    var lineCount = 0
        private set

    fun set(vararg r: Run): Flow {
        runs = r.toList()
        words.clear()
        for (run in runs) {
            for (w in run.text.split(' ')) if (w.isNotEmpty()) words += Word(run, w)
        }
        laidOutWidth = -1f
        return this
    }

    fun setText(font: () -> BitmapFont, color: Tok, s: String): Flow = set(Run(font, color, s))

    fun layout(text: Text, width: Float): Float {
        if (width == laidOutWidth) return height
        laidOutWidth = width
        var x = 0f
        var line = 0
        var first = true
        for (w in words) {
            val font = w.run.font()
            val ww = text.width(font, w.text)
            val space = if (first) 0f else font.spaceXadvance
            if (!first && x + space + ww > width) {
                line++
                x = 0f
            } else {
                x += space
            }
            w.x = x
            w.line = line
            x += ww
            first = false
        }
        lineCount = if (words.isEmpty()) 0 else line + 1
        height = lineCount * lineHeight
        return height
    }

    /** Fonts may be regenerated (density change); force a relayout next time. */
    fun invalidate() {
        laidOutWidth = -1f
    }

    fun draw(text: Text, pal: Palette, x: Float, top: Float, alpha: Float = 1f, center: Float = Float.NaN) {
        var lineStart = 0
        while (lineStart < words.size) {
            val line = words[lineStart].line
            var end = lineStart
            while (end < words.size && words[end].line == line) end++
            var shift = 0f
            if (!center.isNaN()) {
                val last = words[end - 1]
                shift = (center - (last.x + text.width(last.run.font(), last.text))) / 2f
            }
            for (i in lineStart until end) {
                val w = words[i]
                text.line(w.run.font(), w.text, x + shift + w.x, top + line * lineHeight, lineHeight, pal[w.run.color], alpha)
            }
            lineStart = end
        }
    }
}

object Widgets {
    /** A card: rounded, bordered, with a soft drop shadow (0 10 dp, 36 dp blur). */
    fun card(shapes: Shapes, pal: Palette, b: Box, radius: Float = 18f, topOnly: Boolean = false, alpha: Float = 1f) {
        val shadow = pal[Tok.SHADOW]
        for (i in 6 downTo 1) {
            val e = i * 5f
            shapes.color(shadow, alpha * 0.22f / i)
            if (topOnly) {
                shapes.topRoundRect(b.x - e / 2f, b.y + 10f - e / 2f, b.w + e, b.h + e, radius + e / 2f)
            } else {
                shapes.roundRect(b.x - e / 2f, b.y + 10f - e / 2f, b.w + e, b.h + e, radius + e / 2f)
            }
        }
        shapes.color(pal[Tok.LINE], alpha)
        if (topOnly) shapes.topRoundRect(b.x, b.y, b.w, b.h, radius) else shapes.roundRect(b.x, b.y, b.w, b.h, radius)
        shapes.color(pal[Tok.UI], alpha)
        if (topOnly) {
            shapes.topRoundRect(b.x + 1f, b.y + 1f, b.w - 2f, b.h - 1f, radius - 1f)
        } else {
            shapes.roundRect(b.x + 1f, b.y + 1f, b.w - 2f, b.h - 2f, radius - 1f)
        }
    }

    /** A button: 42 dp tall, radius 10; primary is accent-filled. */
    private val mixed = Color()

    /** CSS opacity on a whole element over the card: pre-mix instead of stacking translucent shapes. */
    private fun faded(pal: Palette, c: Color, enabled: Boolean): Color =
        if (enabled) c else mixed.set(pal[Tok.UI]).lerp(c, 0.45f)

    fun button(shapes: Shapes, pal: Palette, b: Box, primary: Boolean, enabled: Boolean, pressed: Boolean) {
        val bg = if (primary) pal[Tok.ACCENT] else pal[Tok.UI]
        val border = if (primary) pal[Tok.ACCENT] else pal[Tok.LINE]
        shapes.color(faded(pal, border, enabled)).roundRect(b.x, b.y, b.w, b.h, 10f)
        shapes.color(faded(pal, bg, enabled)).roundRect(b.x + 1f, b.y + 1f, b.w - 2f, b.h - 2f, 9f)
        if (pressed && enabled) shapes.color(pal[Tok.INK], 0.08f).roundRect(b.x, b.y, b.w, b.h, 10f)
    }

    fun buttonLabel(text: Text, font: BitmapFont, pal: Palette, b: Box, label: String, primary: Boolean, enabled: Boolean) {
        val c = if (primary) pal[Tok.ACCENT_FG] else pal[Tok.UI_FG]
        text.line(font, label, b.x + b.w / 2f, b.y, b.h, faded(pal, c, enabled), 1f, com.badlogic.gdx.utils.Align.center)
    }

    /** A 20 dp checkbox drawn like an accent-coloured native one. */
    fun checkbox(shapes: Shapes, pal: Palette, x: Float, y: Float, checked: Boolean) {
        if (checked) {
            shapes.color(pal[Tok.ACCENT]).roundRect(x, y, 20f, 20f, 4f)
            shapes.color(pal[Tok.ACCENT_FG])
            shapes.capsule(x + 4.5f, y + 10.5f, x + 8.5f, y + 14.5f, 2.4f)
            shapes.capsule(x + 8.5f, y + 14.5f, x + 15.5f, y + 6f, 2.4f)
        } else {
            shapes.color(pal[Tok.MUTED]).roundRect(x, y, 20f, 20f, 4f)
            shapes.color(pal[Tok.UI]).roundRect(x + 1.5f, y + 1.5f, 17f, 17f, 2.5f)
        }
    }
}

/** Tracks one pressed widget so a tap only activates when it starts and ends on the same target. */
class Press {
    var pointer = -1
        private set
    var target: Any? = null
        private set

    fun down(pointer: Int, target: Any?) {
        this.pointer = pointer
        this.target = target
    }

    /** Returns the target if [pointer] releases on it. */
    fun up(pointer: Int, targetUnder: Any?): Any? {
        if (pointer != this.pointer) return null
        val t = target
        this.pointer = -1
        target = null
        return if (t != null && t == targetUnder) t else null
    }

    fun cancel() {
        pointer = -1
        target = null
    }

    fun isPressed(t: Any) = target == t
}
