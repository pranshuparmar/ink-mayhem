package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink

/** World 3, Up High: a sky-blue dot grid of floating islands. Climbs, long gaps, drifting clouds; paper planes; Cloud chalk. */
object World3 {
    val CLOUD_STEPS = StageDef(3, 1, "Cloud Steps", Theme.SKY, 3.6f) {
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

    val STAGES = listOf(CLOUD_STEPS)
}
