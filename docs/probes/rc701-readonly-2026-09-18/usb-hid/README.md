# USB HID gadget 可行性：只读实测

目标：遥控器作为 USB HID 游戏控制器连接电脑；不把内部 USB 输入设备误认为对外 HID 输出。
实测设备 RC701 / 7XJCM3L00111DE；不能自动视为其他 RM700 固件的实测结果。

## 已确认

- 运行内核：CONFIG_USB_GADGET=y、CONFIG_USB_CONFIGFS=y、CONFIG_USB_F_HID=y、CONFIG_USB_CONFIGFS_F_HID=y、CONFIG_USB_DWC3_DUAL_ROLE=y。
- kallsyms 存在 hidg_bind、f_hidg_write、hid_func_type，与内核配置相符。HID gadget 已内置，不需要为了增加该驱动而刷内核。
- 物理 UDC 为 a600000.dwc3，当前 state=configured；另有 dummy_udc.0，后者不是物理外接端口。
- 当前 sys.usb.config/state=mtp,bulk,adb；persist.sys.usb.config=adb；configfs=1。
- /config/usb_gadget/g1 和 g2 权限 root:root 0770，shell 无法遍历。未创建 HID 函数，未检测到 /dev/hidg*。
- 搜索 /system/etc/init 与 /vendor/etc/init 未发现 hidg、hid.、hid,adb 或 usb.config=hid 配置。不能直接假定设置 hid,adb 属性就能启用。
- init.qcom.usb.rc 当前模式定义四个函数：ffs.bulk、ffs.mtp、ffs.bulk3、ffs.adb。g2 另有已有用途，不可当作空闲测试目录。

## 尚未确认

尚无可用的配置授权路径；未配置游戏手柄报告描述符，未验证端点资源是否足以同时保留现有功能和HID，未验证电脑枚举、报告发送、ADB共存或重启恢复。
ROOT并非唯一可能方式，但需要有权限的系统组件完成configfs配置和hidg节点授权；普通应用与当前shell权限不足。

## 后续变更前置条件

获得明确且可控的配置权限后，先读取真实函数链接、UDC、描述符及节点权限并备份；建立经验证的独立恢复通道与设备侧超时回滚，再做仅运行时的HID+ADB测试。不能仅凭属性值声称回滚已准备完成，也不能保证重绑UDC时USB ADB不中断。
本轮没有写入USB属性/configfs、切换模式、重启、解绑UDC、刷写或运行提权工具。

## 系统配置入口复核

- 本机 framework.jar 的 UsbManager 功能表仅含 mtp/ptp/rndis/midi/accessory/audio_source/adb/ncm；usbFunctionsFromString 遇到hid会抛 IllegalArgumentException。
- UsbService.setCurrentFunctions 检查 MANAGE_USB 和 areSettableFunctions。获得该权限不等于可以创建任意configfs功能。
- 现有 usb_bulk_raw 两个进程以root运行，init限定启动参数。二进制导入与字符串指向 /dev/usb-ffs/bulk*/ep0 的描述符写入，未发现用于创建HID/configfs或接受配置请求的接口证据；不能把其root身份当成可用配置入口。
- service.adb.tcp.port 当前为5555（只读观察，非本轮启用），尚未验证网络连接和独立恢复能力。
- 目前没有找到可直接使用的受控HID配置服务。configfs写权限仍是阻塞项，不能通过标准USB模式切换解决。

本轮未运行setprop、svc usb setFunctions、service call、提权工具、重启或USB重绑。后续若继续研究权限路径，应针对明确的特权接口做离线审计；尚不能交付可执行的安全切换/回滚方案。
