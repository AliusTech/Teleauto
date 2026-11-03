package tech.alius.rc.model

import org.junit.Assert.*
import org.junit.Test

class TeleautoScreenInputGateTest {
    @Test fun lockSuppressesCallbacksAndInvalidatesQueuedEventsEvenAfterUnlock() {
        val gate = TeleautoScreenInputGate()
        val beforeLock = gate.generation
        var callbacks = 0
        assertTrue(gate.runIfOpen { callbacks++ })
        gate.setLocked(true)
        assertFalse(gate.runIfOpen { callbacks++ })
        gate.setLocked(false)
        assertFalse(gate.runIfOpen(beforeLock) { callbacks++ })
        assertTrue(gate.runIfOpen { callbacks++ })
        assertEquals(2, callbacks)
    }
    @Test fun commandTransportFailsClosedWithoutLicenseRegardlessOfScreenLock() {
        val gate = TeleautoControlOutputGate.gate
        var writes = 0
        tech.alius.rc.usb.TeleautoCdcMavlinkTransport.open { writes++; true }
        try {
            for (locked in listOf(false, true, false)) {
                gate.setLocked(locked)
                assertFalse(tech.alius.rc.usb.TeleautoCdcMavlinkTransport.write(byteArrayOf(1)))
            }
            assertEquals(0, writes)
            assertEquals(TeleautoScreenInputResult.LICENSE_REQUIRED, gate.check())
        } finally {
            gate.setLocked(false)
            tech.alius.rc.usb.TeleautoCdcMavlinkTransport.close()
        }
    }
    @Test fun authorizationAndScreenLockAreIndependentAndOldCallbacksNeverReplay() {
        var authorized = true
        val gate = TeleautoScreenInputGate { authorized }
        val queued = gate.generation
        var calls = 0
        assertTrue(gate.runIfOpen { calls++ })
        authorized = false
        assertFalse(gate.runIfOpen { calls++ })
        assertEquals(TeleautoScreenInputResult.LICENSE_REQUIRED, gate.check())
        gate.setLocked(true)
        authorized = true
        assertFalse(gate.runIfOpen { calls++ })
        assertEquals(TeleautoScreenInputResult.UNLOCK_REQUIRED, gate.check())
        gate.setLocked(false)
        gate.invalidate()
        assertFalse(gate.runIfOpen(queued) { calls++ })
        assertTrue(gate.runIfOpen { calls++ })
        assertEquals(2, calls)
    }
    @Test fun lockedScreenRequiresUnlockUntilExplicitlyUnlocked() {
        val gate = TeleautoScreenInputGate()
        assertEquals(TeleautoScreenInputResult.ACCEPTED, gate.check())
        gate.setLocked(true)
        repeat(3) { assertEquals(TeleautoScreenInputResult.UNLOCK_REQUIRED, gate.check()) }
        gate.setLocked(false)
        assertEquals(TeleautoScreenInputResult.ACCEPTED, gate.check())
    }
    @Test fun releasingTouchOnLockPreservesPhysicalOwnership() {
        val input = TeleautoControlInputState()
        input.physicalEvent(TeleautoPhysicalKey.C1, true)
        input.touchEvent(TeleautoPhysicalKey.C1, true)
        input.touchEvent(TeleautoPhysicalKey.L1, true)
        input.touchKeys().forEach { input.touchEvent(it, false) }
        assertEquals(setOf(TeleautoPhysicalKey.C1), input.held())
        assertTrue(input.touchKeys().isEmpty())
    }
}
