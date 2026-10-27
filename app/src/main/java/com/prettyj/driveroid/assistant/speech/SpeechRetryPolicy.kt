package com.prettyj.driveroid.assistant.speech

import kotlin.math.min

/**
 * Back-off schedule used when a recognition session fails for a recoverable reason (busy service,
 * dropped network, microphone temporarily held by another app).
 *
 * Pure and side-effect free so the schedule can be unit tested on the JVM.
 */
object SpeechRetryPolicy {

    const val INITIAL_DELAY_MILLIS = 500L
    const val MAX_DELAY_MILLIS = 15_000L

    /** Highest doubling that is applied before falling back to [MAX_DELAY_MILLIS]. */
    private const val MAX_SHIFT = 5

    /**
     * @param failureCount 1 for the first failure of a streak; the counter is reset on every
     *  successfully recognised utterance.
     * @return milliseconds to wait before the next attempt: 500, 1000, 2000, 4000, 8000 and then
     *  capped at [MAX_DELAY_MILLIS].
     */
    fun delayMillisFor(failureCount: Int): Long {
        if (failureCount <= 1) return INITIAL_DELAY_MILLIS
        val doublings = (failureCount - 1).coerceAtMost(MAX_SHIFT)
        return min(INITIAL_DELAY_MILLIS shl doublings, MAX_DELAY_MILLIS)
    }
}
