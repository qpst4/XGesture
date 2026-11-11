package com.slideindex.app.ui.gesturepicker

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureShortcutPayload
import com.slideindex.app.ui.gestureExecuteShellCommandPreview
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.quickLauncherPanelLabel
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.privilege.PrivilegeUiStrings
import com.slideindex.app.util.MediaSessionHelper
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.TaskManagerUtil

private fun resolveAppDisplayName(context: Context, packageName: String): String {
    if (packageName.isBlank()) return packageName
    return try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
}

private fun launchAppActionLabel(context: Context, packageName: String, cloneUserId: Int): String =
    if (packageName.isBlank()) {
        context.getString(R.string.gesture_action_launch_app)
    } else {
        val base = resolveAppDisplayName(context, packageName)
        val labeled = if (cloneUserId > 0) {
            com.slideindex.app.overlay.CloneAppsIndex.labelForUser(context, cloneUserId, base)
        } else {
            base
        }
        context.getString(
            R.string.gesture_action_launch_app_named,
            labeled,
        )
    }

fun filterGestureActions(
    context: Context,
    actions: List<GestureAction>,
    query: String,
): List<GestureAction> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return actions
    return actions.filter { action ->
        val label = gestureActionLabelText(context, action)
        val aliases = gestureActionSearchAliases(context, action)
        label.lowercase().contains(q) ||
            PinyinHelper.sortKey(label).contains(q) ||
            aliases.any { it.contains(q) || PinyinHelper.sortKey(it).contains(q) } ||
            gestureActionDescriptionText(context, action)?.lowercase()?.contains(q) == true
    }
}

fun gestureActionSearchAliases(context: Context, action: GestureAction): List<String> = when (action.type) {
    GestureActionType.QUICK_MENU -> listOf(
        context.getString(R.string.gesture_search_quick_menu_1),
        "quick menu",
        "toolbox",
        "utilities",
        context.getString(R.string.gesture_search_quick_menu_2),
        "calculator",
        "converter",
        "qr code",
        "password",
        "stopwatch",
    )
    GestureActionType.SHAKE_MENU -> listOf(
        context.getString(R.string.shake_menu_title),
        context.getString(R.string.shake_menu_action_subtitle),
        "shake menu",
        "menu",
        "layer",
    )
    GestureActionType.APP_CAROUSEL_SWITCHER -> listOf(
        context.getString(R.string.gesture_search_carousel_1),
        "app switcher",
        context.getString(R.string.gesture_search_carousel_2),
        context.getString(R.string.gesture_search_carousel_3),
        context.getString(R.string.gesture_search_carousel_4),
        context.getString(R.string.gesture_search_carousel_5),
        "carousel",
    )
    GestureActionType.FINGERTIP_RING -> listOf(
        context.getString(R.string.gesture_search_fingertip_1),
        "fingertip ring",
        "fingertip dial",
        "dial",
        context.getString(R.string.gesture_search_fingertip_2),
        context.getString(R.string.gesture_search_fingertip_3),
        "radial",
        "ring menu",
    )
    GestureActionType.SCREEN_OFF_KEEP_AWAKE -> listOf(
        context.getString(R.string.gesture_search_screen_off_1),
        context.getString(R.string.gesture_search_screen_off_2),
        context.getString(R.string.gesture_search_screen_off_3),
        context.getString(R.string.gesture_search_screen_off_4),
        context.getString(R.string.gesture_search_screen_off_5),
        "screen off",
    )
    GestureActionType.TOUCH_BLOCK -> listOf(
        context.getString(R.string.gesture_search_touch_block_1),
        context.getString(R.string.gesture_search_touch_block_2),
        "touch block",
        "block touch",
        "lock touch",
        "circle",
    )
    GestureActionType.SCREEN_CONTROL -> listOf(
        context.getString(R.string.gesture_action_screen_control),
        "screen control",
        "touchpad",
        "trackpad",
        "pointer",
        "remote control",
    )
    GestureActionType.SCREEN_DRAW -> listOf(
        context.getString(R.string.gesture_action_screen_draw),
        "screen draw",
        "draw",
        "annotate",
        "annotation",
        "doodle",
    )
    // 两种叫法都吃下（「相册图片编辑」是正式名，老直觉是「图片编辑」/「编辑图片」）。
    GestureActionType.GALLERY_IMAGE_EDIT -> listOf(
        context.getString(R.string.gesture_action_gallery_image_edit),
        "gallery",
        "photo editor",
        "image editor",
        "edit photo",
        "图片编辑",
        "编辑图片",
    )
    GestureActionType.WECHAT_SCAN -> listOf(
        context.getString(R.string.gesture_action_wechat_scan),
        "wechat",
        "wechat scan",
        "weixin",
    )
    GestureActionType.WECHAT_PAY_CODE -> listOf(
        context.getString(R.string.gesture_action_wechat_pay_code),
        "wechat",
        "wechat pay",
        "pay code",
        "收款码",
    )
    GestureActionType.ALIPAY_PAY_CODE -> listOf(
        context.getString(R.string.gesture_action_alipay_pay_code),
        "alipay",
        "alipay pay",
        "pay code",
    )
    GestureActionType.ALIPAY_SCAN -> listOf(
        context.getString(R.string.gesture_action_alipay_scan),
        "alipay",
        "alipay scan",
        "scan",
    )
    GestureActionType.PIN_TO_SCREEN -> listOf(
        context.getString(R.string.gesture_search_pin_1),
        context.getString(R.string.gesture_search_pin_2),
        context.getString(R.string.gesture_search_pin_3),
        context.getString(R.string.gesture_search_pin_4),
        "pin",
        context.getString(R.string.gesture_search_pin_5),
    )
    GestureActionType.FOREGROUND_ACTIVITY_INSPECTOR -> listOf(
        context.getString(R.string.gesture_search_fg_activity_1),
        context.getString(R.string.gesture_search_fg_activity_2),
        context.getString(R.string.gesture_search_fg_activity_3),
        "activity",
        context.getString(R.string.gesture_search_fg_activity_4),
        "activity inspector",
        "top activity",
        "foreground",
    )
    GestureActionType.CURRENT_APP_INFO -> listOf(
        context.getString(R.string.gesture_search_app_info_1),
        context.getString(R.string.gesture_search_app_info_2),
        "app info",
        context.getString(R.string.gesture_search_app_info_3),
    )
    GestureActionType.OPEN_INTERNET_PANEL -> listOf(
        context.getString(R.string.gesture_search_internet_1),
        context.getString(R.string.gesture_search_internet_2),
        "wifi",
        context.getString(R.string.gesture_search_internet_3),
        "internet panel",
    )
    GestureActionType.OPEN_VOLUME_PANEL -> listOf(
        context.getString(R.string.gesture_search_volume_1),
        context.getString(R.string.gesture_search_volume_2),
        context.getString(R.string.gesture_search_volume_3),
        "volume panel",
    )
    GestureActionType.OPEN_VOLUME_SLIDER -> listOf(
        context.getString(R.string.gesture_search_volume_1),
        context.getString(R.string.gesture_search_volume_2),
        context.getString(R.string.gesture_search_volume_3),
        "volume slider",
        "volume hud",
    )
    GestureActionType.VOLUME_UP -> listOf(
        context.getString(R.string.gesture_search_volume_1),
        context.getString(R.string.gesture_search_volume_2),
        context.getString(R.string.gesture_search_volume_3),
        "volume up",
    )
    GestureActionType.VOLUME_DOWN -> listOf(
        context.getString(R.string.gesture_search_volume_1),
        context.getString(R.string.gesture_search_volume_2),
        context.getString(R.string.gesture_search_volume_3),
        "volume down",
    )
    GestureActionType.FORCE_STOP_CURRENT_APP -> listOf(
        context.getString(R.string.gesture_search_force_stop_1),
        context.getString(R.string.gesture_search_force_stop_2),
        context.getString(R.string.gesture_search_force_stop_3),
        "force stop",
        "kill app",
        "kill",
    )
    GestureActionType.SIMULATE_KEY_EVENT -> listOf(
        context.getString(R.string.gesture_search_keyevent_1),
        context.getString(R.string.gesture_search_keyevent_2),
        "keyevent",
        "input keyevent",
    )
    GestureActionType.TOGGLE_FLOAT_BALL -> listOf(
        context.getString(R.string.gesture_search_float_ball_hide),
        context.getString(R.string.gesture_search_float_ball_show),
        "float ball",
    )
    // 中文界面里搜「bluetooth」「near field」也能找到这两个开关（标签本身只有中/本地化文案）。
    GestureActionType.TOGGLE_NFC -> listOf("near field", "nfc toggle")
    GestureActionType.TOGGLE_BLUETOOTH -> listOf("bluetooth", "bt")
    else -> emptyList()
}

