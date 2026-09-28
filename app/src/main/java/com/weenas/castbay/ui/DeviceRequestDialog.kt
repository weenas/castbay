package com.weenas.castbay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.R
import com.weenas.castbay.service.AirPlayManager
import com.weenas.castbay.ui.screen.HomeButton

/**
 * A new device asking to cast, while new devices need approval: Allow or Block, remembered
 * (and changeable in Settings). The device was turned away meanwhile, so once allowed it
 * casts when it tries again, which the dialog then says.
 */
@Composable
fun DeviceRequestDialog(
    request: AirPlayManager.DeviceRequest?,
    onAnswer: (allow: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    if (request == null) return
    CastBayDialog(onDismiss) {
        val focus = remember { FocusRequester() }
        if (!request.allowed) {
            Text(
                stringResource(R.string.device_request_title, request.name),
                color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 640.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.device_request_help), color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
            Spacer(modifier = Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                HomeButton(stringResource(R.string.device_allow), Modifier.focusRequester(focus)) { onAnswer(true) }
                HomeButton(stringResource(R.string.device_block), muted = true) { onAnswer(false) }
            }
        } else {
            Text(
                stringResource(R.string.device_request_allowed, request.name),
                color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 640.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.device_request_try_again), color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
            Spacer(modifier = Modifier.height(32.dp))
            HomeButton(stringResource(R.string.close), Modifier.focusRequester(focus), onClick = onDismiss)
        }
        LaunchedEffect(request.allowed) { focus.requestFocus() }
    }
}
