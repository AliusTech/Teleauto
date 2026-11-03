# RM500 wheel and C1/C2 input

Reference: user-supplied `RCPlus_Control_1.00_Public_Test.apk.1`.
Only interface and payload behavior were used; no reference binaries are shipped.

The reference registers `ProtocolManager.getDefault().addPackListener(...)`
with `PackFilter.obtain()` and `PackRule.Builder().cmd(6, 0xAE).build()`.
Its listener implements the `com.dji.protocol.IPackListener` Binder descriptor:
transaction 1 reads a nullable `Pack` via `Pack.CREATOR`, then reads `Pack.data`;
transaction 2 reports failure. Stop removes the same listener.

The reference native decoder requires at least 17 payload bytes:

| Offset | Encoding | Meaning |
| --- | --- | --- |
| 0 | u32 LE | Buttons; C1 bit 18, C2 bit 19, RTH bit 5 |
| 4 | u8 | Extension |
| 5, 7, 9, 11 | u16 LE | Right X/Y, left Y/X |
| 13, 15 | u16 LE | Left/right wheel |

Axes subtract 1024, then divide by 660 and clamp to [-1, 1].
The SDK uses the callback for wheels and C1/C2/RTH, preserving evdev stick
handling and existing TCP mode handling. After the first valid payload,
evdev wheels and TCP button states cannot overwrite callback values.

A read-only reflection probe on the connected ag501 (Android API 25)
confirmed the manager/filter/builder/listener methods and public Pack.data /
Pack.CREATOR fields. This verifies API availability, not physical button
operation or ordinary application process registration. Those still require
an application rebuilt with the updated core and a hands-on controller check.

Validation: Android core/UI unit tests, UI boundary check, release assembly,
and both Maven Local publications passed. Flutter UI and platform plugin
analyze/test passed. Regression tests cover payload offsets, normalization,
malformed payloads, duplicate button levels, release events and fallback
source suppression.

## Ordinary-app acceptance result

On-device acceptance did **not** pass. An independent debug application built
against the published core AAR received `SecurityException: requires
 dji.permission.PROTOCOL` when registering the firmware listener. The evdev
fallback also reported `DIRECT_INPUT_PERMISSION_REQUIRED` on this device.
A non-exported service using the reference APK's `com.dji.agras` process name
was also rejected by the firmware. That experimental service was removed.
No physical wheel/button operation has been validated.

Read-only inspection of this firmware's ProtocolManagerService showed that it
checks process identity against installed system packages / system signatures.
An attempted change to use `com.dji` was rejected by automatic approval review
as an unauthorized privilege-boundary bypass, and was not executed. The SDK
retains the ordinary-process implementation; using this API on this firmware
still requires a supported authorization path.

## Joystick follow-up

The installed Flutter application initially logged `initialized=false,
license=SECURITY_ERROR`. Diagnostic logging identified
`NoSuchAlgorithmException: EC AlgorithmParameters not available` on API 25.
P-256 validation now compares the full explicit secp256r1 domain parameters;
signature verification and all license restrictions remain unchanged. A test
compares against provider-generated P-256 and rejects P-384, altered order and
altered cofactor. Android checks and Flutter plugin analyze/tests passed.

The foreground host previously swallowed embedded SOURCE_JOYSTICK events.
Both native and Flutter hosts now route them to the selected profile. RM500
accepts only its confirmed DJI RC USB identity (2ca3:1501) and maps X/Y/RX/RY
(and available Z/RZ/HAT axes) as reported by this device's dumpsys input. The
SDK authorization and output gates still apply. This does not grant direct
evdev access or bypass the ProtocolManager restriction.

The rebuilt application was installed and launched. Its new log reports
`LICENSE_NOT_ACTIVE`, confirming the earlier crypto setup error is resolved.
Device date is 2020-01-01; the bundled license begins 2026-09-11. Awaiting
correct device time and physical joystick operation; no success claim yet.

## After device time correction

At 2026-09-14 16:48 CST, device time was corrected and the installed app was
restarted. Verification advanced to the encrypted license clock store, then
failed with `KeyStoreConnectException`. System logs show the keystore process
SIGSEGV during a cipher operation, `Open tee session failed`, and Rockchip
Keymaster reporting no configured hardware module / invalid key blob. Android
restarted the keystore service, which still reported TEE session failure.
No keys, clock state, license checks or system permissions were removed or
bypassed. Physical input acceptance remains blocked pending system Keystore
recovery and successful SDK initialization.

## Authorized reboot result

The user authorized reboot. Device reconnected, boot completed, and the corrected
2026-09-14 time persisted. Boot logs still report `rk_secure_storage open fail`
and `Open tee session failed` before Teleauto starts. Teleauto remains
`initialized=false, license=SECURITY_ERROR`.

The post-reboot exception maps through the installed release R8 mapping to
TeleautoClockStore.kt:25: the key alias exists but neither the clock state file
nor its backup exists. This is consistent with the earlier first-write crash.
The fail-closed state consistency check rejects that incomplete initialization.
No data or key reset was performed. Reboot did not resolve either the TEE issue
or the incomplete authorization-clock state.

## User-authorized clean reinstall

At 2026-09-14 17:03 CST, `tech.alius.teleauto` was uninstalled without keeping
data and the latest release APK was installed successfully. On its first
launch, license clock encryption again failed with KeyStoreConnectException
and SDK reported `initialized=false, license=SECURITY_ERROR`. A clean install
therefore reproduces the underlying Keystore failure; the earlier incomplete
state is not the sole cause. No other applications were uninstalled.

