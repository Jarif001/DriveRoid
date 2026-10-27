package com.prettyj.driveroid.assistant.wakeword

/**
 * Contract for the wake-word stage of the voice pipeline.
 *
 * The assistant arms the detector when the service starts and disarms it when the service stops,
 * which is what [com.prettyj.driveroid.assistant.VoiceAssistantController] drives. Every recognised
 * utterance is offered to [matchWakeWord], which decides whether the user actually called the
 * assistant.
 *
 * [PhraseWakeWordDetector] implements this by matching phrases in transcripts. An on-device
 * keyword-spotting detector can replace it later without changing the controller, as long as it
 * implements this interface.
 */
interface WakeWordDetector {

    /** `true` while the detector is armed and would react to the wake word. */
    val isArmed: Boolean

    /** Starts listening for the wake word. Must be safe to call while already armed. */
    fun arm()

    /** Stops listening and releases the detector resources. Must be safe to call while idle. */
    fun disarm()

    /**
     * Tests whether [transcript] contains the wake word.
     *
     * @param transcript the utterance the user just spoke.
     * @return the part of the utterance *after* the wake word (an empty string when the user only
     *  said the wake word), or `null` when the transcript contains no wake word. The returned text
     *  is normalised (lowercase, punctuation-free) and is what the command stage receives.
     */
    fun matchWakeWord(transcript: String): String?
}
