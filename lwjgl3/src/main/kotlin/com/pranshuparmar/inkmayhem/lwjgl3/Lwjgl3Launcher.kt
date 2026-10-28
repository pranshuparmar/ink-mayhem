package com.pranshuparmar.inkmayhem.lwjgl3

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.pranshuparmar.inkmayhem.DevOptions
import com.pranshuparmar.inkmayhem.InkMayhemGame
import com.pranshuparmar.inkmayhem.game.HazardType
import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.Platform
import com.pranshuparmar.inkmayhem.input.PenButtons
import com.pranshuparmar.inkmayhem.input.PenPhase
import com.pranshuparmar.inkmayhem.input.PenQueue
import com.pranshuparmar.inkmayhem.input.ToolType

/**
 * Desktop launcher for fast iteration. The mouse arrives as a mouse (so it needs the debug
 * "Allow finger and mouse" setting, like on Android); with --emulate-pen it acts as a stylus:
 * keys 1 / 2 / 3 give light / normal / hard pressure and the right button is the side button.
 *
 * Options: --landscape, --size WxH (dp), --density D, --dark, --reduced-motion,
 *          --autoplay (the correct-ink bot plays), --wrong-ink TUNNEL=SPRING, --seed N, --settings,
 *          --calibration, --skip-calibration, --tutorial LESSON (1-based), --skip-tutorial,
 *          --stage 1-2 (with --autoplay its solution plays), --select, --speed N,
 *          --rotate-after seconds, --demo-draw,
 *          --screenshot out.png --after seconds
 */
fun main(args: Array<String>) {
    val opts = Options(args)
    val platform = DesktopPlatform(opts)
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Ink Mayhem")
        val (w, h) = opts.sizeDp
        setWindowedMode((w * opts.density).toInt(), (h * opts.density).toInt())
        setBackBufferConfig(8, 8, 8, 8, 0, 0, 4)
        useVsync(true)
        setForegroundFPS(0)
        setResizable(true)
    }
    val dev = DevOptions(
        seed = opts.seed,
        autoplay = opts.autoplay,
        openSettings = opts.openSettings,
        openCalibration = opts.openCalibration,
        skipCalibration = opts.skipCalibration || opts.autoplay,
        tutorialLesson = opts.tutorialLesson?.minus(1),
        skipTutorial = opts.skipTutorial,
        stage = opts.stage,
        openSelect = opts.openSelect,
        speed = opts.speed,
        wrongInk = opts.wrongInk,
    )
    val game = InkMayhemGame(platform, dev)
    Lwjgl3Application(DesktopHost(game, platform, opts), config)
}

private class Options(args: Array<String>) {
    private val list = args.toList()
    private fun value(name: String): String? = list.indexOf(name).takeIf { it >= 0 }?.let { list.getOrNull(it + 1) }
    val landscape = "--landscape" in list
    val sizeDp: Pair<Int, Int> = value("--size")?.split('x')?.let { it[0].toInt() to it[1].toInt() }
        ?: if (landscape) 900 to 420 else 420 to 900
    val density = value("--density")?.toFloat() ?: 1f
    val dark = "--dark" in list
    val reducedMotion = "--reduced-motion" in list
    val emulatePen = "--emulate-pen" in list
    val autoplay = "--autoplay" in list
    val seed = value("--seed")?.toLong()
    val openSettings = "--settings" in list
    val openCalibration = "--calibration" in list
    val skipCalibration = "--skip-calibration" in list
    val tutorialLesson = value("--tutorial")?.toInt()
    val skipTutorial = "--skip-tutorial" in list
    val stage = value("--stage")
    val openSelect = "--select" in list
    val speed = value("--speed")?.toFloat() ?: 1f
    /** e.g. --wrong-ink TUNNEL=SPRING */
    val wrongInk = value("--wrong-ink")?.split(',')?.associate {
        val (h, i) = it.split('=')
        HazardType.valueOf(h) to Ink.valueOf(i)
    } ?: emptyMap()
    /** Starts a run, hovers, then slowly draws a stroke with rising pressure (to see the gauge). */
    val demoDraw = "--demo-draw" in list
    /** Swaps width and height after this many seconds, like rotating the phone mid-run. */
    val rotateAfter = value("--rotate-after")?.toFloat()
    val screenshot: String? = value("--screenshot")
    val after = value("--after")?.toFloat() ?: 2f
}

private class DesktopPlatform(private val opts: Options) : Platform {
    override val isDebugBuild = true
    override val penQueue = PenQueue()
    override val density get() = opts.density
    override val description = "Desktop ${System.getProperty("os.name")}"
    override fun isDarkMode() = opts.dark
    override fun isReducedMotion() = opts.reducedMotion
    override fun vibrate(millis: Int, amplitude: Int) = Unit
}

