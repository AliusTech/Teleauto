# JoystickManager SDK · App 集成说明

本文档说明如何集成无 UI 的 `teleauto-core.aar`。应用及共享 UI 现由独立的 Teleauto 项目维护，本仓库不再发布 UI AAR。

- **适用版本**:0.1.0(可通过 `DJIRemoteControl.VERSION` 查询)
- **环境要求**:minSdk ≥ 25(JDK 17 编译产物),运行于 DJI 带屏遥控器本体
- **已验证机型**:DJI RC Plus / RC Plus 2(rm700 平台)、AG501(rm500 档案);内置 rm500 / rm510 档案

## 一、SDK 结构

SDK 采用「门面 + 组件」分层:

| 类 | 职责 |
|---|---|
| `DJIRemoteControl` | 总门面:初始化 / 释放、设备信息、版本号 |
| `JoystickManager` | 摇杆输入组件:输入订阅、摇杆快照、按键状态、工作模式、蓝牙手柄 |
| `DJIRemoteControllerStatusManager` | 遥控器状态组件:系统聚合电量快照与变化监听、BLE 手柄电量数据源 |
| `DJITransmitModuleManager` | USB CDC 高频头发现、选择与热插拔状态 |
| `DJIVideoTransmissionManager` | 图传接收、解码、Surface 与模块状态 |
| `tech.alius.rc.device.*` | 统一遥控器、固定翼、遥控车设备及动态组件模型 |
| `DJIRemoteControlConfig` | 初始化配置(回调线程、初始模式) |
| `tech.alius.rc.model.*` | 输入模型:`PhysicalKey` / `KeyAction` / `StickSnapshot` / `RcKeyEvent` / `ConnectionState` / `DeviceInfo` |
| `tech.alius.rc.input.*` | 输入源抽象：内置摇杆 / Android USB、Bluetooth HID 外置摇杆 |

包名 `tech.alius.rc`,与内部类名无冲突 concerns,直接 import 使用。

## 二、集成步骤

### 1. 放入 AAR

把 `teleauto-core.aar` 拷贝到应用模块的 `libs/` 目录:

```
app/
├── libs/
│   ├── teleauto-core.aar
└── build.gradle.kts
```

### 2. 声明依赖

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(files("libs/teleauto-core.aar"))
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")
}
```

> Maven 坐标集成会自动解析 Bouncy Castle 运行时依赖；直接拷贝 AAR 时需要如上显式声明。软件加密用于本地授权时间记录，不包含授权签发私钥。
> 若 libs 下有多个 aar,可用 `implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))`。
> Core 保留可替换的标准 Joystick Backend 边界；当前版本使用 Android 标准 HID/InputDevice API，
> 不再打包 SDL native library。RTL8812 图传 native 库首版仅提供 `arm64-v8a`。

### 3. 声明权限

`AndroidManifest.xml`:

```xml
<!-- 必须:adb 自连 localhost:5555 读取输入 -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- 可选:仅使用蓝牙手柄模式(JoystickManager.Mode.BLUETOOTH_JOYSTICK)时需要 -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
```

> Android 12+ 使用蓝牙手柄模式需运行时申请 `BLUETOOTH_CONNECT` / `BLUETOOTH_ADVERTISE`。

### 4. 混淆(如有)

无需手动配置:AAR 内置 `consumer-rules.pro`,开启混淆时自动生效(adblib、公有 API、模型类均已 keep)。

## 三、基础用法

### 初始化与设备信息

```kotlin
import tech.alius.rc.DJIRemoteControl
import tech.alius.rc.DJIRemoteControlConfig
import tech.alius.rc.JoystickManager

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // 初始化:自动识别机型;返回 false 表示未识别的机型(输入读取不可用,但不崩溃)
    val supported = DJIRemoteControl.initialize(this)

    // 可选配置:回调线程(默认主线程,可安全更新 UI)、初始工作模式
    // DJIRemoteControl.initialize(this, DJIRemoteControlConfig(
    //     callbackExecutor = myExecutor,
    //     mode = JoystickManager.Mode.CONTROLLER,
    // ))

