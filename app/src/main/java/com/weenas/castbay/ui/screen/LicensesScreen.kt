package com.weenas.castbay.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.R
import com.weenas.castbay.ui.AppBackground
import com.weenas.castbay.ui.usingKeys

/** A licence whose full text ships in assets/licenses/. */
private enum class Licence(val title: String, val asset: String) {
    GPL3("GNU General Public License v3.0", "gpl-3.0.txt"),
    LGPL21("GNU Lesser General Public License v2.1", "lgpl-2.1.txt"),
    APACHE2("Apache License 2.0", "apache-2.0.txt"),
    MIT_LLHTTP("MIT License (llhttp)", "mit-llhttp.txt"),
    OPENSSL("OpenSSL License and SSLeay License", "openssl.txt"),
}

/** Code CastBay is built from and ships, with what it does here and its licence. */
private class Component(val name: String, val purpose: Int, val licence: Licence, val url: String)

private val COMPONENTS = listOf(
    Component("CastBay", R.string.licenses_castbay, Licence.GPL3, "github.com/weenas/castbay"),
    Component("UxPlay", R.string.licenses_uxplay, Licence.GPL3, "github.com/FDH2/UxPlay"),
    Component("UxPlay RAOP library (from shairplay / RPiPlay)", R.string.licenses_raop, Licence.LGPL21, "github.com/FDH2/UxPlay"),
    Component("playfair", R.string.licenses_playfair, Licence.GPL3, "github.com/FDH2/UxPlay"),
    Component("llhttp", R.string.licenses_llhttp, Licence.MIT_LLHTTP, "github.com/nodejs/llhttp"),
    Component("libplist", R.string.licenses_libplist, Licence.LGPL21, "github.com/libimobiledevice/libplist"),
    Component("Apple Lossless Audio Codec (ALAC)", R.string.licenses_alac, Licence.APACHE2, "github.com/macosforge/alac"),
    Component("OpenSSL 1.1.1", R.string.licenses_openssl, Licence.OPENSSL, "openssl.org"),
    Component("AndroidX: Jetpack Compose, Media3 (ExoPlayer), Lifecycle, TV", R.string.licenses_androidx, Licence.APACHE2, "developer.android.com/jetpack"),
    Component("Material Components for Android", R.string.licenses_material, Licence.APACHE2, "github.com/material-components"),
    Component("Kotlin, kotlinx.coroutines", R.string.licenses_kotlin, Licence.APACHE2, "kotlinlang.org"),
    Component("ZXing", R.string.licenses_zxing, Licence.APACHE2, "github.com/zxing/zxing"),
    Component("Gson", R.string.licenses_gson, Licence.APACHE2, "github.com/google/gson"),
)

/**
 * About → Open-source licences: what CastBay is built from, each part's licence, and the
 * licences' full texts (as their licences require them to come with the app). Every line takes
 * the focus in turn, so a remote moves through them; Back from a text returns to the list.
 */
@Composable
fun LicensesScreen() {
    var open by remember { mutableStateOf<Licence?>(null) }
    val licence = open
    BackHandler(enabled = licence != null) { open = null }
    AppBackground {
        if (licence == null) ComponentList(onOpen = { open = it }) else LicenceText(licence)
    }
}

@Composable
private fun ComponentList(onOpen: (Licence) -> Unit) {
    val first = remember { FocusRequester() }
    val keys = usingKeys()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(stringResource(R.string.licenses_title), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                stringResource(R.string.licenses_intro),
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
        }
        items(COMPONENTS.size) { i ->
            val component = COMPONENTS[i]
            FocusRow(
                onClick = { onOpen(component.licence) },
                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(component.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(
                        stringResource(component.purpose) + " · " + component.url,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(component.licence.title, fontSize = 14.sp, color = Color(0xFFB9A8FF))
            }
        }
    }
    LaunchedEffect(Unit) { if (keys) runCatching { first.requestFocus() } }
}

/** A licence's full text, a paragraph per line. */
@Composable
private fun LicenceText(licence: Licence) {
    val context = LocalContext.current
    val paragraphs = remember(licence) {
        runCatching { context.assets.open("licenses/${licence.asset}").bufferedReader().use { it.readText() } }
            .getOrDefault("")
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    val first = remember { FocusRequester() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            Text(licence.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
        }
        items(paragraphs.size) { i ->
            FocusRow(onClick = null, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) {
                Text(paragraphs[i], fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
    LaunchedEffect(licence) { runCatching { first.requestFocus() } }
}

/** A row that takes the focus (outlined), and opens something with OK or a tap if [onClick]. */
@Composable
private fun FocusRow(onClick: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.background(Color.White.copy(alpha = 0.12f)).border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(10.dp)) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.focusable())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
