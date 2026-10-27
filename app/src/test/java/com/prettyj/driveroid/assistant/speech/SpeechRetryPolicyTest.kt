package com.prettyj.driveroid.assistant.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the retry schedule that keeps a flaky recogniser from being hammered. */
class SpeechRetryPolicyTest {

    @Test
    fun `doubles the delay for each consecutive failure`() {
        assertEquals(500L, SpeechRetryPolicy.delayMillisFor(1))
        assertEquals(1_000L, SpeechRetryPolicy.delayMillisFor(2))
        assertEquals(2_000L, SpeechRetryPolicy.delayMillisFor(3))
        assertEquals(4_000L, SpeechRetryPolicy.delayMillisFor(4))
        assertEquals(8_000L, SpeechRetryPolicy.delayMillisFor(5))
    }

    @Test
    fun `caps the delay for a long failure streak`() {
        assertEquals(SpeechRetryPolicy.MAX_DELAY_MILLIS, SpeechRetryPolicy.delayMillisFor(6))
        assertEquals(SpeechRetryPolicy.MAX_DELAY_MILLIS, SpeechRetryPolicy.delayMillisFor(20))
        assertTrue(SpeechRetryPolicy.delayMillisFor(6) >= SpeechRetryPolicy.delayMillisFor(5))
    }

    @Test
    fun `never returns less than the initial delay`() {
        assertEquals(SpeechRetryPolicy.INITIAL_DELAY_MILLIS, SpeechRetryPolicy.delayMillisFor(1))
        assertEquals(SpeechRetryPolicy.INITIAL_DELAY_MILLIS, SpeechRetryPolicy.delayMillisFor(0))
        assertEquals(SpeechRetryPolicy.INITIAL_DELAY_MILLIS, SpeechRetryPolicy.delayMillisFor(-5))
    }
}
