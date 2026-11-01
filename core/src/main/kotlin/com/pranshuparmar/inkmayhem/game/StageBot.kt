package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink

/**
 * Plays a stage's scripted solution: each stroke is drawn through the normal pen path once the
 * runner reaches its trigger. Tests use it to prove every stage can be cleared with par ink; the
 * desktop autoplay uses it for screenshots.
 */
class StageBot(private val sim: GameSim, private val inkFor: (Ink) -> Ink = { it }) {
    private var drawn = BooleanArray(0)
    private var builtFor: Any? = null
    private var t = 0.0
    private val pointer = 97

    /** Call after [GameSim.loadStage]: strokes still ahead of the runner will be drawn again. */
    fun reset() {
        builtFor = sim.ink
        val n = sim.stage?.layout?.solution?.size ?: 0
        drawn = BooleanArray(n)
        val x = sim.runner.body.position.x
        sim.stage?.layout?.solution?.forEachIndexed { i, s -> if (s.atX < x - 8f && s.xs.last() < x) drawn[i] = true }
    }

    /** Call once per tick before [GameSim.tick]. */
    fun update() {
        val l = sim.stage?.layout ?: return
        if (sim.state != GameState.RUN || sim.drawing != null || sim.runnerHeld) return
        if (builtFor !== sim.ink) reset() // the stage was rebuilt (a retry)
        // a runner waiting at a gate (the first page's first run) is as good as there
        val rx = sim.waitingAt?.let { maxOf(sim.runner.body.position.x, it.x1) } ?: sim.runner.body.position.x
        for (i in l.solution.indices) {
            val s = l.solution[i]
            if (drawn[i] || rx < s.atX) continue
            drawn[i] = true
            draw(s)
            return // one stroke per tick, like a hand
        }
    }

    private fun draw(s: StageLayout.Stroke) {
        val p = StageBuilder.PRESSURE.getValue(inkFor(s.ink))
        for (i in s.xs.indices) {
            t += 1.0 / 240
            if (i == 0) sim.penDown(pointer, s.xs[i], s.ys[i], p, erase = false, t = t) else sim.penMove(pointer, s.xs[i], s.ys[i], p, t)
        }
        sim.penUp(pointer)
    }
}
