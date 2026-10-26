package com.pranshuparmar.inkmayhem.lab

import com.badlogic.gdx.utils.FloatArray as GdxFloatArray
import com.pranshuparmar.inkmayhem.input.PenButtons
import com.pranshuparmar.inkmayhem.input.PenPhase
import com.pranshuparmar.inkmayhem.input.PenSample
import com.pranshuparmar.inkmayhem.input.ToolType
import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Tok
import kotlin.math.hypot

/**
 * Milestone-1 bring-up screen: a still notebook page for checking the S Pen bridge on device.
 * Ink thickness and colour follow calibrated pressure exactly as the game will; the side button
 * (or the eraser end) erases; hovering shows the nib; fingers are rejected.
 */
class PenLab(private val onFingerRejected: () -> Unit) {
    private class Stroke {
        val pts = GdxFloatArray(256) // x, y, p triples in dp / normalized pressure
    }

    private val strokes = ArrayList<Stroke>()
    private var drawing: Stroke? = null
    private var drawingPointer = -1
    private var smoothed = -1f
    private var erasingPointer = -1

    var hoverOn = false
        private set
    var hoverX = 0f
        private set
    var hoverY = 0f
        private set

    /** Live (smoothed) pressure of the current stroke, or -1 when not drawing. */
    val livePressure: Float get() = if (drawing != null) smoothed else -1f

    /** [xDp]/[yDp] are screen dp; [p] is calibrated pressure (fixed for fingers). */
    fun onSample(s: PenSample, xDp: Float, yDp: Float, p: Float, allowFinger: Boolean, scale: Float) {
        val allowed = s.toolType.isPen || allowFinger
        when (s.phase) {
            PenPhase.DOWN -> {
                if (!allowed) {
                    onFingerRejected()
                    return
                }
                if (drawing != null || erasingPointer >= 0) return
                hoverOn = false
                if (s.toolType == ToolType.ERASER || s.buttons and (PenButtons.SIDE or PenButtons.SECONDARY) != 0) {
                    erasingPointer = s.pointerId
                    eraseAt(xDp, yDp, scale)
                    return
                }
                drawing = Stroke().also { strokes += it }
                drawingPointer = s.pointerId
                smoothed = -1f
                addPoint(xDp, yDp, p, scale)
            }
            PenPhase.MOVE -> when (s.pointerId) {
                drawingPointer -> if (drawing != null) addPoint(xDp, yDp, p, scale)
                erasingPointer -> eraseAt(xDp, yDp, scale)
            }
            PenPhase.UP, PenPhase.CANCEL -> {
                if (s.pointerId == drawingPointer) {
                    drawing = null
                    drawingPointer = -1
                }
                if (s.pointerId == erasingPointer) erasingPointer = -1
            }
            PenPhase.HOVER -> if (s.toolType.isPen || (s.toolType == ToolType.MOUSE && allowFinger)) {
                hoverOn = true
                hoverX = xDp
                hoverY = yDp
            }
            PenPhase.HOVER_EXIT -> hoverOn = false
        }
    }

    private fun addPoint(x: Float, y: Float, p: Float, scale: Float) {
        val d = drawing ?: return
        smoothed = if (smoothed < 0f) p else smoothed * 0.7f + p * 0.3f
        val a = d.pts
        if (a.size >= 3) {
            val lx = a[a.size - 3]
            val ly = a[a.size - 2]
            if (hypot(x - lx, y - ly) / scale < 0.02f) {
                a[a.size - 1] = maxOf(a[a.size - 1], smoothed)
                return
            }
        }
        a.add(x, y, smoothed)
    }

    private fun eraseAt(x: Float, y: Float, scale: Float) {
        val r = 0.3f * scale
        strokes.removeAll { s ->
            val a = s.pts
            var hit = false
            var i = 0
            while (i < a.size && !hit) {
                if (hypot(a[i] - x, a[i + 1] - y) < r + Ink.thickness(a[i + 2]) * scale / 2f) hit = true
                i += 3
            }
            hit && s !== drawing
        }
    }

    fun clear() = strokes.clear()

    fun draw(shapes: Shapes, pal: Palette, scale: Float, tint: Boolean) {
        for (s in strokes) {
            val a = s.pts
            if (a.size == 3) {
                shapes.color(colorOf(pal, a[2], tint)).circle(a[0], a[1], Ink.thickness(a[2]) * scale / 2f)
            }
            var i = 3
            while (i < a.size) {
                val p = (a[i + 2] + a[i - 1]) / 2f
                shapes.color(colorOf(pal, p, tint)).capsule(a[i - 3], a[i - 2], a[i], a[i + 1], Ink.thickness(p) * scale)
                i += 3
            }
        }
        if (hoverOn && drawing == null && erasingPointer < 0) {
            shapes.color(pal[Tok.INK]).ring(hoverX, hoverY, Ink.thickness(0.45f) * scale / 2f + 4f, 1.2f, 3f, 3f)
            shapes.color(pal[Tok.INK]).circle(hoverX, hoverY, 1.6f)
        }
    }

    private fun colorOf(pal: Palette, p: Float, tint: Boolean) = if (!tint) pal[Tok.INK] else when (Ink.of(p)) {
        Ink.ROPE -> pal[Tok.ROPE]
        Ink.SPRING -> pal[Tok.SPRING]
        Ink.BEAM -> pal[Tok.BEAM]
    }
}
