# RM700 (rc701 / DJI RC PLUS 2) 安全研究档案 — 2026-09-18

零风险(只读)摸底 + 离线二进制分析。研究目的:评估设备提权可行性,为 USB HID gadget
(内核 `CONFIG_USB_CONFIGFS_F_HID=y` 已具备)与深度集成做准备。

## 环境画像

| 项 | 值 |
|---|---|
| 型号 | DJI RC PLUS 2(product: rc701,serial 7XJCM3L00111DE) |
| SoC | Qualcomm lahaina(骁龙 888) |
| 系统 | Android 11,补丁 2022-11-01,内核 5.4.210-qgki-ge1f631e431b5 |
| 构建 | user / ro.debuggable=0 |
| Bootloader | 锁定(flash.locked=1),verified boot green |
| **SELinux** | **Permissive(实测 getenforce)** |
| 挂载 | /vendor、/system 均只读(ext4 ro) |

## 已验证原语

1. **shell 可写 `dji.*` 系统属性**(实测 setprop 成功,Permissive 放行)。
   可启停全部属性触发的 root 服务(见下表)。
2. `/blackbox` 全局可写(0777,root:system),root 消费方仅路径拼接,无命令注入。

## Root 守护进程(init.svc.*)

dji_amt、dji_upgrade、dji_sdrs_agent、dji_rtk、dji_rtk_gnd、dji_blackbox、
dji_link、dji_wlm、dji_lte、dji_wlm_slave、dji_config_store —— **全部 uid=0**。

属性触发(已确认可由 shell 拉起/停止):
`dji.wlm_service`、`dji.wlm_slave_service`、`dji.blackbox_service(_v2)`、
`dji.configstore`、`dji.upgrade_service`。

## 监听面

- Unix socket(root 属主,0666 全局可连):`/dev/socket/fpv_sock{,1,2}`、`/dev/socket/dji_fpv`
  ——属主 `dji_link`(root),见 `/vendor/etc/init/hw/init.qcom.rc:942`
- TCP 0.0.0.0(root uid):8901、8902、40007、40008、40009(WiFi 可达;shell 无权读
  /proc/*/fd,未能映射到具体进程;drpc_config.json 内部 RPC 均为回环,非其来源)

## dji_link 逆向(离线,capstone)

- 格式串交叉引用:
  - `am start %s` @0x23f94 / `am force-stop %s` @0x24018 / `start|stop %s`(init 服务)
  - 特例:`settings put global bluetooth_on 0`(停 com.android.bluetooth)
- handler 0x23f54:`%s` 取自全局表条目+3(条目 0x44 字节:+0 u16 使能,+2 格式选择,
  +3 包名字符串),snprintf 后调 0x3da40(疑似 popen/system)
- 表初始化 0x23b88:解析 **`/vendor/etc/lmk.json`** 的 `proc_list[].name`
  (路径可被环境变量 `DJI_JSON` 覆盖,但 init 起的 daemon 环境不可控;/vendor 只读)
- 结论:该注入点需要 root 才能改输入 → 循环依赖,暂不可用

## 其他 daemon popen 面(字符串级)

- dji_lte:多处 popen;LTE/Quectel 模块管理(AT 命令、FOTA httpx 客户端),无 web 服务
- dji_sdrs_agent:popen;"SDRS LOG socket listen at port %d"(TCP);配置
  /system/etc/drpc_config.json(RO);执行命令多为固定参数(dji_mb_ctrl / ln / rm)
- dji_amt:无执行面
- upgrade_test(root oneshot):`upgrade_test $(cat /data/upgrade_test_param)`
  —— 参数文件位于 /data(system:system 771),shell 不可写,阻塞

## 排除项

- /config/usb_gadget/g1 仅 root 组可写 → USB HID 差一个 root shell
- Magisk 快捷路线:BL 锁 + 无 OEM 解锁开关(待实测 fastboot)
- dji_config_store:只解析 /system/etc/dji.json(RO),无执行面

## 下一步(按优先级)

1. dji_link / dji_wlm 的 4×unix socket + TCP 40007-9 协议测绘(需 Ghidra 级反编译
   或运行时被动观测;协议客户端疑在 system_server 的 com.dji.DjiServicesHelper,
   services.jar 内已定位到类,接口未还原)
2. `adb reboot bootloader` 实测 fastboot 与 OEM 解锁状态(可逆,零砖风险)
3. 高通 EDL(9008)通道研究:lahaina firehose loader 可得即可全盘 dump(只读)
4. Java 层 com.dji.protocol.IProtocolManager AIDL 还原(services.jar 已拉取),
   评估 binder 面是否有写文件/执行原语
