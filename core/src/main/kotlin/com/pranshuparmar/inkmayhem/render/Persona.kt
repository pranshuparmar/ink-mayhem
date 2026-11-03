package com.pranshuparmar.inkmayhem.render

import com.pranshuparmar.inkmayhem.game.GameSim
import com.pranshuparmar.inkmayhem.game.GameState
import com.pranshuparmar.inkmayhem.game.ItemKind
import com.pranshuparmar.inkmayhem.game.Mood
import com.pranshuparmar.inkmayhem.physics.Ink
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The runner's personality in a stage: from what the sim is doing it picks the face (scared of
 * the boulder, wide-mouthed off a spring, smug after a stomp, wobbly on rope, watching the pen
 * while it draws, dozing off at a gate), where the eyes look, how the body squashes, and now and
 * then a remark in a speech bubble. Looks only: it reads the sim and never changes it, so
 * recordings replay the same.
 */
class Persona {
    val state = BuddyState()
    /** The body's stretch (rising fast) and squash (landing), for the drawer. */
    var stretch = 0f
        private set
    var impact = 0f
        private set

    private var inkRef: Any? = null
    private var lastVx = 0f
    private var lastVy = 0f
    private var fallVy = 0f
    private var squashed = 0
    private var bounces = 0
    private var snaps = 0
    private var gold = 0
    private var reached = -1
    private var power: ItemKind? = null
    private var bold = false
    private var bossFight = false
    private var bossHp = -1
    private var bossDown = false
    private var tideUp = false
    private var onInk: Ink? = null
    private var mood = Mood.CALM
    private var simState = GameState.RUN
    private var held = true
    private var stompAt = -10f
    private var pick = 0

    /** [remarks]: speech bubbles on (the setting). [still]: reduced motion. */
    fun update(sim: GameSim, dt: Float, remarks: Boolean, still: Boolean) {
        val run = sim.runner
        val v = run.body.linearVelocity
        if (sim.ink !== inkRef) rebase(sim)
        val sdt = max(dt, 1e-4f)
        val ax = ((v.x - lastVx) / sdt).coerceIn(-60f, 60f)
        val ay = ((v.y - lastVy) / sdt).coerceIn(-60f, 60f)
        // a landing after a fall squashes it
        var hit = 0f
        if (fallVy > 4f && v.y < 1.2f) hit = min(0.5f, fallVy / 22f)
        fallVy = if (v.y > 0.5f) max(fallVy, v.y) else 0f
        impact = max(hit, run.squash * 0.5f)
        stretch = if (!still && v.y < -2.5f) ((-v.y - 2.5f) / 14f).coerceIn(0f, 0.3f) else 0f
        lastVx = v.x
        lastVy = v.y

        pickFace(sim)
        look(sim)
        if (remarks && sim.stage != null) remark(sim) else if (!remarks) state.hush()
        state.update(dt, if (still) 0f else ax, if (still) 0f else ay, still, if (still) 0f else impact)
    }

    private fun rebase(sim: GameSim) {
        inkRef = sim.ink
        squashed = sim.squashed
        bounces = sim.bounces
        snaps = sim.snaps
        gold = sim.stageRun?.goldMask ?: 0
        reached = sim.stageRun?.reached ?: -1
        power = sim.runner.power
        bold = sim.runner.bold
        bossFight = sim.bossFight
        bossHp = sim.boss?.hp ?: -1
        bossDown = sim.boss?.down ?: false
        tideUp = false
        onInk = null
        held = true
        stompAt = -10f
        state.hush()
    }

