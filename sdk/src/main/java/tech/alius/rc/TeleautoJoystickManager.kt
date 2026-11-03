package tech.alius.rc

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import tech.alius.rc.input.TeleautoExternalJoystickConfig
import tech.alius.rc.input.TeleautoAvailableJoystickInputSource
import tech.alius.rc.input.TeleautoJoystickBackend
import tech.alius.rc.input.TeleautoJoystickDeviceInfo
import tech.alius.rc.input.TeleautoJoystickInputSource
import tech.alius.rc.input.TeleautoStandardJoystickBackend
import tech.alius.rc.internal.TeleautoLatestValueDispatcher
import tech.alius.rc.internal.TeleautoDebugLatencyReporter
import tech.alius.rc.model.TeleautoConnectionState
import tech.alius.rc.model.TeleautoRemoteControllerInfo
import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoRcKeyEvent
import tech.alius.rc.model.TeleautoKeyAction
import tech.alius.rc.model.TeleautoStickSnapshot
import tech.alius.rc.model.TeleautoThreePositionSwitchState
import tech.alius.rc.device.TeleautoVirtualHidKind
import tech.alius.rc.model.TeleautoInputConnectionIssue
import tech.alius.rc.profile.TeleautoRemoteControllerProfile
import tech.alius.rc.profile.TeleautoProfileRegistry

/**
 * 摇杆输入组件:输入订阅、摇杆快照、按键状态、工作模式与蓝牙手柄。
 * 生命周期由总门面 [TeleautoSdk] 驱动(初始化/释放/设备信息走 TeleautoSdk)。
 */
object TeleautoJoystickManager {

    // ── 监听器接口 ────────────────────────────────────
    interface InputListener {
        fun onScreenLockStateChanged(locked: Boolean, unlockProgress: Float) {}
        fun onKeyEvent(event: TeleautoRcKeyEvent) {}
        fun onStickChanged(snapshot: TeleautoStickSnapshot) {}
        fun onHeldKeysChanged(held: Set<TeleautoPhysicalKey>) {}
        fun onThreePositionSwitchChanged(state: TeleautoThreePositionSwitchState) {
            onFlightModeSwitchChanged(state)
        }
        @Deprecated("Use onThreePositionSwitchChanged")
        fun onFlightModeSwitchChanged(state: TeleautoThreePositionSwitchState) {}
    }
    interface ConnectionListener {
        fun onStateChanged(state: TeleautoConnectionState) {}
        fun onIssueChanged(issue: TeleautoInputConnectionIssue?) {}
    }
    /** 外置 Joystick 热插拔通知。列表只包含当前仍在线的设备。 */
    fun interface JoystickDeviceListener {
        fun onDevicesChanged(devices: List<TeleautoJoystickDeviceInfo>)
    }

    /**
     * App-facing aggregate observer. Hardware/profile differences stay behind the SDK boundary;
     * one observer receives the complete joystick state and event surface.
     */
    interface Observer : InputListener, ConnectionListener, HidStateListener, JoystickDeviceListener {
        override fun onHidStateChanged(mode: HidMode, detail: String?) {}
        override fun onDevicesChanged(devices: List<TeleautoJoystickDeviceInfo>) {}
    }

    /** Idempotent binding returned by [observe]. */
    fun interface Subscription : AutoCloseable {
        override fun close()
    }

    private var appContext: Context? = null
    private var config: TeleautoConfig = TeleautoConfig()
    private var profile: TeleautoRemoteControllerProfile? = null
    @Volatile private var snapshot: TeleautoStickSnapshot = TeleautoStickSnapshot()
    @Volatile private var threePositionSwitchState = TeleautoThreePositionSwitchState()
    @Volatile private var connectionState = TeleautoConnectionState.DISCONNECTED
    @Volatile private var connectionIssue: TeleautoInputConnectionIssue? = null
    @Volatile private var deviceInfo: TeleautoRemoteControllerInfo? = null
    @Volatile private var sdlInput: TeleautoStandardJoystickBackend? = null
    @Volatile private var selectedJoystickId: String? = null
    private var externalConfig = TeleautoExternalJoystickConfig()
    private var sdlForeground = false
    private var activeInputSource: TeleautoJoystickInputSource? = null

    private val inputListeners = mutableListOf<InputListener>()
    private val connectionListeners = mutableListOf<ConnectionListener>()
    private val joystickDeviceListeners = mutableListOf<JoystickDeviceListener>()
    private val mergedInput = tech.alius.rc.model.TeleautoControlInputState()
    private val touchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val virtualPulses = mutableMapOf<TeleautoPhysicalKey, Runnable>()
    @Volatile private var virtualFreeDial: Float? = null
    private val keyStates = HashMap<TeleautoPhysicalKey, Boolean>()
    private val sdkLatency = TeleautoDebugLatencyReporter("TeleautoJoystick-Latency", "sdk_dispatch")
    @Volatile private var lastStickTraceNs = 0L
    private val stickDispatcher = TeleautoLatestValueDispatcher(
        executor = { config.callbackExecutor },
        deliver = { delivery: Pair<Long, TeleautoStickSnapshot> ->
            screenInputGate.runIfOpen(delivery.first) {
                val value = delivery.second
                sdkLatency.onDelivery(System.nanoTime() - value.timestampNs)
                val listeners = synchronized(inputListeners) { inputListeners.toList() }
                listeners.forEach { it.onStickChanged(value) }
                if (BuildConfig.DEBUG && value.timestampNs - lastStickTraceNs >= 250_000_000L) {
                    lastStickTraceNs = value.timestampNs
                    Log.d(TAG, "stick_delivered locked=${screenInputGate.locked} source=$inputSource " +
                        "left=${value.leftX},${value.leftY} right=${value.rightX},${value.rightY} listeners=${listeners.size}")
                }
            }
        },
    )

