package com.pranshuparmar.inkmayhem.game

import com.badlogic.gdx.math.Vector2
import com.pranshuparmar.inkmayhem.fastEach
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.Contact
import com.badlogic.gdx.physics.box2d.ContactImpulse
import com.badlogic.gdx.physics.box2d.ContactListener
import com.badlogic.gdx.physics.box2d.Manifold
import com.pranshuparmar.inkmayhem.physics.FixtureKind
import com.pranshuparmar.inkmayhem.physics.FixtureTag
import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.physics.InkPhysics
import com.pranshuparmar.inkmayhem.physics.JointKind
import com.pranshuparmar.inkmayhem.physics.Stroke
import com.pranshuparmar.inkmayhem.render.Tok
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class GameState { TITLE, RUN, DEAD, CLEAR }

/**
 * The POC's game logic (everything in its IIFE except DOM, canvas and WebAudio): run lifecycle,
 * world generation, the fixed-step tick, drawing and erasing, scoring and deaths.
 *
 * World units are metres, y down. Input arrives in world coordinates with normalized pressure.
 * World generation draws from a seeded RNG; cosmetic randomness (tips, particle phases) uses a
 * separate one so it never perturbs the level.
 */
class GameSim(
    seed: Long = Random.nextLong(),
    var events: GameEvents = GameEvents.None,
    /** Best score so far (persisted by the caller via [GameEvents.died]). */
    var best: Int = 0,
    /** Initial viewport in metres (W / SCALE, H / SCALE); see [setViewport]. */
    vwMetres: Float = 12f,
    vhMetres: Float = 26f,
) {
    var seed: Long = seed
        private set
    private var gen = Random(seed)
    private val fx = Random(seed xor 0x5DEECE66DL)

    var state = GameState.TITLE
        private set
    lateinit var ink: InkPhysics
        private set
    lateinit var runner: Runner
        private set

    val blocks = ArrayList<com.pranshuparmar.inkmayhem.physics.GroundRect>()
    val ceilings = ArrayList<com.pranshuparmar.inkmayhem.physics.GroundRect>()
    val hazards = ArrayList<Hazard>()
    val drops = ArrayList<Drop>()
    val notes = ArrayList<Note>()
    val boulders = ArrayList<Boulder>()
    /** Stage pieces (empty in Endless): floor and ceiling spike strips, lifts and seesaws. */
    val spikeStrips = ArrayList<com.pranshuparmar.inkmayhem.physics.GroundRect>()
    val lifts = ArrayList<Lift>()
    val seesaws = ArrayList<Seesaw>()

    var combo = 1
        private set
    var score = 0.0
        private set
    private var scoredDist = 0f
    private var pendingDeath: String? = null
    private var pendingBounce = false
    private val boulderBounce = ArrayList<Body>(2)

    private var genX = 0f
    private var level = 20f
    private var nSec = 0
    private var lastHazard: HazardType? = null
    private var boulderCd = 0
    private val poolWeights = FloatArray(POOL.size)

    /** Camera top-left in metres. */
    val cam = Vector2()
    /** Camera before the latest tick, for render interpolation. */
    val camPrev = Vector2()
    /** Viewport size in metres (W / SCALE, H / SCALE). */
    var vw = vwMetres
        private set
    var vh = vhMetres
        private set
    /** Screen dp per metre; only the scratch volume uses it. */
    var scale = 34f
    /** "Colour ink by type": snap particles take the ink's colour instead of plain ink. */
    var tint = false

    var dist = 0f
        private set
    var inkLeft = INK_START
        private set
    var timeScale = 1f
        private set
    var simT = 0f
        private set
    /** Real-time clock (seconds) maintained by the caller, for the restart delay. */
    var realTime = 0.0
    var deadAt = 0.0
        private set
    var death: Death? = null
        private set
    private var nextSpeedNote = 100f
    var snaps = 0
        private set
    /** Spring bounces of the runner this run. */
    var bounces = 0
        private set

    /** Tutorial: the runner stands still (no drive, no stuck rules) until released. */
    var runnerHeld = false
    /** Tutorial: frame this x instead of following the runner (NaN = follow). */
    var camFocusX = Float.NaN
    /** Where the runner sits in the view, as a fraction of its height (the tutorial's card covers the top). */
    var camRunnerY = 0.6f
    /** Tutorial: strokes cost nothing. */
    var unlimitedInk = false

    // ---------------------------------------------------------------- stages

    /** The stage being played, or null in Endless (and the tutorial). */
    var stage: StageDef? = null
        private set
    /** Progress through the current stage attempt, kept across checkpoint retries. */
    var stageRun: StageRun? = null
        private set
    /** When the runner crossed the finish line (state [GameState.CLEAR]). */
    var clearedAt = 0.0
        private set
    private var runnerOnLift = 0

    /** A stage attempt: its checkpoint and what the player had when they passed it. */
    class StageRun(val def: StageDef, hazardCount: Int) {
        var checkpoint = false
        var cpScore = 0.0
        var cpCombo = 1
        var cpInk = INK_START
        var cpDrops = 0
        /** Per stage hazard: 0 not judged, 1 par or lighter, 2 heavier than par. */
        val judged = IntArray(hazardCount)
        private val cpJudged = IntArray(hazardCount)
        var drops = 0

        internal fun save(sim: GameSim) {
            checkpoint = true
            cpScore = sim.score; cpCombo = sim.combo; cpInk = sim.inkLeft; cpDrops = drops
            judged.copyInto(cpJudged)
        }

        internal fun restore(sim: GameSim) {
            sim.score = cpScore; sim.combo = cpCombo; sim.inkLeft = cpInk; drops = cpDrops
            cpJudged.copyInto(judged)
        }

        /** No hazard was cleared with heavier ink than it needed. */
        val allLight: Boolean get() = judged.none { it == 2 }
    }

    /** The stroke being drawn, or null. */
    var drawing: Drawing? = null
        private set
    var erasingPointer = -1
        private set

    class Drawing(val pointer: Int) {
        val xs = com.badlogic.gdx.utils.FloatArray(256)
        val ys = com.badlogic.gdx.utils.FloatArray(256)
        val ps = com.badlogic.gdx.utils.FloatArray(256)
        var cost = 0f
        /** Smoothed pressure (EMA), NaN before the first sample. */
        var smoothed = Float.NaN
        var dry = false
        var lastT = Double.NaN
        val size: Int get() = xs.size

        /** Footprint and heaviest ink so far, so a hazard crossed mid-stroke is still judged. */
        var x0 = Float.POSITIVE_INFINITY
        var x1 = Float.NEGATIVE_INFINITY
        var heaviest = -1
        /** Bit per ink kind (1 shl weight) seen in this stroke so far. */
        var kinds = 0
        /** Wet ink is laid up to this point. */
        var wetFrom = 0
        /** The stroke began where it will be pinned; a stroke begun in mid-air would just fall. */
        var startAnchored = false
        var wetRope = 0f
        var tooLong = false
    }

    val difficulty: Float get() = (dist / 600f).coerceIn(0f, 1f)
    val runSpeed: Float get() = if (speedOverride.isNaN()) 3.4f + 2.6f * difficulty else speedOverride

    /** Sandbox only: a fixed run speed instead of the distance-based one. */
    var speedOverride = Float.NaN
    private var generating = true

    fun setViewport(vwMetres: Float, vhMetres: Float) {
        vw = vwMetres
        vh = vhMetres
    }

    /** Reseeds world generation for the next [newRun] (tests and the desktop autoplay). */
    fun reseed(newSeed: Long) {
        seed = newSeed
        gen = Random(newSeed)
    }

    // ---------------------------------------------------------------- run lifecycle

    fun newRun() {
        resetWorld()
        stageRun = null
        speedOverride = Float.NaN
        generating = true
        level = 20f; genX = -40f; nSec = 0; lastHazard = null; boulderCd = 0
        addGround(48f)
        spawnRunner(0f)
        generate()
    }

    private fun resetWorld() {
        events.runReset()
        if (::ink.isInitialized) ink.dispose()
        ink = InkPhysics()
        ink.world.setContactListener(contacts)
        blocks.clear(); drops.clear(); notes.clear(); boulders.clear()
        ceilings.clear(); hazards.clear(); spikeStrips.clear(); lifts.clear(); seesaws.clear()
        stage = null; runnerOnLift = 0
        combo = 1; score = 0.0; scoredDist = 0f; pendingDeath = null; boulderBounce.clear()
        dist = 0f; inkLeft = INK_START; timeScale = 1f; simT = 0f; snaps = 0; bounces = 0; nextSpeedNote = 100f
        drawing = null; erasingPointer = -1; pendingBounce = false; death = null
    }

    private fun spawnRunner(x: Float) {
        val body = ink.addBall(x, level - RUNNER_R - 0.02f, RUNNER_R, 2f, 1f, 0.05f, FixtureTag.RUNNER, bullet = true)
        runner = Runner(body)
        runner.bestX = x
        cam.set(x - vw * 0.28f, level - vh * 0.62f)
        camPrev.set(cam)
    }

    /** Records every moving thing's transform before a tick so frames between ticks can interpolate. */
    private fun snapshot() {
        camPrev.set(cam)
        runner.prev.snapshot()
        boulders.fastEach { it.prev.snapshot() }
        lifts.fastEach { it.prev.snapshot() }
        seesaws.fastEach { it.prev.snapshot() }
        ink.strokes.fastEach { s -> s.groups.fastEach { it.snapshot() } }
    }

    /**
     * Builds [def] and puts the runner at its start, or at its checkpoint when [fromCheckpoint]
     * and this attempt has passed it (score, combo, ink and judgements roll back to that moment).
     * The runner is held until the caller sets [runnerHeld] to false.
     */
    fun loadStage(def: StageDef, fromCheckpoint: Boolean) {
        val keep = stageRun?.takeIf { fromCheckpoint && it.def === def && it.checkpoint }
        val l = def.layout
        resetWorld()
        generating = false
        stage = def
        for (b in l.blocks) {
            val r = if (b.topR == b.top) ink.addRect(b.x, b.top, b.w, b.depth)
            else ink.addSlope(b.x, b.top, b.w, b.topR, max(b.top, b.topR) + b.depth)
            r.level = max(b.top, b.topR)
            blocks += r
        }
        for (k in l.rocks) {
            ceilings += if (k.spiked) ink.addSpikedRock(k.x, k.bottom - ROCK_H, k.w, ROCK_H, SPIKE_DEPTH)
            else ink.addRect(k.x, k.bottom - ROCK_H, k.w, ROCK_H)
        }
        for (k in l.spikes) spikeStrips += ink.addSpikes(k.x, k.y, k.w, k.up)
        for (k in l.lifts) lifts += Lift(ink.addLift(k.x, k.lowY, k.w, LIFT_H), k, LIFT_H)
        for (k in l.seesaws) {
            seesaws += Seesaw(ink.addSeesaw(k.cx, k.cy, k.len, SEESAW_THICK, k.angle, k.minAngle, k.maxAngle), k.len, k.cx, k.cy)
        }
        for (k in l.items) {
            drops += when (k.kind) {
                ItemKind.DROP -> Drop(k.x, k.y, DROP_INK, false, fx.nextFloat() * 6f)
                ItemKind.HIGH_DROP -> Drop(k.x, k.y, HIGH_DROP_INK, true, fx.nextFloat() * 6f)
                ItemKind.HEART -> Drop(k.x, k.y, 0f, true, fx.nextFloat() * 6f, heart = true)
            }
        }
        for (k in l.notes) notes += Note(k.x, k.y, k.text).also { it.maxW = k.maxW }
        for ((i, k) in l.hazards.withIndex()) hazards += Hazard(k.type, k.x0, k.x1, k.par).also { it.index = i }
        for (k in l.runways) blocks.firstOrNull { it.x == k.x }?.boulderTrigger = k.triggerX
        val sx = if (keep != null) l.checkpointX + 1f else l.startX
        level = l.groundAt(sx)
        spawnRunner(sx)
        dist = sx
        scoredDist = sx
        nextSpeedNote = Float.POSITIVE_INFINITY
        speedOverride = def.speed
        hazards.fastEach { if (it.x1 < sx) it.done = true }
        drops.fastEach { if (it.x < sx) it.taken = true }
        val run = keep ?: StageRun(def, l.hazards.size)
        if (keep != null) run.restore(this)
        stageRun = run
        runnerHeld = true
        camFocusX = Float.NaN
        state = GameState.RUN
        snapCamera()
    }

    /**
     * Sandbox for tests and tools: hand-built terrain instead of generated sections. Each rect is
     * (x, top, width) of 40 m deep ground; the runner starts on the ground at [runnerX] and the
     * run begins immediately.
     */
    fun loadSandbox(rects: List<FloatArray>, runnerX: Float) {
        resetWorld()
        generating = false
        for (r in rects) {
            genX = r[0]
            level = r[1]
            addGround(r[2])
        }
        level = blocks.first { runnerX >= it.x && runnerX <= it.right }.level
        spawnRunner(runnerX)
        dist = runnerX
        scoredDist = runnerX
        state = GameState.RUN
    }

    /** A tunnel's spiked rock spanning [x0]..[x1] with its underside at [bottom] (also for sandboxes). */
    fun addTunnelRock(x0: Float, x1: Float, bottom: Float): com.pranshuparmar.inkmayhem.physics.GroundRect =
        ink.addSpikedRock(x0, bottom - ROCK_H, x1 - x0, ROCK_H, SPIKE_DEPTH).also { ceilings += it }

    /** Adds a rolling boulder (also used by the runway trigger). */
    fun spawnBoulder(x: Float, y: Float, vx: Float): Boulder {
        val body = ink.addBall(x, y, BOULDER_R, 2.5f, 1f, 0.02f, FixtureTag.BOULDER, bullet = false)
        body.setLinearVelocity(vx, 0f)
        return Boulder(body).also { boulders += it }
    }

    /** Abandons whatever is going on and shows a fresh level under the title card. */
    fun toTitle() {
        newRun()
        state = GameState.TITLE
    }

    /** Jumps the camera to where it is heading (a new tutorial scene shouldn't pan in). */
    fun snapCamera() {
        val p = runner.body.position
        cam.set(camTargetX(p.x), p.y - vh * camRunnerY)
        camPrev.set(cam)
    }

    private fun camTargetX(runnerX: Float) = if (camFocusX.isNaN()) runnerX - vw * 0.28f else camFocusX - vw / 2f

    private fun startRun() {
        if (state == GameState.TITLE) {
            state = GameState.RUN
            return
        }
        newRun()
        state = GameState.RUN
    }

    private fun die(why: String, kind: DeathKind) {
        if (state != GameState.RUN) return
        state = GameState.DEAD
        deadAt = realTime
        events.shake(0.35f)
        events.sound(Sfx.DEATH)
        events.haptic(Haptic.DEATH)
        val d = floor(dist).toInt()
        val sc = floor(score).toInt()
        val isBest = sc > best
        if (isBest) best = sc
        val tips = TIPS.getValue(kind)
        death = Death(why, kind, sc, d, isBest, best, tips[fx.nextInt(tips.size)])
        events.died(death!!)
        events.scratch(0f, 0.5f)
        drawing = null
        ink.clearWet()
    }

    /** POC: a pen-down 0.7 s after dying restarts. */
    val canRestart: Boolean get() = state == GameState.DEAD && realTime - deadAt > RESTART_DELAY

    // ---------------------------------------------------------------- world generation

    private fun addGround(len: Float): com.pranshuparmar.inkmayhem.physics.GroundRect {
        val r = ink.addRect(genX, level, len, 40f)
        r.level = level
        blocks += r
        genX += len
        return r
    }

    private fun note(text: String, dx: Float, dy: Float) {
        notes += Note(genX + dx, level - dy, text)
    }

    private fun placeDrops(x0: Float, len: Float) {
        if (len < 4f) return
        if (gen.nextFloat() < 0.6f) drops += Drop(x0 + 1.5f + gen.nextFloat() * (len - 3f), level - 0.75f, DROP_INK, false, fx.nextFloat() * 6f)
        if (gen.nextFloat() < 0.22f) {
            // the same draws either way, so a rock never changes the rest of the level
            val d = Drop(x0 + 2f + gen.nextFloat() * (len - 4f), level - 4.2f, HIGH_DROP_INK, true, fx.nextFloat() * 6f)
            if (!insideRock(d)) drops += d
        }
    }

    /** A high drop over a tunnel would sit inside its rock, where nothing can reach it. */
    private fun insideRock(d: Drop): Boolean {
        ceilings.fastEach { c -> if (d.x > c.x - ROCK_CLEARANCE && d.x < c.right + ROCK_CLEARANCE && d.y < c.y + c.h + ROCK_CLEARANCE) return true }
        return false
    }

    private fun rnd(a: Float, b: Float) = a + gen.nextFloat() * (b - a)

    private fun hazard(type: HazardType, x0: Float, x1: Float) {
        hazards += Hazard(type, x0, x1)
    }

    private fun genSection() {
        val d = difficulty
        val flatLen = max(4.5f, rnd(8f, 12f) - 4f * min(1f, genX / 700f))
        // every hazard sits between flats
        val x0 = genX
        addGround(flatLen)
        placeDrops(x0, flatLen)
        var type: HazardType?
        var text: String? = null
        if (nSec < INTRO.size) {
            type = INTRO[nSec].first
            text = INTRO[nSec].second
        } else {
            poolWeights[0] = 1.6f                                  // gapS
            poolWeights[1] = 2.6f                                  // gapM
            poolWeights[2] = 2f + 1.5f * d                         // gapL
            poolWeights[3] = 2.4f                                  // wall
            poolWeights[4] = 1.8f + d                              // tunnel
            poolWeights[5] = 1f                                    // drop
            poolWeights[6] = if (boulderCd <= 0) 1.6f + d else 0f  // boulder
            var tot = 0f
            for (w in poolWeights) tot += w
            var r = gen.nextFloat() * tot
            type = null // float rounding can leave r > 0: the POC's undefined type, a plain flat
            for (i in POOL.indices) {
                r -= poolWeights[i]
                if (r <= 0f) {
                    type = POOL[i]
                    break
                }
            }
            if (type == lastHazard && type != HazardType.GAP_M) type = HazardType.GAP_M
        }
        if (type == HazardType.WALL && level < 11f) type = HazardType.DROP
        if (type == HazardType.DROP && level > 27f) type = HazardType.WALL
        if (text != null) {
            note(text, -flatLen + 0.6f, if (type == HazardType.TUNNEL) 4.6f else 3.4f)
            // the tunnel's rock covers the end of this flat: wrap the note in the room before it
            if (type == HazardType.TUNNEL) notes[notes.size - 1].maxW = flatLen - TUNNEL_OVERHANG - 0.6f - 0.4f
        }
        boulderCd--
        when (type) {
            null -> Unit
            HazardType.GAP_S -> gap(type, rnd(2.2f, 3.2f))
            HazardType.GAP_M -> gap(type, rnd(4.2f, 5.8f))
            HazardType.GAP_L -> gap(type, rnd(7.6f, 9.2f))
            HazardType.WALL -> {
                level -= rnd(2.1f, 2.8f)
                hazard(HazardType.WALL, genX, genX + 1f)
            }
            HazardType.DROP -> level += rnd(1.6f, 2.6f)
            HazardType.TUNNEL -> {
                // A spiked ceiling over a gap: springs launch you into it. Only the underside is
                // spiked, and the rock rises out of sight so nothing can bounce over it.
                val w = rnd(3.8f, 5.2f)
                val cx0 = genX - TUNNEL_OVERHANG
                val cx1 = genX + w + TUNNEL_OVERHANG
                addTunnelRock(cx0, cx1, level - 2.1f)
                drops.removeAll { it.high && insideRock(it) }
                gap(type, w)
            }
            HazardType.BOULDER -> {
                // runway: the boulder spawns on it, 10 m behind the runner, and closes in
                val r0 = genX
                addGround(18f).boulderTrigger = r0 + 10.5f
                gap(type, rnd(4.8f, 5.8f))
                boulderCd = 3
            }
        }
        lastHazard = type
        nSec++
    }

    private fun gap(type: HazardType, w: Float) {
        val x0 = genX
        genX += w
        hazard(type, x0, genX)
    }

    private fun generate() {
        if (!generating) return
        while (genX < cam.x + vw + 30f) genSection()
    }

    /** Deepest ground level within ±10 m of [x]. */
    fun levelNear(x: Float): Float {
        var lv = Float.NEGATIVE_INFINITY
        blocks.fastEach { b -> if (b.x < x + 10f && b.x + b.w > x - 10f) lv = max(lv, b.level) }
        return if (lv == Float.NEGATIVE_INFINITY) level else lv
    }

    // ---------------------------------------------------------------- contacts

    private val contacts = object : ContactListener {
        override fun beginContact(c: Contact) {
            val fa = c.fixtureA
            val fb = c.fixtureB
            val ua = fa.userData as? FixtureTag
            val ub = fb.userData as? FixtureTag
            val other = if (ua?.kind == FixtureKind.RUNNER) ub else if (ub?.kind == FixtureKind.RUNNER) ua else null
            if (other?.kind == FixtureKind.SPRING) pendingBounce = true
            if (other?.kind == FixtureKind.CEILING) pendingDeath = "Spiked"
            if (other?.kind == FixtureKind.LIFT) {
                runnerOnLift++
                val body = if (ua?.kind == FixtureKind.LIFT) fa.body else fb.body
                lifts.fastEach { if (it.body === body) it.loaded = true }
            }
            // springs launch boulders too
            if (ua?.kind == FixtureKind.BOULDER && ub?.kind == FixtureKind.SPRING) addBoulderBounce(fa.body)
            if (ub?.kind == FixtureKind.BOULDER && ua?.kind == FixtureKind.SPRING) addBoulderBounce(fb.body)
        }

        override fun endContact(c: Contact) {
            val ua = c.fixtureA.userData as? FixtureTag
            val ub = c.fixtureB.userData as? FixtureTag
            if ((ua?.kind == FixtureKind.RUNNER && ub?.kind == FixtureKind.LIFT) || (ub?.kind == FixtureKind.RUNNER && ua?.kind == FixtureKind.LIFT)) {
                runnerOnLift = max(0, runnerOnLift - 1)
            }
        }
        override fun preSolve(c: Contact, m: Manifold) = Unit
        override fun postSolve(c: Contact, i: ContactImpulse) = Unit
    }

    private fun addBoulderBounce(b: Body) {
        if (b !in boulderBounce) boulderBounce += b
    }

    // ---------------------------------------------------------------- input (world coordinates)

    /**
     * POC pointerdown on the canvas, after the finger check. [erase] = side button / eraser end.
     * Starts the run from the title or (0.7 s after dying) restarts it; otherwise erases or begins
     * a stroke. [n] is normalized pressure, [t] the sample time in seconds.
     */
    fun penDown(pointer: Int, x: Float, y: Float, n: Float, erase: Boolean, t: Double) {
        if (state == GameState.TITLE || canRestart) {
            startRun()
            return
        }
        if (state != GameState.RUN) return
        if (drawing != null || erasingPointer >= 0) return
        if (erase) {
            erasingPointer = pointer
            eraseAt(x, y)
            return
        }
        if (inkLeft <= 0.5f) {
            events.toast(OUT_OF_INK)
            return
        }
        drawing = Drawing(pointer)
        addPoint(x, y, n, t)
    }

    fun penMove(pointer: Int, x: Float, y: Float, n: Float, t: Double) {
        val d = drawing
        if (d != null && d.pointer == pointer) {
            addPoint(x, y, n, t)
            return
        }
        if (erasingPointer >= 0 && pointer == erasingPointer) eraseAt(x, y)
    }

    fun penUp(pointer: Int) {
        val d = drawing
        if (d != null && d.pointer == pointer) finishStroke()
        if (pointer == erasingPointer) erasingPointer = -1
    }

    private fun addPoint(x: Float, y: Float, n: Float, t: Double) {
        val d = drawing ?: return
        d.smoothed = if (d.smoothed.isNaN()) n else d.smoothed * 0.7f + n * 0.3f
        val last = d.size - 1
        if (last >= 0) {
            val len = hypot(x - d.xs[last], y - d.ys[last])
            if (len < 0.02f) {
                d.ps[last] = max(d.ps[last], d.smoothed)
                return
            }
            val th = Ink.thickness(d.smoothed)
            val c = len * th * th * InkPhysics.INK_K
            if (!unlimitedInk && inkLeft - d.cost - c <= 0f) {
                if (!d.dry) {
                    d.dry = true
                    events.toast(OUT_OF_INK)
                }
                return
            }
            d.cost += c
            val dt = max(0.001, if (d.lastT.isNaN()) 0.0 else t - d.lastT)
            d.lastT = t
            events.scratch(min(0.09f, (len * scale / dt.toFloat()) * 0.00012f) * (0.5f + d.smoothed), d.smoothed)
        } else {
            d.lastT = t
            d.startAnchored = ink.anchorable(x, y)
        }
        d.xs.add(x); d.ys.add(y); d.ps.add(d.smoothed)
        d.x0 = min(d.x0, x)
        d.x1 = max(d.x1, x)
        if (last >= 0) {
            val w = Ink.of((d.ps[last] + d.smoothed) / 2f).weight
            d.heaviest = max(d.heaviest, w)
            d.kinds = d.kinds or (1 shl w)
        }
        growWet(d)
    }

    /**
     * Lays wet ink up to the newest point in pieces of at least [WET_STEP], so the runner can
     * roll on a stroke while it is still being drawn (the POC built it only on pen-up). Only a
     * stroke begun where it will be pinned is laid (one begun in mid-air falls when finished),
     * and rope past [ROPE_MAX_LEN] isn't, as the finished rope would give way there.
     */
    private fun growWet(d: Drawing) {
        if (!d.startAnchored) return
        val i = d.size - 1
        val j = d.wetFrom
        val len = hypot(d.xs[i] - d.xs[j], d.ys[i] - d.ys[j])
        if (len < WET_STEP) return
        d.wetFrom = i
        val p = (d.ps[i] + d.ps[j]) / 2f
        val kind = Ink.of(p)
        if (kind == Ink.ROPE) {
            d.wetRope += len
            if (d.wetRope > ROPE_MAX_LEN) {
                if (!d.tooLong) events.toast(TOO_LONG_FOR_ROPE)
                d.tooLong = true
                return
            }
        }
        ink.addWet(d.xs[j], d.ys[j], d.xs[i], d.ys[i], kind, Ink.thickness(p))
    }

    private fun finishStroke() {
        val d = drawing
        drawing = null
        ink.clearWet()
        events.scratch(0f, 0.5f)
        if (d == null || d.size < 2) return
        val n = d.size
        val ps = d.ps
        // Flatten the pen-down and pen-up pressure ramps.
        val k = min(3, n / 4)
        if (k > 0) {
            val pStart = ps[k]
            val pEnd = ps[n - 1 - k]
            for (i in 0 until k) ps[i] = max(ps[i], pStart)
            for (i in n - k until n) ps[i] = max(ps[i], pEnd)
        }
        val s = ink.buildStroke(d.xs.items, d.ys.items, ps.items, n) ?: return
        if (!unlimitedInk) inkLeft = max(0f, inkLeft - s.cost)
        // footprint + heaviest ink, for scoring against each hazard's par
        var x0 = Float.POSITIVE_INFINITY
        var x1 = Float.NEGATIVE_INFINITY
        for (i in 0 until n) { x0 = min(x0, d.xs[i]); x1 = max(x1, d.xs[i]) }
        s.x0 = x0; s.x1 = x1
        s.heaviest = s.segs.maxOf { it.kind.weight }
        for (a in s.anchors) events.burst(a.x, a.y, 5, 1.2f, Tok.GRAPHITE)
        if (s.anchors.isNotEmpty()) events.sound(Sfx.ANCHOR)
        weakenLongRope(s)
    }

    /** A rope longer than 6.8 m can't hold its own weight: its middle joint gives way after ~0.65 s. */
    private fun weakenLongRope(s: Stroke) {
        val ropes = s.segs.filter { it.kind == Ink.ROPE }
        if (ropes.size * InkPhysics.SEG <= ROPE_MAX_LEN) return
        val mid = ropes[ropes.size / 2]
        var best: com.pranshuparmar.inkmayhem.physics.JointInfo? = null
        var bestD = 1e9f
        for (u in s.joints) {
            if (u.kind != JointKind.ROPE) continue
            val dd = hypot(u.x - mid.ax, u.y - mid.ay)
            if (dd < bestD) { bestD = dd; best = u }
        }
        val u = best ?: return
        u.strength = 1f
        u.age = -0.35f
        events.toast(TOO_LONG_FOR_ROPE, delaySec = 0.5f)
    }

    private fun eraseAt(x: Float, y: Float) {
        val s = ink.strokeAt(x, y, 0.3f) ?: return
        inkLeft = min(INK_MAX, inkLeft + s.cost * 0.5f)
        events.burst(x, y, 10, 2f, Tok.MUTED)
        events.sound(Sfx.ERASE)
        ink.removeStroke(s)
    }

    // ---------------------------------------------------------------- simulation

    /** One fixed 1/60 s tick (POC tick(dt)). */
    fun tick(dt: Float) {
        snapshot()
        val target = if (state == GameState.DEAD) 0.35f else 1f
        timeScale += (target - timeScale) * min(1f, dt * 10f)
        val sdt = if (state == GameState.TITLE) 0f else dt * timeScale
        simT += sdt
        val rb = runner.body

        if (state == GameState.CLEAR) {
            // past the flag the runner rolls to a stop
            val w = rb.angularVelocity
            rb.applyTorque((3f * (0f - w)).coerceIn(-7f, 7f), true)
            if (sdt > 0f) {
                updateLifts(sdt)
                ink.step(sdt, 2)
            }
            pendingBounce = false
            pendingDeath = null
            ink.events.clear()
        }

        if (state == GameState.RUN || state == GameState.DEAD) {
            if (state == GameState.RUN && !runnerHeld) {
                val w = rb.angularVelocity
                val wt = runSpeed / RUNNER_R
                // stages let a downhill roll build speed: the drive barely brakes there
                rb.applyTorque((3f * (wt - w)).coerceIn(if (stage != null) -STAGE_BRAKE else -7f, 7f), true)
                val v = rb.linearVelocity
                if (kotlin.math.abs(v.x) < 0.4f && kotlin.math.abs(v.y) < 0.6f) {
                    runner.stuck += sdt
                    if (runner.stuck > 0.45f) {
                        val m = rb.mass
                        rb.applyLinearImpulse(1.6f * m, -4.6f * m, rb.worldCenter.x, rb.worldCenter.y, true)
                        runner.stuck = 0f
                        events.sound(Sfx.HOP)
                    }
                } else {
                    runner.stuck = 0f
                }
            }
            boulders.fastEach { bo ->
                val bw = bo.body.angularVelocity
                val bt = runSpeed * 1.4f / BOULDER_R
                bo.body.applyTorque((25f * (bt - bw)).coerceIn(-90f, 90f), true)
            }
            if (sdt > 0f) {
                updateLifts(sdt)
                ink.step(sdt, 2)
            }
            runner.bounceCd -= sdt
            if (pendingBounce && runner.bounceCd <= 0f && state == GameState.RUN && !runnerHeld) {
                val v = rb.linearVelocity
                rb.setLinearVelocity(max(v.x, runSpeed * 0.95f), -9.6f)
                runner.bounceCd = 0.3f
                bounces++
                runner.squash = 1f
                val p = rb.position
                events.burst(p.x, p.y + RUNNER_R, 8, 2f, Tok.SPRING, 0f)
                events.sound(Sfx.BOING)
            }
            pendingBounce = false
            boulderBounce.fastEach { bb ->
                val bo = boulders.firstOrNull { it.body === bb } ?: return@fastEach
                if (!bo.bounceUntil.isNaN() && simT < bo.bounceUntil) return@fastEach
                val v = bb.linearVelocity
                bb.setLinearVelocity(max(v.x, runSpeed * 1.3f), -9.6f)
                bo.bounceUntil = simT + 0.3f
                val q = bb.position
                events.burst(q.x, q.y + BOULDER_R, 10, 2.5f, Tok.SPRING, 0f)
                events.sound(Sfx.BOULDER_BOING)
                if (state == GameState.RUN) events.toast("The spring launched the boulder!", force = true)
            }
            boulderBounce.clear()
            val pd = pendingDeath
            if (pd != null && state == GameState.RUN) {
                val p = rb.position
                events.burst(p.x, p.y - RUNNER_R, 16, 3f, Tok.INK)
                die(pd, DeathKind.SPIKED)
            }
            pendingDeath = null
            while (ink.events.isNotEmpty()) {
                val ev = ink.events.removeFirst()
                snaps++
                events.burst(ev.x, ev.y, 14, 3f, if (tint) snapColor(ev.kind) else Tok.INK)
                events.sound(Sfx.SNAP)
                events.haptic(Haptic.SNAP)
                events.shake(0.12f)
            }
        }

        if (state == GameState.RUN) {
            val p = rb.position
            val px = p.x
            val py = p.y
            dist = max(dist, px)
            if (dist >= nextSpeedNote) {
                events.toast("${nextSpeedNote.toInt()} m. Faster now.", force = true)
                nextSpeedNote += 100f
                events.sound(Sfx.SPEED_UP)
            }
            // boulder triggers
            for (bi in blocks.indices) {
                val b = blocks[bi]
                if (!b.boulderTrigger.isNaN() && !b.triggered && px > b.boulderTrigger) {
                    b.triggered = true
                    spawnBoulder(px - 10f, b.level - BOULDER_R - 0.05f, runSpeed * 1.3f)
                    events.toast("Boulder behind you!", force = true)
                    events.sound(Sfx.RUMBLE)
                    events.shake(0.15f)
                }
            }
            boulders.fastEach { bo ->
                val q = bo.body.position
                if (hypot(q.x - px, q.y - py) < BOULDER_R + RUNNER_R + 0.04f) die("Flattened", DeathKind.BOULDER)
            }
            drops.fastEach { dr ->
                if (dr.taken) return@fastEach
                if (hypot(dr.x - px, dr.y - py) < 0.85f) {
                    dr.taken = true
                    if (dr.heart) {
                        events.burst(dr.x, dr.y, 14, 2.5f, Tok.BEAM)
                        events.extraLife()
                        return@fastEach
                    }
                    inkLeft = min(INK_MAX, inkLeft + dr.value)
                    stageRun?.let { it.drops++ }
                    events.burst(dr.x, dr.y, 10, 2f, Tok.DROP)
                    events.sound(if (dr.high) Sfx.PICKUP_HIGH else Sfx.PICKUP)
                }
            }
            // score: every metre is worth the current combo
            if (dist > scoredDist) {
                score += (dist - scoredDist).toDouble() * combo
                scoredDist = dist
            }
            judgeHazards(px, py)
            stageProgress(px)
            if (py > levelNear(px) + 4f) die("Fell in", DeathKind.FELL)
            // riding a lift isn't being stuck
            if (px > runner.bestX + 0.5f || runnerHeld || runnerOnLift > 0) {
                runner.bestX = px
                runner.bestT = simT
            } else if (simT - runner.bestT > 5f) {
                die("Stuck", DeathKind.STUCK)
            }
        }

        // camera; stages climb and fall, so there it looks ahead vertically and follows faster
        val p = rb.position
        val tx = camTargetX(p.x)
        val staged = stage != null
        val ty = p.y - vh * camRunnerY + if (staged) (rb.linearVelocity.y * 0.3f).coerceIn(-2f, 4f) else 0f
        cam.x += (tx - cam.x) * min(1f, dt * 4f)
        cam.y += (ty - cam.y) * min(1f, dt * if (staged) 4f else 2.5f)
        if (state != GameState.TITLE) generate()

        runner.squash = max(0f, runner.squash - dt * 4f)
        drops.fastEach { it.t += dt }
    }

    private fun judgeHazards(px: Float, py: Float) {
        for (hi in hazards.indices) {
            val hz = hazards[hi]
            if (hz.done || px < hz.x1 + 0.6f || state != GameState.RUN) continue
            hz.done = true
            var heavy = -1
            ink.strokes.fastEach { s -> if (!s.x1.isNaN() && s.x1 >= hz.x0 - 3f && s.x0 <= hz.x1 + 1f) heavy = max(heavy, s.heaviest) }
            // A stroke still being drawn counts once both its ends would be pinned (a finished
            // bridge, or ink lying on the ground): crossing on half-drawn ink scores nothing.
            val d = drawing
            if (d != null && d.x1 >= hz.x0 - 3f && d.x0 <= hz.x1 + 1f && liveStrokeComplete(d)) heavy = max(heavy, d.heaviest)
            val par = hz.par.weight
            val cx = (hz.x0 + hz.x1) / 2f
            val cy = py - 1.6f
            if (heavy < 0) continue
            stageRun?.let { if (hz.index >= 0) it.judged[hz.index] = if (heavy <= par) 1 else 2 }
            if (heavy < par) {
                combo = min(COMBO_MAX, combo + 1)
                val bonus = 50 * combo
                score += bonus
                events.pop(cx, cy, "Featherweight +$bonus", Tok.ROPE)
                events.sound(Sfx.FEATHERWEIGHT)
            } else if (heavy == par) {
                combo = min(COMBO_MAX, combo + 1)
                events.pop(cx, cy, "Clean ×$combo", Tok.ROPE)
                events.sound(Sfx.CHIME, combo)
            } else {
                val lost = combo > 1
                combo = 1
                events.pop(cx, cy, "Too heavy: ${hz.par.label} was enough", Tok.BEAM)
                if (lost) events.sound(Sfx.COMBO_LOST)
            }
        }
    }

    private fun liveStrokeComplete(d: Drawing): Boolean {
        val n = d.size
        return n >= 2 && d.startAnchored && ink.anchorable(d.xs[n - 1], d.ys[n - 1])
    }

    /** Checkpoint and finish line of a stage. */
    private fun stageProgress(px: Float) {
        val run = stageRun ?: return
        val l = run.def.layout
        if (!run.checkpoint && !l.checkpointX.isNaN() && px > l.checkpointX) {
            run.save(this)
            events.checkpoint()
        }
        if (state == GameState.RUN && px > l.finishX) {
            state = GameState.CLEAR
            clearedAt = realTime
            drawing = null
            ink.clearWet()
            events.scratch(0f, 0.5f)
            events.stageCleared()
        }
    }

    /** Moves the lifts: a waiting lift rises once the runner is on it; the others travel up and down. */
    private fun updateLifts(dt: Float) {
        lifts.fastEach { lf ->
            val d = lf.def
            val top = lf.top
            if (lf.pause > 0f) {
                lf.pause -= dt
                lf.body.setLinearVelocity(0f, 0f)
                return@fastEach
            }
            if (lf.dir == 0) {
                lf.dir = when {
                    d.waitForRunner && lf.loaded && top > d.highY + 0.001f -> -1
                    !d.waitForRunner -> -1
                    else -> 0
                }
            }
            if (lf.dir == 0) {
                lf.body.setLinearVelocity(0f, 0f)
                return@fastEach
            }
            val goal = if (lf.dir < 0) d.highY else d.lowY
            val left = goal - top // negative when rising
            val step = d.speed * dt
            if (kotlin.math.abs(left) <= step) {
                // arrive exactly this step, then park (waiting lift) or pause and turn round
                lf.body.setLinearVelocity(0f, left / dt)
                if (d.waitForRunner) {
                    lf.dir = 0 // parked at the top for good
                } else {
                    lf.dir = -lf.dir
                    lf.pause = LIFT_PAUSE
                }
            } else {
                lf.body.setLinearVelocity(0f, if (left < 0f) -d.speed else d.speed)
            }
        }
    }

    /** POC tok[ev.kind] || tok.ink: anchors have no colour token of their own. */
    private fun snapColor(kind: JointKind) = when (kind) {
        JointKind.ROPE -> Tok.ROPE
        JointKind.SPRING -> Tok.SPRING
        JointKind.BEAM -> Tok.BEAM
        JointKind.ANCHOR -> Tok.INK
    }

    private var cullT = 0f

    /** POC cull(dt): every 0.5 s, drop everything more than 25 m behind the camera. */
    fun cull(dt: Float) {
        cullT += dt
        if (cullT < 0.5f) return
        cullT = 0f
        val lim = cam.x - 25f
        blocks.removeAll { b -> (b.x + b.w < lim).also { if (it) ink.removeRect(b) } }
        ceilings.removeAll { c -> (c.x + c.w < lim).also { if (it) ink.removeRect(c) } }
        hazards.removeAll { it.done && it.x1 <= lim }
        for (s in ArrayList(ink.strokes)) {
            if (s.groups.all { g -> g.body.let { it == null || it.position.x < lim } }) ink.removeStroke(s)
        }
        boulders.removeAll { bo ->
            val q = bo.body.position
            (q.x < lim || q.y > levelNear(q.x) + 30f).also { if (it) ink.destroyBody(bo.body) }
        }
        drops.removeAll { it.taken || it.x <= lim }
        notes.removeAll { it.x <= lim - 10f }
    }

    fun dispose() {
        if (::ink.isInitialized) ink.dispose()
    }

    // Last, so every property above (notably the contact listener) is initialized first.
    init {
        newRun()
    }

    companion object {
        const val INK_MAX = 100f
        const val INK_START = 70f
        const val ROPE_MAX_LEN = 6.8f
        /** Stage lifts and seesaws. */
        const val LIFT_H = 0.5f
        const val LIFT_PAUSE = 0.8f
        /** Stages: the most the drive torque brakes an over-speeding runner (Endless: 7). */
        const val STAGE_BRAKE = 1.5f
        const val SEESAW_THICK = 0.3f
        /** Wet ink is laid in pieces at least this long (half a stroke segment). */
        const val WET_STEP = InkPhysics.SEG / 2f
        /** Tunnel rock: how far it overhangs each side of the gap, its height and its spiked layer. */
        const val TUNNEL_OVERHANG = 3.5f
        const val ROCK_H = 40f
        const val SPIKE_DEPTH = 0.12f
        private const val ROCK_CLEARANCE = 0.6f
        const val RUNNER_R = 0.4f
        const val BOULDER_R = 0.9f
        const val DROP_INK = 12f
        const val HIGH_DROP_INK = 25f
        const val COMBO_MAX = 9
        const val RESTART_DELAY = 0.7
        const val OUT_OF_INK = "Out of ink. Grab the blue drops."
        const val TOO_LONG_FOR_ROPE = "Too long for rope. Press harder."

        val INTRO = listOf(
            HazardType.GAP_S to "Draw a bridge ahead of the runner.",
            HazardType.GAP_M to "Light touch makes rope. Use the lightest ink that works to grow your combo.",
            HazardType.WALL to "Write normally for a spring. Bounce over.",
            HazardType.TUNNEL to "Low ceiling. Bouncing here is fatal. Rope it.",
            HazardType.GAP_L to "Too far for rope. Press hard for a beam, or bounce across.",
            HazardType.BOULDER to "Boulder behind! Rope drops it. Springs and beams carry it after you.",
        )

        private val POOL = arrayOf(
            HazardType.GAP_S, HazardType.GAP_M, HazardType.GAP_L, HazardType.WALL,
            HazardType.TUNNEL, HazardType.DROP, HazardType.BOULDER,
        )

        val TIPS = mapOf(
            DeathKind.FELL to listOf(
                "Long gaps need a beam: press hard.",
                "Write normally on the near edge to bounce across.",
                "Draw ahead of the runner, not under it.",
            ),
            DeathKind.BOULDER to listOf(
                "Rope is too weak for the boulder. Let it fall through.",
                "Springs launch the boulder after you. Use rope.",
                "Beams carry the boulder too. Use light ink.",
            ),
            DeathKind.SPIKED to listOf(
                "Under a low ceiling, springs are fatal. Rope it.",
                "A flat rope under a ceiling is all you need.",
            ),
            DeathKind.STUCK to listOf(
                "Thick beams make walls. Erase with the side button.",
                "A spring in front of a wall bounces you over.",
            ),
        )
    }
}
