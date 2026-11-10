package com.slideindex.app.overlay.panels

import android.accessibilityservice.AccessibilityService
import android.content.Context
import com.slideindex.app.settings.AppSettings

/**
 * 面板模块（`:feature:overlay-panels`）与 `:app` 的接线点。
 *
 * 面板需要"执行特权动作 / 读最近任务 / 起小窗"这些 app 核心能力，但模块不能依赖 `:app`。
 * 这里用回调把它们反转：`SlideIndexApp` 启动时注入，未注入时全部降级为"不可用"。
 */
object PanelRuntimeHooks {
    /** 关闭 CNotice 面板（`:app` 的 CNoticeOverlayWindow.closePanel）。 */
    @Volatile
    var closeCNoticePanel: (() -> Unit)? = null

    /** 应用冻结状态。 */
    @Volatile
    var freezerStateOfApp: ((Context, com.slideindex.app.data.AppInfo) -> com.slideindex.app.freezer.FreezerAppState)? = null

    /** 启动应用（必要时先解冻）。 */
    @Volatile
    var launchAppFromSearch: (suspend (Context, com.slideindex.app.data.AppInfo, Boolean) -> Boolean)? = null

    /** 冻结状态判断/切换。 */
    @Volatile
    var isAppFrozen: ((Context, com.slideindex.app.data.AppInfo) -> Boolean)? = null

    /** 见 [isAppFrozen]。 */
    @Volatile
    var setAppFrozen: (suspend (Context, com.slideindex.app.data.AppInfo, Boolean) -> Boolean)? = null

    /** 暂停状态判断/切换。 */
    @Volatile
    var isAppPaused: ((Context, com.slideindex.app.data.AppInfo) -> Boolean)? = null

    /** 见 [isAppPaused]。 */
    @Volatile
    var setAppPaused: (suspend (Context, com.slideindex.app.data.AppInfo, Boolean) -> Boolean)? = null

    /** 预热系统设置索引。 */
    @Volatile
    var ensureSystemSettingsIndex: (suspend (Context) -> Unit)? = null

    /** 预热快捷方式索引。 */
    @Volatile
    var ensureShortcutIndex: (suspend (android.content.Context, List<com.slideindex.app.data.AppInfo>) -> Unit)? = null

    /** 系统设置搜索。 */
    @Volatile
    var searchSystemSettings:
        (suspend (Context, String, Int) -> List<com.slideindex.app.search.settings.SystemSettingsSearchEntry>)? = null

    /** 快捷方式搜索。 */
    @Volatile
    var searchShortcuts:
        (suspend (Context, List<com.slideindex.app.data.AppInfo>, String, Int) ->
            List<com.slideindex.app.search.shortcuts.ShortcutSearchEntry>)? = null

    /** 剪贴板历史搜索。 */
    @Volatile
    var searchClipboardHistory:
        (suspend (String, Int) -> List<com.slideindex.app.clipboard.ClipboardEntry>)? = null

    /** 网络搜索建议。 */
    @Volatile
    var webSuggestions: (suspend (String) -> List<Any>)? = null

    /** 启动一条系统设置项。 */
    @Volatile
    var launchSystemSetting: ((Context, com.slideindex.app.search.settings.SystemSettingsSearchEntry, AppSettings, Boolean) -> Boolean)? = null

    /** 打开/分享本地文件。 */
    @Volatile
    var openFileEntry: ((Context, com.slideindex.app.search.files.DeviceFileEntry) -> Boolean)? = null

    /** 分享本地文件；`onDismiss` 在分享发起后回调。 */
    @Volatile
    var shareFileEntry: ((Context, com.slideindex.app.search.files.DeviceFileEntry, () -> Unit) -> Boolean)? = null

    /** 搜索历史流（每条只是查询词）。 */
    @Volatile
    var searchHistoryQueries: (() -> kotlinx.coroutines.flow.Flow<List<String>>?)? = null

    /** 记录一次搜索查询。 */
    @Volatile
    var recordSearchHistory: ((Context, String) -> Unit)? = null

    /** 打开链接（带长按标记）。 */
    @Volatile
    var openUrlWithLongPress: ((Context, String, AppSettings, Boolean) -> Unit)? = null

