package com.weenas.castbay.ui.screen

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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.R
import com.weenas.castbay.ui.AppBackground
import com.weenas.castbay.util.Diagnostics

/**
 * About → Diagnostics: what this device is and what recently happened to the app (see
 * [Diagnostics]), to photograph when reporting a problem from a device without adb.
 * Each line takes the focus in turn, so a remote scrolls through them.
 */
@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    val device = remember { Diagnostics.deviceInfo(context) }
    var events by remember { mutableStateOf(Diagnostics.events()) }

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
                    HomeButton(stringResource(R.string.diagnostics_clear), muted = true, onClick = {
                        Diagnostics.clear()
                        events = Diagnostics.events()
                    })
                }
                Text(stringResource(R.string.diagnostics_note), fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
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
