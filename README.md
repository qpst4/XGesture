<div align="center">

<img src="art/logo.svg" width="96" alt="XGesture" />

# 📱 XGesture — The Ultimate Android Gesture & One-Handed Productivity Suite

**All-in-One Open-Source Alternative to Samsung One Hand Operation+ (OHO+), Quick Cursor & FooView**  
*Edge Panels · Reachability Floating Cursor · Floating Ball OCR & Reverse Image Search · Shake & Back-Tap Gestures · Notification & OTP Manager · App Freezer · Freeform Window · Shizuku & LSPosed*

**English** | [简体中文](README_zh.md) | [日本語](README_ja.md)

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

<img src="art/screenshots/hero_showcase.webp" width="96%" alt="XGesture Full Showcase" />

</div>

---

**XGesture** is a universal system-level gesture and single-handed productivity suite designed for all Android 12+ devices across all OEMs. Built on Accessibility Services with flexible multi-privilege modes (**Shizuku, Root / KernelSU / Magisk**, and optional **LSPosed** integration), it brings desktop-grade control and effortless reachability to modern large-screen phones.

Easily trigger **50+ system actions** via customizable multi-angle screen-edge swipes, reachability floating pointers, multi-functional floating ball, device shaking, desk-flipping, or back-tap gestures. Deeply integrates **100% offline on-device OCR**, word segmentation (CppJieba), and reverse image search aggregation—delivering high-efficiency overlay launchers, app freezing, OTP verification code extraction, notification management, and OEM freeform windows on top of any app, completely free from ads and cloud trackers.

- **Package Name:** `com.slideindex.app`
- **Current Version:** 1.40.0 (versionCode 72)
- **Requirements:** Android 12+ (API 31+)
- **License:** [AGPL-3.0 License](LICENSE)

---

## 📥 Download & Installation

<div align="center">

