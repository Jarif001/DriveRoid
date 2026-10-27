package com.prettyj.driveroid.assistant.speech

/**
 * Recognition errors grouped by how the assistant should react to them.
 *
 * [android.speech.SpeechRecognizer] reports a raw integer code; the mapping happens once inside
 * [SpeechRecognizerManager], so the voice pipeline never deals with platform codes directly.
 */
enum class SpeechErrorKind {

    /** Nothing was recognised (usually silence) - listen again straight away. */
    NO_MATCH,

    /** The user stopped talking without producing a usable utterance - listen again straight away. */
    TIMEOUT,

    /** The recogniser was busy or left in a bad state - cancel it, then listen again. */
    BUSY,

    /** No network / server problem - retry with back-off. */
    NETWORK,

    /** The microphone could not be used (for example another app holds it) - retry with back-off. */
    AUDIO,

    /** The recognition service died or reported a client-side problem - retry with back-off. */
    CLIENT,

    /** Microphone permission was revoked while the assistant was running - stop the assistant. */
    PERMISSION,

    /** Anything not mapped above - retry with back-off. */
    UNKNOWN,
}
