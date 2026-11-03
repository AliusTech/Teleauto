# 大疆多协议高频头 — 设计文档

> **2026-09-24 实施审计：未完成，不能投板。** 最新实况和依据见 [执行报告](implementation/2026-09-24/REPORT.md)。
> 当前工作工程为「遥控器射频板」；以下历史“完成/干净/已接线”描述仅代表迁移阶段，不代表电路设计或当前 DRC 通过。
> 632 个物理引脚中，210 个联网、6 个有依据 NC、416 个未连接；已确认 BATTERY/BATTERY_5V 电源桥接。

## 当前确定的设计要求

- 2–6S 电池输入与 USB 供电；USB 单独供电时限功率。
- ELRS 天线口目标 1W；nRF24L01+ / A7105 各 500mW；ESP-NOW 不要求外置大功率。
- RTL8812AU 保留裸芯片。已取得参考图，但其 BOM/射频器件与来源尚未完全核定，未据此落图。
- 所有物理脚须正确联网或有证据的 NC；不允许以单脚标签网或批量 NC 代替电路。
- 本轮已落图并验证的变更只有 U3 19–24 外部 NC；六脚用于封装内 Flash。

## 对旧稿的关键纠正

- U11 输入 BATTERY 与输出 BATTERY_5V 被 C18:2/C19:1 之间的共线导线桥接；TPS2117 VIN1/MODE 因此读为 BATTERY。需修复桥接并重设计高压降压。不能只改网名。
- U4 属 IN_5V→3V3 低压域，不因 6S 需求就一概更换；U11 必须按实际高压输入重新选型。
- CH334P 用 12MHz 晶振且无 RREF 脚；原任务表 24MHz/RREF 要求错误。
- DPX205850DT-4055A1 为射频双工器，不能用作 USB ESD/数据线滤波；USB1/2/3 尚未处置。
- ESP32-C3 microSD 应按 SPI 设计；GPIO18/19 为 USB D−/D+，不是默认 UART0。与主控现有 GPIO4/5 连接需配合固件映射。
- U15 实际物理封装与符号映射仍待核实；不能仅凭 5 脚符号推断完整 TCXO 接法。
- 图纸当前还有出界、标签重叠及孤立图元，器件本体不重叠不等于完整布局通过。

资料来源与适用边界见 [REFERENCES.md](implementation/2026-09-24/REFERENCES.md)。原稿、生成脚本已原样保存于 `implementation/2026-09-24/baseline/documents/`。

---

以下为历史架构与迁移记录；与上述实况冲突时以上述审计为准。

> DJI RC 配套高频头:多协议 2.4G 射频链 + RTL8812AU 数字 VTX,USB 接入 DJI RC 遥控器。
>
> EasyEDA 工程:`DJI RC Plus改造计划` / board「大疆多协议高频头」
> 原理图 **schematic9**(uuid `de9f23bc06e9e3b6`),四页:POWER / MCU / VTX / LoRa
> PCB **PCB8**(uuid `a6b755b4dd3530b7`,与 schematic9 linked)
> 器件总数:153(2026-09-23 实测)

## 1. 系统架构

```
                     USB(DJI RC 遥控器侧)
                            │
        ┌───────────────────┼──────────────────────┐
        │                   │                      │
   电源输入/保护        N32H473CCU7 U2          CH334P U20
   TPS2117 U13 ──►   ┌─────┴─────┐             USB2.0 Hub
   JW5357M U4/U11    │  主控 MCU  │                │
   (buck ×2)         │ SWD/IO/串口│           ┌────┴─────┐
        │            └─────┬─────┘           │          │
        │           SPI/控制总线              │          │
        │        ┌──────┬───┴───┬──────┬─────┘          │
        │        │      │       │      │            RTL8812AU U21
        │      U3     U5      U19    U27            (2T2R WiFi,
        │   ESP32-  SX1281  NRF24  A7105            5.8G VTX,
        │   C3FH4  (ELRS)  (玩具)  (FlySky)          40M 晶振 X3)
        │        │      │       │      │                │
        │        └──────┴───┬───┴──────┘                │
        │              SKY13414 U29               IPEX 天线 ×2
        │              (SP4T 天线开关)               (视频链路)
        │                   │
        │              AT2401C U17(2.4G PA,nRF24 链路)
        │                   │
        └──────────── 2.4G 天线(控制链路)
```

