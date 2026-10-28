package com.pranshuparmar.inkmayhem.ui

import com.badlogic.gdx.utils.Align
import com.pranshuparmar.inkmayhem.game.StageDef
import com.pranshuparmar.inkmayhem.game.StageSession
import com.pranshuparmar.inkmayhem.game.StageStore
import com.pranshuparmar.inkmayhem.game.Stages
import com.pranshuparmar.inkmayhem.render.Face
import com.pranshuparmar.inkmayhem.render.Fonts
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok

/** Three stars in a row: earned ones filled in gold, the rest outlined. */
internal fun drawStars(shapes: Shapes, pal: Palette, cx: Float, cy: Float, r: Float, earned: Int, gap: Float, scale: FloatArray? = null) {
    for (i in 0 until 3) {
        val x = cx + (i - 1) * gap
        val k = scale?.get(i) ?: 1f
        if (i < earned && k > 0f) {
            shapes.color(pal[Tok.SPRING]).star(x, cy, r * k)
        } else {
            shapes.color(pal[Tok.LINE]).star(x, cy, r)
            shapes.color(pal[Tok.UI]).star(x, cy, r * 0.62f)
        }
    }
}

/** World 1's stages as rows with their stars and best; locked ones until the one before is cleared. */
class StageSelectCard(private val fonts: Fonts, private val store: StageStore) {
    val box = Box()
    val back = Box()
    val press = Press()
    private val defs = Stages.ALL
    private val rows = Array(defs.size) { Box() }
    // read from Preferences when the list opens (refresh), not on every frame or pen sample
    private val open = BooleanArray(defs.size)
    private val stars = IntArray(defs.size)
    private val subs = Array(defs.size) { "" }

    fun refresh() {
        for (i in defs.indices) {
            open[i] = StageSession.unlocked(defs[i], store)
            stars[i] = store.stars(defs[i].id)
            val best = store.best(defs[i].id)
            subs[i] = if (!open[i]) "LOCKED" else if (best > 0) "BEST ${Format.thousands(best)}" else "NOT CLEARED YET"
        }
    }

    init {
        refresh()
    }

    fun layout(text: Text, f: Frame) {
        val h = 20f + 36f + 12f + rows.size * (ROW_H + 8f) + 6f + 42f + 20f
        box.set((f.wDp - f.cardW) / 2f + (f.padL - f.padR) / 2f, (f.hDp - h) / 2f, f.cardW, h)
        var y = box.y + 20f + 36f + 12f
        for (i in rows.indices) {
            rows[i].set(box.x + 20f, y, box.w - 40f, ROW_H)
            y += ROW_H + 8f
        }
        val bw = text.width(fonts.get(Face.UI_BOLD, 14f), "Back") + 32f
        back.set(box.right - 20f - bw, y + 6f, bw, 42f)
    }

    /** The stage under (x, y) if it can be played, or [back]. */
    fun targetAt(x: Float, y: Float): Any? {
        if (back.contains(x, y)) return back
        for (i in rows.indices) if (rows[i].contains(x, y) && open[i]) return defs[i]
        return null
    }

    fun drawShapes(shapes: Shapes, pal: Palette, f: Frame) {
        shapes.color(pal[Tok.PAPER], 0.55f).rect(0f, 0f, f.wDp, f.hDp)
        Widgets.card(shapes, pal, box)
        for (i in rows.indices) {
            val b = rows[i]
            Widgets.button(shapes, pal, b, primary = false, enabled = open[i], pressed = press.isPressed(defs[i]))
            if (open[i]) drawStars(shapes, pal, b.right - 46f, b.y + b.h / 2f, 9f, stars[i], 21f)
        }
        Widgets.button(shapes, pal, back, primary = false, enabled = true, pressed = press.isPressed(back))
    }

