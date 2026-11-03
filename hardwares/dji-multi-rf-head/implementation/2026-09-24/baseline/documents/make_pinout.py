#!/usr/bin/env python3
"""生成 PINOUT.md:全部芯片逐引脚定义(编号/名称/类型/当前网/设计建议)。"""
import json, os

BASE = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
INV = json.load(open(os.path.join(BASE, 'migration/build/pintables/pin-inventory.json')))
LIVE = json.load(open(os.path.join(BASE, 'migration/build/pintables/live-nets.json')))

# 图页归属(新工程)
PAGE_OF = {}
for page, refs in json.load(open(os.path.join(BASE, 'migration/build/placement-members.json')))['pages'].items():
    for r in refs: PAGE_OF[r] = page
PAGE_OF.update({'U20':'MCU','U14':'MCU','U8':'MCU','U9':'MCU','R23':'MCU'})
PAGE_OF.pop('U14', None); PAGE_OF['U14']='MCU'

# 芯片元数据(型号/厂商/封装/C号/手册)
META = {
 'U1':('ZX-SH1.0-4PWT','兆讯仕/MEGASTAR','CONN 4P-1.00','C7430446','本地:17_ZX-SH1.0-4PWT.pdf'),
 'U4':('JW5357MSOTB#TR','Joulwatt/杰华特','SOT-23-6','C5197556','本地:03_JW5357MSOTB_TR.pdf'),
 'U11':('JW5357MSOTB#TR','Joulwatt/杰华特','SOT-23-6','C5197556','本地:03_JW5357MSOTB_TR.pdf'),
 'U13':('TPS2117DRLR','TI/德州仪器','SOT-583-8','C22399676','本地:08_TPS2117DRLR.pdf'),
 'U2':('N32H473CCU7','Nations/国民技术','QFN-48','C49191475','本地:10_N32H473CCU7.pdf'),
 'USB2':('DPX205850DT-4055A1','TDK','滤波阵列 DFN','C2847617','本地:16_DPX205850DT-4055A1.pdf'),
 'USB3':('DPX205850DT-4055A1','TDK','滤波阵列 DFN','C2847617','本地:16_DPX205850DT-4055A1.pdf'),
 'Q1':('AO3401A','AOS/万代','SOT-23 P-MOS','C15127','-'),
 'X1':('SX3B8.000F1010F30(8MHz)','SX/星晶','3225 4P','C7302001','本地:11_SX3B8*.pdf;疑为X2重复件'),
 'X2':('SX3B8.000F1010F30(8MHz)','SX/星晶','3225 4P','C7302001','本地:11_SX3B8*.pdf'),
 'X7':('40MHz/3225/12pF','Generic','3225 4P','C47089401','疑为RTL时钟残留(与X3重复)'),
 'U21':('RTL8812AU-CG','Realtek/瑞昱','QFN-76','C2928090','手册受控;引脚名取自库符号(已核为真实命名)'),
 'U20':('CH334P','WCH/沁恒','QFN-16','C5373042','网补:沁恒官网手册'),
 'U14':('ZX-SH1.0-4PWT','兆讯仕/MEGASTAR','CONN 4P-1.00','C7430446','本地:17_ZX-SH1.0-4PWT.pdf'),
 'U8':('RCLAMP0521T-ES','安世/Nexperia','DFN1006-2L','C5180263','网补:ESD 阵列'),
 'U9':('RCLAMP0521T-ES','安世/Nexperia','DFN1006-2L','C5180263','网补:ESD 阵列'),
 'Q3':('AO3401A','AOS/万代','SOT-23 P-MOS','C15127','-'),
 'X3':('40MHz/3225/12pF/10ppm','Generic','3225 4P','C47089401','RTL8812AU 时钟'),
 'U3':('ESP32-C3FH4','Espressif/乐鑫','QFN-32','C2858491','本地:01_ESP32-C3FH4.pdf'),
 'U5':('SX1281IMLTRT','Semtech','QFN-24','C2151551','mLRS:DS_SX1280-1_V3.2.pdf(SX1280/81合订)'),
 'U19':('NRF24L01P-R','NORDIC/北欧半导体','QFN-20-EP','C8791','网补:nRF24L01+ 数据手册(标准脚位)'),
 'U27':('A7105','AMICCOM/联杰','QFN-20','C126376','网补:A7105 数据手册'),
 'U29':('SKY13414-485LF','Skyworks','QFN-14','C255353','网补:SKY13414 手册;引脚名取自库符号'),
 'U17':('AT2401C','杭州中科微','QFN-16','C783588','本地:18_AT2401C.pdf'),
 'U15':('ABDFTCXO-52.000MHz-L-2-T5','Abracon','OSC 5P','C568572','型号与快照manifest待核(EPSON近似件在档)'),
 'USB1':('DPX205850DT-4055A1','TDK','滤波阵列 DFN','C2847617','本地:16_DPX205850DT-4055A1.pdf'),
 'Q2':('AO3401A','AOS/万代','SOT-23 P-MOS','C15127','-'),
 'X5':('Q22FA12800101(40MHz)','NDK','2016 4P','C255898','本地:13_Q22FA12800101.pdf'),
 'X6':('X322516MLB4SI(16MHz)','TYC/台湾晶技','3225 4P','C13738','网补:16MHz CL=9pF'),
 'X9':('ABM8AIG-12.000MHZ-12-D2Z-T','Abracon','3225 4P','C1986775','网补:12MHz'),
 'CN3':('MUP-M627-1','HRO/广募','microSD 座','C5205362','网补:microSD 引脚(标准)'),
 'CN1':('HX PZ2.0-1x4P','HX/华芯','CONN 4P-2.00','C22438287','-'),
 'CN4':('HX PZ2.0-1x4P','HX/华芯','CONN 4P-2.00','C22438287','-'),
 'TP1':('Test-Point','Generic','测试点','—','-'),
 'TP2':('Test-Point','Generic','测试点','—','-'),
}

