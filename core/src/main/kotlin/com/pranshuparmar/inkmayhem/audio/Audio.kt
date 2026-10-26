package com.pranshuparmar.inkmayhem.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import com.pranshuparmar.inkmayhem.Settings
import com.pranshuparmar.inkmayhem.game.Sfx
import kotlin.math.exp

/**
 * Plays the synthesized sound effects. The pen scratch is a looped band-passed noise whose volume
 * and pitch glide towards their targets like the POC's setTargetAtTime (0.03 s / 0.05 s).
 */
class Audio(private val settings: Settings) : Disposable {
    private val sounds = HashMap<String, Sound>()
    private val bySfx = arrayOfNulls<Sound>(Sfx.entries.size)
    private val chimes = arrayOfNulls<Sound>(10)
    private var scratch: Sound? = null
    private var scratchId = -1L

    private var targetVolume = 0f
    private var targetFreq = 3000f
    private var volume = 0f
    private var freq = 3000f
    private var appliedVolume = -1f
    private var appliedPitch = -1f

    init {
        try {
            for (name in SfxSynth.NAMES) {
                val f = Gdx.files.internal("sfx/$name.wav")
                if (f.exists()) sounds[name] = Gdx.audio.newSound(f)
            }
        } catch (e: Exception) {
            Gdx.app.error("Audio", "sound effects unavailable", e)
        }
        for (s in Sfx.entries) bySfx[s.ordinal] = sounds[s.name.lowercase()]
        for (c in 2..9) chimes[c] = sounds["chime_$c"]
        scratch = sounds["scratch"]
    }

    fun play(sfx: Sfx, variant: Int = 0) {
        if (!settings.sound) return
        val s = if (sfx == Sfx.CHIME) chimes[variant.coerceIn(2, 9)] else bySfx[sfx.ordinal]
        s?.play(1f)
    }

    /** POC setScratch(vol, p): pitch follows pressure (1800 + (1 − p) × 2600 Hz). */
    fun scratch(vol: Float, p: Float) {
        targetVolume = if (settings.sound) vol else 0f
        targetFreq = 1800f + (1f - p.coerceIn(0f, 1f)) * 2600f
    }

    fun update(dt: Float) {
        val s = scratch ?: return
        volume += (targetVolume - volume) * (1f - exp(-dt / 0.03f))
        freq += (targetFreq - freq) * (1f - exp(-dt / 0.05f))
        val v = (volume / SfxSynth.SCRATCH_REF).coerceIn(0f, 1f)
        val pitch = (freq / SfxSynth.SCRATCH_CENTER.toFloat()).coerceIn(0.5f, 2f)
        if (scratchId == -1L) {
            if (v < 0.001f) return
            scratchId = s.loop(0f, pitch, 0f) // SoundPool loads asynchronously: retry until it starts
            if (scratchId == -1L) return
            appliedVolume = -1f
        }
        if (kotlin.math.abs(v - appliedVolume) > 0.002f) {
            s.setVolume(scratchId, v)
            appliedVolume = v
        }
        if (kotlin.math.abs(pitch - appliedPitch) > 0.005f) {
            s.setPitch(scratchId, pitch)
            appliedPitch = pitch
        }
    }

    override fun dispose() {
        sounds.values.forEach { it.dispose() }
        sounds.clear()
    }
}
