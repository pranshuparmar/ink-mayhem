package com.pranshuparmar.inkmayhem.ui

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.utils.Align
import com.pranshuparmar.inkmayhem.render.BubbleLayout
import com.pranshuparmar.inkmayhem.render.BuddyDrawer
import com.pranshuparmar.inkmayhem.render.BuddyState
import com.pranshuparmar.inkmayhem.render.Expr
import com.pranshuparmar.inkmayhem.render.Face
import com.pranshuparmar.inkmayhem.render.Fonts
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The end of the game, after the Big Smudge: the page clean again, the runner rolling along a
 * line that draws itself, then the player's notebook (stages, stars, gold stars, scores) and the
 * credits roll up to "Thanks for playing!". From the title screen it shows the credits alone.
 * One button: Skip while it rolls, then back to the title.
 */
class EndingCard(private val fonts: Fonts) {
    /** What the player has done, for the notebook page. */
    class Totals(val cleared: Int, val stages: Int, val stars: Int, val gold: Int, val score: Int)

    val button = Box()
    val press = Press()
    /** The runner, rolling along its line in the finale. */
    private val buddy = BuddyState()
    private var drawer: BuddyDrawer? = null
    private val bubble = BubbleLayout()
    private var lastNow = Double.NaN
    private var cheered = false
    private var bubbleTipX = 0f
    private var bubbleTipY = 0f

    private enum class Kind { TITLE, LINE, NOTE, HEAD, ROW, RUNNER, GAP }

    private class Item(val kind: Kind, val text: String = "", val value: String = "", val h: Float) {
        var y = 0f
    }

    private val items = ArrayList<Item>()
    private var height = 0f
    private var startAt = 0.0
    /** The finale (with the epilogue, the runner and the totals), or just the credits. */
    private var finale = true
    private var skippedAt = Double.NaN
    private var holdS = 0f
    private var s0 = 0f
    private var sEnd = 0f
    private var laidH = -1f
    private var laidW = -1f
    private var colW = 0f
    private var cx = 0f
    private var top = 0f
    private var bottomPad = 0f

    fun start(now: Double, totals: Totals?) {
        startAt = now
        skippedAt = Double.NaN
        finale = totals != null
        press.cancel()
        cheered = false
        buddy.hush()
        lastNow = Double.NaN
        items.clear()
        if (totals != null) {
            items += Item(Kind.TITLE, "The Big Smudge is gone.", h = 52f)
            items += Item(Kind.NOTE, "Every page is clean again.", h = 30f)
            items += Item(Kind.RUNNER, h = 86f)
            items += Item(Kind.GAP, h = 40f)
            items += Item(Kind.HEAD, "YOUR NOTEBOOK", h = 26f)
            items += Item(Kind.ROW, "STAGES CLEARED", "${totals.cleared} / ${totals.stages}", 24f)
            items += Item(Kind.ROW, "STARS", "${totals.stars} / ${totals.stages * 3}", 24f)
            items += Item(Kind.ROW, "GOLD STARS", "${totals.gold} / ${totals.stages * 3}", 24f)
            items += Item(Kind.ROW, "BEST SCORES, ADDED UP", Format.thousands(totals.score), 24f)
            items += Item(Kind.GAP, h = 64f)
        }
        items += Item(Kind.TITLE, "Ink Mayhem", h = 58f)
        items += Item(Kind.LINE, "A game by $AUTHOR", h = 26f)
        items += Item(Kind.GAP, h = 30f)
        items += Item(Kind.HEAD, "MADE WITH", h = 24f)
        items += Item(Kind.LINE, "libGDX, Box2D, FreeType and Kotlin", h = 24f)
        items += Item(Kind.GAP, h = 22f)
        items += Item(Kind.HEAD, "LETTERING", h = 24f)
        items += Item(Kind.LINE, "Kalam, IBM Plex Mono", h = 24f)
        items += Item(Kind.LINE, "and Atkinson Hyperlegible", h = 24f)
        items += Item(Kind.NOTE, "under the SIL Open Font License", h = 26f)
        items += Item(Kind.GAP, h = 22f)
        items += Item(Kind.HEAD, "MUSIC AND SOUND", h = 24f)
        items += Item(Kind.LINE, "Synthesized note by note for this game", h = 24f)
        items += Item(Kind.GAP, h = 22f)
        items += Item(Kind.HEAD, "STARRING", h = 24f)
        items += Item(Kind.LINE, "Dot, a drop of ink", h = 24f)
        items += Item(Kind.NOTE, "and your S Pen", h = 26f)
        items += Item(Kind.GAP, h = 70f)
        items += Item(Kind.TITLE, "Thanks for playing!", h = 52f)
        var y = 0f
        for (it in items) {
            it.y = y
            y += it.h
        }
        height = y
        laidH = -1f
    }

