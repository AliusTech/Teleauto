package tech.alius.rc.model
import org.junit.Assert.*
import org.junit.Test
class TeleautoPhysicalUnlockHoldTest {
    @Test fun requiresBothKeysForTwoSeconds() {
        val hold = TeleautoPhysicalUnlockHold()
        hold.update(true, false, 0)
        assertEquals(0f, hold.progress(3000), 0f)
        hold.update(true, true, 3000)
        assertEquals(0.5f, hold.progress(4000), 0f)
        hold.update(true, true, 4500)
        assertEquals(1f, hold.progress(5000), 0f)
    }
    @Test fun releasingEitherKeyRestartsTheEntireHold() {
        for (releaseC1 in listOf(true, false)) {
            val hold = TeleautoPhysicalUnlockHold()
            hold.update(true, true, 0)
            hold.update(!releaseC1, releaseC1, 1900)
            assertEquals(0f, hold.progress(2000), 0f)
            hold.update(true, true, 2100)
            assertEquals(0f, hold.progress(2100), 0f)
            assertEquals(1f, hold.progress(4100), 0f)
            hold.reset()
            assertFalse(hold.active)
        }
    }
}