fun gestureActionDescriptionText(context: Context, action: GestureAction): String? =
    when (action.type) {
        GestureActionType.APP_CAROUSEL_SWITCHER -> context.getString(R.string.gesture_action_app_carousel_switcher_desc)
    GestureActionType.QUICK_MENU -> context.getString(R.string.gesture_action_quick_menu_desc)
        GestureActionType.FINGERTIP_RING -> context.getString(R.string.gesture_action_fingertip_ring_desc)
        GestureActionType.SCREEN_OFF_KEEP_AWAKE -> context.getString(R.string.gesture_action_screen_off_keep_awake_desc)
        GestureActionType.TOUCH_BLOCK -> context.getString(R.string.gesture_action_touch_block_desc)
        GestureActionType.SCREEN_CONTROL -> context.getString(R.string.gesture_action_screen_control_desc)
        GestureActionType.SCREEN_DRAW -> context.getString(R.string.gesture_action_screen_draw_desc)
        GestureActionType.GALLERY_IMAGE_EDIT -> context.getString(R.string.gesture_action_gallery_image_edit_desc)
        GestureActionType.WECHAT_SCAN -> context.getString(R.string.gesture_action_wechat_scan_desc)
        GestureActionType.WECHAT_PAY_CODE -> context.getString(R.string.gesture_action_wechat_pay_code_desc)
        GestureActionType.ALIPAY_PAY_CODE -> context.getString(R.string.gesture_action_alipay_pay_code_desc)
        GestureActionType.ALIPAY_SCAN -> context.getString(R.string.gesture_action_alipay_scan_desc)
        GestureActionType.PIN_TO_SCREEN -> context.getString(R.string.gesture_action_pin_to_screen_desc)
        GestureActionType.FOREGROUND_ACTIVITY_INSPECTOR -> context.getString(R.string.gesture_action_foreground_activity_inspector_desc)
        GestureActionType.ADJUST_VOLUME -> context.getString(R.string.gesture_action_adjust_volume_desc)
        GestureActionType.ADJUST_BRIGHTNESS -> context.getString(R.string.gesture_action_adjust_brightness_desc)
        GestureActionType.TOGGLE_AUTO_BRIGHTNESS -> context.getString(R.string.gesture_action_toggle_auto_brightness_desc)
        GestureActionType.TOGGLE_AUTO_ROTATE -> context.getString(R.string.gesture_action_toggle_auto_rotate_desc)
        GestureActionType.FORCE_PORTRAIT -> context.getString(R.string.gesture_action_force_portrait_desc)
        GestureActionType.FORCE_LANDSCAPE -> context.getString(R.string.gesture_action_force_landscape_desc)
        GestureActionType.VOICE_SEARCH -> context.getString(R.string.gesture_action_voice_search_desc)
        GestureActionType.VOICE_ASSISTANT -> context.getString(R.string.gesture_action_voice_assistant_desc)
        GestureActionType.SCROLL_TO_TOP -> context.getString(R.string.gesture_action_scroll_to_top_desc)
        GestureActionType.SCROLL_TO_BOTTOM -> context.getString(R.string.gesture_action_scroll_to_bottom_desc)
        GestureActionType.FULLSCREEN_SCREENSHOT_PICK -> context.getString(R.string.gesture_action_fullscreen_screenshot_pick_desc)
        GestureActionType.REGIONAL_SCREENSHOT_PICK -> context.getString(R.string.gesture_action_regional_screenshot_pick_desc)
        GestureActionType.SEARCH_PANEL -> context.getString(R.string.gesture_action_search_panel_desc)
        GestureActionType.FILE_BROWSER -> context.getString(R.string.file_browser_entry_desc)
        GestureActionType.OPEN_LINK -> context.getString(R.string.gesture_action_open_link_desc)
        GestureActionType.VOLUME_PANEL -> context.getString(R.string.gesture_action_volume_panel_desc)
        GestureActionType.SCREEN_TRANSLATE -> context.getString(R.string.gesture_action_screen_translate_desc)
        GestureActionType.REMIND -> context.getString(R.string.gesture_action_remind_desc)
        GestureActionType.TIMED_DND -> context.getString(R.string.gesture_action_timed_dnd_desc)
        GestureActionType.UNIVERSAL_COPY -> context.getString(R.string.gesture_action_universal_copy_desc)
        GestureActionType.SCREEN_SEARCH -> context.getString(R.string.gesture_action_screen_search_desc)
        GestureActionType.SMART_SCREENSHOT -> context.getString(R.string.gesture_action_smart_screenshot_desc)
        GestureActionType.FREEZER_PANEL -> context.getString(R.string.gesture_action_freezer_panel_desc)
        GestureActionType.REFREEZE -> context.getString(R.string.gesture_action_refreeze_desc)
        GestureActionType.CLIPBOARD_PICK -> context.getString(R.string.gesture_action_clipboard_pick_desc)
        GestureActionType.CLIPBOARD_PASTE -> context.getString(R.string.gesture_action_clipboard_paste_desc)
        GestureActionType.POINTER_REALTIME_GESTURE -> context.getString(R.string.gesture_action_pointer_realtime_gesture_desc)
        GestureActionType.TOGGLE_MUTE -> context.getString(R.string.gesture_action_toggle_mute_desc)
        GestureActionType.TOGGLE_SHAKE_GESTURE -> context.getString(R.string.gesture_action_toggle_shake_desc)
        GestureActionType.TOGGLE_FLOAT_BALL -> context.getString(R.string.gesture_action_toggle_float_ball_desc)
        GestureActionType.LOCK_SCREEN_AND_SILENCE_RING -> context.getString(R.string.gesture_action_lock_screen_and_silence_ring_desc)
        GestureActionType.LOCK_SCREEN_AND_MUTE_ALL -> context.getString(R.string.gesture_action_lock_screen_and_mute_all_desc)
        GestureActionType.SNOOZE_OVERLAYS -> context.getString(R.string.gesture_action_snooze_overlays_desc)
        GestureActionType.OPEN_INTERNET_PANEL -> context.getString(R.string.gesture_action_open_internet_panel_desc)
        GestureActionType.OPEN_VOLUME_PANEL -> context.getString(R.string.gesture_action_open_volume_panel_desc)
        GestureActionType.OPEN_VOLUME_SLIDER -> context.getString(R.string.gesture_action_open_volume_slider_desc)
        GestureActionType.VOLUME_UP -> context.getString(R.string.gesture_action_volume_up_desc)
        GestureActionType.VOLUME_DOWN -> context.getString(R.string.gesture_action_volume_down_desc)
        GestureActionType.FORCE_STOP_CURRENT_APP -> context.getString(R.string.gesture_action_force_stop_current_app_desc)
        GestureActionType.CURRENT_APP_INFO -> context.getString(R.string.gesture_action_current_app_info_desc)
        GestureActionType.SIMULATE_KEY_EVENT -> context.getString(R.string.gesture_action_simulate_key_event_desc)
        else -> null
    }

fun launchShortcutDisplayLabel(action: GestureAction.LaunchShortcut): String =
    action.label.ifBlank {
        GestureShortcutPayload.decode(action.payloadKey)?.label.orEmpty()
    }

