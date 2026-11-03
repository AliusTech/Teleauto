package tech.alius.rc

import android.content.Context
import tech.alius.rc.model.TeleautoRemoteControllerInfo
import tech.alius.rc.device.TeleautoDevice
import tech.alius.rc.device.TeleautoDeviceId
import tech.alius.rc.device.TeleautoDeviceManager
import tech.alius.rc.device.TeleautoDeviceSnapshot
import tech.alius.rc.device.TeleautoDeviceType
import tech.alius.rc.device.TeleautoDeviceListObserver
import tech.alius.rc.device.TeleautoDeviceSubscription
import tech.alius.rc.device.TeleautoTargetDeviceConfig
import tech.alius.rc.usb.TeleautoUsbDeviceRegistry

/**
 * DJI 遥控器 SDK 总门面:负责生命周期、设备信息与版本。
 * 能力按组件拆分——摇杆/按键输入见 [TeleautoJoystickManager],
 * 后续射频等 manager 以平级组件加入。
 */
object TeleautoSdk {

    /** SDK 版本号。默认来自仓库 VERSION，发布任务可通过 -PsdkVersion 覆盖。 */
    val VERSION: String get() = BuildConfig.VERSION

    val isInitialized: Boolean get() = TeleautoJoystickManager.isInitialized

    // ── 生命周期(由门面统一驱动,组件不直接暴露)──────
    @Synchronized
    fun initialize(context: Context, cfg: TeleautoConfig = TeleautoConfig()): Boolean {
        release()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.initialize(context, cfg.licenseVendorId,
            listener = { status -> handler.post { runCatching { cfg.callbackExecutor.execute { runCatching { cfg.licenseListener?.onLicenseStatusChanged(status) } } } } },
            invalidated = { generation -> handler.post { releaseInvalidLicense(generation) } },
        )) return false
        tech.alius.rc.model.TeleautoControlOutputGate.gate.invalidate()
        TeleautoTransmitModuleManager.initialize(cfg)
        TeleautoRemoteControllerStatusManager.initialize(context, cfg.callbackExecutor)
        tech.alius.rc.video.TeleautoVideoTransmissionManager.initialize(context, cfg)
        TeleautoUsbDeviceRegistry.initialize(
            context.applicationContext,
            listOfNotNull(
                tech.alius.rc.video.TeleautoVideoTransmissionManager.usbAdapter(),
                TeleautoTransmitModuleManager.usbAdapter(),
            ),
        )
        val initialized = TeleautoJoystickManager.initialize(context, cfg)
        TeleautoDeviceManager.initialize(context, cfg.callbackExecutor)
        TeleautoRuntimeEnvironmentManager.initialize(
            context.applicationContext,
            TeleautoJoystickManager.getDeviceInfo()?.hardwareProfile,
            cfg.callbackExecutor,
        )
        TeleautoRemoteControlStatusManager.initialize(cfg.callbackExecutor)
        return initialized
    }

    @Synchronized
    private fun releaseInvalidLicense(generation: Long) {
        if (tech.alius.rc.license.TeleautoLicenseRuntime.generation != generation) return
        releaseResources(preserveLicenseFailure = true)
    }

    @Synchronized
    fun release() = releaseResources(preserveLicenseFailure = false)

    private fun releaseResources(preserveLicenseFailure: Boolean) {
        tech.alius.rc.license.TeleautoLicenseRuntime.stop(preserveLicenseFailure)
        tech.alius.rc.model.TeleautoControlOutputGate.gate.invalidate()
        TeleautoRemoteControlStatusManager.release()
        TeleautoRuntimeEnvironmentManager.release()
        TeleautoDeviceManager.release()
        TeleautoJoystickManager.release()
        TeleautoUsbDeviceRegistry.release()
        tech.alius.rc.video.TeleautoVideoTransmissionManager.release()
        TeleautoTransmitModuleManager.clear()
        TeleautoRemoteControllerStatusManager.release()
    }

    /** 实时订阅遥控器及已接入模块的统一状态。 */
    fun observeStatus(listener: TeleautoRemoteControlStatusManager.Listener): TeleautoRemoteControlStatusManager.Subscription =
        TeleautoRemoteControlStatusManager.observe(listener)

    /** 无需订阅时读取当前统一状态快照。 */
    fun getStatus(): tech.alius.rc.model.TeleautoRemoteControlStatus =
        TeleautoRemoteControlStatusManager.getStatus()

    /** 初始化时自动检测；界面只需展示其中的用户文案。 */
    fun getEnvironmentStatus(): tech.alius.rc.model.TeleautoRuntimeEnvironmentStatus =
        TeleautoRuntimeEnvironmentManager.getStatus()

    fun observeEnvironment(listener: TeleautoRuntimeEnvironmentManager.Listener): TeleautoRuntimeEnvironmentManager.Subscription =
        TeleautoRuntimeEnvironmentManager.observe(listener)

    // ── 设备信息 ──────────────────────────────────────
    fun getDeviceInfo(): TeleautoRemoteControllerInfo? = TeleautoJoystickManager.getDeviceInfo()

    // ── Unified devices ───────────────────────────────
    fun getSupportedDeviceTypes(): List<TeleautoDeviceType> = TeleautoDeviceManager.supportedTypes()
    fun getDevices(): List<TeleautoDeviceSnapshot> = TeleautoDeviceManager.snapshots()
    fun getRemoteController(): TeleautoDevice? = TeleautoDeviceManager.remoteController()
    fun getActiveTargetDevice(): TeleautoDevice? = TeleautoDeviceManager.activeTarget()
    fun createDevice(type: TeleautoDeviceType, config: TeleautoTargetDeviceConfig = TeleautoTargetDeviceConfig()): TeleautoDevice =
        TeleautoDeviceManager.create(type, config)
    fun updateDeviceCapabilities(deviceId: TeleautoDeviceId, capabilities: Set<tech.alius.rc.device.TeleautoDeviceCapability>): Boolean =
        TeleautoDeviceManager.updateCapabilities(deviceId, capabilities)
    fun removeDevice(deviceId: TeleautoDeviceId): Boolean = TeleautoDeviceManager.remove(deviceId)
    fun observeDevices(observer: TeleautoDeviceListObserver): TeleautoDeviceSubscription = TeleautoDeviceManager.observe(observer)
}
