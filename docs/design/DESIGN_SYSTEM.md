# DJI RC visual system

## Figma authority

The console authority is `DJI-RC / Frame 1 / node 1:2` at 1920×1200. The checked-in visual reference is `design-assets/figma/dji-rc/dji_rc_frame_1_reference.png`; exported SVG and texture sources live beside it and must not be replaced by hand-drawn approximations. The baseline conversion is `1 Figma px = 0.4dp`, producing a 768×480dp design canvas.

| Figma node | UI component | Baseline geometry |
|---|---|---|
| `1:4`, `1:9` | `DJIStatusBarView` | 768×40dp |
| `1:41`, `1:10` | `DJIJoystickView` | 168×168dp |
| `1:78`, `1:72` | `DJICenteringDialView` | 168×9.6dp track |
| `1:75` | `DJIContinuousDialView` | 168×9.6dp track |
| `1:3` | central shell | 368×408dp |
| `1:5`, `1:6` | video presentation/HUD | 336×189.2dp |
| `1:81`, `1:86` | hardware keycap | 73.44×36dp, 8dp radius |
| `1:111` | `DJILogoView` | original SVG aspect ratio |

The console uses proportional scaling from the baseline. A 16:9 layout preserves the top bar, joystick squares, video aspect ratio and keycap aspect ratio, then compresses only vertical whitespace. Wider canvases add safe margins instead of stretching controls.

The Android controller console uses a restrained DJI-inspired dark technical language. This document is the source of truth for new UI work; runtime values live only in `djirc-ui` through `DJIUiTokens`, `DJIUiTypography` and `DJIMaterialAssets`.

## Color

- The controller UI is monochrome for outdoor sunlight readability; no interaction depends on hue.
- Primary: `#FFFFFF`; pressed: `#D8D8D8`; soft state: 18% white.
- Background: `#000000`; alternate: `#131313`; deep: `#000000`.
- Surface: `#232323`; raised center surface: `#3F3F3F`; joystick/key surface: `#242424`.
- Glass: `#1A1A1A` at 85%; strong glass: `#1C1C1C` at 98%.
- Default borders are white at 24% opacity; selected borders are white at 65%.
- Text: `#FFFFFF`; muted: `#D0D0D0`; faint: `#969696`.
- Success, warning and danger use labels, icons and border patterns rather than hue alone.

## Geometry

- Spacing follows `4 / 8 / 12 / 16 / 24dp`.
- Console page inset, section gap and card inset are all `16dp`; compact content gaps use `4dp`.
- Dashboard geometry must reference the `djirc_ui_*` dimensions exported by `ui/src/main/res/values/djirc_ui_dimens.xml`; do not introduce per-card padding values.
- Figma console radius follows `8dp` keycaps and `20dp` joystick/video/center shells; utility UI keeps `6dp` statuses and `18dp` dialogs.
- Outline icons use a 24×24 viewBox, 1.8px stroke, round caps and joins. SVG sources are under `ui/src/main/assets/icons`; `djirc_ui_*` VectorDrawable files are runtime derivatives.

## Typography

- All interface labels use the centralized industrial condensed face from `DJIUiTypography`.
- Live values use the centralized monospaced numeric face so updates never change their visual width.
- No component may create its own typeface; a licensed DJI font can later replace the centralized family without component changes.
- The physical 5D cluster uses a `28dp` semantic key size and `4dp` gaps; its cross silhouette must remain centered as one unit.
- In the right control card, the physical 5D cluster sits above the C1/C2 common-key row.
- The 5D cluster and common-key row use a dedicated `18dp` optical separation token.
- The center wordmark moves through allocated layout height, never negative translation, so its recessed edges cannot be clipped.

## Material and motion

- Cards use a graphite gradient, ambient/deep shadow and a restrained top reflection. Their border is subordinate to the shadow and never bright gray.
- The app base uses deterministic anodized-metal micro-weave; no raster tile or animated noise.
- High-frequency controller input never animates. Press feedback is 120ms in / 90ms out. Occasional overlays use 210ms ease-out in / 140ms out and remain interruptible.
- The central DJI wordmark is an enlarged, neutral translucent engraving. It does not use the interaction blue.
- The dashboard wordmark uses the semantic vertical offset token so its optical position stays independent from the bottom-aligned flight-mode control.

