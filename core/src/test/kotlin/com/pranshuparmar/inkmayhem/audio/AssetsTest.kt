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
    fun `the asset folder holds only fonts and sounds`() {
        val top = assets.listFiles()!!.map { it.name }.toSet()
        assertEquals(setOf("fonts", "sfx"), top, "unexpected files in ${assets.path} would ship in the APK")
        val fonts = File(assets, "fonts").listFiles()!!.filter { it.isFile }.map { it.name }.toSet()
        assertTrue(fonts.all { it.endsWith(".ttf") }, "fonts: $fonts")
        assertTrue(File(assets, "fonts/licenses").listFiles()!!.size == 3, "one OFL licence per family")
    }
}
