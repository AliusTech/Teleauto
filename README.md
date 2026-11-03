# Teleauto SDK

授权签发工具：交互式 Rust CLI、SDK 版本密钥策略及 FastAPI 调用合同见 [License Generator](license-generator/README.md)。

> Teleauto SDK 统一维护核心能力、共享 UI（`sdk/ui/`）与底层模块。独立 Teleauto 应用项目专注业务：根目录是 Flutter 工程，`assistant/` 是桌面端。

**中文** | [English](README.en.md) | [日本語](README.ja.md)

**TeleautoSdk** 是一套运行在 DJI 带屏遥控器**本体**上的 Android 输入读取库(附输入测试器 Demo)。它把遥控器的全部物理输入——左右摇杆、左右拨轮、拍照/录像键、5D 五向键、C1–C3 自定义键、返航、急停、GPIO 肩键等——翻译成跨机型统一的输入事件模型,供你在遥控器上做二次开发:外设联动、行业应用、辅助操控、自动化测试等。

DJI 官方 Mobile SDK 面向手机端连接无人机,并不提供运行在遥控器本体上的输入读取 API。想在带屏遥控器上开发自己的应用,摇杆和按键数据拿不到——本项目就是为填补这个空白而准备的。

## 目录

- [特性](#特性)
- [工作原理](#工作原理)
- [支持硬件](#支持硬件)
- [快速开始](#快速开始)
- [集成到你的工程](#集成到你的工程)
- [屏幕常亮与后台常驻](#屏幕常亮与后台常驻)
- [发布(维护者)](#发布维护者)
- [API 使用示例](#api-使用示例)
- [输入模型](#输入模型)
- [GPIO 肩键(仅 RM700 系)](#gpio-肩键仅-rm700-系)
- [蓝牙手柄模式](#蓝牙手柄模式)
- [新增机型适配](#新增机型适配)
- [项目结构](#项目结构)
- [已知限制](#已知限制)
- [第三方库与许可](#第三方库与许可)

## 特性

- **统一设备模型**:遥控器、固定翼和遥控车都通过 `TeleautoDevice` 管理；遥控器自动连接，目标设备由 App 选择后通过 MAVLink 连接。
- **统一 USB 热插拔**:Core 集中发现 RTL8812AU 图传接收端与 USB CDC 高频头，拔出立即停止 I/O，重新插入自动恢复。
- **自动机型识别**:读取系统属性匹配机型档案(`DeviceProfile`),未识别机型不崩溃、不影响宿主自身的按键通道。
- **统一输入模型**:跨机型一致的 `TeleautoPhysicalKey` / `TeleautoKeyAction` / `TeleautoStickSnapshot`,订阅端代码与具体遥控器型号解耦。
- **语义化处理**:录像键翻转(toggle)语义 + 1.5s 防长按误翻转;半按对焦双通道触发绕过系统 ~200ms 判定延迟;摇杆归一化与死区处理。
- **断线自动重连**:adb 传输断开后 1.5s 自动重试,连接状态实时上报。
- **蓝牙手柄模式**:SDK 内置多模式切换,一行代码把遥控器变成 PC/Mac 的系统游戏手柄(BLE HID over GATT,零 root;详见下文)。
- **单一权限**:仅需 `INTERNET`(adb 自连 localhost:5555;蓝牙手柄模式另需 BLUETOOTH 系权限)。
- **常亮与常驻**:Demo 内置前台服务保活(`START_STICKY` + 局部唤醒锁),Activity 关闭或息屏后输入读取与蓝牙手柄模式不中断;App 在前台期间屏幕常亮(详见下文)。

## 工作原理

App 安装在遥控器上,通过内置 ADB 协议实现(adblib)**自连本机 `localhost:5555`**,开两条数据通道:

```text
┌───────────────────── DJI 带屏遥控器(Android 设备) ─────────────────────┐
│                                                                        │
│   宿主 App(你的二次开发应用)                                           │
│  ┌───────────────── TeleautoSdk SDK(tech.alius.rc) ──────────────────┐    │
│  │   统一输入模型:TeleautoPhysicalKey / TeleautoKeyAction / TeleautoStickSnapshot         │    │
│  │        ▲                    ▲                                  │    │
│  │   机型档案 DeviceProfile(rm500 / rm700)                        │    │
│  │   原始事件 → 统一语义的翻译层                                  │    │
│  │        ▲                    ▲                                  │    │
│  │ ┌──────┴─────────┐   ┌──────┴───────────┐                      │    │
│  │ │ evdev 通道     │   │ logcat 通道      │                      │    │
│  │ │ getevent -l    │   │ logcat -s        │                      │    │
│  │ └──────┬─────────┘   │ KeyCodeListener  │                      │    │
│  │        │             └──────┬───────────┘                      │    │
│  └────────┼────────────────────┼──────────────────────────────────┘    │
│           └── adb 自连 localhost:5555(内置 adblib) ──┘                 │
│                                                                        │
│   GPIO 肩键 L1-L3/R1-R3(仅 RM700 系):evdev 统一翻译(标准 KeyEvent 仍并行)│
│   (KEYCODE_F1-F6)直达宿主 Activity,不经 SDK                            │
└────────────────────────────────────────────────────────────────────────┘
```

| 通道 | 命令 | 提供的输入 |
|---|---|---|
| evdev | `getevent -l` | `EV_ABS`:左右摇杆 4 轴(`ABS_X/Y`、`ABS_RX/RY`)、左右拨轮(`ABS_Z/RZ`);`EV_KEY`:拍照键(`BTN_TR`)、电源键(`KEY_POWER`)。触屏设备(`fts_ts`)自动跳过 |
| logcat | `logcat -T 1 -s KeyCodeListener:D *:S` | DJI 私有按键广播:`ACTION_KEY_*` 脉冲(录像/拍照/对焦/C1–C3/返航/急停/LED 鉴权/5D 键)与 `mKeyCodeList` 当前按住集合(用于抬起检测) |

翻译层的几个关键语义:

- **归一化**:摇杆/拨轮轴值统一映射到 `[-1, 1]`,内置死区(摇杆平坦值 128,最小死区 4%),读端无需再处理原始量程。
- **录像键**:`ACTION_KEY_RECORD` 是无抬起事件的脉冲流,SDK 翻译为 `TOGGLED_ON` / `TOGGLED_OFF` 翻转语义,并以 1.5s 窗口过滤长按 repeat 造成的误翻转。
- **半按对焦**:dpad 通道对 `FOCUS` 有 ~200ms 判定延迟,SDK 用 `mKeyCodeList` 即时检测按下 + 脉冲兜底的双通道触发(250ms 去重),半按响应接近实时。
- **GPIO 肩键**(仅 RM700 系):SDK 经 evdev 通道统一翻译为 `L1–L3 / R1–R3` 键事件(`DOWN` / `UP`),订阅 `InputListener` 即可;系统的标准 `KeyEvent`(`KEYCODE_F1`–`F6`)分发不受影响,宿主 Activity 仍可并行在 `onKeyDown` / `onKeyUp` 处理。

## 支持硬件

截至 2026-09-14，SDK 已注册以下机型。**机型已适配不代表全部控件已通过真机验证。**

| 机型代号 | 显示名称 | 验证状态 |
|---|---|---|
| `rm500` / `ag501` | DJI SMART CONTROLLER | 初始化、输入节点已连接；C1/C2、正面暂停键、返航和三段开关仍未解决 |
| `rm700` | DJI RC Plus | 已适配，待真机验收 |
| `rc701` | DJI RC Plus 2 | 已真机验证，用户确认正常 |
| `rm510` | DJI RC Pro | 新增协议适配，待真机验收 |
| `rc520` | DJI RC Pro 2 | 新增协议适配，待真机验收 |

目前完成真机验收的是 **RC701（DJI RC Plus 2）**。RM500 的按键协议访问仍受固件权限限制；RM510、RC520 的协议权限、轴方向及实际按键响应尚未确认。RC520 复用协议格式的兼容性也需真机确认，详见 [RC Pro 适配说明](docs/rc-pro-support.md)。其他未列出的型号尚未注册，不能据此视为支持。

### 机型识别机制

- 优先读取 `ro.product.name`，未匹配时回退到 `ro.product.model`；去除首尾空格并忽略大小写后完整匹配，不使用通配符。
- SDK 内置四类档案：**rm500**（`rm500` / `ag501`）、**rm700**（`rm700` / `rc701`）、**rm510**、**rc520**。RM510 与 RC520 独立识别，不套用 RM500 的系统输入权限准备流程。
- 识别结果通过 `TeleautoSdk.getDeviceInfo()` 暴露。机型识别、SDK 授权初始化、输入通道连接与控件验收是不同状态，应分别检查。

## 快速开始

### 环境要求

- JDK 17
- Android SDK:compileSdk 37、Build Tools 36.0.0
- 构建工具链:Android Gradle Plugin 9.3.1、Gradle 9.5.0
- 遥控器端需开启 adb(开发者选项),保证本机 `localhost:5555` 可连

### 构建

> `local.properties` 不入库,克隆后需自行创建,`sdk.dir` 指向你本机的 Android SDK 路径。

```bash
./gradlew :sdk:assembleRelease :sdk:testDebugUnitTest
```

### Teleauto

Android App、共享 UI、桌面助手及设计素材已迁入独立的 **Teleauto** 项目。根目录是 Flutter 工程，桌面项目位于 `Teleauto/assistant/`。本仓库只维护 SDK 与底层模块。

## 集成到你的工程

### 方式一:Maven 依赖(GitHub Packages,推荐)

SDK 发布在本仓库的 GitHub Packages,第三方应用一行依赖接入:

1. 创建仅含 `read:packages` scope 的 GitHub PAT(classic);组织开启 SSO 时,对 token 做一次 SSO 授权;
2. 凭证放入全局 Gradle 配置(避免提交进工程仓库):

```properties
# ~/.gradle/gradle.properties
gpr.user=你的 GitHub 用户名
gpr.key=ghp_xxxxxxxxxxxx
```

3. 在你的 `settings.gradle.kts` 添加仓库:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/AliusTech/dji_rc")
            credentials {
                username = providers.gradleProperty("gpr.user").get()
                password = providers.gradleProperty("gpr.key").get()
            }
        }
    }
}
```

4. 添加依赖(版本号对应仓库 tag,如 `v0.1.0` → `0.1.0`):

```kotlin
implementation("tech.alius:djirc:0.1.0")
```

维护方在 package 页面把你的账号加为 Reader 后即可拉取。`INTERNET` 权限已由 AAR 的 manifest 声明,使用方无需重复声明;开启混淆时 SDK 的 keep 规则也会由 AAR 自动应用。

### 方式二:源码集成

把 `djirc/src/main/java/` 下的 `tech/alius/rc` 与 `com/tananaev/adblib` 两个目录复制进你的工程源码集,并在 `AndroidManifest.xml` 声明 `INTERNET` 权限。

两种方式都要求 `minSdk ≥ 25`(RC Plus 出厂为 API 29,库向下兼容到 25)。首次运行时,SDK 会在应用 `filesDir/adbkey` 下生成 adb RSA 密钥,adbd 接受该密钥后即可完成自连。

## 屏幕常亮与后台常驻

遥控器常作为长时间工作的控制终端,Demo 提供两层保活能力(`TeleautoRemoteControlService`,可直接复用到宿主工程):

- **屏幕常亮**:`MainActivity` 在 `onCreate` 为窗口加 `FLAG_KEEP_SCREEN_ON`,App 在前台期间屏幕不自动息屏;退到后台后恢复系统息屏策略,输入读取不受影响。
- **后台常驻**:`TeleautoRemoteControlService` 前台服务持有 SDK 生命周期——`START_STICKY` 进程被杀后由系统重建,`PARTIAL_WAKE_LOCK` 保证息屏时 CPU 与 adb / 蓝牙通道不休眠;通知栏实时显示机型与连接状态,「退出」动作停止服务并完整释放 SDK(含蓝牙手柄模式)。Activity 关闭只解绑自己的监听器,不会停止读取。

```kotlin
// ① 屏幕常亮(Activity)
window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

// ② 常驻:参照 demo/TeleautoRemoteControlService.kt 声明前台服务并在合适时机启动
startForegroundService(Intent(this, TeleautoRemoteControlService::class.java))
```

服务对应的清单声明(specialUse 类型适用于遥控器这类专用设备场景;Android 13+ 展示通知需运行时申请 `POST_NOTIFICATIONS`):

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".TeleautoRemoteControlService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="djirc-input-reader" />
</service>
```

## 发布(维护者)

推送版本 tag 自动发布,无需配置 Actions secrets(使用内置 `GITHUB_TOKEN`):

```bash
git tag v0.2.0
git push origin v0.2.0   # Actions 自动构建并把 tech.alius:djirc:0.2.0 发布到 GitHub Packages
```

- 版本号 = tag 去掉 `v` 前缀;本地构建默认 `0.0.0-SNAPSHOT`;
- 发布流程:`.github/workflows/publish.yml`(先整包编译验证,再发布);
- 第三方授权:GitHub package 页面 → Package settings → Manage access,添加对方账号为 Reader。

### 手动发布(不依赖 Actions)

Actions 不可用(如计费限制)时,可本地直接发布到 GitHub Packages(需 classic PAT,`write:packages` 权限):

```bash
GITHUB_ACTOR=你的用户名 GITHUB_TOKEN=ghp_你的令牌 \
  ./gradlew :djirc:publishDjircPublicationToGitHubPackagesRepository -PdjircVersion=0.1.0
```

发布到自建 Gitea Maven Registry(token 勾选 package 读写权限;详见 `djirc/build.gradle.kts` 注释):

```bash
./gradlew :djirc:publishDjircPublicationToGiteaRepository -PdjircVersion=0.1.0 \
  -Pgitea.url=https://你的Gitea/api/packages/组织或用户/maven \
  -Pgitea.user=用户名 -Pgitea.token=你的令牌
```

> 注意:私有包的存储/流量计入 GitHub 计费,组织付款或消费限额异常时私有发布会失败;公开包免费且消费方无需凭证。

## API 使用示例

```kotlin
class MyActivity : Activity() {

    private val listener = object : TeleautoJoystickManager.InputListener {
        override fun onKeyEvent(event: TeleautoRcKeyEvent) {
            when (event.key) {
                TeleautoPhysicalKey.RECORD -> when (event.action) {
                    TeleautoKeyAction.TOGGLED_ON  -> startRecording()   // 录像键:翻转语义
                    TeleautoKeyAction.TOGGLED_OFF -> stopRecording()
                }
                TeleautoPhysicalKey.SHUTTER -> if (event.action == TeleautoKeyAction.TRIGGER) takePhoto()
                else -> { /* 其余按键 */ }
            }
        }

        override fun onStickChanged(snapshot: TeleautoStickSnapshot) {
            // 高频覆盖式更新;UI 场景建议像 Demo 一样轮询 getStickSnapshot()
        }

        override fun onHeldKeysChanged(held: Set<TeleautoPhysicalKey>) {
            // dpad 私有键的当前按住集合(可用于抬起检测)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化:自动识别机型,返回是否识别到支持的遥控器
        val supported = TeleautoSdk.initialize(this)
        // 回调线程默认为主线程(UI 安全);高频处理可换线程池:
        // TeleautoSdk.initialize(this, TeleautoConfig(callbackExecutor = myExecutor))

        TeleautoJoystickManager.addInputListener(listener)
        TeleautoJoystickManager.addConnectionListener(object : TeleautoJoystickManager.ConnectionListener {
            override fun onStateChanged(state: TeleautoConnectionState) { /* 断线/重连 */ }
        })

        val info = TeleautoSdk.getDeviceInfo()      // model / product / 固件版本 / profileId …
        val connection = TeleautoJoystickManager.connection     // DISCONNECTED / CONNECTING / CONNECTED
    }

    override fun onDestroy() {
        TeleautoJoystickManager.removeInputListener(listener)
        TeleautoSdk.release()
        super.onDestroy()
    }

    private fun poll() {
        val s = TeleautoJoystickManager.getStickSnapshot()      // 所有摇杆/拨轮轴（含 freeDial）∈ [-1,1]
        TeleautoJoystickManager.setFreeDialValue(0.6f)          // 支持时从指定值继续累计
        val keys = TeleautoJoystickManager.getKeyStates()       // 各键当前按下状态(仅 DOWN/UP 类)
    }
}
```

## 输入模型

### TeleautoPhysicalKey(统一物理键枚举)

| 分组 | 枚举值 | 说明 |
|---|---|---|
| 摇杆 / 拨轮(状态型,经 `TeleautoStickSnapshot`) | `LEFT_STICK_X/Y`、`RIGHT_STICK_X/Y`、`LEFT_DIAL`、`RIGHT_DIAL`、`FREE_DIAL` | 归一化 `[-1,1]`,含死区 |
| GPIO 面板键(仅 rm700 系) | `L1`–`L3`、`R1`–`R3` | 电平型 `DOWN`/`UP`,走宿主 KeyEvent,不经 SDK 分发 |
| 私有键(dpad 通道) | `RECORD`、`SHUTTER`、`FOCUS`、`C1`、`C2`、`C3` | 拍照键两段式:半按 `FOCUS`、全按 `SHUTTER` |
| | `GOHOME`、`PAUSE`、`AUTH_LED` | 返航 / 急停 / LED 鉴权 |
| | `D_UP/D_DOWN/D_LEFT/D_RIGHT/D_MIDDLE` | 5D 五向键 |
| evdev 按键 | `POWER` | 电源键,`DOWN`/`UP` |

未配备的键在对应机型上不会触发,`TeleautoRemoteControllerInfo.supportedInputs` 列出当前机型全集。

### TeleautoKeyAction(动作语义)

| 值 | 含义 |
|---|---|
| `DOWN` / `UP` | 电平按下 / 抬起(GPIO 键、电源键) |
| `TRIGGER` | 脉冲触发(拍照 / 对焦 / C 键 / 5D 键等) |
| `TOGGLED_ON` / `TOGGLED_OFF` | 翻转开 / 关(录像开始 / 停止) |

### TeleautoStickSnapshot(摇杆 / 拨轮快照)

不可变数据类,字段 `leftX`、`leftY`、`rightX`、`rightY`、`leftDial`、`rightDial`、`freeDial`(均 `[-1,1]`)+ `timestampNs`。支持无限波轮的机型可调用 `TeleautoJoystickManager.setFreeDialValue(value)` 指定当前位置，后续硬件脉冲从该值继续累计；设置成功后立即发布快照。高频覆盖式更新,读端零锁;UI 场景推荐固定频率轮询 `TeleautoJoystickManager.getStickSnapshot()` 而非逐事件刷新。

### TeleautoConnectionState 与 TeleautoRemoteControllerInfo

- `TeleautoConnectionState`:`DISCONNECTED`(未初始化或 adb 断开)/ `CONNECTING`(初始化完成,连接建立中)/ `CONNECTED`(双通道正常工作)。
- `TeleautoRemoteControllerInfo`:`model`、`product`、`device`、`manufacturer`、`firmwareVersion`(`dji.prop.external_version`)、`androidVersion`、`profileId`、`supportedInputs`。

### 各机型输入能力对照

| 输入 | rm500 | rm700 |
|---|:--:|:--:|
| 摇杆 4 轴 + 左右拨轮 | ✓ | ✓ |
| 拍照(两段式)/ 录像 / C1 / C2 / 返航 / 电源 | ✓ | ✓ |
| 5D 五向键 | — | ✓ |
| 急停 `PAUSE` | — | ✓ |
| C3 / LED 鉴权 | — | ✓ |
| GPIO 肩键 L1–L3 / R1–R3(宿主 KeyEvent) | — | ✓ |

## GPIO 肩键(仅 RM700 系)

RM700 类机型的 GPIO 面板键不走 SDK 通道,而是以标准 `KeyEvent` 到达前台 Activity。映射关系固定为 `KEYCODE_F1`–`F6` → `L1`–`L3` / `R1`–`R3`:

```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    val key = when (keyCode) {
        KeyEvent.KEYCODE_F1 -> TeleautoPhysicalKey.L1
        KeyEvent.KEYCODE_F2 -> TeleautoPhysicalKey.L2
        KeyEvent.KEYCODE_F3 -> TeleautoPhysicalKey.L3
        KeyEvent.KEYCODE_F4 -> TeleautoPhysicalKey.R1
        KeyEvent.KEYCODE_F5 -> TeleautoPhysicalKey.R2
        KeyEvent.KEYCODE_F6 -> TeleautoPhysicalKey.R3
        else -> return super.onKeyDown(keyCode, event)
    }
    if (event.repeatCount == 0) handleShoulderDown(key)
    return true
}
```

## BLE HID 模式

SDK 内置多模式切换(枚举 `TeleautoRemoteControlMode`),复用同一套输入获取链路,运行时随时切换:

```kotlin
TeleautoJoystickManager.mode = TeleautoJoystickManager.Mode.BLE_HID           // 进入 BLE HID 模式
// 在电脑蓝牙设置中搜索机型名(如「DJI RC Plus」)并配对,即挂载为系统游戏手柄
TeleautoJoystickManager.addHidStateListener { mode, detail -> /* ADVERTISING / PAIRING / CONNECTED / ERROR */ }
TeleautoJoystickManager.mode = TeleautoJoystickManager.Mode.CONTROLLER        // 退出,恢复数据回调
```

- **模式语义**:BLE HID 模式下输入数据不再经 `InputListener` 回调,直接翻译为标准 gamepad HID 报文,按 **Xbox/Standard Gamepad 布局**映射——左/右霍尔摇杆 → 左/右摇杆轴(X/Y、Rx/Ry),左右拨轮 → 扳机 **LT/RT**(线性 + 数字态),录像键 → **LB**,拍照键 → **RB**,C1/C2/C3 → A/B/X,半按对焦 → Y,返航/急停 → View/Menu,5D 中/LED 鉴权 → LS/RS,5D 四向 → Hat Switch 十字键。
- **连接体验**:首次开启后在电脑蓝牙设置中搜索遥控器并完成系统配对;HID 特征使用加密访问保证真实 bonding。配对记录与最后主机地址会保留,以后开启 BLE HID 模式将持续广播并等待电脑自动回连。状态依次为 `ADVERTISING`、`PAIRING`、`CONNECTED`,遥控器端在首个 Input Report 成功发送后显示“通信正常”。官方验收页为 `docs/gamepad-test.html`,使用浏览器 **Gamepad API**(`navigator.getGamepads()`)读取。
- **电量**:`TeleautoRemoteControllerStatusManager` 提供与 RC Plus 系统状态栏同源的聚合电量快照/变化监听;BLE Battery Service 使用同一数值向电脑报告手柄电量。
- **传输方案**:纯应用层 **BLE HID over GATT(HOGP)**。DJI 固件关闭了系统 HID Device profile(`profile_supported_hid_device` 被厂商 overlay 置 false 且不可变更),USB gadget HID 亦需 root;本方案零 root、零系统修改,Mac/Windows 原生识别。当前 Web 侧以 **Universal Gamepad / Gamepad API** 为准;WebHID 只用于确认 `Report Map`、`Report Reference`、Input Report 通知等底层枚举细节。
- **权限**:`BLUETOOTH_CONNECT` / `BLUETOOTH_ADVERTISE`(Android 12+ 运行时申请;≤11 由 manifest 声明的 `BLUETOOTH`/`BLUETOOTH_ADMIN` 覆盖);需设备支持 BLE 外设广播。
- **限制**:GPIO 肩键(L1–L3/R1–R3)走宿主 KeyEvent,电源键不进入 HID,避免占用标准手柄槽位;BLE 连接间隔约 7.5–15ms,适合模拟器/训练类应用。

## 新增机型适配

新机型只需新增一个 `DeviceProfile` 实现,其余通道与翻译层全部复用:

1. 继承 `TeleautoRemoteControlProfile`(已实现双通道读取、轴归一化、私有键翻译、录像 toggle、对焦双通道等通用逻辑):

```kotlin
class NewDeviceProfile : TeleautoRemoteControlProfile() {
    override val id = "new-device"
    override val productName = "DJI New Controller"
    override val matchModels = setOf("new_device")       // ro.product.name 精确匹配
    override val supportedInputs = baseInputs + setOf(
        TeleautoPhysicalKey.D_UP, TeleautoPhysicalKey.D_DOWN, TeleautoPhysicalKey.D_LEFT,
        TeleautoPhysicalKey.D_RIGHT, TeleautoPhysicalKey.D_MIDDLE,
        TeleautoPhysicalKey.PAUSE,
    )
}
```

2. 在 `TeleautoSdk.initialize()` 中注册(与现有三个档案并列):

```kotlin
TeleautoProfileRegistry.register(tech.alius.rc.profile.newdevice.NewDeviceProfile())
```

3. 若新机型的输入拓扑不同(如轴代号、私有键命名有差异),覆写 `TeleautoRemoteControlProfile` 的相应处理方法即可。

适配新机型时的真机校准命令:

```bash
adb shell getprop ro.product.model     # 型号标识
adb shell getprop ro.product.name      # 档案匹配使用的属性
adb shell getevent -l                  # 观察摇杆/拨轮的 evdev 轴代号
adb shell logcat -s KeyCodeListener    # 观察私有按键的 ACTION_KEY_*
```

## 项目结构

```text
dji_rc/
├── sdk/                         # tech.alius:teleauto-core
├── modules/video_transmission/  # Native video implementation
├── third_party/                 # SDK dependencies
├── hardware/                    # Protocol and hardware references
├── docs/                        # SDK integration / protocol docs
├── .github/workflows/           # SDK build, tests and publish
└── VERSION                      # SDK release version
```

## 已知限制

- 依赖遥控器端 adbd 的 TCP 5555 自连可用(需在系统开发者选项中开启 adb);首次连接需 adbd 接受本应用生成的 RSA 密钥。
- dpad 私有键通道本身没有抬起事件,SDK 通过 `mKeyCodeList` 集合变化推断抬起;个别按键的抬起时序可能受系统 dpad 判定影响。
- RM700 系列的连续波轮与三段式开关不经 Android 输入栈(evdev)与 dpad 广播,由固件经本机 TCP 遥测接口(localhost:40009,DUML 帧)提供;SDK 以 `TeleautoTcpInputReader` 通道获取:波轮更新 `TeleautoStickSnapshot.freeDial`,三段开关按 `0/1/2` 发布 `TeleautoThreePositionSwitchState`，物理标签为 `C/N/S`。具体业务用途由宿主 App 决定。
- Demo 应用的 release 构建当前复用 debug 签名(SDK 以 AAR 分发,不受影响);正式分发 Demo 前需配置独立签名。
- 蓝牙手柄模式为 BLE 传输;USB 有线 HID 需要设备 root(configfs gadget),生产固件不可用。
- 支持状态为「计划支持」的机型尚未在对应真机上验证,输入拓扑可能与内置档案存在差异,以实测为准。

## 第三方库与许可

- [tananaev/adblib](https://github.com/tananaev/adblib)(MIT License)—— 以源码形式内置,提供纯 Java 的 ADB 协议实现。
- 本项目自身许可证:内部项目,暂未指定。
