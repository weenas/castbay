package com.weenas.castbay.service

import com.weenas.castbay.util.Diagnostics
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.weenas.castbay.MainActivity

class AirPlayService : Service() {

    companion object {
        const val ACTION_START = "com.weenas.castbay.action.START_RECEIVER"
        const val ACTION_STOP = "com.weenas.castbay.action.STOP_RECEIVER"
        const val CHANNEL_ID = "castbay_channel"
        const val NOTIFICATION_ID = 1

        /** Whether the receiver is up, for [com.weenas.castbay.receiver.ReceiverStarter]. */
        @Volatile var isReceiving = false
            private set
    }

    private val manager by lazy { AirPlayManager.getInstance(this) }
    private var running = false
        set(value) {
            field = value
            isReceiving = value
        }
    private val stateCallback: (AirPlayConnectionState, StreamInfo, String?) -> Unit = { state, _, _ ->
        if (running) {
            when (state) {
                AirPlayConnectionState.AdvertisingOnly -> getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification(getString(com.weenas.castbay.R.string.notification_discoverable_only)))
                AirPlayConnectionState.Error -> {
                    running = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                else -> Unit
            }
        }
    }

    // Its notification in the language chosen in Settings.
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate() {
        super.onCreate()
        Diagnostics.init(this)
        Diagnostics.record("service", "Created")
        // Brings the receiver back once the device has a network again, e.g. a car waking up.
        com.weenas.castbay.receiver.WakeJobService.schedule(this)
        createNotificationChannel()
        manager.registerStateCallback(stateCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Diagnostics.record("service", "Start command ${intent?.action ?: "(restart)"}" + if (running) ", already running" else "")
        if (intent?.action == ACTION_STOP) {
            if (running) manager.stop()
            running = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY

        startForeground(NOTIFICATION_ID, buildNotification(getString(com.weenas.castbay.R.string.notification_starting)))
        val settings = ReceiverSettingsStore(this).load()
        if (!manager.start(settings)) {
            Diagnostics.record("service", "Receiver failed to start")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        running = true
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(
                getString(
                    if (manager.isDiscoveryOnly) com.weenas.castbay.R.string.notification_discovery_only
                    else com.weenas.castbay.R.string.notification_waiting
                )
            ))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Diagnostics.record("service", "Destroyed" + if (running) " while running" else "")
        if (running) manager.stop()
        manager.unregisterStateCallback(stateCallback)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(com.weenas.castbay.R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(com.weenas.castbay.R.string.app_name))
            .setContentText(message)
            .setSmallIcon(com.weenas.castbay.R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
