package com.weenas.castbay.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.service.ReceiverSettings
import androidx.compose.ui.res.stringResource
import com.weenas.castbay.R
import com.weenas.castbay.ui.settingValueLabel
import com.weenas.castbay.ui.AppBackground
import com.weenas.castbay.ui.SegmentedChoice
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.weenas.castbay.viewmodel.AirPlayViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AirPlayViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    // Starts on "Receive casts", not the device name: focusing the text field opens the keyboard.
    val firstChoice = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstChoice.requestFocus() } }
    // Same look as the home screen: no app bar, a title with a button beside it.
    AppBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    HomeButton(stringResource(R.string.action_back), onClick = onBack)
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item {
                SettingsCard(Segment.Single) {
                    SwitchSetting(
                        stringResource(R.string.setting_receiver),
                        settings.receiverEnabled,
                        Modifier.focusRequester(firstChoice)
                    ) { viewModel.setReceiverEnabled(it) }
                    Text(stringResource(R.string.setting_receiver_note), color = Color.Gray, fontSize = 14.sp)
                }
            }
            // Connection settings change what senders see, so the receiver restarts for them;
            // playback settings apply live and are also in the quick menu during playback.
            item { SectionTitle(stringResource(R.string.section_connection), stringResource(R.string.section_connection_note)) }
            item {
                SettingsCard(Segment.Top) {
                    DeviceNameSetting(value = settings.deviceName) { name ->
                        viewModel.updateSettings { it.copy(deviceName = name) }
                    }
                    SwitchSetting(stringResource(R.string.setting_append_tv_name), settings.appendTvName) { enabled ->
                        viewModel.updateSettings { it.copy(appendTvName = enabled) }
                    }
                    Text(
                        stringResource(R.string.setting_append_tv_name_note, settings.advertisedName),
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
            item {
                SettingsCard(Segment.Middle) {
                    SwitchSetting(stringResource(R.string.setting_dlna), settings.dlnaEnabled) { enabled ->
                        viewModel.updateSettings { it.copy(dlnaEnabled = enabled) }
                    }
                }
            }
            item {
                SettingsCard(Segment.Middle) {
                    AccessSetting(settings, viewModel)
                }
            }
            item {
                SettingsCard(Segment.Bottom) {
                    ChoiceSetting(
                        stringResource(R.string.setting_takeover),
                        if (settings.allowTakeover) TAKEOVER_ALLOW else TAKEOVER_REFUSE,
                        listOf(TAKEOVER_REFUSE, TAKEOVER_ALLOW),
                        display = { stringResource(if (it == TAKEOVER_ALLOW) R.string.setting_takeover_allow else R.string.setting_takeover_refuse) }
                    ) { choice -> viewModel.updateSettings { it.copy(allowTakeover = choice == TAKEOVER_ALLOW) } }
                }
            }
            // Screen mirroring only: apps' AirPlay and DLNA video and music aren't affected. Each
            // Auto names what it gives on this TV with the other settings.
            item { SectionTitle(stringResource(R.string.section_mirroring), stringResource(R.string.section_mirroring_note)) }
            item {
                SettingsCard(Segment.Top) {
                    val autoProfile = remember(settings) {
                        viewModel.mirroringProfile(settings.copy(resolution = ReceiverSettings.RESOLUTION_AUTO))
                    }
                    val autoLabel = stringResource(R.string.auto_with, "${autoProfile.height}p")
                    ChoiceSetting(
                        stringResource(R.string.setting_resolution),
                        settings.resolution,
                        ReceiverSettings.RESOLUTIONS,
                        display = { if (it == ReceiverSettings.RESOLUTION_AUTO) autoLabel else settingValueLabel(it) }
                    ) {
                        viewModel.updateSettings { current -> current.copy(resolution = it) }
                    }
                }
            }
            item {
                SettingsCard(Segment.Middle) {
                    val autoFps = settings.copy(frameRate = ReceiverSettings.FRAME_RATE_AUTO).maxFps()
                    val autoLabel = stringResource(R.string.auto_with, stringResource(R.string.frame_rate_fps, autoFps))
                    ChoiceSetting(
                        stringResource(R.string.setting_frame_rate),
                        settings.frameRate,
                        ReceiverSettings.FRAME_RATES,
                        display = { if (it == ReceiverSettings.FRAME_RATE_AUTO) autoLabel else settingValueLabel(it) }
                    ) {
                        viewModel.updateSettings { current -> current.copy(frameRate = it) }
                    }
                }
            }
            item {
                SettingsCard(Segment.Bottom) {
                    // H.265 only for 4K mirroring, on a 4K screen with a hardware HEVC decoder.
                    val autoProfile = remember(settings) {
                        viewModel.mirroringProfile(settings.copy(videoCodec = ReceiverSettings.CODEC_AUTO))
                    }
                    val autoLabel = stringResource(R.string.auto_with, if (autoProfile.h265) "H.265" else "H.264")
                    ChoiceSetting(
                        stringResource(R.string.setting_codec),
                        settings.videoCodec,
                        ReceiverSettings.VIDEO_CODECS,
                        display = { if (it == ReceiverSettings.CODEC_AUTO) autoLabel else settingValueLabel(it) }
                    ) {
                        viewModel.updateSettings { current -> current.copy(videoCodec = it) }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.section_playback), stringResource(R.string.section_playback_note)) }
            item {
                SettingsCard(Segment.Top) {
                    SwitchSetting(stringResource(R.string.setting_stats), settings.showStats) { enabled ->
                        viewModel.updateSettings { it.copy(showStats = enabled) }
                    }
                }
            }
            item {
                SettingsCard(Segment.Middle) {
                    SwitchSetting(stringResource(R.string.setting_lyrics), settings.showLyrics) { enabled ->
                        viewModel.updateSettings { it.copy(showLyrics = enabled) }
                    }
                    Text(stringResource(R.string.setting_lyrics_note), color = Color.Gray, fontSize = 14.sp)
                }
            }
            item {
                SettingsCard(Segment.Bottom) {
                    ChoiceSetting(stringResource(R.string.setting_picture), settings.pictureMode, ReceiverSettings.PICTURE_MODES) {
                        viewModel.updateSettings { current -> current.copy(pictureMode = it) }
                    }
                }
            }
}
    }
}

