package com.prettyj.driveroid.assistant

/**
 * Immutable view of the assistant, as published by [AssistantStateRepository].
 *
 * @property state current state of the voice assistant.
 * @property error failure reason, only set while [state] is [AssistantState.ERROR].
 * @property transcript latest recognised utterance (interim or final), shown by the UI as
 *  "Heard: ...". `null` when nothing has been heard since the assistant started.
 */
data class AssistantSnapshot(
    val state: AssistantState = AssistantState.STOPPED,
    val error: AssistantError? = null,
    val transcript: String? = null,
) {

    /** `true` while the assistant service is running; drives the primary button label. */
    val isSessionActive: Boolean
        get() = state.isSessionActive

    companion object {
        /** The assistant is idle: no service, no microphone, nothing to release. */
        val IDLE = AssistantSnapshot()
    }
}
