package com.pranshuparmar.inkmayhem.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.ScreenUtils

/**
 * The album: pages torn out of stages and result cards, kept as PNGs in the app's own storage
 * ([dir]), newest first, at most [MAX] of them. The Notebook shows them; each can be shared
 * (which also saves a copy where the player's gallery sees it) or deleted.
 */
class PageAlbum(private val dir: () -> FileHandle?) {
    /** Every picture, newest first. */
    fun list(): List<FileHandle> {
        val d = dir() ?: return emptyList()
        if (!d.exists()) return emptyList()
        return d.list { f -> f.name.startsWith(PREFIX) && f.name.endsWith(".png") }
            .sortedByDescending { stamp(it.name()) }
    }

    /**
     * Writes [pixmap] as a new picture ([kind] goes in its name), at most [MAX_SIDE] pixels on its
     * long side, with a small copy for the Notebook's grid, and drops the oldest past [MAX].
     * Plain CPU work: fine off the GL thread. [pixmap] is left for the caller to dispose.
     */
    fun add(pixmap: Pixmap, kind: String, nowMs: Long): FileHandle? {
        val d = dir() ?: return null
        d.mkdirs()
        val f = d.child(name(kind, nowMs))
        val big = Grab.scaled(pixmap, MAX_SIDE)
        try {
            PixmapIO.writePNG(f, big ?: pixmap)
        } finally {
            big?.dispose()
        }
        val small = Grab.scaled(pixmap, THUMB_SIDE)
        try {
            PixmapIO.writePNG(thumb(f), small ?: pixmap)
        } finally {
            small?.dispose()
        }
        prune()
        return f
    }

    /** The small copy of [f] for the grid. */
    fun thumb(f: FileHandle): FileHandle = f.parent().child(THUMBS).child(f.name())

    /** Keeps the newest [MAX]. */
    fun prune() {
        list().drop(MAX).forEach { delete(it) }
    }

    fun delete(f: FileHandle) {
        if (!f.name().startsWith(PREFIX)) return
        thumb(f).let { if (it.exists()) it.delete() }
        f.delete()
    }

    companion object {
        const val MAX = 24
        const val MAX_SIDE = 1600
        const val THUMB_SIDE = 360
        private const val THUMBS = "thumbs"
        const val PREFIX = "ink-mayhem-"

        /** "ink-mayhem-page-1700000000000.png"; [kind] is reduced to letters, digits and dashes. */
        fun name(kind: String, nowMs: Long): String =
            PREFIX + kind.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("") + "-" + nowMs + ".png"

        /** The time in a picture's name, or 0. */
        fun stamp(name: String): Long = name.removeSuffix(".png").substringAfterLast('-').toLongOrNull() ?: 0L

        /** The album in the app's own storage. */
        fun local(): PageAlbum = PageAlbum { runCatching { Gdx.files.local("pages") }.getOrNull() }
    }
}

/** Reading what's been drawn back as a picture. */
object Grab {
    /** A copy of [p] at most [maxSide] pixels on its long side, or null when it already fits. */
    fun scaled(p: Pixmap, maxSide: Int): Pixmap? {
        val s = maxSide.toFloat() / maxOf(p.width, p.height)
        if (s >= 1f) return null
        val w = maxOf(1, (p.width * s).toInt())
        val h = maxOf(1, (p.height * s).toInt())
        val q = Pixmap(w, h, Pixmap.Format.RGBA8888)
        q.filter = Pixmap.Filter.BiLinear
        q.drawPixmap(p, 0, 0, p.width, p.height, 0, 0, w, h)
        return q
    }

    /**
     * The pixels of the bound framebuffer's ([x], [y], [w], [h]) in pixels, upright, made
     * opaque (the screen's alpha isn't meant to be seen).
     */
    fun pixmap(x: Int, y: Int, w: Int, h: Int): Pixmap {
        val bytes = ScreenUtils.getFrameBufferPixels(x, y, w, h, true)
        for (i in 3 until bytes.size step 4) bytes[i] = -1
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        BufferUtils.copy(bytes, 0, p.pixels, bytes.size)
        return p
    }
}
