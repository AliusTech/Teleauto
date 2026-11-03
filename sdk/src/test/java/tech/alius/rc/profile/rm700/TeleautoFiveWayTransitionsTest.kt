package tech.alius.rc.profile.rm700

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.alius.rc.model.TeleautoKeyAction
import tech.alius.rc.model.TeleautoPhysicalKey

class TeleautoFiveWayTransitionsTest {
    @Test fun pressAndRelease() {
        val down = fiveWayTransitions(emptySet(), setOf(TeleautoPhysicalKey.D_UP))
        assertEquals(listOf(TeleautoPhysicalKey.D_UP to TeleautoKeyAction.DOWN), down.map { it.key to it.action })
        val up = fiveWayTransitions(setOf(TeleautoPhysicalKey.D_UP), emptySet())
        assertEquals(listOf(TeleautoPhysicalKey.D_UP to TeleautoKeyAction.UP), up.map { it.key to it.action })
    }

    @Test fun diagonalCanHoldTwoDirections() {
        val events = fiveWayTransitions(emptySet(), setOf(TeleautoPhysicalKey.D_UP, TeleautoPhysicalKey.D_RIGHT))
        assertEquals(setOf(TeleautoPhysicalKey.D_UP, TeleautoPhysicalKey.D_RIGHT), events.map { it.key }.toSet())
        assertEquals(setOf(TeleautoKeyAction.DOWN), events.map { it.action }.toSet())
    }

    @Test fun oppositeDirectionReleasesBeforePressing() {
        val events = fiveWayTransitions(setOf(TeleautoPhysicalKey.D_LEFT), setOf(TeleautoPhysicalKey.D_RIGHT))
        assertEquals(
            listOf(TeleautoPhysicalKey.D_LEFT to TeleautoKeyAction.UP, TeleautoPhysicalKey.D_RIGHT to TeleautoKeyAction.DOWN),
            events.map { it.key to it.action },
        )
    }

    @Test fun repeatedSnapshotDoesNotRepeatEvents() {
        assertEquals(emptyList<Any>(), fiveWayTransitions(setOf(TeleautoPhysicalKey.D_MIDDLE), setOf(TeleautoPhysicalKey.D_MIDDLE)))
    }
}