- **控制链路(2.4G)**:四合一射频共享天线,SKY13414 SP4T 分时切换,单协议同一时刻在线。
- **视频链路(5.8G)**:RTL8812AU 经 CH334P Hub 挂在 USB 总线上,wifibroadcast 类数字 VTX/VRX。

## 2. 协议映射(四合一射频)

| 芯片 | 位号 | 主要协议/生态 | 典型用途 |
|---|---|---|---|
| SX1281 | U5(SX1281IMLTRT,52M TCXO U15) | **ExpressLRS 2.4G** | FPV 穿越机、长距离/低延迟 RC |
| nRF24L01 | U19(NRF24L01P-R,QFN-20,16M 晶振 X6;PA=U17 AT2401C) | Bayang、Syma、Hisky、WLtoys、MJX、Cheerson、JJRC 等 | 国产玩具/微型无人机 |
| A7105 | U27(QFN-20,12M 晶振 X9) | FlySky AFHDS / AFHDS2A | 富斯遥控器、接收机、国产航模 |
| ESP32-C3FH4 | U3(40M 晶振 X5,+microSD CN3) | ESP-NOW / Wi-Fi / BLE | DIY 遥控、机器人、智能车、物联网 |

四芯片 SPI 共总线(SPI_SCK/MISO/MOSI + 独立 NSS),由 N32H473 统一调度。

## 2.0 工程与页面映射(2026-09-24 干净重建完成)

**工作工程:「遥控器射频板」**——四页已全部干净重建,网表引擎/DRC 恢复:

| 项 | UUID | 状态 |
|---|---|---|
| 工程 遥控器射频板 | `c15f2ba144e644eca844ab18ebaf753e` | 工作工程 |
| Board1(schematic1 ↔ PCB1) | sch `280977e0...` / pcb `3e4fbf69c50aec75` | 绑定完好 |
| POWER 页 | `7620d079e8618c97` | 23 件,**63/63 网已接线(引擎确认)** |
| MCU 页 | `932d3fd21946e404` | 49 件(含 USB Hub 组),**102/102 网已接线** |
| VTX 页 | `364c5031568c17c2` | 30 件(8812AU 图传组),待接线(WIP) |
| LoRa 页 | `6da79bf818384203` | 51 件(U19=NRF24L01P-R),**45/45 网已接线** |

**引擎恢复与干净重建(2026-09-24)**:复制内容携带几何冲突(引脚坐标重合/本体互压/页外)
使网表导出与 DRC 管线损坏。四页经"清空→防冲突放置(三审计)→幂等挂网"重建后,
**导出恢复、netlist 视图恢复、DRC 四页 致命 0 / 错误 0**(64 警告=射频/VTX 链待接线引脚,
属设计进行中)。分工:**VTX=图传模块(RTL8812AU);USB Hub(CH334P+U14+U8/U9+R23)在 MCU 页**。
原 DJI RC Plus改造计划工程保留为历史。详见 migration/REPORT.md §9。

## 2.1 全部 IC/芯片清单(按图页,2026-09-24 更新)

### POWER 页(4 颗)

| 位号 | 型号 | 厂商 | 作用 | 封装 | C 号 |
|---|---|---|---|---|---|
| U1 | ZX-SH1.0-4PWT | MEGASTAR | 电源/USB 输入连接器(4P 1.0mm 侧插):接入主机 USB 5V 供电与 USB_DP/DN 差分数据 | CONN 4P-1.00 | C7430446 |
| U13 | TPS2117DRLR | TI(德州仪器) | 电源复用器 Power Mux(1.6–5.5V / 4A):USB 5V 与电池 5V 轨二选一自动切换(优先级可配),输出 IN_5V | SOT-583-8 | C22399676 |
| U4 | JW5357MSOTB | Joulwatt(杰华特) | 同步降压 DC-DC → **3V3 主数字轨**(绝对最大 20V,6S 输入需换型,见已知问题) | SOT-23-6 | C5197556 |
| U11 | JW5357MSOTB | Joulwatt(杰华特) | 同步降压 DC-DC → **BATTERY_5V 5V 轨**(供射频功放/VTX;同上耐压限制) | SOT-23-6 | C5197556 |

