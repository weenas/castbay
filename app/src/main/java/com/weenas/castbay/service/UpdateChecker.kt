package com.weenas.castbay.service

import android.content.Context
import com.weenas.castbay.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One version's release notes, in English and Chinese: plain-text bullet points. */
data class ReleaseNotes(val version: String, val date: String, val en: List<String>, val zh: List<String>)

/**
 * A newer CastBay: its version ("1.0.72"), its release page, and where to download its APK
 * ([apkUrls], tried in order) with the SHA-256 the download must have (null if not known),
 * and the latest versions' release notes, newest first (from the website; empty from GitHub).
 */
data class AppUpdate(
    val version: String,
    val url: String,
    val apkUrls: List<String> = emptyList(),
    val sha256: String? = null,
    val notes: List<ReleaseNotes> = emptyList()
) {
    /** The notes of the versions after [installed] up to this one, newest first. */
    fun notesSince(installed: String): List<ReleaseNotes> = notes.filter {
        UpdateChecker.isNewer(it.version, installed) && !UpdateChecker.isNewer(it.version, version)
    }

    internal fun toJson(): String = JSONObject()
        .put("version", version).put("page", url).put("urls", JSONArray(apkUrls)).put("sha256", sha256 ?: "")
        .put("notes", JSONArray(notes.map {
            JSONObject().put("version", it.version).put("date", it.date).put("en", JSONArray(it.en)).put("zh", JSONArray(it.zh))
        }))
        .toString()

    internal companion object {
        private fun strings(array: JSONArray?): List<String> =
            (0 until (array?.length() ?: 0)).mapNotNull { array!!.optString(it).takeIf(String::isNotBlank) }

        private fun releaseNotes(o: JSONObject): ReleaseNotes? {
            val version = o.optString("version").takeIf { it.isNotEmpty() } ?: return null
            return ReleaseNotes(version, o.optString("date"), strings(o.optJSONArray("en")), strings(o.optJSONArray("zh")))
        }

        /** From the website's latest.json, or as [toJson] kept it; null if malformed. */
        fun fromJson(json: String): AppUpdate? = runCatching {
            val o = JSONObject(json)
            val urls = o.optJSONArray("urls")
            AppUpdate(
                version = o.getString("version"),
                url = o.getString("page"),
                apkUrls = (0 until (urls?.length() ?: 0)).map { urls!!.getString(it) },
                sha256 = o.optString("sha256").takeIf { it.length == 64 },
                notes = o.optJSONArray("notes")?.let { array ->
                    (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(::releaseNotes) }
                }.orEmpty()
            )
        }.getOrNull()
    }
}

/**
 * Looks for a newer release at most once a day: first on the website (castbay.weenas.com
 * publishes latest.json with each release; it is reachable where GitHub often isn't), then
 * on GitHub. The one request sends nothing about the TV (the server sees an address, as for
 * any web page). The result is kept, so the home screen can show it at once and without
 * asking again. Blocking; call off the main thread.
 */
class UpdateChecker(context: Context, private val appVersion: String) {
    private val preferences = context.applicationContext.getSharedPreferences("updates", Context.MODE_PRIVATE)

    /** The last release found, if newer than this app (no request made). */
    fun known(): AppUpdate? {
        val update = preferences.getString(KEY_UPDATE, null)?.let(AppUpdate::fromJson) ?: return null
        return update.takeIf { isNewer(it.version, appVersion) }
    }

    /** Asks if the last check was over a day ago (or [force]); then as [known]. */
    fun check(nowMs: Long = System.currentTimeMillis(), force: Boolean = false): AppUpdate? {
        if (!force && nowMs - preferences.getLong(KEY_CHECKED_AT, 0) < CHECK_INTERVAL_MS) return known()
        fetch(nowMs)
        // Without an answer, it is tried again at the next start.
        return known()
    }

    /**
     * Asks now, whatever the time of the last check (About's Check for updates): a newer
     * release, null if this is the latest, or a failure if neither server answered.
     */
    fun checkNow(nowMs: Long = System.currentTimeMillis()): Result<AppUpdate?> =
        if (fetch(nowMs) != null) Result.success(known()) else Result.failure(java.io.IOException("no answer"))

    /** The latest release from the website, else GitHub, kept as [known]; null if neither answered. */
    private fun fetch(nowMs: Long): AppUpdate? {
        val latest = runCatching { fromWebsite() }
            .onFailure { Log.w(TAG, "Website update check failed: ${it.message}") }
            .getOrNull()
            ?: runCatching { fromGitHub() }
                .onFailure { Log.w(TAG, "GitHub update check failed: ${it.message}") }
                .getOrNull()
        if (latest != null) {
            preferences.edit().putString(KEY_UPDATE, latest.toJson()).putLong(KEY_CHECKED_AT, nowMs).apply()
            Log.i(TAG, "Latest release ${latest.version} (this is $appVersion)")
        }
        return latest
    }

    private fun fromWebsite(): AppUpdate? = AppUpdate.fromJson(get(WEBSITE_URL))

    private fun fromGitHub(): AppUpdate? = newest(get(RELEASES_URL))

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // GitHub's API requires a User-Agent.
            connection.setRequestProperty("User-Agent", "CastBay/$appVersion (https://github.com/weenas/castbay)")
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw java.io.IOException("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "CastBayUpdate"
        private const val WEBSITE_URL = "https://castbay.weenas.com/latest.json"
        private const val RELEASES_URL = "https://api.github.com/repos/weenas/castbay/releases?per_page=10"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        private const val TIMEOUT_MS = 10_000
        private const val KEY_UPDATE = "latest"
        private const val KEY_CHECKED_AT = "checked_at"

        /**
         * The highest version among the published (not draft) releases in a GitHub listing,
         * with its CastBay-<version>.apk and that file's SHA-256 when GitHub lists them.
         */
        internal fun newest(releasesJson: String): AppUpdate? {
            val releases = JSONArray(releasesJson)
            return (0 until releases.length()).map { releases.getJSONObject(it) }
                .filter { !it.optBoolean("draft") }
                .mapNotNull { release ->
                    val version = release.optString("tag_name").removePrefix("v")
                    if (parse(version) == null) return@mapNotNull null
                    val assets = release.optJSONArray("assets") ?: JSONArray()
                    val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                        .firstOrNull { it.optString("name") == "CastBay-$version.apk" }
                    AppUpdate(
                        version = version,
                        url = release.optString("html_url"),
                        apkUrls = listOfNotNull(apk?.optString("browser_download_url")?.takeIf { it.isNotEmpty() }),
                        sha256 = apk?.optString("digest")?.removePrefix("sha256:")?.takeIf { it.length == 64 }
                    )
                }
                .maxWithOrNull { a, b -> compare(a.version, b.version) }
        }

        /** Whether [candidate] ("1.0.72") is a later version than [current] ("1.0.70"). */
        fun isNewer(candidate: String, current: String): Boolean {
            if (parse(candidate) == null || parse(current) == null) return false
            return compare(candidate, current) > 0
        }

        // A test build's "1.1.0-dev+6228385" counts as 1.1.0.
        private fun parse(version: String): List<Int>? =
            version.substringBefore('-').substringBefore('+')
                .split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }

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
