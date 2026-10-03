package com.weenas.castbay

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import com.weenas.castbay.util.Diagnostics
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weenas.castbay.service.AppLanguage
import com.weenas.castbay.ui.LocalBackgroundImage
import com.weenas.castbay.ui.DeviceRequestDialog
import com.weenas.castbay.ui.PairingPinDialog
import com.weenas.castbay.ui.screen.MirrorScreen
import com.weenas.castbay.ui.screen.SettingsScreen
import com.weenas.castbay.ui.screen.AboutScreen
import com.weenas.castbay.ui.screen.ChangelogScreen
import com.weenas.castbay.ui.screen.LicensesScreen
import com.weenas.castbay.ui.screen.DiagnosticsScreen
import com.weenas.castbay.ui.screen.HelpScreen
import com.weenas.castbay.ui.theme.CastBayTheme
import com.weenas.castbay.viewmodel.AirPlayViewModel

class MainActivity : ComponentActivity() {
    // The language chosen in Settings (Settings recreates the activity when it changes).
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    /** Every key as it arrives (Diagnostics): which keys a remote or a car's buttons send. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Diagnostics.record("key", KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_") +
                " (${event.keyCode}) from ${event.device?.name ?: "?"}")
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest theme only paints the launch screen; the app itself has a plain background.
        setTheme(R.style.Theme_CastBay)
        super.onCreate(savedInstanceState)
        Diagnostics.init(this)
        Diagnostics.record("app", "Opened")
        setContent {
            // Decoded once for the home and Settings backgrounds (the same artwork as the launch screen).
            val backgroundImage = ImageBitmap.imageResource(R.drawable.splash_image)
            CompositionLocalProvider(LocalBackgroundImage provides backgroundImage) {
                CastBayTheme { Screens() }
            }
        }
    }
}

@Composable
private fun Screens() {
    // Saved, so recreating the activity for a new language stays in Settings.
    var currentScreen by rememberSaveable { mutableStateOf("mirror") }
    val viewModel: AirPlayViewModel = viewModel()

    val navigateTo by viewModel.navigateTo.collectAsState()
    val navigateBack by viewModel.navigateBack.collectAsState()

    LaunchedEffect(navigateTo) {
        navigateTo?.let {
            currentScreen = it
            viewModel.onNavigateToConsumed()
        }
    }

    LaunchedEffect(navigateBack) {
        if (navigateBack) {
            currentScreen = "mirror"
            viewModel.onNavigateBackConsumed()
        }
    }

    // A cast that starts while Settings or About is open takes the screen, as on an Apple TV.
    // Only on the transition: opening Settings during a cast still works.
    val state by viewModel.state.collectAsState()
    val streaming = state.connectionState == com.weenas.castbay.service.AirPlayConnectionState.Streaming
    LaunchedEffect(streaming) {
        if (streaming) currentScreen = "mirror"
    }

    // The remote's Back key leaves Settings, Help and About for the home screen instead of closing the app.
    BackHandler(enabled = currentScreen != "mirror") {
        // Diagnostics and the release notes are opened from About, and go back there.
        currentScreen = if (currentScreen == "diagnostics" || currentScreen == "changelog" || currentScreen == "licenses") "about" else "mirror"
    }

    Box {
        when (currentScreen) {
            "mirror" -> MirrorScreen(viewModel = viewModel)
            "settings" -> SettingsScreen(viewModel = viewModel)
            "help" -> HelpScreen(viewModel = viewModel)
            "about" -> AboutScreen(viewModel = viewModel)
            "diagnostics" -> DiagnosticsScreen()
            "changelog" -> ChangelogScreen(viewModel = viewModel)
            "licenses" -> LicensesScreen()
        }
        val pairingPin by viewModel.pairingPin.collectAsState()
        PairingPinDialog(pairingPin, onDismiss = viewModel::dismissPairingPin)
        val deviceRequest by viewModel.deviceRequest.collectAsState()
        DeviceRequestDialog(deviceRequest, onAnswer = viewModel::answerDeviceRequest, onDismiss = viewModel::dismissDeviceRequest)
    }
}
