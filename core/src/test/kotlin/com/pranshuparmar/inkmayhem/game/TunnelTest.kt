package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.physics.TestNatives
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The tunnel's rock: only its underside is spiked, and it can't be bounced over. */
class TunnelTest {

    @Test
    fun `standing on top of a spiked rock is safe`() {
        val sb = Sandbox(gap = 4.5f, runnerX = -20f, speed = 3.4f)
        try {
            val rock = sb.sim.addTunnelRock(-10f, -4f, Sandbox.LEVEL - 2.1f)
            // put the runner on the roof and let it roll off the far end
            sb.sim.runner.body.setTransform(-9f, rock.y - GameSim.RUNNER_R - 0.02f, 0f)
            sb.sim.runner.body.setLinearVelocity(0f, 0f)
            sb.run(3f)
            assertNull(sb.sim.death, "died: ${sb.sim.death?.reason}")
            assertTrue(sb.runnerX > rock.right, "runner at ${sb.runnerX}")
        } finally {
            sb.dispose()
        }
    }

    @Test
    fun `a spring under the rock still spikes`() {
        val sb = Sandbox(gap = 4.5f, runnerX = -12f, speed = 3.4f)
        try {
            sb.sim.addTunnelRock(-3.5f, 8f, Sandbox.LEVEL - 2.1f)
            sb.groundLine(-0.4f, 4.9f, INK_P.getValue(Ink.SPRING))
            sb.run(6f)
            assertEquals("Spiked", sb.sim.death?.reason)
        } finally {
            sb.dispose()
        }
    }

    @Test
    fun `sliding down the side of the rock is safe`() {
        val sb = Sandbox(gap = 4.5f, runnerX = -20f, speed = 3.4f)
        try {
            val rock = sb.sim.addTunnelRock(-3.5f, 8f, Sandbox.LEVEL - 2.1f)
            // just left of the face, a metre above the spiked underside, falling
            sb.sim.runner.body.setTransform(rock.x - GameSim.RUNNER_R - 0.01f, rock.y + rock.h - 1f, 0f)
            sb.sim.runner.body.setLinearVelocity(0f, 0f)
            sb.run(0.6f)
            assertNull(sb.sim.death, "died: ${sb.sim.death?.reason}")
        } finally {
            sb.dispose()
        }
    }

    /**
     * Springs before the rock, from right at its face to well back, at the slowest and fastest
     * run speeds: the runner never gets above the rock. A rope bridges the gap underneath so the
     * runner has somewhere to go.
     */
    @ParameterizedTest(name = "spring {0} m before the rock at {1} m/s")
    @CsvSource("1.0,3.4", "2.0,3.4", "3.0,3.4", "4.5,3.4", "6.0,3.4", "1.0,6.0", "2.5,6.0", "4.0,6.0", "6.0,6.0", "8.0,6.0")
    fun `a spring can't bounce the runner over the rock`(before: Float, speed: Float) {
        val sb = Sandbox(gap = 4.5f, runnerX = -24f, speed = speed)
        try {
            val rock = sb.sim.addTunnelRock(-3.5f, 8f, Sandbox.LEVEL - 2.1f)
            val bottom = rock.y + rock.h
            sb.groundLine(rock.x - before - 0.8f, rock.x - before, INK_P.getValue(Ink.SPRING))
            sb.groundLine(-0.4f, 4.9f, INK_P.getValue(Ink.ROPE))
            var highest = Float.POSITIVE_INFINITY
            sb.run(8f) {
                if (sb.runnerX > rock.x - GameSim.RUNNER_R && sb.runnerX < rock.right + GameSim.RUNNER_R) {
                    highest = minOf(highest, sb.runnerY)
                }
            }
            assertTrue(highest > bottom, "runner reached y = $highest over a rock whose underside is at $bottom")
        } finally {
            sb.dispose()
        }
    }

    companion object {
        val INK_P = StageBuilder.PRESSURE

        init {
            TestNatives.ensure()
        }
    }
}
