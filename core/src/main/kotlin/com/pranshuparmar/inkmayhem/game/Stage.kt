package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink

/** How a stage's page looks: the paper and how blocks are drawn. */
enum class Theme { PAGE, CELLAR, SKY, CASTLE, MIDNIGHT }

/**
 * A hand-built stage: World [world]-[number], always the same layout so it can be learned.
 * [build] lays it out with a [StageBuilder]; [layout] is built once and shared.
 */
class StageDef(
    val world: Int,
    val number: Int,
    val name: String,
    val theme: Theme,
    /** Run speed in m/s for the whole stage (Endless ramps 3.4 → 6). */
    val speed: Float,
    private val build: StageBuilder.() -> Unit,
) {
    val id: String = "$world-$number"
    val layout: StageLayout by lazy { StageLayout().also { StageBuilder(it).build() } }
}

/** Everything in a stage, in world metres (y down), before any physics exists. */
class StageLayout {
    /**
     * [hidden]: only solid once a highlighter finds it; [bonus]: part of a bonus page far below;
     * [cloud]: drawn as a cloud, and [bouncy] ones launch the runner like a spring.
     */
    class Block(
        val x: Float, val top: Float, val w: Float, val depth: Float, val topR: Float = top,
        val hidden: Boolean = false, val bonus: Boolean = false, val cloud: Boolean = false, val bouncy: Boolean = false,
    ) {
        val right: Float get() = x + w
    }

    /** Rock overhead with its underside at [bottom], rising out of sight; spiked or plain. */
    class Rock(val x: Float, val w: Float, val bottom: Float, val spiked: Boolean)

    class Spikes(val x: Float, val y: Float, val w: Float, val up: Boolean)

    /**
     * A lift: a platform [w] wide whose top moves between [lowY] and [highY]. With [waitForRunner]
     * it stays at [lowY] until the runner lands on it, rises once and stays up; otherwise it
     * travels up and down for ever, pausing at each end.
     */
    class Lift(val x: Float, val w: Float, val lowY: Float, val highY: Float, val speed: Float, val waitForRunner: Boolean)

    /** A plank [len] long pinned at its centre, tipping between [minAngle] and [maxAngle] (radians, + = clockwise). */
    class Seesaw(val cx: Float, val cy: Float, val len: Float, val angle: Float, val minAngle: Float, val maxAngle: Float)

    /** [hidden] ones only show (and can only be taken) once a highlighter stroke finds them. */
    class Item(val x: Float, val y: Float, val kind: ItemKind, val hidden: Boolean = false)

    class Text(val x: Float, val y: Float, val text: String, val maxW: Float)

    class Hazard(val type: HazardType, val x0: Float, val x1: Float, val par: Ink)

    class Runway(val x: Float, val level: Float, val triggerX: Float)

    /** A Blot standing on the ground at [x]; it paces between [x0] and [x1] (NaN: no limits). */
    class BlotSpec(val x: Float, val y: Float, val x0: Float, val x1: Float, val kind: Creature = Creature.BLOT)

    /** A flight of [count] paper planes, [gap] apart, flying in at [up] metres above the ground once the runner passes [x]. */
    class Flight(val x: Float, val count: Int, val gap: Float, val up: Float, val ground: Float)

    /** An inkwell at ([x], [y]) dropping to ([toX], [toY]; NaN: the page's ground there). */
    class WarpSpec(val x: Float, val y: Float, val toX: Float, val toY: Float, val hidden: Boolean, val exit: Boolean)

    /** A doodle box with its top-left at ([x], [top]), [size] square, holding [holds]. */
    class Box(val x: Float, val top: Float, val size: Float, val holds: ItemKind, val hidden: Boolean = false)

    class ArenaSpec(val x0: Float, val x1: Float, val level: Float, val kind: BossKind, val ceiling: Float)

