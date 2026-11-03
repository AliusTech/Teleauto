# 蓝牙手柄与 PC 连接面板

Xbox 手柄与 Joystick 共享 SDK UI 连接面板。选择模式后显示实际广播名称，并提示在 PC 蓝牙设置添加该设备；已配对 PC 可以重新连接。控制面板不伪造“主动连接PC成功”，配对确认由两端系统处理。

- 未开启/失败：配对 / 连接 PC，重新检查蓝牙权限并启动 HID。
- 广播中：显示等待 PC 配对/连接，可重新等待或取消。
- 配对中：显示建立连接，可取消；不显示连接成功。
- 已连接：手柄、绿色连接标志、PC 图标及主机名称（无法取得时为 PC），提供断开 PC 连接。
- 主动断开：关闭该 HID 会话及广播，保留配对记录与已选择的虚拟设备，可以从同一面板恢复。

成功状态要求当前会话主机已绑定，且同一主机订阅输入报告。电量读取不作为连接门槛，避免主机缓存或跳过电量读取时界面一直停在建立连接。仅普通目标设备CONNECTED或GATT链路接通不足以显示成功。遥控器继续通过原有锁定和SDK授权控制输入输出。

Xbox 扳机使用自回中滚轮的单侧线性位移：LT = clamp(-leftDial, 0, 1)，RT = clamp(rightDial, 0, 1)，以 0–255 量化发送。回中和反方向为 0，有效方向到顶为 1；不再取绝对值或叠加 HID 层死区。Joystick 模式仍保留双向轴。物理方向与主机输入端到端验收待完成。

RC701 实测反馈左摇杆上下反向后，BLE HID 左 Y 轴改为直接量化 leftY，移除输出层取负；Xbox 和 Joystick 共用此修正。端点与回中单元测试覆盖两种模式，实机方向待更新后复验。

新增 Flutter 操作 connectHidDevice；仅对当前虚拟HID目标有效，停止旧会话后重新启动。状态事件新增hidHostName、hidDeviceName。共享UI继续独立于core，Teleauto仅转发业务动作和状态。

验证：核心/UI Gradle检查、Maven Local发布、两个Flutter包及Teleauto analyze/test、Teleauto release APK构建通过。控件测试覆盖OFF/ADVERTISING/PAIRING/CONNECTED/ERROR、状态恢复、旧目标连接标志不提前报成功、禁用操作。已检查已连接状态渲染。

限制：尚未在真实PC上验收本次新面板的配对→输入→断开→重连闭环；未自动操作PC蓝牙，也未改变USB HID配置。
