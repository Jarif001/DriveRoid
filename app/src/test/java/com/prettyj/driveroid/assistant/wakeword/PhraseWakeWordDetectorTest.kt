package com.prettyj.driveroid.assistant.wakeword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Locks the wake-word contract the pipeline relies on to decide when a command was addressed to DriveRoid. */
class PhraseWakeWordDetectorTest {

    private lateinit var detector: PhraseWakeWordDetector

    @Before
    fun setUp() {
        detector = PhraseWakeWordDetector()
        detector.arm()
    }

    @Test
    fun `matches the wake word regardless of case and punctuation`() {
        assertEquals("", detector.matchWakeWord("Hey, Driveroid!!"))
    }

    @Test
    fun `returns the text that follows the wake word`() {
        assertEquals("call mom", detector.matchWakeWord("hey driveroid, call mom"))
    }

    @Test
    fun `returns null when the wake word is missing`() {
        assertNull(detector.matchWakeWord("call mom when you arrive"))
    }

    @Test
    fun `finds the wake word in the middle of an utterance`() {
        assertEquals("now", detector.matchWakeWord("please say driveroid now"))
    }

    @Test
    fun `prefers the longest phrase on a tie`() {
        assertEquals("open maps", detector.matchWakeWord("ok driveroid open maps"))
    }

    @Test
    fun `matches every supported variant`() {
        listOf(
            "driveroid", "ok driveroid", "hey driveroid",
            "drive ride", "hey drive ride",
            "drive ryan", "drive road", "drive way", "drive right", "drive red"
        ).forEach { phrase ->
            assertEquals("phrase=$phrase", "", detector.matchWakeWord(phrase))
        }
    }

    @Test
    fun `returns text after a drive- variants wake word`() {
        assertEquals("call mom", detector.matchWakeWord("drive ryan call mom"))
        assertEquals("open maps", detector.matchWakeWord("drive road open maps"))
        assertEquals("navigate home", detector.matchWakeWord("drive way navigate home"))
        assertEquals("turn left", detector.matchWakeWord("drive right turn left"))
        assertEquals("stop here", detector.matchWakeWord("drive red stop here"))
    }

    @Test
    fun `matches drive ride as driveroid alias`() {
        assertEquals("call mom", detector.matchWakeWord("drive ride call mom"))
        assertEquals("", detector.matchWakeWord("hey drive ride"))
    }

    @Test
    fun `does not match inside an unrelated word`() {
        assertNull(detector.matchWakeWord("the driveroids are coming"))
    }

    @Test
    fun `ignores transcripts while disarmed`() {
        detector.disarm()
        assertFalse(detector.isArmed)
        assertNull(detector.matchWakeWord("hey driveroid"))
    }

    @Test
    fun `ignores blank transcripts`() {
        assertNull(detector.matchWakeWord("   "))
    }
}
