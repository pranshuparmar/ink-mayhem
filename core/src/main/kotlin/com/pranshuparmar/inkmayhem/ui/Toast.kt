package com.pranshuparmar.inkmayhem.ui

/**
 * Toasts: one message at a time, hidden 2.2 s after the latest show, and a repeat of the same
 * message within 3 s is ignored unless forced. Opacity eases over 0.2 s, so replacing a visible
 * message just swaps the text. A toast can also be scheduled for later.
 */
class Toast {
    var message: String = ""
        private set
    private var hideAt = -1e9
    private var lastAt = -1e9
    private var pending: String? = null
    private var pendingForce = false
    private var pendingAt = 0.0
    private var lastUpdate = Double.NaN

    /** 0..1, for fading and the 8 dp slide. */
    var visibility = 0f
        private set

    /** Told about each toast that is shown (input recordings note them). */
    var onShow: ((String) -> Unit)? = null

    fun show(msg: String, now: Double, force: Boolean = false) {
        if (!force && msg == message && now - lastAt < REPEAT_S) return
        onShow?.invoke(msg)
        message = msg
        lastAt = now
        hideAt = now + SHOW_S
    }

    fun showLater(msg: String, now: Double, delay: Double, force: Boolean = false) {
        pending = msg
        pendingForce = force
        pendingAt = now + delay
    }

    /** Call once per frame. */
    fun update(now: Double, reducedMotion: Boolean) {
        val p = pending
        if (p != null && now >= pendingAt) {
            pending = null
            show(p, now, pendingForce)
        }
        val dt = if (lastUpdate.isNaN()) 0.0 else now - lastUpdate
        lastUpdate = now
        val target = if (now < hideAt) 1f else 0f
        visibility = if (reducedMotion) target else PressureGauge.approach(visibility, target, (dt / FADE_S).toFloat())
    }

    fun clear() {
        hideAt = -1e9
        pending = null
    }

    companion object {
        const val SHOW_S = 2.2
        const val FADE_S = 0.2
        const val REPEAT_S = 3.0
    }
}
