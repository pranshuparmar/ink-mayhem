package com.pranshuparmar.inkmayhem.input

/** What a sample means. HOVER_EXIT and CANCEL extend HOVER | DOWN | MOVE | UP so the core knows when the nib left. */
enum class PenPhase { HOVER, HOVER_EXIT, DOWN, MOVE, UP, CANCEL }

enum class ToolType {
    STYLUS, ERASER, FINGER, MOUSE, UNKNOWN;

    val isPen: Boolean get() = this == STYLUS || this == ERASER
}

object PenButtons {
    /** S Pen side button (Android BUTTON_STYLUS_PRIMARY; Samsung also reports BUTTON_SECONDARY). */
    const val SIDE = 1
    /** Right mouse button (desktop / debug only). */
    const val SECONDARY = 2
}

/**
 * One platform-agnostic pen sample. Instances are mutable and reused by [PenQueue.drain] so the
 * hot input path never allocates.
 */
class PenSample {
    /** Screen position in pixels, origin top-left. */
    var x = 0f
    var y = 0f
    /** Raw pressure as reported by the platform, nominally 0..1. */
    var pressure = 0f
    /** Tilt in degrees, PointerEvent-style (X: left/right, Y: towards/away from the user). */
    var tiltX = 0f
    var tiltY = 0f
    /** Bit set of [PenButtons]. */
    var buttons = 0
    var phase = PenPhase.HOVER
    var toolType = ToolType.UNKNOWN
    var pointerId = 0
    var timeNanos = 0L
    /** True for samples recovered from Android's historical batch rather than the event head. */
    var historical = false

    fun set(other: PenSample): PenSample {
        x = other.x; y = other.y; pressure = other.pressure
        tiltX = other.tiltX; tiltY = other.tiltY; buttons = other.buttons
        phase = other.phase; toolType = other.toolType; pointerId = other.pointerId
        timeNanos = other.timeNanos; historical = other.historical
        return this
    }
}
