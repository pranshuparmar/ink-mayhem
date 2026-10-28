package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink

/** How a stage's page looks: the paper and how blocks are drawn. */
enum class Theme { PAGE, CELLAR, SKY, CASTLE }

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
    class Block(val x: Float, val top: Float, val w: Float, val depth: Float, val topR: Float = top) {
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

    class Item(val x: Float, val y: Float, val kind: ItemKind)

    class Text(val x: Float, val y: Float, val text: String, val maxW: Float)

    class Hazard(val type: HazardType, val x0: Float, val x1: Float, val par: Ink)

    class Runway(val x: Float, val level: Float, val triggerX: Float)

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
    val solution = ArrayList<Stroke>()

    var startX = 0f
    var checkpointX = Float.NaN
    var finishX = Float.NaN
    /** The finish flag stands on this level. */
    var finishLevel = 0f
    var checkpointLevel = 0f

    /** Top of the highest solid surface under [x] (blocks only), or NaN over a gap. */
    fun groundAt(x: Float): Float {
        var best = Float.NaN
        for (b in blocks) {
            if (x < b.x || x > b.right) continue
            val t = if (b.topR == b.top) b.top else b.top + (b.topR - b.top) * (x - b.x) / b.w
            if (best.isNaN() || t < best) best = t
        }
        return best
    }
}

enum class ItemKind { DROP, HIGH_DROP, HEART }

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
    fun gap(len: Float, type: HazardType = HazardType.GAP_M, par: Ink = type.par!!, solve: Ink = par): StageBuilder {
        val x0 = x
        x += len
        l.hazards += StageLayout.Hazard(type, x0, x, par)
        bridge(x0, level, x, level, solve)
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

    fun note(text: String, dx: Float = 0.6f, up: Float = 3.4f, maxW: Float = Float.POSITIVE_INFINITY) {
        l.notes += StageLayout.Text(x + dx, level - up, text, maxW)
    }

    // ------------------------------------------------------------------ markers

    fun start(len: Float): StageBuilder {
        l.startX = x + 2f
        return flat(len)
    }

    /** The midway bookmark: retries start here once it's passed. Put it where flat ground follows. */
    fun checkpoint(): StageBuilder {
        l.checkpointX = x
        l.checkpointLevel = level
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
        /** Mid-band pressure for each ink, as the Endless bot uses. */
        val PRESSURE = mapOf(Ink.ROPE to 0.2f, Ink.SPRING to 0.47f, Ink.BEAM to 0.8f)
    }
}
