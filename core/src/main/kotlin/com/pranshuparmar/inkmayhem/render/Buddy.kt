package com.pranshuparmar.inkmayhem.render

import com.badlogic.gdx.utils.Align
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The runner as a character: a blot of ink with big googly eyes on top, eyebrows floating over
 * them and a mouth, all upright whatever way it rolls. It is drawn the same way in the stage, on
 * the title screen, on the pause screen and in the ending; only what drives it differs. Nothing
 * here feeds the simulation: it is looks only.
 */
enum class Expr {
    /** Rolling along, content. */
    CALM,
    /** A grin and rosy cheeks: a clean bridge, a star, a combo. */
    HAPPY,
    /** In the air off a spring: mouth wide open, brows up. */
    WHEE,
    /** An edge ahead with nothing drawn: brows up in the middle, a little "o". */
    WORRIED,
    /** A boulder or the tide behind: wide eyes, a wobbly mouth and a bead of sweat. */
    SCARED,
    /** Just after a big bounce: spinning eyes. */
    DIZZY,
    /** Hurt: crossed-out eyes. */
    OUCH,
    /** A boss, or a beam underfoot: brows down, teeth set. */
    DETERMINED,
    /** Watching the pen draw, or waiting at something new: one brow up. */
    CURIOUS,
    /** Kept waiting a long while: drooping lids and z's. */
    SLEEPY,
    /** Just squashed something: a smirk and a wink. */
    PROUD,
    /** On rope: a wavy mouth, eyes darting. */
    WOBBLY,
}

/** Everything about the face that eases from frame to frame, and what it's saying. */
class BuddyState {
    var expr = Expr.CALM
        private set
    private var exprAt = 0f
    var t = 0f
        private set

    // the face's shape, eased towards the expression's
    var browTilt = 0f; private set
    var browLift = 0f; private set
    var browAsym = 0f; private set
    var lid = 0f; private set
    var wink = 0f; private set
    // googly pupils: offset and velocity, in units of how far a pupil can move
    var pupX = 0.6f; private set
    var pupY = 0f; private set
    private var pvx = 0f
    private var pvy = 0f
    /** Where it looks, as a direction (it's normalised when used). */
    var lookX = 1f
    var lookY = 0.1f
    /** The body's squash (+ wide) and stretch (− tall), eased. */
    var squash = 0f; private set
    private var blinkIn = 2.5f
    var blink = 0f; private set

    var bubble: String? = null
        private set
    var bubbleAt = 0f
        private set
    var bubbleFor = 0f
        private set
    private var bubbleCool = 0f

    fun set(e: Expr) {
        if (e == expr) return
        expr = e
        exprAt = t
    }

    /** Seconds in the current expression. */
    val inExpr: Float get() = t - exprAt

    /**
     * A remark in a speech bubble, unless one was said lately ([force]: say it anyway). Returns
     * whether it's shown.
     */
    fun say(line: String, force: Boolean = false, cooldown: Float = 4.5f): Boolean {
        if (!force && bubbleCool > 0f) return false
        bubble = line
        bubbleAt = t
        bubbleFor = 1.5f + 0.04f * line.length
        bubbleCool = cooldown
        return true
    }

    fun hush() {
        bubble = null
    }

    /** How visible the bubble is (it pops in and fades out), 0 when there's none. */
    val bubbleAlpha: Float
        get() {
            if (bubble == null) return 0f
            val age = t - bubbleAt
            return min(min(1f, age / 0.12f), ((bubbleFor - age) / 0.25f).coerceIn(0f, 1f))
        }

    /** The pop as it appears: a little overshoot. */
    val bubbleScale: Float
        get() {
            val age = t - bubbleAt
            return if (age >= 0.2f) 1f else 0.7f + 0.3f * (age / 0.2f) + 0.12f * sin(age / 0.2f * PI_F)
        }

