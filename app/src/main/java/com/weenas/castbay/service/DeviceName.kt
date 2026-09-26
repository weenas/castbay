package com.weenas.castbay.service

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * The name senders list this TV under: the chosen name followed by the TV's own device name (or
 * model), e.g. "CastBay (Living Room TV)", so several TVs on one network tell apart.
 */
object DeviceName {
    const val BRAND = "CastBay"

    /** mDNS names are at most 63 bytes; a little under, as the advertiser allows. */
    private const val MAX_BYTES = 60

    /** The TV's device name in its Settings, else its model; "" if neither is usable. */
    fun tvName(context: Context): String {
        val system = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()
        return tvName(system, Build.MODEL)
    }

    internal fun tvName(system: String?, model: String?): String =
        listOf(system, model)
            // Some makers' names read like identifiers ("TCL_Android_TV").
            .map { it?.replace('_', ' ')?.trim().orEmpty() }
            .firstOrNull { it.isNotEmpty() && !it.equals(BRAND, ignoreCase = true) }
            .orEmpty()

    /** [name] with "([tvName])" after it, unless [tvName] is empty or already in it. */
    fun compose(name: String, tvName: String): String {
        val base = name.trim().ifEmpty { BRAND }
        if (tvName.isEmpty() || base.contains(tvName, ignoreCase = true)) return fit(base)
        var tv = tvName
        while ("$base ($tv)".toByteArray().size > MAX_BYTES && tv.isNotEmpty()) tv = tv.dropLast(1).trimEnd()
        return if (tv.isEmpty()) fit(base) else "$base ($tv)"
    }

    private fun fit(name: String): String {
        var fitted = name
        while (fitted.toByteArray().size > MAX_BYTES) fitted = fitted.dropLast(1)
        return fitted.trimEnd()
    }
}
