package com.weenas.castbay.ui.screen

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import com.weenas.castbay.service.DacpClient
import com.weenas.castbay.service.NowPlaying
import com.weenas.castbay.service.StatsFormat
import com.weenas.castbay.service.Lyrics
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.weenas.castbay.R
import com.weenas.castbay.ui.mirroringLabel
import com.weenas.castbay.ui.AppBackground
import com.weenas.castbay.ui.BrandTitle
import com.weenas.castbay.ui.Backdrop
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import com.weenas.castbay.ui.MediaIcons
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.weenas.castbay.ui.LocalBackgroundImage
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import com.weenas.castbay.service.PictureLayout
import com.weenas.castbay.service.ReceiverSettings
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontFamily
import com.weenas.castbay.service.NetworkStatus
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.service.AirPlayConnectionState
import com.weenas.castbay.viewmodel.AirPlayViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirrorScreen(viewModel: AirPlayViewModel) {
    val state by viewModel.state.collectAsState()

    // Mirroring and AirPlay video own the whole screen: no app bar, no padding, just the picture.
    val stream = state.streamInfo
    if (state.connectionState == AirPlayConnectionState.Streaming &&
        (stream.isVideoPlayback || stream.isAudioOnly || stream.isMirroring)
    ) {
        val settings by viewModel.settings.collectAsState()
        val kind = when {
            stream.isVideoPlayback -> StreamKind.VIDEO
            stream.isAudioOnly -> StreamKind.AUDIO
            else -> StreamKind.MIRRORING
        }
        var menuOpen by remember(kind) { mutableStateOf(false) }
        // The playing content takes D-pad focus, and gets it back when the quick menu closes.
        val contentFocus = remember(kind) { FocusRequester() }
        LaunchedEffect(kind, menuOpen) {
            if (!menuOpen) contentFocus.requestFocus()
        }
        BackHandler(enabled = menuOpen) { menuOpen = false }
        // Back leaves casting only when pressed twice, so a stray press doesn't cut it off.
        var backArmed by remember(kind) { mutableStateOf(false) }
        BackHandler(enabled = !menuOpen) {
            if (backArmed) viewModel.endCasting() else backArmed = true
        }
        LaunchedEffect(backArmed) {
            if (backArmed) {
                delay(BACK_AGAIN_WINDOW_MS)
                backArmed = false
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    // Down and Menu are free during playback (left/right/OK control it).
                    if (menuOpen || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (event.key != Key.DirectionDown && event.key != Key.Menu) return@onPreviewKeyEvent false
                    menuOpen = true
                    true
                }
        ) {
            val contentModifier = Modifier.focusRequester(contentFocus)
            when (kind) {
                StreamKind.VIDEO -> VideoPlayback(viewModel = viewModel, pictureMode = settings.pictureMode, modifier = contentModifier)
                StreamKind.AUDIO -> {
                    val song = stream.nowPlaying
                    // Waits a moment before looking up: the length usually arrives after the title.
                    val lyrics by produceState<Lyrics?>(null, settings.showLyrics, song.title, song.artist, song.durationSec.toInt()) {
                        value = null
                        val title = song.title
                        if (!settings.showLyrics || title.isNullOrBlank()) return@produceState
                        delay(LYRICS_LOOKUP_DELAY_MS)
                        value = withContext(Dispatchers.IO) {
                            viewModel.findLyrics(title, song.artist, song.album, song.durationSec)
                        }
                    }
                    AudioPlayback(
                        nowPlaying = song,
                        onCommand = viewModel::remoteControl,
                        onSkip = viewModel::skipMusic,
                        canChangeTrack = !stream.isDlna,
                        lyrics = lyrics,
                        modifier = contentModifier
                    )
                }
                StreamKind.MIRRORING -> MirroringVideo(viewModel = viewModel, streamInfo = stream, pictureMode = settings.pictureMode, modifier = contentModifier)
            }
            if (settings.showStats) {
                StatsOverlay(viewModel, Modifier.align(Alignment.TopStart).padding(24.dp))
            }
            if (backArmed) {
                Text(
                    stringResource(R.string.press_back_again),
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 64.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 28.dp, vertical = 14.dp)
                )
            }
            if (menuOpen) {
                QuickMenu(
                    viewModel = viewModel,
                    hasPicture = kind != StreamKind.AUDIO,
                    player = if (kind == StreamKind.VIDEO) viewModel.videoPlayer else null,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(48.dp)
                )
            }
        }
        return
    }

    // No app bar: the name is on screen already, and Settings sits beside the main button.
    AppBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (state.connectionState) {
                AirPlayConnectionState.Idle -> IdleScreen(
                    viewModel = viewModel,
                    onStart = { viewModel.startServer() }
                )
                AirPlayConnectionState.Discovering -> DiscoveringScreen(
                    viewModel = viewModel,
                    lastError = state.errorMessage,
                    onStop = { viewModel.stopServer() }
                )
                // Same screen as Idle, with the button saying "Starting…", so starting doesn't
                // flash an extra screen before the waiting one.
                AirPlayConnectionState.Registering -> IdleScreen(
                    viewModel = viewModel,
                    onStart = {},
                    starting = true
                )
                AirPlayConnectionState.AdvertisingOnly -> AdvertisingOnlyScreen(
                    onStop = { viewModel.stopServer() }
                )
                AirPlayConnectionState.Connecting -> ConnectingScreen()
                AirPlayConnectionState.Connected -> ConnectedScreen(
                    viewModel = viewModel,
                    streamInfo = state.streamInfo
                )
                AirPlayConnectionState.Streaming -> StreamingScreen(
                    viewModel = viewModel,
                    streamInfo = state.streamInfo
                )
                AirPlayConnectionState.Disconnected -> DisconnectedScreen(
                    onStart = { viewModel.startServer() }
                )
                AirPlayConnectionState.Error -> ErrorScreen(
                    error = state.errorMessage ?: stringResource(R.string.unknown_error),
                    onRetry = { viewModel.startServer() }
                )
            }
        }
    }
}

