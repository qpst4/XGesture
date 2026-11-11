package com.slideindex.app.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.overlay.PanelSide

fun panelSideSwipeOutlinedIcon(side: PanelSide): ImageVector = when (side) {
    PanelSide.LEFT -> ThinActionIcons.Back
    PanelSide.RIGHT -> ThinActionIcons.ArrowRight
    PanelSide.TOP -> ThinActionIcons.ArrowUp
    PanelSide.BOTTOM -> ThinActionIcons.ArrowDown
}

fun pointerSwipeDirectionOutlinedIcon(
    direction: com.slideindex.app.gesture.PointerSwipeDirection
): ImageVector = when (direction) {
    com.slideindex.app.gesture.PointerSwipeDirection.LEFT -> ThinActionIcons.Back
    com.slideindex.app.gesture.PointerSwipeDirection.RIGHT -> ThinActionIcons.ArrowRight
    com.slideindex.app.gesture.PointerSwipeDirection.UP -> ThinActionIcons.ArrowUp
    com.slideindex.app.gesture.PointerSwipeDirection.DOWN -> ThinActionIcons.ArrowDown
}

/** 动作图标（outlined / 浮窗 raster）统一走自绘 [ThinActionIcons]。 */
@Suppress("DEPRECATION")
fun gestureActionTypeOutlinedIcon(type: GestureActionType): ImageVector = when (type) {
    GestureActionType.NONE -> ThinActionIcons.Block
    GestureActionType.OPEN_INDEX -> ThinActionIcons.SortByAlpha
    GestureActionType.QUICK_LAUNCHER -> ThinActionIcons.Apps
    GestureActionType.HONEYCOMB_LAUNCHER -> ThinActionIcons.Hive
    GestureActionType.APP_RING_LAUNCHER -> ThinActionIcons.RingLauncher
    GestureActionType.FINGERTIP_RING -> ThinActionIcons.MenuOpen
    GestureActionType.APP_CAROUSEL_SWITCHER -> ThinActionIcons.AppCarouselSwitcher
    GestureActionType.HOLOGRAPHIC_LAUNCHER -> ThinActionIcons.Globe
    GestureActionType.TASK_SWITCHER -> ThinActionIcons.ViewCarousel
    GestureActionType.SHELL_COMMAND_PANEL -> ThinActionIcons.Code
    GestureActionType.EXECUTE_SHELL_COMMAND -> ThinActionIcons.PlayCircle
    GestureActionType.RUN_MACRO -> ThinActionIcons.PlayCircle
    GestureActionType.QUICK_TOOLS_OVERLAY -> MaterialSymbolThinIcons.Handyman
    GestureActionType.QUICK_MENU -> ThinActionIcons.QuickMenu
    GestureActionType.SHAKE_MENU -> ThinActionIcons.MenuOpen
    GestureActionType.WIDGET_POPUP_OVERLAY -> ThinActionIcons.Widgets
    GestureActionType.OPEN_STASH_PANEL -> ThinActionIcons.Inventory
    GestureActionType.OPEN_CLIPBOARD_PANEL -> ThinActionIcons.ContentPaste
    GestureActionType.OPEN_CLIPBOARD_FLOAT -> ThinActionIcons.ContentPaste
    GestureActionType.CLIPBOARD_PICK -> ThinActionIcons.TextFields
    GestureActionType.CLIPBOARD_PASTE -> ThinActionIcons.ContentPaste
    GestureActionType.FLOATING_POINTER -> ThinActionIcons.MyLocation
    GestureActionType.SIMULATE_POINTER_SWIPE -> ThinActionIcons.TouchApp
    GestureActionType.BACK -> ThinActionIcons.Back
    GestureActionType.HOME -> ThinActionIcons.Home
    GestureActionType.RECENTS -> ThinActionIcons.Recents
    GestureActionType.CLOSE_CURRENT_APP -> ThinActionIcons.Close
    GestureActionType.FORCE_STOP_CURRENT_APP -> ThinActionIcons.ForceStopApp
    GestureActionType.FREE_WINDOW_CURRENT_APP -> ThinActionIcons.FreeWindow
    GestureActionType.CLICK_PASSTHROUGH -> MaterialSymbolThinIcons.TouchApp
    GestureActionType.FLASHLIGHT -> ThinActionIcons.Flashlight
    GestureActionType.ADJUST_VOLUME -> ThinActionIcons.VolumeUp
    GestureActionType.VOLUME_UP -> ThinActionIcons.VolumeIncrease
    GestureActionType.VOLUME_DOWN -> ThinActionIcons.VolumeDecrease
    GestureActionType.ADJUST_BRIGHTNESS -> ThinActionIcons.Brightness
    GestureActionType.TOGGLE_AUTO_BRIGHTNESS -> MaterialSymbolThinIcons.BrightnessAuto
    GestureActionType.LAUNCH_ASSISTANT -> ThinActionIcons.Assistant
    GestureActionType.VOICE_SEARCH -> ThinActionIcons.VoiceSearch
    GestureActionType.VOICE_ASSISTANT -> ThinActionIcons.VoiceAssistant
    GestureActionType.TOGGLE_AUTO_ROTATE -> MaterialSymbolThinIcons.MobileRotate
    GestureActionType.FORCE_PORTRAIT -> ThinActionIcons.ForcePortrait
    GestureActionType.FORCE_LANDSCAPE -> ThinActionIcons.ForceLandscape
    GestureActionType.TOGGLE_MUTE -> ThinActionIcons.VolumeOff
    GestureActionType.MEDIA_PLAY_PAUSE -> ThinActionIcons.PlayPause
    GestureActionType.MEDIA_PREVIOUS -> ThinActionIcons.SkipPrevious
    GestureActionType.MEDIA_NEXT -> ThinActionIcons.SkipNext
    GestureActionType.PREVIOUS_APP -> ThinActionIcons.Restore
    GestureActionType.OPEN_NOTIFICATIONS -> ThinActionIcons.Notifications
    GestureActionType.READ_LATEST_MESSAGE -> ThinActionIcons.ChatBubble
    GestureActionType.READ_LATEST_MESSAGE_SMALL_WINDOW -> ThinActionIcons.FreeWindow
    GestureActionType.CLEAR_NOTIFICATIONS -> ThinActionIcons.ClearNotifications
    GestureActionType.OPEN_QUICK_SETTINGS -> ThinActionIcons.QuickSettings
    GestureActionType.LOCK_SCREEN -> ThinActionIcons.Lock
    GestureActionType.LOCK_SCREEN_AND_SILENCE_RING -> ThinActionIcons.LockSilenceRing
    GestureActionType.LOCK_SCREEN_AND_MUTE_ALL -> ThinActionIcons.LockMuteAll
    GestureActionType.SCREENSHOT -> ThinActionIcons.Screenshot
    GestureActionType.FULLSCREEN_SCREENSHOT_PICK -> ThinActionIcons.TextFields
    GestureActionType.REGIONAL_SCREENSHOT_PICK -> ThinActionIcons.ScreenshotRegion
    GestureActionType.SEARCH_PANEL -> ThinActionIcons.Search
    GestureActionType.FILE_BROWSER -> MaterialSymbolThinIcons.Folder
    GestureActionType.VOLUME_PANEL -> ThinActionIcons.VolumeUp
    GestureActionType.SCREEN_TRANSLATE -> ThinActionIcons.TextFields
    GestureActionType.SCREEN_SEARCH -> ThinActionIcons.Search
    GestureActionType.REMIND,
    GestureActionType.REMIND_1M, GestureActionType.REMIND_3M, GestureActionType.REMIND_5M,
    GestureActionType.REMIND_10M, GestureActionType.REMIND_15M -> ThinActionIcons.Alarm
    GestureActionType.UNIVERSAL_COPY -> ThinActionIcons.ContentPaste
    GestureActionType.FREEZER_PANEL -> ThinActionIcons.Fridge
    GestureActionType.REFREEZE -> MaterialSymbolThinIcons.AcUnit
    GestureActionType.POWER_MENU -> ThinActionIcons.Power
    GestureActionType.KEEP_SCREEN_ON -> ThinActionIcons.KeepScreenOn
    GestureActionType.SCROLL_TO_TOP -> ThinActionIcons.ScrollToTop
    GestureActionType.SCROLL_TO_BOTTOM -> ThinActionIcons.ScrollToBottom
    GestureActionType.PAGE_SCROLL -> MaterialSymbolThinIcons.SwapVert
    GestureActionType.TOGGLE_DND -> ThinActionIcons.DoNotDisturb
    GestureActionType.TIMED_DND -> ThinActionIcons.DoNotDisturb
    GestureActionType.SCREEN_RECORD -> ThinActionIcons.ScreenRecord
    GestureActionType.TOGGLE_WIFI -> ThinActionIcons.Wifi
    GestureActionType.TOGGLE_SHAKE_GESTURE -> ThinActionIcons.Gesture
    GestureActionType.TOGGLE_FLOAT_BALL -> ThinActionIcons.FloatBall
    GestureActionType.TOGGLE_MOBILE_DATA -> ThinActionIcons.Cellular
    GestureActionType.TOGGLE_NFC -> MaterialSymbolThinIcons.Nfc
    GestureActionType.TOGGLE_BLUETOOTH -> MaterialSymbolThinIcons.Bluetooth
    GestureActionType.SWITCH_INPUT_METHOD -> ThinActionIcons.Keyboard
    GestureActionType.OPEN_INTERNET_PANEL -> ThinActionIcons.Wifi
    GestureActionType.OPEN_VOLUME_PANEL -> ThinActionIcons.VolumeUp
    GestureActionType.OPEN_VOLUME_SLIDER -> ThinActionIcons.VolumeIncrease
    GestureActionType.OPEN_LINK -> MaterialSymbolThinIcons.Link
    GestureActionType.CURRENT_APP_INFO -> ThinActionIcons.Assistant
    GestureActionType.SCREEN_OFF_KEEP_AWAKE -> ThinActionIcons.ScreenOffKeepAwake
    GestureActionType.TOUCH_BLOCK -> ThinActionIcons.TouchApp
    GestureActionType.PIN_TO_SCREEN -> ThinActionIcons.Pin
    GestureActionType.SIMULATE_KEY_EVENT -> ThinActionIcons.Keyboard
    GestureActionType.POINTER_GESTURE_RECORDER -> ThinActionIcons.Gesture
    GestureActionType.POINTER_REALTIME_GESTURE -> ThinActionIcons.Gesture
    GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU -> ThinActionIcons.MenuOpen
    GestureActionType.LAUNCH_APP -> ThinActionIcons.Apps
    GestureActionType.LAUNCH_SHORTCUT -> ThinActionIcons.Shortcut
    GestureActionType.CORNER_INNER_CANCEL -> ThinActionIcons.Close
    GestureActionType.CORNER_INNER_PIN_WHEEL -> ThinActionIcons.Block
    GestureActionType.SNOOZE_OVERLAYS -> ThinActionIcons.VisibilityOff
    GestureActionType.FOREGROUND_ACTIVITY_INSPECTOR -> ThinActionIcons.Search
    GestureActionType.SMART_SCREENSHOT -> ThinActionIcons.Screenshot
    GestureActionType.SCREEN_DRAW -> ThinActionIcons.PenTablet
    GestureActionType.GALLERY_IMAGE_EDIT -> MaterialSymbolThinIcons.PhotoLibrary
    GestureActionType.SCREEN_CONTROL -> MaterialSymbolThinIcons.RemoteGen
    GestureActionType.WECHAT_SCAN -> PayQrActionIcons.WechatScan
    GestureActionType.WECHAT_PAY_CODE -> PayQrActionIcons.WechatPayCode
    GestureActionType.ALIPAY_PAY_CODE -> PayQrActionIcons.AlipayPayCode
    GestureActionType.ALIPAY_SCAN -> PayQrActionIcons.AlipayScan
    GestureActionType.QUICK_WHEEL -> ThinActionIcons.QuickWheel
}

