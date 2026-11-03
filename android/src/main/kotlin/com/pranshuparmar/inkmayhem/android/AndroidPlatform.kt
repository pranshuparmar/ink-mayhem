package com.pranshuparmar.inkmayhem.android

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.widget.Toast
import com.pranshuparmar.inkmayhem.Platform
import com.pranshuparmar.inkmayhem.input.PenQueue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidPlatform(private val activity: AndroidLauncher, override val penQueue: PenQueue) : Platform {

    override val isDebugBuild: Boolean = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override val countsPlays: Boolean get() = !isDebugBuild

    override val density: Float get() = activity.resources.displayMetrics.density

    override val description: String = "Android ${Build.VERSION.RELEASE} · ${Build.MODEL}"

    @Volatile private var dark = isNight(activity.resources.configuration)
    @Volatile private var reducedMotion = !ValueAnimator.areAnimatorsEnabled()

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            activity.getSystemService(Vibrator::class.java)
        }

    override val appVersion: String = runCatching {
        @Suppress("DEPRECATION")
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrDefault("")

    override fun isDarkMode(): Boolean = dark

    override fun isReducedMotion(): Boolean = reducedMotion

    override fun vibrate(millis: Int, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(millis.toLong(), amplitude.coerceIn(1, 255)))
    }

    override fun vibratePattern(timings: LongArray, amplitudes: IntArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator() || timings.size != amplitudes.size || timings.isEmpty()) return
        val amps = IntArray(amplitudes.size) { amplitudes[it].coerceIn(0, 255) }
        runCatching { v.vibrate(VibrationEffect.createWaveform(timings, amps, -1)) }
    }

    /** Copies the picture into Pictures/Ink Mayhem and opens the share sheet (Android 10 and later). */
    override fun shareImage(file: File, title: String) {
        Thread({
            val saved = runCatching { saveToPictures(file) }.getOrNull()
            val uri = saved?.uri
            activity.runOnUiThread {
                when {
                    saved == null -> Toast.makeText(activity, "Couldn't save the picture", Toast.LENGTH_LONG).show()
                    uri == null -> Toast.makeText(activity, "Saved to ${saved.where}", Toast.LENGTH_LONG).show()
                    else -> {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, title)
                            clipData = ClipData.newRawUri(file.name, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { activity.startActivity(Intent.createChooser(send, title)) }
                    }
                }
            }
        }, "share-image").start()
    }

    private fun saveToPictures(file: File): Saved? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = activity.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Ink Mayhem")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            val out = resolver.openOutputStream(uri) ?: return null
            out.use { o -> file.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return Saved(uri, "Pictures/Ink Mayhem/${file.name}")
        }
        // Android 9: the app's own pictures folder, reachable over USB
        val dir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
        val copy = File(dir, file.name)
        file.copyTo(copy, overwrite = true)
        return Saved(null, copy.absolutePath)
    }

    fun onConfigurationChanged(config: Configuration) {
        dark = isNight(config)
    }

    fun onResume() {
        reducedMotion = !ValueAnimator.areAnimatorsEnabled()
    }

    private fun isNight(config: Configuration): Boolean =
        (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // ------------------------------------------------------------------ input recordings

    /** Debug builds record every launch into the app's own storage, keeping the last few. */
    override fun newRecording(): File? {
        if (!isDebugBuild) return null
        val dir = File(activity.filesDir, "recordings")
        if (!dir.isDirectory && !dir.mkdirs()) return null
        // the session before a crash is often the interesting one, so keep a few
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(KEEP_RECORDINGS - 1)?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(dir, "ink-mayhem-$stamp.txt")
    }

    /** Copies the recording into Downloads and opens the share sheet (Android 10 and later). */
    override fun shareRecording(file: File) {
        Thread({
            val saved = runCatching { saveToDownloads(file) }.getOrNull()
            val uri = saved?.uri
            activity.runOnUiThread {
                when {
                    saved == null -> Toast.makeText(activity, "Couldn't save the recording", Toast.LENGTH_LONG).show()
                    uri == null -> Toast.makeText(activity, "Saved to ${saved.where}", Toast.LENGTH_LONG).show()
                    else -> {
                        Toast.makeText(activity, "Saved to ${saved.where}", Toast.LENGTH_LONG).show()
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Ink Mayhem input recording")
                            clipData = ClipData.newRawUri(file.name, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { activity.startActivity(Intent.createChooser(send, "Share input recording")) }
                    }
                }
            }
        }, "share-recording").start()
    }

    private class Saved(val uri: Uri?, val where: String)

    private fun saveToDownloads(file: File): Saved? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = activity.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            val out = resolver.openOutputStream(uri) ?: return null
            out.use { o -> file.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return Saved(uri, "Downloads/${file.name}")
        }
        // Android 9: no shared Downloads without a storage permission; the app's own folder
        // can be reached over USB
        val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val copy = File(dir, file.name)
        file.copyTo(copy, overwrite = true)
        return Saved(null, copy.absolutePath)
    }

    private companion object {
        const val KEEP_RECORDINGS = 5
    }
}
