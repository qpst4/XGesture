# 开源归属映射（开发维护用）

> ⚠️ 改 `THIRD_PARTY_NOTICES.md` 时记得**同步** `app/src/main/assets/licenses/third_party_notices.md`
> —— App 内「开源许可」页读的是**后者**（打进 APK 那份），只改仓库根那份等于没改。
> 改完运行 `python scripts/check-notices-sync.py` 校验两份逐行一致。
> （这条提示刻意放在这里：两份 notices 都是**用户可见**文档，不该出现开发指令。）

## GestureEVO 文件对照表（跟上游同步时用）

来源：<https://github.com/LumiaGG/GestureEVO>（GPL-3.0-only）→ 其 `app/src/main/java/io/github/lumiagg/gestureevo/`。
这些文件里都已带来源头注释；改上游时照这张表定位，**不必重新考古**。

| 本仓库文件 | GestureEVO 源文件 |
|---|---|
| `app/.../inspire/AccessibilityNodeManager.kt` | `inspire/AccessibilityNodeManager.kt` |
| `app/.../inspire/BaseFloatingWindow.kt` | `floating/BaseFloatingWindow.kt` |
| `app/.../inspire/InspireDataHolder.kt` | `inspire/InspireDataHolder.kt` |
| `app/.../inspire/InspireFloating.kt` | `inspire/InspireFloating.kt` |
| `app/.../inspire/ManagedBitmap.kt` | `inspire/ManagedBitmap.kt` |
| `app/.../inspire/ui/InspireView.kt` | `inspire/ui/InspireView.kt` |
| `app/.../inspire/ui/InspireViewCallback.kt` | `inspire/ui/InspireView.kt`（该文件内联的 interface `InspireViewCallBack`） |
| `app/.../inspire/ScreenContentNode.kt` | `inspire/AccessibilityNodeManager.kt`（该文件内联的 data class） |
| `app/.../inspire/InspireCoordinator.kt` | `inspire/InspireFloating.kt`（仅沿用 `processScreenContent` 并行取词骨架） |
| `app/.../imageeditor/SlideIndexImageEditorActivity.kt` | `inspire/imageeditor/InspireImageEditorActivity.kt` |
| `app/.../imageeditor/model/EditAction.kt` | `inspire/imageeditor/model/EditAction.kt` |
| `app/.../imageeditor/model/EditorMode.kt` | `inspire/imageeditor/model/EditorMode.kt` |
| `app/.../imageeditor/state/EditorSession.kt` | `inspire/imageeditor/state/EditorSession.kt` |
| `app/.../imageeditor/ui/BrushSizePreviewView.kt` | `inspire/imageeditor/ui/BrushSizePreviewView.kt` |
| `app/.../imageeditor/ui/ShapeTypePreviewView.kt` | `inspire/imageeditor/ui/ShapeTypePreviewView.kt` |
| `app/.../imageeditor/ui/ImageEditorView.java` | `inspire/imageeditor/ui/InspireImageEditorView.kt` |
| `app/.../imageeditor/utils/EditorExportEngine.kt` | `inspire/imageeditor/utils/EditorExportEngine.kt` |
| `app/.../imageeditor/utils/EditorRenderUtils.kt` | `inspire/imageeditor/utils/EditorRenderUtils.kt` |
| `app/.../screensearch/ScreenSearchFloating.kt` | `screen_search/ScreenSearchFloating.kt` |
| `app/.../screensearch/ScreenSearchHighlightView.kt` | `screen_search/ScreenSearchHighlightView.kt` |
| `app/.../screensearch/ScreenSearchCapture.kt` | `screen_search/ScreenSearchFloating.kt`（沿用 `buildRevealCropRect`） |
| `app/.../timeddnd/TempDndManager.kt` | `utils/TempDndRestoreWorker.kt`（同名 object） |
| `app/.../timeddnd/TempDndRestoreWorker.kt` | `utils/TempDndRestoreWorker.kt` |
| `app/.../timeddnd/TempDndNotificationReceiver.kt` | `utils/TempDndRestoreWorker.kt` |
| `app/.../timeddnd/TempDndLimits.kt` | `utils/TempDndRestoreWorker.kt`（`DEFAULT_MINUTES` / `MAX_MINUTES` 时长范围） |
| `app/.../dragpress/BackgroundTouchBlocker.kt` | `drop/BackgroundTouchBlocker.kt` |
| `app/.../dragpress/ContentDragTarget.kt` | `drop/ContentDragTarget.kt` |
| `app/.../dragpress/DragContentPreview.kt` | `drop/DragContentPreview.kt` |
| `app/.../dragpress/DragContentPreviewPolicy.kt` | `drop/DragContentPreviewPolicy.kt` |
| `app/.../dragpress/EvdevTouchParser.java` | `drop/EvdevTouchParser.java` |
| `app/.../dragpress/GlobalPressDragSource.kt` | `drop/GlobalPressDragSource.kt` |
| `app/.../dragpress/PressContentCapture.kt` | `drop/PressContentCapture.kt` |
| `app/.../dragpress/PressTriggerDetector.java` | `drop/PressTriggerDetector.java` |
| `app/.../dragpress/ProcessCommandRunner.kt` | `utils/process/ProcessCommandRunner.kt` |
| `app/.../dragpress/ProcessOutputLines.kt` | `utils/process/ProcessOutputLines.kt` |
| `app/.../dragpress/ProcessStreamRelay.kt` | `utils/process/ProcessStreamRelay.kt` |
| `app/.../dragpress/ShizukuTouchConnection.kt` | `drop/ShizukuTouchConnection.kt` |
| `app/.../dragpress/ShizukuTouchInterrupter.kt` | `drop/ShizukuTouchInterrupter.kt` |
| `app/.../dragpress/TouchInputProtocol.kt` | `drop/TouchInputProtocol.kt` |
| `app/.../dragpress/TouchInputSource.kt` | `drop/TouchInputSource.kt` |
| `app/.../dragpress/TouchInterruptionSequence.kt` | `drop/TouchInterruptionSequence.kt` |
| `app/.../dragpress/TouchStreamHost.kt` | `drop/TouchStreamHost.kt` |
| `app/.../dragpress/TouchStreamProcess.kt` | `drop/TouchStreamProcess.kt` |

