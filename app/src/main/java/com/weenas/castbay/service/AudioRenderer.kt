package com.weenas.castbay.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import com.weenas.castbay.util.Log
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * Plays AirPlay audio through an [AudioTrack]:
 * - [render]: AAC-ELD frames from screen mirroring (44.1 kHz stereo, 480 samples each),
 *   decoded with MediaCodec, created lazily on the first frame;
 * - [renderPcm]: PCM the native layer already decoded (ALAC from audio streaming).
 *
 * Audio is played as it arrives rather than scheduled by timestamp. Both paths write from
 * the same thread, so they share one track, torn down when the session ends.
 *
 * Mirroring audio plays at once, to stay with the picture. Music has no picture to keep up
 * with, but arrives only just in time: any wait for a Wi-Fi resend drained the track and was
 * heard as a stutter (the TCL logged its queue at 0 and repeated underruns). So music starts,
 * and restarts after an underrun or a flush, only once [MUSIC_LEAD_FRAMES] are buffered.
 */
class AudioRenderer {
    private val lock = Any()
    private val handler = Handler(HandlerThread("CastBay-audio").apply { start() }.looper)

    private var codec: MediaCodec? = null
    private var track: AudioTrack? = null

    // For the stats overlay: which path is active and its compressed input rate.
    private val inputRate = RateMeter()
    @Volatile private var activeStats: AudioStats? = null
    private val pendingFrames = ArrayDeque<ByteArray>()
    private val freeInputs = ArrayDeque<Int>()
    private var droppedFrames = 0L

    /** Bumped by [flush] and [stop], so PCM queued before them is skipped. */
    private val generation = AtomicInteger()
    private val pendingPcm = AtomicInteger()
    /** Linear gain from the sender's volume slider; kept across track re-creation. */
    @Volatile private var volume = 1f

    // Music (ALAC) health, logged: the track's underruns and how low the queue ran, which is
    // the cushion left against Wi-Fi resends. Audio thread only.
    private var loggedUnderruns = 0
    private var lowestQueue = Int.MAX_VALUE
    private var queueLoggedAtMs = 0L
    /** The current track plays music (buffered before playing) rather than mirroring audio. */
    private var musicTrack = false
    /** Frames written to the music track since it was created or flushed. Audio thread only. */
    private var musicFramesWritten = 0L
    /** Music paused from the TV, ahead of the sender, which takes about a second to stop. */
    private var musicHeld = false

    fun render(frame: ByteArray) {
        if (frame.isEmpty()) return
        inputRate.record(frame.size)
        synchronized(lock) {
            if (codec == null) startCodecLocked()
            if (codec == null) return
            if (pendingFrames.size >= MAX_PENDING_FRAMES) {
                // Audio frames decode independently, so dropping the oldest only costs a
                // short gap and keeps latency bounded.
                pendingFrames.removeFirst()
                droppedFrames++
            }
            pendingFrames.addLast(frame)
            feedLocked()
        }
    }

    /** Plays interleaved S16 stereo PCM at 44.1 kHz. */
    /** [compressedBytes]: size of the ALAC frame [pcm] was decoded from, for the bitrate stat. */
    fun renderPcm(pcm: ByteArray, compressedBytes: Int = 0) {
        if (pcm.isEmpty()) return
        inputRate.record(compressedBytes)
        if (activeStats?.codec != ALAC_STATS.codec) activeStats = ALAC_STATS
        if (pendingPcm.get() >= MAX_PENDING_PCM) {
            synchronized(lock) { droppedFrames++ }
            return
        }
        val queuedIn = generation.get()
        pendingPcm.incrementAndGet()
        handler.post {
            pendingPcm.decrementAndGet()
            if (queuedIn != generation.get()) return@post
            val output = synchronized(lock) {
                // A mirroring track (small, playing at once) can't buffer music: replace it.
                if (track != null && !musicTrack) {
                    track?.release()
                    track = null
                }
                track ?: createTrack(SAMPLE_RATE, CHANNELS, bufferMs = MUSIC_BUFFER_MS, play = false).also {
                    track = it
                    musicTrack = true
                    musicFramesWritten = 0
                    loggedUnderruns = 0
                }
            }
            // Blocking once the track's buffer is full, which paces this thread to playback.
            output.write(pcm, 0, pcm.size)
            musicFramesWritten += pcm.size / BYTES_PER_FRAME
            // The session can end (stop() releases the track) while this runs; a released track
            // throws from its getters, which crashed the app on the TCL.
            try {
                keepMusicBuffered(output)
                logMusicHealth(output)
            } catch (released: IllegalStateException) {
                Log.d(TAG, "Music track released while playing")
            }
        }
    }