    private fun centreOf(i: Int) = items[i].y + items[i].h / 2f

    fun layout(f: Frame) {
        colW = min(f.cardW, 360f)
        cx = f.wDp / 2f + (f.padL - f.padR) / 2f
        bottomPad = f.safeB + 24f + 44f + 16f
        button.set(cx - BUTTON_W / 2f, f.hDp - f.safeB - 24f - 44f, BUTTON_W, 44f)
        if (laidH == f.hDp && laidW == f.wDp) return
        laidH = f.hDp
        laidW = f.wDp
        top = f.safeT
        val visible = f.hDp - f.safeT - bottomPad
        val mid = f.safeT + visible / 2f
        if (finale) {
            // the epilogue (to the runner's lane) held in the middle, then the roll
            val epi = (items[0].y + items[2].y + items[2].h) / 2f
            s0 = epi - mid
            holdS = HOLD_S
        } else {
            s0 = items[0].y - (f.safeT + visible * 0.3f)
            holdS = 0.6f
        }
        sEnd = max(s0, centreOf(items.size - 1) - mid)
    }

    /** Seconds since it started (or the end, once skipped). */
    private fun t(now: Double): Float = if (!skippedAt.isNaN()) endT() + (now - skippedAt).toFloat() else (now - startAt).toFloat()

    private fun endT() = holdS + (sEnd - s0) / SPEED

    private fun scroll(t: Float) = if (t < holdS) s0 else min(sEnd, s0 + (t - holdS) * SPEED)

    fun finished(now: Double) = t(now) >= endT()

    fun skip(now: Double) {
        if (!finished(now)) skippedAt = now
    }

    fun targetAt(x: Float, y: Float): Any? = if (button.contains(x, y)) button else null

    private fun alphaAt(y: Float, f: Frame): Float {
        // fade in from the bottom edge and out at the top
        val fadeTop = f.safeT + 40f
        val fadeBottom = f.hDp - bottomPad
        return min(((y - f.safeT) / (fadeTop - f.safeT)).coerceIn(0f, 1f), ((fadeBottom - y) / 40f).coerceIn(0f, 1f))
    }

    fun drawShapes(shapes: Shapes, pal: Palette, f: Frame, now: Double, reduced: Boolean) {
        val t = t(now)
        val sc = scroll(t)
        for (it in items) {
            if (it.kind != Kind.RUNNER) continue
            val y = it.y - sc + it.h / 2f
            val a = alphaAt(y, f)
            if (a <= 0f) continue
            val x0 = cx - colW / 2f + 12f
            val x1 = cx + colW / 2f - 12f
            // the line draws itself just ahead of the runner, which rolls along it
            val p = if (reduced) 1f else ((t - 0.9f) / 3.6f).coerceIn(0f, 1f)
            val lead = min(1f, p + 0.12f)
            shapes.color(pal[Tok.ROPE], a)
            var px = x0
            var py = y + 18f
            val n = 24
            for (k in 1..n) {
                val u = k / n.toFloat()
                if (u > lead) break
                val x = x0 + (x1 - x0) * u
                val yy = y + 18f + 5f * sin(u * PI_F)
                shapes.capsule(px, py, x, yy, 2.8f)
                px = x; py = yy
            }
            val rx = x0 + (x1 - x0) * p
            val hop = if (p >= 1f && !reduced) kotlin.math.abs(sin((t - 4.5f) * 5f)) * 10f else 0f
            val ry = y + 18f + 5f * sin(p * PI_F) - 14f - hop
            // rolling it's thrilled; there it's pleased with itself, and says so
            val dt = if (lastNow.isNaN()) 0f else (now - lastNow).toFloat().coerceIn(0f, 0.1f)
            lastNow = now
            buddy.set(if (p < 1f) Expr.WHEE else if (t < 6.2f) Expr.PROUD else Expr.HAPPY)
            buddy.lookX = if (p < 1f) 1f else -0.4f
            buddy.lookY = if (p < 1f) 0.2f else 0.6f
            if (p >= 1f && !cheered) {
                cheered = true
                buddy.say("We did it!", force = true)
            }
            buddy.update(dt, still = reduced)
            val d = drawer ?: BuddyDrawer(shapes).also { drawer = it }
            d.draw(pal, buddy, rx, ry, 14f, 1f, 1f, a)
            bubbleTipX = rx
            bubbleTipY = ry - 14f * 1.5f
            // a few stars where it ends up
            if (p >= 1f) {
                for (k in 0 until 3) shapes.color(pal[Tok.STAR], a).star(x1 - 30f + k * 15f, y - 22f - (k % 2) * 6f, 6f)
            }
        }
        Widgets.button(shapes, pal, button, primary = finished(now), enabled = true, pressed = press.isPressed(button))
        val line = buddy.bubble
        if (line != null && finale && bubble.text == line) {
            // (laid out with the text pass's fonts: the bubble's text is drawn there)
            drawer?.bubbleShapes(pal, bubble, buddy.bubbleAlpha * alphaAt(bubbleTipY, f))
        }
    }

