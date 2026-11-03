# 待确认：公开 API 去品牌命名

已完成：SDK 命名空间 `tech.alius.rc`，Android 包名 `tech.alius.teleauto`，仓库与应用拆分。

待确认的兼容性变更：

| 当前 | 建议 |
| --- | --- |
| `DJIRemoteControl` | `RcRemoteControl` |
| `DJIJoystickManager` | `RcJoystickManager` |
| 其他 `DJI` 前缀公开类 | 对应 `Rc` 前缀类 |
| `tech.alius:djirc-core` | `tech.alius.rc:rc-core` |

同步 Teleauto 的导入、共享 UI 类名、混淆规则、XML 控件引用、桌面工具和文档，并完整构建及测试。此改动会使旧导入和旧 Maven 坐标不再适用，需要作为新的不兼容版本发布；不自动发布远端。

固件使用的 USB 设备名称、厂商 ID、系统属性、广播 action 和协议字段保持原值。第三方库与硬件技术资料中的真实名称不伪造或替换。

目前尚未执行本表变更：自动审批因 API 与硬件兼容性风险拒绝了批量重命名操作。