    /** 用搜索引擎搜文本（带长按标记）。 */
    @Volatile
    var launchTextSearchWithLongPress: ((Context, Any?, String, AppSettings, Boolean) -> Boolean)? = null

    /** 启动任务/快捷方式条目（`:app` 的 AppShortcutLoader.launchShortcut）。 */
    @Volatile
    var launchTaskShortcut: ((Context, String, Any) -> Unit)? = null

    /** 取应用图标（Compose ImageBitmap；`:app` 的 PickerAppIconBitmap）。 */
    @Volatile
    var pickerIconBitmap: (suspend (Context, String) -> androidx.compose.ui.graphics.ImageBitmap?)? = null

    /** 预览已缓存的应用图标（不触发加载）。 */
    @Volatile
    var peekPickerIconBitmap: ((String) -> androidx.compose.ui.graphics.ImageBitmap?)? = null

    /** 截图前隐藏指针/浮球/边缘手势条（三项，均为同一批 chrome 抑制）。 */
    @Volatile
    var suppressChromeForCapture: (() -> Unit)? = null

    /** 截图后恢复上述 chrome。 */
    @Volatile
    var restoreChromeAfterCapture: (() -> Unit)? = null

    /** 打开文件浏览器浮窗。 */
    @Volatile
    var showFileBrowser: ((Context, AppSettings, String?) -> Unit)? = null

    /** 设置流（`:app` 的 settingsRepository.settings）。 */
    @Volatile
    var settingsFlow: ((Context) -> kotlinx.coroutines.flow.Flow<AppSettings>?)? = null

    /** 宿主 overlay 上下文（`:app` 的 overlayHostContext）。 */
    @Volatile
    var overlayHostContext: (() -> Context?)? = null


    /** 无障碍服务实例（`performGlobalAction` 等要用）。 */
    @Volatile
    var accessibilityService: (() -> AccessibilityService?)? = null

    /** 最近任务标识（`pkg/activity` 形式），拿不到时返回 null。 */
    @Volatile
    var recentTaskIdentifiers: (() -> List<String>?)? = null

    /** 强停应用；返回是否成功。 */
    @Volatile
    var forceStopPackage: ((String) -> Boolean)? = null

    /** 从最近任务列表里移除这些包。 */
    @Volatile
    var removeRecentPackages: ((Collection<String>) -> Unit)? = null

    /** 以小窗启动应用；`onSessionEnd` 在小窗结束时回调。 */
    @Volatile
    var launchFreeWindow: ((Context, String, AppSettings, (() -> Unit)?) -> Unit)? = null

    /** 应用图标覆盖（用户自定义图标）；返回 null 时用系统图标。 */
    @Volatile
    var iconOverrideBitmap: ((Context, String, Int) -> android.graphics.Bitmap?)? = null

    /** 同上，但返回 Drawable（全息启动器的球面图标用）。 */
    @Volatile
    var iconOverrideDrawable: ((Context, String, Int) -> android.graphics.drawable.Drawable?)? = null

    /** 应用仓库（`:feature:apps`）；面板读应用名/图标用。 */
    @Volatile
    var appRepository: ((Context) -> com.slideindex.app.data.AppRepository?)? = null

    /** 分身应用显示名前缀（如「微信 · 分身 2」）。 */
    @Volatile
    var cloneAppLabel: ((Context, Int, String) -> String)? = null

    /** 手势动作图标（`:app` 的 GestureActionIconBitmap）。 */
    @Volatile
    var actionIconBitmap:
        ((com.slideindex.app.gesture.GestureAction, Int, Int) -> android.graphics.Bitmap?)? = null

    /** 手势动作图标（完整参数：动作/尺寸/着色/描边/底板/形状序号）。 */
    @Volatile
    var actionIconBitmapFull:
        ((com.slideindex.app.gesture.GestureAction, Int, Int, Boolean, Boolean, Int) -> android.graphics.Bitmap?)? = null
    /** 手势动作执行器；面板只表达"选了哪个动作"。 */
    @Volatile
    var gestureActionRunner: GestureActionRunner? = null

