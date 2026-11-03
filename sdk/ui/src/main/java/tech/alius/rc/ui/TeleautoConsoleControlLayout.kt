package tech.alius.rc.ui

import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoControlSlot

/** One visual arrangement for every host using the shared controller console. */
object TeleautoConsoleControlLayout {
        // 19 路控制输入：L/R 内侧竖列，左外侧 C1/返航/AUTH，右外侧 C2/C3/暂停。
    internal val canonicalLeft = listOf(
            TeleautoPhysicalKey.AUTH_LED to TeleautoControlSlot.LEFT_3,
            TeleautoPhysicalKey.GOHOME to TeleautoControlSlot.LEFT_2,
            TeleautoPhysicalKey.C1 to TeleautoControlSlot.LEFT_1,
            TeleautoPhysicalKey.L1 to TeleautoControlSlot.RIGHT_1,
            TeleautoPhysicalKey.L2 to TeleautoControlSlot.RIGHT_2,
            TeleautoPhysicalKey.L3 to TeleautoControlSlot.RIGHT_3,
        )
    internal val canonicalRight = listOf(
            TeleautoPhysicalKey.PAUSE to TeleautoControlSlot.RIGHT_3,
            TeleautoPhysicalKey.C2 to TeleautoControlSlot.RIGHT_1,
            TeleautoPhysicalKey.C3 to TeleautoControlSlot.RIGHT_2,
            TeleautoPhysicalKey.R1 to TeleautoControlSlot.LEFT_1,
            TeleautoPhysicalKey.R2 to TeleautoControlSlot.LEFT_2,
            TeleautoPhysicalKey.R3 to TeleautoControlSlot.LEFT_3,
        )
    internal fun unify(@Suppress("UNUSED_PARAMETER") slots: List<TeleautoHardwareControlSlot>, canonical: List<Pair<TeleautoPhysicalKey, TeleautoControlSlot>>): List<TeleautoHardwareControlSlot> {
            // Profile placement must never override the shared screen geometry.
            return canonical.map { (key, slot) -> TeleautoHardwareControlSlot(key, slot) }
        }
    fun configure(left: TeleautoHardwareControlsView, right: TeleautoHardwareControlsView, leftSlots: List<TeleautoHardwareControlSlot>, rightSlots: List<TeleautoHardwareControlSlot>) {
        left.configureSlots(unify(leftSlots, canonicalLeft))
        right.configureSlots(unify(rightSlots, canonicalRight))
    }
}
