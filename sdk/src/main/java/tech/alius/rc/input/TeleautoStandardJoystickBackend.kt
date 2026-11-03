package tech.alius.rc.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import tech.alius.rc.model.TeleautoKeyAction
import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoRcKeyEvent
import tech.alius.rc.model.TeleautoStickSnapshot
import kotlin.math.abs

/**
 * Platform adapter for standard Android HID joysticks.
 *
 * It deliberately implements the same backend contract as embedded profiles, so the public SDK
 * model is portable and a native SDL adapter can be supplied later without changing callers.
 */
internal class TeleautoStandardJoystickBackend(
    context: Context,
    private val callback: TeleautoJoystickBackend.Callback,
    private val embeddedDevicePredicate: (TeleautoJoystickDeviceInfo) -> Boolean,
) : TeleautoJoystickBackend, InputManager.InputDeviceListener {
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var started = false
    private var foreground = false
    private var devices = emptyList<TeleautoJoystickDeviceInfo>()
    private var selectedId: String? = null
    private var config = TeleautoExternalJoystickConfig()
    private val axes = FloatArray(32)
    private val held = linkedSetOf<TeleautoPhysicalKey>()

    fun devices(): List<TeleautoJoystickDeviceInfo> = devices
    fun selected(): TeleautoJoystickDeviceInfo? = devices.firstOrNull { it.id == selectedId }

    fun select(id: String?): Boolean {
        if (id != null && devices.none { it.id == id }) return false
        reset()
        selectedId = id
        publishConnection()
        return true
    }

    fun configure(value: TeleautoExternalJoystickConfig) {
        config = value
        publishStick(System.nanoTime())
    }

    fun configuration(): TeleautoExternalJoystickConfig = config

    override fun start() {
        if (started) return
        started = true
        inputManager.registerInputDeviceListener(this, mainHandler)
        refreshDevices()
    }

    override fun stop() {
        if (!started) return
        inputManager.unregisterInputDeviceListener(this)
        started = false
        reset()
        callback.onConnection(false)
    }

    override fun setForeground(foreground: Boolean) {
        if (this.foreground == foreground) return
        this.foreground = foreground
        if (!foreground) reset()
        publishConnection()
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (!accept(event.deviceId) || event.action != MotionEvent.ACTION_MOVE) return false
        val ranges = joystickRanges(event.device)
        ranges.take(axes.size).forEachIndexed { index, range ->
            axes[index] = normalize(event.getAxisValue(range.axis), range)
        }
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        setKey(TeleautoPhysicalKey.D_LEFT, hatX < -0.5f, event.eventTime * 1_000_000L)
        setKey(TeleautoPhysicalKey.D_RIGHT, hatX > 0.5f, event.eventTime * 1_000_000L)
        setKey(TeleautoPhysicalKey.D_UP, hatY < -0.5f, event.eventTime * 1_000_000L)
        setKey(TeleautoPhysicalKey.D_DOWN, hatY > 0.5f, event.eventTime * 1_000_000L)
        publishStick(event.eventTime * 1_000_000L)
        return true
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!accept(event.deviceId)) return false
        val button = keyCodeToButton(event.keyCode) ?: return false
        val key = config.buttonMapping[button] ?: return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) setKey(key, true, event.eventTime * 1_000_000L)
            KeyEvent.ACTION_UP -> setKey(key, false, event.eventTime * 1_000_000L)
            else -> return false
        }
        return true
    }

    override fun onInputDeviceAdded(deviceId: Int) = refreshDevices()
    override fun onInputDeviceChanged(deviceId: Int) = refreshDevices()
    override fun onInputDeviceRemoved(deviceId: Int) {
        if (selected()?.sdlInstanceId == deviceId) reset()
        refreshDevices()
    }

    private fun refreshDevices() {
        devices = inputManager.inputDeviceIds.asIterable().mapNotNull { inputManager.getInputDevice(it) }
            .filter(::isJoystick)
            .map(::toDeviceInfo)
            .filterNot(embeddedDevicePredicate)
        callback.onDevicesChanged(devices)
        if (selectedId != null && selected() == null) reset()
        publishConnection()
    }

    private fun accept(deviceId: Int): Boolean =
        started && foreground && selected()?.sdlInstanceId == deviceId

    private fun publishConnection() = callback.onConnection(started && foreground && selected() != null)

    private fun publishStick(timestampNs: Long) {
        val mapping = config.axisMapping
        fun read(index: Int): Float {
            val value = axes.getOrElse(index) { 0f }.coerceIn(-1f, 1f)
            return if (abs(value) <= config.deadZone) 0f else value
        }
        callback.onStick(
            TeleautoStickSnapshot(
                leftX = read(mapping.leftX),
                leftY = read(mapping.leftY) * if (config.invertLeftY) -1f else 1f,
                rightX = read(mapping.rightX),
                rightY = read(mapping.rightY) * if (config.invertRightY) -1f else 1f,
                leftDial = read(mapping.leftAux),
                rightDial = read(mapping.rightAux),
                timestampNs = timestampNs,
            )
        )
    }

    private fun setKey(key: TeleautoPhysicalKey, down: Boolean, timestampNs: Long) {
        val changed = if (down) held.add(key) else held.remove(key)
        if (!changed) return
        callback.onKey(TeleautoRcKeyEvent(key, if (down) TeleautoKeyAction.DOWN else TeleautoKeyAction.UP, timestampNs))
        callback.onHeldKeys(held.toSet())
    }

    private fun reset() {
        val now = System.nanoTime()
        held.toList().forEach { callback.onKey(TeleautoRcKeyEvent(it, TeleautoKeyAction.UP, now)) }
        held.clear()
        axes.fill(0f)
        callback.onHeldKeys(emptySet())
        callback.onStick(TeleautoStickSnapshot(timestampNs = now))
    }

    private fun toDeviceInfo(device: InputDevice): TeleautoJoystickDeviceInfo {
        val ranges = joystickRanges(device)
        val descriptor = device.descriptor.orEmpty()
        return TeleautoJoystickDeviceInfo(
            id = "android:$descriptor:${device.vendorId}:${device.productId}",
            sdlInstanceId = device.id,
            name = device.name,
            guid = descriptor,
            vendorId = device.vendorId,
            productId = device.productId,
            axisCount = ranges.size,
            buttonCount = GAMEPAD_KEY_CODES.count { device.hasKeys(it).firstOrNull() == true },
            hatCount = if (ranges.any { it.axis == MotionEvent.AXIS_HAT_X }) 1 else 0,
        )
    }

    private fun joystickRanges(device: InputDevice): List<InputDevice.MotionRange> =
        device.motionRanges.filter { it.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK }
            .filterNot { it.axis == MotionEvent.AXIS_HAT_X || it.axis == MotionEvent.AXIS_HAT_Y }
            .sortedBy { range -> AXIS_ORDER.indexOf(range.axis).let { if (it < 0) Int.MAX_VALUE else it } }

    private fun normalize(value: Float, range: InputDevice.MotionRange): Float {
        val center = (range.max + range.min) * 0.5f
        val radius = (range.max - range.min) * 0.5f
        return if (radius <= 0f) 0f else ((value - center) / radius).coerceIn(-1f, 1f)
    }

    private fun isJoystick(device: InputDevice): Boolean =
        device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    private fun keyCodeToButton(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> 0
        KeyEvent.KEYCODE_BUTTON_B -> 1
        KeyEvent.KEYCODE_BUTTON_X -> 2
        KeyEvent.KEYCODE_BUTTON_Y -> 3
        KeyEvent.KEYCODE_BUTTON_L1 -> 4
        KeyEvent.KEYCODE_BUTTON_R1 -> 5
        KeyEvent.KEYCODE_BUTTON_SELECT -> 8
        KeyEvent.KEYCODE_BUTTON_START -> 9
        KeyEvent.KEYCODE_BUTTON_THUMBL -> 10
        KeyEvent.KEYCODE_BUTTON_THUMBR -> 11
        else -> null
    }

    private companion object {
        // Preserve SDL's logical X/Y/Rx/Ry + auxiliary-axis ordering for existing configs.
        val AXIS_ORDER = intArrayOf(
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_RX,
            MotionEvent.AXIS_RY,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_LTRIGGER,
            MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_BRAKE,
            MotionEvent.AXIS_GAS,
        )
        val GAMEPAD_KEY_CODES = intArrayOf(
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR,
        )
    }
}
