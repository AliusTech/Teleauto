package tech.alius.rc.model

import org.junit.Assert.*
import org.junit.Test

class TeleautoControlInputStateTest {
    @Test fun physicalReleaseDoesNotReleaseTouch() {
        val state = TeleautoControlInputState()
        state.physicalEvent(TeleautoPhysicalKey.L1, true)
        state.touchEvent(TeleautoPhysicalKey.L1, true)
        assertTrue(state.physicalEvent(TeleautoPhysicalKey.L1, false))
        assertEquals(setOf(TeleautoPhysicalKey.L1), state.physicalSnapshot(emptySet()))
        assertFalse(state.touchEvent(TeleautoPhysicalKey.L1, false))
    }
    @Test fun touchReleaseDoesNotReleaseHardwareAndClearResetsBoth() {
        val state = TeleautoControlInputState()
        state.touchEvent(TeleautoPhysicalKey.C1, true)
        state.physicalEvent(TeleautoPhysicalKey.C1, true)
        assertTrue(state.touchEvent(TeleautoPhysicalKey.C1, false))
        state.touchEvent(TeleautoPhysicalKey.R3, true)
        state.clear()
        assertTrue(state.held().isEmpty())
    }
    @Test fun channelContractExcludesLocalControls() {
        val channels = TeleautoRcTransmissionChannels.all
        assertEquals(19, channels.size)
        assertEquals(19, channels.toSet().size)
        assertEquals(7, TeleautoRcTransmissionChannels.axes.size)
        assertEquals(12, TeleautoRcTransmissionChannels.buttons.size)
        assertFalse(TeleautoPhysicalKey.AUTH_LED in TeleautoRcTransmissionChannels.holdButtons)
        val local = listOf(TeleautoPhysicalKey.RECORD, TeleautoPhysicalKey.SHUTTER, TeleautoPhysicalKey.FOCUS,
            TeleautoPhysicalKey.D_UP, TeleautoPhysicalKey.D_DOWN, TeleautoPhysicalKey.D_LEFT, TeleautoPhysicalKey.D_RIGHT, TeleautoPhysicalKey.D_MIDDLE,
            TeleautoPhysicalKey.MODE_1, TeleautoPhysicalKey.MODE_2, TeleautoPhysicalKey.MODE_3)
        assertTrue(local.none { it in channels })
    }
}
