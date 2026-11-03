package tech.alius.rc.hid

import tech.alius.rc.model.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.math.roundToInt

/** The 19 approved inputs; media, five-way navigation and mode switches remain local. */
class TeleautoHidJoystickMapper(private val kind: TeleautoHidJoystickDescriptor.DeviceKind = TeleautoHidJoystickDescriptor.DeviceKind.GAMEPAD) {
    private val buttonBits = mapOf(
        TeleautoPhysicalKey.R3 to 0, // A
        TeleautoPhysicalKey.R2 to 1, // B
        TeleautoPhysicalKey.L2 to 2, // X
        TeleautoPhysicalKey.R1 to 3, // Y
        TeleautoPhysicalKey.C1 to 4, // LB
        TeleautoPhysicalKey.C2 to 5, // RB
        TeleautoPhysicalKey.GOHOME to 8, // View
        TeleautoPhysicalKey.L3 to 9, // Menu
        TeleautoPhysicalKey.C3 to 10, // LS
        TeleautoPhysicalKey.AUTH_LED to 11, // RS
        TeleautoPhysicalKey.L1 to 16, // Home
        TeleautoPhysicalKey.PAUSE to 17, // Auxiliary button 18
    )
    private val buttons = AtomicInteger(0)
    private val pulseDeadlines = AtomicLongArray(18)
    @Volatile private var sticks = TeleautoStickSnapshot()
    fun onFlightModeSwitchChanged(state: FlightModeSwitchState) { /* Local-only control. */ }
    fun onStickChanged(snapshot: TeleautoStickSnapshot) { sticks = snapshot }
    @Synchronized fun onKeyEvent(event: TeleautoRcKeyEvent) {
        val bit = buttonBits[event.key] ?: return
        when (event.action) {
            TeleautoKeyAction.DOWN, TeleautoKeyAction.TOGGLED_ON -> { pulseDeadlines.set(bit, 0); setBit(bit, true) }
            TeleautoKeyAction.UP, TeleautoKeyAction.TOGGLED_OFF -> { pulseDeadlines.set(bit, 0); setBit(bit, false) }
            TeleautoKeyAction.TRIGGER -> { pulseDeadlines.set(bit, System.nanoTime()+180_000_000L); setBit(bit, true) }
        }
    }
    @Synchronized fun onHeldKeysChanged(held: Set<TeleautoPhysicalKey>) {
        buttonBits.forEach { (key, bit) ->
            if (key in held) { pulseDeadlines.set(bit, 0); setBit(bit, true) }
            else if (pulseDeadlines.get(bit) == 0L) setBit(bit, false)
        }
    }
    @Synchronized fun buildReport(): TeleautoHidJoystickReport {
        val now = System.nanoTime()
        buttonBits.values.forEach { bit ->
            val deadline = pulseDeadlines.get(bit)
            if (deadline != 0L && now >= deadline && pulseDeadlines.compareAndSet(bit, deadline, 0)) setBit(bit, false)
        }
        val value = sticks
        val joystick = kind == TeleautoHidJoystickDescriptor.DeviceKind.JOYSTICK
        return TeleautoHidJoystickReport(
            digitalButtonBits = buttons.get(),
            leftTrigger = if (joystick) axisToByte(value.leftDial) else triggerToByte(-value.leftDial),
            rightTrigger = if (joystick) axisToByte(value.rightDial) else triggerToByte(value.rightDial),
            leftStickX = axisToByte(value.leftX),
            leftStickY = axisToByte(value.leftY),
            // Preserve the existing physical right-stick orientation correction.
            rightStickX = axisToByte(-value.rightY),
            rightStickY = axisToByte(value.rightX),
            freeDial = axisToByte(value.freeDial),
        )
    }
    private fun axisToByte(value: Float): Byte = ((value.coerceIn(-1f, 1f)+1f)*127.5f).roundToInt().coerceIn(0,255).toByte()
    private fun triggerToByte(value: Float): Byte {
        // Self-centering dials: only the outward half-axis presses the trigger.
        // Keep the normalized displacement linear; the input backend owns dead zones.
        return (value.coerceIn(0f, 1f) * 255).roundToInt().toByte()
    }
    private fun setBit(bit: Int, on: Boolean) {
        while (true) {
            val old = buttons.get()
            val next = if (on) old or (1 shl bit) else old and (1 shl bit).inv()
            if (buttons.compareAndSet(old, next)) return
        }
    }
}
