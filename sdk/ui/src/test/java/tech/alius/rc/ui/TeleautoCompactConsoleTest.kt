package tech.alius.rc.ui

import org.junit.Assert.*
import org.junit.Test

class TeleautoCompactConsoleTest {
    @Test fun columnsStayCenteredWithoutResizingKeys() {
        for (width in listOf(168f, 420f, 560f)) {
            val key = width * .35f
            val (left, right) = TeleautoCompactControlColumns.calculate(width, key)
            assertEquals(left, width - right - key, .001f)
            assertEquals((width - 2 * key) * .6f, right - left - key, .001f)
            assertTrue(left >= 0f && right + key <= width)
        }
    }
    @Test fun onlyASimultaneousUninterruptedHoldUnlocks() {
        val hold = TeleautoScreenUnlockHold()
        hold.update(true, false, 0); assertEquals(0f, hold.progress(3000), 0f)
        hold.update(true, true, 3000); assertEquals(.5f, hold.progress(4000), .001f)
        hold.update(true, true, 4000); assertEquals(1f, hold.progress(5000), 0f)
        hold.update(false, true, 5000); assertEquals(0f, hold.progress(5000), 0f)
        hold.update(true, true, 6000); assertEquals(.5f, hold.progress(7000), .001f)
        hold.reset(); assertFalse(hold.active); assertEquals(0f, hold.progress(9000), 0f)
    }
    @Test fun missingAndConflictingHardwarePositionsUseCanonicalLayout() {
        val wrong = listOf(TeleautoHardwareControlSlot(tech.alius.rc.model.TeleautoPhysicalKey.C1,
            tech.alius.rc.model.TeleautoControlSlot.RIGHT_1))
        val layout = TeleautoConsoleControlLayout
        val empty = layout.unify(emptyList(), layout.canonicalLeft)
        assertEquals(empty, layout.unify(wrong, layout.canonicalLeft))
        assertEquals(6, empty.size)
        assertEquals(tech.alius.rc.model.TeleautoControlSlot.LEFT_1,
            empty.single { it.key == tech.alius.rc.model.TeleautoPhysicalKey.C1 }.slot)
        assertEquals(6, layout.unify(emptyList(), layout.canonicalRight).size)
    }
    @Test fun touchCannotUnlock() {
        assertTrue(TeleautoSlideLockGesture.completed(false, .96f))
        assertFalse(TeleautoSlideLockGesture.completed(false, .5f))
        assertFalse(TeleautoSlideLockGesture.completed(true, 0f))
        assertFalse(TeleautoSlideLockGesture.completed(true, 1f))
    }
}
