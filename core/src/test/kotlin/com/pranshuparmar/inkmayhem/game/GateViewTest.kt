package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.TestNatives
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A first run stops the runner before each new thing until a stroke does the job. Wherever it
 * stops, the whole dotted guide has to be on screen, or the stroke can't be drawn: on a portrait
 * phone (15 m across, the narrowest view) and a landscape one (26.7 m).
 */
class GateViewTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("stages")
    fun `every guide waited at is on screen`(def: StageDef) {
        for (vw in floatArrayOf(PORTRAIT_W, LANDSCAPE_W)) check(def, vw)
    }

    private fun check(def: StageDef, vw: Float) {
        val waits = def.layout.gates.count { !it.waitX.isNaN() }
        if (waits == 0) return
        val sim = GameSim(seed = 1, events = RecordingEvents(), vwMetres = vw, vhMetres = vw * 2.2f)
        try {
            sim.learning = true
            sim.loadStage(def, fromCheckpoint = false)
            sim.runnerHeld = false
            // the scripted solution plays the rest; at each stop it waits until the guide has been looked at
            val bot = StageBot(sim)
            bot.reset()
            val looked = HashSet<Gate>()
            var seen = 0
            var ticks = 0
            while (seen < waits && ticks < 60 * 240) {
                val w = sim.waitingAt
                if (w == null || w in looked) bot.update()
                sim.realTime += 1.0 / 60
                sim.tick(1f / 60f)
                sim.cull(1f / 60f)
                ticks++
                if (sim.state != GameState.RUN) fail("${def.id} at $vw m: ${sim.state} (${sim.death?.reason}) with $seen of $waits stops seen")
                val g = sim.waitingAt ?: continue
                if (g in looked) continue
                // let the runner stop and the camera settle on the stop, then look
                repeat(180) {
                    sim.realTime += 1.0 / 60
                    sim.tick(1f / 60f)
                }
                val x0 = sim.cam.x
                val x1 = sim.cam.x + vw
                assertTrue(
                    minOf(g.gx0, g.gx1) >= x0 + 0.3f && maxOf(g.gx0, g.gx1) <= x1 - 0.3f,
                    "${def.id} at ${vw} m: the ${g.label} guide ${g.gx0}..${g.gx1} isn't all on screen ($x0..$x1)",
                )
                looked += g
                seen++
            }
            assertEquals(waits, seen, "${def.id} at $vw m: reached $seen of $waits stops")
        } finally {
            sim.dispose()
        }
    }

    companion object {
        const val PORTRAIT_W = 15f
        const val LANDSCAPE_W = 26.7f

        @JvmStatic
        fun stages() = Stages.ALL.filter { d -> d.layout.gates.any { !it.waitX.isNaN() } }

        init {
            TestNatives.ensure()
        }
    }
}
