# DJI RC Pro 消费级机型适配

| 系统机型 | 显示名称 | SDK 档案 | 状态 |
| --- | --- | --- | --- |
| rm510 | DJI RC PRO | RM510 | 协议适配完成，待真机验证 |
| rc520 | DJI RC PRO 2 | RC520 | 协议适配完成，待真机验证 |

两者独立注册，不与 RM500、RM700 混用。机型代号忽略大小写及首尾空格，仍要求完整匹配。

输入使用固件 ProtocolManager 的 0x06/0xAE 回调，解析方式参考用户提供的 RCPlus_Control 1.00。SDK 不打包该 App 的二进制，也不使用其厂商进程名。仅在收到有效数据时报告已连接。固件必须允许宿主访问协议服务；本次没有增加权限绕过、系统签名或提权。若固件拒绝 dji.permission.PROTOCOL，机型识别仍有效，但输入不可用。

协议解码包括四轴、左右波轮、C1/C2、五维、返航/暂停/拍照/对焦/录像及三档模式；RM510 额外声明 C3（bit20）。RC520 未声明 C3 或农业肩键。参考 App 的 RM510 分支明确存在；RC520 使用同一协议格式属于待真机确认的兼容性假设。没有为未知输入节点/USB VID/PID 写入猜测值，没有套用 RM500 的 event1 权限准备流程。

验证覆盖：两个机型的独立识别、大小写与误匹配、协议轴顺序/端点、C1/C2/C3 按下去重、松开、三档状态、停止后不再发布数据，以及 core/UI Gradle 检查。真机验收需分别检查初始化授权、协议权限、各轴方向与回中、各键按下/松开、三档顺序以及重新打开 App 后的恢复。

产品标识来源：
- https://www.dji.com/es/rc-pro/specs （RM510）
- https://dl.djicdn.com/downloads/DJI_RC_Pro_2/PI/DJI_RC_Pro_2_Product_Information.pdf （RC520）

构建产物：Teleauto 的 `build/app/outputs/flutter-apk/app-release.apk`；助手 `/tmp/teleauto-assistant-target/debug/bundle/macos/Teleauto Assistant.app`。SDK core/UI 已发布至 Maven Local，Flutter 两个包 analyze/test 通过。当前 ADB 无连接设备，尚未安装本轮产物或完成真机验收。

助手全目标 `cargo test` 会构建仓库已有 Windows 专用 rc2vjoy/rc2vigem 示例，在 macOS 因 kernel32/Windows 依赖失败；本轮使用 `cargo test --lib` 验证助手本体，macOS App 构建成功。
