package tech.alius.rc.profile.rm700

import android.content.Context
import android.os.Build
import android.util.Log
import tech.alius.rc.internal.TeleautoAdbInputReader
import tech.alius.rc.internal.TeleautoTcpInputReader
import tech.alius.rc.profile.TeleautoRemoteControllerProfile
import tech.alius.rc.internal.TeleautoAdbLogcatConfig
import tech.alius.rc.internal.TeleautoAdbLogcatReader
import tech.alius.rc.model.TeleautoHardwareProfile
import tech.alius.rc.model.TeleautoHardwareControlCatalog
import tech.alius.rc.model.TeleautoInputConnectionIssue
import tech.alius.rc.model.TeleautoKeyAction
import tech.alius.rc.model.TeleautoThreePositionSwitchPosition
import tech.alius.rc.model.TeleautoThreePositionSwitchState
import tech.alius.rc.model.TeleautoContinuousDialControl
import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoRcKeyEvent
import tech.alius.rc.model.TeleautoStickSnapshot
import tech.alius.rc.profile.config.TeleautoRcInputProfiles

/**
 * RM700 类(RC Plus / RC Plus 2 / TKPL 农业带屏控,同硬件平台)。
 * 基础拓扑之外附加:GPIO 面板按键 L1-L3/R1-R3、5D、C3、急停、LED鉴权(均已实测)。
 * 输入通道:ADB 自连 evdev(摇杆/拨轮/拍照/电源/GPIO 肩键)+ logcat(私有键)。
 */
class TeleautoRm700Profile : TeleautoRemoteControllerProfile {

    internal val logcatConfig = TeleautoAdbLogcatConfig.RM700
    override val id = "rm700"
    override val hardwareProfile = TeleautoHardwareProfile.RM700
    override val productNames = mapOf(
        "rm700" to "RC Plus",
        "rc701" to "DJI RC PLUS 2",
    )
    override val matchModels = productNames.keys
    override val threePositionSwitch = tech.alius.rc.model.TeleautoThreePositionSwitchDescriptor("C", "N", "S")
    override val supportedInputs: Set<TeleautoPhysicalKey> = setOf(
        TeleautoPhysicalKey.LEFT_STICK_X, TeleautoPhysicalKey.LEFT_STICK_Y,
        TeleautoPhysicalKey.RIGHT_STICK_X, TeleautoPhysicalKey.RIGHT_STICK_Y,
        TeleautoPhysicalKey.LEFT_DIAL, TeleautoPhysicalKey.RIGHT_DIAL, TeleautoPhysicalKey.FREE_DIAL,
        TeleautoPhysicalKey.MODE_1, TeleautoPhysicalKey.MODE_2, TeleautoPhysicalKey.MODE_3,
        TeleautoPhysicalKey.SHUTTER, TeleautoPhysicalKey.RECORD, TeleautoPhysicalKey.POWER,
        // RM700 专属:

        TeleautoPhysicalKey.FOCUS, TeleautoPhysicalKey.C1, TeleautoPhysicalKey.C2, TeleautoPhysicalKey.GOHOME,
        TeleautoPhysicalKey.D_UP, TeleautoPhysicalKey.D_DOWN, TeleautoPhysicalKey.D_LEFT, TeleautoPhysicalKey.D_RIGHT, TeleautoPhysicalKey.D_MIDDLE,
        TeleautoPhysicalKey.L1, TeleautoPhysicalKey.L2, TeleautoPhysicalKey.L3,
        TeleautoPhysicalKey.R1, TeleautoPhysicalKey.R2, TeleautoPhysicalKey.R3,
        TeleautoPhysicalKey.C3, TeleautoPhysicalKey.PAUSE, TeleautoPhysicalKey.AUTH_LED
    )
    override val primaryControlOrder = listOf(
        TeleautoPhysicalKey.GOHOME, TeleautoPhysicalKey.PAUSE, TeleautoPhysicalKey.FOCUS,
        TeleautoPhysicalKey.C1, TeleautoPhysicalKey.C2, TeleautoPhysicalKey.C3,
    )
    override val leftControlOrder = listOf(
        TeleautoPhysicalKey.GOHOME, TeleautoPhysicalKey.PAUSE, TeleautoPhysicalKey.C1, TeleautoPhysicalKey.C2, TeleautoPhysicalKey.C3,
        TeleautoPhysicalKey.L1, TeleautoPhysicalKey.L2, TeleautoPhysicalKey.L3,
    )
    override val rightControlOrder = listOf(
        TeleautoPhysicalKey.D_UP, TeleautoPhysicalKey.D_LEFT, TeleautoPhysicalKey.D_MIDDLE,
        TeleautoPhysicalKey.D_RIGHT, TeleautoPhysicalKey.D_DOWN,
        TeleautoPhysicalKey.R1, TeleautoPhysicalKey.R2, TeleautoPhysicalKey.R3, TeleautoPhysicalKey.AUTH_LED,
    )
    override val hardwareControls = listOf(
        TeleautoHardwareControlCatalog.LEFT_CENTERING_DIAL_01,
        TeleautoHardwareControlCatalog.LEFT_JOYSTICK_01,
        TeleautoHardwareControlCatalog.RTH_BUTTON,
        TeleautoHardwareControlCatalog.PAUSE_BUTTON,
        TeleautoHardwareControlCatalog.CUSTOM_BUTTON_C1,
        TeleautoHardwareControlCatalog.LEFT_HOLD_BUTTON_L1,
        TeleautoHardwareControlCatalog.LEFT_HOLD_BUTTON_L2,
        TeleautoHardwareControlCatalog.LEFT_HOLD_BUTTON_L3,
        TeleautoHardwareControlCatalog.RIGHT_CENTERING_DIAL_01,
        TeleautoHardwareControlCatalog.RIGHT_JOYSTICK_01,
        TeleautoHardwareControlCatalog.RIGHT_FIVE_WAY_01,
        TeleautoHardwareControlCatalog.CUSTOM_BUTTON_C2,
        TeleautoHardwareControlCatalog.CUSTOM_BUTTON_C3,
        TeleautoHardwareControlCatalog.FOCUS_BUTTON,
        TeleautoHardwareControlCatalog.PHOTO_BUTTON,
        TeleautoHardwareControlCatalog.RECORD_BUTTON,
        TeleautoHardwareControlCatalog.RIGHT_HOLD_BUTTON_R1,
        TeleautoHardwareControlCatalog.RIGHT_HOLD_BUTTON_R2,
        TeleautoHardwareControlCatalog.RIGHT_HOLD_BUTTON_R3,
        TeleautoHardwareControlCatalog.AUTH_BUTTON,
        TeleautoHardwareControlCatalog.RIGHT_CONTINUOUS_DIAL_01,
        TeleautoHardwareControlCatalog.RIGHT_THREE_POSITION_SWITCH_01,
    )