### MCU 页(10 颗)

| 位号 | 型号 | 厂商 | 作用 | 封装 | C 号 |
|---|---|---|---|---|---|
| U2 | N32H473CCU7 | Nations(国民技术) | **主控 MCU**:系统控制核心——SPI 主机调度四合一射频、UART 连 ESP32-C3、USB 通信、SWD 调试、IO 扩展与前端控制 | QFN-48 | C49191475 |
| U20 | CH334P | WCH(沁恒) | USB 2.0 Hub 控制器:上行接 U14 主机口,下行挂载 RTL8812AU 图传(其余口备用/可选 ESP32 USB) | QFN-16 | C5373042 |
| U14 | ZX-SH1.0-4PWT | MEGASTAR | USB 上行连接器(主机侧数据+供电) | CONN 4P-1.00 | C7430446 |
| USB2 | DPX205850DT-4055A1 | TDK | 集成 ESD 保护+滤波阵列:USB 差分对防护(器件侧) | 滤波阵列 | C2847617 |
| USB3 | DPX205850DT-4055A1 | TDK | 同上(连接器侧) | 滤波阵列 | C2847617 |
| U8 | RCLAMP0521T-ES | Nexperia(安世) | ESD 保护二极管对:USB_DP 静电防护 | DFN1006-2L | C5180263 |
| U9 | RCLAMP0521T-ES | Nexperia(安世) | ESD 保护二极管对:USB_DN 静电防护 | DFN1006-2L | C5180263 |
| Q1 | AO3401A | AOS(万代) | P 沟道 MOS(-30V/4A):电源闸门/防倒灌 | SOT-23 | C15127 |
| X2 | SX3B8.000F1010F30 | 星晶 | **U2 主时钟晶振 8MHz** | 3225 4P | C7302001 |
| X1 | SX3B8.000F1010F30 | 星晶 | 8MHz 晶振(疑为 X2 重复件,待裁决删除) | 3225 4P | C7302001 |
| X7 | 40MHz 3225 | Generic | 40MHz 晶振(疑为 RTL8812AU 时钟残留,与 X3 重复,待裁决) | 3225 4P | C47089401 |
| TP1 | 测试点 | — | CHIP_EN 测试点 | — | — |

### VTX 页(4 颗)

| 位号 | 型号 | 厂商 | 作用 | 封装 | C 号 |
|---|---|---|---|---|---|
| U21 | RTL8812AU-CG | Realtek(瑞昱) | **数字图传核心**:2T2R 双频(2.4G/5.8G)WiFi USB 控制器——经 CH334P 挂 USB,wifibroadcast 类视频链;内部 SPS 需外置电感 L3+反馈 R18,两路 RF 独立双天线 | QFN-76 | C2928090 |
| X3 | 40MHz 3225 | Generic | RTL8812AU 时钟晶振 | 3225 4P | C47089401 |
| Q3 | AO3401A | AOS(万代) | VTX 电源闸门(V33_8812 轨前级,上电默认关) | SOT-23 | C15127 |
| TP2 | 测试点 | — | VTX 调试测试点 | — | — |

### LoRa 页(10 颗,四合一射频链)

