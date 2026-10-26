package com.pranshuparmar.inkmayhem.physics

import com.badlogic.gdx.utils.GdxNativesLoader

/** Loads libGDX's core natives for headless tests (a backend does this in the app). */
object TestNatives {
    init {
        GdxNativesLoader.load()
    }

    fun ensure() = Unit
}
