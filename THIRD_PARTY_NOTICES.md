# Third-Party Notices

XGesture（`com.slideindex.app`）在 [GNU Affero General Public License v3.0](LICENSE) 下发布。
以下开源项目的代码或思路被参考、移植或复用，特此致谢。

完整文件级映射见 [docs/ATTRIBUTIONS.md](docs/ATTRIBUTIONS.md)。

---

## SideGesture (gulugulu / SideGesture)

- **Copyright:** aaronzzx and contributors
- **License:** [Apache License 2.0](app/src/main/assets/licenses/Apache-2.0.txt)
- **Source:** https://github.com/aaronzzx/SideGesture (formerly https://github.com/aaronzzx/gulugulu)
- **Used in:** 边缘手势触发动画（`GestureAnimation*`）、快捷方式解析（`ShortcutUtils`）、无障碍覆盖层窗口类型与系统手势排除区域、屏幕尺寸度量、顶部触钮几何与窗口布局等
- **Modifications:** 以 Kotlin 重写并集成至 XGesture；架构拆分为 `EdgeOverlayHost`、`OverlayWindowTypes` 等模块；v1.6 顶部触钮按 Bottom 镜像适配

---

## Clipboard Whitelist

- **Copyright:** Tehcneko
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/Tehcneko/ClipboardWhitelist
- **Used in:** LSPosed 剪贴板白名单 Hook（`ClipboardWhitelistHook`）及 App 侧同步桥接（`ClipboardWhitelistBridge`）
- **Modifications:** 集成至 SlideIndex Xposed 模块；prefs 命名修正；增加 Compose 管理界面与设置备份联动

---

## Circle To Search

- **Copyright:** AKS-Labs and contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/AKS-Labs/CircleToSearch
- **Used in:** 以图搜图图床上传（Litterbox/Catbox）、Google/Yandex/TinEye hosted-URL 构建、多引擎 WebView 预加载面板
- **Modifications:** 拆分为 `ImageHostUploader` / `ImageSearchUrlBuilder` 等模块；扩展 IQDB、SauceNAO、ASCII2D 等 direct-POST 引擎；集成至悬浮球取词面板

---

## Nova Text (BigBang)

- **Copyright:** CashewTeam and contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/CashewTeam/BigBang_NovaText
- **Used in:** 悬浮球取词、无障碍文本提取与 OCR 降级等交互思路（无直接代码移植）
- **Note:** XGesture 悬浮球主实现参考 GestureEVO / FooView 路线；与 Nova Text 属同类产品思路借鉴（GestureEVO 已开源，另见下一条）

---

## GestureEVO

- **Copyright:** GestureEVO contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)（项目标注 GPL-3.0-only）
- **Source:** https://github.com/LumiaGG/GestureEVO
- **Used in:** 悬浮球取词管线（`inspire/`：`InspireView`、`InspireFloating`、`InspireDataHolder`、`ManagedBitmap`、`AccessibilityNodeManager`、`BaseFloatingWindow` 等，类职责与模块划分沿用该项目）、屏幕搜索（`screensearch/`：`ScreenSearchFloating`、`ScreenSearchCapture`、`ScreenSearchHighlightView` 等，搜索主循环、无障碍与 OCR 两条取文路径、滚动节奏与区域裁剪策略沿用该项目）、图片编辑器（`imageeditor/`：`EditorSession`、`EditorMode`、`EditAction`、`EditorExportEngine`、`EditorRenderUtils`、`BrushSizePreviewView`、`ShapeTypePreviewView` 沿用其模块划分；核心视图 `ui/ImageEditorView` 与其 `ui/InspireImageEditorView` 有 71 个同名方法）、临时免打扰（`timeddnd/`：`TempDndManager` 与其同名实现，`hasUsableDndPolicyAccess` 签名一致）、无障碍手势注入的观感对齐（`SlideIndexAccessibilityGestureInjector`）
- **Modifications:** 以 Kotlin 集成至 XGesture 取词与手势管线；按本项目的数据模型、设置体系与主题改写；构建与依赖替换为本仓库体系；屏幕搜索在其基础上废除区域裁剪改为全屏遍历、单步滚动幅度由 60% 屏高收窄至 30%、新增滚动后停稳判定与触底检测、截图前隐藏本项目浮层；图片编辑器拆分为 model/state/ui/utils 多文件，核心视图在本仓库为 Java 实现（其源为 Kotlin `ui/InspireImageEditorView`）并接入本项目的 Material 取色与主题，新增序号标注、钉图、共享拖拽与快捷工具；临时免打扰拆为 6 个文件并新增取消、规则记录与独立的时长范围（`TempDndLimits`，1..1440 分钟，与该项目一致；原先借用提醒模块的 1..120）。移植文件均已加来源头注释
- **Note:** GestureEVO 的 `inspire/` 内含 `BigBang*` 类，其取词交互应源自 BigBang / Nova Text 一脉（本项目对二者的参考见上一条）

