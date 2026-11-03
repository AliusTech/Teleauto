package tech.alius.rc.model

/** 接入 SDK 的模块类别。 */
enum class TeleautoModuleCategory {
    TRANSMIT,
    VIDEO_TRANSMISSION,
}

/** 跨模块统一的工作状态，避免第三方 App 依赖具体硬件枚举。 */
enum class TeleautoModuleWorkState {
    CONNECTING,
    RUNNING,
    PAUSED,
    ERROR,
}

/** 已实际发现的模块状态；未接入的可选模块不会出现在列表中。 */
data class TeleautoConnectedModuleStatus(
    val id: String,
    val category: TeleautoModuleCategory,
    val displayName: String,
    val workState: TeleautoModuleWorkState,
    val batteryLevelPercent: Int? = null,
    val hardwareId: String? = null,
    val firmwareVersion: String? = null,
    val detail: String? = null,
)

/** 第三方 App 可直接消费的遥控器与模块实时状态快照。 */
data class TeleautoRemoteControlStatus(
    val initialized: Boolean,
    val device: TeleautoRemoteControllerInfo?,
    val inputConnection: TeleautoConnectionState,
    val battery: TeleautoBatteryState?,
    val modules: List<TeleautoConnectedModuleStatus>,
    val devices: List<tech.alius.rc.device.TeleautoDeviceSnapshot> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis(),
)
