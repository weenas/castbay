package com.weenas.castbay.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.weenas.castbay.R
import com.weenas.castbay.service.MirroringProfile
import com.weenas.castbay.service.ReceiverSettings

/**
 * The on-screen name of a stored setting value. Settings keep their English values (so saved
 * settings survive a language change); only what is shown is translated.
 */
@Composable
fun settingValueLabel(value: String): String = when (value) {
    ReceiverSettings.RESOLUTION_AUTO, ReceiverSettings.FRAME_RATE_AUTO, ReceiverSettings.CODEC_AUTO -> stringResource(R.string.auto)
    ReceiverSettings.CODEC_H264_ONLY -> stringResource(R.string.setting_codec_h264_only)
    ReceiverSettings.PICTURE_FIT -> stringResource(R.string.picture_fit)
    ReceiverSettings.PICTURE_FILL -> stringResource(R.string.picture_fill)
    ReceiverSettings.PICTURE_STRETCH -> stringResource(R.string.picture_stretch)
    else -> value.removeSuffix(" FPS").toIntOrNull()?.let { stringResource(R.string.frame_rate_fps, it) } ?: value
}

/** E.g. "H.265 · 2160p" or "H.264 · 1080p", as in Settings (2160p rather than 4K, like 1080p). */
fun mirroringLabel(profile: MirroringProfile): String = "${profile.codec} · ${profile.height}p"
