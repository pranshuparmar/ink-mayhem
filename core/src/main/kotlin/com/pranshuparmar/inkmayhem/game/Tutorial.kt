package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.fastEach
import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.physics.Stroke
import kotlin.math.max

/**
 * The first-run tutorial: four short lessons on hand-built terrain, played with the real sim and
 * pen input (how pressure picks the ink is taught by tuning the pen, just before). A dotted guide
 * shows where to draw; the runner waits until the player has drawn a stroke that does the job and
 * lifted the pen (a stray stroke is wiped with a hint), then runs. The lesson passes when the
 * runner gets through the right way, and on a failure it says what went wrong and sets the scene
 * up again.
 *
 * [sim] is the tutorial's own [GameSim], never the one holding the player's run.
 */
class Tutorial(val sim: GameSim) {

    enum class Lesson(val title: String, val body: String, val waiting: String) {
        ROPE(
            "Light touch: rope",
            "Bridge the gap along the dotted line, lightly, from edge to edge. Rope costs almost no ink.",
            "I’ll wait until you lift the pen.",
        ),
        SPRING(
            "Write normally: spring",
            "Draw a short line on the dotted mark, pressing the way you write. A spring bounces you over the wall.",
            "I’ll wait until you lift the pen.",
        ),
        BEAM(
            "Press hard: beam",
            "Too far for rope. Press hard along the dotted line, edge to edge.",
            "I’ll wait until you lift the pen.",
        ),
        ERASE(
            "Side button: erase",
            "That post is in the way. Hold the side button and touch the post to erase it.",
            "Erasing gives back half the ink.",
        ),
    }

    enum class Phase { TRYING, PASSED, FAILED, FINISHED }

    var lessonIndex = 0
        private set
    val lesson: Lesson get() = Lesson.entries[lessonIndex]
    val lessonCount: Int get() = Lesson.entries.size

    var phase = Phase.TRYING
        private set
    /** What just happened, for the card: empty, praise ([good]) or a hint. */
    var feedback = ""
        private set
    var good = false
        private set

    /**
     * Where to draw, in world metres (x0, y0, x1, y1), with the ink it teaches; empty once the
     * runner is on its way. The erase lesson rings the post instead ([guideRing]).
     */
    val guide = FloatArray(4)
    var showGuide = false
        private set
    var guideInk = Ink.ROPE
        private set
    val guideRing: Boolean get() = showGuide && lesson == Lesson.ERASE

    private var phaseT = 0f
    private var released = false
    /** Times the runner got through with heavier ink than the lesson teaches. */
    private var heavyPasses = 0
    private var post: Stroke? = null
    private var progressX = 0f
    private var progressT = 0f

    /** Starts at the first lesson ([lesson] lets tests and the desktop launcher jump ahead). */
    fun start(lesson: Int = 0) = load(lesson.coerceIn(0, lessonCount - 1))

    /** Ends the tutorial now (the Skip button and Android back). */
    fun skip() {
        phase = Phase.FINISHED
        sim.events.scratch(0f, 0.5f)
    }

    // ---------------------------------------------------------------- scenes

    private fun load(index: Int) {
        lessonIndex = index
        phase = Phase.TRYING
        phaseT = 0f
        released = false
        post = null
        if (feedback.isNotEmpty() && good) feedback = ""
        showGuide = true
        when (lesson) {
            Lesson.ROPE -> {
                scene(listOf(ground(-40f, 40f), ground(ROPE_GAP, 60f)), runnerX = -2.5f, focus = ROPE_GAP / 2f)
                sim.hazards += Hazard(HazardType.GAP_S, 0f, ROPE_GAP)
                setGuide(-0.3f, LEVEL - 0.05f, ROPE_GAP + 0.3f, LEVEL - 0.05f, Ink.ROPE)
            }
            Lesson.SPRING -> {
                scene(listOf(ground(-40f, 40f), floatArrayOf(0f, LEVEL - WALL_H, 60f)), runnerX = -6.5f, focus = -2f)
                sim.hazards += Hazard(HazardType.WALL, 0f, 1f)
                setGuide(SPRING_X0, LEVEL - 0.15f, SPRING_X1, LEVEL - 0.15f, Ink.SPRING)
            }
            Lesson.BEAM -> {
                scene(listOf(ground(-40f, 40f), ground(BEAM_GAP, 60f)), runnerX = -1.6f, focus = BEAM_GAP / 2f - 0.6f)
                sim.hazards += Hazard(HazardType.GAP_L, 0f, BEAM_GAP)
                setGuide(-0.3f, LEVEL - 0.05f, BEAM_GAP + 0.3f, LEVEL - 0.05f, Ink.BEAM)
            }
            Lesson.ERASE -> {
                scene(listOf(ground(-40f, 80f)), runnerX = -3f, focus = 0f)
                post = drawPost(POST_X)
                setGuide(POST_X, LEVEL - POST_H / 2f, POST_X, LEVEL - POST_H / 2f, Ink.BEAM)
            }
        }
        sim.snapCamera()
    }