    class GateSpec(
        val ink: Ink, val x0: Float, val x1: Float, val gx0: Float, val gy0: Float, val gx1: Float, val gy1: Float,
        val label: String, val tunes: Boolean, val waitX: Float, val slack: Float,
        val forbid: Ink? = null, val crush: Boolean = false, val needs: Ink? = null,
    )

    /** A row of crumbling bricks, each [w] wide, their tops at [y]. */
    class CrumbleRow(val x: Float, val y: Float, val n: Int, val w: Float)

    /** An ink pool from [x0] to [x1], its surface at [surface]: it swallows the runner, ink and creatures. */
    class Pool(val x0: Float, val x1: Float, val surface: Float)

    /** The ink tide: it rises behind the runner once it passes [x0] and chases it until [x1]. */
    class TideSpec(val x0: Float, val x1: Float)

    /** One stroke of the scripted solution, drawn once the runner reaches [atX]. */
    class Stroke(val atX: Float, val xs: FloatArray, val ys: FloatArray, val ink: Ink)

    val blocks = ArrayList<Block>()
    val rocks = ArrayList<Rock>()
    val spikes = ArrayList<Spikes>()
    val lifts = ArrayList<Lift>()
    val seesaws = ArrayList<Seesaw>()
    val items = ArrayList<Item>()
    val notes = ArrayList<Text>()
    val hazards = ArrayList<Hazard>()
    val runways = ArrayList<Runway>()
    val blots = ArrayList<BlotSpec>()
    val boxes = ArrayList<Box>()
    val gates = ArrayList<GateSpec>()
    val arenas = ArrayList<ArenaSpec>()
    val warps = ArrayList<WarpSpec>()
    val flights = ArrayList<Flight>()
    val crumbles = ArrayList<CrumbleRow>()
    val pools = ArrayList<Pool>()
    val tides = ArrayList<TideSpec>()
    val solution = ArrayList<Stroke>()

    /** A bookmark: once passed, a retry starts 1 m past it. */
    class Checkpoint(val x: Float, val level: Float)

    var startX = 0f
    /** In order along the stage. */
    val checkpoints = ArrayList<Checkpoint>()
    /** The last checkpoint (the midway one), or NaN without any. */
    val checkpointX: Float get() = checkpoints.lastOrNull()?.x ?: Float.NaN
    var finishX = Float.NaN
    /** The finish flag stands on this level. */
    var finishLevel = 0f

    /** Top of the highest solid surface of the page under [x] (blocks only, not hidden or bonus ones), or NaN over a gap. */
    fun groundAt(x: Float): Float {
        var best = Float.NaN
        for (b in blocks) {
            if (x < b.x || x > b.right || b.hidden || b.bonus) continue
            val t = if (b.topR == b.top) b.top else b.top + (b.topR - b.top) * (x - b.x) / b.w
            if (best.isNaN() || t < best) best = t
        }
        return best
    }
}

/** What a stage holds. GOLD is one of its three hidden gold stars; the last four only come out of doodle boxes. */
enum class ItemKind { DROP, HIGH_DROP, HEART, STAR, GOLD, CAP, HIGHLIGHTER, CHALK, GOLD_NIB, STAR_NIB }

/**
 * Lays out a stage left to right. [x] is the cursor and [level] the ground height there (y down,
 * so going up means a smaller level). Pieces that need ink also record a scripted solution, so
 * tests can play every stage through.
 */
class StageBuilder(private val l: StageLayout) {
    var x = 0f
        private set
    var level = START_LEVEL
        private set
    /** How deep new ground goes: [DEPTH] is solid to the bottom of the page; sky islands are shallow. */
    var depth = DEPTH

    // ------------------------------------------------------------------- ground

    /** Solid ground from the cursor for [len] metres. */
    fun flat(len: Float): StageBuilder {
        l.blocks += StageLayout.Block(x, level, len, depth)
        x += len
        return this
    }