private const val START_TIMEOUT_MS = 10_000L

/** [starting]: the receiver is starting; the button reads "Starting…" until it is ready. */
@Composable
fun IdleScreen(viewModel: AirPlayViewModel, onStart: () -> Unit, starting: Boolean = false) {
    // Shown right on the press: the service reports "starting" a moment later.
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        // In case the service never reports back, don't stay "Starting…" forever.
        if (pressed) {
            delay(START_TIMEOUT_MS)
            pressed = false
        }
    }
    HomeLayout(info = { ReceiverInfo(viewModel = viewModel) }) {
        BrandTitle()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_tagline),
            fontSize = 18.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(48.dp))
        val busy = starting || pressed
        HomeButtons(viewModel) {
            // Stays enabled while starting, so D-pad focus doesn't jump away; presses are ignored.
            HomeButton(
                stringResource(if (busy) R.string.starting else R.string.action_start),
                Modifier.initialFocus()
            ) {
                if (!busy) {
                    pressed = true
                    onStart()
                }
            }
        }
    }
}

@Composable
fun DiscoveringScreen(viewModel: AirPlayViewModel, lastError: String? = null, onStop: () -> Unit) {
    HomeLayout(info = { ReceiverInfo(viewModel = viewModel) }) {
        val settings by viewModel.settings.collectAsState()
        // Text's default style has a fixed 24 sp line height, so larger text that may wrap sets
        // its own (in sp: an em line height in the theme crashed text fields' label animation).
        Text(stringResource(R.string.waiting_title), color = Color.White, fontSize = 28.sp, lineHeight = 36.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(if (settings.dlnaEnabled) R.string.waiting_how_dlna else R.string.waiting_how, settings.deviceName),
            color = Color.White,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.waiting_same_network), color = Color.Gray, fontSize = 16.sp)
        if (lastError != null) {
            // Why the last AirPlay video stopped, e.g. the TV couldn't reach the video site.
            Spacer(modifier = Modifier.height(16.dp))
            Text(lastError, color = Color(0xFFFFB4AB), fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(32.dp))
        HomeButtons(viewModel) {
            HomeButton(stringResource(R.string.action_stop), Modifier.initialFocus(), onClick = onStop)
        }
    }
}

