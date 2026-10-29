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
import com.pranshuparmar.inkmayhem.record.InputLog
import com.pranshuparmar.inkmayhem.record.MemoryPreferences
import com.pranshuparmar.inkmayhem.record.Replay
import com.pranshuparmar.inkmayhem.record.ReplayFrames
import com.pranshuparmar.inkmayhem.record.ReplayTarget
import java.io.File
import java.util.zip.Deflater

/**
 * Desktop launcher for fast iteration. The mouse arrives as a mouse (so it needs the debug
 * "Allow finger and mouse" setting, like on Android); with --emulate-pen it acts as a stylus:
 * keys 1 / 2 / 3 give light / normal / hard pressure and the right button is the side button.
 *
 * Options: --landscape, --size WxH (dp), --density D, --dark, --reduced-motion,
 *          --autoplay (the correct-ink bot plays), --wrong-ink TUNNEL=SPRING, --seed N, --settings,
 *          --calibration, --skip-calibration, --tutorial LESSON (1-based), --skip-tutorial,
 *          --stage 1-2 (with --autoplay its solution plays), --select, --speed N,
 *          --rotate-after seconds, --demo-draw, --demo-tune LINES (with --calibration),
 *          --screenshot out.png --after seconds,
 *          --record out.txt (an input recording, as debug builds make on Android),
 *          --replay rec.txt [--frames dir --every N --from S --to S] (plays a recording back exactly;
 *            --after then counts the recording's clock)
 */
fun main(args: Array<String>) {
    val opts = Options(args)
    if (opts.replay != null) return replay(opts)
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
    val game = InkMayhemGame(platform, devOptions(opts, opts.seed))
    Lwjgl3Application(DesktopHost(game, platform, opts), config)
}

private fun devOptions(opts: Options, seed: Long?) = DevOptions(
    seed = seed,
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
    toast = opts.toast,
)

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
    val toast = value("--toast")
    /** e.g. --wrong-ink TUNNEL=SPRING */
    val wrongInk = value("--wrong-ink")?.split(',')?.associate {
        val (h, i) = it.split('=')
        HazardType.valueOf(h) to Ink.valueOf(i)
    } ?: emptyMap()
    /** Starts a run, hovers, then slowly draws a stroke with rising pressure (to see the gauge). */
    val demoDraw = "--demo-draw" in list
    /**
     * Draws this many pen-tuning lines across the middle of the screen with a light pen's raw
     * pressures (rope 0.03, spring 0.07, beam 0.2), then a free light-to-hard stroke if 4.
     */
    val demoTune = value("--demo-tune")?.toInt() ?: 0
    /** Swaps width and height after this many seconds, like rotating the phone mid-run. */
    val rotateAfter = value("--rotate-after")?.toFloat()
    val screenshot: String? = value("--screenshot")
    val after = value("--after")?.toFloat() ?: 2f
    val record: String? = value("--record")
    val replay: String? = value("--replay")
    /** Replay: save every Nth frame as a PNG into this directory, between --from and --to seconds. */
    val frames: String? = value("--frames")
    val every = value("--every")?.toInt() ?: 4
    val from = value("--from")?.toDouble() ?: 0.0
    val to = value("--to")?.toDouble() ?: Double.MAX_VALUE
    /** Replay window height in pixels (the game still sees the recorded screen size). */
    val replayHeight = value("--replay-height")?.toInt() ?: 900
    /** Replay: don't put the runner back at each probe (to see raw drift). */
    val noResync = "--no-resync" in list
    val args = list
}

private class DesktopPlatform(private val opts: Options) : Platform {
    override val isDebugBuild = true
    override val penQueue = PenQueue()
    /** A replay sets the recorded density. */
    var densityValue = opts.density
    override val density get() = densityValue
    override val description = "Desktop ${System.getProperty("os.name")}"
    override fun isDarkMode() = opts.dark
    override fun isReducedMotion() = opts.reducedMotion
    override fun vibrate(millis: Int, amplitude: Int) = Unit
    override val appVersion = DESKTOP + opts.args.joinToString(" ")
    /** A replay starts from the recorded preferences instead of the desktop's own. */
    var prefs: com.badlogic.gdx.Preferences? = null
    override fun preferences() = prefs ?: super.preferences()
    override fun newRecording(): File? = opts.record?.let(::File)
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

    private var tuneLine = 0
    private var tuneT = 0f

    /** --demo-tune: one 0.6 s line every 1.2 s, starting at 0.8 s. */
    private fun demoTune() {
        if (tuneLine >= opts.demoTune) return
        val w = Gdx.graphics.width.toFloat()
        val h = Gdx.graphics.height.toFloat()
        val q = platform.penQueue
        val now = System.nanoTime()
        val u = (elapsed - 0.8f - tuneLine * 1.2f) / 0.6f
        if (u < 0f) return
        val free = tuneLine == 3
        val p = if (free) 0.02f + 0.28f * u.coerceIn(0f, 1f) else TUNE_RAW[tuneLine]
        val x = w * (0.18f + 0.64f * u.coerceIn(0f, 1f))
        val y = h * 0.5f + if (free) h * 0.03f * kotlin.math.sin(u * 9f) else 0f
        val phase = when {
            tuneT == 0f -> PenPhase.DOWN
            u >= 1f -> PenPhase.UP
            else -> PenPhase.MOVE
        }
        q.offer(x, y, p, 0f, 0f, 0, phase, ToolType.STYLUS, 7, now)
        tuneT += 1f
        if (phase == PenPhase.UP) {
            tuneLine++
            tuneT = 0f
        }
    }

