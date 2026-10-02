package com.weenas.castbay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.weenas.castbay.util.Diagnostics
import com.weenas.castbay.util.Log
import androidx.core.content.ContextCompat
import com.weenas.castbay.service.AirPlayService
import com.weenas.castbay.service.ReceiverSettingsStore

/** Starts the receiver when the TV boots, so it is ready like an Apple TV. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Diagnostics.init(context)
        Diagnostics.record("boot", "Broadcast ${intent.action}")
        if (intent.action !in BOOT_ACTIONS) return
        // A debug build beside the release app waits to be opened for a test.
        if (com.weenas.castbay.BuildConfig.DEBUG) {
            Diagnostics.record("boot", "Debug build: not started at boot")
            return
        }
        Log.i(TAG, "Starting AirPlay receiver after boot (${intent.action})")
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AirPlayService::class.java).setAction(AirPlayService.ACTION_START)
            )
        } catch (error: IllegalStateException) {
            // Android 15+ no longer lets BOOT_COMPLETED start mediaPlayback foreground services
            // (ForegroundServiceStartNotAllowedException); the user starts it from the app.
            Log.w(TAG, "Not allowed to start the receiver at boot", error)
            Diagnostics.record("boot", "Not allowed to start the receiver: ${error.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "CastBayBoot"
        val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            // Fast-boot variants some TV and phone vendors send instead.
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
    }
}
