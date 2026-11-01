package com.weenas.castbay.util

import android.content.Context

/**
 * The one-time home screen card that asks whether to send anonymous usage statistics and error
 * reports (both off until the person says yes, and changeable in Settings → General). It is shown
 * until it has been answered once, unless one of the two is already on.
 */
object ConsentPrompt {
    private const val PREFS = "consent"
    private const val KEY_ASKED = "asked"

    fun shouldAsk(asked: Boolean, statsOn: Boolean, errorReportsOn: Boolean): Boolean =
        !asked && !statsOn && !errorReportsOn

    fun asked(context: Context): Boolean = prefs(context).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED, true).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
