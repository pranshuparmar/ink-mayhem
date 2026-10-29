package com.pranshuparmar.inkmayhem.audio

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards the shared asset folder that ships inside the APK. */
class AssetsTest {
    private val assets = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "android/src/main/assets") }
        .first { it.isDirectory }

    @Test
    fun `committed sound effects match the synthesizer`() {
        for ((name, pcm) in SfxSynth.all()) {
            val f = File(assets, "sfx/$name.wav")
            assertTrue(f.isFile, "missing ${f.path}; run ./gradlew :core:generateSfx")
            assertContentEquals(SfxSynth.wav(pcm), f.readBytes(), "${f.name} is stale; run ./gradlew :core:generateSfx")
        }
        assertEquals(SfxSynth.NAMES.toSet(), SfxSynth.all().keys)
    }

    @Test
    fun `committed music matches the synthesizer and loops in time`() {
        for ((name, pcm) in MusicSynth.all()) {
            val f = File(assets, "music/$name.wav")
            assertTrue(f.isFile, "missing ${f.path}; run ./gradlew :core:generateSfx")
            assertContentEquals(MusicSynth.wav(pcm), f.readBytes(), "${f.name} is stale; run ./gradlew :core:generateSfx")
            // small enough to play as a looping sound (Android's SoundPool holds about 1 MB decoded)
            assertTrue(f.length() < 1_000_000, "${f.name} is ${f.length()} bytes")
        }
        assertEquals(MusicSynth.NAMES.toSet(), MusicSynth.all().keys)
        // eight bars at 168 bpm, to the sample
        assertEquals(252_000, MusicSynth.INK_RUSH.loopSamples)
        var eighths = 0
        for ((_, len) in MusicSynth.INK_RUSH.lead) eighths += len
        assertEquals(64, eighths, "the melody fills the eight bars")
    }

    @Test
    fun `the asset folder holds only fonts, sounds and music`() {
        val top = assets.listFiles()!!.map { it.name }.toSet()
        assertEquals(setOf("fonts", "sfx", "music"), top, "unexpected files in ${assets.path} would ship in the APK")
        val fonts = File(assets, "fonts").listFiles()!!.filter { it.isFile }.map { it.name }.toSet()
        assertTrue(fonts.all { it.endsWith(".ttf") }, "fonts: $fonts")
        assertTrue(File(assets, "fonts/licenses").listFiles()!!.size == 3, "one OFL licence per family")
    }
}