    /** A hill side: the ground rises by [rise] (falls if negative) over [len] metres. */
    fun slope(len: Float, rise: Float): StageBuilder {
        l.blocks += StageLayout.Block(x, level, len, depth + maxOf(0f, rise), level - rise)
        x += len
        level -= rise
        return this
    }

    /** A gap to bridge; [solve] is the ink the scripted solution uses (par by default). */
    fun gap(len: Float, type: HazardType = HazardType.GAP_M, par: Ink = type.par!!, solve: Ink = par, lead: Float = 8f): StageBuilder {
        val x0 = x
        x += len
        l.hazards += StageLayout.Hazard(type, x0, x, par)
        bridge(x0, level, x, level, solve, lead)
        return this
    }

    /** A gap with nothing scripted: the caller adds its own solution. */
    fun openGap(len: Float, type: HazardType = HazardType.GAP_M, par: Ink = type.par!!): StageBuilder {
        val x0 = x
        x += len
        l.hazards += StageLayout.Hazard(type, x0, x, par)
        return this
    }

    /** A wall [up] metres high at the cursor; the solution writes a spring just before it. */
    fun wall(up: Float): StageBuilder {
        l.hazards += StageLayout.Hazard(HazardType.WALL, x, x + 1f, Ink.SPRING)
        spring(x - 3.2f, x - 2.4f, level)
        level -= up
        return this
    }

    /** The ground steps down [down] metres at the cursor (nothing to draw). */
    fun stepDown(down: Float): StageBuilder {
        level += down
        return this
    }

    /** Moves the cursor without adding ground (open air: the caller builds what's there). */
    fun skip(len: Float): StageBuilder {
        x += len
        return this
    }

    fun setLevel(y: Float): StageBuilder {
        level = y
        return this
    }

    /** A floating ledge [thick] deep with its top at [top], from [x0] for [len] metres. */
    fun ledge(x0: Float, top: Float, len: Float, thick: Float = LEDGE_THICK) {
        l.blocks += StageLayout.Block(x0, top, len, thick)
    }

    /** A pillar from deep below up to [top] (the walls of shafts and the posts of seesaws). */
    fun pillar(x0: Float, top: Float, len: Float) {
        l.blocks += StageLayout.Block(x0, top, len, DEPTH + 20f)
    }

    // --------------------------------------------------------------- set pieces

    /** A spiked ceiling over a gap, like Endless's tunnel: rope underneath, never a spring. */
    fun tunnel(gapLen: Float, overhang: Float = 3.5f): StageBuilder {
        l.rocks += StageLayout.Rock(x - overhang, gapLen + 2f * overhang, level - 2.1f, spiked = true)
        return gap(gapLen, HazardType.TUNNEL)
    }

    /** Plain rock overhead from the cursor for [len] metres, its underside [clear] above the ground. */
    fun cave(len: Float, clear: Float) {
        l.rocks += StageLayout.Rock(x, len, level - clear, spiked = false)
    }

    fun spikes(x0: Float, y: Float, len: Float, up: Boolean = true) {
        l.spikes += StageLayout.Spikes(x0, y, len, up)
    }

    fun lift(x0: Float, w: Float, lowY: Float, highY: Float, speed: Float = 1.6f, waitForRunner: Boolean = true) {
        l.lifts += StageLayout.Lift(x0, w, lowY, highY, speed, waitForRunner)
    }

    fun seesaw(cx: Float, cy: Float, len: Float, angle: Float, minAngle: Float, maxAngle: Float) {
        l.seesaws += StageLayout.Seesaw(cx, cy, len, angle, minAngle, maxAngle)
    }

    /** A boulder runway from the cursor: one starts rolling 10 m behind the runner at +10.5 m. */
    fun runway(len: Float): StageBuilder {
        l.runways += StageLayout.Runway(x, level, x + 10.5f)
        return flat(len)
    }