| 本仓库路径 | 来源项目 | 关系 | 许可证 |
|-----------|---------|------|--------|
| `app/.../xposed/hook/ClipboardWhitelistHook.kt` | [Clipboard Whitelist](https://github.com/Tehcneko/ClipboardWhitelist) | 移植+修改 | GPL-3.0 |
| `app/.../clipboard/ClipboardWhitelistBridge.kt` | 同上 | 衍生 | GPL-3.0 |
| `app/.../clipboard/ClipboardWhitelistContract.kt` | 同上 | 衍生 | GPL-3.0 |
| `app/.../overlay/animation/GestureAnimationState.kt` | [SideGesture](https://github.com/aaronzzx/gulugulu) | 移植 | Apache-2.0 |
| `app/.../overlay/animation/GestureAnimation.kt` | 同上 | 移植 | Apache-2.0 |
| `app/.../overlay/animation/GestureAnimationTriggerDirection.kt` | 同上 | 移植 | Apache-2.0 |
| `app/.../overlay/animation/GestureAnimationButton.kt` | 同上 | 移植 | Apache-2.0 |
| `app/.../overlay/animation/GestureAnimationOverlay.kt` | 同上 | 改编 | Apache-2.0 |
| `app/.../util/ShortcutUtils.kt` | 同上 | 移植 | Apache-2.0 |
| `app/.../util/LauncherShortcutInfo.kt` | 同上 | 移植 | Apache-2.0 |
| `app/.../util/PackageManagerCompat.kt` | 同上 | 移植 | Apache-2.0 |
| `app/.../overlay/OverlayWindowTypes.kt` | 同上 | 改编 | Apache-2.0 |
| `app/.../overlay/EdgeOverlayHost.kt` | 同上 | 改编 | Apache-2.0 |
| `app/.../overlay/OverlayScreenMetrics.kt` | 同上 | 改编 | Apache-2.0 |
| `app/.../gesture/GestureZoneLayout.kt`（exclusion / TOP 几何） | [SideGesture](https://github.com/aaronzzx/SideGesture) | 改编 | Apache-2.0 |
| `app/.../overlay/EdgeSystemGestureExclusionView.kt` | 同上 | 改编 | Apache-2.0 |
| `app/.../search/ImageHostUploader.kt` | [Circle To Search](https://github.com/AKS-Labs/CircleToSearch) | 改编 | GPL-3.0 |
| `app/.../search/ImageSearchUrlBuilder.kt` | 同上 | 改编 | GPL-3.0 |
| `app/.../search/ImageSearchBitmapUtils.kt` | 同上 | 移植 | GPL-3.0 |
| `app/.../search/ImageSearchEngine.kt` | 同上 | 改编 | GPL-3.0 |
| `app/.../overlay/FloatBallImageSearchPanel.kt` | 同上 | 思路+架构参考 | GPL-3.0 |
| `app/.../overlay/FloatBallOverlay.kt` 等悬浮球取词 | [Nova Text](https://github.com/CashewTeam/BigBang_NovaText) | 交互思路 | GPL-3.0 |
| `app/.../inspire/*`（悬浮球取词管线，10 个文件） | [GestureEVO](https://github.com/LumiaGG/GestureEVO) | 移植+改写（`InspireView`/`InspireFloating`/`InspireDataHolder`/`ManagedBitmap`/`AccessibilityNodeManager`/`BaseFloatingWindow` 等同名类；10 个文件的逐文件对应见上方 GestureEVO 对照表——其中 `InspireCoordinator.kt` 仅沿用 `processScreenContent` 并行取词骨架、`PickPrefetchCache.kt` 为本项目自研，其余 8 个为移植或派生） | GPL-3.0 |
| `app/.../screensearch/*`（屏内搜索，6 个文件） | 同上 | 移植+改写（`ScreenSearchFloating`/`ScreenSearchHighlightView`/`ScreenSearchCapture` 有同名类或方法；搜索主循环与 `MAX_ATTEMPTS`/`MAX_CAPTURE_FAILURES`/`PRE_START_DELAY_MS`/`REVEAL_SCAN_RATIO` 等常量同源，本仓库另做了全屏遍历、滚动停稳判定与触底检测）；`ScreenSearchIndicatorCard`/`ScreenSearchPanelCard`/`ScreenSearchPanelState` **无对应，为本项目自研** | GPL-3.0 |
| `app/.../service/SlideIndexAccessibilityGestureInjector.kt`（滚动观感） | 同上 | 行为对齐 | GPL-3.0 |
| `app/.../imageeditor/*`（图片编辑器，26 个文件） | 同上 | **移植+改写**（`model/{EditAction,EditorMode}`、`state/EditorSession`、`ui/{BrushSizePreviewView,ShapeTypePreviewView,ImageEditorView}`、`utils/{EditorExportEngine,EditorRenderUtils}` 对应其 `inspire/imageeditor/*`；`ImageEditorView` 与其 `InspireImageEditorView` **71 个同名方法**，`SlideIndexImageEditorActivity` 与其 Activity **18 个同名方法**。其余（快捷工具、素材取色、钉图、共享拖拽、序号标注等）为本项目自研） | GPL-3.0 |
| `app/.../timeddnd/*`（临时免打扰，6 个文件） | 同上 | **移植+改写**（`TempDndManager` 对应其 `utils/TempDndRestoreWorker.kt` 内的同名 object：`hasUsableDndPolicyAccess` 签名一致，`enableTempDnd`/`restoreDnd`/`scheduleRestore`/`showCountdownNotification`/`createNotificationChannelIfNeeded` 同名；本仓库拆为 6 个文件，新增 `cancelActive`、规则记录，并把时长范围独立为 `TempDndLimits`（1..1440 分钟，与其取值一致；原先借用提醒模块的 1..120）） | GPL-3.0 |
| `app/.../dragpress/*`（全局长按强行拖放源端，18 个文件） | [GestureEVO](https://github.com/LumiaGG/GestureEVO) | **移植+改写**（`GlobalPressDragSource`/`TouchInputSource`/`TouchStreamHost`/`ShizukuTouchInterrupter`/`ShizukuTouchConnection`/`PressContentCapture`/`DragContentPreview`/`PressTriggerDetector`/`EvdevTouchParser` 等与 `drop/` 同名类一一对应，`ProcessCommandRunner`/`ProcessOutputLines`/`ProcessStreamRelay` 对应 `utils/process/`；三个 Shizuku 触摸方法的 AIDL 签名与其 `IUserService` 一致，`ITouchInterruptionGuard` 除包名外逐字相同；设置改由 lambda 注入，目标端改接本项目流转栏 `DragDropFlowController`；`EvdevTouchParser`/`PressTriggerDetector` 上游另标注转写自 MMGG） | GPL-3.0 |
| `app/.../barcode/QrPayloadParser.kt` | [QuickDash](https://github.com/balajitechlabs/quickdash) | 行为规格参考（无代码移植） | GPL-3.0（仓库自述） |
| `app/.../barcode/QrCodeGenerator.kt` | 同上 | 行为规格参考（无代码移植） | 同上 |
| `app/.../quickmenu/UnitConversionEngine.kt` | 同上 | 行为规格参考（无代码移植） | 同上 |
| `app/.../quickmenu/CurrencyRateEngine.kt` | 同上 | 行为规格参考（无代码移植） | 同上 |
| `app/.../quickmenu/PasswordGenerator.kt` | 同上 | 行为规格参考（无代码移植） | 同上 |
| `app/.../quickmenu/StopwatchLogic.kt` | 同上 | 行为规格参考（无代码移植） | 同上 |
| `app/.../quickmenu/CountdownLogic.kt` | 同上 | 行为规格参考（无代码移植） | 同上 |
| `app/.../quickmenu/TextToolkit.kt` | 同上 | 行为规格参考（无代码移植） | 同上 |
| `app/.../overlay/quickmenu/QuickMenuScanActivity.kt` | 同上 | 交互对齐（取景框/手电筒/放大/对焦） | 同上 |
| `app/.../overlay/quickmenu/QuickMenuToolScreens.kt` | 同上 | 交互对齐（扫码页布局、密码页动作摆位） | 同上 |
| `app/.../xposed/hook/SmsHandlerHook.kt` | [XposedSmsCode](https://github.com/tianma8023/XposedSmsCode) | 改编 | GPL-3.0 |
| `app/.../xposed/hook/SmsProviderHook.kt` | 同上 | 补充（Telephony Provider 捕获） | GPL-3.0 |
| `app/.../xposed/hook/SmsCaptureForwarder.kt` | 同上 | 衍生 | GPL-3.0 |
| `app/.../xposed/hook/PermissionGranterHook.kt` | 同上 | 改编 | GPL-3.0 |
| `app/.../xposed/hook/SystemInputInjectorHook.kt` | 同上 | 改编（`InputHelper`） | GPL-3.0 |
| `app/.../receiver/OtpSmsBridgeReceiver.kt` | 同上 | 架构参考 | GPL-3.0 |
| `core/common/.../otp/VerificationCodeExtractor.kt` | 同上 | 改编（`SmsCodeUtils`） | GPL-3.0 |
| `core/common/.../otp/OtpKeywords.kt` | 同上 | 参考 | GPL-3.0 |
| `core/common/.../otp/OtpRulesParser.kt` | 同上 | 格式兼容（`RuleImporter` v1） | GPL-3.0 |
| `feature/otp/.../OtpOfficialRulesLoader.kt` | 同上 | 衍生 | GPL-3.0 |
| `feature/otp/src/main/assets/smscode-rules.json` | 同上 | 官方规则数据（smscode-rules 格式） | GPL-3.0 |
| `app/.../stash/*` | — | 本项目自有 | AGPL-3.0 |
| `app/.../clipboard/monitor/*` | [ClipboardListener](https://github.com/aa2013/ClipboardListener) | 移植+改编 | MIT |
| `app/src/main/aidl/.../monitor/IClipboardListenerService.aidl` | 同上 | 移植 | MIT |
| `app/src/main/aidl/.../monitor/IOnClipboardChanged.aidl` | 同上 | 移植 | MIT |
| `app/src/main/assets/listener.zip` | 同上 | 衍生资产（监听 DEX） | MIT |
| `app/.../clipboard/ClipboardFocusReader.kt` | [ClipShare](https://github.com/aa2013/ClipShare) | 改编 | GPL-3.0 |
| `app/.../search/ral/*` | [Root Activity Launcher](https://github.com/zacharee/RootActivityLauncher) | 移植+改编 | GPL-3.0 |
| `app/.../search/NonExportedActivityLauncher.kt` | 同上 | 衍生 | GPL-3.0 |
| `app/.../activity/ActivityShortcutLauncher.kt`（未导出 Activity 分支） | 同上 | 集成调用 | GPL-3.0 |
| `app/.../search/SearchEngineLauncher.kt`（未导出 Activity 分支） | 同上 | 集成调用 | GPL-3.0 |
| `app/.../overlay/HoneycombGeometry.java` | [FanFreeform](https://github.com/oxohang/FanFreeform) | 移植+扩展 | GPL-3.0 |
| `app/.../overlay/HoneycombOverlayView.java` | 同上 | 移植+修改 | GPL-3.0 |
| `app/.../overlay/HoneycombOverlayController.java` | 同上 | 移植+修改 | GPL-3.0 |
| `app/.../overlay/BlurredWallpaperCache.kt` | 同上 | 改编（截图捕获路径） | GPL-3.0 |
| `app/.../overlay/HoneycombRuntimeTarget.kt` | 同上 | 改编（`RuntimeTarget`） | GPL-3.0 |
| `app/.../overlay/HoneycombAppPickerOverlayWindow.kt` | 同上 | 改编 | GPL-3.0 |
| `app/.../overlay/HoneycombCorner.kt` | 同上 | 移植（`GestureGeometry.Corner`） | GPL-3.0 |
| `app/.../ui/HoneycombLayoutEditorScreen.kt` | 同上 | 部分移植（布局编辑触摸） | GPL-3.0 |
| `feature/settings/.../HoneycombDisplaySettings.kt` | 同上 | 默认值对齐 `ConfigContract` | GPL-3.0 |
| `app/.../overlay/HoneycombDisplayConfig.kt` | 同上 | 衍生（设置映射） | GPL-3.0 |
| `app/.../overlay/HoneycombTargetResolver.kt` | 同上 | 衍生（`QuickLauncher` 解析） | GPL-3.0 |
| `app/.../search/files/FileSearchIndex.kt` | [Quick Search](https://github.com/teja2495/quick-search) | 移植（`FileSearchRepository` 核心） | MIT |
| `app/.../search/files/FileSearchFilter.kt` | 同上 | 移植 | MIT |
| `app/.../search/files/FileType.kt` | 同上 | 移植（`search/models/FileType`） | MIT |
| `app/.../search/files/FileClassifier.kt` | 同上 | 移植（`search/utils/FileClassifier`） | MIT |
| `app/.../search/files/FolderPathPatternMatcher.kt` | 同上 | 移植 | MIT |
| `app/.../search/files/DeviceFileEntry.kt` | 同上 | 衍生（`DeviceFile` 模型） | MIT |
| `app/.../search/calculator/CalculatorUtils.kt` | 同上 | 移植 | MIT |
| `app/.../search/websuggestions/WebSuggestionsUtils.kt` | 同上 | 移植 | MIT |
| `app/.../search/contacts/ContactSearchLauncher.kt` | 同上 | 行为对齐（`Intent.ACTION_VIEW`） | MIT |
| `app/.../search/contacts/ContactSearchIndex.kt` | 同上 | 思路参考（联系人索引） | MIT |
| `app/.../overlay/searchpanel/SearchPanelAlias.kt` | 同上 | 行为规格参考（前缀别名匹配：first token + 空格） | MIT |
| `app/.../overlay/SystemWallpaperBlurHelper.kt` | 同上 | 行为规格参考（壁纸取图顺序 drawable→fastDrawable→file） | MIT |
| `app/.../ui/settings/components/SettingsRows.kt`（`subtitleContent`） | 同上 | 交互参考（别名 pill 的放置） | MIT |
| Gradle `top.yukonga.miuix.kmp:*` | [Miuix](https://github.com/compose-miuix-ui/miuix) | Maven 依赖 | Apache-2.0 |
| `app/.../ui/miuix/bottombar/liquid/Lens.kt` | 同上（官方示例） | vendoring | Apache-2.0 |
| `app/.../ui/miuix/GroupedCardItems.kt` | [Mishka](https://github.com/YuKongA/Mishka) | 移植+修改 | GPL-3.0 |
| `app/.../ui/miuix/WindowSize.kt` | 同上 | 改编（`WideContentBox`） | GPL-3.0 |
| `app/.../ui/settings/components/SettingsCardLazyGroup.kt` | 同上 | 改编（`groupedCardItems` 集成） | GPL-3.0 |
| `app/.../ui/miuix/MiuixScaffold.kt` | 同上 | 架构参考（Lazy 设置脚手架） | GPL-3.0 |
| `app/.../ui/MainMiuixNavigationRail.kt` | 同上 | 架构参考 | GPL-3.0 |
| `app/.../ui/MainMiuixFloatingNavBar.kt` | 同上 | 架构参考 | GPL-3.0 |
| `app/.../ui/M3eSettingsUi.kt` | 同上 | 架构参考 | GPL-3.0 |
| `app/.../overlay/volumepanel/*`（扩展面板） | [EdgeGesture](https://github.com/evilgodxu/EdgeGesture) | 移植+重构 | GPL-3.0 |
| `app/.../remind/*`（N分钟后闹钟提醒） | 同上 | 移植+重构 | GPL-3.0 |
| `app/.../copy/UniversalCopy*`（全局复制） | 同上 | 移植+重构 | GPL-3.0 |
| `app/.../translate/overlay/ScreenTranslationOverlayManager.kt` | 同上 | 改编 | GPL-3.0 |
| `app/.../backtap/BackTap*`（背面敲击手势） | 同上 | 移植+重构 | GPL-3.0 |
| `app/.../freezer/*`（应用冻结室） | [EdgeX](https://github.com/oxohang/EdgeX) | 移植+重构 | GPL-3.0 |
| `app/.../freezer/FreezerPrivilegedOps.kt` | [Hail](https://github.com/aistra0528/Hail) | 行为对齐（PM API/shell 兜底、挂起 7/9 参反射级联 + `FLAG_SUSPENDED` 回读） | GPL-3.0 |
| `feature/apps/.../FreezerAppsScreen.kt` | 同上 | 界面重写 | GPL-3.0 |
| `app/src/main/jniLibs/*/libevdev_manager.so` | [Key Mapper](https://github.com/keymapperorg/KeyMapper) | 预编译二进制复用（ARMv7/ARMv8 原生 evdev grab 引擎） | GPL-3.0 |
| `app/.../io/github/sds100/keymapper/sysbridge/service/SystemBridge.kt` | 同上 | 移植（JNI 桥接契约声明与回调分发） | GPL-3.0 |
| `app/.../io/github/sds100/keymapper/common/models/*`（3 个模型文件） | 同上 | 移植（`EvdevDeviceInfo`/`GrabTargetKeyCode`/`GrabbedDeviceHandle`） | GPL-3.0 |
| `app/.../physicalkey/EvdevPhysicalKeyObserver.kt` | 同上 | 衍生/适配（`EVIOCGRAB` + input 节点扫描与手势事件转换） | GPL-3.0 |
| `app/.../physicalkey/PhysicalKeyFilterBridge.kt` | 同上 | 衍生/适配（Root/Shizuku 特权拦截与 AIDL IPC 跨进程桥接） | GPL-3.0 |
| `app/.../physicalkey/PhysicalKeyGestureRecognizer.kt` | 同上 | 行为参考（多击/长按消歧与动态 0 延迟即刻放行） | GPL-3.0 |
| `app/.../search/settings/SystemSettingsSearch*.kt` | — | 本项目自有（Android `indexables/raw`） | AGPL-3.0 |

更新借鉴代码时请同步修改 `THIRD_PARTY_NOTICES.md` 与本表。