    /** SDK 工作模式(可在运行时切换) */
    enum class Mode {
        /** 遥控器模式(默认):输入数据经 InputListener 回调分发给宿主 */
        CONTROLLER,
        /** BLE HID 模式:输入数据翻译为标准 HOGP joystick 报文供 PC/Mac 使用。 */
        BLE_HID,
        /** @deprecated 使用 [BLE_HID]。保留以兼容现有 SDK 调用方。 */
        @Deprecated("Use BLE_HID", ReplaceWith("BLE_HID"))
        BLUETOOTH_JOYSTICK,
    }

    /** SDK 识别并提供给 UI 展示的可用输出模式。 */
    data class AvailableOutputMode(
        val id: String,
        val mode: Mode,
        val displayName: String,
        val description: String,
        val selected: Boolean,
    )

    @Volatile private var hidPipeline: tech.alius.rc.hid.TeleautoHidJoystickPipeline? = null
    private val hidStateListeners = mutableListOf<HidStateListener>()

    /** BLE HID 状态(未开启时恒为 OFF) */
    enum class HidMode { OFF, ADVERTISING, PAIRING, CONNECTED, ERROR }

    interface HidStateListener {
        fun onHidStateChanged(mode: HidMode, detail: String? = null)
    }

    internal val isInitialized: Boolean get() = appContext != null

    /** 当前工作模式;切换即时生效(蓝牙手柄模式要求 API 29+ 且已初始化) */
    var mode: Mode = Mode.CONTROLLER
        set(value) {
            if (field == value) return
            field = value
            applyMode(value)
        }

    /** BLE HID 对主机暴露的标准设备类型。切换类型会重建报告描述符。 */
    var virtualHidKind: TeleautoVirtualHidKind = TeleautoVirtualHidKind.GAMEPAD
        set(value) {
            if (field == value) return
            field = value
            if (mode == Mode.BLE_HID || mode == Mode.BLUETOOTH_JOYSTICK) {
                hidPipeline?.stop()
                hidPipeline = null
                applyMode(Mode.BLE_HID)
            }
        }

    /**
     * 当前摇杆输入来源。与 [mode] 正交：例如 EXTERNAL_JOYSTICK + BLE_HID 可实现
     * Android USB/Bluetooth HID 输入再转发为 BLE HID 输出。
     */
    var inputSource: TeleautoJoystickInputSource = TeleautoJoystickInputSource.EMBEDDED_JOYSTICK
        set(value) {
            if (field == value && activeInputSource == value) return
            field = value
            applyInputSource(value)
        }

    val hidHostName: String? get() = hidPipeline?.transport?.hostName
    val hidDeviceName: String? get() = hidPipeline?.transport?.deviceName

    val hidMode: HidMode
        get() {
            val p = hidPipeline ?: return HidMode.OFF
            return when (p.transport.state) {
                tech.alius.rc.hid.TeleautoBleHidJoystick.State.CONNECTED -> HidMode.CONNECTED
                tech.alius.rc.hid.TeleautoBleHidJoystick.State.PAIRING -> HidMode.PAIRING
                tech.alius.rc.hid.TeleautoBleHidJoystick.State.ADVERTISING -> HidMode.ADVERTISING
                tech.alius.rc.hid.TeleautoBleHidJoystick.State.IDLE -> HidMode.ERROR
            }
        }

    // ── 生命周期 ──────────────────────────────────────
    @Synchronized
    internal fun initialize(context: Context, cfg: TeleautoConfig = TeleautoConfig()): Boolean {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return false
        if (appContext != null) return true
        appContext = context.applicationContext
        config = cfg
        TeleautoProfileRegistry.register(tech.alius.rc.profile.rm700.TeleautoRm700Profile())
        TeleautoProfileRegistry.register(tech.alius.rc.profile.rm500.TeleautoRm500Profile())
        TeleautoProfileRegistry.register(tech.alius.rc.profile.rcpro.TeleautoRm510Profile())
        TeleautoProfileRegistry.register(tech.alius.rc.profile.rcpro.TeleautoRc520Profile())

        val rawModel = prop("ro.product.model") ?: Build.MODEL
        val product = prop("ro.product.name") ?: Build.PRODUCT
        // 优先 ro.product.name 精确匹配,未命中再回退 ro.product.model(部分机型两属性不一致)
        val detected = TeleautoProfileRegistry.detect(product) ?: TeleautoProfileRegistry.detect(rawModel)
        if (detected == null) {
            Log.w(TAG, "unsupported device: model=$rawModel product=$product")
        }
        profile = detected
        val matchedCode = if (product.lowercase() in detected?.matchModels.orEmpty()) product else rawModel
        deviceInfo = TeleautoRemoteControllerInfo(
            displayName = detected?.displayName(matchedCode) ?: rawModel,
            modelCode = matchedCode.lowercase(),
            productCode = product,
            deviceCode = prop("ro.product.device") ?: Build.DEVICE,
            manufacturer = prop("ro.product.manufacturer") ?: Build.MANUFACTURER,
            firmwareVersion = prop("dji.prop.external_version") ?: "unknown",
            boardPlatform = prop("ro.board.platform")?.takeIf { it.isNotBlank() }
                ?: android.os.Build.BOARD.orEmpty(),
            androidVersion = Build.VERSION.RELEASE ?: "?",
            hardwareProfile = detected?.hardwareProfile,
            supportedInputs = detected?.supportedInputs ?: emptySet(),
            threePositionSwitch = detected?.threePositionSwitch,
            primaryControlOrder = detected?.primaryControlOrder.orEmpty(),
            leftControlOrder = detected?.leftControlOrder.orEmpty(),
            rightControlOrder = detected?.rightControlOrder.orEmpty(),
            hardwareControls = detected?.hardwareControls.orEmpty(),
        )
        // 先发布初态，再启动输入源，避免异步 CONNECTED 被 CONNECTING 覆盖。
        setState(TeleautoConnectionState.DISCONNECTED)
        // 外置输入适配层惰性启动；嵌入式输入不创建其监听器。
        if (cfg.inputSource == TeleautoJoystickInputSource.EXTERNAL_JOYSTICK) {
        sdlInput = TeleautoStandardJoystickBackend(context.applicationContext, sdlCallback) { device ->
            detected?.isEmbeddedJoystick(device.name, device.vendorId, device.productId) == true
        }.also {
            it.configure(externalConfig)
            it.start()
            it.setForeground(sdlForeground && cfg.inputSource == TeleautoJoystickInputSource.EXTERNAL_JOYSTICK)
        }
        }
        inputSource = cfg.inputSource
        mode = cfg.mode // 经属性 setter 生效(启动 HID 管线)
        Log.i(TAG, "initialized: ${deviceInfo!!.displayName} modelCode=${deviceInfo!!.modelCode} hardware=${detected?.hardwareProfile} backend=${detected?.id} input=$inputSource mode=$mode sdk=${TeleautoSdk.VERSION}")
        return inputSource == TeleautoJoystickInputSource.EXTERNAL_JOYSTICK || detected != null
    }

