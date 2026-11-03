package com.pranshuparmar.inkmayhem.ui

import com.badlogic.gdx.utils.Align
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
 * Dot's speech balloon in [b]: a rounded card outlined in ink, its tail at height [tailY]
 * reaching to [tipX] (Dot's edge), on its left side when [tailLeft].
 */
internal fun balloonShapes(shapes: Shapes, pal: Palette, b: Box, a: Float, tailLeft: Boolean, tailY: Float, tipX: Float, tailA: Float = a) {
    shapes.color(pal[Tok.SHADOW], 0.5f * a).roundRect(b.x + 2f, b.y + 4f, b.w, b.h, 16f)
    shapes.color(pal[Tok.INK], a).roundRect(b.x - 2f, b.y - 2f, b.w + 4f, b.h + 4f, 18f)
    shapes.color(pal[Tok.UI], a).roundRect(b.x, b.y, b.w, b.h, 16f)
    if (tailA <= 0f) return
    if (tailLeft) {
        shapes.color(pal[Tok.INK], tailA).triangle(b.x - 1f, tailY - 10f, b.x - 1f, tailY + 8f, tipX + 4f, tailY)
        shapes.color(pal[Tok.UI], tailA).triangle(b.x + 1.5f, tailY - 7.5f, b.x + 1.5f, tailY + 5.5f, tipX + 8f, tailY)
    } else {
        shapes.color(pal[Tok.INK], tailA).triangle(b.right + 1f, tailY - 10f, b.right + 1f, tailY + 8f, tipX - 4f, tailY)
        shapes.color(pal[Tok.UI], tailA).triangle(b.right - 1.5f, tailY - 7.5f, b.right - 1.5f, tailY + 5.5f, tipX - 8f, tailY)
    }
}

/**
 * Dot, stepping out of the page to help: the runner pops up off the paper towards the player
 * (it grows, and its shadow falls on the page below it), settles in a top corner clear of where
 * the player has to draw, and explains in a speech balloon with a dotted pointer to what it
 * means. It goes back in (shrinking into the runner with a splash) when the player starts
 * drawing, taps "Got it", or the moment passes. It also knocks on the glass when the player has
 * left it waiting, and introduces itself on the very first launch.
 */
class DotHelper(private val fonts: Fonts) {
    /** One balloonful: an optional handwritten note, what Dot says, its face, and its button. */
    class Page(val note: String?, val say: String, val expr: Expr, val button: String = "Got it", val lookAtPlayer: Boolean = false)

    private enum class Mode { HIDDEN, OUT, IN }

    private var mode = Mode.HIDDEN
    private var modeAt = 0.0
    private var pages: List<Page> = emptyList()
    private var page = 0
    /** A welcome: every tap moves it on (nothing behind it takes taps). */
    var modal = false
        private set
    /** Where it came out of (the runner on screen) and how big that was, in dp. */
    private var fromX = 0f
    private var fromY = 0f
    private var fromR = 8f
    /** What it points at on screen (NaN: nothing). */
    var targetX = Float.NaN
    var targetY = Float.NaN
    private var knockAt = -10.0
    private var lastNow = Double.NaN

    private val face = BuddyState()
    private var drawer: BuddyDrawer? = null
    val button = Box()
    val balloon = Box()
    private val dot = Box()
    val press = Press()
    private var seatX = 0f
    private var seatY = 0f
    private var onLeft = true
    private var noteH = 0f
    private var sayH = 0f

    /** Called when the last page is done with (by its button, or by drawing). */
    var onFinished: (() -> Unit)? = null
    /** Dot has just landed back in the runner: the game splashes ink there. */
    var landed = false
        private set

    /** Out (or on its way out) and taking taps. */
    val open: Boolean get() = mode == Mode.OUT
    /** Drawn at all (out, or diving back in). */
    val visible: Boolean get() = mode != Mode.HIDDEN

    /**
     * Steps out of the runner at ([x], [y], radius [r] on screen) with [pages]; already out, it
     * just changes what it says.
     */
    fun show(now: Double, pages: List<Page>, x: Float, y: Float, r: Float, modal: Boolean = false) {
        this.pages = pages
        page = 0
        this.modal = modal
        press.cancel()
        fromX = x; fromY = y; fromR = r
        if (mode != Mode.OUT) {
            mode = Mode.OUT
            modeAt = now
        }
        face.hush()
        face.set(pages[0].expr)
    }

    /** Knocks twice on the glass, to get the player's attention. */
    fun knock(now: Double) {
        knockAt = now
    }

    /** Goes back into the runner. */
    fun dismiss(now: Double) {
        if (mode != Mode.OUT) return
        mode = Mode.IN
        modeAt = now
        press.cancel()
        onFinished?.invoke()
        onFinished = null
    }

    /** The next page, or back in after the last. */
    fun next(now: Double) {
        if (page < pages.size - 1) {
            page++
            face.set(pages[page].expr)
        } else {
            dismiss(now)
        }
    }

    /** Where the runner is now (so it dives back into the right place). */
    fun follow(x: Float, y: Float, r: Float) {
        fromX = x; fromY = y; fromR = r
    }

    /** The button, Dot itself, or the balloon (which just swallows the tap). */
    fun targetAt(x: Float, y: Float): Any? = when {
        !open -> null
        button.contains(x, y) -> button
        dot.contains(x, y) -> dot
        balloon.contains(x, y) -> balloon
        else -> null
    }

    private val outS = 0.45
    private val inS = 0.35

    /** 0 → 1 on the way out, 1 → 0 on the way in. */
    private fun lift(now: Double): Float {
        val t = (now - modeAt).toFloat()
        return when (mode) {
            Mode.OUT -> ease((t / outS.toFloat()).coerceIn(0f, 1f))
            Mode.IN -> 1f - ease((t / inS.toFloat()).coerceIn(0f, 1f))
            Mode.HIDDEN -> 0f
        }
    }

    private fun ease(u: Float) = 1f - (1f - u) * (1f - u) * (1f - u)

    /**
     * Lays out the seat and the balloon: the top corner whose balloon stays clear of the target
     * (the guide to draw along), below the HUD.
     */
    fun layout(text: Text, f: Frame, hudBottom: Float) {
        if (!visible) return
        val p = pages.getOrNull(page) ?: return
        val maxW = min(340f, f.wDp - f.padL - f.padR - R * 2f - 18f)
        val noteFont = fonts.get(Face.HAND, 17f)
        val sayFont = fonts.get(Face.UI_BOLD, 15f)
        val inner = maxW - 28f
        noteH = if (p.note != null) measure(text, noteFont, p.note, inner) + 6f else 0f
        sayH = measure(text, sayFont, p.say, inner)
        val bw = maxW
        val bh = 14f + noteH + sayH + 12f + 36f + 12f
        val top = max(hudBottom + 8f, f.safeT + 8f)
        // the corner away from what it points at
        onLeft = targetX.isNaN() || targetX > f.wDp * 0.45f ||
            !overlaps(f.padL + R * 2f + 18f, top, bw, bh, targetX, targetY)
        if (!onLeft && overlaps(f.wDp - f.padR - R * 2f - 18f - bw, top, bw, bh, targetX, targetY)) onLeft = true
        if (onLeft) {
            seatX = f.padL + R
            balloon.set(seatX + R + 18f, top, bw, bh)
        } else {
            seatX = f.wDp - f.padR - R
            balloon.set(seatX - R - 18f - bw, top, bw, bh)
        }
        seatY = top + R + 6f
        button.set(balloon.right - 14f - BUTTON_W, balloon.bottom - 12f - 36f, BUTTON_W, 36f)
        dot.set(seatX - R, seatY - R * 1.3f, R * 2f, R * 2.4f)
    }

    private fun overlaps(x: Float, y: Float, w: Float, h: Float, tx: Float, ty: Float): Boolean =
        !tx.isNaN() && tx > x - 40f && tx < x + w + 40f && ty > y - 40f && ty < y + h + 40f

    private fun measure(text: Text, font: com.badlogic.gdx.graphics.g2d.BitmapFont, s: String, w: Float): Float {
        text.layout.setText(font, s, 0, s.length, com.badlogic.gdx.graphics.Color.WHITE, w, Align.left, true, null)
        return text.layout.height
    }

    fun drawShapes(shapes: Shapes, pal: Palette, f: Frame, now: Double, reduced: Boolean) {
        if (!visible) return
        val dt = if (lastNow.isNaN()) 0f else (now - lastNow).toFloat().coerceIn(0f, 0.1f)
        lastNow = now
        val u = if (reduced && mode == Mode.OUT) 1f else lift(now)
        if (mode == Mode.IN && u <= 0f) {
            mode = Mode.HIDDEN
            landed = true
            return
        }
        val p = pages.getOrNull(page)
        // a welcome has the stage to itself: what's behind fades back
        if (modal) shapes.color(pal[Tok.PAPER], 0.7f * u).rect(0f, 0f, f.wDp, f.hDp)
        // the knock: it leans in at the glass twice
        val k = (now - knockAt).toFloat()
        val knock = if (k in 0f..0.6f && !reduced) kotlin.math.abs(sin(k / 0.3f * PI_F)) else 0f
        val r = fromR + (R - fromR) * u
        val arc = if (reduced) 0f else 60f * sin(u * PI_F) * if (mode == Mode.IN) 0.6f else 1f
        val x = fromX + (seatX - fromX) * u
        val y = fromY + (seatY - fromY) * u - arc
        val scale = 1f + 0.14f * knock
        // its shadow falls on the page below it: the higher it's lifted, the further and softer
        if (u > 0.02f) {
            shapes.color(pal[Tok.SHADOW], 0.5f * u).ellipse(x + 10f * u, y + r * 1.15f + 16f * u, r * (0.9f + 0.3f * u), r * 0.28f)
        }
        // looking at the player (out of the screen), or at what it's on about
        if (p != null && (p.lookAtPlayer || targetX.isNaN())) {
            face.lookX = 0f
            face.lookY = 0.05f
        } else {
            face.lookX = targetX - x
            face.lookY = (targetY - y) * 0.8f
        }
        face.update(dt, still = reduced)
        val d = drawer ?: BuddyDrawer(shapes).also { drawer = it }
        d.draw(pal, face, x, y, r * scale, if (onLeft) 1f else -1f, max(1f, r / 30f))
        if (knock > 0f) {
            // the knock: little impact lines towards the player
            shapes.color(pal[Tok.INK], knock)
            for (i in -1..1) {
                val a = -PI_F / 2f + i * 0.5f + if (onLeft) 0.6f else -0.6f
                val r0 = r * 1.35f
                val r1 = r * 1.7f
                shapes.capsule(x + kotlin.math.cos(a) * r0, y + kotlin.math.sin(a) * r0, x + kotlin.math.cos(a) * r1, y + kotlin.math.sin(a) * r1, 2.2f)
            }
        }
        if (mode != Mode.OUT || p == null) return
        val ba = ((now - modeAt - 0.25).toFloat() / 0.2f).coerceIn(0f, 1f)
        if (ba <= 0f) return
        // the pointer: dots from the balloon to what it means
        if (!targetX.isNaN()) {
            val sx = if (onLeft) balloon.x + balloon.w * 0.7f else balloon.x + balloon.w * 0.3f
            val sy = balloon.bottom
            val dx = targetX - sx
            val dy = targetY - 14f - sy
            val len = kotlin.math.hypot(dx, dy)
            if (len > 30f) {
                shapes.color(pal[Tok.ACCENT], ba)
                var s = 10f
                while (s < len - 10f) {
                    shapes.circle(sx + dx * s / len, sy + dy * s / len, 2.2f)
                    s += 9f
                }
                val ax = targetX - dx / len * 14f
                val ay = targetY - 14f - dy / len * 14f
                val nx = -dy / len
                val ny = dx / len
                shapes.triangle(targetX - dx / len * 4f, targetY - 14f - dy / len * 4f, ax + nx * 6f, ay + ny * 6f, ax - nx * 6f, ay - ny * 6f)
            }
        }
        // the balloon, its tail to Dot
        balloonShapes(shapes, pal, balloon, ba, onLeft, seatY - 4f, if (onLeft) seatX + R else seatX - R)
        Widgets.button(shapes, pal, button, primary = true, enabled = true, pressed = press.isPressed(button))
    }

    fun drawText(text: Text, pal: Palette, now: Double) {
        if (mode != Mode.OUT) return
        val p = pages.getOrNull(page) ?: return
        val ba = ((now - modeAt - 0.25).toFloat() / 0.2f).coerceIn(0f, 1f)
        if (ba <= 0f) return
        val x = balloon.x + 14f
        val w = balloon.w - 28f
        var y = balloon.y + 14f
        if (p.note != null) {
            val nf = fonts.get(Face.HAND, 17f)
            text.draw(nf, p.note, x, y, pal[Tok.GRAPHITE], ba, Align.left, w)
            y += noteH
        }
        text.draw(fonts.get(Face.UI_BOLD, 15f), p.say, x, y, pal[Tok.UI_FG], ba, Align.left, w)
        Widgets.buttonLabel(text, fonts.get(Face.UI_BOLD, 14f), pal, button, p.button, primary = true, enabled = true)
        // its name on the first page of a welcome
        if (modal && page == 0) {
            text.line(fonts.get(Face.MONO_BOLD, 10.5f), "DOT", seatX, seatY + R * 1.25f, 14f, pal[Tok.MUTED], ba, Align.center)
        }
    }

    /** Clears [landed] once the game has splashed. */
    fun consumeLanded(): Boolean {
        val l = landed
        landed = false
        return l
    }

    /** Gone at once (the stage ended, a death). */
    fun hide() {
        mode = Mode.HIDDEN
        press.cancel()
        onFinished = null
    }

    companion object {
        /** Dot's radius once it's out, in dp. */
        const val R = 30f
        private const val BUTTON_W = 96f
        private const val PI_F = kotlin.math.PI.toFloat()
    }
}
