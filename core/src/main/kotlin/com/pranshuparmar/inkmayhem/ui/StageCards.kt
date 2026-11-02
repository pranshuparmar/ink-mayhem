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

/** Three small gold-star marks in a row from ([x], [cy]): found ones filled, the rest outlined. */
internal fun drawGoldMarks(shapes: Shapes, pal: Palette, x: Float, cy: Float, r: Float, mask: Int, gap: Float) {
    for (i in 0 until 3) {
        val cx = x + r + i * gap
        if (mask and (1 shl i) != 0) {
            shapes.color(pal[Tok.STAR]).star(cx, cy, r)
        } else {
            shapes.color(pal[Tok.STAR], 0.55f).star(cx, cy, r)
            shapes.color(pal[Tok.UI]).star(cx, cy, r * 0.55f)
        }
    }
}

/**
 * The stage list: a notebook with a tab per world. Each of the world's stages is a row with its
 * stars, gold stars and best; a stage is locked until the one before it is cleared. Every world's
 * tab opens, locked or not, so its stages can be looked over (a padlock marks a locked world);
 * only open stages play.
 */
class StageSelectCard(private val fonts: Fonts, private val store: StageStore) {
    val box = Box()
    /** Picks up where the player left off: the next stage not yet cleared. */
    val play = Box()
    val press = Press()
    /** The world tabs' targets, 1-based by position. */
    class WorldTab(val number: Int)
    val tabs = Stages.WORLDS.map { WorldTab(it.number) }
    private val tabBoxes = Array(tabs.size) { Box() }
    /** The world on show. */
    var world = 1
        private set
    private val all = Stages.ALL
    private val rows = Array(MAX_ROWS) { Box() }
    // read from Preferences when the list opens (refresh), not on every frame or pen sample
    private val open = BooleanArray(all.size)
    private val stars = IntArray(all.size)
    private val gold = IntArray(all.size)
    private val subs = Array(all.size) { "" }
    private val worldOpen = BooleanArray(tabs.size)
    private var title = ""
    private var rowH = ROW_H

    fun refresh() {
        for (i in all.indices) {
            open[i] = StageSession.unlocked(all[i], store)
            stars[i] = store.stars(all[i].id)
            gold[i] = store.gold(all[i].id)
            val best = store.best(all[i].id)
            subs[i] = if (!open[i]) "LOCKED" else if (best > 0) "BEST ${Format.thousands(best)}" else "NOT CLEARED YET"
        }
        for (w in Stages.WORLDS.indices) {
            val first = Stages.WORLDS[w].stages.firstOrNull()
            worldOpen[w] = first != null && open[all.indexOf(first)]
        }
        show(world)
    }

    /** Shows world [n] (if it has any stages). */
    fun show(n: Int) {
        val w = Stages.WORLDS.firstOrNull { it.number == n } ?: return
        world = n
        title = "World ${w.number} · ${w.name}"
    }

    private val shown: List<StageDef> get() = Stages.WORLDS.first { it.number == world }.stages

    init {
        refresh()
    }

    fun layout(text: Text, f: Frame) {
        // five rows fit a landscape phone too: rows shrink to what the height allows
        val avail = f.hDp - f.safeT - f.safeB - 24f
        rowH = ((avail - FIXED_H) / MAX_ROWS - ROW_GAP).coerceIn(MIN_ROW_H, ROW_H)
        val n = shown.size
        val h = FIXED_H + n * (rowH + ROW_GAP)
        box.set((f.wDp - f.cardW) / 2f + (f.padL - f.padR) / 2f, (f.hDp - h) / 2f + (f.safeT - f.safeB) / 2f, f.cardW, h)
        var y = box.y + 20f + 34f + 10f
        for (i in 0 until n) {
            rows[i].set(box.x + 20f, y, box.w - 40f, rowH)
            y += rowH + ROW_GAP
        }
        val bw = text.width(fonts.get(Face.UI_BOLD, 14f), "Play") + 48f
        play.set(box.right - 20f - bw, y + 6f, bw, 42f)
        for (t in tabBoxes.indices) tabBoxes[t].set(box.x + 20f + t * (TAB + 6f), y + 6f + (42f - TAB) / 2f, TAB, TAB)
    }

    /** The stage under (x, y) if it can be played, a world tab, or [play]. */
    fun targetAt(x: Float, y: Float): Any? {
        if (play.contains(x, y)) return play
        for (t in tabBoxes.indices) if (tabBoxes[t].contains(x, y)) return tabs[t]
        val list = shown
        for (i in list.indices) {
            val k = all.indexOf(list[i])
            if (rows[i].contains(x, y) && open[k]) return list[i]
        }
        return null
    }

