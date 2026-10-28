package com.pranshuparmar.inkmayhem.game

import com.badlogic.gdx.physics.box2d.Body
import com.pranshuparmar.inkmayhem.physics.Ink

/** Hazard types from genSection(). DROP has no par and is never judged. */
enum class HazardType(val par: Ink?) {
    GAP_S(Ink.ROPE), GAP_M(Ink.ROPE), GAP_L(Ink.BEAM), WALL(Ink.SPRING), DROP(null), TUNNEL(Ink.ROPE), BOULDER(Ink.ROPE)
}

/** A stretch to judge once the runner passes x1 + 0.6. Stages may set their own [par]. */
class Hazard(val type: HazardType, val x0: Float, val x1: Float, val par: Ink = type.par!!) {
    var done = false
    /** Position in a stage's hazard list (−1 in Endless), for its stars. */
    var index = -1
}

/**
 * Ink refill; [high] drops sit 4.2 m up and need a bounce. A [heart] is a stage's extra life
 * instead. [t] only animates the bob.
 */
class Drop(val x: Float, val y: Float, val value: Float, val high: Boolean, var t: Float, val heart: Boolean = false) {
    var taken = false
}

/** A stage lift (kinematic): see [StageLayout.Lift]. */
class Lift(val body: Body, val def: StageLayout.Lift, val h: Float) {
    val prev = Pose(body)
    /** The runner has touched it (a waiting lift then rises). */
    var loaded = false
    /** −1 rising, +1 sinking, 0 parked. */
    var dir = 0
    var pause = 0f

    val top: Float get() = body.position.y - h / 2f
}

/** A stage seesaw: a plank pinned at ([px], [py]). */
class Seesaw(val body: Body, val len: Float, val px: Float, val py: Float) {
    val prev = Pose(body)
}

/** Handwritten onboarding note on the page. */
class Note(val x: Float, val y: Float, val text: String) {
    /** Wrap width in metres (the renderer also caps it in dp). */
    var maxW = Float.POSITIVE_INFINITY
}

class Boulder(val body: Body) {
    /** simT before which springs can't launch it again (NaN = never launched). */
    var bounceUntil = Float.NaN
    val prev = Pose(body)
}

/** A body's transform before the latest tick, for render interpolation. */
class Pose(private val body: Body) {
    var x = body.position.x
    var y = body.position.y
    var a = body.angle

    fun snapshot() {
        x = body.position.x
        y = body.position.y
        a = body.angle
    }

    fun lerpX(alpha: Float) = x + (body.position.x - x) * alpha
    fun lerpY(alpha: Float) = y + (body.position.y - y) * alpha
    fun lerpA(alpha: Float) = a + (body.angle - a) * alpha
}

class Runner(val body: Body) {
    val prev = Pose(body)
    var stuck = 0f
    var bounceCd = 0f
    var bestX = 0f
    var bestT = 0f
    /** 1 right after a spring bounce, decays to 0; squashes the blot. */
    var squash = 0f
}

/** Why a run ended; picks the death-card tips. */
enum class DeathKind { FELL, BOULDER, SPIKED, STUCK }

class Death(
    val reason: String,
    val kind: DeathKind,
    val score: Int,
    val distance: Int,
    val isBest: Boolean,
    val best: Int,
    val tip: String,
)