# 引脚类型/建议规则(按名称模式)
import re
def classify(name, ref):
    n=(name or '').upper()
    if re.match(r'^(GND|VSS|EP|TAB|GND_EP)', n): return ('地','接 GND')
    if re.match(r'^(VDD|VCC|VIN|VBAT|VBAT_IO|VDD_|V33|V12|VD33|VD12|VBUS|5V|3V3|VDDA|AVDD|DCDC|VDD_PA)', n):
        return ('电源', '接对应电源轨(见电源树)')
    if 'RF' in n or n.startswith('ANT'):
        return ('射频','接 RF 前端/匹配网络(手册参考值)')
    if re.match(r'^(XI|XO|XTAL|OSC_IN|OSC_OUT|XTI|XTO)', n): return ('时钟','接晶振/TCXO')
    if re.match(r'^(NRST|RESET|RST|EN|CHIP_EN|PR1|ST)', n): return ('控制','上/下拉按手册复位与使能要求')
    if re.match(r'^(SWD|JTAG|TMS|TCK)', n): return ('调试','接 SWD 调试口')
    if re.match(r'^(DM|DP|USB)', n): return ('USB','接 USB 差分对')
    if re.match(r'^(IO|GPIO|P[ABC]\d|PA\d|PB\d|PC\d)', n): return ('数字 IO','按接线表定义分配(见建议)')
    if re.match(r'^(MOSI|MISO|SCK|NSS|CS|CSN|SDIO|SCS|SDA|SCL)', n): return ('SPI/总线','接对应总线与片选')
    if re.match(r'^(DIO|IRQ|BUSY|GIO)', n): return ('中断/状态','接 MCU GPIO(中断/忙检测)')
    if re.match(r'^(TX|RX|USART|UART)', n): return ('UART','ESP32↔主控串口')
    if re.match(r'^(COM|LB|HB|IN|OUT)$', n): return ('信号/滤波','按器件功能(双工/滤波)')
    if n in ('D','G','S'): return ('MOS 端','Q: G 栅/S 源/D 漏')
    if n in ('1','2','3','4','5','6') or n.isdigit(): return ('无源脚','按电路功能')
    return ('数字/其它','-')

