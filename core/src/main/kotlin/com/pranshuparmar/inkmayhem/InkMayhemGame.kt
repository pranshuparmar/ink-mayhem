package com.pranshuparmar.inkmayhem

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.Align
import com.pranshuparmar.inkmayhem.audio.Audio
import com.pranshuparmar.inkmayhem.game.Death
import com.pranshuparmar.inkmayhem.game.GameEvents
import com.pranshuparmar.inkmayhem.game.GameSim
import com.pranshuparmar.inkmayhem.game.GameState
import com.pranshuparmar.inkmayhem.game.Haptic
import com.pranshuparmar.inkmayhem.game.InkBot
import com.pranshuparmar.inkmayhem.game.Sfx
import com.pranshuparmar.inkmayhem.input.PenButtons
import com.pranshuparmar.inkmayhem.input.PenPhase
import com.pranshuparmar.inkmayhem.input.PenReadout
import com.pranshuparmar.inkmayhem.input.PenSample
import com.pranshuparmar.inkmayhem.input.Pressure
import com.pranshuparmar.inkmayhem.input.ToolType
import com.pranshuparmar.inkmayhem.render.Face
import com.pranshuparmar.inkmayhem.render.Fonts
import com.pranshuparmar.inkmayhem.render.Fx
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok
import com.pranshuparmar.inkmayhem.render.WorldRenderer
import com.pranshuparmar.inkmayhem.ui.CalibrationOverlay
import com.pranshuparmar.inkmayhem.ui.DeathCard
import com.pranshuparmar.inkmayhem.ui.DebugOverlay
import com.pranshuparmar.inkmayhem.ui.Frame
import com.pranshuparmar.inkmayhem.ui.Hud
import com.pranshuparmar.inkmayhem.ui.Press
import com.pranshuparmar.inkmayhem.ui.PressureGauge
import com.pranshuparmar.inkmayhem.ui.SettingsSheet
import com.pranshuparmar.inkmayhem.ui.TitleCard
import com.pranshuparmar.inkmayhem.ui.Toast
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Desktop-only conveniences for development and screenshots. */
class DevOptions(
    val seed: Long? = null,
    val autoplay: Boolean = false,
    val openSettings: Boolean = false,
    val openCalibration: Boolean = false,
    val skipCalibration: Boolean = false,
    /** Autoplay draws this ink for these hazards instead of par (to reach the death card). */
    val wrongInk: Map<com.pranshuparmar.inkmayhem.game.HazardType, com.pranshuparmar.inkmayhem.physics.Ink> = emptyMap(),
)

class InkMayhemGame(private val platform: Platform, private val dev: DevOptions = DevOptions()) : ApplicationAdapter() {
    private lateinit var settings: Settings
    private lateinit var shapes: Shapes
    private lateinit var batch: SpriteBatch
    private lateinit var text: Text
    private lateinit var fonts: Fonts
    private lateinit var audio: Audio
    private lateinit var sim: GameSim
    private lateinit var world: WorldRenderer
    private lateinit var titleCard: TitleCard
    private lateinit var deathCard: DeathCard
    private lateinit var sheet: SettingsSheet
    private lateinit var calibration: CalibrationOverlay
    private val uiCam = OrthographicCamera()
    private var pal = Palette.LIGHT
    private val fx = Fx()
    private val hud = Hud()
    private val gauge = PressureGauge()
    private val toast = Toast()
    private val overlay = DebugOverlay()
    private val readout = PenReadout()
    private val frame = Frame()
    private val hudPress = Press()
    private var bot: InkBot? = null

    private var density = 1f
    private var wDp = 1f
    private var hDp = 1f
    /** Pixels (dp) per metre: clamp(min(W, H) / 12, 22, 64). */
    private var scale = 30f
    private var sized = false

    private val sample = PenSample()
    private var lastNanos = 0L
    private val startNanos = System.nanoTime()
    private val clock: Double get() = (System.nanoTime() - startNanos) / 1e9
    private var acc = 0f
    private var paused = false
    private var lastSnapHaptic = -1.0