    fun drawText(text: Text, pal: Palette) {
        text.line(fonts.get(Face.HAND_BOLD, 34f), "World 1", box.x + 20f, box.y + 20f, 36f, pal[Tok.INK])
        val idFont = fonts.get(Face.MONO_BOLD, 15f)
        val nameFont = fonts.get(Face.UI_BOLD, 16f)
        val small = fonts.get(Face.MONO_MEDIUM, 10.5f)
        for (i in rows.indices) {
            val b = rows[i]
            val fg = if (open[i]) pal[Tok.UI_FG] else pal[Tok.MUTED]
            text.line(idFont, defs[i].id, b.x + 14f, b.y, b.h, fg)
            text.line(nameFont, defs[i].name, b.x + 58f, b.y + 9f, 20f, fg)
            text.line(small, subs[i], b.x + 58f, b.y + 30f, 14f, pal[Tok.MUTED])
        }
        Widgets.buttonLabel(text, fonts.get(Face.UI_BOLD, 14f), pal, back, "Back", primary = false, enabled = true)
    }

    companion object {
        private const val ROW_H = 54f
    }
}

/**
 * Before each try: the page goes dark with "WORLD 1-1", the stage's name and the lives left,
 * like an old console between lives. Retries dim the page more lightly and show only the lives,
 * briefly (ink can already go down then).
 */
class StageIntro(private val fonts: Fonts) {
    private var worldText = ""
    private var livesText = ""
    private var forStage: StageDef? = null
    private var forLives = -1

    private fun fade(s: StageSession): Float {
        val total = if (s.firstIntro) StageSession.INTRO_S else StageSession.RETRY_INTRO_S
        return ((total - s.phaseT) / 0.3f).coerceIn(0f, 1f)
    }

    fun visible(s: StageSession) = fade(s) > 0.01f

    fun drawShapes(shapes: Shapes, pal: Palette, f: Frame, s: StageSession) {
        val fade = fade(s)
        val cx = f.wDp / 2f
        val cy = f.hDp / 2f
        shapes.color(pal[Tok.INK], if (s.firstIntro) fade else 0.55f * fade).rect(0f, 0f, f.wDp, f.hDp)
        // the runner blot next to the lives
        shapes.color(pal[Tok.PAPER], fade).circle(cx - 34f, cy + 52f, 14f)
        shapes.color(pal[Tok.INK], fade).circle(cx - 34f + 3f, cy + 48f, 2.4f)
        shapes.color(pal[Tok.INK], fade).circle(cx - 34f + 9f, cy + 48f, 2.4f)
    }

    fun drawText(text: Text, pal: Palette, f: Frame, s: StageSession) {
        if (forStage !== s.stage) {
            forStage = s.stage
            worldText = "WORLD ${s.stage.id}"
        }
        if (forLives != s.lives) {
            forLives = s.lives
            livesText = "× ${s.lives}"
        }
        val fade = fade(s)
        val cx = f.wDp / 2f
        val cy = f.hDp / 2f
        if (s.firstIntro) {
            text.line(fonts.get(Face.MONO_BOLD, 22f), worldText, cx, cy - 92f, 28f, pal[Tok.PAPER], fade, Align.center)
            text.line(fonts.get(Face.HAND_BOLD, 44f), s.stage.name, cx, cy - 56f, 52f, pal[Tok.PAPER], fade, Align.center)
        }
        text.line(fonts.get(Face.HAND_BOLD, 36f), livesText, cx - 12f, cy + 32f, 40f, pal[Tok.PAPER], fade)
    }
}

/** No lives left: try the stage again with fresh lives, or go back to the stage list. */
class GameOverCard(private val fonts: Fonts) {
    val box = Box()
    val again = Box()
    val stages = Box()
    val press = Press()

    fun layout(text: Text, f: Frame) {
        val h = 20f + 44f + 8f + 20f + 18f + 42f + 20f
        box.set((f.wDp - f.cardW) / 2f + (f.padL - f.padR) / 2f, (f.hDp - h) / 2f, f.cardW, h)
        val font = fonts.get(Face.UI_BOLD, 14f)
        val y = box.bottom - 20f - 42f
        val sw = text.width(font, "Stages") + 32f
        val aw = text.width(font, "Try again") + 32f
        stages.set(box.right - 20f - sw, y, sw, 42f)
        again.set(stages.x - 8f - aw, y, aw, 42f)
    }

