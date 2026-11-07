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