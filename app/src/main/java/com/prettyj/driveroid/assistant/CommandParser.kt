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
 *   - "call mother in whatsapp" → CallWhatsApp("mother", "<number>") — number resolved later
 *   - "call mother in whats app" → CallWhatsApp("mother", "<number>")
 *   - "call mother in whats up" → CallWhatsApp("mother", "<number>")
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

        // "call <name> in messenger" → messenger call
        for (suffix in messengerSuffixes) {
            if (afterCall.endsWith(suffix)) {
                val name = afterCall.substring(0, afterCall.length - suffix.length).trim()
                return if (name.isNotEmpty()) Command.CallMessenger(name) else Command.Unknown(transcript)
            }
        }

        // "call <name> in whatsapp" / "in whats app" / "in whats up" → WhatsApp call
        for (suffix in whatsappSuffixes) {
            if (afterCall.endsWith(suffix)) {
                val name = afterCall.substring(0, afterCall.length - suffix.length).trim()
                return if (name.isNotEmpty()) Command.CallWhatsApp(name, "") else Command.Unknown(transcript)
            }
        }

        // "call <name>" → phone call
        return if (afterCall.isNotEmpty()) Command.CallPhone(afterCall) else Command.Unknown(transcript)
    }
}