    private fun ground(x: Float, w: Float) = floatArrayOf(x, LEVEL, w)

    private fun setGuide(x0: Float, y0: Float, x1: Float, y1: Float, ink: Ink) {
        guide[0] = x0; guide[1] = y0; guide[2] = x1; guide[3] = y1
        guideInk = ink
    }

    private fun scene(rects: List<FloatArray>, runnerX: Float, focus: Float) {
        sim.loadSandbox(rects, runnerX)
        sim.speedOverride = SPEED
        sim.unlimitedInk = true
        sim.runnerHeld = true
        sim.camFocusX = focus
    }

    /** A thick beam standing on the ground: the obstacle of the erase lesson. */
    private fun drawPost(x: Float): Stroke? {
        val p = 0.85f
        val n = 18
        for (i in 0..n) {
            val y = LEVEL - 0.05f - i * POST_H / n
            if (i == 0) sim.penDown(POST_POINTER, x, y, p, erase = false, t = 0.0) else sim.penMove(POST_POINTER, x, y, p, i / 240.0)
        }
        sim.penUp(POST_POINTER)
        return sim.ink.strokes.lastOrNull()
    }

    // ---------------------------------------------------------------- input

    /** Pen-down on the page during the tutorial. */
    fun penDown(pointer: Int, x: Float, y: Float, n: Float, erase: Boolean, t: Double) {
        if (phase != Phase.TRYING) return
        if (lesson == Lesson.ERASE && !erase && post.let { it != null && it in sim.ink.strokes }) hint("That drew ink. Hold the side button while you touch the post.")
        sim.penDown(pointer, x, y, n, erase, t)
    }

    fun penMove(pointer: Int, x: Float, y: Float, n: Float, t: Double) = sim.penMove(pointer, x, y, n, t)

    fun penUp(pointer: Int) {
        val before = sim.ink.strokes.size
        sim.penUp(pointer)
        // a stroke that can't do this lesson's job is wiped, and the runner keeps waiting
        if (phase != Phase.TRYING || released || lesson == Lesson.ERASE) return
        if (sim.ink.strokes.size <= before) return
        val s = sim.ink.strokes.last()
        if (!fitsLesson(s)) {
            val c = (s.x0 + s.x1) / 2f
            sim.events.burst(c, LEVEL - 1f, 10, 2f, com.pranshuparmar.inkmayhem.render.Tok.MUTED)
            sim.ink.removeStroke(s)
            hint(MISSED[lesson.ordinal])
        } else if (!good && feedback.isNotEmpty()) {
            feedback = ""
        }
    }

    /**
     * Whether a finished stroke can do the lesson's job: a bridge has to reach both edges, a
     * spring has to lie on the ground before the wall. (Which ink it is, is judged later.)
     */
    private fun fitsLesson(s: Stroke): Boolean {
        var lowest = Float.NEGATIVE_INFINITY
        s.segs.fastEach { lowest = max(lowest, max(it.ay, it.by)) }
        val nearGround = lowest > LEVEL - REACH
        return when (lesson) {
            Lesson.ROPE -> nearGround && s.x0 <= EDGE_SLACK && s.x1 >= ROPE_GAP - EDGE_SLACK
            Lesson.BEAM -> nearGround && s.x0 <= EDGE_SLACK && s.x1 >= BEAM_GAP - EDGE_SLACK
            Lesson.SPRING -> nearGround && s.x1 < -0.3f && s.x0 > -6f
            Lesson.ERASE -> true
        }
    }

    // ---------------------------------------------------------------- simulation

    /** One fixed tick: the sim, then the lesson's checks. */
    fun tick(dt: Float) {
        if (phase == Phase.FINISHED) return
        sim.tick(dt)
        phaseT += dt
        when (phase) {
            Phase.TRYING -> check()
            Phase.PASSED -> if (phaseT > PASS_PAUSE) {
                if (lessonIndex + 1 < lessonCount) load(lessonIndex + 1) else phase = Phase.FINISHED
            }
            Phase.FAILED -> if (phaseT > FAIL_PAUSE) load(lessonIndex)
            Phase.FINISHED -> Unit
        }
    }

