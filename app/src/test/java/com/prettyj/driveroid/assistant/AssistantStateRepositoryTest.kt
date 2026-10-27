package com.prettyj.driveroid.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Guards the repository contract that the UI and the pipeline share: a state change must not lose
 * the transcript, and a transcript update must not disturb the state.
 */
class AssistantStateRepositoryTest {

    @Before
    fun reset() {
        AssistantStateRepository.update(AssistantState.STOPPED)
        AssistantStateRepository.publishTranscript(null)
    }

    @Test
    fun `publishing a transcript keeps the state and the error`() {
        AssistantStateRepository.update(AssistantState.ERROR, AssistantError.PERMISSION_DENIED)
        AssistantStateRepository.publishTranscript("hey driveroid")

        assertEquals(AssistantState.ERROR, AssistantStateRepository.current.state)
        assertEquals(AssistantError.PERMISSION_DENIED, AssistantStateRepository.current.error)
        assertEquals("hey driveroid", AssistantStateRepository.current.transcript)
    }

    @Test
    fun `changing the state keeps the transcript`() {
        AssistantStateRepository.publishTranscript("call mom")
        AssistantStateRepository.update(AssistantState.PROCESSING)

        assertEquals(AssistantState.PROCESSING, AssistantStateRepository.current.state)
        assertEquals("call mom", AssistantStateRepository.current.transcript)
        assertNull(AssistantStateRepository.current.error)
    }

    @Test
    fun `leaving the error state clears the error`() {
        AssistantStateRepository.update(AssistantState.ERROR, AssistantError.SERVICE_START_FAILED)
        AssistantStateRepository.update(AssistantState.STOPPED)

        assertEquals(AssistantState.STOPPED, AssistantStateRepository.current.state)
        assertNull(AssistantStateRepository.current.error)
    }

    @Test
    fun `reset state is idle and not an active session`() {
        assertFalse(AssistantStateRepository.current.isSessionActive)
        assertNull(AssistantStateRepository.current.transcript)
        assertEquals(AssistantState.STOPPED, AssistantStateRepository.current.state)
    }
}
