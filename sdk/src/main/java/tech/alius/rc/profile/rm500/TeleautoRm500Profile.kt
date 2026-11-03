package tech.alius.rc.profile.rm500

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import tech.alius.rc.internal.TeleautoDirectInputEventReader
import tech.alius.rc.internal.TeleautoProtocolInput
import tech.alius.rc.internal.TeleautoProtocolInputReader
import tech.alius.rc.internal.TeleautoTcpInputReader
import tech.alius.rc.model.TeleautoHardwareProfile
import tech.alius.rc.model.TeleautoHardwareControlCatalog
import tech.alius.rc.model.TeleautoKeyAction
import tech.alius.rc.model.TeleautoInputConnectionIssue
import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoRcKeyEvent
import tech.alius.rc.model.TeleautoStickSnapshot
import tech.alius.rc.model.TeleautoThreePositionSwitchDescriptor
import tech.alius.rc.model.TeleautoThreePositionSwitchPosition
import tech.alius.rc.model.TeleautoThreePositionSwitchState
import tech.alius.rc.profile.TeleautoRemoteControllerProfile
import tech.alius.rc.profile.config.TeleautoRcInputProfiles

/**
 * RM500 / ag501 第一代带屏遥控器(RK3399 / Android 7.1)。
 * 摇杆使用 evdev;波轮和 C1/C2 优先使用厂商 ProtocolManager 状态回调。
 * 半按对焦(FOCUS)只在 DUML 位域(bit7)出现,evdev 无对应键码;
 * 拍照全按(bit6)/录像(bit8)与 evdev 重复,保持 evdev 单通道。
 */
open class TeleautoRm500Profile : TeleautoRemoteControllerProfile {
    override val id = "rm500"
    override val hardwareProfile = TeleautoHardwareProfile.RM500
    override val productNames = mapOf(
        "rm500" to "DJI SMART CONTROLLER",
        "ag501" to "DJI SMART CONTROLLER",
    )
    override val matchModels: Set<String> get() = productNames.keys
    override val threePositionSwitch = TeleautoThreePositionSwitchDescriptor("C", "N", "S")
    override val supportedInputs: Set<TeleautoPhysicalKey> = setOf(
        TeleautoPhysicalKey.LEFT_STICK_X, TeleautoPhysicalKey.LEFT_STICK_Y,
        TeleautoPhysicalKey.RIGHT_STICK_X, TeleautoPhysicalKey.RIGHT_STICK_Y,
        TeleautoPhysicalKey.LEFT_DIAL, TeleautoPhysicalKey.RIGHT_DIAL,
        TeleautoPhysicalKey.MODE_1, TeleautoPhysicalKey.MODE_2, TeleautoPhysicalKey.MODE_3,
        TeleautoPhysicalKey.SHUTTER, TeleautoPhysicalKey.RECORD, TeleautoPhysicalKey.FOCUS,
        TeleautoPhysicalKey.C1, TeleautoPhysicalKey.C2, TeleautoPhysicalKey.GOHOME,
        TeleautoPhysicalKey.D_UP, TeleautoPhysicalKey.D_DOWN, TeleautoPhysicalKey.D_LEFT,
        TeleautoPhysicalKey.D_RIGHT, TeleautoPhysicalKey.D_MIDDLE,
    )
    open override val primaryControlOrder = listOf(TeleautoPhysicalKey.GOHOME, TeleautoPhysicalKey.C1, TeleautoPhysicalKey.C2)
    override val leftControlOrder = listOf(TeleautoPhysicalKey.GOHOME, TeleautoPhysicalKey.C1, TeleautoPhysicalKey.C2)
    override val rightControlOrder = listOf(
        TeleautoPhysicalKey.D_UP, TeleautoPhysicalKey.D_LEFT, TeleautoPhysicalKey.D_MIDDLE,
        TeleautoPhysicalKey.D_RIGHT, TeleautoPhysicalKey.D_DOWN,
    )
    override val hardwareControls = listOf(
        TeleautoHardwareControlCatalog.LEFT_CENTERING_DIAL_01,
        TeleautoHardwareControlCatalog.LEFT_JOYSTICK_01,
        TeleautoHardwareControlCatalog.RTH_BUTTON,
        TeleautoHardwareControlCatalog.CUSTOM_BUTTON_C1,
        TeleautoHardwareControlCatalog.RIGHT_CENTERING_DIAL_01,
        TeleautoHardwareControlCatalog.RIGHT_JOYSTICK_01,
        TeleautoHardwareControlCatalog.RIGHT_FIVE_WAY_01,
        TeleautoHardwareControlCatalog.CUSTOM_BUTTON_C2,
        TeleautoHardwareControlCatalog.FOCUS_BUTTON,
        TeleautoHardwareControlCatalog.PHOTO_BUTTON,
        TeleautoHardwareControlCatalog.RECORD_BUTTON,
        TeleautoHardwareControlCatalog.RIGHT_THREE_POSITION_SWITCH_01,
    )

