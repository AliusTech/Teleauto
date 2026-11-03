# Alius Android UI 组件库

控制台和控制中心的视觉实现统一由 `:ui` 提供。App 不维护页面 XML、设备图标分派或按键位置副本。

## 使用入口

- `R.layout.djirc_control_console`：完整控制台、图传层、设备管理页和底部控制组件。
- `DJIControlConsoleLayout`：根布局与触摸锁定分发；`onLockChanged` 提供业务通知。
- `DJIConsoleControlLayout.configure`：统一左右按键分组，接收设备支持的按键槽。
- `DeviceManagementView`：设备管理、创建设备、模块管理、设置及二级菜单；`setControllerName` 设置型号。
- `DJIResponsiveMetrics.scale(context)`：使用显示屏实际尺寸计算比例，避免启动时系统栏改变控件大小。
- `DJIResponsiveLayout.apply` 与 `adaptDashboardSpacing`：初始化 XML 尺寸与纵向留白。每次创建布局仅调用一次 `apply`。

宿主使用 `import tech.alius.rc.ui.R as UiR` 访问组件布局和视图 ID，只绑定业务状态及回调。

## 素材与样式

- `src/main/res/drawable-nodpi/djirc_ui_hardware_*`：矩形/圆形按钮凸起与按下 PNG，支持按压位移和释放亮灯。
- `src/main/res/drawable/ic_device_*`：按设备类型区分的图标，包括 Xbox、Joystick、CyberBrick、多旋翼、固定翼和机器人。
- `DJIDeviceIcons.kt`：设备类型穷举映射，新增类型须明确指定图标，禁止以类别静默回退。
- `DJICraterSurface`：摇杆和两类拨轮共用的可缩放火山口材质。
- `DJIMaterialAssets`：卡片、导航、按钮和顶栏的共享表面。
- `DJIUiTokens` / `djirc_ui_dimens.xml`：样式及布局参数。
- `src/main/assets/icons`：品牌与控制图标源素材；CyberBrick 保留用户提供的图形。

## 交互约定

摇杆及回中拨轮只显示物理输入；连续拨轮允许左右拖动且不显示数值。AUTH 单击，其余按键按设备描述符保留点击/持续按住语义。

滑动锁定只能从滑块开始、到达另一端后松手触发；不足行程、取消和多指中断不切换状态。锁定后仅解锁滑块接受 App 触摸，物理输入不停止、不冻结。根组件自动释放虚拟触摸按键，保留物理按键状态。锁定状态随视图实例状态保存。

支持 1920×1080 与 1920×1280；在相同密度下两者控件尺寸相同，多出的高度分配到留白，两侧底部组件共用底部基线。

导航状态约定：只有当前页面对应的可见菜单项显示选中底色；分组展开时父项不与子项同时选中，收起时由父项表示当前页面。五向键焦点使用透明细边框，触摸操作清除旧焦点。二级项使用 SPACE_1 间距。

CyberBrick 与 Xbox、Joystick 同属虚拟设备分类，但保留专用 BLE 接收端连接流程，不作为虚拟 HID 手柄连接。

### 紧凑布局与物理解锁

`DJIConsoleControlLayout` 决定所有机型的屏幕位置，缺失物理按钮仍保持触屏单击/持续按压语义。`DJIResponsiveLayout` 将额外高度四等分，`DJIHardwareControlsView` 仅收紧行列空隙。宿主锁定时必须释放虚拟输入（含脉冲），之后将输入事件和 held 快照交给根布局的 `onLockedKeyEvent` / `onLockedHeldKeys`。输入断开、输入源切换、后台或失焦应调用 `cancelUnlock`。根布局只接收锁定期间、前台连接有效的输入用于 C1+C2 两秒解锁；物理通道不被吞掉。

### 存储位置菜单

存储位置使用固定按钮和下方锚定菜单，菜单仅列另一存储类型。`submitMediaStorageOptions` 新增 `externalPresent`（默认 `externalEnabled` 兼容旧调用），存在但不可写的卡显示禁用项，无卡时无箭头且不展开。`onMediaStorageRefreshRequested` 在进入存储页时请求宿主刷新；重新提交状态会关闭旧弹层。

### SDK 输出锁定

宿主调用 `JoystickManager.setScreenLocked`；普通输入在锁定时不回调且不发送。通过 Observer 的 `onScreenLockStateChanged(locked, unlockProgress)` 更新 `DJIControlConsoleLayout.submitSdkLockState`。物理 C1+C2 两秒解锁由 SDK 识别，页面不得通过普通按键回调重复执行解锁逻辑。

宿主继承 `DJIJoystickActivity`，原生 KeyEvent / Joystick MotionEvent 由 SDK 接管。不得覆写 onKeyDown/onKeyUp 直接点亮按钮；界面只在 SDK Observer 中渲染。SDK 导航回调使用 `dispatchSdkNavigationEvent`，避免将已分发事件重复注入。
