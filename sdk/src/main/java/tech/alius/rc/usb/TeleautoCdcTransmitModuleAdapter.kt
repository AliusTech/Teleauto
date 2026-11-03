package tech.alius.rc.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import tech.alius.rc.TeleautoTransmitModuleManager
import tech.alius.rc.model.TeleautoTransmitModuleInfo
import tech.alius.rc.model.TeleautoTransmitModuleState
import tech.alius.rc.model.TeleautoTransmitModuleType
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

internal interface TeleautoByteStreamListener {
    fun onBytes(bytes: ByteArray, length: Int)
    fun onTransportClosed() {}
}

internal object TeleautoCdcMavlinkTransport {
    private val listeners = CopyOnWriteArrayList<TeleautoByteStreamListener>()
    @Volatile private var writer: ((ByteArray) -> Boolean)? = null
    @Volatile var connected: Boolean = false
        private set

    fun addListener(listener: TeleautoByteStreamListener) { listeners.addIfAbsent(listener) }
    fun removeListener(listener: TeleautoByteStreamListener) { listeners.remove(listener) }
    fun write(bytes: ByteArray): Boolean {
        var sent = false
        tech.alius.rc.model.TeleautoControlOutputGate.gate.runIfOpen { sent = writer?.invoke(bytes) == true }
        return sent
    }

    internal fun open(write: (ByteArray) -> Boolean) { writer = write; connected = true }
    internal fun offer(bytes: ByteArray, length: Int) { listeners.forEach { it.onBytes(bytes, length) } }
    internal fun close() {
        if (!connected && writer == null) return
        connected = false
        writer = null
        listeners.forEach(TeleautoByteStreamListener::onTransportClosed)
    }
}

