package com.prettyj.driveroid.assistant

/**
 * Explicit assistant state model.
 *
 * The state is owned by [VoiceAssistantService] and its [VoiceAssistantController], and published
 * through [AssistantStateRepository] so that the UI always renders the truth about the voice
 * pipeline instead of guessing from local Activity state.
 *
 * Only [STOPPED], [STARTING], [LISTENING_FOR_WAKE_WORD] and [ERROR] are reachable in this phase.
 * [LISTENING_FOR_COMMAND], [PROCESSING], [WAITING_FOR_RESPONSE] and [CALLING] are declared up front
 * so that wake-word detection, speech recognition and calling can be added without changing the
 * contract the UI already renders.
 */
enum class AssistantState {

    /** No assistant: the service is gone, nothing is holding the microphone. */
    STOPPED,

    /** The service is entering the foreground and preparing the pipeline. */
    STARTING,

    /** The assistant is armed and waiting for the wake word. */
    LISTENING_FOR_WAKE_WORD,

    /** A wake word was detected; the assistant is capturing a command. */
    LISTENING_FOR_COMMAND,

    /** The command was captured and is being turned into an action. */
    PROCESSING,

    /** The assistant asked something and is waiting for the user to answer. */
    WAITING_FOR_RESPONSE,

    /** A call is being placed or is in progress. */
    CALLING,

    /** The assistant could not run; [AssistantSnapshot.error] explains why. */
    ERROR;

    /**
     * `true` while a voice session is alive: the foreground service is running and the pipeline is
     * prepared. Both [STOPPED] and [ERROR] mean "no assistant running", which is what the UI uses
     * to decide between START LISTENING and STOP LISTENING.
     */
    val isSessionActive: Boolean
        get() = when (this) {
            STOPPED, ERROR -> false
            STARTING,
            LISTENING_FOR_WAKE_WORD,
            LISTENING_FOR_COMMAND,
            PROCESSING,
            WAITING_FOR_RESPONSE,
            CALLING -> true
        }
}
