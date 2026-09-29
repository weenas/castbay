package com.weenas.castbay.service

import android.content.Context
import com.weenas.castbay.util.Log
import com.weenas.castbay.protocol.AirPlayNative
import com.weenas.castbay.protocol.AudioSink
import com.weenas.castbay.protocol.VideoPlaybackListener
import com.weenas.castbay.protocol.VideoSink

/** Now-playing callbacks for audio streaming; called on protocol threads. */
interface AudioInfoListener {
    fun onMetadata(dmap: ByteArray)
    fun onCoverArt(image: ByteArray)
    fun onProgress(positionSec: Double, durationSec: Double)
}

/**
 * Connects the protocol library to the app. Its callbacks are internal so debug builds'
 * simulated sender can drive the app exactly as the protocol does.
 */
class NativeBridge(
    internal val onConnectionStarted: () -> Unit,
    internal val onVideoData: (ByteArray, Long, Boolean) -> Unit,
    internal val onAudioData: (ByteArray, Long) -> Unit,
    internal val onPcmData: (ByteArray, Long, Int) -> Unit,
    internal val onAudioFlush: () -> Unit,
    internal val onVolume: (Float) -> Unit,
    internal val audioInfo: AudioInfoListener,
    internal val videoPlayback: VideoPlaybackListener,
    internal val onRemoteControl: (dacpId: String, activeRemote: String) -> Unit,
    /** Whether to admit a sender setting up a session; see [AirPlayNative.clientListener]. */
    internal val onClient: (deviceId: String, name: String, model: String) -> Boolean,
    internal val onFeedback: () -> Unit,
    internal val onPin: (pin: String) -> Unit,
    internal val onSenderReport: (SenderReport) -> Unit,
    internal val onPaired: (PairedDevice) -> Unit,
    internal val onSessionEnd: () -> Unit
) {
    companion object {
        private const val TAG = "NativeBridge"
        private const val KEY_AIRPLAY_PORT = "airplay"
        /** Apple TVs listen on 7000; used when free. */
        private const val AIRPLAY_DEFAULT_PORT = 7000
        val isAvailable: Boolean

        init {
            isAvailable = try {
                Class.forName("com.weenas.castbay.protocol.AirPlayNative")
                Log.d(TAG, "AirPlay protocol library loaded successfully")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Native library not available", e)
                false
            }
        }
    }

    private var keyFile: String? = null
    private var ports: android.content.SharedPreferences? = null
    private var language = "en"

    /**
     * Makes a new pairing identity (the key file is created afresh at the next start): senders
     * paired with the old one pair again with a PIN instead of failing to connect.
     */
    fun resetIdentity() {
        keyFile?.let { java.io.File(it).delete() }
    }

    fun initialize(context: Context) {
        Log.d(TAG, "Initializing native bridge")
        keyFile = java.io.File(context.noBackupFilesDir, "airplay_pairing_key.pem").absolutePath
        ports = context.getSharedPreferences("receiver_ports", Context.MODE_PRIVATE)
        language = java.util.Locale.getDefault().toLanguageTag()
        if (!isAvailable) return
        AirPlayNative.connectionListener = onConnectionStarted
        AirPlayNative.videoPlaybackListener = videoPlayback
        AirPlayNative.remoteControlListener = onRemoteControl
        AirPlayNative.clientListener = onClient
        AirPlayNative.feedbackListener = onFeedback
        AirPlayNative.pinListener = onPin
        AirPlayNative.senderReportListener = { values -> SenderReport.parse(values)?.let(onSenderReport) }
        AirPlayNative.pairedListener = { key, id, name -> onPaired(PairedDevice(key, id, name)) }
        AirPlayNative.setVideoSink(object : VideoSink {
            override fun onVideoData(data: ByteArray, presentationTimeUs: Long, isH265: Boolean) {
                // Qualified: an unqualified call resolves to this override and recurses.
                this@NativeBridge.onVideoData(data, presentationTimeUs, isH265)
            }

            override fun onSessionEnd() {
                this@NativeBridge.onSessionEnd.invoke()
            }
        })
        AirPlayNative.setAudioSink(object : AudioSink {
            override fun onAudioData(data: ByteArray, presentationTimeUs: Long) {
                this@NativeBridge.onAudioData(data, presentationTimeUs)
            }

            override fun onPcmData(data: ByteArray, playAtUs: Long, compressedBytes: Int) {
                this@NativeBridge.onPcmData(data, playAtUs, compressedBytes)
            }

            override fun onAudioFlush() {
                this@NativeBridge.onAudioFlush.invoke()
            }

            override fun onVolume(db: Float) {
                this@NativeBridge.onVolume.invoke(db)
            }

            override fun onMetadata(dmap: ByteArray) = audioInfo.onMetadata(dmap)
            override fun onCoverArt(image: ByteArray) = audioInfo.onCoverArt(image)
            override fun onProgress(positionSec: Double, durationSec: Double) =
                audioInfo.onProgress(positionSec, durationSec)
        })
    }

    fun start(
        deviceName: String,
        hardwareAddress: ByteArray,
        profile: MirroringProfile,
        maxFps: Int,
        password: String,
        usePin: Boolean,
        pairedKeys: List<String>,
        allowTakeover: Boolean
    ): Int {
        val key = keyFile ?: return 0
        if (!isAvailable) return 0
        return try {
            // AirPlay's usual port first, then whichever port worked last time.
            val preferred = ports?.getInt(KEY_AIRPLAY_PORT, AIRPLAY_DEFAULT_PORT) ?: AIRPLAY_DEFAULT_PORT
            AirPlayNative.start(
                deviceName, hardwareAddress, key, language,
                profile.width, profile.height, maxFps, password, usePin, pairedKeys.toTypedArray(),
                allowTakeover, profile.h265, preferred
            ).also { port ->
                if (port > 0 && port != preferred) ports?.edit()?.putInt(KEY_AIRPLAY_PORT, port)?.apply()
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native start method is unavailable", e)
            0
        }
    }

    /** TXT records the protocol core expects senders to see, or null if the library is missing. */
    fun discoveryRecords(): DiscoveryRecords? {
        if (!isAvailable) return null
        return try {
            DiscoveryRecords(
                airplay = AirPlayNative.airPlayTxtRecord(),
                raop = AirPlayNative.raopTxtRecord()
            )
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native TXT record methods are unavailable", e)
            null
        }
    }

    fun stop() {
        if (!isAvailable) return
        try {
            AirPlayNative.stop()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native stop method is unavailable", e)
        }
    }

    fun disconnect() {
        if (!isAvailable) return
        try {
            AirPlayNative.disconnect()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native disconnect method is unavailable", e)
        }
    }

}