    @Synchronized
    internal fun release() {
        mode = Mode.CONTROLLER // 经属性 setter 停止 HID 管线
        sdlInput?.stop(); sdlInput = null
        activeInputSource = null
        appContext = null
        synchronized(hidStateListeners) { hidStateListeners.clear() }
        profile?.stop(); profile = null
        synchronized(inputListeners) { inputListeners.clear() }
        synchronized(connectionListeners) { connectionListeners.clear() }
        synchronized(joystickDeviceListeners) { joystickDeviceListeners.clear() }
        releaseVirtualKeys()
        mergedInput.clear()
        virtualFreeDial = null
        synchronized(keyStates) { keyStates.clear() }
        stickDispatcher.clear()
        snapshot = TeleautoStickSnapshot()
        setState(TeleautoConnectionState.DISCONNECTED)
    }

    // ── 设备信息 ──────────────────────────────────────
    internal fun getDeviceInfo(): TeleautoRemoteControllerInfo? = deviceInfo
    val connection: TeleautoConnectionState get() = connectionState
    internal val inputConnectionIssue: TeleautoInputConnectionIssue? get() = connectionIssue

    // ── 订阅 ─────────────────────────────────────────
    /**
     * Atomically binds all joystick callbacks and immediately delivers the latest state snapshot.
     * Call [Subscription.close] when the UI owner is destroyed.
     */
    @Suppress("DEPRECATION")
    fun observe(observer: Observer): Subscription {
        val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        addInputListener(observer)
        addConnectionListener(observer)
        addHidStateListener(observer)
        addJoystickDeviceListener(observer)

        post {
            if (closed.get()) return@post
            observer.onStateChanged(connectionState)
            observer.onIssueChanged(connectionIssue)
            observer.onScreenLockStateChanged(screenInputGate.locked, 0f)
            screenInputGate.runIfOpen {
                observer.onStickChanged(snapshot)
                observer.onHeldKeysChanged(getKeyStates().filterValues { it }.keys)
                observer.onThreePositionSwitchChanged(threePositionSwitchState)
            }
            observer.onHidStateChanged(hidMode, null)
            observer.onDevicesChanged(sdlInput?.devices().orEmpty())
        }

        return Subscription {
            if (closed.compareAndSet(false, true)) {
                removeJoystickDeviceListener(observer)
                removeHidStateListener(observer)
                removeConnectionListener(observer)
                removeInputListener(observer)
            }
        }
    }

    @Deprecated("Use observe(observer) and close the returned Subscription")
    fun addInputListener(l: InputListener): Boolean = synchronized(inputListeners) {
        if (inputListeners.contains(l)) false else inputListeners.add(l)
    }
    @Deprecated("Use observe(observer) and close the returned Subscription")
    fun removeInputListener(l: InputListener) = synchronized(inputListeners) { inputListeners.remove(l) }
    @Deprecated("Use observe(observer) and close the returned Subscription")
    fun addConnectionListener(l: ConnectionListener): Boolean = synchronized(connectionListeners) {
        if (connectionListeners.contains(l)) false else connectionListeners.add(l)
    }
    @Deprecated("Use observe(observer) and close the returned Subscription")
    fun removeConnectionListener(l: ConnectionListener) = synchronized(connectionListeners) { connectionListeners.remove(l) }
    @Deprecated("Use observe(observer) and close the returned Subscription")
    fun addJoystickDeviceListener(l: JoystickDeviceListener): Boolean = synchronized(joystickDeviceListeners) {
        if (joystickDeviceListeners.contains(l)) false else joystickDeviceListeners.add(l)
    }
    @Deprecated("Use observe(observer) and close the returned Subscription")
    fun removeJoystickDeviceListener(l: JoystickDeviceListener) =
        synchronized(joystickDeviceListeners) { joystickDeviceListeners.remove(l) }

    // ── 状态快照 ──────────────────────────────────────
    fun getStickSnapshot(): TeleautoStickSnapshot = if (tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) snapshot else TeleautoStickSnapshot()

