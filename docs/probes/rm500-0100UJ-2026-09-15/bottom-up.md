# RM500 底层输入排查（2026-09-15）

设备：455LJ1710100UJ，AG501，Android 7.1.2。尚未确认全部目标按钮事件。

## 已实测

- 内核启动日志：内部 USB 2-1，VID 2ca3 / PID 1501，产品 DJI Virtual Joystick。
- sysfs 驱动链接指向 `bus/usb/drivers/xpad`；不是通用 HID 驱动。
- USB urbnum 从 5224 增至 6626，runtime_status=active；说明存在 USB 活动，不等于按钮已上报。
- 当前 adb shell uid=2000，附加组包含 1004(input)。普通 `getevent -lp`、`getevent -lt` 可直接读取全部输入节点。此前只根据 root:input 0660 判断 shell 无权限不成立。
- 实时 event1 已捕获 ABS_X/ABS_Y 摇杆变化。event2 已捕获 KEY_POWER；本轮物理操作尚待用户确认，不建立目标按钮映射。
- 40007：5秒被动监听，然后每2秒发送已公开定义的 General Version Inquiry (00/01)，共10次，仍收到0字节。收发日志见 tcp-40007-version-query.jsonl。未发送控制或配置命令。

## 固件静态证据

从本机 services.dex 反编译 ComKeyManager、MotionHelper、DjiServicesHelper：

- ComKeyManager.interceptMotion 依次做解析、启用状态、应用过滤。
- DjiServicesHelper.enableMotion：设备需已配置；先检查资源包名排除列表；dji_lab_game_mode 非0时允许，否则仅允许 SYSTEM / UPDATED_SYSTEM_APP。
- 当前 settings global dji_lab_game_mode=0。这里只读取，尚未修改。
- 该条件解释普通应用 MotionEvent 可能被拦截，不能据此宣称 C1/C2/暂停/返航/三段开关已找到。

## 公开资料

- https://github.com/stiad/dji-rc-linux/blob/main/dji_rc_joystick.c ：40007 版本查询保活；作者未验证 RM500。
- https://github.com/o-gs/dji-firmware-tools/blob/master/comm_dissector/wireshark/dji-dumlv1-general.lua ：00/01 为 Version Inquiry。

下一步：按操作标注采集所有内核输入节点，确认目标按钮是否出现在 xpad 下游，再比较 Android 分发；不把其他机型位域当成 RM500 实测映射。

## 已确认操作的逐行抓包

`kernel-all-nodes-04-pty` 已完成180秒采集，用户确认操作 C1/C2/暂停/返航/三段开关及摇杆。全部4个节点均已打开，PTY避免末尾stdio缓冲丢失。收到event1 ABS_X/ABS_Y及event0触摸事件，唯一EV_KEY为event2 KEY_POWER DOWN/UP；没有目标按钮或HAT变化。完整计数见该目录analysis.json。该结果仅说明目标未从当前evdev链路暴露，不能排除独立MCU协议或xpad未解析的USB字段。
