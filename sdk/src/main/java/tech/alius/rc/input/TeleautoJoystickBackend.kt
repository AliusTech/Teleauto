package tech.alius.rc.input

import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoRcKeyEvent
import tech.alius.rc.model.TeleautoStickSnapshot

internal interface TeleautoJoystickBackend {
    interface Callback {
        fun onDevicesChanged(devices: List<TeleautoJoystickDeviceInfo>) {}
        fun onStick(snapshot: TeleautoStickSnapshot)
        fun onKey(event: TeleautoRcKeyEvent)
        fun onHeldKeys(held: Set<TeleautoPhysicalKey>)
        fun onConnection(connected: Boolean)
    }

    fun start()
    fun stop()
    fun setForeground(foreground: Boolean)
}