    private fun eventDevice(context: Context): String {
        val product = Build.PRODUCT.orEmpty().lowercase()
        val model = Build.MODEL.orEmpty().lowercase()
        return if (product == "rc701" || model.contains("rc_plus_2")) {
            "/dev/input/event4"
        } else {
            TeleautoRcInputProfiles.get(context, id).eventDevice
        }
    }

    private fun mapProfileAction(name: String): TeleautoPhysicalKey? = when (name) {
        "ACTION_KEY_FOCUS" -> TeleautoPhysicalKey.FOCUS
        "ACTION_KEY_C1" -> TeleautoPhysicalKey.C1
        "ACTION_KEY_C2" -> TeleautoPhysicalKey.C2
        "ACTION_KEY_GOHOME" -> TeleautoPhysicalKey.GOHOME
        "ACTION_KEY_C3" -> TeleautoPhysicalKey.C3
        "ACTION_KEY_PAUSE" -> TeleautoPhysicalKey.PAUSE
        "ACTION_KEY_AUTH_LED" -> TeleautoPhysicalKey.AUTH_LED
        "ACTION_KEY_UP" -> TeleautoPhysicalKey.D_UP
        "ACTION_KEY_DOWN" -> TeleautoPhysicalKey.D_DOWN
        "ACTION_KEY_LEFT" -> TeleautoPhysicalKey.D_LEFT
        "ACTION_KEY_RIGHT" -> TeleautoPhysicalKey.D_RIGHT
        "ACTION_KEY_MIDDLE" -> TeleautoPhysicalKey.D_MIDDLE
        else -> null
    }




    /** 真正跨机型的基础输入；机型专属按键必须由子类显式附加。 */



    private var inputReader: TeleautoAdbInputReader? = null
    private var tcpReader: TeleautoTcpInputReader? = null
    @Volatile private var flightModePos: Int = -1  // -1=UNKNOWN
    private var callback: TeleautoRemoteControllerProfile.Callback? = null

