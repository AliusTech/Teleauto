package tech.alius.rc.model

/** 跨机型统一物理输入枚举,未配备的键在对应机型上不触发 */
enum class TeleautoPhysicalKey(val label: String) {
    // 摇杆轴 / 拨轮(状态型,经 TeleautoStickSnapshot)
    LEFT_STICK_X("X 左横"), LEFT_STICK_Y("Y 左纵"),
    RIGHT_STICK_X("RX 右横"), RIGHT_STICK_Y("RY 右纵"),
    LEFT_DIAL("Dial左"), RIGHT_DIAL("Dial右"), FREE_DIAL("连续波轮"),
    // GPIO 面板按键(电平型 DOWN/UP,仅 rm700 系具备)
    L1("L1"), L2("L2"), L3("L3"), R1("R1"), R2("R2"), R3("R3"),
    // 私有键(dpad 通道)
    RECORD("录像"), SHUTTER("拍照"), FOCUS("对焦"),
    C1("C1"), C2("C2"), C3("C3"),
    // 三段式开关(档位拨杆,经固件 TCP 遥测通道,仅 rc701 系具备;切到档位发 TRIGGER)
    MODE_1("三段-1"), MODE_2("三段-2"), MODE_3("三段-3"),
    GOHOME("返航"), PAUSE("急停"), AUTH_LED("LED鉴权"),
    D_UP("5D上"), D_DOWN("5D下"), D_LEFT("5D左"), D_RIGHT("5D右"), D_MIDDLE("5D中"),
    POWER("电源键")
}

/** 按键动作语义(通道层完成翻译,订阅者看到统一模型) */
enum class TeleautoKeyAction {
    DOWN,          // 电平按下(GPIO)
    UP,            // 电平抬起(GPIO)
    TRIGGER,       // 脉冲触发(拍照/对焦/C1等)
    TOGGLED_ON,    // 翻转开(录像开始)
    TOGGLED_OFF    // 翻转关(录像停止)
}

/** SDK 支持的硬件配置家族；系统机型代号统一归入其中之一。 */
enum class TeleautoHardwareProfile(val id: String) {
    RM500("rm500"), RM700("rm700"), RM510("rm510"), RC520("rc520")
}

enum class TeleautoHardwareControlSide { LEFT, CENTER, RIGHT }

/** Stable console slot. Unsupported controls leave their slot empty instead of reflowing peers. */
enum class TeleautoControlSlot {
    ROUND_1, ROUND_2, ROUND_3,
    LEFT_1, LEFT_2, LEFT_3,
    RIGHT_1, RIGHT_2, RIGHT_3,
}

data class TeleautoHardwareControlPlacement(
    val side: TeleautoHardwareControlSide,
    val slot: TeleautoControlSlot,
)

/** Stable functional grouping; UI placement may differ between hardware profiles. */
enum class TeleautoHardwareControlGroup { SAFETY, CUSTOM, AUXILIARY, MEDIA, NAVIGATION }

/** Profile 提供的强类型硬件描述；id严格使用“位置 + 类型 + 序号”。 */
sealed interface TeleautoHardwareControlDescriptor {
    val id: String
    val displayName: String
    val side: TeleautoHardwareControlSide
    val order: Int
    val inputs: Set<TeleautoPhysicalKey>
    val group: TeleautoHardwareControlGroup
        get() = when {
            inputs.any { it == TeleautoPhysicalKey.GOHOME || it == TeleautoPhysicalKey.PAUSE } -> TeleautoHardwareControlGroup.SAFETY
            inputs.any { it == TeleautoPhysicalKey.C1 || it == TeleautoPhysicalKey.C2 || it == TeleautoPhysicalKey.C3 } -> TeleautoHardwareControlGroup.CUSTOM
            inputs.any { it == TeleautoPhysicalKey.FOCUS || it == TeleautoPhysicalKey.SHUTTER || it == TeleautoPhysicalKey.RECORD } -> TeleautoHardwareControlGroup.MEDIA
            this is TeleautoFiveWayControl -> TeleautoHardwareControlGroup.NAVIGATION
            else -> TeleautoHardwareControlGroup.AUXILIARY
        }
    val placement: TeleautoHardwareControlPlacement?
        get() = null
}

data class TeleautoClickButtonControl(
    override val id: String, override val displayName: String, override val side: TeleautoHardwareControlSide,
    override val order: Int, val input: TeleautoPhysicalKey,
    override val placement: TeleautoHardwareControlPlacement? = null,
) : TeleautoHardwareControlDescriptor { override val inputs = setOf(input) }

data class TeleautoHoldButtonControl(
    override val id: String, override val displayName: String, override val side: TeleautoHardwareControlSide,
    override val order: Int, val input: TeleautoPhysicalKey,
    override val placement: TeleautoHardwareControlPlacement? = null,
) : TeleautoHardwareControlDescriptor { override val inputs = setOf(input) }

