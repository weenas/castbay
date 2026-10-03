package com.weenas.castbay.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.os.Build
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.weenas.castbay.util.Diagnostics
import com.weenas.castbay.util.Log

/**
 * Holds Android's audio focus while something is cast, as any media player does. Other
 * players (a car's radio, a TV's own apps) then pause, and the system sends media keys (a
 * car's steering-wheel buttons, a remote's) to CastBay's media session rather than to them.
 * [onLost] is called when another app takes the focus for good, e.g. the person started
 * playing something else.
 */
class AudioFocus(context: Context, private val onLost: () -> Unit) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var held = false

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.i(TAG, "Audio focus lost to another app")
                Diagnostics.record("focus", "Lost to another app")
                held = false
                onLost()
            }
            AudioManager.AUDIOFOCUS_GAIN -> held = true
        }
    }

    // Android 8+; before, the same through the older calls (which use the main thread).
    private val request = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            // Navigation prompts and the like: the system lowers CastBay's volume meanwhile.
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(listener, main)
            .build()
    } else null

    /** Takes the focus if CastBay doesn't hold it. Any thread. */
    fun acquire() {
        if (held) return
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) audioManager?.requestAudioFocus(request!!)
            else @Suppress("DEPRECATION") audioManager?.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.i(TAG, if (held) "Audio focus gained" else "Audio focus refused")
        Diagnostics.record("focus", if (held) "Gained" else "Refused")
    }

    /** Gives the focus back, e.g. when the cast ends. Any thread. */
    fun release() {
        if (!held) return
        held = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) audioManager?.abandonAudioFocusRequest(request!!)
        else @Suppress("DEPRECATION") audioManager?.abandonAudioFocus(listener)
        Log.i(TAG, "Audio focus released")
        Diagnostics.record("focus", "Released")
    }

    private companion object {
        const val TAG = "CastBayAudioFocus"
    }
}
