package com.weenas.castbay.util

import android.content.Context
import com.weenas.castbay.R
import java.io.IOException
import java.net.URL

/**
 * Where CastBay's own requests go (the update check, problem reports, usage statistics): the
 * website, castbay.weenas.com, on Cloudflare; and cast.weenas.com, a relay that passes the same
 * requests on to it, for networks where Cloudflare can't be reached (common in mainland China).
 * A request tries them in turn, starting with the one that answered last.
 */
object Servers {
    private const val TAG = "CastBayServers"

    @Volatile private var lastAnswered: String? = null

    /** The website answered, with [code]: trying the other address wouldn't help. */
    open class HttpError(val code: Int) : IOException("HTTP $code")

    /**
     * Makes [request] to [path] ("/api/reports") on each address until one answers: its result,
     * or the website's [HttpError], or the last network failure. Blocking.
     */
    fun <T> call(context: Context, path: String, request: (URL) -> T): T {
        val bases = bases(context).sortedByDescending { it == lastAnswered }
        var failure: IOException? = null
        for (base in bases) {
            try {
                return request(URL(base + path)).also { lastAnswered = base }
            } catch (e: HttpError) {
                lastAnswered = base
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "${base.removePrefix("https://")}$path failed: ${e.javaClass.simpleName}: ${e.message?.take(60)}")
                failure = e
            }
        }
        throw failure ?: IOException("no address")
    }

    private fun bases(context: Context): List<String> =
        listOf(context.getString(R.string.server_url)) +
            listOfNotNull(context.getString(R.string.relay_url).takeIf { it.isNotEmpty() })
}