@Suppress("DEPRECATION")
fun gestureActionTypeThinIcon(type: GestureActionType): ImageVector = when (type) {
    GestureActionType.NONE -> ThinActionIcons.Block
    GestureActionType.OPEN_INDEX -> ThinActionIcons.SortByAlpha
    GestureActionType.QUICK_LAUNCHER -> ThinActionIcons.Apps
    GestureActionType.HONEYCOMB_LAUNCHER -> ThinActionIcons.Hive
    GestureActionType.APP_RING_LAUNCHER -> ThinActionIcons.RingLauncher
    GestureActionType.FINGERTIP_RING -> ThinActionIcons.MenuOpen
    GestureActionType.APP_CAROUSEL_SWITCHER -> ThinActionIcons.AppCarouselSwitcher
    GestureActionType.HOLOGRAPHIC_LAUNCHER -> ThinActionIcons.Globe
    GestureActionType.TASK_SWITCHER -> ThinActionIcons.ViewCarousel
    GestureActionType.SHELL_COMMAND_PANEL -> ThinActionIcons.Code
    GestureActionType.EXECUTE_SHELL_COMMAND -> ThinActionIcons.Code
    GestureActionType.RUN_MACRO -> ThinActionIcons.PlayCircle
    GestureActionType.QUICK_TOOLS_OVERLAY -> MaterialSymbolThinIcons.Handyman
    GestureActionType.QUICK_MENU -> ThinActionIcons.QuickMenu
    GestureActionType.SHAKE_MENU -> ThinActionIcons.MenuOpen
    GestureActionType.WIDGET_POPUP_OVERLAY -> ThinActionIcons.Widgets
    GestureActionType.OPEN_STASH_PANEL -> ThinActionIcons.Inventory
    GestureActionType.OPEN_CLIPBOARD_PANEL -> ThinActionIcons.ContentPaste
    GestureActionType.OPEN_CLIPBOARD_FLOAT -> ThinActionIcons.ContentPaste
    GestureActionType.CLIPBOARD_PICK -> ThinActionIcons.TextFields
    GestureActionType.CLIPBOARD_PASTE -> ThinActionIcons.ContentPaste
    GestureActionType.FLOATING_POINTER -> ThinActionIcons.MyLocation
    GestureActionType.SIMULATE_POINTER_SWIPE -> ThinActionIcons.TouchApp
    GestureActionType.BACK -> ThinActionIcons.Back
    GestureActionType.HOME -> ThinActionIcons.Home
    GestureActionType.RECENTS -> ThinActionIcons.Recents
    GestureActionType.CLOSE_CURRENT_APP -> ThinActionIcons.Close
    GestureActionType.FORCE_STOP_CURRENT_APP -> ThinActionIcons.ForceStopApp
    GestureActionType.FREE_WINDOW_CURRENT_APP -> ThinActionIcons.FreeWindow
    GestureActionType.CLICK_PASSTHROUGH -> MaterialSymbolThinIcons.TouchApp
    GestureActionType.FLASHLIGHT -> ThinActionIcons.Flashlight
    GestureActionType.ADJUST_VOLUME -> ThinActionIcons.VolumeUp
    GestureActionType.VOLUME_UP -> ThinActionIcons.VolumeIncrease
    GestureActionType.VOLUME_DOWN -> ThinActionIcons.VolumeDecrease
    GestureActionType.ADJUST_BRIGHTNESS -> ThinActionIcons.Brightness
    GestureActionType.TOGGLE_AUTO_BRIGHTNESS -> MaterialSymbolThinIcons.BrightnessAuto
    GestureActionType.LAUNCH_ASSISTANT -> ThinActionIcons.Assistant
    GestureActionType.VOICE_SEARCH -> ThinActionIcons.VoiceSearch
    GestureActionType.VOICE_ASSISTANT -> ThinActionIcons.VoiceAssistant
    GestureActionType.TOGGLE_AUTO_ROTATE -> MaterialSymbolThinIcons.MobileRotate
    GestureActionType.FORCE_PORTRAIT -> ThinActionIcons.ForcePortrait
    GestureActionType.FORCE_LANDSCAPE -> ThinActionIcons.ForceLandscape
    GestureActionType.TOGGLE_MUTE -> ThinActionIcons.VolumeOff
    GestureActionType.MEDIA_PLAY_PAUSE -> ThinActionIcons.PlayPause
    GestureActionType.MEDIA_PREVIOUS -> ThinActionIcons.SkipPrevious
    GestureActionType.MEDIA_NEXT -> ThinActionIcons.SkipNext
    GestureActionType.PREVIOUS_APP -> ThinActionIcons.Restore
    GestureActionType.OPEN_NOTIFICATIONS -> ThinActionIcons.Notifications
    GestureActionType.READ_LATEST_MESSAGE -> ThinActionIcons.ChatBubble
    GestureActionType.READ_LATEST_MESSAGE_SMALL_WINDOW -> ThinActionIcons.FreeWindow
    GestureActionType.CLEAR_NOTIFICATIONS -> ThinActionIcons.ClearNotifications
    GestureActionType.OPEN_QUICK_SETTINGS -> ThinActionIcons.QuickSettings
    GestureActionType.LOCK_SCREEN -> ThinActionIcons.Lock
    GestureActionType.LOCK_SCREEN_AND_SILENCE_RING -> ThinActionIcons.LockSilenceRing
    GestureActionType.LOCK_SCREEN_AND_MUTE_ALL -> ThinActionIcons.LockMuteAll
    GestureActionType.SCREENSHOT -> ThinActionIcons.Screenshot
    GestureActionType.FULLSCREEN_SCREENSHOT_PICK -> ThinActionIcons.TextFields
    GestureActionType.REGIONAL_SCREENSHOT_PICK -> ThinActionIcons.ScreenshotRegion
    GestureActionType.SEARCH_PANEL -> ThinActionIcons.Search
    GestureActionType.FILE_BROWSER -> MaterialSymbolThinIcons.Folder
    GestureActionType.VOLUME_PANEL -> ThinActionIcons.VolumeUp
    GestureActionType.SCREEN_TRANSLATE -> ThinActionIcons.TextFields
    GestureActionType.SCREEN_SEARCH -> ThinActionIcons.Search
    GestureActionType.REMIND,
    GestureActionType.REMIND_1M, GestureActionType.REMIND_3M, GestureActionType.REMIND_5M,
    GestureActionType.REMIND_10M, GestureActionType.REMIND_15M -> ThinActionIcons.Alarm
    GestureActionType.UNIVERSAL_COPY -> ThinActionIcons.ContentPaste
    GestureActionType.FREEZER_PANEL -> ThinActionIcons.Fridge
    GestureActionType.REFREEZE -> MaterialSymbolThinIcons.AcUnit
    GestureActionType.POWER_MENU -> ThinActionIcons.Power
    GestureActionType.KEEP_SCREEN_ON -> ThinActionIcons.KeepScreenOn
    GestureActionType.SCROLL_TO_TOP -> ThinActionIcons.ScrollToTop
    GestureActionType.SCROLL_TO_BOTTOM -> ThinActionIcons.ScrollToBottom
    GestureActionType.PAGE_SCROLL -> MaterialSymbolThinIcons.SwapVert
    GestureActionType.TOGGLE_DND -> ThinActionIcons.DoNotDisturb
    GestureActionType.TIMED_DND -> ThinActionIcons.DoNotDisturb
    GestureActionType.SCREEN_RECORD -> ThinActionIcons.ScreenRecord
    GestureActionType.TOGGLE_WIFI -> ThinActionIcons.Wifi
    GestureActionType.TOGGLE_SHAKE_GESTURE -> ThinActionIcons.Gesture
    GestureActionType.TOGGLE_FLOAT_BALL -> ThinActionIcons.FloatBall
    GestureActionType.TOGGLE_MOBILE_DATA -> ThinActionIcons.Cellular
    GestureActionType.TOGGLE_NFC -> MaterialSymbolThinIcons.Nfc
    GestureActionType.TOGGLE_BLUETOOTH -> MaterialSymbolThinIcons.Bluetooth
    GestureActionType.SWITCH_INPUT_METHOD -> ThinActionIcons.Keyboard
    GestureActionType.OPEN_INTERNET_PANEL -> ThinActionIcons.Wifi
    GestureActionType.OPEN_VOLUME_PANEL -> ThinActionIcons.VolumeUp
    GestureActionType.OPEN_VOLUME_SLIDER -> ThinActionIcons.VolumeIncrease
    GestureActionType.OPEN_LINK -> MaterialSymbolThinIcons.Link
    GestureActionType.CURRENT_APP_INFO -> ThinActionIcons.Assistant
    GestureActionType.SCREEN_OFF_KEEP_AWAKE -> ThinActionIcons.ScreenOffKeepAwake
    GestureActionType.TOUCH_BLOCK -> ThinActionIcons.TouchApp
    GestureActionType.PIN_TO_SCREEN -> ThinActionIcons.Pin
    GestureActionType.SIMULATE_KEY_EVENT -> ThinActionIcons.Keyboard
    GestureActionType.POINTER_GESTURE_RECORDER -> ThinActionIcons.Gesture
    GestureActionType.POINTER_REALTIME_GESTURE -> ThinActionIcons.Gesture
    GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU -> ThinActionIcons.MenuOpen
    GestureActionType.LAUNCH_APP -> ThinActionIcons.Apps
    GestureActionType.LAUNCH_SHORTCUT -> ThinActionIcons.Shortcut
    GestureActionType.CORNER_INNER_CANCEL -> ThinActionIcons.Close
    GestureActionType.CORNER_INNER_PIN_WHEEL -> ThinActionIcons.Block
    GestureActionType.SNOOZE_OVERLAYS -> ThinActionIcons.VisibilityOff
    GestureActionType.FOREGROUND_ACTIVITY_INSPECTOR -> ThinActionIcons.Search
    GestureActionType.SMART_SCREENSHOT -> ThinActionIcons.Screenshot
    GestureActionType.SCREEN_DRAW -> ThinActionIcons.PenTablet
    GestureActionType.GALLERY_IMAGE_EDIT -> MaterialSymbolThinIcons.PhotoLibrary
    GestureActionType.SCREEN_CONTROL -> MaterialSymbolThinIcons.RemoteGen
    GestureActionType.WECHAT_SCAN -> PayQrActionIcons.WechatScan
    GestureActionType.WECHAT_PAY_CODE -> PayQrActionIcons.WechatPayCode
    GestureActionType.ALIPAY_PAY_CODE -> PayQrActionIcons.AlipayPayCode
    GestureActionType.ALIPAY_SCAN -> PayQrActionIcons.AlipayScan
    GestureActionType.QUICK_WHEEL -> ThinActionIcons.QuickWheel
}
