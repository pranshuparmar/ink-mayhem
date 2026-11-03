package com.pranshuparmar.inkmayhem.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Particles, floating score pops and screen shake. Pure cosmetics:
 * updated once per sim tick and pooled so bursts don't allocate.
 */
class Fx {
    class Particle {
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
        var life = 0f; var r = 0f
        var color = Tok.INK
    }

    class Pop {
        var x = 0f; var y = 0f; var life = 0f
        var text = ""
        var color = Tok.INK
    }

    val particles = ArrayList<Particle>(256)
    val pops = ArrayList<Pop>(8)
    private val particlePool = ArrayDeque<Particle>()
    private val popPool = ArrayDeque<Pop>()
    private val rnd = Random(1234)

    /** Metres; decays 1 per second. */
    var shake = 0f
        private set
    var reducedMotion = false

    fun burst(x: Float, y: Float, n: Int, spread: Float, color: Tok, up: Float = Float.NaN) {
        val count = if (reducedMotion) min(n, 4) else n
        // an upward kick of 0 means the default (half the spread)
        val kick = if (up.isNaN() || up == 0f) spread * 0.5f else up
        repeat(count) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val s = spread * (0.4f + rnd.nextFloat())
            val p = particlePool.removeLastOrNull() ?: Particle()
            p.x = x; p.y = y
            p.vx = cos(a) * s
            p.vy = sin(a) * s - kick
            p.life = 0.5f + rnd.nextFloat() * 0.4f
            p.r = 0.03f + rnd.nextFloat() * 0.07f
            p.color = color
            particles += p
        }
    }

    fun pop(x: Float, y: Float, text: String, color: Tok) {
        val p = popPool.removeLastOrNull() ?: Pop()
        p.x = x; p.y = y; p.text = text; p.color = color; p.life = 1.6f
        pops += p
    }

    fun shake(amount: Float) {
        if (!reducedMotion) shake = max(shake, amount)
    }

    /** Called once per tick with the tick's dt (not slowed by the death time scale). */
    fun update(dt: Float) {
        var i = pops.size - 1
        while (i >= 0) {
            val q = pops[i]
            q.y -= dt * 0.8f
            q.life -= dt
            if (q.life <= 0f) popPool.addLast(pops.removeAt(i))
            i--
        }
        i = particles.size - 1
        while (i >= 0) {
            val q = particles[i]
            q.vy += 9f * dt
            q.x += q.vx * dt
            q.y += q.vy * dt
            q.life -= dt
            if (q.life <= 0f) particlePool.addLast(particles.removeAt(i))
            i--
        }
        shake = max(0f, shake - dt)
    }

    /** Random shake offset for this frame, in metres. */
    fun shakeOffset(): Float = if (shake > 0f) (rnd.nextFloat() - 0.5f) * shake * 0.8f else 0f

    fun clear() {
        particles.forEach { particlePool.addLast(it) }
        particles.clear()
        pops.forEach { popPool.addLast(it) }
        pops.clear()
        shake = 0f
    }
}
