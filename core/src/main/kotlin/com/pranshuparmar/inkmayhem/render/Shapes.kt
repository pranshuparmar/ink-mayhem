package com.pranshuparmar.inkmayhem.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Filled-shape drawing on top of ShapeRenderer, in whatever units the current projection uses.
 * [pxPerUnit] (device pixels per unit) sizes circle tessellation and hairlines so shapes look the
 * same at any zoom. Everything is drawn as filled triangles; MSAA smooths the edges.
 */
class Shapes : Disposable {
    val sr = ShapeRenderer(30_000)
    var pxPerUnit = 1f
    private val tmp = Color()

    fun begin(projection: Matrix4, pxPerUnit: Float) {
        this.pxPerUnit = pxPerUnit
        // SpriteBatch.end() turns blending off, so every shape pass re-enables it.
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        sr.projectionMatrix = projection
        sr.begin(ShapeRenderer.ShapeType.Filled)
    }

    fun end() = sr.end()

    fun color(c: Color, alpha: Float = 1f): Shapes {
        sr.setColor(tmp.set(c).also { it.a *= alpha })
        return this
    }

    private fun segs(r: Float): Int = (sqrt(max(1f, r * pxPerUnit)) * 4f).toInt().coerceIn(8, 96)

    fun rect(x: Float, y: Float, w: Float, h: Float) = sr.rect(x, y, w, h)

