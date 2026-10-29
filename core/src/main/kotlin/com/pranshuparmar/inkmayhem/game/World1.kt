package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink

/**
 * World 1: four hand-built stages, each the same every time. 1-1 teaches on a gentle page,
 * 1-2 goes underground (rope country), 1-3 climbs into the sky, and 1-4 is the castle, with a
 * boulder chase to finish. Positions are in metres from the start; the ground starts at 20.
 */
object Stages {
    /**
     * The game opens on this page, already running. A short handwritten note introduces each new
     * thing just before it. The first three obstacles each ask for one ink with a doodled guide:
     * a rope gap, a spring wall and a beam chasm. While the pen is untuned, they also tune it from
     * the strokes drawn for them, and the runner waits at each until one is drawn. Then a Blot to
     * bounce on, a doodle box with the marker cap, and a second Blot that a Bold runner just rolls
     * flat.
     */
    val FIRST_PAGE = StageDef(1, 1, "First Page", Theme.PAGE, 3.4f) {
        start(18f)
        note("Draw the way with the S Pen. How hard you press makes different ink.", dx = -16.5f, maxW = 9f)
        note("Light touch makes rope. Draw it across the gap.", dx = -6.5f, up = 2.6f, maxW = 7f)
        hint(Ink.ROPE, x - 1f, x + 3f, x - 0.1f, level - 0.05f, x + 3.1f, level - 0.05f, tunes = true, waitX = x - 1.4f)
        starLine(x + 0.6f, level - 0.65f, x + 2.4f, level - 0.65f, 3)
        gap(3f, HazardType.GAP_S)
        flat(13f)
        note("Write normally for a spring. Lay it on the ground to bounce over the wall.", dx = -11.5f, maxW = 8f)
        hint(Ink.SPRING, x - 4.6f, x + 1f, x - 3.8f, level - 0.1f, x - 2.2f, level - 0.1f, tunes = true, waitX = x - 5f)
        starArc(x - 3.6f, level - 0.55f, 2.75f, 5, from = 0.3f, to = 1.3f)
        wall(2.4f)
        flat(13f)
        note("Too far for rope. Press hard: a beam spans anything.", dx = -9f, maxW = 7f)
        hint(Ink.BEAM, x - 1f, x + 7.6f, x - 0.1f, level - 0.05f, x + 7.7f, level - 0.05f, tunes = true, waitX = x - 1.4f)
        starLine(x + 1.2f, level - 0.75f, x + 6.4f, level - 0.75f, 4)
        gap(7.6f, HazardType.GAP_L)
        flat(24f)
        // a Blot walks at you: a spring before it bounces you onto it
        val b1 = x - 5f
        note("A Blot! Don't touch it. Draw a spring and land on it.", dx = -22f, maxW = 8f)
        blot(b1)
        hint(Ink.SPRING, b1 - 9.5f, b1 - 5f, b1 - 8.6f, level - 0.1f, b1 - 7.4f, level - 0.1f)
        spring(b1 - 8.1f, b1 - 7.3f, level, lead = 4f)
        flat(20f)
        // a doodle box overhead: bounce up into it and catch the marker cap it pops out
        val bx = x - 14f
        note("Spring up into the box.", dx = -20f, up = 5.4f, maxW = 6f)
        box(bx, up = 2.6f)
        hint(Ink.SPRING, bx - 2f, bx + 1.5f, bx - 0.8f, level - 0.1f, bx + 0.4f, level - 0.1f)
        spring(bx - 0.5f, bx + 0.3f, level, lead = 4f)
        stars(bx + 3f, 4)
        flat(16f)
        // Bold rolls straight through the next one
        note("Bold rolls Blots flat. Not Bold? Bounce.", dx = -12f, maxW = 7f)
        blot(x - 3f)
        flat(4f)
        slope(10f, 2f)
        flat(4f)
        drop(x - 2f)
        note("Downhill, you speed up.", dx = -2f, up = 3.2f)
        slope(10f, -2f)
        stars(x + 1f, 4)
        flat(8f)
        checkpoint()
        flat(4f)
        starLine(x + 0.8f, level - 0.65f, x + 3.7f, level - 0.65f, 4)
        gap(4.5f)
        flat(10f)
        wall(2.4f)
        // the high road: a second spring reaches a ledge with drops and a heart
        val hx = x
        flat(20f)
        note("Spring again for the high road.", dx = -18f, up = 2.2f)
        ledge(hx + 7f, level - 3.2f, 10f)
        spring(hx + 4.4f, hx + 5.2f, level)
        for (i in 0 until 3) dropAt(hx + 9f + i * 1.8f, level - 3.95f)
        heart(hx + 15.6f, level - 4.1f)
        stepDown(2.4f)
        flat(8f)
        note("Seesaws tip as you cross.", dx = -6f)
        seesawGap(6f)
        flat(8f)
        drops(x - 6f, 3)
        gap(5f)
        flat(8f)
        stars(x - 6f, 5)
        finish()
    }

