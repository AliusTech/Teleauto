package tech.alius.rc.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeleautoEvdevBinaryParserTest {
    @Test fun parses32BitEventsAcrossArbitraryChunks() {
        val events = mutableListOf<Triple<Int, Int, Int>>()
        val parser = TeleautoEvdevBinaryParser(TeleautoEvdevBinaryParser.EVENT32_SIZE) { t, c, v -> events += Triple(t, c, v) }
        val bytes = event(16, 3, 0, -1234) + event(16, 3, 1, 2345) + event(16, 0, 0, 0)
        parser.feed(bytes.copyOfRange(0, 7))
        parser.feed(bytes.copyOfRange(7, 29))
        parser.feed(bytes.copyOfRange(29, bytes.size))
        assertEquals(listOf(Triple(3, 0, -1234), Triple(3, 1, 2345), Triple(0, 0, 0)), events)
    }

    @Test fun parses64BitKeyAndSynFromOneChunk() {
        val events = mutableListOf<Triple<Int, Int, Int>>()
        val parser = TeleautoEvdevBinaryParser(TeleautoEvdevBinaryParser.EVENT64_SIZE) { t, c, v -> events += Triple(t, c, v) }
        parser.feed(event(24, 1, 311, 1) + event(24, 0, 0, 0))
        assertEquals(listOf(Triple(1, 311, 1), Triple(0, 0, 0)), events)
    }

    @Test fun autoDetectsBothLayouts() {
        for (size in listOf(16, 24)) {
            val events = mutableListOf<Triple<Int, Int, Int>>()
            val parser = TeleautoEvdevBinaryParser { t, c, v -> events += Triple(t, c, v) }
            parser.feed(event(size, 3, 4, 99) + event(size, 0, 0, 0))
            assertTrue("layout=$size", events.contains(Triple(3, 4, 99)))
        }
    }

    @Test fun countsDamagedInputWithoutEmittingUnknownEvents() {
        val events = mutableListOf<Triple<Int, Int, Int>>()
        val parser = TeleautoEvdevBinaryParser(TeleautoEvdevBinaryParser.EVENT32_SIZE) { t, c, v -> events += Triple(t, c, v) }
        parser.feed(ByteArray(64) { 0x7f })
        assertTrue(parser.invalidBytes > 0)
        assertTrue(events.isEmpty())
    }

    private fun event(size: Int, type: Int, code: Int, value: Int): ByteArray {
        val result = ByteArray(size)
        // Non-zero timestamps make 32/64-bit auto detection unambiguous.
        put32(result, 0, 1_723_456_789)
        put32(result, if (size == 16) 4 else 8, 654_321)
        val offset = size - 8
        result[offset] = type.toByte()
        result[offset + 2] = code.toByte()
        result[offset + 3] = (code ushr 8).toByte()
        put32(result, offset + 4, value)
        return result
    }

    private fun put32(target: ByteArray, offset: Int, value: Int) {
        for (i in 0..3) target[offset + i] = (value ushr (i * 8)).toByte()
    }
}
