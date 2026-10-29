package com.pranshuparmar.inkmayhem.game

import com.pranshuparmar.inkmayhem.render.Tok

/** Every sound the POC synthesizes with WebAudio. */
enum class Sfx {
    ANCHOR, HOP, BOING, BOULDER_BOING, SNAP, ERASE, SPEED_UP, RUMBLE,
    PICKUP, PICKUP_HIGH, FEATHERWEIGHT, CHIME, COMBO_LOST, DEATH,
    // stages (new): short original jingles
    STAGE_START, CHECKPOINT, CLEAR, ONE_UP, GAME_OVER, STAR,
    // slice 1: a lost life, Blots, doodle boxes, the marker cap; star trails use [STAR_TRAIL] variants
    LIFE_LOST, SQUASH, POP, BOLD, SHRINK, STAR_TRAIL,
}

/**
 * Presentation hooks fired by [GameSim]. The sim itself stays headless; the game wires these to
 * audio, particles, toasts and haptics. All default to no-ops for tests.
 */
interface GameEvents {
    /** [variant] is the combo for [Sfx.CHIME], the streak step for [Sfx.STAR_TRAIL]. */
    fun sound(sfx: Sfx, variant: Int = 0) {}

    /** Pen scratch loop: volume 0..~0.14 and the live pressure (pitch). */
    fun scratch(volume: Float, pressure: Float) {}

    fun toast(message: String, force: Boolean = false, delaySec: Float = 0f) {}

    /** Particle burst in world space; [up] is the extra upward kick (NaN = spread / 2). */
    fun burst(x: Float, y: Float, n: Int, spread: Float, color: Tok, up: Float = Float.NaN) {}

    /** Floating score text in world space. */
    fun pop(x: Float, y: Float, text: String, color: Tok) {}

    /** Screen shake impulse (metres); ignored with reduced motion. */
    fun shake(amount: Float) {}

    fun haptic(kind: Haptic) {}

    fun died(death: Death) {}

    /** A new run is being built (POC newRun clears particles and pops). */
    fun runReset() {}

    /** Stages: the runner passed the midway bookmark. */
    fun checkpoint() {}

    /** Stages: the runner crossed the finish line. */
    fun stageCleared() {}

    /** Stages: the runner picked up a heart. */
    fun extraLife() {}

    /** Stages: a star was taken (every [GameSim.STARS_PER_LIFE] is a life). */
    fun star() {}

    object None : GameEvents
}

enum class Haptic { SNAP, DEATH }
