package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.game.Sandbox.Companion.LEVEL
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behaviours involving the runner and the boulder, on the real game tick.
 */
class RunnerBehaviourTest {

    @ParameterizedTest(name = "p={0} across {1} m")
    @MethodSource("crossings")
    fun `runner crosses rope, spring and beam bridges`(p: Float, gap: Float) {
        val s = Sandbox(gap, runnerX = -6f, speed = 3.4f)
        try {
            s.line(0f, gap, LEVEL - 0.05f, p)
            s.run(8f)
            assertEquals(GameState.RUN, s.sim.state, "died: ${s.sim.death?.reason}")
            assertTrue(s.runnerX > gap + 1f && s.runnerY < LEVEL + 1f, "runner ended at (${s.runnerX}, ${s.runnerY})")
        } finally {
            s.dispose()
        }
    }

    @ParameterizedTest(name = "rope p={0} over {1} m, boulder at {2} m/s")
    @MethodSource("boulderOnRope")
    fun `a boulder falls through rope bridges`(p: Float, gap: Float, v: Float) {
        val s = Sandbox(gap, runnerX = gap + 3f, speed = v / 1.3f)
        try {
            s.groundLine(-0.4f, gap + 0.4f, p)
            val b = s.boulder(-8f, v)
            s.run(6f)
            assertTrue(b.body.position.y > LEVEL + 3f, "boulder still at y=${b.body.position.y}")
        } finally {
            s.dispose()
        }
    }

    @ParameterizedTest(name = "beam p={0} over {1} m")
    @MethodSource("beamCarries")
    fun `an 8 m beam carries the runner and the boulder`(p: Float, gap: Float) {
        val s = Sandbox(gap, runnerX = -2f, speed = 3.4f)
        try {
            s.line(0f, gap, LEVEL - 0.05f, p)
            val b = s.boulder(-14f, 3.4f * 1.3f)
            s.run(7f)
            assertTrue(s.runnerX > gap + 1f && s.runnerY < LEVEL + 1f, "runner ended at (${s.runnerX}, ${s.runnerY})")
            assertTrue(b.body.position.x > gap && b.body.position.y < LEVEL, "boulder ended at ${b.body.position}")
        } finally {
            s.dispose()
        }
    }

    @ParameterizedTest(name = "spring p={0} over {1} m, boulder at {2} m/s")
    @MethodSource("springLaunch")
    fun `springs launch the boulder`(p: Float, gap: Float, v: Float) {
        val s = Sandbox(gap, runnerX = gap + 10f, speed = v / 1.3f)
        try {
            s.line(0f, gap, LEVEL - 0.05f, p)
            val b = s.boulder(-8f, v)
            var minVy = 0f
            s.run(5f) { minVy = minOf(minVy, b.body.linearVelocity.y) }
            assertTrue(minVy <= -9.59f, "boulder vy only reached $minVy")
        } finally {
            s.dispose()
        }
    }

    @Test
    fun `a rope longer than 6_8 m snaps on its own`() {
        val s = Sandbox(7.6f, runnerX = 7.6f + 20f, speed = 3.4f)
        try {
            s.line(0f, 7.6f, LEVEL - 0.05f, 0.2f)
            var snapAt = -1f
            s.run(3f) { if (snapAt < 0 && s.sim.snaps > 0) snapAt = s.sim.simT }
            assertTrue(snapAt in 0.3f..1.2f, "first snap at $snapAt s")
            assertTrue(GameSim.TOO_LONG_FOR_ROPE in s.events.toasts)
        } finally {
            s.dispose()
        }
    }

    @Test
    fun `a rope under 6_8 m is left alone`() {
        val s = Sandbox(6f, runnerX = 6f + 20f, speed = 3.4f)
        try {
            s.line(0f, 6f, LEVEL - 0.05f, 0.2f)
            s.run(3f)
            assertEquals(0, s.sim.snaps)
            assertTrue(GameSim.TOO_LONG_FOR_ROPE !in s.events.toasts)
        } finally {
            s.dispose()
        }
    }

    companion object {
        private val ALL_INKS = floatArrayOf(0.12f, 0.2f, 0.28f, 0.38f, 0.47f, 0.56f, 0.68f, 0.8f, 0.95f)

        @JvmStatic
        fun crossings(): Stream<Arguments> = Stream.of(5.5f, 5.7f, 6f).flatMap { g ->
            ALL_INKS.toList().stream().map { p -> Arguments.of(p, g) }
        }

        @JvmStatic
        fun boulderOnRope(): Stream<Arguments> = Stream.of(4.8f, 5.3f, 5.8f).flatMap { g ->
            Stream.of(4.4f, 5.4f, 6.5f).flatMap { v -> Stream.of(0.15f, 0.25f).map { p -> Arguments.of(p, g, v) } }
        }

        @JvmStatic
        fun beamCarries(): Stream<Arguments> = Stream.of(7.6f, 8f).flatMap { g ->
            Stream.of(0.7f, 0.85f, 1f).map { p -> Arguments.of(p, g) }
        }

        @JvmStatic
        fun springLaunch(): Stream<Arguments> = Stream.of(4.8f, 5.8f).flatMap { g ->
            Stream.of(0.38f, 0.47f, 0.56f).flatMap { p -> Stream.of(4.4f, 6.5f).map { v -> Arguments.of(p, g, v) } }
        }
    }
}