    /** 槽位图标（`:app` 的 CornerSlotIconBitmap，带应用/快捷方式图标与角标）。 */
    @Volatile
    var cornerSlotIconBitmap:
        ((
            Context,
            com.slideindex.app.gesture.GestureAction,
            Int,
            Int,
            List<com.slideindex.app.activity.ActivityShortcut>,
            List<com.slideindex.app.shell.ShellCommand>,
        ) -> android.graphics.Bitmap?)? = null

    /**
     * 取"模糊后的壁纸"给面板做背景；返回缓存命中时的位图，未命中时异步回调。
     *
     * 参数：无障碍服务、应用 context、模糊半径 dp、回调。
     */
    @Volatile
    var captureBlurredWallpaper:
        ((android.accessibilityservice.AccessibilityService, Context, Int, WallpaperBlurCallback) -> android.graphics.Bitmap?)? =
        null

    /** 应用图标（同步，命中缓存）；未命中返回 null。 */
    @Volatile
    var peekActivityIcon: ((String, String) -> androidx.compose.ui.graphics.ImageBitmap?)? = null

    /** 应用图标（异步加载，供 LaunchedEffect 用）。 */
    @Volatile
    var loadActivityIcon:
        (suspend (Context, String, String) -> androidx.compose.ui.graphics.ImageBitmap?)? = null

    /** 给 Compose 树提供 app 依赖（`:app` 的 LocalAppDependencies）。 */
    @Volatile
    var appDependenciesComposition: AppDependenciesComposition? = null

    /** 注入一次点击（悬浮窗穿透用）。 */
    @Volatile
    var dispatchTapAsync: ((Float, Float, (Boolean) -> Unit) -> Unit)? = null

    /** 角标快捷方式槽位的选择 Activity Intent。 */
    @Volatile
    var cornerSlotPickIntent: ((Context, String, Int) -> android.content.Intent?)? = null

    /** 当前是否应隐藏触发区（锁屏/横屏/桌面/暂停等规则）。 */
    @Volatile
    var suppressTrigger: ((AppSettings, Context, Boolean) -> Boolean)? = null
    /** 环状启动器：打开槽位图标编辑页的 Intent。 */
    @Volatile
    var ringSlotIconEditorIntent:
        ((Context, com.slideindex.app.settings.FvRingLauncherAxis, Int) -> android.content.Intent?)? = null

    /** 环状启动器：创建快捷方式（host 为 `:app` 的 `AppShortcutLoader.CreateShortcutHost`）。 */
    @Volatile
    var createShortcutLauncher: ((Context, Any, () -> Unit, (Any?) -> Unit) -> Unit)? = null
    /** 写回展开面板某槽位的动作。 */
    @Volatile
    var setExpandPanelSlotAction: ((Int, com.slideindex.app.gesture.GestureAction?) -> Unit)? = null

    /** 选择器用的应用列表（全量加载，含冻结/分身）。 */
    @Volatile
    var pickerAppsLoad:
        (suspend (Context, com.slideindex.app.data.AppRepository?, Set<String>) -> List<com.slideindex.app.data.AppInfo>)? =
        null
    /** 展开面板的槽位选择器内容（`:app` 的 ExpandPanelSlotPicker，需 app 侧依赖）。 */
    @Volatile
    var expandPanelSlotPicker: PanelExpandSlotPicker? = null

    /** 屏幕熄灭时注册一次性回调（`:app` 的 ScreenOffDismissReceiver）。返回注销函数。 */
    @Volatile
    var registerScreenOffDismiss: ((Context, () -> Unit) -> (() -> Unit))? = null

    /** 内容面板显示/隐藏时的场景通知。 */
    @Volatile
    var onContentPanelShown: (() -> Unit)? = null

    /** 见 [onContentPanelShown]。 */
    @Volatile
    var onContentPanelHidden: (() -> Unit)? = null

    /** 为面板内容包一层 app 的 CompositionLocal（LocalAppDependencies）。 */
    @Volatile
    var appDependenciesWrapper: PanelComposeWrapper? = null
    /** 内置图片编辑：拉起编辑器并回调结果路径（`:app` 的 StashEditImageTrampolineActivity）。 */
    @Volatile
    var editStashImage: ((Context, String, (String?) -> Unit) -> Unit)? = null

