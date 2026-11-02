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
import com.pranshuparmar.inkmayhem.input.SteadyPressure
import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.physics.InkPhysics
import com.pranshuparmar.inkmayhem.physics.JointKind
import com.pranshuparmar.inkmayhem.physics.Stroke
import com.pranshuparmar.inkmayhem.render.Tok
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
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
    /** Stage only: star trails, Blots, doodle boxes, the marker caps they pop and the doodled hints. */
    val stars = ArrayList<Star>()
    val golds = ArrayList<GoldStar>()
    val blots = ArrayList<Blot>()
    val boxes = ArrayList<DoodleBox>()
    val caps = ArrayList<Cap>()
    val gates = ArrayList<Gate>()

    var combo = 1
        private set
    var score = 0.0
        private set
    private var scoredDist = 0f
    private var pendingDeath: String? = null
    private var pendingBounce = false
    private val boulderBounce = ArrayList<Body>(2)
    /** The doodle box the runner touched this step, if any. */
    private var pendingBox: FixtureTag? = null
    /** Blots hit by a beam or a boulder this step, with what hit them. */
    private val blotHits = ArrayList<Body>(4)
    private val blotHitBy = ArrayList<Body>(4)
    /** The hitter's downward speed as it touched (after the step, a heavy target has stopped it). */
    private val blotHitVy = com.badlogic.gdx.utils.FloatArray(4)

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
    /** Stars collected this attempt, Blots squashed and doodle boxes popped. */
    var starsTaken = 0
        private set
    var squashed = 0
        private set
    /** The streak of stars taken close together: each rings a note higher. */
    private var starStep = -1
    private var lastStarT = -10f
    /** How the runner's face looks (see [updateMood]). */
    var mood = Mood.CALM
        private set
    private var bounceAt = -10f
    private var happyUntil = -10f
    /** simT the runner last lost Bold or squashed something (the face reacts). */
    private var ouchAt = -10f

    /**
     * The pen hasn't been tuned: a stroke drawn at a tuning gate is the gate's ink whatever the
     * pressure, and [lastGate] tells the caller which ink its pressure was meant to be.
     */
    var gateTuning = false
    /** The tuning gate the last finished stroke was drawn at, or null. */
    var lastGate: Gate? = null
        private set
    /** A boss arena has closed behind the runner and its boss is still up (the boss tune plays). */
    var bossFight = false
        private set
    val arenas = ArrayList<Arena>()
    /** The stage's boss, waiting in its arena, or null. */
    var boss: Boss? = null
        private set
    /** Highlighter swipes (fading), inkwells, and what's hidden until a swipe finds it. */
    val swipes = ArrayList<Swipe>()
    val warps = ArrayList<Warp>()
    private val hiddenBlocks = ArrayList<StageLayout.Block>()
    private val hiddenBoxes = ArrayList<StageLayout.Box>()
    /** Down on a bonus page (its floor is the ground that counts). */
    var inBonus = false
        private set
    /** Beetles touching rope or springs: the beetle's body and the stroke it is chewing. */
    private val beetleOn = ArrayList<Body>(4)
    private val beetleOnStroke = ArrayList<Stroke>(4)
    /** Crumbling bricks, and the ones the runner touched this step. */
    val crumbles = ArrayList<Crumble>()
    private val crumbleTouched = ArrayList<Body>(2)
    /** Paper planes in the air, flights still to come, and planes that touched ink this step. */
    val planes = ArrayList<Plane>()
    private val flights = ArrayList<StageLayout.Flight>()
    private val planeHits = ArrayList<Body>(4)
    /** Ink pools, the ink tides, and strokes a pool is swallowing (and when they're gone). */
    val pools = ArrayList<StageLayout.Pool>()
    val tides = ArrayList<Tide>()
    private val sinking = ArrayList<Stroke>(2)
    private val sinkAt = com.badlogic.gdx.utils.FloatArray(2)
    /** Beams that crushed something crumble away a moment later (they'd lie about as obstacles): stroke and when. */
    private val crumbling = ArrayList<Stroke>(2)
    private val crumbleAt = com.badlogic.gdx.utils.FloatArray(2)
    /** Strokes an eraser rubbed against this step (removed after the step), and a spring a boss touched. */
    private val toErase = ArrayList<Stroke>(4)
    private var bossOnSpring = false
    /** The gate the runner is stopping or stopped at, waiting for a stroke, or null. */
    var waitingAt: Gate? = null
        private set
    /**
     * The first page's first run: the runner waits before each new thing (the hints with a wait
     * point) until a stroke that does the job is there; misses are wiped with a tip.
     */
    var learning = false

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

    /** A stage attempt: its last checkpoint passed and what the player had when they passed it. */
    class StageRun(val def: StageDef, hazardCount: Int) {
        /** Index of the last checkpoint passed, −1 for none. */
        var reached = -1
        val checkpoint: Boolean get() = reached >= 0
        var cpScore = 0.0
        var cpCombo = 1
        var cpInk = INK_START
        var cpDrops = 0
        /** Per stage hazard: 0 not judged, 1 par or lighter, 2 heavier than par. */
        val judged = IntArray(hazardCount)
        private val cpJudged = IntArray(hazardCount)
        var drops = 0
        /** Gold stars taken this attempt (bit per index), and at the checkpoint. */
        var goldMask = 0
        private var cpGold = 0

        internal fun save(sim: GameSim, index: Int) {
            reached = index
            cpScore = sim.score; cpCombo = sim.combo; cpInk = sim.inkLeft; cpDrops = drops; cpGold = goldMask
            judged.copyInto(cpJudged)
        }

        internal fun restore(sim: GameSim) {
            sim.score = cpScore; sim.combo = cpCombo; sim.inkLeft = cpInk; drops = cpDrops; goldMask = cpGold
            cpJudged.copyInto(judged)
        }

        /** No hazard was cleared with heavier ink than it needed. */
        val allLight: Boolean get() = judged.none { it == 2 }
    }

    /** The stroke being drawn, or null. */
    var drawing: Drawing? = null
        private set

    /** Each stroke is one ink, from the pressure it settles at (see [SteadyPressure]). */
    private val steady = SteadyPressure()

    /** The stroke being drawn: its steady pressure so far (the gauge's marker), 0 when not drawing. */
    val drawingPressure: Float
        get() {
            val d = drawing ?: return 0f
            return if (d.smoothed.isNaN()) 0f else steady.value(d.smoothed)
        }

    /** The ink the stroke being drawn will be, or null. */
    val drawingInk: Ink? get() = drawing?.let { inkOf(it, drawingPressure) }

    /** A stroke's ink at steady pressure [p]: a tuning gate's own while the pen is untuned. */
    private fun inkOf(d: Drawing, p: Float): Ink = d.forced?.ink ?: Ink.of(p)

    /** Whether the pen's current end of the stroke being drawn touches a surface and would hook. */
    val tipHooks: Boolean
        get() {
            val d = drawing ?: return false
            val n = d.size
            if (n == 0 || d.smoothed.isNaN()) return false
            val kind = inkOf(d, drawingPressure)
            return ink.anchorable(d.xs[n - 1], d.ys[n - 1], Ink.thickness(Ink.clampTo(d.ps[n - 1], kind)))
        }

    /** The ink of the last finished stroke (recordings note it). */
    var lastStrokeInk: Ink? = null
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
        /** Wet ink is laid up to this point, in this ink. */
        var wetFrom = 0
        var wetInk: Ink? = null
        /** The stroke began where it will be pinned; a stroke begun in mid-air would just fall. */
        var startAnchored = false
        var wetRope = 0f
        var tooLong = false
        /** Drawn at this tuning gate while the pen is untuned: its ink is the gate's. */
        var forced: Gate? = null
        /** Begun at this gate while learning (or tuning): finished, it is checked against the job. */
        var gate: Gate? = null
        /** Drawn with a power on: a highlighter swipe (no ink at all), Gold nib ink, or Cloud chalk. */
        var highlight = false
        var goldNib = false
        var chalk = false
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
        ink = InkPhysics(contactAnchors = true)
        ink.world.setContactListener(contacts)
        blocks.clear(); drops.clear(); notes.clear(); boulders.clear()
        ceilings.clear(); hazards.clear(); spikeStrips.clear(); lifts.clear(); seesaws.clear()
        stars.clear(); golds.clear(); blots.clear(); boxes.clear(); caps.clear(); gates.clear()
        blotHits.clear(); blotHitBy.clear(); blotHitVy.clear(); pendingBox = null
        arenas.clear(); boss = null; bossFight = false; toErase.clear(); bossOnSpring = false
        swipes.clear(); warps.clear(); hiddenBlocks.clear(); hiddenBoxes.clear(); inBonus = false
        crumbling.clear(); crumbleAt.clear()
        planes.clear(); flights.clear(); planeHits.clear()
        crumbles.clear(); crumbleTouched.clear()
        pools.clear(); tides.clear(); sinking.clear(); sinkAt.clear()
        beetleOn.clear(); beetleOnStroke.clear()
        starsTaken = 0; squashed = 0; starStep = -1; lastStarT = -10f
        mood = Mood.CALM; bounceAt = -10f; happyUntil = -10f; ouchAt = -10f; lastGate = null
        stage = null; runnerOnLift = 0
        combo = 1; score = 0.0; scoredDist = 0f; pendingDeath = null; boulderBounce.clear()
        dist = 0f; inkLeft = INK_START; timeScale = 1f; simT = 0f; snaps = 0; bounces = 0; nextSpeedNote = 100f
        drawing = null; erasingPointer = -1; pendingBounce = false; death = null
    }

    private fun spawnRunner(x: Float) {
        val body = ink.addBall(x, level - RUNNER_R - 0.02f, RUNNER_R, 2f, 1f, 0.05f, FixtureTag.RUNNER, bullet = true)
        runner = Runner(body)
        runner.bestX = x
        cam.set(x - vw * runnerFrac, level - vh * 0.62f)
        camPrev.set(cam)
    }

    /**
     * Where the runner sits across the view: the POC's 28 %, or 22 % on a narrow (portrait) view so
     * more of what's coming fits.
     */
    private val runnerFrac: Float get() = if (vw < NARROW_VIEW) 0.22f else 0.28f

    /** Records every moving thing's transform before a tick so frames between ticks can interpolate. */
    private fun snapshot() {
        camPrev.set(cam)
        runner.prev.snapshot()
        boulders.fastEach { it.prev.snapshot() }
        lifts.fastEach { it.prev.snapshot() }
        seesaws.fastEach { it.prev.snapshot() }
        blots.fastEach { if (!it.dead) it.prev.snapshot() }
        boss?.let { if (!it.down) it.prev.snapshot() }
        planes.fastEach { if (!it.down) it.prev.snapshot() }
        crumbles.fastEach { if (!it.gone) it.prev.snapshot() }
        tides.fastEach { it.prevX = it.x }
        caps.fastEach { if (!it.taken) it.prev.snapshot() }
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
            if (b.hidden) {
                hiddenBlocks += b
                continue
            }
            val r = if (b.topR == b.top) ink.addRect(b.x, b.top, b.w, b.depth, tag = if (b.bouncy) FixtureTag.wet(Ink.SPRING) else null)
            else ink.addSlope(b.x, b.top, b.w, b.topR, max(b.top, b.topR) + b.depth)
            r.level = max(b.top, b.topR)
            r.bonus = b.bonus
            r.cloud = b.cloud
            r.bouncy = b.bouncy
            blocks += r
        }
        // touching blocks share an edge: hide the stretch the neighbour covers, not the rest
        blocks.fastEach { b ->
            blocks.fastEach { o ->
                if (o !== b && kotlin.math.abs(o.right - b.x) < 0.01f) {
                    b.edgeLTo = o.topAt(o.right)
                    b.edgeLFrom = o.bottom
                }
                if (o !== b && kotlin.math.abs(o.x - b.right) < 0.01f) {
                    b.edgeRTo = o.topAt(o.x)
                    b.edgeRFrom = o.bottom
                }
            }
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
                ItemKind.STAR -> {
                    stars += Star(k.x, k.y, fx.nextFloat() * 6f, k.hidden)
                    continue
                }
                ItemKind.GOLD -> {
                    golds += GoldStar(k.x, k.y, golds.size, k.hidden)
                    continue
                }
                else -> continue // power-ups only come out of doodle boxes
            }
        }
        for (k in l.blots) {
            val body = ink.addWalker(k.x, k.y - k.kind.r - 0.02f, k.kind.r, 1f, creatureTag(k.kind))
            blots += Blot(body, k.x0, k.x1, k.kind)
        }
        for (k in l.boxes) {
            if (k.hidden) hiddenBoxes += k else addBox(k)
        }
        flights += l.flights
        for (row in l.crumbles) for (i in 0 until row.n) {
            val bx = row.x + i * row.w
            val body = ink.addLift(bx + 0.02f, row.y, row.w - 0.04f, CRUMBLE_H, FixtureTag.CRUMBLE)
            crumbles += Crumble(body, bx, row.y, row.w, CRUMBLE_H)
        }
        pools += l.pools
        for (k in l.tides) tides += Tide(k.x0, k.x1)
        for (k in l.warps) {
            val ty = if (k.toY.isNaN()) l.groundAt(k.toX).let { if (it.isNaN()) k.y else it } else k.toY
            warps += Warp(k.x, k.y, k.toX, ty, k.hidden, k.exit)
        }
        for (k in l.arenas) {
            val a = Arena(k.x0, k.x1, k.level, k.kind, k.ceiling)
            // the far wall stands from the start; the near one closes behind the runner
            a.right = ink.addRect(k.x1, k.level - ARENA_WALL, ARENA_WALL_W, ARENA_WALL + 40f).also { it.level = k.level; blocks += it }
            arenas += a
            val bx = k.x1 - 3f
            val body = if (k.kind == BossKind.AIRLINER) ink.addFlyer(k.x0 + 4f, k.level - AIRLINER_UP, k.kind.r, FixtureTag.BOSS)
            else ink.addWalker(bx, k.level - k.kind.r - 0.02f, k.kind.r, BOSS_DENSITY, FixtureTag.BOSS)
            boss = Boss(k.kind, body, a)
        }
        for (k in l.gates) gates += Gate(k.ink, k.x0, k.x1, k.gx0, k.gy0, k.gx1, k.gy1, k.label, k.tunes, k.waitX, k.slack, k.forbid, k.crush, k.needs)
        for (k in l.notes) notes += Note(k.x, k.y, k.text).also { it.maxW = k.maxW }
        for ((i, k) in l.hazards.withIndex()) hazards += Hazard(k.type, k.x0, k.x1, k.par).also { it.index = i }
        for (k in l.runways) blocks.firstOrNull { it.x == k.x }?.boulderTrigger = k.triggerX
        val sx = if (keep != null) l.checkpoints[keep.reached].x + 1f else l.startX
        level = l.groundAt(sx)
        spawnRunner(sx)
        dist = sx
        scoredDist = sx
        nextSpeedNote = Float.POSITIVE_INFINITY
        speedOverride = def.speed
        hazards.fastEach { if (it.x1 < sx) it.done = true }
        drops.fastEach { if (it.x < sx) it.taken = true }
        stars.fastEach { if (it.x < sx) it.taken = true }
        gates.fastEach { if (it.x1 < sx) it.passed = true }
        boxes.fastEach { if (it.rect.right < sx) it.used = true }
        val run = keep ?: StageRun(def, l.hazards.size)
        if (keep != null) run.restore(this)
        // gold stars taken before the checkpoint stay taken
        golds.fastEach { if (run.goldMask and (1 shl it.index) != 0) it.taken = true }
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

    private fun camTargetX(runnerX: Float): Float {
        if (!camFocusX.isNaN()) return camFocusX - vw / 2f
        // a boulder closing in from behind gets room on screen: the runner slides right to make it
        var frac = runnerFrac
        boulders.fastEach { bo ->
            val bx = bo.body.position.x
            if (bx < runnerX && runnerX - bx < CHASE_VIEW) {
                frac = max(frac, min(CHASE_FRAC, (runnerX - bx + BOULDER_R + 1f) / vw))
            }
        }
        // and so does the ink tide, though less: there's drawing to do ahead
        tides.fastEach { t -> if (t.up && t.ebbAt.isNaN()) frac = max(frac, min(TIDE_FRAC, (runnerX - t.x + 1.2f) / vw)) }
        return runnerX - vw * frac
    }

    /** Starts the Endless run from the title card (its "Endless" button). */
    fun begin() {
        if (state == GameState.TITLE) startRun()
    }

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
        // a bonus page far below counts only while the runner is down there
        blocks.fastEach { b -> if (b.bonus == inBonus && b.x < x + 10f && b.x + b.w > x - 10f) lv = max(lv, b.level) }
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
            if (other?.kind == FixtureKind.BOX) pendingBox = other
            if (other?.kind == FixtureKind.CRUMBLE) crumbleTouched += if (ua?.kind == FixtureKind.CRUMBLE) fa.body else fb.body
            // a beam dropped on a Blot (or a boss, an Eraser, a beetle), or a boulder rolling into one, squashes it
            val blotA = ua?.kind.let { it == FixtureKind.BLOT || it == FixtureKind.BOSS || it == FixtureKind.ERASER || it == FixtureKind.BEETLE }
            val blotB = ub?.kind.let { it == FixtureKind.BLOT || it == FixtureKind.BOSS || it == FixtureKind.ERASER || it == FixtureKind.BEETLE }
            // an Eraser (or the Giant Eraser) rubs out rope and springs it touches
            val eraserA = ua?.kind == FixtureKind.ERASER || (ua?.kind == FixtureKind.BOSS && boss?.kind == BossKind.GIANT_ERASER)
            val eraserB = ub?.kind == FixtureKind.ERASER || (ub?.kind == FixtureKind.BOSS && boss?.kind == BossKind.GIANT_ERASER)
            if (eraserA || eraserB) {
                val other2 = if (eraserA) ub else ua
                val st = other2?.stroke
                if (st != null && (other2.kind == FixtureKind.ROPE || other2.kind == FixtureKind.SPRING) && st !in toErase) toErase += st
            }
            // a paper plane tears through rope and crumples against springs and beams
            val planeA = ua?.kind == FixtureKind.PLANE
            val planeB = ub?.kind == FixtureKind.PLANE
            if (planeA != planeB) {
                val ink2 = if (planeA) ub else ua
                val st = ink2?.stroke
                if (st != null && ink2.kind == FixtureKind.ROPE && st !in toErase) toErase += st
                if (st != null && (ink2.kind == FixtureKind.SPRING || ink2.kind == FixtureKind.BEAM)) planeHits += if (planeA) fa.body else fb.body
            }
            // a beetle on rope or a spring starts chewing it
            val beetleA = ua?.kind == FixtureKind.BEETLE
            val beetleB = ub?.kind == FixtureKind.BEETLE
            if (beetleA || beetleB) {
                val food = if (beetleA) ub else ua
                val st = food?.stroke
                if (st != null && !st.gold && (food.kind == FixtureKind.ROPE || food.kind == FixtureKind.SPRING)) {
                    beetleOn += if (beetleA) fa.body else fb.body
                    beetleOnStroke += st
                }
            }
            // the Big Smudge stepping onto a spring is launched
            if ((ua?.kind == FixtureKind.BOSS && ub?.kind == FixtureKind.SPRING) || (ub?.kind == FixtureKind.BOSS && ua?.kind == FixtureKind.SPRING)) bossOnSpring = true
            if (blotA != blotB) {
                val hitter = if (blotA) ub else ua
                if (hitter?.kind == FixtureKind.BEAM || hitter?.kind == FixtureKind.BOULDER) {
                    blotHits += if (blotA) fa.body else fb.body
                    blotHitBy += if (blotA) fb.body else fa.body
                    blotHitVy.add((if (blotA) fb.body else fa.body).linearVelocity.y)
                }
            }
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
            if (ua?.kind == FixtureKind.BEETLE || ub?.kind == FixtureKind.BEETLE) {
                val body = if (ua?.kind == FixtureKind.BEETLE) c.fixtureA.body else c.fixtureB.body
                val st = (if (ua?.kind == FixtureKind.BEETLE) ub else ua)?.stroke
                for (i in beetleOn.indices.reversed()) if (beetleOn[i] === body && beetleOnStroke[i] === st) {
                    beetleOn.removeAt(i)
                    beetleOnStroke.removeAt(i)
                }
            }
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
        // (a stage restarts through its session, never into an Endless run)
        if (stage == null && (state == GameState.TITLE || canRestart)) {
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
        val highlighting = runner.power == ItemKind.HIGHLIGHTER
        if (inkLeft <= 0.5f && !highlighting && runner.power != ItemKind.GOLD_NIB) {
            events.toast(OUT_OF_INK)
            return
        }
        drawing = Drawing(pointer).also { d ->
            d.highlight = highlighting
            d.goldNib = runner.power == ItemKind.GOLD_NIB
            d.chalk = runner.power == ItemKind.CHALK
            d.gate = if (learning || gateTuning) gateAt(x) else null
            d.forced = d.gate?.takeIf { gateTuning && it.tunes }
        }
        steady.reset()
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
        steady.add(t, n)
        if (d.highlight) {
            // a highlighter swipe: only where it went
            val lst = d.size - 1
            if (lst >= 0 && hypot(x - d.xs[lst], y - d.ys[lst]) < 0.05f) return
            d.xs.add(x); d.ys.add(y); d.ps.add(d.smoothed)
            d.x0 = min(d.x0, x); d.x1 = max(d.x1, x)
            return
        }
        val kind = inkOf(d, steady.value(d.smoothed))
        val last = d.size - 1
        if (last >= 0) {
            val len = hypot(x - d.xs[last], y - d.ys[last])
            if (len < 0.02f) {
                d.ps[last] = max(d.ps[last], d.smoothed)
                return
            }
            val th = Ink.thickness(Ink.clampTo(d.smoothed, kind))
            val c = len * th * th * InkPhysics.INK_K
            if (!unlimitedInk && !d.goldNib && inkLeft - d.cost - c <= 0f) {
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
            d.startAnchored = ink.anchorable(x, y, Ink.thickness(Ink.clampTo(d.smoothed, kind)))
        }
        d.xs.add(x); d.ys.add(y); d.ps.add(d.smoothed)
        d.x0 = min(d.x0, x)
        d.x1 = max(d.x1, x)
        if (last >= 0) {
            // the whole stroke is one ink; while drawing, it is the one it is heading for
            d.heaviest = kind.weight
            d.kinds = d.kinds or (1 shl kind.weight)
        }
        growWet(d, kind)
    }

    /**
     * Lays wet ink up to the newest point in pieces of at least [WET_STEP], so the runner can
     * roll on a stroke while it is still being drawn (the POC built it only on pen-up). Only a
     * stroke begun where it will be pinned is laid (one begun in mid-air falls when finished),
     * and rope past [ROPE_MAX_LEN] isn't, as the finished rope would give way there.
     */
    private fun growWet(d: Drawing, kind: Ink) {
        // (Cloud chalk floats where it's drawn, hooked or not)
        if (!d.startAnchored && !d.chalk) return
        if (kind != d.wetInk) {
            // the stroke has settled on another ink: lay what is wet again in it
            if (d.wetInk != null) ink.clearWet()
            d.wetInk = kind
            d.wetFrom = 0
            d.wetRope = 0f
        }
        var j = d.wetFrom
        for (i in j + 1 until d.size) {
            val len = hypot(d.xs[i] - d.xs[j], d.ys[i] - d.ys[j])
            if (len < WET_STEP) continue
            if (kind == Ink.ROPE) {
                d.wetRope += len
                if (d.wetRope > ROPE_MAX_LEN) {
                    if (!d.tooLong) events.toast(TOO_LONG_FOR_ROPE)
                    d.tooLong = true
                    break
                }
            }
            val p = Ink.clampTo((d.ps[i] + d.ps[j]) / 2f, kind)
            ink.addWet(d.xs[j], d.ys[j], d.xs[i], d.ys[i], kind, Ink.thickness(p))
            j = i
        }
        d.wetFrom = j
    }

    private fun finishStroke() {
        val d = drawing
        drawing = null
        ink.clearWet()
        events.scratch(0f, 0.5f)
        lastStrokeInk = null
        lastGate = null
        if (d == null || d.size < 2) return
        if (d.highlight) {
            highlightSwipe(d)
            return
        }
        // one ink for the whole stroke, from the pressure it settled at (or the tuning gate's)
        val kind = d.forced?.ink ?: Ink.of(steady.finish(d.smoothed))
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
        for (i in 0 until n) ps[i] = Ink.clampTo(ps[i], kind)
        val s = ink.buildStroke(d.xs.items, d.ys.items, ps.items, n) ?: return
        lastStrokeInk = kind
        if (!unlimitedInk && !d.goldNib) inkLeft = max(0f, inkLeft - s.cost)
        if (d.goldNib) {
            // Gold nib ink never snaps
            s.gold = true
            s.joints.fastEach { it.strength = Float.MAX_VALUE }
        }
        if (d.chalk) {
            // Cloud chalk stays where it was drawn
            s.chalk = true
            s.groups.fastEach { g -> g.body?.type = com.badlogic.gdx.physics.box2d.BodyDef.BodyType.StaticBody }
        }
        // footprint + heaviest ink, for scoring against each hazard's par
        var x0 = Float.POSITIVE_INFINITY
        var x1 = Float.NEGATIVE_INFINITY
        for (i in 0 until n) { x0 = min(x0, d.xs[i]); x1 = max(x1, d.xs[i]) }
        s.x0 = x0; s.x1 = x1
        s.heaviest = s.segs.maxOf { it.kind.weight }
        // learning: a stroke for the obstacle the runner waits at has to do the job, or it's wiped
        val g = d.gate ?: waitingAt?.takeIf { x1 >= it.x0 - GATE_SLACK && x0 <= it.x1 + GATE_SLACK }
        if (g != null && learning && !g.waitX.isNaN() && !g.done) {
            val tip = missTip(g, s, kind)
            if (tip != null) {
                ink.removeStroke(s)
                if (!unlimitedInk) inkLeft = min(INK_MAX, inkLeft + s.cost)
                events.burst((x0 + x1) / 2f, (d.ys[0] + d.ys[n - 1]) / 2f, 10, 2f, Tok.MUTED)
                events.sound(Sfx.ERASE)
                events.toast(tip, force = true)
                return
            }
            if (!g.crush) g.done = true
        }
        lastGate = d.forced
        for (a in s.anchors) events.burst(a.x, a.y, 5, 1.2f, Tok.GRAPHITE)
        if (s.anchors.isNotEmpty()) events.sound(Sfx.ANCHOR)
        if (!s.gold && !s.chalk) weakenLongRope(s)
    }

    // ---------------------------------------------------------------- powers and secrets

    /** A highlighter swipe: it costs nothing, builds nothing, and reveals whatever hidden lies under it. */
    private fun highlightSwipe(d: Drawing) {
        val n = d.size
        val xs = FloatArray(n) { d.xs[it] }
        val ys = FloatArray(n) { d.ys[it] }
        swipes += Swipe(xs, ys, n, simT)
        var found = 0
        fun near(x: Float, y: Float, reach: Float): Boolean {
            for (i in 0 until n) if (kotlin.math.abs(xs[i] - x) < reach && kotlin.math.abs(ys[i] - y) < reach) return true
            return false
        }
        stars.fastEach { if (it.hidden && near(it.x, it.y, HIGHLIGHT_R)) { it.hidden = false; found++; events.burst(it.x, it.y, 4, 1f, Tok.STAR) } }
        golds.fastEach { if (it.hidden && near(it.x, it.y, HIGHLIGHT_R)) { it.hidden = false; found++; events.burst(it.x, it.y, 10, 1.6f, Tok.STAR) } }
        warps.fastEach { if (it.hidden && near(it.x, it.y - 0.3f, HIGHLIGHT_R + 0.3f)) { it.hidden = false; found++; events.burst(it.x, it.y - 0.4f, 10, 1.6f, Tok.INK) } }
        for (i in hiddenBlocks.indices.reversed()) {
            val b = hiddenBlocks[i]
            var hit = false
            for (k in 0 until n) if (xs[k] >= b.x - HIGHLIGHT_R && xs[k] <= b.right + HIGHLIGHT_R && ys[k] >= b.top - HIGHLIGHT_R && ys[k] <= b.top + b.depth + HIGHLIGHT_R) { hit = true; break }
            if (!hit) continue
            hiddenBlocks.removeAt(i)
            val r = ink.addRect(b.x, b.top, b.w, b.depth)
            r.level = b.top
            blocks += r
            found++
            events.burst(b.x + b.w / 2f, b.top, 12, 2f, Tok.STAR)
        }
        for (i in hiddenBoxes.indices.reversed()) {
            val b = hiddenBoxes[i]
            if (!near(b.x + b.size / 2f, b.top + b.size / 2f, HIGHLIGHT_R + b.size / 2f)) continue
            hiddenBoxes.removeAt(i)
            addBox(b)
            found++
            events.burst(b.x + b.size / 2f, b.top, 10, 1.6f, Tok.STAR)
        }
        if (found > 0) events.sound(Sfx.GOLD)
    }

    private fun addBox(k: StageLayout.Box) {
        val tag = FixtureTag(FixtureKind.BOX)
        boxes += DoodleBox(ink.addRect(k.x, k.top, k.size, k.size, tag = tag), k.holds, tag)
    }

    private fun creatureTag(c: Creature) = when (c) {
        Creature.BLOT -> FixtureTag.BLOT
        Creature.ERASER -> FixtureTag.ERASER
        Creature.BEETLE -> FixtureTag.BEETLE
    }

    /** A pickup caught: the marker cap makes the runner Bold; the others are timed powers. */
    private fun applyPower(kind: ItemKind, x: Float, y: Float) {
        happyUntil = simT + HAPPY_S * 2f
        events.burst(x, y, 16, 2.4f, Tok.ACCENT)
        if (kind == ItemKind.CAP) {
            if (!runner.bold) setBold(true)
            score += CAP_POINTS
            events.pop(x, y - 1.2f, "Bold! +$CAP_POINTS", Tok.ACCENT)
            events.sound(Sfx.BOLD)
            events.toast("Bold: you survive one hit and flatten Blots.", force = true)
            return
        }
        runner.power = kind
        runner.powerUntil = simT + POWER_S
        score += CAP_POINTS
        events.sound(Sfx.BOLD)
        val (name, what) = when (kind) {
            ItemKind.HIGHLIGHTER -> "Highlighter!" to "Draw over the page to find what's hidden."
            ItemKind.CHALK -> "Cloud chalk!" to "What you draw floats where you draw it."
            ItemKind.GOLD_NIB -> "Gold nib!" to "Free ink that never snaps, and beetles can't bite."
            else -> "Star nib!" to "Nothing can hurt you: roll right through."
        }
        events.pop(x, y - 1.2f, "$name +$CAP_POINTS", Tok.STAR)
        events.toast("$name $what", force = true)
    }

    /** Timed powers run out. */
    private fun updatePower() {
        if (runner.power != null && simT >= runner.powerUntil) {
            runner.power = null
            events.sound(Sfx.POWER_END)
        }
        // highlighter swipes fade
        swipes.removeAll { simT - it.at > SWIPE_S }
    }

    /** Rolling over an inkwell's mouth drops the runner to a bonus page, or lifts it back up. */
    private fun updateWarps(px: Float, py: Float) {
        val rb = runner.body
        warps.fastEach { w ->
            if (w.hidden || kotlin.math.abs(px - w.x) > 0.45f || kotlin.math.abs(py + runner.r - w.y) > 0.6f) return@fastEach
            if (kotlin.math.abs(rb.linearVelocity.y) > 2f) return@fastEach
            inBonus = !w.exit
            drawing = null
            ink.clearWet()
            rb.setTransform(w.toX, w.toY - runner.r - 0.05f, rb.angle)
            rb.setLinearVelocity(runSpeed, 0f)
            runner.prev.snapshot()
            runner.bestX = w.toX
            runner.bestT = simT
            snapCamera()
            events.burst(w.toX, w.toY - 0.5f, 14, 2.2f, Tok.INK)
            events.sound(if (w.exit) Sfx.CHECKPOINT else Sfx.POP)
            if (!w.exit) events.toast("A bonus page!", force = true)
            return
        }
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
            waitingAt = if (state == GameState.RUN && !runnerHeld) gateToWaitAt(rb.position.x) else null
            val wg = waitingAt
            if (wg != null) {
                // a first-timer's runner rolls to a stop before the obstacle and waits for their stroke
                val vDes = kotlin.math.sqrt(2f * WAIT_DECEL * max(0f, wg.waitX - rb.position.x))
                val wt = min(runSpeed, vDes) / runner.r
                rb.applyTorque((3f * (wt - rb.angularVelocity)).coerceIn(-7f, 7f), true)
                runner.stuck = 0f
                // stood there a while with nothing drawn: say what to do
                if (kotlin.math.abs(rb.linearVelocity.x) < 0.2f && drawing == null) {
                    wg.waitT += sdt
                    if (wg.waitT > NUDGE_S && !wg.nudged) {
                        wg.nudged = true
                        events.toast(TIP_IDLE, force = true)
                    }
                }
            } else if (state == GameState.RUN && !runnerHeld) {
                val w = rb.angularVelocity
                val wt = runner.dir * runSpeed / runner.r
                // stages let a downhill roll build speed: the drive barely brakes there (but turns
                // round in a boss arena)
                val brake = if (stage != null && !bossFight) -STAGE_BRAKE else -7f
                rb.applyTorque((3f * (wt - w)).coerceIn(if (runner.dir > 0) brake else -7f, if (runner.dir > 0) 7f else -brake), true)
                val v = rb.linearVelocity
                if (kotlin.math.abs(v.x) < 0.4f && kotlin.math.abs(v.y) < 0.6f) {
                    runner.stuck += sdt
                    if (runner.stuck > 0.45f) {
                        val m = rb.mass
                        rb.applyLinearImpulse(runner.dir * 1.6f * m, -4.6f * m, rb.worldCenter.x, rb.worldCenter.y, true)
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
                rb.setLinearVelocity(if (runner.dir > 0) max(v.x, runSpeed * 0.95f) else min(v.x, -runSpeed * 0.95f), -9.6f)
                runner.bounceCd = 0.3f
                bounces++
                runner.squash = 1f
                bounceAt = simT
                val p = rb.position
                events.burst(p.x, p.y + runner.r, 8, 2f, Tok.SPRING, 0f)
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
            if (pd != null && state == GameState.RUN) hurt(pd, DeathKind.SPIKED)
            pendingDeath = null
            if (state == GameState.RUN) {
                crushBlots()
                hitBox()
                bossSpring()
            }
            toErase.fastEach { st -> if (st in ink.strokes) eraseStroke(st) }
            planeHits.fastEach { pb -> planes.firstOrNull { !it.down && it.body === pb }?.let { crumplePlane(it, null) } }
            planeHits.clear()
            crumbleTouched.fastEach { cb -> crumbles.firstOrNull { !it.gone && it.body === cb && it.touchedAt.isNaN() }?.touchedAt = simT }
            crumbleTouched.clear()
            updateCrumbles(sdt)
            toErase.clear(); bossOnSpring = false
            blotHits.clear(); blotHitBy.clear(); blotHitVy.clear(); pendingBox = null
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
                if (hypot(q.x - px, q.y - py) < BOULDER_R + runner.r + 0.04f) die("Flattened", DeathKind.BOULDER)
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
            takeStars(px, py)
            updatePlanes(px, py)
            updatePower()
            updateCrumbling()
            updatePools()
            updateTide(px, sdt)
            updateWarps(px, py)
            updateArena(px, py)
            updateBlots(px, py)
            updateCaps(px, py)
            gates.fastEach { if (!it.passed && px > it.x1 + 0.6f) { it.passed = true; it.passedAt = simT } }
            // a crush gate is done once nothing is left alive under it
            gates.fastEach { g -> if (g.crush && !g.done && blots.none { !it.dead && it.body.position.x > g.x0 - 1f && it.body.position.x < g.x1 + 4f }) g.done = true }
            // score: every metre is worth the current combo
            if (dist > scoredDist) {
                score += (dist - scoredDist).toDouble() * combo
                scoredDist = dist
            }
            judgeHazards(px, py)
            stageProgress(px)
            // (read again: an inkwell may have just moved the runner)
            val now = rb.position
            if (inPool(now.x, now.y + runner.r - POOL_GRACE)) {
                events.burst(now.x, now.y, 18, 3f, Tok.BLOT)
                die("Swallowed by the ink", DeathKind.POOL)
            }
            if (now.y > levelNear(now.x) + 4f) die("Fell in", DeathKind.FELL)
            // riding a lift isn't being stuck
            if (px > runner.bestX + 0.5f || runnerHeld || waitingAt != null || bossFight || (runnerOnLift > 0 && liftMoving())) {
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
        updateMood()
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
                happyUntil = simT + HAPPY_S
                events.pop(cx, cy, "Featherweight +$bonus", Tok.ROPE)
                events.sound(Sfx.FEATHERWEIGHT)
            } else if (heavy == par) {
                combo = min(COMBO_MAX, combo + 1)
                happyUntil = simT + HAPPY_S
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
        return n >= 2 && d.startAnchored && tipHooks
    }

    // ---------------------------------------------------------------- slice 1: stars, Blots, boxes, Bold

    // ---------------------------------------------------------------- bosses

    /**
     * A boss arena: once the runner is in, a wall closes behind it, the camera holds the arena
     * and the runner rolls back and forth between the walls until the boss is beaten.
     */
    private fun updateArena(px: Float, py: Float) {
        val a = arenas.firstOrNull { !it.won } ?: return
        if (!a.closed) {
            if (px < a.x0 + ARENA_ENTER) return
            a.closed = true
            a.left = ink.addRect(a.x0 - ARENA_WALL_W, a.level - ARENA_WALL, ARENA_WALL_W, ARENA_WALL + 40f).also { it.level = a.level; blocks += it }
            bossFight = true
            camFocusX = a.mid
            events.toast(a.kind.title + "!", force = true)
            events.sound(Sfx.RUMBLE)
            events.shake(0.25f)
            events.burst(a.x0, a.level - 2f, 14, 3f, Tok.GRAPHITE)
            return
        }
        if (runner.dir > 0 && px > a.x1 - runner.r - TURN_GAP) runner.dir = -1
        if (runner.dir < 0 && px < a.x0 + runner.r + TURN_GAP) runner.dir = 1
        boss?.let { if (!it.down) updateBoss(it, px, py) }
    }

    private fun bossSpeed(b: Boss): Float {
        val base = when (b.kind) {
            BossKind.BIG_BLOT -> 1.4f
            BossKind.GIANT_ERASER -> 1.0f
            BossKind.AIRLINER -> 2.6f
            BossKind.BEETLE_QUEEN -> 1.0f
            BossKind.BIG_SMUDGE -> 1.2f
        }
        // angrier with every hit
        return base * (1f + 0.2f * (b.kind.hp - b.hp))
    }

    private fun updateBoss(b: Boss, px: Float, py: Float) {
        val body = b.body
        val q = body.position
        val a = b.arena
        val v = body.linearVelocity
        if (b.kind == BossKind.AIRLINER) {
            // back and forth across the top of the arena, bobbing
            if (b.dir > 0 && q.x > a.x1 - 1.5f) b.dir = -1
            if (b.dir < 0 && q.x < a.x0 + 1.5f) b.dir = 1
            val ty = a.level - AIRLINER_UP + 0.3f * sin(simT * 2f)
            body.setLinearVelocity(b.dir * bossSpeed(b), (ty - q.y) * 3f)
        } else if (simT >= b.hurtUntil && kotlin.math.abs(v.y) < 0.6f && !b.launched) {
            // walks at the runner
            b.dir = if (px < q.x) -1 else 1
            body.setLinearVelocity(b.dir * bossSpeed(b), v.y)
        }
        if (b.launched && v.y >= 0f && q.y > a.level - b.r - 0.3f) b.launched = false
        // the Big Smudge, sprung into its spiked roof
        if (b.kind == BossKind.BIG_SMUDGE && b.launched && !a.ceiling.isNaN() && q.y - b.r < a.ceiling + SPIKE_DEPTH + 0.1f && simT >= b.hurtUntil) {
            hitBoss(b, "Spiked!")
            if (!b.down) body.setLinearVelocity(v.x, 3f)
        }
        if (b.down) return
        // its moves: the Big Smudge spits Blots, the Queen lays beetles, the Airliner drops planes
        b.actT -= 1f / 60f
        if (b.actT <= 0f) {
            b.actT = BOSS_ACT_S
            bossAct(b, px)
        }
        // touching the runner
        val rb = runner.body
        val rv = rb.linearVelocity
        val d = hypot(q.x - px, q.y - py)
        val stomp = (rv.y > 0.5f && py < q.y - 0.3f) || simT - bounceAt < BOUNCE_STOMP_S
        if (d > runner.r + b.r + if (stomp) STOMP_SLACK else 0.04f) return
        when (b.kind) {
            BossKind.AIRLINER -> if (rv.y < 0f && py > q.y && simT >= b.hurtUntil) {
                // bounced up into it
                hitBoss(b, "Hit!")
                rb.setLinearVelocity(rv.x, 2f)
                return
            }
            BossKind.BIG_BLOT, BossKind.BEETLE_QUEEN -> if (stomp) {
                if (simT >= b.hurtUntil) hitBoss(b, "Stomp!")
                rb.setLinearVelocity(rv.x, STOMP_VY)
                runner.squash = 1f
                return
            }
            BossKind.GIANT_ERASER, BossKind.BIG_SMUDGE -> if (stomp) {
                if (b.launched || b.body.linearVelocity.y < -1f) {
                    // flying up at its roof: it brushes the runner aside rather than carrying it up there
                    rb.setLinearVelocity(if (px < q.x) -3f else 3f, max(rv.y, 2f))
                    return
                }
                // too big to squash: the runner bounces off its top
                if (py < q.y) rb.setLinearVelocity(rv.x, STOMP_VY)
                runner.squash = 1f
                return
            }
        }
        if (simT >= b.hurtUntil) hurt(b.kind.title, DeathKind.BOSS)
    }

    /** A spring under the Big Smudge throws it up at its spiked roof. */
    private fun bossSpring() {
        val b = boss ?: return
        if (!bossOnSpring || b.down || b.kind != BossKind.BIG_SMUDGE || b.launched || !b.arena.closed) return
        val v = b.body.linearVelocity
        b.body.setLinearVelocity(v.x, SMUDGE_LAUNCH)
        b.launched = true
        val q = b.body.position
        events.burst(q.x, q.y + b.r, 12, 2.5f, Tok.SPRING, 0f)
        events.sound(Sfx.BOULDER_BOING)
    }

    private fun bossAct(b: Boss, px: Float) {
        val q = b.body.position
        when (b.kind) {
            BossKind.AIRLINER -> {
                // a plane dives at where the runner is
                val rp = runner.body.position
                val dx = rp.x - q.x
                val dy = rp.y - q.y
                val len = max(0.1f, hypot(dx, dy))
                launchPlane(q.x, q.y + b.r, dx / len * PLANE_DIVE, dy / len * PLANE_DIVE)
                events.sound(Sfx.POP)
            }
            BossKind.BIG_SMUDGE, BossKind.BEETLE_QUEEN -> {
                if (blots.count { !it.dead } >= 2) return
                // a Blot out of the Smudge's mouth (a beetle from the Queen), towards the runner
                val kind = if (b.kind == BossKind.BEETLE_QUEEN) Creature.BEETLE else Creature.BLOT
                val dir = if (px < q.x) -1f else 1f
                val body = ink.addWalker(q.x + dir * (b.r + kind.r + 0.1f), q.y - 0.3f, kind.r, 1f, creatureTag(kind))
                body.setLinearVelocity(dir * 3f, -4f)
                blots += Blot(body, b.arena.x0 + 0.5f, b.arena.x1 - 0.5f, kind).also { it.awake = true; it.dir = dir.toInt() }
                events.sound(Sfx.POP)
            }
            else -> Unit
        }
    }

    private fun hitBoss(b: Boss, label: String) {
        if (b.down || simT < b.hurtUntil) return
        b.hp--
        b.hurtUntil = simT + BOSS_HURT_S
        val q = b.body.position
        score += BOSS_HIT_POINTS
        happyUntil = simT + HAPPY_S
        events.burst(q.x, q.y, 16, 3f, Tok.INK)
        events.pop(q.x, q.y - b.r - 0.8f, "$label +$BOSS_HIT_POINTS", Tok.ACCENT)
        events.sound(Sfx.BOSS_HIT)
        events.shake(0.2f)
        if (b.kind != BossKind.AIRLINER) {
            // knocked back, away from the runner
            val away = if (runner.body.position.x < q.x) 1f else -1f
            b.body.setLinearVelocity(away * 4f, -5f)
        }
        if (b.hp <= 0) bossDown(b)
    }

    private fun bossDown(b: Boss) {
        b.down = true
        b.downAt = simT
        val q = b.body.position
        val qx = q.x
        val qy = q.y
        events.burst(qx, qy, 40, 5f, Tok.INK)
        events.burst(qx, qy, 20, 4f, Tok.STAR)
        ink.destroyBody(b.body)
        score += BOSS_POINTS
        events.pop(qx, qy - 1.5f, "${b.kind.title} beaten! +$BOSS_POINTS", Tok.ACCENT)
        events.sound(Sfx.BOSS_DOWN)
        events.shake(0.35f)
        // its Blots go with it, the fight's ink fades (dropped beams would wall the way out), and the far wall opens
        blots.fastEach { bl -> if (!bl.dead) squash(bl, "+$BLOT_POINTS") }
        val a = b.arena
        drawing = null
        ink.clearWet()
        for (s in ArrayList(ink.strokes)) {
            if (s.x1 >= a.x0 && s.x0 <= a.x1) {
                val p = s.segs.firstOrNull()?.group?.body?.position
                if (p != null) events.burst(p.x, p.y, 6, 1.5f, Tok.MUTED)
                ink.removeStroke(s)
            }
        }
        a.won = true
        a.right?.let { w ->
            events.burst(w.x + w.w / 2f, a.level - 2f, 24, 3f, Tok.GRAPHITE)
            ink.removeRect(w)
            blocks.remove(w)
        }
        a.right = null
        bossFight = false
        runner.dir = 1
        camFocusX = Float.NaN
    }

    /** An Eraser rubbed out [s] (rope or a spring). */
    private fun eraseStroke(s: Stroke) {
        val p = s.segs.firstOrNull()?.group?.body?.position
        if (p != null) events.burst(p.x, p.y, 10, 2f, Tok.MUTED)
        events.sound(Sfx.ERASE)
        ink.removeStroke(s)
    }

    /**
     * A Blot or spikes got the runner. Bold takes the hit: the runner shrinks back and nothing
     * hurts for [SAFE_S]. Otherwise it's a life.
     */
    private fun hurt(why: String, kind: DeathKind) {
        if (state != GameState.RUN || simT < runner.safeUntil) return
        // the Star nib: nothing hurts
        if (runner.power == ItemKind.STAR_NIB) return
        val rb = runner.body
        val p = rb.position
        if (!runner.bold) {
            events.burst(p.x, p.y - runner.r, 16, 3f, Tok.INK)
            die(why, kind)
            return
        }
        setBold(false)
        runner.safeUntil = simT + SAFE_S
        ouchAt = simT
        events.burst(p.x, p.y, 12, 2.5f, Tok.ACCENT)
        events.sound(Sfx.SHRINK)
        events.shake(0.2f)
        events.haptic(Haptic.SNAP)
        // knocked clear: up off floor spikes and Blots, down from spikes overhead
        val v = rb.linearVelocity
        rb.setLinearVelocity(max(v.x, runSpeed * 0.9f), if (kind == DeathKind.SPIKED && spikesAbove(p.x, p.y)) 2f else -7.5f)
    }

    /** Whether the nearest spikes to ([px], [py]) hang above it (a spiked ceiling or a ledge's underside). */
    private fun spikesAbove(px: Float, py: Float): Boolean {
        var best = Float.POSITIVE_INFINITY
        var above = false
        fun consider(r: com.pranshuparmar.inkmayhem.physics.GroundRect, spikeY: Float, up: Boolean) {
            val dx = max(0f, max(r.x - px, px - r.right))
            val d = hypot(dx, spikeY - py)
            if (d < best) { best = d; above = !up }
        }
        spikeStrips.fastEach { consider(it, if (it.spikesUp) it.y else it.bottom, it.spikesUp) }
        ceilings.fastEach { if (it.kind == FixtureKind.CEILING) consider(it, it.bottom, false) }
        return above
    }

    /** The marker cap on or off: Bold is a bigger runner of the same weight. */
    private fun setBold(on: Boolean) {
        runner.bold = on
        runner.r = if (on) BOLD_R else RUNNER_R
        ink.resizeBall(runner.body, runner.r)
    }

    private fun takeStars(px: Float, py: Float) {
        golds.fastEach { gs ->
            if (gs.taken || gs.hidden || hypot(gs.x - px, gs.y - py) > runner.r + GOLD_R) return@fastEach
            gs.taken = true
            stageRun?.let { it.goldMask = it.goldMask or (1 shl gs.index) }
            score += GOLD_POINTS
            happyUntil = simT + HAPPY_S * 2f
            events.burst(gs.x, gs.y, 18, 2.6f, Tok.STAR)
            events.pop(gs.x, gs.y - 1f, "Gold star! +$GOLD_POINTS", Tok.STAR)
            events.sound(Sfx.GOLD)
            events.goldStar(gs.index)
        }
        val reach = runner.r + STAR_R
        stars.fastEach { st ->
            if (st.taken || st.hidden || kotlin.math.abs(st.x - px) > reach || hypot(st.x - px, st.y - py) > reach) return@fastEach
            st.taken = true
            // taken close together, each rings one note higher (eight in all)
            starStep = if (simT - lastStarT < STREAK_S) min(STAR_NOTES - 1, starStep + 1) else 0
            lastStarT = simT
            starsTaken++
            score += STAR_POINTS
            happyUntil = simT + HAPPY_S
            events.burst(st.x, st.y, 6, 1.4f, Tok.SPRING)
            events.sound(Sfx.STAR_TRAIL, starStep)
            events.star()
        }
    }

    /**
     * Blots wake when the runner comes within [BLOT_WAKE], then walk at it (pacing their patrol,
     * turning round at walls). Touching one from above squashes it and bounces the runner; a Bold
     * runner flattens it whatever the angle; any other touch hurts.
     */
    private fun updateBlots(px: Float, py: Float) {
        val rb = runner.body
        blots.fastEach { b ->
            if (b.dead) return@fastEach
            val body = b.body
            val q = body.position
            if (q.y > levelNear(q.x) + 10f || inPool(q.x, q.y)) {
                if (q.y < levelNear(q.x) + 10f) events.burst(q.x, q.y, 10, 2f, Tok.BLOT)
                b.gone = true
                ink.destroyBody(body)
                return@fastEach
            }
            if (!b.awake && q.x - px < b.kind.wake) b.awake = true
            val v = body.linearVelocity
            val speed = b.kind.speed
            if (b.awake) {
                if (b.walking && kotlin.math.abs(v.x) < speed * 0.3f) b.dir = -b.dir // walked into something
                if (!b.x0.isNaN() && q.x <= b.x0) b.dir = 1
                if (!b.x1.isNaN() && q.x >= b.x1) b.dir = -1
                body.setLinearVelocity(b.dir * speed, v.y)
                b.walking = true
            }
            if (b.kind == Creature.BEETLE) chew(b)
            if (state != GameState.RUN) return@fastEach
            val d = hypot(q.x - px, q.y - py)
            val rv = rb.linearVelocity
            // coming down on it counts a little wide, like a stomp in any platformer, and so does
            // meeting one anywhere in a spring's bounce, landing included
            val stomp = (rv.y > 0.5f && py < q.y - 0.15f) || simT - bounceAt < BOUNCE_STOMP_S
            if (d > runner.r + b.kind.r + if (stomp) STOMP_SLACK else 0.04f) return@fastEach
            when {
                runner.power == ItemKind.STAR_NIB -> squash(b, "Zap! +$BLOT_POINTS", atRunner = true)
                // an Eraser is too rubbery to squash: the runner bounces off it
                stomp && b.kind == Creature.ERASER -> {
                    rb.setLinearVelocity(rv.x, STOMP_VY)
                    runner.squash = 1f
                }
                stomp -> {
                    squash(b, "Stomp! +$BLOT_POINTS")
                    rb.setLinearVelocity(rv.x, STOMP_VY)
                    runner.squash = 1f
                }
                runner.bold -> squash(b, "Flattened! +$BLOT_POINTS", atRunner = true)
                simT < runner.safeUntil -> Unit
                else -> when (b.kind) {
                    Creature.BLOT -> hurt("Blotted", DeathKind.BLOT)
                    Creature.ERASER -> hurt("Rubbed out", DeathKind.ERASER)
                    Creature.BEETLE -> hurt("Bitten", DeathKind.BEETLE)
                }
            }
        }
    }

    /** Flights take off as the runner passes their mark; planes fly on, hit the runner or crumple. */
    private fun updatePlanes(px: Float, py: Float) {
        for (i in flights.indices.reversed()) {
            val f = flights[i]
            if (px < f.x) continue
            flights.removeAt(i)
            for (k in 0 until f.count) launchPlane(f.x + PLANE_START + k * f.gap, f.ground - f.up, -PLANE_SPEED, 0f)
        }
        val rb = runner.body
        planes.fastEach { pl ->
            if (pl.down) return@fastEach
            val q = pl.body.position
            if (q.x < cam.x - 6f || q.y > levelNear(q.x) + 6f) {
                pl.crumpledAt = simT - 10f
                ink.destroyBody(pl.body)
                return@fastEach
            }
            if (state != GameState.RUN) return@fastEach
            if (hypot(q.x - px, q.y - py) > runner.r + PLANE_R) return@fastEach
            val rv = rb.linearVelocity
            val bouncing = simT - bounceAt < BOUNCE_STOMP_S || (rv.y > 0.5f && py < q.y - 0.15f)
            when {
                bouncing || runner.bold || runner.power == ItemKind.STAR_NIB -> crumplePlane(pl, "Crumpled! +$PLANE_POINTS")
                simT < runner.safeUntil -> Unit
                else -> {
                    crumplePlane(pl, null)
                    hurt("Hit by a paper plane", DeathKind.PLANE)
                }
            }
        }
        planes.removeAll { it.down && simT - it.crumpledAt > 0.8f }
    }

    private fun launchPlane(x: Float, y: Float, vx: Float, vy: Float) {
        val body = ink.addFlyer(x, y, PLANE_R, FixtureTag.PLANE)
        body.setLinearVelocity(vx, vy)
        planes += Plane(body, vx, vy)
    }

    private fun crumplePlane(pl: Plane, label: String?) {
        if (pl.down) return
        val q = pl.body.position
        pl.cx = q.x
        pl.cy = q.y
        pl.crumpledAt = simT
        ink.destroyBody(pl.body)
        events.burst(pl.cx, pl.cy, 8, 1.6f, Tok.GRAPHITE)
        events.sound(Sfx.SQUASH)
        if (label != null) {
            score += PLANE_POINTS
            happyUntil = simT + HAPPY_S
            events.pop(pl.cx, pl.cy - 1f, label, Tok.ACCENT)
        }
    }

    /** The beam [body] belongs crumbles away [CRUMBLE_S] from now, if it was dropped (a bridge hooked at an end stays). */
    private fun crumble(body: Body) {
        val st = (body.fixtureList.firstOrNull()?.userData as? FixtureTag)?.stroke ?: return
        if (st in crumbling || st.anchors.isNotEmpty()) return
        crumbling += st
        crumbleAt.add(simT + CRUMBLE_S)
    }

    private fun updateCrumbling() {
        for (i in crumbling.indices.reversed()) {
            if (simT < crumbleAt[i]) continue
            val st = crumbling.removeAt(i)
            crumbleAt.removeIndex(i)
            if (st in ink.strokes) eraseStroke(st)
        }
    }

    /**
     * An ink beetle goes for rope and springs: one over it within [BEETLE_REACH] up, it hops up to;
     * once it's on one, it eats it in [BITE_S]. Beams and Gold nib ink it leaves alone.
     */
    private fun chew(b: Blot) {
        if (!b.awake) return
        val q = b.body.position
        var food: Stroke? = null
        var foodY = 0f
        var foodX = 0f
        ink.strokes.fastEach { s ->
            if (food != null || s.gold || s.x1.isNaN()) return@fastEach
            val kind = s.segs.firstOrNull()?.kind ?: return@fastEach
            if (kind == Ink.BEAM || q.x < s.x0 - 3f || q.x > s.x1 + 3f) return@fastEach
            // the stroke's height near the beetle
            var best = Float.POSITIVE_INFINITY
            var y = 0f
            s.groups.fastEach { g -> g.body?.position?.let { p -> val dd = kotlin.math.abs(p.x - q.x); if (dd < best) { best = dd; y = p.y } } }
            if (best > 3f || q.y - y > BEETLE_REACH || y - q.y > 1.2f) return@fastEach
            food = s
            foodY = y
            // (a stroke shorter than 0.8 m: its middle)
            foodX = if (s.x1 - s.x0 > 0.8f) q.x.coerceIn(s.x0 + 0.4f, s.x1 - 0.4f) else (s.x0 + s.x1) / 2f
        }
        val s = food
        if (s == null) {
            b.biteT = 0f
            return
        }
        val v = b.body.linearVelocity
        if (q.y - foodY > 0.9f) {
            // below it: hop up (once on the ground again)
            if (kotlin.math.abs(v.y) < 0.2f) b.body.setLinearVelocity((foodX - q.x).coerceIn(-2f, 2f), -kotlin.math.sqrt(2f * InkPhysics.GRAVITY * (q.y - foodY + 0.8f)))
            b.biteT = 0f
            return
        }
        b.biteT += 1f / 60f
        if (b.biteT < BITE_S) return
        b.biteT = 0f
        eraseStroke(s)
        events.toast("A beetle ate it!", force = true)
    }

    /** Whether ([x], [y]) is under an ink pool's surface. */
    private fun inPool(x: Float, y: Float): Boolean {
        for (i in pools.indices) {
            val p = pools[i]
            if (x > p.x0 && x < p.x1 && y > p.surface) return true
        }
        return false
    }

    /** Ink that dips into a pool is swallowed a moment later: bridges have to stay above it. */
    private fun updatePools() {
        if (pools.isEmpty()) return
        ink.strokes.fastEach { s ->
            if (s.x1.isNaN() || s in sinking || s.chalk) return@fastEach
            var wet = false
            s.groups.fastEach { g ->
                if (!wet && g.body != null) g.segs.fastEach { sg ->
                    if (!wet && (inPool(g.worldX(sg.lax, sg.lay), g.worldY(sg.lax, sg.lay)) || inPool(g.worldX(sg.lbx, sg.lby), g.worldY(sg.lbx, sg.lby)))) wet = true
                }
            }
            if (!wet) return@fastEach
            sinking += s
            sinkAt.add(simT + POOL_SINK_S)
        }
        for (i in sinking.indices.reversed()) {
            if (simT < sinkAt[i]) continue
            val st = sinking.removeAt(i)
            sinkAt.removeIndex(i)
            if (st !in ink.strokes) continue
            val p = st.groups.firstOrNull { it.body != null }?.body?.position
            if (p != null) events.burst(p.x, p.y, 12, 2f, Tok.BLOT)
            events.sound(Sfx.ERASE)
            ink.removeStroke(st)
            if (state == GameState.RUN) events.toast("The ink pool swallowed it!", force = true)
        }
    }

    /**
     * The ink tide: once the runner passes its start it rises [TIDE_RISE] behind and closes in to
     * settle [TIDE_GAP] behind a runner at full speed: further back it runs faster, nearer it
     * slows ([TIDE_PULL] of the run speed a metre), so a clean run keeps it at bay and a stall of
     * a second or two lets it catch up. It holds still while a first-timer waits at a gate. Ink
     * and creatures behind its front go under. Past its end it breaks and drains.
     */
    private fun updateTide(px: Float, dt: Float) {
        tides.fastEach { t ->
            if (!t.up) {
                if (px > t.x0 && px < t.x1) {
                    t.x = px - TIDE_RISE
                    t.prevX = t.x
                    events.toast("The ink tide is rising! Keep rolling.", force = true)
                    events.sound(Sfx.RUMBLE)
                    events.shake(0.2f)
                }
                return@fastEach
            }
            if (!t.ebbAt.isNaN()) {
                t.x -= TIDE_EBB * dt
                return@fastEach
            }
            if (px >= t.x1) {
                t.ebbAt = simT
                return@fastEach
            }
            if (waitingAt != null || runnerHeld) return@fastEach
            val pace = (1f + (px - t.x - TIDE_GAP) * TIDE_PULL).coerceIn(TIDE_PACE_MIN, TIDE_PACE_MAX)
            t.x += runSpeed * pace * dt
            if (px - runner.r < t.x) {
                events.burst(px, runner.body.position.y, 18, 3f, Tok.BLOT)
                die("Swallowed by the tide", DeathKind.TIDE)
                return@fastEach
            }
            blots.fastEach { b ->
                if (!b.dead && b.body.position.x < t.x) {
                    b.gone = true
                    ink.destroyBody(b.body)
                }
            }
        }
    }

    /** Touched crumbling bricks drop away a moment later, faster and faster. */
    private fun updateCrumbles(dt: Float) {
        crumbles.fastEach { c ->
            if (c.gone) return@fastEach
            if (!c.falling) {
                if (c.touchedAt.isNaN() || simT - c.touchedAt < CRUMBLE_DELAY) return@fastEach
                c.falling = true
                events.burst(c.x + c.w / 2f, c.y + c.h, 6, 1.2f, Tok.GRAPHITE)
            }
            val v = c.body.linearVelocity
            c.body.setLinearVelocity(0f, v.y + InkPhysics.GRAVITY * dt)
            if (c.body.position.y > c.y + 30f) {
                c.gone = true
                ink.destroyBody(c.body)
            }
        }
    }

    /** Beams that fell on a Blot (rather than one it walked into) and boulders squash it. */
    private fun crushBlots() {
        for (i in blotHits.indices) {
            val bs = boss
            if (bs != null && !bs.down && bs.body === blotHits[i]) {
                // a falling beam (or a boulder) hurts the bosses that a beam can
                val byB = blotHitBy[i]
                val boulder = (byB.fixtureList.first().userData as? FixtureTag)?.kind == FixtureKind.BOULDER
                // (a beam that hit a boss stays: lying there, it turns the boss round)
                if ((boulder || blotHitVy[i] > 0.5f) && bs.kind != BossKind.BIG_SMUDGE && bs.kind != BossKind.AIRLINER) hitBoss(bs, "Beam drop!")
                continue
            }
            val b = blots.firstOrNull { !it.dead && it.body === blotHits[i] } ?: continue
            val by = blotHitBy[i]
            val boulder = (by.fixtureList.first().userData as? FixtureTag)?.kind == FixtureKind.BOULDER
            if (boulder || blotHitVy[i] > 0.5f) {
                squash(b, if (boulder) "Squashed! +$BLOT_POINTS" else "Beam drop! +$BLOT_POINTS")
                if (!boulder) crumble(by)
            }
        }
    }

    /** [atRunner]: the score pops over the runner (it rolled through, and may be far across the view). */
    private fun squash(b: Blot, label: String, atRunner: Boolean = false) {
        val q = b.body.position
        b.sx = q.x
        b.sy = q.y
        val popX = if (atRunner) runner.body.position.x else b.sx
        b.squashedAt = simT
        ink.destroyBody(b.body)
        squashed++
        score += BLOT_POINTS
        happyUntil = simT + HAPPY_S
        events.burst(b.sx, b.sy, 14, 2.6f, Tok.INK)
        events.pop(popX, b.sy - 1.2f, label, Tok.ACCENT)
        events.sound(Sfx.SQUASH)
        events.shake(0.1f)
    }

    /** The runner bumped a doodle box from below: it pops what it holds, once. */
    private fun hitBox() {
        val tag = pendingBox ?: return
        val box = boxes.firstOrNull { it.tag === tag } ?: return
        val rb = runner.body
        val p = rb.position
        val r = box.rect
        if (p.y < r.bottom - 0.1f) return // landed on top or met its side
        val off = kotlin.math.abs(p.x - (r.x + r.w / 2f))
        if (off > r.w / 2f) {
            // met an underside corner: carried on forwards, or it would drop back onto the spring
            // under it and bounce into the corner for ever
            val v = rb.linearVelocity
            rb.setLinearVelocity(max(v.x, runSpeed * 0.95f), max(v.y, 1f))
        }
        if (box.used || off > r.w / 2f + runner.r) return
        box.used = true
        box.hitAt = simT
        val cx = r.x + r.w / 2f
        events.sound(Sfx.POP)
        events.burst(cx, r.y, 8, 1.8f, Tok.GRAPHITE)
        when (box.holds) {
            ItemKind.CAP, ItemKind.HIGHLIGHTER, ItemKind.CHALK, ItemKind.GOLD_NIB, ItemKind.STAR_NIB -> {
                val body = ink.addWalker(cx, r.y - CAP_R - 0.05f, CAP_R, 1f, FixtureTag.CAP)
                body.setLinearVelocity(CAP_SPEED, CAP_HOP)
                caps += Cap(body, box.holds)
                events.toast(
                    when (box.holds) {
                        ItemKind.CAP -> "A marker cap! Catch it."
                        ItemKind.HIGHLIGHTER -> "A highlighter! Catch it."
                        ItemKind.CHALK -> "Cloud chalk! Catch it."
                        ItemKind.GOLD_NIB -> "A gold nib! Catch it."
                        else -> "A star nib! Catch it."
                    },
                    force = true,
                )
            }
            else -> {
                // a box of stars: they spill out and count at once
                repeat(BOX_STARS) {
                    starStep = min(STAR_NOTES - 1, starStep + 1)
                    starsTaken++
                    score += STAR_POINTS
                    events.star()
                }
                lastStarT = simT
                events.sound(Sfx.STAR_TRAIL, starStep)
                events.burst(cx, r.y - 0.5f, 12, 2f, Tok.SPRING)
            }
        }
    }

    /** Caps slide away along the ground (turning at walls) until the runner catches one. */
    private fun updateCaps(px: Float, py: Float) {
        caps.fastEach { c ->
            if (c.taken) return@fastEach
            val body = c.body
            val q = body.position
            if (q.y > levelNear(q.x) + 10f) {
                c.taken = true
                ink.destroyBody(body)
                return@fastEach
            }
            val v = body.linearVelocity
            // it hops out of the box first; once down it slides, and hurries after a runner that
            // got ahead of it (the runner can't turn back)
            if (v.y >= 0f && kotlin.math.abs(v.y) < 0.5f) {
                val behind = q.x < px - 0.3f
                if (behind) c.dir = 1
                else if (kotlin.math.abs(v.x) < CAP_SPEED * 0.3f) c.dir = -c.dir
                body.setLinearVelocity(c.dir * if (behind) runSpeed + CAP_CATCH_UP else CAP_SPEED, v.y)
            }
            if (hypot(q.x - px, q.y - py) > runner.r + CAP_R + 0.1f || state != GameState.RUN) return@fastEach
            c.taken = true
            val qx = q.x
            val qy = q.y
            ink.destroyBody(body)
            applyPower(c.kind, qx, qy)
        }
    }

    /**
     * Learning: the next gate the runner will wait at (however far ahead). What lies past it
     * isn't introduced yet.
     */
    val pendingGate: Gate?
        get() = if (!learning) null else gates.firstOrNull { !it.waitX.isNaN() && !it.done && !it.passed }

    /** Learning: the gate ahead that the runner has to stop and wait at, if any. */
    private fun gateToWaitAt(rx: Float): Gate? {
        if (!learning) return null
        return gates.firstOrNull { !it.waitX.isNaN() && !it.done && !it.passed && rx < it.x1 && rx > it.waitX - WAIT_FROM }
    }

    /**
     * The gate a stroke begun at [x] is for, if the runner hasn't passed it: a tuning gate (any
     * time the pen is untuned), or one the runner will wait at (while learning).
     */
    private fun gateAt(x: Float): Gate? {
        val rx = runner.body.position.x
        return gates.firstOrNull {
            (it.tunes || (learning && !it.waitX.isNaN() && !it.done)) &&
                !it.passed && rx < it.x1 && x >= it.x0 - GATE_SLACK && x <= it.x1 + GATE_SLACK
        }
    }

    /**
     * Null if stroke [s] of [kind] does [g]'s job, or a tip for the player if not. A spring hint
     * wants a spring lying on the ground along the guide; the others a bridge hooked on both
     * sides of the gap (a beam hint, a beam: rope and springs don't reach that far).
     */
    private fun missTip(g: Gate, s: Stroke, kind: Ink): String? {
        if (g.forbid != null && kind == g.forbid) {
            return if (kind == Ink.SPRING) "Not a spring here: it bounces you into the spikes. A light touch makes rope."
            else "Not ${kind.label} here. Try a lighter touch."
        }
        if (g.needs != null && kind != g.needs) {
            return when (g.needs) {
                Ink.ROPE -> "That was ${kind.label}: only rope lets the boulder drop. A light touch."
                Ink.BEAM -> "That was ${kind.label}: beetles eat rope and springs. Press hard for a beam."
                else -> "That was ${kind.label}. Write normally for a spring."
            }
        }
        // a crush gate is done when the creature under it is squashed, whatever the stroke looked like
        if (g.crush) return null
        if (g.ink == Ink.SPRING) {
            val ground = max(g.gy0, g.gy1) + 0.1f
            var top = Float.POSITIVE_INFINITY
            var low = Float.NEGATIVE_INFINITY
            s.segs.fastEach { top = min(top, min(it.ay, it.by)); low = max(low, max(it.ay, it.by)) }
            val onGround = s.anchors.isNotEmpty() && low > ground - 0.6f && top > ground - 1.2f
            val along = s.x1 >= g.gx0 - g.slack && s.x0 <= g.gx1 + g.slack / 2f
            if (!onGround || !along) return TIP_PAD
            if (kind != Ink.SPRING) return "That was ${kind.label}. For a spring, write normally: not too light, not too hard."
            return null
        }
        val left = s.anchors.any { it.x <= g.gx0 + EDGE_REACH }
        val right = s.anchors.any { it.x >= g.gx1 - EDGE_REACH }
        if (!left || !right) return TIP_BRIDGE
        if (g.ink == Ink.BEAM && kind != Ink.BEAM) return "That was ${kind.label}: it can't hold over a gap that long. Press hard for a beam."
        return null
    }

    /**
     * The face: hurt, then scared of a boulder behind, wide-eyed at an edge with nothing ahead,
     * whee on a bounce and dizzy after it, a grin after anything good.
     */
    private fun updateMood() {
        val p = runner.body.position
        mood = when {
            state == GameState.DEAD || simT < runner.safeUntil || simT - ouchAt < 0.4f -> Mood.OUCH
            state == GameState.CLEAR -> Mood.HAPPY
            boulders.any { val bx = it.body.position.x; bx < p.x && p.x - bx < CHASE_VIEW } -> Mood.SCARED
            simT - bounceAt < 1.5f -> Mood.HAPPY
            simT - bounceAt < 2.4f -> Mood.DIZZY
            edgeAhead(p.x, p.y) -> Mood.WORRIED
            simT < happyUntil || combo >= 4 -> Mood.HAPPY
            else -> Mood.CALM
        }
    }

    /** On the ground with no ground and no ink just ahead: a gap edge. */
    private fun edgeAhead(px: Float, py: Float): Boolean {
        if (kotlin.math.abs(runner.body.linearVelocity.y) > 0.6f) return false
        val ax = px + runner.r + 1.2f
        val feet = py + runner.r
        blocks.fastEach { b -> if (ax >= b.x && ax <= b.right && b.topAt(ax) < feet + 1.5f && b.topAt(ax) > feet - 1.5f) return false }
        lifts.fastEach { if (kotlin.math.abs(it.body.position.x - ax) < it.def.w / 2f + 0.5f) return false }
        seesaws.fastEach { if (kotlin.math.abs(it.px - ax) < it.len / 2f) return false }
        ink.strokes.fastEach { s -> if (!s.x1.isNaN() && ax >= s.x0 - 0.3f && ax <= s.x1 + 0.3f) return false }
        val d = drawing
        if (d != null && ax >= d.x0 && ax <= d.x1) return false
        return true
    }

    private fun liftMoving(): Boolean {
        for (i in lifts.indices) if (lifts[i].dir != 0) return true
        return false
    }

    /** Checkpoint and finish line of a stage. */
    private fun stageProgress(px: Float) {
        val run = stageRun ?: return
        val l = run.def.layout
        // saved once the hazard just before it has been judged (judging lags 0.6 m), still
        // short of the respawn point 1 m past it
        val next = run.reached + 1
        if (next < l.checkpoints.size && px > l.checkpoints[next].x + 0.6f) {
            run.save(this, next)
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
        stars.removeAll { it.taken || it.x <= lim }
        golds.removeAll { it.taken || it.x <= lim }
        blots.removeAll { b ->
            if (b.dead) b.sx < lim
            else (b.body.position.x < lim).also { if (it) ink.destroyBody(b.body) }
        }
        caps.removeAll { c -> c.taken || (c.body.position.x < lim).also { if (it) ink.destroyBody(c.body) } }
        boxes.removeAll { b -> (b.rect.right < lim).also { if (it) ink.removeRect(b.rect) } }
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
        /** A view narrower than this (metres) is a portrait one: the runner sits further left. */
        const val NARROW_VIEW = 18f
        /** A chasing boulder this close behind is kept on screen... */
        const val CHASE_VIEW = 14f
        /** ...with the runner at most this far across. */
        const val CHASE_FRAC = 0.5f
        const val BOULDER_R = 0.9f
        const val DROP_INK = 12f
        const val HIGH_DROP_INK = 25f
        const val COMBO_MAX = 9
        const val RESTART_DELAY = 0.7
        /** Slice 1. Stars: pickup radius, points, the streak window and its notes. */
        const val STAR_R = 0.32f
        const val STAR_POINTS = 20
        const val STREAK_S = 0.9f
        const val STAR_NOTES = 8
        /** Gold stars: pickup radius and points. */
        const val GOLD_R = 0.5f
        const val GOLD_POINTS = 250
        /** Every this many stars is an extra life. */
        const val STARS_PER_LIFE = 50
        /** Blots: size, walking speed, how near they wake, points, and the stomp's hop (m/s, up). */
        const val BLOT_R = 0.38f
        const val BLOT_SPEED = 1.1f
        const val BLOT_WAKE = 11f
        const val BLOT_POINTS = 100
        const val STOMP_VY = -6.5f
        /** A stomp reaches this much further than a touch. */
        const val STOMP_SLACK = 0.3f
        /** A spring bounce (about 1.9 s in the air) squashes any Blot met this long after it. */
        const val BOUNCE_STOMP_S = 2.3f
        /** The marker cap and Bold: cap size, slide speed and hop, points, Bold's radius and the safe time after a hit. */
        const val CAP_R = 0.24f
        const val CAP_SPEED = 2.2f
        const val CAP_HOP = -4f
        /** Behind the runner, a cap slides this much faster than it (m/s). */
        const val CAP_CATCH_UP = 1.5f
        const val CAP_POINTS = 500
        const val BOLD_R = 0.52f
        const val SAFE_S = 1.5f
        /** A doodle box of stars holds this many. */
        const val BOX_STARS = 5
        /** How long the face grins after something good. */
        const val HAPPY_S = 1.2f
        /** Boss arenas: wall height and thickness, how far in the runner is when the near wall closes, where it turns. */
        const val ARENA_WALL = 12f
        const val ARENA_WALL_W = 0.8f
        const val ARENA_ENTER = 1.5f
        const val TURN_GAP = 0.15f
        /** Bosses: density, seconds of a hit's flashing, seconds between moves, points per hit and for the win. */
        const val BOSS_DENSITY = 2f
        const val BOSS_HURT_S = 1.2f
        const val BOSS_ACT_S = 3.5f
        const val BOSS_HIT_POINTS = 200
        const val BOSS_POINTS = 1000
        /** The Airliner flies this high; a spring throws the Big Smudge up at this speed (m/s, up). */
        const val AIRLINER_UP = 4.4f
        const val SMUDGE_LAUNCH = -15f
        /** A stroke begun this far either side of a tuning gate still belongs to it. */
        const val GATE_SLACK = 1.5f
        /** Timed powers last this long; a highlighter swipe finds things this near it and fades over SWIPE_S. */
        const val POWER_S = 9f
        const val HIGHLIGHT_R = 1.1f
        const val SWIPE_S = 1.6f
        /** Paper planes: size, speed (a flight's and a dive's), where a flight starts ahead of its mark, points. */
        const val PLANE_R = 0.28f
        const val PLANE_SPEED = 4f
        const val PLANE_DIVE = 4.5f
        const val PLANE_START = 16f
        const val PLANE_POINTS = 50
        /** Crumbling bricks: thickness and how long after a touch they fall. */
        const val CRUMBLE_H = 0.5f
        const val CRUMBLE_DELAY = 0.45f
        /** A beetle hops up to rope or a spring this far above it. */
        const val BEETLE_REACH = 7f
        /** A beam that crushed something crumbles this long after. */
        const val CRUMBLE_S = 0.8f
        /** A beetle eats the rope or spring under it in this long. */
        const val BITE_S = 0.9f
        /** Ink pools: a stroke dipping in is gone this long after, and the runner sinks this far in first. */
        const val POOL_SINK_S = 0.35f
        const val POOL_GRACE = 0.1f
        /**
         * The ink tide: how far behind it rises, how far behind it settles, how its pace (a share of
         * the run speed) changes per metre off that and its limits, how fast it drains, and how far
         * across the view the runner may slide so it stays in sight.
         */
        const val TIDE_RISE = 9f
        const val TIDE_GAP = 5f
        const val TIDE_PULL = 0.08f
        const val TIDE_PACE_MIN = 0.6f
        const val TIDE_PACE_MAX = 1.25f
        const val TIDE_EBB = 4f
        const val TIDE_FRAC = 0.4f
        /** A learning runner brakes at this rate (m/s²) from this far (m) before a gate's wait point. */
        const val WAIT_DECEL = 3f
        const val WAIT_FROM = 8f
        /** After this long waiting with nothing drawn, a nudge. */
        const val NUDGE_S = 7f
        /** A bridge's hook counts for a side if it is within this of that side's end of the guide. */
        const val EDGE_REACH = 0.9f
        const val TIP_IDLE = "Draw along the dotted line with the S Pen."
        const val TIP_BRIDGE = "That one fell. Start on the ground on this side and end on the ground on the far side."
        const val TIP_PAD = "Lay the spring flat on the ground, along the dotted line."
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
            DeathKind.BLOT to listOf(
                "Bounce on Blots: a spring just before one.",
                "Drop a beam on a Blot from above.",
            ),
            DeathKind.BOSS to listOf("Watch what hurts it, and spring clear of it."),
            DeathKind.ERASER to listOf("A beam wall stops an Eraser. Rope over a gap drops it."),
            DeathKind.PLANE to listOf("A beam roof stops paper planes. Or spring over them."),
            DeathKind.BEETLE to listOf("Beetles can't bite beams. Race them, or bounce on them."),
            DeathKind.TIDE to listOf("Keep moving: the tide never stops.", "Draw ahead: every stall lets the tide catch up."),
            DeathKind.POOL to listOf("Ink pools swallow ink too. Keep bridges tight, above the ink.", "A beam doesn't sag into the pool."),
        )
    }
}