/** The home screen's main button with Settings and About beside it, in the same style. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeButtons(viewModel: AirPlayViewModel, main: @Composable () -> Unit) {
    // Narrower than elsewhere so three fit beside the info panel; they wrap if they still don't.
    CompositionLocalProvider(LocalHomeButtonMinWidth provides 120.dp) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            main()
            HomeButton(stringResource(R.string.settings), onClick = { viewModel.navigateToSettings() })
            HomeButton(stringResource(R.string.about), onClick = { viewModel.navigateToAbout() })
        }
    }
}

private val LocalHomeButtonMinWidth = staticCompositionLocalOf { 160.dp }

/** A home/Settings button. The focused one gets a white outline, visible from the sofa. */
@Composable
fun HomeButton(text: String, modifier: Modifier = Modifier, muted: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Button(
        onClick = onClick,
        interactionSource = interaction,
        // Muted: grey, e.g. a switch that is off.
        colors = if (muted) ButtonDefaults.buttonColors(containerColor = Color(0xFF4A4A52), contentColor = Color(0xFFDDDDDD))
        else ButtonDefaults.buttonColors(),
        border = if (focused) BorderStroke(3.dp, Color.White) else null,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        modifier = modifier.widthIn(min = LocalHomeButtonMinWidth.current)
    ) {
        Text(text, fontSize = 20.sp)
    }
}

/**
 * Status and actions beside the receiver info on wide screens (TVs are only ~540 dp tall at
 * 1080p), stacked and scrollable on narrow ones.
 */
@Composable
private fun HomeLayout(info: @Composable () -> Unit, primary: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = primary
                )
                Spacer(modifier = Modifier.width(48.dp))
                Box(modifier = Modifier.weight(1f)) { info() }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                primary()
                Spacer(modifier = Modifier.height(32.dp))
                info()
            }
        }
    }
}

/**
 * What a user needs to cast to this TV at a glance: its AirPlay name, network and address,
 * and how the receiver is set up.
 */
@Composable
fun ReceiverInfo(viewModel: AirPlayViewModel) {
    val settings by viewModel.settings.collectAsState()
    val network by viewModel.network.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshNetwork()
    }

    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x1FFFFFFF))
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        InfoRow(stringResource(R.string.info_name), settings.deviceName)
        InfoRow(stringResource(R.string.info_network), networkLabel(network))
        if (network.type == NetworkStatus.Type.WIFI && network.ssid == null) {
            if (!viewModel.canReadWifiName()) {
                // Android only reveals the Wi-Fi name to apps with the location permission.
                TextButton(onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                    Text(stringResource(R.string.show_wifi_name))
                }
            } else {
                Text(stringResource(R.string.wifi_name_needs_location), color = Color.Gray, fontSize = 14.sp)
            }
        }
        InfoRow(stringResource(R.string.info_ip), network.ipv4.joinToString(", ").ifEmpty { "—" })
        val mirroring = remember(settings) { viewModel.mirroringProfile(settings) }
        InfoRow(stringResource(R.string.info_mirroring), mirroringLabel(mirroring))
        InfoRow(stringResource(R.string.info_dlna), stringResource(if (settings.dlnaEnabled) R.string.info_dlna_on else R.string.off))
        InfoRow(stringResource(R.string.info_password), stringResource(if (settings.requirePassword) R.string.setting_password_on else R.string.setting_password_off))
        InfoRow(stringResource(R.string.info_second_device), stringResource(if (settings.allowTakeover) R.string.info_takes_over else R.string.info_refused))
        if (network.type == NetworkStatus.Type.NONE) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.no_network), color = Color(0xFFFFB4AB), fontSize = 16.sp)
        }
    }
}