fun gestureActionLabelText(context: Context, action: GestureAction): String = when (action) {
    is GestureAction.LaunchApp -> launchAppActionLabel(context, action.packageName, action.cloneUserId)
    is GestureAction.LaunchShortcut -> {
        val shortcutLabel = launchShortcutDisplayLabel(action)
        if (shortcutLabel.isBlank()) {
            context.getString(R.string.gesture_action_launch_shortcut)
        } else {
            context.getString(R.string.gesture_action_launch_shortcut_named, shortcutLabel)
        }
    }
    is GestureAction.ExecuteShellCommand -> {
        if (action.command.isBlank()) {
            context.getString(R.string.gesture_action_execute_shell_command)
        } else {
            context.getString(
                R.string.gesture_action_execute_shell_command_named,
                gestureExecuteShellCommandPreview(action.command),
            )
        }
    }
    is GestureAction.OpenLink -> {
        val display = action.label.ifBlank { action.url }
        if (display.isBlank()) {
            context.getString(R.string.gesture_action_open_link)
        } else {
            display
        }
    }
    is GestureAction.SimulateKeyEvent -> {
        val name = if (action.keyName.isNotBlank()) action.keyName else com.slideindex.app.gesture.KeyEventPresets.getDisplayName(context, action.keyCode)
        context.getString(R.string.gesture_action_simulate_key_event_named, name)
    }
    else -> when (action.type) {
        GestureActionType.RUN_MACRO -> {
            // 显示"运行宏：<宏名>"；宏被删掉时给一个明确的失效文案，而不是空字符串。
            val macroId = (action as? GestureAction.RunMacro)?.macroId.orEmpty()
            val macroName = com.slideindex.app.macro.MacroStore.byId(context, macroId)?.name
            if (macroName.isNullOrBlank()) {
                context.getString(R.string.macro_action_run_macro_missing)
            } else {
                context.getString(R.string.macro_action_run_macro_named, macroName)
            }
        }
        GestureActionType.PAGE_SCROLL -> context.getString(R.string.page_scroll_action_name)
        GestureActionType.NONE -> context.getString(R.string.gesture_action_none)
        GestureActionType.OPEN_INDEX -> context.getString(R.string.gesture_action_open_index)
        GestureActionType.QUICK_LAUNCHER -> context.getString(R.string.gesture_action_quick_launcher)
        GestureActionType.HONEYCOMB_LAUNCHER -> context.getString(R.string.gesture_action_honeycomb_launcher)
        GestureActionType.APP_RING_LAUNCHER -> context.getString(R.string.gesture_action_ring_launcher)
        GestureActionType.APP_CAROUSEL_SWITCHER -> context.getString(R.string.gesture_action_app_carousel_switcher)
        GestureActionType.FINGERTIP_RING -> context.getString(R.string.gesture_action_fingertip_ring)
        GestureActionType.HOLOGRAPHIC_LAUNCHER -> context.getString(R.string.gesture_action_holographic_launcher)
        GestureActionType.TASK_SWITCHER -> context.getString(R.string.gesture_action_task_switcher)
        GestureActionType.BACK -> context.getString(R.string.gesture_action_back)
        GestureActionType.HOME -> context.getString(R.string.gesture_action_home)
        GestureActionType.RECENTS -> context.getString(R.string.gesture_action_recents)
        GestureActionType.CLOSE_CURRENT_APP -> context.getString(R.string.gesture_action_close_current_app)
        GestureActionType.FORCE_STOP_CURRENT_APP -> context.getString(R.string.gesture_action_force_stop_current_app)
        GestureActionType.FREE_WINDOW_CURRENT_APP -> context.getString(R.string.gesture_action_free_window_current_app)
        GestureActionType.CLICK_PASSTHROUGH -> context.getString(R.string.gesture_action_click_passthrough)
        GestureActionType.FLASHLIGHT -> context.getString(R.string.gesture_action_flashlight)
        GestureActionType.ADJUST_VOLUME -> context.getString(R.string.gesture_action_adjust_volume)
        GestureActionType.ADJUST_BRIGHTNESS -> context.getString(R.string.gesture_action_adjust_brightness)
        GestureActionType.TOGGLE_AUTO_BRIGHTNESS -> context.getString(R.string.gesture_action_toggle_auto_brightness)
        GestureActionType.LAUNCH_ASSISTANT -> context.getString(R.string.gesture_action_launch_assistant)
        GestureActionType.VOICE_SEARCH -> context.getString(R.string.gesture_action_voice_search)
        GestureActionType.VOICE_ASSISTANT -> context.getString(R.string.gesture_action_voice_assistant)
        GestureActionType.TOGGLE_AUTO_ROTATE -> context.getString(R.string.gesture_action_toggle_auto_rotate)
        GestureActionType.FORCE_PORTRAIT -> context.getString(R.string.gesture_action_force_portrait)
        GestureActionType.FORCE_LANDSCAPE -> context.getString(R.string.gesture_action_force_landscape)
        GestureActionType.TOGGLE_MUTE -> context.getString(R.string.gesture_action_toggle_mute)
        GestureActionType.MEDIA_PLAY_PAUSE -> context.getString(R.string.gesture_action_media_play_pause)
        GestureActionType.MEDIA_PREVIOUS -> context.getString(R.string.gesture_action_media_previous)
        GestureActionType.MEDIA_NEXT -> context.getString(R.string.gesture_action_media_next)
        GestureActionType.PREVIOUS_APP -> context.getString(R.string.gesture_action_previous_app)
        GestureActionType.OPEN_NOTIFICATIONS -> context.getString(R.string.gesture_action_open_notifications)
        GestureActionType.READ_LATEST_MESSAGE -> context.getString(R.string.gesture_action_read_message)
        GestureActionType.READ_LATEST_MESSAGE_SMALL_WINDOW ->
            context.getString(R.string.gesture_action_read_message_small_window)
        GestureActionType.CLEAR_NOTIFICATIONS -> context.getString(R.string.gesture_action_clear_notifications)
        GestureActionType.OPEN_QUICK_SETTINGS -> context.getString(R.string.gesture_action_open_quick_settings)
        GestureActionType.LOCK_SCREEN -> context.getString(R.string.gesture_action_lock_screen)
        GestureActionType.LOCK_SCREEN_AND_SILENCE_RING -> context.getString(R.string.gesture_action_lock_screen_and_silence_ring)
        GestureActionType.LOCK_SCREEN_AND_MUTE_ALL -> context.getString(R.string.gesture_action_lock_screen_and_mute_all)
        GestureActionType.SCREENSHOT -> context.getString(R.string.gesture_action_screenshot)
        GestureActionType.FULLSCREEN_SCREENSHOT_PICK -> context.getString(R.string.gesture_action_fullscreen_screenshot_pick)
        GestureActionType.REGIONAL_SCREENSHOT_PICK -> context.getString(R.string.gesture_action_regional_screenshot_pick)
        GestureActionType.SEARCH_PANEL -> context.getString(R.string.gesture_action_search_panel)
        GestureActionType.FILE_BROWSER -> context.getString(R.string.file_browser_title)
        GestureActionType.OPEN_LINK -> context.getString(R.string.gesture_action_open_link)
        GestureActionType.VOLUME_PANEL -> context.getString(R.string.gesture_action_volume_panel)
        GestureActionType.VOLUME_UP -> context.getString(R.string.gesture_action_volume_up)
        GestureActionType.VOLUME_DOWN -> context.getString(R.string.gesture_action_volume_down)
        GestureActionType.SCREEN_TRANSLATE -> context.getString(R.string.gesture_action_screen_translate)
        GestureActionType.REMIND,
        GestureActionType.REMIND_1M,
        GestureActionType.REMIND_3M,
        GestureActionType.REMIND_5M,
        GestureActionType.REMIND_10M,
        GestureActionType.REMIND_15M,
        -> context.getString(R.string.gesture_action_remind)
        GestureActionType.UNIVERSAL_COPY -> context.getString(R.string.gesture_action_universal_copy)
        GestureActionType.SCREEN_SEARCH -> context.getString(R.string.gesture_action_screen_search)
        GestureActionType.SMART_SCREENSHOT -> context.getString(R.string.gesture_action_smart_screenshot)
        GestureActionType.FREEZER_PANEL -> context.getString(R.string.gesture_action_freezer_panel)
        GestureActionType.REFREEZE -> context.getString(R.string.gesture_action_refreeze)
        GestureActionType.POWER_MENU -> context.getString(R.string.gesture_action_power_menu)
        GestureActionType.KEEP_SCREEN_ON -> context.getString(R.string.gesture_action_keep_screen_on)
        GestureActionType.SNOOZE_OVERLAYS -> context.getString(R.string.gesture_action_snooze_overlays)
        GestureActionType.SCROLL_TO_TOP -> context.getString(R.string.gesture_action_scroll_to_top)
        GestureActionType.SCROLL_TO_BOTTOM -> context.getString(R.string.gesture_action_scroll_to_bottom)
        GestureActionType.SHELL_COMMAND_PANEL -> context.getString(R.string.gesture_action_shell_command_panel)
        GestureActionType.EXECUTE_SHELL_COMMAND -> context.getString(R.string.gesture_action_execute_shell_command)
        GestureActionType.QUICK_TOOLS_OVERLAY -> context.getString(R.string.gesture_action_quick_tools_overlay)
    GestureActionType.QUICK_MENU -> context.getString(R.string.gesture_action_quick_menu)
        GestureActionType.WIDGET_POPUP_OVERLAY -> context.getString(R.string.gesture_action_widget_popup_overlay)
        GestureActionType.OPEN_STASH_PANEL -> context.getString(R.string.gesture_action_stash_panel)
        GestureActionType.OPEN_CLIPBOARD_PANEL -> context.getString(R.string.gesture_action_clipboard_panel)
        GestureActionType.OPEN_CLIPBOARD_FLOAT -> context.getString(R.string.gesture_action_clipboard_float)
        GestureActionType.CLIPBOARD_PICK -> context.getString(R.string.gesture_action_clipboard_pick)
        GestureActionType.CLIPBOARD_PASTE -> context.getString(R.string.gesture_action_clipboard_paste)
        GestureActionType.FLOATING_POINTER -> context.getString(R.string.gesture_action_floating_pointer)
        GestureActionType.SIMULATE_POINTER_SWIPE -> context.getString(R.string.gesture_action_pointer_swipe)
        GestureActionType.POINTER_GESTURE_RECORDER -> context.getString(R.string.gesture_action_pointer_gesture_recorder)
        GestureActionType.POINTER_REALTIME_GESTURE -> context.getString(R.string.gesture_action_pointer_realtime_gesture)
        GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU -> context.getString(R.string.gesture_action_open_floating_pointer_radial_menu)
        GestureActionType.TOGGLE_DND -> context.getString(R.string.gesture_action_toggle_dnd)
        GestureActionType.TIMED_DND -> context.getString(R.string.gesture_action_timed_dnd)
        GestureActionType.SCREEN_RECORD -> context.getString(R.string.gesture_action_screen_record)
        GestureActionType.TOGGLE_WIFI -> context.getString(R.string.gesture_action_toggle_wifi)
        GestureActionType.TOGGLE_SHAKE_GESTURE -> context.getString(R.string.gesture_action_toggle_shake)
        GestureActionType.TOGGLE_FLOAT_BALL -> context.getString(R.string.gesture_action_toggle_float_ball)
        GestureActionType.TOGGLE_MOBILE_DATA -> context.getString(R.string.gesture_action_toggle_mobile_data)
        GestureActionType.TOGGLE_NFC -> context.getString(R.string.gesture_action_toggle_nfc)
        GestureActionType.TOGGLE_BLUETOOTH -> context.getString(R.string.gesture_action_toggle_bluetooth)
        GestureActionType.SWITCH_INPUT_METHOD -> context.getString(R.string.gesture_action_switch_input_method)
        GestureActionType.OPEN_INTERNET_PANEL -> context.getString(R.string.gesture_action_open_internet_panel)
        GestureActionType.OPEN_VOLUME_PANEL -> context.getString(R.string.gesture_action_open_volume_panel)
        GestureActionType.OPEN_VOLUME_SLIDER -> context.getString(R.string.gesture_action_open_volume_slider)
        GestureActionType.CURRENT_APP_INFO -> context.getString(R.string.gesture_action_current_app_info)
        GestureActionType.SCREEN_OFF_KEEP_AWAKE -> context.getString(R.string.gesture_action_screen_off_keep_awake)
        GestureActionType.TOUCH_BLOCK -> context.getString(R.string.gesture_action_touch_block)
        GestureActionType.SCREEN_CONTROL -> context.getString(R.string.gesture_action_screen_control)
        GestureActionType.SCREEN_DRAW -> context.getString(R.string.gesture_action_screen_draw)
        GestureActionType.GALLERY_IMAGE_EDIT -> context.getString(R.string.gesture_action_gallery_image_edit)
        GestureActionType.WECHAT_SCAN -> context.getString(R.string.gesture_action_wechat_scan)
        GestureActionType.WECHAT_PAY_CODE -> context.getString(R.string.gesture_action_wechat_pay_code)
        GestureActionType.ALIPAY_PAY_CODE -> context.getString(R.string.gesture_action_alipay_pay_code)
        GestureActionType.ALIPAY_SCAN -> context.getString(R.string.gesture_action_alipay_scan)
        GestureActionType.PIN_TO_SCREEN -> context.getString(R.string.gesture_action_pin_to_screen)
        GestureActionType.FOREGROUND_ACTIVITY_INSPECTOR -> context.getString(R.string.gesture_action_foreground_activity_inspector)
        GestureActionType.SIMULATE_KEY_EVENT -> context.getString(R.string.gesture_action_simulate_key_event)
        GestureActionType.CORNER_INNER_CANCEL -> context.getString(R.string.gesture_action_corner_inner_cancel)
        GestureActionType.CORNER_INNER_PIN_WHEEL -> context.getString(R.string.gesture_action_corner_inner_pin_wheel)
        GestureActionType.LAUNCH_APP -> context.getString(R.string.gesture_action_launch_app)
        GestureActionType.LAUNCH_SHORTCUT -> context.getString(R.string.gesture_action_launch_shortcut)
        GestureActionType.QUICK_WHEEL -> context.getString(R.string.gesture_action_quick_wheel)
        GestureActionType.SHAKE_MENU -> context.getString(R.string.shake_menu_title)
    }
}

