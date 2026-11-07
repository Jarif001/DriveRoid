package com.prettyj.driveroid.assistant

/** The result of parsing a recognised command transcript. */
sealed class Command {
    /** Place a phone call to a contact. */
    data class CallPhone(val contactName: String) : Command()

    /** Place a Messenger call to a contact (searched by name). */
    data class CallMessenger(val contactName: String) : Command()

    /** Place a WhatsApp call to a contact (resolved by phone number). */
    data class CallWhatsApp(val contactName: String, val phoneNumber: String) : Command()

    /** The transcript could not be understood as any supported command. */
    data class Unknown(val transcript: String) : Command()
}

/**
 * Parses a recognised command transcript into a [Command].
 *
 * Supported forms:
 *   - "call ammu"           → CallPhone("amu")
 *   - "call bappi in messenger" → CallMessenger("bappi")
 *   - "call mother in whatsapp" → CallWhatsApp("mother", "") — number resolved later
 *   - "call mother in whats app" → CallWhatsApp("mother", "")
 *   - "call mother in whats up" → CallWhatsApp("mother", "")
 *   - "call h a s n a t"          → CallPhone("hasnat")  (spelled-out name)
 *   - "call h a s n a t in whatsapp" → CallWhatsApp("hasnat", "")
 *
 * When the name part consists of single letters separated by spaces (for example a name the
 * recogniser cannot pronounce), the letters are concatenated into one word.
 */
object CommandParser {
    private const val CALL_PREFIX = "call "

    private val messengerSuffixes = listOf(" in messenger")
    private val whatsappSuffixes = listOf(" in whatsapp", " in whats app", " in whats up")

    fun parse(transcript: String): Command {
        val text = transcript.trim().lowercase()
        if (!text.startsWith(CALL_PREFIX)) {
            return Command.Unknown(transcript)
        }

        val afterCall = text.substring(CALL_PREFIX.length).trim()

        // Detect and strip any suffix first so the name part is clean.
        var namePart = afterCall
        var suffix: String? = null

        for (s in messengerSuffixes) {
            if (afterCall.endsWith(s)) {
                namePart = afterCall.substring(0, afterCall.length - s.length).trim()
                suffix = s
                break
            }
        }
        if (suffix == null) {
            for (s in whatsappSuffixes) {
                if (afterCall.endsWith(s)) {
                    namePart = afterCall.substring(0, afterCall.length - s.length).trim()
                    suffix = s
                    break
                }
            }
        }

        // If the name part is a sequence of single letters separated by spaces, concatenate them.
        val contactName = buildSpelledName(namePart)

        return when (suffix) {
            " in messenger" -> if (contactName.isNotEmpty()) Command.CallMessenger(contactName) else Command.Unknown(transcript)
            " in whatsapp", " in whats app", " in whats up" -> if (contactName.isNotEmpty()) Command.CallWhatsApp(contactName, "") else Command.Unknown(transcript)
            null -> if (contactName.isNotEmpty()) Command.CallPhone(contactName) else Command.Unknown(transcript)
            else -> Command.Unknown(transcript)
        }
    }

    /**
     * Builds a contact name from [namePart], handling two cases:
     *
     * 1. Spelled-out name with spaces: "h a s n a t" → "hasnat"
     * 2. Spelled-out name with some letters together: "h as nat" → "hasnat"
     * 3. Spelled-out name with punctuation between letters: "h.a.s.n.a.t" → "hasnat"
     *
     * If [namePart] contains no spaces (already a single word) or contains non-letter characters
     * that cannot be stripped, it is returned unchanged.
     */
    private fun buildSpelledName(namePart: String): String {
        if (namePart.isEmpty()) return ""

        // Check if there are whitespace or punctuation separators between letters.
        val hasSeparators = namePart.contains(Regex("[\\s.,;:!?]"))
        if (!hasSeparators) return namePart

        // Remove all whitespace and common punctuation that recognisers insert between letters.
        val cleaned = namePart.replace(Regex("[\\s.,;:!?]+"), "")

        // Only treat as spelled-out if the result is purely alphabetic.
        if (cleaned.isNotEmpty() && cleaned.all { it in 'a'..'z' }) {
            return cleaned
        }

        return namePart
    }
}
