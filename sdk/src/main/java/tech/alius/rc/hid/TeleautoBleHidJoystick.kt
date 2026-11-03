package tech.alius.rc.hid

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID
import tech.alius.rc.TeleautoRemoteControllerStatusManager

/**
 * 纯应用层 BLE HID over GATT(HOGP)手柄传输。
 *
 * DJI 遥控器的系统镜像关闭了 Android HID Device profile(profile_supported_hid_device
 * 被 /product overlay 置 false 且不可变更),经典 BluetoothHidDevice API 无法注册;
 * 本类直接以 GATT Server 外设角色暴露标准 HID 服务,Mac/Windows 搜索配对后即挂载为
 * 系统 joystick。实现自 alius_rc 项目真机验证版本移植。
 *
 * 流程:启动广播 → 主机连接并完成 LE bonding → 订阅输入报告 →
 * [reportProvider] 轮询(10ms)经 notifyCharacteristicChanged 下发报告。
 *
 * @param deviceName 对外蓝牙名称(机型名);同时写入 GAP Device Name
 * @param reportProvider 取当前输入报告;null 视为中位
 * @param onConnectionState 主机就绪(已配对且订阅)/等待/断开回调
 * @param onLog 过程日志回调
 */
class TeleautoBleHidJoystick(
    private val context: Context,
    val deviceName: String,
    private val deviceKind: TeleautoHidJoystickDescriptor.DeviceKind = TeleautoHidJoystickDescriptor.DeviceKind.GAMEPAD,
    private val reportProvider: () -> TeleautoHidJoystickReport?,
    private val onConnectionState: (connected: Boolean, address: String?, detail: String?) -> Unit = { _, _, _ -> },
    private val onLog: (String) -> Unit = {},
) {
    enum class State { IDLE, ADVERTISING, PAIRING, CONNECTED }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val bluetoothManager: BluetoothManager?
        get() = context.getSystemService(BluetoothManager::class.java)

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 报告循环专用线程:100Hz GATT IPC 不上主线程 */
    private val reportLoopLock = Any()
    private var reportHandlerThread: HandlerThread? = null
    private var reportHandler: Handler? = null

    private var gattServer: BluetoothGattServer? = null
    private var inputReportCharacteristic: BluetoothGattCharacteristic? = null
    private var outputReportCharacteristic: BluetoothGattCharacteristic? = null
    private var connectedDevice: BluetoothDevice? = null
    private var subscribedDevice: BluetoothDevice? = null
    private var serviceQueue = ArrayDeque<BluetoothGattService>()
    private var started = false
    private var advertising = false
    private var servicesReady = false
    private var bondReceiverRegistered = false
    private var hostReadyNotified = false
    private var communicationReadyNotified = false
    private var genericAccessSubmitted = false
    private var lastSentReport: TeleautoHidJoystickReport? = null
    private val neutralReport = if (deviceKind == TeleautoHidJoystickDescriptor.DeviceKind.JOYSTICK) TeleautoHidJoystickReport.NEUTRAL.copy(leftTrigger = 0x80.toByte(), rightTrigger = 0x80.toByte()) else TeleautoHidJoystickReport.NEUTRAL
    private val payloadBuffer = ByteArray(10) // 100Hz 循环复用,避免每帧分配
    private var sendFailureLogged = false

    /** 用户主动 stop 后不再自动重连 */
    private var userStopped = true
    private var reconnectAttempt = 0
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @get:SuppressLint("MissingPermission")
    val state: State
        get() = when {
            isConnected -> State.CONNECTED
            started && connectedDevice != null -> State.PAIRING
            advertising -> State.ADVERTISING
            started -> State.ADVERTISING
            else -> State.IDLE
        }

    /** Current host label, never a stale bonded-device entry. */
    val hostName: String?
        @SuppressLint("MissingPermission") get() = runCatching {
            connectedDevice?.let { it.name?.takeIf(String::isNotBlank) ?: "PC" }
        }.getOrNull()

    @get:SuppressLint("MissingPermission")
    val isConnected: Boolean
        get() = started && connectedDevice?.bondState == BluetoothDevice.BOND_BONDED &&
            subscribedDevice?.address == connectedDevice?.address

    private val reconnectRunnable = Runnable {
        if (userStopped) return@Runnable
        if (reconnectAttempt >= RECONNECT_MAX_ATTEMPTS) {
            emit("BLE HID reconnect gave up after $RECONNECT_MAX_ATTEMPTS attempts")
            return@Runnable
        }
        reconnectAttempt++
        emit("BLE HID reconnect attempt $reconnectAttempt")
        start()
    }

    /**
     * 订阅看门狗:主机完成配对后若迟迟不订阅输入报告,只记录状态。
     * 不自动重建 GATT,避免 macOS/Chrome 弹出多次蓝牙连接提示。
     */
    private var watchdogRestarts = 0
    private val subscriptionWatchdog = Runnable {
        if (userStopped || !started) return@Runnable
        val device = connectedDevice ?: return@Runnable
        if (subscribedDevice == null) {
            val bondState = device.bondState
            if (bondState != BluetoothDevice.BOND_BONDED) {
                emit("BLE HID subscription watchdog waiting for bond (${bondStateLabel(bondState)})")
                scheduleSubscriptionWatchdog(device.address)
                return@Runnable
            }
            if (watchdogRestarts >= WATCHDOG_MAX_RESTARTS) {
                emit("BLE HID subscription watchdog observed no Input Report subscription; staying connected")
                return@Runnable
            }
            watchdogRestarts++
            restartGattSession("host connected but no HID subscription (restart #$watchdogRestarts)")
        }
    }

    private fun scheduleSubscriptionWatchdog(address: String) {
        mainHandler.removeCallbacks(subscriptionWatchdog)
        mainHandler.postDelayed(subscriptionWatchdog, SUBSCRIPTION_WATCHDOG_MS)
        emit("BLE HID subscription watchdog armed for $address (${SUBSCRIPTION_WATCHDOG_MS}ms)")
    }

    /** 重建 GATT 会话(不改变 userStopped 语义):等效于外设侧"拔插" */
    @SuppressLint("MissingPermission")
    private fun restartGattSession(reason: String) {
        emit("BLE HID restarting GATT session: $reason")
        stopReportLoop()
        bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        advertising = false
        gattServer?.close()
        gattServer = null
        inputReportCharacteristic = null
        outputReportCharacteristic = null
        connectedDevice = null
        subscribedDevice = null
        serviceQueue.clear()
        servicesReady = false
        hostReadyNotified = false
        communicationReadyNotified = false
        genericAccessSubmitted = false
        lastSentReport = null
        val server = bluetoothManager?.openGattServer(context, gattCallback)
        if (server == null) {
            emit("BLE HID GATT server reopen failed")
            scheduleReconnect()
            return
        }
        gattServer = server
        buildServices()
        addNextServiceOrAdvertise()
        startReportLoop()
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            advertising = true
            emit("BLE HID advertising started: $deviceName")
        }

        override fun onStartFailure(errorCode: Int) {
            advertising = false
            emit("BLE HID advertising failed: $errorCode")
            scheduleReconnect()
        }
    }

    private val bondStateReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_PAIRING_REQUEST -> handlePairingRequest(intent)
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    val previousState =
                        intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                    val address = device?.address ?: "unknown"
                    emit("BLE HID bond state: $address ${bondStateLabel(previousState)} -> ${bondStateLabel(state)}")
                    // BONDING -> NONE 表示配对被拒(主机不会挂载 HID 设备)
                    if (previousState == BluetoothDevice.BOND_BONDING && state == BluetoothDevice.BOND_NONE) {
                        emit("BLE HID pairing FAILED: $address bonding rejected")
                        onConnectionState(false, address, "配对失败,请在电脑端删除设备后重试")
                    }
                    if (address == connectedDevice?.address) {
                        when (state) {
                            BluetoothDevice.BOND_BONDING ->
                                onConnectionState(false, address, "电脑已发现设备,正在进行蓝牙配对")
                            BluetoothDevice.BOND_BONDED -> {
                                rememberHost(address)
                                onConnectionState(false, address, "蓝牙配对成功,正在建立系统手柄通道")
                                scheduleSubscriptionWatchdog(address)
                                notifyHostReadyIfSubscribed("bonded")
                            }
                        }
                    }
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (service?.uuid == UUID_GENERIC_ACCESS_SERVICE) {
                emit(
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        "BLE GAP appearance service added: Gamepad"
                    } else {
                        "BLE GAP appearance service add failed: status=$status"
                    },
                )
                return
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                emit("BLE HID service add failed: ${uuidLabel(service?.uuid)} status=$status")
                if (service?.uuid != UUID_HID_SERVICE) {
                    addNextServiceOrAdvertise()
                }
                return
            }
            emit("BLE HID service added: ${uuidLabel(service?.uuid)}")
            addNextServiceOrAdvertise()
        }

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            if (userStopped || !started) return
            val address = device?.address ?: "unknown"
            // status 非 SUCCESS 视作断开(0x05 认证失败 / 0x13 远端断开 / 0x3D MIC 失败等)
            val effectiveState = if (status != BluetoothGatt.GATT_SUCCESS) {
                emit("BLE HID connection failed: $address status=0x${status.toString(16)} newState=$newState")
                BluetoothProfile.STATE_DISCONNECTED
            } else {
                newState
            }
            when (effectiveState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    reconnectAttempt = 0
                    hostReadyNotified = false
                    communicationReadyNotified = false
                    if (device?.bondState == BluetoothDevice.BOND_BONDED) rememberHost(address)
                    emit("BLE HID host connected: $address bond=${bondStateLabel(device?.bondState)}")
                    if (!servicesReady) {
                        emit("BLE HID host connected before services were ready; deferring enumeration refresh")
                        onConnectionState(false, address, "电脑已连接,等待 BLE HID 服务完成初始化")
                        return
                    }
                    onConnectionState(
                        false,
                        address,
                        if (device?.bondState == BluetoothDevice.BOND_BONDED) {
                            "主机已配对,等待系统订阅 Input Report"
                        } else {
                            "主机已连接,等待 HID 枚举或系统订阅 Input Report"
                        },
                    )
                    scheduleSubscriptionWatchdog(address)
                    notifyHostReadyIfSubscribed("connected")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    mainHandler.removeCallbacks(subscriptionWatchdog)
                    if (device == connectedDevice) connectedDevice = null
                    if (device == subscribedDevice) {
                        subscribedDevice = null
                        lastSentReport = null
                    }
                    hostReadyNotified = false
                    communicationReadyNotified = false
                    emit("BLE HID host disconnected: $address")
                    onConnectionState(
                        false,
                        address,
                        if (device?.bondState == BluetoothDevice.BOND_BONDED) {
                            "已配对电脑断开,正在广播并等待自动回连"
                        } else {
                            "电脑已断开,正在等待重新配对"
                        },
                    )
                    scheduleAdvertisingRestart("host disconnected")
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?,
        ) {
            emit("BLE HID characteristic read: ${uuidLabel(characteristic?.uuid)} offset=$offset")
            if (characteristic?.uuid == UUID_BATTERY_LEVEL) {
                @Suppress("DEPRECATION")
                characteristic.value = byteArrayOf(currentBatteryLevel().toByte())
                if (device?.address == connectedDevice?.address) {
                    emit("BLE HID battery level read confirmed by host: ${currentBatteryLevel()}%")
                    notifyHostReadyIfSubscribed("battery read")
                }
            }
            @Suppress("DEPRECATION")
            sendGattResponse(device, requestId, offset, characteristic?.value ?: byteArrayOf())
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor?,
        ) {
            emit("BLE HID descriptor read: ${uuidLabel(descriptor?.uuid)} offset=$offset")
            @Suppress("DEPRECATION")
            sendGattResponse(device, requestId, offset, descriptor?.value ?: byteArrayOf())
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (characteristic?.uuid == UUID_HID_CONTROL_POINT && value?.isNotEmpty() == true) {
                emit("BLE HID control point write: ${value[0].toInt() and 0xFF}")
            }
            // dual-rumble 输出报告:遥控器不做主机驱动的震动,静默忽略
            if (characteristic == outputReportCharacteristic && value != null && value.isNotEmpty()) {
                emit("BLE HID rumble report ignored")
            }
            if (responseNeeded && device != null) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            val notifyEnabled =
                value?.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == true
            if (descriptor?.uuid == UUID_CLIENT_CHARACTERISTIC_CONFIG) {
                @Suppress("DEPRECATION")
                descriptor.value = value
                subscribedDevice = if (notifyEnabled) device else null
                val address = device?.address ?: "unknown"
                emit("BLE HID input report notifications ${if (notifyEnabled) "enabled" else "disabled"} for $address")
                if (notifyEnabled) {
                    mainHandler.removeCallbacks(subscriptionWatchdog)
                    watchdogRestarts = 0
                    notifyHostReadyIfSubscribed("notifications enabled")
                } else {
                    hostReadyNotified = false
                    communicationReadyNotified = false
                    lastSentReport = null
                    onConnectionState(false, address, "系统 Input Report 订阅已关闭")
                    if (connectedDevice != null) scheduleSubscriptionWatchdog(address)
                }
            }
            if (responseNeeded && device != null) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return false
        if (!hasBlePermissions()) {
            emit("BLE HID unavailable: Bluetooth permission missing")
            return false
        }
        val adapter = bluetoothAdapter
        if (adapter == null) {
            emit("BLE HID unavailable: no Bluetooth adapter")
            return false
        }
        if (!adapter.isEnabled) {
            // 使能等待最长 5s,不能在主线程阻塞(ANR);转后台执行后再继续启动
            Thread {
                val ok = tryEnableAdapter()
                mainHandler.post {
                    if (!ok) emit("BLE HID unavailable: Bluetooth disabled")
                    else if (!userStopped) startAfterAdapterReady()
                }
            }.start()
            return true
        }
        startAfterAdapterReady()
        return true
    }

    /** 蓝牙可用后的实际启动序列(权限/广播能力检查 + 会话建立)。 */
    private fun startAfterAdapterReady(): Boolean {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return false
        val adapter = bluetoothAdapter ?: run {
            emit("BLE HID unavailable: no Bluetooth adapter")
            return false
        }
        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null || !adapter.isMultipleAdvertisementSupported) {
            emit("BLE HID unavailable: advertiser not supported")
            return false
        }

        stop()
        userStopped = false
        registerBondReceiver()
        runCatching { adapter.name = deviceName }
        val server = bluetoothManager?.openGattServer(context, gattCallback)
        if (server == null) {
            emit("BLE HID unavailable: GATT server open failed")
            return false
        }
        gattServer = server
        started = true
        buildServices()
        emit("BLE HID GATT server starting")
        addNextServiceOrAdvertise()
        startReportLoop()
        knownBondedHost()?.let {
            onConnectionState(false, it.address, "发现已配对电脑,正在广播并尝试自动回连")
        }
        return true
    }

    @SuppressLint("MissingPermission")
    /**
     * 移除与当前主机的配对(反射 removeBond)。
     * 用于主机端缓存了旧 HID 描述符/密钥异常时强制重新配对;
     * 移除后需在电脑端重新点击连接。
     */
    fun removeBond(): Boolean {
        val device = connectedDevice ?: knownBondedHost() ?: return false
        return try {
            val m = BluetoothDevice::class.java.getMethod("removeBond")
            m.isAccessible = true
            val ok = (m.invoke(device) as? Boolean) == true
            emit("BLE HID removeBond(${device.address}) = $ok")
            ok
        } catch (e: Exception) {
            Log.w(TAG, "removeBond failed", e)
            false
        }
    }

    fun stop() {
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.removeCallbacks(subscriptionWatchdog)
        watchdogRestarts = 0
        userStopped = true
        reconnectAttempt = 0
        stopReportLoop()
        bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        connectedDevice?.let { device ->
            runCatching { gattServer?.cancelConnection(device) }
        }
        gattServer?.close()
        gattServer = null
        inputReportCharacteristic = null
        outputReportCharacteristic = null
        connectedDevice = null
        subscribedDevice = null
        serviceQueue.clear()
        started = false
        advertising = false
        servicesReady = false
        hostReadyNotified = false
        communicationReadyNotified = false
        genericAccessSubmitted = false
        lastSentReport = null
        sendFailureLogged = false
        unregisterBondReceiver()
    }

    private fun tryEnableAdapter(): Boolean {
        val a = bluetoothAdapter ?: return false
        try {
            @Suppress("DEPRECATION")
            if (!a.enable()) return false
        } catch (e: Exception) {
            Log.w(TAG, "enable() failed", e)
            return false
        }
        var waited = 0L
        while (!a.isEnabled && waited < 5000) {
            Thread.sleep(200)
            waited += 200
        }
        return a.isEnabled
    }

    private fun scheduleReconnect() {
        if (userStopped) return
        if (reconnectAttempt >= RECONNECT_MAX_ATTEMPTS) return
        mainHandler.removeCallbacks(reconnectRunnable)
        val delay = nextReconnectDelayMs()
        emit("BLE HID scheduling reconnect in ${delay}ms (attempt ${reconnectAttempt + 1}/$RECONNECT_MAX_ATTEMPTS)")
        mainHandler.postDelayed(reconnectRunnable, delay)
    }

    private fun nextReconnectDelayMs(): Long {
        val raw = RECONNECT_BASE_DELAY_MS shl reconnectAttempt.coerceAtMost(RECONNECT_SHIFT_CAP)
        return raw.coerceAtMost(RECONNECT_MAX_DELAY_MS)
    }

    @SuppressLint("MissingPermission")
    private fun addNextServiceOrAdvertise() {
        val server = gattServer ?: return
        val next = serviceQueue.removeFirstOrNull()
        if (next != null) {
            if (!server.addService(next)) {
                emit("BLE HID service add rejected: ${uuidLabel(next.uuid)}")
                if (next.uuid != UUID_HID_SERVICE) {
                    addNextServiceOrAdvertise()
                }
            }
            return
        }
        servicesReady = true
        val earlyConnection = connectedDevice
        if (earlyConnection != null) {
            // 已配对的 macOS 可能在异步服务表创建完成前自动回连。该连接看不到完整
            // HID 数据库，也不会读取 Battery/订阅 Input Report；服务就绪后断开一次，
            // 让 macOS 在完整服务表上自动重新枚举。
            emit("BLE HID services ready; restarting early host ${earlyConnection.address} for full enumeration")
            onConnectionState(
                false,
                earlyConnection.address,
                "BLE HID 服务已就绪,正在重新连接以完成电量与输入枚举",
            )
            connectedDevice = null
            server.cancelConnection(earlyConnection)
            scheduleAdvertisingRestart("restart early host after services ready")
            return
        }
        startAdvertising()
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
        val adapter = bluetoothAdapter ?: return
        val settingsBuilder = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
        enableBleDiscoverableIfAvailable(settingsBuilder)
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(UUID_HID_SERVICE))
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()
        adapter.bluetoothLeAdvertiser?.startAdvertising(settingsBuilder.build(), data, scanResponse, advertiseCallback)
            ?: emit("BLE HID advertising failed: advertiser unavailable")
        submitOptionalGenericAccessService()
    }

    @SuppressLint("MissingPermission")
    private fun scheduleAdvertisingRestart(reason: String) {
        if (userStopped || !started) return
        emit("BLE HID refreshing advertising: $reason")
        bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        advertising = false
        mainHandler.postDelayed({
            if (!userStopped && started && connectedDevice == null) startAdvertising()
        }, ADVERTISING_RESTART_DELAY_MS)
    }

    private fun buildServices() {
        serviceQueue = ArrayDeque<BluetoothGattService>().apply {
            add(deviceInformationService())
            add(batteryService())
            add(hidService())
        }
    }

    private fun submitOptionalGenericAccessService() {
        val server = gattServer ?: return
        if (genericAccessSubmitted) return
        genericAccessSubmitted = true
        val accepted = server.addService(genericAccessService())
        emit("BLE GAP appearance service submit ${if (accepted) "accepted" else "rejected"}")
    }

    private fun genericAccessService(): BluetoothGattService {
        val service = BluetoothGattService(
            UUID_GENERIC_ACCESS_SERVICE,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )
        service.addCharacteristic(readCharacteristic(UUID_DEVICE_NAME, deviceName.encodeToByteArray()))
        // GAP Appearance 与 Report Map 保持一致，避免主机把 Joystick 缓存成 Gamepad。
        val appearance = deviceKind.appearance
        service.addCharacteristic(readCharacteristic(UUID_APPEARANCE, byteArrayOf(appearance.toByte(), (appearance shr 8).toByte())))
        return service
    }

    private fun deviceInformationService(): BluetoothGattService {
        val service = BluetoothGattService(
            UUID_DEVICE_INFORMATION_SERVICE,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )
        service.addCharacteristic(readCharacteristic(UUID_MANUFACTURER_NAME, "AliusTech".encodeToByteArray()))
        // PnP ID:VID 0x2CA3(DJI)小端
        service.addCharacteristic(
            readCharacteristic(
                UUID_PNP_ID,
                byteArrayOf(0x02.toByte(), 0xA3.toByte(), 0x2C.toByte(), 0x02.toByte(), 0x00.toByte(), 0x02.toByte(), 0x00.toByte()),
            ),
        )
        return service
    }

    private fun batteryService(): BluetoothGattService {
        val service = BluetoothGattService(UUID_BATTERY_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            readCharacteristic(UUID_BATTERY_LEVEL, byteArrayOf(currentBatteryLevel().toByte())),
        )
        return service
    }

    /** 读取遥控器系统电池百分比，供 BLE Battery Service 暴露给主机。 */
    private fun currentBatteryLevel(): Int =
        TeleautoRemoteControllerStatusManager.getBatteryState()?.levelPercent ?: 0

    private fun hidService(): BluetoothGattService {
        val service = BluetoothGattService(UUID_HID_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            hidReadCharacteristic(UUID_HID_INFORMATION, TeleautoHidJoystickDescriptor.bleHidInformation()),
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                UUID_REPORT_MAP,
                BluetoothGattCharacteristic.PROPERTY_READ,
                HID_READ_PERMISSION,
            ).apply {
                @Suppress("DEPRECATION")
                value = TeleautoHidJoystickDescriptor.bleReportMap(TeleautoHidJoystickDescriptor.REPORT_ID, deviceKind)
            },
        )
        inputReportCharacteristic = BluetoothGattCharacteristic(
            UUID_REPORT,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            HID_READ_PERMISSION,
        ).apply {
            @Suppress("DEPRECATION")
            value = neutralReport.toPayload()
            addDescriptor(
                BluetoothGattDescriptor(
                    UUID_CLIENT_CHARACTERISTIC_CONFIG,
                    HID_DESCRIPTOR_READ_PERMISSION or HID_DESCRIPTOR_WRITE_PERMISSION,
                ).apply {
                    @Suppress("DEPRECATION")
                    value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                },
            )
            addDescriptor(
                BluetoothGattDescriptor(
                    UUID_REPORT_REFERENCE,
                    HID_DESCRIPTOR_READ_PERMISSION,
                ).apply {
                    @Suppress("DEPRECATION")
                    value = byteArrayOf(TeleautoHidJoystickDescriptor.REPORT_ID, REPORT_TYPE_INPUT)
                },
            )
        }
        service.addCharacteristic(inputReportCharacteristic)
        outputReportCharacteristic = BluetoothGattCharacteristic(
            UUID_REPORT,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            HID_WRITE_PERMISSION,
        ).apply {
            addDescriptor(
                BluetoothGattDescriptor(
                    UUID_REPORT_REFERENCE,
                    HID_DESCRIPTOR_READ_PERMISSION,
                ).apply {
                    @Suppress("DEPRECATION")
                    value = byteArrayOf(TeleautoHidJoystickDescriptor.OUTPUT_REPORT_ID, REPORT_TYPE_OUTPUT)
                },
            )
        }
        service.addCharacteristic(outputReportCharacteristic)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                UUID_PROTOCOL_MODE,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                HID_READ_PERMISSION or HID_WRITE_PERMISSION,
            ).apply {
                @Suppress("DEPRECATION")
                value = byteArrayOf(PROTOCOL_MODE_REPORT)
            },
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                UUID_HID_CONTROL_POINT,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                HID_WRITE_PERMISSION,
            ),
        )
        return service
    }

    private fun readCharacteristic(uuid: UUID, valueBytes: ByteArray): BluetoothGattCharacteristic =
        BluetoothGattCharacteristic(
            uuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        ).apply {
            @Suppress("DEPRECATION")
            value = valueBytes
        }

    private fun hidReadCharacteristic(uuid: UUID, valueBytes: ByteArray): BluetoothGattCharacteristic =
        BluetoothGattCharacteristic(
            uuid,
            BluetoothGattCharacteristic.PROPERTY_READ,
            HID_READ_PERMISSION,
        ).apply {
            @Suppress("DEPRECATION")
            value = valueBytes
        }

    @SuppressLint("MissingPermission")
    private fun handlePairingRequest(intent: Intent) {
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        val address = device?.address ?: "unknown"
        val variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
        val key = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, -1)
        emit("BLE HID pairing request: $address variant=${pairingVariantLabel(variant)} key=$key")

        val confirmed = when (variant) {
            BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION -> {
                runCatching { device?.setPairingConfirmation(true) == true }.getOrDefault(false)
            }
            BluetoothDevice.PAIRING_VARIANT_PIN -> {
                // HOGP joystick pairing is normally Just Works or passkey confirmation. If
                // the stack asks for legacy PIN, try 000000 so pairing does not stall.
                runCatching { device?.setPin("000000".encodeToByteArray()) == true }.getOrDefault(false)
            }
            else -> {
                // Android exposes only PIN and passkey confirmation as public constants,
                // but vendors may send consent-style variants. setPairingConfirmation
                // is harmless when the current variant does not use it.
                runCatching { device?.setPairingConfirmation(true) == true }.getOrDefault(false)
            }
        }
        emit("BLE HID pairing request handled: $address confirmed=$confirmed")
    }

    private fun enableBleDiscoverableIfAvailable(builder: AdvertiseSettings.Builder) {
        // 部分 Android 版本可将 LE 广播标记为 discoverable;RC Plus 的 API 30 无此方法
        runCatching {
            AdvertiseSettings.Builder::class.java
                .getMethod("setDiscoverable", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
        }.onSuccess {
            emit("BLE HID advertising discoverable flag enabled")
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyHostReadyIfSubscribed(reason: String) {
        val device = connectedDevice ?: return
        val bondState = device.bondState
        if (bondState != BluetoothDevice.BOND_BONDED) {
            emit("BLE HID host not ready: waiting for encrypted bond")
            onConnectionState(false, device.address, "电脑已连接,等待蓝牙配对完成")
            return
        }
        if (subscribedDevice?.address != device.address) {
            emit("BLE HID host not ready: waiting for Input Report subscription")
            onConnectionState(false, device.address, "主机已连接,等待系统订阅 Input Report")
            return
        }
        // Battery reads are independent of the host's input subscription and may
        // be cached or skipped on reconnect. They must not gate HID readiness.
        if (hostReadyNotified) return
        hostReadyNotified = true
        emit("BLE HID joystick host ready: ${device.address} bond=${bondStateLabel(bondState)} ($reason)")
        onConnectionState(true, device.address, "蓝牙配对成功,系统手柄已连接,等待输入通信")
    }

    @SuppressLint("MissingPermission")
    private fun knownBondedHost(): BluetoothDevice? {
        val address = preferences.getString(PREF_LAST_HOST_ADDRESS, null) ?: return null
        return bluetoothAdapter?.bondedDevices?.firstOrNull { it.address == address }
    }

    private fun rememberHost(address: String) {
        preferences.edit().putString(PREF_LAST_HOST_ADDRESS, address).apply()
    }

    private fun registerBondReceiver() {
        if (bondReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            priority = 1000
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(bondStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(bondStateReceiver, filter)
        }
        bondReceiverRegistered = true
    }

    private fun unregisterBondReceiver() {
        if (!bondReceiverRegistered) return
        runCatching { context.unregisterReceiver(bondStateReceiver) }
        bondReceiverRegistered = false
    }

    private fun bondStateLabel(state: Int?): String = when (state) {
        BluetoothDevice.BOND_NONE -> "none"
        BluetoothDevice.BOND_BONDING -> "bonding"
        BluetoothDevice.BOND_BONDED -> "bonded"
        BluetoothDevice.ERROR -> "error"
        null -> "unknown"
        else -> "unknown($state)"
    }

    private fun pairingVariantLabel(variant: Int): String = when (variant) {
        BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION -> "passkey_confirmation"
        BluetoothDevice.PAIRING_VARIANT_PIN -> "pin"
        else -> "unknown($variant)"
    }

    private fun uuidLabel(uuid: UUID?): String = when (uuid) {
        UUID_GENERIC_ACCESS_SERVICE -> "Generic Access Service"
        UUID_DEVICE_INFORMATION_SERVICE -> "Device Information Service"
        UUID_BATTERY_SERVICE -> "Battery Service"
        UUID_HID_SERVICE -> "HID Service"
        UUID_HID_INFORMATION -> "HID Information"
        UUID_REPORT_MAP -> "Report Map"
        UUID_REPORT -> "Report"
        UUID_PROTOCOL_MODE -> "Protocol Mode"
        UUID_HID_CONTROL_POINT -> "HID Control Point"
        UUID_CLIENT_CHARACTERISTIC_CONFIG -> "Client Characteristic Configuration"
        UUID_REPORT_REFERENCE -> "Report Reference"
        UUID_PNP_ID -> "PnP ID"
        UUID_DEVICE_NAME -> "Device Name"
        UUID_APPEARANCE -> "Appearance"
        UUID_BATTERY_LEVEL -> "Battery Level"
        UUID_MANUFACTURER_NAME -> "Manufacturer Name"
        null -> "unknown"
        else -> uuid.toString()
    }

    @SuppressLint("MissingPermission")
    private fun sendLatestReport() {
        tech.alius.rc.model.TeleautoControlOutputGate.gate.runIfOpen {
            val report = reportProvider() ?: neutralReport
            sendReport(report)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun sendReport(report: TeleautoHidJoystickReport) {
        if (!isConnected) return
        val server = gattServer ?: return
        val targetDevice = subscribedDevice ?: return
        val characteristic = inputReportCharacteristic ?: return
        report.fillInto(payloadBuffer)
        characteristic.value = payloadBuffer.copyOf() // GATT 持有引用,copyOf(9B) 远轻于整对象分配
        val accepted = server.notifyCharacteristicChanged(targetDevice, characteristic, false)
        if (accepted) {
            if (!communicationReadyNotified && isConnected) {
                communicationReadyNotified = true
                onConnectionState(
                    true,
                    targetDevice.address,
                    "蓝牙配对成功 · 电脑已连接 · Input Report 通信正常",
                )
            }
            if (report != lastSentReport) {
                lastSentReport = report
                emit(
                    "BLE HID report changed: " +
                        payloadBuffer.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) },
                )
            }
            sendFailureLogged = false
        } else if (!sendFailureLogged) {
            sendFailureLogged = true
            emit("BLE HID input report notify failed")
            onConnectionState(false, targetDevice.address, "Input Report 通知发送失败,请重新连接蓝牙手柄")
        }
    }

    private fun startReportLoop() = synchronized(reportLoopLock) {
        stopReportLoop()
        val thread = HandlerThread("TeleautoSdk-HidReportLoop").apply { start() }
        val handler = Handler(thread.looper)
        reportHandlerThread = thread
        reportHandler = handler
        handler.post(object : Runnable {
            override fun run() {
                // Never wait for the output gate while owning reportLoopLock:
                // an input observer may change modes and stop this session.
                tech.alius.rc.model.TeleautoControlOutputGate.gate.runIfOpen {
                    val current = synchronized(reportLoopLock) { reportHandler === handler }
                    if (current && started) sendLatestReport()
                }
                synchronized(reportLoopLock) {
                    if (reportHandler === handler) handler.postDelayed(this, REPORT_INTERVAL_MS)
                }
            }
        })
        emit("BLE HID report loop started")
    }

    private fun stopReportLoop() = synchronized(reportLoopLock) {
        reportHandler?.removeCallbacksAndMessages(null)
        reportHandler = null
        reportHandlerThread?.quitSafely()
        reportHandlerThread = null
    }

    private fun sendGattResponse(device: BluetoothDevice?, requestId: Int, offset: Int, value: ByteArray) {
        val response = if (offset in 0..value.size) value.copyOfRange(offset, value.size) else byteArrayOf()
        if (device != null) {
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, response)
        }
    }

    private fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun emit(message: String) {
        Log.d(TAG, message)
        onLog(message)
    }

    companion object {
        private const val TAG = "TeleautoSdk-BleHid"
        private const val REPORT_INTERVAL_MS = 10L // 100Hz 报告循环
        private const val ADVERTISING_RESTART_DELAY_MS = 500L
        private const val PREFS_NAME = "ble_hid_transport"
        private const val PREF_LAST_HOST_ADDRESS = "last_host_address"

        // 退避重连:1s → 2s → 4s → 8s → 16s → 30s,最多 6 次
        private const val RECONNECT_BASE_DELAY_MS = 1000L
        private const val RECONNECT_MAX_DELAY_MS = 30_000L
        private const val RECONNECT_MAX_ATTEMPTS = 6
        private const val RECONNECT_SHIFT_CAP = 5

        // 订阅看门狗:配对后 60s 无订阅只记录状态,不自动重连,避免重复蓝牙连接提示。
        private const val SUBSCRIPTION_WATCHDOG_MS = 60_000L
        private const val WATCHDOG_MAX_RESTARTS = 0

        private const val REPORT_TYPE_INPUT: Byte = 0x01
        private const val REPORT_TYPE_OUTPUT: Byte = 0x02
        private const val PROTOCOL_MODE_REPORT: Byte = 0x01

        private const val HID_READ_PERMISSION = BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
        private const val HID_WRITE_PERMISSION = BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
        private const val HID_DESCRIPTOR_READ_PERMISSION = BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED
        private const val HID_DESCRIPTOR_WRITE_PERMISSION = BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED

        private val UUID_GENERIC_ACCESS_SERVICE = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        private val UUID_DEVICE_NAME = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
        private val UUID_APPEARANCE = UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb")
        private val UUID_DEVICE_INFORMATION_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        private val UUID_MANUFACTURER_NAME = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
        private val UUID_PNP_ID = UUID.fromString("00002a50-0000-1000-8000-00805f9b34fb")
        private val UUID_BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val UUID_BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        private val UUID_HID_SERVICE = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")
        private val UUID_HID_INFORMATION = UUID.fromString("00002a4a-0000-1000-8000-00805f9b34fb")
        private val UUID_REPORT_MAP = UUID.fromString("00002a4b-0000-1000-8000-00805f9b34fb")
        private val UUID_HID_CONTROL_POINT = UUID.fromString("00002a4c-0000-1000-8000-00805f9b34fb")
        private val UUID_REPORT = UUID.fromString("00002a4d-0000-1000-8000-00805f9b34fb")
        private val UUID_PROTOCOL_MODE = UUID.fromString("00002a4e-0000-1000-8000-00805f9b34fb")
        private val UUID_CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val UUID_REPORT_REFERENCE = UUID.fromString("00002908-0000-1000-8000-00805f9b34fb")
    }
}
