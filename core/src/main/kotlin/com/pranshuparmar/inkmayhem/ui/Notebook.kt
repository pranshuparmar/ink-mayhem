package com.pranshuparmar.inkmayhem.ui

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Align
import com.pranshuparmar.inkmayhem.Settings
import com.pranshuparmar.inkmayhem.game.Doodle
import com.pranshuparmar.inkmayhem.game.Jukebox
import com.pranshuparmar.inkmayhem.game.Stages
import com.pranshuparmar.inkmayhem.game.Sticker
import com.pranshuparmar.inkmayhem.input.PenPhase
import com.pranshuparmar.inkmayhem.input.PenSample
import com.pranshuparmar.inkmayhem.physics.Ink
import com.pranshuparmar.inkmayhem.render.Face
import com.pranshuparmar.inkmayhem.render.Fonts
import com.pranshuparmar.inkmayhem.render.PageAlbum
import com.pranshuparmar.inkmayhem.render.Palette
import com.pranshuparmar.inkmayhem.render.Shapes
import com.pranshuparmar.inkmayhem.render.Text
import com.pranshuparmar.inkmayhem.render.Tok
import kotlin.math.max
import kotlin.math.min

/**
 * The Notebook, from the title screen: the player's numbers (Stats), the sticker book, the
 * pages they tore out and results they shared (Pages), the jukebox (Music) and, once the ending
 * has been reached, Doodle mode. Credits and Done at its foot. A card over the title page that
 * takes every pen sample while open.
 */