## Brand assets

- `installer/app-icon.png` is the desktop master raster; `npm --prefix installer run icon` regenerates Tauri derivatives.
- In-app brand assets are rendered in monochrome; the launcher source remains independent from runtime UI coloring.

## Component boundary

- `djirc-core` owns hardware, USB, MAVLink, decoding, recording and state lifecycles. It contains no Android `View`, layout or theme resources.
- `djirc-ui` owns all rendering, focus, adaptive measurement, visual motion and component assets. Components accept immutable UI state and emit semantic `DJIUiAction` callbacks.
- The Demo App owns page composition, state mapping, persistence and storage permission workflows. It must not create `Paint`, `Shader`, `GradientDrawable`, colors, typography or component backgrounds.
- New visual values require a semantic token reused by more than one component and documentation here.
- Original design exports stay under `design-assets/figma/dji-rc/slices`. Only optimized runtime derivatives are copied into `ui/src/main/res`; neither `sdk` nor `app` may own or duplicate console artwork.
## Console geometry

The controller console uses the Figma `1920 × 1200` coordinate system. At the
`768 × 480dp` baseline the side/center/side columns are `168 / 368 / 168dp`,
corresponding exactly to `420 / 920 / 420px`; the remaining width is reserved
for the two outer insets and two column gaps. Smaller displays scale below 1.0
instead of clamping and squeezing the weighted columns.

Joystick artwork and motion share a single `420 × 420` local coordinate space.
The crater body fills the control region while both trim tracks are inset `24px`
from its design edge, keeping them flush with the recessed surface edge. Their
track and grey knob assets are shared with the top centering dials.
Dial travel fill uses the shared deep-grey `SURFACE_RAISED` role and never
switches to white while the wheel is moving.
Hardware keys use stable profile-provided slots; an unsupported key leaves its
slot empty and never causes another key to move.

The target-device cover and live video are mutually exclusive contents of one
shared 16:9 presentation slot. Video directly replaces the cover without an
extra frame artwork or padding layer. Both states use the same `20dp` radius;
fullscreen removes the video outline.
Android 7 does not use `clipToOutline` for the video corner because it produces
visible diagonal facets. `DJIVideoView` caches one round-rect path on size change
and clips only the TextureView child while an anti-aliased black drawable owns
the visible edge.

## Controller device presentation (2026-09-09)

Xbox, Joystick and CyberBrick use `DJIDeviceTypeCoverView`: shared black video-slot
surface, gray outline, 48dp icon, token typography, connection status and quiet
connection/disconnection actions. `DJIMaterialAssets` is the native Android
shared surface/button equivalent. The original nested success card is not used.
CyberBrick selection is inside the same card; a scrollable receiver list replaces
the icon temporarily. Error, cancellation and empty search stay in this card.

The hardware panel uses `HARDWARE_PANEL_HEIGHT = 196dp` in both side components,
scaled down as a unit when height is constrained. L1–L3 and R1–R3 are continuous
inner columns; C1–C3 form the right outer column. RTH/PAUSE use the user-provided
vector icons. The continuous dial and AUTH share a utility row. The short line
on a key marks support for holding. Round keys stay round when active.

Touch contracts: sticks and spring-centered dials are display-only. The continuous
dial supports horizontal drag and retains the selected value. Hold-capable keys
emit down/up, including release on drag-out, cancellation and loss of focus.
AUTH is a single click. Hardware and touch holds are merged by source; releasing
one source does not release the other.

## Controller resolution adaptation (2026-09-10)

1920×1080 and 1920×1280 share control geometry at the same Android density.
`DJIResponsiveMetrics` fits against the 16:9 compact canvas and reserves the actual
square joystick height plus a complete 196dp hardware panel before choosing scale.
This prevents the lower panel from shrinking independently on the shorter display.
`DASHBOARD_MIN_ASPECT` (16:9), `DASHBOARD_TOP_INSET` (11dp),
`DASHBOARD_BOTTOM_INSET` (21dp), `DASHBOARD_DIAL_TO_STICK_GAP` (11dp), and
`CENTERING_DIAL_HEIGHT` (14dp) describe this shared geometry; spacing values mirror
the corresponding XML dimension tokens. A `SPACE_1` reserve absorbs pixel rounding.

