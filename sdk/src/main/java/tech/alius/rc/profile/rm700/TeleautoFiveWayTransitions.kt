package tech.alius.rc.profile.rm700

import tech.alius.rc.model.TeleautoKeyAction
import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoRcKeyEvent

/** Deterministic release-before-press transitions for Hat/5D snapshots. */
internal fun fiveWayTransitions(
    previous: Set<TeleautoPhysicalKey>,
    next: Set<TeleautoPhysicalKey>,
): List<TeleautoRcKeyEvent> = buildList {
    previous.filterNot(next::contains).forEach { add(TeleautoRcKeyEvent(it, TeleautoKeyAction.UP)) }
    next.filterNot(previous::contains).forEach { add(TeleautoRcKeyEvent(it, TeleautoKeyAction.DOWN)) }
}
