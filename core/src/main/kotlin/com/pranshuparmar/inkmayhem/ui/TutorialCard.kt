package com.pranshuparmar.inkmayhem.ui

import com.pranshuparmar.inkmayhem.game.Tutorial
import com.pranshuparmar.inkmayhem.render.Face
import com.pranshuparmar.inkmayhem.render.Fonts
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok

/**
 * The lesson card pinned to the top of the page during the tutorial: progress, the lesson's
 * title and instruction, a feedback line and a Skip button. The game swallows pen-downs on it so
 * nobody draws under it.
 */
class TutorialCard(private val fonts: Fonts) {
    val box = Box()
    val skip = Box()
    val press = Press()
    private val body = Flow(21f)
    private val feedback = Flow(18f)
    private var bodyFor: Tutorial.Lesson? = null
    private var feedbackFor: String? = null
    private var feedbackTone = Tok.MUTED
    private var stepText = ""

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
        val inner = f.cardW - 32f
        feedback.layout(text, inner)
        val h = 14f + 16f + 4f + 32f + 6f + body.layout(text, inner) + 8f + FEEDBACK_H + 12f
        box.set((f.wDp - f.cardW) / 2f + (f.padL - f.padR) / 2f, f.safeT + 10f, f.cardW, h)
        val skipW = text.width(fonts.get(Face.UI_BOLD, 13f), SKIP) + 22f
        skip.set(box.right - 12f - skipW, box.y + 10f, skipW, 30f)
    }

    private fun feedbackTop() = box.bottom - 12f - FEEDBACK_H

    fun drawShapes(shapes: Shapes, pal: Palette, @Suppress("UNUSED_PARAMETER") t: Tutorial) {
        Widgets.card(shapes, pal, box)
        Widgets.button(shapes, pal, skip, primary = false, enabled = true, pressed = press.isPressed(skip))
    }

    fun drawText(text: Text, pal: Palette, t: Tutorial) {
        val x = box.x + 16f
        var y = box.y + 14f
        text.line(fonts.get(Face.MONO_MEDIUM, 11f), stepText, x, y, 16f, pal[Tok.MUTED])
        y += 16f + 4f
        text.line(fonts.get(Face.HAND_BOLD, 28f), t.lesson.title, x, y, 32f, pal[Tok.INK])
        y += 32f + 6f
        body.draw(text, pal, x, y)
        Widgets.buttonLabel(text, fonts.get(Face.UI_BOLD, 13f), pal, skip, SKIP, primary = false, enabled = true)
        feedback.draw(text, pal, x, feedbackTop() + (FEEDBACK_H - feedback.height) / 2f)
    }

    fun invalidate() {
        body.invalidate()
        feedback.invalidate()
    }

    companion object {
        const val SKIP = "Skip tutorial"
        /** Room for two lines of feedback, so the card doesn't jump when a hint appears. */
        private const val FEEDBACK_H = 36f
    }
}
