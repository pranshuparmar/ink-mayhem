package com.pranshuparmar.inkmayhem.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.Align
import com.pranshuparmar.inkmayhem.render.BuddyDrawer
import com.pranshuparmar.inkmayhem.render.BuddyState
import com.pranshuparmar.inkmayhem.render.Expr
import com.pranshuparmar.inkmayhem.render.Face
import com.pranshuparmar.inkmayhem.render.Fonts
import com.pranshuparmar.inkmayhem.render.Grab
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok

/**
 * A result to share, drawn as a notebook page: the moment it happened (a picture of the page) on
 * top, then what was done, the stars, a few numbers, and the game's name with Dot at the foot.
 * Drawn off screen at the screen's own density, so the type is as sharp as in the game.
 */
class ShareCard(private val fonts: Fonts) {
    class Content(
        /** Small capitals over the title: "WORLD 1-3 · SEESAW LANE". */
        val heading: String,
        val title: String,
        /** Stars earned out of three, or -1 for no row of stars. */
        val stars: Int,
        /** Gold stars found (a bit each), or -1 for none shown. */
        val gold: Int,
        val lines: List<Pair<String, String>>,
    )

    private val buddy = BuddyState().also { it.set(Expr.PROUD) }

    /**
     * Draws [c] over [page] (the moment, or null), its picture framed round ([focusX], [focusY])
     * in the page's pixels (Dot, usually), and returns the card. Call on the GL thread, outside
     * other drawing.
     */
    fun render(
        shapes: Shapes, text: Text, batch: SpriteBatch, pal: Palette, density: Float, page: Pixmap?, c: Content,
        focusX: Float = Float.NaN, focusY: Float = Float.NaN,
    ): Pixmap {
        val wPx = (W * density).toInt()
        val hPx = (H * density).toInt()
        val fbo = FrameBuffer(Pixmap.Format.RGBA8888, wPx, hPx, false)
        val cam = OrthographicCamera().apply { setToOrtho(true, W, H); update() }
        val tex = page?.let { Texture(it) }
        try {
            fbo.begin()
            val paper = pal[Tok.PAPER]
            Gdx.gl.glClearColor(paper.r, paper.g, paper.b, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            shapes.begin(cam.combined, density)
            // ruled paper with a margin
            var y = 34f
            while (y < H) {
                shapes.color(pal[Tok.RULE]).rect(0f, y, W, 1f)
                y += RULE
            }
            shapes.color(pal[Tok.MARGIN], 0.7f).rect(MARGIN_X, 0f, 1.2f, H)
            // the picture's frame
            shapes.color(pal[Tok.LINE]).roundRect(PIC_X - 2f, PIC_Y - 2f, PIC_W + 4f, PIC_H + 4f, 12f)
            shapes.color(pal[Tok.UI]).roundRect(PIC_X - 1f, PIC_Y - 1f, PIC_W + 2f, PIC_H + 2f, 11f)
            shapes.end()
            if (tex != null) {
                batch.projectionMatrix = cam.combined
                batch.begin()
                batch.setColor(1f, 1f, 1f, 1f)
                // the middle of the picture at the frame's shape
                val want = PIC_W / PIC_H
                val tw = tex.width.toFloat()
                val th = tex.height.toFloat()
                val sw: Float
                val sh: Float
                if (tw / th > want) { sh = th; sw = th * want } else { sw = tw; sh = tw / want }
                // round the focus (a little ahead of it: what Dot rolled through is behind it), kept inside the page
                val fx = if (focusX.isNaN()) tw / 2f else focusX + sw * 0.1f
                val fy = if (focusY.isNaN()) th / 2f else focusY
                val sx = (fx - sw / 2f).coerceIn(0f, tw - sw).toInt()
                val sy = (fy - sh / 2f).coerceIn(0f, th - sh).toInt()
                batch.draw(tex, PIC_X, PIC_Y, PIC_W, PIC_H, sx, sy, sw.toInt(), sh.toInt(), false, true)
                batch.end()
            }
            shapes.begin(cam.combined, density)
            var row = PIC_Y + PIC_H + 16f + 18f + 40f
            if (c.stars >= 0) {
                for (i in 0 until 3) {
                    val cx = W / 2f + (i - 1) * 46f
                    val cy = row + 18f
                    shapes.color(if (i < c.stars) pal[Tok.STAR] else pal[Tok.MUTED], if (i < c.stars) 1f else 0.5f).star(cx, cy, 17f)
                    if (i >= c.stars) shapes.color(pal[Tok.PAPER]).star(cx, cy, 11f)
                }
                row += 44f
            }
            if (c.gold >= 0) {
                for (i in 0 until 3) {
                    val cx = W - SIDE - 8f - (2 - i) * 20f
                    val cy = row + c.lines.size * LINE_H + 11f
                    if (c.gold and (1 shl i) != 0) shapes.color(pal[Tok.STAR]).star(cx, cy, 8f)
                    else {
                        shapes.color(pal[Tok.MUTED], 0.6f).star(cx, cy, 8f)
                        shapes.color(pal[Tok.PAPER]).star(cx, cy, 4.6f)
                    }
                }
            }
            BuddyDrawer(shapes).draw(pal, buddy, SIDE + 14f, H - 30f, 14f, 1f, 1f)
            shapes.end()
            batch.projectionMatrix = cam.combined
            batch.begin()
            val top = PIC_Y + PIC_H + 16f
            text.line(fonts.get(Face.MONO_MEDIUM, 11f), c.heading, W / 2f, top, 18f, pal[Tok.MUTED], 1f, Align.center)
            text.line(fonts.get(Face.HAND_BOLD, 36f), c.title, W / 2f, top + 18f, 40f, pal[Tok.INK], 1f, Align.center)
            var ly = PIC_Y + PIC_H + 16f + 18f + 40f + if (c.stars >= 0) 44f else 0f
            val label = fonts.get(Face.MONO_MEDIUM, 12f)
            val value = fonts.get(Face.MONO_BOLD, 15f)
            for ((k, v) in c.lines) {
                text.line(label, k, SIDE, ly, LINE_H, pal[Tok.MUTED])
                text.line(value, v, W - SIDE, ly, LINE_H, pal[Tok.INK], 1f, Align.right)
                ly += LINE_H
            }
            if (c.gold >= 0) text.line(label, "GOLD STARS", SIDE, ly, LINE_H, pal[Tok.MUTED])
            text.line(fonts.get(Face.HAND_BOLD, 20f), "Ink Mayhem", SIDE + 36f, H - 46f, 24f, pal[Tok.INK])
            text.line(fonts.get(Face.UI, 11f), "Draw the way ahead with your S Pen.", SIDE + 36f, H - 24f, 14f, pal[Tok.MUTED])
            batch.end()
            return Grab.pixmap(0, 0, wPx, hPx)
        } finally {
            fbo.end()
            fbo.dispose()
            tex?.dispose()
        }
    }

    companion object {
        /** The card in dp (4:5, a shape most apps show whole). */
        const val W = 360f
        const val H = 450f
        private const val RULE = 26f
        private const val MARGIN_X = 14f
        private const val SIDE = 26f
        private const val PIC_X = 20f
        private const val PIC_Y = 20f
        private const val PIC_W = 320f
        private const val PIC_H = 160f
        private const val LINE_H = 22f
    }
}
