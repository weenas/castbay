package com.weenas.castbay.service

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import com.weenas.castbay.util.Log

/**
 * Decodes one small H.265 picture (assets/hevc_selftest.h265: Main profile, 256x144, an IDR with its
 * parameter sets, made by the encoder a sender uses) with the decoder the video renderer would use.
 * A TV that says it has a hardware H.265 decoder is not told to a CastBay Desktop sender as one
 * until this has worked: some TVs list a decoder that then fails to start or never gives a picture.
 */
object HevcSelfTest {
    private const val TAG = "CastBayHevcTest"
    private const val TIMEOUT_MS = 3000L

    /** True when a picture came out. Takes a fraction of a second; run it off the main thread. */
    fun run(context: Context): Boolean {
        val started = SystemClock.elapsedRealtime()
        var codec: MediaCodec? = null
        val result = try {
            val unit = context.assets.open("hevc_selftest.h265").use { it.readBytes() }
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 256, 144)
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
            codec.configure(format, null, null, 0)
            codec.start()
            decodes(codec, unit, started + TIMEOUT_MS).also {
                Log.i(TAG, "H.265 self-test with ${codec.name}: ${if (it) "a picture came out" else "no picture"} after ${SystemClock.elapsedRealtime() - started} ms")
            }
        } catch (error: Exception) {
            Log.w(TAG, "H.265 self-test failed: ${error.javaClass.simpleName}: ${error.message}")
            false
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
        }
        return result
    }

    /** Queues the unit, then the end of the stream (so a decoder that holds pictures back lets it go), and waits for a picture. */
    private fun decodes(codec: MediaCodec, unit: ByteArray, deadline: Long): Boolean {
        val info = MediaCodec.BufferInfo()
        var unitQueued = false
        var endQueued = false
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!unitQueued) {
                val index = codec.dequeueInputBuffer(20_000)
                if (index >= 0) {
                    codec.getInputBuffer(index)?.apply { clear(); put(unit) }
                    codec.queueInputBuffer(index, 0, unit.size, 0, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                    unitQueued = true
                }
            } else if (!endQueued) {
                val index = codec.dequeueInputBuffer(20_000)
                if (index >= 0) {
                    codec.queueInputBuffer(index, 0, 0, 33_000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    endQueued = true
                }
            }
            val output = codec.dequeueOutputBuffer(info, 20_000)
            if (output >= 0) {
                val picture = info.size > 0
                codec.releaseOutputBuffer(output, false)
                if (picture) return true
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return false
            }
        }
        return false
    }
}