/** Feeds mouse input into the pen queue and handles --screenshot. */
private class DesktopHost(
    private val game: ApplicationListener,
    private val platform: DesktopPlatform,
    private val opts: Options,
) : ApplicationListener by game {
    private var pressure = 0.32f // raw; normal writing at the default calibration
    private var buttons = 0
    private var elapsed = 0f

    override fun create() {
        game.create()
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(x: Int, y: Int, pointer: Int, button: Int): Boolean {
                buttons = if (button == Input.Buttons.RIGHT) (PenButtons.SIDE or PenButtons.SECONDARY) else 0
                emit(x, y, PenPhase.DOWN)
                return true
            }

            override fun touchDragged(x: Int, y: Int, pointer: Int): Boolean {
                emit(x, y, PenPhase.MOVE)
                return true
            }

            override fun touchUp(x: Int, y: Int, pointer: Int, button: Int): Boolean {
                emit(x, y, PenPhase.UP)
                buttons = 0
                return true
            }

            override fun mouseMoved(x: Int, y: Int): Boolean {
                emit(x, y, PenPhase.HOVER)
                return true
            }

            override fun keyDown(keycode: Int): Boolean {
                when (keycode) {
                    Input.Keys.NUM_1 -> pressure = 0.15f
                    Input.Keys.NUM_2 -> pressure = 0.32f
                    Input.Keys.NUM_3 -> pressure = 0.6f
                    else -> return false
                }
                return true
            }
        }
    }

    private fun emit(x: Int, y: Int, phase: PenPhase) {
        val tool = if (opts.emulatePen) ToolType.STYLUS else ToolType.MOUSE
        val p = if (phase == PenPhase.HOVER) 0f else pressure
        platform.penQueue.offer(x.toFloat(), y.toFloat(), p, 0f, 0f, buttons, phase, tool, 0, System.nanoTime())
    }

    private var rotated = false
    private var demoStep = 0

    /** --demo-draw: pen down to start, hover from 1 s, then a 1.5 s stroke from light to hard. */
    private fun demoDraw() {
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        val q = platform.penQueue
        val now = System.nanoTime()
        if (demoStep == 0) {
            q.offer(w / 2, h / 2, 0.3f, 0f, 0f, 0, PenPhase.DOWN, ToolType.STYLUS, 3, now)
            q.offer(w / 2, h / 2, 0.3f, 0f, 0f, 0, PenPhase.UP, ToolType.STYLUS, 3, now)
            demoStep = 1
        }
        val t = elapsed
        val x0 = w * 0.42f
        val y0 = h * 0.5f
        when {
            t in 1f..1.6f -> q.offer(x0, y0, 0f, 0f, 0f, 0, PenPhase.HOVER, ToolType.STYLUS, 3, now)
            t > 1.6f && demoStep == 1 -> {
                q.offer(x0, y0, 0.1f, 0f, 0f, 0, PenPhase.DOWN, ToolType.STYLUS, 4, now)
                demoStep = 2
            }
            demoStep == 2 && t < 3.1f -> {
                val u = (t - 1.6f) / 1.5f
                q.offer(x0 + u * w * 0.5f, y0 - u * h * 0.06f, 0.1f + 0.5f * u, 0f, 0f, 0, PenPhase.MOVE, ToolType.STYLUS, 4, now)
            }
            demoStep == 2 -> {
                q.offer(x0 + w * 0.5f, y0 - h * 0.06f, 0.6f, 0f, 0f, 0, PenPhase.UP, ToolType.STYLUS, 4, now)
                demoStep = 3
            }
        }
    }

    override fun render() {
        if (opts.demoDraw) demoDraw()
        game.render()
        elapsed += Gdx.graphics.deltaTime
        val rotateAfter = opts.rotateAfter
        if (rotateAfter != null && !rotated && elapsed >= rotateAfter) {
            rotated = true
            Gdx.graphics.setWindowedMode(Gdx.graphics.width.let { Gdx.graphics.height }, Gdx.graphics.width)
        }
        val shot = opts.screenshot ?: return
        if (elapsed >= opts.after) {
            val w = Gdx.graphics.backBufferWidth
            val h = Gdx.graphics.backBufferHeight
            // createFromFrameBuffer already returns rows top-down.
            val pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h)
            val px = pixmap.pixels
            for (i in 3 until w * h * 4 step 4) px.put(i, 0xFF.toByte()) // the screen ignores alpha; so does the PNG
            PixmapIO.writePNG(Gdx.files.absolute(shot), pixmap, -1, true)
            pixmap.dispose()
            Gdx.app.exit()
        }
    }
}
