package tech.alius.rc

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import tech.alius.rc.input.TeleautoJoystickInputSource

/**
 * SDK 配置。
 *
 * @param callbackExecutor 输入事件回调执行线程。
 *        默认主线程,宿主可在回调中安全更新 UI;
 *        高频处理场景可换成自己的线程池。
 * @param mode 初始工作模式(默认遥控器模式),运行时可经 [TeleautoJoystickManager.mode] 随时切换。
 *        BLE HID 模式要求 Android 10 / API 29+,宿主需声明并申请
 *        BLUETOOTH_CONNECT 权限,且遥控器已与 PC/Mac 完成蓝牙配对。
 */
class TeleautoConfig(
    var callbackExecutor: Executor = mainExecutor(),
    var mode: TeleautoJoystickManager.Mode = TeleautoJoystickManager.Mode.CONTROLLER,
    /** 初始摇杆输入源；默认保持 DJI 遥控器原有输入链路。 */
    var inputSource: TeleautoJoystickInputSource = TeleautoJoystickInputSource.EMBEDDED_JOYSTICK,
    /** 图传(OpenIPC):启动时自动开启 RTP/UDP 接收。 */
    var videoEnabled: Boolean = false,
    /** 图传 RTP/UDP 监听端口(OpenIPC 生态惯例 5600)。 */
    var videoPort: Int = 5600,
    /** 直接接管图传接收端（RTL8812）并启动 WFB-NG 接收。 */
    var wfbVideoEnabled: Boolean = true,
    /** WFB-NG 无线频道，需与 OpenIPC Air Unit 一致。 */
    var wfbChannel: Int = 161,
    /** WFB-NG 频道带宽，仅支持 20 或 40 MHz。 */
    var wfbBandwidth: Int = 20,
    /** 高频头USB CDC线路速率。标准USB CDC设备可忽略该提示值。 */
    var transmitModuleBaudRate: Int = 115_200,
    /** 多个CDC设备同时存在时，指定由SDK返回的稳定USB设备ID。 */
    var preferredTransmitUsbDeviceId: String? = null,
    /** Optional signed vendor binding in addition to package and APK certificate. */
    var licenseVendorId: String? = null,
    var licenseListener: tech.alius.rc.license.TeleautoLicenseListener? = null,
) {
    companion object {
        /** 主线程执行器(默认) */
        fun mainExecutor(): Executor {
            val handler = Handler(Looper.getMainLooper())
            return Executor { handler.post(it) }
        }
    }
}