    /**
     * Steps the face by [dt]: eases it to its expression, blinks, and moves the pupils on a
     * spring towards where it looks, jolted by the body's acceleration ([ax], [ay], m/s²; 0 in
     * the menus) so they wobble like googly eyes. [still]: reduced motion (no wobble or bob).
     */
    fun update(dt: Float, ax: Float = 0f, ay: Float = 0f, still: Boolean = false, impact: Float = 0f) {
        t += dt
        bubbleCool = max(0f, bubbleCool - dt)
        if (bubble != null && t - bubbleAt > bubbleFor) bubble = null
        val k = 1f - kotlin.math.exp(-dt / 0.08f)
        val target = SHAPES[expr.ordinal]
        browTilt += (target[0] - browTilt) * k
        browLift += (target[1] - browLift) * k
        browAsym += (target[2] - browAsym) * k
        lid += (target[3] - lid) * k
        val wantWink = if (expr == Expr.PROUD && inExpr < 0.9f) 1f else 0f
        wink += (wantWink - wink) * min(1f, dt / 0.06f)
        // a blink every few seconds
        blinkIn -= dt
        if (blinkIn <= 0f) {
            blinkIn = 2.2f + ((t * 7.31f) % 1f) * 3.2f
            blink = 1f
        }
        blink = max(0f, blink - dt / 0.16f)
        // pupils: a spring to the look direction, kicked by the body's acceleration
        val l = max(0.001f, hypot(lookX, lookY))
        val tx = lookX / l * 0.85f
        val ty = lookY / l * 0.85f
        if (still) {
            pupX = tx; pupY = ty; pvx = 0f; pvy = 0f
        } else {
            val fx = 140f * (tx - pupX) - 9f * pvx - ax * 0.35f
            val fy = 140f * (ty - pupY) - 9f * pvy - ay * 0.35f
            pvx += fx * dt
            pvy += fy * dt
            pupX += pvx * dt
            pupY += pvy * dt
            val m = hypot(pupX, pupY)
            if (m > 1f) {
                // the pupil hits the rim and rolls round it
                pupX /= m; pupY /= m
                val dot = pvx * pupX + pvy * pupY
                if (dot > 0f) { pvx -= dot * pupX * 1.6f; pvy -= dot * pupY * 1.6f }
            }
        }
        // the body: a landing or bounce squashes it, eased back
        squash = max(squash * kotlin.math.exp(-dt / 0.12f), impact)
    }

    companion object {
        private const val PI_F = kotlin.math.PI.toFloat()

        /** Per expression: brow tilt (+ cross, − worried), brow lift, brow asymmetry, lids (0 open, 1 shut). */
        private val SHAPES = arrayOf(
            floatArrayOf(0f, 0f, 0f, 0.05f), // CALM
            floatArrayOf(-0.2f, 0.6f, 0f, 0f), // HAPPY
            floatArrayOf(-0.3f, 1f, 0f, 0f), // WHEE
            floatArrayOf(-0.9f, 0.5f, 0f, 0f), // WORRIED
            floatArrayOf(-0.8f, 0.9f, 0f, 0f), // SCARED
            floatArrayOf(0.2f, 0.2f, 0.4f, 0f), // DIZZY
            floatArrayOf(0.6f, -0.2f, 0f, 0f), // OUCH
            floatArrayOf(0.9f, -0.4f, 0f, 0.15f), // DETERMINED
            floatArrayOf(0f, 0.3f, 0.9f, 0f), // CURIOUS
            floatArrayOf(-0.2f, -0.2f, 0f, 0.6f), // SLEEPY
            floatArrayOf(0.3f, 0.2f, -0.6f, 0.1f), // PROUD
            floatArrayOf(-0.5f, 0.4f, 0.3f, 0f), // WOBBLY
        )
    }
}

/**
 * Draws the character. Works in any units: [r] is the body's radius and [lw] a hairline in the
 * same units. [dir] is the way it faces (+1 right).
 */
class BuddyDrawer(private val shapes: Shapes) {
    private val drop = FloatArray(DROP.size)

