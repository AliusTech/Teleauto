# Teleauto SDK

For the interactive Rust issuer, SDK key-version policy and FastAPI integration contract, see [License Generator](license-generator/README.md).

> Teleauto SDK owns core capabilities, shared UI (`sdk/ui/`) and low-level modules. The independent Teleauto app project owns business orchestration: Android at its root, desktop in `assistant/`.

[中文](README.md) | **English** | [日本語](README.ja.md)

**TeleautoSdk** is an Android input-reading library (with an input-tester demo) that runs **on the DJI screen controller itself**. It translates every physical input on the controller — both control sticks, both dials, the shutter/record buttons, the 5D button, the C1–C3 custom buttons, RTH, pause, GPIO shoulder keys and more — into a unified input-event model that is consistent across models, so you can build your own software on the controller: accessory integration, enterprise applications, assisted operation, automated testing and so on.

DJI's official Mobile SDK targets a phone connected to the aircraft and provides no input-reading API that runs on the controller itself. If you want to develop your own app for a screen controller, stick and button data are simply out of reach — this project exists to fill that gap.

## Table of Contents

- [Features](#features)
- [How It Works](#how-it-works)
- [Supported Hardware](#supported-hardware)
- [Quick Start](#quick-start)
- [Integrating Into Your Project](#integrating-into-your-project)
- [Screen Keep-On & Always-On Running](#screen-keep-on--always-on-running)
- [Releasing (Maintainers)](#releasing-maintainers)
- [API Example](#api-example)
- [Input Model](#input-model)
- [GPIO Shoulder Keys (RM700 Series Only)](#gpio-shoulder-keys-rm700-series-only)
- [Bluetooth Gamepad Mode](#bluetooth-gamepad-mode)
- [Adding Support for New Models](#adding-support-for-new-models)
- [Project Structure](#project-structure)
- [Known Limitations](#known-limitations)
- [Third-Party Libraries & License](#third-party-libraries--license)

## Features

- **Unified devices**: remote controllers, fixed-wing aircraft and ground rovers share the `TeleautoDevice` lifecycle and typed component model.
- **Automatic model detection**: system properties are read and matched against device profiles (`DeviceProfile`); unrecognized models never crash and never affect the host app's own key channels.
- **Unified input model**: `TeleautoPhysicalKey` / `TeleautoKeyAction` / `TeleautoStickSnapshot` are consistent across models, keeping subscriber code decoupled from the specific controller hardware.
- **Semantic processing**: toggle semantics for the record button plus a 1.5 s guard against long-press mis-toggles; a dual-channel half-press focus trigger that bypasses the system's ~200 ms decision delay; stick normalization and dead-zone handling.
- **Automatic reconnection**: retries 1.5 s after the adb transport drops; connection state reported in real time.
- **Bluetooth gamepad mode**: multi-mode switching built into the SDK — one line turns the controller into a system gamepad on PC/Mac (BLE HID over GATT, zero root; see below).
- **Single permission**: only `INTERNET` (adb loopback connection to `localhost:5555`; gamepad mode additionally needs BLUETOOTH permissions).
- **Keep screen on & stay running**: the demo ships a foreground keep-alive service (`START_STICKY` + partial wake lock) so input reading and the Bluetooth gamepad mode survive Activity closure or screen-off; the screen stays on while the app is foregrounded (see below).

## How It Works

The app is installed on the controller and uses the bundled ADB protocol implementation (adblib) to **connect back to itself at `localhost:5555`**, opening two data channels:

```text
┌──────────────── DJI Screen Controller (Android device) ────────────────┐
│                                                                        │
│   Host app (your own application)                                      │
│  ┌───────────────── TeleautoSdk SDK (tech.alius.rc) ─────────────────┐    │
│  │   Unified input model: TeleautoPhysicalKey / TeleautoKeyAction / TeleautoStickSnapshot │    │
│  │        ▲                    ▲                                  │    │
│  │   Device profiles (rm500 / rm510 / rm700 …)                    │    │
│  │   Translation layer: raw events → unified semantics            │    │
│  │        ▲                    ▲                                  │    │
│  │ ┌──────┴─────────┐   ┌──────┴───────────┐                      │    │
│  │ │ evdev channel  │   │ logcat channel   │                      │    │
│  │ │ getevent -l    │   │ logcat -s        │                      │    │
│  │ └──────┬─────────┘   │ KeyCodeListener  │                      │    │
│  │        │             └──────┬───────────┘                      │    │
│  └────────┼────────────────────┼──────────────────────────────────┘    │
│           └── adb loopback to localhost:5555 (built-in adblib) ──┘     │
│                                                                        │
│   GPIO shoulder keys L1-L3/R1-R3 (RM700 only): unified evdev events   │
│   (KEYCODE_F1-F6) go straight to the host Activity, bypassing the SDK  │
└────────────────────────────────────────────────────────────────────────┘
```

| Channel | Command | Inputs provided |
|---|---|---|
| evdev | `getevent -l` | `EV_ABS`: the four stick axes (`ABS_X/Y`, `ABS_RX/RY`) and both dials (`ABS_Z/RZ`); `EV_KEY`: shutter (`BTN_TR`) and power (`KEY_POWER`). Touch devices (`fts_ts`) are skipped automatically |
| logcat | `logcat -T 1 -s KeyCodeListener:D *:S` | DJI's private key broadcasts: `ACTION_KEY_*` pulses (record/shutter/focus/C1–C3/RTH/pause/LED auth/5D button) and the currently held set from `mKeyCodeList` (used for release detection) |

Key semantics implemented by the translation layer:

- **Normalization**: stick and dial axis values are mapped uniformly to `[-1, 1]` with built-in dead zones (stick flat value 128, minimum dead zone 4%), so readers never have to deal with raw ranges.
- **Record button**: `ACTION_KEY_RECORD` is a pulse stream with no release events. The SDK translates it into `TOGGLED_ON` / `TOGGLED_OFF` toggle semantics and filters out false toggles caused by long-press repeats using a 1.5 s window.
- **Half-press focus**: the dpad channel has a ~200 ms decision delay for `FOCUS`. The SDK uses a dual-channel trigger — immediate press detection from `mKeyCodeList` plus a pulse fallback (250 ms deduplication) — making the half-press response near-instant.
- **GPIO shoulder keys** (RM700 series only): the SDK translates them from the evdev channel into unified `L1–L3 / R1–R3` key events (`DOWN` / `UP`) — just subscribe to `InputListener`. The system's standard `KeyEvent` dispatch (`KEYCODE_F1`–`F6`) keeps working in parallel, so hosts may still use `onKeyDown` / `onKeyUp`.

## Supported Hardware

As of 2026-09-14, the SDK registers the following models. **An implemented profile does not mean every control has passed hardware testing.**

| Model identifier | Display name | Validation status |
|---|---|---|
| `rm500` / `ag501` | DJI SMART CONTROLLER | Initialization and input-node connection verified; C1/C2, front pause, return-to-home and the three-position switch remain unresolved |
| `rm700` | DJI RC Plus | Implemented; awaiting hardware validation |
| `rc701` | DJI RC Plus 2 | Hardware tested; confirmed working by the user |
| `rm510` | DJI RC Pro | Protocol adapter added; awaiting hardware validation |
| `rc520` | DJI RC Pro 2 | Protocol adapter added; awaiting hardware validation |

**RC701 (DJI RC Plus 2)** is currently the model that has completed hardware acceptance testing. Firmware permissions still block the RM500 button protocol. Protocol access, axis directions and physical controls on RM510/RC520 remain unverified; RC520 compatibility with the shared packet format also requires hardware confirmation. See [RC Pro support notes](docs/rc-pro-support.md). Unlisted models are not registered.

### How Model Detection Works

- Match `ro.product.name` first, then `ro.product.model`. Matching ignores case and surrounding whitespace but requires the complete identifier; no wildcards are used.
- Four profiles are built in: **rm500** (`rm500` / `ag501`), **rm700** (`rm700` / `rc701`), **rm510**, and **rc520**. RM510 and RC520 are identified separately and do not use RM500 system input-permission provisioning.
- Device information is exposed through `TeleautoSdk.getDeviceInfo()`. Model detection, SDK authorization, input connection and physical-control validation are separate states.

## Quick Start

### Requirements

- JDK 17
- Android SDK: compileSdk 37, Build Tools 36.0.0
- Build toolchain: Android Gradle Plugin 9.3.1, Gradle 9.5.0
- ADB must be enabled on the controller (developer options) so that the local `localhost:5555` is reachable

### Build

> `local.properties` is not committed; after cloning, create it yourself and point `sdk.dir` to your local Android SDK path.

```bash
./gradlew :sdk:assembleRelease :sdk:testDebugUnitTest
```

### Teleauto

Android App, shared UI, desktop assistant and design assets now live in the independent **Teleauto** project. Its root is the Android build; the desktop project is in `Teleauto/assistant/`. This repository maintains only the SDK and lower-level modules.

## Integrating Into Your Project

### Option 1: Maven dependency (GitHub Packages, recommended)

The SDK is published to this repository's GitHub Packages; third-party apps integrate with a one-line dependency:

1. Create a GitHub PAT (classic) with only the `read:packages` scope; if the organization enforces SSO, authorize the token for it once;
2. Put the credentials into your global Gradle config (keep them out of your project repo):

```properties
# ~/.gradle/gradle.properties
gpr.user=your GitHub username
gpr.key=ghp_xxxxxxxxxxxx
```

3. Add the repository in your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/AliusTech/dji_rc")
            credentials {
                username = providers.gradleProperty("gpr.user").get()
                password = providers.gradleProperty("gpr.key").get()
            }
        }
    }
}
```

4. Add the dependency (versions map to repository tags, e.g. `v0.1.0` → `0.1.0`):

```kotlin
implementation("tech.alius:djirc:0.1.0")
```

Once the maintainers add your account as a Reader on the package page you can pull it. The `INTERNET` permission is already declared in the AAR's manifest, so consumers don't need to declare it; when minification is enabled, the SDK's keep rules are applied automatically from the AAR.

### Option 2: Source integration

Copy the `tech/alius/rc` and `com/tananaev/adblib` directories from `djirc/src/main/java/` into your project's source set and declare the `INTERNET` permission in your `AndroidManifest.xml`.

Both options require `minSdk ≥ 25` (the RC Plus ships with API 29; the library stays compatible down to 25). On first run the SDK generates an adb RSA key under the app's `filesDir/adbkey`; once adbd accepts the key, the loopback connection is established.

## Screen Keep-On & Always-On Running

Controllers often serve as long-running control terminals, so the demo provides two layers of keep-alive (`TeleautoRemoteControlService`, reusable in host projects):

- **Keep screen on**: `MainActivity` sets `FLAG_KEEP_SCREEN_ON` on its window in `onCreate` — the screen never times out while the app is in the foreground. Once the app goes to the background, the system's normal screen-off policy resumes; input reading is unaffected.
- **Stay running**: the `TeleautoRemoteControlService` foreground service owns the SDK lifecycle — `START_STICKY` lets the system restart it after a process kill, and a `PARTIAL_WAKE_LOCK` keeps the CPU, adb channels and Bluetooth alive while the screen is off. The notification shows the model and connection state in real time; the "Exit" action stops the service and fully releases the SDK (including gamepad mode). Closing the Activity only unregisters its own listeners — reading continues.

```kotlin
// ① Keep the screen on (Activity)
window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

// ② Stay running: declare a foreground service modeled on demo/TeleautoRemoteControlService.kt and start it
startForegroundService(Intent(this, TeleautoRemoteControlService::class.java))
```

Manifest declarations for the service (the `specialUse` type fits dedicated-device scenarios such as controllers; on Android 13+ showing the notification requires the runtime `POST_NOTIFICATIONS` grant):

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".TeleautoRemoteControlService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="djirc-input-reader" />
</service>
```

## Releasing (Maintainers)

Push a version tag to publish automatically — no Actions secrets needed (uses the built-in `GITHUB_TOKEN`):

```bash
git tag v0.2.0
git push origin v0.2.0   # Actions builds and publishes tech.alius:djirc:0.2.0 to GitHub Packages
```

- The version is the tag without the `v` prefix; local builds default to `0.0.0-SNAPSHOT`.
- Publish pipeline: `.github/workflows/publish.yml` (compiles the whole project first, then publishes).
- Granting third parties: GitHub package page → Package settings → Manage access → add the account as Reader.

### Manual publishing (without Actions)

When Actions is unavailable (e.g. billing limits), publish to GitHub Packages locally with a classic PAT (`write:packages` scope):

```bash
GITHUB_ACTOR=your-username GITHUB_TOKEN=ghp_your-token \
  ./gradlew :djirc:publishDjircPublicationToGitHubPackagesRepository -PdjircVersion=0.1.0
```

Publishing to a self-hosted Gitea Maven registry (token with package read/write scope; see comments in `djirc/build.gradle.kts`):

```bash
./gradlew :djirc:publishDjircPublicationToGiteaRepository -PdjircVersion=0.1.0 \
  -Pgitea.url=https://your-gitea/api/packages/OWNER/maven \
  -Pgitea.user=username -Pgitea.token=your-token
```

> Note: storage/transfer for private packages is billed by GitHub — publishing fails when org payment or the spending limit is broken; public packages are free and need no credentials to consume.

## API Example

```kotlin
class MyActivity : Activity() {

    private val listener = object : TeleautoJoystickManager.InputListener {
        override fun onKeyEvent(event: TeleautoRcKeyEvent) {
            when (event.key) {
                TeleautoPhysicalKey.RECORD -> when (event.action) {
                    TeleautoKeyAction.TOGGLED_ON  -> startRecording()   // Record button: toggle semantics
                    TeleautoKeyAction.TOGGLED_OFF -> stopRecording()
                }
                TeleautoPhysicalKey.SHUTTER -> if (event.action == TeleautoKeyAction.TRIGGER) takePhoto()
                else -> { /* other keys */ }
            }
        }

        override fun onStickChanged(snapshot: TeleautoStickSnapshot) {
            // High-frequency overwrite-style updates; for UI work, poll getStickSnapshot() like the demo does
        }

        override fun onHeldKeysChanged(held: Set<TeleautoPhysicalKey>) {
            // Currently held set of dpad private keys (useful for release detection)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initializes and auto-detects the model; returns whether a supported controller was found
        val supported = TeleautoSdk.initialize(this)
        // Callbacks run on the main thread by default (UI-safe); for high-frequency
        // processing, switch to a thread pool instead:
        // TeleautoSdk.initialize(this, TeleautoConfig(callbackExecutor = myExecutor))

        TeleautoJoystickManager.addInputListener(listener)
        TeleautoJoystickManager.addConnectionListener(object : TeleautoJoystickManager.ConnectionListener {
            override fun onStateChanged(state: TeleautoConnectionState) { /* disconnect / reconnect */ }
        })

        val info = TeleautoSdk.getDeviceInfo()      // model / product / firmware version / profileId …
        val connection = TeleautoJoystickManager.connection     // DISCONNECTED / CONNECTING / CONNECTED
    }

    override fun onDestroy() {
        TeleautoJoystickManager.removeInputListener(listener)
        TeleautoSdk.release()
        super.onDestroy()
    }

    private fun poll() {
        val s = TeleautoJoystickManager.getStickSnapshot()      // all stick/dial axes, including freeDial, are in [-1,1]
        TeleautoJoystickManager.setFreeDialValue(0.6f)          // continue from an explicit value when supported
        val keys = TeleautoJoystickManager.getKeyStates()       // current pressed state of each key (DOWN/UP style only)
    }
}
```

## Input Model

### TeleautoPhysicalKey (Unified Physical Key Enum)

| Group | Values | Notes |
|---|---|---|
| Sticks / dials (stateful, via `TeleautoStickSnapshot`) | `LEFT_STICK_X/Y`, `RIGHT_STICK_X/Y`, `LEFT_DIAL`, `RIGHT_DIAL`, `FREE_DIAL` | Normalized `[-1,1]`, dead zones applied |
| GPIO panel keys (rm700 series only) | `L1`–`L3`, `R1`–`R3` | Level-style `DOWN`/`UP`; delivered through host KeyEvents, not dispatched by the SDK |
| Private keys (dpad channel) | `RECORD`, `SHUTTER`, `FOCUS`, `C1`, `C2`, `C3` | Two-stage shutter: half-press `FOCUS`, full press `SHUTTER` |
| | `GOHOME`, `PAUSE`, `AUTH_LED` | RTH / pause / LED authentication |
| | `D_UP/D_DOWN/D_LEFT/D_RIGHT/D_MIDDLE` | 5D button (five-way) |
| evdev keys | `POWER` | Power button, `DOWN`/`UP` |

Keys a model doesn't have never fire on that model; `TeleautoRemoteControllerInfo.supportedInputs` lists the full set for the current model.

### TeleautoKeyAction (Action Semantics)

| Value | Meaning |
|---|---|
| `DOWN` / `UP` | Level press / release (GPIO keys, power button) |
| `TRIGGER` | Pulse trigger (shutter / focus / C keys / 5D button, etc.) |
| `TOGGLED_ON` / `TOGGLED_OFF` | Toggle on / off (recording start / stop) |

### TeleautoStickSnapshot (Stick / Dial Snapshot)

An immutable data class with fields `leftX`, `leftY`, `rightX`, `rightY`, `leftDial`, `rightDial`, and `freeDial` (all `[-1,1]`) plus `timestampNs`. On hardware with a free dial, `TeleautoJoystickManager.setFreeDialValue(value)` sets its current position and subsequent hardware pulses continue from that value. A successful call publishes a snapshot immediately. It is updated by high-frequency overwrite and read lock-free; for UI work, poll `TeleautoJoystickManager.getStickSnapshot()` at a fixed rate instead of refreshing per event.

### TeleautoConnectionState and TeleautoRemoteControllerInfo

- `TeleautoConnectionState`: `DISCONNECTED` (not initialized, or adb dropped) / `CONNECTING` (initialized, connection being established) / `CONNECTED` (both channels working normally).
- `TeleautoRemoteControllerInfo`: `model`, `product`, `device`, `manufacturer`, `firmwareVersion` (`dji.prop.external_version`), `androidVersion`, `profileId`, `supportedInputs`.

### Input Capability Matrix

| Input | rm500 | rm510 | rm700 |
|---|:--:|:--:|:--:|
| 4 stick axes + both dials | ✓ | ✓ | ✓ |
| Shutter (two-stage) / record / C1 / C2 / RTH / power | ✓ | ✓ | ✓ |
| 5D button | — | ✓ | ✓ |
| Pause `PAUSE` | — | ✓ | ✓ |
| C3 / LED auth | — | — | ✓ |
| GPIO shoulder keys L1–L3 / R1–R3 (host KeyEvents) | — | — | ✓ |

## GPIO Shoulder Keys (RM700 Series Only)

On RM700-class controllers the GPIO panel keys don't go through the SDK channels; they reach the foreground activity as standard `KeyEvent`s. The mapping is fixed: `KEYCODE_F1`–`F6` → `L1`–`L3` / `R1`–`R3`:

```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    val key = when (keyCode) {
        KeyEvent.KEYCODE_F1 -> TeleautoPhysicalKey.L1
        KeyEvent.KEYCODE_F2 -> TeleautoPhysicalKey.L2
        KeyEvent.KEYCODE_F3 -> TeleautoPhysicalKey.L3
        KeyEvent.KEYCODE_F4 -> TeleautoPhysicalKey.R1
        KeyEvent.KEYCODE_F5 -> TeleautoPhysicalKey.R2
        KeyEvent.KEYCODE_F6 -> TeleautoPhysicalKey.R3
        else -> return super.onKeyDown(keyCode, event)
    }
    if (event.repeatCount == 0) handleShoulderDown(key)
    return true
}
```

## Bluetooth Gamepad Mode

The SDK has built-in multi-mode switching (enum `TeleautoRemoteControlMode`), reusing the same input pipeline; switch at runtime any time:

```kotlin
TeleautoJoystickManager.mode = TeleautoJoystickManager.Mode.BLUETOOTH_JOYSTICK  // enter gamepad mode
// Pair the controller in the PC/Mac Bluetooth settings (name = model, e.g. "DJI RC Plus")
// — it mounts as a system gamepad.
TeleautoJoystickManager.addHidStateListener { mode, detail -> /* ADVERTISING / CONNECTED / ERROR */ }
TeleautoJoystickManager.mode = TeleautoJoystickManager.Mode.CONTROLLER        // exit, data callbacks resume
```

- **Mode semantics**: in gamepad mode input data is no longer delivered through `InputListener`; it is translated directly into standard gamepad HID reports with an **Xbox layout** — sticks → left/right stick axes (X/Y, Rx/Ry), dials → **LT/RT** triggers (linear), record → **LB**, shutter → **RB**, C1/C2/C3 → A/B/X, half-press focus → Y, 5D four-way → D-pad.
- **Connection experience**: the first pairing needs one click in the computer's Bluetooth settings (Just Works, no passkey); afterwards entering gamepad mode auto-reconnects. If a host connects but does not subscribe within 5 s, the GATT session is rebuilt automatically (an automatic "re-plug"); `TeleautoJoystickManager.resetHidPairing()` clears a broken pairing. Local test page: `docs/gamepad-test.html`.
- **Transport**: pure app-level **BLE HID over GATT (HOGP)**. DJI firmware disables the system HID Device profile (`profile_supported_hid_device` forced false by an immutable vendor overlay) and USB gadget HID requires root; this approach needs neither — verified working on macOS (native Bluetooth + browser Gamepad API, GAP Appearance declared as Gamepad).
- **Permissions**: `BLUETOOTH_CONNECT` / `BLUETOOTH_ADVERTISE` (runtime on Android 12+; ≤11 covered by manifest `BLUETOOTH`/`BLUETOOTH_ADMIN`); the device must support BLE peripheral advertising.
- **Limits**: GPIO shoulder keys (L1–L3/R1–R3) arrive via host KeyEvents and occupy no button bits in v1; BLE connection interval is ~7.5–15 ms, suited to simulators and training apps.

## Adding Support for New Models

A new model only needs a new `DeviceProfile` implementation; both channels and the translation layer are fully reused:

1. Extend `TeleautoRemoteControlProfile` (which already implements the shared logic: dual-channel reading, axis normalization, private-key translation, record toggle, dual-channel focus, etc.):

```kotlin
class Rm520Profile : TeleautoRemoteControlProfile() {
    override val id = "rm520"
    override val productName = "DJI RC Pro 2"
    override val matchModels = setOf("rc520")            // exact match on ro.product.name
    override val supportedInputs = baseInputs + setOf(
        TeleautoPhysicalKey.D_UP, TeleautoPhysicalKey.D_DOWN, TeleautoPhysicalKey.D_LEFT,
        TeleautoPhysicalKey.D_RIGHT, TeleautoPhysicalKey.D_MIDDLE,
        TeleautoPhysicalKey.PAUSE,
    )
}
```

2. Register it in `TeleautoSdk.initialize()` (alongside the three existing profiles):

```kotlin
TeleautoProfileRegistry.register(tech.alius.rc.profile.rm520.Rm520Profile())
```

3. If the new model has a different input topology (e.g. different axis codes or private-key names), simply override the corresponding handling methods of `TeleautoRemoteControlProfile`.

Calibration commands to run on real hardware when adapting a new model:

```bash
adb shell getprop ro.product.model     # model identifier
adb shell getprop ro.product.name      # property used for profile matching
adb shell getevent -l                  # inspect the evdev axis codes of sticks/dials
adb shell logcat -s KeyCodeListener    # inspect ACTION_KEY_* of the private keys
```

## Project Structure

```text
dji_rc/
├── sdk/                         # tech.alius:teleauto-core
├── modules/video_transmission/  # Native video implementation
├── third_party/                 # SDK dependencies
├── hardware/                    # Protocol and hardware references
├── docs/                        # SDK integration / protocol docs
├── .github/workflows/           # SDK build, tests and publish
└── VERSION                      # SDK release version
```

## Known Limitations

- Relies on adbd's TCP 5555 loopback being available on the controller (enable ADB in the system developer options); on first connection, adbd must accept the RSA key generated by this app.
- The dpad private-key channel itself carries no release events; the SDK infers releases from changes in the `mKeyCodeList` set. Release timing of individual keys may be affected by the system's dpad arbitration.
- The demo app's release build currently reuses the debug signature (the SDK itself ships as an AAR and is unaffected); configure a dedicated signing config before distributing the demo.
- Gamepad mode transports over BLE; wired USB HID requires root (configfs gadget), unavailable on production firmware.
- Models marked "Planned" have not been verified on the corresponding real hardware; their input topology may differ from the built-in profiles — real-device testing is authoritative.

## Third-Party Libraries & License

- [tananaev/adblib](https://github.com/tananaev/adblib) (MIT License) — bundled as source code; provides a pure-Java ADB protocol implementation.
- License of this project: internal project, license not yet specified.
