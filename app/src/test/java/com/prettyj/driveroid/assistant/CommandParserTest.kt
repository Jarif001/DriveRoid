package com.prettyj.driveroid.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandParserTest {

    @Test
    fun `parses call name as phone call`() {
        assertEquals(Command.CallPhone("amu"), CommandParser.parse("call amu"))
        assertEquals(Command.CallPhone("bappi"), CommandParser.parse("call bappi"))
    }

    @Test
    fun `parses call name in messenger as messenger call`() {
        assertEquals(Command.CallMessenger("amu"), CommandParser.parse("call amu in messenger"))
        assertEquals(Command.CallMessenger("bappi"), CommandParser.parse("call bappi in messenger"))
    }

    @Test
    fun `parses call name in whatsapp variants as whatsapp call`() {
        assertEquals(Command.CallWhatsApp("mother", ""), CommandParser.parse("call mother in whatsapp"))
        assertEquals(Command.CallWhatsApp("mother", ""), CommandParser.parse("call mother in whats app"))
        assertEquals(Command.CallWhatsApp("father", ""), CommandParser.parse("call father in whats up"))
    }

    @Test
    fun `is case insensitive`() {
        assertEquals(Command.CallPhone("amu"), CommandParser.parse("CALL AMU"))
        assertEquals(Command.CallMessenger("amu"), CommandParser.parse("Call AmU in Messenger"))
    }

    @Test
    fun `handles leading and trailing whitespace`() {
        assertEquals(Command.CallPhone("amu"), CommandParser.parse("  call amu  "))
        assertEquals(Command.CallMessenger("amu"), CommandParser.parse("  call amu in messenger  "))
    }

    @Test
    fun `returns unknown for non-call commands`() {
        assertEquals(Command.Unknown("hello"), CommandParser.parse("hello"))
        assertEquals(Command.Unknown("send message"), CommandParser.parse("send message"))
        assertEquals(Command.Unknown("call"), CommandParser.parse("call"))
        assertEquals(Command.Unknown(""), CommandParser.parse(""))
    }
}
