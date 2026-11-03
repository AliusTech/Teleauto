# 全器件物理引脚审计

> 状态：未完成。联网仅表示网表成员关系，不表示电路正确；POWER 电池输入与降压输出存在已证实桥接。

工程：遥控器射频板；153 件，632 个物理引脚。

统计：connected=210；unconnected=416；nc=6。

来源：保留的 typed 快照，采集时间与 SHA-256 见 [pin-audit.json](implementation/2026-09-24/pin-audit.json)。
重新采集后须更新 current-snapshots.json，再运行生成器；运行脚本本身不会刷新 EDA。

[设计文档](DESIGN.md) · [任务表](TASKS.md) · [本轮执行报告](implementation/2026-09-24/REPORT.md)

## POWER

| 位号.物理脚 | 符号脚名 | 实际网络 | 状态 | NC 依据 |
|---|---|---|---|---|
| C15.1 | 1 | PCB_1N6 | connected | — |
| C15.2 | 2 | PCB_1N14 | connected | — |
| C18.1 | 1 | GND | connected | — |
| C18.2 | 2 | BATTERY | connected | — |
| C19.1 | 1 | BATTERY | connected | — |
| C19.2 | 2 | GND | connected | — |
| C20.1 | 1 | 3V3 | connected | — |
| C20.2 | 2 | PCB_1N9 | connected | — |
| C27.1 | 1 | BATTERY | connected | — |
| C27.2 | 2 | PCB_1N10 | connected | — |
| C29.1 | 1 | GND | connected | — |
| C29.2 | 2 | IN_5V | connected | — |
| C30.1 | 1 | 3V3 | connected | — |
| C30.2 | 2 | GND | connected | — |
| C9.1 | 1 | PCB_1N5 | connected | — |
| C9.2 | 2 | PCB_1N13 | connected | — |
| L10.1 | 1 | PCB_1N6 | connected | — |
| L10.2 | 2 | BATTERY | connected | — |
| L9.1 | 1 | PCB_1N5 | connected | — |
| L9.2 | 2 | 3V3 | connected | — |
| R10.1 | 1 | BATTERY | connected | — |
| R10.2 | 2 | PCB_1N10 | connected | — |
| R11.1 | 1 | PCB_1N94 | connected | — |
| R11.2 | 2 | GND | connected | — |
| R12.1 | 1 | PCB_1N94 | connected | — |
| R12.2 | 2 | BATTERY | connected | — |
| R15.1 | 1 | GND | connected | — |
| R15.2 | 2 |  | unconnected | — |
| R3.1 | 1 | 1.0_5V | connected | — |
| R3.2 | 2 | 5V | connected | — |
| R4.1 | 1 | USB_5V | connected | — |
| R4.2 | 2 | 5V | connected | — |
| R5.1 | 1 | GND | connected | — |
| R5.2 | 2 | PCB_1N9 | connected | — |
| R7.1 | 1 | 3V3 | connected | — |
| R7.2 | 2 | PCB_1N9 | connected | — |
| R9.1 | 1 | GND | connected | — |
| R9.2 | 2 | PCB_1N10 | connected | — |
| U1.1 | 1 | 1.0_5V | connected | — |
| U1.2 | 2 | USB_DN | connected | — |
| U1.3 | 3 | USB_DP | connected | — |
| U1.4 | 4 | GND | connected | — |
| U1.5 | 5 | GND | connected | — |
| U1.6 | 6 | GND | connected | — |
| U11.1 | BST | PCB_1N14 | connected | — |
| U11.2 | GND | GND | connected | — |
| U11.3 | FB | PCB_1N10 | connected | — |
| U11.4 | EN | BATTERY | connected | — |
| U11.5 | VIN | BATTERY | connected | — |
| U11.6 | SW | PCB_1N6 | connected | — |
| U13.1 | GND | GND | connected | — |
| U13.2 | VOUT | IN_5V | connected | — |
| U13.3 | VIN1 | BATTERY | connected | — |
| U13.4 | PR1 | PCB_1N94 | connected | — |
| U13.5 | MODE | BATTERY | connected | — |
| U13.6 | VIN2 | 5V | connected | — |
| U13.7 | VOUT | IN_5V | connected | — |
| U13.8 | ST | PCB_1N91 | connected | — |
| U4.1 | BST | PCB_1N13 | connected | — |
| U4.2 | GND | GND | connected | — |
| U4.3 | FB | PCB_1N9 | connected | — |
| U4.4 | EN | IN_5V | connected | — |
| U4.5 | VIN | IN_5V | connected | — |
| U4.6 | SW | PCB_1N5 | connected | — |

## MCU

