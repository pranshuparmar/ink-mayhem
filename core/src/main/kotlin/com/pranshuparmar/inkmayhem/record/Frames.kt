package com.pranshuparmar.inkmayhem.record

import com.badlogic.gdx.Gdx
import com.pranshuparmar.inkmayhem.Platform
import com.pranshuparmar.inkmayhem.input.PenQueue
import kotlin.math.min

/**
 * What the game reads from outside itself at the top of each frame: its time step, the clock,
 * system flags and safe-area insets. Live play reads the real ones; a replay feeds a
 * recording's, so the game can't tell the difference.
 */
interface FrameSource {
    /** Called first thing in each frame. */
    fun begin()

    /** After a pause, or once set up: the next step shouldn't count the time away. */
    fun reset()

    val dt: Float
    /** Seconds since launch. */
    val now: Double
    val dark: Boolean
    val reduced: Boolean
    /** Safe-area insets in pixels. */
    val insetL: Int
    val insetT: Int
    val insetR: Int
    val insetB: Int
}

/** The real clock and screen. */
class LiveFrames(private val platform: Platform) : FrameSource {
    private val start = System.nanoTime()
    private var last = start

    override var dt = 0f
        private set
    override var now = 0.0
        private set
    override var dark = false
        private set
    override var reduced = false
        private set
    override var insetL = 0
        private set
    override var insetT = 0
        private set
    override var insetR = 0
        private set
    override var insetB = 0
        private set

    override fun begin() {
        val t = System.nanoTime()
        dt = min(MAX_DT, (t - last) / 1e9f)
        last = t
        now = (t - start) / 1e9
        dark = platform.isDarkMode()
        reduced = platform.isReducedMotion()
        insetL = Gdx.graphics.safeInsetLeft
        insetT = Gdx.graphics.safeInsetTop
        insetR = Gdx.graphics.safeInsetRight
        insetB = Gdx.graphics.safeInsetBottom
    }

    override fun reset() {
        last = System.nanoTime()
    }

    companion object {
        /** A long hitch counts as this much, so the sim never leaps. */
        const val MAX_DT = 0.05f
    }
}

/** A recording's clock and screen, set by [Replay] before each frame. */
class ReplayFrames : FrameSource {
    override var dt = 0f
    override var now = 0.0
    override var dark = false
    override var reduced = false
    override var insetL = 0
    override var insetT = 0
    override var insetR = 0
    override var insetB = 0
    override fun begin() = Unit
    override fun reset() = Unit
}

/** What a replay calls on the game between frames. */
interface ReplayTarget {
    fun resize(w: Int, h: Int, density: Float)
    fun pause()
    fun resume()
    fun back()
}

/**
 * Steps through a recording: [next] applies everything up to the next frame to [target],
 * then loads that frame's clock into [frames] and its samples into [queue]. The caller then
 * renders one game frame.
 */
class Replay(val log: InputLog, private val frames: ReplayFrames, private val queue: PenQueue) {
    private var i = 0
    var frameIndex = -1
        private set

    fun next(target: ReplayTarget): InputLog.Step.Frame? {
        while (i < log.steps.size) {
            when (val s = log.steps[i++]) {
                is InputLog.Step.Resize -> target.resize(s.w, s.h, s.density)
                is InputLog.Step.Insets -> {
                    frames.insetL = s.l; frames.insetT = s.t; frames.insetR = s.r; frames.insetB = s.b
                }
                is InputLog.Step.Flags -> {
                    frames.dark = s.dark
                    frames.reduced = s.reduced
                }
                InputLog.Step.Pause -> target.pause()
                InputLog.Step.Resume -> target.resume()
                InputLog.Step.Back -> target.back()
                is InputLog.Step.Event -> Unit
                is InputLog.Step.Frame -> {
                    frames.dt = s.dt
                    frames.now = s.now
                    for (k in s.samples.indices) {
                        // the game drains the whole queue each frame, so it never fills here
                        check(queue.offer(s.samples[k])) { "pen queue full at frame ${frameIndex + 1}" }
                    }
                    frameIndex++
                    return s
                }
            }
        }
        return null
    }
}
