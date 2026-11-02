package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink

/** World 2, The Cellar: graph paper underground. Low roofs, spiked tunnels, slides, lifts; the Eraser; the Highlighter. */
object World2 {
    /**
     * World 2's teaching page. Underground the roof is low: a spring would hit it, so the first gap
     * and the spiked tunnel want rope; a rope from edge to edge makes a slide down a shaft; an
     * Eraser (rope and springs don't stop it) needs a beam dropped on it; and a doodle box holds the
     * Highlighter, which finds an inkwell down to a bonus page. On the first run the runner waits
     * before each of those.
     */
    val CELLAR_DOOR = StageDef(2, 1, "Cellar Door", Theme.CELLAR, 3.5f) {
        cave(46f, 3.6f)
        start(18f)
        note("Underground the roof is low: a spring would hit it.", dx = -16f, up = 2.7f, maxW = 7f)
        note("Draw rope across.", dx = -6f, up = 2.7f, maxW = 5f)
        hint(Ink.ROPE, x - 1f, x + 3f, x - 0.1f, level - 0.05f, x + 3.1f, level - 0.05f, waitX = x - 1.4f, forbid = Ink.SPRING)
        starLine(x + 0.6f, level - 0.65f, x + 2.4f, level - 0.65f, 3)
        gap(3f, HazardType.GAP_S)
        flat(10f)
        drops(x - 7f, 3)
        note("Spikes overhead: rope only.", dx = -3f, up = 2.7f, maxW = 6f)
        hint(Ink.ROPE, x - 1f, x + 4.5f, x - 0.1f, level - 0.05f, x + 4.6f, level - 0.05f, waitX = x - 4.8f, forbid = Ink.SPRING)
        tunnel(4.5f)
        flat(10f)
        cave(24f, 3.6f)
        flat(4f)
        note("A rope from edge to edge makes a slide.", dx = -3.5f, up = 2.7f, maxW = 6f)
        hint(Ink.ROPE, x - 1f, x + 5f, x - 0.1f, level - 0.05f, x + 5.1f, level + 3.95f, waitX = x - 1.4f, forbid = Ink.SPRING)
        slide(5f, 4f)
        flat(4f)
        checkpoint()
        flat(18f)
        // an Eraser: it rubs out rope and springs, so drop a beam on it
        val e1 = x - 1f
        note("An Eraser! It rubs out rope and springs. Drop a beam on it.", dx = -17f, up = 4f, maxW = 7f)
        eraser(e1)
        // (it wakes when the runner is 18 m off: the beam goes down on it asleep)
        hint(Ink.BEAM, e1 - 2f, e1 + 2f, e1 - 0.9f, level - 3.4f, e1 + 0.9f, level - 3.4f, waitX = e1 - 19.5f, crush = true, label = "drop a beam")
        solve(e1 - 0.9f, level - 3.4f, e1 + 0.9f, level - 3.4f, Ink.BEAM, lead = 19f)
        flat(14f)
        // the Highlighter, and what it finds
        val bx = x - 6f
        note("A doodle box: spring up into it.", dx = -12f, up = 5.4f, maxW = 6f)
        box(bx, up = 2.6f, holds = ItemKind.HIGHLIGHTER)
        springOn(bx - 0.5f, lead = 4f)
        flat(36f)
        val w = x - 22f
        note("Highlighter on: draw over the page to find what's hidden.", dx = -32f, up = 4.2f, maxW = 7f)
        hint(Ink.ROPE, w - 2f, w + 2f, w - 1.2f, level - 0.4f, w + 1.2f, level - 0.4f, label = "highlight here")
        inkwell(w, back = 14f)
        swipe(w - 1.2f, level - 0.4f, w + 1.2f, level - 0.4f, lead = 7f)
        // the bonus page ends with a gold star
        gold(w + 15f, level + StageBuilder.BONUS_DROP - 0.9f)
        flat(6f)
        note("Lifts wait for you: roll on.", dx = -5f, up = 2.8f, maxW = 6f)
        liftUp(3f, 5f)
        flat(12f)
        bounceGold(x - 10f)
        drops(x - 4f, 3)
        starLine(x + 0.8f, level - 0.65f, x + 3.7f, level - 0.65f, 4)
        spikePit(4.5f)
        flat(8f)
        highRoad(x - 4f, 6f)
        gold(x - 1f, level - 4f)
        flat(8f)
        finish()
    }

