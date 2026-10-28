package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.physics.TestNatives

/** Plays a stage headlessly with its scripted solution (or [inkFor] swapping inks). */
class StagePlay(val def: StageDef, inkFor: (Ink) -> Ink = { it }, solve: Boolean = true) {
    val events = RecordingEvents()
    // A portrait phone: 412 × 915 dp at 34.3 dp/m.
    val sim = GameSim(seed = 1, events = events, vwMetres = 12f, vhMetres = 26.7f)
    private val bot = if (solve) StageBot(sim, inkFor) else null
    var lowestY = Float.NEGATIVE_INFINITY
    var highestY = Float.POSITIVE_INFINITY

    init {
        sim.loadStage(def, fromCheckpoint = false)
        sim.runnerHeld = false
        bot?.reset()
    }

    /** Ticks until the stage is cleared, the runner dies or [maxSeconds] pass. */
    fun play(maxSeconds: Float = 240f, each: () -> Unit = {}): GameState {
        var ticks = 0
        while (sim.state == GameState.RUN && ticks < maxSeconds * 60) {
            bot?.update()
            sim.realTime += 1.0 / 60
            sim.tick(1f / 60f)
            sim.cull(1f / 60f)
            val y = sim.runner.body.position.y
            lowestY = maxOf(lowestY, y)
            highestY = minOf(highestY, y)
            each()
            ticks++
        }
        return sim.state
    }

    /** Retries from the checkpoint (or the start) the way the game does after a death. */
    fun retry() {
        sim.loadStage(def, fromCheckpoint = true)
        sim.runnerHeld = false
        bot?.reset()
    }

    val x: Float get() = sim.runner.body.position.x
    val y: Float get() = sim.runner.body.position.y
    val summary: String
        get() = "state ${sim.state} at x ${"%.1f".format(x)} y ${"%.1f".format(y)}, death ${sim.death?.reason}, pops ${events.pops}"

    fun dispose() = sim.dispose()

    companion object {
        init {
            TestNatives.ensure()
        }
    }
}
