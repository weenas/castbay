package com.weenas.castbay.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentPromptTest {
    @Test
    fun asksWhenNothingWasAnsweredAndBothAreOff() {
        assertTrue(ConsentPrompt.shouldAsk(asked = false, statsOn = false, errorReportsOn = false))
    }

    @Test
    fun doesNotAskAgainOnceAnswered() {
        assertFalse(ConsentPrompt.shouldAsk(asked = true, statsOn = false, errorReportsOn = false))
    }

    @Test
    fun doesNotAskWhenEitherIsAlreadyOn() {
        assertFalse(ConsentPrompt.shouldAsk(asked = false, statsOn = true, errorReportsOn = false))
        assertFalse(ConsentPrompt.shouldAsk(asked = false, statsOn = false, errorReportsOn = true))
    }
}
