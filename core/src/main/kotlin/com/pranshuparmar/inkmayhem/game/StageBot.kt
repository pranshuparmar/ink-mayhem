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
        if (sim.bossFight) {
            fight()
            return
        }
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

    private var nextFightStroke = 0f

    /**
     * A boss fight can't be scripted by position (the runner rolls back and forth), so the bot
     * reacts: a spring in front of the runner when a stompable boss (or a Blot it spat) is coming,
     * a beam dropped on the Giant Eraser, a spring under the Airliner, a spring in the Big
     * Smudge's path.
     */
    private fun fight() {
        val b = sim.boss ?: return
        if (b.down || sim.simT < nextFightStroke) return
        val run = sim.runner
        val rp = run.body.position
        val dir = run.dir
        val q = b.body.position
        val ahead = (q.x - rp.x) * dir
        val onGround = kotlin.math.abs(run.body.linearVelocity.y) < 0.5f
        val g = b.arena.level
        val towards = b.dir == -dir
        // a Blot it spat, walking at the runner
        val blot = sim.blots.firstOrNull { !it.dead && (it.body.position.x - rp.x) * dir in 1.8f..6.5f }
        val drew = when {
            onGround && blot != null -> springAt(rp.x + dir * 0.5f, dir, g)
            b.kind == BossKind.BIG_BLOT || b.kind == BossKind.BEETLE_QUEEN ->
                onGround && ahead in 2.2f..7.5f && springAt(rp.x + dir * 0.5f, dir, g)
            b.kind == BossKind.GIANT_ERASER -> when {
                ahead in 3.5f..9f && sim.simT >= b.hurtUntil -> stroke(q.x - dir * 0.6f - 0.9f, g - 3.6f, q.x - dir * 0.6f + 0.9f, g - 3.6f, Ink.BEAM)
                onGround && ahead in 1.3f..3.4f -> springAt(rp.x + dir * 0.4f, dir, g)
                else -> false
            }
            b.kind == BossKind.AIRLINER -> onGround && ahead in 0.4f..2.4f && towards && springAt(rp.x + dir * 0.3f, dir, g)
            b.kind == BossKind.BIG_SMUDGE -> ahead in 3f..8f && towards && !b.launched && springAt(q.x - dir * (b.r + 1.4f), dir, g)
            else -> false
        }
        if (drew) nextFightStroke = sim.simT + 1.1f
    }

    private fun springAt(x0: Float, dir: Int, ground: Float): Boolean {
        val p = StageBuilder.PRESSURE.getValue(Ink.SPRING)
        val y = ground - (Ink.thickness(p) / 2f + 0.04f)
        return stroke(x0, y, x0 + dir * 0.8f, y, Ink.SPRING)
    }

    private fun stroke(x0: Float, y0: Float, x1: Float, y1: Float, ink: Ink): Boolean {
        val n = maxOf(2, (kotlin.math.hypot(x1 - x0, y1 - y0) / 0.05f).toInt() + 1)
        draw(StageLayout.Stroke(0f, FloatArray(n) { x0 + (x1 - x0) * it / (n - 1) }, FloatArray(n) { y0 + (y1 - y0) * it / (n - 1) }, ink))
        return true
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
