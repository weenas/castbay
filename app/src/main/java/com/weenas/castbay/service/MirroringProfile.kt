package com.weenas.castbay.service

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

/** The TV's HEVC (H.265) decoding ability, as far as mirroring needs it. */
data class HevcSupport(
    /** A hardware HEVC decoder exists; software ones can't keep up with live mirroring. */
    val hardware: Boolean,
    /** That decoder handles 3840x2160 at 30 fps. */
    val uhd: Boolean,
    /** The hardware HEVC decoders found, for the log. */
    val decoders: List<String> = emptyList()
) {
    companion object {
        val NONE = HevcSupport(hardware = false, uhd = false)

        /** Queries the platform decoders once; cheap enough to call at receiver start. */
        fun detect(): HevcSupport {
            val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true) } &&
                    isHardware(info)
            }
            if (decoders.isEmpty()) return NONE
            val uhd = decoders.any { info ->
                runCatching {
                    info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC)
                        .videoCapabilities?.areSizeAndRateSupported(3840, 2160, 30.0) == true
                }.getOrDefault(false)
            }
            return HevcSupport(hardware = true, uhd = uhd, decoders = decoders.map { it.name })
        }

        private fun isHardware(info: MediaCodecInfo): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isHardwareAccelerated
            } else {
                // Before API 29 there is no flag; Android's own software codecs have these prefixes.
                !info.name.startsWith("OMX.google.", ignoreCase = true) &&
                    !info.name.startsWith("c2.android.", ignoreCase = true)
            }
    }
}

/**
 * What the receiver offers senders for screen mirroring: the codec and the display size they
 * encode to. Senders only switch to H.265 for displays taller than 1080p (see UxPlay's -h265
 * notes), so it is offered only together with a 4K display. [note] says why the size is capped.
 */
data class MirroringProfile(
    val h265: Boolean,
    val width: Int,
    val height: Int,
    val cap: Cap? = null,
    /** The panel's height in lines, for [Cap.SCREEN]. */
    val panelLines: Int = 0
) {
    /** Why mirroring isn't offered in 4K. */
    enum class Cap { SCREEN, DECODER, NEEDS_H265, NO_HW_DECODER }

    val codec: String get() = if (h265) "H.265" else "H.264"
    val upTo4k: Boolean get() = height >= UHD_HEIGHT

    /** [cap] in English, e.g. "1080p screen"; the UI shows a translated one. */
    val note: String?
        get() = when (cap) {
            Cap.SCREEN -> "${panelLines}p screen"
            Cap.DECODER -> "decoder can't do 4K"
            Cap.NEEDS_H265 -> "4K needs H.265"
            Cap.NO_HW_DECODER -> "no hardware H.265 decoder for 4K"
            null -> null
        }

    /** For logs and tests, e.g. "H.265 · up to 4K" or "H.264 · 1080p (1080p screen)". */
    val label: String
        get() {
            val size = if (upTo4k) "up to 4K" else "${height}p"
            return "$codec · $size" + (note?.let { " ($it)" } ?: "")
        }

    companion object {
        private const val UHD_HEIGHT = 2160
        private const val FHD_HEIGHT = 1080

        /** [panelWidth]/[panelHeight]: the panel's largest mode, not the (often 1080p) UI mode. */
        fun of(settings: ReceiverSettings, hevc: HevcSupport, panelWidth: Int, panelHeight: Int): MirroringProfile {
            val hevcAllowed = settings.videoCodec == ReceiverSettings.CODEC_AUTO && hevc.hardware
            val (width, height) = settings.displaySize(panelWidth, panelHeight, allowUhd = hevcAllowed && hevc.uhd)
            // At 1080p senders send H.264 anyway, so offering H.265 there would change nothing.
            val h265 = hevcAllowed && hevc.uhd && height > FHD_HEIGHT
            val panel = minOf(panelWidth, panelHeight)
            val cap = when {
                settings.resolution != ReceiverSettings.RESOLUTION_AUTO || height >= UHD_HEIGHT -> null
                panel <= 0 -> null
                panel < UHD_HEIGHT -> Cap.SCREEN
                hevcAllowed -> Cap.DECODER
                settings.videoCodec == ReceiverSettings.CODEC_H264_ONLY -> Cap.NEEDS_H265
                else -> Cap.NO_HW_DECODER
            }
            return MirroringProfile(h265, width, height, cap, panel)
        }
    }
}
