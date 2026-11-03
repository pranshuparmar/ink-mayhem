package com.pranshuparmar.inkmayhem.game

import kotlin.math.floor
import kotlin.math.min

/** Where stage results live (Preferences in the game, a map in tests). */
interface StageStore {
    fun stars(id: String): Int
    fun best(id: String): Int
    /** Gold stars ever found in a stage: a bit per index 0..2. */
    fun gold(id: String): Int
    fun record(id: String, stars: Int, score: Int, gold: Int)
}

/**
 * Playing stages: lives, the intro before each try, retries from the checkpoint, game over and
 * the clear tally with stars. Runs [sim], a sim of its own.
 *
 * A death costs a life and restarts from the last checkpoint. With no lives left it is game
 * over: "Try again" restarts the stage with [START_LIVES]. Lives carry on into the next stage
 * and hearts add one (up to [MAX_LIVES]).
 */
class StageSession(val sim: GameSim, private val store: StageStore) {
    enum class Phase { INTRO, PLAY, DYING, GAME_OVER, CLEARED }

    class Result(
        val stage: StageDef,
        val score: Int,
        val inkBonus: Int,
        val total: Int,
        val stars: Int,
        val noDeaths: Boolean,
        val allLight: Boolean,
        val newBest: Boolean,
        /** Gold stars taken on this clear, and all ever found in the stage (bits per index). */
        val gold: Int,
        val goldEver: Int,
    )

    lateinit var stage: StageDef
        private set
    var lives = START_LIVES
        private set
    var phase = Phase.INTRO
        private set
    /** Seconds in the current phase (real time). */
    var phaseT = 0f
        private set
    /** Deaths in this stage since it was started (game over resets them with the stage). */
    var deaths = 0
        private set
    var result: Result? = null
        private set
    /** True for the first intro of a stage (the long one, with the stage name). */
    var firstIntro = true
        private set
    /** Stars taken since the lives were last reset; every [GameSim.STARS_PER_LIFE] is a life. */
    var stars = 0
        private set

    /** Starts [def] with its intro card. */
    fun start(def: StageDef, freshLives: Boolean = true) {
        stage = def
        if (freshLives) {
            lives = START_LIVES
            stars = 0
        }
        deaths = 0
        result = null
        firstIntro = true
        sim.loadStage(def, fromCheckpoint = false)
        enter(Phase.INTRO)
    }

    /** A star was taken. Returns true when it made an extra life. */
    fun addStar(): Boolean {
        stars++
        if (stars % GameSim.STARS_PER_LIFE != 0) return false
        extraLife()
        return true
    }

    /** A heart: one more life. */
    fun extraLife() {
        lives = min(MAX_LIVES, lives + 1)
    }

    /** Game over → "Try again": the stage from the start with full lives. */
    fun tryAgain() = start(stage, freshLives = true)

    /** After a clear: the next stage, keeping the lives. Returns false at the end of the game. */
    fun next(): Boolean {
        val n = Stages.next(stage) ?: return false
        start(n, freshLives = false)
        return true
    }

    /** Call once per frame with real seconds, after the sim ticked. */
    fun update(dt: Float) {
        phaseT += dt
        when (phase) {
            Phase.INTRO -> if (phaseT >= if (firstIntro) INTRO_S else RETRY_INTRO_S) {
                sim.runnerHeld = false
                enter(Phase.PLAY)
            }
            Phase.PLAY -> when (sim.state) {
                GameState.DEAD -> {
                    lives--
                    deaths++
                    enter(Phase.DYING)
                }
                GameState.CLEAR -> if (sim.realTime - sim.clearedAt >= CLEAR_DELAY) finish()
                else -> Unit
            }
            Phase.DYING -> if (phaseT >= DYING_S) {
                if (lives > 0) {
                    sim.loadStage(stage, fromCheckpoint = true)
                    firstIntro = false
                    enter(Phase.INTRO)
                } else {
                    enter(Phase.GAME_OVER)
                }
            }
            Phase.GAME_OVER, Phase.CLEARED -> Unit
        }
    }

    private fun finish() {
        val run = sim.stageRun ?: return
        val score = floor(sim.score).toInt()
        val inkBonus = floor(sim.inkLeft).toInt() * INK_BONUS
        val total = score + inkBonus
        val noDeaths = deaths == 0
        val light = run.allLight
        val stars = 1 + (if (noDeaths) 1 else 0) + (if (light) 1 else 0)
        val newBest = total > store.best(stage.id)
        store.record(stage.id, stars, total, run.goldMask)
        result = Result(stage, score, inkBonus, total, stars, noDeaths, light, newBest, run.goldMask, store.gold(stage.id))
        enter(Phase.CLEARED)
    }

    private fun enter(p: Phase) {
        phase = p
        phaseT = 0f
    }

    companion object {
        const val START_LIVES = 3
        const val MAX_LIVES = 9
        const val INTRO_S = 2.2f
        const val RETRY_INTRO_S = 1.2f
        const val DYING_S = 1.6f
        const val CLEAR_DELAY = 1.0
        /** Tally: points per unit of ink left at the finish. */
        const val INK_BONUS = 10

        /** A stage can be played once the one before it has been cleared. */
        fun unlocked(def: StageDef, store: StageStore): Boolean {
            val i = Stages.ALL.indexOf(def)
            return i <= 0 || store.stars(Stages.ALL[i - 1].id) > 0
        }
    }
}
