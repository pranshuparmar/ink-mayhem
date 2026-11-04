package com.pranshuparmar.inkmayhem

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Net

/**
 * Anonymous play counts, so it's known whether anyone plays and how far they get. Each count is
 * one GET to GoatCounter (goatcounter.com) with an event name such as `stage-1-1-clear`, and
 * nothing else: no identifier, no device details, and a User-Agent that names only the game's
 * version. Nothing is stored on the device.
 *
 * Release builds only, and only while "Anonymous play counts" is on ([enabled] says both). Debug
 * builds, the desktop launcher, replays and tests never send. Each event counts at most once a
 * visit; a visit starts at launch, or on coming back after [NEW_VISIT_MS] away.
 *
 * Fire and forget: one try, a short timeout, no queue and no retries, so offline play just isn't
 * counted. Counting never throws into the game, and it touches nothing the simulation or a
 * replay reads.
 */
class PlayCounts(
    version: String,
    private val enabled: () -> Boolean,
    private val send: (url: String, userAgent: String) -> Unit = ::sendWithGdx,
) {
    val version: String = cleanVersion(version)
    val userAgent: String = "InkMayhem/${this.version} (Android)"

    private val seen = HashSet<String>()
    private var awaySince = -1L

    /** Counts [name] once this visit. Never throws. */
    fun track(name: String) {
        try {
            if (!enabled()) return
            val path = clean(name) ?: return
            if (!seen.add(path)) return
            send(url(path), userAgent)
        } catch (e: Exception) {
            // counting must never surface an error into the game
        }
    }

    /** The app went to the background at [nowMs]. */
    fun away(nowMs: Long) {
        awaySince = nowMs
    }

    /** The app came back at [nowMs]: a long absence starts a new visit, counted as an open. */
    fun back(nowMs: Long) {
        val since = awaySince
        awaySince = -1L
        if (since < 0L || nowMs - since < NEW_VISIT_MS) return
        seen.clear()
        opened()
    }

    /** A visit started: the app open, and which version it is. */
    fun opened() {
        track("app-open")
        track("version-$version")
    }

    companion object {
        const val ENDPOINT = "https://ink-mayhem.goatcounter.com/count"
        const val NEW_VISIT_MS = 30L * 60L * 1000L
        const val TIMEOUT_MS = 5000
        private const val MAX_PATH = 80

        /** The count URL for an event path: `p` is the path, `e` marks it as an event. */
        fun url(path: String): String = "$ENDPOINT?p=${java.net.URLEncoder.encode(path, "UTF-8")}&e=true"

        /**
         * An event name as a path: lower case letters, digits, dots and dashes; anything else
         * becomes a dash. Null if nothing is left.
         */
        fun clean(name: String): String? {
            val s = buildString {
                for (c in name.lowercase()) append(if (c in 'a'..'z' || c in '0'..'9' || c == '.' || c == '-') c else '-')
            }.trim('-').take(MAX_PATH)
            return s.ifEmpty { null }
        }

        /** "1.0.1 (3)" → "1.0.1". */
        fun cleanVersion(v: String): String =
            v.substringBefore(' ').filter { it.isLetterOrDigit() || it == '.' || it == '-' }.ifEmpty { "unknown" }

        private fun sendWithGdx(url: String, userAgent: String) {
            val net = Gdx.net ?: return
            val request = Net.HttpRequest(Net.HttpMethods.GET).apply {
                this.url = url
                timeOut = TIMEOUT_MS
                setHeader("User-Agent", userAgent)
            }
            net.sendHttpRequest(request, object : Net.HttpResponseListener {
                override fun handleHttpResponse(httpResponse: Net.HttpResponse) = Unit
                override fun failed(t: Throwable) = Unit
                override fun cancelled() = Unit
            })
        }
    }
}