    /** A gap over a pit [deep] metres down with spikes on its floor; rope across by default. */
    fun spikePit(len: Float, deep: Float = 3f, type: HazardType = HazardType.GAP_M, par: Ink = type.par!!, solve: Ink = par): StageBuilder {
        ledge(x, level + deep, len, DEPTH) // a solid floor, not a shelf over nothing
        spikes(x, level + deep, len)
        return gap(len, type, par, solve)
    }

    /**
     * A shaft down: the ground ends, spikes wait far below, and the way on is [drop] metres
     * lower across [width] metres. A rope drawn from edge to edge is a slide.
     */
    fun slide(width: Float = 5f, drop: Float = 4f): StageBuilder {
        val x0 = x
        val l0 = level
        ledge(x0, l0 + 10f, width, DEPTH)
        spikes(x0, l0 + 10f, width)
        openGap(width, HazardType.GAP_M, Ink.ROPE)
        bridge(x0, l0, x0 + width, l0 + drop, Ink.ROPE)
        level += drop
        return this
    }

    /** A gap to ground [drop] metres lower: a sloping bridge. */
    fun gapDown(len: Float, drop: Float, solve: Ink = Ink.ROPE): StageBuilder {
        val x0 = x
        val l0 = level
        openGap(len, HazardType.GAP_S, Ink.ROPE)
        bridge(x0, l0, x0 + len, l0 + drop, solve)
        level += drop
        return this
    }

    /** [steps] walls of [up] metres, [stepLen] apart: a staircase of springs. */
    fun climb(steps: Int, up: Float = 2.4f, stepLen: Float = 7.5f): StageBuilder {
        repeat(steps) {
            wall(up)
            flat(stepLen)
        }
        return this
    }

    /** A lift in a [width]-metre slot carries the runner [rise] metres up a wall. */
    fun liftUp(width: Float = 3f, rise: Float = 5f): StageBuilder {
        lift(x, width, level, level - rise)
        x += width
        level -= rise
        return this
    }

    /** A seesaw across a [len]-metre gap, resting on the near edge; optional spikes below. */
    fun seesawGap(len: Float = 6f, spikesBelow: Boolean = false): StageBuilder {
        val half = (len + 0.6f) / 2f
        val a = -kotlin.math.asin(SEESAW_LIFT / half)
        seesaw(x + len / 2f, level - SEESAW_LIFT, len + 0.6f, a, a - 0.01f, -a + 0.01f)
        if (spikesBelow) {
            ledge(x, level + 3f, len, DEPTH)
            spikes(x, level + 3f, len)
        }
        x += len
        return this
    }

    // ------------------------------------------------------------- collectibles

    /** An ink drop [up] metres above the ground at [x0] (0.75 sits it on the ground). */
    fun drop(x0: Float, up: Float = 0.75f) {
        l.items += StageLayout.Item(x0, ground(x0) - up, if (up > 2f) ItemKind.HIGH_DROP else ItemKind.DROP)
    }

    /** A row of [n] drops along the ground, [gap] apart, from [x0]. */
    fun drops(x0: Float, n: Int, gap: Float = 1.2f, up: Float = 0.75f) {
        for (i in 0 until n) drop(x0 + i * gap, up)
    }

    /** A drop at an absolute height (on ledges, in the air). */
    fun dropAt(x0: Float, y: Float, high: Boolean = false) {
        l.items += StageLayout.Item(x0, y, if (high) ItemKind.HIGH_DROP else ItemKind.DROP)
    }

    /** An extra life, usually somewhere you have to go out of your way for. */
    fun heart(x0: Float, y: Float) {
        l.items += StageLayout.Item(x0, y, ItemKind.HEART)
    }

    /** A star [up] metres above the ground at [x0]. */
    fun star(x0: Float, up: Float = 0.6f) {
        l.items += StageLayout.Item(x0, ground(x0) - up, ItemKind.STAR)
    }

