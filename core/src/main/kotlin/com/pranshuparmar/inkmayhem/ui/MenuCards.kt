package com.pranshuparmar.inkmayhem.ui

import com.badlogic.gdx.utils.Align
import com.pranshuparmar.inkmayhem.game.StageDef
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
 * The title screen the game opens on: the name, the one idea (press lighter or harder for rope,
 * spring or beam) and the way in. Play picks up at the first stage not yet cleared; Stages,
 * How to play and Settings sit under it.
 */
class TitleCard(private val fonts: Fonts) {
    val play = Box()
    val stages = Box()
    val howTo = Box()
    val settings = Box()
    val press = Press()

    private var top = 0f
    private var left = 0f
    private var colW = 0f
    private var cx = 0f
    private var playSub = ""
    private var playLabel = "Play"
    /** When it came on screen (it fades in), in the game's clock. */
    private var shownAt = 0.0

    /** [next]: where Play goes (null once every stage is cleared); [fresh]: nothing cleared yet. */
    fun show(next: StageDef?, fresh: Boolean, now: Double) {
        press.cancel()
        shownAt = now
        playLabel = if (fresh) "Start" else "Play"
        playSub = when {
            next == null -> "EVERY STAGE CLEARED · PICK ONE"
            else -> "WORLD ${next.id} · ${next.name.uppercase()}"
        }
    }

    /** The name's size: [TITLE_SIZE], or smaller when it and the runner beside it wouldn't fit. */
    private var titleSize = TITLE_SIZE

    fun layout(text: Text, f: Frame) {
        colW = min(f.cardW, 380f)
        cx = f.wDp / 2f + (f.padL - f.padR) / 2f
        left = cx - colW / 2f
        val room = f.wDp - f.padL - f.padR - 2f * BLOT_ROOM
        val nameW = text.width(fonts.get(Face.HAND_BOLD, TITLE_SIZE), TITLE)
        titleSize = if (nameW <= room) TITLE_SIZE else kotlin.math.floor(TITLE_SIZE * room / nameW)
        // a little above the middle: the eye lands on the name first
        top = max(f.safeT + 12f, (f.hDp - HEIGHT) / 2f + (f.safeT - f.safeB) / 2f - 12f)
        var y = top + TITLE_H + TAG_H + 18f + LEGEND_H + 26f
        play.set(left, y, colW, PLAY_H)
        y += PLAY_H + 10f
        val w = (colW - 16f) / 3f
        stages.set(left, y, w, 42f)
        howTo.set(left + w + 8f, y, w, 42f)
        settings.set(left + 2f * (w + 8f), y, w, 42f)
    }

    fun targetAt(x: Float, y: Float): Any? = when {
        play.contains(x, y) -> play
        stages.contains(x, y) -> stages
        howTo.contains(x, y) -> howTo
        settings.contains(x, y) -> settings
        else -> null
    }

    private fun alpha(now: Double, reduced: Boolean) = if (reduced) 1f else ((now - shownAt) / FADE_S).toFloat().coerceIn(0f, 1f)

    fun drawShapes(shapes: Shapes, pal: Palette, text: Text, now: Double, reduced: Boolean) {
        val a = alpha(now, reduced)
        // the runner, sat at the end of the name, blinking now and then
        val titleW = text.width(fonts.get(Face.HAND_BOLD, titleSize), TITLE)
        val bx = cx + titleW / 2f + 22f
        val by = top + TITLE_H - 22f + if (reduced) 0f else 2f * sin(now.toFloat() * 3f)
        shapes.color(pal[Tok.INK], a).circle(bx, by, 15f)
        val blink = !reduced && (now % 4.0) < 0.12
        shapes.color(pal[Tok.PAPER], a)
        if (blink) {
            shapes.rect(bx - 6f, by - 3f, 5f, 1.6f)
            shapes.rect(bx + 2f, by - 3f, 5f, 1.6f)
        } else {
            shapes.circle(bx - 3.5f, by - 3f, 3f)
            shapes.circle(bx + 4.5f, by - 3f, 3f)
            shapes.color(pal[Tok.INK], a).circle(bx - 2.5f, by - 3f, 1.4f)
            shapes.color(pal[Tok.INK], a).circle(bx + 5.5f, by - 3f, 1.4f)
        }
        // the three inks: a slack rope, a coiled spring, a heavy beam
        val ly = top + TITLE_H + TAG_H + 18f + 10f
        val seg = colW / 3f
        for (i in 0 until 3) {
            val x0 = left + i * seg + 16f
            val x1 = left + (i + 1) * seg - 16f
            when (i) {
                0 -> {
                    shapes.color(pal[Tok.ROPE], a)
                    var px = x0
                    var py = ly
                    for (k in 1..8) {
                        val u = k / 8f
                        val x = x0 + (x1 - x0) * u
                        val y = ly + 6f * sin(u * PI_F)
                        shapes.capsule(px, py, x, y, 2.5f)
                        px = x; py = y
                    }
                }
                1 -> shapes.color(pal[Tok.SPRING], a).zigzag(x0, ly + 3f, x1, ly + 3f, 9, 4f, 3.2f)
                else -> shapes.color(pal[Tok.BEAM], a).capsule(x0, ly + 3f, x1, ly + 3f, 8f)
            }
        }
        Widgets.button(shapes, pal, play, primary = true, enabled = true, pressed = press.isPressed(play))
        Widgets.button(shapes, pal, stages, primary = false, enabled = true, pressed = press.isPressed(stages))
        Widgets.button(shapes, pal, howTo, primary = false, enabled = true, pressed = press.isPressed(howTo))
        Widgets.button(shapes, pal, settings, primary = false, enabled = true, pressed = press.isPressed(settings))
    }

