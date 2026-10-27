package com.prettyj.driveroid.assistant.wakeword

import com.prettyj.driveroid.util.DriveroidLog
import java.util.Locale

/**
 * Wake-word detection by matching phrases inside recognition transcripts.
 *
 * This is the deliberately simple V1 strategy: the platform recogniser runs continuously and each
 * transcript is tested against [wakeWordPhrases]. It needs no extra audio pipeline, but it does
 * depend on the recogniser transcribing the wake word correctly, and the microphone stays busy for
 * as long as the assistant is active. On-device keyword spotting can replace this class later
 * without touching the controller, because only the [WakeWordDetector] contract is used.
 */
class PhraseWakeWordDetector(
    private val wakeWordPhrases: List<String> = DEFAULT_PHRASES,
) : WakeWordDetector {

    private var armed = false

    override val isArmed: Boolean
        get() = armed

    override fun arm() {
        if (armed) {
            DriveroidLog.d(TAG, "arm() ignored: wake word detector already armed")
            return
        }
        armed = true
        DriveroidLog.i(TAG, "Wake word armed for phrases $wakeWordPhrases")
    }

    override fun disarm() {
        if (!armed) {
            DriveroidLog.d(TAG, "disarm() ignored: wake word detector already idle")
            return
        }
        armed = false
        DriveroidLog.i(TAG, "Wake word disarmed")
    }

    override fun matchWakeWord(transcript: String): String? {
        if (!armed) return null
        val normalized = normalize(transcript)
        if (normalized.isEmpty()) return null

        // Pad with spaces so phrases only ever match on word boundaries ("driveroid" must not match
        // inside an unrelated word).
        val haystack = " $normalized "
        var bestStart = -1
        var bestEnd = -1
        for (phrase in wakeWordPhrases) {
            val needle = " ${normalize(phrase)} "
            if (needle.isBlank()) continue
            val start = haystack.indexOf(needle)
            if (start < 0) continue
            val end = start + needle.length
            // Earliest match wins; on a tie the longer phrase wins ("hey driveroid" beats "driveroid").
            if (bestStart == -1 || start < bestStart || (start == bestStart && end > bestEnd)) {
                bestStart = start
                bestEnd = end
            }
        }
        if (bestStart < 0) return null
        return haystack.substring(bestEnd).trim()
    }

    /** Lowercase (locale-independent), strip everything but letters/digits/whitespace, collapse spaces. */
    private fun normalize(text: String): String =
        text.lowercase(Locale.ROOT)
            .replace(NON_WORD, " ")
            .replace(SPACES, " ")
            .trim()

    private companion object {
        const val TAG = "PhraseWakeWordDetector"
        val NON_WORD = "[^\\p{L}\\p{N}\\s]+".toRegex()
        val SPACES = "\\s+".toRegex()
        val DEFAULT_PHRASES = listOf("hey driveroid", "ok driveroid", "driveroid")
    }
}
