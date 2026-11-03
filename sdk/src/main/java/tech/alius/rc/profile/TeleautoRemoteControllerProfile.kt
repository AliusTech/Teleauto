package tech.alius.rc.profile

import android.content.Context
import tech.alius.rc.model.TeleautoRemoteControllerInfo
import tech.alius.rc.model.TeleautoHardwareProfile
import tech.alius.rc.model.TeleautoHardwareControlDescriptor
import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoRcKeyEvent
import tech.alius.rc.model.TeleautoStickSnapshot
import tech.alius.rc.model.TeleautoThreePositionSwitchDescriptor
import tech.alius.rc.model.TeleautoThreePositionSwitchState
import tech.alius.rc.model.TeleautoInputConnectionIssue

/** DJI RC 机型档案基类:描述遥控器能力与输入映射,新机型只需新增实现 */
interface TeleautoRemoteControllerProfile {
    val id: String                    // "rc-plus"
    val hardwareProfile: TeleautoHardwareProfile
    val productNames: Map<String, String> // system model -> public product name
    val matchModels: Set<String>      // 支持的 ro.product.name 代号集(严格精确匹配),如 {"rm700","rc701"}
    val supportedInputs: Set<TeleautoPhysicalKey>
    /** 首页主功能区顺序；由硬件 Profile 决定，宿主不得按型号硬编码。 */
    val primaryControlOrder: List<TeleautoPhysicalKey> get() = emptyList()
    /** 左右摇杆下方的硬件事件区；只声明该机型真实存在且已验证的输入。 */
    val leftControlOrder: List<TeleautoPhysicalKey> get() = emptyList()
    val rightControlOrder: List<TeleautoPhysicalKey> get() = emptyList()
    val hardwareControls: List<TeleautoHardwareControlDescriptor> get() = emptyList()
    val threePositionSwitch: TeleautoThreePositionSwitchDescriptor? get() = null

    fun displayName(modelCode: String): String =
        productNames[modelCode.lowercase()] ?: productNames.values.first()

    /** SDL 枚举时用于排除遥控器自身的内置 HID，避免误显示为外置摇杆。 */
    fun isEmbeddedJoystick(name: String, vendorId: Int, productId: Int): Boolean = false

    /** 启动各输入通道 */
    fun start(context: Context, callback: Callback)

    /** Foreground Android input fallback; profiles without a standard input mapping ignore it. */
    fun handleMotionEvent(event: android.view.MotionEvent): Boolean = false

    /** 停止并释放资源(幂等) */
    fun stop()

    /**
     * 设置无限连续波轮的标准化当前位置。仅具备 [TeleautoPhysicalKey.FREE_DIAL]
     * 且输入通道已经启动的 Profile 返回 true。
     */
    fun setFreeDialValue(value: Float): Boolean = false

    // 注:GPIO 肩键(KEY_F1–F6)由 SDK 经 evdev 通道统一翻译为 L1–L3/R1–R3 事件;
    // 系统的标准 KeyEvent 分发不受影响,宿主 Activity 仍可并行用 onKeyDown/onKeyUp 处理

    /** profile → 门面 的上行回调 */
    interface Callback {
        fun onKeyEvent(event: TeleautoRcKeyEvent)
        fun onStickChanged(snapshot: TeleautoStickSnapshot)
        fun onHeldKeysChanged(held: Set<TeleautoPhysicalKey>)
        fun onConnectionChanged(connected: Boolean)
        fun onConnectionIssueChanged(issue: TeleautoInputConnectionIssue?) {}
        fun onThreePositionSwitchChanged(state: TeleautoThreePositionSwitchState) {}
    }
}

/** 机型注册表:按系统属性自动匹配 */
object TeleautoProfileRegistry {
    private val profiles = mutableListOf<TeleautoRemoteControllerProfile>()

    fun register(profile: TeleautoRemoteControllerProfile) {
        profiles.removeAll { it.id == profile.id }
        profiles.add(profile)
    }

    /** 优先按 ro.product.name 精确匹配,未命中回退 ro.product.model(部分机型两属性不一致);同款机型的代号显式列举在 matchModels */
    fun detect(product: String): TeleautoRemoteControllerProfile? =
        profiles.firstOrNull { p -> p.matchModels.any { it.equals(product.trim(), ignoreCase = true) } }

    fun all(): List<TeleautoRemoteControllerProfile> = profiles.toList()
}
