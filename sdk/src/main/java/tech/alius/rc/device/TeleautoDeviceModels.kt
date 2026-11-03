package tech.alius.rc.device

import tech.alius.rc.model.TeleautoBatteryState
import tech.alius.rc.model.TeleautoRemoteControllerInfo
import tech.alius.rc.model.TeleautoHardwareControlDescriptor
import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoStickSnapshot
import tech.alius.rc.model.TeleautoThreePositionSwitchState

enum class TeleautoDeviceCategory(val displayName: String) {
    AIRCRAFT("航空设备"),
    GROUND("地面设备"),
    MARINE("水域设备"),
    ROBOT("机器人"),
    VIRTUAL("虚拟设备"),
}

/** Selectable vehicle types mirror MAV_TYPE values; peripheral component types are discovered, not manually created. */
enum class TeleautoDeviceType(
    val displayName: String,
    val mavType: Int?,
    val category: TeleautoDeviceCategory?,
) {
    REMOTE_CONTROLLER("遥控器", null, null),
    GENERIC_AIRCRAFT("通用飞行器", 0, TeleautoDeviceCategory.AIRCRAFT),
    FIXED_WING("固定翼", 1, TeleautoDeviceCategory.AIRCRAFT),
    QUADROTOR("四旋翼", 2, TeleautoDeviceCategory.AIRCRAFT),
    COAXIAL_HELICOPTER("共轴直升机", 3, TeleautoDeviceCategory.AIRCRAFT),
    HELICOPTER("直升机", 4, TeleautoDeviceCategory.AIRCRAFT),
    AIRSHIP("飞艇", 7, TeleautoDeviceCategory.AIRCRAFT),
    FREE_BALLOON("自由气球", 8, TeleautoDeviceCategory.AIRCRAFT),
    ROCKET("火箭", 9, TeleautoDeviceCategory.AIRCRAFT),
    GROUND_ROVER("遥控车", 10, TeleautoDeviceCategory.GROUND),
    SURFACE_BOAT("水面船艇", 11, TeleautoDeviceCategory.MARINE),
    SUBMARINE("水下执行器", 12, TeleautoDeviceCategory.MARINE),
    HEXAROTOR("六旋翼", 13, TeleautoDeviceCategory.AIRCRAFT),
    OCTOROTOR("八旋翼", 14, TeleautoDeviceCategory.AIRCRAFT),
    TRICOPTER("三旋翼", 15, TeleautoDeviceCategory.AIRCRAFT),
    FLAPPING_WING("扑翼机", 16, TeleautoDeviceCategory.AIRCRAFT),
    KITE("风筝", 17, TeleautoDeviceCategory.AIRCRAFT),
    VTOL_TAILSITTER_DUOROTOR("双旋翼尾座 VTOL", 19, TeleautoDeviceCategory.AIRCRAFT),
    VTOL_TAILSITTER_QUADROTOR("四旋翼尾座 VTOL", 20, TeleautoDeviceCategory.AIRCRAFT),
    VTOL_TILTROTOR("倾转旋翼 VTOL", 21, TeleautoDeviceCategory.AIRCRAFT),
    VTOL_FIXEDROTOR("复合翼 VTOL", 22, TeleautoDeviceCategory.AIRCRAFT),
    VTOL_TAILSITTER("尾座式 VTOL", 23, TeleautoDeviceCategory.AIRCRAFT),
    VTOL_TILTWING("倾转机翼 VTOL", 24, TeleautoDeviceCategory.AIRCRAFT),
    PARAFOIL("翼伞", 28, TeleautoDeviceCategory.AIRCRAFT),
    DODECAROTOR("十二旋翼", 29, TeleautoDeviceCategory.AIRCRAFT),
    DECAROTOR("十旋翼", 35, TeleautoDeviceCategory.AIRCRAFT),
    GENERIC_MULTIROTOR("通用多旋翼", 43, TeleautoDeviceCategory.AIRCRAFT),
    GROUND_QUADRUPED("四足机器人", 46, TeleautoDeviceCategory.GROUND),
    /** MAVLink common has no humanoid MAV_TYPE yet; ground-rover is the compatible wire identity. */
    HUMANOID_ROBOT("人形机器人", 10, TeleautoDeviceCategory.ROBOT),
    VTOL_GYRODYNE("旋翼 VTOL", 47, TeleautoDeviceCategory.AIRCRAFT),
    CYBERBRICK("CyberBrick", null, TeleautoDeviceCategory.VIRTUAL),
    VIRTUAL_GAMEPAD("Xbox 手柄", null, TeleautoDeviceCategory.VIRTUAL),
    VIRTUAL_JOYSTICK("Joystick 摇杆", null, TeleautoDeviceCategory.VIRTUAL),
    ;

    companion object {
        fun selectable(): List<TeleautoDeviceType> = entries.filter { it.category != null }
        fun fromMavType(value: Int): TeleautoDeviceType? = selectable().firstOrNull { it.mavType == value }
    }

    val isBluetoothTarget: Boolean get() = isVirtualHid || this == CYBERBRICK

    val isVirtualHid: Boolean
        get() = this == VIRTUAL_GAMEPAD || this == VIRTUAL_JOYSTICK
}

