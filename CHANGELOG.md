# 更新日志

## 未发布

- **rc701 连续波轮与三段式开关支持**:两路输入不经 Android 输入栈,新增
  `TcpInputReader` 通道(固件本机遥测接口 `localhost:40007`,DUML 遥测帧,
  无需 root)获取——波轮连续值更新 `StickSnapshot.leftDial/rightDial`,
  三段开关档位以 `PhysicalKey.MODE_1/2/3` 的 `TRIGGER` 事件上报;
  `Rc701Profile` 独立档案(`useTcpInput`),与一代 rm700(evdev 波轮)分离
- API 分层:`DJIRemoteControl` 总门面(生命周期 / 设备信息 / `VERSION`)+
  `DJIJoystickManager` 摇杆输入组件(监听 / 快照 / 模式 / 蓝牙手柄);
  `DJIRemoteControlMode` 更名 `Mode`,后续射频等 manager 以平级组件加入

本项目的版本化发版流程:推送 `vX.Y.Z` tag → CI 自动构建并把 `tech.alius:djirc:X.Y.Z` 发布到 GitHub Packages(见 README「发布(维护者)」)。

## v0.1.0(2026-08-17)

首个版本化发布。

### djirc SDK(`tech.alius:djirc`)

- **双通道输入读取**:adb 自连 `localhost:5555`,`getevent -l` 读摇杆/拨轮/拍照/电源,`logcat -s KeyCodeListener` 读 DJI 私有按键;断线 1.5s 自动重连
- **机型档案**:内置 rm500 / rm510 / rm700(含 rc701 代号)三份 `DeviceProfile`,按 `ro.product.name` 精确匹配;rm700 已在 RC Plus 真机实测
- **统一输入模型**:`PhysicalKey` / `KeyAction` / `StickSnapshot` 跨机型一致,摇杆归一化与死区、录像 toggle 防误翻转、半按对焦双通道低延迟
- **蓝牙手柄模式**(`DJIRemoteControlMode.BLUETOOTH_GAMEPAD`):BLE HID over GATT,一行切换把遥控器变成 PC/Mac 的 Xbox 布局系统手柄,零 root
- **零第三方依赖**:纯 framework API,ADB 协议由内置 adblib(MIT)源码实现;`DJIRemoteControl.VERSION` 暴露构建注入的版本号

### Demo 应用(`:app`)

- 输入测试器:机型识别、六轴实时显示、全部按键状态
- 前台保活服务 `DJIRemoteControlService`:`START_STICKY` + `PARTIAL_WAKE_LOCK`,Activity 关闭/息屏后读取不中断;前台期间屏幕常亮

### 安装助手 DJI RC Configurator(`installer/`,Tauri)

- 设备识别与连接、本地/在线双方式选定安装包、安装与授权流水线
- 设备注册联网检查、应用维护(拉手式面板,白名单应用的删除/禁用)
- 实机按键测试(三视图目标引导 + 预览窗口,完成自动收尾)
