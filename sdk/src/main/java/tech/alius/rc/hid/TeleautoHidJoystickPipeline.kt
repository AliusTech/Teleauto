package tech.alius.rc.hid

import android.content.Context
import android.util.Log
import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoRcKeyEvent
import tech.alius.rc.model.TeleautoStickSnapshot
import tech.alius.rc.model.FlightModeSwitchState

/**
 * 蓝牙手柄管线:输入事件 → mapper 状态 → BLE HID GATT 报告。
 *
 * [TeleautoBleHidJoystick] 内部 100Hz 报告循环轮询 [mapper];本类只负责把
 * TeleautoJoystickManager bridge 的事件喂进 mapper 与传输生命周期。
 */
internal class TeleautoHidJoystickPipeline(
    context: Context,
    deviceName: String,
    deviceKind: TeleautoHidJoystickDescriptor.DeviceKind,
    private val onState: (String?) -> Unit,
) {
    val mapper = TeleautoHidJoystickMapper(deviceKind)
    val transport = TeleautoBleHidJoystick(
        context.applicationContext,
        deviceName,
        deviceKind,
        reportProvider = { mapper.buildReport() },
        onConnectionState = { connected, address, detail ->
            onState(
                detail ?: if (connected) {
                    "系统已订阅 Input Report,主机 $address 可作为手柄读取"
                } else {
                    "主机已断开"
                },
            )
        },
        onLog = { },
    )

    val available: Boolean get() = true // BLE GATT 自 API 18+ 可用,目标设备均为 API 29+

    fun start() {
        if (!transport.start()) {
            onState("启动失败(权限/蓝牙不可用)")
            Log.w(TAG, "BLE HID start failed")
        }
    }

    fun stop() {
        transport.stop()
    }

    // ── 输入事件入口(TeleautoJoystickManager bridge 转发)────────────
    fun onStickChanged(snapshot: TeleautoStickSnapshot) = mapper.onStickChanged(snapshot)
    fun onFlightModeSwitchChanged(state: FlightModeSwitchState) = mapper.onFlightModeSwitchChanged(state)
    fun onKeyEvent(event: TeleautoRcKeyEvent) = mapper.onKeyEvent(event)
    fun onHeldKeysChanged(held: Set<TeleautoPhysicalKey>) = mapper.onHeldKeysChanged(held)

    companion object {
        private const val TAG = "TeleautoJoystickManager-HidPipeline"
    }
}
