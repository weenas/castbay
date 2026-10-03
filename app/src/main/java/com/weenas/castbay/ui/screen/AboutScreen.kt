package com.weenas.castbay.ui.screen

import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import android.net.Uri
import android.content.Intent
import com.weenas.castbay.ui.usingKeys
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.BuildConfig
import com.weenas.castbay.R
import com.weenas.castbay.service.UpdateInstall
import com.weenas.castbay.ui.AppBackground
import com.weenas.castbay.ui.BrandSlogan
import com.weenas.castbay.ui.BrandTitle
import com.weenas.castbay.ui.QrCode
import com.weenas.castbay.ui.isNarrowScreen
import com.weenas.castbay.util.AppVersion
import com.weenas.castbay.viewmodel.AirPlayViewModel

const val WEBSITE_URL = "https://castbay.weenas.com"
private const val PRIVACY_URL = "https://castbay.weenas.com/privacy"
private const val SOURCE_URL = "https://github.com/weenas/castbay"
/** The latest APK, from the website (short enough to type on a TV, too). */
private const val APK_URL = "https://castbay.weenas.com/apk"

/**
 * The app, its version and where to find more: the website, the privacy policy and the source,
 * each a link the device's browser opens, and a QR code to download CastBay with a phone (to
 * pass it on, or to update by hand). A TV without a browser shows a link's QR code instead.
 */
@Composable
fun AboutScreen(viewModel: AirPlayViewModel) {
    val context = LocalContext.current
    val version = remember { AppVersion.name(context) }
    val update by viewModel.update.collectAsState()
    // A link this device couldn't open (no browser, as on many TVs): its QR code, for a phone.
    var linkForPhone by remember { mutableStateOf<String?>(null) }
    val qrUrl = linkForPhone ?: APK_URL
    val qr = remember(qrUrl) { QrCode.bitmap(qrUrl, 512)?.asImageBitmap() }
    val open = { url: String ->
        val opened = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        linkForPhone = if (opened) null else url
    }

    // Beside each other on a TV or a car's screen; the QR code goes below on a phone held
    // upright, where the text would otherwise be squeezed to a letter a line.
    val narrow = isNarrowScreen()
    val info: @Composable ColumnScope.() -> Unit = {
        BrandTitle()
        Spacer(modifier = Modifier.height(12.dp))
        BrandSlogan()
        Spacer(modifier = Modifier.height(6.dp))
        Text(stringResource(R.string.app_tagline), fontSize = 20.sp, color = Color.White.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(28.dp))
        AboutLine(stringResource(R.string.info_version), version)
        if (update == null && BuildConfig.SELF_UPDATE) CheckAction(viewModel)
        update?.let {
            Text(
                stringResource(R.string.about_update, it.version),
                fontSize = 18.sp,
                color = UPDATE_COLOR,
                modifier = Modifier.padding(start = 120.dp, bottom = 4.dp)
            )
            val hasNotes = it.notesSince(version).isNotEmpty()
            if (BuildConfig.SELF_UPDATE && it.apkUrls.isNotEmpty()) {
                UpdateAction(viewModel, it.version, onNotes = if (hasNotes) viewModel::navigateToChangelog else null)
            } else if (hasNotes) {
                // Store builds don't install, but can still say what is new.
                HomeButton(
                    stringResource(R.string.update_notes), Modifier.padding(start = 120.dp, top = 8.dp, bottom = 12.dp),
                    muted = true, onClick = viewModel::navigateToChangelog
                )
            }
        }
        LinkLine(stringResource(R.string.about_website), WEBSITE_URL) { open(WEBSITE_URL) }
        LinkLine(stringResource(R.string.about_privacy), PRIVACY_URL) { open(PRIVACY_URL) }
        LinkLine(stringResource(R.string.about_source), SOURCE_URL) { open(SOURCE_URL) }
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.about_license), fontSize = 15.sp, color = Color.White.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(16.dp))
        // Out of the way: for reporting a problem on a device without adb (a car's).
        HomeButton(stringResource(R.string.about_diagnostics), muted = true, onClick = { viewModel.navigateToDiagnostics() })
    }
    val qrCode: @Composable (ImageBitmap) -> Unit = { bitmap ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                bitmap = bitmap,
                contentDescription = qrUrl,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(10.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                linkForPhone?.let { stringResource(R.string.about_scan_link, it.removePrefix("https://")) }
                    ?: stringResource(if (update != null) R.string.about_scan_update else R.string.about_scan),
                fontSize = 15.sp,
                color = if (update != null && linkForPhone == null) UPDATE_COLOR else Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 260.dp)
            )
        }
    }

    AppBackground {
        // Centred on a TV; on a short screen (a car's, a phone on its side) it scrolls rather
        // than cutting off what's below the version.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val scrolling = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
            if (narrow) {
                Column(modifier = scrolling.padding(horizontal = 24.dp, vertical = 32.dp)) {
                    info()
                    if (qr != null) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { qrCode(qr) }
                    }
                }
            } else {
                Row(
                    modifier = scrolling.padding(horizontal = 64.dp, vertical = 40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), content = info)
                    if (qr != null) {
                        Spacer(modifier = Modifier.width(48.dp))
                        qrCode(qr)
                    }
                }
            }
        }
    }
}

