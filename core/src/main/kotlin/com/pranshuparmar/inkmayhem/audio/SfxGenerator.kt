package com.pranshuparmar.inkmayhem.audio

import java.io.File

/** `./gradlew :core:generateSfx` writes every synthesized sound into the shared assets folder. */
object SfxGenerator {
    @JvmStatic
    fun main(args: Array<String>) {
        val dir = File(args.firstOrNull() ?: "android/src/main/assets/sfx")
        dir.mkdirs()
        for ((name, pcm) in SfxSynth.all()) {
            val f = File(dir, "$name.wav")
            f.writeBytes(SfxSynth.wav(pcm))
            println("${f.path}  ${f.length()} bytes")
        }
    }
}
