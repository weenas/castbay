package com.weenas.castbay.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weenas.castbay.R
import com.weenas.castbay.ui.AppBackground
import com.weenas.castbay.ui.BrandSlogan
import com.weenas.castbay.ui.BrandTitle
import com.weenas.castbay.ui.QrCode
import com.weenas.castbay.util.AppVersion
import com.weenas.castbay.viewmodel.AirPlayViewModel

const val WEBSITE_URL = "https://castbay.weenas.com"
private const val PRIVACY_URL = "castbay.weenas.com/privacy"
private const val SOURCE_URL = "github.com/weenas/castbay"

/**
 * The app, its version and where to find more: the website (also as a QR code, since a TV
 * can't easily open links but a phone can scan one), the privacy policy and the source.
 */
@Composable
fun AboutScreen(viewModel: AirPlayViewModel) {
    val context = LocalContext.current
    val version = remember { AppVersion.name(context) }
    val update by viewModel.update.collectAsState()
    // With a newer version out, the code opens its download page instead of the website.
    val qrUrl = update?.url ?: WEBSITE_URL
    val qr = remember(qrUrl) { QrCode.bitmap(qrUrl, 512)?.asImageBitmap() }

    AppBackground {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BrandTitle()
                Spacer(modifier = Modifier.height(12.dp))
                BrandSlogan()
                Spacer(modifier = Modifier.height(6.dp))
                Text(stringResource(R.string.app_tagline), fontSize = 20.sp, color = Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(28.dp))
                AboutLine(stringResource(R.string.info_version), version)
                update?.let {
                    Text(
                        stringResource(R.string.about_update, it.version),
                        fontSize = 18.sp,
                        color = UPDATE_COLOR,
                        modifier = Modifier.padding(start = 120.dp, bottom = 4.dp)
                    )
                }
                AboutLine(stringResource(R.string.about_website), WEBSITE_URL.removePrefix("https://"))
                AboutLine(stringResource(R.string.about_privacy), PRIVACY_URL)
                AboutLine(stringResource(R.string.about_source), SOURCE_URL)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.about_license), fontSize = 15.sp, color = Color.White.copy(alpha = 0.6f))
            }
            if (qr != null) {
                Spacer(modifier = Modifier.width(48.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = qr,
                        contentDescription = qrUrl,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(10.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(if (update != null) R.string.about_scan_update else R.string.about_scan),
                        fontSize = 15.sp,
                        color = if (update != null) UPDATE_COLOR else Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(label, fontSize = 18.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(120.dp))
        Text(value, fontSize = 18.sp, color = Color.White)
    }
}

/** The new-version notice: the accent purple, bright on the dark background. */
private val UPDATE_COLOR = Color(0xFFB9A6FF)
