# RM500 系统输入服务可行性探针

独立包名 `tech.alius.rc.inputprobe`，默认进程名与包名一致。仅订阅 ProtocolManager 06/AE，不发送控制包，不导出 Service/Binder 接口，无网络权限。仅记录输入计数及 C1/C2/C3、返航、三段状态。BOOT_COMPLETED 接收器用于验证重启启动；原型不与 Teleauto 进行 IPC。

## 已完成

2026-09-14 在 RM500 / ag501 `455LJ3T2010192` 普通安装运行：`system=false`、`SUBSCRIBE_FAILED`、`dji.permission.PROTOCOL` 拒绝、frames=0。已停止探针重试。APK 构建通过。

## 待明确授权的系统安装对照

目标新增目录仅为 `/system/app/TeleautoInputProbe/`，APK 为 `TeleautoInputProbe.apk`。通过既有助手准备通道临时将 /system 重挂为可写，安装独立探针、恢复文件权限和 SELinux 标签、核对 APK 字节后恢复 /system 只读，再重启。保持默认进程身份，不覆盖现有系统应用。重启后依次验证 system=true、SUBSCRIBED、实际输入事件、再次重启时 BOOT_RECEIVED 和数据恢复。

这会使探针持久获得系统应用身份；它不只是普通 APK 安装。固件兼容问题可能导致系统扫描或启动异常，需具备设备恢复手段。回滚需要删除上述专用目录、恢复 /system 只读并重启，再卸载普通安装的 `tech.alius.rc.inputprobe`。不删除 Teleauto 数据。

首次自动审批拒绝系统分区部署；用户明确同意后，2026-09-15 已完成下述系统安装对照。

## 2026-09-15 系统安装结果

- 用户授权后，独立目录 `/system/app/TeleautoInputProbe/TeleautoInputProbe.apk` 安装完成，拉回 APK 逐字节校验一致；文件 root:root、0644、system_file 标签，/system 恢复只读。
- 自动重启后收到 `BOOT_RECEIVED`；`uid=10047 system=true updatedSystem=true`。原 /data/app 同包 APK 作为系统应用更新被 Android 保留。
- `SUBSCRIBED cmd=06/AE`，dumpsys protocol 中实际存在 key 1710、cmd=(06,ae) 的监听器。未再出现权限拒绝。
- 用户依次操作 C1/C2/C3、返航、三档后仍为 frames=0。因此仅证实持久系统身份、开机自启和协议监听注册可行，尚未证实此固件通过 06/AE 上报控件。不可将此结果标记为 RM500 输入已修复。
- 后续只读检查时 ADB 断开；未进一步调整过滤器。探针仍保留为开机启动的系统应用，没有转发数据给 Teleauto。

## 报文与过滤器排查（待设备回连）

本机保存的 services.dex 中 `PushQueue.notifyReceiversLQ` 按 `(cmdSet << 8) | cmdId` 查询监听器，然后直接回调；它不使用注册规则中的 sender/receiver 筛选。唯一显式丢弃规则是 sender=(8,1)、cmd=(0,0x42)，不是 06/AE。`ProtocolManagerService.notifyRecv` 会将主动/应答包交给 ActQueue，其他包交给 PushQueue，所以没有 Push 回调也不能推断固件完全没有流量。

扩展探针已编译：订阅 06 命令集 256 个命令，以及 00/42、21/06 两个已知状态/心跳对照。记录所有收到的命令计数；仅对 06 命令记录最多 32 字节前缀，每秒最多 20 条变化日志。只有 06/AE 且长度至少 17 才使用既有按钮解析，其余报文不猜测映射。所有订阅均为只读，不调用 send，也不修改丢弃规则。

设备仍未连接，扩展 APK 尚未安装，暂无新的实际报文证据。下一步普通覆盖更新同包探针（设备已有系统底包）、启动并操作各控件，比较命令/字节变化。若所有对照命令都无数据，再检查上游传输及主动/应答分流。

## 实施采样工具

