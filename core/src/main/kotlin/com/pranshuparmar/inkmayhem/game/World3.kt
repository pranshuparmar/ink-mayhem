package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink

/** World 3, Up High: a sky-blue dot grid of floating islands. Climbs, long gaps, drifting clouds; paper planes; Cloud chalk. */
object World3 {
    /**
     * World 3's teaching page, on islands in the sky. What's new waits for you on the first run:
     * a bouncy cloud that launches you like a spring, a flight of paper planes to spring through,
     * and Cloud chalk, whose lines stay where they're drawn, even a light one across a gap too
     * long for rope.
     */
    val CLOUD_STEPS = StageDef(3, 1, "Cloud Steps", Theme.SKY, 3.6f) {
        depth = 5f
        start(16f)
        note("Up high: islands over nothing. A spring on every step.", dx = -14f, maxW = 7f)
        climb(2, 2.4f, 7.5f)
        flat(4f)
        // a bouncy cloud: it launches you like a spring
        note("Bouncy clouds launch you like springs. Just roll on.", dx = -4f, maxW = 6f)
        val c1 = x
        openGap(6.5f, HazardType.GAP_M)
        cloud(c1, level, 2.2f, bouncy = true)
        starArc(c1 - 0.2f, level - 0.55f, n = 5, from = 0.3f, to = 1.4f)
        flat(12f)
        // paper planes: spring through them
        val p1 = x - 4f
        note("Paper planes! Spring through them to crumple them.", dx = -11f, maxW = 6f)
        planes(p1)
        hint(Ink.SPRING, p1 + 2f, p1 + 7f, p1 + 3.2f, level - 0.1f, p1 + 4.4f, level - 0.1f, waitX = p1 - 3f)
        springOn(p1 + 3.4f, lead = 4f)
        flat(14f)
        drops(x - 6f, 3)
        checkpoint()
        flat(6f)
        // Cloud chalk: what you draw stays put, so even a light line holds across a long gap
        val bx = x - 2f
        note("A doodle box: spring up into it.", dx = -8f, up = 5.4f, maxW = 6f)
        box(bx, up = 2.6f, holds = ItemKind.CHALK)
        springOn(bx - 0.5f, lead = 4f)
        flat(14f)
        note("Cloud chalk: lines stay where you draw them. Even a light one holds here.", dx = -12f, up = 4.2f, maxW = 7f)
        hint(Ink.ROPE, x - 1f, x + 11f, x - 0.1f, level - 0.05f, x + 11.1f, level - 0.05f, waitX = x - 1.4f, label = "chalk line")
        gap(11f, HazardType.GAP_L, par = Ink.BEAM, solve = Ink.ROPE, lead = 6f)
        flat(8f)
        bounceGold(x - 7f)
        seesawGap(6f)
        flat(11f)
        stars(x - 10f, 4)
        note("Down the islands: bridges can slope.", dx = -4f, maxW = 6f)
        gapDown(3.5f, 2f)
        flat(6f)
        gapDown(3.5f, 2f)
        flat(18f)
        // a cloud above with a heart and a gold star, for whoever bounces up
        springOn(x - 14.8f)
        cloud(x - 11.1f, level - 3.4f, 5f)
        heart(x - 7.6f, level - 4.3f)
        gold(x - 9.6f, level - 4.3f)
        gap(5f)
        flat(6f)
        wall(2.4f)
        flat(10f)
        bounceGold(x - 8f)
        flat(2f)
        finish()
    }

    /** Paper planes, flight after flight: spring through them, between gaps and climbs. */
    val FLIGHT_PATH = StageDef(3, 2, "Flight Path", Theme.SKY, 3.7f) {
        depth = 5f
        start(14f)
        note("More planes. Spring through each flight.", dx = -12f, maxW = 6f)
        val p1 = x - 6f
        planes(p1)
        springOn(p1 + 3.4f, lead = 4f)
        flat(14f)
        starLine(x + 0.6f, level - 0.65f, x + 3.4f, level - 0.65f, 3)
        gap(4f)
        flat(14f)
        val p2 = x - 12f
        planes(p2, count = 4, gap = 1.2f)
        springOn(p2 + 3.4f, lead = 4f)
        flat(4f)
        wall(2.4f)
        flat(12f)
        bounceGold(x - 9f)
        checkpoint()
        flat(10f)
        val c1 = x
        openGap(6.5f, HazardType.GAP_M)
        cloud(c1, level, 2.2f, bouncy = true)
        flat(16f)
        val p3 = x - 14f
        planes(p3, count = 3, gap = 1.6f)
        springOn(p3 + 3.4f, lead = 4f)
        flat(4f)
        gap(7.6f, HazardType.GAP_L)
        flat(14f)
        highRoad(x - 10f, 6f)
        gold(x - 6f, level - 3.8f)
        heart(x - 5f, level - 3.8f)
        val p4 = x + 2f
        flat(16f)
        planes(p4, count = 5, gap = 1.1f)
        springOn(p4 + 3.4f, lead = 4f)
        flat(4f)
        gapDown(3.5f, 2f)
        flat(12f)
        bounceGold(x - 9f)
        flat(2f)
        finish()
    }

