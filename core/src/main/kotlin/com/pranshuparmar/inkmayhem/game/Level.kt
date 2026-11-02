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

/** A star of a trail: worth points, rings a rising chime, and every 50 is a life. */
class Star(val x: Float, val y: Float, val phase: Float, var hidden: Boolean = false) {
    var taken = false
}

/** One of a stage's three gold stars ([index] 0..2); [hidden] until a highlighter finds it. */
class GoldStar(val x: Float, val y: Float, val index: Int, var hidden: Boolean) {
    var taken = false
}

/**
 * A Blot: an ink smudge that waddles at the runner. A bounce onto it (or a beam dropped on it)
 * squashes it; touching it otherwise costs a life, unless the runner is Bold. It wakes when the
 * runner comes near and paces between [x0] and [x1] (no limits when they're NaN).
 */
class Blot(val body: Body, val x0: Float, val x1: Float) {
    val prev = Pose(body)
    var awake = false
    /** −1 walking left (towards the runner), +1 right. */
    var dir = -1
    /** simT it was squashed at, or NaN. */
    var squashedAt = Float.NaN
    val squashed: Boolean get() = !squashedAt.isNaN()
    /** Where it was squashed (the splat stays there a moment). */
    var sx = 0f
    var sy = 0f
    /** Fell off the page or was culled: its body is gone and nothing is drawn. */
    var gone = false
    /** It has been set walking (a stop after that means it walked into something). */
    var walking = false
    /** No body any more (squashed or gone): never touch [body] then. */
    val dead: Boolean get() = squashed || gone
}

/** A doodle box: solid, and a hit from below pops out what it holds (once). */
class DoodleBox(
    val rect: com.pranshuparmar.inkmayhem.physics.GroundRect,
    val holds: ItemKind,
    /** Its fixture's tag: a contact with it names this box. */
    val tag: com.pranshuparmar.inkmayhem.physics.FixtureTag,
) {
    var used = false
    /** simT it was hit at (for the bump), or NaN. */
    var hitAt = Float.NaN
}

/** The marker cap a doodle box pops out: it rolls away along the ground until the runner catches it. */
class Cap(val body: Body) {
    val prev = Pose(body)
    /** Picked up, or lost off the page: its body is gone. */
    var taken = false
    var dir = 1
}

/**
 * A doodled hint over x0..x1: a dotted guide from (gx0, gy0) to (gx1, gy1) in [ink]'s colour with
 * a one-word [label]. A tuning gate ([tunes], the first page's first three obstacles) also tunes
 * the pen: while it is untuned, a stroke drawn there is that ink, and its pressure is learnt. On
 * the first page's first run the runner stops at [waitX] (NaN: never) until a stroke that does
 * the job is there: a spring hint wants a spring lying on the ground along the guide, any other a
 * bridge hooked on both sides (a beam hint, a beam).
 */
class Gate(
    val ink: Ink, val x0: Float, val x1: Float,
    val gx0: Float, val gy0: Float, val gx1: Float, val gy1: Float,
    val label: String, val tunes: Boolean, val waitX: Float = Float.NaN,
    /** How far before the guide's start a spring may reach and still count (half that past its end). */
    val slack: Float = 0.8f,
) {
    /** The runner is past it: the guide fades out. */
    var passed = false
    var passedAt = 0f
    /** A stroke that does the job is in place: a waiting runner goes on. */
    var done = false
    /** Seconds the runner has stood waiting here, and whether it has nudged the player yet. */
    var waitT = 0f
    var nudged = false
}

/** Each world's boss: its name, hit points and size. How it is hit is in [GameSim]. */
enum class BossKind(val title: String, val hp: Int, val r: Float) {
    /** World 1: bounce onto it (springs), like any Blot. */
    BIG_BLOT("The Big Blot", 3, 1.1f),
    /** World 2: rubs out rope and springs it touches; only a beam dropped on it hurts it. */
    GIANT_ERASER("The Giant Eraser", 3, 1.0f),
    /** World 3: flies overhead dropping paper planes; bounce up into it. */
    AIRLINER("The Paper Airliner", 3, 0.9f),
    /** World 4: lays beetles; bounce onto it. */
    BEETLE_QUEEN("The Beetle Queen", 3, 1.0f),
    /** World 5: spits Blots; spring it up into its own spiked ceiling. */
    BIG_SMUDGE("The Big Smudge", 4, 1.2f),
}

/**
 * A boss arena from [x0] to [x1] on ground [level]: a wall closes behind the runner as it enters,
 * the runner rolls back and forth between the walls, and the far wall opens once the boss is
 * beaten. [ceiling] is a spiked roof's underside (NaN: open).
 */
class Arena(val x0: Float, val x1: Float, val level: Float, val kind: BossKind, val ceiling: Float) {
    var closed = false
    var won = false
    var left: com.pranshuparmar.inkmayhem.physics.GroundRect? = null
    var right: com.pranshuparmar.inkmayhem.physics.GroundRect? = null
    val mid: Float get() = (x0 + x1) / 2f
}

/** A boss: walks (or flies) in its arena; [hp] hits to beat. */
class Boss(val kind: BossKind, val body: Body, val arena: Arena) {
    val prev = Pose(body)
    var hp = kind.hp
    /** −1 left, +1 right. */
    var dir = -1
    /** Just hit: it flashes, and neither hurts nor can be hurt, until this simT. */
    var hurtUntil = -10f
    var down = false
    var downAt = 0f
    /** Seconds to its next move (a spit, a beetle, a plane). */
    var actT = 2f
    /** Launched by a spring (the Big Smudge) and not yet back down. */
    var launched = false
    val r: Float get() = kind.r
}

/** How the runner's face looks this frame. */
enum class Mood { CALM, WORRIED, HAPPY, DIZZY, SCARED, OUCH }

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
    /** Which way it rolls: +1 right; −1 only back and forth in a boss arena. */
    var dir = 1
    /** Its radius: bigger while [bold]. */
    var r = GameSim.RUNNER_R
    var stuck = 0f
    var bounceCd = 0f
    var bestX = 0f
    var bestT = 0f
    /** 1 right after a spring bounce, decays to 0; squashes the blot. */
    var squash = 0f
    /** The marker cap: survives one hit, flattens Blots by rolling into them. */
    var bold = false
    /** After losing Bold, nothing hurts until this simT. */
    var safeUntil = -1f
}

/** Why a run ended; picks the death-card tips. */
enum class DeathKind { FELL, BOULDER, SPIKED, STUCK, BLOT, BOSS, ERASER, PLANE, BEETLE, TIDE }

class Death(
    val reason: String,
    val kind: DeathKind,
    val score: Int,
    val distance: Int,
    val isBest: Boolean,
    val best: Int,
    val tip: String,
)
