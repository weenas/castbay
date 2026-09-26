package com.weenas.castbay.service

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * The name senders list this TV under until the user picks one: "CastBay (Living Room TV)",
 * from the TV's own device name (or model), so several TVs on one network tell apart.
 */
object DeviceName {
    const val BRAND = "CastBay"

    /** mDNS names are at most 63 bytes; a little under, as the advertiser allows. */
    private const val MAX_BYTES = 60

    fun default(context: Context): String {
        val system = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()
        return compose(system, Build.MODEL)
    }

    /** [tvName]: the TV's device name in its Settings; [model] when there is none. */
    internal fun compose(tvName: String?, model: String?): String {
        val tv = listOf(tvName, model)
            // Some makers' names read like identifiers ("TCL_Android_TV").
            .map { it?.replace('_', ' ')?.trim().orEmpty() }
            .firstOrNull { it.isNotEmpty() && !it.equals(BRAND, ignoreCase = true) }
            ?: return BRAND
        var name = "$BRAND ($tv)"
        var shortened = tv
        while (name.toByteArray().size > MAX_BYTES && shortened.isNotEmpty()) {
            shortened = shortened.dropLast(1).trimEnd()
            name = "$BRAND ($shortened)"
        }
        return if (shortened.isEmpty()) BRAND else name
    }
}
