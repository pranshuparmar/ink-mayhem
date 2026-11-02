package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink

/** World 2, The Cellar: graph paper underground. Low roofs, spiked tunnels, slides, lifts; the Eraser; the Highlighter. */
object World2 {
    val CELLAR_DOOR = StageDef(2, 1, "Cellar Door", Theme.CELLAR, 3.5f) {
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

    val STAGES = listOf(CELLAR_DOOR)
}