    /** Plays once [MUSIC_LEAD_FRAMES] are buffered; after an underrun, pauses to rebuild them. */
    private fun keepMusicBuffered(output: AudioTrack) {
        val buffered = musicFramesWritten - (output.playbackHeadPosition.toLong() and 0xFFFFFFFFL)
        if (output.playState == AudioTrack.PLAYSTATE_PLAYING) {
            if (output.underrunCount > loggedUnderruns) {
                // Silence is already heard; one pause to refill beats a run of short gaps.
                output.pause()
                Log.i(TAG, "Music ran dry; buffering ${MUSIC_LEAD_FRAMES * 1000 / SAMPLE_RATE} ms again")
            }
        } else if (!musicHeld && buffered >= minOf(MUSIC_LEAD_FRAMES, output.bufferSizeInFrames * 3 / 4)) {
            // Never more than the track holds, or a full paused track would block writes for good.
            output.play()
        }
    }

    private fun logMusicHealth(output: AudioTrack) {
        lowestQueue = minOf(lowestQueue, pendingPcm.get())
        val underruns = output.underrunCount
        if (underruns > loggedUnderruns) {
            Log.i(TAG, "Music underrun (${underruns - loggedUnderruns} more, $underruns in all); queue ${pendingPcm.get()} frames")
            loggedUnderruns = underruns
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - queueLoggedAtMs >= QUEUE_LOG_INTERVAL_MS) {
            // 352-sample frames: 8 ms each.
            Log.i(TAG, "Music queue: lowest $lowestQueue frames (${lowestQueue * 8} ms) in the last ${QUEUE_LOG_INTERVAL_MS / 1000} s")
            lowestQueue = Int.MAX_VALUE
            queueLoggedAtMs = now
        }
    }

    /**
     * Pauses music at once ([hold]) when it is paused from the TV, rather than a second later
     * when the sender stops sending; audio still arriving is kept and plays on release.
     */
    fun holdMusic(hold: Boolean) {
        handler.post {
            musicHeld = hold
            val output = synchronized(lock) { track?.takeIf { musicTrack } } ?: return@post
            try {
                if (hold) output.pause() else keepMusicBuffered(output)
            } catch (released: IllegalStateException) {
                Log.d(TAG, "Music track released while holding")
            }
        }
    }

    fun setVolume(gain: Float) {
        volume = gain
        synchronized(lock) { track?.setVolume(gain) }
    }

    /** Drops audio not yet played, e.g. when the sender pauses, seeks or skips a track. */
    fun flush() {
        generation.incrementAndGet()
        synchronized(lock) { pendingFrames.clear() }
        handler.post {
            synchronized(lock) {
                track?.let {
                    it.pause()
                    it.flush()
                    // Music plays again once buffered (keepMusicBuffered); mirroring at once.
                    // A flush means new audio from the sender (a resume, seek or new song).
                    if (musicTrack) {
                        musicFramesWritten = 0
                        musicHeld = false
                    } else {
                        it.play()
                    }
                }
            }
        }
    }

    /** What is being played, for the stats overlay; null when no audio is active. */
    fun stats(): AudioStats? = activeStats?.copy(bitrateBps = inputRate.bitsPerSecond())

    fun stop() {
        generation.incrementAndGet()
        activeStats = null
        inputRate.reset()
        synchronized(lock) {
            releaseLocked()
            if (droppedFrames > 0) Log.i(TAG, "Session ended, $droppedFrames audio frames dropped")
            droppedFrames = 0
        }
    }

