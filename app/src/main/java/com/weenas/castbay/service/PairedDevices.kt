package com.weenas.castbay.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A sender that entered the TV's PIN, identified by its pairing [publicKey] (base64). */
data class PairedDevice(val publicKey: String, val deviceId: String, val name: String)

/**
 * Senders paired by PIN (PIN pairing): they connect again without it. Only these may skip the
 * PIN; the protocol core refuses any other client that claims to be paired.
 */
class PairedDevices(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("paired_devices", Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<PairedDevice> = try {
        val array = JSONArray(preferences.getString(KEY, "[]"))
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            PairedDevice(item.getString("pk"), item.optString("id"), item.optString("name"))
        }
    } catch (e: org.json.JSONException) {
        emptyList()
    }

    /** Keeps [device]; a device that paired again (a new key under the same ID) replaces its old entry. */
    @Synchronized
    fun add(device: PairedDevice) {
        val devices = load().filterNot {
            it.publicKey == device.publicKey || (device.deviceId.isNotEmpty() && it.deviceId == device.deviceId)
        } + device
        val array = JSONArray()
        devices.forEach {
            array.put(JSONObject().put("pk", it.publicKey).put("id", it.deviceId).put("name", it.name))
        }
        preferences.edit().putString(KEY, array.toString()).apply()
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "devices"
    }
}
