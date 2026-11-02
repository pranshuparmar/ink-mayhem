package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every World 1 stage, played with its scripted solution. */
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
    fun `rope over Up High's long gap falls in`() {
        val p = StagePlay(World3.CLOUD_STEPS, inkFor = { if (it == Ink.BEAM) Ink.ROPE else it })
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
        for (w in Stages.WORLDS.filter { it.stages.size == 5 }) {
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
