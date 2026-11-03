# rc701 固件 DUML 遥测协议与新增输入实现说明

> 适用机型:DJL RC Plus 2(`ro.product.name=rc701`,档案 `Rc701Profile`)
> 关联代码:`djirc/src/main/java/tech/alius/rc/internal/TcpInputReader.kt`
> 状态:已在真机实测(三段开关档位切换、无限波轮双向旋转,一增一减确认)

## 1. 背景

RC Plus 2 的**三段式开关**与**右侧无限连续波轮**不经过 Android 标准输入栈:

- evdev(`DJI embedded joystick`,/dev/input/event4)上无任何事件;
- dpad 广播(KeyCodeListener)亦无日志。

两路输入由遥控器 MCU 经 DUSS 私有总线直达固件守护进程。经抓包分析,固件在本机
开放了 TCP 遥测端口,普通应用(无 root)即可连接获取,SDK 的 `TcpInputReader`
即基于该通道实现。

## 2. 端口拓扑

| 端口 | 内容 | 备注 |
|---|---|---|
| 40007 | TCP 封装 DUML 遥测,~10Hz 周期推送 | **SDK 采用**(高频,适合连续值) |
| 40009 | 裸 DUML 流(无 TCP 封装),1Hz + 事件触发 | 0xAE 帧多带一字段(无限波轮),但采样率低 |
| 8901 / 8902 | 链路/其它遥测(非 0x55 帧格式) | 未发现输入数据 |
| 33/0x06 帧 | 1Hz 自增心跳计数器 | 与输入无关,解析时忽略 |

注意:40007 服务有"活动唤醒"行为——闲置后回到静默,检测到输入活动恢复 10Hz 推送;
服务端会周期性主动断开连接(~3-5s),SDK 需自动重连(已实现),断开期间的输入变化会丢失。

## 3. 帧格式

### 3.1 TCP 封装(仅 40007)

```
55 CC 30 75 | payloadLen(u32 LE) | DUML 包(payloadLen 字节)
```

### 3.2 DUML 包

```
pkt[0]  = 0x55(SOF)
pkt[1]  = 帧总长(如 0x1e = 30)
pkt[9]  = cmdSet
pkt[10] = cmdId
pkt[11 .. 帧长-3] = 数据
pkt[帧长-2 .. 帧长-1] = CRC(2 字节,本通道不做校验)
```

### 3.3 0xAE 遥控器状态包(40007/40009 均有)

数据(`pkt[11..]` → 记为 `data`):

| 偏移 | 类型 | 含义 |
|---|---|---|
| [0..1] | u16 LE | 按键位域:bit5=返航、bit6=拍照全按、bit7=半按(对焦)、bit8=录像 |
| [2..3] | u16 LE | 模式:低 2 位 = **三段开关档位(0/1/2)**;bit2=C1;bit3=C2 |
| [4] | u8 | **右侧无限波轮事件脉冲**:bit7=事件标志(0x80)、bit6=方向、低 6 位=本次步进格数(0x00=无事件) |
| [5..16] | 6×2B | 通道 2..7 = RH/RV/LV/LH/左拨轮/右拨轮,每通道 2 字节 [pos, wrap],wrap 中心 = 4 |

注意:40009 的 0xAE 帧含 [4] 无限波轮字段;40007 的同帧该字段实测亦携带
(用户转动时 0x00 ↔ 0xC2/0xC4 跳变)。SDK 最终连 40007 以获得 10Hz 采样。

### 3.4 无限波轮脉冲语义

```
data[4] = 0x80(事件标志)| bit6(方向)| 低 6 位(本次步进格数)
```

实测样本(慢速转动):

| 原始值 | 解析 |
|---|---|
| 0xC2(194) | 方向 A,2 格 |
| 0xC4(196) | 方向 A,4 格 |
| 0x82(130) | 方向 B,2 格 |
| 0x84(132) | 方向 B,4 格 |

即:**每个非零帧代表一次带方向/格数的转动事件**,0x00 表示本周期无事件。
SDK 按方向累计步进(方向 A:+格数,方向 B:−格数),得到连续值。

## 4. SDK 实现映射

| 协议数据 | SDK 输出 |
|---|---|
| 三段开关档位 0/1/2 | 切档时发 `PhysicalKey.MODE_1/2/3` 的 `KeyAction.TRIGGER` |
| 无限波轮脉冲累计 | `StickSnapshot.freeDial`(连续值,每 50 格 = 满量程 ±1.0,可调) |
| 按键位域(返航/录像/拍照/C1/C2) | 不经此通道分发(与既有 evdev/logcat 通道重复,避免双份事件) |

- 通道类:`internal/TcpInputReader.kt`(40007,断线 3 秒自动重连)
- 接线:`DJIRemoteControlProfile.startTcpReader()`(仅 `useTcpInput=true` 的档案启用)
- 档案:`Rc701Profile`(rc701,启用 TCP)/ `Rm700Profile`(一代,波轮走 evdev ABS_Z/RZ,不冲突)
- 第三方 App 通过统一 Observer/Subscription API 获取三档开关与 `freeDial` 实时状态。
- Assistant:`input_test.rs` 解析 `free-dial` / `mode-switch` 测试项;测试清单已同步

## 5. 调试与已知限制

- `TcpInputReader` 内置诊断日志(tag `TcpInputReader`):连接/alive 心跳/前 40 帧明细,
  `adb logcat -s TcpInputReader` 可观察通道状态;正式发布可酌情精简。
- 静默唤醒:遥控器闲置后服务回到低频模式,首次输入的首格可能丢失(唤醒延迟),
  连续转动不受影响。
- 服务端周期性断连(约 3-5 秒),断开期间的转动增量无法恢复;连续累计以重连后
  首帧位置为基线。
- 量程(50 格满量程)与方向符号为当前实测约定,可按宿主需求调整
  (`TcpInputReader.dialNorm` / 满量程常量)。
- 本协议为逆向实测所得,固件更新可能变更行为;字段语义以实测为准。

## 6. 逆向来源说明

协议通过以下手段实测确认:设备端 `nc 127.0.0.1 40007` 抓取原始字节流、
分段对照实验(摇杆对照组)、以及参考开源工具(joytoserialport,其 DataParser
解析同一端口)的帧结构比对。`TcpInputReader` 为独立编写实现,仅复用协议
格式知识(帧布局/字段偏移),未复制任何第三方代码。