    /**
     * Erasers in the tunnels. Under a low roof a beam dropped on one is the answer; one that walks
     * off an edge is gone, so a bridge drawn after it falls survives; a spiked tunnel with one
     * coming through it.
     */
    val RUBBER_TUNNELS = StageDef(2, 2, "Rubber Tunnels", Theme.CELLAR, 3.5f) {
        cave(40f, 3.6f)
        start(14f)
        note("Erasers walk off edges. Let this one fall, then draw your bridge.", dx = -12f, up = 2.7f, maxW = 7f)
        // it wakes 18 m off and walks into the gap well before the runner gets there
        val g1 = x
        gap(4f, lead = 3.5f)
        eraser(g1 + 4f + 1.5f)
        flat(14f)
        drops(x - 10f, 3)
        val e1 = x - 2f
        note("Drop a beam on it.", dx = -20f, up = 2.7f, maxW = 5f)
        eraser(e1)
        solve(e1 - 0.9f, level - 3f, e1 + 0.9f, level - 3f, Ink.BEAM, lead = 19f)
        flat(10f)
        tunnel(4.5f)
        flat(6f)
        // out of the cave for a while: a gold star up high
        flat(10f)
        bounceGold(x - 8f)
        cave(36f, 3.6f)
        flat(4f)
        checkpoint()
        flat(10f)
        val e2 = x - 3f
        eraser(e2)
        solve(e2 - 0.9f, level - 3f, e2 + 0.9f, level - 3f, Ink.BEAM, lead = 19f)
        flat(6f)
        val g2 = x
        gap(4.5f, lead = 3.5f)
        eraser(g2 + 4.5f + 1.5f)
        flat(12f)
        spikePit(4f)
        flat(8f)
        // a visible inkwell to a bonus page with a gold star
        inkwell(x - 4f, back = 13f, hidden = false)
        gold(x - 4f + 15f, level + StageBuilder.BONUS_DROP - 0.9f)
        flat(14f)
        highRoad(x - 6f, 5f)
        gold(x - 3f, level - 4f)
        heart(x - 1.5f, level - 4f)
        flat(4f)
        tunnel(4f)
        flat(8f)
        finish()
    }

    /**
     * The Highlighter's stage: two of them, and plenty to find. A bridge nobody drew over a spike
     * pit, star trails, an inkwell to a bonus page, gold stars only a swipe shows.
     */
    val HIDDEN_WAYS = StageDef(2, 3, "Hidden Ways", Theme.CELLAR, 3.5f) {
        start(14f)
        note("Highlighters find what's hidden. Try every corner.", dx = -12f, maxW = 7f)
        val b1 = x - 4f
        box(b1, up = 2.6f, holds = ItemKind.HIGHLIGHTER)
        springOn(b1 - 0.5f, lead = 4f)
        flat(14f)
        // a bridge nobody drew, over a spike pit
        hiddenLedge(x - 0.1f, level - 0.15f, 4.7f, 0.3f)
        swipe(x - 0.5f, level - 0.3f, x + 4.5f, level - 0.3f, lead = 5f)
        for (i in 0 until 4) starAt(x + 0.6f + i * 1.1f, level - 0.65f, hidden = true)
        val p1 = x
        ledge(x, level + 3f, 4.5f, StageBuilder.DEPTH)
        spikes(x, level + 3f, 4.5f)
        skip(4.5f)
        flat(26f)
        // a hidden gold star up where a bounce peaks: swipe it while the highlighter lasts, then
        // spring up to it once it has run out
        gold(x - 9f, level - 4.6f, hidden = true)
        swipe(x - 10f, level - 4.6f, x - 8f, level - 4.6f, lead = 14f)
        springOn(x - 11f, lead = 4f)
        flat(4f)
        cave(20f, 3.6f)
        flat(6f)
        tunnel(4.5f)
        flat(10f)
        checkpoint()
        flat(10f)
        val b2 = x - 5f
        box(b2, up = 2.6f, holds = ItemKind.HIGHLIGHTER)
        springOn(b2 - 0.5f, lead = 4f)
        flat(30f)
        val w = x - 16f
        inkwell(w, back = 14f, hidden = true)
        swipe(w - 1.2f, level - 0.4f, w + 1.2f, level - 0.4f, lead = 7f)
        gold(w + 15f, level + StageBuilder.BONUS_DROP - 0.9f)
        for (i in 0 until 5) starAt(x - 26f + i * 1.2f, level - 2.4f, hidden = true)
        // (well after the highlighter has run out: a bridge drawn with it on would only highlight)
        flat(16f)
        gap(4.5f)
        flat(10f)
        // the last gold star: hidden on the high road
        highRoad(x - 6f, 5f)
        gold(x - 3f, level - 4f)
        flat(4f)
        spikePit(4f)
        flat(8f)
        finish()
    }

