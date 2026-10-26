package com.pranshuparmar.inkmayhem.lwjgl3

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.pranshuparmar.inkmayhem.InkMayhemGame
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
 *          --demo-strokes (injects light / normal / hard pen strokes), --screenshot out.png --after seconds
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
    val game = InkMayhemGame(platform)
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
    val demoStrokes = "--demo-strokes" in list
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
        if (opts.demoStrokes) Gdx.app.postRunnable { injectDemoStrokes() }
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

    /** Three wavy strokes at light, normal and hard raw pressure, then a hovering nib. */
    private fun injectDemoStrokes() {
        val q = platform.penQueue
        val d = opts.density
        val w = Gdx.graphics.width / d
        for ((row, raw) in listOf(0.15f, 0.32f, 0.6f).withIndex()) {
            val y0 = (300f + row * 90f) * d
            for (i in 0..60) {
                val x = (80f + (w - 140f) * i / 60f) * d
                val y = y0 + 18f * d * kotlin.math.sin(i / 6f)
                val phase = when (i) { 0 -> PenPhase.DOWN; 60 -> PenPhase.UP; else -> PenPhase.MOVE }
                q.offer(x, y, raw, 0f, 0f, 0, phase, ToolType.STYLUS, 1, System.nanoTime())
            }
        }
        q.offer(w * 0.6f * d, 620f * d, 0f, 0f, 0f, 0, PenPhase.HOVER, ToolType.STYLUS, 1, System.nanoTime())
    }

    private fun emit(x: Int, y: Int, phase: PenPhase) {
        val tool = if (opts.emulatePen) ToolType.STYLUS else ToolType.MOUSE
        val p = if (phase == PenPhase.HOVER) 0f else pressure
        platform.penQueue.offer(x.toFloat(), y.toFloat(), p, 0f, 0f, buttons, phase, tool, 0, System.nanoTime())
    }

    override fun render() {
        game.render()
        val shot = opts.screenshot ?: return
        elapsed += Gdx.graphics.deltaTime
        if (elapsed >= opts.after) {
            val w = Gdx.graphics.backBufferWidth
            val h = Gdx.graphics.backBufferHeight
            // createFromFrameBuffer already returns rows top-down.
            val pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h)
            PixmapIO.writePNG(Gdx.files.absolute(shot), pixmap, -1, true)
            pixmap.dispose()
            Gdx.app.exit()
        }
    }
}
