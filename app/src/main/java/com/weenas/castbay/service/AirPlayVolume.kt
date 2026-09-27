package com.weenas.castbay.service

import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow

/**
 * Converts the sender's volume to a linear gain for AudioTrack and ExoPlayer.
 *
 * AirPlay sends the volume slider as -30 dB (bottom) to 0 dB (top), and -144 dB for mute.
 * As in UxPlay's "taper" option (shairport-sync's scheme), each halving of the slider costs
 * 10 dB, so the slider feels even; the plain dB value is used where it is louder, near the
 * bottom of the range.
 */
object AirPlayVolume {
    const val MIN_DB = -30f
    const val MUTE_DB = -144f

    /** Where the sender's slider is, 0 to 1 (0 when muted), for the TV's volume display. */
    fun toSlider(db: Float): Float = ((db - MIN_DB) / -MIN_DB).coerceIn(0f, 1f)

    fun toGain(db: Float): Float {
        if (db <= MIN_DB) return 0f  // includes mute
        if (db >= 0f) return 1f
        val slider = (db - MIN_DB) / -MIN_DB
        val tapered = 10.0 * log2(slider.toDouble())
        val gainDb = max(tapered, db.toDouble())
        return 10.0.pow(gainDb / 20.0).toFloat()
    }
}