    private fun startCodecLocked() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectELD)
            setInteger(MediaFormat.KEY_IS_ADTS, 0)
            setByteBuffer("csd-0", ByteBuffer.wrap(ELD_AUDIO_SPECIFIC_CONFIG))
        }
        val decoder = try {
            MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to create AAC decoder", error)
            return
        }
        try {
            decoder.setCallback(DecoderCallback(decoder), handler)
            decoder.configure(format, null, null, 0)
            decoder.start()
            codec = decoder
            Log.i(TAG, "AAC-ELD decoder started: ${decoder.name}")
            activeStats = AudioStats("AAC-ELD", SAMPLE_RATE, CHANNELS, bitsPerSample = 16, decoder = decoder.name)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start AAC-ELD decoder", error)
            decoder.release()
        }
    }

    private fun feedLocked() {
        val decoder = codec ?: return
        while (freeInputs.isNotEmpty() && pendingFrames.isNotEmpty()) {
            val index = freeInputs.removeFirst()
            val frame = pendingFrames.removeFirst()
            try {
                val input = decoder.getInputBuffer(index) ?: continue
                if (frame.size > input.capacity()) {
                    decoder.queueInputBuffer(index, 0, 0, 0, 0)
                    droppedFrames++
                    continue
                }
                input.clear()
                input.put(frame)
                decoder.queueInputBuffer(index, 0, frame.size, 0, 0)
            } catch (error: IllegalStateException) {
                Log.e(TAG, "Audio decoder rejected input", error)
                releaseLocked()
                return
            }
        }
    }

    private fun releaseLocked() {
        pendingFrames.clear()
        freeInputs.clear()
        codec?.let { decoder ->
            codec = null
            try {
                decoder.stop()
            } catch (error: IllegalStateException) {
                Log.w(TAG, "Audio decoder was already stopped", error)
            } finally {
                decoder.release()
            }
        }
        track?.let {
            track = null
            try {
                it.stop()
            } catch (error: IllegalStateException) {
                Log.w(TAG, "AudioTrack was already stopped", error)
            }
            it.release()
        }
    }

    /**
     * [bufferMs]: 0 for the smallest safe buffer (mirroring), or how much the track holds;
     * [play]: start at once, or leave it to [keepMusicBuffered].
     */
    private fun createTrack(sampleRate: Int, channels: Int, bufferMs: Int = 0, play: Boolean = true): AudioTrack {
        val channelMask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        val bufferBytes = maxOf(minBuffer * 2, sampleRate / 1000 * bufferMs * channels * 2)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()
            .also {
                it.setVolume(volume)
                if (play) it.play()
            }
    }

    /** Callbacks run on the audio thread; [owner] guards against events from a released codec. */
    private inner class DecoderCallback(private val owner: MediaCodec) : MediaCodec.Callback() {
        override fun onInputBufferAvailable(mc: MediaCodec, index: Int) {
            synchronized(lock) {
                if (codec !== owner) return
                freeInputs.addLast(index)
                feedLocked()
            }
        }

        override fun onOutputBufferAvailable(mc: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            val output: AudioTrack
            val pcm: ByteArray
            synchronized(lock) {
                if (codec !== owner) return
                try {
                    val buffer = mc.getOutputBuffer(index)
                    pcm = if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        ByteArray(info.size).also { buffer.get(it) }
                    } else {
                        ByteArray(0)
                    }
                    mc.releaseOutputBuffer(index, false)
                } catch (error: IllegalStateException) {
                    Log.w(TAG, "Could not read audio output buffer", error)
                    return
                }
                output = track ?: createTrack(SAMPLE_RATE, CHANNELS).also {
                    track = it
                    musicTrack = false
                }
            }
            // Blocking write paces this thread to playback without holding the lock, so the
            // protocol thread can keep queueing input meanwhile.
            if (pcm.isNotEmpty()) output.write(pcm, 0, pcm.size)
        }

        override fun onOutputFormatChanged(mc: MediaCodec, format: MediaFormat) {
            Log.i(TAG, "Audio decoder output format: $format")
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            synchronized(lock) {
                if (codec !== owner) return
                track?.release()
                track = createTrack(sampleRate, channels)
                musicTrack = false
            }
        }

        override fun onError(mc: MediaCodec, error: MediaCodec.CodecException) {
            synchronized(lock) {
                if (codec !== owner) return
                Log.e(TAG, "Audio decoder error (transient=${error.isTransient})", error)
                if (!error.isTransient) releaseLocked()
            }
        }
    }

    companion object {
        /**
         * How much later music is heard than it arrives: what is buffered before it plays.
         * Resends came back within 10 ms on the TCL; 0.5 s made pausing and resuming slow. The sender's progress is shifted by it, so lyrics match what is heard.
         */
        const val MUSIC_LEAD_MS = 300L

        private const val TAG = "CastBayAudio"
        private const val SAMPLE_RATE = 44100
        private const val CHANNELS = 2
        /** AudioSpecificConfig for AAC-ELD, 44.1 kHz, stereo, 480-sample frames (as in RPiPlay). */
        private val ELD_AUDIO_SPECIFIC_CONFIG = byteArrayOf(0xF8.toByte(), 0xE8.toByte(), 0x50, 0x00)
        /** About half a second of 480-sample frames. */
        private const val MAX_PENDING_FRAMES = 48
        /**
         * About three seconds of 352-sample frames: senders stream music about two seconds
         * ahead of playback, and that initial burst must not be dropped.
         */
        private const val MAX_PENDING_PCM = 375
        private const val QUEUE_LOG_INTERVAL_MS = 30_000L
        /** 16-bit stereo. */
        private const val BYTES_PER_FRAME = 4
        /** Music buffered before it plays: rides out Wi-Fi resends and jitter. */
        private const val MUSIC_LEAD_FRAMES = (SAMPLE_RATE * MUSIC_LEAD_MS / 1000).toInt()
        /** Music track size: room above the lead, so writes don't block before it plays. */
        private const val MUSIC_BUFFER_MS = 2000

        /** AirPlay's fixed ALAC format, decoded by Apple's reference decoder in the app. */
        private val ALAC_STATS = AudioStats(
            "ALAC", sampleRate = 44100, channels = 2, bitsPerSample = 16, decoder = "Apple ALAC (in app)"
        )
    }
}