/** Optional target-device capabilities selected by the host application. */
enum class TeleautoDeviceCapability {
    VIDEO_TRANSMISSION,
}

enum class TeleautoDeviceConnectionMode { AUTOMATIC, MANUAL }

enum class TeleautoDeviceConnectionState {
    CREATED, CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTED, ERROR
}

enum class TeleautoComponentConnectionState { CONNECTING, CONNECTED, PAUSED, DISCONNECTED, ERROR }

enum class TeleautoComponentType {
    REMOTE_CONTROLLER,
    BATTERY,
    JOYSTICK,
    BUTTONS,
    FIVE_WAY_BUTTON,
    CENTERING_DIAL,
    CONTINUOUS_DIAL,
    THREE_POSITION_SWITCH,
    TRANSMIT_MODULE,
    VIDEO_RECEIVER,
    VIDEO_TRANSMISSION,
    AUTOPILOT,
    GPS,
    POSITION,
    ATTITUDE,
    IMU,
    CONTROL_LINK,
    VIRTUAL_HID,
}

@JvmInline
value class TeleautoDeviceId(val value: String)

@JvmInline
value class TeleautoComponentId(val value: String)

data class TeleautoDeviceInfo(
    val displayName: String,
    val manufacturer: String? = null,
    val model: String? = null,
    val firmwareVersion: String? = null,
    val systemId: Int? = null,
    val componentId: Int? = null,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val uid: String? = null,
    val remoteControllerInfo: TeleautoRemoteControllerInfo? = null,
)

/** Raw MAVLink component identity is kept separate from UI state components. */
data class TeleautoMavlinkProtocolComponentInfo(
    val systemId: Int,
    val componentId: Int,
    val mavType: Int,
    val autopilotType: Int,
    val vendorName: String? = null,
    val modelName: String? = null,
    val softwareVersion: String? = null,
    val hardwareVersion: String? = null,
    val serialNumber: String? = null,
    val capabilities: ULong? = null,
    val updatedAtMs: Long = System.currentTimeMillis(),
)

sealed interface TeleautoDeviceComponent {
    val id: TeleautoComponentId
    val type: TeleautoComponentType
    val connectionState: TeleautoComponentConnectionState
    val updatedAtMs: Long
}

data class TeleautoRemoteControllerComponent(
    override val id: TeleautoComponentId = TeleautoComponentId("remote-controller"),
    override val connectionState: TeleautoComponentConnectionState,
    val info: TeleautoRemoteControllerInfo,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    override val type = TeleautoComponentType.REMOTE_CONTROLLER
}

data class TeleautoBatteryComponent(
    override val id: TeleautoComponentId,
    override val connectionState: TeleautoComponentConnectionState = TeleautoComponentConnectionState.CONNECTED,
    val levelPercent: Int?,
    val voltageMv: Int? = null,
    val currentMa: Int? = null,
    val temperatureC: Float? = null,
    val sourceState: TeleautoBatteryState? = null,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    override val type = TeleautoComponentType.BATTERY
}

