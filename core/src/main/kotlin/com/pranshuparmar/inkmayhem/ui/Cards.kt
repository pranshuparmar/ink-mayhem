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

/**
 * POC #title: name, pitch, the three-ink legend and how scoring works, then the two ways to play
 * (Stages, Endless) instead of the POC's "touch the pen to the page".
 */
class TitleCard(private val fonts: Fonts) {
    val box = Box()
    val stagesButton = Box()
    val endlessButton = Box()
    val press = Press()
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
    private var starsLine = ""
    private val legendRowH = FloatArray(3)

    private var shownBest = -1
    private var shownStars = -1

    fun layout(text: Text, f: Frame, best: Int, stars: Int, maxStars: Int) {
        if (best != shownBest || stars != shownStars) {
            shownBest = best
            shownStars = stars
            bestLine = if (best > 0) "ENDLESS BEST ${Format.thousands(best)}" else ""
            starsLine = "$stars / $maxStars"
        }
        starsW = text.width(fonts.get(Face.MONO_MEDIUM, 12f), starsLine)
        val inner = f.cardW - 40f
        var h = 20f + 40f + 12f + pitch.layout(text, inner) + 12f
        for ((i, row) in legend.withIndex()) {
            legendRowH[i] = max(row.second, row.third.layout(text, inner - 56f))
            h += legendRowH[i] + if (i < 2) 8f else 0f
        }
        h += 12f + rules.layout(text, inner) + 16f + 46f + 12f + 16f + 20f
        box.set((f.wDp - f.cardW) / 2f + (f.padL - f.padR) / 2f, (f.hDp - h) / 2f, f.cardW, h)
        val by = box.bottom - 20f - 16f - 12f - 46f
        val bw = (inner - 10f) / 2f
        stagesButton.set(box.x + 20f, by, bw, 46f)
        endlessButton.set(stagesButton.right + 10f, by, bw, 46f)
    }

    fun targetAt(x: Float, y: Float): Any? = when {
        stagesButton.contains(x, y) -> stagesButton
        endlessButton.contains(x, y) -> endlessButton
        else -> null
    }

    fun drawShapes(shapes: Shapes, pal: Palette, alpha: Float) {
        Widgets.card(shapes, pal, box, alpha = alpha)
        var y = box.y + 20f + 40f + 12f + pitchH() + 12f
        for ((i, row) in legend.withIndex()) {
            val rh = legendRowH[i]
            shapes.color(pal[row.first], alpha).roundRect(box.x + 20f, y + rh / 2f - row.second / 2f, 44f, row.second, min(4f, row.second / 2f))
            y += rh + 8f
        }
        Widgets.button(shapes, pal, stagesButton, primary = true, enabled = true, pressed = press.isPressed(stagesButton))
        Widgets.button(shapes, pal, endlessButton, primary = false, enabled = true, pressed = press.isPressed(endlessButton))
        shapes.color(pal[Tok.SPRING], alpha).star(box.right - 20f - starsW - 12f, stagesButton.bottom + 12f + 7.5f, 7.5f)
    }

    private var starsW = 0f

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
        val font = fonts.get(Face.UI_BOLD, 15f)
        Widgets.buttonLabel(text, font, pal, stagesButton, "Stages", primary = true, enabled = true)
        Widgets.buttonLabel(text, font, pal, endlessButton, "Endless", primary = false, enabled = true)
        val mono = fonts.get(Face.MONO_MEDIUM, 12f)
        val ly = stagesButton.bottom + 12f
        if (bestLine.isNotEmpty()) text.line(mono, bestLine, x, ly, 16f, pal[Tok.MUTED], alpha)
        text.line(mono, starsLine, box.right - 20f, ly, 16f, pal[Tok.MUTED], alpha, com.badlogic.gdx.utils.Align.right)
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
