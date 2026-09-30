package com.weenas.castbay.ui

import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager

/**
 * Whether this is a touch screen (a car's head unit, a tablet) rather than a TV worked with a
 * remote: help and hints then describe taps instead of keys.
 */
@Composable
fun hasTouchScreen(): Boolean {
    val context = LocalContext.current
    return remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) }
}

/**
 * Whether the person is using keys (a remote, a steering wheel's arrows) rather than touch,
 * as last used. Screens give an element focus up front only then: on a touch screen that
 * outline looks as if it had already been pressed.
 */
@Composable
fun usingKeys(): Boolean = LocalInputModeManager.current.inputMode == InputMode.Keyboard