    /** Lays out the runner's bubble; call before [drawShapes] with the frame's [text]. */
    fun layoutBubble(text: Text, f: Frame) {
        val line = buddy.bubble ?: return
        bubble.place(text, fonts.get(Face.HAND_BOLD, 16f), line, bubbleTipX, bubbleTipY, 1f, 8f, f.wDp - 8f, f.safeT + 4f, buddy.bubbleScale)
    }

    fun drawText(text: Text, pal: Palette, f: Frame, now: Double) {
        val sc = scroll(t(now))
        val left = cx - colW / 2f
        val right = cx + colW / 2f
        for (it in items) {
            val y = it.y - sc
            if (y + it.h < f.safeT - 10f || y > f.hDp) continue
            val a = alphaAt(y + it.h / 2f, f)
            if (a <= 0f) continue
            when (it.kind) {
                Kind.TITLE -> text.line(fit(text, Face.HAND_BOLD, 44f, it.text), it.text, cx, y, it.h, pal[Tok.INK], a, Align.center)
                Kind.LINE -> text.line(fit(text, Face.UI, 16f, it.text), it.text, cx, y, it.h, pal[Tok.UI_FG], a, Align.center)
                Kind.NOTE -> text.line(fonts.get(Face.HAND, 20f), it.text, cx, y, it.h, pal[Tok.GRAPHITE], a, Align.center)
                Kind.HEAD -> text.line(fonts.get(Face.MONO_BOLD, 11f), it.text, cx, y, it.h, pal[Tok.ACCENT], a, Align.center)
                Kind.ROW -> {
                    text.line(fonts.get(Face.MONO_MEDIUM, 12f), it.text, left, y, it.h, pal[Tok.MUTED], a)
                    text.line(fonts.get(Face.MONO_BOLD, 15f), it.value, right, y, it.h, pal[Tok.INK], a, Align.right)
                }
                else -> Unit
            }
        }
        if (buddy.bubble != null && finale && bubble.text == buddy.bubble) bubble.drawText(text, fonts.get(Face.HAND_BOLD, 16f), pal, buddy.bubbleAlpha * alphaAt(bubbleTipY, f))
        val done = finished(now)
        Widgets.buttonLabel(text, fonts.get(Face.UI_BOLD, 14f), pal, button, if (done) "Back to the title" else "Skip", primary = done, enabled = true)
    }

    /** [face] at [size], or smaller so [s] fits the column. */
    private fun fit(text: Text, face: Face, size: Float, s: String): BitmapFont {
        val font = fonts.get(face, size)
        val w = text.width(font, s)
        return if (w <= colW) font else fonts.get(face, kotlin.math.floor(size * colW / w))
    }

    companion object {
        /** Who made it, for the credits. */
        const val AUTHOR = "Pranshu Parmar"
        /** The epilogue stays this long before the roll; the roll's speed in dp a second. */
        private const val HOLD_S = 5.5f
        private const val SPEED = 42f
        private const val BUTTON_W = 200f
        private const val PI_F = kotlin.math.PI.toFloat()
    }
}
