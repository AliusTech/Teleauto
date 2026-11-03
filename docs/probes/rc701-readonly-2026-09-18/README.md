# RC701 只读探索记录

日期：2026-09-18。用户按同硬件路线探索 RM700 ROOT；实测设备仍为 RC701，结论不自动推广到 RM700。

- 序列号：7XJCM3L00111DE
- 固件：DJI/rc701/rc701:11/V01.15.03.89/389:user/release-keys
- shell uid=2000，ro.debuggable=0，ro.secure=1
- ro.boot.flash.locked=1，vbmeta.device_state=locked，verifiedbootstate=green
- 本地与设备 services.jar SHA256 相同：009014c03529abd98ce949d89486094a01dc923a3ac7e4d529fa85f4b1d395d6

## 协议服务命令表

| 命令 | 静态行为 | 本轮执行 |
| --- | --- | --- |
| help | 输出帮助，查询启用及日志状态 | 是，v10 enabled/log off；退出码255但已输出帮助 |
| push | 通过 addPackListener 注册监听，仍执行权限检查 | 否 |
| send | 调用 sendWithListen，仍执行权限检查并发送报文 | 否 |
| test | 发送预设00/01查询包 | 否 |
| enable/disable | 修改持久属性 persist.dji.feat.services | 否 |
| log_on/log_off | 修改持久属性 persist.dji.debug | 否 |

`onShellCommand` 无独立权限检查不代表子命令绕过检查；直接方法调用保持 Binder 调用身份。权限检查异常路径返回拒绝值，已用 JADX fallback 原始指令核对。

底层 `send(Pack)` 方法未调用同样的 enforceCallingPermission，JADX fallback 也显示直接 doPack/native_send。这只是静态发现：尚未完成完整 Binder/SELinux 可达性验证，未实际调用，不能视作 ROOT 或任意代码执行。

`dumpsys protocol` 可只读访问，服务version=10，现有06/AE监听包括 DemoModeMgr 与代理监听器。存在监听器不等于本轮观察到输入报文。

## 安全边界与结论

本轮仅运行 adb设备列表、getprop、id、cmd protocol help、dumpsys protocol、sha256sum；其余为本地JADX静态分析。未进入fastboot、解锁、刷写、重启、修改属性、添加监听或发送协议包。

目前未获得 ROOT，也未证明存在安全可用的 ROOT 路径。此前 unknown command 的叙述未在本轮复验，不能证明所有解锁路线均不可能。