@Composable
private fun networkLabel(network: NetworkStatus): String = when (network.type) {
    NetworkStatus.Type.WIFI -> network.ssid?.let { stringResource(R.string.network_wifi_named, it) } ?: stringResource(R.string.network_wifi)
    NetworkStatus.Type.ETHERNET -> stringResource(R.string.network_ethernet)
    NetworkStatus.Type.OTHER -> stringResource(R.string.network_other)
    NetworkStatus.Type.NONE -> stringResource(R.string.network_none)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = Color.Gray, fontSize = 18.sp, modifier = Modifier.weight(0.45f))
        Text(value, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(0.55f))
    }
}

@Composable
fun AdvertisingOnlyScreen(onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.advertising_only_title), color = Color.White, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.advertising_only_detail), color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onStop, modifier = Modifier.initialFocus()) { Text(stringResource(R.string.action_stop)) }
    }
}

@Composable
fun ConnectingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.connecting), color = Color.White)
    }
}

@Composable
fun ConnectedScreen(viewModel: AirPlayViewModel, streamInfo: com.weenas.castbay.service.StreamInfo) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.connected), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.connected_from, streamInfo.sourceName), color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Text("${streamInfo.videoWidth}x${streamInfo.videoHeight}", color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { viewModel.stopServer() }, modifier = Modifier.initialFocus()) {
            Text(stringResource(R.string.action_stop), fontSize = 20.sp)
        }
    }
}

@Composable
fun StreamingScreen(viewModel: AirPlayViewModel, streamInfo: com.weenas.castbay.service.StreamInfo) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.streaming), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.streaming_source, streamInfo.sourceName), color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { viewModel.stopServer() }, modifier = Modifier.initialFocus()) {
            Text(stringResource(R.string.action_stop), fontSize = 20.sp)
        }
    }
}

/**
 * Audio streaming (e.g. a music app): cover art, track details and progress. The remote's
 * OK and left/right keys control the sender; media keys reach it through the media session.
 */
@Composable
fun AudioPlayback(
    nowPlaying: NowPlaying,
    onCommand: (DacpClient.Command) -> Unit,
    /** Skips about ten seconds forward (true) or back. */
    onSkip: (Boolean) -> Unit,
    /** Previous/next track: AirPlay senders can; a DLNA sender's playlist is its own. */
    canChangeTrack: Boolean = true,
    lyrics: Lyrics? = null,
    modifier: Modifier = Modifier
) {
    val cover = remember(nowPlaying.coverArt) {
        nowPlaying.coverArt?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    // Ticks the progress between the sender's (infrequent) reports.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(nowPlaying, lyrics != null) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            // Lyrics lines change faster than the progress bar needs.
            delay(if (lyrics != null) 200 else 500)
        }
    }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // The cover, blurred, behind everything.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        MusicBackdrop(nowPlaying.coverArt)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .shadow(24.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                if (cover != null) {
                    Image(bitmap = cover, contentDescription = stringResource(R.string.cover_art), contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
                } else {
                    Text("♪", fontSize = 120.sp, color = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.width(56.dp))
            Column(modifier = Modifier.weight(1f)) {
                // An explicit line height: the default text style's is 24 sp, so a long title wrapped
                // onto a second line drawn over the first.
                // One line each, scrolling when too long, so long names never push the layout
                // (a TV screen is only ~540 dp tall).
                Text(nowPlaying.title ?: stringResource(R.string.airplay_audio), fontSize = 48.sp,
                    fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, modifier = Modifier.marquee())
                nowPlaying.artist?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(it, fontSize = 32.sp, color = MUSIC_TEXT_SECONDARY, maxLines = 1, modifier = Modifier.marquee())
                }
                nowPlaying.album?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, fontSize = 24.sp, color = MUSIC_TEXT_TERTIARY, maxLines = 1, modifier = Modifier.marquee())
                }
                if (lyrics != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    LyricsView(lyrics, nowPlaying.currentPositionSec(now))
                }
                if (nowPlaying.durationSec > 0) {
                    val position = nowPlaying.currentPositionSec(now)
                    Spacer(modifier = Modifier.height(28.dp))
                    // No thumb: it would suggest dragging, and senders can't seek to a time.
                    LinearProgressIndicator(
                        progress = { (position / nowPlaying.durationSec).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MUSIC_ACCENT,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        drawStopIndicator = {}
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(position), fontSize = 20.sp, color = MUSIC_TEXT_TERTIARY)
                        Text(formatTime(nowPlaying.durationSec), fontSize = 20.sp, color = MUSIC_TEXT_TERTIARY)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                // Centred under the progress bar, as in Apple Music. The screen's focus (and so
                // the remote's OK) starts on play/pause.
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MusicControls(nowPlaying.playing, onCommand, onSkip, canChangeTrack, playModifier = modifier)
                }
            }
        }
    }
}