    /** Down and up through the mine: rope slides down shafts, lifts up walls, spike pits between. */
    val MINE_LIFTS = StageDef(2, 4, "Mine Lifts", Theme.CELLAR, 3.6f) {
        start(14f)
        note("Down the shafts on rope slides; up the walls on lifts.", dx = -12f, maxW = 7f)
        flat(2f)
        slide(5f, 4f)
        flat(8f)
        drops(x - 6f, 3)
        liftUp(3f, 5f)
        flat(10f)
        bounceGold(x - 8f)
        starLine(x + 0.8f, level - 0.65f, x + 3.2f, level - 0.65f, 3)
        spikePit(4f)
        flat(6f)
        slide(5f, 4f)
        flat(6f)
        cave(26f, 3.6f)
        flat(4f)
        tunnel(4.5f)
        flat(10f)
        checkpoint()
        flat(8f)
        liftUp(3f, 6f)
        flat(8f)
        val e1 = x - 2f
        eraser(e1)
        solve(e1 - 0.9f, level - 3.4f, e1 + 0.9f, level - 3.4f, Ink.BEAM, lead = 19f)
        // (far enough on that the beam lying there doesn't count against the slide)
        flat(12f)
        slide(5f, 4f)
        // (the high road's spring stays clear of the slide's judging)
        flat(13f)
        highRoad(x - 7f, 6f)
        gold(x - 3f, level - 3.8f)
        heart(x - 2f, level - 3.8f)
        flat(4f)
        spikePit(4.5f)
        flat(8f)
        liftUp(3f, 4f)
        flat(12f)
        bounceGold(x - 10f)
        flat(4f)
        finish()
    }

    /** The world's finale: a run through the cellar, then the Giant Eraser. Only a dropped beam hurts it. */
    val THE_GIANT_ERASER = StageDef(2, 5, "The Giant Eraser", Theme.CELLAR, 3.6f) {
        cave(30f, 3.6f)
        start(14f)
        note("The Giant Eraser waits below. Bring beams.", dx = -12f, up = 2.7f, maxW = 7f)
        gap(3.5f, HazardType.GAP_S)
        flat(8f)
        tunnel(4.5f)
        flat(14f)
        // clear of the tunnel's rock (it overhangs 3.5 m)
        bounceGold(x - 8f)
        slide(5f, 4f)
        flat(8f)
        val e1 = x - 2f
        eraser(e1)
        solve(e1 - 0.9f, level - 3.4f, e1 + 0.9f, level - 3.4f, Ink.BEAM, lead = 19f)
        flat(8f)
        liftUp(3f, 5f)
        flat(12f)
        highRoad(x - 7f, 5f)
        gold(x - 4f, level - 4f)
        heart(x - 2.5f, level - 4f)
        flat(4f)
        spikePit(4.5f)
        flat(8f)
        bounceGold(x - 6.5f)
        flat(2f)
        checkpoint()
        flat(10f)
        note("Only a beam dropped on it hurts it. Spring over it.", dx = -8f, maxW = 6f)
        arena(BossKind.GIANT_ERASER)
        flat(6f)
        finish()
    }

    val STAGES = listOf(CELLAR_DOOR, RUBBER_TUNNELS, HIDDEN_WAYS, MINE_LIFTS, THE_GIANT_ERASER)
}
