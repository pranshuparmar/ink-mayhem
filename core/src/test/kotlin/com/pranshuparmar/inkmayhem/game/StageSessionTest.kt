package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.game.StageSession.Phase
import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.physics.TestNatives
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Lives, checkpoint retries, game over, stars and unlocks. */
class StageSessionTest {
    class MapStore : StageStore {
        val stars = HashMap<String, Int>()
        val best = HashMap<String, Int>()
        override fun stars(id: String) = stars[id] ?: 0
        override fun best(id: String) = best[id] ?: 0
        override fun record(id: String, stars: Int, score: Int) {
            this.stars[id] = maxOf(stars(id), stars)
            best[id] = maxOf(best(id), score)
        }
    }

    private val store = MapStore()
    private val sim = GameSim(seed = 1, vwMetres = 12f, vhMetres = 26.7f)
    private val session = StageSession(sim, store)
    private val events = object : RecordingEvents() {
        override fun extraLife() {
            super.extraLife()
            session.extraLife()
        }
    }
    private var bot = StageBot(sim)

    init {
        sim.events = events
    }

    @AfterEach
    fun dispose() = sim.dispose()

    /** Plays until [until] or [seconds] pass; the bot draws the scripted solution. */
    private fun run(seconds: Float, until: () -> Boolean) {
        repeat((seconds * 60).toInt()) {
            if (until()) return
            if (session.phase == Phase.PLAY) bot.update()
            sim.realTime += 1.0 / 60
            sim.tick(1f / 60f)
            sim.cull(1f / 60f)
            session.update(1f / 60f)
        }
    }

    /** A small stage: a gap, the checkpoint, then a gap whose solution is missing ([trap]). */
    private fun mini(trap: Boolean) = StageDef(9, if (trap) 1 else 2, "Mini", Theme.PAGE, 3.6f) {
        start(14f)
        gap(4f)
        flat(8f)
        checkpoint()
        flat(6f)
        if (trap) openGap(5f) else gap(5f)
        flat(8f)
        finish()
    }

    @Test
    fun `the runner waits through the intro, then runs`() {
        session.start(Stages.FIRST_PAGE)
        val x = sim.runner.body.position.x
        run(StageSession.INTRO_S - 0.2f) { false }
        assertEquals(Phase.INTRO, session.phase)
        assertEquals(x, sim.runner.body.position.x, 0.01f)
        run(1f) { false }
        assertEquals(Phase.PLAY, session.phase)
        assertTrue(sim.runner.body.position.x > x + 0.5f)
    }

    @Test
    fun `a death costs a life and retries from the checkpoint with the score it had there`() {
        val def = mini(trap = true)
        session.start(def)
        run(60f) { sim.stageRun?.checkpoint == true }
        val cpScore = sim.stageRun!!.cpScore
        assertTrue(cpScore > 0.0)
        run(60f) { session.phase == Phase.DYING }
        assertEquals(2, session.lives)
        run(5f) { session.phase == Phase.INTRO }
        assertEquals(Phase.INTRO, session.phase)
        assertEquals(def.layout.checkpointX + 1f, sim.runner.body.position.x, 0.05f)
        assertEquals(cpScore, sim.score, 1e-6)
        assertEquals(GameState.RUN, sim.state)
    }

    @Test
    fun `the last life lost is game over, and try again restarts with three lives`() {
        val def = mini(trap = true)
        session.start(def)
        run(200f) { session.phase == Phase.GAME_OVER }
        assertEquals(Phase.GAME_OVER, session.phase)
        assertEquals(0, session.lives)
        assertEquals(3, session.deaths)
        session.tryAgain()
        assertEquals(StageSession.START_LIVES, session.lives)
        assertEquals(def.layout.startX, sim.runner.body.position.x, 0.05f)
        assertEquals(0, store.stars(def.id))
    }

    @Test
    fun `a clean clear earns three stars, records the best and unlocks the next stage`() {
        assertFalse(StageSession.unlocked(Stages.CELLAR, store))
        session.start(Stages.FIRST_PAGE)
        run(240f) { session.phase == Phase.CLEARED }
        val r = assertNotNull(session.result)
        assertEquals(3, r.stars)
        assertTrue(r.newBest)
        assertEquals(r.total, store.best("1-1"))
        assertEquals(r.score + r.inkBonus, r.total)
        assertTrue(StageSession.unlocked(Stages.CELLAR, store))
        // the 1-1 heart was picked up on the high road
        assertEquals(StageSession.START_LIVES + 1, session.lives)
    }

    @Test
    fun `a clear after a death, with heavy ink, earns one star`() {
        val def = mini(trap = true)
        session.start(def)
        run(60f) { session.phase == Phase.DYING }
        // swap in a working bot for the retry: draw the missing bridge by hand
        run(5f) { session.phase == Phase.INTRO }
        val gapX = def.layout.hazards[1].x0
        val p = StageBuilder.PRESSURE.getValue(Ink.BEAM)
        var t = 50.0
        for (i in 0..100) {
            val x = gapX + i * 0.05f
            t += 1.0 / 240
            if (i == 0) sim.penDown(3, x, 19.95f, p, false, t) else sim.penMove(3, x, 19.95f, p, t)
        }
        sim.penUp(3)
        run(60f) { session.phase == Phase.CLEARED }
        val r = assertNotNull(session.result)
        assertFalse(r.noDeaths)
        assertFalse(r.allLight, "a beam where rope was enough")
        assertEquals(1, r.stars)
    }

    @Test
    fun `lives carry into the next stage and hearts stop at nine`() {
        session.start(Stages.FIRST_PAGE)
        repeat(10) { session.extraLife() }
        assertEquals(StageSession.MAX_LIVES, session.lives)
        run(240f) { session.phase == Phase.CLEARED }
        assertTrue(session.next())
        assertEquals(Stages.CELLAR, session.stage)
        assertEquals(StageSession.MAX_LIVES, session.lives)
    }

    companion object {
        init {
            TestNatives.ensure()
        }
    }
}