/**
 * A group's title, above its card: larger than the settings' names and in the accent colour,
 * so the groups stand apart; its note says what the group's settings affect.
 */
@Composable
private fun SectionTitle(title: String, note: String?) {
    Column(modifier = Modifier.padding(start = 8.dp, top = 32.dp, bottom = 10.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SECTION_ACCENT)
        note?.let { Text(it, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp)) }
    }
}

/** Where a list item sits in its group's card: the card is drawn a piece per item. */
private enum class Segment { Single, Top, Middle, Bottom }

/**
 * A group's settings on one card, like the home screen's info panel. Each list item draws
 * its piece ([segment]), with a thin line between settings.
 */
@Composable
private fun SettingsCard(segment: Segment, content: @Composable ColumnScope.() -> Unit) {
    val radius = 20.dp
    val shape = when (segment) {
        Segment.Single -> RoundedCornerShape(radius)
        Segment.Top -> RoundedCornerShape(topStart = radius, topEnd = radius)
        Segment.Middle -> RectangleShape
        Segment.Bottom -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    }
    val first = segment == Segment.Single || segment == Segment.Top
    val last = segment == Segment.Single || segment == Segment.Bottom
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CARD_BACKGROUND)
            .drawBehind {
                if (!first) {
                    val inset = 24.dp.toPx()
                    drawLine(CARD_DIVIDER, Offset(inset, 0f), Offset(size.width - inset, 0f), strokeWidth = 1.dp.toPx())
                }
            }
            .padding(start = 24.dp, end = 16.dp, top = if (first) 12.dp else 6.dp, bottom = if (last) 12.dp else 6.dp),
        content = content
    )
}

private val SECTION_ACCENT = Color(0xFFB9A6FF)
/** As the home screen's info panel. */
private val CARD_BACKGROUND = Color(0x1FFFFFFF)
private val CARD_DIVIDER = Color(0x14FFFFFF)

@Composable
fun ChoiceSetting(
    label: String,
    value: String,
    choices: List<String>,
    /** How a stored value is shown, in the TV's language. */
    display: @Composable (String) -> String = { settingValueLabel(it) },
    modifier: Modifier = Modifier,
    onSelected: (String) -> Unit
) {
    SettingLine(label) {
        SegmentedChoice(value, choices, display, modifier, onSelected)
    }
}

@Composable
fun DeviceNameSetting(value: String, onSaved: (String) -> Unit) {
    TextSetting(
        value = value,
        label = stringResource(R.string.setting_device_name),
        canSave = { it.isNotBlank() },
        onSaved = onSaved
    )
}

