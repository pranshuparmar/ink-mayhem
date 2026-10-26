package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.physics.TestNatives
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Whole seeded runs on the generated level, played by [InkBot]. */
class ScriptedRunTest {

    private class Run(seed: Long, inkFor: (HazardType) -> Ink = { it.par ?: Ink.ROPE }) {
        val events = RecordingEvents()
        // A portrait phone: 412 × 915 dp at 34.3 dp/m.
        val sim = GameSim(seed = seed, events = events, vwMetres = 12f, vhMetres = 26.7f)
        val bot = InkBot(sim, inkFor = inkFor)
        var maxCombo = 1
        var comboAt120 = 0

        init {
            sim.penDown(0, 0f, 0f, 0.45f, erase = false, t = 0.0) // pen down on the title card starts the run
        }

        /** Plays until [untilDist] metres, death, or [maxSeconds] of simulated time. */
        fun play(untilDist: Float, maxSeconds: Float = 240f) {
            var ticks = 0
            while (sim.state == GameState.RUN && sim.dist < untilDist && ticks < maxSeconds * 60) {
                bot.update()
                sim.realTime += 1.0 / 60
                sim.tick(1f / 60f)
                sim.cull(1f / 60f)
                maxCombo = maxOf(maxCombo, sim.combo)
                if (comboAt120 == 0 && sim.dist >= 120f) comboAt120 = sim.combo
                ticks++
            }
        }

        val introJudged: Int get() = events.pops.size
        fun dispose() = sim.dispose()
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = [1, 7, 42, 2026, 31337])
    fun `the correct-ink bot clears the six intro hazards without dying`(seed: Long) {
        val r = Run(seed)
        try {
            // The sixth intro hazard (the boulder gap) ends around 110 m; play well past it.
            r.play(untilDist = 140f)
            assertNull(r.sim.death, "died: ${r.sim.death?.reason} at ${r.sim.dist} m; judged ${r.events.pops}")
            assertTrue(r.events.pops.size >= 6, "only judged ${r.events.pops}")
            assertTrue(r.events.pops.take(6).all { it.startsWith("Clean") }, "intro judged ${r.events.pops.take(6)}")
        } finally {
            r.dispose()
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = [1, 7, 42, 2026, 31337])
    fun `the correct-ink bot reaches an 8-9 combo past 120 m`(seed: Long) {
        val r = Run(seed)
        try {
            r.play(untilDist = 170f)
            assertNull(r.sim.death, "died: ${r.sim.death?.reason} at ${r.sim.dist} m; judged ${r.events.pops}")
            assertTrue(r.sim.dist >= 170f)
            assertTrue(r.maxCombo >= 8, "max combo ${r.maxCombo}; judged ${r.events.pops}")
        } finally {
            r.dispose()
        }
    }

    @Test
    fun `a spring in the tunnel dies Spiked`() {
        val r = Run(7) { if (it == HazardType.TUNNEL) Ink.SPRING else it.par ?: Ink.ROPE }
        try {
            r.play(untilDist = 140f)
            assertEquals("Spiked", r.sim.death?.reason)
            assertEquals(DeathKind.SPIKED, r.sim.death?.kind)
        } finally {
            r.dispose()
        }
    }

    @Test
    fun `rope over a long gap dies Fell in`() {
        val r = Run(7) { if (it == HazardType.GAP_L) Ink.ROPE else it.par ?: Ink.ROPE }
        try {
            r.play(untilDist = 140f)
            assertEquals("Fell in", r.sim.death?.reason)
            assertTrue(GameSim.TOO_LONG_FOR_ROPE in r.events.toasts)
        } finally {
            r.dispose()
        }
    }

    @Test
    fun `the same seed generates the same level`() {
        val a = GameSim(seed = 99, vwMetres = 12f, vhMetres = 26.7f)
        val b = GameSim(seed = 99, vwMetres = 12f, vhMetres = 26.7f)
        try {
            assertEquals(a.hazards.map { Triple(it.type, it.x0, it.x1) }, b.hazards.map { Triple(it.type, it.x0, it.x1) })
            assertEquals(a.drops.map { it.x to it.y }, b.drops.map { it.x to it.y })
            // Only the first ~40 m exist before the run starts; they follow the scripted intro order.
            assertTrue(a.hazards.isNotEmpty())
            assertEquals(GameSim.INTRO.map { it.first }.take(a.hazards.size), a.hazards.map { it.type }.take(6))
        } finally {
            a.dispose()
            b.dispose()
        }
    }

    companion object {
        init {
            TestNatives.ensure()
        }
    }
}