    /** 图片编辑是否有在途任务。 */
    @Volatile
    var isStashImageEditing: (() -> Boolean)? = null

    /** 图片编辑链路日志 tag（与 `:app` 的 trampoline 同一个值）。 */
    @Volatile
    var stashImageEditLogTag: String = "StashImageEdit"
    /** 记住一次查询（搜索面板联动）。 */
    @Volatile
    var rememberSearchQuery: ((Context, String) -> Unit)? = null

    /** 打开链接（内置浏览器/系统浏览器按设置）。 */
    @Volatile
    var openUrl: ((Context, String, AppSettings) -> Unit)? = null
    /** 取图面板的图片搜索是否可见（`:app` 的 FloatBallImageSearchPanel.panelVisible）。 */
    @Volatile
    var imageSearchPanelVisible: (() -> androidx.compose.runtime.State<Boolean>)? = null

    /** 请求把焦点交还取词面板。 */
    @Volatile
    var requestPickResultPanelFocus: (() -> Unit)? = null
    /** 记住取图面板搜索栅格是否展开。 */
    @Volatile
    var setPickSearchGridExpanded: ((Boolean) -> Unit)? = null

    /** 拖拽开始时挂起取词面板（隐藏但不销毁）。 */
    @Volatile
    var suspendPickResultForDrag: (() -> Unit)? = null

    /** 拖拽结束/恢复时恢复取词面板。 */
    @Volatile
    var resumePickResultFromDrag: (() -> Unit)? = null

    /** 关闭取词面板。 */
    @Volatile
    var dismissPickResultPanel: (() -> Unit)? = null
    /** 打开图片搜索面板。 */
    @Volatile
    var showImageSearchPanel: ((Context, android.graphics.Bitmap) -> Unit)? = null

    /** 用搜索引擎打开图片。 */
    @Volatile
    var launchImageShareSearch: ((Context, Any?, android.graphics.Bitmap) -> Boolean)? = null

    /** 用搜索引擎打开文本。 */
    @Volatile
    var launchTextSearch: ((Context, Any?, String, AppSettings) -> Boolean)? = null

    /** 打开搜索面板窗口；返回是否成功。 */
    @Volatile
    var showSearchPanelWindow: ((Context) -> Boolean)? = null

    /** 往搜索面板会话塞一张图（null = 清空）。 */
    @Volatile
    var pushSearchPanelImage: ((android.graphics.Bitmap?) -> Unit)? = null

    /** 从外部入口发起翻译。 */
    @Volatile
    var translateFromExternalEntry: ((Context, String, (String) -> Unit) -> Unit)? = null
    /** 生成可分享的图片 URI（`:app` 的 FloatBallTextPick.createShareImageUri）。 */
    @Volatile
    var createShareImageUri: ((Context, android.graphics.Bitmap) -> android.net.Uri?)? = null

    /** 当前设置仓库（`:app` 的 SettingsRepository；面板需要直接读写时用）。 */
    @Volatile
    var settingsRepository: ((Context) -> com.slideindex.app.settings.SettingsRepository?)? = null

    /** 刷新最近任务列表（异步回调；条目带 taskId / userId，可用于分身）。 */
    @Volatile
    var refreshRecentTasks:
        ((com.slideindex.app.data.AppRepository, (List<com.slideindex.app.util.RecentAppEntry>) -> Unit) -> Unit)? = null

    /** 快捷启动项图标的最终解析（7 参数版，`:app` 的 QuickLauncherIconResolver.iconBitmap）。 */
    @Volatile
    var quickLauncherIconBitmap: (
        (
            com.slideindex.app.launcher.QuickLauncherItem,
            Map<String, com.slideindex.app.data.AppInfo>,
            Int,
            Context,
            List<com.slideindex.app.activity.ActivityShortcut>,
            List<com.slideindex.app.shell.ShellCommand>,
        ) -> android.graphics.Bitmap?
    )? = null