/** Checks for a newer version now, and says how it went; a newer one replaces it with [UpdateAction]. */
@Composable
private fun CheckAction(viewModel: AirPlayViewModel) {
    val state by viewModel.updateCheck.collectAsState()
    val focus = remember { FocusRequester() }
    val keys = usingKeys()
    LaunchedEffect(Unit) { if (keys) focus.requestFocus() }
    Row(
        modifier = Modifier.padding(start = 120.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val checking = state == AirPlayViewModel.UpdateCheck.CHECKING
        HomeButton(
            text = stringResource(if (checking) R.string.update_checking else R.string.update_check),
            muted = checking,
            modifier = Modifier.focusRequester(focus),
            onClick = { viewModel.checkForUpdateNow() }
        )
        val note = when (state) {
            AirPlayViewModel.UpdateCheck.UP_TO_DATE -> R.string.update_latest
            AirPlayViewModel.UpdateCheck.FAILED -> R.string.update_check_failed
            else -> null
        }
        note?.let {
            Spacer(modifier = Modifier.width(16.dp))
            Text(stringResource(it), fontSize = 15.sp, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

/**
 * Downloads and installs the new version when pressed, with how far it has got; focused on
 * opening, so OK is all it takes.
 */
@Composable
internal fun UpdateAction(
    viewModel: AirPlayViewModel,
    version: String,
    modifier: Modifier = Modifier.padding(start = 120.dp, top = 8.dp, bottom = 12.dp),
    /** Opens the new versions' release notes; no button without it. */
    onNotes: (() -> Unit)? = null
) {
    val state by viewModel.updateInstall.collectAsState()
    val focus = remember { FocusRequester() }
    val keys = usingKeys()
    LaunchedEffect(Unit) { if (keys) focus.requestFocus() }
    Column(modifier = modifier) {
        val busy = state is UpdateInstall.Downloading
        HomeButton(
            text = when (val s = state) {
                is UpdateInstall.Downloading -> s.progress?.let { stringResource(R.string.update_downloading, (it * 100).toInt()) }
                    ?: stringResource(R.string.update_downloading_unknown)
                is UpdateInstall.Failed -> stringResource(R.string.update_retry)
                else -> stringResource(R.string.update_install, version)
            },
            muted = busy,
            modifier = Modifier.focusRequester(focus),
            onClick = { if (!busy) viewModel.installUpdate() }
        )
        // Below it: beside it, the two didn't fit next to the QR code.
        if (onNotes != null) HomeButton(stringResource(R.string.update_notes), Modifier.padding(top = 10.dp), muted = true, onClick = onNotes)
        val note = when (val s = state) {
            UpdateInstall.Installing -> R.string.update_installing
            is UpdateInstall.Failed -> when (s.reason) {
                UpdateInstall.Reason.DOWNLOAD -> R.string.update_failed_download
                UpdateInstall.Reason.CHECKSUM -> R.string.update_failed_checksum
                UpdateInstall.Reason.INSTALLER -> R.string.update_failed_installer
            }
            else -> null
        }
        note?.let {
            Text(
                stringResource(it),
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 8.dp).widthIn(max = 520.dp)
            )
        }
    }
}

/** A labelled link: the accent colour, outlined when focused; OK or a tap opens it. */
@Composable
private fun LinkLine(label: String, url: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 18.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(120.dp))
        Text(
            url.removePrefix("https://"),
            fontSize = 18.sp,
            color = LINK_COLOR,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .onFocusChanged { focused = it.isFocused }
                .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(label, fontSize = 18.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(120.dp))
        Text(value, fontSize = 18.sp, color = Color.White)
    }
}

private val LINK_COLOR = Color(0xFFCBBDFF)

/** The new-version notice: the accent purple, bright on the dark background. */
private val UPDATE_COLOR = Color(0xFFB9A6FF)
