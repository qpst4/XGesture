package com.slideindex.app.ui.navigation

import top.yukonga.miuix.kmp.nav.core.NavKey
import com.slideindex.app.ui.MainBottomNavDestination
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppNavKey : NavKey {
    // Home tab
    @Serializable data object HomeMain : AppNavKey
    @Serializable data object HomeInteractionAppearance : AppNavKey
    @Serializable data object HomeAppKeepAlive : AppNavKey
    @Serializable data object HomePrivilegeMode : AppNavKey
    @Serializable data object HomeLayout : AppNavKey
    @Serializable data object HomeHiddenApps : AppNavKey
    @Serializable data object HomePreviousAppBlacklist : AppNavKey
    @Serializable data object HomePreviousAppBlacklistPick : AppNavKey
    @Serializable data object HomeExcludedApps : AppNavKey
    @Serializable data object HomeExcludedAppsPick : AppNavKey
    @Serializable data object HomeFreeWindow : AppNavKey
    @Serializable data object HomeFreeWindowLaunchPolicy : AppNavKey
    @Serializable data object HomeFreeWindowMode : AppNavKey
    @Serializable data object HomeTriggerCollection : AppNavKey
    @Serializable data object HomeTriggerCollectionLandscape : AppNavKey
    @Serializable data class HomeSideGestures(val side: String, val handleId: String) : AppNavKey
    @Serializable data class HomeSideGesturesDefaultMode(val side: String, val handleId: String) : AppNavKey
    @Serializable data class HomeSideGestureSlotConfig(
        val side: String,
        val handleId: String,
        val triggerId: Int,
    ) : AppNavKey
    @Serializable data class HomeSideGestureSlotActionPick(
        val side: String,
        val handleId: String,
        val triggerId: Int,
    ) : AppNavKey
    @Serializable data class HomeSideGestureSlotMyShortcuts(
        val side: String,
        val handleId: String,
        val triggerId: Int,
    ) : AppNavKey
    @Serializable data class HomeSideGestureSlotPresetShortcuts(
        val side: String,
        val handleId: String,
        val triggerId: Int,
    ) : AppNavKey
    @Serializable data class HomeSideGestureSlotPickApp(
        val side: String,
        val handleId: String,
        val triggerId: Int,
    ) : AppNavKey
    @Serializable data class HomeSideGestureSlotPickActivity(
        val side: String,
        val handleId: String,
        val triggerId: Int,
        val packageName: String,
    ) : AppNavKey
    @Serializable data class HomeSideGestureSlotModePick(
        val side: String,
        val handleId: String,
        val triggerId: Int,
    ) : AppNavKey
    @Serializable data class HomeSideGestureSlotQuickLauncherPanel(
        val side: String,
        val handleId: String,
        val triggerId: Int,
        val panelId: String = "",
    ) : AppNavKey
    @Serializable data class HomeSideGestureSlotShellCommand(
        val side: String,
        val handleId: String,
        val triggerId: Int,
        val initialCommand: String = "",
    ) : AppNavKey
    @Serializable data class HomeSideGestureFingertipRing(
        val side: String,
        val handleId: String,
        val triggerId: Int,
    ) : AppNavKey
    @Serializable data class HomeSideGestureFingertipRingSlotActionPick(
        val side: String,
        val handleId: String,
        val triggerId: Int,
        val slotIndex: Int,
    ) : AppNavKey
    @Serializable data class HomeSideGestureFingertipRingShellCommand(
        val side: String,
        val handleId: String,
        val triggerId: Int,
        val slotIndex: Int,
        val initialCommand: String = "",
    ) : AppNavKey
    @Serializable data class HomeSideGestureFingertipRingSwipeConfig(
        val side: String,
        val handleId: String,
        val triggerId: Int,
        val slotIndex: Int,
    ) : AppNavKey
    @Serializable data class HomeSideGestureSlotSimulateKeyEvent(
        val side: String,
        val handleId: String,
        val triggerId: Int,
        val initialKeyCode: Int = 82,
        val initialKeyName: String = "",
        val initialIsLongPress: Boolean = false,
    ) : AppNavKey
    @Serializable data class HomeSideGesturesAppearance(val side: String, val handleId: String) : AppNavKey
    @Serializable data class HomeSideGesturesDesign(val side: String, val handleId: String) : AppNavKey
    @Serializable data object HomeGestureAngle : AppNavKey
    @Serializable data object HomeSystemBackGestureWidth : AppNavKey
    @Serializable data object HomeAnimationStyleSelect : AppNavKey
    @Serializable data object HomeWaveAnimationStyle : AppNavKey
    @Serializable data object HomeCapsuleAnimationStyle : AppNavKey
    @Serializable data object HomeBubbleAnimationStyle : AppNavKey
    @Serializable data object HomeAndroidAnimationStyle : AppNavKey

    @Serializable data object HomeCornerGesture : AppNavKey
    @Serializable data object HomeCornerGestureInteraction : AppNavKey
    @Serializable data object HomeCornerGestureSlots : AppNavKey
    @Serializable data class HomeCornerGestureSlotEditor(
        val corner: String,
        val slotIndex: Int,
    ) : AppNavKey
    @Serializable data class HomeCornerGestureSlotActionPick(
        val corner: String,
        val slotIndex: Int,
    ) : AppNavKey
    @Serializable data class HomeCornerGestureSlotMyShortcuts(
        val corner: String,
        val slotIndex: Int,
    ) : AppNavKey
    @Serializable data class HomeCornerGestureSlotPresetShortcuts(
        val corner: String,
        val slotIndex: Int,
    ) : AppNavKey
    @Serializable data class HomeCornerGestureSlotPickApp(
        val corner: String,
        val slotIndex: Int,
    ) : AppNavKey
    @Serializable data class HomeCornerGestureSlotPickActivity(
        val corner: String,
        val slotIndex: Int,
        val packageName: String,
    ) : AppNavKey
    @Serializable data class HomeCornerGestureSlotShellCommand(
        val corner: String,
        val slotIndex: Int,
        val initialCommand: String = "",
    ) : AppNavKey
    @Serializable data class HomeCornerGestureSlotSimulateKeyEvent(
        val corner: String,
        val slotIndex: Int,
        val initialKeyCode: Int = 82,
        val initialKeyName: String = "",
        val initialIsLongPress: Boolean = false,
    ) : AppNavKey
    @Serializable data class HomeCornerGestureSlotSubMenuPick(
        val corner: String,
        val slotIndex: Int,
    ) : AppNavKey
    @Serializable data class HomeCornerGestureSlotSubMenuMyShortcuts(
        val corner: String,
        val slotIndex: Int,
    ) : AppNavKey
    @Serializable data class HomeCornerGestureSlotSubMenuPresetShortcuts(
        val corner: String,
        val slotIndex: Int,
    ) : AppNavKey
    @Serializable data class HomeCornerGestureSlotSubMenuPickApp(
        val corner: String,
        val slotIndex: Int,
    ) : AppNavKey
    @Serializable data class HomeCornerGestureSlotSubMenuPickActivity(
        val corner: String,
        val slotIndex: Int,
        val packageName: String,
    ) : AppNavKey

    @Serializable data object HomeCornerGestureInnerZoneActionPick : AppNavKey
    @Serializable data object HomeCornerGestureInnerZoneMyShortcuts : AppNavKey
    @Serializable data object HomeCornerGestureInnerZonePresetShortcuts : AppNavKey
    @Serializable data object HomeCornerGestureInnerZonePickApp : AppNavKey
    @Serializable data class HomeCornerGestureInnerZonePickActivity(val packageName: String) : AppNavKey
    @Serializable data class HomeCornerGestureInnerZoneShellCommand(val initialCommand: String = "") : AppNavKey
    @Serializable data class HomeCornerGestureInnerZoneSimulateKeyEvent(
        val initialKeyCode: Int = 82,
        val initialKeyName: String = "",
        val initialIsLongPress: Boolean = false,
    ) : AppNavKey

    // Shake tab
    @Serializable data object ShakeGestures : AppNavKey
    @Serializable data object ShakeGestureBlacklist : AppNavKey
    @Serializable data object ShakeGestureBlacklistPick : AppNavKey
    @Serializable data object ShakeLockScreenSettings : AppNavKey
    @Serializable data object ShakeFaceDownSettings : AppNavKey
    @Serializable data object ShakeIndependentSensitivity : AppNavKey
    @Serializable data object ShakeIndependentAppSettings : AppNavKey
    @Serializable data object ShakeIndependentAppPick : AppNavKey
    @Serializable data class ShakePerAppActions(val packageName: String) : AppNavKey
    @Serializable data class ShakeGestureActionPick(
        val target: ShakeActionPickTarget,
        val gestureTypeId: Int,
        val packageName: String = "",
    ) : AppNavKey
    @Serializable data class ShakeGestureActionMyShortcuts(
        val target: ShakeActionPickTarget,
        val gestureTypeId: Int,
        val packageName: String = "",
    ) : AppNavKey
    @Serializable data class ShakeGestureActionPresetShortcuts(
        val target: ShakeActionPickTarget,
        val gestureTypeId: Int,
        val packageName: String = "",
    ) : AppNavKey
    @Serializable data class ShakeGestureActionPickApp(
        val target: ShakeActionPickTarget,
        val gestureTypeId: Int,
        val packageName: String = "",
    ) : AppNavKey
    @Serializable data class ShakeGestureActionPickActivity(
        val target: ShakeActionPickTarget,
        val gestureTypeId: Int,
        val packageName: String = "",
        val appPackageName: String,
    ) : AppNavKey
    @Serializable data class ShakeGestureActionShellCommand(
        val target: ShakeActionPickTarget,
        val gestureTypeId: Int,
        val packageName: String = "",
        val initialCommand: String = "",
    ) : AppNavKey
    @Serializable data class ShakeGestureActionSimulateKeyEvent(
        val target: ShakeActionPickTarget,
        val gestureTypeId: Int,
        val packageName: String = "",
        val initialKeyCode: Int = 82,
        val initialKeyName: String = "",
        val initialIsLongPress: Boolean = false,
    ) : AppNavKey

    // Notification tab
    @Serializable data object NotificationHub : AppNavKey
    @Serializable data object NotificationHistory : AppNavKey
    @Serializable data object NotificationFilterRules : AppNavKey
    @Serializable data class NotificationFilterRuleEditor(val ruleId: String = "") : AppNavKey
    @Serializable data object NotificationFilterSettings : AppNavKey
    @Serializable data object MessageReminder : AppNavKey
    @Serializable data object MessageReminderUnlockRules : AppNavKey
    @Serializable data object MessageReminderAllowedApps : AppNavKey
    @Serializable data class MessageReminderAppFilterEdit(val packageName: String) : AppNavKey
    @Serializable data class MessageReminderGestureActionPick(val slot: String) : AppNavKey
    @Serializable data class MessageReminderGestureMyShortcuts(val slot: String) : AppNavKey
    @Serializable data class MessageReminderGesturePresetShortcuts(val slot: String) : AppNavKey
    @Serializable data class MessageReminderGesturePickApp(val slot: String) : AppNavKey
    @Serializable data class MessageReminderGesturePickActivity(val slot: String, val packageName: String) : AppNavKey
    @Serializable data class MessageReminderGestureShellCommand(val slot: String, val initialCommand: String = "") : AppNavKey
    @Serializable data class MessageReminderGestureSimulateKeyEvent(
        val slot: String,
        val initialKeyCode: Int = 82,
        val initialKeyName: String = "",
        val initialIsLongPress: Boolean = false,
    ) : AppNavKey
    @Serializable data object MessageReminderDndApps : AppNavKey
    @Serializable data class MessageStyleDetail(val styleId: String) : AppNavKey
    @Serializable data object MessageStyleSideBubbleCount : AppNavKey
    @Serializable data object OtpHub : AppNavKey
    @Serializable data object OtpExtraction : AppNavKey
    @Serializable data object OtpAutoFill : AppNavKey
    @Serializable data object OtpRules : AppNavKey
    @Serializable data object OtpRecords : AppNavKey
    @Serializable data object OtpSmsBlacklist : AppNavKey
    @Serializable data object OtpBlockedApps : AppNavKey
    @Serializable data object OtpBlockedAppsPick : AppNavKey

    // Extension tab
    @Serializable data object ExtensionHub : AppNavKey
    @Serializable data object QuickWheelList : AppNavKey
    @Serializable data class QuickWheelConfig(val wheelId: String) : AppNavKey
    @Serializable data class QuickWheelSlotEditor(
        val wheelId: String,
        /** `C` 中心容器 / `0:<i>` 一级 / `0:<i>>1:<j>` 二级。 */
        val path: String,
    ) : AppNavKey
    @Serializable data object AppCarouselSwitcherSettings : AppNavKey
    @Serializable data object ExtensionExternalInvocations : AppNavKey
    @Serializable data object ExtensionLauncherShortcutMenu : AppNavKey
    @Serializable data object ExtensionAbout : AppNavKey
    @Serializable data object ExtensionDiagnosticLogs : AppNavKey
    @Serializable data class ExtensionDiagnosticLogDetail(val fileName: String) : AppNavKey
    @Serializable data object ExtensionFreezer : AppNavKey
    @Serializable data object ExtensionFreezerApps : AppNavKey
    @Serializable data object ExtensionBackTap : AppNavKey
    @Serializable data object ExtensionBackTapActionPick : AppNavKey
    @Serializable data object ExtensionBackTapActionMyShortcuts : AppNavKey
    @Serializable data object ExtensionBackTapActionPresetShortcuts : AppNavKey
    @Serializable data object ExtensionBackTapActionPickApp : AppNavKey
    @Serializable data class ExtensionBackTapActionPickActivity(val appPackageName: String) : AppNavKey
    @Serializable data class ExtensionBackTapActionShellCommand(val initialCommand: String = "") : AppNavKey
    @Serializable data class ExtensionBackTapActionSimulateKeyEvent(
        val initialKeyCode: Int = 82,
        val initialKeyName: String = "",
        val initialIsLongPress: Boolean = false,
    ) : AppNavKey
    @Serializable data object ExtensionBackup : AppNavKey
    @Serializable data object ExtensionCloudBackup : AppNavKey
    @Serializable data object ExtensionCloudStorageSettings : AppNavKey

    /** [configId] 为空串表示新建配置。 */
    @Serializable data class ExtensionCloudStorageEditor(val configId: String) : AppNavKey
    @Serializable data object ExtensionMissingPermissions : AppNavKey
    @Serializable data object ExtensionPrivacy : AppNavKey
    @Serializable data object ExtensionThirdPartyNotices : AppNavKey
    @Serializable data class ExtensionLicenseText(val assetFileName: String) : AppNavKey
    @Serializable data object QuickLauncher : AppNavKey
    @Serializable data class QuickLauncherAdd(
        val panelId: String,
        /** 目标文件夹在面板根列表中的索引；-1 表示加到面板根。 */
        val folderIndex: Int = -1,
    ) : AppNavKey
    @Serializable data class QuickLauncherEdit(val panelId: String) : AppNavKey
    @Serializable data class QuickLauncherPickApp(
        val panelId: String,
        val fromCreateFolder: Boolean = false,
        val folderIndex: Int = -1,
    ) : AppNavKey
    @Serializable data class QuickLauncherPickActivity(
        val panelId: String,
        val packageName: String,
        val fromCreateFolder: Boolean = false,
        val folderIndex: Int = -1,
    ) : AppNavKey
    @Serializable data class QuickLauncherMyShortcuts(
        val panelId: String,
        val fromCreateFolder: Boolean = false,
        val folderIndex: Int = -1,
    ) : AppNavKey
    @Serializable data class QuickLauncherPresetShortcuts(
        val panelId: String,
        val fromCreateFolder: Boolean = false,
        val folderIndex: Int = -1,
    ) : AppNavKey
    @Serializable data class QuickLauncherShellCommand(
        val panelId: String,
        val initialCommand: String = "",
        val fromCreateFolder: Boolean = false,
        val folderIndex: Int = -1,
    ) : AppNavKey
    @Serializable data class QuickLauncherSimulateKeyEvent(
        val panelId: String,
        val initialKeyCode: Int = 82,
        val initialKeyName: String = "",
        val initialIsLongPress: Boolean = false,
        val fromCreateFolder: Boolean = false,
        val folderIndex: Int = -1,
    ) : AppNavKey
    @Serializable data class QuickLauncherOpenLink(
        val panelId: String,
        val initialUrl: String = "",
        val initialLabel: String = "",
        val fromCreateFolder: Boolean = false,
        val folderIndex: Int = -1,
    ) : AppNavKey
    @Serializable data class QuickLauncherCreateFolder(val panelId: String) : AppNavKey

    @Serializable data object HoneycombLauncher : AppNavKey
    @Serializable data object HoneycombLauncherAdd : AppNavKey
    @Serializable data object HoneycombLauncherPickApp : AppNavKey
    @Serializable data class HoneycombLauncherPickActivity(val packageName: String) : AppNavKey
    @Serializable data object HoneycombLauncherMyShortcuts : AppNavKey
    @Serializable data object HoneycombLauncherPresetShortcuts : AppNavKey
    @Serializable data class HoneycombLauncherShellCommand(val initialCommand: String = "") : AppNavKey
    @Serializable data class HoneycombLauncherSimulateKeyEvent(
        val initialKeyCode: Int = 82,
        val initialKeyName: String = "",
        val initialIsLongPress: Boolean = false,
    ) : AppNavKey
    @Serializable data class HoneycombLauncherOpenLink(
        val initialUrl: String = "",
        val initialLabel: String = "",
    ) : AppNavKey
    @Serializable data object HoneycombDisplaySettings : AppNavKey
    @Serializable data object RingLauncherSettings : AppNavKey
    @Serializable data object HolographicLauncherSettings : AppNavKey
    @Serializable data object HolographicLauncherHiddenApps : AppNavKey
    @Serializable data object ShellCommands : AppNavKey
    @Serializable data object ActivityShortcuts : AppNavKey
    @Serializable data object ActivityShortcutPresets : AppNavKey
    @Serializable data object ActivityShortcutPickApp : AppNavKey
    @Serializable data class ActivityShortcutPickActivity(val packageName: String) : AppNavKey
    @Serializable data object ActivityShortcutPickAppShortcut : AppNavKey
    @Serializable data object ActivityShortcutPickAppShortcutPresets : AppNavKey
    @Serializable data object ActivityShortcutPickShell : AppNavKey
    @Serializable data object ShellCommandHistory : AppNavKey
    @Serializable data class ShellCommandEditor(val commandId: String = "") : AppNavKey
    @Serializable data object ShellCommandResult : AppNavKey
    @Serializable data object WidgetPanel : AppNavKey
    @Serializable data object FloatingPointer : AppNavKey
    @Serializable data object StashClipboard : AppNavKey
    @Serializable data object ClipboardHistorySettings : AppNavKey
    @Serializable data object ClipboardMonitoringSettings : AppNavKey
    @Serializable data object StashPanelSettings : AppNavKey
    @Serializable data object ClipboardFloatSettings : AppNavKey
    @Serializable data object ClipboardFloatBlacklist : AppNavKey
    @Serializable data object ClipboardFloatBlacklistPick : AppNavKey
    @Serializable data object ClipboardLsposedWhitelist : AppNavKey
    @Serializable data object ClipboardLsposedWhitelistPick : AppNavKey
    @Serializable data object SearchPanel : AppNavKey
    @Serializable data object SearchPanelPresentationLayout : AppNavKey
    @Serializable data object SearchPanelFileSearch : AppNavKey
    @Serializable data object SearchPanelAppSearch : AppNavKey
    @Serializable data object SearchPanelShortcutSearch : AppNavKey
    @Serializable data object SearchPanelClipboardSearch : AppNavKey
    @Serializable data object SearchPanelContactSearch : AppNavKey
    @Serializable data object SearchPanelSystemSettingsSearch : AppNavKey
    @Serializable data object FloatBall : AppNavKey
    @Serializable data object OcrModels : AppNavKey
    @Serializable data class VlmOcrSettings(val providerId: String? = null) : AppNavKey
    @Serializable data object NativeEnginePacks : AppNavKey
    @Serializable data object FloatBallAppearance : AppNavKey
    @Serializable data object FloatBallStyle : AppNavKey
    @Serializable data object FloatBallGesture : AppNavKey
    @Serializable data class FloatBallGestureActionPick(val gestureTypeId: Int) : AppNavKey
    @Serializable data class FloatBallGestureMyShortcuts(val gestureTypeId: Int) : AppNavKey
    @Serializable data class FloatBallGesturePresetShortcuts(val gestureTypeId: Int) : AppNavKey
    @Serializable data class FloatBallGesturePickApp(val gestureTypeId: Int) : AppNavKey
    @Serializable data class FloatBallGesturePickActivity(val gestureTypeId: Int, val packageName: String) : AppNavKey
    @Serializable data class FloatBallGestureShellCommand(
        val gestureTypeId: Int,
        val initialCommand: String = "",
    ) : AppNavKey
    @Serializable data class FloatBallGestureSimulateKeyEvent(
        val gestureTypeId: Int,
        val initialKeyCode: Int = 82,
        val initialKeyName: String = "",
        val initialIsLongPress: Boolean = false,
    ) : AppNavKey
    @Serializable data object FloatBallPick : AppNavKey
    /** 「取词 → 拾取与手感」：拾取十字与指针手感参数（原「取词拾取配置」页）。 */
    @Serializable data object FloatBallPickHandfeel : AppNavKey
    @Serializable data object FloatBallPickPanelLayoutBehavior : AppNavKey
    @Serializable data object ShareImageOcrHistory : AppNavKey
    @Serializable data object FloatBallTranslation : AppNavKey
    @Serializable data object CloudTranslateSettings : AppNavKey
    @Serializable data class CloudTranslateModelPicker(val providerId: String) : AppNavKey
    @Serializable data object FloatBallSearchEngine : AppNavKey
    @Serializable data object FloatBallSearchEnginePresetPicker : AppNavKey
    @Serializable data class FloatBallSearchEngineEditor(val engineId: String = "") : AppNavKey
    @Serializable data object FloatBallSearchEnginePreviewSort : AppNavKey
    @Serializable data object FloatBallImageSearchEngine : AppNavKey
    @Serializable data class FloatBallImageSearchEngineEditor(val engineId: String = "") : AppNavKey
    @Serializable data class FloatBallImageSearchEngineDetail(val engineId: String) : AppNavKey
    @Serializable data class SearchEnginePickApp(
        val isImageSearch: Boolean = false,
        val target: String,
        val titleResId: Int,
        val selectedPackageName: String,
    ) : AppNavKey
    @Serializable data class SearchEnginePickActivity(
        val isImageSearch: Boolean = false,
        val packageName: String,
        val selectedClassName: String,
    ) : AppNavKey
    @Serializable data class SearchEnginePickShareTarget(
        val isImageSearch: Boolean = false,
        val selectedPackageName: String,
        val selectedActivityClassName: String,
    ) : AppNavKey
    @Serializable data object TranslateModels : AppNavKey
    /** 「选择翻译 App」：引擎选「本地 App」时挑一个能接收取词文本的应用。 */
    @Serializable data object TranslateAppPicker : AppNavKey
    @Serializable data object FloatingPointerPointer : AppNavKey
    @Serializable data object FloatingPointerJoystick : AppNavKey
    @Serializable data object FloatingPointerRadialMenu : AppNavKey
    @Serializable data class FloatingPointerRadialActionPick(
        val target: FloatingPointerRadialActionTarget,
        val slotIndex: Int = -1,
    ) : AppNavKey
    @Serializable data class FloatingPointerRadialMyShortcuts(
        val target: FloatingPointerRadialActionTarget,
        val slotIndex: Int = -1,
    ) : AppNavKey
    @Serializable data class FloatingPointerRadialPresetShortcuts(
        val target: FloatingPointerRadialActionTarget,
        val slotIndex: Int = -1,
    ) : AppNavKey
    @Serializable data class FloatingPointerRadialPickApp(
        val target: FloatingPointerRadialActionTarget,
        val slotIndex: Int = -1,
    ) : AppNavKey
    @Serializable data class FloatingPointerRadialPickActivity(
        val target: FloatingPointerRadialActionTarget,
        val slotIndex: Int = -1,
        val packageName: String,
    ) : AppNavKey
    @Serializable data class FloatingPointerRadialShellCommand(
        val slotIndex: Int,
        val initialCommand: String = "",
    ) : AppNavKey
    @Serializable data class FloatingPointerRadialSimulateKeyEvent(
        val slotIndex: Int,
        val initialKeyCode: Int = 82,
        val initialKeyName: String = "",
        val initialIsLongPress: Boolean = false,
    ) : AppNavKey
    @Serializable data class FloatingPointerRadialSwipeConfig(val slotIndex: Int) : AppNavKey
    @Serializable data object FloatingPointerEdgeActions : AppNavKey
    @Serializable data class FloatingPointerEdgeSideSettings(val side: String) : AppNavKey
    @Serializable data class FloatingPointerEdgeActionPick(val side: String, val slotIndex: Int) : AppNavKey
    @Serializable data class FloatingPointerEdgeMyShortcuts(val side: String, val slotIndex: Int) : AppNavKey
    @Serializable data class FloatingPointerEdgePresetShortcuts(val side: String, val slotIndex: Int) : AppNavKey
    @Serializable data class FloatingPointerEdgePickApp(val side: String, val slotIndex: Int) : AppNavKey
    @Serializable data class FloatingPointerEdgePickActivity(val side: String, val slotIndex: Int, val packageName: String) : AppNavKey
    @Serializable data class FloatingPointerEdgeShellCommand(
        val side: String,
        val slotIndex: Int,
        val initialCommand: String = "",
    ) : AppNavKey
    @Serializable data class FloatingPointerEdgeSimulateKeyEvent(
        val side: String,
        val slotIndex: Int,
        val initialKeyCode: Int = 82,
        val initialKeyName: String = "",
        val initialIsLongPress: Boolean = false,
    ) : AppNavKey
}

@Serializable
enum class FloatingPointerRadialActionTarget {
    LONG_PRESS,
    SLOT,
}

fun AppNavKey.isRootDestination(): Boolean = when (this) {
    AppNavKey.HomeMain,
    AppNavKey.ShakeGestures,
    AppNavKey.NotificationHub,
    AppNavKey.ExtensionHub,
    -> true
    else -> false
}

fun AppNavKey.toBottomNavDestination(): MainBottomNavDestination = when (this) {
    AppNavKey.ShakeGestures -> MainBottomNavDestination.Shake
    AppNavKey.NotificationHub -> MainBottomNavDestination.Notification
    AppNavKey.ExtensionHub -> MainBottomNavDestination.Extension
    else -> MainBottomNavDestination.Home
}

fun MainBottomNavDestination.toRootNavKey(): AppNavKey = when (this) {
    MainBottomNavDestination.Home -> AppNavKey.HomeMain
    MainBottomNavDestination.Shake -> AppNavKey.ShakeGestures
    MainBottomNavDestination.Notification -> AppNavKey.NotificationHub
    MainBottomNavDestination.Extension -> AppNavKey.ExtensionHub
}
