package com.weenas.castbay.service

import android.content.Context

data class ReceiverSettings(
    /** The name the user chose; senders see [advertisedName]. */
    val deviceName: String = DeviceName.BRAND,
    /** Add the TV's own name after [deviceName], so several TVs on one network tell apart. */
    val appendTvName: Boolean = true,
    /** The TV's device name (or model), read from the system on load; not stored. */
    val tvName: String = "",
    val resolution: String = RESOLUTION_AUTO,
    val frameRate: String = FRAME_RATE_AUTO,
    /** "Auto" offers H.265 when the TV decodes it in hardware; otherwise H.264 only. */
    val videoCodec: String = CODEC_AUTO,
    /** Whether senders must enter [pin]; otherwise anyone on the network can cast. */
    val requirePassword: Boolean = false,
    /** The password (digits) used when [requirePassword] is on; kept when it is turned off. */
    val pin: String = "",
    /**
     * What happens when another device casts while one is connected: true = it takes over
     * (the current one is disconnected), false = it is refused.
     */
    val allowTakeover: Boolean = false,
    /** Also act as a DLNA renderer, for video apps' own "cast" buttons (Bilibili, iQiyi, ...). */
    val dlnaEnabled: Boolean = true,
    /** Show a "stats for nerds" overlay (codec, resolution, bitrate, ...) while playing. */
    val showStats: Boolean = false,
    /** How mirroring and AirPlay video fill the screen: [PICTURE_FIT], [PICTURE_FILL] or [PICTURE_STRETCH]. */
    val pictureMode: String = PICTURE_FIT,
    /** Look up and show synced lyrics for AirPlay music (sends the song's title to lrclib.net). */
    val showLyrics: Boolean = false
) {
    /**
     * The display size advertised to senders, which they size mirroring to. "Auto" is the
     * TV's own display, capped at 1080p unless [allowUhd] (H.265 with a 4K-capable decoder):
     * H.264 mirroring gains nothing beyond 1080p.
     */
    fun displaySize(displayWidth: Int, displayHeight: Int, allowUhd: Boolean = false): Pair<Int, Int> =
        when (resolution) {
            "720p" -> 1280 to 720
            "1080p" -> 1920 to 1080
            else -> {
                val landscapeWidth = maxOf(displayWidth, displayHeight)
                val landscapeHeight = minOf(displayWidth, displayHeight)
                when {
                    landscapeWidth <= 0 || landscapeHeight <= 0 -> 1920 to 1080
                    landscapeHeight >= 2160 && allowUhd -> 3840 to 2160
                    landscapeHeight > 1080 -> 1920 to 1080
                    else -> landscapeWidth to landscapeHeight
                }
            }
        }

    /**
     * Whether switching from [previous] needs a running receiver restarted: connection settings
     * do (senders must reconnect); playback settings apply live and are also in the quick menu.
     */
    /** The name senders list this TV under. */
    val advertisedName: String
        get() = DeviceName.compose(deviceName, if (appendTvName) tvName else "")

    fun needsRestartComparedTo(previous: ReceiverSettings): Boolean =
        withoutLiveSettings() != previous.withoutLiveSettings()

    private fun withoutLiveSettings() = copy(showStats = false, pictureMode = PICTURE_FIT, showLyrics = false)

    /** Frames per second senders may mirror at. "Auto" is 60: TVs decode in hardware. */
    fun maxFps(): Int = if (frameRate == "30 FPS") 30 else 60

    /** The client-access password to enforce, or "" when access is open. */
    fun requiredPin(): String = pin.takeIf { requirePassword && isValidPin(it) }.orEmpty()

    companion object {
        const val RESOLUTION_AUTO = "Auto"
        const val FRAME_RATE_AUTO = "Auto"
        val RESOLUTIONS = listOf(RESOLUTION_AUTO, "720p", "1080p")
        /**
         * Auto is 60, the most senders mirror at, so there is no separate 60 FPS; a stored
         * "60 FPS" from older versions loads as Auto, the same.
         */
        val FRAME_RATES = listOf(FRAME_RATE_AUTO, "30 FPS")
        const val PICTURE_FIT = "Fit"
        const val PICTURE_FILL = "Fill"
        const val PICTURE_STRETCH = "Stretch"
        val PICTURE_MODES = listOf(PICTURE_FIT, PICTURE_FILL, PICTURE_STRETCH)
        const val CODEC_AUTO = "Auto"
        const val CODEC_H264_ONLY = "H.264 only"
        val VIDEO_CODECS = listOf(CODEC_AUTO, CODEC_H264_ONLY)

        /** UxPlay requires client-access passwords of at least 4 characters. */
        const val MIN_PIN_LENGTH = 4

        fun isValidPin(pin: String) = pin.length >= MIN_PIN_LENGTH && pin.all { it.isDigit() }
    }
}

class ReceiverSettingsStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("receiver_settings", Context.MODE_PRIVATE)

    private val tvName = DeviceName.tvName(context.applicationContext)

    fun load() = ReceiverSettings(
        deviceName = preferences.getString("device_name", DeviceName.BRAND).orEmpty().ifBlank { DeviceName.BRAND },
        appendTvName = preferences.getBoolean("append_tv_name", true),
        tvName = tvName,
        // Values from older versions (e.g. "4K") fall back to Auto.
        resolution = preferences.getString("resolution", null)
            ?.takeIf { it in ReceiverSettings.RESOLUTIONS } ?: ReceiverSettings.RESOLUTION_AUTO,
        frameRate = preferences.getString("frame_rate", null)
            ?.takeIf { it in ReceiverSettings.FRAME_RATES } ?: ReceiverSettings.FRAME_RATE_AUTO,
        videoCodec = preferences.getString("video_codec", null)
            ?.takeIf { it in ReceiverSettings.VIDEO_CODECS } ?: ReceiverSettings.CODEC_AUTO,
        pin = preferences.getString("pin", "").orEmpty(),
        // Before this setting existed, a saved PIN meant "required".
        requirePassword = preferences.getBoolean(
            "require_password",
            ReceiverSettings.isValidPin(preferences.getString("pin", "").orEmpty())
        ),
        allowTakeover = preferences.getBoolean("allow_takeover", false),
        dlnaEnabled = preferences.getBoolean("dlna_enabled", true),
        showStats = preferences.getBoolean("show_stats", false),
        pictureMode = preferences.getString("picture_mode", null)
            ?.takeIf { it in ReceiverSettings.PICTURE_MODES } ?: ReceiverSettings.PICTURE_FIT,
        showLyrics = preferences.getBoolean("show_lyrics", false)
    )

    fun save(settings: ReceiverSettings) {
        preferences.edit()
            .putString("device_name", settings.deviceName.trim().ifBlank { DeviceName.BRAND })
            .putBoolean("append_tv_name", settings.appendTvName)
            .putString("resolution", settings.resolution)
            .putString("frame_rate", settings.frameRate)
            .putString("video_codec", settings.videoCodec)
            .putString("pin", settings.pin)
            .putBoolean("require_password", settings.requirePassword)
            .putBoolean("allow_takeover", settings.allowTakeover)
            .putBoolean("dlna_enabled", settings.dlnaEnabled)
            // Receiving can no longer be turned off (Back twice on the home screen quits).
            .remove("start_on_boot")
            .putBoolean("show_stats", settings.showStats)
            .putString("picture_mode", settings.pictureMode)
            .putBoolean("show_lyrics", settings.showLyrics)
            .remove("audio_latency")
            .apply()
    }
}