val info = DJIRemoteControl.getDeviceInfo()
Log.d("Demo", "机型 ${info?.model},档案 ${info?.profileId},固件 ${info?.firmwareVersion},SDK ${DJIRemoteControl.VERSION}")
}
```

外置 Joystick 页面可继承 SDK 提供的 `DJIJoystickActivity`，由它统一转发 Android 标准
Joystick 的 Activity 生命周期和输入事件：

```kotlin
class MainActivity : DJIJoystickActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setJoystickContentView(R.layout.activity_main)
        DJIRemoteControl.initialize(this)
    }
}
```

### 切换摇杆输入源

输入源与 BLE HID 输出模式相互独立。默认值为 `EMBEDDED_JOYSTICK`，行为与旧版本一致；
切换为 `EXTERNAL_JOYSTICK` 后读取用户指定的 Bluetooth HID 或 USB HID 外接摇杆，
包括 `DJI Bluetooth joystick`。

```kotlin
import tech.alius.rc.input.JoystickInputSource

val devices = JoystickManager.getConnectedJoystickDevices()
val selected = devices.first { it.name == "DJI bluetooth joystick" } // 实际产品中由用户选择
JoystickManager.selectJoystickDevice(selected.id)
JoystickManager.inputSource = JoystickInputSource.EXTERNAL_JOYSTICK
```

可在切换前配置死区、轴顺序与按钮映射：

```kotlin
JoystickManager.configureExternalJoystick(
    ExternalJoystickConfig(
        deadZone = 0.04f,
        invertLeftY = true,
        invertRightY = true,
        axisMapping = JoystickAxisMapping(),
    )
)
```

`getConnectedJoystickDevices()` 可查看当前候选设备及其名称、GUID、VID、PID。必须调用
`selectJoystickDevice(deviceId)` 明确指定输入设备；SDK 只消费该设备的事件，断开时不会
自动切换到其他摇杆。SDK 以 Android `SOURCE_JOYSTICK` 作为准入条件，并兼容设备附带的
`SOURCE_GAMEPAD` 按键事件。组合
`EXTERNAL_JOYSTICK` 输入源与 `BLE_HID` 输出模式，可形成 HID 输入 → SDK 统一模型 → BLE HID 输出链路。
外置模式完全由 Android 标准 HID 输入读取，不建立 localhost:5555 ADB连接；Activity 进入后台时立即释放按键、
归零所有轴并暂停读取。BLE输出在系统侧声明为标准 Game Pad Usage 与 Gamepad Appearance。

SDK 会自动识别 USB/Bluetooth Joystick 的插入与移除。设备选择页面可通过热插拔监听器实时刷新：

```kotlin
private val deviceListener = JoystickManager.JoystickDeviceListener { devices ->
    joystickAdapter.submitList(devices)
}

JoystickManager.addJoystickDeviceListener(deviceListener)
// Activity 销毁时：
JoystickManager.removeJoystickDeviceListener(deviceListener)
```

设备断开时 SDK 会立即释放全部按键并将轴归零，同时保留用户选择标识，不会静默改用另一台设备。

### 订阅输入事件

```kotlin
import tech.alius.rc.model.*

private val listener = object : JoystickManager.InputListener {
    override fun onKeyEvent(event: RcKeyEvent) {
        when (event.key) {
            PhysicalKey.RECORD -> when (event.action) {
                KeyAction.TOGGLED_ON  -> startRecording()   // 录像键:翻转语义
                KeyAction.TOGGLED_OFF -> stopRecording()
            }
            PhysicalKey.SHUTTER -> if (event.action == KeyAction.TRIGGER) takePhoto()
            else -> { /* 其余按键 */ }
        }
    }

    override fun onStickChanged(snapshot: StickSnapshot) {
        // 高频回调;UI 场景建议轮询 getStickSnapshot()(见下)
    }

    override fun onHeldKeysChanged(held: Set<PhysicalKey>) {
        // dpad 私有键当前按住集合(可做抬起检测)
    }
}

// 注册 / 注销
JoystickManager.addInputListener(listener)
JoystickManager.removeInputListener(listener)
```

### 摇杆快照轮询(UI 推荐)

```kotlin
private val handler = Handler(Looper.getMainLooper())
private val poll = object : Runnable {
    override fun run() {
        val s = JoystickManager.getStickSnapshot()
        // s.leftX / leftY / rightX / rightY / leftDial / rightDial ∈ [-1,1](含死区)
        handler.postDelayed(this, 40)
    }
}
```

### 连接状态

```kotlin
JoystickManager.addConnectionListener(object : JoystickManager.ConnectionListener {
    override fun onStateChanged(state: ConnectionState) {
        // DISCONNECTED / CONNECTING / CONNECTED;断线后 SDK 自动重连(1.5s)
    }
})
```

### 电池状态

```kotlin
val battery = DJIRemoteControllerStatusManager.getBatteryState()
val level = DJIRemoteControllerStatusManager.getBatteryLevel() // 0..100，未就绪时 -1
Log.d("Demo", "电量 ${battery?.levelPercent}%, 状态 ${battery?.status}")