    /** UI 渲染用:始终返回全量按键集(不按机型硬件裁剪),RM500/RM700 统一布局。 */
    val displayInputs: Set<TeleautoPhysicalKey>
        get() = setOf(
            TeleautoPhysicalKey.LEFT_STICK_X, TeleautoPhysicalKey.LEFT_STICK_Y,
            TeleautoPhysicalKey.RIGHT_STICK_X, TeleautoPhysicalKey.RIGHT_STICK_Y,
            TeleautoPhysicalKey.LEFT_DIAL, TeleautoPhysicalKey.RIGHT_DIAL, TeleautoPhysicalKey.FREE_DIAL,
            TeleautoPhysicalKey.MODE_1, TeleautoPhysicalKey.MODE_2, TeleautoPhysicalKey.MODE_3,
            TeleautoPhysicalKey.SHUTTER, TeleautoPhysicalKey.RECORD, TeleautoPhysicalKey.FOCUS,
            TeleautoPhysicalKey.C1, TeleautoPhysicalKey.C2, TeleautoPhysicalKey.C3, TeleautoPhysicalKey.GOHOME, TeleautoPhysicalKey.PAUSE,
            TeleautoPhysicalKey.AUTH_LED, TeleautoPhysicalKey.POWER,
            TeleautoPhysicalKey.D_UP, TeleautoPhysicalKey.D_DOWN, TeleautoPhysicalKey.D_LEFT, TeleautoPhysicalKey.D_RIGHT, TeleautoPhysicalKey.D_MIDDLE,
            TeleautoPhysicalKey.L1, TeleautoPhysicalKey.L2, TeleautoPhysicalKey.L3,
            TeleautoPhysicalKey.R1, TeleautoPhysicalKey.R2, TeleautoPhysicalKey.R3,
        )

    private val screenInputGate = tech.alius.rc.model.TeleautoControlOutputGate.gate
    fun checkScreenInput(): tech.alius.rc.model.TeleautoScreenInputResult = screenInputGate.check()
    @Volatile private var latestPhysicalSnapshot = TeleautoStickSnapshot()
    private val unlockPhysicalKeys = mutableSetOf<TeleautoPhysicalKey>()
    private val suppressedUnlockKeys = mutableSetOf<TeleautoPhysicalKey>()
    private val physicalUnlockHold = tech.alius.rc.model.TeleautoPhysicalUnlockHold()
    private val unlockTick = object : Runnable {
        override fun run() {
            if (!screenInputGate.locked || !physicalUnlockHold.active) return
            val progress = physicalUnlockHold.progress(android.os.SystemClock.uptimeMillis())
            if (progress >= 1f) setScreenLocked(false)
            else { notifyScreenLock(progress); touchHandler.postDelayed(this, 16L) }
        }
    }
    private fun notifyScreenLock(progress: Float) {
        val locked = screenInputGate.locked
        val generation = screenInputGate.generation
        post {
            if (generation != screenInputGate.generation) return@post
            synchronized(inputListeners) { inputListeners.toList() }.forEach {
            it.onScreenLockStateChanged(locked, progress)
        } }
    }
    private fun trackUnlockKey(event: TeleautoRcKeyEvent) {
        if (event.key != TeleautoPhysicalKey.C1 && event.key != TeleautoPhysicalKey.C2) return
        val generation = screenInputGate.generation
        touchHandler.post {
            if (!screenInputGate.locked || generation != screenInputGate.generation) return@post
            when (event.action) {
                TeleautoKeyAction.DOWN, TeleautoKeyAction.TOGGLED_ON -> unlockPhysicalKeys.add(event.key)
                TeleautoKeyAction.UP, TeleautoKeyAction.TOGGLED_OFF -> unlockPhysicalKeys.remove(event.key)
                else -> return@post
            }
            updateUnlockHold()
        }
    }
    private fun updateUnlockHold() {
        val wasActive = physicalUnlockHold.active
        physicalUnlockHold.update(TeleautoPhysicalKey.C1 in unlockPhysicalKeys,
            TeleautoPhysicalKey.C2 in unlockPhysicalKeys, android.os.SystemClock.uptimeMillis())
        if (physicalUnlockHold.active && !wasActive) unlockTick.run()
        else if (!physicalUnlockHold.active) {
            touchHandler.removeCallbacks(unlockTick)
            notifyScreenLock(0f)
        }
    }
    /** SDK owns output suppression; raw readers stay alive for the unlock chord. */
    fun setScreenLocked(locked: Boolean): Unit = synchronized(screenInputGate) {
        if (screenInputGate.locked == locked) return@synchronized
        screenInputGate.setLocked(locked)
        stickDispatcher.clear()
        touchHandler.removeCallbacks(unlockTick)
        physicalUnlockHold.reset()
        if (!locked) suppressedUnlockKeys.addAll(unlockPhysicalKeys)
        unlockPhysicalKeys.clear()
        releaseVirtualKeys()
        mergedInput.clear()
        synchronized(keyStates) { keyStates.clear() }
        hidPipeline?.onHeldKeysChanged(emptySet())
        if (BuildConfig.DEBUG) Log.d(TAG, "screen_lock=$locked control_output=${!locked}")
        notifyScreenLock(0f)
        if (!locked) bridge.onStickChanged(latestPhysicalSnapshot)
    }
    private fun postInput(action: () -> Unit) {
        val generation = screenInputGate.generation
        post { screenInputGate.runIfOpen(generation, action) }
    }