Extra height is spacing, distributed 1:2:1: top inset, stick-to-controls gap on
both sides, bottom inset. At 1920×1280 this adds 50/100/50 pixels respectively.
Both sides use the same additions, captured from base margins after scaling;
repeated layouts do not accumulate spacing. Xbox, Joystick, CyberBrick and video
retain the common center presentation, original aspect ratio and icon styling.

## Hardware key feedback (2026-09-10)

The shared hardware-key label token is now 18dp (formerly 14.4dp), using the
bundled bold face. Labels fit within the available face width; the compact AUTH
label is width-limited to keep it inside its circular key. Both display sizes use
the same rule. Hold markers remain separate from the label.

`DJIHardwareKeySurface` presents the common raised key, with `HARDWARE_KEY_DEPTH`
(`SPACE_1`, 4dp) and `HARDWARE_KEY_EDGE` (1dp). Existing surface, border and white
light tokens supply all colors. The base stays fixed while the face depresses;
hit rectangles do not move. Round keys retain a circular face in both states.
Physical holds and touch holds light the rim immediately; release restores the
raised face immediately and fades the rim over `MOTION_STATE` (160ms), using
ease-out (0.23, 1, 0.32, 1). A new press cancels the fade. Native ValueAnimator
honors the system animator-duration setting, including disabled animations.
Trigger-only events show a brief lamp pulse. Detaching cancels pending feedback.

The base, face gradient and edge are now pre-rendered transparent PNG assets:
`djirc_ui_hardware_key_{raised,pressed}` (294×144) and
`djirc_ui_hardware_round_{raised,pressed}` (160×160), stored in `drawable-nodpi`.
These are 4 pixels per design dp, sized above the displayed controls on both
1920-wide target screens. Matching state bounds prevent size jumps. Text, icons
and the fading light remain independent native overlays. The small fixed-aspect
keys use regular PNGs; they do not stretch across a variable-width container.
Recreate assets with `design-assets/scripts/render_hardware_keys.swift` from the
repository root. The script mirrors the documented geometry and color tokens.

## Revised control placement (2026-09-10)

This supersedes the earlier RTH/PAUSE and AUTH utility-row placement. The left
outer column is AUTH, RTH, C1 from top to bottom; L1–L3 remain the inner column.
The right inner column is R1–R3 and its outer column is PAUSE, C2, C3. The continuous
dial now spans the right footer below the buttons. Round key faces occupy the
same row pitch as rectangular keys, with unchanged touch/hold contracts. Only
display placement changes; physical control identities and channel mapping stay fixed.

## Shared console and settings library (2026-09-10)

`ui/README.md` documents the public layout and component entry points. The entire
console XML is now `ui/src/main/res/layout/djirc_control_console.xml`. App owns
state binding only. Materials, hardware button images, source control SVGs,
device icon identities, navigation hierarchy and fallback button arrangement
remain in `:ui`.

Crater shoulders and recessed tracks share `DJICraterSurface`. Navigation and
cards use the `DJIMaterialAssets` factory and a 12dp card radius. The former top
lock/trapezoid and center-shell bitmap are not part of the new composition.
The lower-left slide lock and lower-right continuous dial share a fixed bottom
inset; the continuous dial shows no number. Locking affects touch dispatch only.

Use `DJIResponsiveMetrics.scale(context)` for fullscreen layouts: resource
configuration height can include transient system-bar deductions. Both requested
resolutions have been checked on the controller using temporary display-size
overrides, then the original 1920×1200 display was restored.

### RM500 / RM700 紧凑控制台（2026-09-10）

控制台按键统一采用固定逻辑模板，机型物理 placement 不覆盖屏幕位置。两列保持现有按键尺寸，空隙乘 `COMPACT_KEY_COLUMN_GAP_RATIO = 0.60` 后整体居中；行距使用 `COMPACT_KEY_ROW_GAP = 12`（原16的75%）。1080以上新增高度四等分到顶部、拨轮至摇杆、摇杆至按钮、按钮至底部组件；除不满4像素的余数外均分，余数留在最后一段。底部边距和摇杆等比例保持不变。