    fun draw(
        pal: Palette, s: BuddyState, cx: Float, cy: Float, r: Float, dir: Float, lw: Float, a: Float = 1f,
        extraSquash: Float = 0f, stretch: Float = 0f, bold: Boolean = false,
    ) {
        val sq = (s.squash + extraSquash).coerceIn(0f, 0.6f)
        val sx = 1f + sq * 0.28f - stretch * 0.18f
        val sy = 1f - sq * 0.28f + stretch * 0.22f
        // the body sits on its base: squashing lowers its top, not its bottom
        val by = cy + r * (1f - sy)
        val ink = pal[Tok.INK]
        if (bold) shapes.color(pal[Tok.ACCENT], a).ellipse(cx, by, r * sx + 2.4f * lw, r * sy + 2.4f * lw)
        shapes.color(ink, a).ellipse(cx, by, r * sx, r * sy)
        // a little ink pooled at the base, so it reads as a blot rather than a ball
        shapes.color(ink, a).ellipse(cx, by + r * sy * 0.82f, r * sx * 0.78f, r * sy * 0.22f)

        val fx = cx + dir * r * 0.14f * sx
        val ey = by - r * 0.5f * sy
        val er = r * 0.37f
        val gap = r * 0.39f * sx
        val lx = fx - gap
        val rx = fx + gap
        drawEyes(pal, s, lx, rx, ey, er, r, dir, lw, a)
        drawBrows(pal, s, lx, rx, by - r * sy - r * 0.16f, r, dir, lw, a)
        drawMouth(pal, s, fx, by + r * 0.24f * sy, r, dir, lw, a)
        when (s.expr) {
            Expr.HAPPY, Expr.WHEE, Expr.PROUD -> {
                // rosy cheeks
                shapes.color(pal[Tok.ERASER], 0.55f * a)
                shapes.ellipse(lx - r * 0.12f, by + r * 0.1f * sy, r * 0.11f, r * 0.07f)
                shapes.ellipse(rx + r * 0.12f, by + r * 0.1f * sy, r * 0.11f, r * 0.07f)
            }
            Expr.SCARED -> {
                // a bead of sweat sliding off the back of its head
                val d = r * 0.16f
                val sxp = cx - dir * r * 0.78f
                val syp = by - r * 0.55f + (s.t * 1.6f % 1f) * r * 0.35f
                for (i in DROP.indices step 2) {
                    drop[i] = sxp + DROP[i] * d
                    drop[i + 1] = syp + DROP[i + 1] * d
                }
                shapes.color(pal[Tok.DROP], a).fan(drop, DROP.size / 2)
            }
            Expr.SLEEPY -> {
                // z's drifting up behind it
                shapes.color(pal[Tok.GRAPHITE], a)
                for (k in 0 until 2) {
                    val u = (s.t * 0.6f + k * 0.5f) % 1f
                    val z = r * (0.18f + 0.08f * k)
                    val zx = cx - dir * r * (0.8f + u * 0.5f)
                    val zy = by - r * (1.3f + u * 0.9f)
                    val za = a * (1f - u)
                    shapes.color(pal[Tok.GRAPHITE], za)
                    shapes.line(zx - z, zy - z, zx + z, zy - z, lw * 1.3f)
                    shapes.line(zx + z, zy - z, zx - z, zy + z, lw * 1.3f)
                    shapes.line(zx - z, zy + z, zx + z, zy + z, lw * 1.3f)
                }
            }
            else -> Unit
        }
    }

    private fun drawEyes(pal: Palette, s: BuddyState, lx: Float, rx: Float, ey: Float, er: Float, r: Float, dir: Float, lw: Float, a: Float) {
        // eyes are white with black pupils on any page; the rim (the page's colour) keeps them
        // apart from a pale body on a dark page
        val white = EYE
        val ink = PUPIL
        val rim = pal[Tok.PAPER]
        val mark = pal[Tok.PAPER]
        val thick = max(lw * 1.3f, r * 0.07f)
        when (s.expr) {
            Expr.OUCH -> {
                val e = er * 0.6f
                shapes.color(mark, a)
                for (side in 0..1) {
                    val x = if (side == 0) lx else rx
                    shapes.capsule(x - e, ey - e, x + e, ey + e, thick)
                    shapes.capsule(x - e, ey + e, x + e, ey - e, thick)
                }
                return
            }
            Expr.DIZZY -> {
                for (side in 0..1) {
                    val x = if (side == 0) lx else rx
                    shapes.color(rim, a).circle(x, ey, er + lw)
                    shapes.color(white, a).circle(x, ey, er)
                    val t = s.t * 9f
                    shapes.color(ink, a)
                    shapes.arc(x, ey, er * 0.62f, thick * 0.8f, t, t + 4.4f)
                    shapes.arc(x, ey, er * 0.28f, thick * 0.8f, t + 2f, t + 6f)
                }
                return
            }
            else -> Unit
        }
        val pr = er * when (s.expr) {
            Expr.SCARED -> 0.32f
            Expr.WHEE, Expr.HAPPY -> 0.52f
            else -> 0.46f
        }
        val reach = er - pr - lw * 0.5f
        for (side in 0..1) {
            val x = if (side == 0) lx else rx
            // the eye nearer the back winks when it's proud
            val winking = s.wink > 0.5f && ((side == 0) == (dir > 0))
            val shut = max(s.blink, s.lid).let { if (winking) 1f else it }
            if (shut > 0.85f) {
                // closed: a curve, smiling if pleased
                val up = s.expr == Expr.HAPPY || s.expr == Expr.PROUD
                shapes.color(mark, a)
                if (up) shapes.arc(x, ey + er * 0.35f, er * 0.7f, thick, PI_F + 0.4f, 2f * PI_F - 0.4f)
                else shapes.capsule(x - er * 0.75f, ey + er * 0.1f, x + er * 0.75f, ey + er * 0.1f, thick)
                continue
            }
            val open = 1f - shut
            val h = er * max(0.18f, open)
            val cyEye = ey + (er - h)
            shapes.color(rim, a).ellipse(x, cyEye, er + lw, h + lw)
            shapes.color(white, a).ellipse(x, cyEye, er, h)
            val px = x + s.pupX * reach
            val py = cyEye + s.pupY * reach * (h / er)
            shapes.color(ink, a).ellipse(px, py, pr, pr * max(0.4f, h / er))
            // a glint, fixed to the light (top left), never turning with the roll
            shapes.color(white, a).circle(px - pr * 0.35f, py - pr * 0.35f * (h / er), pr * 0.3f)
        }
    }