[![Download Full APK](https://img.shields.io/badge/Download%20Full%20APK-Built--in%20Offline%20Engines-238636?style=for-the-badge&logo=android&logoColor=white)](https://github.com/qpst4/XGesture/releases/latest)
[![Download Lite APK](https://img.shields.io/badge/Download%20Lite%20APK-Compact%20Size-0969DA?style=for-the-badge&logo=android&logoColor=white)](https://github.com/qpst4/XGesture/releases/latest)

</div>

| Variant | Scenario | Description |
| :--- | :--- | :--- |
| **Full Package** (`xgesture-*-full.apk`) | **Recommended for new users** | Out-of-the-box experience with built-in offline OCR, Jieba segmentation, and offline translation native engines |
| **Lite Package** (`xgesture-*-lite.apk`) | Compact size / on-demand updates | Retains core gestures and fundamental features with smaller package size; download extension engines on demand |

> [!TIP]
> Both packages share the same `applicationId` (`com.slideindex.app`), allowing direct overwriting installation without losing configurations.

---

## 🌟 Core Features

The app features four main navigation tabs: 🏠 **Home** · 📳 **Motion** · 🔔 **Notification** · 🧩 **Extensions**.

### 🏠 Home — Edge Gestures, Floating Ball & Customization

#### 1. Edge Gestures
- **Triggers & Aesthetics**: Highly responsive left/right/top/bottom edge triggers, supporting bubble, capsule, wave animations, and haptic feedback.
- **Custom Layout & Landscape Triggers**: Freely adjust trigger position, height, thickness, angle, and segments; supports **dedicated landscape trigger handles** for gaming and media viewing.
- **Smart Anti-Mistouch**: Auto-hide in landscape/lock screen/home screen, with per-app exclusion lists and foreground switching blacklists.
- **Corner Radial Menu**: Swipe from bottom-left or bottom-right corners to summon a radial menu with real-time Gaussian blur and action grouping.
- **OEM Freeform Integration**: Deeply adapts to native Android Freeform, Xiaomi MIUI/HyperOS Small Window, and Meizu Flyme Small Window.

#### 2. 50+ Configurable Gesture Actions

| Category | Actions Supported |
| :--- | :--- |
| **System Navigation** | Back, Home, Recents, Previous App, Lock Screen, Power Menu, Split Screen |
| **Screenshot & Visual** | Fullscreen Screenshot, Region Screenshot, Screenshot OCR, Region OCR, Screen Recorder, Flashlight |
| **OCR & Image Search** | Floating Ball OCR, Reverse Image Search, Live Screen Translation, Universal Screen Copy, Pin Screenshot, QR Code Scanner |
| **Panels & Launchers** | Quick Launcher, App Index, Ring Launcher (FV Style), Honeycomb Launcher, Holographic Launcher, Task Switcher (OHO Style), App Freezer, Expand Panel (Volume/Brightness), Widget Overlay |
| **Media & Controls** | Previous/Next Track, Play/Pause, Volume Adjust, Brightness Adjust, Switch IME |
| **Tools & History** | Floating Pointer, Clipboard History Panel, Quick Tools Panel (OHO Style), Pause Overlay, Pause Gestures |
| **Advanced & Extensions** | Run Shell Commands (Shizuku / Root), Launch Activity / Shortcut, Direct App Launch, Alarm Reminder in N min, Re-freeze Apps |

#### 3. 🔮 Floating Ball (OCR, Image Search & Gestures)
*Operates independently from the Floating Pointer, combining joystick navigation with screen OCR pointers.*

- **Accessibility & Offline Multi-Engine OCR**: Extracts text via accessibility nodes first, seamlessly falling back to local offline OCR (**ML Kit / Tesseract / PaddleOCR ONNX**).
- **Text Selection Panel**: One-click search, translate, segmentation (CppJieba), select all, remove spaces, and copy; supports region clipping and shared image OCR history.
- **Search Aggregation**: Customizable search engines, grid sorting, search history, prefix aliases, and deep links; supports importing from GestureEVO / SearchEVO.
- **Reverse Image Search**: Summon multi-engine reverse search (Google, Yandex, TinEye, SauceNAO, IQDB, 3D-IQDB, ASCII2D, trace.moe, AnimeTrace, Copyseeker) with built-in WebView preview.
- **Pin to Screen**: Pin screenshots or text cards as top-level floating windows with pinch-to-zoom and drag support.
- **Appearance & Tuning**: Custom themes, images, GIFs, and slideshows; adjustable pointer sensitivity and OCR tolerance.

#### 4. Theme & Appearance
- **Miuix Design System**: Comprehensive adoption of Miuix UI (HyperOS style) with collapsible TopAppBar, virtualized grouped cards, overscroll damping, and haptic feedback.
- **Material You Dynamic Coloring**: Automatically extracts wallpaper colors via MaterialKolor on Android 12+, generating 9 palette styles injected into MiuixTheme.
- **Blur & Performance**: Bottom navigation and floating overlays support Gaussian blur and progressive blur; can be disabled to completely eliminate scroll sampling overhead.

---

### 📳 Motion & Device Gestures — Shake, Flip & Back-Tap

- **6-Direction Shake Recognition**: Distinguishes left/right flip, forward/backward flip, and quick side shakes; supports per-app custom actions and sensitivities.
- **Desk-Flip Mute**: Place face-down on a flat surface while screen is on to trigger lock screen and silent mode.
- **Back-Tap Gestures (BackTap)**: Accelerometer-based back-cover double-tap recognition supporting 50+ actions, screen on/off/always trigger policies, sensitivity tuning, and charging guard.
- **Scenario Rules**: Screen on/locked activation policies, app blacklists/whitelists, independent sensitivity thresholds, and vibration feedback.

---

### 🔔 Notification — Reminders, History & OTP

- **Multi-Style Reminders**: Intercepts notifications with Dynamic Island cards, heads-up popups, side bubbles, or screen danmaku; supports "Open Latest Message on Unlock" (with whitelist and confirm policies).
- **Notification History & Filtering**: Categorizes active, history, and hidden notifications with multi-dimensional regex and keyword archiving.
- **OTP Verification Center**: Automatically detects verification codes in SMS and app notifications with regex extraction, clipboard auto-copy, and auto-fill; optional LSPosed module for system-level SMS injection.

---

### 🧩 Extensions — Utility Tools & Backup

| Module | Navigation Entry | Core Capabilities |
| :--- | :--- | :--- |
| **App Index** | Extensions → App Index | Alphabetical Pinyin rail app drawer with adjustable columns and transparency |
| **Quick Launcher** | Extensions → Quick Launcher | Grid launcher supporting multi-panel switching, pagination, folder grouping, and shortcut library |
| **Ring Launcher** | Gesture Action "Ring Launcher" | FV-style concentric ring layout with customizable tiers, spacing, shapes, and slots |
| **Honeycomb Launcher** | Extensions → Honeycomb Launcher | Hexagonal honeycomb grid layout with swipe-out direct launching |
| **Holographic Launcher** | Extensions → Holographic Launcher | Fullscreen 3D holographic sphere launcher; drag to rotate and click to open |
| **Task Switcher** | Gesture Action "Task Switcher" | OHO-style vertical running tasks panel with swipe-to-switch, kill, clear all, and freeform launch |
| **Quick Tools Panel** | Gesture Action "Quick Tools Panel" | OHO-style quick settings panel aggregating system toggles, actions, and shortcuts |
| **App Freezer** | Extensions → App Freezer | Batch freeze and unfreeze background apps via Shizuku / Root with quick re-freeze gesture |
| **Search Panel** | Extensions → Search Panel | Unified search for apps, contacts, files, system settings, web queries, and reverse image search |
| **Activity Shortcuts** | Extensions → Activity Shortcuts | Hidden system settings, non-exported activity launcher, App Shortcuts, and URI deep links |
| **External Invocation** | Extensions → External Invocation | `xgesture://` deeplinks and intent actions for Tasker, MacroDroid, and other automations |
| **Shell Commands** | Extensions → Shell Commands | Command panels, template variable substitution, and custom icons via Shizuku / Root |
| **Widget Panel** | Extensions → Widget Panel | Floating desktop widget host with adjustable blur background and multi-selection |
| **Floating Pointer** | Extensions → Floating Pointer | Virtual joystick-controlled ring pointer with hover selection, radial actions, and gesture macro replay |
| **Clipboard History** | Gesture Action "Clipboard Panel" | Rich clipboard history with search, edge floating window, and Shizuku background listener |
| **Settings Backup** | Extensions → Settings Backup | Export and import all configurations and assets as encrypted ZIP files |

#### 📸 Key Interfaces & Interaction Preview

<table>
  <tr>
    <th width="33.33%" align="center">Corner Radial Menu</th>
    <th width="33.33%" align="center">Ring Launcher (FV Style)</th>
    <th width="33.33%" align="center">Quick Launcher (Grid)</th>
  </tr>
  <tr>
    <td align="center"><img src="art/screenshots/01_circle_launcher_framed.webp" width="100%" alt="Corner Radial Menu" /></td>
    <td align="center"><img src="art/screenshots/03_honeycomb_launcher_framed.webp" width="100%" alt="Ring Launcher" /></td>
    <td align="center"><img src="art/screenshots/06_quick_launcher_framed.webp" width="100%" alt="Quick Launcher" /></td>
  </tr>
  <tr>
    <th align="center">Floating Pointer (Joystick)</th>
    <th align="center">Shell Command Panel (Shizuku)</th>
    <th align="center">Clipboard History / Stash</th>
  </tr>
  <tr>
    <td align="center"><img src="art/screenshots/08_floating_pointer_framed.webp" width="100%" alt="Floating Pointer" /></td>
    <td align="center"><img src="art/screenshots/07_shell_panel_framed.webp" width="100%" alt="Shell Command Panel" /></td>
    <td align="center"><img src="art/screenshots/10_clipboard_panel_framed.webp" width="100%" alt="Clipboard History" /></td>
  </tr>
  <tr>
    <th align="center">App Index (Pinyin Rail)</th>
    <th align="center">Widget Floating Panel</th>
    <th align="center">Pin Screenshot Window</th>
  </tr>
  <tr>
    <td align="center"><img src="art/screenshots/05_app_index_framed.webp" width="100%" alt="App Index" /></td>
    <td align="center"><img src="art/screenshots/09_widget_panel_framed.webp" width="100%" alt="Widget Floating Panel" /></td>
    <td align="center"><img src="art/screenshots/11_pin_image_framed.webp" width="100%" alt="Pin Screenshot" /></td>
  </tr>
</table>

---

## 🔗 External Invocation

Open XGesture panels from other apps, Tasker, MacroDroid, or `adb`. In-app reference: **Extensions → Shortcuts & actions → External Invocation** (tap to copy).

> **Prerequisites:** Search panel, stash, and clipboard require the sidebar and accessibility service. Notification hub requires notification listener access.

### Deeplinks (recommended)

Format: `xgesture://open/<path>?q=<optional query>`

| Feature | URI | Notes |
| :--- | :--- | :--- |
| Notification hub | `xgesture://open/notification-history` | Open notification hub |
| Notification hub (prefill) | `xgesture://open/notification-history?q=keyword` | Open with search prefill |
| Stash | `xgesture://open/stash` | Open stash panel |
| Stash (prefill) | `xgesture://open/stash?q=keyword` | Open with search prefill |
| Clipboard | `xgesture://open/clipboard` | Open clipboard panel |
| Clipboard (prefill) | `xgesture://open/clipboard?q=keyword` | Open with search prefill |
| Search panel | `xgesture://open/search-panel` | Open search panel |
| Search panel (prefill) | `xgesture://open/search-panel?q=keyword` | Open with query prefill |

Examples:

```bash
# Open search panel
adb shell am start -a android.intent.action.VIEW -d "xgesture://open/search-panel"

# Open search panel with prefill
adb shell am start -a android.intent.action.VIEW -d "xgesture://open/search-panel?q=weather"
```

### Intent actions (advanced)

Use when there is no matching URI, or when you need an explicit component. Package name: `com.slideindex.app`.

| Feature | Action | Component | Optional extra |
| :--- | :--- | :--- | :--- |
| Notification hub | `com.slideindex.app.action.OPEN_NOTIFICATION_HISTORY` | `.MainActivity` | — (use deeplink for search prefill) |
| Stash | `com.slideindex.app.action.OPEN_STASH_PANEL` | `.service.StashClipboardTrampolineActivity` | `q` |
| Clipboard | `com.slideindex.app.action.OPEN_CLIPBOARD_PANEL` | `.service.StashClipboardTrampolineActivity` | `q` |
| Search panel | `com.slideindex.app.action.OPEN_SEARCH_PANEL` | `.service.SearchPanelTrampolineActivity` | `q` |
| Toggle gestures | `com.slideindex.app.action.TOGGLE_GESTURE` | `.service.ToggleGestureTrampolineActivity` | — |
| Shell panel | `com.slideindex.app.action.OPEN_SHELL_PANEL` | `.service.ShellCommandPanelTrampolineActivity` | — (not exported; app shortcuts only) |

Examples:

```bash
# Open search panel with prefill
adb shell am start -a com.slideindex.app.action.OPEN_SEARCH_PANEL \
  -n com.slideindex.app/.service.SearchPanelTrampolineActivity \
  --es q "weather"

# Toggle edge gesture master switch
adb shell am start -a com.slideindex.app.action.TOGGLE_GESTURE \
  -n com.slideindex.app/.service.ToggleGestureTrampolineActivity
```

---

## 💡 Design Philosophy & Comparisons

### Standing on the Shoulders of Giants
Android has seen legendary tools that shaped mobile navigation and productivity:
- **Samsung One Hand Operation+ (OHO+)** perfected multi-angle edge triggers and smooth system gestures.
- **Quick Cursor** introduced thumb-friendly screen reachability via intuitive floating cursors.
- **FooView (FV)** pioneered floating-ball universal OCR, search aggregation, and floating window shortcuts.

### Why XGesture?
While we deeply admire these pioneering tools, modern Android users frequently face fragmentation:
- **Vendor Lock-in**: OHO+ is one of the most beloved gesture tools ever created, but it remains exclusive to Samsung One UI. Non-Samsung users on Pixel, Xiaomi, Motorola, Sony, etc., have long sought a genuine alternative.
- **Open Source & Privacy-First**: Proprietary utilities often carry cloud-dependent OCR and closed ecosystems. XGesture is **100% AGPL-3.0 open-source**, bundling **offline on-device OCR (PaddleOCR ONNX / ML Kit)** that respects user privacy.
- **Unified Navigation**: Rather than running multiple separate tools for edge gestures, single-handed pointers, and floating OCR, XGesture brings them together into a unified, coherent experience with fluid Miuix animations.

| Dimension | **XGesture (This App)** | **Samsung OHO+** | **Quick Cursor** | **FooView** |
| :--- | :---: | :---: | :---: | :---: |
| **Open Source & License** | ✅ **AGPL-3.0 (100% FOSS)** | ❌ Proprietary (Closed) | ❌ Proprietary (Closed) | ❌ Proprietary (Closed) |
| **Device Compatibility** | ✅ **Universal (Any Android 12+)** | ⚠️ Samsung Galaxy only | ✅ Universal | ✅ Universal |
| **Pricing & Monetization** | ✅ **100% Free & No Ads forever** | ✅ Free (with One UI) | ⚠️ Free basic + PRO in-app purchases | ✅ Free (Ad/sponsor supported) |
| **Edge Navigation Gestures** | ✅ **Full OHO+ style (Multi-angle & Hold)** | ✅ Native OHO+ gestures | ⚠️ Edge swipe for cursor only | ⚠️ Floating / Edge gestures |
| **Single-Hand Reachability** | ✅ **Built-in reachability joystick pointer** | ⚠️ Virtual Touchpad (floating box mode) | ✅ Native reachability cursor | ⚠️ Floating ball crosshair |
| **Screen OCR & Reverse Search** | ✅ **Local offline models (ONNX / ML Kit)** | ❌ No such feature | ❌ No such feature | ⚠️ Cloud & web-assisted OCR |
| **Deep System Privileges** | ✅ **Shizuku + Root + LSPosed + A11y** | ✅ Samsung system-signature privileges | ⚠️ Accessibility Service | ⚠️ Root + Accessibility Service |
| **Visual Aesthetics & Haptics** | ✅ **Miuix & Real-time Gaussian Blur** | ✅ One UI native design | ⚠️ Standard Material | ⚠️ Classic utility UI |

---

## 🛠️ Tech Stack

| Layer | Core Technologies / Components |
| :--- | :--- |
| **Languages** | Kotlin 2.4.0 + C++17 (NDK / CMake) |
| **UI Framework** | Miuix UI (KMP 0.9.4 / HyperOS style) + Jetpack Compose (BOM 2026.07.01) |
| **Color & Visuals** | MaterialKolor 5.0.0 (Dynamic Wallpaper Colors) + Haze 1.7.2 (Gaussian Blur) |
| **Architecture & DI** | MVVM + UDF (Unidirectional Data Flow) + Dagger Hilt 2.60.1 |
| **Async & State** | Kotlin Coroutines 1.11.0 + StateFlow / SharedFlow + DataStore Preferences 1.2.1 |
| **OCR & AI** | ML Kit Text Recognition 16.0.1 + Tesseract4Android 4.9.0 + PaddleOCR (ONNX Runtime 1.28.0 + OpenCV 4.12.0) |
| **NLP & Tokenization** | CppJieba (JNI Native Tokenizer) + ML Kit Translate / Language ID |
| **Privileged Access** | Shizuku API 13.1.5 + LibSuperuser 1.1.1 + HiddenApiBypass 6.1 + LibXposed API 102.0.0 |
| **Network & Parsing** | OkHttp 5.4.0 + ZXing Core 3.5.4 + kotlinx.serialization 1.11.0 + Markdown Renderer M3 |

---

## 📐 Architecture

The app adopts a **Multi-Module layered architecture** following **MVVM + UDF** patterns:

```
                              ┌─────────────────────────────┐
                              │          :app (Root)        │
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

1. **Service Coordination & Overlay Rendering**: `SlideIndexAccessibilityService` intercepts global gestures and dispatches system events, while overlays are managed by `OverlayLayout` and independent WindowManagers.
2. **Dynamic Engine Loading**: OCR, translation, and segmentation engines support both Full and Lite packaging modes; native `.so` libraries and models can be extracted at runtime.
3. **Privileged Pipelines**: Shizuku IPC (rootless privilege), Root fallback executor (LibSu), and LSPosed module collaborate to provide seamless background clipboard monitoring and process lifecycle control.

---

## 📂 Project Structure

```
.
├── app/                                 # Host Application: DI assembly, JNI C++ bridge, and main UI
│   └── src/main/
│       ├── cpp/                         # C++17 JNI Sources (CppJieba bridge)
│       └── java/com/slideindex/app/
│           ├── activity/                # Top-level Activities & transparent interaction layers
│           ├── backtap/                 # Back-tap gestures
│           ├── clipboard/               # Clipboard history & Shizuku background monitor
│           ├── gesture/                 # Core gesture service & overlay trigger bars
│           ├── overlay/                 # System overlay window managers
│           ├── search/                  # Text & reverse image search aggregator
│           ├── shell/                   # Shell command executor
│           ├── ui/                      # Compose UI, Miuix themes, and dynamic colors
│           └── xposed/                  # LSPosed module entry & hooks
├── core/                                # Core modules & engines
│   ├── autofill/                        # OTP autofill framework
│   ├── common/                          # Extensions, shared models, and utilities
│   ├── gesture/                         # Gesture recognition algorithms & event bus
│   ├── monitoring/                      # Performance tracing & memory monitoring
│   ├── native-engine/                   # Offline native engine distribution & asset extraction
│   ├── notification/                    # Notification interception & processing
│   ├── ocr/                             # Multi-engine OCR dispatcher (ML Kit / Tesseract / ONNX)
│   ├── overlay-layout/                  # Overlay layout rendering core
│   └── translate/                       # Multi-language translation & language detection
├── feature/                             # Business feature modules
│   ├── apps/                            # App scanner, icon caching, and launchers
│   ├── message/                         # Heads-up notification & danmaku rendering
│   ├── notification/                    # Notification history & archiving
│   ├── otp/                             # Verification code extraction & statistics
│   ├── settings/                        # Settings, preferences backup, and DataStore
│   └── shake/                           # Sensor shake & desk-flip algorithms
├── vendor/
│   └── ppocr-sdk/                       # PaddleOCR SDK binding & ONNX Runtime
├── gradle/libs.versions.toml             # Dependency catalog
└── RELEASE_NOTES.md                     # Release changelog
```

---

## 🚀 Building & Compilation

### Requirements
- **JDK 25**
- **Android Studio** (Ladybug or newer recommended)
- **Android SDK** (API 37 `compileSdk`) & **NDK 28+**

### Build Commands
```bash
# Clone the repository
git clone https://github.com/qpst4/XGesture.git
cd XGesture

# Build Full Debug APK (with built-in offline engines)
./gradlew assembleFullDebug

# Build Lite Release APK (compact version)
./gradlew assembleLiteRelease
```

---

## 🌍 Help Translate

[![Translation status](https://hosted.weblate.org/widget/XGesture/app-strings/svg-badge.svg)](https://hosted.weblate.org/engage/XGesture/)

Contribute **in-app UI translations** on [Weblate](https://hosted.weblate.org/engage/XGesture/) — no Git required. See [CONTRIBUTING.md](CONTRIBUTING.md#translating-the-app-weblate--recommended).

---

## 💬 Community & Feedback

Welcome to join the community discussions and provide feedback or feature requests:

<div align="center">

[![GitHub Discussions](https://img.shields.io/badge/GitHub-Discussions-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/qpst4/XGesture/discussions)
[![GitHub Issues](https://img.shields.io/badge/GitHub-Issues-EA4AAA?style=for-the-badge&logo=github&logoColor=white)](https://github.com/qpst4/XGesture/issues)
<br>
[![Telegram Chat](https://img.shields.io/badge/Telegram-Chat-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/+NtcG5HPiKXVmNTU1)
[![QQ Group](art/qq_group_badge.svg)](https://qm.qq.com/q/Zx4wd2LB4G)

> Telegram: [chat group](https://t.me/+NtcG5HPiKXVmNTU1) (XGesture · X手势) · QQ Group: **1042783385**

</div>

---

## 💖 Sponsor & Support

If you find **XGesture** helpful in your daily workflow, consider buying the developer a cup of coffee ☕! Your generous support is the greatest motivation for ongoing development and OEM adaptations.

<div align="center">
  <img src="art/sponsor.png" width="220" alt="WeChat Reward Code" />
</div>

---

## 📜 License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE) (AGPLv3).

---

## 🤝 Acknowledgements

Special thanks to the following open-source projects for their architecture, inspiration, and code references (see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for details):

- [SideGesture](https://github.com/aaronzzx/SideGesture) — Edge gesture and overlay architecture reference
- [EdgeGesture](https://github.com/evilgodxu/EdgeGesture) — Expand panel, alarm reminder, screen translation, universal copy, and back-tap gesture reference
- [EdgeX](https://github.com/oxohang/EdgeX) & [FanFreeform](https://github.com/oxohang/FanFreeform) — Honeycomb launcher and App Freezer architecture reference
- [ClipboardListener](https://github.com/aa2013/ClipboardListener) & [ClipShare](https://github.com/aa2013/ClipShare) — Android 10+ background clipboard listening architecture
- [Root Activity Launcher](https://github.com/zacharee/RootActivityLauncher) — Non-exported activity launching scheme
- [Circle To Search](https://github.com/AKS-Labs/CircleToSearch) — Multi-engine reverse image search strategy
- [Nova Text](https://github.com/CashewTeam/BigBang_NovaText) — Floating ball OCR and word selection interaction
- [XposedSmsCode](https://github.com/tianma8023/XposedSmsCode) — OTP SMS Hook and verification code extraction rules
- [Miuix](https://github.com/compose-miuix-ui/miuix) — Elegant Material & Miuix style Compose UI component library

---

## 📝 Changelog

For complete release history, see [RELEASE_NOTES.md](RELEASE_NOTES.md) and [CHANGELOG.md](CHANGELOG.md).