    private var callback: TeleautoRemoteControllerProfile.Callback? = null
    private var inputReader: TeleautoDirectInputEventReader? = null
    private var tcpReader: TeleautoTcpInputReader? = null
    private var protocolReader: TeleautoProtocolInputReader? = null
    @Volatile private var protocolInputSeen = false
    @Volatile private var readerGeneration = 0L
    private val held = linkedSetOf<TeleautoPhysicalKey>()
    @Volatile private var dumlInputSeen = false
    @Volatile private var threePositionRaw = -1
    private var leftX = 0f
    private var leftY = 0f
    private var rightX = 0f
    private var rightY = 0f
    private var leftDial = 0f
    private var rightDial = 0f
    @Volatile private var androidInputSeen = false
    private var axesDirty = false
    private var recordOn = false
    private var lastRecordMs = 0L

    override fun isEmbeddedJoystick(name: String, vendorId: Int, productId: Int): Boolean =
        vendorId == DJI_VENDOR_ID && productId == AG501_PRODUCT_ID && name.equals(DJI_RC_NAME, true)

    @Synchronized
    override fun start(context: Context, callback: TeleautoRemoteControllerProfile.Callback) {
        stop()
        this.callback = callback
        val generation = ++readerGeneration
        protocolReader = TeleautoProtocolInputReader { input ->
            synchronized(this) {
                if (generation == readerGeneration) handleProtocolInput(input)
            }
        }.also { it.start() }
        inputReader = TeleautoDirectInputEventReader(
            onAxis = { code, _, value ->
                if (generation == readerGeneration) handleAxis(code, value)
            },
            onKey = { code, down ->
                if (generation == readerGeneration) handleLinuxKey(code, down)
            },
            onState = { connected ->
                if (generation == readerGeneration) {
                    if (!connected && !protocolInputSeen && !androidInputSeen) resetInput()
                    callback.onConnectionChanged(connected || androidInputSeen)
                }
            },
            onPermissionRequired = { required ->
                if (generation == readerGeneration) {
                    callback.onConnectionIssueChanged(
                        TeleautoInputConnectionIssue.DIRECT_INPUT_PERMISSION_REQUIRED.takeIf { required && !androidInputSeen },
                    )
                }
            },
            devicePath = TeleautoRcInputProfiles.get(context, id).eventDevice,
            onSync = { timestampNs ->
                if (generation == readerGeneration) publishAxes(timestampNs)
            },
        ).also { it.start() }

        // RM500 的三档、C1/C2/RTH 不属于 event1 轴。厂商 40007 的
        // DUML 0x06/0xAE 状态帧与 RM700 使用相同位域；TCP 一旦产生
        // 首帧即成为这三个电平按键的权威通道，event1 只作首帧前兜底。
        tcpReader?.stop()
        tcpReader = TeleautoTcpInputReader(
            port = DUML_TELEMETRY_PORT,
            onMode = { position ->
                if (generation == readerGeneration && !protocolInputSeen) handleDumlMode(position)
            },
            onFreeDial = {},
            onRcState = { buttons, modes ->
                if (generation == readerGeneration) handleDumlRcState(buttons, modes)
            },
            onState = { connected ->
                if (generation == readerGeneration && !connected) resetDumlInput()
            },
        ).also { it.start() }
    }

    @Synchronized
    override fun stop() {
        readerGeneration++
        protocolReader?.stop()
        protocolReader = null
        protocolInputSeen = false
        androidInputSeen = false
        inputReader?.stop()
        inputReader = null
        tcpReader?.stop()
        tcpReader = null
        resetInput()
        callback = null
    }