    // 摇杆快照累积(reader 单线程回调)
    @Volatile private var leftX = 0f; @Volatile private var leftY = 0f
    @Volatile private var rightX = 0f; @Volatile private var rightY = 0f
    @Volatile private var leftDial = 0f; @Volatile private var rightDial = 0f
    @Volatile private var freeDial = 0f
    private var axesDirty = false
    private val axesLock = Any()

    // 录像 toggle 状态 + 防长按 repeat
    @Volatile private var recordOn = false
    @Volatile private var lastRecordMs = 0L
    private val logPending = StringBuilder()
    private var evdevHeld = emptySet<TeleautoPhysicalKey>()
    private var dumlHeld = emptySet<TeleautoPhysicalKey>()
    private var hatX = 0
    private var hatY = 0
    private var auxiliaryInputSeen = false

    @Synchronized
    override fun start(context: Context, callback: TeleautoRemoteControllerProfile.Callback) {
        // Activity 与前台服务可能在首次安装启动时几乎同时初始化。
        // 保证重入不会留下多个 reader 争抢 localhost adbd/getevent 流。
        inputReader?.stop()
        this.callback = callback
        inputReader = TeleautoAdbInputReader(
            onAxis = { code, _, norm -> handleAxis(code, norm) },
            onKey = { code, down -> when (code) {
                311 -> if (down) emit(TeleautoPhysicalKey.SHUTTER, TeleautoKeyAction.TRIGGER)
                116 -> emit(TeleautoPhysicalKey.POWER, if (down) TeleautoKeyAction.DOWN else TeleautoKeyAction.UP)
                // DJI embedded joystick exposes its 5D cap as standard Linux
                // gamepad controls on RC701. Consume these directly so the
                // public SDK does not depend solely on delayed vendor logs.
                317 -> updateEvdevHeld(TeleautoPhysicalKey.D_MIDDLE, down)
                544 -> updateEvdevHeld(TeleautoPhysicalKey.D_UP, down)
                545 -> updateEvdevHeld(TeleautoPhysicalKey.D_DOWN, down)
                546 -> updateEvdevHeld(TeleautoPhysicalKey.D_LEFT, down)
                547 -> updateEvdevHeld(TeleautoPhysicalKey.D_RIGHT, down)
                // GPIO 面板肩键(仅 rm700 系硬件具备;其他机型不会出现这些 evdev 事件)
                187 -> emit(TeleautoPhysicalKey.L1, if (down) TeleautoKeyAction.DOWN else TeleautoKeyAction.UP)
                188 -> emit(TeleautoPhysicalKey.L2, if (down) TeleautoKeyAction.DOWN else TeleautoKeyAction.UP)
                189 -> emit(TeleautoPhysicalKey.L3, if (down) TeleautoKeyAction.DOWN else TeleautoKeyAction.UP)
                190 -> emit(TeleautoPhysicalKey.R1, if (down) TeleautoKeyAction.DOWN else TeleautoKeyAction.UP)
                191 -> emit(TeleautoPhysicalKey.R2, if (down) TeleautoKeyAction.DOWN else TeleautoKeyAction.UP)
                192 -> emit(TeleautoPhysicalKey.R3, if (down) TeleautoKeyAction.DOWN else TeleautoKeyAction.UP)
            } },
            onState = { connected ->
                if (!connected) resetAxes()
                callback.onConnectionChanged(connected)
            },
            onAuthorizationRequired = { required ->
                callback.onConnectionIssueChanged(
                    TeleautoInputConnectionIssue.ADB_AUTHORIZATION_REQUIRED.takeIf { required },
                )
            },
            onTransportPreparationRequired = { required ->
                callback.onConnectionIssueChanged(
                    TeleautoInputConnectionIssue.ADB_TRANSPORT_PREPARATION_REQUIRED.takeIf { required },
                )
            },
            devicePath = eventDevice(context),
            onSync = ::publishAxes,
            auxiliaryCommand = TeleautoRcInputProfiles.get(context, id).logcatCommand,
            onAuxiliaryData = ::handleLogcatData,
        ).also { it.start(context) }

        // 高频 DUML 遥测通道：逐格发布 rc701 三段开关与无限连续波轮。
        tcpReader?.stop()
        tcpReader = TeleautoTcpInputReader(
            port = 40007,
            onMode = { pos ->
                val changed = flightModePos != pos
                flightModePos = pos
                if (changed) {
                    // 切到档位同时发 TRIGGER 事件(三段-1/2/3)
                    val modeKey = when (pos) {
                        0 -> TeleautoPhysicalKey.MODE_1
                        1 -> TeleautoPhysicalKey.MODE_2
                        2 -> TeleautoPhysicalKey.MODE_3
                        else -> null
                    }
                    modeKey?.let { emit(it, TeleautoKeyAction.TRIGGER) }
                    callback.onThreePositionSwitchChanged(TeleautoThreePositionSwitchState.fromRawValue(pos))
                }
            },
            onFreeDial = ::handleFreeDial,
            onRcState = ::handleDumlRcState,
            onState = { connected ->
                if (!connected) {
                    flightModePos = -1
                    updateDumlHeld(emptySet())
                    callback.onThreePositionSwitchChanged(TeleautoThreePositionSwitchState(TeleautoThreePositionSwitchPosition.UNKNOWN))
                }
            },
            freeDialStepsPerFullScale = hardwareControls
                .filterIsInstance<TeleautoContinuousDialControl>()
                .first { it.axis == TeleautoPhysicalKey.FREE_DIAL }
                .stepsPerFullScale,
        ).also { it.start() }
    }

