package com.pranshuparmar.inkmayhem.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.LongMap

/** The three bundled OFL families from the POC. */
enum class Face(val file: String) {
    HAND("fonts/Kalam-Regular.ttf"),
    HAND_BOLD("fonts/Kalam-Bold.ttf"),
    UI("fonts/AtkinsonHyperlegible-Regular.ttf"),
    UI_BOLD("fonts/AtkinsonHyperlegible-Bold.ttf"),
    MONO("fonts/IBMPlexMono-Regular.ttf"),
    MONO_MEDIUM("fonts/IBMPlexMono-Medium.ttf"),
    MONO_BOLD("fonts/IBMPlexMono-Bold.ttf"),
}

/**
 * Generates bitmap fonts with gdx-freetype at device pixel size and caches them. Fonts are
 * flipped for the y-down cameras and scaled back so callers think in dp (CSS px).
 */
class Fonts(private var density: Float) : Disposable {
    private val generators = HashMap<Face, FreeTypeFontGenerator>()
    private val cache = LongMap<BitmapFont>() // primitive keys: get() runs hundreds of times a frame

    /** A font of [face] at [sizeDp], rendered crisp for the current density. */
    fun get(face: Face, sizeDp: Float, chars: String = TEXT): BitmapFont {
        val px = Math.round(sizeDp * density).coerceAtLeast(6)
        val key = (face.ordinal.toLong() shl 40) or (px.toLong() shl 20) or (chars.hashCode().toLong() and 0xFFFFF)
        return cache.get(key) ?: generate(face, px, chars).also { cache.put(key, it) }
    }

    private fun generate(face: Face, px: Int, chars: String): BitmapFont {
        val gen = generators.getOrPut(face) { FreeTypeFontGenerator(Gdx.files.internal(face.file)) }
        val p = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            size = px
            flip = true
            characters = chars
            minFilter = Texture.TextureFilter.Linear
            magFilter = Texture.TextureFilter.Linear
            kerning = true
            hinting = FreeTypeFontGenerator.Hinting.AutoMedium
        }
        val font = gen.generateFont(p)
        font.data.setScale(1f / density)
        font.setUseIntegerPositions(false)
        font.data.markupEnabled = false
        return font
    }

    /** Drops every cached font, e.g. when the display density changes. */
    fun setDensity(d: Float) {
        if (d == density) return
        density = d
        cache.values().forEach { it.dispose() }
        cache.clear()
    }

    override fun dispose() {
        cache.values().forEach { it.dispose() }
        cache.clear()
        generators.values.forEach { it.dispose() }
        generators.clear()
    }

    companion object {
        /** Everything the game writes, including the POC's typographic extras. */
        val TEXT = FreeTypeFontGenerator.DEFAULT_CHARS + "×·‘’“”…→—–°"
        const val DIGITS = "0123456789,.-+×mpts "
    }
}
