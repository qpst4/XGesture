<div align="center">

<img src="art/logo.svg" width="96" alt="XGesture（X手势）" />

# 📱 XGesture（X手势）— Android 究極のジェスチャー＆片手生産性ツール

**Samsung OHO+、FooView、Quick Cursor のオープンソース統合版**  
*エッジジェスチャー · 片手カーソル · フローティングボール OCR・逆画像検索 · シェイク/伏せ/バックタップ · 通知＆OTP · アプリ凍結 · フリーフォーム · Shizuku & LSPosed*

[English](README.md) | [简体中文](README_zh.md) | **日本語**

[![Release](https://img.shields.io/github/v/release/qpst4/XGesture?style=flat-square&color=6340e6)](https://github.com/qpst4/XGesture/releases)
[![License](https://img.shields.io/badge/license-AGPL--3.0-blue?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%2012%2B-brightgreen?style=flat-square)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-purple?style=flat-square)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-9.4.0-blue?style=flat-square)](https://developer.android.com/build)
[![Gradle](https://img.shields.io/badge/Gradle-9.7.1-blue?style=flat-square)](https://gradle.org)
[![Compose BOM](https://img.shields.io/badge/Compose%20BOM-2026.08.01-blue?style=flat-square)](https://developer.android.com/jetpack/compose)
[![minSdk](https://img.shields.io/badge/minSdk-31-orange?style=flat-square)](https://developer.android.com)
[![targetSdk](https://img.shields.io/badge/targetSdk-37-orange?style=flat-square)](https://developer.android.com)

<br />

<img src="art/screenshots/hero_showcase.webp" width="96%" alt="XGesture 全景プレビュー" />

</div>

---

**XGesture（X手势）** は、Android 12 以降の全メーカー端末向けに設計された、システムレベルのジェスチャー＆片手生産性強化ツールです。アクセシビリティサービスを基盤に、**Shizuku、Root（KernelSU / Magisk / APatch）**、および任意の **LSPosed** による複数の権限昇格モードを統合し、大型スマートフォンの片手操作の課題を解決します。

画面端の多角度マルチセグメントスワイプ、片手フローティングポインター、多機能フローティングボール、端末のシェイク、伏せ置き、背面タップなどで **50 種以上のシステムアクション** を簡単に起動できます。**100% ローカルオフライン OCR**、分かち書き（CppJieba）、逆画像検索の集約を深く統合し、任意のアプリ上に高効率なオーバーレイランチャー、アプリ凍結室、OTP 認証コード抽出、通知管理、各 OEM のフリーフォーム小窓を提供。クラウドアップロードや広告は一切なく、プライバシー最優先です。

- **パッケージ名：** `com.slideindex.app`
- **現在のバージョン：** 1.40.0（versionCode 72）
- **システム要件：** Android 12+（API 31+）
- **ライセンス：** [AGPL-3.0 License](LICENSE)

---

## 📥 ダウンロード＆インストール

<div align="center">

[![Download Full APK](https://img.shields.io/badge/Full%20版をダウンロード-オフラインエンジン内蔵-238636?style=for-the-badge&logo=android&logoColor=white)](https://github.com/qpst4/XGesture/releases/latest)
[![Download Lite APK](https://img.shields.io/badge/Lite%20版をダウンロード-軽量パッケージ-0969DA?style=for-the-badge&logo=android&logoColor=white)](https://github.com/qpst4/XGesture/releases/latest)

</div>

| ビルド | 用途 | 説明 |
| :--- | :--- | :--- |
| **Full 版** (`xgesture-*-full.apk`) | **新規ユーザー推奨** | オフライン OCR、Jieba 分かち書き、オフライン翻訳 Native エンジンを内蔵し、すぐに使える |
| **Lite 版** (`xgesture-*-lite.apk`) | 小さいサイズ / オンライン更新 | コアジェスチャーと基本機能のみ。サイズが小さく、拡張エンジンは必要に応じてダウンロード |

> [!TIP]
> 両バージョンの `applicationId` は `com.slideindex.app` で共通。直接上書きインストールでき、設定は保持されます。

---

## 🌟 主な機能

アプリ下部に 4 つの主要タブ：**🏠 ホーム** · **📳 モーション** · **🔔 通知** · **🧩 拡張**

### 🏠 ホーム — エッジジェスチャー、フローティングボール、カスタマイズ

#### 1. エッジジェスチャー
- **トリガーと外観**：左右端および上下の高感度トリガーバー。バブル、カプセル、ウェーブなどのアニメーションと触覚フィードバックをサポート。
- **カスタムレイアウトと横画面専用設定**：トリガー位置、高さ、太さ、角度、セグメントを自由調整。**横画面専用トリガーハンドル** にも対応し、ゲームや動画視聴時の誤タッチを防止。
- **スマート誤タッチ防止**：横画面/ロック画面/ホーム画面での自動非表示、使用状況アクセス権に基づくアプリ別除外リストとフォアグラウンド切替ブラックリスト。
- **コーナーラジアルメニュー**：左下または右下から扇形メニューを呼び出し。リアルタイム壁紙ガウスぼかしとジェスチャーアクションのグループ化をサポート。
- **フリーフォーム小窓の深い統合**：Android ネイティブ Freeform、Xiaomi MIUI/HyperOS 小窓、Meizu Flyme 小窓など各 OEM 専用の小窓ポリシーに対応。

#### 2. 50 種以上の設定可能なジェスチャーアクション

| カテゴリ | 対応操作 |
| :--- | :--- |
| **システムナビ** | 戻る、ホーム、最近のアプリ、前のアプリ、ロック画面（消音付き）、電源メニュー、画面分割 |
| **スクリーンショット＆視覚** | 全画面スクショ、範囲スクショ、全画面 OCR、範囲 OCR、画面録画、懐中電灯 |
| **OCR＆画像検索** | フローティングボール OCR、逆画像検索集約、即時翻訳、画面全体コピー、ピン留めパネル、QR コード認識 |
| **パネル＆ランチャー** | クイックランチャー、アプリインデックス、円形ランチャー（FV 風）、ハニカムランチャー、ホログラフィックランチャー、タスクスイッチャー（OHO 風）、アプリ凍結室、拡張パネル（音量/明るさ）、Widget 浮遊パネル |
| **メディア＆制御** | 前/次の曲、再生/一時停止、音量調整、明るさ調整、IME 切替 |
| **ツール＆履歴** | フローティングポインター、クリップボード履歴パネル、クイックツールパネル（OHO 風）、一時停止オーバーレイ、ジェスチャー一時停止 |
| **高度＆拡張** | Shell コマンド実行（Shizuku / Root）、Activity / ショートカット起動、指定アプリ起動、N 分後アラーム、再凍結 |

#### 3. 🔮 フローティングボール（OCR、画像検索、マルチジェスチャー）
*「フローティングポインター」とは独立して動作。ジョイスティック操作と画面 OCR ポインターを一体化。*

- **アクセシビリティ＆ローカル多エンジン OCR**：まずアクセシビリティノードからテキスト取得、失敗時はローカルオフライン OCR（**ML Kit / Tesseract / PaddleOCR ONNX**）へスムーズにフォールバック。
- **テキスト選択パネル**：ワンタップ検索、翻訳、点詞分かち書き（CppJieba）、全選択、空白除去、コピー。範囲選択と画像共有 OCR 履歴をサポート。
- **テキスト検索集約**：検索エンジンリストのカスタマイズ、グリッド並べ替え、検索履歴、プレフィックスエイリアス、ディープリンク。GestureEVO / SearchEVO からのインポートも可能。
- **逆画像検索集約**：範囲スクショ後に多エンジン検索パネルを表示（Google、Yandex、TinEye、SauceNAO、IQDB、3D-IQDB、ASCII2D、trace.moe、AnimeTrace、Copyseeker）。並列検索と内蔵 WebView プレビューをサポート。
- **ピン留め**：スクショやテキストブロックを画面上部に固定。ピンチズーム、ドラッグ、コントロールバー非表示をサポート。
- **外観と微調整**：プリセット配色、カスタム画像、GIF、スライドショー。ポインター感度と OCR 許容値を調整可能。

#### 4. テーマと UI
- **Miuix デザインシステム**：Miuix UI（HyperOS 風）を全面採用。折りたたみ TopAppBar、仮想化グループカード、オーバースクロールダンピング、触覚フィードバック。
- **Material You ダイナミックカラー**：Android 12+ で MaterialKolor により壁紙色を自動抽出し、9 種のパレットスタイルを Miuix テーマに注入。
- **ぼかしとパフォーマンス**：下部ナビゲーションと浮遊パネルにガウスぼかしとプログレッシブぼかしをサポート。リストスクロール時の画面サンプリングコストを完全に排除するため、ぼかしをオフにすることも可能。

---

### 📳 モーション＆端末ジェスチャー — シェイク、伏せ置き、背面タップ

- **6 方向シェイク認識**：左右反転、前後反転、左右の素早い振りを精密に区別。アプリ別の感度と専用アクションを設定可能。
- **伏せ置きミュート**：画面オン時に端末を下向きに机に置くと、ロック画面と着信音ミュートを自動実行（カスタム連動アクションと音声フィードバックをサポート）。
- **背面タップ（BackTap）**：加速度センサーによる背面二重タップ認識。50 種以上のアクションに対応。画面オン/オフ/常時トリガーポリシー、感度調整、充電時の誤タッチ防止。
- **シナリオルール**：画面オン/ロック時の有効化ポリシー、アプリ別ブラック/ホワイトリスト、独立した感度しきい値、振動フィードバック。

---

### 🔔 通知 — リマインダー、履歴、OTP

- **多様な通知リマインダー**：システム通知をインターセプトし、Dynamic Island 風カード、ヘッドアップ、サイドバブル、画面弾幕などで表示。「ロック解除後に最新メッセージを開く」（ロック画面で受信した通知を解除後に自動開く。常に許可/確認ルールをサポート）、通知専用ジェスチャー、DND アプリのブラック/ホワイトリスト。
- **通知履歴とフィルタリング**：アクティブ、履歴、非表示に分類管理。多次元正規表現とキーワードによる自動アーカイブ/ブロック。
- **OTP 認証コードセンター**：SMS とアプリ通知から認証コードを自動認識。正規表現抽出、クリップボード書き込み、自動入力。成功率統計付き。オプションの LSPosed モジュールでシステムレベルの SMS 注入を強化。

---

### 🧩 拡張 — ユーティリティとバックアップ

| 機能 | 入口 | 概要 |
| :--- | :--- | :--- |
| **アプリインデックス** | 拡張 → アプリインデックス | 拼音頭文字インデックス付きアプリリスト。列数とパネル透明度を調整可能 |
| **クイックランチャー** | 拡張 → クイックランチャー | グリッドランチャー。マルチパネル切替、ページング、フォルダドラッグ統合、ショートカットライブラリ |
| **円形ランチャー** | ジェスチャー「円形ランチャー」 | FV 風の同心円レイアウト。層数、間隔、形状、スロットをカスタマイズ |
| **ハニカムランチャー** | 拡張 → ハニカムランチャー | 六角形ハニカムグリッド。外周へスワイプでアプリとショートカットを直接起動 |
| **ホログラフィックランチャー** | 拡張 → ホログラフィックランチャー | 全画面 3D 球体ランチャー。ドラッグで 3D 球体を回転、タップで起動 |
| **タスクスイッチャー** | ジェスチャー「タスクスイッチャー」 | OHO 風の最近のタスクパネル。スワイプ切替、個別終了、全クリア、小窓起動 |
| **クイックツールパネル** | ジェスチャー「クイックツールパネル」 | OHO 風のクイック設定パネル。システムトグルとショートカットを集約 |
| **アプリ凍結室** | 拡張 → 凍結室 | Shizuku / Root でバックグラウンドアプリを一括凍結/解凍。ジェスチャーから再凍結も可能 |
| **検索パネル** | 拡張 → 検索パネル | アプリ、連絡先、ファイル、システム設定、Web 検索、逆画像検索の統合検索 |
| **Activity ショートカット** | 拡張 → Activity ショートカット | 非表示システム設定、非エクスポート Activity、App Shortcuts、URI ディープリンク |
| **外部呼び出し** | 拡張 → 外部呼び出し | `xgesture://` Deeplink と Intent Action（Tasker / MacroDroid 等向け） |
| **Shell コマンド** | 拡張 → Shell コマンド | コマンドパネル、テンプレート変数、カスタムアイコン。Shizuku / Root で実行 |
| **Widget パネル** | 拡張 → Widget パネル | デスクトップ Widget を浮遊表示。ぼかし背景と複数選択をサポート |
| **フローティングポインター** | 拡張 → フローティングポインター | 仮想ジョイスティック制御のリングポインター。ホバー選択、ラジアルアクション、ジェスチャー録画再生 |
| **クリップボード履歴** | ジェスチャー「クリップボードパネル」 | テキスト/画像履歴検索、エッジ浮遊ウィンドウ、ページング。Shizuku バックグラウンド監視と `xgesture://` 外部プロトコル |
| **設定バックアップ** | 拡張 → 設定バックアップ | 全設定とアセットを ZIP でエクスポート/インポート。機密データは独立暗号化 |

#### 📸 主要 UI プレビュー

<table>
  <tr>
    <th width="33.33%" align="center">コーナーラジアルメニュー</th>
    <th width="33.33%" align="center">円形ランチャー（FV 風）</th>
    <th width="33.33%" align="center">クイックランチャー（グリッド）</th>
  </tr>
  <tr>
    <td align="center"><img src="art/screenshots/01_circle_launcher_framed.webp" width="100%" alt="コーナーラジアルメニュー" /></td>
    <td align="center"><img src="art/screenshots/03_honeycomb_launcher_framed.webp" width="100%" alt="円形ランチャー" /></td>
    <td align="center"><img src="art/screenshots/06_quick_launcher_framed.webp" width="100%" alt="クイックランチャー" /></td>
  </tr>
  <tr>
    <th align="center">フローティングポインター</th>
    <th align="center">Shell コマンドパネル</th>
    <th align="center">クリップボード履歴</th>
  </tr>
  <tr>
    <td align="center"><img src="art/screenshots/08_floating_pointer_framed.webp" width="100%" alt="フローティングポインター" /></td>
    <td align="center"><img src="art/screenshots/07_shell_panel_framed.webp" width="100%" alt="Shell パネル" /></td>
    <td align="center"><img src="art/screenshots/10_clipboard_panel_framed.webp" width="100%" alt="クリップボード履歴" /></td>
  </tr>
  <tr>
    <th align="center">アプリインデックス</th>
    <th align="center">Widget 浮遊パネル</th>
    <th align="center">ピン留め浮窓</th>
  </tr>
  <tr>
    <td align="center"><img src="art/screenshots/05_app_index_framed.webp" width="100%" alt="アプリインデックス" /></td>
    <td align="center"><img src="art/screenshots/09_widget_panel_framed.webp" width="100%" alt="Widget パネル" /></td>
    <td align="center"><img src="art/screenshots/11_pin_image_framed.webp" width="100%" alt="ピン留め" /></td>
  </tr>
</table>

---

## 🔗 外部呼び出し

他のアプリ、Tasker、MacroDroid、`adb` から XGesture パネルを起動できます。アプリ内：**拡張 → クイック操作 → 外部呼び出し** で参照・コピー可能。

> **前提条件：** 検索パネル、収納夹、クリップボードパネルにはサイドバーとアクセシビリティサービスが必要。通知フィルターには通知リスナー権限が必要。

### Deeplink（推奨）

形式：`xgesture://open/<path>?q=<任意のキーワード>`

| 機能 | URI | 説明 |
| :--- | :--- | :--- |
| 通知フィルター | `xgesture://open/notification-history` | 通知フィルターを開く |
| 通知フィルター（検索入力） | `xgesture://open/notification-history?q=キーワード` | 検索語を事前入力 |
| 収納夹 | `xgesture://open/stash` | 収納夹パネルを開く |
| 収納夹（検索入力） | `xgesture://open/stash?q=キーワード` | 検索語を事前入力 |
| クリップボード | `xgesture://open/clipboard` | クリップボードパネルを開く |
| クリップボード（検索入力） | `xgesture://open/clipboard?q=キーワード` | 検索語を事前入力 |
| 検索パネル | `xgesture://open/search-panel` | 検索パネルを開く |
| 検索パネル（キーワード入力） | `xgesture://open/search-panel?q=キーワード` | キーワードを事前入力 |

例：

```bash
# 検索パネルを開く
adb shell am start -a android.intent.action.VIEW -d "xgesture://open/search-panel"

# キーワードを事前入力して検索パネルを開く
adb shell am start -a android.intent.action.VIEW -d "xgesture://open/search-panel?q=天気"
```

### Intent Action（上級者向け）

URI がない場合、またはコンポーネントを明示する場合に使用。パッケージ名は `com.slideindex.app`。

| 機能 | Action | コンポーネント | 任意 extra |
| :--- | :--- | :--- | :--- |
| 通知フィルター | `com.slideindex.app.action.OPEN_NOTIFICATION_HISTORY` | `.MainActivity` | —（検索入力は Deeplink を使用） |
| 収納夹 | `com.slideindex.app.action.OPEN_STASH_PANEL` | `.service.StashClipboardTrampolineActivity` | `q` |
| クリップボード | `com.slideindex.app.action.OPEN_CLIPBOARD_PANEL` | `.service.StashClipboardTrampolineActivity` | `q` |
| 検索パネル | `com.slideindex.app.action.OPEN_SEARCH_PANEL` | `.service.SearchPanelTrampolineActivity` | `q` |
| ジェスチャー切替 | `com.slideindex.app.action.TOGGLE_GESTURE` | `.service.ToggleGestureTrampolineActivity` | — |
| Shell パネル | `com.slideindex.app.action.OPEN_SHELL_PANEL` | `.service.ShellCommandPanelTrampolineActivity` | —（非エクスポート、アプリ内ショートカットのみ） |

例：

```bash
# キーワードを事前入力して検索パネルを開く
adb shell am start -a com.slideindex.app.action.OPEN_SEARCH_PANEL \
  -n com.slideindex.app/.service.SearchPanelTrampolineActivity \
  --es q "天気"

# エッジジェスチャーのマスタースイッチを切替
adb shell am start -a com.slideindex.app.action.TOGGLE_GESTURE \
  -n com.slideindex.app/.service.ToggleGestureTrampolineActivity
```

---

## 💡 設計思想と比較

### 巨人の肩の上に
Android には革新的なジェスチャー＆生産性ツールが数多く生まれました：
- **Samsung One Hand Operation+ (OHO+)**：画面端の多角度トリガーと滑らかな追従アニメーションを極めた。
- **Quick Cursor**：エッジからの片手カーソルで大型画面の操作を救った。
- **FooView (FV)**：フローティングボール OCR、多エンジン画像検索、浮遊小窓の統合度を押し上げた。

### なぜ XGesture か？
これらの名作に深く敬意を払いながらも、現代の Android ユーザーにはまだ残る課題があります：
- **メーカー壁の打破**：OHO+ は最高級のジェスチャーツールの一つだが、Samsung Galaxy に限定。Xiaomi、OPPO、vivo、Pixel、Meizu など非 Samsung ユーザーが同等の体験を求めている。
- **オープンソース＆プライバシー最優先**：商用ツールにはクローズドコンポーネントやクラウド API が含まれることが多い。XGesture は **100% AGPL-3.0 完全オープンソース** で、**ローカルオフラインモデル（PaddleOCR ONNX / ML Kit）** を内蔵。
- **オールインワン統合**：3〜4 個の独立ツールを常駐させる必要なく、エッジジェスチャー、片手ポインター、フローティングボール OCR を深く連携。Miuix のダンピングアニメーションで統一された体験を提供。

| 比較項目 | **XGesture（X手势）** | **Samsung OHO+** | **Quick Cursor** | **FooView** |
| :--- | :---: | :---: | :---: | :---: |
| **オープンソース** | ✅ **AGPL-3.0（100% FOSS）** | ❌ プロプライエタリ | ❌ プロプライエタリ | ❌ プロプライエタリ |
| **対応端末** | ✅ **全メーカー（Android 12+）** | ⚠️ Samsung Galaxy 限定 | ✅ 全機種 | ✅ 全機種 |
| **料金と広告** | ✅ **完全無料 / 広告なし / 課金なし** | ✅ 無料（Samsung 端末） | ⚠️ 基本無料 + PRO 課金 | ✅ 無料（スポンサー等） |
| **エッジジェスチャー** | ✅ **OHO+ 風（多角度/長押しホールド）** | ✅ ネイティブ OHO+ | ⚠️ エッジスワイプでポインター | ⚠️ エッジ/ボールスワイプ |
| **片手リーチ** | ✅ **内蔵フローティングカーソル** | ⚠️ 仮想タッチパッド | ✅ ネイティブ片手カーソル | ⚠️ ボール十字照準 |
| **画面 OCR / 逆画像検索** | ✅ **ローカルオフライン（ONNX / ML Kit）** | ❌ 非対応 | ❌ 非対応 | ⚠️ クラウド中心 |
| **システム権限** | ✅ **Shizuku + Root + LSPosed + A11y** | ✅ Samsung システム署名 | ⚠️ アクセシビリティ | ⚠️ Root + A11y |
| **UI とアニメーション** | ✅ **Miuix + リアルタイムガウスぼかし** | ✅ One UI ネイティブ | ⚠️ 標準 Material | ⚠️ クラシック UI |

---

## 🛠️ 技術スタック

| レイヤー | コア技術 / 依存 |
| :--- | :--- |
| **言語** | Kotlin 2.4.0 + C++17（NDK / CMake） |
| **UI** | Miuix UI（KMP 0.9.4 / HyperOS 風）+ Jetpack Compose（BOM 2026.07.01） |
| **カラー＆視覚** | MaterialKolor 5.0.0 + Haze 1.7.2（ガウスぼかし） |
| **アーキテクチャ＆ DI** | MVVM + UDF + Dagger Hilt 2.60.1 |
| **非同期＆状態** | Kotlin Coroutines 1.11.0 + StateFlow / SharedFlow + DataStore Preferences 1.2.1 |
| **OCR＆ AI** | ML Kit 16.0.1 + Tesseract4Android 4.9.0 + PaddleOCR（ONNX Runtime 1.28.0 + OpenCV 4.12.0） |
| **NLP** | CppJieba（JNI）+ ML Kit Translate / Language ID |
| **権限昇格** | Shizuku API 13.1.5 + LibSuperuser 1.1.1 + HiddenApiBypass 6.1 + LibXposed API 102.0.0 |
| **ネットワーク** | OkHttp 5.4.0 + ZXing 3.5.4 + kotlinx.serialization 1.11.0 + Markdown Renderer M3 |

---

## 📐 アーキテクチャ

**マルチモジュール** 構成で **MVVM + UDF** を採用：

```
                              ┌─────────────────────────────┐
                              │          :app (ルート)       │
                              └──────────────┬──────────────┘
                                             │
                      ┌──────────────────────┼──────────────────────┐
                      ▼                      ▼                      ▼
           ┌──────────────────────┐┌──────────────────────┐┌──────────────────────┐
           │   :feature:settings  ││     :feature:otp     ││   :feature:shake     │
           │   :feature:apps      ││ :feature:notification││   :feature:message   │
           └──────────┬───────────┘└──────────┬───────────┘└──────────┬───────────┘
                      │                       │                       │
                      └──────────────────────┼───────────────────────┘
                                             ▼
           ┌──────────────────────────────────────────────────────────────────────┐
           │ :core:gesture       :core:ocr          :core:translate  :core:autofill│
           │ :core:overlay-layout:core:native-engine:core:monitoring :core:common │
           └──────────────────────────────────┬───────────────────────────────────┘
                                             ▼
                                  ┌───────────────────────┐
                                  │   :vendor:ppocr-sdk   │
                                  └───────────────────────┘
```

1. **サービス調整と Overlay 描画**：`SlideIndexAccessibilityService` がグローバルジェスチャーをインターセプト。浮遊層は `OverlayLayout` と各 WindowManager で管理。
2. **多エンジン動的ロード**：OCR、翻訳、分かち書きは Full / Lite 両モード。Native `.so` とモデルは実行時に展開可能。
3. **システム権限チャネル**：Shizuku IPC、Root（LibSu）、LSPosed モジュールが協調し、バックグラウンドクリップボード監視とプロセス制御を実現。

---

## 📂 プロジェクト構成

```
.
├── app/                                 # ホストアプリ：DI、JNI、メイン UI
│   └── src/main/
│       ├── cpp/                         # C++17 JNI（CppJieba ブリッジ）
│       └── java/com/slideindex/app/
│           ├── activity/                # トップレベル Activity
│           ├── backtap/                 # 背面ジェスチャー
│           ├── clipboard/               # クリップボード履歴
│           ├── gesture/                 # コアジェスチャーサービス
│           ├── overlay/                 # システム Overlay 管理
│           ├── search/                  # テキスト＆逆画像検索
│           ├── shell/                   # Shell コマンド実行
│           ├── ui/                      # Compose UI、テーマ
│           └── xposed/                  # LSPosed モジュール
├── core/                                # コアライブラリ
├── feature/                             # 機能モジュール
├── vendor/ppocr-sdk/                    # PaddleOCR SDK
├── gradle/libs.versions.toml
└── RELEASE_NOTES.md
```

---

## 🚀 ビルド

### 要件
- **JDK 25**
- **Android Studio**（Ladybug 以降推奨）
- **Android SDK**（API 37）と **NDK 28+**

### コマンド
```bash
git clone https://github.com/qpst4/XGesture.git
cd XGesture

# Full Debug（オフラインエンジン内蔵）
./gradlew assembleFullDebug

# Lite Release（軽量版）
./gradlew assembleLiteRelease
```

---

## 🌍 翻訳に参加

[![翻訳状況](https://hosted.weblate.org/widget/XGesture/app-strings/svg-badge.svg)](https://hosted.weblate.org/engage/XGesture/)

[Weblate](https://hosted.weblate.org/engage/XGesture/) で **アプリ UI の翻訳** に協力（Git 不要）。詳細は [貢献ガイド（日本語）](docs/contributing_ja.md#アプリ-ui-の翻訳weblate-推奨)。

---

## 💬 コミュニティ

<div align="center">

[![GitHub Discussions](https://img.shields.io/badge/GitHub-Discussions-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/qpst4/XGesture/discussions)
[![GitHub Issues](https://img.shields.io/badge/GitHub-Issues-EA4AAA?style=for-the-badge&logo=github&logoColor=white)](https://github.com/qpst4/XGesture/issues)
<br>
[![Telegram](https://img.shields.io/badge/Telegram-Chat-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/+NtcG5HPiKXVmNTU1)
[![QQ Group](art/qq_group_badge.svg)](https://qm.qq.com/q/Zx4wd2LB4G)

> Telegram：[チャット](https://t.me/+NtcG5HPiKXVmNTU1) · QQ：**1042783385**

</div>

---

## 💖 スポンサー

XGesture が日常で役立つと感じたら、開発者にコーヒー ☕ を一杯お願いします！

<div align="center">
  <img src="art/sponsor.png" width="220" alt="WeChat 寄付コード" />
</div>

---

## 📜 ライセンス

[GNU Affero General Public License v3.0](LICENSE)（AGPLv3）で公開されています。

---

## 🤝 謝辞

以下のオープンソースプロジェクトに感謝します（詳細は [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)）：

- [SideGesture](https://github.com/aaronzzx/SideGesture) — エッジジェスチャーと Overlay 構成
- [EdgeGesture](https://github.com/evilgodxu/EdgeGesture) — 拡張パネル、背面ジェスチャー等
- [EdgeX](https://github.com/oxohang/EdgeX) & [FanFreeform](https://github.com/oxohang/FanFreeform) — ハニカムランチャーと凍結室
- [ClipboardListener](https://github.com/aa2013/ClipboardListener) & [ClipShare](https://github.com/aa2013/ClipShare) — バックグラウンドクリップボード監視
- [Root Activity Launcher](https://github.com/zacharee/RootActivityLauncher) — 非エクスポート Activity 起動
- [Circle To Search](https://github.com/AKS-Labs/CircleToSearch) — 逆画像検索多エンジン統合
- [Nova Text](https://github.com/CashewTeam/BigBang_NovaText) — フローティングボール OCR
- [XposedSmsCode](https://github.com/tianma8023/XposedSmsCode) — OTP SMS Hook
- [Miuix](https://github.com/compose-miuix-ui/miuix) — Miuix Compose コンポーネント

---

## 📝 更新履歴

完全な履歴は [RELEASE_NOTES.md](RELEASE_NOTES.md) と [CHANGELOG.md](CHANGELOG.md) を参照してください。
