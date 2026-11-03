package tech.alius.rc.hid

import org.junit.Assert.*
import org.junit.Test
import tech.alius.rc.model.*

class TeleautoHidJoystickFlightModeTest {
    @Test fun leftStickVerticalOutputPreservesInputDirectionInBothHidModes() {
        TeleautoHidJoystickDescriptor.DeviceKind.entries.forEach { kind ->
            val mapper = TeleautoHidJoystickMapper(kind)
            listOf(-1f to 0, 0f to 128, 1f to 255).forEach { (input, expected) ->
                mapper.onStickChanged(TeleautoStickSnapshot(leftY = input))
                val report = mapper.buildReport()
                assertEquals(expected, report.leftStickY.toInt() and 255)
                assertEquals(128, report.leftStickX.toInt() and 255)
            }
        }
    }
    @Test fun xboxTriggersAreLinearOutwardHalfAxesAndReleaseAtCenter() {
        val mapper = TeleautoHidJoystickMapper()
        listOf(0f to 0, .02f to 5, .25f to 64, .5f to 128, 1f to 255, 2f to 255).forEach { (position, expected) ->
            mapper.onStickChanged(TeleautoStickSnapshot(leftDial = -position, rightDial = position))
            val report = mapper.buildReport()
            assertEquals(expected, report.leftTrigger.toInt() and 255)
            assertEquals(expected, report.rightTrigger.toInt() and 255)
        }
        listOf(1f, .5f, 0f).forEach { reverse ->
            mapper.onStickChanged(TeleautoStickSnapshot(leftDial = reverse, rightDial = -reverse))
            assertEquals(0, mapper.buildReport().leftTrigger.toInt() and 255)
            assertEquals(0, mapper.buildReport().rightTrigger.toInt() and 255)
        }
        mapper.onStickChanged(TeleautoStickSnapshot(leftDial = -1f))
        assertEquals(255, mapper.buildReport().leftTrigger.toInt() and 255)
        assertEquals(0, mapper.buildReport().rightTrigger.toInt() and 255)
        mapper.onStickChanged(TeleautoStickSnapshot(rightDial = 1f))
        assertEquals(0, mapper.buildReport().leftTrigger.toInt() and 255)
        assertEquals(255, mapper.buildReport().rightTrigger.toInt() and 255)
    }
    @Test fun reportMapUsesSelectedUsageAndExactlyTenInputBytes() {
        TeleautoHidJoystickDescriptor.DeviceKind.entries.forEach { kind ->
            val bytes = TeleautoHidJoystickDescriptor.bleReportMap(TeleautoHidJoystickDescriptor.REPORT_ID, kind)
            assertEquals(kind.applicationUsage, bytes[3].toInt() and 255)
            var size = 0; var count = 0; var inputBits = 0; var i = 0
            while (i < bytes.size) {
                val tag = bytes[i++].toInt() and 255
                val length = when (tag and 3) { 3 -> 4; else -> tag and 3 }
                var value = 0
                repeat(length) { value = value or ((bytes[i++].toInt() and 255) shl (8*it)) }
                when (tag and 0xfc) { 0x74 -> size = value; 0x94 -> count = value; 0x80 -> inputBits += size*count }
            }
            assertEquals(80, inputBits)
        }
    }
    @Test fun modeAndMediaStayLocalButContinuousDialIsTransmitted() {
        val mapper = TeleautoHidJoystickMapper()
        val neutral = mapper.buildReport().toPayload()
        listOf(TeleautoPhysicalKey.RECORD, TeleautoPhysicalKey.SHUTTER, TeleautoPhysicalKey.FOCUS, TeleautoPhysicalKey.D_UP, TeleautoPhysicalKey.D_MIDDLE, TeleautoPhysicalKey.MODE_1).forEach {
            mapper.onKeyEvent(TeleautoRcKeyEvent(it, TeleautoKeyAction.DOWN))
        }
        mapper.onFlightModeSwitchChanged(FlightModeSwitchState(FlightModeSwitchPosition.RIGHT))
        assertArrayEquals(neutral, mapper.buildReport().toPayload())
        mapper.onStickChanged(TeleautoStickSnapshot(freeDial = -1f))
        assertEquals(0, mapper.buildReport().toPayload()[9].toInt() and 255)
        mapper.onStickChanged(TeleautoStickSnapshot(freeDial = 1f))
        assertEquals(255, mapper.buildReport().toPayload()[9].toInt() and 255)
    }
    @Test fun allTwelveButtonsAreDistinctAndRelease() {
        val patterns = TeleautoRcTransmissionChannels.buttons.map { key ->
            val mapper = TeleautoHidJoystickMapper()
            mapper.onKeyEvent(TeleautoRcKeyEvent(key, TeleautoKeyAction.DOWN))
            val report = mapper.buildReport()
            assertTrue(report.digitalButtonBits != 0)
            val payload = report.toPayload()
            val wireBits = (payload[0].toInt() and 255) or ((payload[3].toInt() and 255) shl 8) or ((payload[4].toInt() and 255) shl 16)
            assertEquals(report.digitalButtonBits, wireBits)
            mapper.onHeldKeysChanged(emptySet())
            assertEquals(0, mapper.buildReport().digitalButtonBits)
            wireBits
        }
        assertEquals(12, patterns.toSet().size)
    }
    @Test fun joystickDialsPreserveDirectionWhileXboxUsesTriggers() {
        val joy = TeleautoHidJoystickMapper(TeleautoHidJoystickDescriptor.DeviceKind.JOYSTICK)
        val xbox = TeleautoHidJoystickMapper()
        val input = TeleautoStickSnapshot(leftDial = -1f, rightDial = 1f)
        joy.onStickChanged(input); xbox.onStickChanged(input)
        assertEquals(0, joy.buildReport().leftTrigger.toInt() and 255)
        assertEquals(255, joy.buildReport().rightTrigger.toInt() and 255)
        assertEquals(255, xbox.buildReport().leftTrigger.toInt() and 255)
    }
}
