package com.weenas.castbay.service

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Local opt-in, short-lived pairing display state and Keystore-backed device credential. */
class RemoteManagementStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("remote_management", Context.MODE_PRIVATE)

    fun enabled() = prefs.getBoolean("enabled", false)
    fun setEnabled(value: Boolean) { prefs.edit().putBoolean("enabled", value).apply() }
    fun deviceId() = prefs.getString("device_id", null)
    fun deviceToken(): String? = prefs.getString("device_token", null)?.let(::decrypt)
    fun hasDeviceCredential() = !deviceToken().isNullOrBlank()

    fun saveDeviceCredential(deviceId: String, token: String) {
        prefs.edit().putString("device_id", deviceId).putString("device_token", encrypt(token)).apply()
    }

    fun clearDeviceCredential() {
        prefs.edit().remove("device_id").remove("device_token").remove("pair_code").remove("pair_expiry").apply()
    }

    fun setPairing(code: String, expiresAt: String) {
        prefs.edit().putString("pair_code", code).putString("pair_expiry", expiresAt).apply()
    }
    fun pairingCode() = prefs.getString("pair_code", null)
    fun pairingExpiresAt() = prefs.getString("pair_expiry", null)
    fun clearPairing() { prefs.edit().remove("pair_code").remove("pair_expiry").remove("pair_message").apply() }
    fun setPairingMessage(message: String) { prefs.edit().putString("pair_message", message).apply() }
    fun pairingMessage() = prefs.getString("pair_message", "").orEmpty()
    fun setState(state: String) { prefs.edit().putString("state", state).apply() }
    fun state() = prefs.getString("state", STATE_DISABLED) ?: STATE_DISABLED

    fun lastCommandSeq() = prefs.getLong("last_command_seq", 0L)
    fun lastCommandId() = prefs.getString("last_command_id", null)
    fun lastCommandStatus() = prefs.getString("last_command_status", "rejected") ?: "rejected"
    fun lastCommandMessage() = prefs.getString("last_command_message", "").orEmpty()
    fun saveCommandResult(seq: Long, id: String, status: String, message: String) {
        prefs.edit().putLong("last_command_seq", seq).putString("last_command_id", id)
            .putString("last_command_status", status).putString("last_command_message", message).apply()
    }
    fun resetCommandHistory() {
        prefs.edit().remove("last_command_seq").remove("last_command_id")
            .remove("last_command_status").remove("last_command_message").apply()
    }
    fun ensureCommandHistoryDevice(deviceId: String) {
        if (prefs.getString("command_history_device_id", null) == deviceId) return
        prefs.edit()
            .remove("last_command_seq")
            .remove("last_command_id")
            .remove("last_command_status")
            .remove("last_command_message")
            .putString("command_history_device_id", deviceId)
            .apply()
    }
    @Synchronized fun nextOutgoingSeq(): Long {
        val next = maxOf(System.currentTimeMillis(), prefs.getLong("outgoing_seq", 0L) + 1L)
        prefs.edit().putLong("outgoing_seq", next).apply()
        return next
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val combined = Base64.decode(value, Base64.NO_WRAP)
        require(combined.size > IV_BYTES)
        val iv = combined.copyOfRange(0, IV_BYTES)
        val ciphertext = combined.copyOfRange(IV_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    companion object {
        const val STATE_DISABLED = "disabled"
        const val STATE_READY_TO_PAIR = "ready"
        const val STATE_PAIRING = "pairing"
        const val STATE_CONNECTED = "connected"
        const val STATE_OFFLINE = "offline"
        const val STATE_ERROR = "error"
        private const val KEY_ALIAS = "castbay_remote_device_token"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
    }
}
