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
import com.pranshuparmar.inkmayhem.game.Sfx
import com.pranshuparmar.inkmayhem.game.StageBot
import com.pranshuparmar.inkmayhem.game.StageDef
import com.pranshuparmar.inkmayhem.game.StageSession
import com.pranshuparmar.inkmayhem.game.Stages
import com.pranshuparmar.inkmayhem.game.Tutorial
import com.pranshuparmar.inkmayhem.input.PenButtons
import com.pranshuparmar.inkmayhem.input.PenCalibration
import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.input.PenPhase
import com.pranshuparmar.inkmayhem.input.PenReadout
import com.pranshuparmar.inkmayhem.input.PenSample
import com.pranshuparmar.inkmayhem.input.PenTuning
import com.pranshuparmar.inkmayhem.input.SteadyPressure
import com.pranshuparmar.inkmayhem.input.Pressure
import com.pranshuparmar.inkmayhem.input.ToolType
import com.pranshuparmar.inkmayhem.record.FrameSource
import com.pranshuparmar.inkmayhem.record.InputRecorder
import com.pranshuparmar.inkmayhem.record.LiveFrames
import com.pranshuparmar.inkmayhem.render.Face
import com.pranshuparmar.inkmayhem.render.Fonts
import com.pranshuparmar.inkmayhem.render.Fx
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok
import com.pranshuparmar.inkmayhem.render.WorldRenderer
import com.pranshuparmar.inkmayhem.ui.CalibrationOverlay
import com.pranshuparmar.inkmayhem.ui.ClearCard
import com.pranshuparmar.inkmayhem.ui.Countdown
import com.pranshuparmar.inkmayhem.ui.EndingCard
import com.pranshuparmar.inkmayhem.ui.PauseCard
import com.pranshuparmar.inkmayhem.ui.TitleCard
import com.pranshuparmar.inkmayhem.ui.GameOverCard
import com.pranshuparmar.inkmayhem.ui.StageIntro
import com.pranshuparmar.inkmayhem.ui.StageSelectCard
import com.pranshuparmar.inkmayhem.ui.DebugOverlay
import com.pranshuparmar.inkmayhem.ui.Frame
import com.pranshuparmar.inkmayhem.ui.Hud
import com.pranshuparmar.inkmayhem.ui.Press
import com.pranshuparmar.inkmayhem.ui.PressureGauge
import com.pranshuparmar.inkmayhem.ui.SettingsSheet
import com.pranshuparmar.inkmayhem.ui.Toast
import com.pranshuparmar.inkmayhem.ui.TutorialCard
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Desktop-only conveniences for development and screenshots. */
class DevOptions(
    val seed: Long? = null,
    val autoplay: Boolean = false,
    val openSettings: Boolean = false,
    val openCalibration: Boolean = false,
    /** Open the tutorial at this lesson (0-based) on start. */
    val tutorialLesson: Int? = null,
    /** Autoplay draws the second ink wherever its solution has the first (to reach a death). */
    val wrongInk: Map<Ink, Ink> = emptyMap(),
    /** Start straight into this stage ("1-2"); with [autoplay] its scripted solution plays it. */
    val stage: String? = null,
    /** Open the stage list on start. */
    val openSelect: Boolean = false,
    /** Run the simulation this many times faster (screenshots of late moments). */
    val speed: Float = 1f,
    /** Show this toast at start (to check how long ones lay out). */
    val toast: String? = null,
    /** Pause the stage this many seconds into it (screenshots of the pause screen). */
    val pauseAt: Float? = null,
    /** Open the ending on start: "finale" (with the player's totals) or "credits". */
    val ending: String? = null,
    /** Show a gallery of the runner's expressions instead of the game (for tuning its face). */
    val faces: Boolean = false,
)

/**
 * The game. Everything it reads from outside (clock, screen, system flags, pen samples) comes
 * through [frames] and the platform's pen queue, so a recording can drive it exactly.
 */