| 位号.物理脚 | 符号脚名 | 实际网络 | 状态 | NC 依据 |
|---|---|---|---|---|
| C1.1 | 1 | GND | connected | — |
| C1.2 | 2 | 3V3 | connected | — |
| C10.1 | 1 | GND | connected | — |
| C10.2 | 2 | 3V3 | connected | — |
| C12.1 | 1 | GND | connected | — |
| C12.2 | 2 | 3V3 | connected | — |
| C14.1 | 1 | GND | connected | — |
| C14.2 | 2 | OSC_OUT | connected | — |
| C16.1 | 1 | 3V3 | connected | — |
| C16.2 | 2 | GND | connected | — |
| C2.1 | 1 | 3V3 | connected | — |
| C2.2 | 2 | GND | connected | — |
| C26.1 | 1 | GND | connected | — |
| C26.2 | 2 | NRST | connected | — |
| C3.1 | 1 | 3V3 | connected | — |
| C3.2 | 2 | GND | connected | — |
| C4.1 | 1 | 3V3 | connected | — |
| C4.2 | 2 | GND | connected | — |
| C40.1 | 1 | C3XTAL_40K_P | connected | — |
| C40.2 | 2 | GND | connected | — |
| C41.1 | 1 | 3V3 | connected | — |
| C41.2 | 2 | GND | connected | — |
| C47.1 | 1 |  | unconnected | — |
| C47.2 | 2 |  | unconnected | — |
| C5.1 | 1 | 3V3 | connected | — |
| C5.2 | 2 | GND | connected | — |
| C52.1 | 1 |  | unconnected | — |
| C52.2 | 2 |  | unconnected | — |
| C55.1 | 1 | GND | connected | — |
| C55.2 | 2 | CHIP_EN | connected | — |
| C56.1 | 1 |  | unconnected | — |
| C56.2 | 2 |  | unconnected | — |
| C57.1 | 1 |  | unconnected | — |
| C57.2 | 2 |  | unconnected | — |
| C59.1 | 1 |  | unconnected | — |
| C59.2 | 2 |  | unconnected | — |
| C6.1 | 1 | 3V3 | connected | — |
| C6.2 | 2 | GND | connected | — |
| C60.1 | 1 |  | unconnected | — |
| C60.2 | 2 |  | unconnected | — |
| C7.1 | 1 | 3V3 | connected | — |
| C7.2 | 2 | GND | connected | — |
| C8.1 | 1 | GND | connected | — |
| C8.2 | 2 | OSC_IN | connected | — |
| CN2.1 | 1 | SWCLK | connected | — |
| CN2.2 | 2 | SWDIO | connected | — |
| CN2.3 | 3 | 3V3 | connected | — |
| CN2.4 | 4 | GND | connected | — |
| CN4.1 | 1 |  | unconnected | — |
| CN4.2 | 2 |  | unconnected | — |
| CN4.3 | 3 |  | unconnected | — |
| CN4.4 | 4 |  | unconnected | — |
| L21.1 | 1 |  | unconnected | — |
| L21.2 | 2 |  | unconnected | — |
| L22.1 | 1 |  | unconnected | — |
| L22.2 | 2 |  | unconnected | — |
| Q1.1 | G |  | unconnected | — |
| Q1.2 | S |  | unconnected | — |
| Q1.3 | D |  | unconnected | — |
| R1.1 | 1 | CHIP_EN | connected | — |
| R1.2 | 2 | 3V3 | connected | — |
| R14.1 | 1 |  | unconnected | — |
| R14.2 | 2 |  | unconnected | — |
| R16.1 | 1 |  | unconnected | — |
| R16.2 | 2 |  | unconnected | — |
| R17.1 | 1 |  | unconnected | — |
| R17.2 | 2 |  | unconnected | — |
| R19.1 | 1 |  | unconnected | — |
| R19.2 | 2 |  | unconnected | — |
| R2.1 | 1 |  | unconnected | — |
| R2.2 | 2 |  | unconnected | — |
| R20.1 | 1 |  | unconnected | — |
| R20.2 | 2 |  | unconnected | — |
| R22.1 | 1 |  | unconnected | — |
| R22.2 | 2 |  | unconnected | — |
| R23.1 | 1 |  | unconnected | — |
| R23.2 | 2 |  | unconnected | — |
| R6.1 | 1 | PCB_1N20 | connected | — |
| R6.2 | 2 | GND | connected | — |
| R8.1 | 1 |  | unconnected | — |
| R8.2 | 2 |  | unconnected | — |
| TP1.1 | 1 | CHIP_EN | connected | — |
| U14.1 | 1 | USB_5V | connected | — |
| U14.2 | 2 | USB_DN | connected | — |
| U14.3 | 3 | USB_DP | connected | — |
| U14.4 | 4 | GND | connected | — |
| U14.5 | 5 | GND | connected | — |
| U14.6 | 6 | GND | connected | — |
| U2.1 | VBAT | 3V3 | connected | — |
| U2.2 | PC13 | PC13 | connected | — |
| U2.3 | PC14-OSC32_IN |  | unconnected | — |
| U2.4 | PC15-OSC32_OUT |  | unconnected | — |
| U2.5 | PH0-OSC_IN | OSC_IN | connected | — |
| U2.6 | PH1-OSC_OUT | OSC_OUT | connected | — |
| U2.7 | PH6-NRST | NRST | connected | — |
| U2.8 | PA0 | PA0 | connected | — |
| U2.9 | PA1 | PA1 | connected | — |
| U2.10 | PA2 | PA2 | connected | — |
| U2.11 | PA3 | PA3 | connected | — |
| U2.12 | PA4 | PA4 | connected | — |
| U2.13 | PA5 | PA5 | connected | — |
| U2.14 | PA6 | PA6 | connected | — |
| U2.15 | PA7 | PA7 | connected | — |
| U2.16 | PC4 | PC4 | connected | — |
| U2.17 | PB0 | BUSY1 | connected | — |
| U2.18 | PB1 | BUSY2 | connected | — |
| U2.19 | PB2 | RST1 | connected | — |
| U2.20 | VREF+ | 3V3 | connected | — |
| U2.21 | VDDA | 3V3 | connected | — |
| U2.22 | PB10 | V2-2 | connected | — |
| U2.23 | VDD | 3V3 | connected | — |
| U2.24 | PB11 | PB11 | connected | — |
| U2.25 | PB12 | PB12 | connected | — |
| U2.26 | PB13 | SPI_SCK | connected | — |
| U2.27 | PB14 | SPI_MISO | connected | — |
| U2.28 | PB15 | SPI_MOSI | connected | — |
| U2.29 | PC6 | PC6 | connected | — |
| U2.30 | PA8 | SPI_NSS1 | connected | — |
| U2.31 | PA9 | USART1_TX | connected | — |
| U2.32 | PA10 | USART1_RX | connected | — |
| U2.33 | PA11 | USB_DN | connected | — |
| U2.34 | PA12 | USB_DP | connected | — |
| U2.35 | VDD | 3V3 | connected | — |
| U2.36 | PA13 | SWDIO | connected | — |
| U2.37 | PA14 | SWCLK | connected | — |
| U2.38 | PA15 | SPI_NSS2 | connected | — |
| U2.39 | PC10 | PC10 | connected | — |
| U2.40 | PC11 | PC11 | connected | — |
| U2.41 | PB3 | RST2 | connected | — |
| U2.42 | PB4 | IRQ1 | connected | — |
| U2.43 | PB5 | IRQ2 | connected | — |
| U2.44 | PB6 | V1-1 | connected | — |
| U2.45 | PB7 | V2-1 | connected | — |
| U2.46 | PB8-BOOT0 | PCB_1N20 | connected | — |
| U2.47 | PB9 | V1-2 | connected | — |
| U2.48 | VDD | 3V3 | connected | — |
| U2.49 | VSS | GND | connected | — |
| U20.1 | XO |  | unconnected | — |
| U20.2 | XI |  | unconnected | — |
| U20.3 | DM4 |  | unconnected | — |
| U20.4 | DP4 |  | unconnected | — |
| U20.5 | DM3 |  | unconnected | — |
| U20.6 | DP3 |  | unconnected | — |
| U20.7 | DM2 |  | unconnected | — |
| U20.8 | DP2 |  | unconnected | — |
| U20.9 | DM1 |  | unconnected | — |
| U20.10 | DP1 |  | unconnected | — |
| U20.11 | DMU |  | unconnected | — |
| U20.12 | DPU |  | unconnected | — |
| U20.13 | RESET#/CDP |  | unconnected | — |
| U20.14 | PGANG |  | unconnected | — |
| U20.15 | V5 |  | unconnected | — |
| U20.16 | VDD33 |  | unconnected | — |
| U20.17 | GND |  | unconnected | — |
| U8.1 | 1 | GND | connected | — |
| U8.2 | 2 | USB_DN | connected | — |
| U9.1 | 1 | GND | connected | — |
| U9.2 | 2 | USB_DP | connected | — |
| USB2.1 | GND |  | unconnected | — |
| USB2.2 | COM |  | unconnected | — |
| USB2.3 | GND |  | unconnected | — |
| USB2.4 | LB |  | unconnected | — |
| USB2.5 | GND |  | unconnected | — |
| USB2.6 | HB |  | unconnected | — |
| USB3.1 | GND |  | unconnected | — |
| USB3.2 | COM |  | unconnected | — |
| USB3.3 | GND |  | unconnected | — |
| USB3.4 | LB |  | unconnected | — |
| USB3.5 | GND |  | unconnected | — |
| USB3.6 | HB |  | unconnected | — |
| X1.1 | 1 |  | unconnected | — |
| X1.2 | GND |  | unconnected | — |
| X1.3 | 3 |  | unconnected | — |
| X1.4 | GND |  | unconnected | — |
| X2.1 | 1 | OSC_IN | connected | — |
| X2.2 | GND | GND | connected | — |
| X2.3 | 3 | OSC_OUT | connected | — |
| X2.4 | GND | GND | connected | — |
| X7.1 | 1 |  | unconnected | — |
| X7.2 | GND |  | unconnected | — |
| X7.3 | 3 |  | unconnected | — |
| X7.4 | GND |  | unconnected | — |

