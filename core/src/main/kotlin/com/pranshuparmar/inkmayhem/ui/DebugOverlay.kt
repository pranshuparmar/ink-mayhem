package com.pranshuparmar.inkmayhem.ui

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.pranshuparmar.inkmayhem.input.PenButtons
import com.pranshuparmar.inkmayhem.input.PenReadout
import com.pranshuparmar.inkmayhem.input.ToolType
import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.render.Face
import com.pranshuparmar.inkmayhem.render.Fonts
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok
import kotlin.math.roundToInt

/**
 * The "Pen readout" (POC #debug): tool type, raw → normalized pressure, buttons, tilt, hover and
 * event rates. Text is rebuilt at most five times a second, like the POC's 200 ms interval.
 */
class DebugOverlay {
    private val lines = ArrayList<String>()
    private var lastBuild = -1.0
    /** [extraLines] is only invoked when the text is rebuilt (5 times a second). */
    fun update(now: Double, r: PenReadout, cal: Float, extraLines: () -> List<String>) {
        if (now - lastBuild < 0.2 && lines.isNotEmpty()) return
        lastBuild = now
        lines.clear()
        val tool = when (r.tool) {
            ToolType.STYLUS -> "stylus"
            ToolType.ERASER -> "eraser"
            ToolType.FINGER -> "finger"
            ToolType.MOUSE -> "mouse"
            ToolType.UNKNOWN -> "-"
        }
        val kind = if (r.tool.isPen) Ink.of(r.normalized).label else ""
        lines += "tool      $tool"
        lines += "pressure  ${f3(r.raw)} → ${f2(r.normalized)} $kind"
        val side = if (r.buttons and PenButtons.SIDE != 0) " side" else ""
        lines += "buttons   ${r.buttons}$side  tilt ${r.tiltX.roundToInt()}° ${r.tiltY.roundToInt()}°"
        lines += "hover     ${if (r.hovering) "yes" else "no "}  ${r.hoverEventsPerSec} ev/s  ${r.hoverSamplesPerSec} smp/s"
        lines += "moves     ${r.moveEventsPerSec} ev/s  ${r.moveSamplesPerSec} smp/s"
        lines += "calib     median ${f3(cal)}"
        lines += extraLines()
    }

    /** Draws the panel; [right] and [top] are the anchor in dp. Call between shape/text passes via the lambdas. */
    fun layoutHeight(font: BitmapFont): Float = 8f * 2 + lines.size * LINE_H

    fun drawBox(shapes: Shapes, pal: Palette, right: Float, top: Float, width: Float, height: Float) {
        shapes.color(pal[Tok.LINE], 0.94f).roundRect(right - width, top, width, height, 8f)
        shapes.color(pal[Tok.UI], 0.94f).roundRect(right - width + 1f, top + 1f, width - 2f, height - 2f, 7f)
    }

    fun drawText(text: Text, font: BitmapFont, pal: Palette, left: Float, top: Float) {
        var y = top + 8f
        for (l in lines) {
            text.line(font, l, left + 10f, y, LINE_H, pal[Tok.UI_FG])
            y += LINE_H
        }
    }

    fun width(text: Text, font: BitmapFont): Float {
        var w = 0f
        for (l in lines) w = maxOf(w, text.width(font, l))
        return w + 20f
    }

    fun font(fonts: Fonts): BitmapFont = fonts.get(Face.MONO, 11f)

    private fun f3(v: Float) = String.format(java.util.Locale.US, "%.3f", v)
    private fun f2(v: Float) = String.format(java.util.Locale.US, "%.2f", v)

    companion object {
        /** 11px / 1.5 line height. */
        const val LINE_H = 16.5f
    }
}