    override fun render() {
        if (opts.demoDraw) demoDraw()
        demoTune()
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

private val TUNE_RAW = floatArrayOf(0.03f, 0.07f, 0.2f)

/**
 * --replay: plays a recording back through the real game. Each frame of this window runs one
 * recorded frame (its clock, time step and pen samples), so the session unfolds exactly as it
 * did, as fast as this machine renders. Prints what happened and checks the recorded probes.
 */
private fun replay(opts: Options) {
    val path = opts.replay ?: return
    val log = File(path).bufferedReader().use { InputLog.read(it) }
    val first = log.steps.firstOrNull { it is InputLog.Step.Resize } as InputLog.Step.Resize?
        ?: error("the recording never sized the screen")
    val platform = DesktopPlatform(opts)
    platform.densityValue = first.density
    platform.prefs = MemoryPreferences.of(log.prefs)
    val frames = ReplayFrames()
    val seed = log.meta["seed"]?.toLongOrNull()
    // a desktop recording replays with the options it was made with (an Android one has none)
    val app = log.meta["app"].orEmpty()
    val dev = if (app.startsWith(DESKTOP)) {
        devOptions(Options(app.removePrefix(DESKTOP).split(' ').filter { it.isNotEmpty() }.toTypedArray()), seed)
    } else {
        DevOptions(seed = seed)
    }
    val game = InkMayhemGame(platform, dev, frames)
    println("replaying ${log.frames} frames from ${log.meta["app"]} on ${log.meta["device"]}, started ${log.meta["started"]}")
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Ink Mayhem replay")
        // even sides, so the saved frames make a video without padding
        val k = opts.replayHeight.toFloat() / first.h
        setWindowedMode((first.w * k).toInt() / 2 * 2, opts.replayHeight / 2 * 2)
        setBackBufferConfig(8, 8, 8, 8, 0, 0, 4)
        useVsync(false)
        setForegroundFPS(0)
        setResizable(false)
    }
    Lwjgl3Application(ReplayHost(game, platform, Replay(log, frames, platform.penQueue), opts), config)
}

private class ReplayHost(
    private val game: InkMayhemGame,
    private val platform: DesktopPlatform,
    private val replay: Replay,
    private val opts: Options,
) : ApplicationListener {
    private var probes = 0
    private var mismatches = 0
    private var maxDrift = 0.0
    private var maxDriftAt = 0.0
    private var resyncs = 0
    private var lastNow = 0.0
    private var saved = 0
    private var done = false

    private val target = object : ReplayTarget {
        override fun resize(w: Int, h: Int, density: Float) {
            platform.densityValue = density
            game.resize(w, h)
        }
        override fun pause() = game.pause()
        override fun resume() = game.resume()
        override fun back() = game.back()
    }

    override fun create() {
        game.create()
        opts.frames?.let { File(it).mkdirs() }
    }

    // the window's own size, pauses and so on don't reach the game: only the recording's do
    override fun resize(width: Int, height: Int) = Unit
    override fun pause() = Unit
    override fun resume() = Unit

    override fun render() {
        if (done) return
        val f = replay.next(target)
        if (f == null) {
            finish()
            return
        }
        game.render()
        lastNow = f.now
        for (e in f.events) println(String.format(java.util.Locale.US, "[%8.3f] %s", f.now, e))
        f.probe?.let { expected ->
            probes++
            // the first six fields say where the game stands; position drift is measured in metres
            val e = expected.split(' ')
            val got = game.probe()
            val g = got.split(' ')
            val sameState = e[0] == g[0] && e[1] == g[1] && e.getOrNull(5) == g.getOrNull(5)
            val d = Math.hypot(e[2].toDouble() - g[2].toDouble(), e[3].toDouble() - g[3].toDouble())
            if (d > maxDrift) {
                maxDrift = d
                maxDriftAt = f.now
            }
            if (!sameState) {
                mismatches++
                if (mismatches <= 5) println(String.format(java.util.Locale.US, "[%8.3f] DIFFERS expected \"%s\" got \"%s\"", f.now, e.take(6).joinToString(" "), g.take(6).joinToString(" ")))
            }
            // snap back only where it differs: a replay on the recording's own machine stays exact
            if (!opts.noResync && got != expected && game.resync(expected)) resyncs++
        }
        val dir = opts.frames
        if (dir != null && f.now >= opts.from && f.now <= opts.to && replay.frameIndex % opts.every == 0) {
            savePng(File(dir, String.format(java.util.Locale.US, "f%06d.png", saved++)))
        }
        val shot = opts.screenshot
        if (shot != null && f.now >= opts.after) {
            savePng(File(shot))
            finish()
        }
        if (f.now > opts.to) finish()
    }

    private fun savePng(file: File) {
        val w = Gdx.graphics.backBufferWidth
        val h = Gdx.graphics.backBufferHeight
        val pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h)
        val px = pixmap.pixels
        for (i in 3 until w * h * 4 step 4) px.put(i, 0xFF.toByte())
        PixmapIO.writePNG(Gdx.files.absolute(file.absolutePath), pixmap, Deflater.BEST_SPEED, true)
        pixmap.dispose()
    }

    private fun finish() {
        if (done) return
        done = true
        println(String.format(
            java.util.Locale.US, "replayed %d frames (%.1f s); probes %d: %d differed in state, runner drift up to %.4f m (at %.1f s), %s%s",
            replay.frameIndex + 1, lastNow, probes, mismatches, maxDrift, maxDriftAt,
            if (opts.noResync) "no resync" else "resynced $resyncs",
            if (saved > 0) "; saved $saved frames" else "",
        ))
        Gdx.app.exit()
    }

    override fun dispose() = game.dispose()
}

private const val DESKTOP = "desktop "
