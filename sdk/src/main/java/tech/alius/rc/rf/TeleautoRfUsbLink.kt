package tech.alius.rc.rf

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * USB CDC 射频链路:通过 Android USB Host API 连接 mLRS 高频头。
 * 自动发现 CDC 设备、请求权限、建立串口通信、解析 CRSF 帧。
 *
 * 用法:
 * ```
 * val link = TeleautoRfUsbLink(context, listener, executor)
 * link.start()  // 自动发现设备并连接
 * ```
 */
class TeleautoRfUsbLink(
    private val context: Context,
    private val listener: TeleautoRfLinkListener,
    private val executor: Executor,
    private val baudRate: Int = TeleautoCrsf.Baud.CRSF_420K,
    private val vendorId: Int? = null,  // 指定 VID 过滤(null = 任意 CDC 设备)
) : TeleautoRfLink {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val running = AtomicBoolean(false)
    private var connection: UsbDeviceConnection? = null
    private var dataInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    private var readThread: Thread? = null
    private var permissionReceiver: BroadcastReceiver? = null

    @Volatile private var connected = false
    @Volatile override var isConnected: Boolean = false
        get() = connected

    @Volatile override var stats: TeleautoRfStats = TeleautoRfStats()
        private set

    private val parser = TeleautoCrsfParser { frame ->
        executor.execute { dispatchFrame(frame) }
    }

    override fun start() {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
        if (running.getAndSet(true)) return
        registerReceiver()
        discoverDevice()
    }

    override fun stop() {
        running.set(false)
        connected = false
        readThread?.interrupt()
        readThread = null
        closeConnection()
        unregisterReceiver()
        parser.reset()
    }

    // ── 设备发现与权限 ──

    private fun discoverDevice() {
        val devices = usbManager.deviceList.values
        val device = devices.firstOrNull { isRfDevice(it) }
        if (device == null) {
            Log.w(TAG, "未发现射频模块 USB 设备")
            return
        }
        requestPermission(device)
    }

    private fun isRfDevice(device: UsbDevice): Boolean {
        if (vendorId != null && device.vendorId != vendorId) return false
        // CDC 设备:class 2 (Communications), 或有 CDC data interface (class 0A)
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA ||
                (intf.interfaceClass == UsbConstants.USB_CLASS_COMM && intf.endpointCount > 0)) {
                return true
            }
        }
        // 也接受 vendor-specific 类(部分 mLRS 模块用自定义 VID/PID)
        return device.deviceClass == UsbConstants.USB_CLASS_VENDOR_SPEC
    }

    private fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            connect(device)
            return
        }
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        usbManager.requestPermission(device, pendingIntent)
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        permissionReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_USB_PERMISSION -> {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        if (granted && device != null) connect(device)
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> discoverDevice()
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> stop()
                }
            }
        }
        context.registerReceiver(permissionReceiver, filter)
    }

    private fun unregisterReceiver() {
        permissionReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        permissionReceiver = null
    }

    // ── 连接与通信 ──

    private fun connect(device: UsbDevice) {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
        val conn = usbManager.openDevice(device) ?: run {
            Log.e(TAG, "无法打开 USB 设备 ${device.deviceName}")
            return
        }

        // 查找 CDC data interface(有 bulk 端点的)
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            var epIn: UsbEndpoint? = null
            var epOut: UsbEndpoint? = null
            for (j in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep else epOut = ep
                }
            }
            if (epIn != null) {
                if (!conn.claimInterface(intf, true)) continue
                this.connection = conn
                this.dataInterface = intf
                this.endpointIn = epIn
                this.endpointOut = epOut
                configureBaudRate(conn, intf, baudRate)
                startReading()
                return
            }
        }
        conn.close()
        Log.e(TAG, "未找到可用的 CDC data interface")
    }

    private fun configureBaudRate(conn: UsbDeviceConnection, intf: UsbInterface, baud: Int) {
        // CDC SET_LINE_CODING: bmRequestType=21, bRequest=20, wValue=0, wIndex=interface
        val lineCoding = byteArrayOf(
            (baud and 0xFF).toByte(),
            ((baud shr 8) and 0xFF).toByte(),
            ((baud shr 16) and 0xFF).toByte(),
            ((baud shr 24) and 0xFF).toByte(),
            0,    // 1 stop bit
            0,    // no parity
            8,    // 8 data bits
        )
        conn.controlTransfer(
            0x21, 0x20, 0, intf.id, lineCoding, lineCoding.size, 500,
        )
    }

    private fun startReading() {
        connected = true
        readThread = Thread({
            val buffer = ByteArray(512)
            while (running.get() && connected && tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) {
                val conn = connection ?: break
                val ep = endpointIn ?: break
                val len = conn.bulkTransfer(ep, buffer, buffer.size, READ_TIMEOUT_MS)
                if (len > 0) {
                    parser.feed(buffer, len)
                }
            }
        }, "TeleautoRfUsbLink-Read").apply {
            isDaemon = true
            start()
        }
    }

    private fun closeConnection() {
        dataInterface?.let { connection?.releaseInterface(it) }
        connection?.close()
        connection = null
        dataInterface = null
        endpointIn = null
        endpointOut = null
    }

    // ── 帧分发 ──

    private var channelCount = 0
    private var telemetryCount = 0
    private var lastStatsTime = System.currentTimeMillis()

    private fun dispatchFrame(frame: TeleautoCrsfFrame) {
        if (!running.get() || !tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
        listener.onRawFrame(frame)

        when (frame.type.toInt() and 0xFF) {
            TeleautoCrsf.Type.RC_CHANNELS_PACKED -> frame.asChannels()?.let {
                listener.onChannels(it)
                channelCount++
            }
            TeleautoCrsf.Type.LINK_STATS -> frame.asLinkStats()?.let {
                listener.onLinkStats(it)
                telemetryCount++
                stats = stats.copy(lastRssi = it.downlinkRssi)
            }
            TeleautoCrsf.Type.BATTERY -> frame.asBattery()?.let {
                listener.onBattery(it)
                telemetryCount++
            }
            TeleautoCrsf.Type.GPS -> frame.asGps()?.let {
                listener.onGps(it)
                telemetryCount++
            }
            TeleautoCrsf.Type.FLIGHT_MODE -> frame.asFlightMode()?.let {
                listener.onFlightMode(it)
                telemetryCount++
            }
        }

        // 1Hz 刷新统计
        val now = System.currentTimeMillis()
        if (now - lastStatsTime >= 1000) {
            stats = stats.copy(
                framesReceived = parser.framesParsed,
                crcErrors = parser.crcErrors,
                channelsPerSecond = channelCount,
                telemetryPerSecond = telemetryCount,
            )
            channelCount = 0
            telemetryCount = 0
            lastStatsTime = now
        }
    }

    companion object {
        private const val TAG = "TeleautoRfUsbLink"
        private const val ACTION_USB_PERMISSION = "tech.alius.rc.rf.USB_PERMISSION"
        private const val READ_TIMEOUT_MS = 50
    }
}
