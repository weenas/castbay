package com.weenas.castbay.service

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.weenas.castbay.BuildConfig
import com.weenas.castbay.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Where installing an update has got to, for the About screen. */
sealed interface UpdateInstall {
    data object Idle : UpdateInstall
    /** [progress] 0..1, or null while the size isn't known. */
    data class Downloading(val progress: Float?) : UpdateInstall
    /** Android's installer is showing; the person confirms the update there. */
    data object Installing : UpdateInstall
    data class Failed(val reason: Reason) : UpdateInstall
    enum class Reason { DOWNLOAD, CHECKSUM, INSTALLER }
}

/**
 * Downloads an [AppUpdate]'s APK and hands it to Android's installer, only when the person
 * asks (About's Update button): nothing is downloaded or installed on its own, and Android
 * still asks them to confirm. The APK must have the release's SHA-256, and Android itself
 * refuses one not signed with CastBay's certificate. Builds without [BuildConfig.SELF_UPDATE]
 * (for app stores, which update apps themselves) leave this out and only point to the website.
 */
class UpdateInstaller(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.cacheDir, "updates")

    /** Blocking; call off the main thread. Tries each of the update's addresses in turn. */
    fun download(update: AppUpdate, onProgress: (Float?) -> Unit): File {
        directory.mkdirs()
        // Only the one being fetched is kept.
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, "CastBay-${update.version}.apk")
        var failure: Exception = IOException("no download address")
        for (url in update.apkUrls) {
            try {
                val sha256 = fetch(url, file, onProgress)
                if (update.sha256 != null && !sha256.equals(update.sha256, ignoreCase = true)) {
                    throw ChecksumException("SHA-256 $sha256, expected ${update.sha256}")
                }
                Log.i(TAG, "Downloaded ${update.version} from $url (${file.length()} bytes, SHA-256 $sha256)")
                return file
            } catch (error: Exception) {
                Log.w(TAG, "Download from $url failed: ${error.message}")
                file.delete()
                failure = error
            }
        }
        throw failure
    }

    /** Opens Android's installer on [apk]; it asks to allow installing from CastBay the first time. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    /** Saves [url] to [file], returning its SHA-256 (hex). */
    private fun fetch(url: String, file: File, onProgress: (Float?) -> Unit): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 }
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var reported = -1
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        val percent = total?.let { (done * 100 / it).toInt() } ?: -1
                        if (percent != reported) {
                            reported = percent
                            onProgress(total?.let { done.toFloat() / it })
                        }
                    }
                }
            }
            if (total != null && done != total) throw IOException("got $done of $total bytes")
            return digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            connection.disconnect()
        }
    }

    class ChecksumException(message: String) : IOException(message)

    private companion object {
        const val TAG = "CastBayUpdate"
        const val TIMEOUT_MS = 20_000
    }
}
