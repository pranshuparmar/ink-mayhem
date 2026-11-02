package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink

/** World 4, The Castle: brickwork. Boulders, seesaws over spikes, crumbling bricks; ink beetles; the Gold nib. */
object World4 {
    val CASTLE_GATE = StageDef(4, 1, "Castle Gate", Theme.CASTLE, 3.8f) {
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

    val STAGES = listOf(CASTLE_GATE)
}