    fun starAt(x0: Float, y: Float, hidden: Boolean = false) {
        l.items += StageLayout.Item(x0, y, ItemKind.STAR, hidden)
    }

    /**
     * One of the stage's three gold stars, at an absolute height: somewhere off the obvious line
     * (a high road, a risky bounce, a hidden spot a highlighter finds). Numbered in the order placed.
     */
    fun gold(x0: Float, y: Float, hidden: Boolean = false) {
        check(l.items.count { it.kind == ItemKind.GOLD } < 3) { "three gold stars a stage" }
        l.items += StageLayout.Item(x0, y, ItemKind.GOLD, hidden)
    }

    /** A row of [n] stars [gap] apart along the ground from [x0]. */
    fun stars(x0: Float, n: Int, gap: Float = 0.9f, up: Float = 0.6f) {
        for (i in 0 until n) star(x0 + i * gap, up)
    }

    /** [n] stars on a straight line from (x0, y0) to (x1, y1): across a bridge, say. */
    fun starLine(x0: Float, y0: Float, x1: Float, y1: Float, n: Int) {
        for (i in 0 until n) {
            val u = if (n == 1) 0.5f else i / (n - 1f)
            starAt(x0 + (x1 - x0) * u, y0 + (y1 - y0) * u)
        }
    }

    /**
     * [n] stars along a spring's bounce: launched at [x0] from [y0] at [vx] m/s across and 9.6 m/s up
     * (gravity 10), where the runner's centre will pass. A bounce carries on at about 2.75 m/s.
     */
    fun starArc(x0: Float, y0: Float, vx: Float = 2.75f, n: Int, from: Float = 0.25f, to: Float = 1.6f) {
        for (i in 0 until n) {
            val t = from + (to - from) * i / (n - 1f)
            starAt(x0 + vx * t, y0 - 9.6f * t + 5f * t * t)
        }
    }

    /**
     * A boss arena [len] metres long from the cursor, with [kind] waiting at its far end: flat
     * ground, a wall that closes behind the runner, and a far wall that opens once it's beaten.
     * The Big Smudge's has a spiked roof [ARENA_ROOF] up.
     */
    fun arena(kind: BossKind, len: Float = ARENA_LEN): StageBuilder {
        val roof = if (kind == BossKind.BIG_SMUDGE) level - ARENA_ROOF else Float.NaN
        if (!roof.isNaN()) l.rocks += StageLayout.Rock(x, len, roof, spiked = true)
        l.arenas += StageLayout.ArenaSpec(x, x + len, level, kind, roof)
        return flat(len)
    }

    /** A Blot on the ground at [x0], pacing [patrol] metres either way once it wakes (0: free). */
    fun blot(x0: Float, patrol: Float = 0f, kind: Creature = Creature.BLOT) {
        val g = ground(x0)
        l.blots += StageLayout.BlotSpec(x0, g, if (patrol > 0f) x0 - patrol else Float.NaN, if (patrol > 0f) x0 + patrol else Float.NaN, kind)
    }

    /** An Eraser on the ground at [x0]: it rubs out rope and springs it touches. */
    fun eraser(x0: Float, patrol: Float = 0f) = blot(x0, patrol, Creature.ERASER)

    /** An ink beetle on the ground at [x0]: it eats rope and spring bridges it crawls on. */
    fun beetle(x0: Float, patrol: Float = 0f) = blot(x0, patrol, Creature.BEETLE)

    /** A cloud to roll on, [len] long with its top at [top]; a [bouncy] one launches you like a spring. */
    fun cloud(x0: Float, top: Float, len: Float, bouncy: Boolean = false) {
        l.blocks += StageLayout.Block(x0, top, len, CLOUD_THICK, cloud = true, bouncy = bouncy)
    }

