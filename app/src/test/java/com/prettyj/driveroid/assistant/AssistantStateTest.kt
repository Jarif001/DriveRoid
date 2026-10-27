package com.prettyj.driveroid.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the state contract the UI depends on: only a running session turns the primary action into
 * STOP LISTENING, and only an active session may hand the stop command to the service.
 */
class AssistantStateTest {

    @Test
    fun `stopped and error are the only states without a live session`() {
        assertFalse(AssistantState.STOPPED.isSessionActive)
        assertFalse(AssistantState.ERROR.isSessionActive)
    }

    @Test
    fun `every other state counts as a live session`() {
        AssistantState.entries
            .filter { it != AssistantState.STOPPED && it != AssistantState.ERROR }
            .forEach { state ->
                assertTrue("$state should be an active session", state.isSessionActive)
            }
    }

    @Test
    fun `startup and pipeline states are part of the explicit model`() {
        assertEquals(
            listOf(
                "STOPPED",
                "STARTING",
                "LISTENING_FOR_WAKE_WORD",
                "LISTENING_FOR_COMMAND",
                "PROCESSING",
                "WAITING_FOR_RESPONSE",
                "CALLING",
                "ERROR",
            ),
            AssistantState.entries.map { it.name },
        )
    }

    @Test
    fun `idle snapshot reports a stopped assistant without an error`() {
        assertEquals(AssistantState.STOPPED, AssistantSnapshot.IDLE.state)
        assertNull(AssistantSnapshot.IDLE.error)
        assertFalse(AssistantSnapshot.IDLE.isSessionActive)
    }

    @Test
    fun `error snapshot keeps the reason but is not an active session`() {
        val snapshot = AssistantSnapshot(AssistantState.ERROR, AssistantError.PERMISSION_DENIED)
        assertEquals(AssistantError.PERMISSION_DENIED, snapshot.error)
        assertFalse(snapshot.isSessionActive)
    }
}
