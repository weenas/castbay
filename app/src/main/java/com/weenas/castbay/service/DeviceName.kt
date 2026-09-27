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

    /**
     * The TV's name as its owner set it: the device name in its Settings, or, where the maker
     * leaves that as a product code (TCL: "tcl_m7642") and keeps the user's name as the
     * Bluetooth name ("卧室电视TCL"), that; else the model. "" if nothing is usable.
     */
    fun tvName(context: Context): String {
        val resolver = context.contentResolver
        val system = runCatching { Settings.Global.getString(resolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        // Not a public setting; some Android versions refuse it, hence runCatching.
        val bluetooth = runCatching { Settings.Secure.getString(resolver, "bluetooth_name") }.getOrNull()
        return tvName(system, bluetooth, Build.MODEL, listOf(Build.MODEL, Build.DEVICE, Build.PRODUCT))
    }

    internal fun tvName(system: String?, bluetooth: String?, model: String?, productCodes: List<String?>): String {
        val codes = productCodes.mapNotNull { it?.trim()?.lowercase() }.toSet()
        fun isCode(name: String) = name.lowercase() in codes || PRODUCT_CODE.matches(name)
        val system = system?.trim().orEmpty()
        return listOf(system.takeUnless { isCode(it) }, bluetooth, system, model)
            // Some makers' names read like identifiers ("TCL_Android_TV").
            .map { it?.replace('_', ' ')?.trim().orEmpty() }
            .firstOrNull { it.isNotEmpty() && !it.equals(BRAND, ignoreCase = true) }
            .orEmpty()
    }

    /** Lowercase letters and digits joined by underscores, e.g. "tcl_m7642": a code, not a name. */
    private val PRODUCT_CODE = Regex("[a-z0-9]+(_[a-z0-9]+)+")

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