    @Synchronized
    override fun stop() {
        inputReader?.stop(); inputReader = null
        tcpReader?.stop(); tcpReader = null
        flightModePos = -1
        recordOn = false
        logPending.setLength(0)
        evdevHeld = emptySet()
        dumlHeld = emptySet()
        hatX = 0; hatY = 0
        auxiliaryInputSeen = false
    }

    override fun setFreeDialValue(value: Float): Boolean =
        tcpReader?.setFreeDialValue(value) == true

    // ── evdev 摇杆/拨轮 → 快照 ──────────────────────
    private fun handleAxis(code: Int, norm: Float) {
        if (code == 16 || code == 17) {
            updateHat(code, norm)
            return
        }
        synchronized(axesLock) {
            when (code) {
                0 -> leftX = norm; 1 -> leftY = norm
                3 -> rightX = norm; 4 -> rightY = norm
                2 -> leftDial = norm; 5 -> rightDial = norm
                else -> return
            }
            axesDirty = true
        }
    }

    private fun publishAxes(timestampNs: Long) {
        val snapshot = synchronized(axesLock) {
            if (!axesDirty) return
            axesDirty = false
            TeleautoStickSnapshot(leftX, leftY, rightX, rightY, leftDial, rightDial, freeDial, timestampNs)
        }
        callback?.onStickChanged(snapshot)
    }

    /** TCP 与 evdev 是独立线程；连续波轮必须自行立即发布，不能等待下一次 SYN_REPORT。 */
    private fun handleFreeDial(norm: Float) {
        synchronized(axesLock) {
            freeDial = norm.coerceIn(-1f, 1f)
            axesDirty = true
        }
        publishAxes(System.nanoTime())
    }

    private fun resetAxes() {
        synchronized(axesLock) {
            axesDirty = false
            leftX = 0f; leftY = 0f; rightX = 0f; rightY = 0f
            leftDial = 0f; rightDial = 0f; freeDial = 0f
        }
        callback?.onStickChanged(TeleautoStickSnapshot())
    }

    private fun resetFreeDial() {
        val snapshot = synchronized(axesLock) {
            if (freeDial == 0f) return
            freeDial = 0f
            axesDirty = false
            TeleautoStickSnapshot(leftX, leftY, rightX, rightY, leftDial, rightDial, freeDial)
        }
        callback?.onStickChanged(snapshot)
    }

