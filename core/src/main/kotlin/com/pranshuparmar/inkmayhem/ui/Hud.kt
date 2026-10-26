package com.pranshuparmar.inkmayhem.ui

import com.badlogic.gdx.utils.Align
import com.pranshuparmar.inkmayhem.game.GameSim
import com.pranshuparmar.inkmayhem.render.Face
import com.pranshuparmar.inkmayhem.render.Fonts
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * POC .hud: score with the ×combo chip and "N m · best X" on the left; ink meter and the settings
 * button on the right. Text is cached and rebuilt only when the numbers change.
 */
class Hud {
    /** The right-hand cluster (meter + button) swallows pen-downs, as in the POC. */
    val rightArea = Box()
    val settingsButton = Box()
    private val chip = Box()

    private var scoreText = "0"
    private var bestText = "0 m · best 0"
    private var comboText = "×1"
    private var lastScore = -1
    private var lastDist = -1
    private var lastBest = -1
    private var lastCombo = 0
    private var bump = 0f

    private var left = 16f
    private var top = 10f
    private var scoreW = 0f

    fun update(dt: Float, sim: GameSim, best: Int, reducedMotion: Boolean) {
        val sc = floor(sim.score).toInt()
        val d = floor(sim.dist).toInt()
        val shownBest = max(best, sc)
        if (sc != lastScore || d != lastDist || shownBest != lastBest) {
            scoreText = Format.thousands(sc)
            bestText = "$d m · best ${Format.thousands(shownBest)}".uppercase()
            lastScore = sc; lastDist = d; lastBest = shownBest
        }
        if (sim.combo != lastCombo) {
            if (sim.combo > lastCombo && lastCombo != 0 && !reducedMotion) bump = BUMP_S
            comboText = "×${sim.combo}"
            lastCombo = sim.combo
        }
        bump = max(0f, bump - dt)
    }

    fun layout(text: Text, fonts: Fonts, wDp: Float, safeLeft: Float, safeTop: Float, safeRight: Float) {
        left = max(16f, safeLeft)
        top = safeTop + 10f
        val right = wDp - max(16f, safeRight)
        scoreW = text.width(fonts.get(Face.HAND_BOLD, 40f, Fonts.DIGITS), scoreText)
        val comboW = text.width(fonts.get(Face.MONO_BOLD, 16f), comboText)
        chip.set(left + scoreW + 10f, top + (40f - 27f) / 2f, comboW + 18f + 3f, 27f)
        settingsButton.set(right - 40f, top, 40f, 40f)
        val barW = (wDp * 0.26f).coerceIn(80f, 160f)
        val labelW = text.width(fonts.get(Face.MONO_MEDIUM, 11f), "INK")
        val meterW = labelW + 8f + barW
        rightArea.set(settingsButton.x - 10f - meterW, top, meterW + 10f + 40f, 40f)
    }

    private fun barBox(wDp: Float): Box {
        val barW = (wDp * 0.26f).coerceIn(80f, 160f)
        return tmpBar.set(settingsButton.x - 10f - barW, top + 15f, barW, 10f)
    }

    private val tmpBar = Box()

    /** Chip scale for the "bump" when the combo grows (CSS .bump: 1.25 with a 0.15 s transition). */
    private fun chipScale(): Float {
        if (bump <= 0f) return 1f
        val t = BUMP_S - bump
        val up = min(1f, t / 0.15f)
        val down = if (t > 0.16f) min(1f, (t - 0.16f) / 0.15f) else 0f
        return 1f + 0.25f * (up - down)
    }

    fun drawShapes(shapes: Shapes, pal: Palette, wDp: Float, ink: Float, combo: Int, pressedSettings: Boolean) {
        // combo chip
        val hot = combo > 1
        val s = chipScale()
        val cw = chip.w * s
        val ch = chip.h * s
        val cx = chip.x + chip.w / 2f - cw / 2f
        val cy = chip.y + chip.h / 2f - ch / 2f
        shapes.color(if (hot) pal[Tok.ROPE] else pal[Tok.LINE]).roundRectOutline(cx, cy, cw, ch, ch / 2f, 1.5f * s)
        // ink meter
        val bar = barBox(wDp)
        shapes.color(pal[Tok.RULE]).roundRect(bar.x, bar.y, bar.w, bar.h, 5f)
        val v = (ink / GameSim.INK_MAX).coerceIn(0f, 1f)
        val fw = bar.w * v
        if (fw > 0.5f) {
            shapes.color(if (ink < 20f) pal[Tok.BEAM] else pal[Tok.DROP])
            // the fill is clipped by the rounded bar: round on the left, square on the right
            if (fw >= 5f) {
                shapes.pie(bar.x + 5f, bar.y + 5f, 5f, 90f, 180f)
                shapes.rect(bar.x + 5f, bar.y, min(fw, bar.w - 5f) - 5f, bar.h)
                if (fw > bar.w - 5f) shapes.pie(bar.x + bar.w - 5f, bar.y + 5f, 5f, 270f, 180f)
            } else {
                shapes.roundRect(bar.x, bar.y + (5f - fw / 2f).coerceAtLeast(0f), fw, min(bar.h, fw), fw / 2f)
            }
        }
        // settings button
        val b = settingsButton
        shapes.color(pal[Tok.LINE]).circle(b.x + 20f, b.y + 20f, 20f)
        shapes.color(pal[Tok.UI]).circle(b.x + 20f, b.y + 20f, 19f)
        if (pressedSettings) shapes.color(pal[Tok.INK], 0.08f).circle(b.x + 20f, b.y + 20f, 20f)
        shapes.color(pal[Tok.UI_FG])
        for (i in -1..1) shapes.circle(b.x + 20f + i * 5.5f, b.y + 20f, 1.7f)
    }

    fun drawText(text: Text, fonts: Fonts, pal: Palette, wDp: Float, combo: Int) {
        text.line(fonts.get(Face.HAND_BOLD, 40f, Fonts.DIGITS), scoreText, left, top, 40f, pal[Tok.INK])
        val hot = combo > 1
        val comboFont = fonts.get(Face.MONO_BOLD, 16f)
        val s = chipScale()
        if (s != 1f) comboFont.data.setScale(comboFont.data.scaleX * s)
        text.line(comboFont, comboText, chip.x + chip.w / 2f, chip.y + chip.h / 2f - 8f, 16f, if (hot) pal[Tok.ROPE] else pal[Tok.MUTED], align = Align.center)
        if (s != 1f) comboFont.data.setScale(comboFont.data.scaleX / s)
        text.line(fonts.get(Face.MONO_MEDIUM, 11f), bestText, left, top + 40f, 16f, pal[Tok.MUTED])
        val bar = barBox(wDp)
        text.line(fonts.get(Face.MONO_MEDIUM, 11f), "INK", bar.x - 8f, bar.y - 3f, 16f, pal[Tok.MUTED], align = Align.right)
    }

    companion object {
        private const val BUMP_S = 0.31f
    }
}

object Format {
    /** en-US grouping (toLocaleString) without allocating a formatter per call. */
    fun thousands(n: Int): String {
        val s = kotlin.math.abs(n).toString()
        if (s.length <= 3) return if (n < 0) "-$s" else s
        val sb = StringBuilder(s.length + s.length / 3 + 1)
        if (n < 0) sb.append('-')
        val first = s.length % 3
        if (first > 0) sb.append(s, 0, first)
        var i = first
        while (i < s.length) {
            if (sb.isNotEmpty() && sb.last() != '-') sb.append(',')
            sb.append(s, i, i + 3)
            i += 3
        }
        return sb.toString()
    }
}
