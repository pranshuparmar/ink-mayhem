package com.pranshuparmar.inkmayhem.ui

import com.badlogic.gdx.utils.Align
import com.pranshuparmar.inkmayhem.game.BossKind
import com.pranshuparmar.inkmayhem.game.Sticker
import com.pranshuparmar.inkmayhem.input.PenPhase
import com.pranshuparmar.inkmayhem.input.PenSample
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
 * A sticker just earned: it's slapped into the top-right margin, under the HUD, with its name,
 * and peels off a few seconds later. Several in a row queue up.
 */
class StickerPop(private val fonts: Fonts) {
    private val queue = ArrayDeque<Sticker>()
    private var current: Sticker? = null
    private var since = 0.0
    private val box = Box()

    val showing: Boolean get() = current != null

    fun add(s: Sticker) {
        queue.addLast(s)
    }

    /** Advances to [now]; returns the sticker that has just been slapped on (for its sound), or null. */
    fun update(now: Double): Sticker? {
        val c = current
        if (c != null && now - since < SHOW_S) return null
        current = queue.removeFirstOrNull()
        since = now
        return current
    }

    fun clear() {
        queue.clear()
        current = null
    }

    private fun slide(now: Double, reduced: Boolean): Float {
        if (reduced) return 1f
        val t = (now - since).toFloat()
        val inU = (t / 0.25f).coerceIn(0f, 1f)
        val outU = ((SHOW_S.toFloat() - t) / 0.3f).coerceIn(0f, 1f)
        return min(1f - (1f - inU) * (1f - inU), outU)
    }

    fun drawShapes(shapes: Shapes, pal: Palette, f: Frame, top: Float, now: Double, reduced: Boolean) {
        val s = current ?: return
        val u = slide(now, reduced)
        val w = 214f
        val h = 58f
        val x = f.wDp - max(12f, f.safeR) - w + (1f - u) * (w + 24f)
        box.set(x, top, w, h)
        Widgets.card(shapes, pal, box, radius = 14f)
        val cx = x + 32f
        val cy = top + h / 2f
        val c = pal[STICKER_TOKS[s.ordinal % STICKER_TOKS.size]]
        shapes.color(pal[Tok.PAPER_WHITE]).circle(cx, cy, 20f)
        shapes.color(c).circle(cx, cy, 17.5f)
        shapes.color(pal[Tok.PAPER_WHITE]).star(cx, cy, 9.5f)
    }

    fun drawText(text: Text, pal: Palette) {
        val s = current ?: return
        val x = box.x + 62f
        text.line(fonts.get(Face.MONO_BOLD, 10.5f), "NEW STICKER", x, box.y + 9f, 16f, pal[Tok.ACCENT])
        text.line(fonts.get(Face.HAND_BOLD, 20f), s.title, x, box.y + 25f, 24f, pal[Tok.INK])
    }

    companion object {
        const val SHOW_S = 3.4
        val STICKER_TOKS = arrayOf(Tok.ROPE, Tok.SPRING, Tok.BEAM, Tok.BLOT, Tok.DROP, Tok.STAR, Tok.ACCENT, Tok.ERASER)
    }
}

/**
 * Before a boss: the page letterboxes, and Dot and the boss trade a few lines in the bottom bar,
 * typed out to a babble. The boss is already there on the page behind. A tap finishes the line or
 * moves on; Skip ends it. Shown until that boss has been beaten once.
 */
class FaceOff(private val fonts: Fonts) {
    class Line(val dot: Boolean, val text: String)

    var open = false
        private set
    private var lines: List<Line> = emptyList()
    private var bossName = ""
    private var index = 0
    private var lineAt = 0.0
    private var openedAt = 0.0
    private var lastChars = 0
    private val skip = Box()
    private val press = Press()
    private val buddy = BuddyState()
    private var drawer: BuddyDrawer? = null
    private var barH = 60f
    /** Characters of the current line shown (all once it's finished typing). */
    private var shown = 0
    private var blip = Float.NaN

    fun start(kind: BossKind, now: Double) {
        lines = LINES.getValue(kind)
        bossName = kind.title
        index = 0
        lineAt = now
        openedAt = now
        lastChars = 0
        shown = 0
        open = true
        press.cancel()
        buddy.hush()
    }

    fun close() {
        open = false
        press.cancel()
    }

    /** Advances the typing; [now] is the game's clock. */
    fun update(now: Double, dt: Float, reduced: Boolean) {
        if (!open) return
        val line = lines[index]
        val n = min(line.text.length, ((now - lineAt) * CHARS_PER_S).toInt())
        if (n > shown) {
            // a syllable of babble every few letters (none for spaces)
            val c = line.text[n - 1]
            if (n / 3 != shown / 3 && c != ' ') blip = if (line.dot) 1.35f + (n % 5) * 0.06f else 0.72f + (n % 4) * 0.05f
            shown = n
        }
        buddy.set(if (line.dot) (if (index == lines.lastIndex) Expr.PROUD else Expr.CURIOUS) else Expr.WORRIED)
        buddy.lookX = 1f
        buddy.lookY = -0.2f
        buddy.update(dt, still = reduced)
    }

    /** The babble's next syllable pitch, or NaN; the game plays it. */
    fun consumeBlip(): Float {
        val b = blip
        blip = Float.NaN
        return b
    }

    /** Whether the line on show is the boss's (for the voice). */
    val bossSpeaking: Boolean get() = open && !lines[index].dot