    private fun check() {
        val rx = sim.runner.body.position.x
        val ry = sim.runner.body.position.y
        if (!released && shouldRelease()) {
            released = true
            showGuide = false
            sim.runnerHeld = false
            sim.camFocusX = Float.NaN
            progressX = rx
            progressT = 0f
        }
        if (sim.state == GameState.DEAD) {
            fail(deathHint(sim.death?.kind))
            return
        }
        if (released) {
            // the sim's own "Stuck" takes 5 s; a lesson should move on sooner
            if (rx > progressX + 0.3f) {
                progressX = rx
                progressT = 0f
            } else {
                progressT += 1f / 60f
                if (progressT > STUCK_S) {
                    fail(deathHint(DeathKind.STUCK))
                    return
                }
            }
        }
        when (lesson) {
            Lesson.ROPE -> if (rx > ROPE_GAP + 1.2f) {
                val heavy = heaviestInk()
                if (heavy > Ink.ROPE.weight && heavyPasses < HEAVY_RETRIES) {
                    heavyPasses++
                    fail("Made it, but that was ${Ink.ofWeight(heavy).label}. Try a lighter touch for rope.")
                } else {
                    pass("Nice rope! Light ink is nearly free.")
                }
            }
            Lesson.SPRING -> if (rx > 1.2f && ry < LEVEL - WALL_H) {
                if (sim.bounces == 0 && heavyPasses < HEAVY_RETRIES) {
                    heavyPasses++
                    fail("You found another way up! Now try a spring: write normally on the ground.")
                } else {
                    pass("Boing! Springs bounce you over walls.")
                }
            }
            Lesson.BEAM -> if (rx > BEAM_GAP + 1.2f) pass("Beams span anything, but cost ten times the ink of rope.")
            Lesson.ERASE -> if (rx > POST_X + 1.5f) pass("Erased! You’re ready. Let’s roll!")
        }
    }

    private fun shouldRelease(): Boolean = when (lesson) {
        Lesson.ERASE -> post.let { it != null && it !in sim.ink.strokes }
        else -> sim.drawing == null && sim.ink.strokes.isNotEmpty()
    }

    private fun heaviestInk(): Int {
        var h = -1
        sim.ink.strokes.fastEach { h = max(h, it.heaviest) }
        return h
    }

    /** The bridge the player drew hooked at most one end. */
    private fun unhooked(): Boolean = sim.ink.strokes.any { it.anchors.size < 2 } && sim.ink.strokes.none { it.anchors.size >= 2 }

    private fun usedRope(): Boolean = sim.ink.strokes.any { s -> s.segs.any { it.kind == Ink.ROPE } }

    private fun deathHint(kind: DeathKind?): String = when {
        kind == DeathKind.STUCK && lesson == Lesson.SPRING -> "Stuck. That wasn’t springy: press the way you write, on the dotted mark."
        kind == DeathKind.STUCK -> "Stuck. Thick ink makes a step: start level with the ground, not on top of it."
        (lesson == Lesson.ROPE || lesson == Lesson.BEAM) && unhooked() ->
            "Fell in. A line only hooks where it touches: start and end it on the edges."
        lesson == Lesson.BEAM && usedRope() -> "Rope snaps over long gaps. Press hard for a beam."
        lesson == Lesson.BEAM -> "Fell in. Press hard and draw from edge to edge."
        lesson == Lesson.ROPE -> "Fell in. Draw from one edge to the other, level with the ground."
        else -> "That didn’t work. Try again."
    }

    private fun pass(msg: String) {
        phase = Phase.PASSED
        phaseT = 0f
        feedback = msg
        good = true
        heavyPasses = 0
        sim.events.sound(Sfx.FEATHERWEIGHT)
    }

    private fun fail(msg: String) {
        phase = Phase.FAILED
        phaseT = 0f
        feedback = msg
        good = false
    }

    private fun hint(msg: String) {
        feedback = msg
        good = false
    }

    companion object {
        const val LEVEL = 20f
        const val SPEED = 3.4f
        const val ROPE_GAP = 3.2f
        const val WALL_H = 2.4f
        const val BEAM_GAP = 7.6f
        const val POST_X = 1.5f
        const val POST_H = 2.6f
        const val PASS_PAUSE = 1.6f
        const val FAIL_PAUSE = 2.2f
        const val STUCK_S = 3f
        /** After this many heavier-than-taught passes the lesson accepts it anyway. */
        const val HEAVY_RETRIES = 2
        private const val POST_POINTER = 98
        /** The spring lesson's mark on the ground before the wall. */
        const val SPRING_X0 = -3.2f
        const val SPRING_X1 = -2.4f
        /** A bridge counts if it starts and ends within this of the edges... */
        private const val EDGE_SLACK = 0.8f
        /** ...and comes within this of the ground somewhere. */
        private const val REACH = 1.2f
        private val MISSED = arrayOf(
            "That one missed. Start on the near edge and end on the far one, along the dots.",
            "That one missed. Draw on the dotted mark, on the ground before the wall.",
            "That one missed. Start on the near edge and end on the far one, along the dots.",
            "",
        )
    }
}
