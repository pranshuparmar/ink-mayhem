package com.pranshuparmar.inkmayhem.ui

import com.pranshuparmar.inkmayhem.game.Tutorial
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

/**
 * Dot presents the How to play lessons: it steps out of the lesson's runner (a plain dot is left
 * rolling) to the top of the screen, and the lesson card is its speech balloon: progress, the
 * lesson's title and instruction, a feedback line and a Skip button. Dot looks at the guide (or
 * at the pen while it draws), worries at a hint or a miss and is delighted when a lesson
 * passes. The game swallows pen-downs on the balloon and on Dot, so nobody draws under them.
 */
class TutorialCard(private val fonts: Fonts) {
    /** The balloon. */
    val box = Box()
    val skip = Box()
    /** Dot, beside the balloon. */
    val dotBox = Box()
    val press = Press()
    private val body = Flow(21f)
    private val feedback = Flow(18f)
    private var bodyFor: Tutorial.Lesson? = null
    private var feedbackFor: String? = null
    private var feedbackTone = Tok.MUTED
    private var stepText = ""
    /** The title's size: 28, smaller when a long title would run out of a narrow balloon. */
    private var titleSize = TITLE_SIZE

    private val face = BuddyState()
    private var drawer: BuddyDrawer? = null
    private var shownAt = Double.NaN
    private var lastNow = Double.NaN
    private var seatX = 0f
    private var seatY = 0f
    /** Where the lesson's runner is on screen (dp), for Dot to step out of. */
    var fromX = 0f
    var fromY = 0f
    var fromR = 8f
    /** What Dot looks at (the guide, or the pen's tip), on screen; NaN: the player. */
    var lookX = Float.NaN
    var lookY = Float.NaN
    private var lastLesson = -1
    private var passedAt = 0.0

    /** The tutorial has started: Dot steps out. */
    fun begin(now: Double) {
        shownAt = now
        lastLesson = -1
        face.hush()
    }

    fun layout(text: Text, f: Frame, t: Tutorial) {
        if (bodyFor != t.lesson) {
            bodyFor = t.lesson
            body.setText({ fonts.get(Face.UI, 14.5f) }, Tok.MUTED, t.lesson.body)
            stepText = "LESSON ${t.lessonIndex + 1} OF ${t.lessonCount}"
        }
        val msg = t.feedback.ifEmpty { t.lesson.waiting }
        val tone = if (t.feedback.isEmpty()) Tok.MUTED else if (t.good) Tok.ROPE else Tok.BEAM
        if (msg != feedbackFor || tone != feedbackTone) {
            feedbackFor = msg
            feedbackTone = tone
            val face = if (t.feedback.isEmpty()) Face.UI else Face.UI_BOLD
            feedback.setText({ fonts.get(face, 13.5f) }, tone, msg)
        }
        // Dot on the left, its balloon beside it
        val dotW = R * 2f + 18f
        val w = min(f.cardW, f.wDp - f.padL - f.padR - dotW)
        val x0 = max(f.padL, (f.wDp - dotW - w) / 2f + (f.padL - f.padR) / 2f)
        val inner = w - 32f
        titleSize = TITLE_SIZE
        while (titleSize > TITLE_MIN && text.width(fonts.get(Face.HAND_BOLD, titleSize), t.lesson.title) > inner) titleSize -= 2f
        feedback.layout(text, inner)
        val h = 14f + 16f + 4f + 32f + 6f + body.layout(text, inner) + 8f + FEEDBACK_H + 12f
        box.set(x0 + dotW, f.safeT + 10f, w, h)
        seatX = x0 + R
        seatY = box.y + R + 10f
        dotBox.set(seatX - R, seatY - R * 1.3f, R * 2f, R * 2.4f)
        val skipW = text.width(fonts.get(Face.UI_BOLD, 13f), SKIP) + 22f
        skip.set(box.right - 12f - skipW, box.y + 10f, skipW, 30f)
    }

    private fun feedbackTop() = box.bottom - 12f - FEEDBACK_H

