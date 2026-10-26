package com.pranshuparmar.inkmayhem.game

import com.badlogic.gdx.physics.box2d.Body
import com.pranshuparmar.inkmayhem.physics.Ink

/** Hazard types from genSection(). DROP has no par and is never judged. */
enum class HazardType(val par: Ink?) {
    GAP_S(Ink.ROPE), GAP_M(Ink.ROPE), GAP_L(Ink.BEAM), WALL(Ink.SPRING), DROP(null), TUNNEL(Ink.ROPE), BOULDER(Ink.ROPE)
}

/** A stretch to judge once the runner passes x1 + 0.6. */
class Hazard(val type: HazardType, val x0: Float, val x1: Float) {
    val par: Ink get() = type.par!!
    var done = false
}

/** Ink refill; [high] drops sit 4.2 m up and need a bounce. [t] only animates the bob. */
class Drop(val x: Float, val y: Float, val value: Float, val high: Boolean, var t: Float) {
    var taken = false
}

/** Handwritten onboarding note on the page. */
class Note(val x: Float, val y: Float, val text: String)

class Boulder(val body: Body) {
    /** simT before which springs can't launch it again (NaN = never launched). */
    var bounceUntil = Float.NaN
}

class Runner(val body: Body) {
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
