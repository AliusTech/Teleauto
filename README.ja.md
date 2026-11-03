# Teleauto SDK

対話型 Rust 署名ツール、SDK バージョン別の鍵ポリシー、FastAPI 呼び出し仕様は [License Generator](license-generator/README.md) を参照してください。

> Teleauto SDK はコア機能、共有 UI（`sdk/ui/`）と下位モジュールを管理します。独立した Teleauto アプリは業務処理を担当し、ルートは Android、`assistant/` はデスクトップ工程です。

[中文](README.md) | [English](README.en.md) | **日本語**

**TeleautoSdk** は、DJI スクリーン搭載送信機**本体**上で動作する Android 入力読み取りライブラリ(入力テスター Demo 付き)です。左右スティック、左右ダイヤル、撮影/録画ボタン、5D 五方向ボタン、C1–C3 カスタムボタン、帰航、急停止、GPIO ショルダーキーなど、送信機のすべての物理入力を、機種横断で統一された入力イベントモデルへ変換します。送信機本体での二次開発——周辺機器連携、業務アプリケーション、操縦支援、自動テストなど——に活用できます。

DJI 公式 Mobile SDK はスマートフォン側からドローンへ接続するためのものであり、送信機本体上で動作する入力読み取り API は提供していません。スクリーン搭載送信機向けに独自アプリを開発しようとしても、スティックやボタンのデータを取得できません——本プロジェクトはこの空白を埋めるものです。

## 目次

