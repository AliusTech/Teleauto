# Schematic9 四页迁移报告(2026-09-23)

状态:**四页迁移完成(降级恢复路径),PCB 网络挂接 209/209 全通**。153 器件全部落位
四页;PCB8 权威网表全部引脚网络已几何验证落位(POWER 62/62、LoRa 45/45、MCU 92/92、
VTX 10/10),全部页面已显式保存。POWER 原 3 个被困脚已通过挪件重接(C15/L9)与
向下坐标挂接(U1:5)补齐。唯一剩余悬空:R15(PCB 无网的 WIP 件,2 脚,不臆造网络、
不自动打 NC)。

## 1. 目标与事实基线

- 目标:schematic9 由 8 个旧页(power-io.P1 / esp32-sd.P2 / rf-rx.P3 / rf-sw.P4 /
  rf-pa.P5 / vtx.P6 / staging.P8 / mcu.P9,153 器件)整理为 POWER / MCU / VTX / LoRa 四页。
- **关键事实(改变了迁移语义)**:旧页从未物化网表——`sch connectivity` 逐页导出
  nets=0 / connections=0;导线不接触引脚;`sch bridge-check` 报 "Unknown wire contact"。
- **连接事实唯一权威来源 = PCB8 焊盘网表**(`snapshots/pcb8-dump.json`,123 器件/75 网)。
  射频链(U5/U19/U21/U27/U29/U17 及其外围)在 PCB 上焊盘无网络——属未设计完成,
  迁移中保持引脚悬空,不臆造连接。
- 位号对映:113 个同位号直映;PCB 旧位号 U22/U23/U25 = 原理图新位号 U5/U19/U21;
  PCB-only 10 件(R13/RF1/RF2/U10/U7/U24/U26)为被替换旧方案件,不迁移;
  原理图独有 40 件为新增未导入件。

## 2. 四页归属(核定结果)

| 页 | 器件数 | 来源 |
|---|---|---|
| POWER | 23 | power-io.P1 整页(TPS2117 + 2×JW5357M + 配套) |
| LoRa | 51 | esp32-sd.P2 + rf-rx.P3 + rf-sw.P4 + rf-pa.P5(四合一射频 + RF 前端) |
| MCU | 44 | mcu.P9 整页 + staging.P8 的 U2 组(U2/X1/USB2/CN4/R2/R8/R16/R17) |
| VTX | 35 | vtx.P6 + staging.P8 的 U21 组(V33_8812/V12_8812 去耦阵列、LX_SPS 电源、X3、RF 匹配) |

staging.P8 拆分依据:页面渲染与网络标签(V33_8812 ×12、V12_8812 ×7、LX_SPS/FB_SPS 等)
——暂存页 100nF 阵列是 **U21 的去耦**,非 U2(U2 去耦在 mcu.P9)。

## 3. 执行记录

1. **Phase 0 快照**:12 页 `sch list` + 逐页 connectivity + PCB8 dump + board 绑定 +
   其它 7 个 board 页快照 → `snapshots/`(恢复所需全部数据)。
2. **Phase 1 离线计划**:PCB pin→net 映射(209 条)→ 分区 → layout-plan --zones →
   sheet-plan → render → compose 四页队列全部编译通过(POWER 4 模块/LoRa 7/MCU 3/VTX 3)。
   执行中被 §5-Bug1 阻塞(compose 队列保留在 build/<页>/apply.json,工具修复后可升级)。
3. **页面删除**:8 个旧页已全部删除(schematic9 现仅 4 目标页;偏差说明见 §5-Bug1)。
4. **降级恢复(typed 路径,最终完成)**:`sch materialize` 放件队列 +
   `sch autoconnect` 单脚顺序挂网 + 少量坐标式 `sch connect --x/--y`(带端点安全核查)。
   - POWER:23 件 + 62/62 网络(C15/L9 因左右出口被真实异网引线封死,挪件重接;
     U1:5 按引脚朝向向下坐标挂接)。
   - LoRa:51 件 + 45/45(U3 右排曾受 U5 间距锁死,U5/Q2 挪位后全通)。
   - MCU:44 件 + 92/92(U2 右排、CN2/C55/TP1 由坐标式挂接收尾)。
   - VTX:35 件 + 10/10(U14 曾落于页外,移回后收尾)。
   - 出界审计:曾 27 件网格溢出页外,已全部移回并补接其引线;终检 0 出界。
5. PCB8 全程未动;board「大疆多协议高频头」(PCB8 ↔ schematic9)绑定完好。
6. **验证方法**:窗口重载后 manufacture netlist 视图损坏(sch read 恒 0,
   save+reload 不恢复;POWER 在损坏前曾以网表视图确认 59/62,与几何验证一致),
   故终检采用 `build/geom_verify.py` 几何验证:导线端点 == 引脚坐标,
   且对端 == 目标网名标记锚点。四页验证命令见 build/ 目录。

## 4. 网名映射约定

平台保留 `$` 前缀(netport 挂 `$1N6` 会被改写为自动名 `$12Nxxx`)。
PCB 匿名网在原理图侧统一重命名:`$1Nxx → PCB_1Nxx`(各页 restore-connect-spec.json)。
命名网(GND/3V3/SPI_*/USART1_*/…)保持原名。PCB_1Nxx 语义对照:
$1N5/$1N9=U4(3V3 buck)SW/EN、$1N6/$1N10=U11(BATTERY_5V buck)SW/EN、
$1N13=U4 输入、$1N14=U11 输入、$1N94=TPS2117 PR1、$1N91=TPS2117 ST,余见 spec。

