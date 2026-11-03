# 资料核实记录

以下仅说明资料支持的事实，不代表对应电路已落图。

| 资料 | 核实结论 | 状态 |
|---|---|---|
| [ESP32-C3 官方手册 v2.4](https://documentation.espressif.com/ESP32-C3_Datasheet_en.pdf) §2.6 / Table 2-12 | ESP32-C3FH4 的 19–24 物理脚已连接封装内 Flash，不得用于其他用途；本轮明确外部 NC | 已落图、保存、重开回读 |
| 同上，引脚表与外设章节 | GPIO18/19 为 USB D−/D+；UART0 默认 RX/TX 为 GPIO20/21；原稿的四位 SDMMC 描述不适用于 C3 | 文档已纠正；microSD 与主控接口待落实 |
| [TDK DPX205850DT-4055A1](https://product.tdk.com/en/search/rf/rf/diplexer/info?part_no=DPX205850DT-4055A1) | 器件类别为 RF Diplexer，不是 USB ESD/滤波阵列 | USB1/2/3 尚未换件；用途必须重新核定 |
| [沁恒 CH334/335 手册 v2.8，厂商 PDF 的 LCSC 镜像](https://datasheet.lcsc.com/datasheet/pdf/0e151f854137982e44eb17164dbe132c.pdf?productCode=C4154405) §1.4 | CH334P 使用 12MHz 晶振；24MHz 属 CH334X；CH334P 无 RREF 引脚 | 未落图；删除错误 24MHz/RREF 任务要求 |
| [TI TPS2117 官方手册](https://www.ti.com/lit/ds/symlink/tps2117.pdf) | VIN1/VIN2 推荐 1.6–5.5V，绝对最大 6V；不能承受原始 6S；MODE 接 VIN1 为优先模式 | POWER 已发现 BATTERY/BATTERY_5V 桥接，未修复 |
| [Abracon ABDFTCXO 官方资料](https://abracon.com/datasheets/ABDFTCXO_ABDFVCTCXO-Series.pdf) | 双输出器件资料标为 10pin package；现有 U15 符号仅 5 引脚，需核对物理焊盘映射，不能把符号当完整封装证明 | 未核定，不据此补线 |
| [RTL8812AU 参考图公开附件](https://github.com/aircrack-ng/rtl8812au/files/14206886/RTL8812AU.Schematics.pdf) | 已下载并渲染查看：图号 01-UD812A14-1V0；包含电源、晶振、USB、两路 RF；SP3T/LNA 等缺明确型号，不是完整 BOM | 保存在 references/RTL8812AU-reference.pdf；来源真实性与适用版本仍需交叉核实 |

## 参考图的使用限制

RTL8812AU 附件来自公开讨论而非已验证的厂商发布渠道，不单独作为逐脚接线权威。取得该图不等于补齐了参考设计：RF 开关/LNA 的型号、无源器件规格及电源选项仍需确定。未将附件中的参数直接应用到 EDA。

## 工具证据

- CLI/daemon v1.5.2，connector v1.5.0，宿主 3.2.166；当前版本健康检查兼容。
- `update --check` 查到 v1.6.0；本轮未更新运行时。更新检查中的 daemon mismatch 是相对新版本，不表示当前 CLI/daemon 彼此不兼容。
- 152 个器件库身份解析缺失；`baseline/U3-identity-resolution.json` 保存单件复现。新版是否解决这一问题尚未验证，不能承诺升级即恢复。
- 禁止用清空重建或手工替换快照中的 UUID 绕过身份门禁。
