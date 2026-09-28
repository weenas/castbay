package com.weenas.castbay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * A dialog over any screen, in the app's style: the mark and name, then [content]. It takes
 * the remote; Back calls [onDismiss].
 */
@Composable
fun CastBayDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
            content()
        }
    }
}

/** The app's lighter purple, as on the music screen. */
val DIALOG_ACCENT = Color(0xFFA48BF5)
