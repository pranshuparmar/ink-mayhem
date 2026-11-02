package com.pranshuparmar.inkmayhem.render

import com.badlogic.gdx.graphics.Color

/** Colour tokens from the POC's CSS custom properties. */
enum class Tok {
    PAPER, RULE, MARGIN, INK, GRAPHITE, ROCK, DROP, UI, UI_FG, MUTED, LINE, ACCENT, ACCENT_FG, ROPE, SPRING, BEAM, SHADOW,
    /** Slice 1: Blots (violet ink, never the runner's) and stars (gold, never a spring's amber). */
    BLOT, STAR,
    /** Worlds: Erasers' pink rubber, beetles' shells, paper (planes, the Airliner). */
    ERASER, BEETLE, PAPER_WHITE,
}

class Palette private constructor(private val colors: Array<Color>) {
    operator fun get(t: Tok): Color = colors[t.ordinal]

    companion object {
        private fun of(vararg pairs: Pair<Tok, String>): Palette {
            val arr = Array(Tok.entries.size) { Color(Color.MAGENTA) }
            for ((t, hex) in pairs) arr[t.ordinal] = Color.valueOf(hex)
            return Palette(arr)
        }

        val LIGHT = of(
            Tok.PAPER to "F6F8FC", Tok.RULE to "CFDBEE", Tok.MARGIN to "E7A0A0",
            Tok.INK to "1C2B4F", Tok.GRAPHITE to "687188", Tok.ROCK to "E1E6F0", Tok.DROP to "2C57CF",
            Tok.UI to "FFFFFF", Tok.UI_FG to "1C2B4F", Tok.MUTED to "5A6886", Tok.LINE to "D5DDEA",
            Tok.ACCENT to "2C57CF", Tok.ACCENT_FG to "FFFFFF",
            Tok.ROPE to "2A8656", Tok.SPRING to "B97C0C", Tok.BEAM to "C03D37",
            Tok.SHADOW to "1C2B4F29", // rgba(28, 43, 79, 0.16)
            Tok.BLOT to "6A3FB5", Tok.STAR to "E3A500",
            Tok.ERASER to "E88AA6", Tok.BEETLE to "2E5D52", Tok.PAPER_WHITE to "FFFFFF",
        )

        /**
         * World 5's ink-soaked page, light mode or dark: deep blue-black paper drawn on in pale ink,
         * the inks a little brighter than dark mode's so they glow.
         */
        val MIDNIGHT = of(
            Tok.PAPER to "0B0F1E", Tok.RULE to "1B2340", Tok.MARGIN to "3A2440",
            Tok.INK to "E8EEFF", Tok.GRAPHITE to "8F9BC2", Tok.ROCK to "141B33", Tok.DROP to "7FB0FF",
            Tok.UI to "161D35", Tok.UI_FG to "E8EEFF", Tok.MUTED to "98A5C8", Tok.LINE to "2A3456",
            Tok.ACCENT to "8FB0FF", Tok.ACCENT_FG to "0B0F1E",
            Tok.ROPE to "6EE0A0", Tok.SPRING to "F5C04F", Tok.BEAM to "FF8078",
            Tok.SHADOW to "00000099",
            Tok.BLOT to "C3A6FF", Tok.STAR to "FFE07A",
            Tok.ERASER to "F5A8C0", Tok.BEETLE to "76C9B4", Tok.PAPER_WHITE to "E8EEFF",
        )

        val DARK = of(
            Tok.PAPER to "121621", Tok.RULE to "222B3E", Tok.MARGIN to "4D2C36",
            Tok.INK to "DCE6FF", Tok.GRAPHITE to "8E99B2", Tok.ROCK to "1D2434", Tok.DROP to "7FA3FF",
            Tok.UI to "1A2030", Tok.UI_FG to "E3EAFB", Tok.MUTED to "95A3C1", Tok.LINE to "2B3448",
            Tok.ACCENT to "7FA3FF", Tok.ACCENT_FG to "0E1424",
            Tok.ROPE to "5CC98D", Tok.SPRING to "E8B24A", Tok.BEAM to "F07A73",
            Tok.SHADOW to "00000080", // rgba(0, 0, 0, 0.5)
            Tok.BLOT to "B597F5", Tok.STAR to "FFD04D",
            Tok.ERASER to "F2A3BB", Tok.BEETLE to "6BB8A4", Tok.PAPER_WHITE to "E8EDF8",
        )
    }
}
