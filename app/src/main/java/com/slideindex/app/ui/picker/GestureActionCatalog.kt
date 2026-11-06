package com.slideindex.app.ui.picker

import android.content.Context
import androidx.annotation.StringRes
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.gesture.isEligibleForSlotPicker
import com.slideindex.app.gesture.slotPickerKindOrNull
import com.slideindex.app.ui.gesturepicker.filterGestureActions

enum class GestureActionCatalogScope {
    GesturePicker,
    QuickLauncher,
}

enum class GestureActionCategory(@StringRes val titleRes: Int) {
    Navigation(R.string.action_category_navigation),
    Controls(R.string.action_category_controls),
    Intelligence(R.string.action_category_intelligence),
    Panel(R.string.action_category_panel),
    Pointer(R.string.action_category_pointer),
    Other(R.string.action_category_other),
}

data class GestureActionSection(
    val category: GestureActionCategory,
    val actions: List<GestureAction>,
)

object GestureActionCatalog {
    fun build(
        scope: GestureActionCatalogScope,
        trigger: GestureTriggerType = GestureTriggerType.SHORT_SWIPE_IN,
        includePointerGestureActions: Boolean = false,
        includeCornerInnerZoneActions: Boolean = false,
        catalogPolicy: ActionPickerCatalogPolicy,
    ): List<GestureAction> {
        val actions = when (scope) {
            GestureActionCatalogScope.GesturePicker -> buildGesturePickerActions(
                trigger = trigger,
                includePointerGestureActions = includePointerGestureActions,
                includeCornerInnerZoneActions = includeCornerInnerZoneActions,
            )
            GestureActionCatalogScope.QuickLauncher -> buildQuickLauncherActions()
        }
        val slotPickerKind = catalogPolicy.slotPickerKindOrNull()
        return if (slotPickerKind == null) {
            actions
        } else {
            actions.filter { it.isEligibleForSlotPicker(slotPickerKind) }
        }
    }

    fun filter(
        context: Context,
        actions: List<GestureAction>,
        query: String,
        pinNoneAtTop: Boolean = false,
        includeCornerInnerZoneActions: Boolean = false,
    ): List<GestureAction> {
        val sorted = filterGestureActions(context, actions, query)
        val pinnedTop = buildList {
            if (pinNoneAtTop) add(GestureAction.None)
            if (includeCornerInnerZoneActions) {
                add(GestureAction.CornerInnerPinWheel)
                add(GestureAction.CornerInnerCancel)
            }
        }
        if (pinnedTop.isEmpty()) return sorted
        val pinnedSet = pinnedTop.toSet()
        return pinnedTop.filter { it in sorted } + sorted.filter { it !in pinnedSet }
    }

    fun groupIntoSections(actions: List<GestureAction>): List<GestureActionSection> {
        if (actions.isEmpty()) return emptyList()
        val buckets = LinkedHashMap<GestureActionCategory, MutableList<GestureAction>>()
        for (category in GestureActionCategory.entries) {
            buckets[category] = mutableListOf()
        }
        for (action in actions) {
            buckets.getValue(categoryOf(action)).add(action)
        }
        return buckets
            .mapNotNull { (category, list) ->
                if (list.isEmpty()) null else GestureActionSection(category, list)
            }
    }