## VTX

| 位号.物理脚 | 符号脚名 | 实际网络 | 状态 | NC 依据 |
|---|---|---|---|---|
| C17.1 | 1 |  | unconnected | — |
| C17.2 | 2 |  | unconnected | — |
| C21.1 | 1 |  | unconnected | — |
| C21.2 | 2 |  | unconnected | — |
| C22.1 | 1 |  | unconnected | — |
| C22.2 | 2 |  | unconnected | — |
| C23.1 | 1 |  | unconnected | — |
| C23.2 | 2 |  | unconnected | — |
| C24.1 | 1 |  | unconnected | — |
| C24.2 | 2 |  | unconnected | — |
| C25.1 | 1 |  | unconnected | — |
| C25.2 | 2 |  | unconnected | — |
| C28.1 | 1 |  | unconnected | — |
| C28.2 | 2 |  | unconnected | — |
| C34.1 | 1 |  | unconnected | — |
| C34.2 | 2 |  | unconnected | — |
| C37.1 | 1 |  | unconnected | — |
| C37.2 | 2 |  | unconnected | — |
| C38.1 | 1 |  | unconnected | — |
| C38.2 | 2 |  | unconnected | — |
| C39.1 | 1 |  | unconnected | — |
| C39.2 | 2 |  | unconnected | — |
| C42.1 | 1 |  | unconnected | — |
| C42.2 | 2 |  | unconnected | — |
| C43.1 | 1 |  | unconnected | — |
| C43.2 | 2 |  | unconnected | — |
| C44.1 | 1 |  | unconnected | — |
| C44.2 | 2 |  | unconnected | — |
| C45.1 | 1 |  | unconnected | — |
| C45.2 | 2 |  | unconnected | — |
| C46.1 | 1 |  | unconnected | — |
| C46.2 | 2 |  | unconnected | — |
| C48.1 | 1 |  | unconnected | — |
| C48.2 | 2 |  | unconnected | — |
| C50.1 | 1 |  | unconnected | — |
| C50.2 | 2 |  | unconnected | — |
| C53.1 | 1 |  | unconnected | — |
| C53.2 | 2 |  | unconnected | — |
| C54.1 | 1 |  | unconnected | — |
| C54.2 | 2 |  | unconnected | — |
| C66.1 | 1 |  | unconnected | — |
| C66.2 | 2 |  | unconnected | — |
| C69.1 | 1 |  | unconnected | — |
| C69.2 | 2 |  | unconnected | — |
| L1.1 | 1 |  | unconnected | — |
| L1.2 | 2 |  | unconnected | — |
| L3.1 | 1 |  | unconnected | — |
| L3.2 | 2 |  | unconnected | — |
| Q3.1 | G |  | unconnected | — |
| Q3.2 | S |  | unconnected | — |
| Q3.3 | D |  | unconnected | — |
| R18.1 | 1 |  | unconnected | — |
| R18.2 | 2 |  | unconnected | — |
| R34.1 | 1 |  | unconnected | — |
| R34.2 | 2 |  | unconnected | — |
| TP2.1 | 1 |  | unconnected | — |
| U21.1 | V33_SYN |  | unconnected | — |
| U21.2 | V12_SYN |  | unconnected | — |
| U21.3 | V12_S0 |  | unconnected | — |
| U21.4 | RF2IN_S0 |  | unconnected | — |
| U21.5 | RF2IP_S0 |  | unconnected | — |
| U21.6 | V33_PAD2G_S0 |  | unconnected | — |
| U21.7 | RF2ON_S0 |  | unconnected | — |
| U21.8 | RF2OP_S0 |  | unconnected | — |
| U21.9 | V33_PAD5G_S0 |  | unconnected | — |
| U21.10 | RF5OP_S0 |  | unconnected | — |
| U21.11 | VD33PA5G_S0 |  | unconnected | — |
| U21.12 | RF5IP_S0 |  | unconnected | — |
| U21.13 | RF5IN_S0 |  | unconnected | — |
| U21.14 | V12_BB_S0 |  | unconnected | — |
| U21.15 | LNAON_S0 |  | unconnected | — |
| U21.16 | PAPE_2G_S0 |  | unconnected | — |
| U21.17 | PAPE_5G_S0 |  | unconnected | — |
| U21.18 | TRSW_S0 |  | unconnected | — |
| U21.19 | TRSWB_S0 |  | unconnected | — |
| U21.20 | VD12A |  | unconnected | — |
| U21.21 | VD33A |  | unconnected | — |
| U21.22 | XI |  | unconnected | — |
| U21.23 | XO |  | unconnected | — |
| U21.24 | CK_BT |  | unconnected | — |
| U21.25 | ANTSW |  | unconnected | — |
| U21.26 | ANTSWB |  | unconnected | — |
| U21.27 | VD12D |  | unconnected | — |
| U21.28 | VD33D |  | unconnected | — |
| U21.29 | GPIO[4]/WL_ACT |  | unconnected | — |
| U21.30 | GPIO[5]/BT_FREQ/BT_EN |  | unconnected | — |
| U21.31 | GPIO[6]/BT_STATE |  | unconnected | — |
| U21.32 | GPIO[7]/BT_PRI |  | unconnected | — |
| U21.33 | LED2/GPIO[8] |  | unconnected | — |
| U21.34 | LED1/GPIO[9] |  | unconnected | — |
| U21.35 | LED0/GPIO[10] |  | unconnected | — |
| U21.36 | GPIO[11]/PDn |  | unconnected | — |
| U21.37 | GPIO[0]/BT_Suspend |  | unconnected | — |
| U21.38 | GPIO[1] |  | unconnected | — |
| U21.39 | GPIO[2] |  | unconnected | — |
| U21.40 | GPIO[3] |  | unconnected | — |
| U21.41 | GND |  | unconnected | — |
| U21.42 | HSDP |  | unconnected | — |
| U21.43 | HSDM |  | unconnected | — |
| U21.44 | VD12S |  | unconnected | — |
| U21.45 | VD33S |  | unconnected | — |
| U21.46 | RREF |  | unconnected | — |
| U21.47 | VDDTX |  | unconnected | — |
| U21.48 | HSON |  | unconnected | — |
| U21.49 | HSOP |  | unconnected | — |
| U21.50 | GND |  | unconnected | — |
| U21.51 | HSIN |  | unconnected | — |
| U21.52 | HSIP |  | unconnected | — |
| U21.53 | VDDRX |  | unconnected | — |
| U21.54 | GND |  | unconnected | — |
| U21.55 | LX_SPS0 |  | unconnected | — |
| U21.56 | LX_SPS0 |  | unconnected | — |
| U21.57 | VDSPS33 |  | unconnected | — |
| U21.58 | FB_SPS |  | unconnected | — |
| U21.59 | VD33D |  | unconnected | — |
| U21.60 | VD12D |  | unconnected | — |
| U21.61 | TRSWB_S1 |  | unconnected | — |
| U21.62 | TRSW_S1 |  | unconnected | — |
| U21.63 | PAPE_5G_S1 |  | unconnected | — |
| U21.64 | PAPE_2G_S1 |  | unconnected | — |
| U21.65 | LNAON_S1 |  | unconnected | — |
| U21.66 | V12_BB_S1 |  | unconnected | — |
| U21.67 | RF2IN_S1 |  | unconnected | — |
| U21.68 | RF2IP_S1 |  | unconnected | — |
| U21.69 | V33_PAD2G_S1 |  | unconnected | — |
| U21.70 | RF2ON_S1 |  | unconnected | — |
| U21.71 | RF2OP_S1 |  | unconnected | — |
| U21.72 | V33_PAD5G_S1 |  | unconnected | — |
| U21.73 | RF5OP_S1 |  | unconnected | — |
| U21.74 | VD33PA5G_S1 |  | unconnected | — |
| U21.75 | RF5IP_S1 |  | unconnected | — |
| U21.76 | RF5IN_S1 |  | unconnected | — |
| U21.77 | GND |  | unconnected | — |
| X3.1 | 1 |  | unconnected | — |
| X3.2 | GND |  | unconnected | — |
| X3.3 | 3 |  | unconnected | — |
| X3.4 | GND |  | unconnected | — |