    /** Takes every sample while open. Returns true when the scene has just ended. */
    fun onSample(s: PenSample, x: Float, y: Float, now: Double): Boolean {
        when (s.phase) {
            PenPhase.DOWN -> press.down(s.pointerId, if (skip.contains(x, y)) skip else this)
            PenPhase.UP -> {
                val hit = press.up(s.pointerId, if (skip.contains(x, y)) skip else this) ?: return false
                if (hit === skip) {
                    close()
                    return true
                }
                val line = lines[index]
                if (shown < line.text.length) {
                    lineAt = now - line.text.length / CHARS_PER_S
                    shown = line.text.length
                } else if (index < lines.lastIndex) {
                    index++
                    lineAt = now
                    shown = 0
                } else {
                    close()
                    return true
                }
            }
            PenPhase.CANCEL -> press.cancel()
            else -> Unit
        }
        return false
    }

    fun drawShapes(shapes: Shapes, pal: Palette, f: Frame, now: Double, reduced: Boolean) {
        if (!open) return
        val u = if (reduced) 1f else ((now - openedAt) / 0.35).toFloat().coerceIn(0f, 1f)
        barH = max(64f, f.hDp * 0.16f)
        val ink = pal[Tok.INK]
        shapes.color(ink, 0.94f).rect(0f, 0f, f.wDp, (f.safeT + barH * 0.55f) * u)
        val bh = (barH + f.safeB) * u
        shapes.color(ink, 0.94f).rect(0f, f.hDp - bh, f.wDp, bh)
        skip.set(f.wDp - max(16f, f.safeR) - 70f, f.safeT + 6f, 70f, 32f)
        shapes.color(pal[Tok.PAPER], if (press.isPressed(skip)) 0.35f else 0.18f).roundRect(skip.x, skip.y, skip.w, skip.h, 10f)
        if (u < 1f) return
        val line = lines[index]
        if (line.dot) {
            val d = drawer ?: BuddyDrawer(shapes).also { drawer = it }
            val r = min(22f, barH * 0.28f)
            d.draw(pal, buddy, max(16f, f.safeL) + r + 6f, f.hDp - f.safeB - barH / 2f + 4f, r, 1f, 1f)
        }
        // the "more" mark once the line has finished typing
        if (shown >= line.text.length && !reduced && ((now * 2.0).toLong() % 2L == 0L)) {
            val mx = f.wDp - max(16f, f.safeR) - 10f
            val my = f.hDp - f.safeB - 16f
            shapes.color(pal[Tok.PAPER], 0.8f).triangle(mx - 6f, my - 4f, mx + 6f, my - 4f, mx, my + 3f)
        }
    }

    fun drawText(text: Text, pal: Palette, f: Frame, now: Double, reduced: Boolean) {
        if (!open) return
        val u = if (reduced) 1f else ((now - openedAt) / 0.35).toFloat().coerceIn(0f, 1f)
        text.line(fonts.get(Face.UI_BOLD, 13f), "Skip", skip.x + skip.w / 2f, skip.y, skip.h, pal[Tok.PAPER], 1f, Align.center)
        if (u < 1f) return
        val line = lines[index]
        val left = max(16f, f.safeL) + if (line.dot) min(22f, barH * 0.28f) * 2f + 22f else 6f
        val top = f.hDp - f.safeB - barH + 10f
        val who = if (line.dot) "DOT" else bossName.uppercase()
        text.line(fonts.get(Face.MONO_BOLD, 11f), who, left, top, 16f, if (line.dot) pal[Tok.ACCENT] else pal[Tok.BEAM])
        val say = line.text.substring(0, shown.coerceIn(0, line.text.length))
        val w = f.wDp - left - max(16f, f.safeR) - 24f
        text.draw(fonts.get(Face.HAND_BOLD, 20f), say, left, top + 22f, pal[Tok.PAPER], 1f, Align.left, w)
    }

    companion object {
        private const val CHARS_PER_S = 38.0
        val LINES: Map<BossKind, List<Line>> = mapOf(
            BossKind.BIG_BLOT to listOf(
                Line(false, "Blub. This page is MINE. Every margin, every line."),
                Line(true, "It's a notebook. It's for everyone!"),
                Line(false, "Then come and take it, little drop."),
                Line(true, "Springs, please. I'll bounce right on top of it!"),
            ),
            BossKind.GIANT_ERASER to listOf(
                Line(false, "Squeak. I rub out mistakes. You look like one."),
                Line(true, "Rude! And it rubs out rope and springs too…"),
                Line(false, "Nothing soft ever stops me."),
                Line(true, "Then something hard. Drop a beam on it!"),
            ),
            BossKind.AIRLINER to listOf(
                Line(false, "Cabin crew, prepare for take-off. Passengers: one ink drop."),
                Line(true, "I didn't buy a ticket!"),
                Line(false, "Then you'll fly with the paper planes."),
                Line(true, "A spring under it, and up I go!"),
            ),
            BossKind.BEETLE_QUEEN to listOf(
                Line(false, "My children are hungry. Your ink smells delicious."),
                Line(true, "Hands off my bridges!"),
                Line(false, "We'll see how long they last."),
                Line(true, "Bounce on her! Springs, and quick."),
            ),
            BossKind.BIG_SMUDGE to listOf(
                Line(false, "Midnight is mine. Every page ends in a smudge."),
                Line(true, "Not this one. This one ends with me."),
                Line(false, "Then roll, little drop. Roll into the dark."),
                Line(true, "A spring in its path throws it into its own spikes!"),
            ),
        )
    }
}
