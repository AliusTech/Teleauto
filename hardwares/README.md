# hardwares/ — DJI RC 配套硬件设计档案

本目录独立维护 DJI RC 遥控器**配套高频头硬件**的设计文档、BOM 与工程快照。
与 `hardware/`(内置射频模块网表、mLRS 参考资料)分开:那是对原机的逆向资料,这里是自研配套硬件。

## 板卡

| 板卡 | EasyEDA 工程 | 原理图 | PCB | 状态 |
|---|---|---|---|---|
| [大疆多协议高频头](dji-multi-rf-head/DESIGN.md) | DJI RC Plus改造计划 | schematic9(POWER/MCU/VTX/LoRa 四页) | PCB8 | 原理图四页整理中 |

## 目录约定

- `<board>/DESIGN.md` — 设计文档:架构、协议映射、电源树、时钟树、接口、已知问题。
- `<board>/BOM.md` — 位号级 BOM(型号 / 立创 C 号 / 封装 / 页归属)。
- `<board>/migration/` — easyeda-agent 迁移/重建记录与快照(只增不改,原始快照不覆盖)。
- 第三方参考(mLRS 等)不放在本目录,仍在 `../hardware/`。

## 变更规则

- 只通过 easyeda-agent typed CLI 修改 EasyEDA 工程;**只允许修改「大疆多协议高频头」部分**
  (schematic9 各页),其它 board/schematic/PCB 一律只读;PCB8 仅在明确授权后才可 import-changes。
- 每次工程结构变更前先落盘快照(`migration/snapshots/`),失败即停,凭快照重放恢复。
- 器件身份以库 UUID + 立创 C 号为准;位号变更须同步更新 BOM.md。
