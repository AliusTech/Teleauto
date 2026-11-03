#!/usr/bin/env python3
"""生成 TASKS.md:结合 PINOUT 实时悬空脚数据的接线任务分解表。"""
import json, os
BASE = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
INV = json.load(open(os.path.join(BASE, 'migration/build/pintables/pin-inventory.json')))
LIVE = json.load(open(os.path.join(BASE, 'migration/build/pintables/live-nets.json')))
PAGE_OF = {}
for page, refs in json.load(open(os.path.join(BASE, 'migration/build/placement-members.json')))['pages'].items():
    for r in refs: PAGE_OF[r] = page
PAGE_OF.update({'U20':'MCU','U14':'MCU','U8':'MCU','U9':'MCU','R23':'MCU'})

def floating(ref):
    info = INV.get(ref)
    if not info: return []
    return [p['name'] or p['pin'] for p in info['pins'] if not LIVE.get(f"{ref}:{p['pin']}")]

def fl(ref, limit=None):
    f = floating(ref)
    if not f: return '—'
    s = ','.join(f)
    return s if limit is None else (s[:limit]+'…' if len(s)>limit else s)

TASKS = [
 ('T0','基线(已完成)','四页干净重建 153 件;骨干网 210/210 接线;DRC 致命0/错误0;网表引擎恢复;PINOUT.md 产出','全部','—','✅ 完成','REPORT.md §9; PINOUT.md'),
 ('T1','决策:2-6S 电源选型','U4/U11(JW5357M,绝对最大20V)必换;候选 SY8303AIC 40V/3A;定轨预算(PA峰值3A/VTX 0.5A/数字0.5A)与 TVS/防反接','POWER 全链','T7','⏳ 待选型','变更清单+新电源树'),
 ('T2','决策:1W 射频前端选型','候选 SKY85303-21(LCSC有货)/CB5309/RFX2402E(驱动级)+1W末级两级;定 AT2401C 去留;T/R 开关与默认关断方案','U17,U29,天线链','—','⏳ 待选型(决策门)','前端拓扑图+BOM 增删'),
 ('T3','决策:重复件裁决','X1(8M,疑=X2 重复)、X7(40M,疑=X3 残留)核实后删除;AT2401C 视 T2 结果','X1,X7,U17','—','⏳ 待确认','变更清单'),
 ('T4','决策:资料补齐','RTL8812AU 参考设计(受控)、CH334P/nRF24L01+/A7105/SKY13414 手册网核;CH334P 需补 24MHz 晶振+RREF 电阻(现缺件)','U20,U21,U19,U27,U29','—','⏳ 部分缺口','手册链接入 PINOUT'),
 ('T5','POWER 页重设计','换 buck(依 T1);重接输入保护/反馈/使能链(Q1/Q2/Q3 闸门)/TPS2117 优先级/全去耦','U4,U11,U13,Q1,Q2,Q3','T1','⏳ 阻塞于 T1','逐脚对账+DRC'),
 ('T6','MCU 页接线','U2 剩余2脚;USB2/USB3 滤波阵列;CH334P 全套(含补晶振/RREF);Q1/CN4;新增控制线 NSS3(A7105 片选)与四合一 IRQ/BUSY/RST 分配','U2,USB2,USB3,U20,Q1,CN4,X1,X7','T3,T4','⏳ 待开始','逐脚对账+DRC'),
 ('T7','VTX 页接线','RTL8812AU 77脚:V33/V12 电源域分组去耦、SPS(L3/R18)、X3 40M、复位、USB 下行(接 CH334P 口1)、两路 RF 匹配+双天线(独立不并)、Q3 闸门','U21,X3,Q3,TP2','T4,T6','⏳ 待开始','逐脚对账+DRC'),
 ('T8','LoRa 页:控制与总线','SX1281/NRF24/A7105 的 SPI(共总线+独立 NSS)、CE/IRQ/BUSY/RST/GIO 接 U2;X5/X6/X9 晶振负载电容;CN3 microSD(ESP32 SDMMC);USB1/Q2','U5,U19,U27,X6,X9,CN3,USB1,Q2','T6(GPIO分配)','⏳ 待开始','逐脚对账+DRC'),
 ('T9','LoRa 页:射频前端','SKY13414 源选择(V1/V2 控制线)、共享 PA/T/R/LNA(依 T2)、各芯片 RF 口匹配网络(手册参考值,标注待调谐)、U15 TCXO 余1脚、功率控制与上电默认关','U5,U19,U27,U29,U17,U15','T2,T8','⏳ 阻塞于 T2','前端网表+DRC'),
 ('T10','全图校验与交付','逐物理引脚对账(接线表vs实际网);sch nets/bridge/layout-lint/官方 DRC 致命0错误0,警告逐项处置;save+reload 复验;DESIGN/BOM/PINOUT 更新;PCB 同步说明(未执行)','全部','T5-T9','⏳ 待开始','四页 DRC 证据+对账报告'),
]