    private fun drawBrows(pal: Palette, s: BuddyState, lx: Float, rx: Float, baseY: Float, r: Float, dir: Float, lw: Float, a: Float) {
        val len = r * 0.36f
        val thick = max(lw * 1.6f, r * 0.13f)
        val y = baseY - s.browLift * r * 0.14f
        shapes.color(pal[Tok.INK], a)
        for (side in 0..1) {
            val x = if (side == 0) lx else rx
            // the inner end is the one nearer the middle of the face
            val innerRight = side == 0
            val tilt = s.browTilt * r * 0.13f
            // one brow cocked: the one at the front
            val asym = if ((side == 1) == (dir > 0)) -s.browAsym * r * 0.16f else 0f
            val xi = if (innerRight) x + len / 2f else x - len / 2f
            val xo = if (innerRight) x - len / 2f else x + len / 2f
            shapes.capsule(xo, y - tilt + asym, xi, y + tilt + asym, thick)
        }
    }

    private fun drawMouth(pal: Palette, s: BuddyState, mx: Float, my: Float, r: Float, dir: Float, lw: Float, a: Float) {
        val w = max(lw * 1.3f, r * 0.1f)
        // the page's colour: light on the dark blot, dark on a pale one
        val white = pal[Tok.PAPER]
        // the mouth is drawn a third bigger than the face's other parts, to read at play size
        val m = r * 1.35f
        shapes.color(white, a)
        when (s.expr) {
            Expr.CALM -> shapes.arc(mx, my - m * 0.12f, m * 0.2f, w, 0.55f, PI_F - 0.55f)
            Expr.HAPPY -> shapes.pie(mx, my - m * 0.06f, m * 0.22f, 0f, 180f)
            Expr.WHEE -> {
                val open = 0.75f + 0.25f * sin(s.t * 14f)
                shapes.ellipse(mx, my, m * 0.15f, m * 0.17f * open)
                shapes.color(pal[Tok.BEAM], a).ellipse(mx, my + m * 0.08f * open, m * 0.09f, m * 0.06f * open)
            }
            Expr.WORRIED -> shapes.ring(mx, my, m * 0.07f, w)
            Expr.SCARED -> shapes.zigzag(mx - m * 0.17f, my, mx + m * 0.17f, my, 5, m * 0.04f, w)
            Expr.DIZZY -> shapes.zigzag(mx - m * 0.15f, my, mx + m * 0.15f, my, 4, m * 0.035f, w)
            Expr.OUCH -> shapes.ellipse(mx, my + m * 0.02f, m * 0.09f, m * 0.12f)
            Expr.DETERMINED -> {
                shapes.roundRect(mx - m * 0.17f, my - m * 0.06f, m * 0.34f, m * 0.12f, m * 0.04f)
                shapes.color(pal[Tok.INK], a).rect(mx - m * 0.15f, my - w * 0.25f, m * 0.3f, w * 0.5f)
            }
            Expr.CURIOUS -> shapes.capsule(mx - m * 0.06f, my, mx + m * 0.08f, my - m * 0.02f, w)
            Expr.SLEEPY -> shapes.ellipse(mx, my, m * 0.06f, m * 0.04f + m * 0.02f * sin(s.t * 2f))
            Expr.PROUD -> shapes.arc(mx + dir * m * 0.05f, my - m * 0.14f, m * 0.2f, w, if (dir > 0) 0.3f else 1.2f, if (dir > 0) PI_F - 1.2f else PI_F - 0.3f)
            Expr.WOBBLY -> {
                val ph = s.t * 10f
                shapes.zigzag(mx - m * 0.16f, my + m * 0.02f * sin(ph), mx + m * 0.16f, my - m * 0.02f * sin(ph), 4, m * 0.03f, w)
            }
        }
    }

