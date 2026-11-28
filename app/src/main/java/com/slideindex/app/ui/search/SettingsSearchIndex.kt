package com.slideindex.app.ui.search

import androidx.annotation.StringRes
import com.slideindex.app.R
import com.slideindex.app.ui.MainBottomNavDestination
import com.slideindex.app.ui.navigation.AppNavKey

/**
 * 设置搜索索引里的一条「页面」。
 *
 * @param title 页面标题的字符串资源：用页面顶栏那份文案，不另造一份。
 * @param tab 该路由注册在哪个底部 tab 的返回栈里。搜索结果必须先切 tab 再入栈，
 *   否则 `NavDisplay` 会抛 `No entry … registered for`（见 `RegisteredNavEntries`）。
 * @param key 目标路由。只收无参的 `data object`：带参路由基本都是 picker / 详情页，
 *   脱离上下文单独打开没有意义，不适合作为搜索结果。
 * @param parent 上一级页面（由导航注册里的 `ctx.navigate(AppNavKey.X)` 反推）。
 *   搜索结果的副标题用"tab › 父页面 › 当前页面"的完整路径，就靠它串起来。
 * @param rows 该页里的设置行标题；命中行时同样跳到 [key] 所在页（本轮不做行内定位）。
 */
internal class SettingsSearchPage(
    @StringRes val title: Int,
    val tab: MainBottomNavDestination,
    val key: AppNavKey,
    val parent: AppNavKey? = null,
    val rows: List<SettingsSearchRow> = emptyList(),
)

/** 设置行的可搜索标题。 */
internal class SettingsSearchRow(@StringRes val title: Int)

/**
 * 全 App 设置页索引。
 *
 * 维护规则：
 * 1. 新增 / 删除设置页 → 同步增删这里的条目；
 * 2. 标题用页面顶栏那份 `R.string`，别另造文案；
 * 3. `parent` 只影响结果副标题的路径，填错不会跳错页；
 * 4. 跑 `python scripts/check-settings-search-index.py`，它会把"导航里注册了、索引里没有"
 *    的路由列出来（只校验路由覆盖率，标题对不对靠 review）。
 */
internal object SettingsSearchIndex {
    val pages: List<SettingsSearchPage> = buildPages()

    /** 供副标题计算路径：key → 页面。 */
    val byKey: Map<AppNavKey, SettingsSearchPage> by lazy { pages.associateBy { it.key } }

    private fun buildPages(): List<SettingsSearchPage> =
        SettingsSearchIndexDsl().apply { table() }.build()