    // ── dpad 私有键 → 统一语义 ───────────────────────
    private fun handleAction(action: String) {
        val key = actionToKey(action.substringAfterLast('.')) ?: return
        // RC701 exposes every 5D direction through event4 with real DOWN/UP.
        // The vendor log arrives after release and must never be merged back
        // into held state, otherwise opposite directions can remain lit.
        if (key in FIVE_WAY_KEYS || key in DUML_LEVEL_KEYS) return
        when (key) {
            TeleautoPhysicalKey.RECORD -> {  // toggle 语义,1.5s 防长按 repeat 误翻转
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastRecordMs > 1500) {
                    lastRecordMs = now
                    recordOn = !recordOn
                    emit(key, if (recordOn) TeleautoKeyAction.TOGGLED_ON else TeleautoKeyAction.TOGGLED_OFF)
                }
            }
            else -> emit(key, TeleautoKeyAction.TRIGGER)
        }
    }

    private fun handleLogcatData(data: ByteArray) {
        if (!auxiliaryInputSeen) {
            auxiliaryInputSeen = true
            Log.i(TAG, "vendor key stream connected")
        }
        val config = logcatConfig
        logPending.append(String(data, Charsets.UTF_8))
        while (true) {
            val newline = logPending.indexOf('\n')
            if (newline < 0) return
            val line = logPending.substring(0, newline)
            logPending.delete(0, newline + 1)
            // mKeyCodeList is the vendor service's debounced action result,
            // not a live held-key snapshot. `send key action` below is the
            // single authoritative semantic event for C1/C2/C3/RTH/Pause.
            config.actionPattern.find(line)?.groupValues?.getOrNull(1)?.let(::handleAction)
        }
    }

    private fun actionToKey(name: String): TeleautoPhysicalKey? = when (name) {
        "ACTION_KEY_RECORD" -> TeleautoPhysicalKey.RECORD
        "ACTION_KEY_SHUTTER" -> TeleautoPhysicalKey.SHUTTER
        else -> mapProfileAction(name)
    }

    private fun emit(key: TeleautoPhysicalKey, action: TeleautoKeyAction) {
        // JSON token 表可覆盖整个 DJI 系列；Profile 能力表才是当前机型唯一真相。
        if (key !in supportedInputs) return
        callback?.onKeyEvent(TeleautoRcKeyEvent(key, action))
    }

    private fun updateHat(code: Int, normalized: Float) {
        val value = when {
            normalized < -0.5f -> -1
            normalized > 0.5f -> 1
            else -> 0
        }
        if (code == 16) hatX = value else hatY = value
        val next = buildSet {
            if (hatX < 0) add(TeleautoPhysicalKey.D_LEFT)
            if (hatX > 0) add(TeleautoPhysicalKey.D_RIGHT)
            if (hatY < 0) add(TeleautoPhysicalKey.D_UP)
            if (hatY > 0) add(TeleautoPhysicalKey.D_DOWN)
            if (TeleautoPhysicalKey.D_MIDDLE in evdevHeld) add(TeleautoPhysicalKey.D_MIDDLE)
        }
        replaceEvdevHeld(next)
    }

    private fun updateEvdevHeld(key: TeleautoPhysicalKey, down: Boolean) {
        val next = evdevHeld.toMutableSet().apply {
            if (down) add(key) else remove(key)
        }
        replaceEvdevHeld(next)
    }

    /** Hat snapshots also produce semantic events so 5D can drive Android focus. */
    private fun replaceEvdevHeld(next: Set<TeleautoPhysicalKey>) {
        if (next == evdevHeld) return
        val previous = evdevHeld
        // Releasing first prevents a left->right transition from briefly
        // presenting two opposing directions to the UI focus graph.
        fiveWayTransitions(previous, next).forEach { emit(it.key, it.action) }
        evdevHeld = next
        publishHeldKeys()
    }

    private fun publishHeldKeys() {
        callback?.onHeldKeysChanged(evdevHeld + dumlHeld)
    }

    /**
     * 40007 的 0xAE 是 MCU 的实时电平，早于 KeyCodeListener 的去抖动作日志。
     * RTH/C1/C2 从这里发布真正的 DOWN/UP，避免 80–220ms 的厂商日志延迟。
     */
    private fun handleDumlRcState(buttonBits: Int, modeBits: Int) {
        val next = buildSet {
            if ((buttonBits and (1 shl 5)) != 0) add(TeleautoPhysicalKey.GOHOME)
            if ((modeBits and (1 shl 2)) != 0) add(TeleautoPhysicalKey.C1)
            if ((modeBits and (1 shl 3)) != 0) add(TeleautoPhysicalKey.C2)
        }
        updateDumlHeld(next)
    }

    private fun updateDumlHeld(next: Set<TeleautoPhysicalKey>) {
        if (next == dumlHeld) return
        val previous = dumlHeld
        dumlHeld = next
        (next - previous).forEach { emit(it, TeleautoKeyAction.DOWN) }
        (previous - next).forEach { emit(it, TeleautoKeyAction.UP) }
        publishHeldKeys()
    }

    private companion object {
        const val TAG = "TeleautoSdk-Rm700"
        val FIVE_WAY_KEYS = setOf(
            TeleautoPhysicalKey.D_UP,
            TeleautoPhysicalKey.D_DOWN,
            TeleautoPhysicalKey.D_LEFT,
            TeleautoPhysicalKey.D_RIGHT,
            TeleautoPhysicalKey.D_MIDDLE,
        )
        val DUML_LEVEL_KEYS = setOf(TeleautoPhysicalKey.GOHOME, TeleautoPhysicalKey.C1, TeleautoPhysicalKey.C2)
    }
}