# 关键芯片的建议补充(悬空脚设计去向,来源=接线设计草案)
ADVICE = {
 'U2': {
   'VDD':'3V3(去耦 100nF×N+4.7µF)', 'VSS':'GND(含 EP)',
   'NRST':'10k 上拉 3V3 + 100nF(可选复位按键)','SWCLK/SWDIO':'SWD 调试口 CN2',
   'OSC_IN/OSC_OUT':'X2 8MHz(CL=按 X2 手册)','USB_DP/USB_DN':'经 USB3 滤波阵列接 USB',
   'PA0-PA7':'原接 U21/FPC1(IO 扩展);新设计分配见接线表','PB11/PB12/PC10/PC11/PC13/PC4/PC6':'GPIO 备用/四合一控制',
 },
 'U3': {
   'GPIO0':'Strapping(启动模式,10k 上拉/下拉按需求)','GPIO2':'Strapping(必须上拉或浮空,忌下拉)',
   'GPIO8':'Strapping(必须上拉)','GPIO9':'Strapping Boot(下拉=下载模式,10k 上拉正常运行)',
   'USB':'ESP32-C3 原生 USB D+/D-(可选接 Hub 下行)','XTAL':'X5 40MHz','VDD3P3':'3V3 + 去耦',
   'GPIO18/19':'默认 UART0 TX/RX(与主控 USART1)','SDMMC':'microSD 4-bit(专用脚 GPIO18-23 复用注意)',
 },
 'U5': {
   'NRESET':'MCU GPIO 复位(10k 上拉)','BUSY':'MCU GPIO(忙状态)','DIO1/DIO2/DIO3':'MCU GPIO 中断',
   'XTA':'52MHz TCXO 输出(U15)','MOSI/MISO/SCK/NSS':'SPI 总线 + 独立 NSS',
   'VR_PA/VDD':'3V3_RF 轨 + 去耦','RF 端口':'接 SKY13414 输入(源选择)',
 },
 'U19': {
   'CE':'MCU GPIO(片使能)','CSN':'独立片选','IRQ':'MCU GPIO 中断',
   'XC1/XC2':'X6 16MHz(CL=9pF→负载电容约 12pF×2,按手册)','VDD_PA':'PA 电源脚,接 3V3_RF + 去耦',
   'ANT1/ANT2':'差分 RF 输出,按 nRF24L01+ 手册匹配网络转单端 → AT2401C 驱动或前端',
   'IREF':'基准电流电阻到 GND(手册值,约 22kΩ)','DVDD':'内部数字电源,去耦电容到 GND(手册:1.8V 输出脚)',
 },
 'U27': {
   'SCS/SDIO/SCK':'SPI + 独立片选','GIO1/GIO2':'MCU GPIO(中断/收发切换)',
   'XI/XO':'X9 12MHz(按手册负载电容)','RFIO':'单端 RF 输出 → 前端源选择',
 },
 'U29': {
   'RFC':'公共端 → 共享 PA/天线链','RF1-RF4':'四路源输入(SX1281/NRF24输出/A7105/ESP32)',
   'V1/V2':'MCU GPIO 二进制选择(真值表见手册)','VDD':'3V3_RF',
 },
 'U17': {
   '说明':'若 1W 前端选型确定,AT2401C(+22dBm)可作驱动级或删除;最终以前端拓扑为准',
 },
 'U21': {
   'V33_*':'V33_8812 轨(3.3V,每脚 100nF 去耦+按手册分组)','VD12*':'V12_8812(1.2V,SPS 供电)',
   'LX_SPS*/FB_SPS':'内部 SPS 外置电感 L3+反馈 R18(按 RTL 参考设计取值,手册受控-待核)',
   'XI/XO':'X3 40MHz','DMU/DPU':'USB 上行(接 CH334P 下行口)',
   'RF5OP/RF2OP':'两路 RF 输出(5G/2.4G)→ 各自匹配+双天线,两链独立',
   'RREF':'基准电阻(按手册值)','RESET#/PGANG':'复位/上电指示(按手册上拉)',
 },
 'U20': {
   'DM1/DP1…DM4/DP4':'Hub 下行口(口1→RTL8812AU;其余备用/ESP32 USB 可选)',
   'DMU/DPU':'上行口 → U14 连接器','XI/XO':'24MHz 晶振(CH334P 要求,需补!)',
   'VDD5/VDD33':'5V 与 3.3V 域','RREF':'基准电阻 12kΩ(按沁恒手册)','RESET#':'复位(10k 上拉)',
 },
 'U13': {
   '说明':'2-6S 改造后:5V(USB)与 BAT 侧经新 5V 轨二选一;PR1=优先级,ST=状态指示',
 },
 'U4/U11': {
   '说明':'绝对最大 20V!6S(25.2V)输入必换(方案:SY8303AIC 40V/3A,见 DESIGN §电源树);FB 分压按 VOUT 重算',
 },
}

def advice_for(ref, pinname):
    a=ADVICE.get(ref) or {}
    n=(pinname or '').upper()
    for k,v in a.items():
        if k==n: return v
    for k,v in a.items():
        if k in n: return v
    if ref in a and '说明' in a: return a['说明']
    return None

