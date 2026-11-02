package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink

/**
 * World 1, The Notebook: ruled paper and the basics. 1-1 teaches rope, spring and beam, Blots,
 * doodle boxes and the marker cap; the rest of the world plays with them. Positions are in metres
 * from the start; the ground starts at 20.
 */
object World1 {
    /**
     * The game opens on this page, already running. A short handwritten note introduces each new
     * thing just before it. The first three obstacles each ask for one ink with a doodled guide:
     * a rope gap, a spring wall and a beam chasm (while the pen is untuned, they also tune it
     * from the strokes drawn for them). Then a Blot to bounce on, a doodle box with the marker
     * cap, and a second Blot that a Bold runner just rolls flat. On the page's first run the
     * runner waits before each of those first five until a stroke that works is drawn, and a
     * checkpoint right after the three inks keeps a later death from sending it back through them.
     */
    val FIRST_PAGE = StageDef(1, 1, "First Page", Theme.PAGE, 3.4f) {
        start(18f)
        // two notes at a time at most, the first above the second, so they read in order
        note("This ink blot is you. It rolls along by itself.", dx = -17.5f, up = 4.6f, maxW = 7f)
        note("Draw across the gap with the S Pen. A light touch makes rope.", dx = -6.5f, up = 3.3f, maxW = 7f)
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
        flat(3f)
        // the three inks are learnt: a death further on starts again here
        checkpoint()
        note("Beams use lots of ink: the bar at the top. Blue drops fill it up.", dx = 1f, up = 4.4f, maxW = 7f)
        drops(x + 4f, 3)
        flat(27f)
        // a Blot walks at you: a spring before it bounces you onto it
        val b1 = x - 5f
        note("A Blot! Don't touch it. Draw a spring and land on it.", dx = -19f, maxW = 8f)
        blot(b1)
        hint(Ink.SPRING, b1 - 9.5f, b1 - 5f, b1 - 8.6f, level - 0.1f, b1 - 7.4f, level - 0.1f, waitX = b1 - 12.5f)
        spring(b1 - 8.1f, b1 - 7.3f, level, lead = 4f)
        flat(20f)
        // a doodle box overhead: bounce up into it and catch the marker cap it pops out
        val bx = x - 14f
        note("Spring up into the box.", dx = -20f, up = 5.4f, maxW = 6f)
        box(bx, up = 2.6f)
        hint(Ink.SPRING, bx - 2f, bx + 1.5f, bx - 0.8f, level - 0.1f, bx + 0.4f, level - 0.1f, waitX = bx - 4.5f, slack = 0.1f)
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
        note("Drew a line in the way? Hold the pen's side button and touch it to erase.", dx = -12f, up = 4.6f, maxW = 8f)
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

    val STAGES = listOf(FIRST_PAGE)
}