    override fun create() {
        settings = Settings(Gdx.app.getPreferences("ink-mayhem"))
        shapes = Shapes()
        batch = SpriteBatch()
        text = Text(batch)
        density = platform.density
        fonts = Fonts(density)
        audio = Audio(settings)
        sim = GameSim(seed = dev.seed ?: System.nanoTime(), events = Events(), best = settings.best)
        world = WorldRenderer(shapes, text, fonts)
        titleCard = TitleCard(fonts)
        deathCard = DeathCard(fonts)
        sheet = SettingsSheet(fonts, settings, platform.isDebugBuild, onRecalibrate = { calibration.show() }, onChanged = ::onSettingsChanged)
        calibration = CalibrationOverlay(
            fonts,
            onDone = { cal ->
                settings.calibration = cal
                toast.show("Calibrated. Your normal pressure makes a spring.", clock, force = true)
            },
            onSkip = { if (settings.calibration == null) settings.calibration = Pressure.DEFAULT_CAL },
        )
        if (dev.openCalibration || (settings.calibration == null && !dev.skipCalibration)) calibration.show()
        if (dev.openSettings) sheet.show()
        // Android back closes an overlay first, then leaves the game as usual.
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun keyDown(keycode: Int): Boolean {
                if (keycode != Input.Keys.BACK) return false
                when {
                    calibration.open -> calibration.skip()
                    sheet.open -> sheet.hide()
                    else -> Gdx.app.exit()
                }
                return true
            }
        }
        lastNanos = System.nanoTime()
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val d = platform.density
        if (d != density) {
            density = d
            fonts.setDensity(d)
            titleCard.invalidate(); deathCard.invalidate(); calibration.invalidate()
        }
        wDp = width / density
        hDp = height / density
        uiCam.setToOrtho(true, wDp, hDp)
        uiCam.update()
        scale = (min(wDp, hDp) / 12f).coerceIn(22f, 64f)
        sim.scale = scale
        sim.setViewport(wDp / scale, hDp / scale)
        if (!sized && sim.state == GameState.TITLE) sim.newRun() // frame the start with the real viewport
        sized = true
        warmFonts()
    }

    /** FreeType rasterizes on first use; do it up front so the first pop or death card doesn't hitch. */
    private fun warmFonts() {
        for (size in floatArrayOf(10f, 11f, 12f, 16f)) {
            fonts.get(Face.MONO, size); fonts.get(Face.MONO_MEDIUM, size); fonts.get(Face.MONO_BOLD, size)
        }
        for (size in floatArrayOf(12.5f, 14f, 14.5f, 15f)) { fonts.get(Face.UI, size); fonts.get(Face.UI_BOLD, size) }
        for (size in floatArrayOf(20f, 32f, 40f)) fonts.get(Face.HAND_BOLD, size)
        fonts.get(Face.HAND, 20f)
        for (size in floatArrayOf(30f, 40f, 64f)) fonts.get(Face.HAND_BOLD, size, Fonts.DIGITS)
        fonts.get(Face.HAND, max(15f, scale * 0.6f))
        fonts.get(Face.HAND_BOLD, max(18f, scale * 0.75f))
        fonts.get(Face.MONO_BOLD, max(14f, scale * 0.5f))
    }

    override fun pause() {
        paused = true
        audio.scratch(0f, 0.5f)
    }

    override fun resume() {
        paused = false
        lastNanos = System.nanoTime()
    }

    override fun render() {
        val t = System.nanoTime()
        val dt = min(0.05f, (t - lastNanos) / 1e9f)
        lastNanos = t
        val now = clock
        pal = if (platform.isDarkMode()) Palette.DARK else Palette.LIGHT
        val reduced = platform.isReducedMotion()
        fx.reducedMotion = reduced
        sim.realTime = now
        sim.tint = settings.tint

        platform.penQueue.drain(sample) { onSample(it, now) }

        if (!paused) {
            acc += dt
            var n = 0
            while (acc >= STEP && n < 4) {
                bot?.update()
                sim.tick(STEP)
                fx.update(STEP)
                acc -= STEP
                n++
            }
            if (n == 4) acc = 0f
            sim.cull(dt)
        }
        if (dev.autoplay) autoplay(now)

        readout.update(now)
        toast.update(now)
        hud.update(dt, sim, settings.best, reduced)
        gauge.update(dt, sim.drawing != null, reduced)
        audio.update(dt)

        draw(now, reduced)
    }

    // ------------------------------------------------------------------------------------- input

    private val allowFinger: Boolean get() = platform.isDebugBuild && settings.allowFinger

    private fun onSample(s: PenSample, now: Double) {
        val n = if (s.toolType.isPen) Pressure.normalize(s.pressure, settings.calibration) else Pressure.FINGER
        readout.onSample(s)
        readout.normalized = n
        val x = s.x / density
        val y = s.y / density
        val allowed = s.toolType.isPen || allowFinger
        if (calibration.open) {
            calibration.onSample(s, x, y, allowed)
            return
        }
        if (sheet.open) {
            sheet.onSample(s, x, y)
            return
        }
        val wx = world.viewX + x / scale
        val wy = world.viewY + y / scale
        val time = s.timeNanos / 1e9
        when (s.phase) {
            PenPhase.DOWN -> {
                if (hud.rightArea.contains(x, y)) {
                    hudPress.down(s.pointerId, if (hud.settingsButton.contains(x, y)) hud.settingsButton else null)
                    return
                }
                if (!allowed) {
                    toast.show(FINGER_TOAST, now)
                    return
                }
                world.hoverOn = false
                val erase = s.toolType == ToolType.ERASER || (s.buttons and (PenButtons.SIDE or PenButtons.SECONDARY)) != 0
                sim.penDown(s.pointerId, wx, wy, n, erase, time)
            }
            PenPhase.MOVE -> sim.penMove(s.pointerId, wx, wy, n, time)
            PenPhase.UP, PenPhase.CANCEL -> {
                if (s.pointerId == hudPress.pointer) {
                    if (hudPress.up(s.pointerId, if (hud.settingsButton.contains(x, y)) hud.settingsButton else null) != null && s.phase == PenPhase.UP) {
                        sheet.show()
                    }
                    return
                }
                sim.penUp(s.pointerId)
            }
            PenPhase.HOVER -> if (s.toolType.isPen || (s.toolType == ToolType.MOUSE && allowFinger)) {
                world.hoverOn = true
                world.hoverX = x
                world.hoverY = y
            }
            PenPhase.HOVER_EXIT -> world.hoverOn = false
        }
    }

    private fun onSettingsChanged() {
        if (!settings.sound) audio.scratch(0f, 0.5f)
    }

    private fun autoplay(now: Double) {
        when (sim.state) {
            GameState.TITLE -> {
                sim.penDown(0, 0f, 0f, 0.45f, erase = false, t = now)
                bot = newBot()
            }
            // With wrongInk the bot is demonstrating a death: stay on the death card.
            GameState.DEAD -> if (dev.wrongInk.isEmpty() && now - sim.deadAt > 1.5) {
                sim.penDown(0, 0f, 0f, 0.45f, erase = false, t = now)
                bot = newBot()
            }
            GameState.RUN -> if (bot == null) bot = newBot()
        }
    }

    private fun newBot() = InkBot(sim) { dev.wrongInk[it] ?: it.par ?: com.pranshuparmar.inkmayhem.physics.Ink.ROPE }

    // ------------------------------------------------------------------------------------ events

    private inner class Events : GameEvents {
        override fun sound(sfx: Sfx, variant: Int) = audio.play(sfx, variant)
        override fun scratch(volume: Float, pressure: Float) = audio.scratch(volume, pressure)
        override fun toast(message: String, force: Boolean, delaySec: Float) {
            if (delaySec > 0f) toast.showLater(message, clock, delaySec.toDouble(), force) else toast.show(message, clock, force)
        }

        override fun burst(x: Float, y: Float, n: Int, spread: Float, color: Tok, up: Float) = fx.burst(x, y, n, spread, color, up)

        override fun pop(x: Float, y: Float, text: String, color: Tok) = fx.pop(x, y, text, color)
        override fun shake(amount: Float) = fx.shake(amount)
        override fun haptic(kind: Haptic) {
            if (!settings.haptics) return
            when (kind) {
                Haptic.SNAP -> if (clock - lastSnapHaptic > 0.08) {
                    lastSnapHaptic = clock
                    platform.vibrate(SNAP_BUZZ_MS, 140)
                }
                Haptic.DEATH -> platform.vibrate(DEATH_BUZZ_MS, 255)
            }
        }

        override fun died(death: Death) {
            if (death.isBest) settings.best = death.best
        }
    }

    // ------------------------------------------------------------------------------------ drawing

    private fun draw(now: Double, reduced: Boolean) {
        val paper = pal[Tok.PAPER]
        Gdx.gl.glClearColor(paper.r, paper.g, paper.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        world.render(sim, fx, pal, settings.tint, uiCam, wDp, hDp, scale, density, (acc / STEP).coerceIn(0f, 1f))

        val safeL = Gdx.graphics.safeInsetLeft / density
        val safeT = Gdx.graphics.safeInsetTop / density
        val safeR = Gdx.graphics.safeInsetRight / density
        val safeB = Gdx.graphics.safeInsetBottom / density
        frame.wDp = wDp; frame.hDp = hDp; frame.safeL = safeL; frame.safeT = safeT; frame.safeR = safeR; frame.safeB = safeB

        // HUD
        hud.layout(text, fonts, wDp, safeL, safeT, safeR)
        val ink = max(0f, sim.inkLeft - (sim.drawing?.cost ?: 0f))
        val drawing = sim.drawing
        val live = if (drawing == null || drawing.smoothed.isNaN()) -1f else drawing.smoothed
        val gaugeLeft = safeL + 10f
        val gaugeTop = hDp * 0.3f
        val showReadout = settings.penReadout
        if (showReadout) overlay.update(now, readout, settings.calibration ?: Pressure.DEFAULT_CAL, readoutExtras)
        val monoFont = overlay.font(fonts)
        val boxW = if (showReadout) overlay.width(text, monoFont) else 0f
        val boxH = if (showReadout) overlay.layoutHeight(monoFont) else 0f
        val boxRight = wDp - max(12f, safeR)
        val boxTop = safeT + 64f
        val tv = toast.visibility(now, reduced)
        val toastFont = fonts.get(Face.UI_BOLD, 14f)
        val toastW = min(text.width(toastFont, toast.message) + 28f, wDp * 0.92f)
        val toastTop = safeT + 70f - (if (reduced) 0f else 8f * (1f - tv))

        shapes.begin(uiCam.combined, density)
        hud.drawShapes(shapes, pal, wDp, ink, sim.combo, hudPress.isPressed(hud.settingsButton))
        gauge.drawShapes(shapes, pal, gaugeLeft, gaugeTop, live)
        if (showReadout) overlay.drawBox(shapes, pal, boxRight, boxTop, boxW, boxH)
        if (tv > 0f) shapes.color(pal[Tok.INK], tv).roundRect(wDp / 2f - toastW / 2f, toastTop, toastW, 34f, 17f)
        shapes.end()
        batch.projectionMatrix = uiCam.combined
        batch.begin()
        hud.drawText(text, fonts, pal, wDp, sim.combo)
        gauge.drawText(text, fonts, pal, gaugeLeft, gaugeTop, live)
        if (showReadout) overlay.drawText(text, monoFont, pal, boxRight - boxW, boxTop)
        if (tv > 0f) text.line(toastFont, toast.message, wDp / 2f, toastTop, 34f, pal[Tok.PAPER], tv, Align.center)
        batch.end()

        // Cards: the title card until the first pen-down; the death card 0.6 s after dying.
        if (sim.state == GameState.TITLE) {
            titleCard.layout(text, frame, settings.best)
            pass({ titleCard.drawShapes(shapes, pal, 1f) }, { titleCard.drawText(text, pal, 1f) })
        } else if (sim.state == GameState.DEAD && now - sim.deadAt >= 0.6) {
            val d = sim.death
            if (d != null) {
                deathCard.layout(text, frame, d)
                pass({ deathCard.drawShapes(shapes, pal) }, { deathCard.drawText(text, pal) })
            }
        }
        if (sheet.open) {
            sheet.layout(text, frame)
            pass({ sheet.drawShapes(shapes, pal, frame) }, { sheet.drawText(text, pal) })
        }
        if (calibration.open) {
            calibration.layout(text, frame)
            pass({ calibration.drawShapes(shapes, pal, frame) }, { calibration.drawText(text, pal) })
        }
    }

    private inline fun pass(shapesPass: () -> Unit, textPass: () -> Unit) {
        shapes.begin(uiCam.combined, density)
        shapesPass()
        shapes.end()
        batch.projectionMatrix = uiCam.combined
        batch.begin()
        textPass()
        batch.end()
    }

    /** Held in a field: a bound function reference would allocate on every frame. */
    private val readoutExtras: () -> List<String> = { readoutLines() }

    private fun readoutLines(): List<String> = listOf(
        "speed     ${String.format(Locale.US, "%.1f", sim.runSpeed)} m/s  snaps ${sim.snaps}",
        "device    ${platform.description}",
    )

    override fun dispose() {
        sim.dispose()
        audio.dispose()
        shapes.dispose()
        batch.dispose()
        fonts.dispose()
    }

    companion object {
        const val STEP = 1f / 60f
        const val FINGER_TOAST = "Use the S Pen. Fingers don’t leave ink."
        const val SNAP_BUZZ_MS = 12
        const val DEATH_BUZZ_MS = 45
    }
}