fun gestureActionSortKey(context: Context, action: GestureAction): String =
    PinyinHelper.sortKey(gestureActionLabelText(context, action))

@Composable
fun gestureActionLabel(action: GestureAction, settings: AppSettings? = null): String {
    val context = LocalContext.current
    val appRepository = rememberAppRepository()
    return when (action) {
    is GestureAction.LaunchApp -> {
        if (action.packageName.isBlank()) {
            stringResource(R.string.gesture_action_launch_app)
        } else {
            val appLabel = appRepository.ensureAppInfo(action.packageName)?.label
                ?: resolveAppDisplayName(LocalContext.current, action.packageName)
            stringResource(R.string.gesture_action_launch_app_named, appLabel)
        }
    }
    is GestureAction.LaunchShortcut -> {
        val label = launchShortcutDisplayLabel(action)
        if (label.isBlank()) {
            stringResource(R.string.gesture_action_launch_shortcut)
        } else {
            label
        }
    }
    is GestureAction.QuickLauncher -> {
        if (action.panelId.isBlank() || settings == null) {
            stringResource(R.string.gesture_action_quick_launcher)
        } else {
            stringResource(
                R.string.gesture_action_quick_launcher_named,
                quickLauncherPanelLabel(settings, action.panelId),
            )
        }
    }
    is GestureAction.ExecuteShellCommand -> {
        if (action.command.isBlank()) {
            stringResource(R.string.gesture_action_execute_shell_command)
        } else {
            stringResource(
                R.string.gesture_action_execute_shell_command_named,
                gestureExecuteShellCommandPreview(action.command),
            )
        }
    }
    is GestureAction.SimulateKeyEvent -> {
        if (action.keyName.isBlank() || (action.keyCode == 82 && action.keyName == "KEYCODE_MENU")) {
            stringResource(R.string.gesture_action_simulate_key_event)
        } else {
            val name = if (action.keyName.isNotBlank()) action.keyName else com.slideindex.app.gesture.KeyEventPresets.getDisplayName(context, action.keyCode)
            stringResource(R.string.gesture_action_simulate_key_event_named, name)
        }
    }
    else -> when (action.type) {
        GestureActionType.RUN_MACRO -> stringResource(R.string.macro_action_run_macro)
        GestureActionType.PAGE_SCROLL -> stringResource(R.string.page_scroll_action_name)
        GestureActionType.NONE -> stringResource(R.string.gesture_action_none)
        GestureActionType.OPEN_INDEX -> stringResource(R.string.gesture_action_open_index)
        GestureActionType.QUICK_LAUNCHER -> stringResource(R.string.gesture_action_quick_launcher)
        GestureActionType.HONEYCOMB_LAUNCHER -> stringResource(R.string.gesture_action_honeycomb_launcher)
        GestureActionType.APP_RING_LAUNCHER -> stringResource(R.string.gesture_action_ring_launcher)
        GestureActionType.APP_CAROUSEL_SWITCHER -> stringResource(R.string.gesture_action_app_carousel_switcher)
        GestureActionType.FINGERTIP_RING -> stringResource(R.string.gesture_action_fingertip_ring)
        GestureActionType.HOLOGRAPHIC_LAUNCHER -> stringResource(R.string.gesture_action_holographic_launcher)
        GestureActionType.TASK_SWITCHER -> stringResource(R.string.gesture_action_task_switcher)
        GestureActionType.BACK -> stringResource(R.string.gesture_action_back)
        GestureActionType.HOME -> stringResource(R.string.gesture_action_home)
        GestureActionType.RECENTS -> stringResource(R.string.gesture_action_recents)
        GestureActionType.CLOSE_CURRENT_APP -> stringResource(R.string.gesture_action_close_current_app)
        GestureActionType.FORCE_STOP_CURRENT_APP -> stringResource(R.string.gesture_action_force_stop_current_app)
        GestureActionType.FREE_WINDOW_CURRENT_APP -> stringResource(R.string.gesture_action_free_window_current_app)
        GestureActionType.CLICK_PASSTHROUGH -> stringResource(R.string.gesture_action_click_passthrough)
        GestureActionType.FLASHLIGHT -> stringResource(R.string.gesture_action_flashlight)
        GestureActionType.ADJUST_VOLUME -> stringResource(R.string.gesture_action_adjust_volume)
        GestureActionType.ADJUST_BRIGHTNESS -> stringResource(R.string.gesture_action_adjust_brightness)
        GestureActionType.TOGGLE_AUTO_BRIGHTNESS -> stringResource(R.string.gesture_action_toggle_auto_brightness)
        GestureActionType.LAUNCH_ASSISTANT -> stringResource(R.string.gesture_action_launch_assistant)
        GestureActionType.VOICE_SEARCH -> stringResource(R.string.gesture_action_voice_search)
        GestureActionType.VOICE_ASSISTANT -> stringResource(R.string.gesture_action_voice_assistant)
        GestureActionType.TOGGLE_AUTO_ROTATE -> stringResource(R.string.gesture_action_toggle_auto_rotate)
        GestureActionType.FORCE_PORTRAIT -> stringResource(R.string.gesture_action_force_portrait)
        GestureActionType.FORCE_LANDSCAPE -> stringResource(R.string.gesture_action_force_landscape)
        GestureActionType.TOGGLE_MUTE -> stringResource(R.string.gesture_action_toggle_mute)
        GestureActionType.MEDIA_PLAY_PAUSE -> stringResource(R.string.gesture_action_media_play_pause)
        GestureActionType.MEDIA_PREVIOUS -> stringResource(R.string.gesture_action_media_previous)
        GestureActionType.MEDIA_NEXT -> stringResource(R.string.gesture_action_media_next)
        GestureActionType.PREVIOUS_APP -> stringResource(R.string.gesture_action_previous_app)
        GestureActionType.OPEN_NOTIFICATIONS -> stringResource(R.string.gesture_action_open_notifications)
        GestureActionType.READ_LATEST_MESSAGE -> stringResource(R.string.gesture_action_read_message)
        GestureActionType.READ_LATEST_MESSAGE_SMALL_WINDOW ->
            stringResource(R.string.gesture_action_read_message_small_window)
        GestureActionType.CLEAR_NOTIFICATIONS -> stringResource(R.string.gesture_action_clear_notifications)
        GestureActionType.OPEN_QUICK_SETTINGS -> stringResource(R.string.gesture_action_open_quick_settings)
        GestureActionType.LOCK_SCREEN -> stringResource(R.string.gesture_action_lock_screen)
        GestureActionType.LOCK_SCREEN_AND_SILENCE_RING -> stringResource(R.string.gesture_action_lock_screen_and_silence_ring)
        GestureActionType.LOCK_SCREEN_AND_MUTE_ALL -> stringResource(R.string.gesture_action_lock_screen_and_mute_all)
        GestureActionType.SCREENSHOT -> stringResource(R.string.gesture_action_screenshot)
        GestureActionType.FULLSCREEN_SCREENSHOT_PICK -> stringResource(R.string.gesture_action_fullscreen_screenshot_pick)
        GestureActionType.REGIONAL_SCREENSHOT_PICK -> stringResource(R.string.gesture_action_regional_screenshot_pick)
        GestureActionType.SEARCH_PANEL -> stringResource(R.string.gesture_action_search_panel)
        GestureActionType.FILE_BROWSER -> stringResource(R.string.file_browser_title)
        GestureActionType.OPEN_LINK -> stringResource(R.string.gesture_action_open_link)
        GestureActionType.VOLUME_PANEL -> stringResource(R.string.gesture_action_volume_panel)
        GestureActionType.VOLUME_UP -> stringResource(R.string.gesture_action_volume_up)
        GestureActionType.VOLUME_DOWN -> stringResource(R.string.gesture_action_volume_down)
        GestureActionType.SCREEN_TRANSLATE -> stringResource(R.string.gesture_action_screen_translate)
        GestureActionType.REMIND,
        GestureActionType.REMIND_1M,
        GestureActionType.REMIND_3M,
        GestureActionType.REMIND_5M,
        GestureActionType.REMIND_10M,
        GestureActionType.REMIND_15M,
        -> stringResource(R.string.gesture_action_remind)
        GestureActionType.UNIVERSAL_COPY -> stringResource(R.string.gesture_action_universal_copy)
        GestureActionType.SCREEN_SEARCH -> stringResource(R.string.gesture_action_screen_search)
        GestureActionType.SMART_SCREENSHOT -> stringResource(R.string.gesture_action_smart_screenshot)
        GestureActionType.FREEZER_PANEL -> stringResource(R.string.gesture_action_freezer_panel)
        GestureActionType.REFREEZE -> stringResource(R.string.gesture_action_refreeze)
        GestureActionType.POWER_MENU -> stringResource(R.string.gesture_action_power_menu)
        GestureActionType.KEEP_SCREEN_ON -> stringResource(R.string.gesture_action_keep_screen_on)
        GestureActionType.SNOOZE_OVERLAYS -> stringResource(R.string.gesture_action_snooze_overlays)
        GestureActionType.SCROLL_TO_TOP -> stringResource(R.string.gesture_action_scroll_to_top)
        GestureActionType.SCROLL_TO_BOTTOM -> stringResource(R.string.gesture_action_scroll_to_bottom)
        GestureActionType.SHELL_COMMAND_PANEL -> stringResource(R.string.gesture_action_shell_command_panel)
        GestureActionType.EXECUTE_SHELL_COMMAND -> stringResource(R.string.gesture_action_execute_shell_command)
        GestureActionType.QUICK_TOOLS_OVERLAY -> stringResource(R.string.gesture_action_quick_tools_overlay)
    GestureActionType.QUICK_MENU -> stringResource(R.string.gesture_action_quick_menu)
        GestureActionType.WIDGET_POPUP_OVERLAY -> stringResource(R.string.gesture_action_widget_popup_overlay)
        GestureActionType.OPEN_STASH_PANEL -> stringResource(R.string.gesture_action_stash_panel)
        GestureActionType.OPEN_CLIPBOARD_PANEL -> stringResource(R.string.gesture_action_clipboard_panel)
        GestureActionType.OPEN_CLIPBOARD_FLOAT -> stringResource(R.string.gesture_action_clipboard_float)
        GestureActionType.CLIPBOARD_PICK -> stringResource(R.string.gesture_action_clipboard_pick)
        GestureActionType.CLIPBOARD_PASTE -> stringResource(R.string.gesture_action_clipboard_paste)
        GestureActionType.FLOATING_POINTER -> stringResource(R.string.gesture_action_floating_pointer)
        GestureActionType.SIMULATE_POINTER_SWIPE -> stringResource(R.string.gesture_action_pointer_swipe)
        GestureActionType.POINTER_GESTURE_RECORDER -> stringResource(R.string.gesture_action_pointer_gesture_recorder)
        GestureActionType.POINTER_REALTIME_GESTURE -> stringResource(R.string.gesture_action_pointer_realtime_gesture)
        GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU -> stringResource(R.string.gesture_action_open_floating_pointer_radial_menu)
        GestureActionType.TOGGLE_DND -> stringResource(R.string.gesture_action_toggle_dnd)
        GestureActionType.TIMED_DND -> stringResource(R.string.gesture_action_timed_dnd)
        GestureActionType.SCREEN_RECORD -> stringResource(R.string.gesture_action_screen_record)
        GestureActionType.TOGGLE_WIFI -> stringResource(R.string.gesture_action_toggle_wifi)
        GestureActionType.TOGGLE_SHAKE_GESTURE -> stringResource(R.string.gesture_action_toggle_shake)
        GestureActionType.TOGGLE_FLOAT_BALL -> stringResource(R.string.gesture_action_toggle_float_ball)
        GestureActionType.TOGGLE_MOBILE_DATA -> stringResource(R.string.gesture_action_toggle_mobile_data)
        GestureActionType.TOGGLE_NFC -> stringResource(R.string.gesture_action_toggle_nfc)
        GestureActionType.TOGGLE_BLUETOOTH -> stringResource(R.string.gesture_action_toggle_bluetooth)
        GestureActionType.SWITCH_INPUT_METHOD -> stringResource(R.string.gesture_action_switch_input_method)
        GestureActionType.OPEN_INTERNET_PANEL -> stringResource(R.string.gesture_action_open_internet_panel)
        GestureActionType.OPEN_VOLUME_PANEL -> stringResource(R.string.gesture_action_open_volume_panel)
        GestureActionType.OPEN_VOLUME_SLIDER -> stringResource(R.string.gesture_action_open_volume_slider)
        GestureActionType.CURRENT_APP_INFO -> stringResource(R.string.gesture_action_current_app_info)
        GestureActionType.SCREEN_OFF_KEEP_AWAKE -> stringResource(R.string.gesture_action_screen_off_keep_awake)
        GestureActionType.TOUCH_BLOCK -> stringResource(R.string.gesture_action_touch_block)
        GestureActionType.SCREEN_CONTROL -> stringResource(R.string.gesture_action_screen_control)
        GestureActionType.SCREEN_DRAW -> stringResource(R.string.gesture_action_screen_draw)
        GestureActionType.GALLERY_IMAGE_EDIT -> stringResource(R.string.gesture_action_gallery_image_edit)
        GestureActionType.WECHAT_SCAN -> stringResource(R.string.gesture_action_wechat_scan)
        GestureActionType.WECHAT_PAY_CODE -> stringResource(R.string.gesture_action_wechat_pay_code)
        GestureActionType.ALIPAY_PAY_CODE -> stringResource(R.string.gesture_action_alipay_pay_code)
        GestureActionType.ALIPAY_SCAN -> stringResource(R.string.gesture_action_alipay_scan)
        GestureActionType.PIN_TO_SCREEN -> stringResource(R.string.gesture_action_pin_to_screen)
        GestureActionType.FOREGROUND_ACTIVITY_INSPECTOR -> stringResource(R.string.gesture_action_foreground_activity_inspector)
        GestureActionType.SIMULATE_KEY_EVENT -> stringResource(R.string.gesture_action_simulate_key_event)
        GestureActionType.CORNER_INNER_CANCEL -> stringResource(R.string.gesture_action_corner_inner_cancel)
        GestureActionType.CORNER_INNER_PIN_WHEEL -> stringResource(R.string.gesture_action_corner_inner_pin_wheel)
        GestureActionType.LAUNCH_APP -> stringResource(R.string.gesture_action_launch_app)
        GestureActionType.LAUNCH_SHORTCUT -> stringResource(R.string.gesture_action_launch_shortcut)
        GestureActionType.QUICK_WHEEL -> stringResource(R.string.gesture_action_quick_wheel)
        GestureActionType.SHAKE_MENU -> stringResource(R.string.shake_menu_title)
    }
    }
}