    /**
     * A flight of [count] paper planes at head height: once the runner passes [x0] they fly in from
     * ahead. A spring's bounce through them crumples them; so does spring or beam ink in their way.
     */
    fun planes(x0: Float, count: Int = 3, gap: Float = 1.4f, up: Float = 0.55f) {
        l.flights += StageLayout.Flight(x0, count, gap, up, ground(x0))
    }

    /** A ledge (or a bridge over a gap) that's only there once a highlighter finds it. */
    fun hiddenLedge(x0: Float, top: Float, len: Float, thick: Float = LEDGE_THICK) {
        l.blocks += StageLayout.Block(x0, top, len, thick, hidden = true)
    }

    /**
     * An inkwell at [x0] on the ground ([hidden] until highlighted): it drops the runner to a bonus
     * page of [stars] far below, [roomLen] long, whose own inkwell lifts it back up to the page at
     * [x0] + [back].
     */
    fun inkwell(x0: Float, back: Float, roomLen: Float = 18f, hidden: Boolean = true, stars: Int = 12) {
        val g = ground(x0)
        val ry = g + BONUS_DROP
        // the bonus page: a floor with a wall at each end, and stars along it
        l.blocks += StageLayout.Block(x0 - 3f, ry, roomLen + 5f, 6f, bonus = true)
        l.blocks += StageLayout.Block(x0 - 3.8f, ry - 8f, 0.8f, 14f, bonus = true)
        l.blocks += StageLayout.Block(x0 + roomLen + 2f, ry - 8f, 0.8f, 14f, bonus = true)
        for (i in 0 until stars) starAt(x0 + 1.5f + i * (roomLen - 3f) / (stars - 1), ry - 0.65f - (if (i % 4 == 1 || i % 4 == 2) 0.6f else 0f))
        l.warps += StageLayout.WarpSpec(x0, g, x0 + 0.6f, ry, hidden, exit = false)
        l.warps += StageLayout.WarpSpec(x0 + roomLen, ry, x0 + back, Float.NaN, false, exit = true)
    }

    /** A doodle box with its underside [up] metres above the ground at [x0] (a spring's bounce reaches it). */
    fun box(x0: Float, up: Float = 2.6f, holds: ItemKind = ItemKind.CAP, hidden: Boolean = false) {
        l.boxes += StageLayout.Box(x0 - BOX / 2f, ground(x0) - up - BOX, BOX, holds, hidden)
    }

    /**
     * A doodled hint over [x0]..[x1]: a dotted guide from (gx0, gy0) to (gx1, gy1) in [ink]'s colour,
     * labelled with the ink's name. A [tunes] gate also tunes an untuned pen, and the runner
     * waits at [waitX] until a stroke is drawn there (see [Gate]).
     */
    fun hint(
        ink: Ink, x0: Float, x1: Float, gx0: Float, gy0: Float, gx1: Float, gy1: Float,
        tunes: Boolean = false, waitX: Float = Float.NaN, slack: Float = 0.8f,
        forbid: Ink? = null, crush: Boolean = false, label: String = ink.label, needs: Ink? = null,
    ) {
        l.gates += StageLayout.GateSpec(ink, x0, x1, gx0, gy0, gx1, gy1, label, tunes, waitX, slack, forbid, crush, needs)
    }

    /**
     * A bridge of crumbling bricks across a [len]-metre gap over spikes [deep] below: each brick
     * falls a moment after the runner rolls on, so keep rolling.
     */
    fun crumbleBridge(len: Float, deep: Float = 3f, brick: Float = 1.2f): StageBuilder {
        ledge(x, level + deep, len, DEPTH)
        spikes(x, level + deep, len)
        val n = maxOf(1, kotlin.math.round(len / brick).toInt())
        l.crumbles += StageLayout.CrumbleRow(x, level, n, len / n)
        x += len
        return this
    }

