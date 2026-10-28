package com.pranshuparmar.inkmayhem.physics

import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.Fixture
import com.badlogic.gdx.physics.box2d.Joint
import kotlin.math.cos
import kotlin.math.sin

/** What a fixture is, for contact handling and rendering. Stored as Fixture user data. */
enum class FixtureKind { GROUND, CEILING, ROPE, SPRING, BEAM, RUNNER, BOULDER, CRATE, LIFT }

class FixtureTag(val kind: FixtureKind, val stroke: Stroke? = null) {
    companion object {
        val GROUND = FixtureTag(FixtureKind.GROUND)
        val CEILING = FixtureTag(FixtureKind.CEILING)
        val RUNNER = FixtureTag(FixtureKind.RUNNER)
        val BOULDER = FixtureTag(FixtureKind.BOULDER)
        val CRATE = FixtureTag(FixtureKind.CRATE)
        val LIFT = FixtureTag(FixtureKind.LIFT)

        private val WET_ROPE = FixtureTag(FixtureKind.ROPE)
        private val WET_SPRING = FixtureTag(FixtureKind.SPRING)
        private val WET_BEAM = FixtureTag(FixtureKind.BEAM)

        /** Wet ink (still being drawn) has no stroke yet, but its kind still counts: springs bounce. */
        fun wet(ink: Ink) = when (ink) { Ink.ROPE -> WET_ROPE; Ink.SPRING -> WET_SPRING; Ink.BEAM -> WET_BEAM }

        fun of(ink: Ink, stroke: Stroke) = FixtureTag(
            when (ink) { Ink.ROPE -> FixtureKind.ROPE; Ink.SPRING -> FixtureKind.SPRING; Ink.BEAM -> FixtureKind.BEAM },
            stroke,
        )
    }
}

/**
 * A static block on the ground body (InkCore addRect). Top-left origin, y down. A sloped block's
 * top runs from [y] at the left to [yR] at the right; its bottom is level at y + [h].
 */
class GroundRect(
    val x: Float, val y: Float, val w: Float, val h: Float, val kind: FixtureKind,
    val yR: Float = y,
) {
    internal var fixture: Fixture? = null
    /** The spiked underside of a ceiling rock, if any. */
    internal var spikes: Fixture? = null

    val sloped: Boolean get() = yR != y
    val bottom: Float get() = y + h

    /** Height of the top edge at [px] (clamped to the block). */
    fun topAt(px: Float): Float = if (yR == y) y else y + (yR - y) * ((px - x) / w).coerceIn(0f, 1f)

    /** Game data: the ground level this block was generated at (its top). */
    var level = y
    /** Game data: runner x that spawns a boulder on this runway, or NaN. */
    var boulderTrigger = Float.NaN
    var triggered = false

    val right: Float get() = x + w
}

/** One rigid body of a stroke: a run of consecutive beam segments, or a single other segment. */
class InkGroup(val kind: Ink) {
    /** Null once destroyed (culled or erased). libGDX pools Body objects, so never keep stale ones. */
    var body: Body? = null
    val segs = ArrayList<Segment>(4)

    /** Transform before the latest tick, for render interpolation (NaN until the first snapshot). */
    var prevX = Float.NaN
    var prevY = 0f
    var prevA = 0f

    fun snapshot() {
        val b = body ?: return
        prevX = b.position.x
        prevY = b.position.y
        prevA = b.angle
    }

    /** Render-time position of a body-local point, [alpha] of the way from the previous tick. */
    fun lerpX(lx: Float, ly: Float, alpha: Float): Float {
        val b = body ?: return Float.NaN
        if (prevX.isNaN()) return worldX(lx, ly)
        val a = prevA + (b.angle - prevA) * alpha
        return prevX + (b.position.x - prevX) * alpha + cos(a) * lx - sin(a) * ly
    }

    fun lerpY(lx: Float, ly: Float, alpha: Float): Float {
        val b = body ?: return Float.NaN
        if (prevX.isNaN()) return worldY(lx, ly)
        val a = prevA + (b.angle - prevA) * alpha
        return prevY + (b.position.y - prevY) * alpha + sin(a) * lx + cos(a) * ly
    }

    /** World transform of a body-local point. */
    fun worldX(lx: Float, ly: Float): Float {
        val b = body ?: return Float.NaN
        val a = b.angle
        return b.position.x + cos(a) * lx - sin(a) * ly
    }

    fun worldY(lx: Float, ly: Float): Float {
        val b = body ?: return Float.NaN
        val a = b.angle
        return b.position.y + sin(a) * lx + cos(a) * ly
    }
}

/** One resampled stroke segment from a to b with its averaged pressure. */
class Segment(
    val ax: Float, val ay: Float, val bx: Float, val by: Float,
    val p: Float, val t: Float, val kind: Ink,
) {
    lateinit var group: InkGroup
    /** Endpoints in the group body's local frame. */
    var lax = 0f; var lay = 0f; var lbx = 0f; var lby = 0f
}

enum class JointKind { ROPE, SPRING, BEAM, ANCHOR }

/** Breakable-joint bookkeeping (POC joint user data). */
class JointInfo(
    var strength: Float,
    val t: Float,
    val kind: JointKind,
    val stroke: Stroke,
    val x: Float,
    val y: Float,
) {
    var joint: Joint? = null
    var load = 0f
    var peak = 0f
    var age = 0f
    var broken = false
}

class Anchor(val x: Float, val y: Float, val joint: JointInfo)

class Stroke(val id: Int) {
    val segs = ArrayList<Segment>()
    val groups = ArrayList<InkGroup>()
    val joints = ArrayList<JointInfo>()
    val anchors = ArrayList<Anchor>()
    var cost = 0f
    var alive = true

    /** Game data: x-extent of the drawn points and the heaviest ink, for judging against par. */
    var x0 = Float.NaN
    var x1 = Float.NaN
    var heaviest = -1

    val hasBodies: Boolean get() = groups.any { it.body != null }
}

/** A joint broke (or a rope was over-long and gave way). */
class SnapEvent(val x: Float, val y: Float, val kind: JointKind)
