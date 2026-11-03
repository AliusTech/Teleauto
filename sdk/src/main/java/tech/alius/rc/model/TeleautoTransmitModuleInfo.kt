package tech.alius.rc.model

/** External modules carrying the command/control link, not video transmission. */
enum class TeleautoTransmitModuleType {
    HIGH_FREQUENCY_2_4_GHZ_TRANSMITTER,
    UNKNOWN,
}

enum class TeleautoTransmitModuleState {
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class TeleautoTransmitModuleInfo(
    val id: String,
    val type: TeleautoTransmitModuleType,
    val displayName: String,
    val state: TeleautoTransmitModuleState,
    val detail: String? = null,
    val hardwareId: String? = null,
    val firmwareVersion: String? = null,
    val batteryLevelPercent: Int? = null,
)