class InkMayhemGame(
    private val platform: Platform,
    private val dev: DevOptions = DevOptions(),
    private val frames: FrameSource = LiveFrames(platform),
) : ApplicationAdapter() {
    private lateinit var settings: Settings
    private lateinit var shapes: Shapes
    private lateinit var batch: SpriteBatch
    private lateinit var text: Text
    private lateinit var fonts: Fonts
    private lateinit var audio: Audio
    private lateinit var sim: GameSim
    private lateinit var world: WorldRenderer
    private lateinit var sheet: SettingsSheet
    private lateinit var calibration: CalibrationOverlay
    private lateinit var tutorialCard: TutorialCard
    /** The tutorial runs on its own sim; the player's run waits (or is abandoned) meanwhile. */
    private var tutorial: Tutorial? = null
    private var tutorialPending = false

    /**
     * Which part of the game is on screen; the tutorial overlays any of them. The game opens on
     * the title screen; there is no Endless.
     */
    private enum class Screen { TITLE, SELECT, STAGE, ENDING }
    private var screen = Screen.TITLE
    /** Where the tutorial goes back to when it ends. */
    private var afterTutorial = Screen.TITLE
    /** Stage play runs on its own sim; null outside [Screen.STAGE]. */
    private var session: StageSession? = null
    private var stageBot: StageBot? = null
    private lateinit var selectCard: StageSelectCard
    private lateinit var gameOverCard: GameOverCard
    private lateinit var clearCard: ClearCard
    private lateinit var stageIntro: StageIntro
    private lateinit var titleCard: TitleCard
    /** A stage on hold, and the 3-2-1 back in after Resume. */
    private lateinit var pauseCard: PauseCard
    private lateinit var countdown: Countdown
    /** After the Big Smudge: the finale and the credits (or the credits alone, from the title). */
    private lateinit var endingCard: EndingCard
    /** When the screen last changed: the new one fades in from the paper. */
    private var fadeAt = -10.0
    private var lastPhase: StageSession.Phase? = null
    private var starsSounded = 0
    private val active: GameSim get() = tutorial?.sim ?: session?.sim ?: sim
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

    private var density = 1f
    private var wDp = 1f
    private var hDp = 1f
    /** Pixels (dp) per metre: clamp(min(W, H) / 12, 22, 64). */
    private var scale = 30f
    private var sized = false

    private val sample = PenSample()
    /** This frame's clock (seconds since launch). */
    private var clock = 0.0
    private var acc = 0f
    private var paused = false
    private var lastSnapHaptic = -1.0

    // debug builds record every launch's input (see record/InputLog.kt)
    private var recorder: InputRecorder? = null
    private var recordingFile: File? = null
    private var recordInputWas = true
    private var frameCount = 0L

    override fun create() {
        val prefs = platform.preferences()
        val worldSeed = dev.seed ?: System.nanoTime()
        startRecording(prefs, worldSeed)
        settings = Settings(prefs)
        recordInputWas = settings.recordInput
        shapes = Shapes()
        batch = SpriteBatch()
        text = Text(batch)
        density = platform.density
        fonts = Fonts(density)
        audio = Audio(settings)
        sim = GameSim(seed = worldSeed, events = Events(saveBest = true), best = settings.best)
        world = WorldRenderer(shapes, text, fonts)
        tutorialCard = TutorialCard(fonts)
        selectCard = StageSelectCard(fonts, settings)
        gameOverCard = GameOverCard(fonts)
        clearCard = ClearCard(fonts)
        stageIntro = StageIntro(fonts)
        titleCard = TitleCard(fonts)
        pauseCard = PauseCard(fonts)
        countdown = Countdown(fonts)
        endingCard = EndingCard(fonts)
        sheet = SettingsSheet(
            fonts, settings, platform.isDebugBuild,
            onRecalibrate = { calibration.show() },
            onHowToPlay = { startTutorial(0) },
            onChanged = ::onSettingsChanged,
            onShareRecording = if (recorder != null) ::shareRecording else null,
            onSoundSet = { audio.play(Sfx.POP) },
        )
        calibration = CalibrationOverlay(
            fonts,
            onDone = { cal ->
                settings.calibration = cal
                toast.show("Pen tuned: rope, spring and beam are yours.", clock, force = true)
            },
            onSkip = { if (!settings.penTuned) settings.calibration = settings.calibration ?: PenCalibration.DEFAULT },
        )
        if (recorder != null) {
            calibration.log = { log(it) }
            toast.onShow = { log("toast $it") }
        }
        // the title screen first; no tuning screen (the first stage tunes the pen) and no tutorial
        // (it teaches by doing): both are a tap away
        if (dev.openCalibration) calibration.show()
        tutorialPending = dev.tutorialLesson != null
        if (dev.openSettings) sheet.show()
        showTitle()

        if (dev.openSelect) openSelect()
        dev.ending?.let { showEnding(finale = it == "finale") }
        // Android back closes an overlay first, pauses a stage, steps back a screen, and on the
        // title leaves the game as usual.
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun keyDown(keycode: Int): Boolean {
                if (keycode != Input.Keys.BACK) return false
                back()
                return true
            }
        }
        frames.reset()
    }

    /**
     * The Android back key: closes an overlay first; in a stage it pauses (and on the pause
     * screen, resumes); the stage list goes back to the title; the title leaves the game.
     */
    fun back() {
        recorder?.back()
        val ses = session
        when {
            calibration.open -> calibration.skip()
            sheet.open -> sheet.hide()
            tutorial != null -> endTutorial(finished = false)
            screen == Screen.TITLE -> Gdx.app.exit()
            screen == Screen.SELECT || screen == Screen.ENDING -> showTitle()
            pauseCard.open -> resumeStage()
            ses != null && pausable(ses) -> pauseStage()
            else -> endStage()
        }
    }

    private fun startRecording(prefs: com.badlogic.gdx.Preferences, worldSeed: Long) {
        if (!platform.isDebugBuild || !prefs.getBoolean(Settings.RECORD_INPUT, true)) return
        val file = platform.newRecording() ?: return
        val r = try {
            InputRecorder(file.bufferedWriter(bufferSize = 1 shl 16))
        } catch (e: java.io.IOException) {
            return
        }
        recorder = r
        recordingFile = file
        r.meta("app", platform.appVersion)
        r.meta("device", platform.description)
        r.meta("started", java.time.Instant.now().toString())
        r.meta("seed", worldSeed.toString())
        for ((k, v) in prefs.get()) r.pref(k, v)
    }

    private fun shareRecording() {
        val file = recordingFile ?: return
        recorder?.let {
            it.event("shared")
            it.flush()
        }
        platform.shareRecording(file)
    }

    /** Notes what happened in the recording, for whoever reads it. */
    private inline fun log(text: () -> String) {
        recorder?.event(text())
    }

    private fun log(text: String) {
        recorder?.event(text)
    }

    /**
     * Where the game stands, written into recordings eight times a second so a replay can check it
     * and put it back: screen, state, runner position, score, strokes, then the runner's velocity,
     * angle and spin, and the camera (current, previous tick, and as last drawn).
     */
    fun probe(): String {
        val a = active
        val b = a.runner.body
        val p = b.position
        val v = b.linearVelocity
        return "${screen.name} ${a.state.name} ${p.x} ${p.y} ${kotlin.math.floor(a.score).toInt()} ${a.ink.strokes.size} " +
            "${v.x} ${v.y} ${b.angle} ${b.angularVelocity} ${a.cam.x} ${a.cam.y} ${a.camPrev.x} ${a.camPrev.y} ${world.camX} ${world.camY}"
    }

    /**
     * Replays: puts the runner and camera back where a [probe] says they were. Physics rounds a
     * little differently on a phone's chip than on a desktop, and a bounce can grow that into a
     * different run; snapping back at each probe keeps a replay on the recorded path. Older
     * probes carry only the position. Returns false when the screen or state already differ.
     */
    fun resync(probe: String): Boolean {
        val f = probe.split(' ')
        val a = active
        if (f.size < 4 || f[0] != screen.name || f[1] != a.state.name) return false
        val b = a.runner.body
        if (f.size >= 16) {
            b.setTransform(f[2].toFloat(), f[3].toFloat(), f[8].toFloat())
            b.setLinearVelocity(f[6].toFloat(), f[7].toFloat())
            b.angularVelocity = f[9].toFloat()
            a.cam.set(f[10].toFloat(), f[11].toFloat())
            a.camPrev.set(f[12].toFloat(), f[13].toFloat())
            world.camX = f[14].toFloat()
            world.camY = f[15].toFloat()
        } else {
            b.setTransform(f[2].toFloat(), f[3].toFloat(), b.angle)
        }
        b.isAwake = true
        return true
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        recorder?.resize(width, height, platform.density)
        val d = platform.density
        if (d != density) {
            density = d
            fonts.setDensity(d)
            calibration.invalidate(); tutorialCard.invalidate()
        }
        wDp = width / density
        hDp = height / density
        uiCam.setToOrtho(true, wDp, hDp)
        uiCam.update()
        // POC: clamp(min(W, H) / 12, 22, 64). A portrait screen also has to show MIN_VIEW_W across,
        // or the runner sees too little of what's coming (and of a boulder behind).
        scale = min(min(wDp, hDp) / 12f, wDp / MIN_VIEW_W).coerceIn(22f, 64f)
        sim.scale = scale
        sim.setViewport(wDp / scale, hDp / scale)
        tutorial?.sim?.let {
            it.scale = scale
            it.setViewport(wDp / scale, hDp / scale)
        }
        session?.sim?.let {
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
        for (size in floatArrayOf(20f, 32f, 34f, 36f, 40f, 44f)) fonts.get(Face.HAND_BOLD, size)
        fonts.get(Face.MONO_BOLD, 15f); fonts.get(Face.MONO_BOLD, 22f); fonts.get(Face.MONO_MEDIUM, 9.5f); fonts.get(Face.MONO_MEDIUM, 10.5f)
        fonts.get(Face.UI_BOLD, 16f)
        fonts.get(Face.HAND, 20f)
        fonts.get(Face.HAND_BOLD, 28f)
        fonts.get(Face.UI, 13.5f); fonts.get(Face.UI_BOLD, 13.5f); fonts.get(Face.UI_BOLD, 13f)
        for (size in floatArrayOf(30f, 40f, 64f)) fonts.get(Face.HAND_BOLD, size, Fonts.DIGITS)
        fonts.get(Face.HAND, max(15f, scale * 0.6f))
        fonts.get(Face.HAND_BOLD, max(18f, scale * 0.75f))
        fonts.get(Face.MONO_BOLD, max(14f, scale * 0.5f))
    }

    override fun pause() {
        recorder?.let {
            it.pause()
            it.flush()
        }
        paused = true
        audio.scratch(0f, 0.5f)
        // coming back to a stage shouldn't throw the player straight back in: hold it paused
        val ses = session
        if (screen == Screen.STAGE && ses != null && pausable(ses) && tutorial == null) pauseStage()
    }

    override fun resume() {
        recorder?.resume()
        paused = false
        frames.reset()
    }

    override fun render() {
        frames.begin()
        val dt = frames.dt
        val now = frames.now
        clock = now
        recorder?.let {
            it.flags(frames.dark, frames.reduced)
            it.insets(frames.insetL, frames.insetT, frames.insetR, frames.insetB)
            it.frame(dt, now)
        }
        // World 5 is a midnight page whatever the system's theme
        pal = when {
            screen == Screen.STAGE && session?.stage?.theme == com.pranshuparmar.inkmayhem.game.Theme.MIDNIGHT -> Palette.MIDNIGHT
            frames.dark -> Palette.DARK
            else -> Palette.LIGHT
        }
        val reduced = frames.reduced
        fx.reducedMotion = reduced
        sim.realTime = now
        sim.tint = settings.tint
        tutorial?.sim?.realTime = now
        session?.sim?.let {
            it.realTime = now
            it.tint = settings.tint
        }
        if (dev.toast != null && sized && !devToastShown) {
            devToastShown = true
            toast.show(dev.toast, now, force = true)
        }
        if (tutorialPending && sized && !calibration.open) {
            tutorialPending = false
            startTutorial(dev.tutorialLesson ?: 0)
        }
        if (sized && !launched && tutorial == null && !tutorialPending) {
            launched = true
            // (desktop: --stage starts there; --autoplay alone plays on from the next stage)
            val first = dev.stage?.let(Stages::byId) ?: if (dev.autoplay && !dev.openSelect) nextStage() else null
            first?.let { startStage(it) }
        }

        platform.penQueue.drain(sample) { onSample(it, now) }
        val pauseAt = dev.pauseAt
        session?.let { if (pauseAt != null && !devPaused && it.sim.simT >= pauseAt && pausable(it)) { devPaused = true; pauseStage() } }

        // a stage holds while paused, while the settings sheet is open and through the count back in
        if (!paused && screen == Screen.STAGE && !pauseCard.open && !sheet.open && !calibration.open) {
            val beat = countdown.update(dt)
            if (beat > 0) audio.play(Sfx.CHIME, 6 - beat)
        }
        val stagePaused = screen == Screen.STAGE && (sheet.open || calibration.open || pauseCard.open || countdown.running)
        if (!paused && !stagePaused) {
            acc += dt * dev.speed
            var n = 0
            val tut = tutorial
            val ses = session
            val maxTicks = (4 * dev.speed).toInt().coerceAtLeast(4)
            while (acc >= STEP && n < maxTicks) {
                if (tut != null) {
                    tut.tick(STEP)
                } else if (ses != null) {
                    if (ses.phase == StageSession.Phase.PLAY) stageBot?.update()
                    ses.sim.tick(STEP)
                } else {
                    sim.tick(STEP) // the stage list's page, standing still
                }
                fx.update(STEP)
                acc -= STEP
                n++
            }
            if (n == maxTicks) acc = 0f
            active.cull(dt)
            if (tut != null && tut.phase == Tutorial.Phase.FINISHED) endTutorial(finished = true)
            if (tut == null && ses != null) {
                ses.update(dt * dev.speed)
                stageFeedback(ses)
            }
        }
        if (dev.autoplay && tutorial == null) autoplay()

        readout.update(now)
        toast.update(now, reduced)
        val ses = session
        if (tutorial == null) hud.update(dt, ses?.sim ?: sim, settings.best, reduced, ses?.lives ?: -1, ses?.stars ?: 0, goldEver)
        gauge.update(dt, active.drawing != null, reduced)
        // load the tunes this screen may need before it needs them (a boss's during the stage's intro)
        val tuneStage = session?.stage
        if (!tunesReady || tuneStage !== tunesFor) {
            tunesReady = true
            tunesFor = tuneStage
            audio.prepare(
                if (tuneStage == null) listOf(Stages.WORLDS[0].music)
                else listOfNotNull(Stages.worldOf(tuneStage).music, BOSS_MUSIC.takeIf { tuneStage.layout.arenas.isNotEmpty() }),
            )
        }
        audio.music(musicWanted(), musicTheme())
        audio.update(dt)

        draw(now, reduced)
        recorder?.let { r ->
            noteChanges()
            if (++frameCount % PROBE_EVERY == 0L) r.probe(probe())
        }
    }

    // ------------------------------------------------------------------------------------- input

    private val allowFinger: Boolean get() = platform.isDebugBuild && settings.allowFinger

    private fun onSample(s: PenSample, now: Double) {
        recorder?.sample(s)
        val n = if (s.toolType.isPen) (settings.calibration ?: PenCalibration.DEFAULT).normalize(s.pressure) else Pressure.FINGER
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
        if (tut == null && cardSample(s, x, y)) return
        val ses = session
        // a stage takes ink in play and during a retry's short intro (the runner is held; the first
        // intro covers the page), but not while it is paused or counting back in
        val playing = tut != null ||
            (ses != null && !pauseCard.open && !countdown.running &&
                (ses.phase == StageSession.Phase.PLAY || (ses.phase == StageSession.Phase.INTRO && !ses.firstIntro)))
        when (s.phase) {
            PenPhase.DOWN -> {
                if (tut != null && card.box.contains(x, y)) {
                    card.press.down(s.pointerId, if (card.skip.contains(x, y)) card.skip else null)
                    return
                }
                if (tut == null && screen != Screen.TITLE && hud.rightArea.contains(x, y)) {
                    hudPress.down(s.pointerId, if (hud.settingsButton.contains(x, y)) hud.settingsButton else null)
                    return
                }
                if (tut == null && screen == Screen.SELECT && hud.backButton.contains(x, y)) {
                    hudPress.down(s.pointerId, hud.backButton)
                    return
                }
                if (!playing) return
                if (!allowed) {
                    toast.show(FINGER_TOAST, now)
                    return
                }
                // POC: the hover nib is only cleared by pen-downs during a run (not the start/restart tap)
                if (active.state == GameState.RUN) world.hoverOn = false
                val erase = s.toolType == ToolType.ERASER || (s.buttons and (PenButtons.SIDE or PenButtons.SECONDARY)) != 0
                if (tut != null) tut.penDown(s.pointerId, wx, wy, n, erase, time) else active.penDown(s.pointerId, wx, wy, n, erase, time)
                if (active.drawing?.pointer == s.pointerId) {
                    rawSteady.reset()
                    rawSteady.add(time, s.pressure)
                    strokeByPen = s.toolType.isPen
                }
            }
            PenPhase.MOVE -> {
                if (tut != null) tut.penMove(s.pointerId, wx, wy, n, time) else active.penMove(s.pointerId, wx, wy, n, time)
                if (active.drawing?.pointer == s.pointerId) rawSteady.add(time, s.pressure)
            }
            PenPhase.UP, PenPhase.CANCEL -> {
                if (tut != null && s.pointerId == card.press.pointer) {
                    if (card.press.up(s.pointerId, if (card.skip.contains(x, y)) card.skip else null) != null && s.phase == PenPhase.UP) {
                        endTutorial(finished = false)
                    }
                    return
                }
                if (s.pointerId == hudPress.pointer) {
                    val under = when {
                        hud.settingsButton.contains(x, y) -> hud.settingsButton
                        screen == Screen.SELECT && hud.backButton.contains(x, y) -> hud.backButton
                        else -> null
                    }
                    val hit = hudPress.up(s.pointerId, under)
                    if (hit != null && s.phase == PenPhase.UP) {
                        val ses2 = session
                        when {
                            hit === hud.backButton -> showTitle()
                            screen == Screen.STAGE && ses2 != null && pausable(ses2) -> pauseStage()
                            else -> sheet.show()
                        }
                    }
                    return
                }
                val note = if (recorder != null) strokeNote(s.pointerId) else null
                val wasDrawing = active.drawing?.pointer == s.pointerId
                if (tut != null) tut.penUp(s.pointerId) else active.penUp(s.pointerId)
                if (note != null) log { "$note -> ${active.lastStrokeInk?.label ?: "nothing"}" }
                val gate = active.lastGate
                if (wasDrawing && gate != null && tut == null && strokeByPen) learnGate(gate.ink, rawSteady.finish(Float.NaN))
            }
            PenPhase.HOVER -> if (s.toolType.isPen || (s.toolType == ToolType.MOUSE && allowFinger)) {
                world.hoverOn = true
                world.hoverX = x
                world.hoverY = y
            }
            PenPhase.HOVER_EXIT -> world.hoverOn = false
        }
    }

    /**
     * Taps on the stage list, game-over and clear cards (a tap counts when it starts and ends on
     * the same button). Returns true when the card took the sample.
     */
    private fun cardSample(s: PenSample, x: Float, y: Float): Boolean {
        val ses = session
        val card = when {
            screen == Screen.TITLE -> 0
            screen == Screen.SELECT -> 1
            screen == Screen.ENDING -> 5
            pauseCard.open -> 4
            ses != null && ses.phase == StageSession.Phase.GAME_OVER -> 2
            ses != null && ses.phase == StageSession.Phase.CLEARED -> 3
            else -> return false
        }
        val press = when (card) {
            0 -> titleCard.press
            1 -> selectCard.press
            2 -> gameOverCard.press
            3 -> clearCard.press
            5 -> endingCard.press
            else -> pauseCard.press
        }
        when (s.phase) {
            PenPhase.DOWN -> {
                // the settings button (and the stage list's way back) stay reachable
                if (card != 0 && card != 4 && card != 5 && hud.rightArea.contains(x, y)) return false
                if (card == 1 && hud.backButton.contains(x, y)) return false
                press.down(s.pointerId, cardTarget(card, x, y))
                return true
            }
            PenPhase.UP -> {
                if (s.pointerId != press.pointer) return false
                val hit = press.up(s.pointerId, cardTarget(card, x, y)) ?: return true
                onCard(hit)
                return true
            }
            PenPhase.CANCEL -> {
                val mine = s.pointerId == press.pointer
                if (mine) press.cancel()
                return mine
            }
            else -> return false // hovers and moves don't hit-test the cards
        }
    }

    private fun cardTarget(card: Int, x: Float, y: Float): Any? = when (card) {
        0 -> titleCard.targetAt(x, y)
        1 -> selectCard.targetAt(x, y)
        2 -> gameOverCard.targetAt(x, y)
        3 -> clearCard.targetAt(x, y)
        5 -> endingCard.targetAt(x, y)
        else -> pauseCard.targetAt(x, y)
    }

    private fun onCard(hit: Any) {
        val ses = session
        when {
            hit === titleCard.play -> nextStage()?.let(::startStage) ?: openSelect()
            hit === titleCard.stages -> openSelect()
            hit === titleCard.howTo -> startTutorial(0)
            hit === titleCard.settings -> sheet.show()
            hit === titleCard.credits -> showEnding(finale = false)
            hit === endingCard.button -> if (endingCard.finished(clock)) showTitle() else endingCard.skip(clock)
            hit === selectCard.play -> (nextStage() ?: Stages.ALL.first()).let(::startStage)
            hit is StageSelectCard.WorldTab -> selectCard.show(hit.number)
            hit is StageDef -> startStage(hit)
            ses == null -> Unit
            hit === pauseCard.resume -> resumeStage()
            hit === pauseCard.restart -> {
                pauseCard.hide()
                ses.start(ses.stage, freshLives = false)
                fx.clear()
                toast.clear()
                audio.play(Sfx.STAGE_START)
            }
            hit === pauseCard.settings -> sheet.show()
            hit === pauseCard.quit -> endStage()
            hit === gameOverCard.again -> {
                ses.tryAgain()
                audio.play(Sfx.STAGE_START)
            }
            hit === gameOverCard.stages || hit === clearCard.stages -> endStage()
            hit === clearCard.retry -> {
                ses.start(ses.stage, freshLives = false)
                audio.play(Sfx.STAGE_START)
            }
            hit === clearCard.next -> when {
                clearCard.hasNext && ses.next() -> {
                    audio.play(Sfx.STAGE_START)
                    fadeAt = clock
                }
                clearCard.hasNext -> endStage()
                else -> showEnding(finale = true)
            }
        }
        // a retry after the first clear is no longer a first run
        session?.let { if (screen == Screen.STAGE) it.sim.learning = firstRun(it.stage) }
    }

    // ------------------------------------------------------------------------------ silent tuning

    /** The raw pressure each stroke settles at, for the tuning gates. */
    private val rawSteady = SteadyPressure()
    private var strokeByPen = false
    /** Raw pressure of the strokes drawn at the rope, spring and beam gates (NaN until drawn). */
    private val gateRaw = FloatArray(3) { Float.NaN }

    /**
     * An untuned pen learns from the first page's first three obstacles: each takes the ink it
     * asks for, and once all three are drawn their pressures tune the pen, if they rise.
     */
    private fun learnGate(ink: Ink, raw: Float) {
        if (raw.isNaN() || settings.penTuned) return
        gateRaw[ink.weight] = raw
        log { "tuning gate ${ink.label}: raw ${"%.3f".format(raw)}" }
        if (gateRaw.any { it.isNaN() }) return
        val (r, sp, b) = gateRaw
        if (PenTuning.rising(r, sp, b)) {
            settings.calibration = PenCalibration.fromLines(r, sp, b)
            session?.sim?.gateTuning = false
            log { "pen tuned from the gates: ${settings.calibration}" }
            toast.show("Got it: the game knows your light, normal and hard touch now.", clock, force = true)
        } else {
            gateRaw.fill(Float.NaN)
            toast.show(TUNE_LATER, clock, force = true)
        }
    }

    // ------------------------------------------------------------------------------------ stages

    /** The first stage (or the list) has been opened. */
    private var launched = false
    private var devToastShown = false
    /** The stage whose tunes are loaded (null: the menus'). */
    private var tunesFor: StageDef? = null
    private var tunesReady = false
    private var devPaused = false

    /** Gold stars found in the stage being played on earlier clears (read once per start). */
    private var goldEver = 0

    private fun startStage(def: StageDef) {
        goldEver = settings.gold(def.id)
        session?.sim?.dispose()
        val ss = GameSim(seed = 1, events = Events(saveBest = false), vwMetres = wDp / scale, vhMetres = hDp / scale)
        ss.scale = scale
        ss.tint = settings.tint
        ss.gateTuning = !settings.penTuned
        ss.learning = firstRun(def)
        val ses = StageSession(ss, settings)
        session = ses
        ses.start(def)
        stageBot = if (dev.autoplay) newBot(ss) else null
        screen = Screen.STAGE
        fadeAt = clock
        pauseCard.hide()
        countdown.cancel()
        lastPhase = null
        world.hoverOn = false
        toast.clear()
        fx.clear()
        audio.scratch(0f, 0.5f)
        audio.play(Sfx.STAGE_START)
    }

    /** A stage can be paused while it's being played: its intro, play and a lost life's moment. */
    private fun pausable(ses: StageSession) =
        ses.phase == StageSession.Phase.INTRO || ses.phase == StageSession.Phase.PLAY || ses.phase == StageSession.Phase.DYING

    private fun pauseStage() {
        if (pauseCard.open) return
        countdown.cancel()
        // a stroke half drawn is let go, as if the pen had lifted
        session?.sim?.drawing?.let { session?.sim?.penUp(it.pointer) }
        pauseCard.show()
        audio.scratch(0f, 0.5f)
        log("paused")
    }

    /** Back from the pause screen: mid-play the runner is counted back in, 3-2-1. */
    private fun resumeStage() {
        pauseCard.hide()
        if (session?.phase == StageSession.Phase.PLAY) {
            countdown.start()
            audio.play(Sfx.CHIME, 6 - Countdown.BEATS)
        }
        log("resumed")
    }

    /** The title screen, Play pointing at the first stage not yet cleared. */
    private fun showTitle() {
        if (screen == Screen.STAGE) {
            session?.sim?.dispose()
            session = null
            stageBot = null
        }
        screen = Screen.TITLE
        fadeAt = clock
        pauseCard.hide()
        countdown.cancel()
        titleCard.show(nextStage(), fresh = Stages.ALL.none { settings.stars(it.id) > 0 }, clock)
        fx.clear()
        toast.clear()
    }

    /** The finale after the last stage, with the player's totals ([finale]), or just the credits. */
    private fun showEnding(finale: Boolean) {
        if (screen == Screen.STAGE) {
            session?.sim?.dispose()
            session = null
            stageBot = null
        }
        val totals = if (!finale) null else EndingCard.Totals(
            cleared = Stages.ALL.count { settings.stars(it.id) > 0 },
            stages = Stages.ALL.size,
            stars = Stages.ALL.sumOf { settings.stars(it.id) },
            gold = Stages.ALL.sumOf { Integer.bitCount(settings.gold(it.id)) },
            score = Stages.ALL.sumOf { settings.best(it.id) },
        )
        endingCard.start(clock, totals)
        screen = Screen.ENDING
        fadeAt = clock
        pauseCard.hide()
        countdown.cancel()
        fx.clear()
        toast.clear()
        audio.scratch(0f, 0.5f)
        log { "ending ${if (finale) "finale" else "credits"}" }
    }

    private fun newBot(s: GameSim) = StageBot(s, inkFor = { dev.wrongInk[it] ?: it })

    /** A world's first stage until it has been cleared once: the runner waits before each new thing. */
    private fun firstRun(def: StageDef) = def.number == 1 && settings.stars(def.id) == 0

    private fun openSelect() {
        fadeAt = clock
        pauseCard.hide()
        countdown.cancel()
        selectCard.refresh()
        // on the world just played, or where play picks up
        val at = session?.stage ?: nextStage() ?: Stages.ALL.last()
        selectCard.show(Stages.worldOf(at).number)
        screen = Screen.SELECT
    }

    /** Where play picks up: the first stage not yet cleared, or null once all are. */
    private fun nextStage(): StageDef? =
        Stages.ALL.firstOrNull { settings.stars(it.id) == 0 && StageSession.unlocked(it, settings) }

    /** Leaves stage play for the stage list. */
    private fun endStage() {
        session?.sim?.dispose()
        session = null
        stageBot = null
        openSelect()
        fx.clear()
        toast.clear()
        audio.scratch(0f, 0.5f)
    }

    /**
     * The world's tune plays through a stage's intro and play, from the top after each lost life;
     * pausing (or the settings sheet, or leaving the app) holds it where it is. The title screen
     * and the stage list play Ink Rush; the tutorial is quiet.
     */
    private fun musicWanted(): Audio.Music {
        val ses = session
        if (tutorial != null) return Audio.Music.STOPPED
        if (screen == Screen.TITLE || screen == Screen.SELECT || screen == Screen.ENDING) return if (paused) Audio.Music.PAUSED else Audio.Music.PLAYING
        if (ses == null) return Audio.Music.STOPPED
        if (ses.phase != StageSession.Phase.INTRO && ses.phase != StageSession.Phase.PLAY) return Audio.Music.STOPPED
        val held = sheet.open || calibration.open || paused || pauseCard.open || countdown.running
        return if (held) Audio.Music.PAUSED else Audio.Music.PLAYING
    }

    /** The world's own tune; a boss fight has its own. */
    private fun musicTheme(): String {
        val ses = session ?: return Stages.WORLDS[0].music
        return if (ses.sim.bossFight) BOSS_MUSIC else Stages.worldOf(ses.stage).music
    }

    /** Sounds for the session's moments: a lost life, game over, and a ding per star on the tally. */
    private fun stageFeedback(ses: StageSession) {
        if (ses.phase != lastPhase) {
            if (ses.phase == StageSession.Phase.DYING) audio.play(Sfx.LIFE_LOST)
            if (ses.phase == StageSession.Phase.GAME_OVER) audio.play(Sfx.GAME_OVER)
            if (ses.phase == StageSession.Phase.CLEARED) starsSounded = 0
            lastPhase = ses.phase
        }
        val r = ses.result
        if (ses.phase == StageSession.Phase.CLEARED && r != null) {
            val shown = clearCard.starsShown(ses.phaseT, r.stars)
            while (starsSounded < shown) {
                starsSounded++
                audio.play(Sfx.STAR)
            }
        }
    }

    // ---------------------------------------------------------------------------------- tutorial

    private fun startTutorial(lesson: Int) {
        tutorial?.sim?.dispose()
        // the tutorial replaces whatever was being played
        if (screen == Screen.STAGE) endStage()
        afterTutorial = if (screen == Screen.TITLE) Screen.TITLE else Screen.SELECT
        fadeAt = clock
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

    /** Back to where it was opened from (the title or the stage list); [finished] = played to the end rather than skipped. */
    private fun endTutorial(finished: Boolean) {
        val t = tutorial ?: return
        tutorial = null
        t.sim.dispose()
        settings.tutorialDone = true
        world.hoverOn = false
        audio.scratch(0f, 0.5f)
        fx.clear()
        if (afterTutorial == Screen.TITLE) showTitle() else openSelect()
        if (finished) toast.show(READY_TOAST, clock, force = true)
    }

    private fun onSettingsChanged() {
        if (!settings.sound) audio.scratch(0f, 0.5f)
        // "Record input" off stops now (what's recorded so far can still be shared); on starts
        // with the next launch, since a replay has to start from one
        if (settings.recordInput == recordInputWas) return
        recordInputWas = settings.recordInput
        val r = recorder
        if (!settings.recordInput && r != null) {
            r.event("recording turned off")
            r.close()
            recorder = null
            calibration.log = null
            toast.onShow = null
        } else if (settings.recordInput && r == null && platform.isDebugBuild) {
            toast.show("Recording starts next launch.", clock, force = true)
        }
    }

    private fun autoplay() {
        val ses = session
        if (ses != null) {
            // stages: the scripted solution plays (see the tick loop); move on after the cards
            if (ses.phase == StageSession.Phase.CLEARED && ses.phaseT > 4.5f) {
                if (!ses.next()) endStage() else stageBot = newBot(ses.sim)
            }
            if (ses.phase == StageSession.Phase.GAME_OVER && ses.phaseT > 2.5f) ses.tryAgain()
        }
    }

    // ------------------------------------------------------------------------------------ events

    /** [saveBest] is off for the tutorial's sim, whose "runs" mustn't touch the best score. */
    private inner class Events(private val saveBest: Boolean) : GameEvents {
        override fun sound(sfx: Sfx, variant: Int) {
            // in a stage the lost-life jingle tells a death; the POC's thud would clash with it
            if (sfx == Sfx.DEATH && !saveBest && session != null && tutorial == null) return
            audio.play(sfx, variant)
        }
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
            log { "died ${death.kind.name}: ${death.reason}" }
            if (saveBest && death.isBest) settings.best = death.best
        }

        override fun runReset() = fx.clear()

        override fun checkpoint() {
            audio.play(Sfx.CHECKPOINT)
            toast.show("Checkpoint", clock, force = true)
        }

        override fun stageCleared() = audio.play(Sfx.CLEAR)

        override fun extraLife() {
            session?.extraLife()
            audio.play(Sfx.ONE_UP)
            toast.show("Extra life!", clock, force = true)
        }

        override fun star() {
            val ses = session ?: return
            if (ses.stars == 0 && ses.stage === Stages.FIRST_PAGE) toast.show(FIRST_STAR, clock, force = true)
            if (!ses.addStar()) return
            audio.play(Sfx.ONE_UP)
            toast.show("${GameSim.STARS_PER_LIFE} stars: extra life!", clock, force = true)
        }
    }

    // ------------------------------------------------------------------------------------ drawing

    private fun draw(now: Double, reduced: Boolean) {
        val paper = pal[Tok.PAPER]
        Gdx.gl.glClearColor(paper.r, paper.g, paper.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        val tut = tutorial
        val ses = session
        world.guideOn = tut != null && tut.showGuide && tut.phase == Tutorial.Phase.TRYING
        if (tut != null && world.guideOn) {
            tut.guide.copyInto(world.guide)
            world.guideRing = tut.guideRing
            world.guideTok = when (tut.guideInk) { Ink.ROPE -> Tok.ROPE; Ink.SPRING -> Tok.SPRING; Ink.BEAM -> Tok.BEAM }
        }
        // the tutorial always colours ink by type: that's what it teaches
        val menu = tut == null && screen != Screen.STAGE
        world.render(
            active, fx, pal, settings.tint || tut != null, uiCam, wDp, hDp, scale, density, (acc / STEP).coerceIn(0f, 1f),
            pageOnly = menu, dt = frames.dt, remarks = settings.remarks && tut == null,
        )

        updateFrame()
        val safeL = frame.safeL
        val safeT = frame.safeT
        val safeR = frame.safeR

        // HUD
        hud.layout(text, fonts, wDp, safeL, safeT, safeR)
        if (tut != null) frameTutorial(tut)
        val hudSim = ses?.sim ?: sim
        val ink = max(0f, hudSim.inkLeft - (hudSim.drawing?.cost ?: 0f))
        val drawing = active.drawing
        // POC: live = drawing ? (drawing.ps ?? 0) : 0, so the marker drops to the bottom as the gauge fades.
        // The marker is the stroke's steady pressure, so the lit band is the ink the stroke will be.
        val live = active.drawingPressure
        val gaugeLeft = safeL + 10f
        val gaugeTop = hDp * 0.3f
        val showReadout = settings.penReadout
        if (showReadout) overlay.update(now, readout, settings.calibration ?: PenCalibration.DEFAULT, readoutExtras)
        val monoFont = overlay.font(fonts)
        val boxW = if (showReadout) overlay.width(text, monoFont) else 0f
        val boxH = if (showReadout) overlay.layoutHeight(monoFont) else 0f
        val boxRight = wDp - max(12f, safeR)
        val boxTop = safeT + 64f
        val tv = toast.visibility
        val toastFont = fonts.get(Face.UI_BOLD, 14f)
        // a toast too long for one line wraps to two (portrait)
        val toastMax = wDp * 0.92f
        val toastOne = text.width(toastFont, toast.message) + 28f
        val toastWrap = toastOne > toastMax
        val toastW = if (toastWrap) toastMax else toastOne
        val toastH = if (toastWrap) TOAST_LINE * 2f + 16f else 34f
        val toastTop = (if (tut != null) tutorialCard.box.bottom + 12f else safeT + if (ses != null) 84f else 70f) - (if (reduced) 0f else 8f * (1f - tv))

        shapes.begin(uiCam.combined, density)
        if (tut != null) {
            tutorialCard.drawShapes(shapes, pal, tut)
        } else if (screen != Screen.TITLE && screen != Screen.ENDING) {
            hud.drawShapes(
                shapes, pal, wDp, ink, hudSim.combo, hudPress.isPressed(hud.settingsButton), full = ses != null,
                pause = ses != null && pausable(ses), back = screen == Screen.SELECT, pressedBack = hudPress.isPressed(hud.backButton),
            )
            if (ses != null) {
                hud.drawProgress(shapes, pal, wDp, ses.sim, safeR)
                hud.drawLivesHeart(shapes, pal)
            }
        }
        gauge.drawShapes(shapes, pal, gaugeLeft, gaugeTop, live, drawing != null)
        val tag = inkTag(drawing, tut != null || ses != null)
        if (tag != null) shapes.color(pal[tagTok(tag)]).roundRect(tagX, tagY, TAG_W, 22f, 11f)
        if (showReadout) overlay.drawBox(shapes, pal, boxRight, boxTop, boxW, boxH)
        if (tv > 0f) shapes.color(pal[Tok.INK], tv).roundRect(wDp / 2f - toastW / 2f, toastTop, toastW, toastH, 17f)
        shapes.end()
        batch.projectionMatrix = uiCam.combined
        batch.begin()
        if (tut != null) {
            tutorialCard.drawText(text, pal, tut)
        } else if (screen != Screen.TITLE && screen != Screen.ENDING) {
            hud.drawText(text, fonts, pal, wDp, hudSim.combo, full = ses != null)
            if (screen == Screen.SELECT) hud.drawBackText(text, fonts, pal)
        }
        gauge.drawText(text, fonts, pal, gaugeLeft, gaugeTop, live, drawing != null)
        if (tag != null) text.line(fonts.get(Face.UI_BOLD, 12f), tag.label, tagX + TAG_W / 2f, tagY, 22f, pal[Tok.PAPER], 1f, Align.center)
        if (showReadout) overlay.drawText(text, monoFont, pal, boxRight - boxW, boxTop)
        if (tv > 0f) {
            if (toastWrap) {
                val lineHeight = toastFont.data.lineHeight
                toastFont.data.setLineHeight(TOAST_LINE * density)
                text.draw(toastFont, toast.message, wDp / 2f - toastW / 2f + 14f, toastTop + 8f + (TOAST_LINE - toastFont.capHeight) / 2f, pal[Tok.PAPER], tv, Align.center, toastW - 28f)
                toastFont.data.setLineHeight(lineHeight) // the font is shared
            } else {
                text.line(toastFont, toast.message, wDp / 2f, toastTop, 34f, pal[Tok.PAPER], tv, Align.center)
            }
        }
        batch.end()

        // Cards: the title, the stage list, or the stage's own cards (and the pause screen over
        // them). During the tutorial its card is the only one.
        if (tut == null && screen == Screen.TITLE) {
            titleCard.hoverOn = world.hoverOn
            titleCard.hoverX = world.hoverX
            titleCard.hoverY = world.hoverY
            titleCard.layout(text, frame)
            pass({ titleCard.drawShapes(shapes, pal, text, now, reduced) }, { titleCard.drawText(text, pal, now, reduced) })
        } else if (tut == null && screen == Screen.ENDING) {
            endingCard.layout(frame)
            endingCard.layoutBubble(text, frame)
            pass({ endingCard.drawShapes(shapes, pal, frame, now, reduced) }, { endingCard.drawText(text, pal, frame, now) })
        } else if (tut == null && screen == Screen.SELECT) {
            selectCard.layout(text, frame)
            pass({ selectCard.drawShapes(shapes, pal, frame) }, { selectCard.drawText(text, pal) })
        } else if (tut == null && ses != null) {
            drawStageCards(ses)
            if (pauseCard.open) {
                pauseCard.layout(frame)
                pauseCard.remarks = settings.remarks
                pass({ pauseCard.drawShapes(shapes, pal, frame, text, now, reduced) }, { pauseCard.drawText(text, pal, ses.stage) })
            } else if (countdown.running) {
                pass({ countdown.drawShapes(shapes, pal, frame, reduced) }, { countdown.drawText(text, pal, frame) })
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
        if (dev.faces) drawFaceGallery(now)
        // a new screen fades in from the paper
        val fade = if (reduced) 0f else 1f - ((now - fadeAt) / SCREEN_FADE_S).toFloat().coerceIn(0f, 1f)
        if (fade > 0f) {
            shapes.begin(uiCam.combined, density)
            shapes.color(pal[Tok.PAPER], fade).rect(0f, 0f, wDp, hDp)
            shapes.end()
        }
    }


    private var tagX = 0f
    private var tagY = 0f

    private val galleryFaces by lazy { com.pranshuparmar.inkmayhem.render.Expr.entries.map { e -> com.pranshuparmar.inkmayhem.render.BuddyState().also { it.set(e) } } }
    private val galleryDrawer by lazy { com.pranshuparmar.inkmayhem.render.BuddyDrawer(shapes) }
    private var galleryAt = Double.NaN

    /** Desktop --faces: every expression, large, on a plain page. */
    private fun drawFaceGallery(now: Double) {
        val dt = if (galleryAt.isNaN()) 0f else (now - galleryAt).toFloat()
        galleryAt = now
        val cols = 4
        val cw = wDp / cols
        val rows = (galleryFaces.size + cols - 1) / cols
        val ch = (hDp - 40f) / rows
        val r = min(cw, ch) * 0.2f
        shapes.begin(uiCam.combined, density)
        shapes.color(pal[Tok.PAPER]).rect(0f, 0f, wDp, hDp)
        galleryFaces.forEachIndexed { i, s ->
            s.update(dt)
            val cx = cw * (i % cols + 0.5f)
            val cy = 40f + ch * (i / cols + 0.5f)
            galleryDrawer.draw(pal, s, cx, cy, r, 1f, 1f)
        }
        shapes.end()
        batch.projectionMatrix = uiCam.combined
        batch.begin()
        galleryFaces.forEachIndexed { i, s ->
            val cx = cw * (i % cols + 0.5f)
            val cy = 40f + ch * (i / cols + 0.5f)
            text.line(fonts.get(Face.MONO_MEDIUM, 11f), s.expr.name, cx, cy + r * 1.5f, 16f, pal[Tok.MUTED], 1f, Align.center)
        }
        batch.end()
    }

    /**
     * Tutorial and stages: the ink under the pen, named right next to it while drawing (the gauge
     * on the edge is easy to miss). Places the tag at [tagX], [tagY].
     */
    private fun inkTag(d: GameSim.Drawing?, show: Boolean): Ink? {
        if (!show || d == null || d.size == 0 || d.smoothed.isNaN()) return null
        val kind = active.drawingInk ?: return null
        val i = d.size - 1
        tagX = ((d.xs[i] - world.camX) * scale + 16f).coerceIn(4f, wDp - TAG_W - 4f)
        tagY = ((d.ys[i] - world.camY) * scale - 44f).coerceIn(4f, hDp - 26f)
        return kind
    }

    private fun tagTok(k: Ink) = when (k) {
        Ink.ROPE -> Tok.ROPE
        Ink.SPRING -> Tok.SPRING
        Ink.BEAM -> Tok.BEAM
    }


    private fun drawStageCards(ses: StageSession) {
        when (ses.phase) {
            StageSession.Phase.INTRO -> if (stageIntro.visible(ses)) {
                pass({ stageIntro.drawShapes(shapes, pal, frame, ses) }, { stageIntro.drawText(text, pal, frame, ses) })
            }
            StageSession.Phase.GAME_OVER -> {
                gameOverCard.layout(text, frame)
                pass({ gameOverCard.drawShapes(shapes, pal, frame) }, { gameOverCard.drawText(text, pal, ses.stage) })
            }
            StageSession.Phase.CLEARED -> {
                val r = ses.result ?: return
                clearCard.layout(text, frame, r, ses.phaseT)
                pass({ clearCard.drawShapes(shapes, pal, frame, r) }, { clearCard.drawText(text, pal, r) })
            }
            else -> Unit
        }
    }

    private fun updateFrame() {
        frame.wDp = wDp
        frame.hDp = hDp
        frame.safeL = frames.insetL / density
        frame.safeT = frames.insetT / density
        frame.safeR = frames.insetR / density
        frame.safeB = frames.insetB / density
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
        "speed     ${String.format(Locale.US, "%.1f", active.runSpeed)} m/s  snaps ${active.snaps}",
        "device    ${platform.description}",
    )

    // ------------------------------------------------------------------------------ recording

    private var notedScreen: Screen? = null
    private var notedTutorial: Tutorial? = null
    private var notedLesson = -1
    private var notedPhase: Tutorial.Phase? = null
    private var notedFeedback = ""
    private var notedSession: StageSession? = null
    private var notedStagePhase: StageSession.Phase? = null
    private var notedLives = -1
    private var notedStage = ""

    /** Recordings: notes screen, lesson and stage changes, and the tutorial's hints. */
    private fun noteChanges() {
        if (screen != notedScreen) {
            notedScreen = screen
            log { "screen ${screen.name}" }
        }
        val t = tutorial
        if (t !== notedTutorial) {
            notedTutorial = t
            notedLesson = -1
            notedPhase = null
            notedFeedback = ""
            if (t == null) log("tutorial closed")
        }
        if (t != null) {
            if (t.lessonIndex != notedLesson || t.phase != notedPhase) {
                notedLesson = t.lessonIndex
                notedPhase = t.phase
                log { "tutorial ${t.lesson.name} ${t.phase.name}" }
            }
            if (t.feedback != notedFeedback) {
                notedFeedback = t.feedback
                if (t.feedback.isNotEmpty()) log { "tutorial says: ${t.feedback}" }
            }
        }
        val ses = session
        if (ses !== notedSession) {
            notedSession = ses
            notedStagePhase = null
            notedLives = -1
            notedStage = ""
        }
        if (ses != null && (ses.phase != notedStagePhase || ses.lives != notedLives || ses.stage.id != notedStage)) {
            notedStagePhase = ses.phase
            notedLives = ses.lives
            notedStage = ses.stage.id
            log { "stage ${ses.stage.id} ${ses.phase.name} lives ${ses.lives}" }
        }
    }

    /**
     * Recordings: the stroke about to be finished, in world metres and normalized pressure, and the
     * inks it showed while drawn; the ink it settles on is added once it is finished.
     */
    private fun strokeNote(pointer: Int): String? {
        val d = active.drawing ?: return null
        if (d.pointer != pointer || d.size == 0) return null
        var y0 = Float.POSITIVE_INFINITY
        var y1 = Float.NEGATIVE_INFINITY
        var p0 = Float.POSITIVE_INFINITY
        var p1 = Float.NEGATIVE_INFINITY
        for (i in 0 until d.size) {
            y0 = min(y0, d.ys[i]); y1 = max(y1, d.ys[i])
            p0 = min(p0, d.ps[i]); p1 = max(p1, d.ps[i])
        }
        val inks = Ink.entries.filter { d.kinds and (1 shl it.weight) != 0 }.joinToString("+") { it.label }
        return String.format(
            Locale.US, "stroke %d points x %.2f..%.2f y %.2f..%.2f p %.2f..%.2f showed %s%s",
            d.size, d.xs[0], d.xs[d.size - 1], y0, y1, p0, p1, inks.ifEmpty { "none" },
            if (d.startAnchored) " anchored" else "",
        )
    }

    override fun dispose() {
        recorder?.close()
        tutorial?.sim?.dispose()
        session?.sim?.dispose()
        sim.dispose()
        audio.dispose()
        shapes.dispose()
        batch.dispose()
        fonts.dispose()
    }

    companion object {
        const val STEP = 1f / 60f
        const val FINGER_TOAST = "Use the S Pen. Fingers don’t leave ink."
        const val TUNE_LATER = "For the best feel, tune your pen in settings."
        const val BOSS_MUSIC = "boss"
        /** A new screen fades in from the paper over this long. */
        private const val SCREEN_FADE_S = 0.3
        const val FIRST_STAR = "Stars! Every ${GameSim.STARS_PER_LIFE} is an extra life."
        const val READY_TOAST = "You’re ready. The lightest ink that works grows your combo."
        const val SNAP_BUZZ_MS = 12
        private const val TAG_W = 58f
        private const val TOAST_LINE = 19f
        /** Metres a view always shows across (portrait zooms out to fit them). */
        private const val MIN_VIEW_W = 15f
        /** Frames between recording probes (eight a second at 120 Hz). */
        private const val PROBE_EVERY = 15L
        const val DEATH_BUZZ_MS = 45
    }
}
