package tech.alius.rc.model

/** Product input contract: 7 analog + 12 digital channels. UI and hardware share identities. */
object TeleautoRcTransmissionChannels {
    val axes = listOf(TeleautoPhysicalKey.LEFT_STICK_X, TeleautoPhysicalKey.LEFT_STICK_Y, TeleautoPhysicalKey.RIGHT_STICK_X, TeleautoPhysicalKey.RIGHT_STICK_Y,
        TeleautoPhysicalKey.LEFT_DIAL, TeleautoPhysicalKey.RIGHT_DIAL, TeleautoPhysicalKey.FREE_DIAL)
    val holdButtons = listOf(TeleautoPhysicalKey.L1, TeleautoPhysicalKey.L2, TeleautoPhysicalKey.L3, TeleautoPhysicalKey.R1, TeleautoPhysicalKey.R2, TeleautoPhysicalKey.R3,
        TeleautoPhysicalKey.C1, TeleautoPhysicalKey.C2, TeleautoPhysicalKey.C3, TeleautoPhysicalKey.GOHOME, TeleautoPhysicalKey.PAUSE)
    val buttons = holdButtons + TeleautoPhysicalKey.AUTH_LED
    val all = axes + buttons
    const val COUNT = 19
}