    fun targetAt(x: Float, y: Float): Any? = if (again.contains(x, y)) again else if (stages.contains(x, y)) stages else null

    fun drawShapes(shapes: Shapes, pal: Palette, f: Frame) {
        shapes.color(pal[Tok.PAPER], 0.55f).rect(0f, 0f, f.wDp, f.hDp)
        Widgets.card(shapes, pal, box)
        Widgets.button(shapes, pal, again, primary = true, enabled = true, pressed = press.isPressed(again))
        Widgets.button(shapes, pal, stages, primary = false, enabled = true, pressed = press.isPressed(stages))
    }

    private var subFor: StageDef? = null
    private var sub = ""

    fun drawText(text: Text, pal: Palette, def: StageDef) {
        val x = box.x + 20f
        if (subFor !== def) {
            subFor = def
            sub = "WORLD ${def.id} · ${def.name.uppercase()}"
        }
        text.line(fonts.get(Face.HAND_BOLD, 40f), "Game over", x, box.y + 20f, 44f, pal[Tok.INK])
        text.line(fonts.get(Face.MONO_MEDIUM, 12f), sub, x, box.y + 72f, 20f, pal[Tok.MUTED])
        val font = fonts.get(Face.UI_BOLD, 14f)
        Widgets.buttonLabel(text, font, pal, again, "Try again", primary = true, enabled = true)
        Widgets.buttonLabel(text, font, pal, stages, "Stages", primary = false, enabled = true)
    }
}

/**
 * The stage-clear tally: stars pop in one by one, the score counts up, then the ink bonus and the
 * total. Next (or Stages after the last stage), Retry, Stages.
 */
class ClearCard(private val fonts: Fonts) {
    val box = Box()
    val next = Box()
    val retry = Box()
    val stages = Box()
    val press = Press()
    var hasNext = true
        private set
    private val starScale = FloatArray(3)
    private var scoreText = ""
    private var bonusText = ""
    private var totalText = ""
    private var lastShown = -1

    fun layout(text: Text, f: Frame, r: StageSession.Result, t: Float) {
        hasNext = Stages.next(r.stage) != null
        val h = 20f + 36f + 14f + 50f + 16f + 14f + 3 * 22f + 12f + 42f + 20f
        box.set((f.wDp - f.cardW) / 2f + (f.padL - f.padR) / 2f, (f.hDp - h) / 2f, f.cardW, h)
        val font = fonts.get(Face.UI_BOLD, 14f)
        val y = box.bottom - 20f - 42f
        val sw = text.width(font, "Stages") + 32f
        val rw = text.width(font, "Retry") + 32f
        val nw = text.width(font, "Next") + 32f
        stages.set(box.right - 20f - sw, y, sw, 42f)
        retry.set(stages.x - 8f - rw, y, rw, 42f)
        next.set(retry.x - 8f - nw, y, nw, 42f)
        // stars pop in from 0.4 s, 0.45 s apart (0 → 1.25 → 1)
        for (i in 0 until 3) {
            val u = (t - STAR_T0 - i * STAR_DT) / 0.25f
            starScale[i] = when {
                u <= 0f -> 0f
                u < 1f -> 1.25f * u
                u < 2f -> 1.25f - 0.25f * (u - 1f)
                else -> 1f
            }
        }
        // the score counts up after the stars, then the bonus, then the total
        val count = ((t - COUNT_T0) / COUNT_S).coerceIn(0f, 1f)
        val shown = (r.score * count).toInt() * 2 + if (count >= 1f) 1 else 0
        if (shown != lastShown) {
            lastShown = shown
            scoreText = Format.thousands((r.score * count).toInt())
            bonusText = if (count >= 1f) "+" + Format.thousands(r.inkBonus) else ""
            totalText = if (count >= 1f) Format.thousands(r.total) else ""
        }
    }

