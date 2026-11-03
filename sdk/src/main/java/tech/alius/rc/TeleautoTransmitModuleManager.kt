package tech.alius.rc

import tech.alius.rc.model.TeleautoTransmitModuleInfo
import tech.alius.rc.model.TeleautoTransmitModuleState
import java.util.concurrent.Executor
import tech.alius.rc.usb.TeleautoCdcTransmitModuleAdapter
import tech.alius.rc.usb.TeleautoUsbModuleAdapter
import tech.alius.rc.device.TeleautoTargetDeviceConfig

/** Discovery/state API for command-link modules such as future high-frequency heads. */
object TeleautoTransmitModuleManager {
    fun interface Listener {
        fun onTransmitModulesChanged(modules: List<TeleautoTransmitModuleInfo>)
    }

    fun interface Subscription : AutoCloseable {
        override fun close()
    }

    private val listeners = mutableListOf<Listener>()
    private val modules = linkedMapOf<String, TeleautoTransmitModuleInfo>()
    private var executor: Executor = TeleautoConfig.mainExecutor()
    private var cdcAdapter: TeleautoCdcTransmitModuleAdapter? = null
    private var preferredUsbDeviceId: String? = null
    private var baudRate: Int = 115_200

    fun getTransmitModules(): List<TeleautoTransmitModuleInfo> =
        synchronized(modules) { modules.values.toList() }

    fun getConnectedTransmitModules(): List<TeleautoTransmitModuleInfo> =
        getTransmitModules().filter { it.state == TeleautoTransmitModuleState.CONNECTED }

    fun getTransmitModule(id: String): TeleautoTransmitModuleInfo? =
        synchronized(modules) { modules[id] }

    fun observe(listener: Listener): Subscription {
        synchronized(listeners) { if (!listeners.contains(listener)) listeners.add(listener) }
        executor.execute { listener.onTransmitModulesChanged(getTransmitModules()) }
        return Subscription { synchronized(listeners) { listeners.remove(listener) } }
    }

    internal fun initialize(config: TeleautoConfig) {
        val callbackExecutor = config.callbackExecutor
        executor = callbackExecutor
        preferredUsbDeviceId = config.preferredTransmitUsbDeviceId
        baudRate = config.transmitModuleBaudRate
        if (cdcAdapter == null) {
            cdcAdapter = TeleautoCdcTransmitModuleAdapter({ baudRate }) { preferredUsbDeviceId }
        }
    }

    internal fun usbAdapter(): TeleautoUsbModuleAdapter? = cdcAdapter

    /** Selects one discovered CDC module when more than one candidate is attached. */
    fun selectTransmitUsbDevice(deviceId: String?): Boolean {
        preferredUsbDeviceId = deviceId
        return cdcAdapter?.select(deviceId) == true
    }

    internal fun configureTarget(config: TeleautoTargetDeviceConfig) {
        val changed = baudRate != config.cdcBaudRate || preferredUsbDeviceId != config.preferredUsbDeviceId
        baudRate = config.cdcBaudRate
        preferredUsbDeviceId = config.preferredUsbDeviceId
        if (changed) cdcAdapter?.reconfigure()
    }

    /** Backend entry point reserved for USB/serial/BLE high-frequency module implementations. */
    internal fun update(module: TeleautoTransmitModuleInfo) {
        val changed = synchronized(modules) {
            if (modules[module.id] == module) false else {
                modules[module.id] = module
                true
            }
        }
        if (changed) notifyChanged()
    }

    /** Detach removes the module from discovery results instead of publishing an ABSENT row. */
    internal fun remove(id: String) {
        val changed = synchronized(modules) { modules.remove(id) != null }
        if (changed) notifyChanged()
    }

    internal fun clear() {
        cdcAdapter?.close()
        cdcAdapter = null
        preferredUsbDeviceId = null
        baudRate = 115_200
        synchronized(modules) { modules.clear() }
        notifyChanged()
        synchronized(listeners) { listeners.clear() }
    }

    private fun notifyChanged() {
        val moduleSnapshot = getTransmitModules()
        val listenerSnapshot = synchronized(listeners) { listeners.toList() }
        executor.execute { listenerSnapshot.forEach { it.onTransmitModulesChanged(moduleSnapshot) } }
    }
}
