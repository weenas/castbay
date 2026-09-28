package com.weenas.castbay.service

import android.content.Context
import com.weenas.castbay.util.Log
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** A newer CastBay on GitHub: its version ("1.0.72") and its release page. */
data class AppUpdate(val version: String, val url: String)

/**
 * Looks for a newer release on GitHub, at most once a day: the one request sends nothing
 * about the TV (GitHub sees an address, as for any web page). The result is kept, so the
 * home screen can show it at once and without asking again. Blocking; call off the main thread.
 */
class UpdateChecker(context: Context, private val appVersion: String) {
    private val preferences = context.applicationContext.getSharedPreferences("updates", Context.MODE_PRIVATE)

    /** The last release found, if newer than this app (no request made). */
    fun known(): AppUpdate? {
        val version = preferences.getString(KEY_VERSION, null) ?: return null
        val url = preferences.getString(KEY_URL, null) ?: return null
        return AppUpdate(version, url).takeIf { isNewer(version, appVersion) }
    }

    /** Asks GitHub if the last check was over a day ago; then as [known]. */
    fun check(nowMs: Long = System.currentTimeMillis()): AppUpdate? {
        if (nowMs - preferences.getLong(KEY_CHECKED_AT, 0) < CHECK_INTERVAL_MS) return known()
        try {
            latestRelease()?.let { (version, url) ->
                preferences.edit().putString(KEY_VERSION, version).putString(KEY_URL, url).apply()
                Log.i(TAG, "Latest release $version (this is $appVersion)")
            }
            preferences.edit().putLong(KEY_CHECKED_AT, nowMs).apply()
        } catch (error: Exception) {
            // Tried again at the next start.
            Log.w(TAG, "Update check failed: ${error.message}")
        }
        return known()
    }

    /** The newest published release (pre-releases too: CastBay's are), as (version, page). */
    private fun latestRelease(): Pair<String, String>? {
        val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
        val body = try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // GitHub's API requires a User-Agent.
            connection.setRequestProperty("User-Agent", "CastBay/$appVersion (https://github.com/weenas/castbay)")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw java.io.IOException("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        return newest(body)
    }

    companion object {
        private const val TAG = "CastBayUpdate"
        private const val RELEASES_URL = "https://api.github.com/repos/weenas/castbay/releases?per_page=10"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        private const val TIMEOUT_MS = 10_000
        private const val KEY_VERSION = "latest_version"
        private const val KEY_URL = "latest_url"
        private const val KEY_CHECKED_AT = "checked_at"

        /** The highest version among the published (not draft) releases in a GitHub listing. */
        internal fun newest(releasesJson: String): Pair<String, String>? {
            val releases = JSONArray(releasesJson)
            return (0 until releases.length()).map { releases.getJSONObject(it) }
                .filter { !it.optBoolean("draft") }
                .mapNotNull { release ->
                    val version = release.optString("tag_name").removePrefix("v")
                    version.takeIf { parse(it) != null }?.let { it to release.optString("html_url") }
                }
                .maxWithOrNull { a, b -> compare(a.first, b.first) }
        }

        /** Whether [candidate] ("1.0.72") is a later version than [current] ("1.0.70"). */
        fun isNewer(candidate: String, current: String): Boolean {
            if (parse(candidate) == null || parse(current) == null) return false
            return compare(candidate, current) > 0
        }

        private fun parse(version: String): List<Int>? =
            version.split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }

        private fun compare(a: String, b: String): Int {
            val x = parse(a).orEmpty()
            val y = parse(b).orEmpty()
            for (i in 0 until maxOf(x.size, y.size)) {
                val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
                if (d != 0) return d
            }
            return 0
        }
    }
}
