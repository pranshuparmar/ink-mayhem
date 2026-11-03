package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.physics.TestNatives

/**
 * Drives [GameSim] in sandbox mode: ground ends at x = 0, a gap, ground again; strokes go
 * through the real pen-input path. Ground level is 20.
 */
class Sandbox(gap: Float, runnerX: Float, speed: Float, farLength: Float = 80f) {
    val events = RecordingEvents()
    val sim = GameSim(seed = 1, events = events, vwMetres = 12f, vhMetres = 26.7f)
    private var t = 0.0

    init {
        sim.loadSandbox(listOf(floatArrayOf(-60f, LEVEL, 60f), floatArrayOf(gap, LEVEL, farLength)), runnerX)
        sim.runSpeed = speed
    }

    /** A straight stroke at constant normalized pressure, sampled every 5 cm. */
    fun line(x0: Float, x1: Float, y: Float, p: Float, pointer: Int = 7) {
        val n = ((x1 - x0) / 0.05f + 1e-3f).toInt() + 1
        for (i in 0 until n) {
            val x = x0 + i * 0.05f
            t += 1.0 / 240
            if (i == 0) sim.penDown(pointer, x, y, p, erase = false, t = t) else sim.penMove(pointer, x, y, p, t)
        }
        sim.penUp(pointer)
    }

    /** Single pen samples, for strokes drawn over several ticks. */
    fun penDown(x: Float, y: Float, p: Float, pointer: Int = 7) {
        t += 1.0 / 240
        sim.penDown(pointer, x, y, p, erase = false, t = t)
    }

    fun penMove(x: Float, y: Float, p: Float, pointer: Int = 7) {
        t += 1.0 / 240
        sim.penMove(pointer, x, y, p, t)
    }

    fun penUp(pointer: Int = 7) = sim.penUp(pointer)

    /** A stroke lying on the ground: 0.4 m past each edge, just above the surface. */
    fun groundLine(x0: Float, x1: Float, p: Float) = line(x0, x1, LEVEL - (Ink.thickness(p) / 2f + 0.04f), p)

    fun boulder(x: Float, vx: Float): Boulder = sim.spawnBoulder(x, LEVEL - GameSim.BOULDER_R - 0.05f, vx)

    fun run(seconds: Float, each: () -> Unit = {}) {
        repeat((seconds * 60).toInt()) {
            sim.realTime += 1.0 / 60
            sim.tick(1f / 60f)
            sim.cull(1f / 60f)
            each()
        }
    }

    val runnerX: Float get() = sim.runner.body.position.x
    val runnerY: Float get() = sim.runner.body.position.y

    fun dispose() = sim.dispose()

    companion object {
        const val LEVEL = 20f

        init {
            TestNatives.ensure() // before any instance builds a Box2D world
        }
    }
}

/** Records what the sim announced, for assertions. */
open class RecordingEvents : GameEvents {
    val toasts = ArrayList<String>()
    val pops = ArrayList<String>()
    val sounds = ArrayList<Sfx>()
    /** The variant of each sound in [sounds] (a star's note, a chime's combo). */
    val variants = ArrayList<Int>()
    var stars = 0
    var deaths = ArrayList<Death>()
    var hearts = 0
    var checkpoints = 0
    var clears = 0

    override fun extraLife() {
        hearts++
    }

    override fun checkpoint() {
        checkpoints++
    }

    override fun stageCleared() {
        clears++
    }

    override fun toast(message: String, force: Boolean, delaySec: Float) {
        toasts += message
    }

    override fun pop(x: Float, y: Float, text: String, color: com.pranshuparmar.inkmayhem.render.Tok) {
        pops += text
    }

    override fun sound(sfx: Sfx, variant: Int) {
        sounds += sfx
        variants += variant
    }

    override fun star() {
        stars++
    }

    override fun died(death: Death) {
        deaths += death
    }
}