    /**
     * A beetle pit: a [len]-metre gap [deep] down with [beetles] ink beetles on its floor. They hop
     * up onto rope or spring bridges over it and eat them; beams they can't bite.
     */
    fun beetlePit(len: Float, beetles: Int = 1, deep: Float = 5f, solve: Ink? = Ink.BEAM, lead: Float = 8f): StageBuilder {
        val x0 = x
        ledge(x0, level + deep, len, DEPTH)
        for (i in 0 until beetles) l.blots += StageLayout.BlotSpec(x0 + len * (i + 1) / (beetles + 1), level + deep, x0 + 0.4f, x0 + len - 0.4f, Creature.BEETLE)
        openGap(len, HazardType.GAP_M, par = Ink.BEAM)
        if (solve != null) bridge(x0, level, x0 + len, level, solve, lead)
        return this
    }

    /**
     * An ink pool across a [len]-metre gap, its surface [down] metres below the ground: it swallows
     * whatever touches it, the runner, ink and creatures alike. The far side may be [drop] metres
     * lower (a sloping bridge). Rope across by default; [solve] null scripts nothing.
     */
    fun inkPool(
        len: Float, drop: Float = 0f, down: Float = POOL_DOWN, type: HazardType = HazardType.GAP_M,
        par: Ink = type.par!!, solve: Ink? = par, lead: Float = 8f,
    ): StageBuilder {
        val x0 = x
        val l0 = level
        l.pools += StageLayout.Pool(x0, x0 + len, l0 + maxOf(0f, drop) + down)
        openGap(len, type, par)
        level += drop
        if (solve != null) bridge(x0, l0, x, level, solve, lead)
        return this
    }

    /** Just an ink pool's surface, [len] from [x0], [down] below the ground there: under a seesaw, say. */
    fun pool(x0: Float, len: Float, down: Float = POOL_DOWN) {
        l.pools += StageLayout.Pool(x0, x0 + len, ground(x0) + down)
    }

    /**
     * An ink pool [len] wide whose far bank stands [up] metres higher: spring across it from the
     * near edge (the solution's spring lies just before it).
     */
    fun poolWall(len: Float = 2.5f, up: Float = 2.4f, lead: Float = 8f): StageBuilder {
        l.pools += StageLayout.Pool(x, x + len, level + POOL_DOWN)
        l.hazards += StageLayout.Hazard(HazardType.WALL, x, x + len, Ink.SPRING)
        spring(x - 1f, x - 0.2f, level, lead)
        x += len
        level -= up
        return this
    }

    private var tideFrom = Float.NaN

    /** The ink tide rises behind the runner from here (see [tideEnd]). */
    fun tideStart(): StageBuilder {
        tideFrom = x
        return this
    }

    /** The tide that rose at [tideStart] breaks here and drains away. */
    fun tideEnd(): StageBuilder {
        check(!tideFrom.isNaN()) { "tideEnd() without tideStart()" }
        l.tides += StageLayout.TideSpec(tideFrom, x)
        tideFrom = Float.NaN
        return this
    }

    fun note(text: String, dx: Float = 0.6f, up: Float = 3.4f, maxW: Float = Float.POSITIVE_INFINITY) {
        l.notes += StageLayout.Text(x + dx, level - up, text, maxW)
    }

    // ------------------------------------------------------------------ markers

    fun start(len: Float): StageBuilder {
        l.startX = x + 2f
        return flat(len)
    }

    /** A bookmark: retries start here once it's passed. Put it where flat ground follows. */
    fun checkpoint(): StageBuilder {
        l.checkpoints += StageLayout.Checkpoint(x, level)
        return this
    }

    /** The finish flag at the cursor, then some ground to roll out on. */
    fun finish(runOut: Float = 16f): StageBuilder {
        l.finishX = x
        l.finishLevel = level
        return flat(runOut)
    }

    // --------------------------------------------------------------- solutions

