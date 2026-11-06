package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模块划分的回归网。
 *
 * 这里用「总键数 + 每模块键数快照」而不是逐个键写死：
 * - 新增键 → 总数变化 → 测试红，作者必须回来确认它落在哪个模块（前缀规则改对了就直接改这里）；
 * - 改前缀规则 → 每模块计数变化 → 同样是显式动作。
 *
 * 目的不是证明划分「正确」（那是产品判断），而是保证它**不会静默漂移**——
 * 一旦某个键悄悄从「边角轮盘」漂到「通用」，选择性恢复就会在用户不知情时少恢复/多清一块。
 */
class SettingsModuleRegistryTest {

    @Test
    fun everyDeclaredKeyBelongsToTheExpectedModule() {
        val keys = declaredKeys()
        assertEquals("偏好键总数变了：请确认新键的模块归属并更新快照", EXPECTED_TOTAL_KEYS, keys.size)

        val counts = keys.groupingBy { SettingsModuleRegistry.moduleOf(it) }.eachCount()
        assertEquals(EXPECTED_COUNTS, counts)
    }

    @Test
    fun representativeKeysKeepTheirModule() {
        assertEquals(SettingsModule.CORNER_WHEEL, SettingsModuleRegistry.moduleOf("corner_gesture_enabled"))
        assertEquals(SettingsModule.CORNER_WHEEL, SettingsModuleRegistry.moduleOf("corner_gesture_left_slots"))
        assertEquals(SettingsModule.QUICK_WHEEL, SettingsModuleRegistry.moduleOf("quick_wheel_entries"))
        assertEquals(SettingsModule.SHELL, SettingsModuleRegistry.moduleOf("shell_commands"))
        assertEquals(SettingsModule.ACTIVITY_SHORTCUTS, SettingsModuleRegistry.moduleOf("activity_shortcuts"))
        assertEquals(SettingsModule.ACTIVITY_SHORTCUTS, SettingsModuleRegistry.moduleOf("launcher_shortcut_menu_order"))
        // 这几条专门锁「宽前缀不能抢走特例」：bottom_nav_ 不能落进触钮的 bottom_，quick_launcher 也不能落进通用。
        assertEquals(SettingsModule.THEME_UI, SettingsModuleRegistry.moduleOf("bottom_nav_style_id"))
        assertEquals(SettingsModule.EDGE_GESTURE, SettingsModuleRegistry.moduleOf("bottom_default_trigger_mode"))
        assertEquals(SettingsModule.THEME_UI, SettingsModuleRegistry.moduleOf("top_app_bar_blur_style_id"))
        assertEquals(SettingsModule.QUICK_LAUNCHER, SettingsModuleRegistry.moduleOf("quick_launcher"))
        assertEquals(SettingsModule.FREE_WINDOW, SettingsModuleRegistry.moduleOf("app_launch_policy_id"))
        assertEquals(SettingsModule.QUICK_LAUNCHER, SettingsModuleRegistry.moduleOf("app_carousel_cancel_distance_dp"))
        assertEquals(SettingsModule.SEARCH, SettingsModuleRegistry.moduleOf("ocr_engine_id"))
        assertEquals(SettingsModule.SHAKE, SettingsModuleRegistry.moduleOf("back_tap_enabled"))
        assertEquals(SettingsModule.GENERAL, SettingsModuleRegistry.moduleOf("service_enabled"))
    }

    @Test
    fun backupDirectoriesMapToOwningModules() {
        assertEquals(SettingsModule.SEARCH, SettingsModuleRegistry.moduleOfDirectory("search_icons"))
        assertEquals(SettingsModule.SHELL, SettingsModuleRegistry.moduleOfDirectory("shell_icons"))
        assertEquals(SettingsModule.ACTIVITY_SHORTCUTS, SettingsModuleRegistry.moduleOfDirectory("shortcut_icons"))
        assertEquals(SettingsModule.FLOAT_BALL, SettingsModuleRegistry.moduleOfDirectory("float_ball_assets"))
        assertEquals(SettingsModule.STASH, SettingsModuleRegistry.moduleOfDirectory("stash"))
        assertEquals(SettingsModule.CLIPBOARD, SettingsModuleRegistry.moduleOfDirectory("clipboard"))
        assertEquals(SettingsModule.SEARCH, SettingsModuleRegistry.moduleOfDirectory("share_image_ocr_history"))
        assertEquals(null, SettingsModuleRegistry.moduleOfDirectory("not_a_backup_dir"))
    }

    @Test
    fun backupSectionsMapToOwningModules() {
        assertEquals(SettingsModule.OTP, SettingsBackupSection.OTP_RECORDS.module)
        assertEquals(SettingsModule.OTP, SettingsBackupSection.OTP_AUTO_FILL_STATS.module)
        assertEquals(SettingsModule.MESSAGE, SettingsBackupSection.NOTIFICATION_HISTORY.module)
        assertEquals(SettingsModule.MESSAGE, SettingsBackupSection.NOTIFICATION_FILTER_RULES.module)
        assertEquals(SettingsModule.MESSAGE, SettingsBackupSection.NOTIFICATION_FILTER_PREFERENCES.module)
        assertEquals(SettingsModule.SHELL, SettingsBackupSection.SHELL_OUTPUT_HISTORY.module)
        assertEquals(SettingsModule.SEARCH, SettingsBackupSection.SEARCH_PANEL_HISTORY.module)
    }

    @Test
    fun shareableModulesAreTheOnesSafeToExport() {
        assertTrue(SettingsModule.SHELL.shareable)
        assertTrue(SettingsModule.ACTIVITY_SHORTCUTS.shareable)
        // 隐私/设备相关模块不允许单独分享。
        assertTrue(!SettingsModule.OTP.shareable)
        assertTrue(!SettingsModule.CLIPBOARD.shareable)
        assertTrue(!SettingsModule.MESSAGE.shareable)
        assertTrue(!SettingsModule.GENERAL.shareable)
    }

    private fun declaredKeys(): Set<String> =
        SettingsPreferenceKeys::class.java.declaredFields
            .filter { Preferences.Key::class.java.isAssignableFrom(it.type) }
            .map { field ->
                field.isAccessible = true
                (field.get(null) as Preferences.Key<*>).name
            }
            .toSet()

    private companion object {
        const val EXPECTED_TOTAL_KEYS = 666

        val EXPECTED_COUNTS: Map<SettingsModule, Int> = mapOf(
            SettingsModule.EDGE_GESTURE to 84,
            SettingsModule.CORNER_WHEEL to 34,
            SettingsModule.QUICK_WHEEL to 2,
            SettingsModule.FLOAT_BALL to 70,
            SettingsModule.FLOATING_POINTER to 52,
            SettingsModule.FINGERTIP_RING to 13,
            SettingsModule.QUICK_LAUNCHER to 91,
            SettingsModule.WIDGET_PANEL to 7,
            SettingsModule.MESSAGE to 73,
            SettingsModule.OTP to 24,
            SettingsModule.SHAKE to 42,
            SettingsModule.CLIPBOARD to 59,
            SettingsModule.DRAG_DROP to 5,
            SettingsModule.STASH to 5,
            SettingsModule.SEARCH to 40,
            SettingsModule.FREE_WINDOW to 13,
            SettingsModule.FREEZER to 6,
            SettingsModule.SHELL to 1,
            SettingsModule.ACTIVITY_SHORTCUTS to 3,
            SettingsModule.THEME_UI to 19,
            SettingsModule.GENERAL to 23,
        )
    }
}
