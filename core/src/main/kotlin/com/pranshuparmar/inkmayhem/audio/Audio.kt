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
    /** What the game wants from the music this frame. */
    enum class Music { STOPPED, PLAYING, PAUSED }

    private val sounds = HashMap<String, Sound>()
    private val bySfx = arrayOfNulls<Sound>(Sfx.entries.size)
    private val chimes = arrayOfNulls<Sound>(10)
    private val starSteps = arrayOfNulls<Sound>(8)
    private var theme: Sound? = null
    private var themeId = -1L
    private var musicWant = Music.STOPPED
    private var musicIs = Music.STOPPED
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
            // the theme is a looping sound, not streamed music: a sound loops without a gap
            val m = Gdx.files.internal("music/ink_rush.wav")
            if (m.exists()) theme = Gdx.audio.newSound(m)
        } catch (e: Exception) {
            Gdx.app.error("Audio", "sound effects unavailable", e)
        }
        for (s in Sfx.entries) bySfx[s.ordinal] = sounds[s.name.lowercase()]
        for (c in 2..9) chimes[c] = sounds["chime_$c"]
        for (k in 0..7) starSteps[k] = sounds["star_$k"]
        scratch = sounds["scratch"]
    }

    fun play(sfx: Sfx, variant: Int = 0) {
        if (!settings.sound) return
        val s = when (sfx) {
            Sfx.CHIME -> chimes[variant.coerceIn(2, 9)]
            Sfx.STAR_TRAIL -> starSteps[variant.coerceIn(0, 7)]
            else -> bySfx[sfx.ordinal]
        }
        s?.play(1f)
    }

    /** POC setScratch(vol, p): pitch follows pressure (1800 + (1 − p) × 2600 Hz). */
    fun scratch(vol: Float, p: Float) {
        targetVolume = if (settings.sound) vol else 0f
        targetFreq = 1800f + (1f - p.coerceIn(0f, 1f)) * 2600f
    }

    /** Call every frame: the theme starts, pauses (keeping its place), resumes or stops to match. */
    fun music(want: Music) {
        musicWant = want
    }

    private fun updateMusic() {
        val m = theme ?: return
        val target = if (settings.music) musicWant else Music.STOPPED
        when {
            target == musicIs -> Unit
            target == Music.PLAYING && musicIs == Music.STOPPED -> {
                themeId = m.loop(MUSIC_VOLUME) // SoundPool loads asynchronously: retry until it starts
                if (themeId != -1L) musicIs = Music.PLAYING
            }
            target == Music.PLAYING -> {
                m.resume(themeId)
                musicIs = Music.PLAYING
            }
            target == Music.PAUSED && musicIs == Music.PLAYING -> {
                m.pause(themeId)
                musicIs = Music.PAUSED
            }
            target == Music.STOPPED -> {
                m.stop(themeId)
                themeId = -1L
                musicIs = Music.STOPPED
            }
        }
    }

    fun update(dt: Float) {
        updateMusic()
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
        theme?.dispose()
        theme = null
    }

    companion object {
        /** The theme under the effects. */
        const val MUSIC_VOLUME = 0.42f
    }
}
