package com.pranshuparmar.inkmayhem.render

import com.badlogic.gdx.graphics.OrthographicCamera
import com.pranshuparmar.inkmayhem.fastEach
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Align
import com.pranshuparmar.inkmayhem.game.GameSim
import com.pranshuparmar.inkmayhem.game.GameState
import com.pranshuparmar.inkmayhem.game.Theme
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

    private var alpha = 1f

    /**
     * The tutorial's guide: a dotted line from (x0, y0) to (x1, y1) in metres to draw along, in
     * [guideTok], or a dashed ring round (x0, y0) when [guideRing]. Off when [guideOn] is false.
     */
    var guideOn = false
    var guideRing = false
    val guide = FloatArray(4)
    var guideTok = Tok.ROPE

    /** Hover nib position in screen dp (set by the input router). */
    var hoverOn = false
    var hoverX = 0f
    var hoverY = 0f

    /** Interpolated camera of the last frame without shake: pen input maps through this (POC toWorld). */
    var camX = 0f
        private set
    var camY = 0f
        private set
    /** The same plus screen shake: what the world is drawn with. */
    private var viewX = 0f
    private var viewY = 0f

    /**
     * [alpha] (0..1) is how far the frame sits between the previous and the latest sim tick: moving
     * bodies and the camera are interpolated so 120 Hz displays scroll smoothly over a 60 Hz sim.
     */
    fun render(
        sim: GameSim, fx: Fx, pal: Palette, tint: Boolean, uiCam: OrthographicCamera,
        wDp: Float, hDp: Float, scale: Float, density: Float, alpha: Float,
    ) {
        this.alpha = alpha
        val px = 1f / scale // one dp in metres
        val vw = wDp / scale
        val vh = hDp / scale
        camX = sim.camPrev.x + (sim.cam.x - sim.camPrev.x) * alpha
        camY = sim.camPrev.y + (sim.cam.y - sim.camPrev.y) * alpha
        viewX = camX + fx.shakeOffset()
        viewY = camY + fx.shakeOffset()
        worldCam.setToOrtho(true, vw, vh)
        worldCam.position.set(viewX + vw / 2f, viewY + vh / 2f, 0f)
        worldCam.update()
        val vx0 = sim.cam.x - 2f
        val vx1 = sim.cam.x + vw + 2f

        // the paper scrolls with the page (ruled, graph or dot grid by theme)
        val theme = sim.stage?.theme ?: Theme.PAGE
        shapes.begin(uiCam.combined, density)
        shapes.color(pal[Tok.RULE])
        val gap = 30f
        val off = (((-camY * scale) % gap) + gap) % gap
        val offX = (((-camX * scale) % gap) + gap) % gap
        when (theme) {
            Theme.PAGE, Theme.CASTLE -> {
                var y = off
                while (y < hDp) {
                    shapes.rect(0f, floor(y), wDp, 1f)
                    y += gap
                }
            }
            Theme.CELLAR -> {
                var y = off
                while (y < hDp) {
                    shapes.rect(0f, floor(y), wDp, 1f)
                    y += gap
                }
                var x = offX
                while (x < wDp) {
                    shapes.rect(floor(x), 0f, 1f, hDp)
                    x += gap
                }
            }
            Theme.SKY -> {
                var y = off
                while (y < hDp) {
                    var x = offX
                    while (x < wDp) {
                        shapes.circle(x, y, 1.4f)
                        x += gap
                    }
                    y += gap
                }
            }
        }
        // (the POC's fixed red margin line is left out: it read as a line chasing the runner)
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
            text.draw(noteFont, n.text, 0f, -noteFont.capHeight, pal[Tok.GRAPHITE], wrapWidth = min(maxW, n.maxW * scale))
        }
        text.batch.transformMatrix = identity
        text.batch.end()

        shapes.begin(worldCam.combined, density * scale)
        val brick = theme == Theme.CASTLE
        drawFlags(sim, pal, px, behind = true)
        sim.blocks.fastEach { r -> drawBlock(r, pal, vx0, vx1, sim.cam.y + vh + 2f, px, brick) }
        sim.ceilings.fastEach { c -> drawCeiling(c, pal, vx0, vx1, sim.cam.y - 2f, px, brick) }
        sim.spikeStrips.fastEach { k -> drawSpikes(k, pal, vx0, vx1) }
        if (guideOn) drawGuide(sim, pal, px)
        drawLifts(sim, pal, px)
        drawSeesaws(sim, pal, px)
        drawFlags(sim, pal, px, behind = false)
        drawDrops(sim, pal, vx0, vx1, px)
        drawStrokes(sim, pal, tint, vx0, vx1, px)
        drawLiveStroke(sim, pal, tint)
        drawBoulders(sim, pal, px)
        drawRunner(sim, pal, px)
        fx.particles.fastEach { q ->
            shapes.color(pal[q.color], min(1f, q.life * 2f)).circle(q.x, q.y, q.r)
        }
        shapes.end()

        // score pops
        text.batch.projectionMatrix = uiCam.combined
        text.batch.begin()
        val popFont = fonts.get(Face.HAND_BOLD, max(18f, scale * 0.75f))
        fx.pops.fastEach { q ->
            val sx = (q.x - viewX) * scale
            val sy = (q.y - viewY) * scale
            text.draw(popFont, q.text, sx, sy - popFont.capHeight, pal[q.color], min(1f, q.life * 1.5f), Align.center)
        }
        text.batch.end()

        // off-screen boulder warning on the left edge
        shapes.begin(worldCam.combined, density * scale)
        val pulse = 0.6f + 0.4f * sin(sim.simT * 12f)
        for (bi in sim.boulders.indices) {
            val q = sim.boulders[bi].body.position
            if (q.x + GameSim.BOULDER_R > sim.cam.x + 0.2f || q.y > sim.cam.y + vh) continue
            val yy = q.y.coerceIn(sim.cam.y + 2f, sim.cam.y + vh - 2f)
            val xx = sim.cam.x + 0.5f
            shapes.color(pal[Tok.BEAM], pulse).triangle(xx - 0.25f, yy, xx + 0.35f, yy - 0.4f, xx + 0.35f, yy + 0.4f)
        }
        shapes.end()
        text.batch.projectionMatrix = uiCam.combined
        text.batch.begin()
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

        // hover nib shadow: placed with the unshaken camera, drawn in the shaking world (POC)
        if (hoverOn && sim.drawing == null && sim.erasingPointer < 0 && sim.state == GameState.RUN) {
            val mx = hoverX / scale + camX
            val my = hoverY / scale + camY
            shapes.begin(worldCam.combined, density * scale)
            shapes.color(pal[Tok.INK]).ring(mx, my, Ink.thickness(0.45f) / 2f + 4f * px, 1.2f * px, 3f * px, 3f * px)
            shapes.color(pal[Tok.INK]).circle(mx, my, 1.6f * px)
            shapes.end()
        }
    }

    private fun drawBlock(r: GroundRect, pal: Palette, vx0: Float, vx1: Float, screenBottom: Float, px: Float, brick: Boolean) {
        if (r.x > vx1 || r.x + r.w < vx0) return
        val bottom = min(r.y + r.h, screenBottom)
        val h = bottom - r.y
        if (h <= 0f) return
        if (r.sloped) {
            drawSlope(r, pal, vx0, vx1, bottom, px)
            return
        }
        shapes.color(pal[Tok.ROCK]).rect(r.x, r.y, r.w, h)
        if (brick) {
            bricks(r.x, r.y, r.w, h, max(r.x, vx0), min(r.x + r.w, vx1), pal, px)
        } else {
            shapes.color(pal[Tok.GRAPHITE], 0.32f)
            val a0 = max(r.x, vx0)
            val a1 = min(r.x + r.w, vx1)
            var d = floor((a0 - h - r.x) / HATCH) * HATCH + r.x
            while (d < a1) {
                hatch(d, r.y + h, h, r.x, r.x + r.w, px)
                d += HATCH
            }
        }
        val lw = 1.6f * px
        shapes.color(pal[Tok.GRAPHITE])
        sideEdge(r.x, r.y - lw / 2f, bottom, r.edgeLTo, r.edgeLFrom, lw)
        shapes.line(r.x, r.y, r.x + r.w, r.y, lw)
        sideEdge(r.x + r.w, r.y - lw / 2f, bottom, r.edgeRTo, r.edgeRFrom, lw)
        // floating ledges show their underside
        if (r.y + r.h <= screenBottom) shapes.line(r.x - lw / 2f, r.y + r.h, r.x + r.w + lw / 2f, r.y + r.h, lw)
    }

    /** A block's side from [top] to [bottom], minus the stretch a flush neighbour covers. */
    private fun sideEdge(x: Float, top: Float, bottom: Float, coverTop: Float, coverBottom: Float, lw: Float) {
        if (coverTop.isNaN()) {
            shapes.line(x, top, x, bottom, lw)
            return
        }
        if (coverTop > top) shapes.line(x, top, x, min(coverTop, bottom), lw)
        if (coverBottom < bottom) shapes.line(x, max(coverBottom, top), x, bottom, lw)
    }

    private val slopePoly = FloatArray(8)

    /** A hill side: a quad with a sloped top, hatched below the slope. */
    private fun drawSlope(r: GroundRect, pal: Palette, vx0: Float, vx1: Float, bottom: Float, px: Float) {
        val x1 = r.x + r.w
        slopePoly[0] = r.x; slopePoly[1] = r.y
        slopePoly[2] = x1; slopePoly[3] = r.yR
        slopePoly[4] = x1; slopePoly[5] = bottom
        slopePoly[6] = r.x; slopePoly[7] = bottom
        shapes.color(pal[Tok.ROCK]).fan(slopePoly, 4)
        shapes.color(pal[Tok.GRAPHITE], 0.32f)
        val k = (r.yR - r.y) / r.w
        val h = bottom - min(r.y, r.yR)
        val a0 = max(r.x, vx0)
        val a1 = min(x1, vx1)
        var d = floor((a0 - h - r.x) / HATCH) * HATCH + r.x
        while (d < a1) {
            // the hatch rises from (d, bottom) at 45° until it meets the slope
            val xm = (bottom + d - r.y + k * r.x) / (1f + k)
            val s0 = max(0f, r.x - d)
            val s1 = minOf(bottom - min(r.y, r.yR), x1 - d, xm - d)
            if (s1 > s0) shapes.line(d + s0, bottom - s0, d + s1, bottom - s1, px)
            d += HATCH
        }
        val lw = 1.6f * px
        shapes.color(pal[Tok.GRAPHITE])
        sideEdge(r.x, r.y, bottom, r.edgeLTo, r.edgeLFrom, lw)
        shapes.capsule(r.x, r.y, x1, r.yR, lw)
        sideEdge(x1, r.yR, bottom, r.edgeRTo, r.edgeRFrom, lw)
    }

    /** Castle masonry: mortar rows every 0.5 m, joints every 1 m, offset on alternate rows. */
    private fun bricks(x: Float, y: Float, w: Float, h: Float, a0: Float, a1: Float, pal: Palette, px: Float) {
        shapes.color(pal[Tok.GRAPHITE], 0.3f)
        val rowH = 0.5f
        var row = 0
        var yy = y + rowH
        while (yy < y + h) {
            shapes.rect(a0, yy - px / 2f, a1 - a0, px)
            yy += rowH
        }
        yy = y
        while (yy < y + h) {
            val off = if (row % 2 == 0) 0f else 0.5f
            var xx = floor((a0 - x - off) / 1f) * 1f + x + off
            while (xx < a1) {
                if (xx > x && xx < x + w) shapes.rect(xx - px / 2f, yy, px, min(rowH, y + h - yy))
                xx += 1f
            }
            yy += rowH
            row++
        }
    }

    /** One 45° hatch line from (d, base) to (d + h, base - h), clipped to x in [x0, x1]. */
    private fun hatch(d: Float, base: Float, h: Float, x0: Float, x1: Float, px: Float) {
        val s0 = max(0f, x0 - d)
        val s1 = min(h, x1 - d)
        if (s1 <= s0) return
        shapes.line(d + s0, base - s0, d + s1, base - s1, px)
    }

    private fun drawCeiling(c: GroundRect, pal: Palette, vx0: Float, vx1: Float, screenTop: Float, px: Float, brick: Boolean) {
        if (c.x > vx1 || c.x + c.w < vx0) return
        val top = max(c.y, screenTop)
        val h = c.y + c.h - top
        if (h <= 0f) return
        shapes.color(pal[Tok.ROCK]).rect(c.x, top, c.w, h)
        if (brick) {
            bricks(c.x, top, c.w, h, max(c.x, vx0), min(c.x + c.w, vx1), pal, px)
        } else {
            shapes.color(pal[Tok.GRAPHITE], 0.32f)
            var d = c.x - h
            while (d < c.x + c.w) {
                hatch(d, top + h, h, c.x, c.x + c.w, px)
                d += HATCH
            }
        }
        val lw = 1.6f * px
        shapes.color(pal[Tok.GRAPHITE])
        shapes.line(c.x - lw / 2f, top, c.x + c.w + lw / 2f, top, lw)
        shapes.line(c.x - lw / 2f, top + h, c.x + c.w + lw / 2f, top + h, lw)
        shapes.line(c.x, top, c.x, top + h, lw)
        shapes.line(c.x + c.w, top, c.x + c.w, top + h, lw)
        if (c.kind != com.pranshuparmar.inkmayhem.physics.FixtureKind.CEILING) return // plain cave roof
        val by = c.y + c.h
        shapes.color(pal[Tok.BEAM])
        var x = c.x + 0.2f
        while (x < c.x + c.w - 0.1f) {
            shapes.triangle(x - 0.14f, by, x, by + 0.3f, x + 0.14f, by)
            x += 0.42f
        }
    }

    private fun drawGuide(sim: GameSim, pal: Palette, px: Float) {
        val pulse = 0.55f + 0.35f * sin(sim.realTime.toFloat() * 4f)
        val c = pal[guideTok]
        if (guideRing) {
            shapes.color(c, pulse).ring(guide[0], guide[1], 1.7f, 2.4f * px, 6f * px, 5f * px)
            return
        }
        val x0 = guide[0]; val y0 = guide[1]; val x1 = guide[2]; val y1 = guide[3]
        val len = kotlin.math.hypot(x1 - x0, y1 - y0)
        val n = max(2, (len / 0.28f).toInt())
        shapes.color(c, pulse)
        for (i in 0..n) {
            val u = i / n.toFloat()
            shapes.circle(x0 + (x1 - x0) * u, y0 + (y1 - y0) * u, 0.07f)
        }
        // an arrow at the far end: draw this way
        val ux = (x1 - x0) / len
        val uy = (y1 - y0) / len
        val ax = x1 + ux * 0.35f
        val ay = y1 + uy * 0.35f
        shapes.triangle(ax, ay, ax - ux * 0.35f - uy * 0.22f, ay - uy * 0.35f + ux * 0.22f, ax - ux * 0.35f + uy * 0.22f, ay - uy * 0.35f - ux * 0.22f)
    }

    /** A strip of spikes on a floor (pointing up) or under a ledge (pointing down). */
    private fun drawSpikes(k: GroundRect, pal: Palette, vx0: Float, vx1: Float) {
        if (k.x > vx1 || k.x + k.w < vx0) return
        val up = k.spikesUp
        shapes.color(pal[Tok.BEAM])
        val base = k.y + k.h
        var x = k.x + 0.16f
        while (x < k.x + k.w - 0.1f) {
            if (up) shapes.triangle(x - 0.15f, base, x, k.y, x + 0.15f, base)
            else shapes.triangle(x - 0.15f, k.y, x, base, x + 0.15f, k.y)
            x += 0.32f
        }
    }

    private fun drawLifts(sim: GameSim, pal: Palette, px: Float) {
        val lw = 1.6f * px
        for (li in sim.lifts.indices) {
            val lf = sim.lifts[li]
            val d = lf.def
            val cx = lf.prev.lerpX(alpha)
            val cy = lf.prev.lerpY(alpha)
            val x0 = cx - d.w / 2f
            val y0 = cy - lf.h / 2f
            // the track it runs on, then the platform
            shapes.color(pal[Tok.GRAPHITE], 0.35f)
            var y = d.highY
            while (y < d.lowY + lf.h) {
                shapes.rect(cx - lw / 2f, y, lw, min(0.25f, d.lowY + lf.h - y))
                y += 0.45f
            }
            shapes.color(pal[Tok.ROCK]).rect(x0, y0, d.w, lf.h)
            shapes.color(pal[Tok.GRAPHITE]).roundRectOutline(x0, y0, d.w, lf.h, 0.08f, lw)
            if (lf.dir == 0 && lf.top > d.highY + 0.01f) {
                // waiting: an up arrow on the platform
                shapes.color(pal[Tok.ACCENT])
                shapes.triangle(cx - 0.22f, y0 + lf.h * 0.75f, cx, y0 + lf.h * 0.2f, cx + 0.22f, y0 + lf.h * 0.75f)
            }
        }
    }

    private fun drawSeesaws(sim: GameSim, pal: Palette, px: Float) {
        for (si in sim.seesaws.indices) {
            val sw = sim.seesaws[si]
            val cx = sw.prev.lerpX(alpha)
            val cy = sw.prev.lerpY(alpha)
            val a = sw.prev.lerpA(alpha)
            val hx = cos(a) * sw.len / 2f
            val hy = sin(a) * sw.len / 2f
            shapes.color(pal[Tok.GRAPHITE]).triangle(sw.px, sw.py, sw.px - 0.4f, sw.py + 1.1f, sw.px + 0.4f, sw.py + 1.1f)
            shapes.color(pal[Tok.GRAPHITE]).line(cx - hx, cy - hy, cx + hx, cy + hy, GameSim.SEESAW_THICK)
            shapes.color(pal[Tok.ROCK]).line(cx - hx, cy - hy, cx + hx, cy + hy, GameSim.SEESAW_THICK - 3.2f * px)
            shapes.color(pal[Tok.PAPER]).circle(sw.px, sw.py, 0.07f)
        }
    }

    /**
     * The checkpoint bookmark and the finish flag (with a little doodled castle past it). Poles
     * draw [behind] the ground; flags in front.
     */
    private fun drawFlags(sim: GameSim, pal: Palette, px: Float, behind: Boolean) {
        val l = sim.stage?.layout ?: return
        val lw = 1.6f * px
        if (!l.checkpointX.isNaN()) {
            val x = l.checkpointX
            val y = l.checkpointLevel
            val reached = sim.stageRun?.checkpoint == true
            if (behind) {
                shapes.color(pal[Tok.GRAPHITE]).line(x, y, x, y - 2.4f, lw * 1.4f)
            } else {
                shapes.color(if (reached) pal[Tok.ROPE] else pal[Tok.ACCENT])
                // a bookmark ribbon: a strip with a notch at the bottom
                shapes.rect(x, y - 2.4f, 0.55f, 0.75f)
                shapes.triangle(x, y - 1.65f, x + 0.275f, y - 1.65f, x, y - 1.35f)
                shapes.triangle(x + 0.275f, y - 1.65f, x + 0.55f, y - 1.65f, x + 0.55f, y - 1.35f)
            }
        }
        if (!l.finishX.isNaN()) {
            val x = l.finishX
            val y = l.finishLevel
            if (behind) {
                castle(x + 4f, y, pal, lw)
                shapes.color(pal[Tok.GRAPHITE]).line(x, y, x, y - 3.4f, lw * 1.6f)
                shapes.color(pal[Tok.GRAPHITE]).circle(x, y - 3.45f, 0.12f)
            } else {
                // a pennant, waving a little
                val wave = sin(sim.simT * 4f) * 0.08f
                shapes.color(pal[Tok.BEAM]).triangle(x, y - 3.3f, x + 1.2f, y - 2.95f + wave, x, y - 2.5f)
            }
        }
    }

    /** A hand-drawn tower: walls, battlements and an arched door. */
    private fun castle(x: Float, ground: Float, pal: Palette, lw: Float) {
        val w = 2.6f
        val h = 2.2f
        val top = ground - h
        shapes.color(pal[Tok.ROCK]).rect(x, top, w, h)
        shapes.color(pal[Tok.GRAPHITE])
        shapes.line(x, ground, x, top - 0.5f, lw)
        shapes.line(x + w, ground, x + w, top - 0.5f, lw)
        // battlements
        var bx = x
        var up = true
        while (bx < x + w - 0.01f) {
            val nx = min(x + w, bx + w / 5f)
            val yy = if (up) top - 0.5f else top
            shapes.color(pal[Tok.ROCK]).rect(bx, yy, nx - bx, top - yy + 0.01f)
            shapes.color(pal[Tok.GRAPHITE]).line(bx, yy, nx, yy, lw)
            if (nx < x + w - 0.01f) shapes.line(nx, top - 0.5f, nx, top, lw)
            bx = nx
            up = !up
        }
        // door
        val dx = x + w / 2f
        shapes.color(pal[Tok.GRAPHITE]).rect(dx - 0.4f, ground - 0.9f, 0.8f, 0.9f)
        shapes.circle(dx, ground - 0.9f, 0.4f)
    }

    private fun drawDrops(sim: GameSim, pal: Palette, vx0: Float, vx1: Float, px: Float) {
        for (di in sim.drops.indices) {
            val d = sim.drops[di]
            if (d.taken || d.x < vx0 || d.x > vx1) continue
            val bob = sin(d.t * 3f) * 0.08f
            if (d.heart) {
                shapes.color(pal[Tok.BEAM]).heart(d.x, d.y + bob, 0.34f)
                shapes.color(pal[Tok.BEAM]).ring(d.x, d.y + bob - 0.05f, 0.62f, 1.2f * px, 2f * px, 3f * px)
                continue
            }
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
                val ax = g.lerpX(sg.lax, sg.lay, alpha)
                val ay = g.lerpY(sg.lax, sg.lay, alpha)
                val bx = g.lerpX(sg.lbx, sg.lby, alpha)
                val by = g.lerpY(sg.lbx, sg.lby, alpha)
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
        val alpha = if (d.dry) 0.5f else 1f
        for (i in 1 until n) { // segments only, like the POC: a single point draws nothing
            val p = (d.ps[i] + d.ps[i - 1]) / 2f
            shapes.color(inkColor(pal, Ink.of(p), tint), alpha).capsule(d.xs[i - 1], d.ys[i - 1], d.xs[i], d.ys[i], Ink.thickness(p))
        }
    }

    private fun drawBoulders(sim: GameSim, pal: Palette, px: Float) {
        val r = GameSim.BOULDER_R
        for (bi in sim.boulders.indices) {
            val pose = sim.boulders[bi].prev
            val cx = pose.lerpX(alpha)
            val cy = pose.lerpY(alpha)
            val a = pose.lerpA(alpha)
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
        val pose = sim.runner.prev
        val cx = pose.lerpX(alpha)
        val cy = pose.lerpY(alpha)
        val sq = sim.runner.squash
        val sx = 1f + sq * 0.2f
        val sy = 1f - sq * 0.2f
        val r = GameSim.RUNNER_R
        shapes.color(pal[Tok.INK]).ellipse(cx, cy, r * sx, r * sy)
        shapes.color(pal[Tok.PAPER], 0.35f).arcScaled(cx, cy, r * 0.62f, 2f * px, 0.3f, 1.4f, pose.lerpA(alpha), sx, sy)
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