    fun drawShapes(shapes: Shapes, pal: Palette, t: Tutorial, now: Double, reduced: Boolean) {
        if (shownAt.isNaN()) shownAt = now
        val dt = if (lastNow.isNaN()) 0f else (now - lastNow).toFloat().coerceIn(0f, 0.1f)
        lastNow = now
        if (t.phase == Tutorial.Phase.PASSED && lastLesson != t.lessonIndex) {
            lastLesson = t.lessonIndex
            passedAt = now
        }
        face.set(
            when (t.phase) {
                Tutorial.Phase.PASSED, Tutorial.Phase.FINISHED -> if (now - passedAt < 0.9) Expr.PROUD else Expr.HAPPY
                Tutorial.Phase.FAILED -> Expr.WORRIED
                Tutorial.Phase.TRYING -> if (t.feedback.isNotEmpty() && !t.good) Expr.WORRIED else Expr.CURIOUS
            },
        )
        // stepping out of the runner: rising, growing, its shadow on the page below
        val u = if (reduced) 1f else ease(((now - shownAt) / OUT_S).toFloat().coerceIn(0f, 1f))
        val r = fromR + (R - fromR) * u
        val x = fromX + (seatX - fromX) * u
        val y = fromY + (seatY - fromY) * u - if (reduced) 0f else 50f * kotlin.math.sin(u * PI_F)
        if (lookX.isNaN()) {
            face.lookX = 0f
            face.lookY = 0.05f
        } else {
            face.lookX = lookX - x
            face.lookY = (lookY - y) * 0.8f
        }
        face.update(dt, still = reduced)
        shapes.color(pal[Tok.SHADOW], 0.5f * u).ellipse(x + 10f * u, y + r * 1.15f + 16f * u, r * (0.9f + 0.3f * u), r * 0.28f)
        val d = drawer ?: BuddyDrawer(shapes).also { drawer = it }
        d.draw(pal, face, x, y, r, 1f, max(1f, r / 30f))
        // the balloon, once it's out
        val ba = if (reduced) 1f else (((now - shownAt) - 0.25).toFloat() / 0.2f).coerceIn(0f, 1f)
        if (ba <= 0f) return
        balloonShapes(shapes, pal, box, ba, tailLeft = true, tailY = seatY - 4f, tipX = seatX + R)
        Widgets.button(shapes, pal, skip, primary = false, enabled = true, pressed = press.isPressed(skip))
    }

    private fun ease(u: Float) = 1f - (1f - u) * (1f - u) * (1f - u)

    fun drawText(text: Text, pal: Palette, t: Tutorial, now: Double, reduced: Boolean) {
        val ba = if (reduced) 1f else (((now - shownAt) - 0.25).toFloat() / 0.2f).coerceIn(0f, 1f)
        if (ba <= 0f) return
        val x = box.x + 16f
        var y = box.y + 14f
        text.line(fonts.get(Face.MONO_MEDIUM, 11f), stepText, x, y, 16f, pal[Tok.MUTED], ba)
        y += 16f + 4f
        text.line(fonts.get(Face.HAND_BOLD, titleSize), t.lesson.title, x, y, 32f, pal[Tok.INK], ba)
        y += 32f + 6f
        body.draw(text, pal, x, y, ba)
        Widgets.buttonLabel(text, fonts.get(Face.UI_BOLD, 13f), pal, skip, SKIP, primary = false, enabled = true)
        feedback.draw(text, pal, x, feedbackTop() + (FEEDBACK_H - feedback.height) / 2f, ba)
    }

    fun invalidate() {
        body.invalidate()
        feedback.invalidate()
    }

    companion object {
        const val SKIP = "Skip tutorial"
        private const val TITLE_SIZE = 28f
        private const val TITLE_MIN = 20f
        /** Room for two lines of feedback, so the card doesn't jump when a hint appears. */
        private const val FEEDBACK_H = 36f
        /** Dot's radius beside the balloon, and how long it takes to step out. */
        private const val R = 26f
        private const val OUT_S = 0.45
        private const val PI_F = kotlin.math.PI.toFloat()
    }
}