    private fun pickFace(sim: GameSim) {
        val run = sim.runner
        val v = run.body.linearVelocity
        val t = sim.simT
        val tide = sim.tides.firstOrNull { it.up && it.ebbAt.isNaN() }
        val tideNear = tide != null && run.body.position.x - tide.x < 6f
        val wait = sim.waitingAt
        val e = when {
            sim.state == GameState.DEAD || sim.mood == Mood.OUCH -> Expr.OUCH
            sim.state == GameState.CLEAR -> Expr.HAPPY
            sim.mood == Mood.SCARED || tideNear -> Expr.SCARED
            t - stompAt < 0.9f -> Expr.PROUD
            t - sim.bounceAt < 1.1f && v.y < 3f -> Expr.WHEE
            sim.mood == Mood.DIZZY -> Expr.DIZZY
            wait != null && abs(v.x) < 0.3f && sim.drawing == null -> if (wait.waitT > SLEEPY_AFTER) Expr.SLEEPY else Expr.CURIOUS
            sim.drawing != null -> Expr.CURIOUS
            sim.bossFight -> Expr.DETERMINED
            sim.mood == Mood.WORRIED -> Expr.WORRIED
            sim.runnerOnInk == Ink.ROPE && abs(v.y) < 1.5f -> Expr.WOBBLY
            sim.runnerOnInk == Ink.BEAM -> Expr.PROUD
            sim.mood == Mood.HAPPY || run.power == ItemKind.STAR_NIB -> Expr.HAPPY
            else -> Expr.CALM
        }
        state.set(e)
    }

    /** Eyes on the pen while it draws, on a gate it waits at, a boss, a creature or a gold star near; back at what's chasing it. */
    private fun look(sim: GameSim) {
        val run = sim.runner
        val p = run.body.position
        val dir = run.dir.toFloat()
        val ex = p.x + dir * run.r * 0.14f
        val ey = p.y - run.r * 0.5f
        fun at(x: Float, y: Float) {
            state.lookX = x - ex
            state.lookY = (y - ey) * 0.8f
        }
        val d = sim.drawing
        val boss = sim.boss
        val wait = sim.waitingAt
        when {
            d != null && d.size > 0 -> at(d.xs[d.size - 1], d.ys[d.size - 1])
            state.expr == Expr.SCARED -> { state.lookX = -dir; state.lookY = 0.1f }
            wait != null -> at((wait.gx0 + wait.gx1) / 2f, (wait.gy0 + wait.gy1) / 2f)
            boss != null && !boss.down && sim.bossFight -> at(boss.body.position.x, boss.body.position.y)
            else -> {
                var best = Float.POSITIVE_INFINITY
                var tx = Float.NaN
                var ty = 0f
                for (i in sim.blots.indices) {
                    val b = sim.blots[i]
                    if (b.dead || !b.awake) continue
                    val q = b.body.position
                    val ahead = (q.x - p.x) * dir
                    if (ahead < -1f || ahead > 8f) continue
                    val dd = abs(q.x - p.x) + abs(q.y - p.y)
                    if (dd < best) { best = dd; tx = q.x; ty = q.y }
                }
                for (i in sim.golds.indices) {
                    val g = sim.golds[i]
                    if (g.taken || g.hidden) continue
                    val ahead = (g.x - p.x) * dir
                    if (ahead < 0f || ahead > 5f) continue
                    val dd = abs(g.x - p.x) + abs(g.y - p.y)
                    if (dd < best) { best = dd; tx = g.x; ty = g.y }
                }
                when {
                    !tx.isNaN() -> at(tx, ty)
                    state.expr == Expr.WORRIED -> { state.lookX = dir; state.lookY = 0.9f }
                    run.body.linearVelocity.y > 3f -> { state.lookX = dir * 0.6f; state.lookY = 0.8f }
                    else -> { state.lookX = dir; state.lookY = 0.12f }
                }
            }
        }
    }

