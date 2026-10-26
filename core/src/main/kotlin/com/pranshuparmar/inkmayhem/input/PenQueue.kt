package com.pranshuparmar.inkmayhem.input

import java.util.concurrent.atomic.AtomicLong

/**
 * Lock-free single-producer / single-consumer ring of pen samples.
 *
 * The producer is the platform input thread (Android UI thread, or the GL thread on desktop);
 * the consumer is the GL thread, which drains everything once per frame. Samples are stored as
 * parallel primitive arrays, so neither side allocates.
 */
class PenQueue(capacity: Int = 4096) {
    private val cap = Integer.highestOneBit(capacity.coerceAtLeast(2) * 2 - 1)
    private val mask = (cap - 1).toLong()
    private val xs = FloatArray(cap)
    private val ys = FloatArray(cap)
    private val pressures = FloatArray(cap)
    private val tiltXs = FloatArray(cap)
    private val tiltYs = FloatArray(cap)
    private val buttons = IntArray(cap)
    private val phases = ByteArray(cap)
    private val tools = ByteArray(cap)
    private val pointers = IntArray(cap)
    private val times = LongArray(cap)
    private val historical = BooleanArray(cap)

    private val head = AtomicLong(0) // next slot to read (consumer-owned)
    private val tail = AtomicLong(0) // next slot to write (producer-owned)

    /** Samples rejected because the consumer fell a whole ring behind. */
    @Volatile var dropped = 0L
        private set

    fun offer(
        x: Float, y: Float, pressure: Float, tiltX: Float, tiltY: Float, buttonState: Int,
        phase: PenPhase, tool: ToolType, pointerId: Int, timeNanos: Long, isHistorical: Boolean = false,
    ): Boolean {
        val t = tail.get()
        if (t - head.get() >= cap) {
            dropped++
            return false
        }
        val i = (t and mask).toInt()
        xs[i] = x; ys[i] = y; pressures[i] = pressure
        tiltXs[i] = tiltX; tiltYs[i] = tiltY; buttons[i] = buttonState
        phases[i] = phase.ordinal.toByte(); tools[i] = tool.ordinal.toByte()
        pointers[i] = pointerId; times[i] = timeNanos; historical[i] = isHistorical
        tail.lazySet(t + 1) // release: publishes the slot writes above
        return true
    }

    fun offer(s: PenSample): Boolean = offer(
        s.x, s.y, s.pressure, s.tiltX, s.tiltY, s.buttons, s.phase, s.toolType, s.pointerId, s.timeNanos, s.historical,
    )

    /** Delivers every queued sample to [handler] through the reused [into] instance. Returns the count. */
    inline fun drain(into: PenSample, handler: (PenSample) -> Unit): Int {
        var n = 0
        while (poll(into)) {
            handler(into)
            n++
        }
        return n
    }

    fun poll(into: PenSample): Boolean {
        val h = head.get()
        if (h >= tail.get()) return false // acquire: sees the producer's writes
        val i = (h and mask).toInt()
        into.x = xs[i]; into.y = ys[i]; into.pressure = pressures[i]
        into.tiltX = tiltXs[i]; into.tiltY = tiltYs[i]; into.buttons = buttons[i]
        into.phase = PHASES[phases[i].toInt()]; into.toolType = TOOLS[tools[i].toInt()]
        into.pointerId = pointers[i]; into.timeNanos = times[i]; into.historical = historical[i]
        head.lazySet(h + 1)
        return true
    }

    val size: Int get() = (tail.get() - head.get()).toInt()

    private companion object {
        val PHASES = PenPhase.entries.toTypedArray()
        val TOOLS = ToolType.entries.toTypedArray()
    }
}