/**
 * Apple-style round icon buttons, focused the way the rest of the app is (the accent colour
 * and a white outline): rewind, previous, play/pause (larger), next, fast forward.
 */
@Composable
private fun MusicControls(
    playing: Boolean,
    onCommand: (DacpClient.Command) -> Unit,
    onSkip: (Boolean) -> Unit,
    canChangeTrack: Boolean,
    playModifier: Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        MediaButton(MediaIcons.Rewind, R.string.music_rewind, MEDIA_BUTTON_SIZE) { onSkip(false) }
        if (canChangeTrack) {
            MediaButton(MediaIcons.Previous, R.string.music_previous, MEDIA_BUTTON_SIZE) {
                onCommand(DacpClient.Command.PREVIOUS)
            }
        }
        MediaButton(
            if (playing) MediaIcons.Pause else MediaIcons.Play,
            if (playing) R.string.music_pause else R.string.music_play,
            PLAY_BUTTON_SIZE,
            playModifier
        ) { onCommand(DacpClient.Command.PLAY_PAUSE) }
        if (canChangeTrack) {
            MediaButton(MediaIcons.Next, R.string.music_next, MEDIA_BUTTON_SIZE) { onCommand(DacpClient.Command.NEXT) }
        }
        MediaButton(MediaIcons.FastForward, R.string.music_fast_forward, MEDIA_BUTTON_SIZE) { onSkip(true) }
    }
}

@Composable
private fun MediaButton(
    icon: ImageVector,
    description: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "mediaButtonScale")
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // The focused button alone glows, so there is only ever one highlight on screen.
            // Drawn behind (and beyond) the button without taking layout space.
            .then(
                if (focused) {
                    Modifier.drawBehind {
                        drawCircle(
                            Brush.radialGradient(
                                listOf(MUSIC_ACCENT.copy(alpha = 0.45f), Color.Transparent),
                                center = center,
                                radius = this.size.minDimension * 0.85f
                            ),
                            radius = this.size.minDimension * 0.85f
                        )
                    }
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            // Frosted glass at rest; the app's accent colour and outline when focused.
            .background(if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f))
            .then(
                if (focused) Modifier.border(3.dp, Color.White, CircleShape) else Modifier
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = stringResource(description), tint = Color.White, modifier = Modifier.size(size * 0.42f))
    }
}

private val MEDIA_BUTTON_SIZE = 60.dp

/** Scrolls text that doesn't fit, pausing before each pass; text that fits stays still. */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.marquee(): Modifier = basicMarquee(
    iterations = Int.MAX_VALUE,
    initialDelayMillis = 2000,
    repeatDelayMillis = 2000,
    velocity = 40.dp
)
/** A lighter tint of the app's purple, bright enough on dark backdrops (progress, the focus glow). */
private val MUSIC_ACCENT = Color(0xFFA48BF5)
/**
 * Secondary text on the music screen: translucent white rather than grey, so it keeps its
 * contrast and picks up the tint of the cover's backdrop, as in Apple Music.
 */
