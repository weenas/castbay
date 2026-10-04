package com.weenas.castbay.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.R
import com.weenas.castbay.service.NetworkCheck
import com.weenas.castbay.ui.AppBackground
import com.weenas.castbay.ui.QrCode
import com.weenas.castbay.util.AppVersion
import com.weenas.castbay.util.Diagnostics
import com.weenas.castbay.util.LogReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * About → Diagnostics: what this device is and what recently happened to the app (see
 * [Diagnostics]), to photograph when reporting a problem from a device without adb, or to
 * upload with the app's recent log ([LogReport]) when the person asks. Run network check
 * ([NetworkCheck]) explains, item by item, why a phone may not find the receiver.
 * Each line takes the focus in turn, so a remote scrolls through them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    val device = remember { Diagnostics.deviceInfo(context) }
    var events by remember { mutableStateOf(Diagnostics.events()) }
    var upload by remember { mutableStateOf<Upload>(Upload.Idle) }
    var checking by remember { mutableStateOf(false) }
    var checkResults by remember { mutableStateOf<List<NetworkCheck.Result>?>(null) }
    val scope = rememberCoroutineScope()

    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Text(stringResource(R.string.diagnostics_title), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
                // Wraps on a narrow screen (a phone, a tablet upright) rather than squeezing the buttons.
                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeButton(stringResource(if (checking) R.string.netcheck_running else R.string.netcheck_run), onClick = {
                        if (checking) return@HomeButton
                        checking = true
                        scope.launch {
                            checkResults = NetworkCheck.run(context)
                            checking = false
                            // The check's outcome is now among the events.
                            events = Diagnostics.events()
                        }
                    })
                    HomeButton(stringResource(R.string.report_upload), onClick = {
                        upload = Upload.Preparing
                        scope.launch {
                            val text = withContext(Dispatchers.IO) { LogReport.build(context) }
                            upload = Upload.Confirm(text)
                        }
                    })
                    HomeButton(stringResource(R.string.diagnostics_clear), muted = true, onClick = {
                        Diagnostics.clear()
                        events = Diagnostics.events()
                    })
                }
                Text(stringResource(R.string.diagnostics_note), fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                UploadPanel(upload, onUpload = { text ->
                    upload = Upload.Sending
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { LogReport.upload(context, text, AppVersion.name(context)) }
                        upload = result.fold({ Upload.Done(it) }, { Upload.Failed(text, it is LogReport.TooManyReports, LogReport.reason(it)) })
                        // The upload (or why it failed) is now among the events.
                        events = Diagnostics.events()
                    }
                }, onCancel = { upload = Upload.Idle })
                if (checkResults != null) {
                    Heading(stringResource(R.string.netcheck_heading))
                    Text(stringResource(R.string.netcheck_intro), fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            items(checkResults.orEmpty()) { CheckRow(it) }
            item {
                if (checkResults != null) Spacer(modifier = Modifier.height(16.dp))
                Heading(stringResource(R.string.diagnostics_device))
            }
            items(device) { (label, value) -> Line("$label: $value") }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Heading(stringResource(R.string.diagnostics_events))
                if (events.isEmpty()) Line(stringResource(R.string.diagnostics_none))
            }
            items(events) { Line(Diagnostics.format(it)) }
        }
    }
}

/** Where an upload of the report is: asked for, read to the person, sent, or not. */
private sealed interface Upload {
    data object Idle : Upload
    data object Preparing : Upload
    class Confirm(val text: String) : Upload
    data object Sending : Upload
    class Done(val id: String) : Upload
    class Failed(val text: String, val tooMany: Boolean, val reason: String) : Upload
}

/**
 * Below the note: what an upload will send, to confirm; then the report's ID, with a QR code
 * for the feedback page on the phone (which fills it into a problem report).
 */
