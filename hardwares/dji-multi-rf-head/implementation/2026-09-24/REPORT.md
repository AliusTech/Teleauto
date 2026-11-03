# 四页原理图实施记录 — incomplete

## 已实际完成

- 保留 POWER / MCU / VTX / LoRa 完整 typed 基线：器件身份、全部物理脚、网络、bbox 与导线；另保存全图 connectivity。
- 对全部 153 件、632 个物理脚建立可重复审计，原稿的 35 件芯片统计不再作为全图覆盖证据。
- 依据 ESP32-C3FH4 官方手册，对 U3 19–24 六个封装内 Flash 脚明确外部 NC。参数文件、Apply journal、`saved:true`、reload 和重开快照均已保留。
- 核实 CH334P 12MHz/无 RREF、DPX 射频双工器与 ESP32 接口错误；已更正文档与生成脚本。
- 下载并渲染检查 RTL8812AU 参考原理图；未将尚未核定的图直接用于接线。
- 更新 DESIGN、PINOUT、TASKS、BOM；原稿/旧脚本保存于 baseline/documents。未修改 PCB、软件 SDK 或其它既有工作。

## 当前实况与未完成事项

| 项目 | 结果 |
|---|---|
| 器件 / 物理脚 | 153 / 632 |
| 联网 / NC / 未连接 | 210 / 6 / 416 |
| POWER bridge-check | 1 个电源桥接、1 个孤立线树，passed=false |
| MCU bridge-check | 1 个孤立标记，passed=false；即使命令退出 0 也不能算通过 |
| MCU / VTX layout-lint | 分别报告 17 / 11 个出界；zoneCheck=not-configured |
| POWER check | 1 个未连接脚、1 个多网导线、48 个 marker-overlap 等；不是布局通过 |
| 官方 DRC | 保存重开后 fatal=0 / error=0 / warn=63 / passed=false；见 official-drc-after.json，仅有聚合结果 |
| 库身份 | 152/153 个实例返回 deviceIdentityError；不能声称库/封装已核定 |
| 电源/前端/USB/GPIO/VTX 完成态 | 未完成；未实施大范围重连、换件或重排 |

“connected”只代表读到网络，不能证明网络正确。尤其 POWER 桥接影响多个原本已联网引脚。

## 阻塞的具体证据

### 电源轨桥接

`POWER-bridge-check.json` 确认 C18:2 与 C19:1 共用导线 `24f94b9ba10be0de`，该线树含 BATTERY 和 BATTERY_5V 两个标记。全图网表把输入、输出和 TPS2117 VIN1/MODE 合并为 BATTERY。

`power-bridge-repair-dry-run.json` 是严格自动接线的只读修复预演，返回 `no safe candidate`；沿引脚外向的路径碰到异网线，其它方向不符合引脚外向约束。没有执行这一失败计划。

不能通过仅改标签、删检查规则或重复短桩来掩盖桥接；需要重算局部布局与线树。

### 完整器件身份缺失

四页 `--include-device-identity` 回读中 152 件无法把 16 位实例封装身份映射为可证明的器件库资产。错误要求 `native META.source plus exact LCSC/model and official asset-library evidence`。

`baseline/U3-identity-resolution.json` 记录 `sch resolve-lcsc` 单件复现：resolvedCount=0。现有保留实例的 compose 输入因此不完整。没有修改原始快照或填入猜测 UUID 绕过校验。

当前 v1.5.2 CLI/daemon 与 v1.5.0 connector 在健康检查中兼容；只读升级检查发现 v1.6.0，但尚未安装，新版是否修复本案未验证。应先恢复可回读的身份解析与局部布局修复能力，再继续落图。

## 验证与重现

```sh
python3 hardwares/dji-multi-rf-head/migration/build/pintables/audit_snapshots.py
python3 hardwares/dji-multi-rf-head/migration/build/pintables/audit_snapshots.py --require-complete
python3 hardwares/dji-multi-rf-head/migration/build/pintables/make_pinout.py
python3 hardwares/dji-multi-rf-head/migration/build/pintables/make_tasks.py
```

第二条命令当前应退出 1，因为仍有 416 个未连接脚。脚本严格区分未知网络、未连接、NC 和 NC/联网冲突；不会把读取失败转换为零问题。

`verification.json` 记录六类引脚状态测试及前后逐脚差异：LoRa 页恰好只有 U3 19–24 的 NC 状态变化，其余物理脚网络、NC 与坐标不变。未完成验收命令确实退出 1。

生成器仅消费 `current-snapshots.json` 指定的保留快照。后续修改 EDA 后必须重新采集并更新清单，不能把重跑脚本当作实时读取。

## 后续实施顺序

1. 恢复真实库资产身份解析和保留实例的局部重排；保留现有 snapshot/journal，禁止清页来绕过问题。
2. 修复 POWER 输入/输出桥接，完成 2–6S 保护/降压和 USB 电流预算，再冻结 GPIO/USB 分配。
3. 核定高功率 PA/收发链与 RTL8812AU 参考设计/BOM，逐模块真实接线。
4. 重跑全部引脚对账、网络/布局检查与官方 DRC，保存重开复验；在此之前不宣称全图完成或可投板。