- [特徴](#特徴)
- [仕組み](#仕組み)
- [対応ハードウェア](#対応ハードウェア)
- [クイックスタート](#クイックスタート)
- [プロジェクトへの組み込み](#プロジェクトへの組み込み)
- [画面常時点灯と常駐実行](#画面常時点灯と常駐実行)
- [リリース(管理者)](#リリース管理者)
- [API 使用例](#api-使用例)
- [入力モデル](#入力モデル)
- [GPIO ショルダーキー(RM700 系のみ)](#gpio-ショルダーキーrm700-系のみ)
- [Bluetooth ゲームパッドモード](#bluetooth-ゲームパッドモード)
- [新機種の対応追加](#新機種の対応追加)
- [プロジェクト構成](#プロジェクト構成)
- [既知の制限事項](#既知の制限事項)
- [サードパーティライブラリとライセンス](#サードパーティライブラリとライセンス)

## 特徴

- **自動機種識別**:システムプロパティを読み取って機種プロファイル(`DeviceProfile`)と照合します。未識別の機種でもクラッシュせず、ホストアプリ自身のキーチャネルにも影響しません。
- **統一入力モデル**:機種横断で一貫した `TeleautoPhysicalKey` / `TeleautoKeyAction` / `TeleautoStickSnapshot` により、購読側のコードは具体的な送信機型番から切り離されます。
- **セマンティック処理**:録画ボタンのトグル(切替)セマンティクス + 長押しによる誤トグル防止(1.5 秒);半押しフォーカスのデュアルチャネルトリガーによるシステムの ~200ms 判定遅延の回避;スティックの正規化とデッドゾーン処理。
- **切断時の自動再接続**:adb トランスポート切断後 1.5 秒で自動リトライし、接続状態をリアルタイムに通知します。
- **Bluetooth ゲームパッドモード**:SDK 内蔵のマルチモード切替 — コード 1 行で送信機を PC/Mac のシステムゲームパッドにします(BLE HID over GATT、root 不要。後述)。
- **唯一のパーミッション**:`INTERNET` のみ(adb による `localhost:5555` への自己接続。ゲームパッドモードは BLUETOOTH 系権限を追加使用)。
- **画面常時点灯と常駐実行**:Demo にフォアグラウンド常駐サービス(`START_STICKY` + partial wake lock)を内蔵。Activity 終了や画面オフ後も入力読み取りと Bluetooth ゲームパッドモードは継続し、アプリがフォアグラウンドの間は画面が自動的に消えません(後述)。

## 仕組み

アプリは送信機にインストールされ、内蔵 ADB プロトコル実装(adblib)で**本体の `localhost:5555` に自己接続**し、2 本のデータチャネルを開きます:

```text
┌────────────── DJI スクリーン搭載送信機(Android デバイス) ──────────────┐
│                                                                        │
│   ホスト App(あなたの二次開発アプリ)                                   │
│  ┌───────────────── TeleautoSdk SDK(tech.alius.rc) ──────────────────┐    │
│  │   統一入力モデル:TeleautoPhysicalKey / TeleautoKeyAction / TeleautoStickSnapshot       │    │
│  │        ▲                    ▲                                  │    │
│  │   機種プロファイル DeviceProfile(rm500 / rm510 / rm700 …)      │    │
│  │   生イベント → 統一セマンティクス翻訳レイヤー                  │    │
│  │        ▲                    ▲                                  │    │
│  │ ┌──────┴─────────┐   ┌──────┴───────────┐                      │    │
│  │ │ evdev チャネル │   │ logcat チャネル  │                      │    │
│  │ │ getevent -l    │   │ logcat -s        │                      │    │
│  │ └──────┬─────────┘   │ KeyCodeListener  │                      │    │
│  │        │             └──────┬───────────┘                      │    │
│  └────────┼────────────────────┼──────────────────────────────────┘    │
│           └── adb 自己接続 localhost:5555(内蔵 adblib) ──┘             │
│                                                                        │
│   GPIO ショルダーキー L1-L3/R1-R3(RM700 系のみ):evdev で統一翻訳        │
│   (KEYCODE_F1-F6)はホスト Activity へ直接、SDK を経由しない            │
└────────────────────────────────────────────────────────────────────────┘
```

| チャネル | コマンド | 取得できる入力 |
|---|---|---|
| evdev | `getevent -l` | `EV_ABS`:左右スティック 4 軸(`ABS_X/Y`、`ABS_RX/RY`)、左右ダイヤル(`ABS_Z/RZ`);`EV_KEY`:撮影ボタン(`BTN_TR`)、電源ボタン(`KEY_POWER`)。タッチパネルデバイス(`fts_ts`)は自動スキップ |
| logcat | `logcat -T 1 -s KeyCodeListener:D *:S` | DJI プライベートキー配信:`ACTION_KEY_*` パルス(録画/撮影/フォーカス/C1–C3/帰航/急停止/LED 認証/5D ボタン)と、現在押下中のキー集合を示す `mKeyCodeList`(離し検出に使用) |

翻訳レイヤーの重要なセマンティクス:

- **正規化**:スティック/ダイヤルの軸値は `[-1, 1]` に統一マッピングされ、デッドゾーンを内蔵します(スティック中立値 128、最小デッドゾーン 4%)。読み取り側で生のレンジを処理する必要はありません。
- **録画ボタン**:`ACTION_KEY_RECORD` は離しイベントのないパルス列のため、SDK は `TOGGLED_ON` / `TOGGLED_OFF` のトグルセマンティクスへ変換し、1.5 秒ウィンドウで長押しリピートによる誤トグルを除去します。
- **半押しフォーカス**:dpad チャネルの `FOCUS` には ~200ms の判定遅延があります。SDK は `mKeyCodeList` による即時押下検出 + パルスによるフォールバックのデュアルチャネルトリガー(250ms 重複除去)で、半押し応答をほぼリアルタイムにします。
- **GPIO ショルダーキー**(RM700 系のみ):SDK が evdev チャネルで `L1–L3 / R1–R3` キーイベント(`DOWN` / `UP`)として統一翻訳します。`InputListener` を購読するだけで利用可能。システムの標準 `KeyEvent`(`KEYCODE_F1`–`F6`)配信も並行して有効で、`onKeyDown` / `onKeyUp` での処理も引き続き利用できます。

## 対応ハードウェア

2026-09-14 時点で、SDK は以下の機種を登録しています。**プロファイルの実装済みと、すべての操作部の実機検証済みは異なります。**

| 機種識別子 | 表示名 | 検証状況 |
|---|---|---|
| `rm500` / `ag501` | DJI SMART CONTROLLER | 初期化と入力ノード接続を確認。C1/C2、前面の一時停止、ホーム帰還、3 段スイッチは未解決 |
| `rm700` | DJI RC Plus | 実装済み、実機検証待ち |
| `rc701` | DJI RC Plus 2 | 実機検証済み、ユーザーが正常動作を確認 |
| `rm510` | DJI RC Pro | プロトコル対応を追加、実機検証待ち |
| `rc520` | DJI RC Pro 2 | プロトコル対応を追加、実機検証待ち |

現在、実機の受け入れ確認が完了しているのは **RC701（DJI RC Plus 2）** です。RM500 のボタンプロトコルはファームウェアの権限制限が残っています。RM510/RC520 のプロトコル権限、軸方向、実際のボタン応答は未確認です。RC520 の共通パケット形式との互換性も実機確認が必要です。[RC Pro 対応メモ](docs/rc-pro-support.md)を参照してください。表にない機種は未登録です。

### 機種識別の仕組み

- `ro.product.name` を優先し、一致しなければ `ro.product.model` を照合します。前後の空白と大文字・小文字を無視した完全一致で、ワイルドカードは使用しません。
- 内蔵プロファイルは **rm500**（`rm500` / `ag501`）、**rm700**（`rm700` / `rc701`）、**rm510**、**rc520** の 4 種です。RM510 と RC520 は個別に識別し、RM500 のシステム入力権限設定は適用しません。
- 機種情報は `TeleautoSdk.getDeviceInfo()` で取得します。機種識別、SDK 認証、入力接続、操作部の実機検証はそれぞれ別の状態です。

## クイックスタート

### 動作環境

- JDK 17
- Android SDK:compileSdk 37、Build Tools 36.0.0
- ビルドツールチェーン:Android Gradle Plugin 9.3.1、Gradle 9.5.0
- 送信機側で adb を有効化(開発者オプション)し、本体の `localhost:5555` に接続可能であること

### ビルド

> `local.properties` はリポジトリに含まれません。クローン後に自分で作成し、`sdk.dir` をローカルの Android SDK パスに指定してください。

```bash
./gradlew :sdk:assembleRelease :sdk:testDebugUnitTest
```

### Teleauto

Android アプリ、共有 UI、デスクトップ助手とデザイン素材は独立した **Teleauto** プロジェクトに移動しました。ルートは Android 工程、デスクトップ工程は `Teleauto/assistant/` です。このリポジトリでは SDK と下位モジュールのみを管理します。

## プロジェクトへの組み込み

### 方法 1:Maven 依存(GitHub Packages、推奨)

SDK は本リポジトリの GitHub Packages に公開されており、サードパーティアプリは依存 1 行で組み込めます:

1. `read:packages` スコープのみの GitHub PAT(classic)を作成します。組織で SSO が有効な場合、token に対して一度 SSO 認可を行ってください。
2. 認証情報をグローバルの Gradle 設定に置きます(プロジェクトリポジトリへのコミットを避ける):

```properties
# ~/.gradle/gradle.properties
gpr.user=あなたの GitHub ユーザー名
gpr.key=ghp_xxxxxxxxxxxx
```

3. `settings.gradle.kts` にリポジトリを追加します:

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

4. 依存を追加します(バージョンはリポジトリの tag に対応。例:`v0.1.0` → `0.1.0`):

```kotlin
implementation("tech.alius:djirc:0.1.0")
```

管理者が package ページであなたのアカウントを Reader に追加すると取得できるようになります。`INTERNET` パーミッションは AAR の manifest で宣言済みのため、使用側での再宣言は不要です。難読化を有効にした場合も、SDK の keep ルールが AAR から自動適用されます。

### 方法 2:ソースコード統合

`djirc/src/main/java/` 配下の `tech/alius/rc` と `com/tananaev/adblib` の 2 つのディレクトリをプロジェクトのソースセットにコピーし、`AndroidManifest.xml` で `INTERNET` パーミッションを宣言します。

いずれの方法も `minSdk ≥ 25` が必要です(RC Plus は出荷時 API 29、ライブラリは 25 まで下位互換)。初回実行時、SDK はアプリの `filesDir/adbkey` に adb RSA 鍵を生成し、adbd がこの鍵を受け入れた後、自己接続が完了します。

## 画面常時点灯と常駐実行

送信機は長時間稼働する操作端末であることが多いため、Demo は 2 層のキープアライブを提供します(`TeleautoRemoteControlService`、ホストプロジェクトでもそのまま利用可能):

- **画面常時点灯**:`MainActivity` が `onCreate` でウィンドウに `FLAG_KEEP_SCREEN_ON` を設定し、アプリがフォアグラウンドの間は画面が自動的に消えません。バックグラウンドに移行すると通常の画面オフポリシーに戻りますが、入力読み取りには影響しません。
- **常駐実行**:`TeleautoRemoteControlService` フォアグラウンドサービスが SDK のライフサイクルを保持します — `START_STICKY` によりプロセス強制終了後もシステムが再起動し、`PARTIAL_WAKE_LOCK` により画面オフ中も CPU と adb / Bluetooth チャネルがスリープしません。通知バーに機種と接続状態をリアルタイム表示し、「終了」アクションでサービスを停止して SDK(ゲームパッドモード含む)を完全に解放します。Activity を閉じても自身のリスナー解除のみが行われ、読み取りは継続します。

```kotlin
// ① 画面常時点灯(Activity)
window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

// ② 常駐:demo/TeleautoRemoteControlService.kt を参考にフォアグラウンドサービスを宣言して起動
startForegroundService(Intent(this, TeleautoRemoteControlService::class.java))
```

サービスのマニフェスト宣言(`specialUse` タイプは送信機のような専用デバイス向け。Android 13+ で通知を表示するには実行時の `POST_NOTIFICATIONS` 許可が必要):

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

## リリース(管理者)

バージョン tag の push で自動公開されます。Actions secrets の設定は不要です(内蔵 `GITHUB_TOKEN` を使用):

```bash
git tag v0.2.0
git push origin v0.2.0   # Actions が自動ビルドし、tech.alius:djirc:0.2.0 を GitHub Packages に公開
```

- バージョン番号 = tag から `v` 接頭辞を除いたもの。ローカルビルドの既定は `0.0.0-SNAPSHOT`。
- 公開フローは `.github/workflows/publish.yml`(全体のコンパイル検証後に公開)。
- サードパーティへの権限付与:GitHub の package ページ → Package settings → Manage access で相手のアカウントを Reader として追加します。

### 手動公開(Actions 不使用)

Actions が利用できない場合(課題制限など)、classic PAT(`write:packages` 権限)でローカルから GitHub Packages に直接公開できます:

```bash
GITHUB_ACTOR=ユーザー名 GITHUB_TOKEN=ghp_トークン \
  ./gradlew :djirc:publishDjircPublicationToGitHubPackagesRepository -PdjircVersion=0.1.0
```

セルフホストの Gitea Maven Registry への公開(package 読み書き権限のトークン。詳細は `djirc/build.gradle.kts` のコメントを参照):

```bash
./gradlew :djirc:publishDjircPublicationToGiteaRepository -PdjircVersion=0.1.0 \
  -Pgitea.url=https://あなたのGitea/api/packages/オーナー/maven \
  -Pgitea.user=ユーザー名 -Pgitea.token=トークン
```

> 注意:プライベートパッケージのストレージ/転送量は GitHub の課金対象です。組織の支払いや利用上限に異常があると公開に失敗します。公開パッケージは無料で、利用側の認証も不要です。

## API 使用例

```kotlin
class MyActivity : Activity() {

    private val listener = object : TeleautoJoystickManager.InputListener {
        override fun onKeyEvent(event: TeleautoRcKeyEvent) {
            when (event.key) {
                TeleautoPhysicalKey.RECORD -> when (event.action) {
                    TeleautoKeyAction.TOGGLED_ON  -> startRecording()   // 録画ボタン:トグルセマンティクス
                    TeleautoKeyAction.TOGGLED_OFF -> stopRecording()
                }
                TeleautoPhysicalKey.SHUTTER -> if (event.action == TeleautoKeyAction.TRIGGER) takePhoto()
                else -> { /* その他のキー */ }
            }
        }

        override fun onStickChanged(snapshot: TeleautoStickSnapshot) {
            // 高頻度の上書き更新。UI では Demo と同様に getStickSnapshot() のポーリングを推奨
        }

        override fun onHeldKeysChanged(held: Set<TeleautoPhysicalKey>) {
            // dpad プライベートキーの現在押下中の集合(離し検出に利用可)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初期化:機種を自動識別し、対応済み送信機かどうかを返す
        val supported = TeleautoSdk.initialize(this)
        // コールバックスレッドはデフォルトでメインスレッド(UI 安全)。
        // 高頻度処理ではスレッドプールに切り替え可能:
        // TeleautoSdk.initialize(this, TeleautoConfig(callbackExecutor = myExecutor))

        TeleautoJoystickManager.addInputListener(listener)
        TeleautoJoystickManager.addConnectionListener(object : TeleautoJoystickManager.ConnectionListener {
            override fun onStateChanged(state: TeleautoConnectionState) { /* 切断 / 再接続 */ }
        })

        val info = TeleautoSdk.getDeviceInfo()      // model / product / ファームウェアバージョン / profileId …
        val connection = TeleautoJoystickManager.connection     // DISCONNECTED / CONNECTING / CONNECTED
    }

    override fun onDestroy() {
        TeleautoJoystickManager.removeInputListener(listener)
        TeleautoSdk.release()
        super.onDestroy()
    }

    private fun poll() {
        val s = TeleautoJoystickManager.getStickSnapshot()      // freeDial を含む全軸が [-1,1]
        TeleautoJoystickManager.setFreeDialValue(0.6f)          // 対応機種では指定値から継続
        val keys = TeleautoJoystickManager.getKeyStates()       // 各キーの現在の押下状態(DOWN/UP 系のみ)
    }
}
```

## 入力モデル

### TeleautoPhysicalKey(統一物理キー列挙型)

| グループ | 列挙値 | 説明 |
|---|---|---|
| スティック / ダイヤル(状態型、`TeleautoStickSnapshot` 経由)| `LEFT_STICK_X/Y`、`RIGHT_STICK_X/Y`、`LEFT_DIAL`、`RIGHT_DIAL`、`FREE_DIAL` | 正規化 `[-1,1]`、デッドゾーン適用済み |
| GPIO パネルキー(rm700 系のみ)| `L1`–`L3`、`R1`–`R3` | レベル型 `DOWN`/`UP`。ホストの KeyEvent 経由で届き、SDK は経由しない |
| プライベートキー(dpad チャネル)| `RECORD`、`SHUTTER`、`FOCUS`、`C1`、`C2`、`C3` | 撮影ボタンは 2 段階:半押し `FOCUS`、全押し `SHUTTER` |
| | `GOHOME`、`PAUSE`、`AUTH_LED` | 帰航 / 急停止 / LED 認証 |
| | `D_UP/D_DOWN/D_LEFT/D_RIGHT/D_MIDDLE` | 5D 五方向ボタン |
| evdev キー | `POWER` | 電源ボタン、`DOWN`/`UP` |

機種が備えていないキーはその機種では発生しません。`TeleautoRemoteControllerInfo.supportedInputs` が現行機種の全集を示します。

### TeleautoKeyAction(アクションセマンティクス)

| 値 | 意味 |
|---|---|
| `DOWN` / `UP` | レベルの押下 / 離し(GPIO キー、電源ボタン)|
| `TRIGGER` | パルストリガー(撮影 / フォーカス / C キー / 5D ボタンなど)|
| `TOGGLED_ON` / `TOGGLED_OFF` | トグル オン / オフ(録画開始 / 停止)|

### TeleautoStickSnapshot(スティック / ダイヤルのスナップショット)

不変データクラス。フィールドは `leftX`、`leftY`、`rightX`、`rightY`、`leftDial`、`rightDial`、`freeDial`(いずれも `[-1,1]`)+ `timestampNs`。対応機種では `TeleautoJoystickManager.setFreeDialValue(value)` で現在値を指定でき、その後のハードウェアパルスは指定値から継続します。成功時はスナップショットを即時配信します。高頻度の上書き更新で、読み取り側はロックフリー。UI ではイベントごとの更新ではなく、固定周期で `TeleautoJoystickManager.getStickSnapshot()` をポーリングする方式を推奨します。

### TeleautoConnectionState と TeleautoRemoteControllerInfo

- `TeleautoConnectionState`:`DISCONNECTED`(未初期化または adb 切断)/ `CONNECTING`(初期化済み、接続確立中)/ `CONNECTED`(双チャネル正常動作)。
- `TeleautoRemoteControllerInfo`:`model`、`product`、`device`、`manufacturer`、`firmwareVersion`(`dji.prop.external_version`)、`androidVersion`、`profileId`、`supportedInputs`。

### 機種別入力対応一覧

| 入力 | rm500 | rm510 | rm700 |
|---|:--:|:--:|:--:|
| スティック 4 軸 + 左右ダイヤル | ✓ | ✓ | ✓ |
| 撮影(2 段階)/ 録画 / C1 / C2 / 帰航 / 電源 | ✓ | ✓ | ✓ |
| 5D 五方向ボタン | — | ✓ | ✓ |
| 急停止 `PAUSE` | — | ✓ | ✓ |
| C3 / LED 認証 | — | — | ✓ |
| GPIO ショルダーキー L1–L3 / R1–R3(ホスト KeyEvent)| — | — | ✓ |

## GPIO ショルダーキー(RM700 系のみ)

RM700 系の GPIO パネルキーは SDK のチャネルを経由せず、標準 `KeyEvent` としてフォアグラウンド Activity に届きます。マッピングは固定で、`KEYCODE_F1`–`F6` → `L1`–`L3` / `R1`–`R3` です:

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

## Bluetooth ゲームパッドモード

SDK はマルチモード切替(列挙 `TeleautoRemoteControlMode`)を内蔵し、同一の入力取得チェーンを再利用して実行時にいつでも切替できます:

```kotlin
TeleautoJoystickManager.mode = TeleautoJoystickManager.Mode.BLUETOOTH_JOYSTICK  // ゲームパッドモードへ
// PC/Mac の Bluetooth 設定で機種名(例「DJI RC Plus」)を検索してペアリングすると
// システムのゲームパッドとしてマウントされます。
TeleautoJoystickManager.addHidStateListener { mode, detail -> /* ADVERTISING / CONNECTED / ERROR */ }
TeleautoJoystickManager.mode = TeleautoJoystickManager.Mode.CONTROLLER        // 終了、データコールバックを復帰
```

- **モードの意味**:ゲームパッドモードでは入力データは `InputListener` へのコールバックを行わず、標準 gamepad HID レポートへ **Xbox レイアウト**で直接翻訳されます — 左右スティック → 左右スティック軸(X/Y、Rx/Ry)、左右ダイヤル → **LT/RT** トリガー(リニア)、録画キー → **LB**、撮影キー → **RB**、C1/C2/C3 → A/B/X、半押しフォーカス → Y、5D 四方向 → 十字キー。
- **接続体験**:初回のみ PC の Bluetooth 設定で一度「接続」をクリック(Just Works、パスコード不要)。以降はモードに入るだけで自動再接続します。ホスト接続後 5 秒以内に HID 購読がない場合、GATT セッションを自動再構築(自動「挿し直し」)。`TeleautoJoystickManager.resetHidPairing()` で異常なペアリングを解除できます。ローカルテストページ:`docs/gamepad-test.html`。
- **トランスポート**:純アプリ層の **BLE HID over GATT(HOGP)**。DJI ファームウェアはシステムの HID Device プロファイルを無効化しており(`profile_supported_hid_device` が不変のベンダー overlay で false)、USB gadget HID は root が必要です。本方式は root 不要・システム変更なしで、macOS で実測検証済み(Bluetooth ネイティブ + ブラウザ Gamepad API、GAP Appearance は Gamepad として宣言)。
- **権限**:`BLUETOOTH_CONNECT` / `BLUETOOTH_ADVERTISE`(Android 12+ は実行時リクエスト。≤11 は manifest の `BLUETOOTH`/`BLUETOOTH_ADMIN` でカバー)。デバイスが BLE ペリファラル広告に対応している必要があります。
- **制限**:GPIO ショルダーキー(L1–L3/R1–R3)はホスト KeyEvent 経由のため v1 ではボタンに割り当てなし。BLE 接続間隔は約 7.5–15ms で、シミュレータ・訓練用途に適します。

## 新機種の対応追加

新機種の対応には `DeviceProfile` 実装を 1 つ追加するだけで、チャネルと翻訳レイヤーはすべて再利用されます:

1. `TeleautoRemoteControlProfile` を継承します(双チャネル読み取り、軸正規化、プライベートキー翻訳、録画トグル、フォーカスのデュアルチャネル化などの共通ロジックは実装済み):

```kotlin
class Rm520Profile : TeleautoRemoteControlProfile() {
    override val id = "rm520"
    override val productName = "DJI RC Pro 2"
    override val matchModels = setOf("rc520")            // ro.product.name と完全一致
    override val supportedInputs = baseInputs + setOf(
        TeleautoPhysicalKey.D_UP, TeleautoPhysicalKey.D_DOWN, TeleautoPhysicalKey.D_LEFT,
        TeleautoPhysicalKey.D_RIGHT, TeleautoPhysicalKey.D_MIDDLE,
        TeleautoPhysicalKey.PAUSE,
    )
}
```

2. `TeleautoSdk.initialize()` 内で登録します(既存の 3 プロファイルと並べて):

```kotlin
TeleautoProfileRegistry.register(tech.alius.rc.profile.rm520.Rm520Profile())
```

3. 新機種の入力トポロジーが異なる場合(軸のコードやプライベートキー命名の違いなど)は、`TeleautoRemoteControlProfile` の該当する処理メソッドをオーバーライドしてください。

新機種対応時の実機での確認コマンド:

```bash
adb shell getprop ro.product.model     # 型番識別
adb shell getprop ro.product.name      # プロファイル照合に使用するプロパティ
adb shell getevent -l                  # スティック/ダイヤルの evdev 軸コードを確認
adb shell logcat -s KeyCodeListener    # プライベートキーの ACTION_KEY_* を確認
```

## プロジェクト構成

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

## 既知の制限事項

- 送信機側の adbd による TCP 5555 自己接続が利用可能であることに依存します(システムの開発者オプションで adb を有効化)。初回接続時、adbd が本アプリ生成の RSA 鍵を受け入れる必要があります。
- dpad プライベートキーチャネル自体には離しイベントがなく、SDK は `mKeyCodeList` の集合変化から離しを推測します。個別キーの離しタイミングは、システムの dpad 判定の影響を受ける場合があります。
- Demo アプリの release ビルドは現状 debug 署名を流用しています(SDK 自体は AAR 配布のため影響なし)。Demo を正式配布する前に専用の署名構成を行ってください。
- ゲームパッドモードは BLE トランスポートです。USB 有線 HID は root が必要で、製品ファームウェアでは利用できません。
- 「対応予定」の機種は対応実機で検証されておらず、入力トポロジーが内蔵プロファイルと異なる可能性があります。実機での検証結果を優先してください。

## サードパーティライブラリとライセンス

- [tananaev/adblib](https://github.com/tananaev/adblib)(MIT License)—— ソースコードとして内蔵。純 Java の ADB プロトコル実装を提供します。
- 本プロジェクト自体のライセンス:内部プロジェクトのため、現時点では未指定です。
