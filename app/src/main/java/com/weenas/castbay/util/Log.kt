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
 * Drop-in for [android.util.Log] that also appends to `logs/castbay.log`, the native and
 * protocol code to `logs/protocol.log` beside it. Each starts afresh past a size, keeping the
 * one before as ".old", so the newest lines are always on disk; a new process keeps the
 * previous one's as ".1" ([previousRun]), which is how a problem report shows what happened
 * before a crash that took the process, and its memory, with it.
 *
 * Release builds keep them small, in internal storage, where only CastBay reads them (they leave
 * the device only in a problem report, scrubbed like the rest). Debug builds keep more, on
 * external storage when they can: some TVs (e.g. TCL) silence app logs in logd, so fetch them with
 * `adb pull /sdcard/Android/data/com.weenas.castbay.debug/files/logs/`.
 */
object Log {
    private const val APP_FILE = "castbay.log"
    private const val PROTOCOL_FILE = "protocol.log"
    private val maxFileBytes = if (BuildConfig.DEBUG) 10L * 1024 * 1024 else 128L * 1024

    @Volatile private var file: File? = null
    private var output: FileOutputStream? = null
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    /** Starts the log files, once per process, keeping the previous process's as ".1". */
    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        // Some old TVs (Android 6) can't create the external directory: then internal storage.
        val roots = if (BuildConfig.DEBUG) listOfNotNull(context.getExternalFilesDir(null), context.filesDir) else listOf(context.filesDir)
        val current = roots.map { File(it, "logs") }.firstNotNullOfOrNull { dir ->
            dir.mkdirs()
            keepAsPrevious(dir, APP_FILE)
            keepAsPrevious(dir, PROTOCOL_FILE)
            val log = File(dir, APP_FILE)
            runCatching { output = FileOutputStream(log, true) }.map { log }.getOrNull()
        } ?: return
        file = current
        try {
            AirPlayNative.setLogFile(File(current.parentFile, PROTOCOL_FILE).absolutePath, maxFileBytes)
        } catch (error: UnsatisfiedLinkError) {
            // No native library: Kotlin logs still reach the file.
        }
        if (BuildConfig.DEBUG) androidx.media3.common.util.Log.setLogger(Media3Logger)
        i("CastBay", "Logging to ${current.parentFile?.absolutePath}")
    }

    private fun keepAsPrevious(dir: File, name: String) {
        for (suffix in listOf("", ".old")) {
            val previous = File(dir, "$name.1$suffix")
            previous.delete()
            File(dir, name + suffix).renameTo(previous)
        }
    }

    /**
     * The previous process's last lines, its own and the protocol code's merged in time order,
     * oldest first: empty if there are none.
     */
    fun previousRun(maxLines: Int): List<String> {
        val dir = file?.parentFile ?: return emptyList()
        fun read(name: String) = listOf("$name.1.old", "$name.1").flatMap { part ->
            runCatching { File(dir, part).takeIf { it.exists() }?.readLines() }.getOrNull().orEmpty()
        }.takeLast(maxLines)
        return mergeByTime(read(APP_FILE), read(PROTOCOL_FILE), maxLines)
    }

    /** Two logs' lines ("MM-dd HH:mm:ss.SSS …") in time order, the last [maxLines]; each keeps its own order. */
    internal fun mergeByTime(first: List<String>, second: List<String>, maxLines: Int): List<String> =
        (first + second).sortedBy { it.take(18) }.takeLast(maxLines)

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
        val stream = startAfreshIfFull(target) ?: return
        val letter = "??VDIWEA".getOrElse(priority) { '?' }
        val prefix = "%s %5d %5d %c %s: ".format(
            Locale.US, timeFormat.format(Date()), android.os.Process.myPid(), android.os.Process.myTid(), letter, tag
        )
        val text = message.lines().joinToString("") { prefix + it + "\n" }
        try {
            stream.write(text.toByteArray())
        } catch (error: Exception) {
            AndroidLog.w("CastBay", "Log file write failed", error)
        }
    }

    /** Past [maxFileBytes], moves the file to ".old" and starts a new one. */
    private fun startAfreshIfFull(target: File): FileOutputStream? {
        if (target.length() <= maxFileBytes) return output
        runCatching { output?.close() }
        val old = File(target.path + ".old")
        old.delete()
        target.renameTo(old)
        output = runCatching { FileOutputStream(target, true) }.getOrNull()
        return output
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
