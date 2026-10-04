package com.weenas.castbay.ui.screen

import android.app.Activity
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.weenas.castbay.service.ReceiverSettings
import com.weenas.castbay.viewmodel.AirPlayViewModel
import kotlinx.coroutines.delay

/**
 * How long the music screen waits before its screen saver. A test build can shorten both
 * (tools/sim saver SECONDS).
 */
object ScreenSaverTiming {
    @Volatile var testDelayMs: Long? = null
    val dimAfterMs get() = testDelayMs ?: (10 * 60_000L)
    val offAfterMs get() = testDelayMs ?: (2 * 60_000L)
    val shiftEveryMs get() = testDelayMs?.div(2) ?: 60_000L
}

enum class SaverLevel { AWAKE, DIM, BLACK }

/** Where the music screen saver is: how dark, and how far the screen is shifted. */
class ScreenSaverState {
    var level by mutableStateOf(SaverLevel.AWAKE)
    var shift by mutableStateOf(IntOffset.Zero)
}

// A slow walk around the screen's position, a few pixels each step, so no pixel shows the
// same thing for hours.
private val SHIFTS_DP = listOf(0 to 0, 6 to 3, 3 to -6, -6 to -3, -3 to 6, 6 to -6, -6 to 6, 0 to 4)

/**
 * The music screen's screen saver (Settings → Playback): after a while without a key or touch
 * ([AirPlayViewModel.lastActivity]), [ReceiverSettings.SCREEN_SAVER_BURN_IN] dims the screen and
 * moves it a few pixels every so often; [ReceiverSettings.SCREEN_SAVER_MUSIC_OFF] blacks it out
 * and lowers the window's brightness (which tablets and car displays obey). [active] is false
 * on other screens and when the cast ends. Any key (but media keys) or touch wakes it.
 */
@Composable
fun rememberMusicScreenSaver(active: Boolean, mode: String, viewModel: AirPlayViewModel): ScreenSaverState {
    val state = remember { ScreenSaverState() }
    val lastActivity by viewModel.lastActivity.collectAsState()
    val density = LocalDensity.current
    LaunchedEffect(active, mode, lastActivity) {
        state.level = SaverLevel.AWAKE
        state.shift = IntOffset.Zero
        viewModel.screenSaverOn = false
        if (!active || mode == ReceiverSettings.SCREEN_SAVER_OFF) return@LaunchedEffect
        val blackOut = mode == ReceiverSettings.SCREEN_SAVER_MUSIC_OFF
        val waited = SystemClock.elapsedRealtime() - lastActivity
        delay(((if (blackOut) ScreenSaverTiming.offAfterMs else ScreenSaverTiming.dimAfterMs) - waited).coerceAtLeast(0))
        state.level = if (blackOut) SaverLevel.BLACK else SaverLevel.DIM
        viewModel.screenSaverOn = true
        if (blackOut) return@LaunchedEffect
        var step = 1
        while (true) {
            val (x, y) = SHIFTS_DP[step % SHIFTS_DP.size]
            state.shift = with(density) { IntOffset(x.dp.roundToPx(), y.dp.roundToPx()) }
            step++
            delay(ScreenSaverTiming.shiftEveryMs)
        }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.screenSaverOn = false }
    }
    // Screen off: the window as dim as the device allows, back to normal when it wakes.
    val activity = LocalContext.current as? Activity
    DisposableEffect(state.level, activity) {
        val window = activity?.window
        window?.attributes = window?.attributes?.apply {
            screenBrightness = if (state.level == SaverLevel.BLACK) 0.01f
                else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        onDispose {
            window?.attributes = window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
    return state
}

/** The music screen's position, shifted by the screen saver (moving slowly, not jumping). */
@Composable
fun animatedSaverShift(state: ScreenSaverState): IntOffset =
    animateIntOffsetAsState(state.shift, tween(4_000), label = "saver shift").value

/** The darkening on top of the music screen: none, dimmed, or black; waking is quick. */
@Composable
fun ScreenSaverOverlay(state: ScreenSaverState) {
    val target = when (state.level) {
        SaverLevel.AWAKE -> 0f
        SaverLevel.DIM -> 0.55f
        SaverLevel.BLACK -> 1f
    }
    val alpha by animateFloatAsState(target, tween(if (state.level == SaverLevel.AWAKE) 250 else 3_000), label = "saver")
    if (alpha > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha)))
}