    fun drawShapes(shapes: Shapes, pal: Palette, f: Frame) {
        // a light veil: the world's own page shows round the card
        shapes.color(pal[Tok.PAPER], 0.3f).rect(0f, 0f, f.wDp, f.hDp)
        Widgets.card(shapes, pal, box)
        val list = shown
        val big = rowH >= 50f
        for (i in list.indices) {
            val b = rows[i]
            val k = all.indexOf(list[i])
            Widgets.button(shapes, pal, b, primary = false, enabled = open[k], pressed = press.isPressed(list[i]))
            if (open[k]) {
                drawStars(shapes, pal, b.right - 46f, b.y + b.h / 2f, if (big) 9f else 8f, stars[k], if (big) 21f else 18f)
                drawGoldMarks(shapes, pal, b.x + 58f + subW[i] + 8f, b.y + b.h * 0.7f, 4.5f, gold[k], 11f)
            }
        }
        for (t in tabBoxes.indices) {
            val b = tabBoxes[t]
            val sel = tabs[t].number == world
            Widgets.button(shapes, pal, b, primary = sel, enabled = true, pressed = press.isPressed(tabs[t]))
            if (!worldOpen[t]) padlock(shapes, if (sel) pal[Tok.ACCENT_FG] else pal[Tok.MUTED], b.right - 7f, b.y + 7f)
        }
        Widgets.button(shapes, pal, play, primary = true, enabled = true, pressed = press.isPressed(play))
    }

    /** Width of each row's subtitle, for placing its gold marks after it (measured in drawText). */
    private val subW = FloatArray(MAX_ROWS)

    fun drawText(text: Text, pal: Palette) {
        text.line(fonts.get(Face.HAND_BOLD, 30f), title, box.x + 20f, box.y + 20f, 34f, pal[Tok.INK])
        val idFont = fonts.get(Face.MONO_BOLD, 15f)
        val nameFont = fonts.get(Face.UI_BOLD, 16f)
        val small = fonts.get(Face.MONO_MEDIUM, 10.5f)
        val list = shown
        for (i in list.indices) {
            val b = rows[i]
            val k = all.indexOf(list[i])
            val fg = if (open[k]) pal[Tok.UI_FG] else pal[Tok.MUTED]
            text.line(idFont, list[i].id, b.x + 14f, b.y, b.h, fg)
            text.line(nameFont, list[i].name, b.x + 58f, b.y + b.h * 0.14f, 20f, fg)
            text.line(small, subs[k], b.x + 58f, b.y + b.h * 0.56f, 14f, pal[Tok.MUTED])
            subW[i] = if (open[k]) text.width(small, subs[k]) else 0f
        }
        val tabFont = fonts.get(Face.MONO_BOLD, 14f)
        for (t in tabBoxes.indices) {
            val sel = tabs[t].number == world
            Widgets.buttonLabel(text, tabFont, pal, tabBoxes[t], tabs[t].number.toString(), primary = sel, enabled = true)
        }
        Widgets.buttonLabel(text, fonts.get(Face.UI_BOLD, 14f), pal, play, "Play", primary = true, enabled = true)
    }

    /** A small padlock centred on ([x], [y]). */
    private fun padlock(shapes: Shapes, c: com.badlogic.gdx.graphics.Color, x: Float, y: Float) {
        shapes.color(c)
        shapes.arc(x, y - 1f, 2.4f, 1.2f, PI_F, 2f * PI_F)
        shapes.roundRect(x - 3.2f, y - 1f, 6.4f, 4.8f, 1f)
    }

    companion object {
        private const val PI_F = kotlin.math.PI.toFloat()
        private const val ROW_H = 54f
        private const val MIN_ROW_H = 40f
        private const val ROW_GAP = 6f
        private const val MAX_ROWS = 5
        private const val TAB = 34f
        /** Title, the gap under it, and the tabs and Play row. */
        private const val FIXED_H = 20f + 34f + 10f + 6f + 42f + 18f
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
 * total. Next (or The end after the last stage), Retry, Stages.
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
        val h = 20f + 36f + 14f + 50f + 16f + 14f + 4 * 22f + 12f + 42f + 20f
        box.set((f.wDp - f.cardW) / 2f + (f.padL - f.padR) / 2f, (f.hDp - h) / 2f, f.cardW, h)
        val font = fonts.get(Face.UI_BOLD, 14f)
        val y = box.bottom - 20f - 42f
        val sw = text.width(font, "Stages") + 32f
        val rw = text.width(font, "Retry") + 32f
        val nw = text.width(font, if (hasNext) "Next" else LAST) + 32f
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
        // gold stars: the row under the tally's heading, found ever (this clear's bright)
        val gy = box.y + 20f + 36f + 14f + 50f + 8f + 14f + 8f + 3 * 22f + 11f
        for (i in 0 until 3) {
            val cx = box.right - 20f - 8f - (2 - i) * 22f
            val bit = 1 shl i
            when {
                r.gold and bit != 0 -> shapes.color(pal[Tok.STAR]).star(cx, gy, 8.5f)
                r.goldEver and bit != 0 -> shapes.color(pal[Tok.STAR], 0.4f).star(cx, gy, 8.5f)
                else -> {
                    shapes.color(pal[Tok.MUTED], 0.6f).star(cx, gy, 8.5f)
                    shapes.color(pal[Tok.UI]).star(cx, gy, 5f)
                }
            }
        }
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
        y += 22f
        text.line(mono, GOLD, x, y, 22f, pal[Tok.MUTED])
        val font = fonts.get(Face.UI_BOLD, 14f)
        Widgets.buttonLabel(text, font, pal, next, if (hasNext) "Next" else LAST, primary = true, enabled = true)
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
        private const val GOLD = "GOLD STARS"
        /** After the last stage the way on is the ending. */
        private const val LAST = "The end"
    }
}
