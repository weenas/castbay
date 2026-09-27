package com.weenas.castbay.viewmodel

import android.app.Application
import android.content.Intent
import android.view.Surface
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import com.weenas.castbay.service.AirPlayConnectionState
import com.weenas.castbay.service.AirPlayManager
import com.weenas.castbay.service.StreamInfo
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
        if (_settings.value.receiverEnabled) startServer()
    }

    /** "Receive casts": on starts the receiver now and from now on, off stops it and keeps it off. */
    fun setReceiverEnabled(enabled: Boolean) {
        updateSettings { it.copy(receiverEnabled = enabled) }
        if (enabled) startServer() else stopServer()
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

    fun toggleVideoPause() = manager.toggleVideoPause()

    private val lyricsClient = com.weenas.castbay.service.LyricsClient(appVersion)

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
