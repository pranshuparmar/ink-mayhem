package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every stage of every world, played with its scripted solution. */
class StageTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("stageIds")
    fun `the scripted solution clears the stage with light ink and no deaths`(id: String) {
        val p = StagePlay(Stages.byId(id)!!)
        try {
            val st = p.play(240f)
            println("STAGE $id: ${p.summary} drops ${p.sim.stageRun?.drops} lowest ${p.lowestY} highest ${p.highestY}")
            assertEquals(GameState.CLEAR, st, p.summary)
            assertTrue(p.sim.stageRun!!.allLight, "heavy judgements: ${p.events.pops}")
            assertEquals(Stages.byId(id)!!.layout.checkpoints.size, p.events.checkpoints, "checkpoints")
            assertEquals(1, p.events.clears)
            val hearts = Stages.byId(id)!!.layout.items.count { it.kind == ItemKind.HEART }
            assertEquals(hearts, p.events.hearts, "the solution takes the high road to every heart")
            val golds = Stages.byId(id)!!.layout.items.count { it.kind == ItemKind.GOLD }
            assertEquals(golds, Integer.bitCount(p.sim.stageRun!!.goldMask), "the solution finds every gold star")
        } finally {
            p.dispose()
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["2-1", "3-1", "4-1", "5-1"])
    fun `a first run of a world's first stage stops at each new thing and clears`(id: String) {
        val def = Stages.byId(id)!!
        val p = StagePlay(def)
        try {
            p.sim.learning = true
            val waited = HashSet<Gate>()
            val st = p.play(300f) { p.sim.waitingAt?.let { waited += it } }
            assertEquals(GameState.CLEAR, st, p.summary)
            assertEquals(def.layout.gates.count { !it.waitX.isNaN() }, waited.size, "waited at ${waited.map { it.label }}")
            assertEquals(3, Integer.bitCount(p.sim.stageRun!!.goldMask), "gold stars")
        } finally {
            p.dispose()
        }
    }

    @Test
    fun `spring under the cellar's spiked ceilings is fatal`() {
        val p = StagePlay(World2.CELLAR_DOOR, inkFor = { if (it == Ink.ROPE) Ink.SPRING else it })
        try {
            assertEquals(GameState.DEAD, p.play(240f))
            assertEquals(DeathKind.SPIKED, p.sim.death?.kind, p.summary)
        } finally {
            p.dispose()
        }
    }

    @Test
    fun `rope over Flight Path's long gap falls in`() {
        val p = StagePlay(World3.FLIGHT_PATH, inkFor = { if (it == Ink.BEAM) Ink.ROPE else it })
        try {
            assertEquals(GameState.DEAD, p.play(240f))
            assertEquals(DeathKind.FELL, p.sim.death?.kind, p.summary)
        } finally {
            p.dispose()
        }
    }

    @Test
    fun `with no ink at all every stage ends early`() {
        for (def in Stages.ALL) {
            val p = StagePlay(def, solve = false)
            try {
                assertEquals(GameState.DEAD, p.play(240f), "${def.id}: ${p.summary}")
            } finally {
                p.dispose()
            }
        }
    }

    @Test
    fun `every world has five stages, numbered in order, each with three gold stars`() {
        assertEquals(5, Stages.WORLDS.size)
        for (w in Stages.WORLDS) {
            assertEquals(5, w.stages.size, w.name)
            for ((i, def) in w.stages.withIndex()) {
                assertEquals(w.number, def.world, def.id)
                assertEquals(i + 1, def.number, def.id)
                assertEquals(w.theme, def.theme, def.id)
                assertEquals(3, def.layout.items.count { it.kind == ItemKind.GOLD }, "${def.id} gold stars")
            }
            assertTrue(w.stages.last().layout.arenas.isNotEmpty(), "${w.name} ends with a boss")
        }
    }

    @Test
    fun `every stage has a start, a checkpoint and a finish in order`() {
        for (def in Stages.ALL) {
            val l = def.layout
            assertTrue(l.startX < l.checkpointX && l.checkpointX < l.finishX, def.id)
            assertTrue(!l.groundAt(l.startX).isNaN() && !l.groundAt(l.checkpointX + 1f).isNaN(), "${def.id} spawns on ground")
        }
    }

    companion object {
        @JvmStatic
        fun stageIds() = Stages.ALL.map { it.id }
    }
}
