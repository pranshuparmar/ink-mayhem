package com.pranshuparmar.inkmayhem.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Align

/** Text drawing for y-down cameras with a reused GlyphLayout, so steady-state frames don't allocate. */
class Text(val batch: SpriteBatch) {
    val layout = GlyphLayout()
    private val tmp = Color()

    fun width(font: BitmapFont, s: CharSequence): Float {
        layout.setText(font, s)
        return layout.width
    }

    /**
     * Draws [s] with its cap-height top at [top]. With [wrapWidth] > 0 the text wraps (and aligns)
     * inside that width. Returns the laid-out height.
     */
    fun draw(
        font: BitmapFont, s: CharSequence, x: Float, top: Float, color: Color, alpha: Float = 1f,
        align: Int = Align.left, wrapWidth: Float = 0f,
    ): Float {
        tmp.set(color).a *= alpha
        val wrap = wrapWidth > 0f
        layout.setText(font, s, 0, s.length, tmp, if (wrap) wrapWidth else 0f, if (wrap) align else Align.left, wrap, null)
        val ax = if (!wrap) when {
            Align.isCenterHorizontal(align) -> x - layout.width / 2f
            Align.isRight(align) -> x - layout.width
            else -> x
        } else x
        font.draw(batch, layout, ax, top)
        return layout.height
    }

    /** Draws one line vertically centred in a CSS-style line box starting at [boxTop]. */
    fun line(
        font: BitmapFont, s: CharSequence, x: Float, boxTop: Float, boxHeight: Float, color: Color,
        alpha: Float = 1f, align: Int = Align.left,
    ): Float {
        val top = boxTop + (boxHeight - font.capHeight) / 2f
        draw(font, s, x, top, color, alpha, align)
        return layout.width
    }
}