data class TeleautoJoystickComponent(
    override val id: TeleautoComponentId = TeleautoComponentId("joystick"),
    override val connectionState: TeleautoComponentConnectionState,
    val snapshot: TeleautoStickSnapshot,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    override val type = TeleautoComponentType.JOYSTICK
}

data class TeleautoButtonComponent(
    override val id: TeleautoComponentId = TeleautoComponentId("buttons"),
    override val connectionState: TeleautoComponentConnectionState,
    val supported: Set<TeleautoPhysicalKey>,
    val held: Set<TeleautoPhysicalKey>,
    val descriptors: List<TeleautoHardwareControlDescriptor>,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    override val type = TeleautoComponentType.BUTTONS
}

data class TeleautoFiveWayButtonComponent(
    override val id: TeleautoComponentId = TeleautoComponentId("five-way"),
    override val connectionState: TeleautoComponentConnectionState,
    val held: Set<TeleautoPhysicalKey>,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    override val type = TeleautoComponentType.FIVE_WAY_BUTTON
}

data class TeleautoDialComponent(
    override val id: TeleautoComponentId,
    override val type: TeleautoComponentType,
    override val connectionState: TeleautoComponentConnectionState,
    val value: Float,
    val minimum: Float = -1f,
    val maximum: Float = 1f,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    init { require(type == TeleautoComponentType.CENTERING_DIAL || type == TeleautoComponentType.CONTINUOUS_DIAL) }
}

data class TeleautoThreePositionSwitchComponent(
    override val id: TeleautoComponentId = TeleautoComponentId("three-position-switch"),
    override val connectionState: TeleautoComponentConnectionState,
    val state: TeleautoThreePositionSwitchState,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    override val type = TeleautoComponentType.THREE_POSITION_SWITCH
}

data class TeleautoTransmitModuleComponent(
    override val id: TeleautoComponentId,
    override val connectionState: TeleautoComponentConnectionState,
    val displayName: String,
    val hardwareId: String? = null,
    val firmwareVersion: String? = null,
    val batteryLevelPercent: Int? = null,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    override val type = TeleautoComponentType.TRANSMIT_MODULE
}

data class TeleautoVideoReceiverComponent(
    override val id: TeleautoComponentId,
    override val connectionState: TeleautoComponentConnectionState,
    val displayName: String,
    val detail: String? = null,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    override val type = TeleautoComponentType.VIDEO_RECEIVER
}

data class TeleautoVideoTransmissionComponent(
    override val id: TeleautoComponentId = TeleautoComponentId("video-transmission"),
    override val connectionState: TeleautoComponentConnectionState,
    val inputFps: Float = 0f,
    val renderedFps: Float = 0f,
    val width: Int = 0,
    val height: Int = 0,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    override val type = TeleautoComponentType.VIDEO_TRANSMISSION
}

data class TeleautoVirtualHidComponent(
    override val id: TeleautoComponentId = TeleautoComponentId("virtual-hid"),
    override val connectionState: TeleautoComponentConnectionState,
    val kind: TeleautoVirtualHidKind,
    val displayName: String,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    override val type = TeleautoComponentType.VIRTUAL_HID
}

enum class TeleautoVirtualHidKind { GAMEPAD, JOYSTICK }

data class TeleautoAutopilotComponent(
    override val id: TeleautoComponentId,
    override val connectionState: TeleautoComponentConnectionState = TeleautoComponentConnectionState.CONNECTED,
    val systemId: Int,
    val componentId: Int,
    val mavType: Int,
    val autopilotType: Int,
    val baseMode: Int,
    val customMode: Long,
    val systemStatus: Int,
    val capabilities: ULong? = null,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    override val type = TeleautoComponentType.AUTOPILOT
}

data class TeleautoGpsComponent(
    override val id: TeleautoComponentId = TeleautoComponentId("gps"),
    override val connectionState: TeleautoComponentConnectionState = TeleautoComponentConnectionState.CONNECTED,
    val fixType: Int,
    val satellitesVisible: Int?,
    val horizontalAccuracyM: Float?,
    val verticalAccuracyM: Float?,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    override val type = TeleautoComponentType.GPS
}

