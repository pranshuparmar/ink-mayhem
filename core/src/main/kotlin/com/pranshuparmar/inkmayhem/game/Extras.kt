package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.LifetimeStats

/**
 * Doodle mode, open once the ending has been reached: switches that bend the rules for fun.
 * While any is on, a stage's stars, best score and gold stars aren't saved, and no stickers are
 * earned. Each is a bit in the saved mask ([com.pranshuparmar.inkmayhem.Settings.doodle]).
 */
enum class Doodle(val title: String, val detail: String) {
    ENDLESS_INK("Endless ink", "The ink meter never runs down"),
    HALF_SPEED("Half speed", "The page runs at half speed"),
    BOLD("Always Bold", "Bigger, flattens Blots, shrugs off every hit"),
    HIGHLIGHTER("Highlighter", "Everything hidden shows from the start"),
    CHALK("Cloud chalk", "Every line floats where you draw it"),
    GOLD_NIB("Gold nib", "Free ink that never snaps or gets eaten"),
    STAR_NIB("Star nib", "Nothing hurts (falls, pools and the tide do)"),
    ;

    val bit: Int get() = 1 shl ordinal

    fun on(mask: Int): Boolean = mask and bit != 0

    companion object {
        /** Sets [sim] up for the switches in [mask] (all off: the normal rules). */
        fun apply(mask: Int, sim: GameSim) {
            sim.unlimitedInk = ENDLESS_INK.on(mask)
            sim.keepBold = BOLD.on(mask)
            var kept = 0
            if (HIGHLIGHTER.on(mask)) kept = kept or (1 shl ItemKind.HIGHLIGHTER.ordinal)
            if (CHALK.on(mask)) kept = kept or (1 shl ItemKind.CHALK.ordinal)
            if (GOLD_NIB.on(mask)) kept = kept or (1 shl ItemKind.GOLD_NIB.ordinal)
            if (STAR_NIB.on(mask)) kept = kept or (1 shl ItemKind.STAR_NIB.ordinal)
            sim.keptPowers = kept
        }

        /** How fast the page runs. */
        fun speed(mask: Int): Float = if (HALF_SPEED.on(mask)) 0.5f else 1f
    }
}

/**
 * Stickers: one-off moments, each stuck in the margin when it happens and kept in the
 * Notebook's sticker book. Some follow from the save ([earnedFrom]); the rest are earned the
 * moment they happen. A bit each in [com.pranshuparmar.inkmayhem.Settings.stickers].
 */
enum class Sticker(val title: String, val how: String) {
    FIRST_CLEAR("First page", "Clear a stage"),
    TUNED("Fine nib", "Tune your pen"),
    LIGHT_TOUCH("Light touch", "Clear a stage with rope alone"),
    TOP_COMBO("Nine times", "Reach a ×9 combo"),
    NINE_LIVES("Nine lives", "Hold nine lives at once"),
    UNTOUCHED("Untouched", "Beat a boss without losing a life"),
    WORLD_GOLD("Gold rush", "Find every gold star in a world"),
    ALL_BOSSES("Boss bin", "Beat all five bosses"),
    ALL_CLEAR("Full notebook", "Clear all 25 stages"),
    ALL_STARS("Star chart", "Three stars on every stage"),
    ALL_GOLD("Golden", "Find every gold star"),
    THE_END("The end?", "Reach the ending"),
    THOUSAND("Ink well spent", "Draw 1,000 strokes"),
    SECOND_THOUGHTS("Second thoughts", "Erase 100 strokes"),
    ASKED("Just asking", "Ask Dot for a hint"),
    SCRAPBOOK("Scrapbook", "Tear out a page"),
    ;

    val bit: Long get() = 1L shl ordinal

    fun on(mask: Long): Boolean = mask and bit != 0L

