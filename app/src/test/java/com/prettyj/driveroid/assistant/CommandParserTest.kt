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
    fun `parses spelled-out name as phone call`() {
        assertEquals(Command.CallPhone("hasnat"), CommandParser.parse("call h a s n a t"))
        assertEquals(Command.CallPhone("amu"), CommandParser.parse("call a m u"))
    }

    @Test
    fun `parses spelled-out name in messenger`() {
        assertEquals(Command.CallMessenger("hasnat"), CommandParser.parse("call h a s n a t in messenger"))
    }

    @Test
    fun `parses spelled-out name in whatsapp`() {
        assertEquals(Command.CallWhatsApp("hasnat", ""), CommandParser.parse("call h a s n a t in whatsapp"))
        assertEquals(Command.CallWhatsApp("hasnat", ""), CommandParser.parse("call h a s n a t in whats app"))
        assertEquals(Command.CallWhatsApp("hasnat", ""), CommandParser.parse("call h a s n a t in whats up"))
    }

    @Test
    fun `concatenates spelled-out name with letters together`() {
        assertEquals(Command.CallPhone("hasnat"), CommandParser.parse("call h as nat"))
        assertEquals(Command.CallPhone("hasnat"), CommandParser.parse("call ha snat"))
    }

    @Test
    fun `concatenates spelled-out name with punctuation between letters`() {
        assertEquals(Command.CallPhone("hasnat"), CommandParser.parse("call h.a.s.n.a.t"))
        assertEquals(Command.CallPhone("hasnat"), CommandParser.parse("call h, a, s, n, a, t"))
    }

    @Test
    fun `parses spelled-out name in whatsapp with punctuation`() {
        assertEquals(Command.CallWhatsApp("hasnat", ""), CommandParser.parse("call h.a.s.n.a.t in whatsapp"))
    }

    @Test
    fun `does not concatenate non-letter tokens`() {
        // Contains a digit: keep as-is.
        assertEquals(Command.CallPhone("a1 b"), CommandParser.parse("call a1 b"))
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

    @Test
    fun `parses open music app`() {
        assertEquals(Command.OpenMusicApp("youtube music"), CommandParser.parse("open youtube music"))
        assertEquals(Command.OpenMusicApp("youtube music"), CommandParser.parse("open youtube"))
        assertEquals(Command.OpenMusicApp("spotify"), CommandParser.parse("open spotify"))
    }

    @Test
    fun `parses play music in app`() {
        assertEquals(Command.PlayMusicInApp("you are beautiful", "youtube music"), CommandParser.parse("play you are beautiful in youtube music"))
        assertEquals(Command.PlayMusicInApp("superman by boyce avenue", "spotify"), CommandParser.parse("play superman by boyce avenue in spotify"))
    }

    @Test
    fun `play music without app suffix is unknown`() {
        assertEquals(Command.Unknown("play you are beautiful"), CommandParser.parse("play you are beautiful"))
    }

    @Test
    fun `open unknown app is unknown`() {
        assertEquals(Command.Unknown("open apple music"), CommandParser.parse("open apple music"))
    }

    @Test
    fun `parses search music in app`() {
        assertEquals(Command.SearchMusicInApp("beautiful", "spotify"), CommandParser.parse("search beautiful in spotify"))
        assertEquals(Command.SearchMusicInApp("you are beautiful", "youtube music"), CommandParser.parse("search you are beautiful in youtube music"))
    }

    @Test
    fun `search music without app suffix is unknown`() {
        assertEquals(Command.Unknown("search beautiful"), CommandParser.parse("search beautiful"))
    }

    @Test
    fun `search vs play are different commands`() {
        assertEquals(Command.SearchMusicInApp("beautiful", "spotify"), CommandParser.parse("search beautiful in spotify"))
        assertEquals(Command.PlayMusicInApp("beautiful", "spotify"), CommandParser.parse("play beautiful in spotify"))
    }
}
