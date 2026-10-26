package com.pranshuparmar.inkmayhem.ui

import com.pranshuparmar.inkmayhem.game.Death
import com.pranshuparmar.inkmayhem.render.Face
import com.pranshuparmar.inkmayhem.render.Fonts
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok
import kotlin.math.max
import kotlin.math.min

/** Where centred cards sit: the viewport minus the POC's 16 dp (or safe-area) padding. */
class Frame(var wDp: Float = 0f, var hDp: Float = 0f, var safeL: Float = 0f, var safeT: Float = 0f, var safeR: Float = 0f, var safeB: Float = 0f) {
    val padL: Float get() = max(16f, safeL)
    val padR: Float get() = max(16f, safeR)
    val cardW: Float get() = min(wDp - padL - padR, 420f)
}

/** POC #title: name, pitch, the three-ink legend, how scoring works and the call to action. */
class TitleCard(private val fonts: Fonts) {
    val box = Box()
    private val pitch = Flow(22.5f).setText({ fonts.get(Face.UI, 15f) }, Tok.MUTED, "Your runner never stops. Draw the way ahead, fast.")
    private val legend = listOf(
        Triple(Tok.ROPE, 3f, Flow(18f).set(
            Flow.Run({ fonts.get(Face.UI_BOLD, 14.5f) }, Tok.UI_FG, "Light touch: rope."),
            Flow.Run({ fonts.get(Face.UI, 14.5f) }, Tok.MUTED, "Nearly free. Drops boulders. Snaps over long gaps."),
        )),
        Triple(Tok.SPRING, 7f, Flow(18f).set(
            Flow.Run({ fonts.get(Face.UI_BOLD, 14.5f) }, Tok.UI_FG, "Normal writing: spring."),
            Flow.Run({ fonts.get(Face.UI, 14.5f) }, Tok.MUTED, "Bounces you, and boulders, high. Fatal under spiked ceilings."),
        )),
        Triple(Tok.BEAM, 12f, Flow(18f).set(
            Flow.Run({ fonts.get(Face.UI_BOLD, 14.5f) }, Tok.UI_FG, "Press hard: beam."),
            Flow.Run({ fonts.get(Face.UI, 14.5f) }, Tok.MUTED, "Spans anything, carries anything. Ten times the ink of rope."),
        )),
    )
    private val rules = Flow(22.5f).setText(
        { fonts.get(Face.UI, 15f) }, Tok.MUTED,
        "Clear each hazard with the lightest ink that works to grow your combo. Heavier than needed resets it. Side button erases.",
    )
    private var bestLine = ""
    private val legendRowH = FloatArray(3)

    fun layout(text: Text, f: Frame, best: Int) {
        bestLine = if (best > 0) "BEST ${Format.thousands(best)}" else ""
        val inner = f.cardW - 40f
        var h = 20f + 40f + 12f + pitch.layout(text, inner) + 12f
        for ((i, row) in legend.withIndex()) {
            legendRowH[i] = max(row.second, row.third.layout(text, inner - 56f))
            h += legendRowH[i] + if (i < 2) 8f else 0f
        }
        h += 12f + rules.layout(text, inner) + 12f + 28f
        h += 12f // the POC's empty #title-best still takes its flex gap
        if (bestLine.isNotEmpty()) h += 16f
        h += 20f
        box.set((f.wDp - f.cardW) / 2f + (f.padL - f.padR) / 2f, (f.hDp - h) / 2f, f.cardW, h)
    }

    fun drawShapes(shapes: Shapes, pal: Palette, alpha: Float) {
        Widgets.card(shapes, pal, box, alpha = alpha)
        var y = box.y + 20f + 40f + 12f + pitchH() + 12f
        for ((i, row) in legend.withIndex()) {
            val rh = legendRowH[i]
            shapes.color(pal[row.first], alpha).roundRect(box.x + 20f, y + rh / 2f - row.second / 2f, 44f, row.second, min(4f, row.second / 2f))
            y += rh + 8f
        }
    }

