# SDK / Teleauto 项目拆分

- `dji_rc`：SDK、输入统一分发与锁定拦截、BLE/USB/MAVLink、机型适配、图传底层及测试。
- 同级 `Teleauto`：根目录 Android 工程（`app` / `ui`），`assistant/` 为桌面 Rust/Tauri 项目；同时维护应用素材、布局、用户手册。
- 迁移保留了当时全部未提交应用代码。迁移前后对 473 个源码/资源文件逐个校验 SHA-256，再修改构建配置与路径。
- SDK Maven 坐标继续为 `tech.alius:teleauto-core`。SDK 的 `VERSION` 与 Teleauto 的 `VERSION` 独立。
- Teleauto 使用 `djircSdkVersion` 固定 SDK 版本。本地先执行 `./gradlew :sdk:publishDjircPublicationToMavenLocal`，或在应用构建时用 `-PsdkSourceDir=../dji_rc` 显式联调。
- 本次未创建远端、未提交、未推送；旧应用提交历史仍在 SDK 仓库中。

验证命令：

```sh
./gradlew :sdk:assembleRelease :sdk:testDebugUnitTest :sdk:publishDjircPublicationToMavenLocal
cd ../Teleauto
./gradlew :app:assembleDebug :ui:testDebugUnitTest :app:verifyUiBoundary
cd assistant
npm run version:check
npm run tauri -- build --bundles app
```

当前 SDK 命名空间已迁移为 `tech.alius.rc`；Teleauto 的应用包名为 `tech.alius.teleauto`。公开 DJI 前缀类名和 Maven 坐标暂时保留：批量 API 重命名被自动审批拒绝，待用户确认后再执行。当前 Maven Local 包仅用于本机联调；正式发布前需为不兼容的命名空间变更确定新版本。

独立桥接固件及验证资料（原 ble_hid 目录）已移除；SDK 内的 BLE HID 传输和输入分发仍保留。隐藏文件统一由 `.*` 忽略，例外保留 `.gitignore` 与 `.github/`。

最新职责调整：共享 UI 已迁回 `sdk/ui/`，组件文档位于本仓库 `docs/ui/`、`docs/design/`。Teleauto 只维护业务编排并依赖 UI AAR。当前 core 仍为 `:sdk`；图传底层的可选依赖拆分尚未完成。

对外产品名统一为 **Teleauto SDK**，模块显示名称为 Teleauto SDK Core / Teleauto SDK UI；SDK 命名空间保持 `tech.alius.rc`。

已完成明确指定的命名迁移：`JoystickManager`、`DeviceManagementView`，制品坐标为 `tech.alius:teleauto-core` 和 `tech.alius:teleauto-ui`。其余公开类和资源名的批量改名未执行。