    /** 分身角标装饰（`:app` 的 CloneBadge.decorate）。 */
    @Volatile
    var cloneBadgeDecorate: ((android.graphics.Bitmap, Boolean) -> android.graphics.Bitmap?)? = null

    /** 已缓存的最近任务（每项含 `app: AppInfo`）。 */
    @Volatile
    var cachedRecentTasks: (() -> List<Any>)? = null
    /** 图标底板是否处于深色模式。 */
    @Volatile
    var iconPlateCurrentDark: (() -> Boolean)? = null

    /** 图标底板模式（用于缓存 key）。 */
    @Volatile
    var iconPlateModeKey: (() -> Any?)? = null

    /** 快捷启动项图标解析（`:app` 的 QuickLauncherIconResolver.iconBitmap）。 */
    @Volatile
    var resolveQuickLauncherIcon:
        ((com.slideindex.app.launcher.QuickLauncherItem, Int, Any?) -> android.graphics.Bitmap?)? = null
    /** OCR 模型是否已安装。 */
    @Volatile
    var isOcrModelInstalled: ((Context, String) -> Boolean)? = null

    /** 区域截图 OCR：返回识别结果。 */
    @Volatile
    var recognizeBitmap:
        (suspend (Context, String, android.graphics.Bitmap) -> com.slideindex.app.ocr.OcrRecognizeResult?)? = null

    /** 查看截图（在取词面板里打开）。 */
    @Volatile
    var viewScreenshot: ((Context, android.graphics.Bitmap, String?, android.graphics.Rect?) -> Boolean)? = null
    /** 默认手势执行器（`:app` 的 ActionExecutor），供自带执行逻辑的面板使用。 */
    @Volatile
    var defaultGestureRunner: GestureActionRunner? = null

    /** 全局图标库里某个包的自定义 iconKey（`:app` 的 GlobalIconStore.appIcon）。 */
    var globalIconKey: ((String) -> String?)? = null

    /** 带 iconKey 的动作图标（`:app` 的 ActionIconView，选择器用）。 */
    @Volatile
    var actionIconView: PanelActionIconView? = null

    /** 内置动作图标的 Composable（`:app` 的 ActionIconSlot）。 */
    @Volatile
    var actionIconSlot: PanelActionIconSlot? = null

    /** 当前设置快照里的应用内快捷方式目录。 */
    @Volatile
    var activityShortcutsCatalog: ((Context) -> List<com.slideindex.app.activity.ActivityShortcut>)? = null
    /** 选择器里的应用图标（ImageBitmap）。 */
    @Volatile
    var pickerAppIcon: (suspend (Context, String) -> androidx.compose.ui.graphics.ImageBitmap?)? = null

    /** 选择器里的应用图标（同步缓存命中）。 */
    var pickerAppIconPeek: ((String) -> androidx.compose.ui.graphics.ImageBitmap?)? = null

    /** 选择器里的 Activity 图标（同步缓存命中）。 */
    var pickerActivityIconPeek: ((String, String) -> androidx.compose.ui.graphics.ImageBitmap?)? = null

    /** 选择器里的 Activity 图标（ImageBitmap）。 */
    @Volatile
    var pickerActivityIcon: (suspend (Context, String, String) -> androidx.compose.ui.graphics.ImageBitmap?)? = null

    /** 托管快捷方式（App 内快捷方式）的 Drawable 图标。 */
    @Volatile
    var managedShortcutManagedDrawable:
        ((Context, com.slideindex.app.activity.ActivityShortcut) -> android.graphics.drawable.Drawable?)? = null
    /** 快捷启动器项图标（Drawable）。 */
    @Volatile
    var quickLauncherIconDrawable:
        ((
            com.slideindex.app.launcher.QuickLauncherItem,
            Map<String, com.slideindex.app.data.AppInfo>,
            Context?,
            List<com.slideindex.app.activity.ActivityShortcut>,
            List<com.slideindex.app.shell.ShellCommand>,
        ) -> android.graphics.drawable.Drawable?)? = null

