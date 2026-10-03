package com.weenas.castbay.util

import android.content.Context
import android.os.Process
import com.weenas.castbay.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A problem report the person chooses to send (About → Diagnostics → Upload): this device,
 * the Diagnostics events and the app's recent log, with personal details taken out on the
 * device ([scrub]). The website keeps it for 90 days under a short ID ("CB-7K3F9Q") that the
 * person passes on with their problem report; nothing else is sent, and nothing is sent
 * without them asking.
 */
object LogReport {
    private const val TAG = "CastBayReport"
    // The website takes up to 512 KB.
    private const val MAX_CHARS = 400_000
    private const val LOGCAT_LINES = 3000
    private const val CONNECT_TIMEOUT_MS = 20_000
    // Some networks upload slowly; the answer comes once the whole report is in.
    private const val READ_TIMEOUT_MS = 60_000
    private const val ATTEMPTS = 2

    /**
     * The report's text, already scrubbed: what [upload] sends. With [crash] (from
     * [CrashReports]), that comes first. Blocking (reads logcat).
     */
    fun build(context: Context, crash: String? = null): String {
        val utc = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val head = buildString {
            appendLine("CastBay problem report, ${utc.format(Date())}")
            Diagnostics.deviceInfo(context).forEach { (label, value) -> appendLine("$label: $value") }
            appendLine()
            if (crash != null) {
                appendLine("== The crash ==")
                appendLine(crash.trimEnd())
                appendLine()
            }
            appendLine("== Diagnostics events (oldest first) ==")
            Diagnostics.events().asReversed().forEach { appendLine(Diagnostics.format(it)) }
            appendLine()
            appendLine("== Log ==")
        }
        // logcat has native and player logs too; some TVs keep none, so then the app's own.
        val logcat = logcat()
        val log = if (logcat.size >= 20) logcat else Log.recent()
        // The newest lines matter most: drop the oldest if it is too long.
        val budget = MAX_CHARS - head.length
        val kept = ArrayDeque<String>()
        var size = 0
        for (line in log.asReversed()) {
            if (size + line.length + 1 > budget) break
            kept.addFirst(line)
            size += line.length + 1
        }
        return scrub(head + kept.joinToString("\n"))
    }

    /**
     * Sends [text], trying again once if the network failed (not if the website refused it):
     * the report's ID, or why it failed ([reason] says it briefly). Blocking; call off the main
     * thread. A failure is recorded in Diagnostics, with its reason.
     */
    fun upload(context: Context, text: String, appVersion: String, crash: Boolean = false): Result<String> {
        var result: Result<String> = Result.failure(java.io.IOException("not sent"))
        for (attempt in 1..ATTEMPTS) {
            result = runCatching { send(context, text, appVersion, crash) }
            val error = result.exceptionOrNull() ?: return result
            Log.w(TAG, "Report upload failed (attempt $attempt): ${reason(error)}", error)
            if (error is Refused || attempt == ATTEMPTS) break
            Thread.sleep(2_000)
        }
        result.exceptionOrNull()?.let { Diagnostics.record("report", "Upload failed: ${reason(it)}") }
        return result
    }

    /** A short reason for an upload failure, for the screen and Diagnostics. */
    fun reason(error: Throwable): String = when (error) {
        is TooManyReports -> "too many reports"
        is Refused -> "HTTP ${error.code}"
        is java.net.SocketTimeoutException -> "timed out"
        is java.net.UnknownHostException -> "address not found (DNS)"
        is java.net.ConnectException -> "could not connect"
        is javax.net.ssl.SSLException -> "secure connection failed (${error.javaClass.simpleName})"
        is java.net.SocketException -> "connection broken (${error.message?.take(40)})"
        else -> "${error.javaClass.simpleName}: ${error.message?.take(60)}"
    }

    private fun send(context: Context, text: String, appVersion: String, crash: Boolean): String {
        val connection = URL(context.getString(R.string.reports_url)).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            connection.setRequestProperty("X-CastBay-Version", appVersion)
            connection.setRequestProperty("X-CastBay-Report", if (crash) "crash" else "manual")
            val bytes = text.toByteArray()
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            if (code == 429) throw TooManyReports()
            if (code != HttpURLConnection.HTTP_CREATED) throw Refused(code)
            val id = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getString("id")
            Log.i(TAG, "Report uploaded as $id (${bytes.size} bytes)")
            Diagnostics.record("report", "Uploaded as $id")
            id
        } finally {
            connection.disconnect()
        }
    }

    /** The website answered, but not with a report ID (sending again wouldn't help). */
    open class Refused(val code: Int) : java.io.IOException("HTTP $code")

    /** The website accepts a few reports a minute from one place. */
    class TooManyReports : Refused(429)

    /** This process's lines in logcat, oldest first; empty if logcat can't be read. */
    private fun logcat(): List<String> = runCatching {
        val process = ProcessBuilder(
            "logcat", "-d", "-v", "threadtime", "-t", LOGCAT_LINES.toString(), "--pid", Process.myPid().toString()
        ).redirectErrorStream(true).start()
        val lines = process.inputStream.bufferedReader().use { it.readLines() }
        process.destroy()
        lines
    }.getOrDefault(emptyList())

    // UxPlay logs the PIN a sender must enter, and the like.
    private val secret = Regex("""(?i)\b(pin|password|passwd)(\s*[=:]\s*)"[^"]*"""")
    private val personal = Regex("${Log.PERSONAL_START}[^${Log.PERSONAL_END}\\n]*${Log.PERSONAL_END}")
    private val link = Regex("""\b[a-zA-Z][a-zA-Z0-9+.-]*://[^\s"'<>]+""")
    private val email = Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+""")
    private val mac = Regex("""\b[0-9A-Fa-f]{2}([:-][0-9A-Fa-f]{2}){5}\b""")
    private val ipv4 = Regex("""\b(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})\b""")
    // Global IPv6 addresses (2000::/3); link-local fe80:: ones say nothing about where one is.
    private val ipv6 = Regex("""\b[23][0-9a-fA-F]{3}(:[0-9a-fA-F]{0,4}){2,7}\b""")

    /**
     * Takes out what could identify the person or what they played: values logged as
     * [Log.personal] (titles, names), PINs and passwords, links, email and hardware addresses, and internet
     * addresses (local network ones, which help find network problems, stay).
     */
    internal fun scrub(text: String): String = text
        .replace(personal, "${Log.PERSONAL_START}…${Log.PERSONAL_END}")
        .replace(secret) { "${it.groupValues[1]}${it.groupValues[2]}\"…\"" }
        .replace(link, "<link>")
        .replace(email, "<email>")
        .replace(mac, "<mac>")
        .replace(ipv6, "<ipv6>")
        .replace(ipv4) { if (isLocal(it.groupValues.drop(1).map(String::toInt))) it.value else "<ip>" }

    private fun isLocal(parts: List<Int>): Boolean {
        val (a, b) = parts
        return parts.any { it > 255 } || // not an address (a version number or the like)
            a == 10 || a == 127 || a == 0 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127) // carrier-grade NAT
    }
}