    val CELLAR = StageDef(1, 2, "Cellar", Theme.CELLAR, 3.5f) {
        cave(40f, 3.6f)
        start(16f)
        note("Underground, springs hit the roof. Rope it.", dx = -12f, up = 2.6f)
        starLine(x + 0.6f, level - 0.65f, x + 2.4f, level - 0.65f, 3)
        gap(3f, HazardType.GAP_S)
        flat(8f)
        drops(x - 6f, 3)
        tunnel(4.5f)
        flat(8f)
        cave(20f, 3.6f)
        flat(4f)
        note("A rope from edge to edge makes a slide.", dx = -3f, up = 2.6f)
        slide(5f, 4f)
        cave(30f, 3.6f)
        flat(10f)
        drops(x - 7f, 3)
        starLine(x + 0.6f, level - 0.65f, x + 3.4f, level - 0.65f, 4)
        spikePit(4f)
        checkpoint()
        flat(11f)
        tunnel(5f)
        flat(8f)
        spikePit(4.5f)
        flat(6f)
        note("Lifts wait for you.", dx = -5f, up = 2.8f)
        liftUp(3f, 5f)
        flat(10f)
        drops(x - 7f, 3)
        stars(x - 9f, 4)
        cave(24f, 3.6f)
        gap(4.5f)
        flat(6f)
        tunnel(4f)
        flat(6f)
        finish()
    }

    val UP_HIGH = StageDef(1, 3, "Up High", Theme.SKY, 3.6f) {
        depth = 5f
        start(16f)
        note("The climb: a spring on every step.", dx = -12f)
        climb(3, 2.4f, 7.5f)
        flat(2f)
        note("Too far for rope. Press hard for a beam.", dx = -8f, up = 3f)
        starLine(x + 1.2f, level - 0.75f, x + 6.4f, level - 0.75f, 4)
        gap(7.6f, HazardType.GAP_L)
        flat(8f)
        drops(x - 6f, 3)
        seesawGap(6f)
        checkpoint()
        flat(11f)
        stars(x - 10f, 4)
        note("Down the islands: bridges can slope.", dx = -4f)
        gapDown(3.5f, 2f)
        flat(6f)
        gapDown(3.5f, 2f)
        flat(18f)
        // a cloud above with a heart, for whoever bounces up. Its spring sits 2 m clear of the
        // last bridge's judging, and the runner drops back to the ground before the next gap.
        spring(x - 14.8f, x - 14f, level)
        ledge(x - 11.1f, level - 3.4f, 5f)
        dropAt(x - 9.6f, level - 4.15f)
        heart(x - 7.6f, level - 4.3f)
        gap(5f)
        flat(6f)
        wall(2.4f)
        flat(8f)
        finish()
    }

    val CASTLE = StageDef(1, 4, "Castle", Theme.CASTLE, 3.8f) {
        start(16f)
        note("The castle. Spikes below, spikes above.", dx = -12f)
        starLine(x + 0.6f, level - 0.65f, x + 2.9f, level - 0.65f, 3)
        spikePit(3.5f)
        flat(6f)
        tunnel(4.5f)
        flat(6f)
        stepDown(2f)
        flat(6f)
        liftUp(3f, 6f)
        flat(6f)
        drops(x - 5f, 3)
        starLine(x + 0.6f, level - 0.65f, x + 3.9f, level - 0.65f, 4)
        spikePit(4.5f)
        checkpoint()
        flat(10f)
        seesawGap(6f, spikesBelow = true)
        flat(6f)
        climb(2, 2.4f, 7.5f)
        flat(1f)
        spikePit(7.6f, type = HazardType.GAP_L)
        flat(4f)
        note("Boulder! Rope drops it, anything else carries it.", dx = -3f)
        runway(18f)
        gap(5f, HazardType.BOULDER)
        flat(6f)
        finish()
    }

    val WORLD_1 = listOf(FIRST_PAGE, CELLAR, UP_HIGH, CASTLE)

    /** Every stage in play order. */
    val ALL: List<StageDef> = WORLD_1

    fun byId(id: String): StageDef? = ALL.firstOrNull { it.id == id }

    fun next(def: StageDef): StageDef? = ALL.getOrNull(ALL.indexOf(def) + 1)
}
