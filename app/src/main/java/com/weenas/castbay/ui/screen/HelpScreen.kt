package com.weenas.castbay.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.R
import com.weenas.castbay.ui.AppBackground
import com.weenas.castbay.viewmodel.AirPlayViewModel

/** One topic: its title and text, which may name the TV with %1$s. */
private data class HelpTopic(@StringRes val title: Int, @StringRes val body: Int)

private val TOPICS = listOf(
    HelpTopic(R.string.help_mirror_title, R.string.help_mirror),
    HelpTopic(R.string.help_airplay_apps_title, R.string.help_airplay_apps),
    HelpTopic(R.string.help_dlna_title, R.string.help_dlna),
    HelpTopic(R.string.help_android_title, R.string.help_android),
    HelpTopic(R.string.help_mac_title, R.string.help_mac),
    HelpTopic(R.string.help_remote_title, R.string.help_remote),
    HelpTopic(R.string.help_not_found_title, R.string.help_not_found),
    HelpTopic(R.string.help_video_title, R.string.help_video),
)

/**
 * How to cast from each kind of device, the remote's keys while playing, and what to check when
 * something doesn't work. Topics are cards the remote moves between, which scrolls the page.
 */
@Composable
fun HelpScreen(viewModel: AirPlayViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val name = settings.advertisedName
    val firstTopic = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstTopic.requestFocus() } }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    AppBackground {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 48.dp, top = 24.dp, end = 48.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.help), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(stringResource(R.string.help_more, WEBSITE_URL.removePrefix("https://")), fontSize = 16.sp, color = Color.Gray)
                    }
                    HomeButton(
                        stringResource(R.string.action_back),
                        Modifier.onFocusChanged { if (it.isFocused) scope.launch { gridState.animateScrollToItem(0) } },
                        onClick = onBack
                    )
                }
            }
            TOPICS.forEachIndexed { index, topic ->
                item {
                    HelpCard(
                        title = stringResource(topic.title),
                        body = stringResource(topic.body, name),
                        modifier = (if (index == 0) Modifier.focusRequester(firstTopic) else Modifier)
                            .onFocusChanged { state ->
                                // Focus only scrolls a card to the screen's edge; on the first and
                                // last rows, go all the way so the title and margins show too.
                                if (!state.isFocused) return@onFocusChanged
                                if (index < 2) scope.launch { gridState.animateScrollToItem(0) }
                                if (index >= TOPICS.size - 2) scope.launch { gridState.animateScrollToItem(TOPICS.size) }
                            }
                    )
                }
            }
        }
    }
}

/** A topic card; the focused one gets the white outline the other screens use. */
@Composable
private fun HelpCard(title: String, body: String, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusable(interactionSource = interaction)
            .clip(shape)
            .background(Color(0x1FFFFFFF))
            .border(3.dp, if (focused) Color.White else Color.Transparent, shape)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        Text(body, fontSize = 16.sp, lineHeight = 24.sp, color = Color.White.copy(alpha = 0.8f))
    }
}
