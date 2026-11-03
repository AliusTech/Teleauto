# Teleauto License Generator

Rust 交互式授权签发工具，同时提供供 FastAPI 调用的非交互 JSON 协议。运行环境为管理员终端或授权服务器。默认构建内置本目录 `keys/` 下的私钥；Android SDK 仅使用导出的公钥。

## 构建与交互操作

```sh
cargo build --manifest-path license-generator/Cargo.toml --release --locked
./license-generator/target/release/teleauto-license-generator
```

无参数启动中文向导：填写目标 SDK 版本 → 选择适用密钥 → 填写开发者/组织 ID、Android 包名及 APK 签名证书 SHA-256 → 选择生效时间和 30/90/365 天或自定义到期时间 → 确认保存。默认授权 ID 为随机 UUID，可指定服务端分配的 ID。取消或输入结束不会签发文件。输出文件已存在时拒绝覆盖。

证书指纹必须取自实际安装 APK 的签名证书，不能用 APK 文件摘要替代。支持多个当前签名者或计划轮换的证书，逗号分隔。日期使用带时区的 RFC3339；签名文件内使用 UTC Unix 秒，生效时间包含边界、到期时间不包含边界。

## 密钥和 SDK 版本规划

首把密钥：`teleauto-0.1.0-k1`，适用 SDK `>= 0.1.0`，暂不设置上限。这里指采用此公钥及授权校验逻辑的 SDK 构建；不能追溯改变已发布的无授权校验构建。

每把密钥有独立、不可复用的 `keyId`，对应一份私钥和公开的版本策略：

| 字段 | 含义 |
| --- | --- |
| `sdkFrom` | 最低适用 SDK 版本，包含该版本，生成时必填 |
| `sdkUntil` | 停止适用的 SDK 版本，不包含该版本；`null` 表示不设上限 |
| `publicKey` | P-256 SPKI DER 的无填充 Base64URL |

版本仅接受 `X.Y.Z`，按数值比较，不接受预发布后缀。签发时检查目标版本；Java 公钥解析器也按 SDK 版本过滤信任密钥。许可证本身通过 `keyId` 使用这个版本范围，不是仅限定于签发时填入的单个版本。

生成下一把密钥示例（版本范围 `[0.5.0, 1.0.0)`）：

```sh
./license-generator/target/release/teleauto-license-generator keygen \
  --key-id teleauto-0.5.0-k1 --sdk-from 0.5.0 --sdk-until 1.0.0 \
  --output-dir license-generator/keys
cargo build --manifest-path license-generator/Cargo.toml --release --locked
./license-generator/target/release/teleauto-license-generator keys
./license-generator/target/release/teleauto-license-generator export-public \
  --output /tmp/teleauto-license-public-keys.properties
```

审核导出的公钥表后更新 `sdk/license-public-keys.properties`，随新 SDK 构建发布。停止接受旧密钥需更新旧密钥策略的 `sdkUntil` 并重新导出公钥表；不要覆盖旧私钥或让同一 `keyId` 指向新密钥。过渡期可允许多个密钥同时适用。签发器按显式 `keyId` 选择，避免自动选错。

纯离线已发布 SDK 的信任策略不能远程撤销。服务器可以停止签发或续期，已经发出的有效文件仍遵循接收端 SDK 内置规则和到期时间。

## 非交互签发

```sh
./license-generator/target/release/teleauto-license-generator issue \
  --key-id teleauto-0.1.0-k1 --sdk-version 0.1.0 \
  --vendor developer-123 --package tech.customer.controller \
  --cert AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA \
  --days 365 --output /tmp/license.json --json
```

示例指纹必须替换为客户 APK 的真实 SHA-256。成功返回 `ok: true`，`result.license` 是应分发给客户的完整授权文件内容；`result.claims`、`sdkVersion` 等是管理元数据，不能当作授权文件放入 APK。客户将 `license` 对象保存到 `app/src/main/assets/sdk/license.json`。

错误退出码为 `2`，正常为 `0`。`--json` 模式 stdout 仅输出一份 JSON；错误响应含 `error.code` 和 `error.message`。参数错误为 `INVALID_ARGUMENTS`，业务/文件/签发错误为 `LICENSE_OPERATION_FAILED`。`--help`、`--version` 为终端帮助输出。

## FastAPI 调用合同

服务端通过固定可执行文件和固定参数启动：

```sh
teleauto-license-generator issue --request - --json
```

向 stdin 写入一份 UTF-8 JSON，不使用 shell 拼接，不需要终端。请求上限 32 KiB：

```json
{
  "keyId": "teleauto-0.1.0-k1",
  "sdkVersion": "0.1.0",
  "vendorId": "developer-123",
  "packageName": "tech.customer.controller",
  "certSha256": ["AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"],
  "notBefore": 1798761600,
  "expiresAt": 1830297600,
  "licenseId": "server-assigned-license-id"
}
```

`notBefore`、`licenseId` 可省略或为 null，分别默认当前时间、随机 UUID。服务端应显式提供授权 ID，以便关联幂等记录。未知字段拒绝。stdin 模式不允许混用身份或期限命令行参数。

[异步调用适配器](examples/fastapi_issuer.py) 可放入后续 FastAPI 工程：固定程序路径、并发限制、15 秒超时、取消时回收进程、JSON 解析。其职责仅为调用签发器，没有开放 HTTP 路由，没有实现用户注册或数据库。

