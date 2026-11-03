# AG501 / RM500 射频板连接探测

探测日期：2026-09-08。通过 Mac ADB 只读检查；未重启、修改 GPIO、开启射频或更改设备配置。
设备实际报告 product/model/device=ag501，平台 rk3399，Linux 4.4.83。项目将 AG501 归入 RM500 档案；以下结果仅针对这台 AG501，不视为所有 RM500 变体的实测结果。

## 结论与证据边界

固件证据强烈支持主板与 Pigeon/SDR 子系统通过 USB 承载网络通信及专用 Bulk 数据通道，并由 GPIO 输入反馈射频状态。但本次没有枚举到目标 USB 设备，因此还不能实测确认其 USB 版本、物理控制器路径、接口描述符或板间连接器针脚。

| 通道 | 证据 | 判断 |
|---|---|---|
| USB 网络 | djilink 程序内存在 Pigeon 监控线程、usb0 收发统计路径、usb0 恢复命令、本机地址 192.168.50.1/192.168.50.3；link.json linkid=2 指向 192.168.50.2:40004 | 固件设计中的 Pigeon 网络通道；当前 usb0 不存在，不能确定具体 RNDIS/CDC 类型及实际本机地址 |
| USB Bulk | link.json 启用 VID:PID=2ca3:001f；linkid=98 为 interface 3、endpoint 0x84；linkid=99 为 interface 8、endpoint 0x89、is_download=1；djilink 含 UsbBulkTrans/UsbBulkDownload 和视频帧处理日志字符串 | 固件配置的数据/下载通道；目标设备当前未枚举，通道用途未通过实际传输验证 |
| 射频状态 GPIO | virtual_sdr compatible=dji,sdr，rf-pin1/2/3 引用 gpio1 控制器，偏移 13/22/23，flags=0；debugfs 显示三者均为输入 | 对应 GPIO1_B5、GPIO1_C6、GPIO1_C7；当前全部低电平，不应直接当作电源使能输出 |
| UART | link.json 启用 /dev/ttyS4，115200、8N1，target=0x06,0x00；本地 40018/40010 转发到该 linkid | 独立 RC 控制通道线索；不能据此认定 ttyS4 直接连接射频板 |

GPIO 在本内核中的全局编号分别为 1045、1054、1055；这不是排线针脚编号。设备树中 gpio1 控制器地址为 ff730000。

## 本次在线状态

- USB 仅发现一个非 root-hub 设备：2-1，2ca3:1501，产品名 DJI Virtual Joystick，xpad 驱动，12 Mbit/s。不能把它等同于配置中的 2ca3:001f 射频数据设备。
- 网络接口只有 lo、sit0、wlan0、p2p0，没有 usb0。
- virtual_sdr/state=0，aircraft_type=0；dji.rc.connected=0。状态数字的具体语义尚未从驱动源码验证。
- SPI 当前设备是 MPU6500；SDIO 设备绑定 ar6k_wlan，模块 qca6174a；PCIe 设备树 status=disabled。这些结果不支持将当前 SPI/SDIO/PCIe 设备认定为 DJI 射频数据链路。
- 普通 shell 无权读取 djilink 的进程文件描述符；设备无 su，ro.debuggable=0。未尝试提权或改动服务。

“没有 usb0/目标 USB 枚举”只能说明本次采样时射频数据通道不在线，不能区分供电未开启、运行模式、排线断开、固件策略或硬件故障。

## 下一步实物验证

在射频功能正常启动状态下复查 2ca3:001f 枚举、usb0 和三根状态 GPIO，即可继续定位 USB 控制器及接口类型。连接器规格、USB 数据线所在针脚、电源轨电压，以及 UART 是否跨这条排线，需要结合板卡照片、原理图或断电通断测量确认，软件探测不能给出可靠针脚定义。

## 原始证据

- [link.json](link.json)：设备 /system/etc/link.json 原样复制。
- [runtime.txt](runtime.txt)：GPIO、网络接口、SDR 状态和 USB sysfs 快照。
- 离线分析临时文件：/private/tmp/rm500-djilink 和 /private/tmp/rm500-device-tree。
