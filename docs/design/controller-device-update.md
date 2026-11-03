# Controller device update

Implemented against the user-approved mockups and subsequent interaction rules.
The logo work and pre-existing joystick/profile fixes remain in place.

## Input contract

19 product inputs = 4 stick axes + 2 centering dials + 1 continuous dial +
L1–L3/R1–R3/C1–C3/RTH/PAUSE/AUTH (12 buttons).
Media, five-way and three-position switch inputs remain local; none is a
transmitted channel. Analog inputs cannot be operated on screen except the
continuous dial. Touch and hardware share channel identities.

Xbox-layout HID mapping: R3=A, R2=B, L2=X, R1=Y, C1=LB, C2=RB, RTH=View,
L3=Menu, C3=LS, AUTH=RS, L1=Home, PAUSE=auxiliary button 18. The continuous
dial is an extra Slider axis. Joystick uses positional Z/Rz dials while Xbox
uses LT/RT magnitude. Both keep a 10-byte report. This is standard BLE HID
with Xbox-style mapping, not a native XInput implementation. Host applications
may ignore nonstandard auxiliary controls. Existing host pairing caches may
need to be cleared after a descriptor change.

## CyberBrick boundary

The Android controller is the BLE central. The implementation provides timed
scanning, explicit device selection, GATT connection/service discovery,
disconnect, retry, cancellation, permission and adapter checks. Unknown BLE
peripherals are listed using their advertised names so renamed receivers are
not silently hidden. Device selection is explicit; no automatic connection.

Stock CyberBrick receiver identification, control UUIDs, authentication and
control packet encoding have not been verified. No arbitrary writes are sent.
A GATT connection is therefore labeled `蓝牙已连接 · 暂不可控制`, and
`controlReady` stays false. This is a working connection transport and UI, not
completed stock CyberBrick control-protocol support. Receiver hardware is needed
for the remaining interoperability work.

## Global Style Audit

- Card: replaced the separate bright placeholder with the shared video-slot surface.
- Typography: Xbox/Joystick/CyberBrick use the same token sizes.
- Button: shared keycap and quiet connection actions; round pressed keys preserved.
- Badge: connection status is text inside the common presentation.
- Tabs: no new tabs introduced.
- Icons: supplied CyberBrick/RTH/pause assets; consistent tint and bounds.
- Root cause: separate placeholder styling and a full-screen HID monitoring overlay.
- Fix: shared device presentation, persistent console, aligned physical key groups.

## UI Consistency Review

- App Shell: pass — retains the controller's three-column console.
- Layout: code review pass; physical screenshot verification pending device connection.
- Typography: pass — shared presentation tokens.
- Color: pass — monochrome palette and shared surfaces.
- Border: pass — same video-slot outline.
- Radius: pass — shared video radius; round keys retain their shape when held.
- Components: pass — shared cover, hardware keys and continuous dial.
- Remaining issues: no connected ADB device at validation time; CyberBrick stock
  control protocol and live BLE interoperability require receiver hardware.

Validation completed: `:app:assembleDebug`, `:app:verifyUiBoundary` and 12 targeted
SDK unit tests (`ControlInputStateTest`, `HidJoystickFlightModeTest`,
`HardwareControlProfileTest`) passed. `git diff --check` passed. APK:
`app/build/outputs/apk/debug/app-debug.apk`. No device installation was performed
because `adb devices -l` returned no connected devices.

## Dual-resolution follow-up (2026-09-10)

The shared scale now reserves the entire side control stack on 1920×1080. The
1920×1280 layout uses the same control sizes, distributing its extra 200 pixels
as 50px top padding, 100px extra stick-to-keys spacing and 50px bottom padding.
The same spacing is applied to both side columns regardless of selected device.
Layout callbacks use captured base margins, so resizing does not compound gaps.

UI Consistency Review (source/geometry review; device screenshots pending):

- App Shell: pass — existing three-column controller composition.
- Layout: pass — symmetric spacing, complete hardware panel reserved.
- Typography: pass — shared scaling, same size across the two target heights.
- Color: pass — existing palette.
- Border: pass — existing shared surface.
- Radius: pass — existing tokens and round controls.
- Components: pass — common responsive helper; no mode-specific spacing.
- Remaining issues: live rendering and touch verification need a connected controller.

Validation: `:app:assembleDebug`, `:ui:testDebugUnitTest` (6 tests, including
4 responsive-layout tests) and `:app:verifyUiBoundary` passed. Responsive cases
cover 1920×1080, 1920×1280 and 1280×720 at densities 1–3. `adb devices -l` still
returned no connected devices; this follow-up has not been installed or screenshot-tested.

## Button legibility and feedback follow-up

