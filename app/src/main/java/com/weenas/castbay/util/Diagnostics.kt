package com.weenas.castbay.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A short history of what happened to the app, kept on the device and shown on the
 * Diagnostics screen (About → Diagnostics), for devices without adb such as car head units:
 * when the process started (and whether the device had just booted), boot broadcasts, the
 * receiver service starting and stopping, keys and media-session commands, taps, and audio
 * focus. Nothing leaves the device; the person reads it on screen (or photographs it).
 */
object Diagnostics {
    data class Event(val atMs: Long, val kind: String, val text: String)

    private const val PREFS = "diagnostics"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 120
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    @Volatile private var appContext: Context? = null
    private val events = ArrayDeque<Event>()
    private var loaded = false

    /** Remembers the context (any) and notes this process's start, with how long the device has been up. */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val uptimeMin = SystemClock.elapsedRealtime() / 60_000
        record("process", "Started (pid ${Process.myPid()}), device up $uptimeMin min, boot #${bootCount() ?: "?"}")
    }

    /** Adds an event; [kind] groups them ("boot", "service", "key", "touch", "focus", …). Any thread. */
    @Synchronized
    fun record(kind: String, text: String) {
        loadLocked()
        events.addLast(Event(System.currentTimeMillis(), kind, text))
        while (events.size > MAX_EVENTS) events.removeFirst()
        saveLocked()
    }

    /** Newest first. */
    @Synchronized
    fun events(): List<Event> {
        loadLocked()
        return events.reversed()
    }

    @Synchronized
    fun clear() {
        events.clear()
        loaded = true
        saveLocked()
    }

    fun format(event: Event): String = "${timeFormat.format(Date(event.atMs))}  ${event.kind}  ${event.text}"

    /** What the device is: for the Diagnostics screen and the compatibility list. */
    fun deviceInfo(context: Context): List<Pair<String, String>> {
        val metrics = context.resources.displayMetrics
        val config = context.resources.configuration
        val touch = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        return listOf(
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.DISPLAY}",
            "Screen" to "${metrics.widthPixels}×${metrics.heightPixels} px, ${config.screenWidthDp}×${config.screenHeightDp} dp, " +
                "density ${metrics.density}" + if (touch) ", touch screen" else "",
            "App" to "${AppVersion.name(context)} (${context.packageName})",
            "Up for" to "${SystemClock.elapsedRealtime() / 60_000} min, boot #${bootCount() ?: "?"}",
        )
    }

    private fun bootCount(): Int? = appContext?.let {
        runCatching { Settings.Global.getInt(it.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull()
    }

    private fun loadLocked() {
        if (loaded) return
        val context = appContext ?: return
        loaded = true
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EVENTS, null) ?: return
        runCatching {
            val array = JSONArray(stored)
            val older = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Event(o.getLong("t"), o.getString("k"), o.getString("x"))
            }
            // Events recorded before the context was known come after the stored ones.
            val newer = events.toList()
            events.clear()
            events.addAll(older)
            events.addAll(newer)
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
    }

    private fun saveLocked() {
        val context = appContext ?: return
        if (!loaded) return
        val array = JSONArray()
        events.forEach { array.put(JSONObject().put("t", it.atMs).put("k", it.kind).put("x", it.text)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_EVENTS, array.toString()).apply()
    }
}
