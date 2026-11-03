package com.pranshuparmar.inkmayhem.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import com.pranshuparmar.inkmayhem.Settings
import com.pranshuparmar.inkmayhem.game.Sfx
import kotlin.math.exp

/**
 * Plays the synthesized sound effects. The pen scratch is a looped band-passed noise whose volume
 * and pitch glide towards their targets (time constants 0.03 s / 0.05 s).
 */
class Audio(private val settings: Settings) : Disposable {
    /** What the game wants from the music this frame. */
    enum class Music { STOPPED, PLAYING, PAUSED }

    private val sounds = HashMap<String, Sound>()
    private val bySfx = arrayOfNulls<Sound>(Sfx.entries.size)
    private val chimes = arrayOfNulls<Sound>(10)
    private val starSteps = arrayOfNulls<Sound>(8)
    /** The tune loaded now (a world's, or the boss's), by its file name under music/. */
    private var theme: Sound? = null
    private var themeName: String? = null
    private var themeId = -1L
    private var wantName: String? = null
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
        s?.play(settings.soundVolume)
    }

    /** The scratch while drawing: pitch follows pressure (1800 + (1 − p) × 2600 Hz). */
    fun scratch(vol: Float, p: Float) {
        targetVolume = vol * settings.soundVolume
        targetFreq = 1800f + (1f - p.coerceIn(0f, 1f)) * 2600f
    }

    /**
     * Call every frame: tune [name] starts, pauses (keeping its place), resumes or stops to match.
     * A different tune stops the one playing and takes its place.
     */
    fun music(want: Music, name: String) {
        musicWant = want
        wantName = name
    }

    /** Tunes loaded, by name: what [prepare] asked for, and the one playing. */
    private val tunes = HashMap<String, Sound>()

    /**
     * Loads [names] ahead of need (a stage's own tune and its boss's, during its intro card) so a
     * switch never waits on a load, and lets go of every other tune but the one playing.
     */
    fun prepare(names: Collection<String>) {
        for (n in names) if (n !in tunes) load(n)?.let { tunes[n] = it }
        val it = tunes.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key !in names && e.key != themeName) {
                e.value.dispose()
                it.remove()
            }
        }
    }

    private fun load(name: String): Sound? = try {
        // a tune is a looping sound, not streamed music: a sound loops without a gap
        val f = Gdx.files.internal("music/$name.wav")
        if (f.exists()) Gdx.audio.newSound(f) else null
    } catch (e: Exception) {
        Gdx.app.error("Audio", "music $name unavailable", e)
        null
    }

    private fun switchTheme(name: String) {
        theme?.let { t -> if (themeId != -1L) t.stop(themeId) }
        themeId = -1L
        musicIs = Music.STOPPED
        themeName = name
        theme = tunes[name] ?: load(name)?.also { tunes[name] = it }
    }

    private var appliedMusic = -1f

    private fun updateMusic() {
        val name = wantName
        if (name != null && name != themeName && musicWant == Music.PLAYING) switchTheme(name)
        val m = theme ?: return
        val target = if (settings.music) musicWant else Music.STOPPED
        when {
            target == musicIs -> Unit
            target == Music.PLAYING && musicIs == Music.STOPPED -> {
                appliedMusic = MUSIC_VOLUME * settings.musicVolume
                themeId = m.loop(appliedMusic) // SoundPool loads asynchronously: retry until it starts
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
        // the music volume slider takes effect on the tune playing
        val mv = MUSIC_VOLUME * settings.musicVolume
        if (musicIs != Music.STOPPED && themeId != -1L && kotlin.math.abs(mv - appliedMusic) > 0.002f) {
            theme?.setVolume(themeId, mv)
            appliedMusic = mv
        }
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
        tunes.values.forEach { it.dispose() }
        tunes.clear()
        theme = null
    }

    companion object {
        /** The theme under the effects. */
        const val MUSIC_VOLUME = 0.42f
    }
}
