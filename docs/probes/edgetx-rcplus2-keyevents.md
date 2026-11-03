# EdgeTX rcplus2 APK 按键事件档案(RM500 家族四层对照)

> 样本:`app-release.apk`(69,452,752 B,MD5 `7e54a0537a131a43008a8e5a3e2313e2`)
> 包名 `com.edgetx.droidui` v0.1.0,label "EdgeTX",minSdk 24 / targetSdk 33
> 目标硬件:RM500 家族带屏遥控器(含 RC Plus 2 / `rc701`,同 `rm700` 档案硬件平台)
> 分析日期:2026-09-28 · jadx + arm64 反汇编(`libedgetx_ui.so` dynsym 完整)

## 1. 输入通道总览

App 在带屏遥控器上运行时同时开两条输入路径,互为兜底:

| 通道 | 实现 | 覆盖输入 |
|---|---|---|
| DJI MSDK v5 KeyManager | `DjiMsdkBridge`(Java) | 全部:摇杆/波轮/C1-C3/返航/录像/拍照/五维/三段 |
| AG501 原始 USB HID | `RcRawJoystick`(Java,直读 USB 端点) | 摇杆/双波轮 + 返航/录像/五维(**无 C1/C2/C3、无三段**) |

USB 原始报文一旦收到数据即置 `ownsInputs()=true`,MSDK 路径的摇杆/波轮/五维派发被抑制。
MSDK Java 层被 cySdkyc 加固壳(`libSdkyclx_clx.so`,InMemoryDexClassLoader 注入)隐藏,
静态不可提取;但字节码字段名引用不受影响,语义可完整还原。

## 2. USB 原始 HID 报文(VID 0x2CA3 / PID 0x1501)

与 SDK 仓库 `TeleautoRm500Profile` 的 AG501 常量一致。报文 ≥18 字节:

| 偏移 | 含义 |
|---|---|
| [0] | 报告类型 |
| [2..3] | u16 LE 计数器 |
| [4..15] | 6 × s16 LE 轴:LH/LV/RH/RV/dialL/dialR,中点 1024 |
| [16] | 按键组1:`0x02`=RTN(返航)、`0x04`=录像 |
| [17] | 按键组2(五维):`0x01`上 / `0x02`下 / `0x04`左 / `0x08`右 / `0x10`中 |

注意:该厂商协议的位编号与内核 HID usage→evdev 键码的映射**不是**同一套编号,
不可互相推导(仓库 evdev 实测:314=返航、307=C1、308=C2、310=录像、304/311=拍照两段、317=五维中键)。

## 3. MSDK v5 按键键(设备本机路径,权威)

| 物理输入 | MSDK v5 Key | App 处理 |
|---|---|---|
| 返航 RTH | `KeyGoHomeButtonDown` | 见 §4 连击语义 |
| C1 | `KeyCustomButton1Down` | 虚拟开关 SF → −1(上);自锁 |
| C2 | `KeyCustomButton2Down` | SF → 0(中) |
| C3 | `KeyCustomButton3Down` | SF → +1(下);**一代 RM500 无此硬件** |
| 三段开关 | `KeyFlightModeSwitchState` → `RCFlightModeSwitch` | SB:SWITCH_ONE(位置一/上)→0、SWITCH_TWO(位置二)→+1、SWITCH_THREE(位置三)→−1 |
| 录像 | `KeyRecordButtonDown` | SC 翻转(250ms 去抖) |
| 暂停 | `KeyPauseButtonDown` | SD 翻转 |
| 拍照 | `KeyShutterButtonDown` | SE 翻转 |
| 五维键 | `KeyFiveDimensionPressedStatus` / `KeyRcHardwareState` | up=1 down=2 left=3 right=4 press=5 |
| 摇杆 | `KeyStickLeft/Right…H/V` | `dji_analog`:±660 → 0..4095(中点 2048) |
| 波轮/滚轮 | `KeyLeftDial` / `KeyRightDial` / `KeyScrollWheel` | 轴 |
| 电机状态 | `KeyAreMotorsOn` | SJ:上电=+1 / 停转=−1 |
| 综合事件 | `KeyRcButtonEventPro`(`RcCustomButtonEvent`) | isC1Click~isC4Click + 按下/释放 action |
| "takeoff" | `KeyRCAuthLedButtonDown` | SA 翻转(字段名按字节码引用,疑为 RC Plus 2 新增键) |

另有 `RCTransformationSwitchState`(收放)→ −1/+1 的换算代码,本包未接线。

## 4. 返航键完整语义(native 反汇编还原)

- **800ms 连击计数**:短按 1/2/3 次分别设五维键微调模式 `off` / `left stick` / `right stick`
  (五维键此时变成对应摇杆的微调,3 秒无操作自动回 `off`;同值不重复派发)
- **长按 ≥600ms**(Java 计时):注入 EdgeTX RTN/EXIT 键(键码 1)合成按住 450ms,即返回/退出

## 5. 五维键 native 映射(EdgeTX 键码)

| 五维键 | EdgeTX 映射 | 备注 |
|---|---|---|
| 上 | 滚轮编码器左旋 | rotary 计数 −1 |
| 下 | 滚轮编码器右旋 | rotary 计数 +1 |
| 左 | 键码 3 = PAGEUP | 微调模式激活时优先做微调 |
| 右 | 键码 4 = PAGEDN | 同上 |
| 中 | 键码 2 = ENTER | 与 Android 系统按键路径 500ms 去重 |

EdgeTX 键码序:`1=RTN(EXIT)、2=ENTER、3=PAGEUP、4=PAGEDN`。
开关索引 0-9 → SA-SJ;`is_latching_switch` 仅认 SF(C1/C2/C3 合成开关)自锁,
位置持久化于 `joystick.switches`(`-1 up / 0 middle / 1 down`)。

## 6. 与 SDK 仓库的对照结论

- RM500 evdev 映射(314/307/308/310/304/311/317)与 APK 双路径互证,方向一致。
- **C3 不补**:一代 RM500 无 C3 硬件;DUML 0x06/0xAE 位域亦无 C3 位
  (bit5=返航、bit6=拍照全按、bit7=半按对焦、bit8=录像;modeBits 低 2 位=三段、bit2=C1、bit3=C2)。
  RC Plus 系列的 C3 走 logcat `ACTION_KEY_C3`,已由 `rm700` 档案覆盖。
- **已据此补全**:`TeleautoRm500Profile` 接入 FOCUS(半按对焦,DUML bit7)——
  evdev 无对应键码、拍照全按(bit6)/录像(bit8)与 evdev 重复故保持单通道。
- 环境信息:APK 的 DJI API key `e5006cd1b47e369e10f5bb98`(注册包名 `com.edgetx.droidui`);
  ProductCapability 含 M4DSeries,内置 MSDK ≥5.16;EdgeTX 固件体为
  `libedgetx-st16mk3-simulator.so`(ST16 MK3 目标的 simulator 构建)。