    /** Scripted solution: a straight stroke from (x0, y0) to (x1, y1) in [ink], drawn when the runner is [lead] m away. */
    fun solve(x0: Float, y0: Float, x1: Float, y1: Float, ink: Ink, lead: Float = 8f) {
        val n = maxOf(2, (kotlin.math.hypot(x1 - x0, y1 - y0) / 0.05f).toInt() + 1)
        val xs = FloatArray(n) { x0 + (x1 - x0) * it / (n - 1) }
        val ys = FloatArray(n) { y0 + (y1 - y0) * it / (n - 1) }
        l.solution += StageLayout.Stroke(minOf(x0, x1) - lead, xs, ys, ink)
    }

    /** A bridge from one edge to another, just under the surface like the Endless bot draws it. */
    fun bridge(x0: Float, y0: Float, x1: Float, y1: Float, ink: Ink, lead: Float = 8f) =
        solve(x0, y0 - 0.05f, x1, y1 - 0.05f, ink, lead)

    /** Scripted solution: a short spring lying on the ground (following a slope) from [x0], [len] long. */
    fun springOn(x0: Float, len: Float = 0.8f, lead: Float = 8f) {
        val p = PRESSURE.getValue(Ink.SPRING)
        val off = com.pranshuparmar.inkmayhem.physics.Ink.thickness(p) / 2f + 0.04f
        solve(x0, ground(x0) - off, x0 + len, ground(x0 + len) - off, Ink.SPRING, lead)
    }

    /**
     * A gold star at the top of a spring's bounce from [x0] (about 2 m on and 4.6 m up), with the
     * spring in the solution. Land it on solid ground: a bounce snaps rope it lands on.
     */
    fun bounceGold(x0: Float, lead: Float = 5f) {
        springOn(x0, lead = lead)
        gold(x0 + 2f, ground(x0) - 4.6f)
    }

    /**
     * A ledge up off the ground, [len] long, reached by a spring bounce from [up] metres up: the
     * high road. The solution draws the spring 2.6 m before it.
     */
    fun highRoad(x0: Float, len: Float, up: Float = 3.2f, solve: Boolean = true) {
        val g = ground(x0)
        ledge(x0, g - up, len)
        if (solve) springOn(x0 - 2.6f)
    }

    /** Scripted solution: a highlighter swipe (drawn while the Highlighter is on) from (x0, y0) to (x1, y1). */
    fun swipe(x0: Float, y0: Float, x1: Float, y1: Float, lead: Float = 6f) = solve(x0, y0, x1, y1, Ink.ROPE, lead)

    /** A short spring lying on the ground from [x0] to [x1]. */
    fun spring(x0: Float, x1: Float, groundY: Float, lead: Float = 8f) {
        val p = PRESSURE.getValue(Ink.SPRING)
        val y = groundY - (com.pranshuparmar.inkmayhem.physics.Ink.thickness(p) / 2f + 0.04f)
        solve(x0, y, x1, y, Ink.SPRING, lead)
    }

    private fun ground(x0: Float): Float = l.groundAt(x0).let { if (it.isNaN()) level else it }

    companion object {
        const val START_LEVEL = 20f
        const val DEPTH = 40f
        const val LEDGE_THICK = 0.6f
        /** A seesaw's pivot sits this far above the ground on either side. */
        const val SEESAW_LIFT = 0.6f
        /** A doodle box's side. */
        const val BOX = 0.9f
        /** A boss arena's length, and the Big Smudge's spiked roof height. */
        const val ARENA_LEN = 13f
        const val ARENA_ROOF = 8.2f
        /** A cloud's thickness. */
        const val CLOUD_THICK = 0.7f
        /** An ink pool's surface lies this far below the ground either side. */
        const val POOL_DOWN = 1.5f
        /** A bonus page lies this far below the page's ground. */
        const val BONUS_DROP = 55f
        /** Mid-band pressure for each ink, as the Endless bot uses. */
        val PRESSURE = mapOf(Ink.ROPE to 0.2f, Ink.SPRING to 0.47f, Ink.BEAM to 0.8f)
    }
}
