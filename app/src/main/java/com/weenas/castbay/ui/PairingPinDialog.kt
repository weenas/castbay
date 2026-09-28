package com.weenas.castbay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.weenas.castbay.R
import com.weenas.castbay.ui.screen.HomeButton

/**
 * The PIN a new device must enter to pair (PIN pairing), over whatever screen is showing, as
 * on an Apple TV. A dialog, so it takes the remote: Back or Close puts it away, and the device
 * gets a new PIN when it tries again.
 */
@Composable
fun PairingPinDialog(pin: String?, onDismiss: () -> Unit) {
    if (pin == null) return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2E2745), Color(0xFF1B1826))))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(28.dp))
                .padding(horizontal = 64.dp, vertical = 40.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_castbay_mark), contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.app_name), color = Color.White.copy(alpha = 0.8f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.pairing_pin_title), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pin.forEach { digit ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = 88.dp, height = 112.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(BorderStroke(2.dp, PIN_ACCENT), RoundedCornerShape(18.dp))
                    ) {
                        Text(digit.toString(), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(stringResource(R.string.pairing_pin_help), color = Color.White.copy(alpha = 0.6f), fontSize = 18.sp)
            Spacer(modifier = Modifier.height(28.dp))
            val focus = remember { FocusRequester() }
            HomeButton(stringResource(R.string.close), Modifier.focusRequester(focus), onClick = onDismiss)
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
    }
}

/** The app's lighter purple, as on the music screen. */
private val PIN_ACCENT = Color(0xFFA48BF5)
