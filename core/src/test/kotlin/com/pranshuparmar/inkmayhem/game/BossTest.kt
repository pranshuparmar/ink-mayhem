package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.TestNatives
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Boss arenas: the wall closes behind, the runner turns at the walls, the boss falls to its weakness and the way opens. */
class BossTest {
    private fun arenaStage(kind: BossKind) = StageDef(9, 5, "Boss", Theme.PAGE, 3.4f) {
        start(12f)
        flat(6f)
        checkpoint()
        flat(6f)
        arena(kind)
        flat(4f)
        finish()
    }

    @ParameterizedTest
    @EnumSource(BossKind::class)
    fun `the bot beats each boss with its weakness, and the way on opens`(kind: BossKind) {
        val p = StagePlay(arenaStage(kind))
        try {
            var closed = false
            var hits = 0
            var hp = kind.hp
            val st = p.play(120f) {
                if (p.sim.bossFight) closed = true
                val b = p.sim.boss!!
                if (b.hp < hp) { hits++; hp = b.hp }
            }
            assertEquals(GameState.CLEAR, st, p.summary)
            assertTrue(closed, "the arena closed")
            assertTrue(p.sim.boss!!.down)
            assertEquals(kind.hp, hits)
            assertFalse(p.sim.bossFight)
            assertTrue(p.events.pops.any { it.startsWith(kind.title) }, p.summary)
        } finally {
            p.dispose()
        }
    }

    @ParameterizedTest
    @EnumSource(BossKind::class)
    fun `every arena is a closed room, walls and a roof with nothing over them`(kind: BossKind) {
        val p = StagePlay(arenaStage(kind), solve = false)
        try {
            val a = p.sim.arenas.single()
            val top = a.level - GameSim.ARENA_WALL
            // a roof over the whole room, its underside no higher than the walls' tops (y points down)
            val roofed = p.sim.ceilings.any { c -> c.x <= a.x0 + 1e-3f && c.right >= a.x1 - 1e-3f && c.y + c.h >= top - 1e-3f }
            assertTrue(roofed, "no roof over the ${kind.title}'s arena")
        } finally {
            p.dispose()
        }
    }

    @ParameterizedTest
    @EnumSource(BossKind::class)
    fun `getting out of the arena without beating the boss doesn't clear the stage`(kind: BossKind) {
        val p = StagePlay(arenaStage(kind), solve = false)
        try {
            var steps = 0
            while (!p.sim.bossFight && p.sim.state == GameState.RUN && steps++ < 200) p.play(0.25f)
            assertTrue(p.sim.bossFight, p.summary)
            // however it got there: the runner past the far wall, rolling on towards the finish
            val a = p.sim.arenas.single()
            val body = p.sim.runner.body
            body.setTransform(a.x1 + 3f, a.level - p.sim.runner.r - 0.05f, 0f)
            body.setLinearVelocity(4f, 0f)
            p.sim.runner.dir = 1
            val st = p.play(8f) { }
            assertTrue(st != GameState.CLEAR, "cleared with ${kind.title} still standing")
            if (st == GameState.RUN) {
                val x = p.sim.runner.body.position.x
                assertTrue(x > a.x0 - 0.5f && x < a.x1 + 0.5f, "runner at $x, outside the arena ${a.x0}..${a.x1}")
            }
        } finally {
            p.dispose()
        }
    }

    @Test
    fun `in an arena the runner rolls back and forth between the walls`() {
        // the Airliner flies too high to touch a runner on the ground
        val p = StagePlay(arenaStage(BossKind.AIRLINER), solve = false)
        try {
            var turns = 0
            var dir = 1
            var minX = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            p.play(25f) {
                if (p.sim.runner.dir != dir) { turns++; dir = p.sim.runner.dir }
                if (p.sim.bossFight) { minX = minOf(minX, p.x); maxX = maxOf(maxX, p.x) }
            }
            assertEquals(GameState.RUN, p.sim.state, p.summary)
            assertTrue(turns >= 3, "turned $turns times")
            val a = p.sim.arenas.first()
            assertTrue(minX > a.x0 - 0.1f && maxX < a.x1 + 0.1f, "kept between the walls: $minX..$maxX")
        } finally {
            p.dispose()
        }
    }

    @Test
    fun `left alone, a boss costs a life`() {
        val p = StagePlay(arenaStage(BossKind.BIG_BLOT), solve = false)
        try {
            assertEquals(GameState.DEAD, p.play(60f), p.summary)
            assertEquals(DeathKind.BOSS, p.sim.death?.kind)
        } finally {
            p.dispose()
        }
    }

    companion object {
        init {
            TestNatives.ensure()
        }
    }
}
