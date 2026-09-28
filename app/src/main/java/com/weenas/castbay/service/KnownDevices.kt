package com.weenas.castbay.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A device that has cast to (or asked to cast to) the TV, by its AirPlay [deviceId]. */
data class KnownDevice(val deviceId: String, val name: String, val model: String, val allowed: Boolean)

/**
 * Devices that have cast here, each allowed or blocked. Blocked devices are always refused;
 * while new devices need approval (ReceiverSettings.ACCESS_CONFIRM), only allowed ones are
 * admitted.
 */
class KnownDevices(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("known_devices", Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<KnownDevice> = try {
        val array = JSONArray(preferences.getString(KEY, "[]"))
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            KnownDevice(item.getString("id"), item.optString("name"), item.optString("model"), item.optBoolean("allowed", true))
        }
    } catch (e: org.json.JSONException) {
        emptyList()
    }

    fun find(deviceId: String): KnownDevice? = load().firstOrNull { it.deviceId == deviceId }

    /** Adds or updates [device] (its latest name, and whether it is allowed). */
    @Synchronized
    fun put(device: KnownDevice) {
        save(load().filterNot { it.deviceId == device.deviceId } + device)
    }

    @Synchronized
    fun remove(deviceId: String) {
        save(load().filterNot { it.deviceId == deviceId })
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY).apply()
    }

    private fun save(devices: List<KnownDevice>) {
        val array = JSONArray()
        devices.forEach {
            array.put(JSONObject().put("id", it.deviceId).put("name", it.name).put("model", it.model).put("allowed", it.allowed))
        }
        preferences.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "devices"
    }
}