`capture.py` 只读取指定 RM500 的探针日志，不清空日志、不修改设备、不自动判断物理动作已完成。每段记录固件、操作标签、重复编号、设备 uptime、主机相对时间以及传输中断情况，保留 JSONL 原始内容。输出目录必须不存在，防止覆盖证据。

```sh
python3 tools/rm500-system-input-probe/capture.py \
  --adb /Users/weixug/Library/Android/sdk/platform-tools/adb \
  --serial 455LJ3T2010192 --label c1 --repeat 1 --seconds 20 \
  --output /tmp/rm500-captures/c1-1
```

采样顺序：baseline；c1/c2/c3/rth 分别按下、保持约 2 秒、松开，每键重复 3 段；c1_c2 组合重复 3 段；switch 三档往返重复 3 段；sticks_dials 作为误触发对照。每段由用户确认实际动作完成，再填写记录，不能仅凭标签推断已操作。

探针 PACKET 增加设备单调时间、报文序号和截断标记；STATUS 记录被限流的变化次数。限流时不更新“已记录状态”，使下一次日志机会仍可记录持续状态。被限流的样本不能用于证明没有短按或漏报；发现限流需缩小已观察到的候选命令后重新采样。

本轮扩展 APK 构建通过，采样脚本 CLI 检查通过。ADB 仍无设备，尚未采集真实样本。根据实施计划的证据门槛，尚未把参考位域接入正式 RM500 桥接，也未宣称完成 SDK 按钮补全。后续继续：实测确认命令与映射 → 同签名/登记校验的持久桥接与助手管理 → SDK 状态/生命周期集成 → 两次重启验收。

## 扩展抓包实测与上游依赖恢复

设备 transport 24 回连后已安装扩展探针；system=true，订阅 06/*、00/42、21/06 成功。用户确认操作目标按钮和三档后，packets=0、commands={}、suppressed=0，不能归因为日志限流。40009 有系统 UID 的本地已建立连接。

进一步发现 `com.dpad.service` 与 `com.dji` 均为当前用户 installed=false、stopped=true；没有私有按键监听日志。原厂 APK 分别仍在 `/system/priv-app/dpad_service/dpad_service.apk` 与 `/system/app/baseService/baseService.apk`。

该 Android 7 的 pm 不支持 install-existing。使用原厂文件 `pm install -r --user 0` 恢复两个包，均 Success，当前用户 installed=true；随后重启，待确认服务启动与数据恢复。此次恢复没有替换系统分区原厂 APK 或清除 Teleauto 数据，/data/app 中为原厂包更新副本。包缺失是已确认事实，但它与 06/AE 无数据的因果关系仍待复测。

## 控件名称纠正

用户确认：RM500 正面此前称为 C3 的实体键实际为暂停键 PAUSE。本任务目标改为 C1/C2、PAUSE、GOHOME 和三档；不能将该键映射为 C3。参考 06/AE 中 PAUSE=bit4 仍只是候选，尚无 RM500 实测报文证据。此前 C3 的操作记录应保留原标签并注明该纠正，不改写原始证据。

切换至序列号 455LJ1710100UJ 后，两个原厂服务原本正常运行；40009 捕获到 00/81 和 00/82 固定状态报文，40007 静置 15 秒无数据。不能将另一台 455LJ3T2010192 的系统探针安装结论套用到本机。

提权 HID 抓取被自动审批拒绝，未执行；改为普通 Activity dispatchKeyEvent 观察前台 Android 事件，无提权或输入权限修改。

用户确认完成普通 Android 按键测试后，日志没有 ANDROID_KEY。dumpsys activity 确认 inputprobe/MainActivity 在前台；dumpsys input 确认 DJI RC event1 已加载且使用 Generic.kl。因而“页面失焦”不是本次无事件的已知原因，仍不能认定底层 HID 没有上报。已停止普通探针重试并移除临时端口转发。下一步 HID 只读采集因自动审批拒绝而待用户对具体临时提权步骤明确授权。
