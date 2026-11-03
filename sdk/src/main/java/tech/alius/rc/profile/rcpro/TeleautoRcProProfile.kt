package tech.alius.rc.profile.rcpro

import android.content.Context
import tech.alius.rc.internal.TeleautoProtocolInput
import tech.alius.rc.internal.TeleautoProtocolInputReader
import tech.alius.rc.model.*
import tech.alius.rc.profile.TeleautoRemoteControllerProfile

/** Consumer RC protocol adapter. Firmware must permit the host to subscribe to 0x06/0xAE. */
abstract class TeleautoRcProProfile : TeleautoRemoteControllerProfile {
    override val matchModels: Set<String> get() = productNames.keys
    override val threePositionSwitch = TeleautoThreePositionSwitchDescriptor("C", "N", "S")
    protected open val hasC3 = false
    override val supportedInputs: Set<TeleautoPhysicalKey> get() = BUTTONS.values.toSet()
        .filterTo(linkedSetOf()) { hasC3 || it != TeleautoPhysicalKey.C3 } + setOf(
        TeleautoPhysicalKey.LEFT_STICK_X, TeleautoPhysicalKey.LEFT_STICK_Y,
        TeleautoPhysicalKey.RIGHT_STICK_X, TeleautoPhysicalKey.RIGHT_STICK_Y,
        TeleautoPhysicalKey.LEFT_DIAL, TeleautoPhysicalKey.RIGHT_DIAL,
        TeleautoPhysicalKey.MODE_1, TeleautoPhysicalKey.MODE_2, TeleautoPhysicalKey.MODE_3,
    )
    override val primaryControlOrder get() = listOf(TeleautoPhysicalKey.GOHOME, TeleautoPhysicalKey.PAUSE,
        TeleautoPhysicalKey.C1, TeleautoPhysicalKey.C2) + if (hasC3) listOf(TeleautoPhysicalKey.C3) else emptyList()
    override val leftControlOrder get() = primaryControlOrder
    override val rightControlOrder = listOf(TeleautoPhysicalKey.D_UP, TeleautoPhysicalKey.D_LEFT,
        TeleautoPhysicalKey.D_MIDDLE, TeleautoPhysicalKey.D_RIGHT, TeleautoPhysicalKey.D_DOWN)
    override val hardwareControls get() = listOf(
        TeleautoHardwareControlCatalog.LEFT_JOYSTICK_01, TeleautoHardwareControlCatalog.RIGHT_JOYSTICK_01,
        TeleautoHardwareControlCatalog.LEFT_CENTERING_DIAL_01, TeleautoHardwareControlCatalog.RIGHT_CENTERING_DIAL_01,
        TeleautoHardwareControlCatalog.RTH_BUTTON, TeleautoHardwareControlCatalog.PAUSE_BUTTON,
        TeleautoHardwareControlCatalog.CUSTOM_BUTTON_C1, TeleautoHardwareControlCatalog.CUSTOM_BUTTON_C2,
        TeleautoHardwareControlCatalog.RIGHT_FIVE_WAY_01, TeleautoHardwareControlCatalog.FOCUS_BUTTON,
        TeleautoHardwareControlCatalog.PHOTO_BUTTON, TeleautoHardwareControlCatalog.RECORD_BUTTON,
        TeleautoHardwareControlCatalog.RIGHT_THREE_POSITION_SWITCH_01,
    ) + if (hasC3) listOf(TeleautoHardwareControlCatalog.CUSTOM_BUTTON_C3) else emptyList()

    private var reader: TeleautoProtocolInputReader? = null
    private var callback: TeleautoRemoteControllerProfile.Callback? = null
    private var generation = 0L
    private var connected = false
    private var held = emptySet<TeleautoPhysicalKey>()
    private var mode = -1

    @Synchronized
    override fun start(context: Context, callback: TeleautoRemoteControllerProfile.Callback) {
        stop()
        this.callback = callback
        val current = ++generation
        reader = TeleautoProtocolInputReader { input ->
            synchronized(this) { if (current == generation) accept(input) }
        }.also { it.start() }
    }

    @Synchronized
    internal fun accept(input: TeleautoProtocolInput) {
        val sink = callback ?: return
        if (!connected) { connected = true; sink.onConnectionChanged(true) }
        sink.onStickChanged(TeleautoStickSnapshot(input.leftX, input.leftY, input.rightX, input.rightY,
            input.leftDial, input.rightDial, 0f, System.nanoTime()))
        val next = BUTTONS.filter { (bit, key) -> input.buttons and (1 shl bit) != 0 && key in supportedInputs }.values.toSet()
        if (next != held) {
            (held - next).forEach { sink.onKeyEvent(TeleautoRcKeyEvent(it, TeleautoKeyAction.UP)) }
            (next - held).forEach { sink.onKeyEvent(TeleautoRcKeyEvent(it, TeleautoKeyAction.DOWN)) }
            held = next
            sink.onHeldKeysChanged(next)
        }
        val position = (input.buttons ushr 16) and 3
        if (position != mode) {
            mode = position
            val key = listOf(TeleautoPhysicalKey.MODE_1, TeleautoPhysicalKey.MODE_2, TeleautoPhysicalKey.MODE_3).getOrNull(position)
            key?.let { sink.onKeyEvent(TeleautoRcKeyEvent(it, TeleautoKeyAction.TRIGGER)) }
            sink.onThreePositionSwitchChanged(TeleautoThreePositionSwitchState.fromRawValue(position))
        }
    }

    @Synchronized
    override fun stop() {
        generation++
        reader?.stop()
        reader = null
        val sink = callback
        held.forEach { sink?.onKeyEvent(TeleautoRcKeyEvent(it, TeleautoKeyAction.UP)) }
        if (sink != null) {
            sink.onHeldKeysChanged(emptySet())
            sink.onStickChanged(TeleautoStickSnapshot())
            sink.onThreePositionSwitchChanged(TeleautoThreePositionSwitchState.fromRawValue(-1))
            sink.onConnectionChanged(false)
        }
        callback = null
        held = emptySet()
        mode = -1
        connected = false
    }

    private companion object {
        val BUTTONS = mapOf(4 to TeleautoPhysicalKey.PAUSE, 5 to TeleautoPhysicalKey.GOHOME,
            6 to TeleautoPhysicalKey.SHUTTER, 7 to TeleautoPhysicalKey.FOCUS, 8 to TeleautoPhysicalKey.RECORD,
            11 to TeleautoPhysicalKey.D_MIDDLE, 12 to TeleautoPhysicalKey.D_RIGHT, 13 to TeleautoPhysicalKey.D_LEFT,
            14 to TeleautoPhysicalKey.D_DOWN, 15 to TeleautoPhysicalKey.D_UP,
            18 to TeleautoPhysicalKey.C1, 19 to TeleautoPhysicalKey.C2, 20 to TeleautoPhysicalKey.C3)
    }
}

class TeleautoRm510Profile : TeleautoRcProProfile() {
    override val id = "rm510"
    override val hardwareProfile = TeleautoHardwareProfile.RM510
    override val productNames = mapOf("rm510" to "DJI RC PRO")
    override val hasC3 = true
}

class TeleautoRc520Profile : TeleautoRcProProfile() {
    override val id = "rc520"
    override val hardwareProfile = TeleautoHardwareProfile.RC520
    override val productNames = mapOf("rc520" to "DJI RC PRO 2")
}