锁定用 `SCREEN_LOCK_TINT` / `SCREEN_LOCK_SURFACE`，复用现有琥珀色语义色；滑块使用 `RADIUS_SM`。物理 C1+C2 持续 `SCREEN_UNLOCK_HOLD_MS = 2000` 时原组件显示进度并解锁，任一松键、输入断开、失焦或退出前台取消进度。锁定时释放所有触屏按键及待发脉冲，再阻止整个根视图的触屏派发，输入通道继续传输。

### 拨轮刻度与顶栏标题

顶栏页面标题和遥控器设备名统一使用 TYPE_APP_HEADER（12sp 基准，随共享响应式比例缩放）及 bold 字体。拨轮共用 10 个等距区间，刻度使用 SURFACE 深灰色，中点长度为槽高的 60%，普通刻度为 35%；刻度绘制于进度之上、滑块之下，不显示数值。

### 中央遥测状态平台

图传/虚拟设备画面下方的中间区域使用 DJIStatusPlatformView，复用 DJICraterSurface 火山口材质，外沿隆起并自然过渡到内侧状态面；肩部尺寸固定随响应式缩放，不随平台宽度变粗。平台宽度与画面一致，高度占用中列剩余空间；现有设备状态置于平台内，无协议数据时在平台内水平居中、靠底部显示 Alius Logo，底部留出 dashboard_logo_bottom_offset。平台本身无点击行为，不产生控制命令；详细 MAVLink 仪表留待后续真实数据接入。

锁定状态配色更新：SCREEN_LOCK_SURFACE 使用不透明 SURFACE 深灰，SCREEN_LOCK_TINT 使用不透明 SURFACE_RAISED 亮灰，取消琥珀色及额外透明度；继续使用槽内裁切。

### 控制页中央材质统一

中央视频框及 Xbox／Joystick／CyberBrick 大卡片通过 centralFrame 共用接近平面的细边过渡，中央状态台通过 centralPlatform 使用上沿受光、下沿阴影的内侧凸起材质；两者均使用 FIGMA_VIDEO_RADIUS 并按响应式比例缩放固定边缘宽度。覆盖上文状态平台复用内凹 DJICraterSurface 的方案。全屏视频不绘制装饰边框。设置页设备卡片及摇杆材质不变；未新增 MAVLink 协议接入。

状态台装饰线沿台面内缩 SPACE_1 绘制，线宽 LIST_DIVIDER_HEIGHT，圆角同步内缩；颜色由上沿 GLASS_BORDER 过渡到底部 SURFACE_RAISED。只用于凸起状态台，不改动视频框或设备卡片。

状态台配色进一步收敛为 CRATER_FLOOR 深炭灰，台面中部保持均匀，SURFACE 至 BACKGROUND_ALT 的明暗变化集中在上下沿。内圈装饰线降为 CRATER_SHOULDER 至 SURFACE，外沿高光降为 SURFACE_RAISED；仅作用于中央凸起状态台，位置、Logo、视频和设备卡片不变。

### 三档模式状态色（2026-09-10）

状态栏模式文字复用共享 Token：S / 运动模式使用 `MODE_SPORT`（蓝色），C / 慢速模式使用 `MODE_CINE`（金色），N / 稳定模式使用 `MODE_NORMAL`（绿色）。品牌 Logo 与运行状态文字保持原色，颜色变化仅用于区分模式。

### Logo 矢量与模式色（2026-09-11）

Logo 源文件为 `ui/src/main/assets/icons/alius-logo.svg`，Android 使用同路径生成的 `ic_alius_logo.xml` VectorDrawable。顶部 Logo 和模式文字共用 `modeColor`：S 蓝、C 金、N 绿；覆盖此前 Logo 保持原色的约定。中央品牌 Logo 同样使用矢量资源，保持中性灰。

### 图传顶部操作区（2026-09-11）

`VIDEO_HUD_INSET` 复用 16dp 间距，标题与放大按钮垂直居中，按钮显示尺寸 25dp、点击尺寸 52dp。点击区域跟随显示位置；长 LIVE 信息省略显示，避免覆盖按钮。紧凑与全屏模式共用。

### 中央 Logo 面板（2026-09-11）

面板使用均匀 `CRATER_FLOOR` 纯色背景和单层 `VIDEO_FRAME_EDGE` 细边框，移除上下渐变、内层装饰线和边缘明暗过渡。

### 滑动锁轨道外框（2026-09-11）