@Composable
fun gestureActionSettingSubtitle(action: GestureAction): String {
    val context = LocalContext.current
    return when (action) {
        is GestureAction.ExecuteShellCommand -> {
            if (action.command.isBlank()) {
                stringResource(R.string.gesture_action_execute_shell_command)
            } else {
                stringResource(
                    R.string.gesture_action_execute_shell_command_named,
                    gestureExecuteShellCommandPreview(action.command, maxLength = 28),
                )
            }
        }
        is GestureAction.SimulateKeyEvent -> {
            val name = if (action.keyName.isNotBlank()) action.keyName else com.slideindex.app.gesture.KeyEventPresets.getDisplayName(context, action.keyCode)
            stringResource(R.string.gesture_action_simulate_key_event_named, name)
        }
        // 快捷轮盘不再暴露"哪个轮盘 / 形态方案"：摘要里只要知道"这个动作是调用轮盘"。
        // 快速启动器只额外标一句"钉住"（含"仅本应用"），因为那会明显改变呼出后的行为。
        is GestureAction.QuickLauncher -> {
            when {
                !action.pinned -> gestureActionLabel(action)
                action.pinCurrentApp -> stringResource(R.string.quick_launcher_action_pin_subtitle_current_app)
                else -> stringResource(R.string.quick_launcher_action_pin_subtitle)
            }
        }
        else -> gestureActionLabel(action)
    }
}

