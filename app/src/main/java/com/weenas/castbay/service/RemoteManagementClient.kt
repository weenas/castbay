package com.weenas.castbay.service

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.weenas.castbay.util.AppVersion
import com.weenas.castbay.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.KeyStore
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Opt-in management control plane. Casting never depends on this client being reachable. */
class RemoteManagementClient(
    context: Context,
    private val manager: AirPlayManager,
    private val service: AirPlayService,
) {
    private val app = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val store = RemoteManagementStore(app)
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "CastBay-remote-management").apply { isDaemon = true }
    }
    @Volatile private var socket: WebSocket? = null
    @Volatile private var pollToken: String? = null
    @Volatile private var pairingId: String? = null
    @Volatile private var closed = false
    @Volatile private var retrySeconds = 2L
    @Volatile private var heartbeatTask: ScheduledFuture<*>? = null
    private val revocationHandled = AtomicBoolean(false)

    fun syncEnabled() {
        if (closed) return
        if (!store.enabled()) {
            socket?.close(1000, "disabled")
            socket = null
            clearPendingPairing()
            store.setState(RemoteManagementStore.STATE_DISABLED)
            return
        }
        if (store.deviceToken().isNullOrBlank()) {
            store.setState(RemoteManagementStore.STATE_READY_TO_PAIR)
            return
        }
        connect()
    }

    fun pair() {
        if (!store.enabled() || closed) return
        executor.execute {
            try {
                store.setPairingMessage("")
                store.setState(RemoteManagementStore.STATE_PAIRING)
                val body = JsonObject().apply {
                    addProperty("manufacturer", Build.MANUFACTURER.orEmpty().take(80).ifBlank { "Android" })
                    addProperty("model", Build.MODEL.orEmpty().take(100).ifBlank { "TV" })
                    addProperty("appVersion", AppVersion.name(app).take(40))
                }
                val response = post("/api/device/v1/pairing-sessions", body, null)
                val id = response.get("pairingId").asString
                val code = response.get("code").asString
                val token = response.get("pollToken").asString
                val expiresAt = response.get("expiresAt").asString
                pairingId = id
                pollToken = token
                store.setPairing(code, expiresAt)
                while (!closed && store.enabled() && pairingId == id) {
                    val request = Request.Builder().url("$BASE/api/device/v1/pairing-sessions/$id")
                        .header("Authorization", "Bearer $token").get().build()
                    http.newCall(request).execute().use { result ->
                        val json = readJson(result)
                        when (json.get("status")?.asString) {
                            "paired" -> {
                                val deviceToken = json.get("deviceToken").asString
                                val deviceId = json.get("deviceId").asString
                                store.saveDeviceCredential(deviceId, deviceToken)
                                store.ensureCommandHistoryDevice(deviceId)
                                revocationHandled.set(false)
                                post("/api/device/v1/pairing-sessions/$id/ack", JsonObject(), deviceToken)
                                pollToken = null
                                pairingId = null
                                store.clearPairing()
                                store.setState(RemoteManagementStore.STATE_CONNECTED)
                                connect()
                                return@execute
                            }
                            "expired", "completed" -> {
                                clearPendingPairing()
                                store.setState(RemoteManagementStore.STATE_READY_TO_PAIR)
                                store.setPairingMessage("expired")
                                return@execute
                            }
                        }
                    }
                    Thread.sleep(2500)
                }
            } catch (error: Exception) {
                if (!closed) {
                    store.setState(RemoteManagementStore.STATE_ERROR)
                    store.setPairingMessage("error")
                    Log.w(TAG, "Remote pairing failed: ${error.javaClass.simpleName}")
                }
            }
        }
    }

    private fun connect() {
        val token = store.deviceToken() ?: return
        if (!store.enabled() || closed || socket != null) return
        store.deviceId()?.let(store::ensureCommandHistoryDevice)
        val request = Request.Builder().url("$WS_BASE/device/v1/signal")
            .header("Authorization", "Bearer $token").build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                retrySeconds = 2
                store.setState(RemoteManagementStore.STATE_CONNECTED)
                sendHello(webSocket, "hello")
                sendStatus(webSocket)
                heartbeatTask?.cancel(false)
                heartbeatTask = executor.scheduleAtFixedRate({
                    if (socket === webSocket && store.enabled()) sendHello(webSocket, "heartbeat")
                }, 50, 50, TimeUnit.SECONDS)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { gson.fromJson(text, JsonObject::class.java) }
                    .onSuccess { data ->
                        when (data.get("type")?.asString) {
                            "command" -> handleCommand(webSocket, data)
                            "device_revoked" -> handleRevoked(webSocket)
                        }
                    }
                    .onFailure { Log.w(TAG, "Ignoring invalid management message") }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = disconnected(webSocket)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (response?.code == 410) clearRevokedLocalState()
                disconnected(webSocket)
            }
        })
    }

    private fun handleRevoked(webSocket: WebSocket) {
        if (revocationHandled.compareAndSet(false, true)) {
            heartbeatTask?.cancel(false)
            heartbeatTask = null
            store.clearDeviceCredential()
            store.clearPairing()
            store.setState(RemoteManagementStore.STATE_READY_TO_PAIR)
            store.setPairingMessage("unbound")
        }
        // A repeated notification is also an opportunity to retry an ack after a transient drop.
        val acknowledged = webSocket.send(gson.toJson(JsonObject().apply {
            addProperty("id", java.util.UUID.randomUUID().toString())
            addProperty("type", "revoked_ack")
            addProperty("seq", store.nextOutgoingSeq())
        }))
        if (!acknowledged) Log.w(TAG, "Could not queue remote unbind acknowledgement")
    }

    private fun clearRevokedLocalState() {
        heartbeatTask?.cancel(false)
        heartbeatTask = null
        socket = null
        store.clearDeviceCredential()
        store.clearPairing()
        store.setState(RemoteManagementStore.STATE_READY_TO_PAIR)
        store.setPairingMessage("unbound")
    }

    private fun disconnected(webSocket: WebSocket) {
        if (socket !== webSocket) return
        socket = null
        heartbeatTask?.cancel(false)
        heartbeatTask = null
        if (!closed && store.enabled() && !store.deviceToken().isNullOrBlank()) {
            store.setState(RemoteManagementStore.STATE_OFFLINE)
            val wait = retrySeconds
            retrySeconds = (retrySeconds * 2).coerceAtMost(60)
            executor.schedule({ connect() }, wait, TimeUnit.SECONDS)
        }
    }

    private fun handleCommand(webSocket: WebSocket, data: JsonObject) {
        val id = data.get("id")?.asString.orEmpty()
        val commandSeq = data.get("seq")?.asLong ?: return
        if (id.isBlank() || data.get("expiresAt")?.asString?.let { expires ->
                runCatching {
                    val parsed = android.text.format.Time().apply { parse3339(expires) }
                    parsed.toMillis(false) < System.currentTimeMillis()
                }.getOrDefault(true)
            } != false) {
            sendAck(webSocket, id, commandSeq, "rejected", "Command expired or invalid")
            return
        }
        val lastSeq = store.lastCommandSeq()
        if (commandSeq <= lastSeq) {
            if (store.lastCommandId() == id) sendAck(webSocket, id, commandSeq, store.lastCommandStatus(), store.lastCommandMessage())
            else sendAck(webSocket, id, commandSeq, "rejected", "Stale command")
            return
        }
        val commandType = data.get("commandType")?.asString.orEmpty()
        mainHandler.post {
        val result = runCatching {
            when (commandType) {
                "set_config" -> applyConfig(data.getAsJsonObject("payload")?.getAsJsonObject("config") ?: error("Missing config"))
                "restart_receiver" -> check(service.restartReceiverFromRemote()) { "Receiver could not be restarted" }
                "unbind" -> {
                    // Acknowledge first; the server invalidates the credential when it receives this.
                    store.clearDeviceCredential()
                    socket = null
                    webSocket.close(1000, "unbound")
                    store.setState(RemoteManagementStore.STATE_READY_TO_PAIR)
                    store.setPairingMessage("unbound")
                }
                else -> error("Unsupported command")
            }
        }
        val status = if (result.isSuccess) "applied" else if (commandType == "set_config") "rejected" else "failed"
        val message = result.exceptionOrNull()?.message.orEmpty().take(200)
        store.saveCommandResult(commandSeq, id, status, message)
        sendAck(webSocket, id, commandSeq, status, message)
        if (status == "applied" && commandType != "unbind") sendStatus(webSocket)
        }
    }

    private fun applyConfig(config: JsonObject) {
        val before = ReceiverSettingsStore(app).load()
        var after = before
        for ((key, value) in config.entrySet()) {
            when (key) {
                "deviceName" -> {
                    val name = value.asString.trim()
                    require(name.isNotEmpty() && name.toByteArray().size <= 60) { "Invalid device name" }
                    after = after.copy(deviceName = name)
                }
                "access" -> {
                    val v = value.asString
                    require(v == ReceiverSettings.ACCESS_OPEN || v == ReceiverSettings.ACCESS_CONFIRM) { "Unsupported access mode" }
                    after = after.copy(access = v)
                }
                "resolution" -> {
                    val v = value.asString
                    require(v in listOf("Auto", "720p", "1080p")) { "Unsupported resolution" }
                    after = after.copy(resolution = v)
                }
                "videoCodec" -> {
                    val v = value.asString
                    require(v in listOf("Auto", "H.264 only")) { "Unsupported video codec" }
                    after = after.copy(videoCodec = v)
                }
                "appendTvName" -> after = after.copy(appendTvName = value.asBoolean)
                "desktopEnabled" -> after = after.copy(desktopEnabled = value.asBoolean)
                "allowTakeover" -> after = after.copy(allowTakeover = value.asBoolean)
                "showStats" -> after = after.copy(showStats = value.asBoolean)
                "showLyrics" -> after = after.copy(showLyrics = value.asBoolean)
                "coverSpin" -> after = after.copy(coverSpin = value.asBoolean)
                "openOnCast" -> {
                    val enabled = value.asBoolean
                    require(!enabled || ScreenLauncher.allowed(app)) {
                        "Allow CastBay to display over other apps on the TV first"
                    }
                    after = after.copy(openOnCast = enabled)
                }
                "testVersions" -> after = after.copy(testVersions = value.asBoolean)
                "sendErrorReports" -> after = after.copy(sendErrorReports = value.asBoolean)
                "frameRate" -> {
                    val setting = value.asString
                    require(setting in ReceiverSettings.FRAME_RATES) { "Unsupported frameRate" }
                    after = after.copy(frameRate = setting)
                }
                "pictureMode" -> {
                    val setting = value.asString
                    require(setting in ReceiverSettings.PICTURE_MODES) { "Unsupported pictureMode" }
                    after = after.copy(pictureMode = setting)
                }
                "screenSaver" -> {
                    val setting = value.asString
                    require(setting in ReceiverSettings.SCREEN_SAVERS) { "Unsupported screenSaver" }
                    after = after.copy(screenSaver = setting)
                }
                "dlnaEnabled" -> after = after.copy(dlnaEnabled = value.asBoolean)
                "startOnBoot" -> after = after.copy(startOnBoot = value.asBoolean)
                "checkUpdates" -> after = after.copy(checkUpdates = value.asBoolean)
                else -> error("Unsupported setting: $key")
            }
        }
        if (after != before) {
            ReceiverSettingsStore(app).save(after)
            manager.restartIfRunning(after)
        }
    }

    private fun sendHello(webSocket: WebSocket, type: String) {
        val message = JsonObject().apply {
            addProperty("id", java.util.UUID.randomUUID().toString())
            addProperty("type", type)
            addProperty("seq", store.nextOutgoingSeq())
            addProperty("protocolVersion", 2)
            addProperty("appVersion", AppVersion.name(app))
            addProperty("receiverState", receiverState())
            addProperty("castingState", manager.remoteCastingState())
            addProperty("androidVersion", Build.VERSION.RELEASE.orEmpty().take(40))
            addProperty("androidSdk", Build.VERSION.SDK_INT)
        }
        webSocket.send(gson.toJson(message))
    }

    private fun sendStatus(webSocket: WebSocket) {
        val s = ReceiverSettingsStore(app).load()
        val message = JsonObject().apply {
            addProperty("id", java.util.UUID.randomUUID().toString())
            addProperty("type", "status")
            addProperty("seq", store.nextOutgoingSeq())
            addProperty("receiverState", receiverState())
            addProperty("castingState", manager.remoteCastingState())
            addProperty("androidVersion", Build.VERSION.RELEASE.orEmpty().take(40))
            addProperty("androidSdk", Build.VERSION.SDK_INT)
            add("actualConfig", JsonObject().apply {
                addProperty("deviceName", s.deviceName)
                addProperty("appendTvName", s.appendTvName)
                addProperty("desktopEnabled", s.desktopEnabled)
                addProperty("allowTakeover", s.allowTakeover)
                addProperty("showStats", s.showStats)
                addProperty("showLyrics", s.showLyrics)
                addProperty("coverSpin", s.coverSpin)
                addProperty("openOnCast", s.openOnCast)
                addProperty("testVersions", s.testVersions)
                addProperty("sendErrorReports", s.sendErrorReports)
                addProperty("frameRate", s.frameRate)
                addProperty("pictureMode", s.pictureMode)
                addProperty("screenSaver", s.screenSaver)
                if (s.access == ReceiverSettings.ACCESS_OPEN || s.access == ReceiverSettings.ACCESS_CONFIRM) addProperty("access", s.access)
                addProperty("resolution", s.resolution)
                addProperty("videoCodec", s.videoCodec)
                addProperty("dlnaEnabled", s.dlnaEnabled)
                addProperty("startOnBoot", s.startOnBoot)
                addProperty("checkUpdates", s.checkUpdates)
            })
        }
        webSocket.send(gson.toJson(message))
    }

    fun sendStatus() { socket?.let(::sendStatus) }

    private fun sendAck(webSocket: WebSocket, commandId: String, commandSeq: Long, status: String, message: String) {
        webSocket.send(gson.toJson(JsonObject().apply {
            addProperty("id", java.util.UUID.randomUUID().toString())
            addProperty("type", "ack")
            addProperty("seq", store.nextOutgoingSeq())
            addProperty("commandId", commandId)
            addProperty("status", status)
            if (message.isNotBlank()) addProperty("message", message)
        }))
    }

    private fun receiverState() = if (AirPlayService.isReceiving) "running" else "stopped"
    private fun post(path: String, body: JsonObject, token: String?): JsonObject {
        val request = Request.Builder().url(BASE + path).post(gson.toJson(body).toRequestBody(JSON))
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        return http.newCall(request).execute().use(::readJson)
    }

    private fun readJson(response: Response): JsonObject {
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IllegalStateException("Management server returned ${response.code}")
        return gson.fromJson(text, JsonObject::class.java) ?: JsonObject()
    }

    private fun clearPendingPairing() {
        pollToken = null
        pairingId = null
        store.clearPairing()
    }

    fun close() {
        closed = true
        socket?.close(1000, "service stopped")
        socket = null
        clearPendingPairing()
        executor.shutdownNow()
        http.dispatcher.cancelAll()
    }

    companion object {
        private const val TAG = "CastBayRemote"
        private const val BASE = "https://manage.weenas.com"
        private const val WS_BASE = "wss://manage.weenas.com"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