PAGES=['POWER','MCU','VTX','LoRa']
out=[]
out.append('# 大疆多协议高频头 — 芯片引脚定义(PINOUT)')
out.append('')
out.append('> 工程:「遥控器射频板」四页 | 生成:2026-09-24(网表引擎恢复后,当前网=实时读取)')
out.append('> 图例:✅ 已接线(netlist 引擎确认) | ⏳ 悬空待设计 | "当前网"为 PCB8 权威网或重建后网名。')
out.append('> 用途:硬件开发接线设计参考;「类型/建议」为工程建议,最终以各芯片数据手册为准。')
out.append('> 手册:本地=Codex outputs/datasheets 或 hardware/mLRS;网补=需网络核实;受控=Realtek 等不公开。')
out.append('')

# 摘要表
out.append('## 0. 芯片总览与悬空脚统计')
out.append('')
out.append('| 页 | 位号 | 型号 | 引脚数 | 已接 | 悬空 |')
out.append('|---|---|---|---|---|---|')
for page in PAGES:
    for ref in sorted(INV.keys(), key=lambda r:(0 if r and r[0] in 'UXQC' else 1, r or '')):
        info=INV[ref]
        if not info or PAGE_OF.get(ref)!=page: continue
        if ref not in META: continue
        pins=info['pins']
        done=sum(1 for p in pins if LIVE.get(f"{ref}:{p['pin']}"))
        out.append(f"| {page} | {ref} | {META[ref][0]} | {len(pins)} | {done} | {len(pins)-done} |")
out.append('')

for page in PAGES:
    out.append(f'## {page} 页')
    out.append('')
    for ref in sorted(INV.keys(), key=lambda r:(0 if r and r[0] in 'UXQC' else 1, r or '')):
        info=INV[ref]
        if not info or PAGE_OF.get(ref)!=page: continue
        if ref not in META: continue
        m=META[ref]
        out.append(f"### {ref} — {m[0]}")
        out.append('')
        out.append(f"厂商:{m[1]} | 封装:{m[2]} | 立创:{m[3]} | 手册:{m[4]}")
        out.append('')
        out.append('| # | 引脚名 | 类型 | 当前 | 设计建议/去向 |')
        out.append('|---|---|---|---|---|')
        pins=sorted(info['pins'], key=lambda p:(int(p['pin']) if p['pin'].isdigit() else 999))
        for p in pins:
            net=LIVE.get(f"{ref}:{p['pin']}")
            cls,adv=classify(p['name'], ref)
            extra=advice_for(ref, p['name'])
            if extra: adv=extra
            cur='✅ '+net if net else '⏳'
            out.append(f"| {p['pin']} | {p['name']} | {cls} | {cur} | {adv} |")
        out.append('')

out.append('## 附:接线路由草案(跨页)')
out.append('')
out.append('| 总线/信号 | 主控侧(U2) | 器件侧 | 状态 |')
out.append('|---|---|---|---|')
out.append('| SPI0 SCK/MISO/MOSI | U2 SPI 脚 | U5/U19/U27 共总线 | 已有 SPI_SCK/MISO/MOSI 网 |')
out.append('| 片选 NSS1/NSS2 | U2 | U5(SX_NSS)/U19(NRF_CSN) | SPI_NSS1/2 已有;A7105 需 NSS3 |')
out.append('| 中断/复位/忙 | U2 GPIO | BUSY1/2、IRQ1/2、RST1/2 已有;A7105/SX 需补 | 部分已有 |')
out.append('| USART1 | U2 | U3 ESP32-C3(UART0=GPIO18/19) | C3TX/RX 已有 |')
out.append('| USB | U14→U20(CH334P)→U21(RTL8812AU) | U2 USB 经 USB3 滤波 | DP/DN 已有 |')
out.append('| RF 前端 | U29 SKY13414(4选1)→共享 PA(1W 目标,选型中)→T/R→天线 | ⏳ 全待设计 |')
out.append('')
out.append('> 注:X1(8M)/X7(40M) 疑为重复件,接线设计确认后建议删除(AT2401C 同理,取决于前端选型)。')

open(os.path.join(BASE, 'PINOUT.md'),'w').write('\n'.join(out))
print('PINOUT.md 生成完成:', len('\n'.join(out)), '字符')