    /** Bouncy clouds: from one to the next over the void, and plain ones to bridge between. */
    val DRIFTING = StageDef(3, 3, "Drifting", Theme.SKY, 3.6f) {
        depth = 5f
        start(14f)
        note("Cloud to cloud: let the bouncy ones carry you.", dx = -12f, maxW = 6f)
        stars(x - 9f, 5)
        val c1 = x
        openGap(6.5f, HazardType.GAP_M)
        cloud(c1, level, 2.2f, bouncy = true)
        starArc(c1 - 0.2f, level - 0.55f, n = 5, from = 0.3f, to = 1.4f)
        flat(10f)
        // two in a row
        val c2 = x
        openGap(12f, HazardType.GAP_L, par = Ink.BEAM)
        cloud(c2, level, 2.2f, bouncy = true)
        cloud(c2 + 5f, level, 3.5f, bouncy = true)
        flat(12f)
        bounceGold(x - 9f)
        // plain clouds to bridge between: hook rope onto them
        note("Plain clouds hold rope like ground does.", dx = -2f, maxW = 6f)
        val k1 = x
        openGap(16f, HazardType.GAP_M)
        cloud(k1 + 4f, level, 4f)
        cloud(k1 + 12f - 0.01f, level, 4f)
        starLine(k1 + 4.5f, level - 0.65f, k1 + 7.5f, level - 0.65f, 3)
        bridge(k1, level, k1 + 4f, level, Ink.ROPE)
        bridge(k1 + 8f, level, k1 + 12f, level, Ink.ROPE)
        flat(10f)
        checkpoint()
        flat(8f)
        val p1 = x - 6f
        planes(p1)
        springOn(p1 + 3.4f, lead = 4f)
        flat(6f)
        val c3 = x
        openGap(6.5f, HazardType.GAP_M)
        cloud(c3, level, 2.2f, bouncy = true)
        flat(6f)
        // a bouncy cloud up high, with a gold star at the top of its bounce
        springOn(x - 2.6f)
        cloud(x + 0.6f, level - 3.2f, 3f, bouncy = true)
        gold(x + 3.6f, level - 7.6f)
        flat(14f)
        gap(4.5f)
        // (the high road's spring clear of the gap's judging)
        flat(12f)
        highRoad(x - 7f, 6f)
        gold(x - 3f, level - 3.8f)
        heart(x - 2f, level - 3.8f)
        flat(4f)
        gapDown(3.5f, 2f)
        flat(10f)
        finish()
    }

    /** Cloud chalk over long gaps, and gold stars floating where only a chalk path reaches. */
    val THIN_AIR = StageDef(3, 4, "Thin Air", Theme.SKY, 3.6f) {
        depth = 5f
        start(14f)
        note("Thin air. Grab the chalk: its lines need nothing to hold on to.", dx = -12f, maxW = 7f)
        val b1 = x - 4f
        box(b1, up = 2.6f, holds = ItemKind.CHALK)
        springOn(b1 - 0.5f, lead = 4f)
        flat(10f)
        // a long gap with a gold star over its middle: a chalk path up and over
        val g1 = x
        openGap(12f, HazardType.GAP_L, par = Ink.BEAM)
        solve(g1 - 0.1f, level - 0.05f, g1 + 6f, level - 1.6f, Ink.ROPE, lead = 5f)
        solve(g1 + 6f, level - 1.6f, g1 + 12.1f, level - 0.05f, Ink.ROPE, lead = 5f)
        gold(g1 + 6f, level - 2.3f)
        flat(10f)
        gap(4f)
        flat(12f)
        checkpoint()
        flat(6f)
        val b2 = x - 2f
        box(b2, up = 2.6f, holds = ItemKind.CHALK)
        springOn(b2 - 0.5f, lead = 4f)
        flat(10f)
        val g2 = x
        openGap(13f, HazardType.GAP_L, par = Ink.BEAM)
        bridge(g2, level, g2 + 13f, level, Ink.ROPE, lead = 5f)
        starLine(g2 + 1f, level - 0.65f, g2 + 12f, level - 0.65f, 6)
        flat(12f)
        bounceGold(x - 9f)
        gap(7.6f, HazardType.GAP_L)
        flat(8f)
        val c1 = x
        openGap(6.5f, HazardType.GAP_M)
        cloud(c1, level, 2.2f, bouncy = true)
        flat(12f)
        highRoad(x - 8f, 6f)
        gold(x - 4f, level - 3.8f)
        heart(x - 3f, level - 3.8f)
        flat(4f)
        finish()
    }

    /** The world's finale: a run across the sky, then the Paper Airliner. Bounce up into it. */
    val THE_PAPER_AIRLINER = StageDef(3, 5, "The Paper Airliner", Theme.SKY, 3.7f) {
        depth = 5f
        start(14f)
        note("The Paper Airliner circles ahead. Bounce up into it.", dx = -12f, maxW = 7f)
        stars(x - 10f, 5)
        val p1 = x - 6f
        planes(p1)
        springOn(p1 + 3.4f, lead = 4f)
        flat(6f)
        gap(4.5f)
        flat(8f)
        val c1 = x
        openGap(6.5f, HazardType.GAP_M)
        cloud(c1, level, 2.2f, bouncy = true)
        starArc(c1 - 0.2f, level - 0.55f, n = 5, from = 0.3f, to = 1.4f)
        flat(12f)
        bounceGold(x - 9f)
        wall(2.4f)
        // (the high road's spring past where the wall's bounce lands)
        flat(16f)
        highRoad(x - 8f, 6f)
        gold(x - 4f, level - 3.8f)
        heart(x - 3f, level - 3.8f)
        flat(4f)
        gap(7.6f, HazardType.GAP_L)
        flat(10f)
        bounceGold(x - 7.5f)
        flat(2f)
        checkpoint()
        flat(10f)
        arena(BossKind.AIRLINER)
        flat(6f)
        finish()
    }

    val STAGES = listOf(CLOUD_STEPS, FLIGHT_PATH, DRIFTING, THIN_AIR, THE_PAPER_AIRLINER)
}