    fun drawText(text: Text, pal: Palette, now: Double, reduced: Boolean) {
        val a = alpha(now, reduced)
        text.line(fonts.get(Face.HAND_BOLD, titleSize), TITLE, cx, top, TITLE_H, pal[Tok.INK], a, Align.center)
        text.line(fonts.get(Face.UI, 15f), TAGLINE, cx, top + TITLE_H, TAG_H, pal[Tok.MUTED], a, Align.center)
        val label = fonts.get(Face.MONO_MEDIUM, 10.5f)
        val ly = top + TITLE_H + TAG_H + 18f + 22f
        val seg = colW / 3f
        for (i in 0 until 3) {
            text.line(label, LEGEND[i], left + (i + 0.5f) * seg, ly, 14f, pal[Tok.UI_FG], a, Align.center)
        }
        val big = fonts.get(Face.UI_BOLD, 17f)
        val small = fonts.get(Face.MONO_MEDIUM, 10.5f)
        text.line(big, playLabel, play.x + play.w / 2f, play.y + 8f, 24f, pal[Tok.ACCENT_FG], 1f, Align.center)
        text.line(small, playSub, play.x + play.w / 2f, play.y + 32f, 16f, pal[Tok.ACCENT_FG], 0.85f, Align.center)
        val font = fonts.get(Face.UI_BOLD, 14f)
        Widgets.buttonLabel(text, font, pal, stages, "Stages", primary = false, enabled = true)
        Widgets.buttonLabel(text, font, pal, howTo, "How to play", primary = false, enabled = true)
        Widgets.buttonLabel(text, font, pal, settings, "Settings", primary = false, enabled = true)
    }

    companion object {
        const val TITLE = "Ink Mayhem"
        const val TAGLINE = "Draw the way ahead with your S Pen."
        private val LEGEND = arrayOf("LIGHT · ROPE", "NORMAL · SPRING", "HARD · BEAM")
        private const val TITLE_SIZE = 64f
        private const val TITLE_H = 72f
        private const val TAG_H = 24f
        private const val LEGEND_H = 36f
        private const val PLAY_H = 58f
        private const val HEIGHT = TITLE_H + TAG_H + 18f + LEGEND_H + 26f + PLAY_H + 10f + 42f
        private const val FADE_S = 0.5
        /** Room each side of the centred name for the runner sat beside it. */
        private const val BLOT_ROOM = 40f
        private const val PI_F = kotlin.math.PI.toFloat()
    }
}

/**
 * A stage on hold: the page dims behind "Paused" and the stage's name. Resume (the game counts
 * the runner back in), Restart the stage, Settings, or back to the Stage list.
 */
class PauseCard(private val fonts: Fonts) {
    val box = Box()
    val resume = Box()
    val restart = Box()
    val settings = Box()
    val quit = Box()
    val press = Press()
    var open = false
        private set

    fun show() {
        open = true
        press.cancel()
    }

    fun hide() {
        open = false
        press.cancel()
    }

