package com.weenas.castbay.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.weenas.castbay.util.Log

/**
 * Takes commands for the [SimulatedSender] from adb:
 *
 *     adb shell am broadcast -a com.weenas.castbay.SIMULATE -p com.weenas.castbay.debug \
 *         --es cmd mirror [--ei seconds 30] [--ei width 1920 --ei height 1080 --ei fps 30]
 *
 * Commands: mirror, music, pause, resume, video (--es url ...), stop. Every one takes
 * --es name / --es model / --es device for the sender, and seconds (0 = until stopped).
 * tools/sim wraps these.
 */
class SimulatorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return
        Log.i(SimulatedSender.TAG, "Command: $cmd ${intent.extras?.keySet()?.joinToString { "$it=${intent.extras?.get(it)}" }}")
        val sim = SimulatedSender.get(context)
        val sender = SimulatedSender.Sender(
            deviceId = intent.getStringExtra("device") ?: "5A:1E:00:00:00:01",
            name = intent.getStringExtra("name") ?: "Simulated iPhone",
            model = intent.getStringExtra("model") ?: "iPhone16,2"
        )
        val seconds = intent.getIntExtra("seconds", 0)
        when (cmd) {
            "mirror" -> sim.mirror(
                sender, seconds,
                width = intent.getIntExtra("width", 1920),
                height = intent.getIntExtra("height", 1080),
                fps = intent.getIntExtra("fps", 30)
            )
            "music" -> sim.music(
                sender, if (seconds > 0) seconds else 180,
                title = intent.getStringExtra("title") ?: "Test Tune",
                artist = intent.getStringExtra("artist") ?: "CastBay Simulator",
                album = intent.getStringExtra("album") ?: "Test Signals"
            )
            "pause" -> sim.pauseMusic()
            "resume" -> sim.resumeMusic()
            "video" -> sim.video(
                sender,
                intent.getStringExtra("url") ?: SimulatedSender.SAMPLE_VIDEO
            )
            "stop" -> sim.stop()
            // Usage statistics: what is kept, then send it now, today's too.
            "stats" -> {
                val app = context.applicationContext
                Log.i(SimulatedSender.TAG, "Statistics: ${com.weenas.castbay.util.UsageStats.describe(app)}")
                kotlin.concurrent.thread { com.weenas.castbay.util.UsageStats.sendFinishedDays(app, includeToday = true) }
            }
            // The music screen saver after SECONDS rather than minutes (no value: back to normal).
            "saver" -> {
                val seconds = intent.getIntExtra("seconds", 0)
                com.weenas.castbay.ui.screen.ScreenSaverTiming.testDelayMs = if (seconds > 0) seconds * 1000L else null
                Log.i(SimulatedSender.TAG, "Screen saver delay: ${if (seconds > 0) "${seconds}s" else "normal"}")
            }
            // For error reports: an uncaught exception on the main thread, as a real bug would.
            "crash" -> android.os.Handler(android.os.Looper.getMainLooper()).post {
                throw IllegalStateException("Simulated crash (tools/sim crash)")
            }
            else -> Log.w(SimulatedSender.TAG, "Unknown command: $cmd")
        }
    }
}