    /**
     * A speech bubble in screen units (dp) over a point at ([tx], [ty]): a rounded card with a
     * tail, behind the character's facing so it doesn't hide what's ahead. Shapes only; the
     * text goes on with [bubbleText] at the same place.
     */
    fun bubbleShapes(pal: Palette, b: BubbleLayout, a: Float) {
        if (a <= 0f || b.w <= 0f) return
        val s = b.scale
        val w = b.w * s
        val h = b.h * s
        val x = b.cx - w / 2f
        val y = b.bottom - h
        shapes.color(pal[Tok.SHADOW], 0.5f * a).roundRect(x + 1.5f, y + 3f, w, h, 12f * s)
        shapes.color(pal[Tok.INK], a).roundRect(x - 1.5f, y - 1.5f, w + 3f, h + 3f, 13.5f * s)
        shapes.color(pal[Tok.UI], a).roundRect(x, y, w, h, 12f * s)
        // the tail, pointing down at the character
        val tx = b.tailX.coerceIn(x + 14f, max(x + 14f, x + w - 14f))
        shapes.color(pal[Tok.INK], a).triangle(tx - 8f, b.bottom - 1f, tx + 2f, b.bottom - 1f, b.tipX, b.tipY + 1.5f)
        shapes.color(pal[Tok.UI], a).triangle(tx - 6f, b.bottom - 2.5f, tx + 0.5f, b.bottom - 2.5f, b.tipX, b.tipY - 1f)
    }

    companion object {
        private const val PI_F = kotlin.math.PI.toFloat()
        private val EYE = com.badlogic.gdx.graphics.Color(1f, 1f, 1f, 1f)
        private val PUPIL = com.badlogic.gdx.graphics.Color.valueOf("141824")
        /** A teardrop, point up, unit size. */
        private val DROP = run {
            val pts = ArrayList<Float>()
            pts += 0f; pts += 0.2f
            for (i in 0..12) {
                val a = -0.6f + i * (PI_F + 1.2f) / 12f
                pts += cos(a) * 0.55f; pts += 0.45f + sin(a) * 0.55f
            }
            pts += 0f; pts += -1f
            pts.toFloatArray()
        }
    }
}

/** Where a speech bubble goes on screen (dp), laid out from its text. */
class BubbleLayout {
    var cx = 0f
    var bottom = 0f
    var w = 0f
    var h = 0f
    var tailX = 0f
    var tipX = 0f
    var tipY = 0f
    var scale = 1f
    var text = ""

    /**
     * Over a character whose head top is at ([hx], [hy]) facing [dir]: the bubble leans back
     * from its facing, kept inside [minX]..[maxX] and below [minY].
     */
    fun place(text: Text, font: com.badlogic.gdx.graphics.g2d.BitmapFont, line: String, hx: Float, hy: Float, dir: Float, minX: Float, maxX: Float, minY: Float, scale: Float) {
        this.text = line
        this.scale = scale
        w = text.width(font, line) + 22f
        h = font.capHeight + 18f
        tipX = hx
        tipY = hy - 4f
        bottom = max(minY + h, hy - 12f)
        cx = (hx - dir * (w / 2f - 22f)).coerceIn(minX + w / 2f, max(minX + w / 2f, maxX - w / 2f))
        tailX = hx - dir * 6f
    }

    fun drawText(text: Text, font: com.badlogic.gdx.graphics.g2d.BitmapFont, pal: Palette, a: Float) {
        if (a <= 0f || scale < 0.85f) return
        text.line(font, this.text, cx, bottom - h, h, pal[Tok.UI_FG], a, Align.center)
    }
}
