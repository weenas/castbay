package com.weenas.castbay.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.weenas.castbay.service.MediaTracks
import com.weenas.castbay.service.ReceiverSettings
import com.weenas.castbay.viewmodel.AirPlayViewModel
import androidx.compose.ui.res.stringResource
import com.weenas.castbay.R
import com.weenas.castbay.ui.DIALOG_ACCENT
import com.weenas.castbay.ui.hasTouchScreen
import com.weenas.castbay.ui.usingKeys
import kotlinx.coroutines.delay
import com.weenas.castbay.ui.settingValueLabel

/**
 * Settings that apply while something plays, in a bar along the bottom: opened with the
 * remote's Down or Menu key, closed with Back, Down or Up. Left and Right move between the
 * options; OK switches one to its next value.
 *
 * [hasPicture] adds the picture mode (mirroring and AirPlay video); [player] adds the AirPlay
 * video's audio tracks and subtitles when it has a choice of them.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QuickMenu(
    viewModel: AirPlayViewModel,
    hasPicture: Boolean,
    player: Player?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    // Track lists change as a stream loads, and after a selection.
    var tracks by remember(player) { mutableStateOf(player?.currentTracks ?: Tracks.EMPTY) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    val audioChoices = remember(tracks) { player?.let { MediaTracks.choices(it, C.TRACK_TYPE_AUDIO) }.orEmpty() }
    val subtitleChoices = remember(tracks) { player?.let { MediaTracks.choices(it, C.TRACK_TYPE_TEXT) }.orEmpty() }

    val firstItem = remember { FocusRequester() }
    val keys = usingKeys()
    // With keys, the first option is ready for OK; on a touch screen nothing looks pressed.
    LaunchedEffect(Unit) { if (keys) firstItem.requestFocus() }
    // Left alone for a few seconds, the menu goes away by itself; any key or tap in it waits again.
    var lastUse by remember { mutableIntStateOf(0) }
    LaunchedEffect(lastUse) {
        delay(IDLE_CLOSE_MS)
        onDismiss()
    }
    fun used(action: () -> Unit): () -> Unit = {
        lastUse++
        action()
    }

    // One translucent row along the bottom, in the colours of the app's dialogs, so the picture
    // stays in view; more options just extend it (it scrolls when they no longer fit).
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xE62E2745), Color(0xE61B1826))))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(20.dp))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) lastUse++
                // Down again (or Up) closes it, like Back.
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionDown || event.key == Key.DirectionUp)
                ) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            // Right on the last option (or Left on the first) stays in the menu rather than
            // wandering to the controls behind it.
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
            .horizontalScroll(rememberScrollState())
            // A tap on the bar between options isn't a tap on the picture (which closes it).
            .pointerInput(Unit) { detectTapGestures { lastUse++ } }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (player != null) VideoControls(player, viewModel, Modifier.focusRequester(firstItem), ::used)
        MenuItem(
            stringResource(R.string.menu_stats),
            stringResource(if (settings.showStats) R.string.on else R.string.off),
            if (player == null) Modifier.focusRequester(firstItem) else Modifier,
            used { viewModel.updateSettings { it.copy(showStats = !it.showStats) } }
        )
        if (hasPicture) {
            MenuItem(stringResource(R.string.menu_picture), settingValueLabel(settings.pictureMode), onClick = used {
                val modes = ReceiverSettings.PICTURE_MODES
                viewModel.updateSettings {
                    it.copy(pictureMode = modes[(modes.indexOf(it.pictureMode) + 1) % modes.size])
                }
            })
        }
        if (!hasPicture) {
            MenuItem(stringResource(R.string.menu_lyrics), stringResource(if (settings.showLyrics) R.string.on else R.string.off), onClick = used {
                viewModel.updateSettings { it.copy(showLyrics = !it.showLyrics) }
            })
        }
        if (player != null && audioChoices.size > 1) {
            MenuItem(stringResource(R.string.menu_audio), audioChoices.firstOrNull { it.selected }?.label ?: stringResource(R.string.auto), onClick = used {
                MediaTracks.next(audioChoices)?.let { MediaTracks.select(player, C.TRACK_TYPE_AUDIO, it) }
            })
        }
        if (player != null && subtitleChoices.isNotEmpty()) {
            MenuItem(stringResource(R.string.menu_subtitles), subtitleChoices.firstOrNull { it.selected && it.group != null }?.label ?: stringResource(R.string.off), onClick = used {
                MediaTracks.next(subtitleChoices)?.let { MediaTracks.select(player, C.TRACK_TYPE_TEXT, it) }
            })
        }
        // Keys to press: nothing to do with a touch screen.
        if (!hasTouchScreen()) Text(
            stringResource(R.string.menu_hint),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/**
 * For videos cast from apps (the sender's own controls may be out of reach, e.g. in a car):
 * pause or resume, showing where the video is, and skip back or ahead.
 */
@Composable
private fun VideoControls(player: Player, viewModel: AirPlayViewModel, modifier: Modifier, used: (() -> Unit) -> () -> Unit) {
    var playing by remember { mutableStateOf(player.isPlaying) }
    var positionMs by remember { mutableLongStateOf(player.currentPosition) }
    var durationMs by remember { mutableLongStateOf(player.duration) }
    LaunchedEffect(player) {
        while (true) {
            playing = player.isPlaying || player.playWhenReady
            positionMs = player.currentPosition
            durationMs = player.duration
            delay(500)
        }
    }
    val time = if (durationMs > 0) "${clock(positionMs)} / ${clock(durationMs)}" else clock(positionMs)
    MenuItem(stringResource(if (playing) R.string.menu_pause else R.string.menu_resume), time, modifier, used { viewModel.toggleVideoPause() })
    val step = stringResource(R.string.menu_seconds, SKIP_SEC)
    MenuItem(stringResource(R.string.menu_back), step, onClick = used { viewModel.seekVideoBy(-SKIP_SEC) })
    MenuItem(stringResource(R.string.menu_ahead), step, onClick = used { viewModel.seekVideoBy(SKIP_SEC) })
}

/** "1:02:03" or "2:03". */
private fun clock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    val h = total / 3600
    val m = total % 3600 / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private const val SKIP_SEC = 10
private const val IDLE_CLOSE_MS = 5_000L

/** An option: its name above its value. OK switches it to the next value. */
@Composable
private fun MenuItem(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .widthIn(min = 120.dp)
            .clip(RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (focused) DIALOG_ACCENT else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val ink = Color(0xFF1B1826)
        Text(label, color = if (focused) ink.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        Text(
            value,
            color = if (focused) ink else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
