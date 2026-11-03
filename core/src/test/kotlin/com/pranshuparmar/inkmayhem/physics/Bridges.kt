package com.pranshuparmar.inkmayhem.physics

/**
 * Test scenarios shared by the physics tests.
 */
object Bridges {
    const val DT = 1f / 60f

    init {
        TestNatives.ensure()
    }

    /** Raw points of a straight stroke from x0 to x1 at height y, sampled every 5 cm. */
    fun line(x0: Float, x1: Float, y: Float, p: Float): Triple<FloatArray, FloatArray, FloatArray> {
        val n = ((x1 - x0) / 0.05f + 1e-3f).toInt() + 1
        val xs = FloatArray(n) { x0 + it * 0.05f }
        val ys = FloatArray(n) { y }
        val ps = FloatArray(n) { p }
        return Triple(xs, ys, ps)
    }

    fun InkPhysics.draw(x0: Float, x1: Float, y: Float, p: Float): Stroke {
        val (xs, ys, ps) = line(x0, x1, y, p)
        return buildStroke(xs, ys, ps, xs.size) ?: error("stroke too short")
    }

    class CrateResult(val held: Boolean, val crateY: Float, val snaps: Int, val anchors: Int, val bodies: Int)

    /**
     * Ground on both sides of a [gap], a straight stroke from 0.4 m before the gap to
     * 0.4 m past it resting just above ground level, one second to settle, then a crate of [size]
     * and [mass] dropped from [drop] metres above the sagged middle, watched for five seconds.
     */
    fun crate(p: Float, gap: Float, mass: Float, size: Float = 0.5f, drop: Float = 2f): CrateResult {
        val ink = InkPhysics()
        try {
            ink.addRect(-10f, 0f, 10f, 40f)
            ink.addRect(gap, 0f, 10f, 40f)
            val t = Ink.thickness(p)
            val s = ink.draw(-0.4f, gap + 0.4f, -(t / 2f + 0.04f), p)
            repeat(60) { ink.step(DT, 2) }
            var midY = -1e9f
            for (sg in s.segs) {
                if (sg.group.body == null) continue
                val ax = sg.group.worldX(sg.lax, sg.lay)
                val ay = sg.group.worldY(sg.lax, sg.lay)
                if (kotlin.math.abs(ax - gap / 2f) < 0.4f) midY = maxOf(midY, ay)
            }
            ink.events.clear()
            val crate = ink.addCrate(gap / 2f, midY - t / 2f - size / 2f - drop, size, mass / (size * size))
            var snaps = 0
            repeat(300) {
                ink.step(DT, 2)
                snaps += ink.events.size
                ink.events.clear()
            }
            val cy = crate.position.y
            return CrateResult(cy < 2f, cy, snaps, s.anchors.size, s.groups.size)
        } finally {
            ink.dispose()
        }
    }
}