轨道使用矢量绘制的单层细边框，阴影限制在边框内并平滑过渡到不透明底色；移除原 PNG 九宫格中的外侧灰色凸起轮廓。锁定填色和解锁进度裁切在内部，保留小圆角及左侧锁定图标位置。

### SDK 控制输出锁定规则（2026-09-11，最新约定）

覆盖此前“只禁触屏、物理输入继续转发”的规则。锁定后 SDK 保持物理采集，但停止普通按键、摇杆、拨轮和三档状态回调；HID 通知循环及 CDC 高频头写入口共用输出门禁，不重复发送旧控制值。队列按锁定代次失效，解锁不补发旧事件。SDK 独立识别物理 C1+C2 两秒解锁，松开任一键或输入断连即重置，组合键释放前不转为普通控制事件。App 只接收 `onScreenLockStateChanged` 状态及进度，连接/设备状态仍可通知。停止发送不等同于发送接收端中立或急停命令。

原生输入统一入口：`DJIJoystickActivity` 接收 Android 按键与摇杆事件，GPIO L1～L3/R1～R3、五向键及拍照/对焦回到 SDK；内置 profile 和外接手柄均经相同输出门禁。App 删除直接处理 GPIO 按下/释放和绕过 SDK 的旧虚拟按键入口。SDK 内去重基于物理源持有状态，避免触屏与物理源互相释放。

### 自定义键与状态栏防误触（2026-09-11）

左右外列从上到下分别为 C1 / AUTH / 返航、C2 / C3 / 暂停；内侧 L1～L3、R1～R3 不变。圆形按键依据统一槽位定位，不再固定在第一行。电量与设置间隔复用 16dp，点击区域保持独立，中间留出间隙。

### 专用按钮颜色（2026-09-11）

AUTH 使用 `AUTH_KEY_COLOR` 红色（复用 DANGER），暂停使用 `PAUSE_KEY_COLOR` 橘黄色。共享按键材质按原亮度着色，保留黑色阴影和按下/弹起亮灯效果；其余按钮保持中性灰。

圆形按键统一直径 46dp（原 40dp，增加 15%），按行中心对齐，绘制与点击区域共用相同边界。

### 彩色按钮哑光质感（2026-09-11）

AUTH 和暂停使用无纹理、无渐变的哑光色面，去掉高亮金属环；保留轻微均匀边缘与底部阴影表达凸起。按下时色面下沉并变暗，亮灯只小幅提高亮度。通过共享 MATTE_KEY 系列参数控制。

左侧外列顺序更新为 C1 / 返航 / AUTH；右侧保持 C2 / C3 / 暂停。

暂停按钮配色更新为橘红色 `PAUSE_KEY_COLOR`（#F27642），保留哑光明暗参数；AUTH 保持红色。

AUTH 按钮使用用户提供的“无人机.svg”（归档为 `ui/src/main/assets/icons/auth.svg`），Android 对应 `ic_rc_auth.xml` 矢量资源。替换 AUTH 文字，保留哑光红色背景、原位置和按键功能。

中央状态面板的 Alius Logo 水平、垂直居中，不使用底部偏移。SVG 与原生 VectorDrawable 保持相同视口：72×108，原始路径包围盒 X=64～121.5，视口起点 X=56.75，使左右留白一致。

### Flutter 控制中心统一布局（2026-09-11）

控制中心共用 TeleautoPanelSurface 和 TeleautoNavigationItem：非选中导航只显示图标和文字，选中项使用细描边、小圆角；操作按钮保持哑光凸起，设备卡片固定四列。CyberBrick 导航使用品牌图标和短名称，详情页保留完整名称。

控制台视频顶部使用 consoleVideoTopGap；状态框随可用高度增长，底边与左侧滑动锁、右侧连续滚轮一致。连续滚轮初始值为中位，填充由中心向左右延伸，释放保留当前位置。电量仍可点击切换已连接来源，去掉下拉箭头，设备与电量间距使用 deviceBatteryGap，设置按钮保留独立间距。

Flutter 中文字体使用 Adobe Source Han Sans CN 完整字库，避免设备分类和电池名称缺字；字体授权随资源保存在 SourceHanSans-LICENSE.txt。共享组件中的 Material 图标由包声明统一加载。
