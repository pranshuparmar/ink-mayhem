package com.pranshuparmar.inkmayhem.physics

import com.badlogic.gdx.math.Vector2
import com.pranshuparmar.inkmayhem.fastEach
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.FixtureDef
import com.badlogic.gdx.physics.box2d.Joint
import com.badlogic.gdx.physics.box2d.PolygonShape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.physics.box2d.joints.RevoluteJointDef
import com.badlogic.gdx.physics.box2d.joints.WeldJointDef
import com.badlogic.gdx.utils.Disposable
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Port of the POC's InkCore: strokes become chains of Box2D bodies whose joints break under load.
 * World units are metres with y pointing down and gravity (0, +10), exactly as in the POC.
 */
class InkPhysics(
    private val anchorR: Float = ANCHOR_R,
    private val cullY: Float = CULL_Y,
) : Disposable {
    val world = World(Vector2(0f, GRAVITY), true)
    val ground: Body = world.createBody(BodyDef())
    val rects = ArrayList<GroundRect>()
    val strokes = ArrayList<Stroke>()
    /** Snap events since the caller last drained them. */
    val events = ArrayDeque<SnapEvent>()

    private val joints = ArrayList<JointInfo>() // creation order; iterated newest first like Planck's list
    private var nextId = 1
    private val box = PolygonShape()
    private val circle = CircleShape()
    private val fd = FixtureDef()
    private val bd = BodyDef()
    private val v = Vector2()

    // ---- static geometry ----

    fun addRect(x: Float, y: Float, w: Float, h: Float, kind: FixtureKind = FixtureKind.GROUND): GroundRect {
        val r = GroundRect(x, y, w, h, kind)
        box.setAsBox(w / 2f, h / 2f, v.set(x + w / 2f, y + h / 2f), 0f)
        fd.reset()
        fd.shape = box
        fd.friction = 0.9f
        r.fixture = ground.createFixture(fd).also {
            it.userData = if (kind == FixtureKind.CEILING) FixtureTag.CEILING else FixtureTag.GROUND
        }
        rects += r
        return r
    }

    /**
     * A spiked ceiling: a solid rock with a thin spiked layer along its underside, so only
     * touching the underside counts as being spiked. The layer stops [SPIKE_INSET] short of each
     * side, where no spikes are drawn, so sliding down a side is safe. Rendering and anchoring
     * treat it as one rect.
     */
    fun addSpikedRock(x: Float, y: Float, w: Float, h: Float, spikeDepth: Float): GroundRect {
        val r = GroundRect(x, y, w, h, FixtureKind.CEILING)
        box.setAsBox(w / 2f, h / 2f, v.set(x + w / 2f, y + h / 2f), 0f)
        fd.reset()
        fd.shape = box
        fd.friction = 0.9f
        r.fixture = ground.createFixture(fd).also { it.userData = FixtureTag.GROUND }
        box.setAsBox(w / 2f - SPIKE_INSET, spikeDepth / 2f, v.set(x + w / 2f, y + h - spikeDepth / 2f), 0f)
        r.spikes = ground.createFixture(fd).also { it.userData = FixtureTag.CEILING }
        rects += r
        return r
    }

    /**
     * A block whose top slopes from ([x], [yL]) to ([x] + [w], [yR]) with a level bottom at
     * [bottom]: hills and ramps. One convex quad on the ground body.
     */
    fun addSlope(x: Float, yL: Float, w: Float, yR: Float, bottom: Float): GroundRect {
        val r = GroundRect(x, yL, w, bottom - yL, FixtureKind.GROUND, yR)
        quad[0] = x; quad[1] = yL
        quad[2] = x + w; quad[3] = yR
        quad[4] = x + w; quad[5] = bottom
        quad[6] = x; quad[7] = bottom
        box.set(quad)
        fd.reset()
        fd.shape = box
        fd.friction = 0.9f
        r.fixture = ground.createFixture(fd).also { it.userData = FixtureTag.GROUND }
        rects += r
        return r
    }

    private val quad = FloatArray(8)

    /**
     * A strip of spikes [SPIKE_H] deep: on a floor ([up], sitting on y) or hanging from a ceiling
     * (hanging from y). Touching it is fatal, like a spiked ceiling's underside.
     */
    fun addSpikes(x: Float, y: Float, w: Float, up: Boolean): GroundRect {
        val top = if (up) y - SPIKE_H else y
        val r = GroundRect(x, top, w, SPIKE_H, FixtureKind.CEILING)
        r.spikesUp = up
        box.setAsBox(w / 2f, SPIKE_H / 2f, v.set(x + w / 2f, top + SPIKE_H / 2f), 0f)
        fd.reset()
        fd.shape = box
        fd.friction = 0.9f
        r.fixture = ground.createFixture(fd).also { it.userData = FixtureTag.CEILING }
        rects += r
        return r
    }

    fun removeRect(r: GroundRect) {
        if (!rects.remove(r)) return
        r.fixture?.let { ground.destroyFixture(it) }
        r.spikes?.let { ground.destroyFixture(it) }
        r.fixture = null
        r.spikes = null
    }

    /** Would a stroke end at (px, py) be pinned to the ground or a ceiling? */
    fun anchorable(px: Float, py: Float): Boolean = distToRects(px, py) <= anchorR

    fun distToRects(px: Float, py: Float): Float {
        var best = Float.POSITIVE_INFINITY
        rects.fastEach { r -> best = min(best, distTo(r, px, py)) }
        return best
    }

    private fun distTo(r: GroundRect, px: Float, py: Float): Float {
        if (!r.sloped) {
            val dx = max(max(r.x - px, 0f), px - (r.x + r.w))
            val dy = max(max(r.y - py, 0f), py - (r.y + r.h))
            return hypot(dx, dy)
        }
        val x1 = r.x + r.w
        val b = r.bottom
        if (px >= r.x && px <= x1 && py >= r.topAt(px) && py <= b) return 0f
        return min(
            min(distSeg(px, py, r.x, r.y, x1, r.yR), distSeg(px, py, x1, r.yR, x1, b)),
            min(distSeg(px, py, x1, b, r.x, b), distSeg(px, py, r.x, b, r.x, r.y)),
        )
    }

    // ---- moving platforms ----

    /** A kinematic box (a lift) with its top-left at ([x], [y]); the caller moves it. */
    fun addLift(x: Float, y: Float, w: Float, h: Float): Body {
        bd.type = BodyDef.BodyType.KinematicBody
        bd.position.set(x + w / 2f, y + h / 2f)
        bd.angle = 0f
        val b = world.createBody(bd)
        box.setAsBox(w / 2f, h / 2f)
        fd.reset()
        fd.shape = box
        fd.friction = 0.9f
        b.createFixture(fd).userData = FixtureTag.LIFT
        return b
    }

    /**
     * A seesaw: a plank of [len] × [thick] centred on ([cx], [cy]), pinned there to the ground
     * and free to tip between [minAngle] and [maxAngle] (radians, positive = clockwise on screen).
     */
    fun addSeesaw(cx: Float, cy: Float, len: Float, thick: Float, angle: Float, minAngle: Float, maxAngle: Float): Body {
        bd.type = BodyDef.BodyType.DynamicBody
        bd.position.set(cx, cy)
        bd.angle = angle
        bd.angularDamping = 1.5f
        val b = world.createBody(bd)
        bd.angularDamping = 0f
        box.setAsBox(len / 2f, thick / 2f)
        fd.reset()
        fd.shape = box
        fd.density = SEESAW_DENSITY
        fd.friction = 0.9f
        b.createFixture(fd).userData = FixtureTag.GROUND
        world.createJoint(RevoluteJointDef().apply {
            initialize(ground, b, v.set(cx, cy))
            enableLimit = true
            lowerAngle = minAngle - angle
            upperAngle = maxAngle - angle
        })
        return b
    }

    // ---- strokes ----

    /**
     * Builds a stroke from raw points ([xs], [ys], [ps] of length [n], normalized pressure).
     * Returns null for strokes shorter than 0.15 m, like the POC.
     */
    fun buildStroke(xs: FloatArray, ys: FloatArray, ps: FloatArray, n: Int): Stroke? {
        if (n < 2 || strokeLength(xs, ys, n) < 0.15f) return null
        val s = resample(xs, ys, ps, n)
        val m = s.size / 3
        if (m < 2) return null
        val stroke = Stroke(nextStrokeId())
        for (i in 0 until m - 1) {
            val p = (s[3 * i + 2] + s[3 * i + 5]) / 2f
            stroke.segs += Segment(s[3 * i], s[3 * i + 1], s[3 * i + 3], s[3 * i + 4], p, Ink.thickness(p), Ink.of(p))
        }
        // Group consecutive beam segments into one rigid body.
        for (sg in stroke.segs) {
            val g = stroke.groups.lastOrNull()
            if (g != null && g.kind == Ink.BEAM && sg.kind == Ink.BEAM) {
                g.segs += sg
            } else {
                stroke.groups += InkGroup(sg.kind).also { it.segs += sg }
            }
        }
        stroke.cost = costOf(s, m)
        for (g in stroke.groups) {
            val ox = g.segs[0].ax
            val oy = g.segs[0].ay
            bd.type = BodyDef.BodyType.DynamicBody
            bd.position.set(ox, oy)
            bd.angle = 0f
            bd.angularDamping = 0.2f
            bd.linearDamping = 0.02f
            bd.bullet = false
            val body = world.createBody(bd)
            bd.angularDamping = 0f
            bd.linearDamping = 0f
            for (sg in g.segs) {
                val len = hypot(sg.bx - sg.ax, sg.by - sg.ay)
                val ang = atan2(sg.by - sg.ay, sg.bx - sg.ax)
                val cx = (sg.ax + sg.bx) / 2f - ox
                val cy = (sg.ay + sg.by) / 2f - oy
                box.setAsBox(len / 2f + sg.t * 0.15f, sg.t / 2f, v.set(cx, cy), ang)
                fd.reset()
                fd.shape = box
                fd.density = if (sg.kind == Ink.ROPE) DENS_ROPE else DENS_INK
                fd.friction = 0.8f
                fd.restitution = if (sg.kind == Ink.SPRING) 0.25f else 0.05f
                fd.filter.groupIndex = (-stroke.id).toShort()
                body.createFixture(fd).userData = FixtureTag.of(sg.kind, stroke)
                sg.group = g
                sg.lax = sg.ax - ox; sg.lay = sg.ay - oy
                sg.lbx = sg.bx - ox; sg.lby = sg.by - oy
            }
            body.userData = stroke
            g.body = body
        }
        // Joints between groups at their shared point; the weaker side decides the kind.
        for (i in 1 until stroke.groups.size) {
            val a = stroke.groups[i - 1]
            val b = stroke.groups[i]
            val sa = a.segs.last()
            val sb = b.segs.first()
            val p = min(sa.p, sb.p)
            val t = Ink.thickness(p)
            val kind = when (Ink.of(p)) { Ink.ROPE -> JointKind.ROPE; Ink.SPRING -> JointKind.SPRING; Ink.BEAM -> JointKind.BEAM }
            stroke.joints += makeJoint(a.body!!, b.body!!, sb.ax, sb.ay, kind, K * t, t, stroke)
        }
        // Anchor stroke ends that touch the ground or a ceiling.
        val first = stroke.segs.first()
        val last = stroke.segs.last()
        anchorEnd(stroke, stroke.groups.first(), first, first.ax, first.ay)
        anchorEnd(stroke, stroke.groups.last(), last, last.bx, last.by)
        strokes += stroke
        return stroke
    }

    private fun anchorEnd(stroke: Stroke, g: InkGroup, sg: Segment, x: Float, y: Float) {
        if (distToRects(x, y) > anchorR) return
        val kind = if (sg.kind == Ink.ROPE) JointKind.ROPE else JointKind.ANCHOR
        val j = makeJoint(ground, g.body!!, x, y, kind, K_ANCHOR * sg.t, sg.t, stroke)
        stroke.joints += j
        stroke.anchors += Anchor(x, y, j)
    }

    private fun makeJoint(
        a: Body, b: Body, x: Float, y: Float, kind: JointKind, strength: Float, t: Float, stroke: Stroke,
    ): JointInfo {
        v.set(x, y)
        val joint: Joint = when (kind) {
            JointKind.ROPE -> world.createJoint(RevoluteJointDef().apply { initialize(a, b, v) })
            JointKind.SPRING -> world.createJoint(WeldJointDef().apply {
                initialize(a, b, v)
                frequencyHz = SPRING_HZ
                dampingRatio = SPRING_DAMP
            })
            JointKind.BEAM, JointKind.ANCHOR -> world.createJoint(WeldJointDef().apply { initialize(a, b, v) })
        }
        val info = JointInfo(strength, t, kind, stroke, x, y)
        info.joint = joint
        joint.userData = info
        joints += info
        return info
    }

    private fun nextStrokeId(): Int {
        // Filter group indices are shorts; recycle ids long before they overflow.
        val id = nextId
        nextId = if (nextId >= 30_000) 1 else nextId + 1
        return id
    }

    // ---- stepping ----

    /** Advances [dt] seconds in [substeps] equal steps, accumulating joint load and breaking joints. */
    fun step(dt: Float, substeps: Int = 2) {
        val h = dt / substeps
        val inv = 1f / h
        repeat(substeps) {
            world.step(h, VELOCITY_ITERATIONS, POSITION_ITERATIONS)
            var i = joints.size - 1
            while (i >= 0) {
                val u = joints[i]
                val j = u.joint
                if (j == null || u.broken) {
                    joints.removeAt(i)
                    i--
                    continue
                }
                u.age += h
                val force = j.getReactionForce(inv).len()
                val torque = abs(j.getReactionTorque(inv))
                val raw = force + if (u.kind == JointKind.ROPE) 0f else TORQUE_W * torque / u.t
                u.load = u.load * 0.8f + raw * 0.2f
                if (u.age > GRACE) u.peak = max(u.peak, u.load)
                if (u.age > GRACE && u.load > u.strength) {
                    val anchor = j.anchorA
                    events.addLast(SnapEvent(anchor.x, anchor.y, u.kind))
                    u.broken = true
                    u.joint = null
                    world.destroyJoint(j)
                    joints.removeAt(i)
                }
                i--
            }
        }
        cullFallen()
    }

    /** Destroys stroke bodies that fell far below the page (InkCore's CULL_Y). */
    private fun cullFallen() {
        strokes.fastEach { s ->
            s.groups.fastEach { g ->
                val b = g.body
                if (b != null && b.position.y > cullY) destroyGroup(g)
            }
        }
        strokes.removeAll { !it.hasBodies }
    }

    private fun destroyGroup(g: InkGroup) {
        val b = g.body ?: return
        for (edge in b.jointList) markBroken(edge.joint)
        g.body = null
        world.destroyBody(b)
    }

    private fun markBroken(j: Joint) {
        val u = j.userData as? JointInfo ?: return
        u.broken = true
        u.joint = null
    }

    fun removeStroke(stroke: Stroke) {
        for (u in stroke.joints) {
            val j = u.joint
            if (!u.broken && j != null) world.destroyJoint(j)
            u.broken = true
            u.joint = null
        }
        for (g in stroke.groups) {
            val b = g.body ?: continue
            g.body = null
            world.destroyBody(b)
        }
        stroke.alive = false
        strokes.remove(stroke)
    }

    fun clearInk() {
        for (s in ArrayList(strokes)) removeStroke(s)
    }

    /** The newest stroke with a segment within [r] (plus half its thickness) of the point. */
    fun strokeAt(px: Float, py: Float, r: Float): Stroke? {
        var i = strokes.size
        while (--i >= 0) {
            val s = strokes[i]
            for (si in s.segs.indices) {
                val sg = s.segs[si]
                val g = sg.group
                if (g.body == null) continue
                val ax = g.worldX(sg.lax, sg.lay)
                val ay = g.worldY(sg.lax, sg.lay)
                val bx = g.worldX(sg.lbx, sg.lby)
                val by = g.worldY(sg.lbx, sg.lby)
                if (distSeg(px, py, ax, ay, bx, by) < r + sg.t / 2f) return s
            }
        }
        return null
    }

    // ---- wet ink ----

    /** The stroke being drawn: static pieces that only the runner collides with. */
    private var wet: Body? = null
    var wetPieces = 0
        private set

    /**
     * Adds a piece of wet ink from (ax, ay) to (bx, by): solid at once, so the runner can roll on
     * a stroke before the pen lifts. Boulders, crates and finished strokes pass through it.
     */
    fun addWet(ax: Float, ay: Float, bx: Float, by: Float, kind: Ink, t: Float) {
        val body = wet ?: run {
            bd.type = BodyDef.BodyType.StaticBody
            bd.position.set(0f, 0f)
            bd.angle = 0f
            world.createBody(bd).also { wet = it }
        }
        val len = hypot(bx - ax, by - ay)
        box.setAsBox(len / 2f + t * 0.15f, t / 2f, v.set((ax + bx) / 2f, (ay + by) / 2f), atan2(by - ay, bx - ax))
        fd.reset()
        fd.shape = box
        fd.friction = 0.8f
        fd.restitution = if (kind == Ink.SPRING) 0.25f else 0.05f
        fd.filter.categoryBits = WET_BITS
        fd.filter.maskBits = RUNNER_BITS
        body.createFixture(fd).userData = FixtureTag.wet(kind)
        wetPieces++
    }

    /** Removes the wet ink (the stroke was built, abandoned or the run ended). */
    fun clearWet() {
        wet?.let { world.destroyBody(it) }
        wet = null
        wetPieces = 0
    }

    // ---- props (runner, boulders, test crates) ----

    fun addBall(x: Float, y: Float, r: Float, density: Float, friction: Float, restitution: Float, tag: FixtureTag, bullet: Boolean): Body {
        bd.type = BodyDef.BodyType.DynamicBody
        bd.position.set(x, y)
        bd.angle = 0f
        bd.bullet = bullet
        val b = world.createBody(bd)
        bd.bullet = false
        circle.radius = r
        circle.position = v.set(0f, 0f)
        fd.reset()
        fd.shape = circle
        fd.density = density
        fd.friction = friction
        fd.restitution = restitution
        if (tag === FixtureTag.RUNNER) fd.filter.categoryBits = RUNNER_BITS
        b.createFixture(fd).userData = tag
        return b
    }

    fun addCrate(x: Float, y: Float, size: Float, density: Float): Body {
        bd.type = BodyDef.BodyType.DynamicBody
        bd.position.set(x, y)
        bd.angle = 0f
        val b = world.createBody(bd)
        box.setAsBox(size / 2f, size / 2f)
        fd.reset()
        fd.shape = box
        fd.density = density
        fd.friction = 0.7f
        fd.restitution = 0.05f
        b.createFixture(fd).userData = FixtureTag.CRATE
        return b
    }

    fun destroyBody(b: Body) {
        for (edge in b.jointList) markBroken(edge.joint)
        world.destroyBody(b)
    }

    override fun dispose() {
        box.dispose()
        circle.dispose()
        world.dispose()
    }

    private fun FixtureDef.reset() {
        shape = null
        density = 0f
        friction = 0.2f
        restitution = 0f
        isSensor = false
        filter.categoryBits = 0x0001
        filter.maskBits = -1
        filter.groupIndex = 0
    }

    companion object {
        init {
            Box2D.init()
        }

        const val GRAVITY = 10f
        /** Resample spacing (m). */
        const val SEG = 0.3f
        /** Joint strength = K × t newtons. */
        const val K = 2400f
        const val K_ANCHOR = 60000f
        /** Weight of bending torque in the load. */
        const val TORQUE_W = 0.6f
        const val SPRING_HZ = 5f
        const val SPRING_DAMP = 0.5f
        const val DENS_ROPE = 6f
        const val DENS_INK = 1.5f
        /** Stroke ends this close to static geometry are pinned (the game's value; InkCore defaults to 0.3). */
        const val ANCHOR_R = 0.45f
        /** Joints can't break in their first 0.3 s. */
        const val GRACE = 0.3f
        /** Stroke cost = INK_K × Σ L t². */
        const val INK_K = 30f
        const val CULL_Y = 400f
        /** Collision categories: wet ink touches only the runner. Everything else is category 1. */
        const val RUNNER_BITS: Short = 0x0002
        const val WET_BITS: Short = 0x0004
        /** Depth of a spike strip on a floor or ceiling. */
        const val SPIKE_H = 0.3f
        /** Seesaw planks: light enough for the runner (about 1 kg) to tip. */
        const val SEESAW_DENSITY = 0.6f
        /** The spiked layer of a ceiling rock stops this far short of each side. */
        const val SPIKE_INSET = 0.1f
        const val VELOCITY_ITERATIONS = 10
        const val POSITION_ITERATIONS = 8

        fun strokeLength(xs: FloatArray, ys: FloatArray, n: Int): Float {
            var len = 0f
            for (i in 1 until n) len += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
            return len
        }

        /** Resamples a polyline at [SEG] spacing, interpolating pressure. Returns x, y, p triples. */
        fun resample(xs: FloatArray, ys: FloatArray, ps: FloatArray, n: Int): FloatArray {
            val out = com.badlogic.gdx.utils.FloatArray(n * 3 + 3)
            out.add(xs[0], ys[0], ps[0])
            var carry = 0f
            for (i in 1 until n) {
                val ax = xs[i - 1]; val ay = ys[i - 1]; val ap = ps[i - 1]
                val bx = xs[i]; val by = ys[i]; val bp = ps[i]
                val len = hypot(bx - ax, by - ay)
                if (len == 0f) continue
                var d = SEG - carry
                while (d <= len) {
                    val u = d / len
                    out.add(ax + (bx - ax) * u, ay + (by - ay) * u, ap + (bp - ap) * u)
                    d += SEG
                }
                carry = len - (d - SEG)
            }
            val lx = xs[n - 1]; val ly = ys[n - 1]; val lp = ps[n - 1]
            val tx = out[out.size - 3]; val ty = out[out.size - 2]
            if (hypot(lx - tx, ly - ty) > SEG * 0.35f) {
                out.add(lx, ly, lp)
            } else if (out.size > 3) {
                out[out.size - 3] = lx; out[out.size - 2] = ly; out[out.size - 1] = lp
            }
            return out.toArray()
        }

        /** INK_K × Σ segment length × t², t from the segment's mean pressure. [m] points. */
        fun costOf(s: FloatArray, m: Int): Float {
            var c = 0f
            for (i in 1 until m) {
                val len = hypot(s[3 * i] - s[3 * i - 3], s[3 * i + 1] - s[3 * i - 2])
                val t = Ink.thickness((s[3 * i + 2] + s[3 * i - 1]) / 2f)
                c += len * t * t
            }
            return c * INK_K
        }

        fun distSeg(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = bx - ax
            val dy = by - ay
            val l2 = (dx * dx + dy * dy).let { if (it == 0f) 1e-9f else it }
            val u = (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
            return hypot(px - (ax + dx * u), py - (ay + dy * u))
        }
    }
}
