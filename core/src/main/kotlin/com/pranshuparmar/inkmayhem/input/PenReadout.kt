package com.pranshuparmar.inkmayhem.input

/**
 * Live pen state for the debug overlay: the last sample's tool, pressure, buttons and tilt, whether
 * the nib is hovering, and per-second rates of hover and contact events (one per MotionEvent) and
 * samples (including Android's historical batch).
 */
class PenReadout {
    var tool = ToolType.UNKNOWN
        private set
    var raw = 0f
        private set
    var normalized = 0f
    var buttons = 0
        private set
    var tiltX = 0f
        private set
    var tiltY = 0f
        private set
    var hovering = false
        private set
    var touching = false
        private set

    var hoverEventsPerSec = 0
        private set
    var hoverSamplesPerSec = 0
        private set
    var moveEventsPerSec = 0
        private set
    var moveSamplesPerSec = 0
        private set

    private var hoverEvents = 0
    private var hoverSamples = 0
    private var moveEvents = 0
    private var moveSamples = 0
    private var windowStart = -1.0

    fun onSample(s: PenSample) {
        tool = s.toolType
        raw = s.pressure
        buttons = s.buttons
        tiltX = s.tiltX
        tiltY = s.tiltY
        when (s.phase) {
            PenPhase.HOVER -> {
                hovering = true
                hoverSamples++
                if (!s.historical) hoverEvents++
            }
            PenPhase.HOVER_EXIT -> hovering = false
            PenPhase.DOWN -> {
                hovering = false
                touching = true
            }
            PenPhase.MOVE -> {
                moveSamples++
                if (!s.historical) moveEvents++
            }
            PenPhase.UP, PenPhase.CANCEL -> touching = false
        }
    }

    /** Rolls the per-second counters; call once per frame with a monotonic clock in seconds. */
    fun update(nowSec: Double) {
        if (windowStart < 0) windowStart = nowSec
        if (nowSec - windowStart >= 1.0) {
            hoverEventsPerSec = hoverEvents; hoverSamplesPerSec = hoverSamples
            moveEventsPerSec = moveEvents; moveSamplesPerSec = moveSamples
            hoverEvents = 0; hoverSamples = 0; moveEvents = 0; moveSamples = 0
            windowStart = nowSec
        }
    }
}