data class TeleautoFiveWayControl(
    override val id: String, override val displayName: String, override val side: TeleautoHardwareControlSide,
    override val order: Int, val up: TeleautoPhysicalKey, val down: TeleautoPhysicalKey, val left: TeleautoPhysicalKey,
    val right: TeleautoPhysicalKey, val center: TeleautoPhysicalKey,
) : TeleautoHardwareControlDescriptor { override val inputs = setOf(up, down, left, right, center) }

data class TeleautoCenteringDialControl(
    override val id: String, override val displayName: String, override val side: TeleautoHardwareControlSide,
    override val order: Int, val axis: TeleautoPhysicalKey,
) : TeleautoHardwareControlDescriptor { override val inputs = setOf(axis) }

data class TeleautoContinuousDialControl(
    override val id: String, override val displayName: String, override val side: TeleautoHardwareControlSide,
    override val order: Int, val axis: TeleautoPhysicalKey,
    /** Relative hardware steps required to travel from zero to either endpoint. */
    val stepsPerFullScale: Int = 10,
) : TeleautoHardwareControlDescriptor {
    init { require(stepsPerFullScale > 0) }
    override val inputs = setOf(axis)
}

data class TeleautoJoystickControl(
    override val id: String, override val displayName: String, override val side: TeleautoHardwareControlSide,
    override val order: Int, val xAxis: TeleautoPhysicalKey, val yAxis: TeleautoPhysicalKey,
) : TeleautoHardwareControlDescriptor { override val inputs = setOf(xAxis, yAxis) }

data class TeleautoThreePositionSwitchControl(
    override val id: String, override val displayName: String, override val side: TeleautoHardwareControlSide,
    override val order: Int, val modes: List<TeleautoThreePositionSwitchMode>, val positionInputs: List<TeleautoPhysicalKey>,
) : TeleautoHardwareControlDescriptor {
    init { require(modes.size == 3 && positionInputs.size == 3) }
    val labels: List<String> get() = modes.map(TeleautoThreePositionSwitchMode::label)
    override val inputs = positionInputs.toSet()
}

/** SDK 所需的本地运行环境状态。实现细节不会暴露给宿主界面。 */
enum class TeleautoRuntimeEnvironmentState { CHECKING, READY, PREPARATION_REQUIRED, UNSUPPORTED }

/**
 * 初始化期间生成的环境快照。宿主只需展示 [userMessage]；当
 * [assistantAction] 非空时，应引导用户使用 Teleauto Assistant 完成一键准备。
 */
data class TeleautoRuntimeEnvironmentStatus(
    val state: TeleautoRuntimeEnvironmentState,
    val userMessage: String,
    val assistantAction: String? = null,
    val checkedAtMs: Long = System.currentTimeMillis(),
)

/** 按键事件(不可变) */
data class TeleautoRcKeyEvent(
    val key: TeleautoPhysicalKey,
    val action: TeleautoKeyAction,
    val timestampNs: Long = System.nanoTime()
)

/** 摇杆/拨轮不可变快照,高频覆盖式更新,读端零锁 */
data class TeleautoStickSnapshot(
    val leftX: Float = 0f, val leftY: Float = 0f,
    val rightX: Float = 0f, val rightY: Float = 0f,
    val leftDial: Float = 0f, val rightDial: Float = 0f,
    val freeDial: Float = 0f,      // 右侧无限连续波轮(rc701 系,经固件 TCP 遥测)
    val timestampNs: Long = System.nanoTime()
)

/** 三档物理开关；具体业务用途由宿主根据绑定设备决定。 */
enum class TeleautoThreePositionSwitchPosition { LEFT, CENTER, RIGHT, UNKNOWN }

/** DJI 三档开关的标准档位身份；宿主可按绑定设备重新解释其业务用途。 */
enum class TeleautoThreePositionSwitchMode(val rawValue: Int, val label: String) {
    CINE(0, "C"), NORMAL(1, "N"), SPORT(2, "S"), UNKNOWN(-1, "—");

    companion object {
        fun fromRawValue(value: Int): TeleautoThreePositionSwitchMode =
            entries.firstOrNull { it.rawValue == value } ?: UNKNOWN
    }
}

data class TeleautoThreePositionSwitchDescriptor(
    val leftLabel: String,
    val centerLabel: String,
    val rightLabel: String,
)