未来服务端负责：

- 注册、登录和开发者身份；由认证结果确定 `vendorId`。
- 应用归属、包名及证书登记；SDK 版本、可选密钥、最大有效期由服务端权限策略决定，不直接信任用户提交。
- 授权记录、续期记录、下载权限和审计。建议保存授权 ID、开发者 ID、应用 ID、SDK 版本、keyId、生效/到期时间、完整原始授权文件、文件 SHA-256、签发人及时间。
- 幂等请求：对开发者 ID 和幂等键设唯一约束；先持久化请求并占用签发记录，签发结果持久化成功后才响应。重试返回原文件，不重新签发不同 `issuedAt` 的文件。
- 续期生成新授权记录和文件，保留历史。后台“撤销”只影响服务端发放流程，不声称离线 SDK 能即时收到撤销。

FastAPI 不实现第二套签名算法。不要将包含私钥的 CLI 提供给普通注册用户下载；向客户仅交付授权文件和 SDK。

## 密钥存放和部署

按项目所有者要求，私钥保存在私有 SDK 仓库 `keys/`。默认 release 可执行文件内置这些私钥，可直接部署到服务器。新增密钥后必须重新构建；修改运行目录不会改变已编译的内置密钥。

也支持不嵌入密钥的构建，用于服务器外置配置：

```sh
cargo build --manifest-path license-generator/Cargo.toml --release --locked --no-default-features
./license-generator/target/release/teleauto-license-generator --key-dir /srv/teleauto/issuer-keys keys
```

外置目录完整替代内置密钥，不混合来源。私钥文件在 Unix 上生成权限为 `0600`。只有签发器编译过程读取私钥；SDK Gradle 仅导入公开的 `.properties`，不得把签发器、PEM 或 keys 目录放入 AAR、APK 或公开下载附件。

## 验证

```sh
cargo test --manifest-path license-generator/Cargo.toml --locked
cargo build --manifest-path license-generator/Cargo.toml --locked
python3 license-generator/tests/interop.py
python3 license-generator/tests/interactive.py
python3 license-generator/tests/check_sdk_artifacts.py sdk/build/outputs/aar/sdk-release.aar sdk/ui/build/outputs/aar/ui-release.aar
cargo clippy --manifest-path license-generator/Cargo.toml --locked --all-targets -- -D warnings
```

互操作测试生成临时密钥，用 Rust CLI 签发，再用 SDK 共用的 Java 校验器验证；覆盖版本上下界、包名/证书不匹配、过期、未生效、篡改、JSON 输入错误及覆盖保护。需要 JDK 17+。

授权格式：ECDSA P-256 / SHA-256，签名输入为 `payload` 的原始 Base64URL ASCII 字符串，签名输出使用 ASN.1 DER 再 Base64URL 编码。不要重新序列化载荷后验签。

## Android SDK 授权接入

将 `result.license` 内容保存为宿主 Android 工程的 `src/main/assets/sdk/license.json`。Flutter 宿主也应放在 `android/app/src/main/assets/sdk/license.json`，不放入 Flutter 的 `flutter_assets` 子目录。

```kotlin
val config = TeleautoConfig(
    licenseVendorId = "developer-123",
    licenseListener = LicenseListener { status ->
        // Application owns localized presentation of status.error.
    },
)
val initialized = TeleautoSdk.initialize(applicationContext, config)
val authorization = LicenseManager.status
```

SDK 初始化先验证签名、公钥版本范围、包名、当前 APK 签名证书、生效与到期时间，再初始化硬件。无授权或无效授权返回 `false`，通过 `LicenseManager.status` / 监听器获取原因。摇杆及普通按键回调、触屏输入、HID/高频头输出均受同一授权状态约束；未授权的触屏提交返回 `LICENSE_REQUIRED`。授权不会覆盖原有屏幕锁定规则。

运行中每次控制输出检查时间，后台每秒检查授权，过期立即撤销会话并安排资源释放；停止录像沿用既有收尾流程。时间状态通过 Bouncy Castle 轻量 API 的 AES-256-GCM 加密保存于 no-backup 目录，30 秒一次持久化。软件随机密钥只用于本地时间记录，与授权签发私钥/验签公钥无关；密钥和首次记录作为一个目录原子提交，后续记录采用原子替换。新安装不读写 AndroidKeyStore。已有旧版加密时间记录仅在首次迁移时尝试通过原 AndroidKeyStore 密钥读取，无法迁移时明确失败，不丢弃原时间下界。软件密钥依赖应用沙箱保护，可被取得应用私有文件的攻击者提取，不具备硬件密钥的保护强度；进程内结合单调时钟与已观察到的最高墙钟防止频繁调用或回拨延长授权。允许 120 秒墙钟误差，但不会把有效时间调回。跨重启使用持久化时间下界。清除应用数据、系统被控制或修改 SDK 二进制仍可能绕过纯离线保护，不能视为可信在线时钟或绝对防破解机制。

本机验证涵盖 Java/Rust 验签互操作、时钟与输出门禁的单元测试和 SDK 构建。真机签名证书、旧版时间记录迁移、运行中到期及各硬件传输链路仍需设备验收。