    fun categoryOf(action: GestureAction): GestureActionCategory = when (action.type) {
        // 1. 导航与系统 (Navigation & System)
        GestureActionType.NONE,
        GestureActionType.CORNER_INNER_PIN_WHEEL,
        GestureActionType.CORNER_INNER_CANCEL,
        GestureActionType.BACK,
        GestureActionType.HOME,
        GestureActionType.RECENTS,
        GestureActionType.PREVIOUS_APP,
        GestureActionType.OPEN_NOTIFICATIONS,
        GestureActionType.READ_LATEST_MESSAGE,
        GestureActionType.READ_LATEST_MESSAGE_SMALL_WINDOW,
        GestureActionType.CLEAR_NOTIFICATIONS,
        GestureActionType.OPEN_QUICK_SETTINGS,
        GestureActionType.CLOSE_CURRENT_APP,
        GestureActionType.FORCE_STOP_CURRENT_APP,
        GestureActionType.FREE_WINDOW_CURRENT_APP,
        GestureActionType.LOCK_SCREEN,
        GestureActionType.LOCK_SCREEN_AND_SILENCE_RING,
        GestureActionType.LOCK_SCREEN_AND_MUTE_ALL,
        GestureActionType.KEEP_SCREEN_ON,
        GestureActionType.POWER_MENU,
        GestureActionType.SCROLL_TO_TOP,
        GestureActionType.SCROLL_TO_BOTTOM,
        GestureActionType.PAGE_SCROLL,
        GestureActionType.CURRENT_APP_INFO,
        GestureActionType.SCREEN_OFF_KEEP_AWAKE,
        GestureActionType.TOUCH_BLOCK,
        -> GestureActionCategory.Navigation

        // 2. 开关与调节 (Controls & Media)
        GestureActionType.TOGGLE_WIFI,
        GestureActionType.TOGGLE_MOBILE_DATA,
        GestureActionType.FLASHLIGHT,
        GestureActionType.TOGGLE_DND,
        GestureActionType.TIMED_DND,
        GestureActionType.TOGGLE_AUTO_ROTATE,
        GestureActionType.FORCE_PORTRAIT,
        GestureActionType.FORCE_LANDSCAPE,
        GestureActionType.TOGGLE_AUTO_BRIGHTNESS,
        GestureActionType.ADJUST_BRIGHTNESS,
        GestureActionType.ADJUST_VOLUME,
        GestureActionType.VOLUME_UP,
        GestureActionType.VOLUME_DOWN,
        GestureActionType.OPEN_VOLUME_PANEL,
        GestureActionType.OPEN_INTERNET_PANEL,
        GestureActionType.TOGGLE_MUTE,
        GestureActionType.MEDIA_PLAY_PAUSE,
        GestureActionType.MEDIA_PREVIOUS,
        GestureActionType.MEDIA_NEXT,
        GestureActionType.SWITCH_INPUT_METHOD,
        // 「切换悬浮球」是一枚开关，归在「开关与调节」；不跟「暂停悬浮层」一起放指针区。
        GestureActionType.TOGGLE_FLOAT_BALL,
        -> GestureActionCategory.Controls

        // 3. 截屏与识别 (Capture, OCR & Assistant)
        GestureActionType.SCREENSHOT,
        GestureActionType.SCREEN_RECORD,
        GestureActionType.REGIONAL_SCREENSHOT_PICK,
        GestureActionType.FULLSCREEN_SCREENSHOT_PICK,
        GestureActionType.PIN_TO_SCREEN,
        GestureActionType.FOREGROUND_ACTIVITY_INSPECTOR,
        GestureActionType.UNIVERSAL_COPY,
        GestureActionType.SCREEN_SEARCH,
        GestureActionType.SCREEN_TRANSLATE,
        GestureActionType.LAUNCH_ASSISTANT,
        GestureActionType.VOICE_SEARCH,
        GestureActionType.VOICE_ASSISTANT,
        GestureActionType.SMART_SCREENSHOT,
        GestureActionType.SCREEN_DRAW,
        // 「相册图片编辑」编辑的是相册里已有的图，归在「截屏与识别」这一族里（与截图→编辑相邻）。
        GestureActionType.GALLERY_IMAGE_EDIT,
        -> GestureActionCategory.Intelligence

        // 4. 面板与启动 (Panels & Launchers)
        GestureActionType.OPEN_INDEX,
        GestureActionType.QUICK_LAUNCHER,
        GestureActionType.APP_RING_LAUNCHER,
        GestureActionType.APP_CAROUSEL_SWITCHER,
        GestureActionType.TASK_SWITCHER,
        GestureActionType.HONEYCOMB_LAUNCHER,
        GestureActionType.HOLOGRAPHIC_LAUNCHER,
        GestureActionType.QUICK_WHEEL,
        GestureActionType.SEARCH_PANEL,
        GestureActionType.OPEN_LINK,
        GestureActionType.VOLUME_PANEL,
        GestureActionType.OPEN_CLIPBOARD_PANEL,
        GestureActionType.OPEN_CLIPBOARD_FLOAT,
        GestureActionType.CLIPBOARD_PICK,
        GestureActionType.CLIPBOARD_PASTE,
        GestureActionType.OPEN_STASH_PANEL,
        GestureActionType.WIDGET_POPUP_OVERLAY,
        GestureActionType.QUICK_TOOLS_OVERLAY,
        GestureActionType.QUICK_MENU,
        GestureActionType.SHAKE_MENU,
        GestureActionType.FILE_BROWSER,
        GestureActionType.WECHAT_SCAN,
        GestureActionType.WECHAT_PAY_CODE,
        GestureActionType.ALIPAY_PAY_CODE,
        GestureActionType.ALIPAY_SCAN,
        -> GestureActionCategory.Panel

        // 5. 指针与高级 (Pointer & Advanced)
        GestureActionType.FLOATING_POINTER,
        GestureActionType.SCREEN_CONTROL,
        GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU,
        GestureActionType.POINTER_GESTURE_RECORDER,
        GestureActionType.POINTER_REALTIME_GESTURE,
        GestureActionType.SIMULATE_POINTER_SWIPE,
        GestureActionType.SHELL_COMMAND_PANEL,
        GestureActionType.EXECUTE_SHELL_COMMAND,
        GestureActionType.RUN_MACRO,
        GestureActionType.SIMULATE_KEY_EVENT,
        GestureActionType.FREEZER_PANEL,
        GestureActionType.REFREEZE,
        GestureActionType.REMIND,
        GestureActionType.REMIND_1M,
        GestureActionType.REMIND_3M,
        GestureActionType.REMIND_5M,
        GestureActionType.REMIND_10M,
        GestureActionType.REMIND_15M,
        GestureActionType.SNOOZE_OVERLAYS,
        GestureActionType.CLICK_PASSTHROUGH,
        -> GestureActionCategory.Pointer

        else -> GestureActionCategory.Other
    }

