package com.slideindex.app.settings

/**
 * 「按模块备份 / 恢复」的模块划分。
 *
 * 一个模块 = 一组偏好键 + 它独占的 `filesDir` 子目录 + 它独占的 JSON 分区。
 * 划分由 [SettingsModuleRegistry] 里的**有序前缀规则**决定，没被任何规则命中的键归 [GENERAL]。
 *
 * 为什么不做成 645 个键的显式表：键是跟着功能一起长的，显式表每次加键都要人工补一行、
 * 漏了就会在备份里静默丢失。前缀规则让新键自动落到正确模块，[SettingsModuleRegistryTest]
 * 再用「总键数快照 + 每模块代表键」把改动变成显式动作。
 *
 * [id] 会写进备份文件，**改动它等于改文件格式**，只能新增不能重命名。
 */
enum class SettingsModule(val id: String) {
    /** 边缘手势、触钮、索引、系统手势接管。 */
    EDGE_GESTURE("edge_gesture"),

    /** 边角轮盘。 */
    CORNER_WHEEL("corner_wheel"),

    /** 快捷轮盘（多轮盘、容器、二级盘）。 */
    QUICK_WHEEL("quick_wheel"),

    /** 悬浮球与双贴边线条。 */
    FLOAT_BALL("float_ball"),

    /** 边缘指针 / 摇杆 / 指尖轮盘之外的指针外观。 */
    FLOATING_POINTER("floating_pointer"),

    /** 触钮手势的指尖轮盘。 */
    FINGERTIP_RING("fingertip_ring"),

    /** 快捷启动器、蜂窝、圆环、全息、应用轮播等「启动器」类面板。 */
    QUICK_LAUNCHER("quick_launcher"),

    /** 小组件面板。 */
    WIDGET_PANEL("widget_panel"),

    /** 消息提醒（浮标 / 卡片 / 弹幕 / C 通知）与通知过滤。 */
    MESSAGE("message"),

    /** 验证码相关。 */
    OTP("otp"),

    /** 摇一摇、翻转静音、返回敲击等体感手势。 */
    SHAKE("shake"),

    /** 剪贴板（含悬浮窗、历史、监听）。 */
    CLIPBOARD("clipboard"),

    /** 拖放分发。 */
    DRAG_DROP("drag_drop"),

    /** 收纳面板。 */
    STASH("stash"),

    /** 搜索/取词面板、搜索引擎、图片搜索、OCR 引擎选择。 */
    SEARCH("search"),

    /** 小窗与启动策略。 */
    FREE_WINDOW("free_window"),

    /** 冰箱（冻结/暂停）。 */
    FREEZER("freezer"),

    /** 自用 shell 命令。 */
    SHELL("shell"),

    /** 应用内直达。 */
    ACTIVITY_SHORTCUTS("activity_shortcuts"),

    /** 外观、主题、底栏、语言、密度。 */
    THEME_UI("theme_ui"),

    /** 未归类的通用键：服务开关、权限模式、屏蔽列表、迁移标记等。 */
    GENERAL("general"),
    ;

    /** 是否允许独立导出成可分享的小包（只有不含隐私、且对他人才有意义的模块才允许）。 */
    val shareable: Boolean
        get() = when (this) {
            SHELL, ACTIVITY_SHORTCUTS, CORNER_WHEEL, QUICK_WHEEL, EDGE_GESTURE, QUICK_LAUNCHER -> true
            else -> false
        }

    /** 该模块可能含隐私数据（验证码记录、剪贴板历史、通知历史），恢复时需要在界面上明确标出。 */
    val sensitive: Boolean
        get() = when (this) {
            OTP, CLIPBOARD, MESSAGE -> true
            else -> false
        }

    /** 导入后是否必须让 overlay 侧重新读设置（几乎全部都需要，显式列出来便于将来分化）。 */
    val needsOverlayRefresh: Boolean
        get() = this != GENERAL

    companion object {
        private val BY_ID = entries.associateBy { it.id }

        fun fromId(id: String): SettingsModule? = BY_ID[id]
    }
}

/** 备份里的独立 JSON 分区（对应 [SensitiveBackupSections] 的字段）。 */
enum class SettingsBackupSection {
    OTP_RECORDS,
    NOTIFICATION_HISTORY,
    NOTIFICATION_FILTER_RULES,
    NOTIFICATION_FILTER_PREFERENCES,
    OTP_AUTO_FILL_STATS,
    SHELL_OUTPUT_HISTORY,
    SEARCH_PANEL_HISTORY,
    ;