---

## QuickDash

- **Copyright:** balajitechlabs and contributors
- **License:** 仓库自述 [GPL-3.0](app/src/main/assets/licenses/GPL-3.0.txt)（上游许可链存疑，见 Note）
- **Source:** https://github.com/balajitechlabs/quickdash
- **Used in:** 「快捷菜单」各工具的行为规格参考（二维码解析与生成、密码生成、单位/汇率换算、秒表与倒计时、文本工具）
- **Modifications:** 全部以 Kotlin 独立重写，**无直接代码移植**；重写过程中另修正了参考实现的多处缺陷（二维码尺寸参数被忽略、SSID 含分隔符时的解析、一处从未生效的渐变着色器）
- **Note:** QuickDash 自称 GPL-3.0，但其上游 PocketOps 标注的是自定义开源许可，与 GPL-3.0 存在冲突（README 末尾另有 All Rights Reserved）。因许可链不清晰，本项目**只参考行为规格、不使用其代码**；若今后需移植其代码，须先厘清该许可链。

---

## XposedSmsCode

- **Copyright:** Tianma (tianma8023) and contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/tianma8023/XposedSmsCode
- **Used in:** LSPosed 短信验证码捕获（`SmsHandlerHook`、`SmsProviderHook`、`SmsCaptureForwarder`）、安装期权限自动授予（`PermissionGranterHook`）、系统级 OTP 自动输入（`SystemInputInjectorHook`）、验证码提取与规则匹配（`VerificationCodeExtractor`、`OtpKeywords`、`OtpRulesParser`）、官方规则资产（`smscode-rules.json`）、OTP 桥接（`OtpSmsBridgeReceiver`）
- **Modifications:** 以 Kotlin + LibXposed 重写并集成至 XGesture OTP 中心；补充 Telephony Provider 层短信捕获；广播桥接与通知监听提取路径合并；官方规则以 assets 内置并按相同 JSON schema 解析

---

## ClipboardListener

- **Copyright:** aa2013
- **License:** [MIT License](app/src/main/assets/licenses/MIT.txt)
- **Source:** https://github.com/aa2013/ClipboardListener
- **Used in:** Android 10+ 剪贴板后台监听（Shizuku/Root 前台服务、`logcat` 与隐藏 API 双路径）、`listener.zip` 监听 DEX、`IClipboardListenerService` / `IOnClipboardChanged` AIDL（`clipboard/monitor/*`）
- **Modifications:** 从 Flutter 插件抽离并以 Kotlin 重写；集成至 XGesture 剪贴板历史/暂存与 Hilt 依赖注入；去除 Dart 桥接层

---

## ClipShare

- **Copyright:** aa2013 and contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/aa2013/ClipShare
- **Used in:** Android 10+ 后台剪贴板读取（`ClipboardFocusReader`：短暂添加 1×1 可聚焦悬浮窗以获取 `ClipboardManager` 读权限）；剪贴板监听整体架构参考
- **Modifications:** 适配 XGesture `ClipboardPayload` 管线；与 ClipboardListener 监听服务联动

---

## Root Activity Launcher

