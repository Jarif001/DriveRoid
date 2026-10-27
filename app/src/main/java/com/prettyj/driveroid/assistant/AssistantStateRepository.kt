package com.prettyj.driveroid.assistant

import com.prettyj.driveroid.util.DriveroidLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process single source of truth for the assistant state.
 *
 * The voice assistant and the UI live in the same process and there is exactly one assistant
 * service, so a [MutableStateFlow] is enough: no service binding, no IPC and no broadcast are
 * required to keep the UI in sync. It also means that an Activity which is recreated (rotation,
 * theme or font-size change) reads the live state synchronously and never starts a second session.
 *
 * Writers:
 *  - [VoiceAssistantController] / [VoiceAssistantService] for everything that happens while the
 *    service runs.
 *  - `MainActivity` only for pre-flight validation that happens before the service is started, such
 *    as a denied microphone permission. It cannot be reported by the service because the service is
 *    never launched in that case.
 *
 * Readers: any UI component, by collecting [state] (or reading [current] in `onCreate`).
 *
 * Updates happen on the main thread only.
 */
object AssistantStateRepository {

    private const val TAG = "AssistantStateRepository"

    private val _state = MutableStateFlow(AssistantSnapshot.IDLE)

    /** Observable assistant state; its current value is always available synchronously. */
    val state: StateFlow<AssistantSnapshot> = _state.asStateFlow()

    /** Synchronous access for code that cannot wait for a flow emission (for example `onCreate`). */
    val current: AssistantSnapshot
        get() = _state.value

    /**
     * Moves the assistant to [state] (and clears or sets [error]), while **keeping** the last known
     * transcript: the UI keeps showing what was heard across a state change, which is exactly what
     * the user needs to confirm their command was captured.
     */
    fun update(state: AssistantState, error: AssistantError? = null) {
        val previous = _state.value
        if (previous.state == state && previous.error == error) {
            DriveroidLog.d(TAG, "State unchanged (${state.name})")
            return
        }
        _state.value = previous.copy(state = state, error = error)
        val errorName = error?.let { " (${it.name})" }.orEmpty()
        DriveroidLog.i(TAG, "${previous.state.name} -> ${state.name}$errorName")
    }

    /**
     * Publishes the latest recognised utterance without touching the state. Called for every interim
     * hypothesis (frequently while the user speaks) and for every final transcript, so this stays on
     * the debug log level.
     */
    fun publishTranscript(transcript: String?) {
        val previous = _state.value
        if (previous.transcript == transcript) return
        _state.value = previous.copy(transcript = transcript)
        DriveroidLog.d(TAG, "Transcript: ${transcript?.let { "\"$it\"" } ?: "<none>"}")
    }
}
