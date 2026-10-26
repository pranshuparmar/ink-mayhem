package com.pranshuparmar.inkmayhem

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Align
import com.pranshuparmar.inkmayhem.input.PenReadout
import com.pranshuparmar.inkmayhem.input.PenSample
import com.pranshuparmar.inkmayhem.input.Pressure
import com.pranshuparmar.inkmayhem.lab.PenLab
import com.pranshuparmar.inkmayhem.render.Face
import com.pranshuparmar.inkmayhem.render.Fonts
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok
import com.pranshuparmar.inkmayhem.ui.DebugOverlay
import com.pranshuparmar.inkmayhem.ui.PressureGauge
import com.pranshuparmar.inkmayhem.ui.Toast
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class InkMayhemGame(private val platform: Platform) : ApplicationAdapter() {
    private lateinit var settings: Settings
    private lateinit var shapes: Shapes
    private lateinit var batch: SpriteBatch
    private lateinit var text: Text
    private lateinit var fonts: Fonts
    private val uiCam = OrthographicCamera()
    private var pal = Palette.LIGHT

    private var density = 1f
    private var wDp = 1f
    private var hDp = 1f
    /** Pixels (dp) per metre: clamp(min(W, H) / 12, 22, 64). */
    private var scale = 30f

    private val sample = PenSample()
    private val readout = PenReadout()
    private val overlay = DebugOverlay()
    private val gauge = PressureGauge()
    private val toast = Toast()
    private lateinit var lab: PenLab

    private var lastNanos = 0L
    private val startNanos = System.nanoTime()
    private val now: Double get() = (System.nanoTime() - startNanos) / 1e9

    override fun create() {
        settings = Settings(Gdx.app.getPreferences("ink-mayhem"))
        shapes = Shapes()
        batch = SpriteBatch()
        text = Text(batch)
        density = platform.density
        fonts = Fonts(density)
        lab = PenLab { toast.show(FINGER_TOAST, now) }
        lastNanos = System.nanoTime()
    }

    override fun resize(width: Int, height: Int) {
        density = platform.density
        fonts.setDensity(density)
        wDp = width / density
        hDp = height / density
        uiCam.setToOrtho(true, wDp, hDp)
        uiCam.update()
        scale = (min(wDp, hDp) / 12f).coerceIn(22f, 64f)
    }

    override fun render() {
        val t = System.nanoTime()
        val dt = min(0.05f, (t - lastNanos) / 1e9f)
        lastNanos = t
        val clock = now
        pal = if (platform.isDarkMode()) Palette.DARK else Palette.LIGHT
        val reduced = platform.isReducedMotion()

        val cal = settings.calibration
        val allowFinger = platform.isDebugBuild && settings.allowFinger
        platform.penQueue.drain(sample) { s ->
            val p = if (s.toolType.isPen) Pressure.normalize(s.pressure, cal) else Pressure.FINGER
            readout.onSample(s)
            readout.normalized = p
            lab.onSample(s, s.x / density, s.y / density, p, allowFinger, scale)
        }
        readout.update(clock)
        toast.update(clock)
        gauge.update(dt, lab.livePressure >= 0f, reduced)
        overlay.update(clock, readout, cal ?: Pressure.DEFAULT_CAL, listOf("device    ${platform.description}"))

        val paper = pal[Tok.PAPER]
        Gdx.gl.glClearColor(paper.r, paper.g, paper.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        val safeLeft = Gdx.graphics.safeInsetLeft / density
        val safeTop = Gdx.graphics.safeInsetTop / density
        val safeRight = Gdx.graphics.safeInsetRight / density
        val gaugeLeft = safeLeft + 10f
        val gaugeTop = hDp * 0.3f

        shapes.begin(uiCam.combined, density)
        drawPage()
        lab.draw(shapes, pal, scale, tint = true)
        gauge.drawShapes(shapes, pal, gaugeLeft, gaugeTop, lab.livePressure)
        val monoFont = overlay.font(fonts)
        val boxW = overlay.width(text, monoFont)
        val boxH = overlay.layoutHeight(monoFont)
        val boxRight = wDp - max(12f, safeRight)
        val boxTop = safeTop + 64f
        overlay.drawBox(shapes, pal, boxRight, boxTop, boxW, boxH)
        val tv = toast.visibility(clock, reduced)
        val toastFont = fonts.get(Face.UI_BOLD, 14f)
        val toastW = text.width(toastFont, toast.message) + 28f
        val toastTop = safeTop + 70f - (if (reduced) 0f else 8f * (1f - tv))
        if (tv > 0f) shapes.color(pal[Tok.INK], tv).roundRect(wDp / 2f - toastW / 2f, toastTop, toastW, 34f, 17f)
        shapes.end()

        batch.projectionMatrix = uiCam.combined
        batch.begin()
        val titleFont = fonts.get(Face.HAND_BOLD, 32f)
        val introLeft = min(56f, wDp * 0.12f) + 14f
        val safeBottom = Gdx.graphics.safeInsetBottom / density
        text.draw(titleFont, "Ink Mayhem · pen lab", introLeft, hDp - safeBottom - 150f, pal[Tok.INK])
        text.draw(
            fonts.get(Face.UI, 15f),
            "Hover to see the nib. Press lightly for rope, write normally for spring, press hard for beam. " +
                "Hold the side button while touching to erase.",
            introLeft, hDp - safeBottom - 104f, pal[Tok.MUTED], wrapWidth = min(420f, wDp - introLeft - 16f),
        )
        gauge.drawText(text, fonts, pal, gaugeLeft, gaugeTop, lab.livePressure)
        overlay.drawText(text, monoFont, pal, boxRight - boxW, boxTop)
        if (tv > 0f) text.line(toastFont, toast.message, wDp / 2f, toastTop, 34f, pal[Tok.PAPER], tv, Align.center)
        batch.end()
    }

    /** Ruled lines every 30 dp and the red margin, fixed on screen. */
    private fun drawPage() {
        shapes.color(pal[Tok.RULE])
        var y = 30f
        while (y < hDp) {
            shapes.rect(0f, floor(y), wDp, 1f)
            y += 30f
        }
        shapes.color(pal[Tok.MARGIN]).rect(min(56f, wDp * 0.12f) - 0.75f, 0f, 1.5f, hDp)
    }

    override fun dispose() {
        shapes.dispose()
        batch.dispose()
        fonts.dispose()
    }

    companion object {
        const val FINGER_TOAST = "Use the S Pen. Fingers don’t leave ink."
    }
}
