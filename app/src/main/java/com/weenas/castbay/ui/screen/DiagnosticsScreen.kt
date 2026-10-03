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
import com.weenas.castbay.ui.AppBackground
import com.weenas.castbay.ui.QrCode
import com.weenas.castbay.util.AppVersion
import com.weenas.castbay.util.Diagnostics
import com.weenas.castbay.util.LogReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * About → Diagnostics: what this device is and what recently happened to the app (see
 * [Diagnostics]), to photograph when reporting a problem from a device without adb, or to
 * upload with the app's recent log ([LogReport]) when the person asks.
 * Each line takes the focus in turn, so a remote scrolls through them.
 */
@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    val device = remember { Diagnostics.deviceInfo(context) }
    var events by remember { mutableStateOf(Diagnostics.events()) }
    var upload by remember { mutableStateOf<Upload>(Upload.Idle) }
    val scope = rememberCoroutineScope()

    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.diagnostics_title), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.weight(1f))
                    HomeButton(stringResource(R.string.report_upload), onClick = {
                        upload = Upload.Preparing
                        scope.launch {
                            val text = withContext(Dispatchers.IO) { LogReport.build(context) }
                            upload = Upload.Confirm(text)
                        }
                    })
                    Spacer(modifier = Modifier.width(12.dp))
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
                        upload = result.fold({ Upload.Done(it) }, { Upload.Failed(text, it is LogReport.TooManyReports) })
                    }
                }, onCancel = { upload = Upload.Idle })
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
    class Failed(val text: String, val tooMany: Boolean) : Upload
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
                    PanelText(stringResource(if (upload.tooMany) R.string.report_too_many else R.string.report_failed), color = Color(0xFFFF8A80))
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