    private fun SettingsSearchIndexDsl.table() {
        page(R.string.cloud_translate_settings_title, MainBottomNavDestination.Home, AppNavKey.CloudTranslateSettings, parent = AppNavKey.FloatBallTranslation)
        page(R.string.multitouch_title, MainBottomNavDestination.Home, AppNavKey.ExtensionMultiTouchGesture) {
            row(R.string.multitouch_enable_title)
        }
        page(R.string.float_ball_settings_title, MainBottomNavDestination.Home, AppNavKey.FloatBall) {
            row(R.string.float_ball_enabled)
            row(R.string.float_ball_appearance_settings_title)
            row(R.string.float_ball_gesture_settings_title)
            row(R.string.float_ball_drag_pick_enabled)
            row(R.string.float_ball_drag_paste_enabled)
            row(R.string.float_ball_pick_word_config_title)
            row(R.string.float_ball_pick_settings_title)
        }
        page(R.string.float_ball_appearance_settings_title, MainBottomNavDestination.Home, AppNavKey.FloatBallAppearance, parent = AppNavKey.FloatBall) {
            row(R.string.float_ball_style_picker_title)
            row(R.string.float_ball_size)
            row(R.string.float_ball_opacity)
            row(R.string.float_ball_idle_delay)
            row(R.string.float_ball_idle_freeze_gif)
            row(R.string.float_ball_idle_opacity)
            row(R.string.float_ball_edge_inset)
            row(R.string.float_ball_position_y)
            row(R.string.float_ball_line_height)
            row(R.string.float_ball_line_width)
            row(R.string.float_ball_line_opacity)
        }
        page(R.string.float_ball_gesture_settings_title, MainBottomNavDestination.Home, AppNavKey.FloatBallGesture, parent = AppNavKey.FloatBall) {
            row(R.string.fv_custom_gesture_title)
            row(R.string.float_ball_gesture_tuning_title)
        }
        page(R.string.float_ball_gesture_tuning_title, MainBottomNavDestination.Home, AppNavKey.FloatBallGestureTuning, parent = AppNavKey.FloatBallGesture) {
            row(R.string.float_ball_gesture_down_swipe_distance)
            row(R.string.float_ball_gesture_side_swipe_distance)
            row(R.string.float_ball_gesture_up_swipe_distance)
            row(R.string.float_ball_gesture_long_press_ms)
            row(R.string.float_ball_gesture_ring_auto_select)
            row(R.string.float_ball_gesture_overlay_anchor_touch_down)
            row(R.string.float_ball_gesture_haptic)
            row(R.string.float_ball_gesture_back_haptic)
        }
        page(R.string.image_search_engine_settings_title, MainBottomNavDestination.Home, AppNavKey.FloatBallImageSearchEngine, parent = AppNavKey.FloatBallPick) {
            row(R.string.image_search_engine_add_share_target)
            row(R.string.image_search_engine_show_aggregated_entry)
            row(R.string.search_group_pick_existing)
            row(R.string.search_group_create_new)
            row(R.string.search_engine_import_paste_json)
        }
        page(R.string.float_ball_pick_settings_title, MainBottomNavDestination.Home, AppNavKey.FloatBallPick) {
            row(R.string.pick_settings_panel_layout_behavior_title)
            row(R.string.pick_handfeel_settings_title)
            row(R.string.float_ball_translation_settings_title)
            row(R.string.search_engine_settings_title)
            row(R.string.image_search_engine_settings_title)
            row(R.string.image_editor_delay_delete_title)
            row(R.string.share_image_ocr_history_enabled)
        }
        page(R.string.pick_handfeel_settings_title, MainBottomNavDestination.Home, AppNavKey.FloatBallPickHandfeel, parent = AppNavKey.FloatBall) {
            row(R.string.float_ball_pick_cross_arm)
            row(R.string.float_ball_pick_offset)
        }
        page(R.string.pick_settings_panel_layout_behavior_title, MainBottomNavDestination.Home, AppNavKey.FloatBallPickPanelLayoutBehavior, parent = AppNavKey.FloatBallPick) {
            row(R.string.float_ball_pick_panel_custom_bottom_inset)
            row(R.string.float_ball_pick_text_size)
            row(R.string.float_ball_pick_merge_links)
            row(R.string.float_ball_pick_auto_select_all)
            row(R.string.float_ball_pick_copy_dismiss_panel)
            row(R.string.float_ball_pick_text_first_panel)
            row(R.string.float_ball_pick_haptic_enabled)
            row(R.string.float_ball_pick_panel_enter_animation)
            row(R.string.float_ball_pick_panel_exit_animation)
        }
        page(R.string.search_engine_settings_title, MainBottomNavDestination.Home, AppNavKey.FloatBallSearchEngine, parent = AppNavKey.FloatBallPick) {
            row(R.string.search_engine_settings_preset_catalog)
            row(R.string.search_engine_import_paste_json)
            row(R.string.search_engine_add_title)
            row(R.string.search_group_entry_title)
        }
        page(R.string.search_engine_preview_sort_title, MainBottomNavDestination.Home, AppNavKey.FloatBallSearchEnginePreviewSort, parent = AppNavKey.FloatBallSearchEngine)
        page(R.string.float_ball_style_picker_title, MainBottomNavDestination.Home, AppNavKey.FloatBallStyle, parent = AppNavKey.FloatBallAppearance) {
            row(R.string.float_ball_style_pick_image)
            row(R.string.float_ball_style_custom_image_no_crop)
            row(R.string.float_ball_style_pick_slideshow)
            row(R.string.float_ball_style_pick_gif)
        }
        page(R.string.float_ball_translation_settings_title, MainBottomNavDestination.Home, AppNavKey.FloatBallTranslation, parent = AppNavKey.FloatBallPick) {
            row(R.string.float_ball_instant_translate)
            row(R.string.float_ball_translate_app)
            row(R.string.float_ball_translate_mlkit_models)
            row(R.string.cloud_translate_settings_title)
        }
        page(R.string.floating_pointer_settings_title, MainBottomNavDestination.Home, AppNavKey.FloatingPointer) {
            row(R.string.floating_pointer_area_preview_title)
            row(R.string.floating_pointer_sensitivity)
            row(R.string.floating_pointer_pointer_settings_title)
            row(R.string.floating_pointer_joystick_settings_title)
            row(R.string.floating_pointer_radial_settings_title)
            row(R.string.floating_pointer_edge_settings_title)
        }
        page(R.string.floating_pointer_edge_settings_title, MainBottomNavDestination.Home, AppNavKey.FloatingPointerEdgeActions, parent = AppNavKey.FloatingPointer) {
            row(R.string.floating_pointer_edge_threshold)
            row(R.string.floating_pointer_edge_preview_sensitivity)
            row(R.string.floating_pointer_edge_preview_glow_size)
            row(R.string.floating_pointer_edge_preview_show_icon)
            row(R.string.floating_pointer_edge_reset_defaults)
        }
        page(R.string.floating_pointer_joystick_settings_title, MainBottomNavDestination.Home, AppNavKey.FloatingPointerJoystick, parent = AppNavKey.FloatingPointer) {
            row(R.string.floating_pointer_joystick_size)
            row(R.string.floating_pointer_joystick_gradient_radius)
            row(R.string.floating_pointer_click_distance_threshold)
            row(R.string.floating_pointer_hover_enter_select)
            row(R.string.floating_pointer_hide_outside_click)
            row(R.string.floating_pointer_hide_quick_swipe)
            row(R.string.floating_pointer_hide_idle_delay)
        }
        page(R.string.floating_pointer_pointer_settings_title, MainBottomNavDestination.Home, AppNavKey.FloatingPointerPointer, parent = AppNavKey.FloatingPointer) {
            row(R.string.floating_pointer_pointer_size)
            row(R.string.floating_pointer_ring_thickness)
            row(R.string.floating_pointer_dot_diameter)
            row(R.string.floating_pointer_click_haptic)
            row(R.string.floating_pointer_ripple_size)
            row(R.string.floating_pointer_ripple_duration)
            row(R.string.floating_pointer_trail_duration)
            row(R.string.floating_pointer_hide_on_release)
        }
        page(R.string.floating_pointer_radial_settings_title, MainBottomNavDestination.Home, AppNavKey.FloatingPointerRadialMenu, parent = AppNavKey.FloatingPointer) {
            row(R.string.floating_pointer_radial_always_visible)
            row(R.string.floating_pointer_joystick_long_press_action)
            row(R.string.floating_pointer_radial_long_press_ms)
            row(R.string.floating_pointer_radial_outer_size)
            row(R.string.floating_pointer_radial_inner_size)
            row(R.string.floating_pointer_radial_divider_thickness)
            row(R.string.floating_pointer_radial_icon_size)
        }
        page(R.string.fv_custom_gesture_title, MainBottomNavDestination.Home, AppNavKey.FvCustomGesture, parent = AppNavKey.FloatBallGesture)
        page(R.string.gesture_hint_style_android, MainBottomNavDestination.Home, AppNavKey.HomeAndroidAnimationStyle) {
            row(R.string.android_back_color_source_app)
            row(R.string.android_back_color_source_system)
        }
        page(R.string.gesture_animation_title, MainBottomNavDestination.Home, AppNavKey.HomeAnimationStyleSelect) {
            row(R.string.gesture_hint_finger_offset_title)
        }
        page(R.string.background_permissions_title, MainBottomNavDestination.Home, AppNavKey.HomeBackgroundPermissions) {
            row(R.string.privilege_mode_shizuku)
            row(R.string.privilege_mode_root)
            row(R.string.hide_from_recents_title)
            row(R.string.secure_settings_title)
        }
        page(R.string.gesture_hint_style_bubble, MainBottomNavDestination.Home, AppNavKey.HomeBubbleAnimationStyle) {
            row(R.string.animation_style_stroke_width)
            row(R.string.animation_style_diameter)
            row(R.string.animation_style_pop_offset)
            row(R.string.animation_style_scaling)
        }
        page(R.string.gesture_hint_style_capsule, MainBottomNavDestination.Home, AppNavKey.HomeCapsuleAnimationStyle) {
            row(R.string.animation_style_stroke_width)
            row(R.string.animation_style_width)
            row(R.string.animation_style_length)
            row(R.string.animation_style_corner_radius)
            row(R.string.animation_style_scaling)
        }
        page(R.string.corner_gesture_settings_title, MainBottomNavDestination.Home, AppNavKey.HomeCornerGesture) {
            row(R.string.corner_gesture_enabled)
            row(R.string.corner_gesture_left_enabled)
            row(R.string.corner_gesture_right_enabled)
            row(R.string.corner_gesture_interaction_appearance_title)
            row(R.string.corner_gesture_slots_section)
        }
        page(R.string.corner_gesture_interaction_appearance_title, MainBottomNavDestination.Home, AppNavKey.HomeCornerGestureInteraction, parent = AppNavKey.HomeCornerGesture) {
            row(R.string.corner_gesture_vertical_edge_height)
            row(R.string.corner_gesture_vertical_edge_width)
            row(R.string.corner_gesture_horizontal_edge_height)
            row(R.string.corner_gesture_horizontal_edge_width)
            row(R.string.corner_gesture_trigger_slop)
            row(R.string.corner_gesture_inner_diameter)
            row(R.string.corner_gesture_ring_spacing)
            row(R.string.corner_gesture_bubble_size)
            row(R.string.corner_gesture_hide_landscape)
            row(R.string.corner_gesture_landscape_prevent_false_touch)
            row(R.string.corner_gesture_override_system_nav)
            row(R.string.corner_gesture_cancel_outside_wheel)
            row(R.string.corner_gesture_progressive_layers)
            row(R.string.corner_gesture_selected_hint_icon_size)
            row(R.string.corner_gesture_show_edit_button)
        }
        page(R.string.corner_gesture_slots_section, MainBottomNavDestination.Home, AppNavKey.HomeCornerGestureSlots, parent = AppNavKey.HomeCornerGesture) {
            row(R.string.corner_gesture_unified_slots)
            row(R.string.corner_gesture_layer_count)
            row(R.string.corner_gesture_layer_slot_count)
            row(R.string.corner_gesture_inner_zone_action)
            row(R.string.corner_gesture_slot_title)
        }
        page(R.string.excluded_apps_title, MainBottomNavDestination.Home, AppNavKey.HomeExcludedApps) {
            row(R.string.excluded_apps_entry_title)
        }
        page(R.string.free_window_entry_title, MainBottomNavDestination.Home, AppNavKey.HomeFreeWindow) {
            row(R.string.long_press_launch_duration)
            row(R.string.free_window_launch_mode)
            row(R.string.free_window_adjust_layout)
        }
        page(R.string.free_window_mode_dialog_title, MainBottomNavDestination.Home, AppNavKey.HomeFreeWindowMode, parent = AppNavKey.HomeFreeWindow)
        page(R.string.gesture_angle_title, MainBottomNavDestination.Home, AppNavKey.HomeGestureAngle) {
            row(R.string.gesture_angle_entry_title)
        }
        page(R.string.gesture_app_profiles_title, MainBottomNavDestination.Home, AppNavKey.HomeGestureAppProfiles) {
            row(R.string.gesture_app_profiles_enabled)
        }
        page(R.string.haptic_settings_title, MainBottomNavDestination.Home, AppNavKey.HomeHapticSettings, parent = AppNavKey.HomeInteractionAppearance) {
            row(R.string.haptic_strength)
            row(R.string.haptic_short_threshold_title)
            row(R.string.haptic_compound_turn_title)
            row(R.string.haptic_return_swipe_title)
            row(R.string.haptic_hover_title)
            row(R.string.haptic_long_press_title)
            row(R.string.haptic_long_threshold_title)
            row(R.string.haptic_slot_letter_title)
            row(R.string.haptic_slot_app_title)
            row(R.string.haptic_confirm_launch_title)
            row(R.string.haptic_action_confirm_title)
        }
        page(R.string.interaction_appearance_settings_title, MainBottomNavDestination.Home, AppNavKey.HomeInteractionAppearance) {
            row(R.string.setting_icon_plate_enabled)
            row(R.string.overlay_material_settings_title)
            row(R.string.haptic_settings_title)
            row(R.string.settings_swipe_dismiss)
            row(R.string.settings_predictive_back)
        }
        page(R.string.app_name, MainBottomNavDestination.Home, AppNavKey.HomeMain) {
            row(R.string.gesture_switch)
            row(R.string.trigger_collection_title)
            row(R.string.gesture_app_profiles_title)
            row(R.string.corner_wheel_home_title)
            row(R.string.multitouch_title)
            row(R.string.physical_key_title)
            row(R.string.keyboard_trigger_behavior_title)
            row(R.string.keyboard_trigger_narrow_percent_title)
            row(R.string.previous_app_blacklist_entry_title)
        }
        page(R.string.overlay_advanced_shimmer_title, MainBottomNavDestination.Home, AppNavKey.HomeOverlayAdvancedShimmer, parent = AppNavKey.HomeOverlayMaterial) {
            row(R.string.overlay_border_highlight_title)
            row(R.string.overlay_radial_gloss_title)
            row(R.string.overlay_vertical_gloss_title)
        }
        page(R.string.overlay_material_settings_title, MainBottomNavDestination.Home, AppNavKey.HomeOverlayMaterial, parent = AppNavKey.HomeInteractionAppearance) {
            row(R.string.backdrop_blur_enabled)
            row(R.string.backdrop_blur_radius_title)
            row(R.string.backdrop_dim_title)
            row(R.string.overlay_blur_enabled)
            row(R.string.overlay_blur_radius_title)
            row(R.string.overlay_opacity_title)
            row(R.string.overlay_corner_radius_title)
        }
        page(R.string.previous_app_blacklist_title, MainBottomNavDestination.Home, AppNavKey.HomePreviousAppBlacklist)
        page(R.string.system_back_gesture_width_title, MainBottomNavDestination.Home, AppNavKey.HomeSystemBackGestureWidth) {
            row(R.string.system_gesture_takeover_top)
            row(R.string.system_gesture_takeover_sides)
            row(R.string.system_gesture_takeover_bottom)
            row(R.string.system_back_gesture_width_entry_title)
        }
        page(R.string.trigger_collection_title, MainBottomNavDestination.Home, AppNavKey.HomeTriggerCollection)
        page(R.string.trigger_collection_title, MainBottomNavDestination.Home, AppNavKey.HomeTriggerCollectionLandscape)
        page(R.string.gesture_hint_style_wave, MainBottomNavDestination.Home, AppNavKey.HomeWaveAnimationStyle) {
            row(R.string.animation_style_stroke_width)
            row(R.string.animation_style_width)
            row(R.string.animation_style_length)
            row(R.string.animation_style_reserved_bounds)
            row(R.string.animation_style_bezier_transform)
            row(R.string.animation_style_scaling)
        }
        page(R.string.native_engine_packs_title, MainBottomNavDestination.Home, AppNavKey.NativeEnginePacks, parent = AppNavKey.TranslateModels) {
            row(R.string.ocr_download_wifi_only)
        }
        page(R.string.ocr_models_title, MainBottomNavDestination.Home, AppNavKey.OcrModels, parent = AppNavKey.FloatBallPick)
        page(R.string.physical_key_title, MainBottomNavDestination.Home, AppNavKey.PhysicalKeySettings) {
            row(R.string.physical_key_enable_title)
            row(R.string.physical_key_intercept_native_title)
            row(R.string.physical_key_screen_off_trigger_title)
            row(R.string.physical_key_media_priority_title)
            row(R.string.physical_key_long_press_duration_title)
            row(R.string.physical_key_double_tap_duration_title)
        }
        page(R.string.search_panel_settings_title, MainBottomNavDestination.Home, AppNavKey.SearchPanel) {
            row(R.string.search_panel_settings_section_layout)
            row(R.string.search_engine_settings_title)
            row(R.string.image_search_engine_settings_title)
            row(R.string.search_panel_calculator_title)
            row(R.string.search_panel_web_suggestions_title)
            row(R.string.search_panel_web_suggestions_count_title)
            row(R.string.search_panel_entry_title)
        }
        page(R.string.search_panel_section_apps, MainBottomNavDestination.Home, AppNavKey.SearchPanelAppSearch, parent = AppNavKey.SearchPanel)
        page(R.string.search_panel_section_clipboard, MainBottomNavDestination.Home, AppNavKey.SearchPanelClipboardSearch, parent = AppNavKey.SearchPanel) {
            row(R.string.search_panel_shortcut_index_refresh)
            row(R.string.search_panel_settings_index_refresh)
        }
        page(R.string.search_panel_section_contacts, MainBottomNavDestination.Home, AppNavKey.SearchPanelContactSearch, parent = AppNavKey.SearchPanel) {
            row(R.string.search_panel_shortcut_index_refresh)
            row(R.string.search_panel_settings_index_refresh)
        }
        page(R.string.search_panel_section_files, MainBottomNavDestination.Home, AppNavKey.SearchPanelFileSearch, parent = AppNavKey.SearchPanel) {
            row(R.string.search_panel_file_show_folders)
            row(R.string.search_panel_file_show_system)
            row(R.string.search_panel_file_previews_title)
        }
        page(R.string.search_panel_settings_section_layout, MainBottomNavDestination.Home, AppNavKey.SearchPanelPresentationLayout, parent = AppNavKey.SearchPanel) {
            row(R.string.search_engine_grid_rows)
            row(R.string.search_engine_grid_columns)
            row(R.string.search_engine_show_labels)
        }
        page(R.string.search_panel_section_shortcuts, MainBottomNavDestination.Home, AppNavKey.SearchPanelShortcutSearch, parent = AppNavKey.SearchPanel) {
            row(R.string.search_panel_shortcut_index_refresh)
            row(R.string.search_panel_settings_index_refresh)
        }
        page(R.string.search_panel_settings_search_title, MainBottomNavDestination.Home, AppNavKey.SearchPanelSystemSettingsSearch, parent = AppNavKey.SearchPanel) {
            row(R.string.search_panel_shortcut_index_refresh)
            row(R.string.search_panel_settings_index_refresh)
        }
        page(R.string.share_image_ocr_history_title, MainBottomNavDestination.Home, AppNavKey.ShareImageOcrHistory, parent = AppNavKey.FloatBallPick)
        page(R.string.float_ball_translate_mlkit_models, MainBottomNavDestination.Home, AppNavKey.TranslateModels, parent = AppNavKey.FloatBallTranslation) {
            row(R.string.ocr_download_wifi_only)
        }
        page(R.string.extension_back_tap_title, MainBottomNavDestination.Shake, AppNavKey.ExtensionBackTap) {
            row(R.string.back_tap_enabled)
            row(R.string.back_tap_action)
            row(R.string.back_tap_sensitivity)
            row(R.string.back_tap_range)
            row(R.string.back_tap_mode_always)
            row(R.string.back_tap_mode_screen_on)
            row(R.string.back_tap_mode_screen_off)
            row(R.string.back_tap_vibration_feedback)
            row(R.string.back_tap_pause_charging)
        }
        page(R.string.face_down_gestures_title, MainBottomNavDestination.Shake, AppNavKey.ShakeFaceDownSettings) {
            row(R.string.face_down_gestures_hold_duration)
            row(R.string.face_down_gestures_cooldown)
            row(R.string.face_down_gestures_require_proximity)
            row(R.string.face_down_gestures_disable_landscape)
            row(R.string.face_down_gestures_vibration_feedback)
            row(R.string.face_down_gestures_audio_feedback_volume)
            row(R.string.face_down_gestures_action)
        }
        page(R.string.shake_gestures_app_blacklist, MainBottomNavDestination.Shake, AppNavKey.ShakeGestureBlacklist)
        page(R.string.shake_gestures_title, MainBottomNavDestination.Shake, AppNavKey.ShakeGestures) {
            row(R.string.shake_work_mode_always)
            row(R.string.shake_gestures_disable_landscape)
            row(R.string.shake_gestures_lock_screen)
            row(R.string.shake_gestures_app_blacklist)
            row(R.string.shake_gestures_global_sensitivity)
            row(R.string.shake_gestures_vibration_feedback)
            row(R.string.shake_gestures_animation_feedback)
            row(R.string.shake_menu_title)
            row(R.string.face_down_gestures_title)
            row(R.string.extension_back_tap_title)
        }
        page(R.string.shake_gestures_independent_app, MainBottomNavDestination.Shake, AppNavKey.ShakeIndependentAppSettings)
        page(R.string.shake_gestures_independent_sensitivity, MainBottomNavDestination.Shake, AppNavKey.ShakeIndependentSensitivity)
        page(R.string.shake_gestures_lock_screen, MainBottomNavDestination.Shake, AppNavKey.ShakeLockScreenSettings)
        page(R.string.shake_menu_title, MainBottomNavDestination.Shake, AppNavKey.ShakeMenuSettings) {
            row(R.string.shake_menu_guard_duration)
            row(R.string.shake_menu_display_delay)
            row(R.string.shake_menu_show_overlay)
            row(R.string.shake_menu_add)
            row(R.string.shake_menu_name)
        }
        page(R.string.message_reminder_title, MainBottomNavDestination.Notification, AppNavKey.MessageReminder) {
            row(R.string.message_reminder_enabled)
            row(R.string.message_reminder_intercept_notifications)
            row(R.string.message_reminder_return_chip)
            row(R.string.message_reminder_return_chip_dismiss)
            row(R.string.message_reminder_allowed_apps)
            row(R.string.message_reminder_dnd_apps)
            row(R.string.message_reminder_suppress_system_dnd)
            row(R.string.message_reminder_hide_in_landscape)
            row(R.string.message_reminder_portrait_danmaku)
            row(R.string.message_reminder_landscape_danmaku)
        }
        page(R.string.message_reminder_allowed_apps_title, MainBottomNavDestination.Notification, AppNavKey.MessageReminderAllowedApps, parent = AppNavKey.MessageReminder)
        page(R.string.message_reminder_dnd_apps_title, MainBottomNavDestination.Notification, AppNavKey.MessageReminderDndApps, parent = AppNavKey.MessageReminder)
        page(R.string.message_reminder_open_last_on_unlock, MainBottomNavDestination.Notification, AppNavKey.MessageReminderUnlockRules, parent = AppNavKey.MessageReminder) {
            row(R.string.message_reminder_unlock_open_delay)
            row(R.string.message_reminder_unlock_auto_dismiss)
        }
        page(R.string.message_style_side_count, MainBottomNavDestination.Notification, AppNavKey.MessageStyleSideBubbleCount) {
            row(R.string.message_style_side_count_option)
        }
        page(R.string.notification_filter_tab_rules, MainBottomNavDestination.Notification, AppNavKey.NotificationFilterRules, parent = AppNavKey.NotificationHistory)
        page(R.string.notification_filter_settings_title, MainBottomNavDestination.Notification, AppNavKey.NotificationFilterSettings, parent = AppNavKey.NotificationHistory)
        page(R.string.notification_history_title, MainBottomNavDestination.Notification, AppNavKey.NotificationHistory) {
            row(R.string.notification_history_entry_title)
        }
        page(R.string.main_nav_notification, MainBottomNavDestination.Notification, AppNavKey.NotificationHub)
        page(R.string.otp_auto_input_entry_title, MainBottomNavDestination.Notification, AppNavKey.OtpAutoFill, parent = AppNavKey.OtpHub) {
            row(R.string.otp_auto_input_enabled_title)
            row(R.string.otp_auto_confirm_title)
            row(R.string.otp_auto_input_delay_title)
            row(R.string.otp_auto_input_interval_title)
            row(R.string.otp_lsposed_inject_title)
        }
        page(R.string.otp_blocked_apps_title, MainBottomNavDestination.Notification, AppNavKey.OtpBlockedApps, parent = AppNavKey.OtpExtraction)
        page(R.string.otp_extraction_entry_title, MainBottomNavDestination.Notification, AppNavKey.OtpExtraction, parent = AppNavKey.OtpHub) {
            row(R.string.otp_code_notification_title)
            row(R.string.otp_code_notification_retention_title)
            row(R.string.otp_copy_to_clipboard_title)
            row(R.string.otp_show_code_toast_title)
            row(R.string.otp_block_code_sms_title)
            row(R.string.otp_mark_sms_read_title)
            row(R.string.otp_delete_sms_after_extract_title)
        }
        page(R.string.otp_hub_entry_title, MainBottomNavDestination.Notification, AppNavKey.OtpHub) {
            row(R.string.otp_extraction_entry_title)
            row(R.string.otp_auto_input_entry_title)
            row(R.string.otp_match_rules_entry_title)
            row(R.string.otp_records_title)
        }
        page(R.string.otp_records_title, MainBottomNavDestination.Notification, AppNavKey.OtpRecords, parent = AppNavKey.OtpHub)
        page(R.string.otp_match_rules_entry_title, MainBottomNavDestination.Notification, AppNavKey.OtpRules, parent = AppNavKey.OtpHub)
        page(R.string.otp_sms_blacklist_section, MainBottomNavDestination.Notification, AppNavKey.OtpSmsBlacklist, parent = AppNavKey.OtpExtraction) {
            row(R.string.otp_sms_blacklist_enabled_title)
            row(R.string.otp_sms_blacklist_action_block_title)
            row(R.string.otp_sms_blacklist_action_delete_title)
        }
        page(R.string.extension_action_center_title, MainBottomNavDestination.Extension, AppNavKey.ActionCenter)
        page(R.string.activity_shortcut_presets_title, MainBottomNavDestination.Extension, AppNavKey.ActivityShortcutPresets, parent = AppNavKey.ActivityShortcuts) {
            row(R.string.activity_shortcut_entry_title)
        }
        page(R.string.activity_shortcut_title, MainBottomNavDestination.Extension, AppNavKey.ActivityShortcuts) {
            row(R.string.activity_shortcut_entry_title)
        }
        page(R.string.app_carousel_settings_title, MainBottomNavDestination.Extension, AppNavKey.AppCarouselSwitcherSettings) {
            row(R.string.app_carousel_cancel_distance_up)
            row(R.string.app_carousel_slide_sensitivity)
            row(R.string.app_carousel_slide_reversed)
            row(R.string.app_carousel_cancel_distance_down)
        }
        page(R.string.clipboard_float_app_blacklist, MainBottomNavDestination.Extension, AppNavKey.ClipboardFloatBlacklist, parent = AppNavKey.ClipboardFloatSettings)
        page(R.string.clipboard_float_section, MainBottomNavDestination.Extension, AppNavKey.ClipboardFloatSettings, parent = AppNavKey.StashClipboard) {
            row(R.string.stash_clipboard_section_clipboard)
            row(R.string.floating_panel_title)
            row(R.string.clipboard_overlay_preview_size_title)
            row(R.string.clipboard_paste_fv_style_enabled)
            row(R.string.clipboard_screenshot_monitoring_title)
            row(R.string.clipboard_history_float_enabled_landscape_title)
            row(R.string.clipboard_history_float_lock_position_title)
            row(R.string.clipboard_history_float_handle_width_title)
            row(R.string.clipboard_history_float_handle_height_title)
            row(R.string.clipboard_history_float_handle_alpha_title)
            row(R.string.clipboard_float_show_chip_title)
            row(R.string.clipboard_float_pin_title)
            row(R.string.clipboard_float_paste_haptic_title)
            row(R.string.clipboard_float_opacity_title)
            row(R.string.clipboard_float_blur_title)
            row(R.string.clipboard_float_blur_radius_title)
            row(R.string.clipboard_float_auto_dim_title)
            row(R.string.stash_clipboard_entry_title)
        }
        page(R.string.stash_clipboard_section_clipboard, MainBottomNavDestination.Extension, AppNavKey.ClipboardHistorySettings, parent = AppNavKey.StashClipboard) {
            row(R.string.floating_panel_title)
            row(R.string.clipboard_float_section)
            row(R.string.clipboard_overlay_preview_size_title)
            row(R.string.clipboard_paste_fv_style_enabled)
            row(R.string.clipboard_screenshot_monitoring_title)
            row(R.string.clipboard_history_float_enabled_landscape_title)
            row(R.string.clipboard_history_float_lock_position_title)
            row(R.string.clipboard_history_float_handle_width_title)
            row(R.string.clipboard_history_float_handle_height_title)
            row(R.string.clipboard_history_float_handle_alpha_title)
            row(R.string.clipboard_float_show_chip_title)
            row(R.string.clipboard_float_pin_title)
            row(R.string.clipboard_float_paste_haptic_title)
            row(R.string.clipboard_float_opacity_title)
            row(R.string.clipboard_float_blur_title)
            row(R.string.clipboard_float_blur_radius_title)
            row(R.string.clipboard_float_auto_dim_title)
            row(R.string.stash_clipboard_entry_title)
        }
        page(R.string.clipboard_lsposed_whitelist, MainBottomNavDestination.Extension, AppNavKey.ClipboardLsposedWhitelist, parent = AppNavKey.ClipboardMonitoringSettings)
        page(R.string.clipboard_monitoring_page_title, MainBottomNavDestination.Extension, AppNavKey.ClipboardMonitoringSettings, parent = AppNavKey.ClipboardHistorySettings)
        page(R.string.drag_drop_flow_title, MainBottomNavDestination.Extension, AppNavKey.DragDropFlowSettings) {
            row(R.string.drag_drop_flow_enable_title)
            row(R.string.drag_drop_flow_haptic_title)
            row(R.string.drag_drop_flow_inline_actions_title)
            row(R.string.drag_drop_flow_text_engines_title)
            row(R.string.drag_drop_flow_image_engines_title)
            row(R.string.drag_drop_flow_file_engines_title)
            row(R.string.drag_drop_flow_global_press_title)
            row(R.string.drag_drop_flow_global_press_vibrate_title)
            row(R.string.drag_drop_flow_global_press_immediate_title)
            row(R.string.drag_drop_flow_global_press_early_text_title)
        }
        page(R.string.about_section_title, MainBottomNavDestination.Extension, AppNavKey.ExtensionAbout) {
            row(R.string.about_check_update_title)
            row(R.string.about_release_notes_title)
            row(R.string.about_replay_onboarding_title)
            row(R.string.about_star_github_title)
            row(R.string.about_feedback_issues_title)
            row(R.string.about_telegram_chat_title)
            row(R.string.about_qq_group_title)
            row(R.string.about_help_translate_title)
        }
        page(R.string.settings_backup_title, MainBottomNavDestination.Extension, AppNavKey.ExtensionBackup) {
            row(R.string.settings_backup_include_sensitive)
            row(R.string.settings_backup_cloud_entry_title)
        }
        page(R.string.cloud_backup_title, MainBottomNavDestination.Extension, AppNavKey.ExtensionCloudBackup, parent = AppNavKey.ExtensionBackup) {
            row(R.string.settings_backup_include_sensitive)
        }
        page(R.string.cloud_storage_settings_title, MainBottomNavDestination.Extension, AppNavKey.ExtensionCloudStorageSettings, parent = AppNavKey.ExtensionCloudBackup) {
            row(R.string.cloud_storage_add)
        }
        page(R.string.composite_library_title, MainBottomNavDestination.Extension, AppNavKey.ExtensionCompositePresets) {
            row(R.string.composite_library_add)
        }
        page(R.string.diagnostic_log_title, MainBottomNavDestination.Extension, AppNavKey.ExtensionDiagnosticLogs)
        page(R.string.external_invocation_title, MainBottomNavDestination.Extension, AppNavKey.ExtensionExternalInvocations) {
            row(R.string.launcher_shortcut_menu_entry_title)
            row(R.string.external_invocation_entry_title)
        }
        page(R.string.extension_freezer_title, MainBottomNavDestination.Extension, AppNavKey.ExtensionFreezer)
        page(R.string.freezer_manage_apps, MainBottomNavDestination.Extension, AppNavKey.ExtensionFreezerApps, parent = AppNavKey.ExtensionFreezer)
        page(R.string.freezer_settings_title, MainBottomNavDestination.Extension, AppNavKey.ExtensionFreezerSettings, parent = AppNavKey.ExtensionFreezerApps) {
            row(R.string.freezer_action_freeze)
            row(R.string.freezer_action_pause)
            row(R.string.freezer_show_system_apps)
            row(R.string.freezer_show_in_launcher_title)
        }
        page(R.string.main_nav_extension, MainBottomNavDestination.Extension, AppNavKey.ExtensionHub) {
            row(R.string.extension_action_center_title)
            row(R.string.extension_shortcut_test_title)
            row(R.string.drag_drop_flow_title)
            row(R.string.link_dispatch_title)
            row(R.string.extension_freezer_title)
            row(R.string.composite_library_title)
            row(R.string.share_sheet_settings_title)
            row(R.string.extension_icon_theme_title)
            row(R.string.extension_icon_library_title)
            row(R.string.extension_native_engine_packs_entry_title)
            row(R.string.diagnostic_report_entry_title)
            row(R.string.about_section_title)
            row(R.string.privacy_policy_entry_title)
            row(R.string.about_open_source_license_title)
            row(R.string.about_third_party_notices_title)
            row(R.string.settings_backup_entry_title)
            row(R.string.extension_quick_wheel_entry_title)
        }
        page(R.string.launcher_shortcut_menu_entry_title, MainBottomNavDestination.Extension, AppNavKey.ExtensionLauncherShortcutMenu, parent = AppNavKey.ExtensionExternalInvocations)
        page(R.string.missing_permissions_title, MainBottomNavDestination.Extension, AppNavKey.ExtensionMissingPermissions, parent = AppNavKey.ExtensionBackup) {
            row(R.string.missing_permissions_entry_title)
        }
        page(R.string.privacy_policy_title, MainBottomNavDestination.Extension, AppNavKey.ExtensionPrivacy, parent = AppNavKey.ExtensionAbout)
        page(R.string.about_third_party_notices_title, MainBottomNavDestination.Extension, AppNavKey.ExtensionThirdPartyNotices, parent = AppNavKey.ExtensionAbout)
        page(R.string.action_icon_library_title, MainBottomNavDestination.Extension, AppNavKey.GlobalIconLibrary)
        page(R.string.holographic_apps_entry_title, MainBottomNavDestination.Extension, AppNavKey.HolographicLauncherApps, parent = AppNavKey.HolographicLauncherSettings)
        page(R.string.holographic_launcher_entry_title, MainBottomNavDestination.Extension, AppNavKey.HolographicLauncherSettings) {
            row(R.string.holographic_settings_timeout)
            row(R.string.holographic_settings_sensitivity)
            row(R.string.holographic_apps_entry_title)
        }
        page(R.string.hidden_apps_title, MainBottomNavDestination.Extension, AppNavKey.HomeHiddenApps, parent = AppNavKey.HomeLayout)
        page(R.string.layout_settings_title, MainBottomNavDestination.Extension, AppNavKey.HomeLayout) {
            row(R.string.layout_settings_entry_title)
            row(R.string.quick_launcher_editor_title)
            row(R.string.honeycomb_launcher_editor_title)
            row(R.string.fv_ring_launcher_entry_title)
            row(R.string.quick_launcher_grid_columns)
            row(R.string.quick_launcher_grid_rows)
        }
        page(R.string.honeycomb_display_settings_entry, MainBottomNavDestination.Extension, AppNavKey.HoneycombDisplaySettings, parent = AppNavKey.HoneycombLauncher) {
            row(R.string.honeycomb_mode_hold)
            row(R.string.honeycomb_mode_browse)
            row(R.string.honeycomb_selected_hint_icon_size)
            row(R.string.honeycomb_fixed_x)
            row(R.string.honeycomb_fixed_y)
            row(R.string.honeycomb_icon_size)
            row(R.string.honeycomb_spacing)
            row(R.string.honeycomb_disc_size)
            row(R.string.honeycomb_animation_speed)
            row(R.string.honeycomb_inertia)
            row(R.string.honeycomb_center_scale)
            row(R.string.honeycomb_edge_scale)
            row(R.string.honeycomb_selection_scale)
        }
        page(R.string.honeycomb_launcher_editor_title, MainBottomNavDestination.Extension, AppNavKey.HoneycombLauncher) {
            row(R.string.honeycomb_display_settings_entry)
        }
        page(R.string.icon_theme_title, MainBottomNavDestination.Extension, AppNavKey.IconThemeSettings)
        page(R.string.link_dispatch_title, MainBottomNavDestination.Extension, AppNavKey.LinkDispatchSettings)
        page(R.string.macro_list_title, MainBottomNavDestination.Extension, AppNavKey.MacroList)
        page(R.string.page_scroll_prefs_title, MainBottomNavDestination.Extension, AppNavKey.PageScrollSettings) {
            row(R.string.page_scroll_speed_title)
            row(R.string.page_scroll_bar_idle_alpha_title)
        }
        page(R.string.quick_launcher_editor_title, MainBottomNavDestination.Extension, AppNavKey.QuickLauncher)
        page(R.string.quick_wheel_title, MainBottomNavDestination.Extension, AppNavKey.QuickWheelList)
        page(R.string.fv_ring_launcher_entry_title, MainBottomNavDestination.Extension, AppNavKey.RingLauncherSettings) {
            row(R.string.fv_ring_launcher_link_appearance_axes_title)
            row(R.string.fv_ring_launcher_link_slot_axes_title)
            row(R.string.fv_ring_launcher_icon_size_title)
            row(R.string.fv_ring_launcher_base_radius_title)
            row(R.string.fv_ring_launcher_layer_gap_title)
            row(R.string.fv_ring_launcher_end_margin_title)
            row(R.string.fv_ring_launcher_show_toolbar_title)
        }
        page(R.string.share_sheet_settings_title, MainBottomNavDestination.Extension, AppNavKey.ShareSheetSettings) {
            row(R.string.share_sheet_toggle_title)
        }
        page(R.string.shell_panel_history_title, MainBottomNavDestination.Extension, AppNavKey.ShellCommandHistory, parent = AppNavKey.ShellCommands) {
            row(R.string.shell_panel_history_entry_title)
        }
        page(R.string.shell_panel_title, MainBottomNavDestination.Extension, AppNavKey.ShellCommands) {
            row(R.string.shell_panel_entry_title)
        }
        page(R.string.stash_clipboard_settings_title, MainBottomNavDestination.Extension, AppNavKey.StashClipboard) {
            row(R.string.stash_clipboard_section_clipboard)
            row(R.string.floating_panel_title)
            row(R.string.clipboard_float_section)
            row(R.string.clipboard_overlay_preview_size_title)
            row(R.string.clipboard_paste_fv_style_enabled)
            row(R.string.clipboard_screenshot_monitoring_title)
            row(R.string.clipboard_history_float_enabled_landscape_title)
            row(R.string.clipboard_history_float_lock_position_title)
            row(R.string.clipboard_history_float_handle_width_title)
            row(R.string.clipboard_history_float_handle_height_title)
            row(R.string.clipboard_history_float_handle_alpha_title)
            row(R.string.clipboard_float_show_chip_title)
            row(R.string.clipboard_float_pin_title)
            row(R.string.clipboard_float_paste_haptic_title)
            row(R.string.clipboard_float_opacity_title)
            row(R.string.clipboard_float_blur_title)
            row(R.string.clipboard_float_blur_radius_title)
            row(R.string.clipboard_float_auto_dim_title)
            row(R.string.stash_clipboard_entry_title)
        }
        page(R.string.floating_panel_title, MainBottomNavDestination.Extension, AppNavKey.StashPanelSettings, parent = AppNavKey.StashClipboard) {
            row(R.string.stash_clipboard_section_clipboard)
            row(R.string.clipboard_float_section)
            row(R.string.clipboard_overlay_preview_size_title)
            row(R.string.clipboard_paste_fv_style_enabled)
            row(R.string.clipboard_screenshot_monitoring_title)
            row(R.string.clipboard_history_float_enabled_landscape_title)
            row(R.string.clipboard_history_float_lock_position_title)
            row(R.string.clipboard_history_float_handle_width_title)
            row(R.string.clipboard_history_float_handle_height_title)
            row(R.string.clipboard_history_float_handle_alpha_title)
            row(R.string.clipboard_float_show_chip_title)
            row(R.string.clipboard_float_pin_title)
            row(R.string.clipboard_float_paste_haptic_title)
            row(R.string.clipboard_float_opacity_title)
            row(R.string.clipboard_float_blur_title)
            row(R.string.clipboard_float_blur_radius_title)
            row(R.string.clipboard_float_auto_dim_title)
            row(R.string.stash_clipboard_entry_title)
        }
        page(R.string.widget_panel_settings_title, MainBottomNavDestination.Extension, AppNavKey.WidgetPanel) {
            row(R.string.widget_panel_show_labels)
            row(R.string.widget_panel_columns_title)
            row(R.string.widget_panel_visible_rows_title)
            row(R.string.widget_panel_cell_width_title)
            row(R.string.widget_panel_margin_top_title)
            row(R.string.widget_panel_margin_left_title)
        }
    }
}

/**
 * 建表 DSL：`page(...) { row(...) }` 比上百个嵌套构造参数更好 review，也能让守卫脚本按
 * `page(` / `row(` 行做文本比对。
 */
private class SettingsSearchIndexDsl {
    private val pages = mutableListOf<SettingsSearchPage>()
    private var rows: MutableList<SettingsSearchRow>? = null

    fun page(
        @StringRes title: Int,
        tab: MainBottomNavDestination,
        key: AppNavKey,
        parent: AppNavKey? = null,
        content: SettingsSearchIndexDsl.() -> Unit = {},
    ) {
        val collected = mutableListOf<SettingsSearchRow>()
        rows = collected
        content()
        rows = null
        pages += SettingsSearchPage(title = title, tab = tab, key = key, parent = parent, rows = collected)
    }

    fun row(@StringRes title: Int) {
        rows?.add(SettingsSearchRow(title))
    }

    fun build(): List<SettingsSearchPage> = pages.toList()
}