@Composable
fun gestureActionDescription(action: GestureAction): String? = when (action.type) {
    GestureActionType.ADJUST_VOLUME -> stringResource(R.string.gesture_action_adjust_volume_desc)
    GestureActionType.ADJUST_BRIGHTNESS -> stringResource(R.string.gesture_action_adjust_brightness_desc)
    GestureActionType.TOGGLE_AUTO_BRIGHTNESS -> stringResource(R.string.gesture_action_toggle_auto_brightness_desc)
    GestureActionType.SCROLL_TO_TOP -> stringResource(R.string.gesture_action_scroll_to_top_desc)
    GestureActionType.SCROLL_TO_BOTTOM -> stringResource(R.string.gesture_action_scroll_to_bottom_desc)
    GestureActionType.FULLSCREEN_SCREENSHOT_PICK -> stringResource(R.string.gesture_action_fullscreen_screenshot_pick_desc)
    GestureActionType.REGIONAL_SCREENSHOT_PICK -> stringResource(R.string.gesture_action_regional_screenshot_pick_desc)
    GestureActionType.SEARCH_PANEL -> stringResource(R.string.gesture_action_search_panel_desc)
    GestureActionType.FILE_BROWSER -> stringResource(R.string.file_browser_entry_desc)
    GestureActionType.OPEN_LINK -> stringResource(R.string.gesture_action_open_link_desc)
    GestureActionType.VOLUME_PANEL -> stringResource(R.string.gesture_action_volume_panel_desc)
    GestureActionType.VOLUME_UP -> stringResource(R.string.gesture_action_volume_up_desc)
    GestureActionType.VOLUME_DOWN -> stringResource(R.string.gesture_action_volume_down_desc)
    GestureActionType.FORCE_STOP_CURRENT_APP -> stringResource(R.string.gesture_action_force_stop_current_app_desc)
    GestureActionType.SCREEN_TRANSLATE -> stringResource(R.string.gesture_action_screen_translate_desc)
    GestureActionType.UNIVERSAL_COPY -> stringResource(R.string.gesture_action_universal_copy_desc)
    GestureActionType.SCREEN_SEARCH -> stringResource(R.string.gesture_action_screen_search_desc)
    GestureActionType.SMART_SCREENSHOT -> stringResource(R.string.gesture_action_smart_screenshot_desc)
    GestureActionType.FREEZER_PANEL -> stringResource(R.string.gesture_action_freezer_panel_desc)
    GestureActionType.REFREEZE -> stringResource(R.string.gesture_action_refreeze_desc)
    GestureActionType.CLIPBOARD_PICK -> stringResource(R.string.gesture_action_clipboard_pick_desc)
    GestureActionType.CLIPBOARD_PASTE -> stringResource(R.string.gesture_action_clipboard_paste_desc)
    GestureActionType.SIMULATE_POINTER_SWIPE -> stringResource(R.string.gesture_action_pointer_swipe_desc)
    GestureActionType.POINTER_GESTURE_RECORDER -> stringResource(R.string.gesture_action_pointer_gesture_recorder_desc)
    GestureActionType.POINTER_REALTIME_GESTURE -> stringResource(R.string.gesture_action_pointer_realtime_gesture_desc)
    GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU -> stringResource(R.string.gesture_action_open_floating_pointer_radial_menu_desc)
    GestureActionType.TOGGLE_MUTE -> stringResource(R.string.gesture_action_toggle_mute_desc)
    GestureActionType.TOGGLE_SHAKE_GESTURE -> stringResource(R.string.gesture_action_toggle_shake_desc)
    GestureActionType.TOGGLE_FLOAT_BALL -> stringResource(R.string.gesture_action_toggle_float_ball_desc)
    GestureActionType.LOCK_SCREEN_AND_SILENCE_RING -> stringResource(R.string.gesture_action_lock_screen_and_silence_ring_desc)
    GestureActionType.LOCK_SCREEN_AND_MUTE_ALL -> stringResource(R.string.gesture_action_lock_screen_and_mute_all_desc)
    GestureActionType.OPEN_INTERNET_PANEL -> stringResource(R.string.gesture_action_open_internet_panel_desc)
    GestureActionType.OPEN_VOLUME_PANEL -> stringResource(R.string.gesture_action_open_volume_panel_desc)
    GestureActionType.OPEN_VOLUME_SLIDER -> stringResource(R.string.gesture_action_open_volume_slider_desc)
    GestureActionType.CURRENT_APP_INFO -> stringResource(R.string.gesture_action_current_app_info_desc)
    GestureActionType.SCREEN_OFF_KEEP_AWAKE -> stringResource(R.string.gesture_action_screen_off_keep_awake_desc)
    GestureActionType.TOUCH_BLOCK -> stringResource(R.string.gesture_action_touch_block_desc)
    GestureActionType.SCREEN_CONTROL -> stringResource(R.string.gesture_action_screen_control_desc)
    GestureActionType.SCREEN_DRAW -> stringResource(R.string.gesture_action_screen_draw_desc)
    GestureActionType.GALLERY_IMAGE_EDIT -> stringResource(R.string.gesture_action_gallery_image_edit_desc)
    GestureActionType.WECHAT_SCAN -> stringResource(R.string.gesture_action_wechat_scan_desc)
    GestureActionType.WECHAT_PAY_CODE -> stringResource(R.string.gesture_action_wechat_pay_code_desc)
    GestureActionType.ALIPAY_PAY_CODE -> stringResource(R.string.gesture_action_alipay_pay_code_desc)
    GestureActionType.ALIPAY_SCAN -> stringResource(R.string.gesture_action_alipay_scan_desc)
    GestureActionType.PIN_TO_SCREEN -> stringResource(R.string.gesture_action_pin_to_screen_desc)
    GestureActionType.APP_CAROUSEL_SWITCHER -> stringResource(R.string.gesture_action_app_carousel_switcher_desc)
    GestureActionType.FINGERTIP_RING -> stringResource(R.string.gesture_action_fingertip_ring_desc)
    GestureActionType.SIMULATE_KEY_EVENT -> stringResource(R.string.gesture_action_simulate_key_event_desc)
    GestureActionType.CORNER_INNER_CANCEL -> stringResource(R.string.gesture_action_corner_inner_cancel_desc)
    GestureActionType.CORNER_INNER_PIN_WHEEL -> stringResource(R.string.gesture_action_corner_inner_pin_wheel_desc)
    GestureActionType.SNOOZE_OVERLAYS -> stringResource(R.string.gesture_action_snooze_overlays_desc)
    GestureActionType.TIMED_DND -> stringResource(R.string.gesture_action_timed_dnd_desc)
    GestureActionType.READ_LATEST_MESSAGE -> stringResource(R.string.gesture_action_read_message_desc)
    GestureActionType.READ_LATEST_MESSAGE_SMALL_WINDOW ->
        stringResource(R.string.gesture_action_read_message_small_window_desc)
    GestureActionType.CLEAR_NOTIFICATIONS -> stringResource(R.string.gesture_action_clear_notifications_desc)
    else -> null
}

