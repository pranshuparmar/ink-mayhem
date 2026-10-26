package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.physics.Ink

/**
 * A scripted player for tests and the desktop autoplay. When a hazard comes within [lookahead]
 * metres it draws one stroke through the normal pen-input path:
 *
 * - gaps, tunnels and boulder gaps: a straight bridge edge to edge, just above ground level;
 * - walls: a short spring on the ground 2.4–3.2 m before the wall, so the bounce clears it.
 *
 * [inkFor] picks the ink per hazard; by default the hazard's par ("correct ink").
 */
class InkBot(
    private val sim: GameSim,
    private val lookahead: Float = 8f,
    private val inkFor: (HazardType) -> Ink = { it.par ?: Ink.ROPE },
) {
    private val planned = HashSet<Hazard>()
    private var t = 0.0
    private val pointer = 99

    /** Call once per tick before [GameSim.tick]. */
    fun update() {
        if (sim.state != GameState.RUN || sim.drawing != null) return
        val rx = sim.runner.body.position.x
        for (hz in sim.hazards) {
            if (hz.done || hz in planned || hz.x0 - rx > lookahead) continue
            planned += hz
            draw(hz)
        }
    }

    private fun draw(hz: Hazard) {
        val ink = inkFor(hz.type)
        val p = PRESSURE.getValue(ink)
        val level = sim.blocks.firstOrNull { it.x < hz.x0 - 0.05f && it.right >= hz.x0 - 0.05f }?.level ?: return
        if (hz.type == HazardType.WALL) {
            line(hz.x0 - 3.2f, hz.x0 - 2.4f, level - (Ink.thickness(p) / 2f + 0.04f), p)
        } else {
            line(hz.x0, hz.x1, level - 0.05f, p)
        }
    }

    private fun line(x0: Float, x1: Float, y: Float, p: Float) {
        val n = ((x1 - x0) / 0.05f).toInt() + 1
        for (i in 0 until n) {
            val x = if (i == n - 1) x1 else x0 + i * 0.05f
            t += 1.0 / 240
            if (i == 0) sim.penDown(pointer, x, y, p, erase = false, t = t) else sim.penMove(pointer, x, y, p, t)
        }
        sim.penUp(pointer)
    }

    companion object {
        /** Mid-band normalized pressure for each ink. */
        val PRESSURE = mapOf(Ink.ROPE to 0.2f, Ink.SPRING to 0.47f, Ink.BEAM to 0.8f)
    }
}