- **Copyright:** Zachary Wander (zacharee)
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/zacharee/RootActivityLauncher
- **Used in:** 应用内直达启动未导出 Activity（`search/ral/*`、`NonExportedActivityLauncher`）；快捷方式启动与搜索引擎深链中的特权启动路径
- **Modifications:** 以 Kotlin 移植 RootActivityLauncher 启动策略链（Normal / Iterative / Shizuku / SamsungExploit / Root / Assistant）；裁剪为 Activity 特权启动场景；接入 Shizuku 权限检查

---

## FanFreeform / Hyper手势

- **Copyright:** oxohang and contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/oxohang/FanFreeform
- **Used in:** 蜂窝启动器几何与命中检测（`HoneycombGeometry`）、全屏蜂窝覆盖层视图与控制器（`HoneycombOverlayView`、`HoneycombOverlayController`）、模糊背景缓存（`BlurredWallpaperCache`）、运行时启动目标（`HoneycombRuntimeTarget`）、overlay 窗口编排（`HoneycombAppPickerOverlayWindow`）、动画原点角（`HoneycombCorner`）、蜂窝布局编辑器触摸逻辑（`HoneycombLayoutEditorScreen` 部分）；显示与动效默认参数对齐 FanFreeform `ConfigContract`（`HoneycombDisplaySettings`）
- **Modifications:** 以 Kotlin / Jetpack Compose 重写设置与编辑界面；集成至 XGesture 边缘手势与 `QuickLauncher` 数据模型；壁纸背景改为无障碍截图捕获并去除 Xposed / SystemUI Hook 依赖；补充 `layoutSlots` 布局编辑 API；接入小窗 / 全屏启动策略

---

## Quick Search

- **Copyright:** Teja Karlapudi
- **License:** [MIT License](app/src/main/assets/licenses/MIT.txt)
- **Source:** https://github.com/teja2495/quick-search
- **Used in:** 聚合搜索中的文件搜索（MediaStore 文件名检索与过滤）、计算器表达式求值、Google 搜索建议 API、联系人打开方式、文件类型分类与路径模式匹配等；搜索面板的前缀别名匹配（`SearchPanelAliasResolver`，first token + 空格取别名）与系统壁纸取图顺序（`SystemWallpaperBlurHelper`，drawable → fastDrawable → wallpaper file）
- **Modifications:** 以 Kotlin 重写并集成至 XGesture 聚合搜索管线；去除 Quick Search 独立 App 的 ViewModel / 偏好层；文件搜索改为 `FileSearchIndex` 等独立模块；搜索面板侧只对齐式样与取图顺序，未移植其代码

---

## Miuix (compose-miuix-ui)

- **Copyright:** compose-miuix-ui contributors (YuKongA et al.)
- **License:** [Apache License 2.0](app/src/main/assets/licenses/Apache-2.0.txt)
- **Source:** https://github.com/compose-miuix-ui/miuix
- **Used in:** 设置页与主界面 Miuix UI（`MiuixTheme`、`Scaffold`、`TopAppBar`、Preference 组件、模糊/Shader、`NavigationRail`、`FloatingNavigationBar`、`WindowDialog` 等）以及 `miuix-nav` 应用内导航；Gradle 依赖 `top.yukonga.miuix.kmp:*`（当前 0.9.4）
- **Note:** 绝大部分为 Maven 运行时依赖，非源码嵌入；`ui/miuix/bottombar/liquid/Lens.kt` 自 miuix 官方示例 vendoring，保留 Apache-2.0 文件头

---

## Mishka

- **Copyright:** YuKongA and contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/YuKongA/Mishka
- **Used in:** Miuix 设置页 Lazy 虚拟化架构：`CardSegment` / `groupedCardItems` 分组卡片、`WideContentBox` 宽屏内容居中、`SettingsCardLazyGroup` 卡片拆行、宽屏 `NavigationRail` 与悬浮底栏布局契约、`MiuixScaffold` 子页 LazyColumn 脚手架等；液态玻璃底栏 `ui/miuix/bottombar/liquid/*`（`LiquidGlassNavigationBar` / `Lens` / `InnerShadow` / `CombinedBackdrop` / `Vibrancy`）与 `ui/miuix/bottombar/animation/*`（`DampedDragAnimation` / `InteractiveHighlight`）
- **Modifications:** 包名与常量适配 XGesture（如 `SettingsContentMaxWidth`、导航目的地）；底栏文件改包名并做 4 处本地修改（均标注 `LOCAL PATCH (XGesture)`）：外部 pager 进度驱动胶囊跟手、可配置模糊半径入参、无障碍/键盘路径点击当前页回调、触摸点按当前页回调（后两者用于"回到顶部"）；去除 Mishka 代理客户端业务逻辑
- **Note:** Mishka 自身亦基于 Miuix；XGesture 对 Miuix 另有独立 Maven 依赖与 NOTICES 条目

