package com.pranshuparmar.inkmayhem.render

import com.badlogic.gdx.graphics.OrthographicCamera
import com.pranshuparmar.inkmayhem.fastEach
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Align
import com.pranshuparmar.inkmayhem.game.GameSim
import com.pranshuparmar.inkmayhem.game.GameState
import com.pranshuparmar.inkmayhem.game.Mood
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
 * Draws the notebook page and everything on it, back to front. World
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
    /** The runner as a character: its drawer, its personality and its speech bubble. */
    private val buddy = BuddyDrawer(shapes)
    private val persona = Persona()
    private val bubble = BubbleLayout()

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
    /** Dot has stepped out of the page: the runner is drawn without its face, and notes and remarks wait. */
    var dotOut = false
    var hoverOn = false
    var hoverX = 0f
    var hoverY = 0f

    /**
     * Interpolated camera of the last frame without shake: pen input maps through this.
     * Set here each frame; a replay may put it back to a recorded value.
     */
    var camX = 0f
    var camY = 0f
    /** The same plus screen shake: what the world is drawn with. */
    private var viewX = 0f
    private var viewY = 0f

    /**
     * [alpha] (0..1) is how far the frame sits between the previous and the latest sim tick: moving
     * bodies and the camera are interpolated so 120 Hz displays scroll smoothly over a 60 Hz sim.
     */
    /**
     * [pageOnly]: just the paper (behind the title screen and the stage list). [dt]: the frame's
     * time, for the runner's face; [remarks]: its speech bubbles are on.
     */
    fun render(
        sim: GameSim, fx: Fx, pal: Palette, tint: Boolean, uiCam: OrthographicCamera,
        wDp: Float, hDp: Float, scale: Float, density: Float, alpha: Float, pageOnly: Boolean = false,
        dt: Float = 1f / 60f, remarks: Boolean = true,
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
            Theme.PAGE, Theme.CASTLE, Theme.MIDNIGHT -> {
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
        // (no fixed red margin line: it read as a line chasing the runner)
        shapes.end()
        if (pageOnly) return
        persona.update(sim, dt, remarks, fx.reducedMotion)

        // notes in the margin hand
        text.batch.projectionMatrix = uiCam.combined
        text.batch.begin()
        val noteSize = max(15f, scale * 0.6f)
        val noteFont = fonts.get(Face.HAND, noteSize)
        val maxW = min(wDp * 0.62f, 340f)
        noteFont.data.setLineHeight(noteSize * 1.15f * density)
        val rx = sim.runner.body.position.x
        placedN = 0
        // a first-timer learns one thing at a time: nothing past the obstacle they're stopping at
        val focusX = sim.pendingGate?.x1 ?: Float.POSITIVE_INFINITY
        for (ni in sim.notes.indices) {
            val n = sim.notes[ni]
            if (dotOut || n.x > vx1 + NOTE_EARLY || n.x > focusX) continue
            val wrap = min(maxW, n.maxW * scale)
            text.layout.setText(noteFont, n.text, 0, n.text.length, pal[Tok.GRAPHITE], wrap, Align.left, true, null)
            val tw = text.layout.width
            val th = text.layout.height
            // A note stays whole on screen while the runner heads for what it describes: it comes
            // in at the right edge, rides the page, waits at the left edge until the runner has
            // passed its end, then fades. A narrow (portrait) view would otherwise cut it off.
            val past = rx - (n.x + tw / scale + NOTE_STAY)
            val alpha = 1f - (past / NOTE_FADE).coerceIn(0f, 1f)
            if (alpha <= 0f) continue
            val sx = ((n.x - viewX) * scale).coerceIn(NOTE_MARGIN, max(NOTE_MARGIN, wDp - tw - NOTE_MARGIN))
            val topMin = NOTE_TOP + noteFont.capHeight
            var sy = ((n.y - viewY) * scale).coerceIn(topMin, max(topMin, hDp - th - NOTE_MARGIN))
            // Held on screen, notes can meet: a later one goes above an earlier one it would cover,
            // or waits until there's room.
            var tries = 0
            while (tries < placedN) {
                val hit = overlapping(sx, sy - noteFont.capHeight, tw, th)
                if (hit < 0) break
                sy = placed[hit * 4 + 1] - NOTE_GAP - th + noteFont.capHeight
                tries++
            }
            if (sy < topMin || overlapping(sx, sy - noteFont.capHeight, tw, th) >= 0) continue
            if (placedN < MAX_PLACED) {
                placed[placedN * 4] = sx; placed[placedN * 4 + 1] = sy - noteFont.capHeight
                placed[placedN * 4 + 2] = sx + tw; placed[placedN * 4 + 3] = sy - noteFont.capHeight + th
                placedN++
            }
            noteMatrix.idt().translate(sx, sy, 0f).rotateRad(0f, 0f, 1f, -0.03f)
            text.batch.transformMatrix = noteMatrix
            // baseline at the note origin, wrapped like wrapText() with 1.15 line height
            text.draw(noteFont, n.text, 0f, -noteFont.capHeight, pal[Tok.GRAPHITE], alpha, wrapWidth = wrap)
        }
        text.batch.transformMatrix = identity
        text.batch.end()

        shapes.begin(worldCam.combined, density * scale)
        val brick = theme == Theme.CASTLE
        drawFlags(sim, pal, px, behind = true)
        drawPools(sim, pal, vx0, vx1, sim.cam.y + vh + 2f, px)
        sim.blocks.fastEach { r -> drawBlock(r, pal, vx0, vx1, sim.cam.y + vh + 2f, px, brick) }
        sim.ceilings.fastEach { c -> drawCeiling(c, pal, vx0, vx1, sim.cam.y - 2f, px, brick) }
        sim.spikeStrips.fastEach { k -> drawSpikes(k, pal, vx0, vx1) }
        drawBoxes(sim, pal, vx0, vx1, px)
        drawCrumbles(sim, pal, vx0, vx1, px)
        drawWarps(sim, pal, vx0, vx1, px)
        drawGates(sim, pal, px)
        if (guideOn) drawGuide(sim, pal, px)
        drawLifts(sim, pal, px)
        drawSeesaws(sim, pal, px)
        drawFlags(sim, pal, px, behind = false)
        drawDrops(sim, pal, vx0, vx1, px)
        drawStars(sim, pal, vx0, vx1)
        drawGolds(sim, pal, vx0, vx1, px)
        drawStrokes(sim, pal, tint, vx0, vx1, px)
        drawLiveStroke(sim, pal, tint, px)
        drawBlots(sim, pal, vx0, vx1, px)
        drawCaps(sim, pal, px)
        drawBoulders(sim, pal, px)
        drawBoss(sim, pal, px)
        drawPlanes(sim, pal, px)
        drawTide(sim, pal, sim.cam.y - 2f, sim.cam.y + vh + 2f, px)
        drawRunner(sim, pal, px)
        drawSwipes(sim, pal)
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
        // the hints' one-word labels, over their guides
        val labelFont = fonts.get(Face.HAND_BOLD, max(16f, scale * 0.62f))
        sim.gates.fastEach { g ->
            val a = gateAlpha(sim, g)
            if (a <= 0f) return@fastEach
            val sx = ((g.gx0 + g.gx1) / 2f - viewX) * scale
            val sy = (min(g.gy0, g.gy1) - GATE_LABEL_UP - viewY) * scale
            text.draw(labelFont, g.label, sx, sy - labelFont.capHeight, pal[inkTok(g.ink)], a, Align.center)
        }
        text.batch.end()

        // the runner's remark, in a speech bubble over its head (leaning back from where it's going)
        val ba = persona.state.bubbleAlpha
        val line = persona.state.bubble
        if (ba > 0f && line != null && !dotOut) {
            val run = sim.runner
            val hx = (run.prev.lerpX(alpha) - viewX) * scale
            val hy = (run.prev.lerpY(alpha) - run.r * 1.5f - viewY) * scale
            val font = fonts.get(Face.HAND_BOLD, 16f)
            bubble.place(text, font, line, hx, hy, run.dir.toFloat(), 8f, wDp - 8f, BUBBLE_TOP, persona.state.bubbleScale)
            shapes.begin(uiCam.combined, density)
            buddy.bubbleShapes(pal, bubble, ba)
            shapes.end()
            text.batch.projectionMatrix = uiCam.combined
            text.batch.begin()
            bubble.drawText(text, font, pal, ba)
            text.batch.end()
        }

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
        val tide = risingTide(sim)
        if (tide != null && tide.x < sim.cam.x + 0.2f) {
            val yy = sim.runner.body.position.y.coerceIn(sim.cam.y + 2f, sim.cam.y + vh - 2f)
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
        if (tide != null && tide.x < sim.cam.x + 0.2f) {
            val yy = sim.runner.body.position.y.coerceIn(sim.cam.y + 2f, sim.cam.y + vh - 2f)
            val xx = sim.cam.x + 0.55f
            markerLabel.setLength(0)
            markerLabel.append("TIDE ").append(max(0, (sim.runner.body.position.x - tide.x).roundToInt())).append(" m")
            text.draw(markerFont, markerLabel, (xx + 0.5f - viewX) * scale, (yy - viewY) * scale - markerFont.capHeight / 2f, pal[Tok.BEAM], pulse)
        }
        text.batch.end()

        // hover nib shadow: placed with the unshaken camera, drawn in the shaking world
        if (hoverOn && sim.drawing == null && sim.erasingPointer < 0 && sim.state == GameState.RUN) {
            val mx = hoverX / scale + camX
            val my = hoverY / scale + camY
            shapes.begin(worldCam.combined, density * scale)
            shapes.color(pal[Tok.INK]).ring(mx, my, Ink.thickness(0.45f) / 2f + 4f * px, 1.2f * px, 3f * px, 3f * px)
            shapes.color(pal[Tok.INK]).circle(mx, my, 1.6f * px)
            shapes.end()
        }
    }

    /** Screen rects (x0, y0, x1, y1 in dp) of the notes drawn so far this frame. */
    private val placed = FloatArray(MAX_PLACED * 4)
    private var placedN = 0

    /** The first note drawn this frame that a note at (x, y), [w] × [h] dp would touch, or −1. */
    private fun overlapping(x: Float, y: Float, w: Float, h: Float): Int {
        for (i in 0 until placedN) {
            val o = i * 4
            if (x < placed[o + 2] + NOTE_GAP && x + w > placed[o] - NOTE_GAP && y < placed[o + 3] + NOTE_GAP && y + h > placed[o + 1] - NOTE_GAP) return i
        }
        return -1
    }

    private fun drawBlock(r: GroundRect, pal: Palette, vx0: Float, vx1: Float, screenBottom: Float, px: Float, brick: Boolean) {
        if (r.x > vx1 || r.x + r.w < vx0) return
        if (r.cloud) {
            drawCloud(r, pal, px)
            return
        }
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

    /** A cloud: a row of puffs over a flat base; a bouncy one has a springy amber rim. */
    private fun drawCloud(r: GroundRect, pal: Palette, px: Float) {
        val n = max(2, (r.w / 0.7f).toInt())
        val step = r.w / n
        val rr = r.h * 0.75f
        val lw = 1.6f * px
        // outline first, then the fill over it
        shapes.color(pal[Tok.GRAPHITE], 0.7f)
        for (i in 0..n) shapes.circle(r.x + i * step, r.y + r.h * 0.45f, rr + lw)
        shapes.rect(r.x - lw, r.y + r.h * 0.45f, r.w + 2f * lw, r.h * 0.55f + lw)
        shapes.color(if (r.bouncy) pal[Tok.SPRING] else pal[Tok.PAPER_WHITE])
        for (i in 0..n) shapes.circle(r.x + i * step, r.y + r.h * 0.45f, rr)
        shapes.rect(r.x, r.y + r.h * 0.45f, r.w, r.h * 0.55f)
        if (r.bouncy) {
            shapes.color(pal[Tok.PAPER_WHITE], 0.8f)
            for (i in 0..n) shapes.circle(r.x + i * step - rr * 0.25f, r.y + r.h * 0.3f, rr * 0.35f)
        }
    }

    /** Paper planes: a small folded dart, nose the way it flies; a crumpled one is a falling ball of paper. */
    private fun drawPlanes(sim: GameSim, pal: Palette, px: Float) {
        sim.planes.fastEach { pl ->
            if (pl.down) {
                val age = sim.simT - pl.crumpledAt
                if (age > 0.8f) return@fastEach
                val a = 1f - age / 0.8f
                shapes.color(pal[Tok.PAPER_WHITE], a).circle(pl.cx, pl.cy + age * age * 5f, 0.16f)
                shapes.color(pal[Tok.GRAPHITE], a).ring(pl.cx, pl.cy + age * age * 5f, 0.16f, px)
                return@fastEach
            }
            val x = pl.prev.lerpX(alpha)
            val y = pl.prev.lerpY(alpha)
            val len = kotlin.math.hypot(pl.vx, pl.vy).coerceAtLeast(0.01f)
            val ux = pl.vx / len
            val uy = pl.vy / len
            val s = GameSim.PLANE_R * 1.6f
            val tipX = x + ux * s
            val tipY = y + uy * s
            val tailX = x - ux * s
            val tailY = y - uy * s
            // wings either side of the body line
            val nx = -uy
            val ny = ux
            shapes.color(pal[Tok.PAPER_WHITE]).triangle(tipX, tipY, tailX + nx * s * 0.6f, tailY + ny * s * 0.6f, tailX, tailY)
            shapes.color(pal[Tok.RULE]).triangle(tipX, tipY, tailX - nx * s * 0.45f, tailY - ny * s * 0.45f, tailX, tailY)
            shapes.color(pal[Tok.GRAPHITE])
            shapes.line(tipX, tipY, tailX + nx * s * 0.6f, tailY + ny * s * 0.6f, 1.2f * px)
            shapes.line(tipX, tipY, tailX - nx * s * 0.45f, tailY - ny * s * 0.45f, 1.2f * px)
            shapes.line(tipX, tipY, tailX, tailY, 1.2f * px)
        }
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
     * The checkpoint bookmarks and the finish flag (with a little doodled castle past it). Poles
     * draw [behind] the ground; flags in front.
     */
    private fun drawFlags(sim: GameSim, pal: Palette, px: Float, behind: Boolean) {
        val l = sim.stage?.layout ?: return
        val lw = 1.6f * px
        val passed = sim.stageRun?.reached ?: -1
        for (ci in l.checkpoints.indices) {
            val x = l.checkpoints[ci].x
            val y = l.checkpoints[ci].level
            val reached = ci <= passed
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
                val col = when {
                    s.gold -> pal[Tok.STAR]
                    s.chalk -> pal[Tok.PAPER_WHITE]
                    else -> inkColor(pal, sg.kind, tint)
                }
                if (s.chalk) shapes.color(pal[Tok.GRAPHITE], 0.6f).capsule(ax, ay, bx, by, sg.t + 2.4f * px)
                shapes.color(col).capsule(ax, ay, bx, by, sg.t)
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

    private fun drawLiveStroke(sim: GameSim, pal: Palette, tint: Boolean, px: Float) {
        val d = sim.drawing ?: return
        val n = d.size
        if (d.highlight) {
            // a highlighter swipe in progress
            shapes.color(pal[Tok.STAR], 0.45f)
            for (i in 1 until n) shapes.capsule(d.xs[i - 1], d.ys[i - 1], d.xs[i], d.ys[i], 0.7f)
            return
        }
        val alpha = if (d.dry) 0.5f else 1f
        // one ink for the whole stroke: the one it is heading for, thicker or thinner within it
        val kind = sim.drawingInk ?: return
        shapes.color(if (d.goldNib) pal[Tok.STAR] else if (d.chalk) pal[Tok.PAPER_WHITE] else inkColor(pal, kind, tint), alpha)
        for (i in 1 until n) { // segments only: a single point draws nothing
            val p = Ink.clampTo((d.ps[i] + d.ps[i - 1]) / 2f, kind)
            shapes.capsule(d.xs[i - 1], d.ys[i - 1], d.xs[i], d.ys[i], Ink.thickness(p))
        }
        // hooks: where the stroke is held, and whether the pen's end would hook if lifted now
        if (n > 0 && d.startAnchored) hookRing(pal, d.xs[0], d.ys[0], px, 1f)
        if (n > 1 && sim.tipHooks) hookRing(pal, d.xs[n - 1], d.ys[n - 1], px, 0.6f + 0.4f * sin(sim.realTime.toFloat() * 10f))
    }

    /** A hook: the same ring a finished stroke's anchor shows. */
    private fun hookRing(pal: Palette, x: Float, y: Float, px: Float, alpha: Float) {
        shapes.color(pal[Tok.GRAPHITE], alpha).circle(x, y, 4f * px + 0.75f * px)
        shapes.color(pal[Tok.PAPER], alpha).circle(x, y, 4f * px - 0.75f * px)
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
        val run = sim.runner
        val pose = run.prev
        val cx = pose.lerpX(alpha)
        val cy = pose.lerpY(alpha)
        val sq = run.squash
        val sy = 1f - sq * 0.2f
        val r = run.r
        // just lost Bold: it blinks while nothing can hurt it
        val a = if (sim.simT < run.safeUntil && sin(sim.simT * 30f) > 0f) 0.35f else 1f
        // the character: upright whatever way the ball rolls, its face set by what's going on
        buddy.draw(pal, persona.state, cx, cy, r, run.dir.toFloat(), px, a, extraSquash = sq * 0.5f, stretch = persona.stretch, bold = run.bold, faceless = dotOut)
        // a timed power: a ring in its colour, the arc left showing the time left
        val pw = run.power
        if (pw != null) {
            val left = ((run.powerUntil - sim.simT) / GameSim.POWER_S).coerceIn(0f, 1f)
            val col = when (pw) {
                com.pranshuparmar.inkmayhem.game.ItemKind.CHALK -> pal[Tok.PAPER_WHITE]
                com.pranshuparmar.inkmayhem.game.ItemKind.HIGHLIGHTER -> pal[Tok.STAR]
                else -> pal[Tok.STAR]
            }
            val blink = if (left < 0.25f && sin(sim.simT * 20f) > 0f) 0.4f else 1f
            shapes.color(pal[Tok.GRAPHITE], 0.5f * blink).arc(cx, cy, r + 0.18f, 3.2f * px, -PI_F / 2f, -PI_F / 2f + 2f * PI_F * left)
            shapes.color(col, blink).arc(cx, cy, r + 0.18f, 2.2f * px, -PI_F / 2f, -PI_F / 2f + 2f * PI_F * left)
            if (pw == com.pranshuparmar.inkmayhem.game.ItemKind.STAR_NIB) {
                shapes.color(pal[Tok.STAR], 0.35f + 0.25f * sin(sim.simT * 18f)).circle(cx, cy - r - 0.35f, 0.12f)
            }
        }
        if (run.bold) {
            // the marker cap, worn like a hat
            val w = r * 0.62f
            val h = r * 0.62f
            val forward = run.dir > 0
            val x0 = if (forward) cx - w * 0.62f else cx - w * 0.38f
            val y0 = cy - r * sy - h * 0.72f
            shapes.color(pal[Tok.ACCENT], a).roundRect(x0, y0, w, h, w * 0.3f)
            shapes.color(pal[Tok.INK], 0.35f * a).rect(x0, y0 + h * 0.72f, w, h * 0.16f)
            val clipX = if (forward) x0 + w + 0.6f * px else x0 - 0.6f * px
            shapes.color(pal[Tok.GRAPHITE], a).line(clipX, y0 + h * 0.15f, clipX, y0 + h * 0.7f, 2.2f * px)
        }
    }


    private fun inkTok(k: Ink) = when (k) {
        Ink.ROPE -> Tok.ROPE
        Ink.SPRING -> Tok.SPRING
        Ink.BEAM -> Tok.BEAM
    }

    /** A hint fades in as the runner nears it and out once it is passed. */
    private fun gateAlpha(sim: GameSim, g: com.pranshuparmar.inkmayhem.game.Gate): Float {
        if (g.passed) return (1f - (sim.simT - g.passedAt) / 0.5f).coerceIn(0f, 1f)
        val pending = sim.pendingGate
        if (pending != null && g.x0 > pending.x1) return 0f
        val ahead = g.gx0 - sim.runner.body.position.x
        return (1f - (ahead - GATE_SHOW) / 3f).coerceIn(0f, 1f)
    }

    /** The hints: a dotted guide in the ink's colour with an arrow, where to draw it. */
    private fun drawGates(sim: GameSim, pal: Palette, px: Float) {
        val pulse = 0.6f + 0.3f * sin(sim.realTime.toFloat() * 4f)
        sim.gates.fastEach { g ->
            val a = gateAlpha(sim, g)
            if (a <= 0f) return@fastEach
            val len = kotlin.math.hypot(g.gx1 - g.gx0, g.gy1 - g.gy0)
            val n = max(2, (len / 0.28f).toInt())
            shapes.color(pal[inkTok(g.ink)], pulse * a)
            for (i in 0..n) {
                val u = i / n.toFloat()
                shapes.circle(g.gx0 + (g.gx1 - g.gx0) * u, g.gy0 + (g.gy1 - g.gy0) * u, 0.07f)
            }
            val ux = (g.gx1 - g.gx0) / len
            val uy = (g.gy1 - g.gy0) / len
            val ax = g.gx1 + ux * 0.35f
            val ay = g.gy1 + uy * 0.35f
            shapes.triangle(ax, ay, ax - ux * 0.35f - uy * 0.22f, ay - uy * 0.35f + ux * 0.22f, ax - ux * 0.35f + uy * 0.22f, ay - uy * 0.35f - ux * 0.22f)
        }
    }

    private fun drawStars(sim: GameSim, pal: Palette, vx0: Float, vx1: Float) {
        val t = sim.simT
        shapes.color(pal[Tok.STAR])
        sim.stars.fastEach { st ->
            if (st.taken || st.hidden || st.x < vx0 || st.x > vx1) return@fastEach
            val bob = sin(t * 3f + st.phase) * 0.06f
            val tw = 1f + 0.12f * sin(t * 5f + st.phase * 2f)
            shapes.star(st.x, st.y + bob, 0.27f * tw, 0.48f)
        }
    }

    /** Gold stars: bigger, outlined, with a turning sparkle ring. */
    private fun drawGolds(sim: GameSim, pal: Palette, vx0: Float, vx1: Float, px: Float) {
        val t = sim.simT
        sim.golds.fastEach { g ->
            if (g.taken || g.hidden || g.x < vx0 || g.x > vx1) return@fastEach
            val bob = sin(t * 2.4f + g.index) * 0.08f
            shapes.color(pal[Tok.INK], 0.85f).star(g.x, g.y + bob, 0.5f, 0.5f)
            shapes.color(pal[Tok.STAR]).star(g.x, g.y + bob, 0.44f, 0.5f)
            shapes.color(pal[Tok.STAR], 0.5f + 0.3f * sin(t * 6f)).ring(g.x, g.y + bob, 0.7f, 1.4f * px, 3f * px, 4f * px)
        }
    }

    /** Blots: a wobbly violet smudge with cross eyes and little feet; a squashed one leaves a splat. */
    private fun drawBlots(sim: GameSim, pal: Palette, vx0: Float, vx1: Float, px: Float) {
        val r = GameSim.BLOT_R
        val t = sim.simT
        sim.blots.fastEach { b ->
            if (b.gone) return@fastEach
            if (b.squashed) {
                val age = t - b.squashedAt
                val a = 1f - (age / SPLAT_S).coerceIn(0f, 1f)
                if (a <= 0f) return@fastEach
                val gy = b.sy + r - 0.06f
                shapes.color(pal[Tok.BLOT], a).ellipse(b.sx, gy, r * 1.7f, r * 0.32f)
                shapes.circle(b.sx - r * 1.5f, gy - 0.04f, r * 0.22f)
                shapes.circle(b.sx + r * 1.4f, gy - 0.02f, r * 0.18f)
                return@fastEach
            }
            val x = b.prev.lerpX(alpha)
            val y = b.prev.lerpY(alpha)
            if (x < vx0 || x > vx1) return@fastEach
            val walk = if (b.awake) t * 9f else 0f
            if (b.kind == com.pranshuparmar.inkmayhem.game.Creature.ERASER) {
                eraserShape(pal, x, y, b.kind.r, b.dir.toFloat(), walk, px)
                return@fastEach
            }
            if (b.kind == com.pranshuparmar.inkmayhem.game.Creature.BEETLE) {
                beetleShape(pal, x, y, b.kind.r, b.dir.toFloat(), walk, px, b.biteT > 0f)
                return@fastEach
            }
            val c = pal[Tok.BLOT]
            // feet, stepping
            shapes.color(c)
            shapes.ellipse(x - r * 0.45f, y + r * 0.88f - max(0f, sin(walk)) * 0.07f, r * 0.32f, r * 0.18f)
            shapes.ellipse(x + r * 0.45f, y + r * 0.88f - max(0f, -sin(walk)) * 0.07f, r * 0.32f, r * 0.18f)
            // the smudge: a body with bumps that wobble
            shapes.ellipse(x, y, r * (1f + 0.04f * sin(t * 6f)), r * (1f - 0.04f * sin(t * 6f)))
            for (i in 0 until 5) {
                val ang = -PI_F * 0.9f + i * PI_F * 0.45f
                val br = r * (0.36f + 0.05f * sin(t * 5f + i * 1.7f))
                shapes.circle(x + kotlin.math.cos(ang) * r * 0.82f, y + sin(ang) * r * 0.82f, br)
            }
            // eyes on the runner (it walks at you), angry brows
            val look = if (b.dir < 0) -1f else 1f
            val ex0 = x - r * 0.3f
            val ex1 = x + r * 0.3f
            val ey = y - r * 0.12f
            shapes.color(pal[Tok.PAPER])
            shapes.circle(ex0, ey, r * 0.22f)
            shapes.circle(ex1, ey, r * 0.22f)
            shapes.color(pal[Tok.INK])
            shapes.circle(ex0 + look * r * 0.08f, ey + r * 0.03f, r * 0.1f)
            shapes.circle(ex1 + look * r * 0.08f, ey + r * 0.03f, r * 0.1f)
            val bw = max(1.6f * px, r * 0.09f)
            shapes.line(ex0 - r * 0.22f, ey - r * 0.32f, ex0 + r * 0.16f, ey - r * 0.2f, bw)
            shapes.line(ex1 + r * 0.22f, ey - r * 0.32f, ex1 - r * 0.16f, ey - r * 0.2f, bw)
        }
    }

    /** Angry eyes at (x0, ey) and (x1, ey) of radius [er], looking [look] (−1 left, +1 right). */
    private fun angryEyes(pal: Palette, x0: Float, x1: Float, ey: Float, er: Float, look: Float, px: Float, a: Float) {
        shapes.color(pal[Tok.PAPER], a)
        shapes.circle(x0, ey, er)
        shapes.circle(x1, ey, er)
        shapes.color(pal[Tok.INK], a)
        shapes.circle(x0 + look * er * 0.35f, ey + er * 0.15f, er * 0.45f)
        shapes.circle(x1 + look * er * 0.35f, ey + er * 0.15f, er * 0.45f)
        val bw = max(1.6f * px, er * 0.4f)
        shapes.line(x0 - er, ey - er * 1.45f, x0 + er * 0.75f, ey - er * 0.9f, bw)
        shapes.line(x1 + er, ey - er * 1.45f, x1 - er * 0.75f, ey - er * 0.9f, bw)
    }

    /** The stage's boss, by kind, with its hit points as pips over it; it flashes while hit. */
    private fun drawBoss(sim: GameSim, pal: Palette, px: Float) {
        val b = sim.boss ?: return
        if (b.down) return
        val x = b.prev.lerpX(alpha)
        val y = b.prev.lerpY(alpha)
        val r = b.r
        val t = sim.simT
        val a = if (t < b.hurtUntil && sin(t * 30f) > 0f) 0.35f else 1f
        val look = b.dir.toFloat()
        when (b.kind) {
            com.pranshuparmar.inkmayhem.game.BossKind.BIG_BLOT -> {
                val c = pal[Tok.BLOT]
                shapes.color(c, a)
                shapes.ellipse(x - r * 0.45f, y + r * 0.88f, r * 0.32f, r * 0.18f)
                shapes.ellipse(x + r * 0.45f, y + r * 0.88f, r * 0.32f, r * 0.18f)
                shapes.ellipse(x, y, r * (1f + 0.04f * sin(t * 5f)), r * (1f - 0.04f * sin(t * 5f)))
                for (i in 0 until 7) {
                    val ang = -PI_F * 0.95f + i * PI_F * 0.32f
                    shapes.circle(x + kotlin.math.cos(ang) * r * 0.84f, y + sin(ang) * r * 0.84f, r * (0.3f + 0.04f * sin(t * 4f + i)))
                }
                angryEyes(pal, x - r * 0.32f, x + r * 0.32f, y - r * 0.1f, r * 0.2f, look, px, a)
                // a crown of drips
                shapes.color(c, a)
                for (i in -1..1) shapes.triangle(x + i * r * 0.35f - r * 0.12f, y - r * 0.92f, x + i * r * 0.35f, y - r * 1.35f, x + i * r * 0.35f + r * 0.12f, y - r * 0.92f)
            }
            com.pranshuparmar.inkmayhem.game.BossKind.GIANT_ERASER -> {
                // a pink rubber block in a blue paper sleeve
                val w = r * 2f
                val h = r * 1.7f
                shapes.color(pal[Tok.ERASER], a).roundRect(x - w / 2f, y - h / 2f, w, h, r * 0.25f)
                shapes.color(pal[Tok.DROP], a).rect(x - w * 0.18f, y - h / 2f, w * 0.46f, h)
                shapes.color(pal[Tok.INK], 0.6f * a).roundRectOutline(x - w / 2f, y - h / 2f, w, h, r * 0.25f, 2f * px)
                angryEyes(pal, x - r * 0.05f, x + r * 0.45f, y - r * 0.15f, r * 0.16f, look, px, a)
                // crumbs of rubbed-out ink at its foot
                shapes.color(pal[Tok.MUTED], 0.6f * a)
                for (i in 0 until 4) shapes.circle(x - look * (r + 0.15f + i * 0.18f), y + h / 2f - 0.06f, 0.05f)
            }
            com.pranshuparmar.inkmayhem.game.BossKind.AIRLINER -> {
                // a big folded paper plane, nose the way it flies
                val n = look
                val len = r * 2.6f
                shapes.color(pal[Tok.PAPER_WHITE], a).triangle(x + n * len / 2f, y, x - n * len / 2f, y - r * 0.9f, x - n * len / 2f, y + r * 0.15f)
                shapes.color(pal[Tok.RULE], a).triangle(x + n * len / 2f, y, x - n * len / 2f, y + r * 0.15f, x - n * len * 0.3f, y + r * 0.55f)
                shapes.color(pal[Tok.GRAPHITE], a)
                shapes.line(x + n * len / 2f, y, x - n * len / 2f, y - r * 0.9f, 1.6f * px)
                shapes.line(x + n * len / 2f, y, x - n * len / 2f, y + r * 0.15f, 1.6f * px)
                shapes.line(x + n * len / 2f, y, x - n * len * 0.3f, y + r * 0.55f, 1.6f * px)
                angryEyes(pal, x + n * r * 0.1f, x + n * r * 0.5f, y - r * 0.2f, r * 0.13f, look, px, a)
            }
            com.pranshuparmar.inkmayhem.game.BossKind.BEETLE_QUEEN -> {
                val c = pal[Tok.BEETLE]
                shapes.color(pal[Tok.INK], a)
                for (i in -1..1) {
                    val lx = x + i * r * 0.55f
                    val kick = sin(t * 10f + i) * 0.08f
                    shapes.line(lx, y + r * 0.4f, lx - r * 0.3f + kick, y + r * 0.95f, 2.4f * px)
                    shapes.line(lx, y + r * 0.4f, lx + r * 0.3f - kick, y + r * 0.95f, 2.4f * px)
                }
                shapes.color(c, a).ellipse(x, y, r * 1.15f, r * 0.85f)
                shapes.color(pal[Tok.INK], 0.35f * a).line(x, y - r * 0.85f, x, y + r * 0.8f, 2f * px)
                shapes.color(c, a).circle(x + look * r * 1.05f, y - r * 0.05f, r * 0.42f)
                angryEyes(pal, x + look * r * 0.9f, x + look * r * 1.22f, y - r * 0.12f, r * 0.11f, look, px, a)
                // her crown
                shapes.color(pal[Tok.STAR], a)
                val cxr = x + look * r * 1.05f
                for (i in -1..1) shapes.triangle(cxr + i * r * 0.18f - r * 0.09f, y - r * 0.42f, cxr + i * r * 0.18f, y - r * 0.75f, cxr + i * r * 0.18f + r * 0.09f, y - r * 0.42f)
            }
            com.pranshuparmar.inkmayhem.game.BossKind.BIG_SMUDGE -> {
                val c = pal[Tok.INK]
                shapes.color(c, a)
                shapes.ellipse(x, y, r * (1.05f + 0.05f * sin(t * 4f)), r * (0.95f - 0.05f * sin(t * 4f)))
                for (i in 0 until 9) {
                    val ang = -PI_F + i * PI_F * 0.25f
                    shapes.circle(x + kotlin.math.cos(ang) * r * 0.9f, y + sin(ang) * r * 0.85f, r * (0.28f + 0.05f * sin(t * 3f + i * 1.3f)))
                }
                // drips under it
                for (i in -2..2) shapes.ellipse(x + i * r * 0.35f, y + r * (0.95f + 0.1f * kotlin.math.abs(sin(t * 2f + i))), r * 0.1f, r * 0.2f)
                angryEyes(pal, x - r * 0.3f, x + r * 0.3f, y - r * 0.15f, r * 0.2f, look, px, a)
                shapes.color(pal[Tok.BEAM], a).ellipse(x + look * r * 0.1f, y + r * 0.35f, r * 0.3f, r * 0.12f)
            }
        }
        // hit points
        val top = y - r - (if (b.kind == com.pranshuparmar.inkmayhem.game.BossKind.BIG_BLOT) 0.75f else 0.45f)
        for (i in 0 until b.kind.hp) {
            val hx = x + (i - (b.kind.hp - 1) / 2f) * 0.32f
            if (i < b.hp) shapes.color(pal[Tok.BEAM]).circle(hx, top, 0.1f)
            else shapes.color(pal[Tok.MUTED], 0.6f).ring(hx, top, 0.1f, 1.2f * px)
        }
    }

    /** Doodle boxes: a sketchy box with a star in it, bumped up a moment when hit; plain once used. */
    private fun drawBoxes(sim: GameSim, pal: Palette, vx0: Float, vx1: Float, px: Float) {
        sim.boxes.fastEach { b ->
            val rc = b.rect
            if (rc.x > vx1 || rc.right < vx0) return@fastEach
            val age = sim.simT - b.hitAt
            val bump = if (!b.hitAt.isNaN() && age < 0.2f) -sin(age / 0.2f * PI_F) * 0.18f else 0f
            val y = rc.y + bump
            val lw = 1.8f * px
            shapes.color(if (b.used) pal[Tok.ROCK] else pal[Tok.UI]).rect(rc.x, y, rc.w, rc.h)
            shapes.color(pal[Tok.GRAPHITE]).roundRectOutline(rc.x, y, rc.w, rc.h, 0.06f, lw)
            if (b.used) return@fastEach
            // a hand-drawn second outline, slightly off, and the star inside
            shapes.color(pal[Tok.GRAPHITE], 0.5f).roundRectOutline(rc.x + 0.04f, y - 0.03f, rc.w - 0.02f, rc.h + 0.02f, 0.06f, px)
            val tw = 1f + 0.1f * sin(sim.simT * 5f)
            shapes.color(pal[Tok.STAR]).star(rc.x + rc.w / 2f, y + rc.h / 2f + 0.02f, rc.w * 0.3f * tw, 0.48f)
        }
    }

    /** The marker cap: an upright pen cap with its clip. */
    private fun drawCaps(sim: GameSim, pal: Palette, px: Float) {
        val r = GameSim.CAP_R
        sim.caps.fastEach { c ->
            if (c.taken) return@fastEach
            val x = c.prev.lerpX(alpha)
            val y = c.prev.lerpY(alpha)
            val w = r * 1.3f
            val h = r * 2f
            when (c.kind) {
                com.pranshuparmar.inkmayhem.game.ItemKind.HIGHLIGHTER -> {
                    // a fat yellow highlighter, chisel tip up
                    shapes.color(pal[Tok.STAR]).roundRect(x - w / 2f, y - h / 2f + h * 0.2f, w, h * 0.8f, w * 0.25f)
                    shapes.color(pal[Tok.INK], 0.6f).triangle(x - w * 0.35f, y - h * 0.3f, x + w * 0.35f, y - h * 0.3f, x + w * 0.2f, y - h * 0.6f)
                }
                com.pranshuparmar.inkmayhem.game.ItemKind.CHALK -> {
                    // a stick of white chalk with a puff of cloud
                    shapes.color(pal[Tok.PAPER_WHITE]).roundRect(x - w * 0.35f, y - h / 2f, w * 0.7f, h, w * 0.3f)
                    shapes.color(pal[Tok.GRAPHITE]).roundRectOutline(x - w * 0.35f, y - h / 2f, w * 0.7f, h, w * 0.3f, 1.4f * px)
                    shapes.color(pal[Tok.PAPER_WHITE], 0.8f).circle(x + w * 0.6f, y - h * 0.5f, r * 0.45f)
                }
                com.pranshuparmar.inkmayhem.game.ItemKind.GOLD_NIB -> {
                    // a fountain-pen nib in gold
                    shapes.color(pal[Tok.STAR]).triangle(x - w * 0.6f, y - h * 0.3f, x + w * 0.6f, y - h * 0.3f, x, y + h * 0.55f)
                    shapes.color(pal[Tok.STAR]).rect(x - w * 0.6f, y - h * 0.5f, w * 1.2f, h * 0.22f)
                    shapes.color(pal[Tok.INK], 0.7f).line(x, y - h * 0.2f, x, y + h * 0.4f, 1.6f * px)
                    shapes.color(pal[Tok.INK], 0.7f).circle(x, y - h * 0.1f, r * 0.12f)
                }
                com.pranshuparmar.inkmayhem.game.ItemKind.STAR_NIB -> {
                    val k = 1f + 0.15f * sin(sim.simT * 10f)
                    shapes.color(pal[Tok.STAR]).star(x, y, r * 1.3f * k, 0.45f)
                    shapes.color(pal[Tok.PAPER_WHITE]).star(x, y, r * 0.55f * k, 0.45f)
                }
                else -> {
                    shapes.color(pal[Tok.ACCENT]).roundRect(x - w / 2f, y - h / 2f, w, h, w * 0.3f)
                    shapes.color(pal[Tok.INK], 0.35f).rect(x - w / 2f, y + h * 0.22f, w, h * 0.14f)
                    shapes.color(pal[Tok.GRAPHITE]).line(x + w / 2f + 0.6f * px, y - h * 0.35f, x + w / 2f + 0.6f * px, y + h * 0.15f, 2f * px)
                }
            }
            shapes.color(pal[Tok.ACCENT], 0.6f + 0.4f * sin(sim.simT * 8f)).ring(x, y, r * 1.9f, 1.2f * px, 2f * px, 3f * px)
        }
    }

    /** An Eraser: a pink rubber block in a blue sleeve, crumbs at its foot, eyes on the runner. */
    private fun eraserShape(pal: Palette, x: Float, y: Float, r: Float, look: Float, walk: Float, px: Float) {
        val w = r * 2.1f
        val h = r * 1.6f
        val bob = kotlin.math.abs(sin(walk)) * 0.04f
        val top = y + r - h - bob
        shapes.color(pal[Tok.ERASER]).roundRect(x - w / 2f, top, w, h, r * 0.25f)
        shapes.color(pal[Tok.DROP]).rect(x - w * 0.15f, top, w * 0.45f, h)
        shapes.color(pal[Tok.INK], 0.55f).roundRectOutline(x - w / 2f, top, w, h, r * 0.25f, 1.5f * px)
        angryEyes(pal, x - r * 0.05f + look * r * 0.1f, x + r * 0.45f + look * r * 0.1f, top + h * 0.4f, r * 0.17f, look, px, 1f)
        shapes.color(pal[Tok.MUTED], 0.6f)
        for (i in 0 until 3) shapes.circle(x - look * (w / 2f + 0.1f + i * 0.13f), y + r - 0.05f, 0.04f)
    }

    /** An ink beetle: a dark shell on six legs, jaws working when it's eating. */
    private fun beetleShape(pal: Palette, x: Float, y: Float, r: Float, look: Float, walk: Float, px: Float, eating: Boolean) {
        shapes.color(pal[Tok.INK])
        for (i in -1..1) {
            val lx = x + i * r * 0.6f
            val kick = sin(walk + i) * 0.06f
            shapes.line(lx, y, lx - r * 0.35f + kick, y + r * 0.95f, 1.6f * px)
            shapes.line(lx, y, lx + r * 0.35f - kick, y + r * 0.95f, 1.6f * px)
        }
        shapes.color(pal[Tok.BEETLE]).ellipse(x, y, r * 1.2f, r * 0.85f)
        shapes.color(pal[Tok.INK], 0.4f).line(x, y - r * 0.85f, x, y + r * 0.7f, 1.4f * px)
        shapes.color(pal[Tok.BEETLE]).circle(x + look * r * 1.1f, y + r * 0.05f, r * 0.45f)
        shapes.color(pal[Tok.PAPER]).circle(x + look * r * 1.25f, y - r * 0.1f, r * 0.14f)
        val jaw = if (eating) 0.12f + 0.1f * kotlin.math.abs(sin(walk * 2f)) else 0.08f
        shapes.color(pal[Tok.INK]).line(x + look * r * 1.5f, y + r * 0.1f, x + look * r * (1.75f + jaw), y + r * 0.35f, 1.6f * px)
    }

    /** Crumbling bricks: cracked bricks that shake once touched, then drop. */
    private fun drawCrumbles(sim: GameSim, pal: Palette, vx0: Float, vx1: Float, px: Float) {
        sim.crumbles.fastEach { c ->
            if (c.gone || c.x > vx1 || c.x + c.w < vx0) return@fastEach
            val cx = c.prev.lerpX(alpha)
            val cy = c.prev.lerpY(alpha)
            val shake = if (!c.touchedAt.isNaN() && !c.falling) sin(sim.simT * 60f) * 0.04f else 0f
            val x = cx - c.w / 2f + shake
            val y = cy - c.h / 2f
            shapes.color(pal[Tok.ROCK]).rect(x, y, c.w, c.h)
            shapes.color(pal[Tok.GRAPHITE]).roundRectOutline(x, y, c.w, c.h, 0.04f, 1.4f * px)
            // the cracks
            shapes.color(pal[Tok.GRAPHITE], 0.7f)
            shapes.line(x + c.w * 0.3f, y, x + c.w * 0.42f, y + c.h * 0.55f, px)
            shapes.line(x + c.w * 0.42f, y + c.h * 0.55f, x + c.w * 0.35f, y + c.h, px)
            shapes.line(x + c.w * 0.72f, y + c.h, x + c.w * 0.64f, y + c.h * 0.4f, px)
        }
    }

    private fun risingTide(sim: GameSim): com.pranshuparmar.inkmayhem.game.Tide? =
        sim.tides.firstOrNull { it.up && it.ebbAt.isNaN() }

    /** Ink pools: deep ink between the pit's walls under a slow, glossy swell, the odd bubble rising. */
    private fun drawPools(sim: GameSim, pal: Palette, vx0: Float, vx1: Float, screenBottom: Float, px: Float) {
        sim.pools.fastEach { p ->
            if (p.x0 > vx1 || p.x1 < vx0 || p.surface > screenBottom) return@fastEach
            val t = sim.simT
            shapes.color(pal[Tok.TIDE]).rect(p.x0, p.surface + 0.12f, p.x1 - p.x0, screenBottom - p.surface)
            val step = 0.2f
            var x = p.x0
            while (x < p.x1 - 0.001f) {
                val w = min(step, p.x1 - x)
                val h = 0.08f * sin(x * 2.3f + t * 2.4f) + 0.04f * sin(x * 5.1f - t * 3.3f)
                shapes.color(pal[Tok.TIDE]).rect(x, p.surface + h, w, 0.14f - h)
                shapes.color(pal[Tok.BLOT], 0.75f).rect(x, p.surface + h, w, 1.6f * px)
                x += step
            }
            // bubbles rising from the deep
            val span = p.x1 - p.x0
            for (k in 0 until max(1, (span / 2.5f).toInt())) {
                val u = ((t * 0.35f + k * 0.618f) % 1f)
                val bx = p.x0 + span * ((k * 0.37f + 0.2f) % 1f)
                val by = p.surface + 1.4f - 1.3f * u
                shapes.color(pal[Tok.BLOT], 0.55f * (1f - u)).ring(bx, by, 0.06f + 0.06f * u, 1.2f * px)
            }
        }
    }

    /** The ink tide: a wall of ink behind its rolling front, flecks flying off its crest; it drains as it breaks. */
    private fun drawTide(sim: GameSim, pal: Palette, top: Float, bottom: Float, px: Float) {
        sim.tides.fastEach { tide ->
            if (!tide.up) return@fastEach
            val a = if (tide.ebbAt.isNaN()) 1f else 1f - ((sim.simT - tide.ebbAt) / TIDE_DRAIN_S).coerceIn(0f, 1f)
            if (a <= 0f) return@fastEach
            val fx = tide.lerpX(alpha)
            val left = viewX - 2f
            if (fx < left) return@fastEach
            val t = sim.simT
            val step = 0.12f
            // thinner behind the HUD, so the score and lives stay readable over it
            val hudBottom = viewY + HUD_BAND * px
            var y = top
            while (y < bottom) {
                val off = 0.35f * sin(y * 1.6f + t * 4.2f) + 0.12f * sin(y * 3.3f - t * 6f)
                val ex = fx + off
                val k = if (y < hudBottom) 0.3f + 0.7f * ((y - hudBottom + HUD_FADE * px) / (HUD_FADE * px)).coerceIn(0f, 1f) else 1f
                shapes.color(pal[Tok.TIDE], 0.94f * a * k).rect(left, y, ex - left, step)
                shapes.color(pal[Tok.BLOT], 0.8f * a).rect(ex - 2.2f * px, y, 2.2f * px, step)
                y += step
            }
            // flecks thrown ahead of the front
            for (k in 0 until 7) {
                val u = ((t * 1.3f + k * 0.29f) % 1f)
                val fy = top + (bottom - top) * ((k * 0.43f + 0.1f) % 1f)
                shapes.color(pal[Tok.TIDE], a * (1f - u)).circle(fx + 0.3f + 0.9f * u, fy - 0.3f * u, 0.09f * (1f - u * 0.5f))
            }
        }
    }

    /** Highlighter swipes: a wide translucent yellow band that fades. */
    private fun drawSwipes(sim: GameSim, pal: Palette) {
        sim.swipes.fastEach { s ->
            val a = (1f - (sim.simT - s.at) / GameSim.SWIPE_S).coerceIn(0f, 1f) * 0.45f
            shapes.color(pal[Tok.STAR], a)
            for (i in 1 until s.n) shapes.capsule(s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i], 0.7f)
        }
    }

    /** Inkwells: a squat jar with an inky mouth on the ground. */
    private fun drawWarps(sim: GameSim, pal: Palette, vx0: Float, vx1: Float, px: Float) {
        sim.warps.fastEach { w ->
            if (w.hidden || w.x < vx0 - 1f || w.x > vx1 + 1f) return@fastEach
            val lw = 1.6f * px
            shapes.color(pal[Tok.ROCK]).roundRect(w.x - 0.55f, w.y - 0.75f, 1.1f, 0.75f, 0.2f)
            shapes.color(pal[Tok.GRAPHITE]).roundRectOutline(w.x - 0.55f, w.y - 0.75f, 1.1f, 0.75f, 0.2f, lw)
            shapes.color(pal[Tok.INK]).ellipse(w.x, w.y - 0.75f, 0.42f, 0.12f)
            shapes.color(pal[Tok.GRAPHITE]).rect(w.x - 0.45f, w.y - 0.95f, 0.9f, 0.18f)
            // an arrow: in (down) or out (up)
            val pulse = 0.5f + 0.5f * sin(sim.simT * 5f)
            shapes.color(pal[Tok.ACCENT], 0.5f + 0.4f * pulse)
            if (w.exit) shapes.triangle(w.x - 0.25f, w.y - 1.3f, w.x + 0.25f, w.y - 1.3f, w.x, w.y - 1.7f)
            else shapes.triangle(w.x - 0.25f, w.y - 1.6f, w.x + 0.25f, w.y - 1.6f, w.x, w.y - 1.2f)
        }
    }

    companion object {
        private const val PI_F = kotlin.math.PI.toFloat()
        /** A broken tide fades out over this long. */
        private const val TIDE_DRAIN_S = 1.5f
        /** Speech bubbles stay below the HUD. */
        private const val BUBBLE_TOP = 92f
        /** The HUD's depth from the top of the screen (dp), and how far above its bottom the tide thins. */
        private const val HUD_BAND = 96f
        private const val HUD_FADE = 24f
        /** A hint shows once the runner is this close (m) to its start, fading in over 3 m. */
        private const val GATE_SHOW = 9f
        /** Above the guide and any stars over it. */
        private const val GATE_LABEL_UP = 1.35f
        /** A squashed Blot's splat lasts this long. */
        private const val SPLAT_S = 0.9f
        private const val HATCH = 0.35f
        /** Notes: how far past the right edge one may come in early (m), how long it waits at the
         *  left edge after the runner passes its end (m) and fades over (m), and its screen margins (dp). */
        private const val NOTE_EARLY = 4f
        private const val NOTE_STAY = 2f
        private const val NOTE_FADE = 2f
        private const val NOTE_MARGIN = 12f
        private const val NOTE_TOP = 96f
        /** Space kept between two notes (dp), and how many are kept apart per frame. */
        private const val NOTE_GAP = 10f
        private const val MAX_PLACED = 8
        private val CRACK = floatArrayOf(-0.3f, -0.2f, 0.1f, 0.25f, 0.4f, 0.05f)

        /** Unit teardrop from two cubic Béziers, tessellated once. */
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
