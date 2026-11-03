package tech.alius.rc.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeleautoTcpInputReaderTest {
    @Test
    fun rcStatePublishesButtonAndModeBitfields() {
        val states = mutableListOf<Pair<Int, Int>>()
        val reader = TeleautoTcpInputReader(
            port = 40009,
            onMode = {},
            onFreeDial = {},
            onState = {},
            onRcState = { buttons, mode -> states += buttons to mode },
        )
        reader.dbgFrames = 40
        val frame = ByteArray(18).apply {
            this[0] = 0x55
            this[1] = size.toByte()
            this[9] = 6
            this[10] = 0xAE.toByte()
            this[11] = 0x20 // RTH bit 5
            this[13] = 0x0d // mode=1, C1 bit 2, C2 bit 3
        }

        assertTrue(reader.parse(frame).isEmpty())
        assertEquals(listOf(0x20 to 0x0d), states)
    }

    @Test
    fun framedTelemetryPublishesEveryFreeDialPulse() {
        val values = mutableListOf<Float>()
        val reader = TeleautoTcpInputReader(40007, {}, values::add, {})
        reader.dbgFrames = 40
        val frame = ByteArray(18).apply {
            this[0] = 0x55
            this[1] = size.toByte()
            this[9] = 6
            this[10] = 0xAE.toByte()
            this[15] = 0xC1.toByte()
        }
        val wrapped = byteArrayOf(0x55, 0xCC.toByte(), 0x30, 0x75, 18, 0, 0, 0) + frame

        val firstRemainder = reader.parse(wrapped.copyOfRange(0, 11))
        val finalRemainder = reader.parse(firstRemainder + wrapped.copyOfRange(11, wrapped.size))

        assertTrue(finalRemainder.isEmpty())
        assertEquals(listOf(0.1f), values)
    }

    @Test
    fun setFreeDialValuePublishesExactStartingValue() {
        val values = mutableListOf<Float>()
        val reader = TeleautoTcpInputReader(40009, {}, values::add, {})

        assertTrue(reader.setFreeDialValue(0.63f))

        assertEquals(listOf(0.63f), values)
        assertEquals(6, reader.dialAccum)
    }

    @Test
    fun freeDialAccumulatesRelativeStepsIntoNormalizedAxis() {
        val values = mutableListOf<Float>()
        val reader = TeleautoTcpInputReader(40009, {}, values::add, {})

        reader.handleFreeDialPulse(0x80 or 3)
        reader.handleFreeDialPulse(0xC0 or 5)

        assertEquals(-0.3f, values[0], 0.0001f)
        assertEquals(0.2f, values[1], 0.0001f)
        assertEquals(2, reader.dialAccum)
    }

    @Test
    fun freeDialClampsAndImmediatelyReversesAtEndpoint() {
        val values = mutableListOf<Float>()
        val reader = TeleautoTcpInputReader(40009, {}, values::add, {})

        reader.handleFreeDialPulse(0xC0 or 20)
        reader.handleFreeDialPulse(0x80 or 1)

        assertEquals(1f, values[0], 0.0001f)
        assertEquals(0.9f, values[1], 0.0001f)
        assertEquals(9, reader.dialAccum)
    }

    @Test
    fun setFreeDialValueRejectsInvalidAxisValues() {
        val values = mutableListOf<Float>()
        val reader = TeleautoTcpInputReader(40009, {}, values::add, {})

        assertFalse(reader.setFreeDialValue(Float.NaN))
        assertFalse(reader.setFreeDialValue(-1.01f))
        assertFalse(reader.setFreeDialValue(1.01f))
        assertTrue(values.isEmpty())
    }
}
