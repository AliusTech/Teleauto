package tech.alius.rc.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

internal data class TeleautoUsbDeviceDescriptor(
    val deviceName: String,
    val deviceId: Int,
    val vendorId: Int,
    val productId: Int,
    val interfaceClasses: Set<Int>,
    val generation: Long,
) {
    val stableId: String get() = "%04x:%04x:%s".format(vendorId, productId, deviceName)
}

internal interface TeleautoUsbModuleAdapter {
    val adapterId: String
    val priority: Int get() = 0
    fun matches(device: UsbDevice): Boolean
    fun onDiscovered(device: UsbDevice, descriptor: TeleautoUsbDeviceDescriptor, hasPermission: Boolean) {}
    fun onPermissionRequired(device: UsbDevice, descriptor: TeleautoUsbDeviceDescriptor) {}
    fun onReady(device: UsbDevice, descriptor: TeleautoUsbDeviceDescriptor, manager: UsbManager)
    fun onPermissionDenied(device: UsbDevice, descriptor: TeleautoUsbDeviceDescriptor) {}
    fun onDetached(descriptor: TeleautoUsbDeviceDescriptor)
    fun close() {}
}

/** One process-wide Android USB owner. Business adapters never register USB broadcasts themselves. */
internal object TeleautoUsbDeviceRegistry {
    private const val TAG = "TeleautoUsbRegistry"
    private const val ACTION_PERMISSION = "tech.alius.rc.USB_MODULE_PERMISSION"

    private data class Claim(
        val device: UsbDevice,
        val descriptor: TeleautoUsbDeviceDescriptor,
        val adapter: TeleautoUsbModuleAdapter,
        var ready: Boolean = false,
    )

    private var context: Context? = null
    private var manager: UsbManager? = null
    private var adapters = emptyList<TeleautoUsbModuleAdapter>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val claims = linkedMapOf<String, Claim>()
    private val generations = hashMapOf<String, Long>()
    private val permissionPending = hashSetOf<String>()
    private val periodicScan = object : Runnable {
        override fun run() {
            if (context == null) return
            scan()
            mainHandler.postDelayed(this, PERIODIC_SCAN_MS)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> scan()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> intent.usbDevice()?.let(::detach)
                ACTION_PERMISSION -> {
                    val device = intent.usbDevice() ?: return
                    permissionPending.remove(device.deviceName)
                    val claim = claims[device.deviceName] ?: return
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        makeReady(claim)
                    } else {
                        claim.adapter.onPermissionDenied(device, claim.descriptor)
                    }
                }
            }
        }
    }

    @Synchronized
    fun initialize(appContext: Context, moduleAdapters: List<TeleautoUsbModuleAdapter>) {
        if (context != null) return
        context = appContext.applicationContext
        manager = context!!.getSystemService(Context.USB_SERVICE) as UsbManager
        adapters = moduleAdapters.sortedByDescending { it.priority }
        val filter = IntentFilter().apply {
            addAction(ACTION_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) context!!.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") context!!.registerReceiver(receiver, filter)
        scan()
        mainHandler.removeCallbacks(periodicScan)
        mainHandler.postDelayed(periodicScan, PERIODIC_SCAN_MS)
    }

    @Synchronized
    fun scan() {
        val usb = manager ?: return
        val present = usb.deviceList
        claims.keys.filterNot(present::containsKey).toList().forEach { name ->
            permissionPending.remove(name)
            claims.remove(name)?.let { it.adapter.onDetached(it.descriptor) }
        }
        present.values.forEach { device ->
            val existing = claims[device.deviceName]
            if (existing != null) {
                if (existing.device.deviceId != device.deviceId) {
                    permissionPending.remove(device.deviceName)
                    claims.remove(device.deviceName)
                    existing.adapter.onDetached(existing.descriptor)
                } else {
                    // Some Android 7 DJI builds grant permission but drop the PendingIntent callback.
                    // A quiet registry rescan must promote the existing claim without starting a
                    // second adapter or forcing the host application to poll USB itself.
                    if (!existing.ready && usb.hasPermission(device)) {
                        permissionPending.remove(device.deviceName)
                        makeReady(existing)
                    }
                    return@forEach
                }
            }
            val adapter = adapters.firstOrNull { runCatching { it.matches(device) }.getOrDefault(false) }
                ?: return@forEach
            val generation = (generations[device.deviceName] ?: 0L) + 1L
            generations[device.deviceName] = generation
            val descriptor = device.descriptor(generation)
            val claim = Claim(device, descriptor, adapter)
            claims[device.deviceName] = claim
            val permitted = usb.hasPermission(device)
            adapter.onDiscovered(device, descriptor, permitted)
            if (permitted) makeReady(claim) else requestPermission(claim)
            Log.i(TAG, "claimed ${descriptor.stableId} generation=$generation adapter=${adapter.adapterId}")
        }
    }

    @Synchronized
    fun release() {
        val app = context ?: return
        mainHandler.removeCallbacks(periodicScan)
        runCatching { app.unregisterReceiver(receiver) }
        claims.values.toList().forEach { it.adapter.onDetached(it.descriptor) }
        adapters.forEach { runCatching { it.close() } }
        claims.clear()
        permissionPending.clear()
        adapters = emptyList()
        manager = null
        context = null
    }

    private fun requestPermission(claim: Claim) {
        val app = context ?: return
        val usb = manager ?: return
        claim.adapter.onPermissionRequired(claim.device, claim.descriptor)
        if (!permissionPending.add(claim.device.deviceName)) return
        val intent = Intent(ACTION_PERMISSION).setPackage(app.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        usb.requestPermission(
            claim.device,
            PendingIntent.getBroadcast(app, claim.device.deviceId, intent, flags),
        )
        // Permission broadcasts are unreliable on a few DJI Android 7 images. These scans are
        // bounded fallbacks; normal attach/permission broadcasts still take the fast path.
        mainHandler.postDelayed({ scan() }, 1_500L)
        mainHandler.postDelayed({ scan() }, 4_000L)
    }

    private fun makeReady(claim: Claim) {
        if (claim.ready) return
        val usb = manager ?: return
        claim.ready = true
        claim.adapter.onReady(claim.device, claim.descriptor, usb)
    }

    private fun detach(device: UsbDevice) {
        permissionPending.remove(device.deviceName)
        claims.remove(device.deviceName)?.let { it.adapter.onDetached(it.descriptor) }
    }

    private fun UsbDevice.descriptor(generation: Long) = TeleautoUsbDeviceDescriptor(
        deviceName = deviceName,
        deviceId = deviceId,
        vendorId = vendorId,
        productId = productId,
        interfaceClasses = (0 until interfaceCount).map { getInterface(it).interfaceClass }.toSet(),
        generation = generation,
    )

    @Suppress("DEPRECATION")
    private fun Intent.usbDevice(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else getParcelableExtra(UsbManager.EXTRA_DEVICE)

    fun isCdcAcm(device: UsbDevice): Boolean = (0 until device.interfaceCount).any { index ->
        val intf = device.getInterface(index)
        intf.interfaceClass == UsbConstants.USB_CLASS_COMM ||
            (intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA)
    }

    private const val PERIODIC_SCAN_MS = 2_000L
}
