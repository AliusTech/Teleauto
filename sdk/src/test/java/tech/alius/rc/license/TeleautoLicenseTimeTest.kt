package tech.alius.rc.license

import org.junit.Assert.*
import org.junit.Test
import tech.alius.rc.license.internal.TeleautoLicenseClock
import tech.alius.rc.license.internal.TeleautoLicenseTimeGuard
import tech.alius.rc.license.internal.TeleautoLicenseVerifier.Error
import tech.alius.rc.model.TeleautoScreenInputGate

class TeleautoLicenseTimeTest {
    @Test fun sameBootMonotonicClockAndCrossBootStoredFloor() {
        val previous = TeleautoLicenseClock.State(1000, 10000, 3)
        assertEquals(1010L, TeleautoLicenseClock.effectiveTime(previous, 1000, 20000, 3))
        assertEquals(1000L, TeleautoLicenseClock.effectiveTime(previous, 990, 50, 4))
        assertThrows(IllegalArgumentException::class.java) { TeleautoLicenseClock.effectiveTime(previous, 500, 50, 4) }
        assertThrows(IllegalArgumentException::class.java) { TeleautoLicenseClock.effectiveTime(previous, 1000, 9000, 3) }
    }
    @Test fun frequentPollingCannotFreezeTheMonotonicExpiry() {
        val time = TeleautoLicenseTimeGuard(1000, 0, 1010)
        for (elapsed in 0..9999) assertEquals(Error.VALID, time.check(1000, elapsed.toLong()))
        assertEquals(Error.LICENSE_EXPIRED, time.check(1000, 10000))
    }
    @Test fun highestObservedWallTimeCannotBeRolledBack() {
        val time = TeleautoLicenseTimeGuard(1000, 0, 2000)
        assertEquals(Error.VALID, time.check(1500, 100))
        assertEquals(Error.CLOCK_ROLLBACK, time.check(1100, 101))
    }
    @Test fun expiryBlocksAllGateActionsEvenIfScreenWasUnlocked() {
        val time = TeleautoLicenseTimeGuard(1000, 0, 1010)
        var elapsed = 0L
        var authorized = true
        val gate = TeleautoScreenInputGate {
            authorized = authorized && time.check(1000, elapsed) == Error.VALID
            authorized
        }
        var sends = 0
        assertTrue(gate.runIfOpen { sends++ })
        gate.setLocked(true)
        assertFalse(gate.runIfOpen { sends++ })
        elapsed = 10000
        gate.setLocked(false)
        assertFalse(gate.runIfOpen { sends++ })
        elapsed = 0 // Returning the clock cannot reauthorize a revoked session.
        assertFalse(gate.runIfOpen { sends++ })
        assertEquals(1, sends)
    }
}
