package tech.alius.rc

import tech.alius.rc.model.TeleautoConnectedModuleStatus
import tech.alius.rc.model.TeleautoConnectionState
import tech.alius.rc.model.TeleautoModuleCategory
import tech.alius.rc.model.TeleautoModuleWorkState
import tech.alius.rc.model.TeleautoRemoteControlStatus
import tech.alius.rc.model.TeleautoTransmitModuleState
import tech.alius.rc.video.TeleautoVideoTransmissionManager
import tech.alius.rc.video.TeleautoVideoStateListener
import tech.alius.rc.video.TeleautoWfbAdapterState
import tech.alius.rc.video.TeleautoWfbAdapterStateListener
import java.util.concurrent.Executor

/** 聚合遥控器、电池、遥控模块与图传模块状态。 */
object TeleautoRemoteControlStatusManager {
    fun interface Listener {
        fun onStatusChanged(status: TeleautoRemoteControlStatus)
    }

    fun interface Subscription : AutoCloseable {
        override fun close()
    }

    private val listeners = mutableListOf<Listener>()
    private var executor: Executor = TeleautoConfig.mainExecutor()
    private var joystickSubscription: TeleautoJoystickManager.Subscription? = null
    private var transmitSubscription: TeleautoTransmitModuleManager.Subscription? = null
    private var deviceSubscription: tech.alius.rc.device.TeleautoDeviceSubscription? = null

    private val joystickObserver = object : TeleautoJoystickManager.Observer {
        override fun onStateChanged(state: TeleautoConnectionState) = publish()
    }
    private val batteryListener = TeleautoRemoteControllerStatusManager.BatteryListener { publish() }
    private val transmitListener = TeleautoTransmitModuleManager.Listener { publish() }
    private val videoListener = TeleautoVideoStateListener { _, _ -> publish() }
    private val adapterListener = TeleautoWfbAdapterStateListener { _, _ -> publish() }

    fun getStatus(): TeleautoRemoteControlStatus = TeleautoRemoteControlStatus(
        initialized = TeleautoSdk.isInitialized,
        device = TeleautoSdk.getDeviceInfo(),
        inputConnection = TeleautoJoystickManager.connection,
        battery = TeleautoRemoteControllerStatusManager.getBatteryState(),
        modules = buildModules(),
        devices = tech.alius.rc.device.TeleautoDeviceManager.snapshots(),
    )

    fun observe(listener: Listener): Subscription {
        synchronized(listeners) {
            if (!listeners.contains(listener)) listeners.add(listener)
        }
        executor.execute { listener.onStatusChanged(getStatus()) }
        return Subscription { synchronized(listeners) { listeners.remove(listener) } }
    }

    internal fun initialize(callbackExecutor: Executor) {
        if (joystickSubscription != null) return
        executor = callbackExecutor
        joystickSubscription = TeleautoJoystickManager.observe(joystickObserver)
        TeleautoRemoteControllerStatusManager.addBatteryListener(batteryListener)
        transmitSubscription = TeleautoTransmitModuleManager.observe(transmitListener)
        TeleautoVideoTransmissionManager.addStateListener(videoListener)
        TeleautoVideoTransmissionManager.addWfbAdapterStateListener(adapterListener)
        deviceSubscription = tech.alius.rc.device.TeleautoDeviceManager.observe { publish() }
        publish()
    }

    internal fun release() {
        joystickSubscription?.close()
        joystickSubscription = null
        transmitSubscription?.close()
        transmitSubscription = null
        deviceSubscription?.close()
        deviceSubscription = null
        TeleautoRemoteControllerStatusManager.removeBatteryListener(batteryListener)
        TeleautoVideoTransmissionManager.removeStateListener(videoListener)
        TeleautoVideoTransmissionManager.removeWfbAdapterStateListener(adapterListener)
        synchronized(listeners) { listeners.clear() }
    }

    private fun buildModules(): List<TeleautoConnectedModuleStatus> = buildList {
        TeleautoTransmitModuleManager.getTransmitModules().forEach { module ->
            add(
                TeleautoConnectedModuleStatus(
                    id = module.id,
                    category = TeleautoModuleCategory.TRANSMIT,
                    displayName = module.displayName,
                    workState = when (module.state) {
                        TeleautoTransmitModuleState.CONNECTING -> TeleautoModuleWorkState.CONNECTING
                        TeleautoTransmitModuleState.CONNECTED -> TeleautoModuleWorkState.RUNNING
                        TeleautoTransmitModuleState.ERROR -> TeleautoModuleWorkState.ERROR
                    },
                    hardwareId = module.hardwareId,
                    firmwareVersion = module.firmwareVersion,
                    batteryLevelPercent = module.batteryLevelPercent,
                    detail = module.detail,
                )
            )
        }
        TeleautoVideoTransmissionManager.getVideoTransmissionModules().forEach { module ->
            add(
                TeleautoConnectedModuleStatus(
                    id = module.id,
                    category = TeleautoModuleCategory.VIDEO_TRANSMISSION,
                    displayName = module.displayName,
                    workState = when (module.state) {
                        TeleautoWfbAdapterState.READY,
                        TeleautoWfbAdapterState.RECEIVING,
                        TeleautoWfbAdapterState.CONNECTED -> if (TeleautoVideoTransmissionManager.decodingPaused) {
                            TeleautoModuleWorkState.PAUSED
                        } else {
                            TeleautoModuleWorkState.RUNNING
                        }
                        TeleautoWfbAdapterState.CONNECTING,
                        TeleautoWfbAdapterState.USB_PRESENT,
                        TeleautoWfbAdapterState.PERMISSION_GRANTED,
                        TeleautoWfbAdapterState.INITIALIZING,
                        TeleautoWfbAdapterState.PERMISSION_REQUIRED -> TeleautoModuleWorkState.CONNECTING
                        TeleautoWfbAdapterState.ERROR,
                        TeleautoWfbAdapterState.ERROR_USB_PROTOCOL -> TeleautoModuleWorkState.ERROR
                        TeleautoWfbAdapterState.DISABLED,
                        TeleautoWfbAdapterState.ABSENT -> TeleautoModuleWorkState.CONNECTING
                    },
                    detail = module.detail,
                )
            )
        }
    }

    private fun publish() {
        val snapshot = getStatus()
        val targets = synchronized(listeners) { listeners.toList() }
        if (targets.isEmpty()) return
        executor.execute { targets.forEach { it.onStatusChanged(snapshot) } }
    }

    /** Internal hook for state changes that do not alter a legacy listener enum. */
    internal fun refresh() = publish()
}
