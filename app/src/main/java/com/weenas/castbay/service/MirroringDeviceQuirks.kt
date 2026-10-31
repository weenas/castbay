package com.weenas.castbay.service

/** Scope this workaround to the tested TV and Android version. */
object MirroringDeviceQuirks {
    fun needsTextureView(manufacturer: String, model: String, sdk: Int): Boolean =
        manufacturer.equals("Panasonic", ignoreCase = true) &&
            model.equals("4KTV-JUP", ignoreCase = true) && sdk == 30
}