    val module: SettingsModule
        get() = when (this) {
            OTP_RECORDS, OTP_AUTO_FILL_STATS -> SettingsModule.OTP
            NOTIFICATION_HISTORY,
            NOTIFICATION_FILTER_RULES,
            NOTIFICATION_FILTER_PREFERENCES,
            -> SettingsModule.MESSAGE
            SHELL_OUTPUT_HISTORY -> SettingsModule.SHELL
            SEARCH_PANEL_HISTORY -> SettingsModule.SEARCH
        }
}

/**
 * 键 / 目录 / 分区 → 模块的唯一映射。
 *
 * 规则**有序**：先匹配到的赢，所以更具体的规则必须排在更宽的前缀前面
 * （例如 `bottom_nav_` 必须排在 `bottom_` 之前、`app_ui_language_tag` 不能被 `app_carousel_` 一带而过）。
 */
object SettingsModuleRegistry {

    /** 前缀规则；未命中任何一条的键归 [SettingsModule.GENERAL]。 */
    private val PREFIX_RULES: List<Pair<SettingsModule, String>> = listOf(
        SettingsModule.CORNER_WHEEL to "corner_gesture_",
        SettingsModule.QUICK_WHEEL to "quick_wheel_",
        SettingsModule.FLOAT_BALL to "float_ball_",
        SettingsModule.FLOATING_POINTER to "floating_pointer_",
        SettingsModule.FINGERTIP_RING to "fingertip_ring_",
        SettingsModule.SHELL to "shell_",
        SettingsModule.ACTIVITY_SHORTCUTS to "activity_",
        SettingsModule.ACTIVITY_SHORTCUTS to "launcher_shortcut_menu_",
        SettingsModule.STASH to "stash_",
        SettingsModule.FREEZER to "freezer_",
        SettingsModule.MESSAGE to "message_",
        SettingsModule.OTP to "otp_",
        SettingsModule.SHAKE to "shake_",
        SettingsModule.SHAKE to "hold_shake_",
        SettingsModule.SHAKE to "lock_screen_shake_",
        SettingsModule.SHAKE to "independent_app_shake_",
        SettingsModule.SHAKE to "face_down_",
        SettingsModule.SHAKE to "back_tap_",
        SettingsModule.CLIPBOARD to "clipboard_",
        SettingsModule.DRAG_DROP to "drag_drop_flow_",
        SettingsModule.DRAG_DROP to "drag_flow_",
        SettingsModule.SEARCH to "search_",
        SettingsModule.SEARCH to "aggregated_image_search_",
        SettingsModule.SEARCH to "ocr_",
        SettingsModule.SEARCH to "share_image_ocr_history_",
        SettingsModule.FREE_WINDOW to "free_window_",
        SettingsModule.QUICK_LAUNCHER to "quick_launcher_",
        SettingsModule.QUICK_LAUNCHER to "honeycomb_",
        SettingsModule.QUICK_LAUNCHER to "fv_ring_launcher_",
        SettingsModule.QUICK_LAUNCHER to "fv_app_switcher_",
        SettingsModule.QUICK_LAUNCHER to "holographic_",
        SettingsModule.QUICK_LAUNCHER to "app_carousel_",
        SettingsModule.WIDGET_PANEL to "widget_panel_",
        // 主题类必须先于 edge_/bottom_ 等宽前缀：bottom_nav_* / top_app_bar_* 都属于外观。
        SettingsModule.THEME_UI to "theme_",
        SettingsModule.THEME_UI to "bottom_nav_",
        SettingsModule.THEME_UI to "top_app_bar_",
        SettingsModule.THEME_UI to "dynamic_color_",
        SettingsModule.THEME_UI to "custom_color_",
        SettingsModule.THEME_UI to "dark_background_",
        SettingsModule.THEME_UI to "icon_plate_",
        SettingsModule.THEME_UI to "ui_density_",
        SettingsModule.THEME_UI to "app_ui_language_",
        SettingsModule.THEME_UI to "overlay_blur_",
        // 边缘手势族的宽前缀放最后。
        SettingsModule.EDGE_GESTURE to "edge_",
        SettingsModule.EDGE_GESTURE to "left_",
        SettingsModule.EDGE_GESTURE to "right_",
        SettingsModule.EDGE_GESTURE to "bottom_",
        SettingsModule.EDGE_GESTURE to "top_",
        SettingsModule.EDGE_GESTURE to "trigger_",
        SettingsModule.EDGE_GESTURE to "gesture_",
        SettingsModule.EDGE_GESTURE to "system_gesture_takeover_",
        SettingsModule.EDGE_GESTURE to "keyboard_trigger_",
        SettingsModule.EDGE_GESTURE to "hide_trigger_",
        SettingsModule.EDGE_GESTURE to "swipe_",
        SettingsModule.EDGE_GESTURE to "intercept_system_back_gesture",
        SettingsModule.EDGE_GESTURE to "limit_max_intercept_length",
        SettingsModule.EDGE_GESTURE to "short_swipe_distance_",
        SettingsModule.EDGE_GESTURE to "long_swipe_distance_",
        SettingsModule.EDGE_GESTURE to "animation_styles",
        SettingsModule.EDGE_GESTURE to "index_height_",
        SettingsModule.EDGE_GESTURE to "index_vertical_",
        SettingsModule.EDGE_GESTURE to "hide_empty_index_",
        SettingsModule.EDGE_GESTURE to "apps_per_row",
        SettingsModule.EDGE_GESTURE to "panel_opacity",
        SettingsModule.EDGE_GESTURE to "selected_hint_icon_",
        SettingsModule.EDGE_GESTURE to "android_back_color_",
        SettingsModule.EDGE_GESTURE to "landscape_triggers_",
        SettingsModule.EDGE_GESTURE to "trigger_edge_width_nullable_",
    )