---

## InstallerX-Revived

- **Copyright:** InstallerX Revived contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/InstallerX-Revived/InstallerX-Revived
- **Used in:** 液态悬浮底栏（`ui/miuix/bottombar/*`）、设置页蓝框提示 `MiuixSettingsTipCard`
- **Modifications:** 包名适配 XGesture；底栏与 Mishka / AndroidLiquidGlass 链路分离标注；`MiuixSettingsTipCard` 仅 vendoring 提示卡片段

---

## EdgeGesture

- **Copyright:** evilgodxu and contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/evilgodxu/EdgeGesture
- **Used in:** 扩展面板（`VolumePanelOverlayWindow`、`VolumePanelContent`、`ExpandPanelSlotPicker`）、N 分钟后闹钟提醒（`RemindAlarmScheduler`、`RemindDurationPickerOverlay`、`RemindAlarmService`）、全局屏幕复制（`UniversalCopyCollector`、`UniversalCopyOverlay`）、屏幕覆盖即时翻译（`ScreenTranslationOverlayManager`）、背面敲击手势（`BackTapDetector`、`BackTapGestureHost`）
- **Modifications:** 以 Kotlin / Jetpack Compose 重构并集成至 XGesture WindowManager 与手势调度架构；接入 Hilt 依赖注入；去除独立 App 业务耦合

---

## EdgeX

- **Copyright:** fan / oxohang and contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/oxohang/EdgeX
- **Used in:** 应用冻结室（Freezer）核心逻辑与 Shizuku / Root 冻结解冻调度（`FreezerBootstrap`、`FreezerOperations`、`FreezerAppsScreen`）
- **Modifications:** 以 Miuix / Compose 风格重写设置与列表界面；集成至 XGesture ExtensionHub 与手势系统

---

## Hail（雹）

- **Copyright:** aistra0528 and contributors
- **License:** [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
- **Source:** https://github.com/aistra0528/Hail
- **Used in:** 应用冻结室的冻结/解冻特权路径（`FreezerPrivilegedOps`：优先 PM API、其次 shell 兜底）与应用挂起（Suspend / 暂停）的实现路径（`setAppSuspended`：按 SDK 的 7/9 参数反射级联、`FLAG_SUSPENDED` 回读判定、`SuspendDialogInfo`、`FreezerBootstrap` 扫描 `FLAG_SUSPENDED`），对齐 Hail 的 `HShell.setAppDisabled` / `HShizuku.setAppDisabled` / `HShizuku.setAppSuspended`
- **Modifications:** 集成至 XGesture 冰箱管线（冰箱主体移植自 EdgeX）；冻结与挂起两条路径均按 Hail 的行为与特权 API 选择实现，未整体引入其模块结构

---

## Material Symbols（Google）

- **Copyright:** Google LLC
- **License:** [Apache License 2.0](app/src/main/assets/licenses/Apache-2.0.txt)
- **Source:** https://github.com/google/material-design-icons
- **Used in:** `app/src/main/res/font/material_symbols_outlined.ttf`（Material Symbols Outlined 可变字体，页面行首图标 `SymbolsIcon` 用）
- **Modifications:** 按本项目实际使用的字形子集化（129 个字形，wght 轴 100–700 保留）；名字→码点表见 `MaterialSymbolsGlyphs`

---

## 许可证全文

- [Apache License 2.0](app/src/main/assets/licenses/Apache-2.0.txt)
- [MIT License](app/src/main/assets/licenses/MIT.txt)
- [GNU General Public License v3.0](app/src/main/assets/licenses/GPL-3.0.txt)
