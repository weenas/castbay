package com.weenas.castbay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.service.AirPlayManager
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The sender's volume, shown for a moment whenever it changes (the phone's volume buttons
 * or slider), like Apple TV's: nothing on the TV itself says how loud the phone has set it.
 */
@Composable
fun VolumeIndicator(volume: AirPlayManager.SenderVolume?, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    val changes = volume?.changes ?: 0
    LaunchedEffect(changes) {
        if (changes == 0) return@LaunchedEffect  // the level a sender connects with
        visible = true
        delay(VISIBLE_MS)
        visible = false
    }
    val level by animateFloatAsState(volume?.level ?: 0f, label = "volumeLevel")
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xCC1C1A22))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Icon(MediaIcons.Speaker, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(level)
                        .clip(RoundedCornerShape(3.dp))
                        .background(VOLUME_ACCENT)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                "${((volume?.level ?: 0f) * 100).roundToInt()}",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.width(36.dp)
            )
        }
    }
}

private const val VISIBLE_MS = 2000L
/** The music screen's lighter purple (progress bar, focus glow). */
private val VOLUME_ACCENT = Color(0xFFA48BF5)
