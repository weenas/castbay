package com.weenas.castbay.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Whether the screen is narrow, as a phone held upright (about 400 dp): side-by-side layouts
 * stack instead. TVs (960 dp) and car displays (1280 dp) are wide, as is a phone on its side.
 */
@Composable
fun isNarrowScreen(): Boolean = LocalConfiguration.current.screenWidthDp < 600