    /** A straight band of [width] between two points, square ends. */
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float) = sr.rectLine(x1, y1, x2, y2, width)

    /** A band with round caps, like canvas lineCap = 'round'. */
    fun capsule(x1: Float, y1: Float, x2: Float, y2: Float, width: Float) {
        val r = width / 2f
        sr.rectLine(x1, y1, x2, y2, width)
        circle(x1, y1, r)
        circle(x2, y2, r)
    }

    fun circle(x: Float, y: Float, r: Float) {
        if (r <= 0f) return
        sr.circle(x, y, r, segs(r))
    }

    /** An outline circle of stroke [width]; [dash] and [gap] (same units) make it dashed. */
    fun ring(x: Float, y: Float, r: Float, width: Float, dash: Float = 0f, gap: Float = 0f) {
        val n = segs(r) * 2
        val step = (2 * PI / n).toFloat()
        if (dash <= 0f) {
            var a0 = 0f
            for (i in 0 until n) {
                val a1 = a0 + step
                sr.rectLine(x + cos(a0) * r, y + sin(a0) * r, x + cos(a1) * r, y + sin(a1) * r, width)
                a0 = a1
            }
            return
        }
        val period = (dash + gap) / r // radians
        val on = dash / r
        var a = 0f
        while (a < 2 * PI) {
            arc(x, y, r, width, a, min(a + on, (2 * PI).toFloat()))
            a += period
        }
    }

    /** Stroked arc from [a0] to [a1] radians. */
    fun arc(x: Float, y: Float, r: Float, width: Float, a0: Float, a1: Float) {
        val n = max(2, (segs(r) * 2 * abs(a1 - a0) / (2 * PI)).toInt())
        val step = (a1 - a0) / n
        var a = a0
        for (i in 0 until n) {
            val b = a + step
            sr.rectLine(x + cos(a) * r, y + sin(a) * r, x + cos(b) * r, y + sin(b) * r, width)
            a = b
        }
    }

    /** Filled ellipse centred on (x, y). */
    fun ellipse(x: Float, y: Float, rx: Float, ry: Float) {
        val n = segs(max(rx, ry))
        val step = (2 * PI / n).toFloat()
        var px = x + rx
        var py = y
        for (i in 1..n) {
            val a = i * step
            val nx = x + cos(a) * rx
            val ny = y + sin(a) * ry
            sr.triangle(x, y, px, py, nx, ny)
            px = nx; py = ny
        }
    }

    /** Stroked arc of an ellipse-scaled circle: angle [a0]..[a1] rotated by [rot], scaled by sx, sy. */
    fun arcScaled(x: Float, y: Float, r: Float, width: Float, a0: Float, a1: Float, rot: Float, sx: Float, sy: Float) {
        val n = max(2, (segs(r) * 2 * abs(a1 - a0) / (2 * PI)).toInt())
        val step = (a1 - a0) / n
        var a = a0 + rot
        var px = x + cos(a) * r * sx
        var py = y + sin(a) * r * sy
        for (i in 0 until n) {
            a += step
            val nx = x + cos(a) * r * sx
            val ny = y + sin(a) * r * sy
            sr.rectLine(px, py, nx, ny, width)
            px = nx; py = ny
        }
    }

    /** Filled pie slice; angles in degrees. */
    fun pie(x: Float, y: Float, r: Float, startDeg: Float, deg: Float) = sr.arc(x, y, r, startDeg, deg, max(4, segs(r) / 2))

    fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = sr.triangle(x1, y1, x2, y2, x3, y3)

    /** Filled rounded rectangle; [r] is clamped to half the shorter side. */
    fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        val rr = min(r, min(w, h) / 2f)
        if (rr <= 0.01f) {
            sr.rect(x, y, w, h)
            return
        }
        sr.rect(x + rr, y, w - 2 * rr, h)
        sr.rect(x, y + rr, rr, h - 2 * rr)
        sr.rect(x + w - rr, y + rr, rr, h - 2 * rr)
        val s = max(2, segs(rr) / 4)
        sr.arc(x + rr, y + rr, rr, 180f, 90f, s)
        sr.arc(x + w - rr, y + rr, rr, 270f, 90f, s)
        sr.arc(x + w - rr, y + h - rr, rr, 0f, 90f, s)
        sr.arc(x + rr, y + h - rr, rr, 90f, 90f, s)
    }

    /** Rounded rectangle with only the top corners rounded (bottom sheets). */
    fun topRoundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        val rr = min(r, min(w, h) / 2f)
        sr.rect(x + rr, y, w - 2 * rr, h)
        sr.rect(x, y + rr, rr, h - rr)
        sr.rect(x + w - rr, y + rr, rr, h - rr)
        val s = max(2, segs(rr) / 4)
        sr.arc(x + rr, y + rr, rr, 180f, 90f, s)
        sr.arc(x + w - rr, y + rr, rr, 270f, 90f, s)
    }

    /** Outline of a rounded rectangle, drawn as a ring of [width] inside the bounds. */
    fun roundRectOutline(x: Float, y: Float, w: Float, h: Float, r: Float, width: Float) {
        val rr = min(r, min(w, h) / 2f)
        val hw = width / 2f
        sr.rectLine(x + rr, y + hw, x + w - rr, y + hw, width)
        sr.rectLine(x + rr, y + h - hw, x + w - rr, y + h - hw, width)
        sr.rectLine(x + hw, y + rr, x + hw, y + h - rr, width)
        sr.rectLine(x + w - hw, y + rr, x + w - hw, y + h - rr, width)
        val q = (PI / 2).toFloat()
        arc(x + rr, y + rr, rr - hw, width, 2 * q, 3 * q)
        arc(x + w - rr, y + rr, rr - hw, width, 3 * q, 4 * q)
        arc(x + w - rr, y + h - rr, rr - hw, width, 0f, q)
        arc(x + rr, y + h - rr, rr - hw, width, q, 2 * q)
    }

    /** Filled convex polygon from interleaved coordinates (triangle fan around the centroid). */
    fun fan(xy: FloatArray, count: Int) {
        var cx = 0f
        var cy = 0f
        for (i in 0 until count) { cx += xy[2 * i]; cy += xy[2 * i + 1] }
        cx /= count; cy /= count
        for (i in 0 until count) {
            val j = (i + 1) % count
            sr.triangle(cx, cy, xy[2 * i], xy[2 * i + 1], xy[2 * j], xy[2 * j + 1])
        }
    }

    /** A five-pointed star (point up) of outer radius [r]; [inner] is the inner radius as a fraction. */
    fun star(cx: Float, cy: Float, r: Float, inner: Float = 0.45f) {
        for (i in 0 until 10) {
            val a0 = (-PI / 2 + i * PI / 5).toFloat()
            val a1 = (-PI / 2 + (i + 1) * PI / 5).toFloat()
            val r0 = if (i % 2 == 0) r else r * inner
            val r1 = if (i % 2 == 0) r * inner else r
            sr.triangle(cx, cy, cx + cos(a0) * r0, cy + sin(a0) * r0, cx + cos(a1) * r1, cy + sin(a1) * r1)
        }
    }

    /** A filled heart of half-width [s] centred on ([cx], [cy]) (y down). */
    fun heart(cx: Float, cy: Float, s: Float) {
        val r = s * 0.52f
        circle(cx - r * 0.92f, cy - s * 0.28f, r)
        circle(cx + r * 0.92f, cy - s * 0.28f, r)
        sr.triangle(cx - s * 0.98f, cy - s * 0.1f, cx + s * 0.98f, cy - s * 0.1f, cx, cy + s * 0.95f)
    }

    /** Draws [n] evenly spaced zig-zag strokes along a segment, perpendicular amplitude [amp]. */
    fun zigzag(ax: Float, ay: Float, bx: Float, by: Float, k: Int, amp: Float, width: Float) {
        val len = hypot(bx - ax, by - ay)
        if (len <= 0f) return
        val nx = -(by - ay) / len
        val ny = (bx - ax) / len
        var px = 0f
        var py = 0f
        for (i in 0..k) {
            val u = i / k.toFloat()
            val z = if (i % 2 == 1) amp else -amp
            val x = ax + (bx - ax) * u + nx * z
            val y = ay + (by - ay) * u + ny * z
            if (i > 0) capsule(px, py, x, y, width)
            px = x; py = y
        }
    }

    override fun dispose() = sr.dispose()

    companion object {
        fun angle(x1: Float, y1: Float, x2: Float, y2: Float) = atan2(y2 - y1, x2 - x1)
    }
}