    /**
     * 精确键覆盖：前缀规则表达不了、或会被更宽的规则抢走的键。
     * 加键时优先进这里，而不是把前缀规则改得更绕。
     */
    private val EXACT_RULES: Map<String, SettingsModule> = mapOf(
        "shell_commands" to SettingsModule.SHELL,
        "activity_shortcuts" to SettingsModule.ACTIVITY_SHORTCUTS,
        "quick_launcher" to SettingsModule.QUICK_LAUNCHER,
        "app_ui_language_tag" to SettingsModule.THEME_UI,
        "top_app_bar_blur_style_id" to SettingsModule.THEME_UI,
        "overlay_blur_enabled" to SettingsModule.THEME_UI,
        "app_launch_policy_id" to SettingsModule.FREE_WINDOW,
        "long_press_launch_duration_ms" to SettingsModule.FREE_WINDOW,
        "ocr_engine_id" to SettingsModule.SEARCH,
        "ocr_download_wifi_only" to SettingsModule.SEARCH,
        "share_image_ocr_history_enabled" to SettingsModule.SEARCH,
    )

    /** 顶层目录 → 模块。目录不在这里的，任何选择性导出/导入都不会碰它。 */
    private val DIRECTORY_MODULES: Map<String, SettingsModule> = mapOf(
        "search_icons" to SettingsModule.SEARCH,
        "shell_icons" to SettingsModule.SHELL,
        "shortcut_icons" to SettingsModule.ACTIVITY_SHORTCUTS,
        "float_ball_assets" to SettingsModule.FLOAT_BALL,
        "fooView" to SettingsModule.FLOAT_BALL,
        "action_icons" to SettingsModule.THEME_UI,
        "quick_wheel_icons" to SettingsModule.QUICK_WHEEL,
        "fv_slot_icons" to SettingsModule.QUICK_LAUNCHER,
        "stash" to SettingsModule.STASH,
        "clipboard" to SettingsModule.CLIPBOARD,
        "share_image_ocr_history" to SettingsModule.SEARCH,
    )

    /** 云端 OCR / 翻译凭据文件的宿主模块。 */
    val CLOUD_CONFIG_MODULE: SettingsModule = SettingsModule.SEARCH

    fun moduleOf(key: String): SettingsModule {
        EXACT_RULES[key]?.let { return it }
        for ((module, prefix) in PREFIX_RULES) {
            if (key.startsWith(prefix)) return module
        }
        return SettingsModule.GENERAL
    }

    fun moduleOfDirectory(topLevelDir: String): SettingsModule? = DIRECTORY_MODULES[topLevelDir]

    fun moduleOfSection(section: SettingsBackupSection): SettingsModule = section.module
}
