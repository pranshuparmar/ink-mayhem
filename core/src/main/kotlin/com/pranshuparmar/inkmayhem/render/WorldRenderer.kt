package com.pranshuparmar.inkmayhem.render

import com.badlogic.gdx.graphics.OrthographicCamera
import com.pranshuparmar.inkmayhem.fastEach
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Align
import com.pranshuparmar.inkmayhem.game.GameSim
import com.pranshuparmar.inkmayhem.game.GameState
import com.pranshuparmar.inkmayhem.physics.GroundRect
import com.pranshuparmar.inkmayhem.physics.Ink
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the notebook page and everything on it, following the POC's render() order. World
 * shapes use a y-down camera in metres; world text is placed through the dp UI camera.
 */
class WorldRenderer(
    private val shapes: Shapes,
    private val text: Text,
    private val fonts: Fonts,
) {
    private val worldCam = OrthographicCamera()
    private val noteMatrix = Matrix4()
    private val identity = Matrix4()
    private val dropPoly = FloatArray(DROP_SHAPE.size)
    private val markerLabel = StringBuilder()

    /** Hover nib position in screen dp (set by the input router). */
    var hoverOn = false
    var hoverX = 0f
    var hoverY = 0f

    /** Camera actually used for the last frame (with shake): input maps through this. */
    var viewX = 0f
        private set
    var viewY = 0f
        private set

    fun render(
        sim: GameSim, fx: Fx, pal: Palette, tint: Boolean, uiCam: OrthographicCamera,
        wDp: Float, hDp: Float, scale: Float, density: Float,
    ) {
        val px = 1f / scale // one dp in metres
        val vw = wDp / scale
        val vh = hDp / scale
        viewX = sim.cam.x + fx.shakeOffset()
        viewY = sim.cam.y + fx.shakeOffset()
        worldCam.setToOrtho(true, vw, vh)
        worldCam.position.set(viewX + vw / 2f, viewY + vh / 2f, 0f)
        worldCam.update()
        val vx0 = sim.cam.x - 2f
        val vx1 = sim.cam.x + vw + 2f

        // ruled lines scroll with the page; the margin stays put
        shapes.begin(uiCam.combined, density)
        shapes.color(pal[Tok.RULE])
        val gap = 30f
        val off = (((-sim.cam.y * scale) % gap) + gap) % gap
        var y = off
        while (y < hDp) {
            shapes.rect(0f, floor(y), wDp, 1f)
            y += gap
        }
        shapes.color(pal[Tok.MARGIN]).rect(min(56f, wDp * 0.12f) - 0.75f, 0f, 1.5f, hDp)
        shapes.end()

        // notes in the margin hand
        text.batch.projectionMatrix = uiCam.combined
        text.batch.begin()
        val noteSize = max(15f, scale * 0.6f)
        val noteFont = fonts.get(Face.HAND, noteSize)
        val maxW = min(wDp * 0.62f, 340f)
        for (ni in sim.notes.indices) {
            val n = sim.notes[ni]
            if (n.x > vx1 || n.x + 12f < vx0) continue
            val sx = (n.x - viewX) * scale
            val sy = (n.y - viewY) * scale
            noteMatrix.idt().translate(sx, sy, 0f).rotateRad(0f, 0f, 1f, -0.03f)
            text.batch.transformMatrix = noteMatrix
            // baseline at the note origin, wrapped like wrapText() with 1.15 line height
            noteFont.data.setLineHeight(noteSize * 1.15f * density)
            text.draw(noteFont, n.text, 0f, -noteFont.capHeight, pal[Tok.GRAPHITE], wrapWidth = maxW)
        }
        text.batch.transformMatrix = identity
        text.batch.end()

        shapes.begin(worldCam.combined, density * scale)
        sim.blocks.fastEach { r -> drawBlock(r, pal, vx0, vx1, sim.cam.y + vh + 2f, px) }
        sim.ceilings.fastEach { c -> drawCeiling(c, pal, vx0, vx1, sim.cam.y - 2f, px) }
        drawDrops(sim, pal, vx0, vx1, px)
        drawStrokes(sim, pal, tint, vx0, vx1, px)
        drawLiveStroke(sim, pal, tint)
        drawBoulders(sim, pal, px)
        drawRunner(sim, pal, px)
        fx.particles.fastEach { q ->
            shapes.color(pal[q.color], min(1f, q.life * 2f)).circle(q.x, q.y, q.r)
        }
        // off-screen boulder warning on the left edge
        val pulse = 0.6f + 0.4f * sin(sim.simT * 12f)
        for (bi in sim.boulders.indices) {
            val q = sim.boulders[bi].body.position
            if (q.x + GameSim.BOULDER_R > sim.cam.x + 0.2f || q.y > sim.cam.y + vh) continue
            val yy = q.y.coerceIn(sim.cam.y + 2f, sim.cam.y + vh - 2f)
            val xx = sim.cam.x + 0.5f
            shapes.color(pal[Tok.BEAM], pulse).triangle(xx - 0.25f, yy, xx + 0.35f, yy - 0.4f, xx + 0.35f, yy + 0.4f)
        }
        // hover nib shadow
        if (hoverOn && sim.drawing == null && sim.erasingPointer < 0 && sim.state == GameState.RUN) {
            val mx = hoverX / scale + viewX
            val my = hoverY / scale + viewY
            shapes.color(pal[Tok.INK]).ring(mx, my, Ink.thickness(0.45f) / 2f + 4f * px, 1.2f * px, 3f * px, 3f * px)
            shapes.color(pal[Tok.INK]).circle(mx, my, 1.6f * px)
        }
        shapes.end()

        // score pops and the boulder distance label
        text.batch.projectionMatrix = uiCam.combined
        text.batch.begin()
        val popFont = fonts.get(Face.HAND_BOLD, max(18f, scale * 0.75f))
        fx.pops.fastEach { q ->
            val sx = (q.x - viewX) * scale
            val sy = (q.y - viewY) * scale
            text.draw(popFont, q.text, sx, sy - popFont.capHeight, pal[q.color], min(1f, q.life * 1.5f), Align.center)
        }
        val markerFont = fonts.get(Face.MONO_BOLD, max(14f, scale * 0.5f))
        for (bi in sim.boulders.indices) {
            val q = sim.boulders[bi].body.position
            if (q.x + GameSim.BOULDER_R > sim.cam.x + 0.2f || q.y > sim.cam.y + vh) continue
            val yy = q.y.coerceIn(sim.cam.y + 2f, sim.cam.y + vh - 2f)
            val xx = sim.cam.x + 0.55f
            markerLabel.setLength(0)
            markerLabel.append("BOULDER ").append(max(0, (sim.runner.body.position.x - q.x).roundToInt())).append(" m")
            text.draw(markerFont, markerLabel, (xx + 0.5f - viewX) * scale, (yy - viewY) * scale - markerFont.capHeight / 2f, pal[Tok.BEAM], pulse)
        }
        text.batch.end()
    }

    private fun drawBlock(r: GroundRect, pal: Palette, vx0: Float, vx1: Float, screenBottom: Float, px: Float) {
        if (r.x > vx1 || r.x + r.w < vx0) return
        val bottom = min(r.y + r.h, screenBottom)
        val h = bottom - r.y
        if (h <= 0f) return
        shapes.color(pal[Tok.ROCK]).rect(r.x, r.y, r.w, h)
        shapes.color(pal[Tok.GRAPHITE], 0.32f)
        val a0 = max(r.x, vx0)
        val a1 = min(r.x + r.w, vx1)
        var d = floor((a0 - h - r.x) / HATCH) * HATCH + r.x
        while (d < a1) {
            hatch(d, r.y + h, h, r.x, r.x + r.w, px)
            d += HATCH
        }
        val lw = 1.6f * px
        shapes.color(pal[Tok.GRAPHITE])
        shapes.line(r.x, bottom, r.x, r.y - lw / 2f, lw)
        shapes.line(r.x, r.y, r.x + r.w, r.y, lw)
        shapes.line(r.x + r.w, r.y - lw / 2f, r.x + r.w, bottom, lw)
    }

    /** One 45° hatch line from (d, base) to (d + h, base - h), clipped to x in [x0, x1]. */
    private fun hatch(d: Float, base: Float, h: Float, x0: Float, x1: Float, px: Float) {
        val s0 = max(0f, x0 - d)
        val s1 = min(h, x1 - d)
        if (s1 <= s0) return
        shapes.line(d + s0, base - s0, d + s1, base - s1, px)
    }

    private fun drawCeiling(c: GroundRect, pal: Palette, vx0: Float, vx1: Float, screenTop: Float, px: Float) {
        if (c.x > vx1 || c.x + c.w < vx0) return
        val top = max(c.y, screenTop)
        val h = c.y + c.h - top
        if (h <= 0f) return
        shapes.color(pal[Tok.ROCK]).rect(c.x, top, c.w, h)
        shapes.color(pal[Tok.GRAPHITE], 0.32f)
        var d = c.x - h
        while (d < c.x + c.w) {
            hatch(d, top + h, h, c.x, c.x + c.w, px)
            d += HATCH
        }
        val lw = 1.6f * px
        shapes.color(pal[Tok.GRAPHITE])
        shapes.line(c.x - lw / 2f, top, c.x + c.w + lw / 2f, top, lw)
        shapes.line(c.x - lw / 2f, top + h, c.x + c.w + lw / 2f, top + h, lw)
        shapes.line(c.x, top, c.x, top + h, lw)
        shapes.line(c.x + c.w, top, c.x + c.w, top + h, lw)
        val by = c.y + c.h
        shapes.color(pal[Tok.BEAM])
        var x = c.x + 0.2f
        while (x < c.x + c.w - 0.1f) {
            shapes.triangle(x - 0.14f, by, x, by + 0.3f, x + 0.14f, by)
            x += 0.42f
        }
    }

    private fun drawDrops(sim: GameSim, pal: Palette, vx0: Float, vx1: Float, px: Float) {
        for (di in sim.drops.indices) {
            val d = sim.drops[di]
            if (d.taken || d.x < vx0 || d.x > vx1) continue
            val bob = sin(d.t * 3f) * 0.08f
            val s = if (d.high) 0.36f else 0.26f
            val cx = d.x
            val cy = d.y + bob
            for (i in DROP_SHAPE.indices step 2) {
                dropPoly[i] = cx + DROP_SHAPE[i] * s
                dropPoly[i + 1] = cy + DROP_SHAPE[i + 1] * s
            }
            shapes.color(pal[Tok.DROP]).fan(dropPoly, DROP_SHAPE.size / 2)
            if (d.high) shapes.ring(cx, cy - s * 0.2f, s * 1.8f, 1.2f * px, 2f * px, 3f * px)
        }
    }

    private fun inkColor(pal: Palette, kind: Ink, tint: Boolean) = if (!tint) pal[Tok.INK] else when (kind) {
        Ink.ROPE -> pal[Tok.ROPE]
        Ink.SPRING -> pal[Tok.SPRING]
        Ink.BEAM -> pal[Tok.BEAM]
    }

    private fun drawStrokes(sim: GameSim, pal: Palette, tint: Boolean, vx0: Float, vx1: Float, px: Float) {
        for (si in sim.ink.strokes.indices) {
            val s = sim.ink.strokes[si]
            for (gi in s.segs.indices) {
                val sg = s.segs[gi]
                val g = sg.group
                if (g.body == null) continue
                val ax = g.worldX(sg.lax, sg.lay)
                val ay = g.worldY(sg.lax, sg.lay)
                val bx = g.worldX(sg.lbx, sg.lby)
                val by = g.worldY(sg.lbx, sg.lby)
                if (max(ax, bx) < vx0 || min(ax, bx) > vx1) continue
                shapes.color(inkColor(pal, sg.kind, tint)).capsule(ax, ay, bx, by, sg.t)
                if (sg.kind == Ink.SPRING && !tint) {
                    // springs carry a little zig-zag so they read as bouncy
                    shapes.color(pal[Tok.PAPER]).zigzag(ax, ay, bx, by, 3, sg.t * 0.28f, 1.2f * px)
                }
            }
            for (ai in s.anchors.indices) {
                val an = s.anchors[ai]
                if (an.joint.broken) continue
                shapes.color(pal[Tok.GRAPHITE]).circle(an.x, an.y, 3.2f * px + 0.75f * px)
                shapes.color(pal[Tok.PAPER]).circle(an.x, an.y, 3.2f * px - 0.75f * px)
            }
        }
    }

    private fun drawLiveStroke(sim: GameSim, pal: Palette, tint: Boolean) {
        val d = sim.drawing ?: return
        val n = d.size
        if (n == 0) return
        val alpha = if (d.dry) 0.5f else 1f
        if (n == 1) {
            shapes.color(inkColor(pal, Ink.of(d.ps[0]), tint), alpha).circle(d.xs[0], d.ys[0], Ink.thickness(d.ps[0]) / 2f)
            return
        }
        for (i in 1 until n) {
            val p = (d.ps[i] + d.ps[i - 1]) / 2f
            shapes.color(inkColor(pal, Ink.of(p), tint), alpha).capsule(d.xs[i - 1], d.ys[i - 1], d.xs[i], d.ys[i], Ink.thickness(p))
        }
    }

    private fun drawBoulders(sim: GameSim, pal: Palette, px: Float) {
        val r = GameSim.BOULDER_R
        for (bi in sim.boulders.indices) {
            val b = sim.boulders[bi].body
            val cx = b.position.x
            val cy = b.position.y
            val a = b.angle
            val ca = cos(a)
            val sa = sin(a)
            shapes.color(pal[Tok.ROCK]).circle(cx, cy, r)
            shapes.color(pal[Tok.GRAPHITE], 0.55f)
            var d = -2f
            while (d < 2f) {
                // hatch from (d, -1) to (d + 0.6, 1) in the boulder's frame, clipped to the disc
                if (clipToDisc(d, -1f, d + 0.6f, 1f, r)) {
                    val x0 = d + 0.6f * clipU0; val y0 = -1f + 2f * clipU0
                    val x1 = d + 0.6f * clipU1; val y1 = -1f + 2f * clipU1
                    shapes.line(cx + ca * x0 - sa * y0, cy + sa * x0 + ca * y0, cx + ca * x1 - sa * y1, cy + sa * x1 + ca * y1, px)
                }
                d += 0.22f
            }
            shapes.color(pal[Tok.INK]).ring(cx, cy, r, 2f * px)
            val crack = CRACK
            for (i in 0 until crack.size / 2 - 1) {
                val x0 = crack[2 * i]; val y0 = crack[2 * i + 1]
                val x1 = crack[2 * i + 2]; val y1 = crack[2 * i + 3]
                shapes.capsule(cx + ca * x0 - sa * y0, cy + sa * x0 + ca * y0, cx + ca * x1 - sa * y1, cy + sa * x1 + ca * y1, 2f * px)
            }
        }
    }

    private var clipU0 = 0f
    private var clipU1 = 0f

    /** Clips segment A→B to the disc of radius r at the origin; the range lands in clipU0..clipU1. */
    private fun clipToDisc(ax: Float, ay: Float, bx: Float, by: Float, r: Float): Boolean {
        val dx = bx - ax
        val dy = by - ay
        val a = dx * dx + dy * dy
        val b = 2f * (ax * dx + ay * dy)
        val c = ax * ax + ay * ay - r * r
        val disc = b * b - 4f * a * c
        if (disc <= 0f) return false
        val sq = sqrt(disc)
        clipU0 = max(0f, (-b - sq) / (2f * a))
        clipU1 = min(1f, (-b + sq) / (2f * a))
        return clipU1 > clipU0
    }

    private fun drawRunner(sim: GameSim, pal: Palette, px: Float) {
        val b = sim.runner.body
        val cx = b.position.x
        val cy = b.position.y
        val sq = sim.runner.squash
        val sx = 1f + sq * 0.2f
        val sy = 1f - sq * 0.2f
        val r = GameSim.RUNNER_R
        shapes.color(pal[Tok.INK]).ellipse(cx, cy, r * sx, r * sy)
        shapes.color(pal[Tok.PAPER], 0.35f).arcScaled(cx, cy, r * 0.62f, 2f * px, 0.3f, 1.4f, b.angle, sx, sy)
        val eye = if (sim.state == GameState.DEAD) 0.05f else r * 0.14f
        shapes.color(pal[Tok.PAPER])
        shapes.ellipse(cx + r * 0.2f * sx, cy - r * 0.25f * sy, eye * sx, eye * sy)
        shapes.ellipse(cx + r * 0.58f * sx, cy - r * 0.25f * sy, eye * sx, eye * sy)
    }

    companion object {
        private const val HATCH = 0.35f
        private val CRACK = floatArrayOf(-0.3f, -0.2f, 0.1f, 0.25f, 0.4f, 0.05f)

        /** Unit teardrop from the POC's two cubic Béziers, tessellated once. */
        private val DROP_SHAPE: FloatArray = run {
            val pts = ArrayList<Float>()
            fun bez(p0x: Float, p0y: Float, c1x: Float, c1y: Float, c2x: Float, c2y: Float, p1x: Float, p1y: Float) {
                for (i in 0 until 12) {
                    val t = i / 12f
                    val u = 1 - t
                    pts += u * u * u * p0x + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * p1x
                    pts += u * u * u * p0y + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * p1y
                }
            }
            bez(0f, -1.5f, 0.9f, -0.4f, 1f, 0.4f, 0f, 1f)
            bez(0f, 1f, -1f, 0.4f, -0.9f, -0.4f, 0f, -1.5f)
            pts.toFloatArray()
        }
    }
}
