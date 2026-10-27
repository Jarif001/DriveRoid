package com.prettyj.driveroid.assistant.speech

/**
 * Events a [SpeechRecognizerManager] forwards to the voice pipeline.
 *
 * All callbacks are delivered on the main thread, and they always describe the *current* session:
 * anything that follows an intentional cancellation is swallowed by the manager.
 */
interface SpeechEventListener {

    /** Interim hypothesis while the user is still talking; safe to be called very often. */
    fun onPartialResult(text: String)

    /** Final transcript of the utterance that just ended. */
    fun onResult(text: String)

    /** The session ended with an error; the listener decides whether and when to listen again. */
    fun onError(error: SpeechErrorKind)
}