## LoRa

| 位号.物理脚 | 符号脚名 | 实际网络 | 状态 | NC 依据 |
|---|---|---|---|---|
| C11.1 | 1 | GND | connected | — |
| C11.2 | 2 | 3V3 | connected | — |
| C13.1 | 1 | GND | connected | — |
| C13.2 | 2 | C3XTAL_40K_N | connected | — |
| C31.1 | 1 |  | unconnected | — |
| C31.2 | 2 |  | unconnected | — |
| C32.1 | 1 |  | unconnected | — |
| C32.2 | 2 |  | unconnected | — |
| C33.1 | 1 |  | unconnected | — |
| C33.2 | 2 |  | unconnected | — |
| C35.1 | 1 |  | unconnected | — |
| C35.2 | 2 |  | unconnected | — |
| C36.1 | 1 | PCB_1N171 | connected | — |
| C36.2 | 2 | XTA | connected | — |
| C49.1 | 1 |  | unconnected | — |
| C49.2 | 2 |  | unconnected | — |
| C51.1 | 1 |  | unconnected | — |
| C51.2 | 2 |  | unconnected | — |
| C58.1 | 1 |  | unconnected | — |
| C58.2 | 2 |  | unconnected | — |
| C61.1 | 1 |  | unconnected | — |
| C61.2 | 2 |  | unconnected | — |
| C62.1 | 1 |  | unconnected | — |
| C62.2 | 2 |  | unconnected | — |
| C63.1 | 1 |  | unconnected | — |
| C63.2 | 2 |  | unconnected | — |
| C64.1 | 1 |  | unconnected | — |
| C64.2 | 2 |  | unconnected | — |
| C65.1 | 1 |  | unconnected | — |
| C65.2 | 2 |  | unconnected | — |
| C67.1 | 1 |  | unconnected | — |
| C67.2 | 2 |  | unconnected | — |
| C68.1 | 1 |  | unconnected | — |
| C68.2 | 2 |  | unconnected | — |
| CN1.1 | 1 | C3RX | connected | — |
| CN1.2 | 2 | C3TX | connected | — |
| CN1.3 | 3 | 3V3 | connected | — |
| CN1.4 | 4 | GND | connected | — |
| CN3.0 | 0 |  | unconnected | — |
| CN3.1 | DAT2 |  | unconnected | — |
| CN3.2 | CD/DAT3 |  | unconnected | — |
| CN3.3 | CMD |  | unconnected | — |
| CN3.4 | VDD |  | unconnected | — |
| CN3.5 | CLK |  | unconnected | — |
| CN3.6 | VSS |  | unconnected | — |
| CN3.7 | DAT0 |  | unconnected | — |
| CN3.8 | DAT1 |  | unconnected | — |
| CN3.9 | CARDDETECT |  | unconnected | — |
| CN3.10 | CARDDETECT |  | unconnected | — |
| L18.1 | 1 |  | unconnected | — |
| L18.2 | 2 |  | unconnected | — |
| L19.1 | 1 |  | unconnected | — |
| L19.2 | 2 |  | unconnected | — |
| L2.1 | 1 | 3V3 | connected | — |
| L2.2 | 2 | PCB_1N169 | connected | — |
| L20.1 | 1 |  | unconnected | — |
| L20.2 | 2 |  | unconnected | — |
| L23.1 | 1 |  | unconnected | — |
| L23.2 | 2 |  | unconnected | — |
| L24.1 | 1 |  | unconnected | — |
| L24.2 | 2 |  | unconnected | — |
| L25.1 | 1 |  | unconnected | — |
| L25.2 | 2 |  | unconnected | — |
| L26.1 | 1 |  | unconnected | — |
| L26.2 | 2 |  | unconnected | — |
| L8.1 | GND |  | unconnected | — |
| L8.2 | OUT/IN |  | unconnected | — |
| L8.3 | GND |  | unconnected | — |
| L8.4 | IN/OUT |  | unconnected | — |
| Q2.1 | G |  | unconnected | — |
| Q2.2 | S |  | unconnected | — |
| Q2.3 | D |  | unconnected | — |
| R21.1 | 1 |  | unconnected | — |
| R21.2 | 2 |  | unconnected | — |
| R24.1 | 1 |  | unconnected | — |
| R24.2 | 2 |  | unconnected | — |
| R25.1 | 1 |  | unconnected | — |
| R25.2 | 2 |  | unconnected | — |
| R26.1 | 1 |  | unconnected | — |
| R26.2 | 2 |  | unconnected | — |
| R27.1 | 1 |  | unconnected | — |
| R27.2 | 2 |  | unconnected | — |
| R28.1 | 1 |  | unconnected | — |
| R28.2 | 2 |  | unconnected | — |
| R29.1 | 1 |  | unconnected | — |
| R29.2 | 2 |  | unconnected | — |
| R30.1 | 1 |  | unconnected | — |
| R30.2 | 2 |  | unconnected | — |
| R31.1 | 1 |  | unconnected | — |
| R31.2 | 2 |  | unconnected | — |
| R32.1 | 1 |  | unconnected | — |
| R32.2 | 2 |  | unconnected | — |
| R33.1 | 1 |  | unconnected | — |
| R33.2 | 2 |  | unconnected | — |
| U15.1 | NC/VC | GND | connected | — |
| U15.2 | GND | GND | connected | — |
| U15.3 | OUT1 | PCB_1N171 | connected | — |
| U15.4 | VDD | PCB_1N169 | connected | — |
| U15.5 | OUT2 |  | unconnected | — |
| U17.1 | GND |  | unconnected | — |
| U17.2 | GND |  | unconnected | — |
| U17.3 | GND |  | unconnected | — |
| U17.4 | TXRX |  | unconnected | — |
| U17.5 | TXEN |  | unconnected | — |
| U17.6 | RXEN |  | unconnected | — |
| U17.7 | GND |  | unconnected | — |
| U17.8 | GND |  | unconnected | — |
| U17.9 | GND |  | unconnected | — |
| U17.10 | ANT |  | unconnected | — |
| U17.11 | GND |  | unconnected | — |
| U17.12 | GND |  | unconnected | — |
| U17.13 | DNC |  | unconnected | — |
| U17.14 | VDD |  | unconnected | — |
| U17.15 | GND |  | unconnected | — |
| U17.16 | VCC |  | unconnected | — |
| U17.17 | EP |  | unconnected | — |
| U19.1 | CE |  | unconnected | — |
| U19.2 | CSN |  | unconnected | — |
| U19.3 | SCK |  | unconnected | — |
| U19.4 | MOSI |  | unconnected | — |
| U19.5 | MISO |  | unconnected | — |
| U19.6 | IRQ |  | unconnected | — |
| U19.7 | VDD |  | unconnected | — |
| U19.8 | VSS |  | unconnected | — |
| U19.9 | XC2 |  | unconnected | — |
| U19.10 | XC1 |  | unconnected | — |
| U19.11 | VDD_PA |  | unconnected | — |
| U19.12 | ANT1 |  | unconnected | — |
| U19.13 | ANT2 |  | unconnected | — |
| U19.14 | VSS |  | unconnected | — |
| U19.15 | VDD |  | unconnected | — |
| U19.16 | IREF |  | unconnected | — |
| U19.17 | VSS |  | unconnected | — |
| U19.18 | VDD |  | unconnected | — |
| U19.19 | DVDD |  | unconnected | — |
| U19.20 | VSS |  | unconnected | — |
| U19.21 | EP |  | unconnected | — |
| U27.1 | RSSI |  | unconnected | — |
| U27.2 | BPBG |  | unconnected | — |
| U27.3 | RFI |  | unconnected | — |
| U27.4 | RFO |  | unconnected | — |
| U27.5 | VDA2 |  | unconnected | — |
| U27.6 | VT |  | unconnected | — |
| U27.7 | GND |  | unconnected | — |
| U27.8 | XI |  | unconnected | — |
| U27.9 | XO |  | unconnected | — |
| U27.10 | VDA3 |  | unconnected | — |
| U27.11 | SCS |  | unconnected | — |
| U27.12 | SCK |  | unconnected | — |
| U27.13 | DVDD |  | unconnected | — |
| U27.14 | SDIO |  | unconnected | — |
| U27.15 | GND |  | unconnected | — |
| U27.16 | GIO1 |  | unconnected | — |
| U27.17 | GIO2 |  | unconnected | — |
| U27.18 | CKO |  | unconnected | — |
| U27.19 | REGI |  | unconnected | — |
| U27.20 | VDA1 |  | unconnected | — |
| U27.21 | EXP |  | unconnected | — |
| U29.1 | N/C |  | unconnected | — |
| U29.2 | RF3 |  | unconnected | — |
| U29.3 | RF1 |  | unconnected | — |
| U29.4 | VDD |  | unconnected | — |
| U29.5 | V3 |  | unconnected | — |
| U29.6 | V2 |  | unconnected | — |
| U29.7 | V1 |  | unconnected | — |
| U29.8 | N/C |  | unconnected | — |
| U29.9 | RF2 |  | unconnected | — |
| U29.10 | RF4 |  | unconnected | — |
| U29.11 | N/C |  | unconnected | — |
| U29.12 | N/C |  | unconnected | — |
| U29.13 | ANT |  | unconnected | — |
| U29.14 | N/C |  | unconnected | — |
| U29.15 | EP |  | unconnected | — |
| U3.1 | LNA_IN | PCB_1N60 | connected | — |
| U3.2 | VDD3P3 | 3V3 | connected | — |
| U3.3 | VDD3P3 | 3V3 | connected | — |
| U3.4 | XTAL_32K_P | C3GPIO0 | connected | — |
| U3.5 | XTAL_32K_N | C3GPIO1 | connected | — |
| U3.6 | GPIO2 | C3GPIO2 | connected | — |
| U3.7 | CHIP_EN | CHIP_EN | connected | — |
| U3.8 | GPIO3 | C3GPIO3 | connected | — |
| U3.9 | MTMS | USART1_TX | connected | — |
| U3.10 | MTDI | USART1_RX | connected | — |
| U3.11 | VDD3P3_RTC | 3V3 | connected | — |
| U3.12 | MTCK |  | unconnected | — |
| U3.13 | MTDO |  | unconnected | — |
| U3.14 | GPIO8 | C3GPIO8 | connected | — |
| U3.15 | GPIO9 | C3GPIO9 | connected | — |
| U3.16 | GPIO10 | C3GPIO10 | connected | — |
| U3.17 | VDD3P3_CPU | 3V3 | connected | — |
| U3.18 | VDD_SPI | 3V3 | connected | — |
| U3.19 | SPIHD |  | nc | ESP32-C3 Datasheet v2.4 §2.6/Table 2-12: in-package Flash pins must not be used for other purposes |
| U3.20 | SPIWP |  | nc | ESP32-C3 Datasheet v2.4 §2.6/Table 2-12: in-package Flash pins must not be used for other purposes |
| U3.21 | SPICS0 |  | nc | ESP32-C3 Datasheet v2.4 §2.6/Table 2-12: in-package Flash pins must not be used for other purposes |
| U3.22 | SPICLK |  | nc | ESP32-C3 Datasheet v2.4 §2.6/Table 2-12: in-package Flash pins must not be used for other purposes |
| U3.23 | SPID |  | nc | ESP32-C3 Datasheet v2.4 §2.6/Table 2-12: in-package Flash pins must not be used for other purposes |
| U3.24 | SPIQ |  | nc | ESP32-C3 Datasheet v2.4 §2.6/Table 2-12: in-package Flash pins must not be used for other purposes |
| U3.25 | GPIO18 | C3GPIO18 | connected | — |
| U3.26 | GPIO19 | C3GPIO19 | connected | — |
| U3.27 | U0RXD | C3RX | connected | — |
| U3.28 | U0TXD | C3TX | connected | — |
| U3.29 | XTAL_N | C3XTAL_40K_N | connected | — |
| U3.30 | XTAL_P | C3XTAL_40K_P | connected | — |
| U3.31 | VDDA | 3V3 | connected | — |
| U3.32 | VDDA | 3V3 | connected | — |
| U3.33 | EP | GND | connected | — |
| U5.1 | VR_PA |  | unconnected | — |
| U5.2 | VDD_IN |  | unconnected | — |
| U5.3 | NRESET |  | unconnected | — |
| U5.4 | XTA |  | unconnected | — |
| U5.5 | GND |  | unconnected | — |
| U5.6 | XTB |  | unconnected | — |
| U5.7 | BUSY |  | unconnected | — |
| U5.8 | DIO1 |  | unconnected | — |
| U5.9 | DIO2 |  | unconnected | — |
| U5.10 | DIO3 |  | unconnected | — |
| U5.11 | VBAT_IO |  | unconnected | — |
| U5.12 | DCC_FB |  | unconnected | — |
| U5.13 | GND |  | unconnected | — |
| U5.14 | DCC_SW |  | unconnected | — |
| U5.15 | VBAT |  | unconnected | — |
| U5.16 | MISO_TX |  | unconnected | — |
| U5.17 | MOSI_RX |  | unconnected | — |
| U5.18 | SCK_RTSN |  | unconnected | — |
| U5.19 | NSS_CTS |  | unconnected | — |
| U5.20 | GND |  | unconnected | — |
| U5.21 | GND |  | unconnected | — |
| U5.22 | RFIO |  | unconnected | — |
| U5.23 | GND |  | unconnected | — |
| U5.24 | GND |  | unconnected | — |
| U5.25 | EP |  | unconnected | — |
| USB1.1 | GND |  | unconnected | — |
| USB1.2 | COM |  | unconnected | — |
| USB1.3 | GND |  | unconnected | — |
| USB1.4 | LB |  | unconnected | — |
| USB1.5 | GND |  | unconnected | — |
| USB1.6 | HB |  | unconnected | — |
| X5.1 | OSC1 | C3XTAL_40K_P | connected | — |
| X5.2 | 2 | GND | connected | — |
| X5.3 | OSC2 | C3XTAL_40K_N | connected | — |
| X5.4 | 4 | GND | connected | — |
| X6.1 | OSC1 |  | unconnected | — |
| X6.2 | GND |  | unconnected | — |
| X6.3 | OSC2 |  | unconnected | — |
| X6.4 | GND |  | unconnected | — |
| X9.1 | OSC1 |  | unconnected | — |
| X9.2 | GND |  | unconnected | — |
| X9.3 | OSC2 |  | unconnected | — |
| X9.4 | GND |  | unconnected | — |

## 已确认的更正

- U3 19–24 脚为封装内 Flash 信号，外部 NC；已保存重开验证。
- GPIO18/19 是 USB D−/D+；UART0 默认 RX/TX 为 GPIO20/21。主控与 ESP32 的现有 USART 网需要按固件映射核实。
- ESP32-C3 的 microSD 使用 SPI 方案，不能沿用原稿的 GPIO18–23 四位 SDMMC 描述。
- CH334P 使用 12MHz 晶振；器件没有 RREF 脚。
- USB1/2/3 的 DPX205850DT-4055A1 是射频双工器，不是 USB ESD 器件。
- 原脚名推断建议已移至 baseline/documents，不能作为接线依据；尤其 VDD_PA、VR_PA 等脚不能仅按名字并入 3V3。