internal class TeleautoCdcTransmitModuleAdapter(
    private val baudRate: () -> Int,
    private val preferredDeviceId: () -> String?,
) : TeleautoUsbModuleAdapter {
    override val adapterId = "cdc-transmit"
    private data class Candidate(val device: UsbDevice, val descriptor: TeleautoUsbDeviceDescriptor, val manager: UsbManager)
    private val candidates = linkedMapOf<String, Candidate>()
    private var active: Candidate? = null
    private var connection: UsbDeviceConnection? = null
    private var dataInterface: UsbInterface? = null
    private var readEndpoint: UsbEndpoint? = null
    private var writeEndpoint: UsbEndpoint? = null
    private var reader: Thread? = null
    private val running = AtomicBoolean(false)

    override fun matches(device: UsbDevice): Boolean = TeleautoUsbDeviceRegistry.isCdcAcm(device)

    override fun onDiscovered(device: UsbDevice, descriptor: TeleautoUsbDeviceDescriptor, hasPermission: Boolean) {
        TeleautoTransmitModuleManager.update(
            descriptor.info(TeleautoTransmitModuleState.CONNECTING, if (hasPermission) "正在初始化高频头" else "等待USB授权")
        )
    }

    override fun onPermissionRequired(device: UsbDevice, descriptor: TeleautoUsbDeviceDescriptor) {
        TeleautoTransmitModuleManager.update(descriptor.info(TeleautoTransmitModuleState.CONNECTING, "请允许访问高频头"))
    }

    override fun onReady(device: UsbDevice, descriptor: TeleautoUsbDeviceDescriptor, manager: UsbManager) {
        candidates[descriptor.stableId] = Candidate(device, descriptor, manager)
        chooseActive()
    }

    override fun onPermissionDenied(device: UsbDevice, descriptor: TeleautoUsbDeviceDescriptor) {
        TeleautoTransmitModuleManager.update(descriptor.info(TeleautoTransmitModuleState.ERROR, "高频头USB权限被拒绝"))
    }

    override fun onDetached(descriptor: TeleautoUsbDeviceDescriptor) {
        candidates.remove(descriptor.stableId)
        if (active?.descriptor?.generation == descriptor.generation && active?.descriptor?.deviceName == descriptor.deviceName) {
            stopActive()
            TeleautoTransmitModuleManager.remove(descriptor.stableId)
            chooseActive()
        } else {
            TeleautoTransmitModuleManager.remove(descriptor.stableId)
        }
    }

    fun select(stableId: String?): Boolean {
        if (stableId != null && !candidates.containsKey(stableId)) return false
        stopActive()
        chooseActive(stableId)
        return true
    }

    fun reconfigure() {
        if (active == null) return
        stopActive()
        chooseActive()
    }

    override fun close() { stopActive(); candidates.clear() }

    private fun chooseActive(explicitId: String? = preferredDeviceId()) {
        if (active != null) return
        val selected = when {
            explicitId != null -> candidates[explicitId]
            candidates.size == 1 -> candidates.values.first()
            else -> null
        }
        if (selected == null) {
            if (candidates.size > 1) candidates.values.forEach {
                TeleautoTransmitModuleManager.update(it.descriptor.info(TeleautoTransmitModuleState.ERROR, "检测到多个高频头，请选择设备"))
            }
            return
        }
        start(selected)
    }

    private fun start(candidate: Candidate) {
        val device = candidate.device
        val data = (0 until device.interfaceCount).map(device::getInterface).firstOrNull { intf ->
            intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA ||
                (0 until intf.endpointCount).count { intf.getEndpoint(it).type == UsbConstants.USB_ENDPOINT_XFER_BULK } >= 2
        } ?: run {
            TeleautoTransmitModuleManager.update(candidate.descriptor.info(TeleautoTransmitModuleState.ERROR, "高频头缺少CDC数据接口")); return
        }
        val input = (0 until data.endpointCount).map(data::getEndpoint)
            .firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_IN }
        val output = (0 until data.endpointCount).map(data::getEndpoint)
            .firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }
        if (input == null || output == null) {
            TeleautoTransmitModuleManager.update(candidate.descriptor.info(TeleautoTransmitModuleState.ERROR, "高频头CDC端点不完整")); return
        }
        val conn = candidate.manager.openDevice(device) ?: run {
            TeleautoTransmitModuleManager.update(candidate.descriptor.info(TeleautoTransmitModuleState.ERROR, "高频头打开失败")); return
        }
        if (!conn.claimInterface(data, true)) {
            conn.close()
            TeleautoTransmitModuleManager.update(candidate.descriptor.info(TeleautoTransmitModuleState.ERROR, "高频头接口认领失败")); return
        }
        configureLine(conn, device, baudRate())
        active = candidate
        connection = conn
        dataInterface = data
        readEndpoint = input
        writeEndpoint = output
        running.set(true)
        TeleautoCdcMavlinkTransport.open { bytes ->
            val c = connection ?: return@open false
            val endpoint = writeEndpoint ?: return@open false
            c.bulkTransfer(endpoint, bytes, bytes.size, 500) == bytes.size
        }
        reader = Thread({ readLoop(candidate) }, "djirc-cdc-mavlink").apply { isDaemon = true; start() }
        TeleautoTransmitModuleManager.update(candidate.descriptor.info(TeleautoTransmitModuleState.CONNECTED, "高频头运行中"))
    }

    private fun readLoop(candidate: Candidate) {
        val buffer = ByteArray(4096)
        try {
            while (running.get()) {
                val c = connection ?: break
                val endpoint = readEndpoint ?: break
                val count = c.bulkTransfer(endpoint, buffer, buffer.size, 250)
                if (count > 0) TeleautoCdcMavlinkTransport.offer(buffer, count)
            }
        } catch (error: Throwable) {
            Log.w(TAG, "CDC reader failed", error)
        } finally {
            if (running.getAndSet(false)) {
                TeleautoCdcMavlinkTransport.close()
                TeleautoTransmitModuleManager.update(candidate.descriptor.info(TeleautoTransmitModuleState.ERROR, "高频头数据通道已断开"))
            }
        }
    }

    private fun stopActive() {
        running.set(false)
        TeleautoCdcMavlinkTransport.close()
        reader?.interrupt()
        reader = null
        val c = connection
        val intf = dataInterface
        if (c != null && intf != null) runCatching { c.releaseInterface(intf) }
        runCatching { c?.close() }
        connection = null
        dataInterface = null
        readEndpoint = null
        writeEndpoint = null
        active = null
    }

    private fun configureLine(connection: UsbDeviceConnection, device: UsbDevice, baud: Int) {
        val controlIndex = (0 until device.interfaceCount).map(device::getInterface)
            .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_COMM }?.id ?: return
        val line = byteArrayOf(
            (baud and 0xff).toByte(), ((baud ushr 8) and 0xff).toByte(),
            ((baud ushr 16) and 0xff).toByte(), ((baud ushr 24) and 0xff).toByte(),
            0, 0, 8,
        )
        connection.controlTransfer(0x21, 0x20, 0, controlIndex, line, line.size, 500)
        connection.controlTransfer(0x21, 0x22, 3, controlIndex, null, 0, 500)
    }

    private fun TeleautoUsbDeviceDescriptor.info(state: TeleautoTransmitModuleState, detail: String) = TeleautoTransmitModuleInfo(
        id = stableId,
        type = TeleautoTransmitModuleType.HIGH_FREQUENCY_2_4_GHZ_TRANSMITTER,
        displayName = "高频头",
        state = state,
        detail = detail,
        hardwareId = "%04X:%04X".format(vendorId, productId),
    )

    companion object { private const val TAG = "TeleautoCdcTransmit" }
}