## Second RM500 comparison

A distinct RM500, serial 455LJ3T2010192, runs the same Android 7.1.2 build
DJI/ag501/ag501:7.1.2/V01.00.00.38/38:user/release-keys. After installation and
correcting its time to 2026-09-14, Teleauto was launched at 17:17 CST.
Keystore again crashed with SIGSEGV during cipher initialization, preceded by
Rockchip Keymaster key-blob parsing and TEE errors. SDK reported
`initialized=false, license=SECURITY_ERROR`. The failure is reproducible on
both tested RM500 units with this firmware; this does not establish that all
RM500 firmware versions are affected. RC Plus 2 (Android 11) initialized with
VALID authorization and the user confirmed normal controls on that device.

## Isolated KeyStore diagnosis (17:19 CST)

A standalone Android application with **no Teleauto SDK dependency** was built
and run on the second RM500. It created only temporary, application-scoped
aliases and deleted them after each test. Results:

| Test | Result |
| --- | --- |
| Software AES-256-GCM encryption/decryption | Passed, byte-for-byte roundtrip |
| AndroidKeyStore load | Passed |
| AndroidKeyStore RSA-2048 generation | Failed while loading generated key pair: Invalid key blob |
| AndroidKeyStore AES-128 generation | Returned a key successfully |
| Reload that new AES-128 key | Failed: UnrecoverableKeyException / Invalid key blob |

This isolates the fault to the device's AndroidKeyStore/Keymaster key backend,
not Teleauto's JSON, license signing key, AES-256 key size or GCM algorithm
implementation. A returned key handle does not establish a usable stored key.
The AES-128 test failed before cipher.init, so no additional encryption crash
was needed to establish this. Production SDK first-run failure at cipher.init
and later consistency-check failure remain separate stages of the same issue.
The underlying TEE deployment/storage cause still requires firmware-level
investigation; these tests do not prove physical hardware damage.

Probe source is preserved in `keystore/MainActivity.java`; no generated keys
or production license data are included.

## Software clock storage change

At the user's request, new clock storage uses Bouncy Castle 1.85.2 lightweight
AES-256-GCM. SDK authorization signing/verifying keys are unchanged and are
never written to AndroidKeyStore. The separate local software key is stored in
no-backup application-private storage. New key/state pairs are committed by
one directory rename; updates atomically replace the encrypted state file.
The software key provides sandbox-level protection, not hardware-backed key
secrecy. Existing legacy clock files use read-only migration; a device with no
legacy clock file never invokes AndroidKeyStore, including an RM500 with an
orphaned alias from the earlier failed first write.

Eleven new tests passed: independent JDK cipher interoperability, nonce
uniqueness, restart/rollback floor, altered ciphertext/key/package rejection,
missing/truncated committed data rejection, interrupted staging/update recovery,
and legacy-floor migration with no silent reset. Required Android core/UI
checks and both Flutter packages' analyze/test passed. Core/UI Maven Local
artifacts and a release APK were built. Device installation was attempted but
ADB had no attached devices; physical validation is pending reconnection.

Library reference: https://www.bouncycastle.org/download/bouncy-castle-java/

### 2026-09-14 输入权限复验（第二台 RM500）

- 软件时钟存储版已安装，首次启动与强制停止后重新启动均为 `initialized=true license=VALID`。
- 用户反馈所有输入无响应。实际 `/dev/input/event1` 为 group 10045、mode 640；当前 `tech.alius.teleauto` UID 为 10046。Android EventHub 没有加载 DJI RC 节点，普通应用直读也被拒绝。
- 助手原状态检测仅检查配置文件存在，遗漏卸载重装后 UID 变化。已在 Teleauto 助手源码增加当前 UID 与 event1 group/mode 校验及回归测试，并将输入按钮命名为“修复输入权限”。
- 用户授权通过助手重新绑定权限并重启。首轮输入准备因 root 创建的探针文件不可被 shell 读取而失败；正在修复探针可读性并复验。尚未据此认定物理输入恢复。

输入权限准备最终结果：修复探针文件可读性、拆分过长的配置复制命令后，助手完成准备并自动重启。重启后 event1 的 group/mode 为 `10046 640`，与当前 App UID 10046 一致。助手界面显示“设备环境已就绪”。日志确认 `direct input connected /dev/input/event1`、`CONNECTING -> CONNECTED`、`initialized=true license=VALID`。助手 2 个回归测试、JS 语法检查、版本一致性检查通过。物理摇杆/波轮/C1/C2 响应仍需用户操作验收。

### 按钮与三段开关后续反馈

用户反馈 C1/C2/C3 与三段开关仍无响应（C3 对应实体控件待确认）。运行日志仍有 `dji.permission.PROTOCOL` 拒绝；对 40007、40009 进行独立只读连接，各等待 8 秒均未收到数据。临时端口转发已移除。
SDK 修复了 ProtocolManager 回调缺失三段开关发布的问题，并在协议数据生效后忽略 TCP 开关回调。新增三档切换及重复状态不重复触发的回归测试；必需 Gradle 检查与 core/UI Maven Local 发布通过。此代码修复尚未重新安装到设备，且不解决固件协议访问拒绝。等待参考 App 在同一设备的对照结果，未进行系统进程身份伪装或协议权限绕过。