fun gestureActionMinSdk(action: GestureAction): Int? = when (action.type) {
    GestureActionType.POINTER_REALTIME_GESTURE -> 36
    else -> null
}

fun isGestureActionEnabledOnDevice(action: GestureAction): Boolean {
    val minSdk = gestureActionMinSdk(action) ?: return true
    return Build.VERSION.SDK_INT >= minSdk
}

@Composable
fun gestureActionRequirementHint(action: GestureAction): String? = when (action.type) {
    GestureActionType.POINTER_REALTIME_GESTURE -> stringResource(R.string.gesture_action_require_min_sdk_36)
    else -> null
}

@Composable
fun gestureActionPermissionHint(action: GestureAction, context: Context): String? =
    gestureActionPermissionHintText(context, action)

fun gestureActionPermissionHintText(context: Context, action: GestureAction): String? =
    when (action.type) {
        GestureActionType.APP_CAROUSEL_SWITCHER -> {
            if (PermissionHelper.hasUsageAccess(context)) return null
            context.getString(R.string.gesture_action_app_carousel_switcher_permission)
        }
        GestureActionType.ADJUST_VOLUME -> {
            if (PermissionHelper.hasNotificationPolicyAccess(context)) return null
            context.getString(R.string.gesture_action_adjust_volume_permission)
        }
        GestureActionType.ADJUST_BRIGHTNESS,
        GestureActionType.TOGGLE_AUTO_BRIGHTNESS,
        GestureActionType.TOGGLE_AUTO_ROTATE,
        GestureActionType.FORCE_PORTRAIT,
        GestureActionType.FORCE_LANDSCAPE,
        -> {
            if (PermissionHelper.canWriteSettings(context) || TaskManagerUtil.hasPermission()) return null
            context.getString(R.string.gesture_action_adjust_brightness_permission)
        }
        GestureActionType.TOGGLE_MUTE -> {
            if (PermissionHelper.hasNotificationPolicyAccess(context)) return null
            context.getString(R.string.gesture_action_toggle_mute_permission)
        }
        GestureActionType.LOCK_SCREEN_AND_SILENCE_RING,
        GestureActionType.LOCK_SCREEN_AND_MUTE_ALL,
        -> {
            if (PermissionHelper.hasNotificationPolicyAccess(context)) return null
            context.getString(R.string.gesture_action_toggle_mute_permission)
        }
        GestureActionType.TOGGLE_DND -> {
            if (PermissionHelper.hasNotificationPolicyAccess(context)) return null
            context.getString(R.string.gesture_action_toggle_mute_permission)
        }
        GestureActionType.TIMED_DND -> {
            when {
                !PermissionHelper.canDrawOverlays(context) ->
                    context.getString(R.string.gesture_action_remind_permission)
                !PermissionHelper.hasNotificationPolicyAccess(context) ->
                    context.getString(R.string.gesture_action_toggle_mute_permission)
                else -> null
            }
        }
        GestureActionType.TOGGLE_WIFI, GestureActionType.TOGGLE_MOBILE_DATA,
        GestureActionType.TOGGLE_NFC, GestureActionType.TOGGLE_BLUETOOTH,
        GestureActionType.EXECUTE_SHELL_COMMAND,
        GestureActionType.FORCE_STOP_CURRENT_APP,
        -> {
            if (TaskManagerUtil.hasPermission()) return null
            context.getString(PrivilegeUiStrings.shellActionPermissionRes())
        }
        GestureActionType.FLASHLIGHT -> {
            if (PermissionHelper.hasCameraPermission(context)) {
                return null
            }
            context.getString(R.string.gesture_action_flashlight_permission)
        }
        GestureActionType.SCREEN_RECORD -> {
            if (PermissionHelper.canDrawOverlays(context)) return null
            context.getString(R.string.gesture_action_screen_record_permission)
        }
        GestureActionType.LOCK_SCREEN, GestureActionType.SCREENSHOT -> null
        GestureActionType.FULLSCREEN_SCREENSHOT_PICK -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_fullscreen_screenshot_pick_permission)
        }
        GestureActionType.REGIONAL_SCREENSHOT_PICK -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_regional_screenshot_pick_permission)
        }
        GestureActionType.SCROLL_TO_TOP, GestureActionType.SCROLL_TO_BOTTOM -> null
        GestureActionType.QUICK_TOOLS_OVERLAY -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_quick_tools_overlay_permission)
        }
        GestureActionType.QUICK_MENU -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_quick_menu_permission)
        }
        GestureActionType.HOLOGRAPHIC_LAUNCHER -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_quick_tools_overlay_permission)
        }
        GestureActionType.WIDGET_POPUP_OVERLAY -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_widget_popup_overlay_permission)
        }
        GestureActionType.OPEN_STASH_PANEL -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_stash_panel_permission)
        }
        GestureActionType.OPEN_CLIPBOARD_PANEL -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_clipboard_panel_permission)
        }
        GestureActionType.OPEN_CLIPBOARD_FLOAT -> {
            if (PermissionHelper.canDrawOverlays(context) &&
                PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)
            ) return null
            context.getString(R.string.gesture_action_clipboard_float_permission)
        }
        GestureActionType.CLIPBOARD_PICK -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_clipboard_pick_permission)
        }
        GestureActionType.CLIPBOARD_PASTE -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_clipboard_paste_permission)
        }
        GestureActionType.FLOATING_POINTER -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_floating_pointer_permission)
        }
        GestureActionType.SIMULATE_POINTER_SWIPE -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_pointer_swipe_permission)
        }
        GestureActionType.POINTER_GESTURE_RECORDER,
        GestureActionType.POINTER_REALTIME_GESTURE,
        -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_pointer_gesture_record_permission)
        }
        GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_open_floating_pointer_radial_menu_permission)
        }
        GestureActionType.REMIND,
        GestureActionType.REMIND_1M,
        GestureActionType.REMIND_3M,
        GestureActionType.REMIND_5M,
        GestureActionType.REMIND_10M,
        GestureActionType.REMIND_15M,
        -> {
            if (PermissionHelper.canDrawOverlays(context)) return null
            context.getString(R.string.gesture_action_remind_permission)
        }
        GestureActionType.SCREEN_CONTROL -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_screen_control_permission)
        }
        GestureActionType.SCREEN_DRAW -> {
            if (PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) return null
            context.getString(R.string.gesture_action_screen_draw_permission)
        }
        // 通知三件套都依赖「通知使用权」：没开时列表里直接说明，点提示可跳到授权页。
        GestureActionType.READ_LATEST_MESSAGE,
        GestureActionType.READ_LATEST_MESSAGE_SMALL_WINDOW,
        GestureActionType.CLEAR_NOTIFICATIONS,
        -> {
            if (PermissionHelper.hasNotificationAccess(context)) return null
            context.getString(R.string.gesture_action_notification_access_permission)
        }
        else -> null
    }