    private fun buildGesturePickerActions(
        trigger: GestureTriggerType,
        includePointerGestureActions: Boolean,
        includeCornerInnerZoneActions: Boolean,
    ): List<GestureAction> = buildList {
        if (includeCornerInnerZoneActions) {
            add(GestureAction.CornerInnerPinWheel)
            add(GestureAction.CornerInnerCancel)
        }
        if (!includeCornerInnerZoneActions) {
            add(GestureAction.None)
        }
        add(GestureAction.QuickLauncher())
        add(GestureAction.OpenIndex)
        add(GestureAction.RingLauncher)
        add(GestureAction.FingertipRing)
        add(GestureAction.TaskSwitcher)
        add(GestureAction.HoneycombLauncher)
        add(GestureAction.HolographicLauncher)
        add(GestureAction.QuickWheel())
        addAll(sharedCoreActions())
        if (includePointerGestureActions) {
            add(GestureAction.OpenFloatingPointerRadialMenu)
            add(GestureAction.PointerGestureRecorder)
            add(GestureAction.PointerRealtimeGesture)
            add(GestureAction.SimulatePointerSwipe())
        }
        add(GestureAction.SnoozeOverlays)
        // 「运行宏」：选中后由选择器弹二级列表挑一条宏（见 MacroPickDialog）。
        add(GestureAction.RunMacro())
        if (trigger == GestureTriggerType.SHORT_SINGLE_TAP) add(GestureAction.ClickPassthrough)
    }

    private fun buildQuickLauncherActions(): List<GestureAction> = buildList {
        add(GestureAction.QuickLauncher())
        add(GestureAction.OpenIndex)
        add(GestureAction.RingLauncher)
        add(GestureAction.FingertipRing)
        add(GestureAction.TaskSwitcher)
        add(GestureAction.HoneycombLauncher)
        add(GestureAction.HolographicLauncher)
        add(GestureAction.QuickWheel())
        addAll(sharedCoreActions())
        add(GestureAction.SnoozeOverlays)
        add(GestureAction.RunMacro())
    }