    /** Remarks on what just happened, now and then. */
    private fun remark(sim: GameSim) {
        val run = sim.runner
        val t = sim.simT
        if (held && !sim.runnerHeld) {
            held = false
            if (sim.state == GameState.RUN && chance(0.8f)) say(START)
        }
        if (sim.runnerHeld) held = true
        if (sim.state != simState) {
            when (sim.state) {
                GameState.DEAD -> say(DEATH, force = true)
                GameState.CLEAR -> say(CLEAR, force = true)
                else -> Unit
            }
            simState = sim.state
        }
        if (sim.state != GameState.RUN) return
        if (sim.squashed > squashed) {
            stompAt = t
            if (chance(0.7f)) say(STOMP)
        }
        squashed = sim.squashed
        if (sim.bounces > bounces && chance(0.35f)) say(BOING)
        bounces = sim.bounces
        if (sim.snaps > snaps && chance(0.6f)) say(SNAP)
        snaps = sim.snaps
        val g = sim.stageRun?.goldMask ?: 0
        if (Integer.bitCount(g) > Integer.bitCount(gold)) say(GOLD, force = true)
        gold = g
        val r = sim.stageRun?.reached ?: -1
        if (r > reached && chance(0.6f)) say(CHECKPOINT)
        reached = r
        val pw = run.power
        if (pw != null && pw != power) say(arrayOf(POWER_LINES[pw] ?: "Ooh!"), force = true)
        power = pw
        if (run.bold && !bold) say(BOLD, force = true)
        bold = run.bold
        val boss = sim.boss
        if (sim.bossFight && !bossFight) say(BOSS_START, force = true)
        bossFight = sim.bossFight
        if (boss != null) {
            if (bossHp >= 0 && boss.hp < bossHp && !boss.down && chance(0.6f)) say(BOSS_HIT)
            if (boss.down && !bossDown) say(BOSS_DOWN, force = true)
            bossHp = boss.hp
            bossDown = boss.down
        }
        val up = sim.tides.any { it.up && it.ebbAt.isNaN() }
        if (up && !tideUp) say(TIDE, force = true)
        tideUp = up
        val ink = sim.runnerOnInk
        if (ink != onInk && ink != null) {
            if (ink == Ink.ROPE && chance(0.2f)) say(ROPE)
            if (ink == Ink.BEAM && chance(0.2f)) say(BEAM)
        }
        onInk = ink
        if (sim.mood == Mood.WORRIED && mood != Mood.WORRIED && chance(0.2f)) say(EDGE)
        mood = sim.mood
        // (at a gate it waits at, Dot steps out of the page to explain instead)
    }

    private fun chance(p: Float): Boolean {
        pick = (pick * 1103515245 + 12345) and 0x7fffffff
        return (pick % 1000) < p * 1000f
    }

    private fun say(lines: Array<String>, force: Boolean = false) {
        pick = (pick * 1103515245 + 12345) and 0x7fffffff
        state.say(lines[pick % lines.size], force)
    }

    companion object {
        /** Waiting this long with nothing drawn, it starts to doze. */
        const val SLEEPY_AFTER = 7f

        private val START = arrayOf("Here we go!", "Let's roll!", "Fresh page!", "Ink me up!")
        private val DEATH = arrayOf("Oof.", "Ouch!", "Again!", "Ow, ow, ow.")
        private val CLEAR = arrayOf("Ta-da!", "Nailed it!", "Too easy!", "We did it!")
        private val STOMP = arrayOf("Squish!", "Gotcha!", "Sorry, Blot!", "Splat!")
        private val BOING = arrayOf("Wheee!", "Boing!", "Up we go!")
        private val SNAP = arrayOf("Eek!", "Whoa!", "That snapped!")
        private val GOLD = arrayOf("Shiny!", "Ooh, gold!", "Mine!")
        private val CHECKPOINT = arrayOf("Bookmarked!", "Saved my place.")
        private val BOLD = arrayOf("Look at my hat!", "Feeling bold.")
        private val BOSS_START = arrayOf("You don't scare me!", "Big one...", "Let's do this.")
        private val BOSS_HIT = arrayOf("Take that!", "Ha!", "And again!")
        private val BOSS_DOWN = arrayOf("We did it!", "Who's next?", "Down it goes!")
        private val TIDE = arrayOf("Gotta go, gotta go!", "Run!", "Not the tide!")
        private val ROPE = arrayOf("Wobbly...", "Steady...")
        private val BEAM = arrayOf("Solid!", "Nice and sturdy.")
        private val EDGE = arrayOf("Uh-oh...", "Um...")
        private val POWER_LINES = mapOf(
            ItemKind.HIGHLIGHTER to "Let's find stuff!",
            ItemKind.CHALK to "Floaty lines!",
            ItemKind.GOLD_NIB to "Golden!",
            ItemKind.STAR_NIB to "Untouchable!",
        )
    }
}