private val MUSIC_TEXT_SECONDARY = Color.White.copy(alpha = 0.85f)
private val MUSIC_TEXT_TERTIARY = Color.White.copy(alpha = 0.6f)
private val PLAY_BUTTON_SIZE = 80.dp

/**
 * The album cover, enlarged and blurred under a dark veil, as Apple Music does; the launch
 * artwork when the song has no cover. Cross-fades when the song changes.
 */
@Composable
private fun MusicBackdrop(coverArt: ByteArray?) {
    val backdrop by produceState<Backdrop.Result?>(null, coverArt) {
        value = coverArt?.let { cover -> withContext(Dispatchers.Default) { Backdrop.fromCover(cover) } }
    }
    // Remembered: this recomposes with every progress tick, and a fresh wrapper each time made
    // the Crossfade below restart over and over, so the backdrop flickered.
    val blurred = remember(backdrop) { backdrop?.bitmap?.asImageBitmap() }
    // Only fall back while there is no cover at all, not while one is being blurred.
    val image = blurred ?: LocalBackgroundImage.current.takeIf { coverArt == null }
    Crossfade(targetState = image, animationSpec = tween(BACKDROP_FADE_MS), label = "backdrop") { current ->
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    // Light over most covers, darker over bright ones, so white text stays readable.
    val veil by animateFloatAsState(backdrop?.veil ?: FALLBACK_VEIL, tween(BACKDROP_FADE_MS), label = "veil")
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = veil)))
    // A faint grain keeps the wide, smooth gradients from showing bands on 8-bit panels.
    val grain = remember { ShaderBrush(ImageShader(Backdrop.grain().asImageBitmap(), TileMode.Repeated, TileMode.Repeated)) }
    Box(modifier = Modifier.fillMaxSize().background(grain, alpha = GRAIN_ALPHA))
}

private const val BACKDROP_FADE_MS = 700
/** Over the launch artwork, for songs without a cover. */
private const val FALLBACK_VEIL = 0.6f
private const val GRAIN_ALPHA = 0.04f

/** A few lines of [lyrics] around the one being sung at [positionSec], which is highlighted. */
@Composable
private fun LyricsView(lyrics: Lyrics, positionSec: Double) {
    val current = lyrics.indexAt(positionSec)
    val first = (current - LYRICS_CONTEXT_LINES).coerceAtLeast(0)
    Column(modifier = Modifier.height(180.dp)) {
        for (index in first..(current + LYRICS_CONTEXT_LINES).coerceAtMost(lyrics.lines.lastIndex)) {
            val line = lyrics.lines[index].text.ifEmpty { "♪" }
            val active = index == current
            // The sung line is brightest; the others fade the further away they are.
            val alpha = when (kotlin.math.abs(index - current)) {
                0 -> 1f
                1 -> 0.6f
                else -> 0.35f
            }
            Text(
                line,
                fontSize = if (active) 26.sp else 22.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = Color.White.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }
    }
}

private const val LYRICS_CONTEXT_LINES = 2
private const val LYRICS_LOOKUP_DELAY_MS = 1500L

private fun formatTime(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Full-screen AirPlay video. The phone stays the main remote; the TV remote can also pause
 * (OK / play-pause) and skip 10 s (left/right, rewind/fast-forward).
 */
@Composable
fun VideoPlayback(viewModel: AirPlayViewModel, pictureMode: String, modifier: Modifier = Modifier) {
    val player = viewModel.videoPlayer
    var paused by remember(player) { mutableStateOf(player?.playWhenReady == false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                paused = !playWhenReady
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause ->
                        viewModel.toggleVideoPause()
                    Key.DirectionLeft, Key.MediaRewind -> viewModel.seekVideoBy(-SEEK_STEP_SEC)
                    Key.DirectionRight, Key.MediaFastForward -> viewModel.seekVideoBy(SEEK_STEP_SEC)
                    else -> return@onKeyEvent false
                }
                true
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    // Spinner while loading or rebuffering, so a slow start isn't a black screen.
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                    // Keys are handled by the Compose container above.
                    isFocusable = false
                }
            },
            update = {
                it.player = player
                it.resizeMode = when (pictureMode) {
                    ReceiverSettings.PICTURE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    ReceiverSettings.PICTURE_STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )
        if (paused) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(60.dp))
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Text("❚❚", fontSize = 44.sp, color = Color.White)
            }
        }
    }
}