Shared control labels use 18dp bold text with width fitting for compact AUTH.
A common raised surface uses dedicated raised/pressed PNG states; press depresses
the face and lights its white rim, release restores elevation and fades the lamp
over 160ms. Input dispatch and dual-resolution layout geometry are unchanged.

UI Consistency Review:

- App Shell: pass — existing console.
- Layout: pass — stable hit bounds and shared resolution metrics.
- Typography: pass — shared bold key token, compact-label width fitting.
- Color: pass — existing monochrome surface/light tokens.
- Border: pass — shared key edge.
- Radius: pass — rectangular keys use the existing radius; round faces stay circular.
- Components: pass — one surface and feedback implementation for both side panels.
- Remaining issues: visual appearance and press/release feel need live device review.

PNG material follow-up: four transparent assets now supply rectangular/circular
raised and pressed faces. Dimensions are 294×144 / 160×160, with identical bounds
between each state pair. The shared renderer retains text, vector icons and the
160ms light overlay separately. Source assets were visually inspected; no live
controller screenshot was available. Typography, interaction and layout remain
as reviewed above.

Placement correction: left outer rows now contain AUTH / RTH / C1; right outer
rows contain PAUSE / C2 / C3. L1–L3 and R1–R3 remain inner columns. The right
footer is a full-width continuous dial. Catalog presentation slots, app fallback
slots and round-key geometry were updated together. Shared materials, typography,
color, radius and responsive spacing are retained. Device rendering remains pending.

## Shared component library implementation (2026-09-10, replaces prior layout notes)

The approved console and control-center composition now lives in `:ui`, including
`djirc_control_console.xml`, `DJIControlConsoleLayout`, `DJIConsoleControlLayout`,
`DJISlideLockView`, all device icon mappings, vector sources, button PNG states,
crater surfaces, navigation materials and layout dimensions. See `ui/README.md`.
App binds device state and callbacks and no longer owns the console XML or fallback
key arrangement.

The top-bar lock and trapezoid and thick center shell are removed. Slide lock is
in the lower left; the continuous dial has no number and shares its bottom edge.
Both dial types and joystick trims use the same recessed material. Device cards
and menus share rounded raised surfaces. Device icons use an exhaustive type
mapping; Xbox has offset sticks and Joystick has a flight-stick grip/base.

Locking intercepts App touch only, allowing the dedicated reverse slide gesture.
The root component releases virtual key touches. Physical stick, held-key and
mode updates continue; physical keys retain the existing input ownership rules.
Short or interrupted slides do not toggle the lock.

Responsive sizing now uses actual display metrics instead of configuration height
(which included transient system-bar insets on the controller). The extra 200 px
at 1920×1280 become 50 px above the console, 100 px between sticks and keys, and
50 px flexible space before the footer. Bottom inset stays fixed. This supersedes
the earlier 50 px extra bottom padding.

Validation: 6 UI tests and 66 SDK tests passed, along with `:app:assembleDebug`,
`:app:verifyUiBoundary` and `git diff --check`. Installed on DJI RC PLUS 2.
Actual 1920×1080 and 1920×1280 screenshots confirm equal control sizes, unclipped
keys and matching footer baselines. Full slide/blocked settings/reverse unlock
were exercised on device; live physical flight commands were not injected.

Final device regression also passed short-slide rejection, complete-slide locking,
blocked settings access while locked, and reverse-slide unlock with restored
settings access. Reference screenshots are stored under
`design-assets/validation/ui-library/` (1080/1280 console and native 1200 console/
expanded control-center menu). The controller was left unlocked on its original
resolution and target device; no additional device was created for the screenshot.

### Android Crater V2 与锁定返回（2026-09-10）

- Crater V2 原始素材保存在 ui 的 assets/materials/crater-v2；运行时资源以 djirc_v2_ 前缀集中在 drawable-nodpi。按钮使用常态/按下态，回弹亮灯以按下态透明度衰减；摇杆固定槽包含在底图中，网格和指针独立绘制。
- SDK `DJIJoystickManager.checkScreenInput()`、`injectKeyEvent()`、`submitFreeDialValue()` 统一返回 `ScreenInputResult`：ACCEPTED、UNLOCK_REQUIRED、INVALID_INPUT。兼容入口 `setFreeDialValue()` 仍返回 Boolean。
- `setScreenLocked(true)` 清除虚拟按键和待释放脉冲，保留物理按键。锁定后所有虚拟输入被拒绝；UI 根节点每次 ACTION_DOWN 通知宿主，宿主读取 SDK 结果统一显示解锁提示，2 秒内去重。
- 解锁仍需同时长按物理 C1+C2 两秒。SDK 返回机器可读结果，App 持有提示文案，不在 SDK 弹 Toast。