private val batteryListener = DJIRemoteControllerStatusManager.BatteryListener { state ->
    // levelPercent: 0..100
    // status: UNKNOWN / CHARGING / DISCHARGING / NOT_CHARGING / FULL
    // temperatureTenthsC: 335 表示 33.5°C
    updateBatteryUi(state)
}

DJIRemoteControllerStatusManager.addBatteryListener(batteryListener)
DJIRemoteControllerStatusManager.removeBatteryListener(batteryListener)
```

RC Plus 可能包含多个电池节点。SDK 读取 Android Battery Service 的聚合结果，与系统状态栏保持一致；BLE HID Battery Service 复用同一数值。

### 释放

```kotlin
override fun onDestroy() {
    DJIRemoteControl.release()   // 涉及关流,建议放子线程
    super.onDestroy()
}
```

> **后台持续读取**:若希望退出界面后输入仍可读取(如蓝牙手柄场景),请在**前台服务**中持有 SDK 生命周期(参考 Teleauto 项目 `app/` 模块的 `DJIRemoteControlService`:START_STICKY + PARTIAL_WAKE_LOCK + 常亮标记)。

## 四、输入模型速查

### PhysicalKey(跨机型统一)

| 分组 | 枚举值 | 说明 |
|---|---|---|
| 摇杆/拨轮 | `LEFT_STICK_X/Y`、`RIGHT_STICK_X/Y`、`LEFT_DIAL`、`RIGHT_DIAL` | 经 `StickSnapshot`,归一化 [-1,1] |
| 私有键 | `RECORD`、`SHUTTER`、`FOCUS`、`C1`、`C2`、`C3`、`GOHOME`、`PAUSE`、`AUTH_LED` | 拍照键两段式:半按 `FOCUS`、全按 `SHUTTER` |
| 5D 键 | `D_UP / D_DOWN / D_LEFT / D_RIGHT / D_MIDDLE` | rm510 / rm700 机型 |
| evdev | `POWER` | 电源键,DOWN/UP |
| GPIO 肩键 | `L1–L3`、`R1–R3` | 仅 rm700;**不经 SDK**,见下节 |

### KeyAction

`DOWN` / `UP`(电平)、`TRIGGER`(脉冲)、`TOGGLED_ON` / `TOGGLED_OFF`(录像翻转,SDK 已做 1.5s 防长按误翻转)。

## 五、注意事项

1. **GPIO 肩键(仅 RM700 系)**:`L1–L3 / R1–R3` 以标准 `KeyEvent`(`KEYCODE_F1`–`F6`)到达前台 Activity,在 `onKeyDown/onKeyUp` 自行处理,不经 SDK 分发。
2. **adb 自连前提**:遥控器需开启 adb(开发者选项),保证本机 `localhost:5555` 可连;首次运行 SDK 会在应用 `filesDir/adbkey` 生成 RSA 密钥,adbd 接受后建连。装好后的授权引导可用配套的「DJI RC Assistant」桌面安装助手完成。
3. **屏幕常亮**:宿主 Activity 自行加 `window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)`。
4. **BLE HID 模式**(可选):`JoystickManager.mode = JoystickManager.Mode.BLE_HID` 后输入不再回调,转为加密配对的 BLE HID 手柄供 PC/Mac 使用;`hidMode` / `addHidStateListener` 可跟踪广播、配对、连接及通信状态,`resetHidPairing()` 可强制重新配对。配对成功后会保留主机记录,下次开启模式时自动广播并等待系统回连。
5. **日志排查**:tag 为 `DJIRemoteControl`(门面)、`DJIRemoteControl-Evdev` / `-Logcat`(输入通道)、`DJIRemoteControl-BleHid`(蓝牙)。

## 六、常见问题

| 现象 | 排查 |
|---|---|
| `initialize` 返回 false | 未识别机型:用 `adb shell getprop ro.product.name` 确认代号,联系维护方补档案 |
| 连接状态一直 DISCONNECTED | 遥控器未开 adb / 5555 未监听;首次密钥未被接受 |
| 部分按键无事件 | 未配备该键的机型不触发;GPIO 肩键走 KeyEvent 通道 |
| 摇杆值漂移 | SDK 已含死区(≥4%);确认未被宿主二次放大 |

---
版本 0.1.0 · 更多细节见仓库 README(双通道原理、机型支持清单、新机型适配指南)。
