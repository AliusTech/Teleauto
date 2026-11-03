package tech.alius.rc.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

data class TeleautoCyberBrickReceiver(val address: String, val name: String, val rssi: Int)
data class TeleautoCyberBrickConnection(
    val scanning: Boolean = false,
    val receivers: List<TeleautoCyberBrickReceiver> = emptyList(),
    val selectedAddress: String? = null,
    val selectedName: String? = null,
    val message: String = "搜索附近接收端",
    val controlReady: Boolean = false,
)
interface TeleautoCyberBrickReceiverDevice : TeleautoDevice {
    fun scanReceivers()
    fun selectReceiver(address: String)
    fun cancelScan()
}

/** BLE central transport only. No invented control UUIDs or writes to unknown characteristics. */
@SuppressLint("MissingPermission")
internal class TeleautoCyberBrickDevice(
    private val context: Context,
    executor: Executor,
    onChange: (TeleautoMutableDevice) -> Unit,
) : TeleautoMutableDevice(TeleautoDeviceId("cyberbrick:receiver"), TeleautoDeviceType.CYBERBRICK, TeleautoDeviceConnectionMode.MANUAL,
    TeleautoDeviceInfo("CyberBrick"), executor, onChange), TeleautoCyberBrickReceiverDevice {
    private val handler = Handler(Looper.getMainLooper())
    private val adapter get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var gatt: BluetoothGatt? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var generation = 0
    private var scanGeneration = 0
    private var scanTimeout: Runnable? = null
    private var connectionTimeout: Runnable? = null
    private var state = TeleautoCyberBrickConnection()
    init { publish() }
    private fun publish() { update(snapshot.copy(cyberBrick = state)) }
    private fun fail(message: String) {
        state = state.copy(scanning = false, controlReady = false, message = message)
        updateConnection(TeleautoDeviceConnectionState.ERROR, TeleautoDeviceConnectionError.Protocol(message)); publish()
    }
    private fun permitted(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        return permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
    override fun connect() {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
        if (state.selectedAddress == null) scanReceivers() else connectSelected()
    }
    override fun cancelScan() {
        scanGeneration++
        scanTimeout?.let(handler::removeCallbacks); scanTimeout = null
        scanCallback?.let { callback -> runCatching { scanner?.stopScan(callback) } }
        scanCallback = null; scanner = null
        state = state.copy(scanning = false); publish()
    }
    override fun scanReceivers() {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
        if (gatt != null) return
        cancelScan()
        if (!permitted()) { fail("请授予蓝牙扫描权限"); return }
        val bluetooth = adapter
        if (bluetooth?.isEnabled != true) { fail("请开启蓝牙"); return }
        if (Build.VERSION.SDK_INT < 31 && !(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            fail("请开启系统位置信息后搜索"); return
        }
        val token = ++scanGeneration
        state = state.copy(scanning = true, receivers = emptyList(), selectedAddress = null, selectedName = null, message = "正在搜索接收端…")
        updateConnection(TeleautoDeviceConnectionState.CREATED); publish()
        val callback = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) { handler.post {
                if (token != scanGeneration || !state.scanning) return@post
                val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: "未命名蓝牙设备"
                // The stock receiver's service/name signature is not verified yet. Let the user
                // explicitly identify the receiver; do not silently hide renamed peripherals.
                val receiver = TeleautoCyberBrickReceiver(result.device.address, name, result.rssi)
                state = state.copy(receivers = (state.receivers.filterNot { it.address == receiver.address } + receiver).sortedByDescending { it.rssi }, message = "请选择你的 CyberBrick 接收端")
                publish()
            } }
            override fun onScanFailed(errorCode: Int) { handler.post {
                if (token == scanGeneration) { cancelScan(); fail("搜索失败，请重试（$errorCode）") }
            } }
        }
        scanCallback = callback
        try {
            scanner = bluetooth.bluetoothLeScanner ?: run { fail("蓝牙扫描不可用"); return }
            scanner!!.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            scanTimeout = Runnable {
                if (token == scanGeneration) {
                    cancelScan()
                    state = state.copy(message = if (state.receivers.isEmpty()) "未发现接收端，请确认设备已开机" else "请选择你的 CyberBrick 接收端")
                    publish()
                }
            }.also { handler.postDelayed(it, 12_000L) }
        } catch (e: Exception) { cancelScan(); fail("无法搜索，请检查蓝牙和权限") }
    }
    override fun selectReceiver(address: String) {
        val receiver = state.receivers.firstOrNull { it.address == address } ?: return
        cancelScan()
        state = state.copy(selectedAddress = receiver.address, selectedName = receiver.name)
        publish()
    }
    private fun connectSelected() {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
        if (gatt != null) return
        if (!permitted()) { fail("请授予蓝牙连接权限"); return }
        val address = state.selectedAddress ?: return
        cancelScan()
        val token = ++generation
        state = state.copy(message = "正在连接…", controlReady = false)
        updateConnection(TeleautoDeviceConnectionState.CONNECTING); publish()
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(client: BluetoothGatt, status: Int, newState: Int) { handler.post {
                if (token != generation || gatt !== client) { client.close(); return@post }
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    closeGatt()
                    state = state.copy(message = "连接已断开", controlReady = false)
                    updateConnection(TeleautoDeviceConnectionState.DISCONNECTED); publish()
                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (!client.discoverServices()) { closeGatt(); fail("无法识别接收端服务，请重试") }
                }
            } }
            override fun onServicesDiscovered(client: BluetoothGatt, status: Int) { handler.post {
                if (token != generation || gatt !== client) return@post
                connectionTimeout?.let(handler::removeCallbacks); connectionTimeout = null
                if (status != BluetoothGatt.GATT_SUCCESS || client.services.isEmpty()) { closeGatt(); fail("接收端服务读取失败"); return@post }
                // Transport is connected; application control readiness must be verified separately.
                state = state.copy(message = "蓝牙已连接 · 暂不可控制", controlReady = false)
                updateConnection(TeleautoDeviceConnectionState.CONNECTED); publish()
            } }
        }
        try {
            gatt = adapter?.getRemoteDevice(address)?.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) { fail("无法建立蓝牙连接"); return }
            connectionTimeout = Runnable { if (token == generation && snapshot.connectionState == TeleautoDeviceConnectionState.CONNECTING) { closeGatt(); fail("连接超时，请重试") } }
                .also { handler.postDelayed(it, 15_000L) }
        } catch (e: Exception) { closeGatt(); fail("连接失败，请检查接收端") }
    }
    private fun closeGatt() {
        generation++
        connectionTimeout?.let(handler::removeCallbacks); connectionTimeout = null
        val previous = gatt; gatt = null
        runCatching { previous?.disconnect() }; runCatching { previous?.close() }
    }
    override fun disconnect() {
        cancelScan(); closeGatt()
        state = state.copy(message = "连接已断开", controlReady = false)
        super.disconnect(); publish()
    }
}