private const val SEEK_STEP_SEC = 10
private const val BACK_AGAIN_WINDOW_MS = 3000L

private enum class StreamKind { VIDEO, AUDIO, MIRRORING }

/** "Stats for nerds": what is playing and how, refreshed every second. */
@Composable
fun StatsOverlay(viewModel: AirPlayViewModel, modifier: Modifier = Modifier) {
    var stats by remember { mutableStateOf(viewModel.playbackStats()) }
    LaunchedEffect(Unit) {
        while (true) {
            stats = viewModel.playbackStats()
            delay(1000)
        }
    }
    val current = stats ?: return
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xB3000000))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        StatsLine(current.source, "", bold = true)
        current.video?.let { video ->
            StatsLine(
                "Video",
                listOf(
                    video.codec, StatsFormat.resolution(video.width, video.height),
                    StatsFormat.fps(video.fps), StatsFormat.bitrate(video.bitrateBps)
                ).joinToString(" · ")
            )
            StatsLine("", listOfNotNull(video.decoder, "dropped ${video.droppedFrames}").joinToString(" · "))
        }
        current.audio?.let { audio ->
            StatsLine(
                "Audio",
                listOf(audio.codec, StatsFormat.audioFormat(audio), StatsFormat.bitrate(audio.bitrateBps))
                    .joinToString(" · ")
            )
            audio.decoder?.let { StatsLine("", it) }
        }
        current.extra.forEach { (label, value) -> StatsLine(label, value) }
    }
}

@Composable
private fun StatsLine(label: String, value: String, bold: Boolean = false) {
    Row {
        Text(
            label,
            color = if (bold) Color.White else Color(0xFFB0B0B0),
            fontSize = 14.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            modifier = if (bold) Modifier else Modifier.width(80.dp)
        )
        Text(value, color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

/**
 * Full-screen mirrored picture, sized by [pictureMode]: letterboxed to the sender's aspect ratio
 * (Fit), cropped to cover the screen (Fill) or stretched.
 */
@Composable
fun MirroringVideo(
    viewModel: AirPlayViewModel,
    streamInfo: com.weenas.castbay.service.StreamInfo,
    pictureMode: String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
            // Focusable so the remote's keys reach the quick menu shortcut.
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        val (width, height) = PictureLayout.size(
            pictureMode, maxWidth.value, maxHeight.value, streamInfo.frameWidth, streamInfo.frameHeight
        )
        val videoModifier = Modifier.requiredSize(width.dp, height.dp)
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            viewModel.setVideoSurface(holder.surface)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            viewModel.setVideoSurface(null)
                        }
                    })
                }
            },
            modifier = videoModifier
        )
    }
}

@Composable
fun DisconnectedScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.disconnected), fontSize = 32.sp, color = Color.White)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onStart, modifier = Modifier.initialFocus()) {
            Text(stringResource(R.string.action_start), fontSize = 20.sp)
        }
    }
}

@Composable
fun ErrorScreen(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.error_title), fontSize = 32.sp, color = Color.Red)
        Spacer(modifier = Modifier.height(16.dp))
        Text(error, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRetry, modifier = Modifier.initialFocus()) {
            Text(stringResource(R.string.action_retry), fontSize = 20.sp)
        }
    }
}

/** Moves D-pad focus to this element when it first appears, so the primary action is one OK press away. */
@Composable
private fun Modifier.initialFocus(): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester) { requester.requestFocus() }
    return focusRequester(requester)
}
