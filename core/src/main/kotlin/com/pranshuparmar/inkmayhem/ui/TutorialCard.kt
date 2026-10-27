package com.pranshuparmar.inkmayhem.ui

import com.badlogic.gdx.utils.Align
import com.pranshuparmar.inkmayhem.game.Tutorial
import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.render.Face
import com.pranshuparmar.inkmayhem.render.Fonts
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok

/**
 * The lesson card pinned to the top of the page during the tutorial: progress, the lesson's
 * title and instruction, then either the three ink chips (pressure lesson) or a feedback line,
 * and a Skip button. The game swallows pen-downs on it so nobody draws under it.
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
    private val chips = Array(Ink.entries.size) { Box() }

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
        var x = box.x + 16f
        val cy = feedbackTop() + (FEEDBACK_H - 26f) / 2f
        val chipFont = fonts.get(Face.UI_BOLD, 13f)
        for (wi in INKS.indices) { // index loops: these run every frame
            val ink = INKS[wi]
            val w = text.width(chipFont, ink.label) + 26f
            chips[ink.weight].set(x, cy, w, 26f)
            x += w + 8f
        }
    }

    private fun feedbackTop() = box.bottom - 12f - FEEDBACK_H

    private fun showChips(t: Tutorial) = t.lesson == Tutorial.Lesson.PRESSURE && t.phase == Tutorial.Phase.TRYING

    fun drawShapes(shapes: Shapes, pal: Palette, t: Tutorial) {
        Widgets.card(shapes, pal, box)
        Widgets.button(shapes, pal, skip, primary = false, enabled = true, pressed = press.isPressed(skip))
        if (!showChips(t)) return
        for (wi in INKS.indices) {
            val ink = INKS[wi]
            val b = chips[ink.weight]
            shapes.color(pal[tok(ink)])
            if (t.seen[ink.weight]) shapes.roundRect(b.x, b.y, b.w, b.h, b.h / 2f) else shapes.roundRectOutline(b.x, b.y, b.w, b.h, b.h / 2f, 1.5f)
        }
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
        if (showChips(t)) {
            val chipFont = fonts.get(Face.UI_BOLD, 13f)
            for (wi in INKS.indices) {
                val ink = INKS[wi]
                val b = chips[ink.weight]
                val c = if (t.seen[ink.weight]) pal[Tok.PAPER] else pal[tok(ink)]
                text.line(chipFont, ink.label, b.x + b.w / 2f, b.y, b.h, c, 1f, Align.center)
            }
            return
        }
        feedback.draw(text, pal, x, feedbackTop() + (FEEDBACK_H - feedback.height) / 2f)
    }

    private fun tok(ink: Ink) = when (ink) {
        Ink.ROPE -> Tok.ROPE
        Ink.SPRING -> Tok.SPRING
        Ink.BEAM -> Tok.BEAM
    }

    fun invalidate() {
        body.invalidate()
        feedback.invalidate()
    }

    companion object {
        const val SKIP = "Skip tutorial"
        private val INKS = Ink.entries.toTypedArray()
        /** Room for two lines of feedback, so the card doesn't jump when a hint appears. */
        private const val FEEDBACK_H = 36f
    }
}
