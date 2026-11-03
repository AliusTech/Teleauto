package tech.alius.rc.video

enum class TeleautoVideoTransmissionModuleType {
    OPENIPC_WFB_RECEIVER,
}

data class TeleautoVideoTransmissionModuleInfo(
    val id: String,
    val type: TeleautoVideoTransmissionModuleType,
    val displayName: String,
    val state: TeleautoWfbAdapterState,
    val detail: String? = null,
)
