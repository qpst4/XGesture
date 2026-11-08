package com.slideindex.app.overlay.quickmenu

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.slideindex.app.overlay.panels.R

/**
 * 「快捷菜单」的工具分组。
 *
 * 分组名只指到字符串资源，具体文案由各语言提供（本模块不内嵌展示文案）。
 */
enum class QuickMenuGroup(@StringRes val titleRes: Int) {
    CONVERT(R.string.quick_menu_section_convert),
    CODE(R.string.quick_menu_section_code),
    TEXT(R.string.quick_menu_section_text),
    SECURITY(R.string.quick_menu_section_security),
    TIME(R.string.quick_menu_section_time),

    /** 日常小工具（计算器、取色…）。 */
    TOOL(R.string.quick_menu_section_tool),

    /** 应用已有的功能/面板入口（前台活动探测、Shell 面板、冰箱…）。 */
    ACTION(R.string.quick_menu_section_action),
}

/**
 * 「快捷菜单」里的工具注册表。
 *
 * 设计要点（对应"聚合、零配置"的产品要求）：
 * - **出厂即满**：枚举里的每一项都会出现在面板中，用户不需要挑选、收藏或绑定；
 * - **顺序即默认排序**：`entries` 的顺序就是界面上分组内、以及"没有最近使用记录时"的顺序；
 * - **分组只用于分区展示**，不影响可用性。
 *
 * 新增一个工具只需要：在枚举里加一项 + 在 `QuickMenuPanel` 的 `when` 里加一个分支 +
 * 补两条字符串。**不需要**再动手势动作那一层（这正是把它做成"一个动作 + 多页容器"的目的）。
 */