data class TeleautoPositionComponent(
    override val id: TeleautoComponentId = TeleautoComponentId("position"),
    override val connectionState: TeleautoComponentConnectionState = TeleautoComponentConnectionState.CONNECTED,
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val altitudeM: Double,
    val relativeAltitudeM: Double? = null,
    val velocityNorthMps: Float? = null,
    val velocityEastMps: Float? = null,
    val velocityDownMps: Float? = null,
    val headingDegrees: Float? = null,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    override val type = TeleautoComponentType.POSITION
}

data class TeleautoAttitudeComponent(
    override val id: TeleautoComponentId = TeleautoComponentId("attitude"),
    override val connectionState: TeleautoComponentConnectionState = TeleautoComponentConnectionState.CONNECTED,
    val rollDegrees: Float,
    val pitchDegrees: Float,
    val yawDegrees: Float,
    val rollRate: Float? = null,
    val pitchRate: Float? = null,
    val yawRate: Float? = null,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    override val type = TeleautoComponentType.ATTITUDE
}

data class TeleautoImuComponent(
    override val id: TeleautoComponentId = TeleautoComponentId("imu"),
    override val connectionState: TeleautoComponentConnectionState = TeleautoComponentConnectionState.CONNECTED,
    val accelerationX: Float,
    val accelerationY: Float,
    val accelerationZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val temperatureC: Float? = null,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    override val type = TeleautoComponentType.IMU
}

data class TeleautoControlLinkComponent(
    override val id: TeleautoComponentId = TeleautoComponentId("control-link"),
    override val connectionState: TeleautoComponentConnectionState = TeleautoComponentConnectionState.CONNECTED,
    val localRssi: Int?,
    val remoteRssi: Int?,
    val txBufferPercent: Int?,
    val receiveErrors: Int? = null,
    val correctedPackets: Int? = null,
    override val updatedAtMs: Long = System.currentTimeMillis(),
) : TeleautoDeviceComponent {
    override val type = TeleautoComponentType.CONTROL_LINK
}

data class TeleautoDeviceSnapshot(
    val id: TeleautoDeviceId,
    val type: TeleautoDeviceType,
    val connectionMode: TeleautoDeviceConnectionMode,
    val connectionState: TeleautoDeviceConnectionState,
    val info: TeleautoDeviceInfo,
    val components: List<TeleautoDeviceComponent>,
    val capabilities: Set<TeleautoDeviceCapability> = emptySet(),
    val protocolComponents: List<TeleautoMavlinkProtocolComponentInfo> = emptyList(),
    val error: TeleautoDeviceConnectionError? = null,
    val cyberBrick: TeleautoCyberBrickConnection? = null,
    val updatedAtMs: Long = System.currentTimeMillis(),
)

sealed interface TeleautoDeviceConnectionError {
    data class TypeMismatch(val expected: TeleautoDeviceType, val actualMavType: Int, val systemId: Int) : TeleautoDeviceConnectionError
    data object TransportUnavailable : TeleautoDeviceConnectionError
    data object HeartbeatTimeout : TeleautoDeviceConnectionError
    data class Protocol(val detail: String) : TeleautoDeviceConnectionError
}

fun interface TeleautoDeviceObserver { fun onDeviceChanged(snapshot: TeleautoDeviceSnapshot) }
fun interface TeleautoDeviceListObserver { fun onDevicesChanged(devices: List<TeleautoDeviceSnapshot>) }
fun interface TeleautoDeviceSubscription : AutoCloseable { override fun close() }

interface TeleautoDevice {
    val id: TeleautoDeviceId
    val type: TeleautoDeviceType
    val connectionMode: TeleautoDeviceConnectionMode
    val snapshot: TeleautoDeviceSnapshot
    fun connect()
    fun disconnect()
    fun observe(observer: TeleautoDeviceObserver): TeleautoDeviceSubscription
}

data class TeleautoTargetDeviceConfig @JvmOverloads constructor(
    val cdcBaudRate: Int = 115_200,
    val preferredUsbDeviceId: String? = null,
    val heartbeatTimeoutMs: Long = 3_500,
    val capabilities: Set<TeleautoDeviceCapability> = emptySet(),
)