fun requestPermissionForAdjustAction(context: Context, action: GestureAction) {
    when (action) {
        GestureAction.AppCarouselSwitcher ->
            PermissionHelper.requestUsageAccess(context)
        GestureAction.AdjustVolume, GestureAction.ToggleMute, GestureAction.ToggleDnd,
        GestureAction.TimedDnd,
        GestureAction.LockScreenAndSilenceRing, GestureAction.LockScreenAndMuteAll,
        ->
            PermissionHelper.requestNotificationPolicyAccess(context)
        GestureAction.AdjustBrightness,
        GestureAction.ToggleAutoBrightness,
        GestureAction.ToggleAutoRotate,
        GestureAction.ForcePortrait,
        GestureAction.ForceLandscape,
        ->
            PermissionHelper.requestWriteSettingsAccess(context)
        GestureAction.Flashlight -> {
            // 已授权（含「仅在使用时允许」）时绝不跳转；仅缺失时请求，而不是把用户丢到应用信息页。
            com.slideindex.app.overlay.searchpanel.CameraPermissionTrampolineActivity.ensureGranted(context)
        }
        GestureAction.QuickToolsOverlay -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.QuickMenu -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.ScreenControl, GestureAction.ScreenDraw -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.HolographicLauncher -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.WidgetPopupOverlay -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.StashPanel -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.ClipboardPanel -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.ClipboardFloat -> {
            if (!PermissionHelper.canDrawOverlays(context)) {
                context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            } else if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.ClipboardPick -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.ClipboardPaste -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.FloatingPointer -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        is GestureAction.SimulatePointerSwipe -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.PointerGestureRecorder,
        GestureAction.PointerRealtimeGesture,
        -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.OpenFloatingPointerRadialMenu -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.ScreenRecord -> {
            if (!PermissionHelper.canDrawOverlays(context)) {
                context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            }
        }
        GestureAction.FullscreenScreenshotPick -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.RegionalScreenshotPick -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.SearchPanel -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.FileBrowser -> {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
            }
        }
        GestureAction.Remind -> {
            if (!PermissionHelper.canDrawOverlays(context)) {
                context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            }
        }
        GestureAction.TimedDnd -> {
            when {
                !PermissionHelper.canDrawOverlays(context) ->
                    context.startActivity(PermissionHelper.overlaySettingsIntent(context))
                !PermissionHelper.hasNotificationPolicyAccess(context) ->
                    PermissionHelper.requestNotificationPolicyAccess(context)
            }
        }
        GestureAction.ReadLatestMessage,
        GestureAction.ReadLatestMessageInSmallWindow,
        GestureAction.ClearNotifications,
        -> {
            if (!PermissionHelper.hasNotificationAccess(context)) {
                MediaSessionHelper.openNotificationListenerSettings(context)
            }
        }
        else -> Unit
    }
}