class NotebookCard(
    private val fonts: Fonts,
    private val settings: Settings,
    private val album: PageAlbum,
    private val onCredits: () -> Unit,
    /** The jukebox or Doodle mode changed. */
    private val onChanged: () -> Unit,
    private val onShare: (FileHandle) -> Unit,
    private val onTap: () -> Unit = {},
    /** Debug builds: Doodle mode is open before the ending (for testing). */
    private val debugOpen: Boolean = false,
) {
    /** Doodle mode's switches can be used: the ending has been reached (or a debug build). */
    private val doodleOpen: Boolean get() = debugOpen || settings.endingSeen

    enum class Tab(val label: String) { STATS("Stats"), STICKERS("Stickers"), PAGES("Pages"), MUSIC("Music"), DOODLE("Doodle") }

    var open = false
        private set
    var tab = Tab.STATS
        private set

    private val card = Box()
    private val tabBoxes = Array(Tab.entries.size) { Box() }
    private val done = Box()
    private val credits = Box()
    private val content = Box()
    private val press = Press()
    private var cols = 1
    private var rowH = ROW_H

    // ---- stats: label and value pairs, read when the Notebook opens
    private var stats: List<Pair<String, String>> = emptyList()
    private val statBoxes = ArrayList<Box>()

    // ---- stickers
    private var earned = 0L
    private val stickerBoxes = Array(Sticker.entries.size) { Box() }
    private var picked: Sticker? = null
    private var stickerR = 22f

    // ---- music and doodle rows
    private class Row(val target: Any) { val box = Box() }
    private val musicRows = buildList { add(Row(OWN)); Jukebox.TRACKS.forEach { add(Row(it)) } }
    private val doodleRows = Doodle.entries.map { Row(it) }
    private val unlocked = BooleanArray(Jukebox.TRACKS.size)

    // ---- pages
    private var pages: List<FileHandle> = emptyList()
    private val thumbs = HashMap<String, Texture>()
    private val pageBoxes = ArrayList<Box>()
    private var pageFirst = 0
    private var perPage = 6
    private val prev = Box()
    private val nextPage = Box()
    private var viewing: FileHandle? = null
    private var viewTex: Texture? = null
    private val viewBox = Box()
    private val share = Box()
    private val delete = Box()
    private val back = Box()
    private var pageRange = ""

    fun show(t: Tab = tab) {
        open = true
        press.cancel()
        refresh()
        select(t)
    }

    fun hide() {
        open = false
        press.cancel()
        closeViewer()
        dropThumbs()
    }

    /** Reads the save: the numbers, stickers, unlocked tunes and pictures. */
    fun refresh() {
        val st = settings.stats
        val all = Stages.ALL
        val bosses = all.filter { it.layout.arenas.isNotEmpty() }
        stats = listOf(
            "Stages cleared" to "${all.count { settings.stars(it.id) > 0 }} / ${all.size}",
            "Stars" to "${all.sumOf { settings.stars(it.id) }} / ${all.size * 3}",
            "Gold stars" to "${all.sumOf { Integer.bitCount(settings.gold(it.id)) }} / ${all.size * Sticker.GOLD_PER_STAGE}",
            "Bosses beaten" to "${bosses.count { settings.stars(it.id) > 0 }} / ${bosses.size}",
            "Strokes drawn" to Format.thousands(st.totalStrokes),
            "Rope · spring · beam" to "${Format.thousands(st.strokes[Ink.ROPE.weight])} · ${Format.thousands(st.strokes[Ink.SPRING.weight])} · ${Format.thousands(st.strokes[Ink.BEAM.weight])}",
            "Ink drawn" to "${Format.thousands(st.metres.toInt())} m",
            "Strokes erased" to Format.thousands(st.erases),
            "Lives lost" to Format.thousands(st.livesLost),
            "Best combo" to "×${st.bestCombo}",
            "Stages played" to Format.thousands(st.starts),
            "Hints asked" to Format.thousands(st.hintsAsked),
            "Stickers" to "${Sticker.entries.count { it.on(settings.stickers) }} / ${Sticker.entries.size}",
        )
        earned = settings.stickers
        Jukebox.TRACKS.forEachIndexed { i, t -> unlocked[i] = Jukebox.unlocked(t, settings) }
        pages = album.list()
        pageFirst = pageFirst.coerceIn(0, max(0, pages.size - 1))
    }

    private fun select(t: Tab) {
        if (t != Tab.PAGES) {
            closeViewer()
            dropThumbs()
        }
        tab = t
        picked = null
    }

    // ------------------------------------------------------------------------------- layout

    fun layout(text: Text, f: Frame) {
        val wide = f.wDp > f.hDp && f.wDp >= 640f
        val w = min(f.wDp - f.padL - f.padR, if (wide) 720f else 420f)
        val h = min(f.hDp - f.safeT - f.safeB - 24f, 660f)
        card.set((f.wDp - w) / 2f + (f.padL - f.padR) / 2f, (f.hDp - h) / 2f + (f.safeT - f.safeB) / 2f, w, h)
        val tw = (card.w - 40f - (Tab.entries.size - 1) * 6f) / Tab.entries.size
        val ty = card.y + 20f + 34f + 8f
        tabBoxes.forEachIndexed { i, b -> b.set(card.x + 20f + i * (tw + 6f), ty, tw, TAB_H) }
        val font = fonts.get(Face.UI_BOLD, 14f)
        val fy = card.bottom - 18f - 42f
        val dw = text.width(font, "Done") + 40f
        done.set(card.right - 20f - dw, fy, dw, 42f)
        val cw = text.width(font, "Credits") + 32f
        credits.set(card.x + 20f, fy, cw, 42f)
        content.set(card.x + 20f, ty + TAB_H + 14f, card.w - 40f, fy - 10f - (ty + TAB_H + 14f))
        when (tab) {
            Tab.STATS -> layoutStats()
            Tab.STICKERS -> layoutStickers()
            Tab.PAGES -> layoutPages(text)
            Tab.MUSIC -> layoutRows(musicRows, 0f)
            Tab.DOODLE -> layoutRows(doodleRows, NOTE_H)
        }
    }

    private fun columns(n: Int, row: Float, reserve: Float): Int =
        if (n * row > content.h - reserve && content.w >= 520f) 2 else 1

    private fun layoutStats() {
        cols = columns(stats.size, STAT_H, 0f)
        val rows = (stats.size + cols - 1) / cols
        val rh = min(STAT_H, content.h / rows)
        val colW = (content.w - (cols - 1) * COL_GAP) / cols
        while (statBoxes.size < stats.size) statBoxes += Box()
        stats.indices.forEach { i ->
            statBoxes[i].set(content.x + (i / rows) * (colW + COL_GAP), content.y + (i % rows) * rh, colW, rh)
        }
        rowH = rh
    }

    private fun layoutStickers() {
        val n = Sticker.entries.size
        val c = when {
            content.w >= 600f -> 8
            else -> 4
        }
        val rows = (n + c - 1) / c
        val cw = content.w / c
        val ch = min(86f, (content.h - CAPTION_H) / rows)
        stickerR = min(22f, ch * 0.3f)
        Sticker.entries.forEachIndexed { i, _ -> stickerBoxes[i].set(content.x + (i % c) * cw, content.y + (i / c) * ch, cw, ch) }
    }

    private fun layoutRows(rows: List<Row>, reserve: Float) {
        cols = columns(rows.size, ROW_H, reserve)
        val per = (rows.size + cols - 1) / cols
        val rh = min(ROW_H, (content.h - reserve) / per)
        val colW = (content.w - (cols - 1) * COL_GAP) / cols
        rows.forEachIndexed { i, r -> r.box.set(content.x + (i / per) * (colW + COL_GAP), content.y + (i % per) * rh, colW, rh) }
        rowH = rh
    }

    private fun layoutPages(text: Text) {
        val v = viewing
        if (v != null) {
            val by = content.bottom - 42f
            val font = fonts.get(Face.UI_BOLD, 14f)
            val bw = text.width(font, "Back") + 32f
            val sw = text.width(font, "Share") + 40f
            val dw = text.width(font, "Delete") + 32f
            back.set(content.x, by, bw, 42f)
            share.set(content.right - sw, by, sw, 42f)
            delete.set(share.x - 8f - dw, by, dw, 42f)
            val tex = viewTex
            val area = Box(content.x, content.y, content.w, content.h - 52f)
            if (tex != null) fit(tex, area, viewBox) else viewBox.set(area.x, area.y, area.w, area.h)
            return
        }
        val c = if (content.w >= 600f) 6 else 3
        val gap = 8f
        val cw = (content.w - (c - 1) * gap) / c
        val aspect = thumbs.values.firstOrNull()?.let { it.height.toFloat() / it.width } ?: 2f
        val navH = 42f
        val ch = min(cw * aspect, content.h - navH - 8f)
        val rowsFit = max(1, ((content.h - navH - 8f + gap) / (ch + gap)).toInt())
        perPage = c * rowsFit
        if (pageFirst >= pages.size) pageFirst = 0
        pageFirst -= pageFirst % perPage
        pageBoxes.clear()
        for (i in 0 until min(perPage, pages.size - pageFirst)) {
            pageBoxes += Box(content.x + (i % c) * (cw + gap), content.y + (i / c) * (ch + gap), cw, ch)
        }
        val ny = content.bottom - navH
        prev.set(content.x, ny, 84f, navH)
        nextPage.set(content.right - 84f, ny, 84f, navH)
        pageRange = if (pages.isEmpty()) "" else "${pageFirst + 1}–${min(pages.size, pageFirst + perPage)} of ${pages.size}"
        loadThumbs()
    }

    /** [tex] scaled to fit inside [area], centred, into [out]. */
    private fun fit(tex: Texture, area: Box, out: Box) {
        val s = min(area.w / tex.width, area.h / tex.height)
        val w = tex.width * s
        val h = tex.height * s
        out.set(area.x + (area.w - w) / 2f, area.y + (area.h - h) / 2f, w, h)
    }

    // ------------------------------------------------------------------------------- input

    /** Takes every sample while open. */
    fun onSample(s: PenSample, x: Float, y: Float) {
        when (s.phase) {
            PenPhase.DOWN -> {
                if (!card.contains(x, y)) {
                    hide()
                    return
                }
                press.down(s.pointerId, targetAt(x, y))
            }
            PenPhase.UP -> {
                val hit = press.up(s.pointerId, targetAt(x, y)) ?: return
                onHit(hit)
            }
            PenPhase.CANCEL -> press.cancel()
            else -> Unit
        }
    }

    private fun onHit(hit: Any) {
        onTap()
        when {
            hit === done -> hide()
            hit === credits -> {
                hide()
                onCredits()
            }
            hit is Tab -> select(hit)
            hit is Sticker -> picked = if (picked === hit) null else hit
            hit === OWN -> {
                settings.jukebox = ""
                onChanged()
            }
            hit is Jukebox.Track -> {
                settings.jukebox = hit.name
                onChanged()
            }
            hit is Doodle -> {
                settings.doodle = settings.doodle xor hit.bit
                onChanged()
            }
            hit === prev -> pageFirst = max(0, pageFirst - perPage)
            hit === nextPage -> if (pageFirst + perPage < pages.size) pageFirst += perPage
            hit is PageHit -> openViewer(pages[hit.index])
            hit === back -> closeViewer()
            hit === share -> viewing?.let(onShare)
            hit === delete -> viewing?.let { f ->
                closeViewer()
                album.delete(f)
                thumbs.remove(f.name())?.dispose()
                pages = album.list()
            }
        }
    }

    private class PageHit(val index: Int) {
        override fun equals(other: Any?) = other is PageHit && other.index == index
        override fun hashCode() = index
    }

    private fun targetAt(x: Float, y: Float): Any? {
        if (done.contains(x, y)) return done
        if (credits.contains(x, y)) return credits
        tabBoxes.forEachIndexed { i, b -> if (b.contains(x, y)) return Tab.entries[i] }
        when (tab) {
            Tab.STICKERS -> Sticker.entries.forEachIndexed { i, s -> if (stickerBoxes[i].contains(x, y)) return s }
            Tab.MUSIC -> musicRows.forEachIndexed { i, r ->
                if (r.box.contains(x, y)) return if (i == 0 || unlocked[i - 1]) r.target else null
            }
            Tab.DOODLE -> if (doodleOpen) doodleRows.forEach { r -> if (r.box.contains(x, y)) return r.target }
            Tab.PAGES -> {
                if (viewing != null) {
                    if (back.contains(x, y)) return back
                    if (share.contains(x, y)) return share
                    if (delete.contains(x, y)) return delete
                    return null
                }
                pageBoxes.forEachIndexed { i, b -> if (b.contains(x, y)) return PageHit(pageFirst + i) }
                if (pageFirst > 0 && prev.contains(x, y)) return prev
                if (pageFirst + perPage < pages.size && nextPage.contains(x, y)) return nextPage
            }
            Tab.STATS -> Unit
        }
        return null
    }

    // ------------------------------------------------------------------------------- pictures

    private fun thumbFor(f: FileHandle): FileHandle = album.thumb(f)

    private fun loadThumbs() {
        val want = HashSet<String>()
        for (i in pageFirst until min(pages.size, pageFirst + perPage)) {
            val f = pages[i]
            want += f.name()
            if (f.name() in thumbs) continue
            val t = thumbFor(f)
            val src = if (t.exists()) t else f
            runCatching { Texture(src).also { it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) } }
                .getOrNull()?.let { thumbs[f.name()] = it }
        }
        val it = thumbs.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key !in want) {
                e.value.dispose()
                it.remove()
            }
        }
    }

    private fun dropThumbs() {
        thumbs.values.forEach { it.dispose() }
        thumbs.clear()
    }

    private fun openViewer(f: FileHandle) {
        closeViewer()
        viewing = f
        viewTex = runCatching { Texture(f).also { it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) } }.getOrNull()
    }

    private fun closeViewer() {
        viewTex?.dispose()
        viewTex = null
        viewing = null
    }

    fun dispose() {
        closeViewer()
        dropThumbs()
    }

    // ------------------------------------------------------------------------------- drawing

    fun drawShapes(shapes: Shapes, pal: Palette, f: Frame) {
        shapes.color(pal[Tok.PAPER], 0.55f).rect(0f, 0f, f.wDp, f.hDp)
        Widgets.card(shapes, pal, card)
        Tab.entries.forEachIndexed { i, t ->
            Widgets.button(shapes, pal, tabBoxes[i], primary = t == tab, enabled = t != Tab.DOODLE || doodleOpen || t == tab, pressed = press.isPressed(t))
        }
        Widgets.button(shapes, pal, credits, primary = false, enabled = true, pressed = press.isPressed(credits))
        Widgets.button(shapes, pal, done, primary = true, enabled = true, pressed = press.isPressed(done))
        when (tab) {
            Tab.STATS -> stats.indices.forEach { i ->
                val b = statBoxes[i]
                shapes.color(pal[Tok.LINE]).rect(b.x, b.bottom - 1f, b.w, 1f)
            }
            Tab.STICKERS -> drawStickerShapes(shapes, pal)
            Tab.MUSIC -> musicRows.forEachIndexed { i, r ->
                val on = i == 0 || unlocked[i - 1]
                shapes.color(pal[Tok.LINE]).rect(r.box.x, r.box.bottom - 1f, r.box.w, 1f)
                val cx = r.box.x + 12f
                val cy = r.box.y + r.box.h / 2f
                val chosen = if (i == 0) settings.jukebox.isEmpty() else settings.jukebox == (r.target as Jukebox.Track).name
                shapes.color(if (on) pal[Tok.ACCENT] else pal[Tok.MUTED], if (on) 1f else 0.5f).ring(cx, cy, 9f, 2f)
                if (chosen) shapes.color(pal[Tok.ACCENT]).circle(cx, cy, 5f)
                if (press.isPressed(r.target)) shapes.color(pal[Tok.INK], 0.06f).roundRect(r.box.x, r.box.y + 2f, r.box.w, r.box.h - 4f, 8f)
            }
            Tab.DOODLE -> if (doodleOpen) {
                doodleRows.forEach { r ->
                    shapes.color(pal[Tok.LINE]).rect(r.box.x, r.box.bottom - 1f, r.box.w, 1f)
                    Widgets.checkbox(shapes, pal, r.box.x + 2f, r.box.y + (r.box.h - 20f) / 2f, (r.target as Doodle).on(settings.doodle))
                }
            } else {
                bigLock(shapes, pal, content.x + content.w / 2f, content.y + content.h * 0.36f)
            }
            Tab.PAGES -> drawPageShapes(shapes, pal)
        }
    }

    private fun drawStickerShapes(shapes: Shapes, pal: Palette) {
        Sticker.entries.forEachIndexed { i, s ->
            val b = stickerBoxes[i]
            val cx = b.x + b.w / 2f
            val cy = b.y + stickerR + 4f
            if (s.on(earned)) {
                val c = pal[STICKER_TOKS[i % STICKER_TOKS.size]]
                shapes.color(pal[Tok.SHADOW], 0.25f).circle(cx + 1.5f, cy + 2.5f, stickerR)
                shapes.color(pal[Tok.PAPER_WHITE]).circle(cx, cy, stickerR + 2.5f)
                shapes.color(c).circle(cx, cy, stickerR)
                shapes.color(pal[Tok.PAPER_WHITE]).star(cx, cy, stickerR * 0.55f)
            } else {
                shapes.color(pal[Tok.MUTED], 0.7f).ring(cx, cy, stickerR, 1.6f, 4f, 3f)
            }
            if (picked === s) shapes.color(pal[Tok.ACCENT]).ring(cx, cy, stickerR + 6f, 2f)
        }
    }

    private fun drawPageShapes(shapes: Shapes, pal: Palette) {
        if (viewing != null) {
            shapes.color(pal[Tok.LINE]).rect(viewBox.x - 1f, viewBox.y - 1f, viewBox.w + 2f, viewBox.h + 2f)
            Widgets.button(shapes, pal, back, primary = false, enabled = true, pressed = press.isPressed(back))
            Widgets.button(shapes, pal, delete, primary = false, enabled = true, pressed = press.isPressed(delete))
            Widgets.button(shapes, pal, share, primary = true, enabled = true, pressed = press.isPressed(share))
            return
        }
        pageBoxes.forEach { b -> shapes.color(pal[Tok.LINE]).rect(b.x - 1f, b.y - 1f, b.w + 2f, b.h + 2f) }
        if (pages.size > perPage) {
            Widgets.button(shapes, pal, prev, primary = false, enabled = pageFirst > 0, pressed = press.isPressed(prev))
            Widgets.button(shapes, pal, nextPage, primary = false, enabled = pageFirst + perPage < pages.size, pressed = press.isPressed(nextPage))
        }
    }

    private fun bigLock(shapes: Shapes, pal: Palette, cx: Float, cy: Float) {
        val c = pal[Tok.MUTED]
        shapes.color(c).arc(cx, cy - 8f, 13f, 4f, kotlin.math.PI.toFloat(), 2f * kotlin.math.PI.toFloat())
        shapes.color(c).roundRect(cx - 20f, cy - 8f, 40f, 30f, 6f)
        shapes.color(pal[Tok.UI]).circle(cx, cy + 5f, 4f)
    }

    /** The pictures (thumbnails, or the one being looked at): drawn in the text pass, before the words. */
    private fun drawPictures(text: Text) {
        if (tab != Tab.PAGES) return
        val b = text.batch
        b.setColor(1f, 1f, 1f, 1f)
        val v = viewTex
        if (viewing != null) {
            if (v != null) b.draw(v, viewBox.x, viewBox.y, viewBox.w, viewBox.h, 0, 0, v.width, v.height, false, true)
            return
        }
        pageBoxes.forEachIndexed { i, box ->
            val t = thumbs[pages[pageFirst + i].name()] ?: return@forEachIndexed
            // the middle of the picture at the cell's shape
            val want = box.w / box.h
            val tw = t.width.toFloat()
            val th = t.height.toFloat()
            val sw: Float
            val sh: Float
            if (tw / th > want) { sh = th; sw = th * want } else { sw = tw; sh = tw / want }
            b.draw(t, box.x, box.y, box.w, box.h, ((tw - sw) / 2f).toInt(), ((th - sh) / 2f).toInt(), sw.toInt(), sh.toInt(), false, true)
        }
    }

    fun drawText(text: Text, pal: Palette) {
        drawPictures(text)
        text.line(fonts.get(Face.HAND_BOLD, 30f), "Notebook", card.x + 20f, card.y + 20f, 34f, pal[Tok.INK])
        val tabFont = fonts.get(Face.UI_BOLD, 13f)
        Tab.entries.forEachIndexed { i, t ->
            Widgets.buttonLabel(text, tabFont, pal, tabBoxes[i], t.label, primary = t == tab, enabled = t != Tab.DOODLE || doodleOpen || t == tab)
        }
        val font = fonts.get(Face.UI_BOLD, 14f)
        Widgets.buttonLabel(text, font, pal, credits, "Credits", primary = false, enabled = true)
        Widgets.buttonLabel(text, font, pal, done, "Done", primary = true, enabled = true)
        when (tab) {
            Tab.STATS -> {
                val label = fonts.get(Face.UI, 14f)
                val value = fonts.get(Face.MONO_BOLD, 14f)
                stats.forEachIndexed { i, (k, v) ->
                    val b = statBoxes[i]
                    text.line(label, k, b.x, b.y, b.h, pal[Tok.UI_FG])
                    text.line(value, v, b.right, b.y, b.h, pal[Tok.INK], 1f, Align.right)
                }
            }
            Tab.STICKERS -> drawStickerText(text, pal)
            Tab.MUSIC -> drawMusicText(text, pal)
            Tab.DOODLE -> drawDoodleText(text, pal)
            Tab.PAGES -> drawPageText(text, pal)
        }
    }

    private fun drawStickerText(text: Text, pal: Palette) {
        val small = fonts.get(Face.UI, 11f)
        Sticker.entries.forEachIndexed { i, s ->
            val b = stickerBoxes[i]
            val on = s.on(earned)
            text.line(small, if (on) s.title else "?", b.x + b.w / 2f, b.y + stickerR * 2f + 8f, 14f, if (on) pal[Tok.UI_FG] else pal[Tok.MUTED], 1f, Align.center)
        }
        val p = picked
        val cap = when {
            p == null -> "${Sticker.entries.count { it.on(earned) }} of ${Sticker.entries.size} stuck in. Tap one to see how it's earned."
            p.on(earned) -> "${p.title}: ${p.how}. Got it!"
            else -> "Not yet: ${p.how}."
        }
        text.draw(fonts.get(Face.UI, 13f), cap, content.x, content.bottom - CAPTION_H + 6f, pal[Tok.MUTED], 1f, Align.left, content.w)
    }

    private fun drawMusicText(text: Text, pal: Palette) {
        val title = fonts.get(Face.UI, 15f)
        val sub = fonts.get(Face.MONO_MEDIUM, 10.5f)
        musicRows.forEachIndexed { i, r ->
            val on = i == 0 || unlocked[i - 1]
            val x = r.box.x + 32f
            val ty = r.box.y + (r.box.h - 33f) / 2f
            if (i == 0) {
                text.line(title, "Each world's own", x, ty, 19f, pal[Tok.UI_FG])
                text.line(sub, "THE GAME AS IT COMES", x, ty + 19f, 14f, pal[Tok.MUTED])
            } else {
                val t = r.target as Jukebox.Track
                text.line(title, t.title, x, ty, 19f, if (on) pal[Tok.UI_FG] else pal[Tok.MUTED])
                val s = if (!on) Jukebox.lockedHint(t).uppercase() else if (t.world == 0) "EVERY BOSS" else "WORLD ${t.world}"
                text.line(sub, s, x, ty + 19f, 14f, pal[Tok.MUTED])
            }
        }
    }

    private fun drawDoodleText(text: Text, pal: Palette) {
        if (!doodleOpen) {
            text.draw(
                fonts.get(Face.UI, 14f), "Reach the ending to open Doodle mode: switches that bend the rules, just for fun.",
                content.x, content.y + content.h * 0.36f + 44f, pal[Tok.MUTED], 1f, Align.center, content.w,
            )
            return
        }
        val title = fonts.get(Face.UI, 15f)
        val detail = fonts.get(Face.UI, 12.5f)
        doodleRows.forEach { r ->
            val d = r.target as Doodle
            val x = r.box.x + 34f
            val ty = r.box.y + (r.box.h - 35f) / 2f
            text.line(title, d.title, x, ty, 19f, pal[Tok.UI_FG])
            text.line(detail, d.detail, x, ty + 19f, 16f, pal[Tok.MUTED])
        }
        val note = if (settings.doodle != 0) "Doodling: stars, bests and stickers aren't kept while any switch is on." else "Turn any on and the stages play by these rules."
        text.draw(fonts.get(Face.UI, 12.5f), note, content.x, content.bottom - NOTE_H + 8f, if (settings.doodle != 0) pal[Tok.BEAM] else pal[Tok.MUTED], 1f, Align.left, content.w)
    }

    private fun drawPageText(text: Text, pal: Palette) {
        val font = fonts.get(Face.UI_BOLD, 14f)
        if (viewing != null) {
            Widgets.buttonLabel(text, font, pal, back, "Back", primary = false, enabled = true)
            Widgets.buttonLabel(text, font, pal, delete, "Delete", primary = false, enabled = true)
            Widgets.buttonLabel(text, font, pal, share, "Share", primary = true, enabled = true)
            return
        }
        if (pages.isEmpty()) {
            text.draw(
                fonts.get(Face.UI, 14f), "Nothing here yet. Pause a stage and tear out the page, or share a stage you've cleared: they're kept here.",
                content.x, content.y + 30f, pal[Tok.MUTED], 1f, Align.center, content.w,
            )
            return
        }
        if (pages.size > perPage) {
            Widgets.buttonLabel(text, font, pal, prev, "Newer", primary = false, enabled = pageFirst > 0)
            Widgets.buttonLabel(text, font, pal, nextPage, "Older", primary = false, enabled = pageFirst + perPage < pages.size)
        }
        text.line(fonts.get(Face.MONO_MEDIUM, 11f), pageRange, content.x + content.w / 2f, prev.y, prev.h, pal[Tok.MUTED], 1f, Align.center)
    }

    companion object {
        private val OWN = Any()
        private const val TAB_H = 34f
        private const val ROW_H = 46f
        private const val STAT_H = 30f
        private const val COL_GAP = 20f
        private const val CAPTION_H = 40f
        private const val NOTE_H = 40f
        private val STICKER_TOKS = StickerPop.STICKER_TOKS
    }
}