@Composable
private fun UploadPanel(upload: Upload, onUpload: (String) -> Unit, onCancel: () -> Unit) {
    if (upload == Upload.Idle) return
    val first = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .padding(bottom = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(20.dp)
    ) {
        when (upload) {
            Upload.Idle -> {}
            Upload.Preparing, Upload.Sending -> PanelText(stringResource(
                if (upload == Upload.Sending) R.string.report_sending else R.string.report_preparing
            ))
            is Upload.Confirm, is Upload.Failed -> {
                val text = if (upload is Upload.Confirm) upload.text else (upload as Upload.Failed).text
                if (upload is Upload.Failed) {
                    PanelText(if (upload.tooMany) stringResource(R.string.report_too_many) else stringResource(R.string.report_failed, upload.reason), color = Color(0xFFFF8A80))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                PanelText(stringResource(R.string.report_confirm, (text.toByteArray().size + 1023) / 1024))
                Spacer(modifier = Modifier.height(14.dp))
                Row {
                    HomeButton(
                        stringResource(if (upload is Upload.Failed) R.string.report_retry else R.string.report_send),
                        Modifier.focusRequester(first),
                        onClick = { onUpload(text) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    HomeButton(stringResource(R.string.report_cancel), muted = true, onClick = onCancel)
                }
                LaunchedEffect(upload) { runCatching { first.requestFocus() } }
            }
            is Upload.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
                val link = "$FEEDBACK_URL?report=${upload.id}"
                val qr = remember(link) { QrCode.bitmap(link, 400)?.asImageBitmap() }
                if (qr != null) {
                    Image(
                        bitmap = qr,
                        contentDescription = link,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                }
                Column {
                    PanelText(stringResource(R.string.report_done))
                    Text(
                        upload.id, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    PanelText(stringResource(R.string.report_done_hint))
                    Spacer(modifier = Modifier.height(12.dp))
                    HomeButton(stringResource(R.string.report_close), Modifier.focusRequester(first), muted = true, onClick = onCancel)
                    LaunchedEffect(upload) { runCatching { first.requestFocus() } }
                }
            }
        }
    }
}

private const val FEEDBACK_URL = "https://castbay.weenas.com/feedback"

@Composable
private fun PanelText(text: String, color: Color = Color.White.copy(alpha = 0.85f)) {
    Text(text, fontSize = 15.sp, color = color)
}

@Composable
private fun Heading(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(bottom = 6.dp))
}

/** One item of the network check: its outcome, what was found and, if any, what to do. Focusable for the remote. */
@Composable
private fun CheckRow(result: NetworkCheck.Result) {
    var focused by remember { mutableStateOf(false) }
    val (badge, badgeColor) = when (result.outcome) {
        NetworkCheck.Outcome.PASS -> stringResource(R.string.netcheck_pass) to Color(0xFF69F0AE)
        NetworkCheck.Outcome.PROBLEM -> stringResource(R.string.netcheck_problem) to Color(0xFFFF8A80)
        NetworkCheck.Outcome.CANT_VERIFY -> stringResource(R.string.netcheck_cant_verify) to Color(0xFFFFD180)
    }
    val (explanation, next) = checkText(result)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .background(Color.White.copy(alpha = if (focused) 0.14f else 0.06f))
            .then(if (focused) Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(10.dp)) else Modifier)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(badge, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = badgeColor, modifier = Modifier.widthIn(min = 96.dp))
            Text(stringResource(checkTitle(result.item)), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        Text(explanation, fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(start = 96.dp, top = 2.dp))
        if (next != null) {
            Text(stringResource(R.string.netcheck_next, next), fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 96.dp, top = 2.dp))
        }
    }
}

private fun checkTitle(item: NetworkCheck.Item) = when (item) {
    NetworkCheck.Item.RECEIVER -> R.string.netcheck_item_receiver
    NetworkCheck.Item.NETWORK -> R.string.netcheck_item_network
    NetworkCheck.Item.AIRPLAY_SERVICE -> R.string.netcheck_item_airplay
    NetworkCheck.Item.RAOP_SERVICE -> R.string.netcheck_item_raop
    NetworkCheck.Item.LISTENER -> R.string.netcheck_item_listener
    NetworkCheck.Item.LAST_FAILURE -> R.string.netcheck_item_last_failure
    NetworkCheck.Item.SAME_NETWORK -> R.string.netcheck_item_same_network
}

/** What was found, and the next step (null when there is none). */
@Composable
private fun checkText(result: NetworkCheck.Result): Pair<String, String?> {
    val text = result.text.orEmpty()
    val port = result.port ?: 0
    val failedNext = stringResource(R.string.netcheck_service_failed_next)
    return when (result.reason) {
        NetworkCheck.Reason.RECEIVER_RUNNING -> stringResource(R.string.netcheck_receiver_running) to null
        NetworkCheck.Reason.RECEIVER_OFF -> stringResource(R.string.netcheck_receiver_off) to stringResource(R.string.netcheck_receiver_start)
        NetworkCheck.Reason.RECEIVER_ERROR -> stringResource(R.string.netcheck_receiver_error, text) to stringResource(R.string.netcheck_receiver_start)
        NetworkCheck.Reason.NETWORK_WIFI -> (result.ssid?.let { stringResource(R.string.netcheck_network_wifi_named, text, it) }
            ?: stringResource(R.string.netcheck_network_wifi, text)) to null
        NetworkCheck.Reason.NETWORK_ETHERNET -> stringResource(R.string.netcheck_network_ethernet, text) to null
        NetworkCheck.Reason.NETWORK_OTHER -> stringResource(R.string.netcheck_network_other, text) to stringResource(R.string.netcheck_network_other_next)
        NetworkCheck.Reason.NETWORK_NO_ADDRESS -> stringResource(R.string.netcheck_network_no_address) to stringResource(R.string.netcheck_network_no_address_next)
        NetworkCheck.Reason.NETWORK_NONE -> stringResource(R.string.netcheck_network_none) to stringResource(R.string.netcheck_network_none_next)
        NetworkCheck.Reason.NETWORK_UNKNOWN -> stringResource(R.string.netcheck_network_unknown) to stringResource(R.string.netcheck_again_next)
        NetworkCheck.Reason.SERVICE_REGISTERED -> stringResource(R.string.netcheck_service_registered, port) to null
        NetworkCheck.Reason.SERVICE_PENDING -> stringResource(R.string.netcheck_service_pending) to stringResource(R.string.netcheck_again_next)
        NetworkCheck.Reason.SERVICE_FAILED -> stringResource(R.string.netcheck_service_failed, nsdErrorText(result.errorCode)) to failedNext
        NetworkCheck.Reason.SERVICE_OFF -> stringResource(R.string.netcheck_service_off) to null
        NetworkCheck.Reason.LISTENER_OK -> stringResource(R.string.netcheck_listener_ok, port) to null
        NetworkCheck.Reason.LISTENER_MISSING -> stringResource(R.string.netcheck_listener_missing) to stringResource(R.string.netcheck_listener_missing_next)
        NetworkCheck.Reason.LISTENER_UNKNOWN -> stringResource(R.string.netcheck_listener_unknown) to null
        NetworkCheck.Reason.NO_FAILURE -> stringResource(R.string.netcheck_no_failure) to null
        NetworkCheck.Reason.FAILURE_RECOVERED -> stringResource(R.string.netcheck_failure_recovered,
            result.serviceType.orEmpty(), checkTime(result.atMs), nsdErrorText(result.errorCode)) to null
        NetworkCheck.Reason.FAILURE_ACTIVE -> stringResource(R.string.netcheck_failure_active,
            result.serviceType.orEmpty(), checkTime(result.atMs), nsdErrorText(result.errorCode)) to failedNext
        NetworkCheck.Reason.EXTERNAL -> stringResource(R.string.netcheck_external) to stringResource(R.string.netcheck_external_next)
    }
}

@Composable
private fun nsdErrorText(code: Int?): String = when (NetworkCheck.nsdError(code)) {
    NetworkCheck.NsdError.INTERNAL -> stringResource(R.string.netcheck_nsd_internal, code ?: 0)
    NetworkCheck.NsdError.ALREADY_ACTIVE -> stringResource(R.string.netcheck_nsd_already_active, code ?: 0)
    NetworkCheck.NsdError.MAX_LIMIT -> stringResource(R.string.netcheck_nsd_max_limit, code ?: 0)
    NetworkCheck.NsdError.NOT_RUNNING -> stringResource(R.string.netcheck_nsd_not_running, code ?: 0)
    NetworkCheck.NsdError.BAD_PARAMETERS -> stringResource(R.string.netcheck_nsd_bad_parameters, code ?: 0)
    NetworkCheck.NsdError.OTHER -> stringResource(R.string.netcheck_nsd_other, code ?: 0)
    NetworkCheck.NsdError.NO_CODE -> stringResource(R.string.netcheck_nsd_no_code)
}

private fun checkTime(atMs: Long?): String =
    atMs?.let { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(it)) }.orEmpty()

/** One line, monospaced; focusable so the remote can move through the list. */
@Composable
private fun Line(text: String) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        color = Color.White.copy(alpha = 0.85f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.background(Color.White.copy(alpha = 0.12f)).border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(6.dp)) else Modifier)
            .focusable()
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