    companion object {
        /** The stickers the save and lifetime numbers already account for. */
        fun earnedFrom(store: StageStore, stats: LifetimeStats): Long {
            var m = 0L
            val all = Stages.ALL
            if (all.any { store.stars(it.id) > 0 }) m = m or FIRST_CLEAR.bit
            val bosses = all.filter { it.layout.arenas.isNotEmpty() }
            if (bosses.isNotEmpty() && bosses.all { store.stars(it.id) > 0 }) m = m or ALL_BOSSES.bit
            if (all.all { store.stars(it.id) > 0 }) m = m or ALL_CLEAR.bit
            if (all.all { store.stars(it.id) >= 3 }) m = m or ALL_STARS.bit
            fun allGold(stages: List<StageDef>) = stages.all { Integer.bitCount(store.gold(it.id)) >= GOLD_PER_STAGE }
            if (Stages.WORLDS.any { allGold(it.stages) }) m = m or WORLD_GOLD.bit
            if (allGold(all)) m = m or ALL_GOLD.bit
            if (stats.totalStrokes >= 1000) m = m or THOUSAND.bit
            if (stats.erases >= 100) m = m or SECOND_THOUGHTS.bit
            if (stats.hintsAsked > 0) m = m or ASKED.bit
            if (stats.pagesTorn > 0) m = m or SCRAPBOOK.bit
            return m
        }

        /** Stickers in [now] that weren't in [before], in book order. */
        fun fresh(before: Long, now: Long): List<Sticker> = entries.filter { it.on(now) && !it.on(before) }

        const val GOLD_PER_STAGE = 3
    }
}

/**
 * The jukebox: what the menus and stages play. "Each world's own" is the game as it comes; a
 * world's tune can be picked once its boss is beaten, and the boss theme once any boss is.
 */
object Jukebox {
    class Track(val name: String, val title: String, val world: Int)

    const val BOSS = "boss"
    private val TUNE_TITLES = mapOf(
        "ink_rush" to "Ink Rush", "cellar" to "Cellar", "sky" to "Sky", "castle" to "Castle",
        "midnight" to "Midnight", BOSS to "Boss fight",
    )

    /** Every tune, in the order the worlds come; the boss theme last (world 0: any boss). */
    val TRACKS: List<Track> = Stages.WORLDS.map { Track(it.music, TUNE_TITLES.getValue(it.music), it.number) } +
        Track(BOSS, TUNE_TITLES.getValue(BOSS), 0)

    private fun bossOf(world: Int): StageDef? =
        Stages.WORLDS.firstOrNull { it.number == world }?.stages?.lastOrNull { it.layout.arenas.isNotEmpty() }

    fun unlocked(t: Track, store: StageStore): Boolean = when (t.world) {
        0 -> Stages.WORLDS.any { w -> bossOf(w.number)?.let { store.stars(it.id) > 0 } == true }
        else -> bossOf(t.world)?.let { store.stars(it.id) > 0 } == true
    }

    /** What beats it, for a locked track. */
    fun lockedHint(t: Track): String = when (t.world) {
        0 -> "Beat any boss"
        else -> "Beat ${bossOf(t.world)?.name ?: "this world's boss"}"
    }

    /**
     * The tune to play: the jukebox's [pick] when it's set and unlocked, else [own] (the
     * screen's or world's own tune).
     */
    fun choose(pick: String, own: String, store: StageStore): String {
        if (pick.isEmpty()) return own
        val t = TRACKS.firstOrNull { it.name == pick } ?: return own
        return if (unlocked(t, store)) t.name else own
    }

}

/** Ask Dot: which strokes of a stage's scripted solution to show as dotted lines. */
object Hints {
    /**
     * The next [count] strokes of [solution] still ahead of a runner at [x]: ones that reach
     * past it and are due within [reach] metres, in the order they're drawn.
     */
    fun ahead(solution: List<StageLayout.Stroke>, x: Float, reach: Float = REACH, count: Int = 2): List<StageLayout.Stroke> =
        solution.filter { it.xs.max() > x + 0.3f && it.atX < x + reach }.sortedBy { it.atX }.take(count)

    const val REACH = 30f
}