/**
 * A text field with a Save button beside it (not inside: a remote's left/right move the cursor
 * in a field, so a button within it can't be reached). Right at the end of the text moves to
 * Save, and the on-screen keyboard's Done key saves too. Save is grey while there's nothing
 * new to save.
 */
@Composable
private fun TextSetting(
    value: String,
    label: String,
    canSave: (String) -> Boolean,
    onSaved: (String) -> Unit,
    password: Boolean = false,
    filter: (String) -> String = { it },
    supportingText: (@Composable (String) -> Unit)? = null,
    isError: (String) -> Boolean = { false }
) {
    var editing by remember(value) { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val savable = canSave(editing.text) && editing.text != value
    val save = {
        if (savable) onSaved(editing.text)
        keyboard?.hide()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = editing,
            onValueChange = { changed ->
                val text = filter(changed.text)
                editing = if (text == changed.text) changed else TextFieldValue(text, TextRange(text.length))
            },
            label = { Text(label) },
            supportingText = supportingText?.let { content -> { content(editing.text) } },
            isError = isError(editing.text),
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.NumberPassword else KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier
                .weight(1f)
                .onPreviewKeyEvent { event ->
                    val atEnd = editing.selection.collapsed && editing.selection.end == editing.text.length
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight && atEnd) {
                        focusManager.moveFocus(FocusDirection.Right)
                    } else {
                        false
                    }
                }
        )
        Spacer(modifier = Modifier.width(16.dp))
        HomeButton(stringResource(R.string.action_save), muted = !savable) { save() }
    }
}

private const val ACCESS_OPEN = "Not required"
private const val ACCESS_PASSWORD = "Required"
private const val TAKEOVER_REFUSE = "Refuse it"
private const val TAKEOVER_ALLOW = "Let it take over"

/**
 * Casting with or without a password. "Required" only takes effect once a valid password
 * is saved, so choosing it can't lock everyone out by accident.
 */
@Composable
fun AccessSetting(settings: ReceiverSettings, viewModel: AirPlayViewModel) {
    var choosingPassword by remember { mutableStateOf(false) }
    ChoiceSetting(
        stringResource(R.string.setting_password),
        // Shows "Required" while a password is being chosen, though it applies only once saved.
        if (settings.requirePassword || choosingPassword) ACCESS_PASSWORD else ACCESS_OPEN,
        listOf(ACCESS_OPEN, ACCESS_PASSWORD),
        display = { stringResource(if (it == ACCESS_PASSWORD) R.string.setting_password_on else R.string.setting_password_off) }
    ) { choice ->
        if (choice == ACCESS_OPEN) {
            choosingPassword = false
            viewModel.updateSettings { it.copy(requirePassword = false) }
        } else if (ReceiverSettings.isValidPin(settings.pin)) {
            viewModel.updateSettings { it.copy(requirePassword = true) }
        } else {
            choosingPassword = true  // enabled when a valid password is saved below
        }
    }
    if (settings.requirePassword || choosingPassword) {
        PinSetting(settings.pin) { pin ->
            choosingPassword = false
            viewModel.updateSettings { it.copy(pin = pin, requirePassword = true) }
        }
    }
}

/** The password senders must enter: at least [ReceiverSettings.MIN_PIN_LENGTH] digits. */
@Composable
fun PinSetting(value: String, onSaved: (String) -> Unit) {
    TextSetting(
        value = value,
        label = stringResource(R.string.setting_password_field),
        canSave = ReceiverSettings::isValidPin,
        onSaved = onSaved,
        password = true,
        filter = { it.filter(Char::isDigit) },
        supportingText = { text ->
            Text(
                if (!ReceiverSettings.isValidPin(text)) stringResource(R.string.setting_password_too_short, ReceiverSettings.MIN_PIN_LENGTH)
                else stringResource(R.string.setting_password_help)
            )
        },
        isError = { text -> text.isNotEmpty() && !ReceiverSettings.isValidPin(text) }
    )
}

@Composable
fun SwitchSetting(label: String, checked: Boolean, modifier: Modifier = Modifier, onChanged: (Boolean) -> Unit) {
    SettingLine(label) {
        // Off and On side by side, like the other settings' choices.
        SegmentedChoice(checked, listOf(false, true), { stringResource(if (it) R.string.on else R.string.off) }, modifier, onChanged)
    }
}

/** One setting: its name on the left, its choices on the right. */
@Composable
private fun SettingLine(label: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
        control()
    }
}
