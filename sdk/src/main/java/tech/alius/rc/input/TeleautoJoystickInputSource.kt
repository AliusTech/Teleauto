package tech.alius.rc.input

/** SDK 摇杆数据的上游来源；与 BLE HID 输出模式相互独立。 */
enum class TeleautoJoystickInputSource {
    /** DJI 遥控器内置摇杆、拨轮和按键（保持原有实现）。 */
    EMBEDDED_JOYSTICK,

    /** 用户指定的 Android 外接摇杆，可来自 Bluetooth HID 或 USB HID。 */
    EXTERNAL_JOYSTICK,
}

/** SDK 识别并提供给 UI 展示的可用输入源。 */
data class TeleautoAvailableJoystickInputSource(
    /** 当前进程内稳定的选择 ID；App 应将该值原样传回 selectInputSource。 */
    val id: String,
    val source: TeleautoJoystickInputSource,
    val displayName: String,
    val description: String,
    val device: TeleautoJoystickDeviceInfo? = null,
    val selected: Boolean = false,
    /** False when the source is known but requires host-side provisioning. */
    val enabled: Boolean = true,
)

/** SDK 枚举出的外置 Joystick；平台 handle 不向 SDK 调用方公开。 */
data class TeleautoJoystickDeviceInfo(
    val id: String,
    val sdlInstanceId: Int,
    val name: String,
    val guid: String,
    val vendorId: Int,
    val productId: Int,
    val axisCount: Int,
    val buttonCount: Int,
    val hatCount: Int,
    val connected: Boolean = true,
)

data class TeleautoJoystickAxisMapping(
    val leftX: Int = 0,
    val leftY: Int = 1,
    val rightX: Int = 2,
    val rightY: Int = 3,
    val leftAux: Int = 4,
    val rightAux: Int = 5,
)

enum class TeleautoDisconnectBehavior { RESET }

data class TeleautoExternalJoystickConfig(
    val deadZone: Float = 0.04f,
    val invertLeftY: Boolean = true,
    val invertRightY: Boolean = true,
    val axisMapping: TeleautoJoystickAxisMapping = TeleautoJoystickAxisMapping(),
    val hatIndex: Int = 0,
    val buttonMapping: Map<Int, tech.alius.rc.model.TeleautoPhysicalKey> = DEFAULT_BUTTON_MAPPING,
    val disconnectBehavior: TeleautoDisconnectBehavior = TeleautoDisconnectBehavior.RESET,
) {
    init {
        require(deadZone in 0f..0.5f) { "deadZone must be in 0..0.5" }
        require(hatIndex >= 0) { "hatIndex must be >= 0" }
    }

    companion object {
        val DEFAULT_BUTTON_MAPPING = mapOf(
            0 to tech.alius.rc.model.TeleautoPhysicalKey.R3,
            1 to tech.alius.rc.model.TeleautoPhysicalKey.R2,
            2 to tech.alius.rc.model.TeleautoPhysicalKey.L2,
            3 to tech.alius.rc.model.TeleautoPhysicalKey.R1,
            4 to tech.alius.rc.model.TeleautoPhysicalKey.RECORD,
            5 to tech.alius.rc.model.TeleautoPhysicalKey.SHUTTER,
            8 to tech.alius.rc.model.TeleautoPhysicalKey.GOHOME,
            9 to tech.alius.rc.model.TeleautoPhysicalKey.L3,
            11 to tech.alius.rc.model.TeleautoPhysicalKey.AUTH_LED,
            16 to tech.alius.rc.model.TeleautoPhysicalKey.L1,
        )
    }
}