    private fun pitchH() = pitch.height

    fun drawText(text: Text, pal: Palette, alpha: Float) {
        val x = box.x + 20f
        var y = box.y + 20f
        text.line(fonts.get(Face.HAND_BOLD, 40f), "Ink Mayhem", x, y, 40f, pal[Tok.INK], alpha)
        y += 40f + 12f
        pitch.draw(text, pal, x, y, alpha)
        y += pitch.height + 12f
        for ((i, row) in legend.withIndex()) {
            val rh = legendRowH[i]
            row.third.draw(text, pal, x + 56f, y + (rh - row.third.height) / 2f, alpha)
            y += rh + 8f
        }
        y += 12f - 8f
        rules.draw(text, pal, x, y, alpha)
        y += rules.height + 12f
        text.line(fonts.get(Face.HAND_BOLD, 20f), "Touch the pen to the page to start", x, y, 28f, pal[Tok.ACCENT], alpha)
        y += 28f
        if (bestLine.isNotEmpty()) text.line(fonts.get(Face.MONO_MEDIUM, 12f), bestLine, x, y + 12f, 16f, pal[Tok.MUTED], alpha)
    }

    fun invalidate() {
        pitch.invalidate(); rules.invalidate(); legend.forEach { it.third.invalidate() }
    }
}

/** POC #over: why the run ended, the score, best and distance, a tip, and how to go again. */
class DeathCard(private val fonts: Fonts) {
    val box = Box()
    private var reason = ""
    private var score = ""
    private var stat = ""
    private var newBest = false
    private val tip = Flow(22.5f)
    private var shownFor: Death? = null

    fun layout(text: Text, f: Frame, death: Death) {
        if (shownFor !== death) {
            shownFor = death
            reason = death.reason
            score = Format.thousands(death.score)
            newBest = death.isBest
            stat = (if (death.isBest) "New best" else "Best ${Format.thousands(death.best)}") + " · ${death.distance} m"
            stat = stat.uppercase()
            tip.setText({ fonts.get(Face.UI, 15f) }, Tok.MUTED, death.tip)
        }
        val inner = f.cardW - 40f
        val h = 20f + 32f + 12f + 64f + 12f + 16f + 12f + tip.layout(text, inner) + 12f + 28f + 20f
        box.set((f.wDp - f.cardW) / 2f + (f.padL - f.padR) / 2f, (f.hDp - h) / 2f, f.cardW, h)
    }

    fun drawShapes(shapes: Shapes, pal: Palette) = Widgets.card(shapes, pal, box)

    fun drawText(text: Text, pal: Palette) {
        val x = box.x + 20f
        var y = box.y + 20f
        text.line(fonts.get(Face.HAND_BOLD, 32f), reason, x, y, 32f, pal[Tok.INK])
        y += 32f + 12f
        val big = fonts.get(Face.HAND_BOLD, 64f, Fonts.DIGITS)
        val w = text.line(big, score, x, y, 64f, pal[Tok.INK])
        // the " pts" span sits on the same baseline at 30 px
        val small = fonts.get(Face.HAND_BOLD, 30f, Fonts.DIGITS)
        text.draw(small, " pts", x + w, y + (64f + big.capHeight) / 2f - small.capHeight, pal[Tok.INK])
        y += 64f + 12f
        text.line(fonts.get(Face.MONO_MEDIUM, 12f), stat, x, y, 16f, if (newBest) pal[Tok.BEAM] else pal[Tok.MUTED])
        y += 16f + 12f
        tip.draw(text, pal, x, y)
        y += tip.height + 12f
        text.line(fonts.get(Face.HAND_BOLD, 20f), "Pen down to run again", x, y, 28f, pal[Tok.ACCENT])
    }

    fun invalidate() = tip.invalidate()
}