CHIP_TASK = {
 'T5':['U4','U11','U13','Q1','Q2','Q3'],
 'T6':['U2','USB2','USB3','U20','Q1','CN4','X1','X7'],
 'T7':['U21','X3','Q3','TP2'],
 'T8':['U5','U19','U27','X6','X9','CN3','USB1','Q2'],
 'T9':['U5','U19','U27','U29','U17','U15'],
}

out=[]
out.append('# 大疆多协议高频头 — 接线设计任务表(TASKS)')
out.append('')
out.append('> 结合 [PINOUT.md](PINOUT.md) 实时悬空数据生成(2026-09-24,网表引擎恢复后读取)。')
out.append('> 状态:✅ 完成 | ⏳ 待办 | 🔒 阻塞(标注于依赖) | ⚠️ 决策门(需拍板)。更新方式:重跑 make_tasks.py。')
out.append('')
out.append('## 1. 任务总表')
out.append('')
out.append('| ID | 任务 | 内容摘要 | 涉及芯片 | 依赖 | 状态 | 验收产出 |')
out.append('|---|---|---|---|---|---|---|')
for t in TASKS:
    out.append(f"| {t[0]} | {t[1]} | {t[2]} | {t[3]} | {t[4]} | {t[5]} | {t[6]} |")
out.append('')
out.append('## 2. 悬空脚明细(按任务)')
out.append('')
for tid, chips in CHIP_TASK.items():
    out.append(f'### {tid}')
    out.append('')
    out.append('| 芯片 | 悬空脚数 | 悬空引脚 |')
    out.append('|---|---|---|')
    for ref in chips:
        f=floating(ref)
        if not ref in INV or not INV[ref]: continue
        if not f:
            out.append(f"| {ref} | 0 |(本任务涉及脚已接;重设计类见任务说明)|")
        else:
            names=','.join(f)
            out.append(f"| {ref} | {len(f)} | {names[:180]}{'…' if len(names)>180 else ''} |")
    out.append('')
out.append('## 3. 建议执行顺序')
out.append('')
out.append('```')
out.append('T1/T2/T3/T4(并行:选型与资料决策)')
out.append('  ├─ T5(POWER 重设计,依赖 T1)')
out.append('  ├─ T6(MCU 页,依赖 T3/T4)── GPIO 分配表是 T8 的输入')
out.append('  │    └─ T7(VTX 页,依赖 T4/T6:USB 下行与 GPIO)')
out.append('  ├─ T8(LoRa 总线/控制,依赖 T6)── 与 T7 可并行')
out.append('  └─ T9(RF 前端,依赖 T2/T8)')
out.append('T10(全图校验交付,依赖 T5–T9 全部)')
out.append('```')
out.append('')
out.append('> 关键路径:T2(1W 前端)→ T9;T1(电源)→ T5。两项选型尽早拍板。')

open(os.path.join(BASE,'TASKS.md'),'w').write('\n'.join(out))
print('TASKS.md 生成完成')
