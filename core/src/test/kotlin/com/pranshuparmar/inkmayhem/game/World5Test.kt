package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** World 5's ink pools and ink tide, and the Star nib. */
class World5Test {
    private fun stage(build: StageBuilder.() -> Unit) = StageDef(9, 9, "Test", Theme.MIDNIGHT, 3.9f, build)

    private fun <T> playing(p: StagePlay, block: (StagePlay) -> T): T = try {
        block(p)
    } finally {
        p.dispose()
    }

    @Test
    fun `an ink pool swallows a runner that rolls in`() = playing(StagePlay(stage {
        start(14f)
        checkpoint()
        inkPool(4.5f)
        flat(10f)
        finish()
    }, solve = false)) { p ->
        assertEquals(GameState.DEAD, p.play(20f))
        assertEquals(DeathKind.POOL, p.sim.death?.kind, p.summary)
        // it goes under at the surface, long before it would have fallen off the page
        assertTrue(p.lowestY < StageBuilder.START_LEVEL + StageBuilder.POOL_DOWN + 0.6f, "lowest ${p.lowestY}")
    }

    @Test
    fun `ink that dips into a pool is swallowed`() = playing(StagePlay(stage {
        start(14f)
        checkpoint()
        val x0 = x
        // a beam between the pit's walls, under the surface
        solve(x0 - 0.05f, level + 2f, x0 + 4.55f, level + 2f, Ink.BEAM, lead = 12f)
        inkPool(4.5f, solve = null)
        flat(10f)
        finish()
    })) { p ->
        p.play(20f)
        assertTrue("The ink pool swallowed it!" in p.events.toasts, "toasts ${p.events.toasts}")
        assertEquals(DeathKind.POOL, p.sim.death?.kind, p.summary)
    }

    @Test
    fun `a tight rope over a pool holds, and the runner crosses`() = playing(StagePlay(stage {
        start(14f)
        checkpoint()
        inkPool(4.5f)
        flat(10f)
        finish()
    })) { p ->
        assertEquals(GameState.CLEAR, p.play(20f), p.summary)
        assertTrue(p.events.toasts.none { "swallowed" in it }, "toasts ${p.events.toasts}")
    }

    private val tideWall = stage {
        start(14f)
        checkpoint()
        tideStart()
        flat(10f)
        wall(2.4f)
        flat(10f)
        tideEnd()
        flat(4f)
        finish()
    }

    @Test
    fun `the tide catches a runner that stalls at a wall`() = playing(StagePlay(tideWall, solve = false)) { p ->
        assertEquals(GameState.DEAD, p.play(20f))
        assertEquals(DeathKind.TIDE, p.sim.death?.kind, p.summary)
    }

    @Test
    fun `a runner that keeps rolling stays ahead of the tide, and it breaks at its end`() = playing(StagePlay(tideWall)) { p ->
        var closest = Float.POSITIVE_INFINITY
        val st = p.play(30f) {
            val t = p.sim.tides.single()
            if (t.up && t.ebbAt.isNaN()) closest = minOf(closest, p.x - t.x)
        }
        assertEquals(GameState.CLEAR, st, p.summary)
        val t = p.sim.tides.single()
        assertTrue(!t.ebbAt.isNaN(), "the tide broke")
        // it closes in from where it rose, but a clean run keeps it a couple of metres back
        assertTrue(closest in 1.5f..GameSim.TIDE_GAP + 0.5f, "closest $closest")
    }

    @Test
    fun `the tide holds still while a first-timer waits at a gate`() = playing(StagePlay(stage {
        start(14f)
        checkpoint()
        tideStart()
        flat(14f)
        hint(Ink.ROPE, x - 1f, x + 4.5f, x - 0.1f, level - 0.05f, x + 4.6f, level - 0.05f, waitX = x - 1.4f)
        inkPool(4.5f)
        flat(10f)
        tideEnd()
        flat(4f)
        finish()
    }, solve = false)) { p ->
        p.sim.learning = true
        p.play(12f)
        assertEquals(GameState.RUN, p.sim.state, p.summary)
        assertTrue(p.sim.waitingAt != null, "waiting at the pool")
        val t = p.sim.tides.single()
        val x = t.x
        p.play(3f)
        assertEquals(x, t.x, "the tide held")
        assertEquals(GameState.RUN, p.sim.state, p.summary)
    }

    @Test
    fun `with the Star nib creatures are zapped and spikes don't hurt`() = playing(StagePlay(stage {
        start(14f)
        val bx = x - 3f
        box(bx, up = 2.6f, holds = ItemKind.STAR_NIB)
        springOn(bx - 0.5f, lead = 4f)
        flat(10f)
        blot(x + 4f)
        eraser(x + 7f)
        beetle(x + 10f)
        flat(14f)
        spikes(x - 3f, level, 2f)
        checkpoint()
        flat(10f)
        finish()
    })) { p ->
        assertEquals(GameState.CLEAR, p.play(30f), p.summary)
        assertEquals(3, p.events.pops.count { it.startsWith("Zap!") }, "pops ${p.events.pops}")
    }
}