    /** Stars popped so far (the game plays a sound as each appears). */
    fun starsShown(t: Float, earned: Int): Int {
        var n = 0
        for (i in 0 until earned) if (t >= STAR_T0 + i * STAR_DT) n++
        return n
    }

    fun targetAt(x: Float, y: Float): Any? = when {
        next.contains(x, y) -> next
        retry.contains(x, y) -> retry
        stages.contains(x, y) -> stages
        else -> null
    }

    fun drawShapes(shapes: Shapes, pal: Palette, f: Frame, r: StageSession.Result) {
        shapes.color(pal[Tok.PAPER], 0.55f).rect(0f, 0f, f.wDp, f.hDp)
        Widgets.card(shapes, pal, box)
        drawStars(shapes, pal, box.x + box.w / 2f, box.y + 20f + 36f + 14f + 25f, 22f, r.stars, STAR_GAP, starScale)
        Widgets.button(shapes, pal, next, primary = true, enabled = true, pressed = press.isPressed(next))
        Widgets.button(shapes, pal, retry, primary = false, enabled = true, pressed = press.isPressed(retry))
        Widgets.button(shapes, pal, stages, primary = false, enabled = true, pressed = press.isPressed(stages))
    }

    private var titleFor: StageSession.Result? = null
    private var title = ""
    private var bonusLabel = "INK LEFT × ${StageSession.INK_BONUS}"

    fun drawText(text: Text, pal: Palette, r: StageSession.Result) {
        val x = box.x + 20f
        if (titleFor !== r) {
            titleFor = r
            title = "${r.stage.id} cleared!"
        }
        text.line(fonts.get(Face.HAND_BOLD, 34f), title, x, box.y + 20f, 36f, pal[Tok.INK])
        var y = box.y + 20f + 36f + 14f + 50f + 8f
        val tiny = fonts.get(Face.MONO_MEDIUM, 9.5f)
        val cx = box.x + box.w / 2f
        val labels = LABELS
        for (i in 0 until 3) {
            val earned = when (i) { 0 -> true; 1 -> r.noDeaths; else -> r.allLight }
            text.line(tiny, labels[i], cx + (i - 1) * STAR_GAP, y, 14f, if (earned) pal[Tok.UI_FG] else pal[Tok.MUTED], 1f, Align.center)
        }
        y += 14f + 8f
        val mono = fonts.get(Face.MONO_MEDIUM, 12f)
        val big = fonts.get(Face.MONO_BOLD, 15f)
        val right = box.right - 20f
        text.line(mono, "SCORE", x, y, 22f, pal[Tok.MUTED])
        text.line(big, scoreText, right, y, 22f, pal[Tok.UI_FG], 1f, Align.right)
        y += 22f
        text.line(mono, bonusLabel, x, y, 22f, pal[Tok.MUTED])
        text.line(big, bonusText, right, y, 22f, pal[Tok.DROP], 1f, Align.right)
        y += 22f
        text.line(mono, if (r.newBest && totalText.isNotEmpty()) TOTAL_BEST else TOTAL, x, y, 22f, if (r.newBest) pal[Tok.BEAM] else pal[Tok.MUTED])
        text.line(big, totalText, right, y, 22f, pal[Tok.INK], 1f, Align.right)
        val font = fonts.get(Face.UI_BOLD, 14f)
        Widgets.buttonLabel(text, font, pal, next, if (hasNext) "Next" else "Stages", primary = true, enabled = true)
        Widgets.buttonLabel(text, font, pal, retry, "Retry", primary = false, enabled = true)
        Widgets.buttonLabel(text, font, pal, stages, "Stages", primary = false, enabled = true)
    }

    companion object {
        const val STAR_T0 = 0.4f
        const val STAR_DT = 0.45f
        const val COUNT_T0 = 1.8f
        const val COUNT_S = 0.9f
        private val LABELS = arrayOf("CLEARED", "NO DEATHS", "LIGHT INK")
        private const val STAR_GAP = 96f
        private const val TOTAL = "TOTAL"
        private const val TOTAL_BEST = "TOTAL · NEW BEST"
    }
}