    fun layout(f: Frame) {
        val h = 20f + 44f + 4f + 20f + 18f + 48f + 10f + 42f + 20f
        box.set((f.wDp - f.cardW) / 2f + (f.padL - f.padR) / 2f, (f.hDp - h) / 2f + (f.safeT - f.safeB) / 2f, f.cardW, h)
        var y = box.y + 20f + 44f + 4f + 20f + 18f
        resume.set(box.x + 20f, y, box.w - 40f, 48f)
        y += 48f + 10f
        val w = (box.w - 40f - 16f) / 3f
        restart.set(box.x + 20f, y, w, 42f)
        settings.set(box.x + 20f + w + 8f, y, w, 42f)
        quit.set(box.x + 20f + 2f * (w + 8f), y, w, 42f)
    }

    fun targetAt(x: Float, y: Float): Any? = when {
        resume.contains(x, y) -> resume
        restart.contains(x, y) -> restart
        settings.contains(x, y) -> settings
        quit.contains(x, y) -> quit
        else -> null
    }

    fun drawShapes(shapes: Shapes, pal: Palette, f: Frame) {
        shapes.color(pal[Tok.PAPER], 0.72f).rect(0f, 0f, f.wDp, f.hDp)
        Widgets.card(shapes, pal, box)
        Widgets.button(shapes, pal, resume, primary = true, enabled = true, pressed = press.isPressed(resume))
        Widgets.button(shapes, pal, restart, primary = false, enabled = true, pressed = press.isPressed(restart))
        Widgets.button(shapes, pal, settings, primary = false, enabled = true, pressed = press.isPressed(settings))
        Widgets.button(shapes, pal, quit, primary = false, enabled = true, pressed = press.isPressed(quit))
    }

    private var subFor: StageDef? = null
    private var sub = ""

    fun drawText(text: Text, pal: Palette, def: StageDef) {
        if (subFor !== def) {
            subFor = def
            sub = "WORLD ${def.id} · ${def.name.uppercase()}"
        }
        val x = box.x + 20f
        text.line(fonts.get(Face.HAND_BOLD, 40f), "Paused", x, box.y + 20f, 44f, pal[Tok.INK])
        text.line(fonts.get(Face.MONO_MEDIUM, 12f), sub, x, box.y + 20f + 44f + 4f, 20f, pal[Tok.MUTED])
        Widgets.buttonLabel(text, fonts.get(Face.UI_BOLD, 16f), pal, resume, "Resume", primary = true, enabled = true)
        val font = fonts.get(Face.UI_BOLD, 14f)
        Widgets.buttonLabel(text, font, pal, restart, "Restart", primary = false, enabled = true)
        Widgets.buttonLabel(text, font, pal, settings, "Settings", primary = false, enabled = true)
        Widgets.buttonLabel(text, font, pal, quit, "Stage list", primary = false, enabled = true)
    }
}

/** After Resume: 3, 2, 1 over the page, then the runner rolls on. */
class Countdown(private val fonts: Fonts) {
    /** Seconds left; 0 when not counting. */
    var left = 0f
        private set
    val running: Boolean get() = left > 0f

    fun start() {
        left = BEATS * BEAT_S
    }

    fun cancel() {
        left = 0f
    }

    /** Advances by [dt]; returns the beat (3, 2, 1) just reached, or 0. */
    fun update(dt: Float): Int {
        if (left <= 0f) return 0
        val before = beat
        left = max(0f, left - dt)
        val now = beat
        return if (now != before && now > 0) now else 0
    }

    /** The number on show: 3, 2 or 1 (0 when done). */
    val beat: Int get() = if (left <= 0f) 0 else kotlin.math.ceil(left / BEAT_S).toInt()

    private val digits = arrayOf("", "1", "2", "3")

    fun drawShapes(shapes: Shapes, pal: Palette, f: Frame, reduced: Boolean) {
        if (!running) return
        shapes.color(pal[Tok.PAPER], 0.35f * min(1f, left / 0.3f)).rect(0f, 0f, f.wDp, f.hDp)
        val u = 1f - (left % BEAT_S) / BEAT_S // 0 → 1 through a beat
        val r = if (reduced) 44f else 44f + 6f * (1f - u)
        shapes.color(pal[Tok.UI], 0.92f).circle(f.wDp / 2f, f.hDp / 2f, r)
        shapes.color(pal[Tok.ACCENT]).ring(f.wDp / 2f, f.hDp / 2f, r, 3f)
    }

    fun drawText(text: Text, pal: Palette, f: Frame) {
        if (!running) return
        text.line(fonts.get(Face.HAND_BOLD, 64f, Fonts.DIGITS), digits[beat.coerceIn(0, 3)], f.wDp / 2f, f.hDp / 2f - 36f, 72f, pal[Tok.INK], 1f, Align.center)
    }

    companion object {
        const val BEATS = 3
        const val BEAT_S = 0.5f
    }
}
