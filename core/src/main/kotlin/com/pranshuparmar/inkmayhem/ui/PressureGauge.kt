package com.pranshuparmar.inkmayhem.ui

import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.render.Face
import com.pranshuparmar.inkmayhem.render.Fonts
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok

/**
 * POC #gauge: a 12 × 150 dp bar on the left edge, rope / spring / beam bands bottom-up sized by
 * the pressure thresholds, with a marker at the live pressure. Fades in only while drawing.
 */
class PressureGauge {
    private var opacity = 0f

    fun update(dt: Float, drawing: Boolean, reducedMotion: Boolean) {
        val target = if (drawing) 1f else 0f
        opacity = if (reducedMotion) target else approach(opacity, target, dt / 0.15f)
    }

    val visible: Boolean get() = opacity > 0.001f

    /** [live] is the marker position; a band lights up only while [drawing]. */
    fun drawShapes(shapes: Shapes, pal: Palette, left: Float, top: Float, live: Float, drawing: Boolean) {
        if (!visible) return
        val bottom = top + H
        val k = if (drawing) Ink.of(live) else null
        for (wi in INKS.indices) { // index loop, no Pairs: this runs every frame while drawing
            val ink = INKS[wi]
            val y0 = bottom - H * BAND_TO[wi]
            val y1 = bottom - H * BAND_FROM[wi]
            val a = opacity * if (ink == k) 1f else 0.5f
            val c = when (ink) { Ink.ROPE -> pal[Tok.ROPE]; Ink.SPRING -> pal[Tok.SPRING]; Ink.BEAM -> pal[Tok.BEAM] }
            shapes.color(c, a)
            when (ink) {
                // Rounded outer ends without overlapping translucent shapes.
                Ink.ROPE -> { shapes.rect(left, y0, W, y1 - y0 - W / 2); shapes.pie(left + W / 2, y1 - W / 2, W / 2, 0f, 180f) }
                Ink.SPRING -> shapes.rect(left, y0, W, y1 - y0)
                Ink.BEAM -> { shapes.pie(left + W / 2, y0 + W / 2, W / 2, 180f, 180f); shapes.rect(left, y0 + W / 2, W, y1 - y0 - W / 2) }
            }
        }
        val y = bottom - H * live.coerceIn(0f, 1f)
        shapes.color(pal[Tok.INK], opacity).roundRect(left - 4f, y - 1.5f, 20f, 3f, 1.5f)
    }

    fun drawText(text: Text, fonts: Fonts, pal: Palette, left: Float, top: Float, live: Float, drawing: Boolean) {
        if (!visible) return
        val font = fonts.get(Face.MONO_MEDIUM, 10f)
        val bottom = top + H
        val k = if (drawing) Ink.of(live) else null
        for (wi in INKS.indices) {
            val ink = INKS[wi]
            val y0 = bottom - H * BAND_TO[wi]
            val y1 = bottom - H * BAND_FROM[wi]
            // the label is the band's ::after, so it shares the band's 0.5 opacity when inactive
            val c = if (ink == k) pal[Tok.UI_FG] else pal[Tok.MUTED]
            text.line(font, LABELS[ink.ordinal], left + 18f, y0, y1 - y0, c, opacity * if (ink == k) 1f else 0.5f)
        }
    }

    companion object {
        const val W = 12f
        const val H = 150f
        private val LABELS = arrayOf("ROPE", "SPRING", "BEAM")
        private val INKS = Ink.entries.toTypedArray()
        /** Each band's pressure range, by ink weight. */
        private val BAND_FROM = floatArrayOf(0f, Ink.ROPE_MAX, Ink.SPRING_MAX)
        private val BAND_TO = floatArrayOf(Ink.ROPE_MAX, Ink.SPRING_MAX, 1f)

        fun approach(v: Float, target: Float, step: Float) =
            if (v < target) minOf(target, v + step) else maxOf(target, v - step)
    }
}