## 5. 工具 bug 证据(需上游修复)

- **Bug 1(阻断 compose 路径)**:`schematic.components.list {allPages,tagPages,designatorsOnly}`
  连接器返回全量数据(designatorsOnly 未实现),~1.03MB 超传输上限截断,
  apply 的 verify-project-unique-designators 门禁解析失败。本项目其它 board 989 件使其恒超限。
  复现:`easyeda call schematic.components.list --payload '{"allPages":true,"tagPages":true,"designatorsOnly":true}'`
- **Bug 2(netlist 视图损坏)**:place 几何守卫回读时 `schematic.text.list` 悬挂导致
  连接器队列永久堵塞;用户侧重载窗口后连接器回归,但 manufacture netlist 视图
  (sch read 的 pin→net、bridge-check 线树)不再计算,save/close/reopen 均不恢复。
  页面图元(器件/导线/标记)完好,几何验证可用。疑似需重启 EasyEDA Pro 应用恢复。
- **Bug 3(噪声)**:autoconnect 批处理高负载下部分落地且报告不一致(假失败定律);
  `sch modify` 亦偶发假失败(报 ok:false 但已落地)。单脚顺序模式 + 读回验证可靠。
- **平台行为**:`$` 前缀网名被保留为自动命名;页外(y<0)放置不被 materialize 拒绝。

## 6. DRC 与"悬空"处置(2026-09-23 追加)

用户在应用内跑设计检查得:致命 12 / 错误 0 / 警告 63 / 信息 1。
排查结论:
- `sch drc`(SDK)对 schematic9 任意页返回同一聚合数字且**对实际修复零响应**
  (修复 3 个悬空脚前后均 12/63)——系 Bug 2(文档级 netlist 引擎损坏)产生的
  陈旧聚合结果;SDK 无逐项明细接口。
- `sch check`(几何重建)逐项可见:悬空导线 0、多网线 0、交叉 0;
  悬空引脚仅 R15 ×2(无网 WIP 件);其余为标记视觉重叠(可后续整理版式)与
  分区框/图签缺失(compose 升级时解决)。
- **需要重启 EasyEDA Pro 应用**让 netlist 引擎重算,再在应用内复跑设计检查;
  若仍有致命项,以应用面板逐项清单为准继续修复。

## 7. 后续步骤(原)

1. **netlist 视图恢复**:重启 EasyEDA Pro 应用后,用
   `easyeda sch read --project "DJI RC Plus改造计划" --page <页uuid> --stay --no-check`
   复核引脚网(应与 geom_verify 结果一致);随后 `sch nets`/`sch check` 审计。
2. **PCB 同步**:netlist 恢复并核对后,如需把四合一新器件导入 PCB8(当前 PCB8 仍是
   旧 123 件状态),在 EasyEDA 中对 board「大疆多协议高频头」执行变更导入(agent 未执行)。
3. **Bug 1 修复后可选升级**:compose 完整重建(分区框图+真实接线树,替代网格降级版式):
   `easyeda sch apply hardwares/dji-multi-rf-head/migration/build/<PAGE>/apply.json --yes`
4. **POWER 页 3 脚补接**:C15:1(PCB_1N6)、L9:2(3V3)、U1:5(GND)。

## 8. 遗留问题(接线阶段处理)

1. R15(无网 WIP 件)2 个悬空引脚:待接线设计时定网络;不自动 NC(规则:未知连接不得标 NC)。
2. 射频链全部引脚悬空(忠实现状);旧页网络命名意图(SX_NSS/SX_DIO1/RF_NRF_M/
   TCXO_OUT/ANT2_N 等)存于旧页快照的 netport 组件内。
3. ~~U19 换绑建议~~:已执行——2026-09-23 由 NRF24L01(C1558392,无货)换为 NRF24L01P-R(C8791),原位姿/位号保持,LoRa 页已保存。X1(8M)/X7(40M)
   疑为 X2/X3 的重复暂存件;U11 输入范围标注超 JW5357M 绝对最大值——见 DESIGN.md。
4. 版式为"功能分组网格"(降级路径),无分区框/标题栏/接线树;compose 升级后改善。


## 9. 2026-09-24 引擎恢复与四页干净重建(新工程「遥控器射频板」)

- 用户创建新工程「遥控器射频板」(复制 schematic9 内容)并指定分工:VTX=8812AU 图传、USB Hub 归 MCU 页。
- 复制内容使新工程导出/检查管线同样损坏(应用内导出亦失败)。
- **毒源定性**(逐页/逐类二分):几何冲突类内容——**引脚坐标完全重合(如 L8.2×USB1.3,短路类=致命)、
  本体互压、页外摆放**——使 lceda 的网表导出与 DRC 管线整体失效;另存在会话级降级
  (放置守卫假失败、零长幽灵线生成→堵塞整页写入)。
- **恢复配方(已验证)**:清空 → 防冲突放置(三审计:引脚重合/本体互压/出界)→
  幂等顺序挂网(单脚 autoconnect+坐标 connect)→ 每页存+重开复位守卫。
- **结果**:四页 153 件全部干净重建;POWER 63/63、MCU 102/102、LoRa 45/45 网接线
  (netlist 引擎视图确认);VTX 30 件待接线(WIP);**DRC 四页 致命 0 / 错误 0**;网表导出恢复。
- 工具经验沉淀:place-loop2(放置循环+幽灵清扫)、防冲突放置审计、geom_verify 几何验证器,
  均存于 migration/build/。
