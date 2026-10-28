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
import com.pranshuparmar.inkmayhem.game.Tutorial
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
import com.pranshuparmar.inkmayhem.ui.TutorialCard
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
    /** Open the tutorial at this lesson (0-based) on start. */
    val tutorialLesson: Int? = null,
    /** Don't open the first-run tutorial (autoplay never does). */
    val skipTutorial: Boolean = false,
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
    private lateinit var tutorialCard: TutorialCard
    /** The tutorial runs on its own sim; the player's run waits (or is abandoned) meanwhile. */
    private var tutorial: Tutorial? = null
    private var tutorialPending = false
    private val active: GameSim get() = tutorial?.sim ?: sim
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
        sim = GameSim(seed = dev.seed ?: System.nanoTime(), events = Events(saveBest = true), best = settings.best)
        world = WorldRenderer(shapes, text, fonts)
        titleCard = TitleCard(fonts)
        deathCard = DeathCard(fonts)
        tutorialCard = TutorialCard(fonts)
        sheet = SettingsSheet(
            fonts, settings, platform.isDebugBuild,
            onRecalibrate = { calibration.show() },
            onHowToPlay = { startTutorial(0) },
            onChanged = ::onSettingsChanged,
        )
        calibration = CalibrationOverlay(
            fonts,
            onDone = { cal ->
                settings.calibration = cal
                toast.show("Calibrated. Your normal pressure makes a spring.", clock, force = true)
            },
            onSkip = { if (settings.calibration == null) settings.calibration = Pressure.DEFAULT_CAL },
        )
        if (dev.openCalibration || (settings.calibration == null && !dev.skipCalibration)) calibration.show()
        // first run: calibrate, then the tutorial, then the title card
        tutorialPending = dev.tutorialLesson != null || (!settings.tutorialDone && !dev.skipTutorial && !dev.autoplay)
        if (dev.openSettings) sheet.show()
        // Android back closes an overlay first, then leaves the game as usual.
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun keyDown(keycode: Int): Boolean {
                if (keycode != Input.Keys.BACK) return false
                when {
                    calibration.open -> calibration.skip()
                    sheet.open -> sheet.hide()
                    tutorial != null -> endTutorial(finished = false)
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
            titleCard.invalidate(); deathCard.invalidate(); calibration.invalidate(); tutorialCard.invalidate()
        }
        wDp = width / density
        hDp = height / density
        uiCam.setToOrtho(true, wDp, hDp)
        uiCam.update()
        scale = (min(wDp, hDp) / 12f).coerceIn(22f, 64f)
        sim.scale = scale
        sim.setViewport(wDp / scale, hDp / scale)
        tutorial?.sim?.let {
            it.scale = scale
            it.setViewport(wDp / scale, hDp / scale)
        }
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
        fonts.get(Face.HAND_BOLD, 28f)
        fonts.get(Face.UI, 13.5f); fonts.get(Face.UI_BOLD, 13.5f); fonts.get(Face.UI_BOLD, 13f)
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
        tutorial?.sim?.realTime = now
        if (tutorialPending && sized && !calibration.open) {
            tutorialPending = false
            startTutorial(dev.tutorialLesson ?: 0)
        }

        platform.penQueue.drain(sample) { onSample(it, now) }

        if (!paused) {
            acc += dt
            var n = 0
            val tut = tutorial
            while (acc >= STEP && n < 4) {
                if (tut != null) {
                    tut.tick(STEP)
                } else {
                    bot?.update()
                    sim.tick(STEP)
                }
                fx.update(STEP)
                acc -= STEP
                n++
            }
            if (n == 4) acc = 0f
            active.cull(dt)
            if (tut != null && tut.phase == Tutorial.Phase.FINISHED) endTutorial(finished = true)
        }
        if (dev.autoplay && tutorial == null) autoplay(now)

        readout.update(now)
        toast.update(now, reduced)
        if (tutorial == null) hud.update(dt, sim, settings.best, reduced)
        gauge.update(dt, active.drawing != null, reduced)
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
        val wx = world.camX + x / scale
        val wy = world.camY + y / scale
        val time = s.timeNanos / 1e9
        val tut = tutorial
        val card = tutorialCard
        when (s.phase) {
            PenPhase.DOWN -> {
                if (tut != null && card.box.contains(x, y)) {
                    card.press.down(s.pointerId, if (card.skip.contains(x, y)) card.skip else null)
                    return
                }
                if (tut == null && hud.rightArea.contains(x, y)) {
                    hudPress.down(s.pointerId, if (hud.settingsButton.contains(x, y)) hud.settingsButton else null)
                    return
                }
                if (!allowed) {
                    toast.show(FINGER_TOAST, now)
                    return
                }
                // POC: the hover nib is only cleared by pen-downs during a run (not the start/restart tap)
                if (active.state == GameState.RUN) world.hoverOn = false
                val erase = s.toolType == ToolType.ERASER || (s.buttons and (PenButtons.SIDE or PenButtons.SECONDARY)) != 0
                if (tut != null) tut.penDown(s.pointerId, wx, wy, n, erase, time) else sim.penDown(s.pointerId, wx, wy, n, erase, time)
            }
            PenPhase.MOVE -> if (tut != null) tut.penMove(s.pointerId, wx, wy, n, time) else sim.penMove(s.pointerId, wx, wy, n, time)
            PenPhase.UP, PenPhase.CANCEL -> {
                if (tut != null && s.pointerId == card.press.pointer) {
                    if (card.press.up(s.pointerId, if (card.skip.contains(x, y)) card.skip else null) != null && s.phase == PenPhase.UP) {
                        endTutorial(finished = false)
                    }
                    return
                }
                if (s.pointerId == hudPress.pointer) {
                    if (hudPress.up(s.pointerId, if (hud.settingsButton.contains(x, y)) hud.settingsButton else null) != null && s.phase == PenPhase.UP) {
                        sheet.show()
                    }
                    return
                }
                if (tut != null) tut.penUp(s.pointerId) else sim.penUp(s.pointerId)
            }
            PenPhase.HOVER -> if (s.toolType.isPen || (s.toolType == ToolType.MOUSE && allowFinger)) {
                world.hoverOn = true
                world.hoverX = x
                world.hoverY = y
            }
            PenPhase.HOVER_EXIT -> world.hoverOn = false
        }
    }

    // ---------------------------------------------------------------------------------- tutorial

    private fun startTutorial(lesson: Int) {
        tutorial?.sim?.dispose()
        val ts = GameSim(seed = 1, events = Events(saveBest = false), vwMetres = wDp / scale, vhMetres = hDp / scale)
        ts.scale = scale
        ts.tint = true
        val t = Tutorial(ts)
        framedLesson = -1
        updateFrame()
        frameTutorial(t)
        t.start(lesson)
        tutorial = t
        world.hoverOn = false
        tutorialCard.press.cancel()
        toast.clear()
        audio.scratch(0f, 0.5f)
    }

    /** Lays out the lesson card and keeps the scene in the free space below it. */
    private fun frameTutorial(t: Tutorial) {
        tutorialCard.layout(text, frame, t)
        val below = tutorialCard.box.bottom
        t.sim.camRunnerY = (below + (hDp - below) * 0.62f) / hDp
        // a new lesson's card may be taller or shorter: frame its scene for this card at once
        if (t.lessonIndex != framedLesson) {
            framedLesson = t.lessonIndex
            t.sim.snapCamera()
        }
    }

    private var framedLesson = -1

    /** Back to the title card; [finished] = played to the end rather than skipped. */
    private fun endTutorial(finished: Boolean) {
        val t = tutorial ?: return
        tutorial = null
        t.sim.dispose()
        settings.tutorialDone = true
        // "How to play" mid-run ends that run: keep its score if it was a best
        if (sim.state == GameState.RUN) {
            val sc = kotlin.math.floor(sim.score).toInt()
            if (sc > settings.best) {
                settings.best = sc
                sim.best = sc
            }
        }
        world.hoverOn = false
        audio.scratch(0f, 0.5f)
        if (sim.state != GameState.TITLE) sim.toTitle() else fx.clear()
        if (finished) toast.show(READY_TOAST, clock, force = true)
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
            GameState.CLEAR -> Unit
        }
    }

    private fun newBot() = InkBot(sim) { dev.wrongInk[it] ?: it.par ?: com.pranshuparmar.inkmayhem.physics.Ink.ROPE }

    // ------------------------------------------------------------------------------------ events

    /** [saveBest] is off for the tutorial's sim, whose "runs" mustn't touch the best score. */
    private inner class Events(private val saveBest: Boolean) : GameEvents {
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
            if (saveBest && death.isBest) settings.best = death.best
        }

        override fun runReset() = fx.clear()
    }

    // ------------------------------------------------------------------------------------ drawing

    private fun draw(now: Double, reduced: Boolean) {
        val paper = pal[Tok.PAPER]
        Gdx.gl.glClearColor(paper.r, paper.g, paper.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        val tut = tutorial
        // the tutorial always colours ink by type: that's what it teaches
        world.render(active, fx, pal, settings.tint || tut != null, uiCam, wDp, hDp, scale, density, (acc / STEP).coerceIn(0f, 1f))

        updateFrame()
        val safeL = frame.safeL
        val safeT = frame.safeT
        val safeR = frame.safeR

        // HUD
        hud.layout(text, fonts, wDp, safeL, safeT, safeR)
        if (tut != null) frameTutorial(tut)
        val ink = max(0f, sim.inkLeft - (sim.drawing?.cost ?: 0f))
        val drawing = active.drawing
        // POC: live = drawing ? (drawing.ps ?? 0) : 0, so the marker drops to the bottom as the gauge fades
        val live = if (drawing == null || drawing.smoothed.isNaN()) 0f else drawing.smoothed
        val gaugeLeft = safeL + 10f
        val gaugeTop = hDp * 0.3f
        val showReadout = settings.penReadout
        if (showReadout) overlay.update(now, readout, settings.calibration ?: Pressure.DEFAULT_CAL, readoutExtras)
        val monoFont = overlay.font(fonts)
        val boxW = if (showReadout) overlay.width(text, monoFont) else 0f
        val boxH = if (showReadout) overlay.layoutHeight(monoFont) else 0f
        val boxRight = wDp - max(12f, safeR)
        val boxTop = safeT + 64f
        val tv = toast.visibility
        val toastFont = fonts.get(Face.UI_BOLD, 14f)
        val toastW = min(text.width(toastFont, toast.message) + 28f, wDp * 0.92f)
        val toastTop = (if (tut != null) tutorialCard.box.bottom + 12f else safeT + 70f) - (if (reduced) 0f else 8f * (1f - tv))

        shapes.begin(uiCam.combined, density)
        if (tut != null) tutorialCard.drawShapes(shapes, pal, tut) else hud.drawShapes(shapes, pal, wDp, ink, sim.combo, hudPress.isPressed(hud.settingsButton))
        gauge.drawShapes(shapes, pal, gaugeLeft, gaugeTop, live, drawing != null)
        if (showReadout) overlay.drawBox(shapes, pal, boxRight, boxTop, boxW, boxH)
        if (tv > 0f) shapes.color(pal[Tok.INK], tv).roundRect(wDp / 2f - toastW / 2f, toastTop, toastW, 34f, 17f)
        shapes.end()
        batch.projectionMatrix = uiCam.combined
        batch.begin()
        if (tut != null) tutorialCard.drawText(text, pal, tut) else hud.drawText(text, fonts, pal, wDp, sim.combo)
        gauge.drawText(text, fonts, pal, gaugeLeft, gaugeTop, live, drawing != null)
        if (showReadout) overlay.drawText(text, monoFont, pal, boxRight - boxW, boxTop)
        if (tv > 0f) text.line(toastFont, toast.message, wDp / 2f, toastTop, 34f, pal[Tok.PAPER], tv, Align.center)
        batch.end()

        // Cards: the title card until the first pen-down; the death card 0.6 s after dying. During
        // the tutorial its own card is the only one.
        if (tut == null && sim.state == GameState.TITLE) {
            titleCard.layout(text, frame, settings.best)
            pass({ titleCard.drawShapes(shapes, pal, 1f) }, { titleCard.drawText(text, pal, 1f) })
        } else if (tut == null && sim.state == GameState.DEAD && now - sim.deadAt >= 0.6) {
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

    private fun updateFrame() {
        frame.wDp = wDp
        frame.hDp = hDp
        frame.safeL = Gdx.graphics.safeInsetLeft / density
        frame.safeT = Gdx.graphics.safeInsetTop / density
        frame.safeR = Gdx.graphics.safeInsetRight / density
        frame.safeB = Gdx.graphics.safeInsetBottom / density
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
        tutorial?.sim?.dispose()
        sim.dispose()
        audio.dispose()
        shapes.dispose()
        batch.dispose()
        fonts.dispose()
    }

    companion object {
        const val STEP = 1f / 60f
        const val FINGER_TOAST = "Use the S Pen. Fingers don’t leave ink."
        const val READY_TOAST = "You’re ready. The lightest ink that works grows your combo."
        const val SNAP_BUZZ_MS = 12
        const val DEATH_BUZZ_MS = 45
    }
}