    /** Native key fallback; profiles and Android share the SDK output gate. */
    fun handleAndroidKeyEvent(event: android.view.KeyEvent): Boolean {
        val key = when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_F1 -> TeleautoPhysicalKey.L1
            android.view.KeyEvent.KEYCODE_F2 -> TeleautoPhysicalKey.L2
            android.view.KeyEvent.KEYCODE_F3 -> TeleautoPhysicalKey.L3
            android.view.KeyEvent.KEYCODE_F4 -> TeleautoPhysicalKey.R1
            android.view.KeyEvent.KEYCODE_F5 -> TeleautoPhysicalKey.R2
            android.view.KeyEvent.KEYCODE_F6 -> TeleautoPhysicalKey.R3
            android.view.KeyEvent.KEYCODE_DPAD_UP -> TeleautoPhysicalKey.D_UP
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> TeleautoPhysicalKey.D_DOWN
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> TeleautoPhysicalKey.D_LEFT
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> TeleautoPhysicalKey.D_RIGHT
            android.view.KeyEvent.KEYCODE_DPAD_CENTER -> TeleautoPhysicalKey.D_MIDDLE
            android.view.KeyEvent.KEYCODE_CAMERA -> TeleautoPhysicalKey.SHUTTER
            android.view.KeyEvent.KEYCODE_FOCUS -> TeleautoPhysicalKey.FOCUS
            else -> return screenInputGate.locked ||
                event.isFromSource(android.view.InputDevice.SOURCE_GAMEPAD) ||
                event.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK)
        }
        if (BuildConfig.DEBUG && screenInputGate.locked) Log.d(TAG, "native_key_suppressed key=$key action=${event.action}")
        if (event.action == android.view.KeyEvent.ACTION_UP || event.repeatCount == 0) {
            val down = event.action == android.view.KeyEvent.ACTION_DOWN
            if (event.action == android.view.KeyEvent.ACTION_DOWN || event.action == android.view.KeyEvent.ACTION_UP) {
                synchronized(screenInputGate) {
                    bridge.onKeyEvent(TeleautoRcKeyEvent(key, if (down) TeleautoKeyAction.DOWN else TeleautoKeyAction.UP))
                }
            }
        }
        return true
    }

    /** Touch keys use the same transport as hardware; sources retain independent ownership. */
    fun injectKeyEvent(key: TeleautoPhysicalKey, action: TeleautoKeyAction): tech.alius.rc.model.TeleautoScreenInputResult {
        val result = checkScreenInput()
        if (result != tech.alius.rc.model.TeleautoScreenInputResult.ACCEPTED) return result
        if (key !in tech.alius.rc.model.TeleautoRcTransmissionChannels.buttons) return tech.alius.rc.model.TeleautoScreenInputResult.INVALID_INPUT
        if (action == TeleautoKeyAction.TRIGGER) {
            virtualPulses.remove(key)?.let(touchHandler::removeCallbacks)
            injectKeyEvent(key, TeleautoKeyAction.DOWN)
            val task = Runnable { virtualPulses.remove(key); injectKeyEvent(key, TeleautoKeyAction.UP) }
            virtualPulses[key] = task
            touchHandler.postDelayed(task, 180L)
            return tech.alius.rc.model.TeleautoScreenInputResult.ACCEPTED
        }
        val down = mergedInput.touchEvent(key, action == TeleautoKeyAction.DOWN || action == TeleautoKeyAction.TOGGLED_ON)
        dispatchInputKey(TeleautoRcKeyEvent(key, if (down) TeleautoKeyAction.DOWN else TeleautoKeyAction.UP))
        return tech.alius.rc.model.TeleautoScreenInputResult.ACCEPTED
    }
    fun releaseVirtualKeys() {
        virtualPulses.values.forEach(touchHandler::removeCallbacks); virtualPulses.clear()
        mergedInput.touchKeys().forEach { key ->
            val down = mergedInput.touchEvent(key, false)
            dispatchInputKey(TeleautoRcKeyEvent(key, if (down) TeleautoKeyAction.DOWN else TeleautoKeyAction.UP))
        }
    }
    fun getKeyStates(): Map<TeleautoPhysicalKey, Boolean> = if (tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) synchronized(keyStates) { keyStates.toMap() } else emptyMap()

    /** Set the latched continuous input, including virtual dials on models without one. */
    fun setFreeDialValue(value: Float): Boolean = submitFreeDialValue(value) == tech.alius.rc.model.TeleautoScreenInputResult.ACCEPTED

    fun submitFreeDialValue(value: Float): tech.alius.rc.model.TeleautoScreenInputResult {
        val result = checkScreenInput()
        if (result != tech.alius.rc.model.TeleautoScreenInputResult.ACCEPTED) return result
        if (!value.isFinite() || value !in -1f..1f) return tech.alius.rc.model.TeleautoScreenInputResult.INVALID_INPUT
        val accepted = inputSource == TeleautoJoystickInputSource.EMBEDDED_JOYSTICK && profile?.setFreeDialValue(value) == true
        virtualFreeDial = if (accepted) null else value
        bridge.onStickChanged(snapshot.copy(freeDial = value))
        return tech.alius.rc.model.TeleautoScreenInputResult.ACCEPTED
    }

    private fun dispatchInputKey(event: TeleautoRcKeyEvent) {
        if (screenInputGate.locked) return
        hidPipeline?.onKeyEvent(event)
        when (event.action) {
            TeleautoKeyAction.DOWN -> synchronized(keyStates) { keyStates[event.key] = true }
            TeleautoKeyAction.UP -> synchronized(keyStates) { keyStates[event.key] = false }
            else -> {}
        }
        postInput {
            if (BuildConfig.DEBUG) Log.d(TAG, "key_delivered key=${event.key} action=${event.action}")
            synchronized(inputListeners) { inputListeners.toList() }.forEach { it.onKeyEvent(event) }
        }
    }

    /** 当前枚举到的 USB/Bluetooth Joystick 列表。 */
    fun getConnectedJoystickDevices(): List<TeleautoJoystickDeviceInfo> {
        ensureSdlBackend()
        return sdlInput?.devices().orEmpty()
    }

    /**
     * 返回 SDK 当前确认可用的输入源，供 App 直接渲染。
     * DJI 内置输入由 TeleautoRemoteControllerProfile 决定；每个外置设备对应一个可选来源。
     */
    fun getAvailableInputSources(): List<TeleautoAvailableJoystickInputSource> = buildList {
        ensureSdlBackend()
        if (profile != null) {
            val directPermissionRequired =
                connectionIssue == TeleautoInputConnectionIssue.DIRECT_INPUT_PERMISSION_REQUIRED
            add(
                TeleautoAvailableJoystickInputSource(
                    id = EMBEDDED_SOURCE_ID,
                    source = TeleautoJoystickInputSource.EMBEDDED_JOYSTICK,
                    displayName = "内置摇杆",
                    description = if (directPermissionRequired) {
                        "设备需要准备，请使用 Teleauto Assistant"
                    } else {
                        deviceInfo?.displayName ?: "遥控器内置输入"
                    },
                    selected = inputSource == TeleautoJoystickInputSource.EMBEDDED_JOYSTICK,
                    enabled = !directPermissionRequired,
                )
            )
        }
        sdlInput?.devices().orEmpty().forEach { device ->
            add(
                TeleautoAvailableJoystickInputSource(
                    id = EXTERNAL_SOURCE_PREFIX + device.id,
                    source = TeleautoJoystickInputSource.EXTERNAL_JOYSTICK,
                    displayName = "外接摇杆",
                    description = device.name,
                    device = device,
                    selected = inputSource == TeleautoJoystickInputSource.EXTERNAL_JOYSTICK &&
                        getSelectedJoystickDevice()?.id == device.id,
                )
            )
        }
    }

    /** Explicitly named counterpart retained as the canonical public API. */
    fun getAvailableJoystickInputSources(): List<TeleautoAvailableJoystickInputSource> = getAvailableInputSources()

    /** 按 [getAvailableInputSources] 返回的 ID 选择唯一输入源。 */
    fun selectInputSource(sourceId: String): Boolean {
        if (sourceId == EMBEDDED_SOURCE_ID) {
            if (profile == null ||
                connectionIssue == TeleautoInputConnectionIssue.DIRECT_INPUT_PERMISSION_REQUIRED
            ) return false
            inputSource = TeleautoJoystickInputSource.EMBEDDED_JOYSTICK
            return true
        }
        if (!sourceId.startsWith(EXTERNAL_SOURCE_PREFIX)) return false
        val deviceId = sourceId.removePrefix(EXTERNAL_SOURCE_PREFIX)
        ensureSdlBackend()
        if (!selectJoystickDevice(deviceId)) return false
        inputSource = TeleautoJoystickInputSource.EXTERNAL_JOYSTICK
        return true
    }

    /** 返回当前设备真正支持的输出模式，App 只负责渲染该列表。 */
    fun getAvailableOutputModes(): List<AvailableOutputMode> = buildList {
        add(
            AvailableOutputMode(
                id = CONTROLLER_OUTPUT_ID,
                mode = Mode.CONTROLLER,
                displayName = "控制器模式",
                description = "数据经 SDK 回调输出",
                selected = mode == Mode.CONTROLLER,
            )
        )
        val context = appContext
        val bleHidAvailable = Build.VERSION.SDK_INT >= 29 && context != null &&
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE)
        if (bleHidAvailable) {
            val state = when (hidMode) {
                HidMode.OFF -> if (mode == Mode.BLE_HID) "启动中" else "BLE Gamepad HID"
                HidMode.ADVERTISING -> "广播中"
                HidMode.PAIRING -> "配对中"
                HidMode.CONNECTED -> "已连接"
                HidMode.ERROR -> "启动失败"
            }
            add(
                AvailableOutputMode(
                    id = BLE_HID_OUTPUT_ID,
                    mode = Mode.BLE_HID,
                    displayName = "BLE Gamepad HID",
                    description = state,
                    selected = mode == Mode.BLE_HID,
                )
            )
        }
    }

    /** 按 [getAvailableOutputModes] 返回的 ID 选择唯一输出模式。 */
    fun selectOutputMode(outputId: String): Boolean {
        val available = getAvailableOutputModes().firstOrNull { it.id == outputId } ?: return false
        mode = available.mode
        return true
    }

    /** 当前用户指定的外置 Joystick；null 表示尚未选择或设备已断开。 */
    fun getSelectedJoystickDevice(): TeleautoJoystickDeviceInfo? = sdlInput?.selected()

    /**
     * 指定 EXTERNAL_JOYSTICK 唯一输入设备。传 null 清除选择；无效 deviceId 返回 false。
     * SDK 不会自动回退到其他已连接手柄。
     */
    fun selectJoystickDevice(deviceId: String?): Boolean {
        if (deviceId == null) {
            selectedJoystickId = null
            sdlInput?.select(null)
            return true
        }
        val backend = sdlInput ?: return false
        if (!backend.select(deviceId)) return false
        selectedJoystickId = deviceId
        return true
    }

    fun configureExternalJoystick(config: TeleautoExternalJoystickConfig) {
        externalConfig = config
        sdlInput?.configure(config)
    }

    fun getExternalJoystickConfig(): TeleautoExternalJoystickConfig = externalConfig

    internal fun onExternalForegroundChanged(foreground: Boolean) {
        sdlForeground = foreground
        sdlInput?.setForeground(foreground && inputSource == TeleautoJoystickInputSource.EXTERNAL_JOYSTICK)
    }

    internal fun onEmbeddedMotionEvent(event: MotionEvent): Boolean {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable() ||
            inputSource != TeleautoJoystickInputSource.EMBEDDED_JOYSTICK) return false
        return profile?.handleMotionEvent(event) == true
    }

    internal fun onExternalMotionEvent(event: MotionEvent): Boolean =
        sdlInput?.handleMotionEvent(event) == true

    internal fun onExternalKeyEvent(event: KeyEvent): Boolean =
        sdlInput?.handleKeyEvent(event) == true

    // ── profile 上行桥 ───────────────────────────────
    // HID 模式下输入同时送往 HID 管线与本地监听器。监听器用于设备端实时监视 UI，
    // 不参与 HID 报告生成，不能因为开启监视页面而改变主机收到的数据。
    private val bridge = object : TeleautoRemoteControllerProfile.Callback {
        override fun onKeyEvent(event: TeleautoRcKeyEvent) = synchronized(screenInputGate) {
            if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return@synchronized
            if (screenInputGate.locked) { trackUnlockKey(event); return@synchronized }
            if (event.key in suppressedUnlockKeys) {
                if (event.action == TeleautoKeyAction.UP || event.action == TeleautoKeyAction.TOGGLED_OFF) suppressedUnlockKeys.remove(event.key)
                return@synchronized
            }
            if ((event.action == TeleautoKeyAction.DOWN || event.action == TeleautoKeyAction.UP) &&
                mergedInput.physicalIsDown(event.key) == (event.action == TeleautoKeyAction.DOWN)) return@synchronized
            val merged = when (event.action) {
                TeleautoKeyAction.DOWN, TeleautoKeyAction.UP -> {
                    val down = mergedInput.physicalEvent(event.key, event.action == TeleautoKeyAction.DOWN)
                    event.copy(action = if (down) TeleautoKeyAction.DOWN else TeleautoKeyAction.UP)
                }
                else -> event
            }
            dispatchInputKey(merged)
        }
        override fun onStickChanged(snapshot: TeleautoStickSnapshot) = synchronized(screenInputGate) {
            if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return@synchronized
            latestPhysicalSnapshot = snapshot
            if (screenInputGate.locked) return@synchronized
            val effective = virtualFreeDial?.let { snapshot.copy(freeDial = it) } ?: snapshot
            hidPipeline?.onStickChanged(effective)
            this@TeleautoJoystickManager.snapshot = effective
            sdkLatency.onOffer()
            stickDispatcher.offer(screenInputGate.generation to effective)
        }
        override fun onHeldKeysChanged(held: Set<TeleautoPhysicalKey>) = synchronized(screenInputGate) {
            if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return@synchronized
            if (screenInputGate.locked) {
                for (key in listOf(TeleautoPhysicalKey.C1, TeleautoPhysicalKey.C2)) {
                    trackUnlockKey(TeleautoRcKeyEvent(key, if (key in held) TeleautoKeyAction.DOWN else TeleautoKeyAction.UP))
                }
                return@synchronized
            }
            suppressedUnlockKeys.retainAll(held)
            val effective = mergedInput.physicalSnapshot(held - suppressedUnlockKeys)
            hidPipeline?.onHeldKeysChanged(effective)
            synchronized(keyStates) {
                (supportedHeldKeys() + tech.alius.rc.model.TeleautoRcTransmissionChannels.buttons).forEach { keyStates[it] = it in effective }
            }
            postInput { synchronized(inputListeners) { inputListeners.toList() }.forEach { it.onHeldKeysChanged(effective) } }
        }
        override fun onConnectionChanged(connected: Boolean) {
            if (!connected) touchHandler.post {
                unlockPhysicalKeys.clear()
                physicalUnlockHold.reset()
                touchHandler.removeCallbacks(unlockTick)
                if (screenInputGate.locked) notifyScreenLock(0f)
            }
            if (!connected) onThreePositionSwitchChanged(TeleautoThreePositionSwitchState())
            if (connected) onConnectionIssueChanged(null)
            setState(if (connected) TeleautoConnectionState.CONNECTED else TeleautoConnectionState.DISCONNECTED)
            TeleautoRuntimeEnvironmentManager.onInputStateChanged(
                deviceInfo?.hardwareProfile,
                connected,
                connectionIssue,
            )
        }
        override fun onConnectionIssueChanged(issue: TeleautoInputConnectionIssue?) {
            if (connectionIssue == issue) return
            connectionIssue = issue
            TeleautoRuntimeEnvironmentManager.onInputStateChanged(
                deviceInfo?.hardwareProfile,
                connectionState == TeleautoConnectionState.CONNECTED,
                issue,
            )
            val listeners = synchronized(connectionListeners) { connectionListeners.toList() }
            post { listeners.forEach { it.onIssueChanged(issue) } }
        }
        override fun onThreePositionSwitchChanged(state: TeleautoThreePositionSwitchState): Unit = synchronized(screenInputGate) {
            if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return@synchronized
            if (screenInputGate.locked) return@synchronized
            threePositionSwitchState = state
            hidPipeline?.onFlightModeSwitchChanged(state)
            postInput { synchronized(inputListeners) { inputListeners.toList() }.forEach { it.onThreePositionSwitchChanged(state) } }
        }
    }

    private val sdlCallback = object : TeleautoJoystickBackend.Callback {
        override fun onDevicesChanged(devices: List<TeleautoJoystickDeviceInfo>) {
            if (selectedJoystickId != null && devices.none { it.id == selectedJoystickId }) {
                // 保留用户选择标识但不自动迁移到其他设备；当前连接状态由 backend 回报。
            }
            val snapshot = devices.toList()
            val listeners = synchronized(joystickDeviceListeners) { joystickDeviceListeners.toList() }
            post { listeners.forEach { it.onDevicesChanged(snapshot) } }
        }
        override fun onKey(event: TeleautoRcKeyEvent) {
            if (inputSource == TeleautoJoystickInputSource.EXTERNAL_JOYSTICK) bridge.onKeyEvent(event)
        }
        override fun onStick(snapshot: TeleautoStickSnapshot) {
            if (inputSource == TeleautoJoystickInputSource.EXTERNAL_JOYSTICK) bridge.onStickChanged(snapshot)
        }
        override fun onHeldKeys(held: Set<TeleautoPhysicalKey>) {
            if (inputSource == TeleautoJoystickInputSource.EXTERNAL_JOYSTICK) bridge.onHeldKeysChanged(held)
        }
        override fun onConnection(connected: Boolean) {
            if (inputSource == TeleautoJoystickInputSource.EXTERNAL_JOYSTICK) {
                bridge.onConnectionChanged(connected)
            }
        }
    }

    // ── 蓝牙 HID 手柄模式(BLE GATT,主机侧发起连接)──
    /** 移除与当前主机的蓝牙配对(主机端缓存旧描述符时用于强制重新配对) */
    fun resetHidPairing(): Boolean = hidPipeline?.transport?.removeBond() ?: false

    @Deprecated("Use observe(observer) and close the returned Subscription")
    fun addHidStateListener(l: HidStateListener): Boolean = synchronized(hidStateListeners) {
        if (hidStateListeners.contains(l)) false else hidStateListeners.add(l)
    }

    @Deprecated("Use observe(observer) and close the returned Subscription")
    fun removeHidStateListener(l: HidStateListener) = synchronized(hidStateListeners) { hidStateListeners.remove(l) }

    private fun notifyHidState(detail: String? = null) {
        val mode = hidMode
        val ls = synchronized(hidStateListeners) { hidStateListeners.toList() }
        post { ls.forEach { it.onHidStateChanged(mode, detail) } }
    }

    /** 模式切换的落地:启停蓝牙 HID 管线(bridge 按 hidPipeline 是否存在分流) */
    private fun applyMode(m: Mode) {
        val ctx = appContext ?: return
        when (m) {
            Mode.CONTROLLER -> {
                hidPipeline?.stop()
                hidPipeline = null
                notifyHidState()
            }
            Mode.BLE_HID,
            Mode.BLUETOOTH_JOYSTICK -> {
                if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
                if (hidPipeline == null) {
                    clearPublicInputState()
                    // 蓝牙对外名称用机型名(DJI_RC_Plus → DJI RC Plus)
                    val btName = deviceInfo?.displayName ?: "DJI RC"
                    val descriptorKind = when (virtualHidKind) {
                        TeleautoVirtualHidKind.GAMEPAD -> tech.alius.rc.hid.TeleautoHidJoystickDescriptor.DeviceKind.GAMEPAD
                        TeleautoVirtualHidKind.JOYSTICK -> tech.alius.rc.hid.TeleautoHidJoystickDescriptor.DeviceKind.JOYSTICK
                    }
                    hidPipeline = tech.alius.rc.hid.TeleautoHidJoystickPipeline(ctx, btName, descriptorKind) { notifyHidState(it) }
                    hidPipeline?.start()
                }
            }
        }
    }

    private fun clearPublicInputState() {
        stickDispatcher.clear()
        snapshot = TeleautoStickSnapshot()
        releaseVirtualKeys()
        mergedInput.clear()
        virtualFreeDial = null
        synchronized(keyStates) { keyStates.clear() }
        bridge.onThreePositionSwitchChanged(TeleautoThreePositionSwitchState())
    }

    fun getThreePositionSwitchState(): TeleautoThreePositionSwitchState = threePositionSwitchState
    @Deprecated("Use getThreePositionSwitchState", ReplaceWith("getThreePositionSwitchState()"))
    fun getFlightModeSwitchState(): TeleautoThreePositionSwitchState = getThreePositionSwitchState()

    /** 输入源切换：停止旧源、清空状态，再启动新源。 */
    /** 标准外置 Joystick 后端按需创建。 */
    private fun ensureSdlBackend() {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
        if (sdlInput != null) return
        val context = appContext ?: return
        sdlInput = TeleautoStandardJoystickBackend(context, sdlCallback) { device ->
            profile?.isEmbeddedJoystick(device.name, device.vendorId, device.productId) == true
        }.also {
            it.configure(externalConfig)
            it.start()
            it.setForeground(sdlForeground && inputSource == TeleautoJoystickInputSource.EXTERNAL_JOYSTICK)
        }
    }

    private fun applyInputSource(source: TeleautoJoystickInputSource) {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
        val ctx = appContext ?: return
        profile?.stop()
        activeInputSource = source
        clearPublicInputState()
        bridge.onStickChanged(TeleautoStickSnapshot())
        bridge.onHeldKeysChanged(emptySet())

        when (source) {
            TeleautoJoystickInputSource.EMBEDDED_JOYSTICK -> {
                sdlInput?.setForeground(false)
                val p = profile
                if (p == null) {
                    setState(TeleautoConnectionState.DISCONNECTED)
                } else {
                    setState(TeleautoConnectionState.CONNECTING)
                    p.start(ctx, bridge)
                }
            }
            TeleautoJoystickInputSource.EXTERNAL_JOYSTICK -> {
                // 外置输入由标准适配层分发，不启动本地 ADB reader。
                setState(TeleautoConnectionState.CONNECTING)
                sdlInput?.configure(externalConfig)
                sdlInput?.setForeground(sdlForeground)
                selectedJoystickId?.let { sdlInput?.select(it) }
            }
        }
    }

    private fun post(r: Runnable) { try { config.callbackExecutor.execute(r) } catch (_: Exception) {} }

    private fun supportedHeldKeys(): Set<TeleautoPhysicalKey> =
        profile?.supportedInputs.orEmpty().filterTo(linkedSetOf()) { key ->
            key !in setOf(
                TeleautoPhysicalKey.LEFT_STICK_X, TeleautoPhysicalKey.LEFT_STICK_Y,
                TeleautoPhysicalKey.RIGHT_STICK_X, TeleautoPhysicalKey.RIGHT_STICK_Y,
                TeleautoPhysicalKey.LEFT_DIAL, TeleautoPhysicalKey.RIGHT_DIAL, TeleautoPhysicalKey.FREE_DIAL,
                TeleautoPhysicalKey.MODE_1, TeleautoPhysicalKey.MODE_2, TeleautoPhysicalKey.MODE_3,
            )
        }

    private fun setState(s: TeleautoConnectionState) {
        if (connectionState == s) return
        Log.i(TAG, "connection state $connectionState -> $s")
        connectionState = s
        val ls = synchronized(connectionListeners) { connectionListeners.toList() }
        post { ls.forEach { it.onStateChanged(s) } }
    }

    /** 反射读取系统属性(ro.* 等) */
    internal fun prop(name: String, def: String = ""): String? = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val m = clazz.getMethod("get", String::class.java, String::class.java)
        m.invoke(null, name, def) as String
    } catch (e: Exception) { null }

    private const val TAG = "TeleautoSdk"
    private const val EMBEDDED_SOURCE_ID = "embedded"
    private const val EXTERNAL_SOURCE_PREFIX = "external:"
    private const val CONTROLLER_OUTPUT_ID = "controller"
    private const val BLE_HID_OUTPUT_ID = "ble_hid"
}