| 位号 | 型号 | 厂商 | 作用 | 封装 | C 号 |
|---|---|---|---|---|---|
| U3 | ESP32-C3FH4 | Espressif(乐鑫) | **四合一 #4**:RISC-V SoC(内置 4MB flash),ESP-NOW / Wi-Fi / BLE 通信;兼 microSD 存储、与主控 UART 互联 | QFN-32 | C2858491 |
| U5 | SX1281IMLTRT | Semtech | **四合一 #1**:2.4G LoRa/FLRC/GFSK 收发器,ExpressLRS 2.4G 远距离低延迟控制链 | QFN-24 | C2151551 |
| U19 | NRF24L01P-R | NORDIC | **四合一 #2**:2.4G GFSK 收发器,玩具级多协议(Bayang/Syma/Hisky/WLtoys/MJX/Cheerson/JJRC 等) | QFN-20-EP | C8791 |
| U27 | A7105 | AMICCOM(联杰) | **四合一 #3**:2.4G FSK 收发器,FlySky AFHDS / AFHDS2A 遥控协议 | QFN-20 | C126376 |
| U29 | SKY13414-485LF | Skyworks | SP4T 射频开关:四颗射频芯片分时共享一根 2.4G 天线的源选择(V1/V2/V3 控制) | QFN-14 | C255353 |
| U17 | AT2401C | 杭州中科微 | 2.4G 射频功放(+22dBm≈160mW):nRF24 链路 PA;1W 方案中作驱动级或移除(待选型) | QFN-16 | C783588 |
| U15 | ABDFTCXO-52.000MHz | Abracon | 52MHz TCXO 温补晶振:SX1281 高稳定时钟源 | OSC 5P | C568572 |
| USB1 | DPX205850DT-4055A1 | TDK | ESD 保护+滤波阵列(LoRa 页 USB 线防护) | 滤波阵列 | C2847617 |
| Q2 | AO3401A | AOS(万代) | RF 电源闸门(射频链供电,上电默认关) | SOT-23 | C15127 |
| X5 | Q22FA12800101 | NDK | ESP32-C3 时钟晶振 40MHz | 2016 4P | C255898 |
| X6 | X322516MLB4SI | TYC(台湾晶技) | nRF24L01+ 时钟晶振 16MHz(CL=9pF) | 3225 4P | C13738 |
| X9 | ABM8AIG-12.000MHZ | Abracon | A7105 时钟晶振 12MHz | 3225 4P | C1986775 |
| CN3 | MUP-M627-1 | HRO | microSD 卡座:ESP32 SDMMC 存储(飞行记录/配置) | MICRO-SD | C5205362 |

> 合计 28 颗(含连接器/晶振/测试点;纯 IC 19 颗)。四合一 = U3+U5+U19+U27,经 U29 共享天线,
> 遥控模式目标天线口 1W(前端选型中,TASKS T2)。逐步接线状态见 [PINOUT.md](PINOUT.md)。

## 2.2 U3 ESP-NOW 模式设计(参考 zvldz/ESP32-UART-Bridge 架构)

