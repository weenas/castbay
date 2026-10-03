package com.weenas.castbay.service

import android.util.LruCache
import com.weenas.castbay.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Looks up time-synced lyrics on LRCLIB (lrclib.net: free, no account, community lyrics) by
 * the title, artist and length AirPlay senders report. Only used when the user turns lyrics
 * on, since it sends what is playing to that site. Blocking; call off the main thread.
 */
/** [appVersion] identifies the app to LRCLIB, as it asks. */
class LyricsClient(private val appVersion: String) {
    private val cache = LruCache<String, Result>(64)

    /** A cached lookup; [lyrics] is null when none were found. */
    private data class Result(val lyrics: Lyrics?)

    fun find(title: String, artist: String?, album: String?, durationSec: Double): Lyrics? {
        val key = listOf(title, artist.orEmpty(), durationSec.toInt().toString()).joinToString("\u0000")
        cache.get(key)?.let { return it.lyrics }
        val lyrics = try {
            lookUp(title, artist, album, durationSec)
        } catch (error: Exception) {
            // Not cached: the site is often slow from some networks, so the next song change retries.
            Log.w(TAG, "Lyrics lookup failed for ${Log.personal(title)}: ${error.message}")
            return null
        }
        cache.put(key, Result(lyrics))
        Log.i(TAG, "Lyrics for ${Log.personal(title)} / ${Log.personal(artist.orEmpty())}: ${lyrics?.lines?.size?.let { "$it lines" } ?: "none"}")
        return lyrics
    }

    private fun lookUp(title: String, artist: String?, album: String?, durationSec: Double): Lyrics? {
        // The exact match needs all four fields; search is looser.
        if (!artist.isNullOrBlank() && !album.isNullOrBlank() && durationSec > 0) {
            exact(title, artist, album, durationSec)?.let { return it }
        }
        search(title, artist, durationSec)?.let { return it }
        return Lyrics.simplifyTitle(title)?.let { search(it, artist, durationSec) }
    }

    private fun exact(title: String, artist: String, album: String, durationSec: Double): Lyrics? {
        val body = get(
            "get",
            "track_name" to title, "artist_name" to artist, "album_name" to album,
            "duration" to durationSec.toInt().toString()
        ) ?: return null
        return JSONObject(body).optString("syncedLyrics").takeIf { it.isNotBlank() }?.let(Lyrics::parseLrc)
    }

    private fun search(title: String, artist: String?, durationSec: Double): Lyrics? {
        val params = mutableListOf("track_name" to title)
        if (!artist.isNullOrBlank()) params += "artist_name" to artist
        val body = get("search", *params.toTypedArray()) ?: return null
        val results = JSONArray(body)
        val candidates = (0 until results.length()).map { i ->
            val item = results.getJSONObject(i)
            LyricsCandidate(item.optDouble("duration", 0.0).takeUnless { it.isNaN() } ?: 0.0, item.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" })
        }
        return LyricsMatcher.best(candidates, durationSec)?.syncedLrc?.let(Lyrics::parseLrc)
    }

    /** The response body, or null for 404 (not found). */
    private fun get(path: String, vararg params: Pair<String, String>): String? {
        val query = params.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, "UTF-8")}" }
        val connection = URL("$BASE_URL/$path?$query").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            // LRCLIB asks clients to identify themselves.
            connection.setRequestProperty("User-Agent", "CastBay/$appVersion (https://github.com/weenas/castbay)")
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw java.io.IOException("HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "CastBayLyrics"
        const val BASE_URL = "https://lrclib.net/api"
        // LRCLIB can take several seconds to answer from China.
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
    }
}
