package com.weenas.castbay.util

import android.content.Context
import com.weenas.castbay.BuildConfig
import com.weenas.castbay.protocol.AirPlayNative
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log as AndroidLog

/**
 * Drop-in for [android.util.Log] that, in debug builds, also appends to
 * `<external files dir>/logs/castbay.log` together with native and protocol logs.
 *
 * Some TVs (e.g. TCL) silence app logs in logd, so logcat shows nothing. Fetch the file with
 * `adb pull /sdcard/Android/data/com.weenas.castbay/files/logs/castbay.log`.
 */
object Log {
    /** Stop appending past this size rather than filling the TV's storage (as native does). */
    private const val MAX_FILE_BYTES = 20L * 1024 * 1024

    @Volatile private var file: File? = null
    private var output: FileOutputStream? = null
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    /** Starts the log file (debug builds only); the previous run's file is kept as ".1". */
    @Synchronized
    fun init(context: Context) {
        if (!BuildConfig.DEBUG || file != null) return
        // Some old TVs (Android 6) can't create the external directory: then internal storage.
        val dirs = listOfNotNull(context.getExternalFilesDir(null), context.filesDir).map { File(it, "logs") }
        val current = dirs.firstNotNullOfOrNull { dir ->
            dir.mkdirs()
            val log = File(dir, "castbay.log")
            if (log.exists()) log.renameTo(File(dir, "castbay.log.1"))
            runCatching { output = FileOutputStream(log, true) }.map { log }.getOrNull()
        } ?: return
        file = current
        try {
            AirPlayNative.setLogFile(current.absolutePath)
        } catch (error: UnsatisfiedLinkError) {
            // No native library: Kotlin logs still reach the file.
        }
        androidx.media3.common.util.Log.setLogger(Media3Logger)
        i("CastBay", "Logging to ${current.absolutePath}")
    }

    fun d(tag: String, message: String) = log(AndroidLog.DEBUG, tag, message, null)
    fun d(tag: String, message: String, error: Throwable?) = log(AndroidLog.DEBUG, tag, message, error)
    fun i(tag: String, message: String) = log(AndroidLog.INFO, tag, message, null)
    fun i(tag: String, message: String, error: Throwable?) = log(AndroidLog.INFO, tag, message, error)
    fun w(tag: String, message: String) = log(AndroidLog.WARN, tag, message, null)
    fun w(tag: String, message: String, error: Throwable?) = log(AndroidLog.WARN, tag, message, error)
    fun e(tag: String, message: String) = log(AndroidLog.ERROR, tag, message, null)
    fun e(tag: String, message: String, error: Throwable?) = log(AndroidLog.ERROR, tag, message, error)

    /**
     * Marks [value] (a media title, link or someone's device name) as personal: logs show it
     * as is, and a problem report the person uploads ([LogReport]) leaves it out.
     */
    fun personal(value: Any?): String = "$PERSONAL_START$value$PERSONAL_END"

    const val PERSONAL_START = '⟦'
    const val PERSONAL_END = '⟧'

    /**
     * The last lines logged (all builds), for a problem report: some TVs silence app logs in
     * logd, so logcat alone may have nothing.
     */
    @Synchronized
    fun recent(): List<String> = recent.toList()

    private const val RECENT_LINES = 1500
    private val recent = ArrayDeque<String>()

    private fun log(priority: Int, tag: String, message: String, error: Throwable?): Int {
        val full = if (error == null) message else message + "\n" + stackTrace(error)
        val result = AndroidLog.println(priority, tag, full)
        remember(priority, tag, full)
        if (file != null) append(priority, tag, full)
        return result
    }

    @Synchronized
    private fun remember(priority: Int, tag: String, message: String) {
        val prefix = "%s %c %s: ".format(Locale.US, timeFormat.format(Date()), "??VDIWEA".getOrElse(priority) { '?' }, tag)
        message.lines().forEach { recent.addLast(prefix + it) }
        while (recent.size > RECENT_LINES) recent.removeFirst()
    }

    @Synchronized
    private fun append(priority: Int, tag: String, message: String) {
        val target = file ?: return
        val stream = output ?: return
        if (target.length() > MAX_FILE_BYTES) return
        val letter = "??VDIWEA".getOrElse(priority) { '?' }
        val prefix = "%s %5d %5d %c %s: ".format(
            Locale.US, timeFormat.format(Date()), android.os.Process.myPid(), android.os.Process.myTid(), letter, tag
        )
        val text = message.lines().joinToString("") { prefix + it + "\n" }
        try {
            // One append-mode write per entry keeps lines whole next to the native writer.
            stream.write(text.toByteArray())
        } catch (error: Exception) {
            AndroidLog.w("CastBay", "Log file write failed", error)
        }
    }

    private fun stackTrace(error: Throwable): String =
        StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().trimEnd()

    /** Routes Media3 (ExoPlayer, EventLogger) logs into the same file. */
    private object Media3Logger : androidx.media3.common.util.Log.Logger {
        override fun d(tag: String, message: String, throwable: Throwable?) { Log.d(tag, message, throwable) }
        override fun i(tag: String, message: String, throwable: Throwable?) { Log.i(tag, message, throwable) }
        override fun w(tag: String, message: String, throwable: Throwable?) { Log.w(tag, message, throwable) }
        override fun e(tag: String, message: String, throwable: Throwable?) { Log.e(tag, message, throwable) }
    }
}