> 参考:[zvldz/ESP32-UART-Bridge](https://github.com/zvldz/ESP32-UART-Bridge)(MIT)。
> 该工程本身**无 ESP-NOW**(其无线链为 UDP/BLE/BT-SPP)且要求双核(不支持 C3)——
> 我们借鉴其**桥接架构与交互范式**,在 U3(ESP32-C3FH4,单核)上实现 ESP-NOW 模式。

### 架构(UART ⇄ ESP-NOW 透传桥)

```
N32H473(U2) --USART1(C3TX/C3RX, 已接线)--> ESP32-C3(U3)
                                                │ 软件任务(FreeRTOS, 单核分时):
                                                ├─ uart_bridge:协议感知解析
                                                │   · CRSF@420k(ELRS 帧格式,首选)
                                                │   · RAW 透传(按 3ms 空闲帧切包)
                                                │   · MAVLink v2(可选)
                                                ├─ espnow_link:收/发队列,重传+RSSI
                                                ├─ bind/led:绑定状态机 + 指示灯
                                                └─ config:Web 配置页(AP 模式)/NVS 存储
                                                ▼
                                   ESP-NOW 对端(机载 ESP32 接收机/其它 peer)
```

### 与参考工程的映射与裁剪

| 参考工程(vzldz) | 本设计(C3 裁剪版) |
|---|---|
| 设备1/2/3 多 UART 路由 | **仅 USART1 一路**(U2 ↔ U3,C3TX/C3RX 网已接) |
| UDP 多目标(≤4 IP) | **ESP-NOW 多 peer(≤4 MAC,NVS 存储)**;广播发现 |
| BLE NUS / BT SPP | 不做(与 ESP-NOW 模式互斥,C3 无 BT Classic) |
| CRSF 解析@420k 8N1 | **保留**(与 ELRS 链一致的通道/链路质量帧) |
| RAW/MAVLink/SBUS 模式 | RAW/MAVLink 保留;SBUS 不需要(有独立 RF 链) |
| Web 配置 + OTA | **保留**(AP 模式配置页:波特率/协议/peer 管理;OTA 备用) |
| GPIO0 BOOT 三击切换模式 | **采纳**:绑定/模式切换交互复用 BOOT(GPIO9)或独立按键 |
| GPIO21/48 RGB LED | **需新增 1 颗状态 LED**(C3FH4 无内置 RGB):连接/绑定/数据流指示 |

### 绑定与状态机(设计要点)

- **绑定**:长按 BOOT 3s(或配置页"添加 peer")进入 60s 绑定窗口,对端同理 → 交换 MAC 存 NVS;
  支持定点(1 对 1)与广播(1 对多)两种 peer 模式。
- **LED 语义**(参考工程配色习惯):慢闪=等待绑定;常亮=已连接;快闪=数据流;双闪=配置模式。
- **失败回退**:ESP-NOW 连续丢包 >N → LED 快闪告警,U2 可经 UART 查询链路状态帧。

### 硬件需求与变更(并入 TASKS T8)

1. **新增器件(候选)**:绑定/模式按键 ×1(SW-SMD,建议接 GPIO0,10k 上拉)+ 状态 LED ×1(+1k 电阻,
   建议空闲 GPIO,如 GPIO3)→ 走器件变更清单。
2. 已满足:USART1(C3TX/C3RX)、40MHz 时钟(X5)、供电 3V3、BOOT(GPIO9)strapping 上拉。
3. 固件基线:PlatformIO,ESP-IDF/Arduino esp-now API;参考工程的 CRSF 解析与 Web 配置代码
   (MIT)可移植,任务布局按单核分时改写(其双核要求是任务布局约束,非硬性)。

## 3. 电源树

```
电源输入 U1(SH1.0 4P)
   │
   ├─ TPS2117 U13(电源自动切换 mux)
   │     ├─► JW5357M U4(buck) ──► 主数字轨
   │     └─► JW5357M U11(buck) ──► 射频/VTX 轨
   │           (L9/L10 APS252012M3R3F,22µF×4 CGA0805…)
   └─ Q2/Q3(AO3401A 电源闸门,按链路使能)
```

跨页电源网(保留原网名,不合并相近名):见 §6。

## 4. 时钟树

| 时钟 | 位号 | 服务对象 |
|---|---|---|
| 52.000MHz TCXO | U15(ABDFTCXO…-L-2-T5) | SX1281 U5 |
| 16MHz 晶振 | X6(X322516MLB4SI) | NRF24L01 U19 |
| 12MHz 晶振 | X9(ABM8AIG-12.000MHZ) | A7105 U27 |
| 40MHz 晶振 | X5(Q22FA12800101) | ESP32-C3 U3 |
| 40MHz 晶振 | X3(40MHZ/3225/12PF) | RTL8812AU U21 |
| 40MHz 晶振 | X7 | N32H473 U2(高速时钟) |
| 8MHz 晶振 | X1/X2(SX3B8.000…) | N32H473 U2(低速时钟) |

## 5. 原理图页组织(schematic9 四页,2026-09-23 迁移)

| 页 | uuid | 内容 | 器件数 | 状态 |
|---|---|---|---|---|
| POWER | `0c2c6a6f3ebfecc3` | 电源输入、保护、稳压(power-io.P1 整页迁入) | 23 | **完成**:62/62 引脚网络 |
| MCU | `e69fa11bb2c66323` | N32H473、IO、SWD(mcu.P9 + staging.P8 的 U2 组) | 44 | **完成**:92/92 引脚网络 |
| VTX | `0d43196f0cf83159` | RTL8812AU 及外围(vtx.P6 + staging.P8 的 U21 组) | 35 | **完成**:10/10 引脚网络 |
| LoRa | `46c48981caa3c9f9` | SX1281、nRF24L01、A7105、ESP32-C3 及 RF 前端(P2+P3+P4+P5) | 51 | **完成**:45/45 引脚网络 |

> 页名 LoRa 为用户指定名,实际承载全部四合一射频。8 个旧页已删除(全部快照于 `migration/snapshots/`);
> 连接基准为 PCB8 焊盘网表(原理图旧页从未成网);PCB 匿名网 `$1Nxx` 在原理图侧更名为 `PCB_1Nxx`。
> 执行细节、工具 bug 与恢复步骤见 `migration/REPORT.md`。

## 6. 跨页网络(迁移保持约束)

原 21 条跨页网(源自 Schematic8 单页读取记录,迁移前后逐引脚对账):
`3V3` `GND` `SPI_SCK/MISO/MOSI` `SPI_NSS1/2` `RST1/2` `BUSY1/2` `IRQ1/2`
`USART1_TX/RX` `USB_DP/DN/5V` `1.0_5V` `IN_5V` `XTA`

约束:
- 电源网族 `BATTERY_5V / IN_5V / 5V / 1.0_5V / USB_5V` **各自独立命名,不因相近合并**;
- 匿名网(`$1N…`)迁移后可能重编号,按**端点集合(ref.pin)等价**验收;
- 保留全部 NC 标识与器件 uniqueId、位号、型号、封装及 PCB8 对应关系。

## 7. 沿革

1. **Schematic7**(`hardware/Netlist_Schematic7_2026-09-08.tel`):DJI RC Plus 内置射频模块逆向网表,
   N32H473 + ESP32-C3 + **双 LR1121**(旧方案)+ MXD8721 开关 + AT2401C PA。
2. **Schematic8**:单页整板(117 位号/488 引脚/124 网络),读取与七页拆分方案见
   外部记录(Codex 2026-09-22 outputs)。
3. **Schematic9(现行)**:双 LR1121 方案废弃,替换为**四合一射频**(SX1281/nRF24L01/A7105/ESP32-C3),
   RF 开关换 SKY13414,新增 RTL8812AU VTX + CH334P Hub;由 8 个旧页整理为 POWER/MCU/VTX/LoRa 四页。

## 8. 已知问题清单

| # | 问题 | 影响 | 处置建议 |
|---|---|---|---|
| 1 | ~~U19 = NRF24L01(C1558392,无货)~~ **已于 2026-09-23 换绑为 NRF24L01P-R(C8791,NORDIC,QFN-20-EP,引脚兼容,原位姿)**,多协议固件 250kbps 档玩具协议所需的 + 版 | 已解决 | — |
| 2 | **U11(JW5357M)原标注 2-6S(25.2V)超数据手册绝对最大 20V** | 过压损坏风险 | 复核输入范围标注/分压,或换耐压更高的 buck |
| 3 | 36 个无网络引脚中 8 个未按手册判定 NC | 悬空脚定义不完整 | 逐脚按数据手册补 NC 或接线 |
| 4 | staging.P8 曾混装 U2+U21(主控与 VTX 同页) | 已随四页迁移消除 | — |

## 9. 引脚定义与任务表

- **[PINOUT.md](PINOUT.md)**:35 颗芯片逐引脚定义(编号/名称/类型/当前网/设计建议)+ 悬空统计。
- **[TASKS.md](TASKS.md)**:结合 PINOUT 悬空数据的接线任务分解(T0–T10,含依赖/阻塞/验收与执行顺序)。

完整逐引脚定义(编号/名称/类型/当前网/设计建议)见 **[PINOUT.md](PINOUT.md)**——
按四页 35 颗芯片分表,含悬空脚统计(总览表)与接线路由草案,是接线设计的工作底稿。

## 10. BOM

见 [BOM.md](BOM.md)(153 器件,含立创 C 号与四页归属)。