enum class QuickMenuTool(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val group: QuickMenuGroup,
    val icon: ImageVector,
) {
    UNIT_CONVERTER(
        titleRes = R.string.quick_menu_unit_title,
        descRes = R.string.quick_menu_unit_desc,
        group = QuickMenuGroup.CONVERT,
        icon = Icons.Outlined.Straighten,
    ),
    CURRENCY_CONVERTER(
        titleRes = R.string.quick_menu_currency_title,
        descRes = R.string.quick_menu_currency_desc,
        group = QuickMenuGroup.CONVERT,
        icon = Icons.Outlined.CurrencyExchange,
    ),
    QR_GENERATOR(
        titleRes = R.string.quick_menu_qr_title,
        descRes = R.string.quick_menu_qr_desc,
        group = QuickMenuGroup.CODE,
        icon = Icons.Outlined.QrCode,
    ),
    SCAN_DECODER(
        titleRes = R.string.quick_menu_scan_title,
        descRes = R.string.quick_menu_scan_desc,
        group = QuickMenuGroup.CODE,
        icon = Icons.Outlined.QrCodeScanner,
    ),
    TEXT_TOOL(
        titleRes = R.string.quick_menu_text_title,
        descRes = R.string.quick_menu_text_desc,
        group = QuickMenuGroup.TEXT,
        icon = Icons.Outlined.TextFields,
    ),
    TRANSLATE(
        titleRes = R.string.quick_menu_translate_title,
        descRes = R.string.quick_menu_translate_desc,
        group = QuickMenuGroup.TEXT,
        icon = Icons.Outlined.Translate,
    ),
    PASSWORD(
        titleRes = R.string.quick_menu_password_title,
        descRes = R.string.quick_menu_password_desc,
        group = QuickMenuGroup.SECURITY,
        icon = Icons.Outlined.Key,
    ),
    STOPWATCH(
        titleRes = R.string.quick_menu_stopwatch_title,
        descRes = R.string.quick_menu_stopwatch_desc,
        group = QuickMenuGroup.TIME,
        icon = Icons.Outlined.Timer,
    ),
    COUNTDOWN(
        titleRes = R.string.quick_menu_timer_title,
        descRes = R.string.quick_menu_timer_desc,
        group = QuickMenuGroup.TIME,
        icon = Icons.Outlined.HourglassBottom,
    ),
    REMINDER(
        titleRes = R.string.quick_menu_reminder_title,
        descRes = R.string.quick_menu_reminder_desc,
        group = QuickMenuGroup.TIME,
        icon = Icons.Outlined.Alarm,
    ),
    TIMER_HISTORY(
        titleRes = R.string.quick_menu_history_title,
        descRes = R.string.quick_menu_history_desc,
        group = QuickMenuGroup.TIME,
        icon = Icons.Outlined.History,
    ),

    // ---- 实用工具（不是"转换/识别"，就是日常小工具）----
    CALCULATOR(
        titleRes = R.string.quick_menu_calc_title,
        descRes = R.string.quick_menu_calc_desc,
        group = QuickMenuGroup.TOOL,
        icon = Icons.Outlined.Calculate,
    ),
    POMODORO(
        titleRes = R.string.quick_menu_pomodoro_title,
        descRes = R.string.quick_menu_pomodoro_desc,
        group = QuickMenuGroup.TIME,
        icon = Icons.Outlined.Timelapse,
    ),
    COLOR_PICKER(
        titleRes = R.string.quick_menu_color_title,
        descRes = R.string.quick_menu_color_desc,
        group = QuickMenuGroup.TOOL,
        icon = Icons.Outlined.Colorize,
    ),
    FILE_BROWSER(
        titleRes = R.string.file_browser_title,
        descRes = R.string.file_browser_entry_desc,
        group = QuickMenuGroup.TOOL,
        icon = Icons.Outlined.FolderOpen,
    ),

    // ---- 应用已有的功能/面板：**预置入口**，点一下即打开，不需要任何配置 ----
    // 这三项不是"工具"，是把 App 里本来就要翻好几层才找得到的东西直接收进菜单。
    ENTRY_FOREGROUND_ACTIVITY(
        titleRes = R.string.gesture_action_foreground_activity_inspector,
        descRes = R.string.quick_menu_entry_fg_desc,
        group = QuickMenuGroup.ACTION,
        icon = Icons.Outlined.Info,
    ),
    ENTRY_FREEZER(
        titleRes = R.string.gesture_action_freezer_panel,
        descRes = R.string.quick_menu_entry_freezer_desc,
        group = QuickMenuGroup.ACTION,
        icon = Icons.Outlined.AcUnit,
    ),
    ENTRY_SHELL_PANEL(
        titleRes = R.string.gesture_action_shell_command_panel,
        descRes = R.string.quick_menu_entry_shell_desc,
        group = QuickMenuGroup.ACTION,
        icon = Icons.Outlined.Terminal,
    ),

    // 再补三个"把已有能力收进菜单"的入口（文案全部复用动作列表里的同名动作，不新增字符串）。
    ENTRY_SEARCH_PANEL(
        titleRes = R.string.gesture_action_search_panel,
        descRes = R.string.external_invocation_entry_search_panel_desc,
        group = QuickMenuGroup.ACTION,
        icon = Icons.Outlined.Search,
    ),
    ENTRY_CLIPBOARD_PANEL(
        titleRes = R.string.gesture_action_clipboard_panel,
        descRes = R.string.external_invocation_entry_clipboard_desc,
        group = QuickMenuGroup.ACTION,
        icon = Icons.Outlined.ContentPaste,
    ),
    ENTRY_PIN_TO_SCREEN(
        titleRes = R.string.gesture_action_pin_to_screen,
        descRes = R.string.gesture_action_pin_to_screen_desc,
        group = QuickMenuGroup.ACTION,
        icon = Icons.Outlined.PushPin,
    ),
    //「闪念」就是侧边面板的两个 tab 之一（代码里叫 Stash，界面上叫 Ideas/闪念）。
    // 这里和上面的「剪贴板」入口是同一个面板的两个 tab，所以两个入口都保留。
    ENTRY_STASH_PANEL(
        titleRes = R.string.gesture_action_stash_panel,
        descRes = R.string.external_invocation_entry_stash_desc,
        group = QuickMenuGroup.ACTION,
        icon = Icons.Outlined.Lightbulb,
    ),
    //「页面滚动」：直接复用动作层的入口（弹出竖条 + 开始滚动）。
    PAGE_SCROLL(
        titleRes = R.string.page_scroll_action_name,
        descRes = R.string.page_scroll_action_description,
        group = QuickMenuGroup.ACTION,
        icon = Icons.Outlined.SwapVert,
    ),
    //「小组件面板」：复用动作层的悬浮窗入口。
    WIDGET_PANEL(
        titleRes = R.string.gesture_action_widget_popup_overlay,
        descRes = R.string.widget_panel_settings_title,
        group = QuickMenuGroup.ACTION,
        icon = Icons.Outlined.Widgets,
    ),
    //「运行宏」：面板内切到宏列表页，点一条就执行。
    MACRO_RUN(
        titleRes = R.string.macro_action_run_macro,
        descRes = R.string.quick_menu_macro_desc,
        group = QuickMenuGroup.ACTION,
        icon = Icons.Outlined.Apps,
    ),
    ;

    /**
     * 该工具是否是“点一下打开独立面板”的预置入口。
     *
     * [com.slideindex.app.overlay.quickmenu.QuickMenuOverlayWindow] 用它兜底：入口打开失败时
     * 留在工具列表，而不是切到一个没有任何内容的空白子页。
     */
    val opensStandalonePanel: Boolean
        get() = when (this) {
            ENTRY_FOREGROUND_ACTIVITY,
            ENTRY_FREEZER,
            ENTRY_SHELL_PANEL,
            ENTRY_SEARCH_PANEL,
            ENTRY_CLIPBOARD_PANEL,
            ENTRY_PIN_TO_SCREEN,
            ENTRY_STASH_PANEL,
            PAGE_SCROLL,
            WIDGET_PANEL,
            FILE_BROWSER,
            -> true

            UNIT_CONVERTER,
            CURRENCY_CONVERTER,
            QR_GENERATOR,
            SCAN_DECODER,
            TEXT_TOOL,
            TRANSLATE,
            PASSWORD,
            STOPWATCH,
            COUNTDOWN,
            REMINDER,
            TIMER_HISTORY,
            CALCULATOR,
            POMODORO,
            COLOR_PICKER,
            MACRO_RUN,
            -> false
        }

    companion object {
        /** 按标题/描述/分组名做一次不区分大小写的包含匹配；空查询返回全部。 */
        fun matching(query: String, titleOf: (QuickMenuTool) -> String, descOf: (QuickMenuTool) -> String): List<QuickMenuTool> {
            val needle = query.trim()
            if (needle.isEmpty()) return entries.toList()
            return entries.filter { tool ->
                titleOf(tool).contains(needle, ignoreCase = true) ||
                    descOf(tool).contains(needle, ignoreCase = true)
            }
        }
    }
}