    private fun sharedCoreActions(): List<GestureAction> = listOf(
        // 1. 导航与系统 (Navigation & System)
        GestureAction.Back,
        GestureAction.Home,
        GestureAction.Recents,
        GestureAction.PreviousApp,
        GestureAction.OpenNotifications,
        // 通知三件套：阅读最新一条 / 小窗阅读 / 清空通知栏，紧跟「打开通知栏」。
        GestureAction.ReadLatestMessage,
        GestureAction.ReadLatestMessageInSmallWindow,
        GestureAction.ClearNotifications,
        GestureAction.OpenQuickSettings,
        GestureAction.CloseCurrentApp,
        GestureAction.ForceStopCurrentApp,
        GestureAction.FreeWindowCurrentApp,
        GestureAction.CurrentAppInfo,
        GestureAction.ScreenOffKeepAwake,
        GestureAction.TouchBlock,
        GestureAction.LockScreen,
        GestureAction.LockScreenAndSilenceRing,
        GestureAction.LockScreenAndMuteAll,
        GestureAction.KeepScreenOn,
        GestureAction.PowerMenu,
        GestureAction.ScrollToTop,
        GestureAction.ScrollToBottom,
        GestureAction.PageScroll,

        // 2. 开关与调节 (Controls & Media)
        GestureAction.ToggleWifi,
        GestureAction.ToggleMobileData,
        GestureAction.OpenInternetPanel,
        GestureAction.Flashlight,
        GestureAction.ToggleDnd,
        GestureAction.TimedDnd,
        GestureAction.ToggleAutoRotate,
        GestureAction.ForcePortrait,
        GestureAction.ForceLandscape,
        GestureAction.ToggleAutoBrightness,
        GestureAction.AdjustBrightness,
        GestureAction.AdjustVolume,
        GestureAction.VolumeUp,
        GestureAction.VolumeDown,
        GestureAction.OpenVolumePanel,
        GestureAction.ToggleMute,
        GestureAction.ToggleShakeGesture,
        GestureAction.ToggleFloatBall,
        GestureAction.MediaPlayPause,
        GestureAction.MediaPrevious,
        GestureAction.MediaNext,
        GestureAction.SwitchInputMethod,

        // 3. 截屏与识别 (Capture, OCR & Assistant)
        GestureAction.Screenshot,
        GestureAction.ScreenRecord,
        GestureAction.RegionalScreenshotPick,
        GestureAction.FullscreenScreenshotPick,
        GestureAction.PinToScreen,
        GestureAction.ForegroundActivityInspector,
        GestureAction.UniversalCopy,
        GestureAction.ScreenSearch,
        GestureAction.ScreenTranslate,
        GestureAction.LaunchAssistant,
        GestureAction.VoiceSearch,
        GestureAction.VoiceAssistant,
        GestureAction.SmartScreenshot,
        GestureAction.ScreenDraw,
        GestureAction.GalleryImageEdit,

        // 4. 面板与启动器 (Panels & Launchers)
        GestureAction.AppCarouselSwitcher,
        GestureAction.SearchPanel,
        GestureAction.FileBrowser,
        GestureAction.OpenLink(url = ""),
        GestureAction.VolumePanel,
        GestureAction.ClipboardPanel,
        GestureAction.ClipboardFloat,
        GestureAction.ClipboardPick,
        GestureAction.ClipboardPaste,
        GestureAction.StashPanel,
        GestureAction.WidgetPopupOverlay,
        GestureAction.QuickToolsOverlay,
        GestureAction.QuickMenu,
        GestureAction.OpenShakeMenu(),
        GestureAction.WechatScan,
        GestureAction.WechatPayCode,
        GestureAction.AlipayPayCode,
        GestureAction.AlipayScan,

        // 5. 指针与高级工具 (Pointer & Advanced)
        GestureAction.FloatingPointer,
        GestureAction.ScreenControl,
        GestureAction.ShellCommandPanel,
        GestureAction.ExecuteShellCommand(),
        GestureAction.SimulateKeyEvent(),
        GestureAction.FreezerPanel,
        GestureAction.Refreeze,
        GestureAction.Remind,
    )
}
