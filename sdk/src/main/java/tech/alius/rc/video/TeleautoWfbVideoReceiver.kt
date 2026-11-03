package tech.alius.rc.video

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.openipc.wfbngrtl8812.WfbNGStats
import com.openipc.wfbngrtl8812.WfbNgLink
import com.openipc.wfbngrtl8812.WfbUsbCompatibilityProfile
import tech.alius.rc.usb.TeleautoUsbDeviceDescriptor
import tech.alius.rc.usb.TeleautoUsbModuleAdapter
import java.io.File

/** RTL8812 business adapter. USB discovery and permission are owned by TeleautoUsbDeviceRegistry. */
internal class TeleautoWfbVideoReceiver(
    context: Context,
    private var channel: Int,
    private var bandwidth: Int,
    private val onStatus: (TeleautoWfbAdapterState, String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var link: WfbNgLink? = null
    private var started = false
    private var activeDevice: UsbDevice? = null
    private var activeDescriptor: TeleautoUsbDeviceDescriptor? = null
    private var lastStatsLogAt = 0L

    val usbAdapter: TeleautoUsbModuleAdapter = object : TeleautoUsbModuleAdapter {
        override val adapterId = "rtl8812-video-receiver"
        override val priority = 100
        override fun matches(device: UsbDevice): Boolean = isSupportedAdapter(device)
        override fun onDiscovered(device: UsbDevice, descriptor: TeleautoUsbDeviceDescriptor, hasPermission: Boolean) {
            if (!started) return
            report(TeleautoWfbAdapterState.USB_PRESENT, "已发现图传接收端")
            if (hasPermission) report(TeleautoWfbAdapterState.PERMISSION_GRANTED, "图传接收端USB权限已授予")
        }
        override fun onPermissionRequired(device: UsbDevice, descriptor: TeleautoUsbDeviceDescriptor) {
            if (started) report(TeleautoWfbAdapterState.PERMISSION_REQUIRED, "请允许访问图传接收端")
        }
        override fun onPermissionDenied(device: UsbDevice, descriptor: TeleautoUsbDeviceDescriptor) {
            if (started) report(TeleautoWfbAdapterState.ERROR, "图传接收端USB权限被拒绝")
        }
        override fun onReady(device: UsbDevice, descriptor: TeleautoUsbDeviceDescriptor, manager: UsbManager) {
            if (started) startDevice(device, descriptor)
        }
        override fun onDetached(descriptor: TeleautoUsbDeviceDescriptor) {
            if (activeDescriptor?.generation != descriptor.generation || activeDescriptor?.deviceName != descriptor.deviceName) return
            val old = link
            val device = activeDevice
            link = null
            activeDevice = null
            activeDescriptor = null
            disposeLinkAsync(old, device)
            if (started) report(TeleautoWfbAdapterState.ABSENT, "等待图传接收端")
        }
        override fun close() = stop()
    }

    fun start() {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
        if (started) return
        started = true
        TeleautoVideoTransportDiagnostics.reset()
        ensureDefaultKey()
        if (activeDevice == null) report(TeleautoWfbAdapterState.ABSENT, "等待图传接收端")
    }

    fun stop() {
        if (!started && link == null) return
        started = false
        val old = link
        link = null
        activeDevice = null
        activeDescriptor = null
        disposeLinkAsync(old)
        report(TeleautoWfbAdapterState.DISABLED, "图传接收已停止")
    }

    fun configure(config: TeleautoVideoReceiverConfig) {
        if (channel == config.channel && bandwidth == config.bandwidthMHz) return
        channel = config.channel
        bandwidth = config.bandwidthMHz
        val device = activeDevice ?: return
        val descriptor = activeDescriptor ?: return
        val old = link
        link = null
        report(TeleautoWfbAdapterState.INITIALIZING, "正在应用图传接收配置")
        Thread({
            old?.setStateListener(null)
            old?.setStatsListener(null)
            runCatching { old?.close() }
                .onFailure { Log.w(TAG, "Unable to apply receiver configuration", it) }
            mainHandler.post {
                if (started && activeDescriptor == descriptor && activeDevice == device && link == null) {
                    startDevice(device, descriptor)
                }
            }
        }, "wfb-reconfigure").start()
    }

    private fun startDevice(device: UsbDevice, descriptor: TeleautoUsbDeviceDescriptor) {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
        if (activeDescriptor == descriptor && link?.isRunning == true) return
        disposeLinkAsync(link, activeDevice)
        activeDevice = device
        activeDescriptor = descriptor
        report(TeleautoWfbAdapterState.INITIALIZING, "正在初始化图传接收端")
        val receiver = WfbNgLink(appContext).also { native ->
            native.setStatsListener(::logStats)
            native.setStateListener { nativeState, detail ->
                mainHandler.post {
                    if (activeDescriptor != descriptor) return@post
                    report(
                        when (nativeState) {
                            WfbNgLink.ReceiverState.READY -> TeleautoWfbAdapterState.READY
                            WfbNgLink.ReceiverState.RECEIVING -> TeleautoWfbAdapterState.RECEIVING
                            WfbNgLink.ReceiverState.ERROR_USB_PROTOCOL -> TeleautoWfbAdapterState.ERROR_USB_PROTOCOL
                        },
                        detail,
                    )
                }
            }
        }
        link = receiver
        if (!receiver.start(channel, bandwidth, device, compatibilityProfile())) {
            if (link === receiver) link = null
            activeDevice = null
            activeDescriptor = null
            receiver.close()
            report(TeleautoWfbAdapterState.ERROR, "图传接收端打开失败")
        }
    }

    private fun report(state: TeleautoWfbAdapterState, detail: String) = onStatus(state, detail)

    private fun disposeLinkAsync(oldLink: WfbNgLink?, detachedDevice: UsbDevice? = null) {
        if (oldLink == null) return
        oldLink.setStateListener(null)
        oldLink.setStatsListener(null)
        Thread({
            runCatching {
                if (detachedDevice != null) oldLink.stopDetached(detachedDevice)
                oldLink.close()
            }.onFailure { Log.w(TAG, "Unable to retire receiver generation", it) }
        }, "wfb-retire").start()
    }

    private fun ensureDefaultKey() {
        val target = File(appContext.filesDir, "gs.key")
        if (target.length() == 64L) return
        appContext.assets.open("gs.key").use { input -> target.outputStream().use(input::copyTo) }
    }

    private fun logStats(stats: WfbNGStats) {
        TeleautoVideoTransportDiagnostics.addFecRecovered(stats.count_p_fec_recovered.toLong())
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastStatsLogAt < 1_500) return
        lastStatsLogAt = now
        Log.d(TAG, "all=${stats.count_p_all} lost=${stats.count_p_lost} " +
            "fec=${stats.count_p_fec_recovered} bad=${stats.count_p_bad} rssi=${stats.avg_rssi}")
    }

    private fun compatibilityProfile(): WfbUsbCompatibilityProfile =
        TeleautoWfbUsbCompatibilitySelector.select(Build.PRODUCT, Build.HARDWARE, Build.VERSION.SDK_INT)

    companion object {
        private const val TAG = "AliusWfb"
        private val supportedIds = setOf(
            0x0BDA to 0x8812, 0x0BDA to 0x881A, 0x0BDA to 0x881B,
            0x2357 to 0x0101, 0x2357 to 0x0103, 0x2357 to 0x010D, 0x2357 to 0x010E,
            0x0B05 to 0x17D2, 0x2604 to 0x0012, 0x0409 to 0x0408, 0x0586 to 0x3426,
            0x0BDA to 0xA81A, 0x0BDA to 0xA82A, 0x0BDA to 0xE822,
        )
        private fun isSupportedAdapter(device: UsbDevice) = (device.vendorId to device.productId) in supportedIds
    }
}
