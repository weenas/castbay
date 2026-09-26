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

/**
 * E.g. "H.265 · up to 4K" or "H.264 · 1080p (1080p screen)", in the TV's language; without
 * [withReason], just "H.264 · 1080p" (the home screen's info panel, where it must fit a line).
 */
@Composable
fun mirroringLabel(profile: MirroringProfile, withReason: Boolean = true): String {
    val size = if (profile.upTo4k) stringResource(R.string.mirroring_up_to_4k) else "${profile.height}p"
    val note = when (profile.cap) {
        MirroringProfile.Cap.SCREEN -> stringResource(R.string.mirroring_note_screen, profile.panelLines)
        MirroringProfile.Cap.DECODER -> stringResource(R.string.mirroring_note_decoder)
        MirroringProfile.Cap.NEEDS_H265 -> stringResource(R.string.mirroring_note_needs_h265)
        MirroringProfile.Cap.NO_HW_DECODER -> stringResource(R.string.mirroring_note_no_hw_decoder)
        null -> null
    }
    return "${profile.codec} · $size" + (note?.takeIf { withReason }?.let { " ($it)" } ?: "")
}
