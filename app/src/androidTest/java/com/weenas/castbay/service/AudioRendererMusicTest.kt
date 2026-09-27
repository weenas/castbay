package com.weenas.castbay.service

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AirPlay music through a real AudioTrack, fed as a sender does: 352-sample ALAC frames in real
 * time. Pausing from the TV holds the track at once while the sender goes on for about 0.8 s;
 * resuming must not leave that audio queued, or every pause adds to the delay (heard as lyrics
 * running early, then as gaps once the track overflows).
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
        repeat(frames) { n ->
            val due = start + n * 352_000_000_000L / 44100
            while (SystemClock.elapsedRealtimeNanos() < due) Thread.sleep(1)
            renderer.renderPcm(ByteArray(352 * 4), 700)
        }
    }

    @Test
    fun steadyMusicStaysNearTheLead() {
        val start = SystemClock.elapsedRealtime()
        val samples = mutableListOf<String>()
        val sampler = Thread {
            while (SystemClock.elapsedRealtime() - start < 6000) {
                samples += "${SystemClock.elapsedRealtime() - start}:${renderer.bufferedMusicMs()}"
                Thread.sleep(500)
            }
        }.apply { start() }
        send(6.0)
        sampler.join()
        android.util.Log.i("AudioRendererMusicTest", "steady: $samples")
        val last = renderer.bufferedMusicMs()
        assertTrue("delay $last ms after 6 s", last < AudioRenderer.MUSIC_LEAD_MS + 300)
    }

    @Test
    fun pausingFromTheTvDoesNotAddDelay() {
        send(3.0)
        val playing = renderer.bufferedMusicMs()
        repeat(3) { round ->
            renderer.holdMusic(true)
            send(0.8)             // the sender still stopping
            Thread.sleep(1500)    // paused
            renderer.holdMusic(false)
            send(2.0)
            val delay = renderer.bufferedMusicMs()
            android.util.Log.i("AudioRendererMusicTest", "playing $playing ms, after pause ${round + 1}: $delay ms")
            assertTrue("delay after pause ${round + 1} is $delay ms (was $playing ms)", delay < playing + 250)
        }
    }
}
