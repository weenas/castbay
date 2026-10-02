package com.weenas.castbay.viewmodel

import android.app.Application
import android.content.Intent
import android.view.Surface
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import com.weenas.castbay.service.AirPlayConnectionState
import com.weenas.castbay.service.AirPlayManager
import com.weenas.castbay.service.StreamInfo
import com.weenas.castbay.service.UpdateInstall
import com.weenas.castbay.service.UpdateInstaller
import com.weenas.castbay.BuildConfig
import com.weenas.castbay.util.Log
import com.weenas.castbay.service.ReceiverSettings
import com.weenas.castbay.service.ReceiverSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AirPlayUiState(
    val connectionState: AirPlayConnectionState = AirPlayConnectionState.Idle,
    val streamInfo: StreamInfo = StreamInfo(),
    val errorMessage: String? = null
)

class AirPlayViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = AirPlayManager.getInstance(application)
    private val settingsStore = ReceiverSettingsStore(application)

    private val _state = MutableStateFlow(AirPlayUiState())
    val state: StateFlow<AirPlayUiState> = _state.asStateFlow()
    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<ReceiverSettings> = _settings.asStateFlow()

    private val networkMonitor = com.weenas.castbay.service.NetworkMonitor(application).also { it.start() }
    /** The TV's network (type, Wi-Fi name, IP) for the home screen. */
    val network: StateFlow<com.weenas.castbay.service.NetworkStatus> = networkMonitor.status
    /** The sender's volume, as it changes: the TV shows it briefly (VolumeIndicator). */
    val senderVolume get() = manager.senderVolume
    /** A PIN a new sender must enter (PIN pairing), shown over any screen; null otherwise. */
    val pairingPin get() = manager.pairingPin

    /** Puts the PIN away; the device gets a new one when it tries again. */
    fun dismissPairingPin() = manager.hidePairingPin()

    /** A new device asking to cast, while new devices need approval. */
    val deviceRequest get() = manager.deviceRequest
    fun answerDeviceRequest(allow: Boolean) = manager.answerDeviceRequest(allow)
    fun dismissDeviceRequest() = manager.dismissDeviceRequest()

    /** Devices that have cast here, each allowed or blocked. */
    fun knownDevices() = manager.knownDevices()
    fun setDeviceAllowed(deviceId: String, allowed: Boolean) = manager.setDeviceAllowed(deviceId, allowed)
    /** Forgets every device, allowed, blocked or paired by PIN: they are new again. */
    fun forgetDevices() = manager.forgetDevices()

    val appVersion: String = com.weenas.castbay.util.AppVersion.name(application)

    fun canReadWifiName() = networkMonitor.canReadSsid()

    /** How mirroring is offered with [settings] on this TV, e.g. "H.265, up to 4K". */
    fun mirroringProfile(settings: ReceiverSettings) = manager.mirroringProfile(settings)

    /** Call after the location permission was granted, so the Wi-Fi name appears. */
    fun refreshNetwork() = networkMonitor.refresh()

    // StateFlow, not LiveData: Compose only recomposes for state it observes.
    /** A screen the home screen asked to open ("settings", "help" or "about"), until it is shown. */
    private val _navigateTo = MutableStateFlow<String?>(null)
    val navigateTo: StateFlow<String?> = _navigateTo.asStateFlow()

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack.asStateFlow()

    private val stateCallback: (AirPlayConnectionState, StreamInfo, String?) -> Unit = { state, streamInfo, error ->
        _state.value = AirPlayUiState(state, streamInfo, error)
    }

    init {
        manager.registerStateCallback(stateCallback)
        // Opening the app starts the receiver (a no-op if it is running), like turning on an Apple TV.
        startServer()
    }

    fun startServer() {
        val intent = Intent(getApplication(), com.weenas.castbay.service.AirPlayService::class.java)
            .setAction(com.weenas.castbay.service.AirPlayService.ACTION_START)
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun stopServer() {
        getApplication<Application>().startService(
            Intent(getApplication(), com.weenas.castbay.service.AirPlayService::class.java)
                .setAction(com.weenas.castbay.service.AirPlayService.ACTION_STOP)
        )
        manager.stop()
    }

    fun remoteControl(command: com.weenas.castbay.service.DacpClient.Command) = manager.remoteControl(command)

    fun skipMusic(forward: Boolean) = manager.skipMusic(forward)

    fun seekMusic(positionSec: Double) = manager.seekMusic(positionSec)

    fun toggleVideoPause() = manager.toggleVideoPause()

    private val lyricsClient = com.weenas.castbay.service.LyricsClient(appVersion)

    private val updateChecker = com.weenas.castbay.service.UpdateChecker(application, appVersion)
    private val _update = MutableStateFlow(if (_settings.value.checkUpdates) updateChecker.known() else null)
    /** A newer CastBay on GitHub, found by the daily check (off in Settings: none). */
    val update: StateFlow<com.weenas.castbay.service.AppUpdate?> = _update.asStateFlow()

    private fun checkForUpdate() {
        if (!_settings.value.checkUpdates) {
            _update.value = null
            return
        }
        kotlin.concurrent.thread(name = "CastBay-update") {
            val found = updateChecker.check()
            if (_settings.value.checkUpdates) _update.value = found
        }
    }

    private val updateInstaller = UpdateInstaller(application)
    private val _updateInstall = MutableStateFlow<UpdateInstall>(UpdateInstall.Idle)
    /** Where downloading and installing [update] has got to (About's Update button). */
    val updateInstall: StateFlow<UpdateInstall> = _updateInstall.asStateFlow()

    /** How the last Check for updates on About went: null before one. */
    enum class UpdateCheck { CHECKING, UP_TO_DATE, FAILED }
    private val _updateCheck = MutableStateFlow<UpdateCheck?>(null)
    val updateCheck: StateFlow<UpdateCheck?> = _updateCheck.asStateFlow()

    /**
     * Checks for a newer version now (About's button). Pressing it is asking to check, so it
     * works with the daily check turned off too.
     */
    fun checkForUpdateNow() {
        if (_updateCheck.value == UpdateCheck.CHECKING) return
        _updateCheck.value = UpdateCheck.CHECKING
        kotlin.concurrent.thread(name = "CastBay-update-check") {
            updateChecker.checkNow()
                .onSuccess { found ->
                    _update.value = found
                    _updateCheck.value = if (found == null) UpdateCheck.UP_TO_DATE else null
                }
                .onFailure { _updateCheck.value = UpdateCheck.FAILED }
        }
    }

    /** Downloads [update] and opens Android's installer on it; the person confirms there. */
    fun installUpdate() {
        val target = _update.value ?: return
        if (!BuildConfig.SELF_UPDATE) return
        if (_updateInstall.value is UpdateInstall.Downloading) return
        _updateInstall.value = UpdateInstall.Downloading(null)
        kotlin.concurrent.thread(name = "CastBay-update-download") {
            val file = try {
                updateInstaller.download(target) { _updateInstall.value = UpdateInstall.Downloading(it) }
            } catch (error: UpdateInstaller.ChecksumException) {
                _updateInstall.value = UpdateInstall.Failed(UpdateInstall.Reason.CHECKSUM)
                return@thread
            } catch (error: Exception) {
                _updateInstall.value = UpdateInstall.Failed(UpdateInstall.Reason.DOWNLOAD)
                return@thread
            }
            _updateInstall.value = try {
                updateInstaller.install(file)
                UpdateInstall.Installing
            } catch (error: Exception) {
                Log.w("CastBayUpdate", "No installer for the update", error)
                UpdateInstall.Failed(UpdateInstall.Reason.INSTALLER)
            }
        }
    }

    // After the checker above: initialisers and init blocks run in the order written.
    init {
        checkForUpdate()
    }

    /** Synced lyrics for a song, or null; looked up online, so call off the main thread. */
    fun findLyrics(title: String, artist: String?, album: String?, durationSec: Double) =
        lyricsClient.find(title, artist, album, durationSec)

    /** Stops the current cast from the TV; the receiver keeps waiting for the next one. */
    fun endCasting() = manager.endCasting()

    /** For the stats overlay; main thread. */
    fun playbackStats() = manager.playbackStats()

    fun seekVideoBy(deltaSec: Int) = manager.seekVideoBy(deltaSec)

    /** The AirPlay video player, if one is active. Main thread only. */
    val videoPlayer get() = manager.videoPlayer

    fun setVideoSurface(surface: Surface?) {
        manager.setVideoSurface(surface)
    }

    /** Turns the daily update check on or off; on checks now (if it hasn't today). */
    fun setCheckUpdates(enabled: Boolean) {
        updateSettings { it.copy(checkUpdates = enabled) }
        checkForUpdate()
    }

    /** Every setting back to its default (the TV's own name is kept: it isn't a setting). */
    fun resetSettings() = updateSettings { ReceiverSettings(tvName = it.tvName) }

    fun updateSettings(transform: (ReceiverSettings) -> ReceiverSettings) {
        val previous = _settings.value
        val updated = transform(previous)
        if (updated == previous) return
        _settings.value = updated
        settingsStore.save(updated)
        // Only protocol settings need the receiver restarted (not start-on-boot or the overlay).
        if (updated.needsRestartComparedTo(previous)) manager.restartIfRunning(updated)
    }

    /** The home screen button that opened the current screen, focused again on the way back. */
    var homeFocus: String = "settings"
        private set

    fun navigateToSettings() = navigateTo("settings")

    fun navigateToAbout() = navigateTo("about")

    fun navigateToHelp() = navigateTo("help")

    /** About → Diagnostics; Back returns to About. */
    fun navigateToDiagnostics() {
        _navigateTo.value = "diagnostics"
    }

    private fun navigateTo(screen: String) {
        homeFocus = screen
        _navigateTo.value = screen
    }

    fun navigateBack() {
        _navigateBack.value = true
    }

    fun onNavigateToConsumed() {
        _navigateTo.value = null
    }

    fun onNavigateBackConsumed() {
        _navigateBack.value = false
    }

    override fun onCleared() {
        super.onCleared()
        networkMonitor.stop()
        manager.unregisterStateCallback(stateCallback)
    }
}
