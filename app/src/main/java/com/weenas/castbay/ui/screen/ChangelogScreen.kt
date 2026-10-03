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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.BuildConfig
import com.weenas.castbay.R
import com.weenas.castbay.ui.AppBackground
import com.weenas.castbay.util.AppVersion
import com.weenas.castbay.viewmodel.AirPlayViewModel

/**
 * About → What's new: the release notes of each version after the installed one, up to the
 * update found, newest first, in the app's language; Download and install at the top. Each
 * point takes the focus in turn, so a remote scrolls through them.
 */
@Composable
fun ChangelogScreen(viewModel: AirPlayViewModel) {
    val context = LocalContext.current
    val update by viewModel.update.collectAsState()
    val installed = remember { AppVersion.name(context) }
    val chinese = LocalConfiguration.current.locales[0].language == "zh"
    val current = update ?: return
    val notes = current.notesSince(installed)

    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(stringResource(R.string.changelog_title, current.version), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    stringResource(R.string.changelog_installed, installed),
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                )
                if (BuildConfig.SELF_UPDATE && current.apkUrls.isNotEmpty()) {
                    UpdateAction(viewModel, current.version, Modifier.padding(bottom = 12.dp))
                }
            }
            notes.forEach { release ->
                item {
                    Text(
                        if (release.date.isEmpty()) release.version else "${release.version} · ${release.date}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                    )
                }
                items(if (chinese) release.zh else release.en) { NotePoint(it) }
            }
        }
    }
}

/** One point of a version's notes; focusable so the remote can move through the list. */
@Composable
private fun NotePoint(text: String) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.background(Color.White.copy(alpha = 0.12f)).border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)) else Modifier)
            .focusable()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text("•", fontSize = 18.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(22.dp))
        Text(text, fontSize = 18.sp, color = Color.White.copy(alpha = 0.9f), lineHeight = 26.sp)
    }
}
