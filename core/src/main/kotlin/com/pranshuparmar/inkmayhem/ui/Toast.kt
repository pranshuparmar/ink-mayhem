package com.pranshuparmar.inkmayhem.ui

/**
 * The POC toast: one message at a time, shown for 2.2 s, and a repeat of the same message within
 * 3 s is ignored unless forced. Delayed toasts replace the POC's setTimeout calls.
 */
class Toast {
    var message: String = ""
        private set
    private var shownAt = -1e9
    private var lastAt = -1e9
    private var pending: String? = null
    private var pendingForce = false
    private var pendingAt = 0.0

    fun show(msg: String, now: Double, force: Boolean = false) {
        if (!force && msg == message && now - lastAt < REPEAT_S) return
        message = msg
        lastAt = now
        shownAt = now
    }

    fun showLater(msg: String, now: Double, delay: Double, force: Boolean = false) {
        pending = msg
        pendingForce = force
        pendingAt = now + delay
    }

    fun update(now: Double) {
        val p = pending ?: return
        if (now >= pendingAt) {
            pending = null
            show(p, now, pendingForce)
        }
    }

    /** 0..1 visibility with the CSS 0.2 s fade in and out. */
    fun visibility(now: Double, reducedMotion: Boolean): Float {
        val t = now - shownAt
        if (t < 0 || t > SHOW_S + FADE_S) return 0f
        if (reducedMotion) return if (t <= SHOW_S) 1f else 0f
        val fadeIn = (t / FADE_S).coerceIn(0.0, 1.0)
        val fadeOut = ((SHOW_S + FADE_S - t) / FADE_S).coerceIn(0.0, 1.0)
        return minOf(fadeIn, fadeOut).toFloat()
    }

    fun clear() {
        shownAt = -1e9
        pending = null
    }

    companion object {
        const val SHOW_S = 2.2
        const val FADE_S = 0.2
        const val REPEAT_S = 3.0
    }
}