data class TeleautoThreePositionSwitchState(
    /** 无法读取或尚未上报时安全回退到物理中档。 */
    val position: TeleautoThreePositionSwitchPosition = TeleautoThreePositionSwitchPosition.CENTER,
    val timestampNs: Long = System.nanoTime(),
) {
    val rawValue: Int get() = when (position) {
        TeleautoThreePositionSwitchPosition.LEFT -> TeleautoThreePositionSwitchMode.CINE.rawValue
        TeleautoThreePositionSwitchPosition.CENTER -> TeleautoThreePositionSwitchMode.NORMAL.rawValue
        TeleautoThreePositionSwitchPosition.RIGHT -> TeleautoThreePositionSwitchMode.SPORT.rawValue
        TeleautoThreePositionSwitchPosition.UNKNOWN -> TeleautoThreePositionSwitchMode.UNKNOWN.rawValue
    }
    val mode: TeleautoThreePositionSwitchMode get() = TeleautoThreePositionSwitchMode.fromRawValue(rawValue)

    companion object {
        fun fromRawValue(value: Int, timestampNs: Long = System.nanoTime()) = TeleautoThreePositionSwitchState(
            position = when (TeleautoThreePositionSwitchMode.fromRawValue(value)) {
                TeleautoThreePositionSwitchMode.CINE -> TeleautoThreePositionSwitchPosition.LEFT
                TeleautoThreePositionSwitchMode.NORMAL -> TeleautoThreePositionSwitchPosition.CENTER
                TeleautoThreePositionSwitchMode.SPORT -> TeleautoThreePositionSwitchPosition.RIGHT
                TeleautoThreePositionSwitchMode.UNKNOWN -> TeleautoThreePositionSwitchPosition.UNKNOWN
            },
            timestampNs = timestampNs,
        )
    }
}

@Deprecated("Use TeleautoThreePositionSwitchPosition")
typealias FlightModeSwitchPosition = TeleautoThreePositionSwitchPosition
@Deprecated("Use TeleautoThreePositionSwitchDescriptor")
typealias FlightModeSwitchDescriptor = TeleautoThreePositionSwitchDescriptor
@Deprecated("Use TeleautoThreePositionSwitchState")
typealias FlightModeSwitchState = TeleautoThreePositionSwitchState

/** 连接状态(DISCONNECTED 时宿主自身的 KeyEvent 通道不受影响) */
enum class TeleautoConnectionState {
    DISCONNECTED,  // 未初始化或 adb 传输断开
    CONNECTING,    // 初始化完成,连接建立中
    CONNECTED      // adb 传输正常,全部通道工作
}

/** 输入链路需要用户处理的问题；用于让宿主给出明确且可恢复的提示。 */
enum class TeleautoInputConnectionIssue {
    ADB_AUTHORIZATION_REQUIRED,
    ADB_TRANSPORT_PREPARATION_REQUIRED,
    DIRECT_INPUT_PERMISSION_REQUIRED,
}

/** 设备信息(初始化时探测) */
data class TeleautoRemoteControllerInfo(
    val displayName: String,     // DJI RC Plus 2
    val modelCode: String,       // rc701
    val productCode: String,     // rc701
    val deviceCode: String,      // rc701
    val manufacturer: String,    // DJI
    val firmwareVersion: String, // dji.prop.external_version
    val androidVersion: String,
    /** SoC/板级平台(ro.board.platform,如 rk3399;取不到时回退 Build.BOARD)。 */
    val boardPlatform: String = "",
    val hardwareProfile: TeleautoHardwareProfile?,
    val supportedInputs: Set<TeleautoPhysicalKey>,
    /** null 表示该机型尚未验证到稳定可读的三段开关事件。 */
    val threePositionSwitch: TeleautoThreePositionSwitchDescriptor? = null,
    /** 由当前硬件 Profile 提供的首页主功能键顺序。 */
    val primaryControlOrder: List<TeleautoPhysicalKey> = emptyList(),
    /** 遥控器机身左、右侧的物理按键顺序；宿主据此还原硬件布局。 */
    val leftControlOrder: List<TeleautoPhysicalKey> = emptyList(),
    val rightControlOrder: List<TeleautoPhysicalKey> = emptyList(),
    val hardwareControls: List<TeleautoHardwareControlDescriptor> = emptyList(),
) {
    @Deprecated("Use threePositionSwitch", ReplaceWith("threePositionSwitch"))
    val flightModeSwitch: TeleautoThreePositionSwitchDescriptor? get() = threePositionSwitch
    val profileId: String get() = hardwareProfile?.id ?: "unknown"
    @Deprecated("Use displayName", ReplaceWith("displayName"))
    val model: String get() = displayName
    @Deprecated("Use productCode", ReplaceWith("productCode"))
    val product: String get() = productCode
    @Deprecated("Use deviceCode", ReplaceWith("deviceCode"))
    val device: String get() = deviceCode
}

/** 遥控器系统聚合电池状态（与 Android 状态栏电量同源）。 */
data class TeleautoBatteryState(
    val levelPercent: Int,
    val status: TeleautoBatteryStatus,
    val isPresent: Boolean,
    val voltageMv: Int,
    val temperatureTenthsC: Int,
    val timestampMs: Long = System.currentTimeMillis(),
)

enum class TeleautoBatteryStatus {
    UNKNOWN,
    CHARGING,
    DISCHARGING,
    NOT_CHARGING,
    FULL,
}

/** Independently reported battery; identity is stable across level updates. */
data class TeleautoBatterySource(
    val id: String,
    val displayName: String,
    val levelPercent: Int?,
    val isPresent: Boolean,
)