    /** 快捷启动器项显示名。 */
    @Volatile
    var quickLauncherLabel:
        ((Context, com.slideindex.app.launcher.QuickLauncherItem, Map<String, com.slideindex.app.data.AppInfo>) -> String?)? =
        null
        /** 保存一条富内容闪念（`:app` 的 StashCoordinator.addRich）。 */
    var addStashRich:
        ((
            List<com.slideindex.app.stash.StashRichPart>,
            String?,
            String?,
            (Boolean) -> Unit,
            (String) -> Unit,
        ) -> Unit)? = null

    /** 选图（暂存编辑器"＋图片"）：回调收到的路径已去重。 */
    var pickStashComposerImages: ((Context, (List<String>) -> Unit) -> Unit)? = null

    /** 切换输入法（环状启动器快捷动作）。 */
    @Volatile
    var switchInputMethod: ((Context) -> Unit)? = null

    /** 注册全局图标库变更监听。 */
    @Volatile
    var addIconChangeListener: ((() -> Unit) -> Unit)? = null

    /** 注销全局图标库变更监听。 */
    @Volatile
    var removeIconChangeListener: ((() -> Unit) -> Unit)? = null

    /** 图标缓存版本键（全局图标库 revision + 图标底板模式）。 */
    @Volatile
    var iconCacheRevision: (() -> String)? = null
    /** 环状启动器：交换两个槽位。 */
    @Volatile
    var swapRingLauncherSlots: ((com.slideindex.app.settings.FvRingLauncherAxis, Int, Int) -> Unit)? = null
    /** 环状启动器：写回圆形数量。 */
    @Volatile
    var persistRingLauncherCircleCount: ((com.slideindex.app.settings.FvRingLauncherAxis, Int) -> Unit)? = null

    /** 环状启动器：写回整套设置。 */
    @Volatile
    var persistRingLauncherSettings:
        ((com.slideindex.app.settings.FvRingLauncherAxis, com.slideindex.app.settings.FvRingLauncherSettings) -> Unit)? = null

    /** 环状启动器：写回"外观联动轴"设置。 */
    @Volatile
    var persistRingLauncherLinkAppearanceAxes:
        ((
            Boolean,
            com.slideindex.app.settings.FvRingLauncherAxis,
            com.slideindex.app.settings.FvRingLauncherAxisMergeDirection?,
        ) -> Unit)? = null

    /** 环状启动器：写回"槽位联动轴"设置。 */
    @Volatile
    var persistRingLauncherLinkSlotAxes:
        ((
            Boolean,
            com.slideindex.app.settings.FvRingLauncherAxis,
            com.slideindex.app.settings.FvRingLauncherAxisMergeDirection?,
        ) -> Unit)? = null
    /** 环状启动器槽位配置面板内容（`:app` 的 RingLauncherSlotConfigSheet）。 */
    @Volatile
    var ringSlotConfigSheetContent: RingSlotConfigSheetContent? = null
    /** 环状启动器槽位配置用的应用列表（含分身/冻结状态）。 */
    @Volatile
    var pickerApps:
        (suspend (Context, com.slideindex.app.data.AppRepository?, Set<String>) -> List<com.slideindex.app.data.AppInfo>)? =
        null
    /** 持久化环状启动器槽位。 */
    @Volatile
    var persistRingLauncherSlot:
        ((com.slideindex.app.settings.FvRingLauncherAxis, Int, com.slideindex.app.launcher.QuickLauncherItem) -> Unit)? =
        null
    /** 当前设置快照（`:app` 的 settingsRepository.readSnapshot()）。 */
    @Volatile
    var settingsSnapshot: ((Context) -> AppSettings?)? = null

    /** 持久化 shell 命令列表。 */
    @Volatile
    var persistShellCommands: ((List<com.slideindex.app.shell.ShellCommand>) -> Unit)? = null

    /** 托管快捷方式图标位图。 */
    @Volatile
    var managedShortcutBitmap:
        ((
            Context,
            com.slideindex.app.gesture.GestureAction.LaunchShortcut,
            Int,
            List<com.slideindex.app.activity.ActivityShortcut>,
        ) -> android.graphics.Bitmap?)? = null

    /** 托管快捷方式归属包名（用于取应用图标）。 */
    @Volatile
    var managedShortcutHostPackage: ((String) -> String?)? = null
    /** 内置分享面板总开关（`:app` 从设置快照读）。 */
    @Volatile
    var isShareSheetEnabled: ((Context) -> Boolean)? = null

