package com.slideindex.app.settings

/**
 * 主 DataStore 之外的独立存储（其他 DataStore / SharedPreferences / 单文件 / SQLite）。
 *
 * 这些数据以前不在备份清单里，只能靠人记；现在统一登记到这里：
 * - [id] 写进备份文件，**改动等于改格式**，只能新增不能重命名；
 * - [module] 决定“按模块导出/恢复”时它跟谁走；
 * - [sensitive] 为 true 时只在“包含敏感数据”的备份里导出。
 *
 * 文件目录（`fooView/`、`action_icons/` 等）不走这里，仍由
 * [SettingsBackupPaths.ALWAYS_BACKUP_DIRS] + [SettingsModuleRegistry.DIRECTORY_MODULES] 管理。
 */
enum class SettingsBackupExternalStore(
    val id: String,
    val module: SettingsModule,
    val sensitive: Boolean = false,
) {
    /** 手势宏；`xgesture_macros`。 */
    MACRO("macro", SettingsModule.EDGE_GESTURE),

    /** 剪贴板文字历史；SQLite `clipboard_history.db`，不能直接拷文件。 */
    CLIPBOARD_HISTORY("clipboard_history", SettingsModule.CLIPBOARD, sensitive = true),

    /** 收纳箱标签/完成态/追加/提醒；`files/stash_meta.json`。 */
    STASH_META("stash_meta", SettingsModule.STASH),

    /** 动作自定义显示名；`action_display_names`。 */
    ACTION_DISPLAY_NAMES("action_display_names", SettingsModule.THEME_UI),

    /** 动作/应用全局图标覆盖；`global_icon_overrides`。 */
    GLOBAL_ICON_OVERRIDES("global_icon_overrides", SettingsModule.THEME_UI),

    /** 页面滚动速度/控制条位置；`page_scroll`。 */
    PAGE_SCROLL("page_scroll", SettingsModule.EDGE_GESTURE),

    /** 屏幕控制面板尺寸/位置；`screen_control`。 */
    SCREEN_CONTROL("screen_control", SettingsModule.GENERAL),

    /** 文件浏览器钉住/标签页/排序；`slide_index_file_browser_prefs`。 */
    FILE_BROWSER("file_browser", SettingsModule.GENERAL),

    /** 分享面板钉住的目标；`slide_index_share_sheet`。 */
    SHARE_SHEET("share_sheet", SettingsModule.GENERAL),

    /** 分享面板的默认目标（文字 / 图片各一个）；同一个 `slide_index_share_sheet`。 */
    SHARE_DEFAULTS("share_defaults", SettingsModule.GENERAL),

    /** 快捷菜单默认值/模块顺序；`slide_index_quick_menu_prefs`。 */
    QUICK_MENU("quick_menu", SettingsModule.GENERAL),

    /** 取词面板上次使用的图片引擎/全屏状态；`pick_result_prefs`。 */
    PICK_RESULT("pick_result", SettingsModule.SEARCH),

    /** 自定义手势录入弹窗的“同时添加对称手势”记忆位；`fv_gesture`。 */
    FV_GESTURE("fv_gesture", SettingsModule.FLOAT_BALL),
    ;

    companion object {
        private val BY_ID = entries.associateBy { it.id }

        fun fromId(id: String): SettingsBackupExternalStore? = BY_ID[id]
    }
}

/** 只保留选中模块的外部存储；`modules == null` 时原样返回。 */
internal fun Map<String, String>.forModules(
    modules: Set<SettingsModule>?,
): Map<String, String> {
    if (modules == null) return this
    return filterKeys { id -> SettingsBackupExternalStore.fromId(id)?.module in modules }
}