    @Synchronized
    override fun handleMotionEvent(event: MotionEvent): Boolean {
        if (callback == null || event.actionMasked != MotionEvent.ACTION_MOVE ||
            event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        val device = event.device ?: return false
        if (!isEmbeddedJoystick(device.name, device.vendorId, device.productId)) return false
        if (!androidInputSeen) {
            androidInputSeen = true
            callback?.onConnectionIssueChanged(null)
            callback?.onConnectionChanged(true)
        }
        fun axis(code: Int, androidAxis: Int) {
            if (device.getMotionRange(androidAxis, event.source) == null) return
            val value = event.getAxisValue(androidAxis)
            handleAxis(code, if (kotlin.math.abs(value) < 0.04f) 0f else value.coerceIn(-1f, 1f))
        }
        axis(0, MotionEvent.AXIS_X)
        axis(1, MotionEvent.AXIS_Y)
        axis(3, MotionEvent.AXIS_RX)
        axis(4, MotionEvent.AXIS_RY)
        axis(2, MotionEvent.AXIS_Z)
        axis(5, MotionEvent.AXIS_RZ)
        axis(16, MotionEvent.AXIS_HAT_X)
        axis(17, MotionEvent.AXIS_HAT_Y)
        publishAxes(System.nanoTime())
        return true
    }

    @Synchronized
    private fun handleAxis(code: Int, value: Float) {
        if (protocolInputSeen && code in setOf(2, 5)) return
        when (code) {
            0 -> leftX = value
            1 -> leftY = value
            2 -> leftDial = value
            3 -> rightX = value
            4 -> rightY = value
            5 -> rightDial = value
            16 -> updateDirectional(TeleautoPhysicalKey.D_LEFT, TeleautoPhysicalKey.D_RIGHT, value)
            17 -> updateDirectional(TeleautoPhysicalKey.D_UP, TeleautoPhysicalKey.D_DOWN, value)
            else -> return
        }
        axesDirty = true
    }

    @Synchronized
    private fun publishAxes(timestampNs: Long) {
        if (!axesDirty) return
        axesDirty = false
        callback?.onStickChanged(
            TeleautoStickSnapshot(leftX, leftY, rightX, rightY, leftDial, rightDial, 0f, timestampNs)
        )
    }

    private fun updateDirectional(negative: TeleautoPhysicalKey, positive: TeleautoPhysicalKey, value: Float) {
        setHeld(negative, value < -0.5f)
        setHeld(positive, value > 0.5f)
    }

    private fun handleLinuxKey(code: Int, down: Boolean) {
        val key = LINUX_KEYS[code] ?: return
        if (key == TeleautoPhysicalKey.RECORD) {
            if (down) toggleRecord()
        } else {
            setHeld(key, down)
        }
    }

    @Synchronized
    private fun setHeld(key: TeleautoPhysicalKey, down: Boolean) {
        if (key !in supportedInputs) return
        if (dumlInputSeen && key in DUML_LEVEL_KEYS) return
        val changed = if (down) held.add(key) else held.remove(key)
        if (!changed) return
        callback?.onKeyEvent(TeleautoRcKeyEvent(key, if (down) TeleautoKeyAction.DOWN else TeleautoKeyAction.UP))
        callback?.onHeldKeysChanged(held.toSet())
    }

    /** 40007 逐帧电平；批量替换后只发布一次 held 快照。 */
    @Synchronized
    private fun handleDumlRcState(buttonBits: Int, modeBits: Int) {
        if (protocolInputSeen) return
        applyRcState(buttonBits, modeBits)
    }

    @Synchronized
    private fun handleProtocolInput(input: TeleautoProtocolInput) {
        protocolInputSeen = true
        leftDial = input.leftDial
        rightDial = input.rightDial
        axesDirty = true
        publishAxes(System.nanoTime())
        applyRcState(input.buttons and 0xffff, input.buttons ushr 16)
        handleDumlMode((input.buttons ushr 16) and 0x3)
    }

    private fun applyRcState(buttonBits: Int, modeBits: Int) {
        dumlInputSeen = true
        val next = buildSet {
            if ((buttonBits and (1 shl 5)) != 0) add(TeleautoPhysicalKey.GOHOME)
            // bit7 半按对焦;bit6 拍照全按与 evdev 重复,不接以免双份事件。
            if ((buttonBits and (1 shl 7)) != 0) add(TeleautoPhysicalKey.FOCUS)
            if ((modeBits and (1 shl 2)) != 0) add(TeleautoPhysicalKey.C1)
            if ((modeBits and (1 shl 3)) != 0) add(TeleautoPhysicalKey.C2)
        }
        val previous = held.filterTo(linkedSetOf()) { it in DUML_LEVEL_KEYS }
        if (previous == next) return
        held.removeAll(DUML_LEVEL_KEYS)
        held.addAll(next)
        (next - previous).forEach { callback?.onKeyEvent(TeleautoRcKeyEvent(it, TeleautoKeyAction.DOWN)) }
        (previous - next).forEach { callback?.onKeyEvent(TeleautoRcKeyEvent(it, TeleautoKeyAction.UP)) }
        callback?.onHeldKeysChanged(held.toSet())
    }

    @Synchronized
    private fun handleDumlMode(position: Int) {
        if (threePositionRaw == position) return
        threePositionRaw = position
        val modeKey = when (position) {
            0 -> TeleautoPhysicalKey.MODE_1
            1 -> TeleautoPhysicalKey.MODE_2
            2 -> TeleautoPhysicalKey.MODE_3
            else -> null
        }
        modeKey?.let { callback?.onKeyEvent(TeleautoRcKeyEvent(it, TeleautoKeyAction.TRIGGER)) }
        callback?.onThreePositionSwitchChanged(TeleautoThreePositionSwitchState.fromRawValue(position))
    }

    @Synchronized
    private fun resetDumlInput() {
        if (protocolInputSeen) return
        val previous = held.filterTo(linkedSetOf()) { it in DUML_LEVEL_KEYS }
        held.removeAll(DUML_LEVEL_KEYS)
        previous.forEach { callback?.onKeyEvent(TeleautoRcKeyEvent(it, TeleautoKeyAction.UP)) }
        if (previous.isNotEmpty()) callback?.onHeldKeysChanged(held.toSet())
        dumlInputSeen = false
        threePositionRaw = -1
        callback?.onThreePositionSwitchChanged(
            TeleautoThreePositionSwitchState(TeleautoThreePositionSwitchPosition.UNKNOWN),
        )
    }

    private fun toggleRecord() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRecordMs < RECORD_DEBOUNCE_MS) return
        lastRecordMs = now
        recordOn = !recordOn
        callback?.onKeyEvent(
            TeleautoRcKeyEvent(TeleautoPhysicalKey.RECORD, if (recordOn) TeleautoKeyAction.TOGGLED_ON else TeleautoKeyAction.TOGGLED_OFF)
        )
    }

    @Synchronized
    private fun resetInput() {
        held.clear()
        dumlInputSeen = false
        threePositionRaw = -1
        leftX = 0f
        leftY = 0f
        rightX = 0f
        rightY = 0f
        leftDial = 0f
        rightDial = 0f
        axesDirty = false
        callback?.onHeldKeysChanged(emptySet())
        callback?.onStickChanged(TeleautoStickSnapshot())
        callback?.onThreePositionSwitchChanged(
            TeleautoThreePositionSwitchState(TeleautoThreePositionSwitchPosition.UNKNOWN),
        )
    }

    private companion object {
        const val DJI_RC_NAME = "DJI RC"
        const val DJI_VENDOR_ID = 0x2ca3
        const val AG501_PRODUCT_ID = 0x1501
        const val RECORD_DEBOUNCE_MS = 250L
        const val DUML_TELEMETRY_PORT = 40007
        // DUML 0x06/0xAE 位域与 MSDK v5 键对照(EdgeTX rcplus2 逆向佐证,详见
        // docs/probes/edgetx-rcplus2-keyevents.md):bit5=返航 KeyGoHomeButtonDown、
        // bit6=拍照全按 KeyShutterButtonDown、bit7=半按对焦、bit8=录像
        // KeyRecordButtonDown;modeBits 低 2 位=三段开关 KeyFlightModeSwitchState
        // (0/1/2 = C/N/S),bit2=C1(KeyCustomButton1Down)、bit3=C2
        // (KeyCustomButton2Down)。一代 RM500 无 C3 硬件,KeyCustomButton3Down
        // 仅 RC Plus 系列(rm700 档案)存在。
        val DUML_LEVEL_KEYS = setOf(TeleautoPhysicalKey.GOHOME, TeleautoPhysicalKey.C1, TeleautoPhysicalKey.C2, TeleautoPhysicalKey.FOCUS)
        val LINUX_KEYS = mapOf(
            304 to TeleautoPhysicalKey.SHUTTER,
            307 to TeleautoPhysicalKey.C1,
            308 to TeleautoPhysicalKey.C2,
            310 to TeleautoPhysicalKey.RECORD,
            311 to TeleautoPhysicalKey.SHUTTER,
            314 to TeleautoPhysicalKey.GOHOME,
            317 to TeleautoPhysicalKey.D_MIDDLE,
        )
    }
}