    /** 手势动作的显示名（`:app` 的 `gestureActionLabelText`）。 */
    @Volatile
    var gestureActionLabel: ((Context, com.slideindex.app.gesture.GestureAction) -> String)? = null

    /** 快捷方式动作的显示名（`:app` 的 `launchShortcutDisplayLabel`）。 */
    @Volatile
    var launchShortcutLabel: ((com.slideindex.app.gesture.GestureAction.LaunchShortcut) -> String)? = null

    /** 托管快捷方式图标（Drawable）。 */
    @Volatile
    var managedShortcutDrawable:
        ((
            Context,
            com.slideindex.app.gesture.GestureAction.LaunchShortcut,
            List<com.slideindex.app.activity.ActivityShortcut>,
        ) -> android.graphics.drawable.Drawable?)? = null

    /** 快捷方式面板项图标（Drawable）。 */
    @Volatile
    var managedQuickItemDrawable:
        ((
            Context,
            com.slideindex.app.launcher.QuickLauncherItem,
            List<com.slideindex.app.activity.ActivityShortcut>,
        ) -> android.graphics.drawable.Drawable?)? = null
}

/** 见 [PanelRuntimeHooks.appDependenciesComposition]。 */
fun interface AppDependenciesComposition {
    @androidx.compose.runtime.Composable
    fun provide(content: @androidx.compose.runtime.Composable () -> Unit)
}

/** 见 [PanelRuntimeHooks.captureBlurredWallpaper]。 */
fun interface WallpaperBlurCallback {
    fun onReady(bitmap: android.graphics.Bitmap?)
}

/**
 * 手势动作执行器（`:app` 的 `ActionExecutor.execute` 的窄接口）。
 *
 * 面板只负责"选定了一个动作"，真正执行（启动应用、执行 shell、起小窗…）在 `:app`。
 */
fun interface GestureActionRunner {
    fun run(
        action: com.slideindex.app.gesture.GestureAction,
        settings: AppSettings,
        longPressArmed: Boolean,
        anchorRawX: Float?,
        anchorRawY: Float?,
        panelSide: com.slideindex.app.overlay.PanelSide?,
    )
}

/** 见 [PanelRuntimeHooks.ringSlotConfigSheetContent]。 */
fun interface RingSlotConfigSheetContent {
    @androidx.compose.runtime.Composable
    fun content(
        slotIndex: Int,
        currentItem: com.slideindex.app.launcher.QuickLauncherItem?,
        settings: AppSettings,
        apps: List<com.slideindex.app.data.AppInfo>,
        onDismiss: () -> Unit,
        onSelectItem: (com.slideindex.app.launcher.QuickLauncherItem?) -> Unit,
        onOpenCustomIconEditor: () -> Unit,
        launchCreateShortcut: (Any, (Any?) -> Unit) -> Unit,
    )
}

/** 见 [PanelRuntimeHooks.actionIconSlot]。 */
fun interface PanelActionIconSlot {
    @androidx.compose.runtime.Composable
    fun content(
        action: com.slideindex.app.gesture.GestureAction,
        sizeDp: Int,
    )
}

/** 见 [PanelRuntimeHooks.actionIconView]。 */
fun interface PanelActionIconView {
    @androidx.compose.runtime.Composable
    fun content(
        action: com.slideindex.app.gesture.GestureAction,
        iconKey: String?,
        sizeDp: Int,
        showBackground: Boolean,
    )
}

/** 见 [PanelRuntimeHooks.expandPanelSlotPicker]。 */
fun interface PanelExpandSlotPicker {
    @androidx.compose.runtime.Composable
    fun content(
        onDismiss: () -> Unit,
        onPicked: (com.slideindex.app.gesture.GestureAction?) -> Unit,
    )
}

/** 见 [PanelRuntimeHooks.appDependenciesWrapper]。 */
fun interface PanelComposeWrapper {
    @androidx.compose.runtime.Composable
    fun content(inner: @androidx.compose.runtime.Composable () -> Unit)
}
