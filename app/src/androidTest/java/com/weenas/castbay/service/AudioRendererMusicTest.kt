package com.weenas.castbay.service

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AirPlay music through a real AudioTrack, fed as a sender does: 352-sample ALAC frames in real
 * time, each due about two seconds after it is sent. Music must be heard when due, as the
 * sender times its progress and where it resumes by that; and stay so after pauses.
 */
@RunWith(AndroidJUnit4::class)
class AudioRendererMusicTest {
    private val renderer = AudioRenderer()

    @After
    fun tearDown() = renderer.stop()

    /** [seconds] of silent stereo frames, paced like a sender (one every 352 / 44100 s). */
    private fun send(seconds: Double) {
        val frames = (seconds * 44100 / 352).toInt()
        val start = SystemClock.elapsedRealtimeNanos()
        val dueStartUs = System.currentTimeMillis() * 1000 + SENDER_DELAY_US
        repeat(frames) { n ->
            val due = start + n * 352_000_000_000L / 44100
            while (SystemClock.elapsedRealtimeNanos() < due) Thread.sleep(1)
            renderer.renderPcm(ByteArray(352 * 4), dueStartUs + n * 352_000_000L / 44100, 700)
        }
    }

    @Test
    fun musicIsHeardWhenDue() {
        send(6.0)
        val error = renderer.musicSyncErrorMs()
        android.util.Log.i("AudioRendererMusicTest", "heard $error ms off after 6 s")
        assertTrue("heard $error ms off after 6 s", abs(error) <= 5)
    }

    /** Apple Music flushes on pausing and resumes with music timed afresh. */
    @Test
    fun resumingAfterAFlushKeepsTiming() {
        send(3.0)
        repeat(3) { round ->
            renderer.flush()
            Thread.sleep(1500)    // paused
            send(3.0)
            val error = renderer.musicSyncErrorMs()
            android.util.Log.i("AudioRendererMusicTest", "after pause ${round + 1}: heard $error ms off")
            assertTrue("heard $error ms off after pause ${round + 1}", abs(error) <= 5)
        }
    }

    /** NetEase Cloud Music just stops sending; what came plays out, and it resumes timed afresh. */
    @Test
    fun resumingAfterTheSenderStopsKeepsTiming() {
        send(3.0)
        Thread.sleep(4000)        // what came plays out, then the pause
        send(3.0)
        val error = renderer.musicSyncErrorMs()
        android.util.Log.i("AudioRendererMusicTest", "after a stop: heard $error ms off")
        assertTrue("heard $error ms off after a stop", abs(error) <= 5)
    }

    private companion object {
        const val SENDER_DELAY_US = 2_000_000L
    }
}
