package com.slideindex.app.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * schema 迁移表的筛选与排序：版本号驱动升级的正确性全在这里。
 *
 * 另含一次真实事故的回归（见文件后半段）：**同名换类型**必须靠"换名字 + 一次性迁移"，
 * 不能原地复用。`clipboard_history_float_handle_width_dp` 从 v1.10.x 起以 **int** 落盘，
 * 新代码把同一个名字声明成 float 去读 —— DataStore 的 `Preferences.Key` 只按名字判等、
 * 值保留旧类型，于是读设置那一行抛 `ClassCastException: Integer cannot be cast to Float`：
 * 进程起不来 → 无障碍被系统记成 crashed → 触钮/悬浮球/轮盘全部挂不上。
 */
class SettingsSchemaMigrationsTest {

    @Test
    fun `current version is positive and migrations never exceed it`() {
        assertTrue(SettingsSchemaMigrations.CURRENT_VERSION >= 1)
        SettingsSchemaMigrations.all.forEach { migration ->
            assertTrue(migration.version in 1..SettingsSchemaMigrations.CURRENT_VERSION)
        }
    }

    @Test
    fun `pending on latest version is empty`() {
        assertTrue(
            SettingsSchemaMigrations.pending(SettingsSchemaMigrations.CURRENT_VERSION).isEmpty(),
        )
    }

    @Test
    fun `pending from zero returns all migrations in ascending order`() {
        val pending = SettingsSchemaMigrations.pending(0)

        assertEquals(SettingsSchemaMigrations.all.size, pending.size)
        assertEquals(pending.sortedBy { it.version }, pending)
    }

    @Test
    fun `migration versions are unique`() {
        val versions = SettingsSchemaMigrations.all.map { it.version }

        assertEquals(versions.size, versions.distinct().size)
    }

    // ---- 同名换类型（int → float）的一次性清理 ----

    private val legacyKey = SettingsPreferenceKeys.LEGACY_CLIPBOARD_HISTORY_FLOAT_HANDLE_WIDTH_DP
    private val widthKey = SettingsPreferenceKeys.CLIPBOARD_HISTORY_FLOAT_HANDLE_WIDTH_DP

    private fun migrate(prefs: MutablePreferences) {
        SettingsSchemaMigrations.pending(0).forEach { it.block(prefs) }
    }

    @Test
    fun `legacy int handle width is dropped without inventing a new value`() {
        // 旧版本留下的 int 24dp。两套量程（旧 24..50 / 新 5..14）不重叠，任何换算都是猜，
        // 所以只删旧键：外观回落到新版默认值，而不是被映射成 5dp（最细）或 14dp（最粗）。
        val prefs = mutablePreferencesOf(legacyKey to 24)

        migrate(prefs)

        assertNull("不该凭空写一个新宽度", prefs[widthKey])
        assertNull("旧键必须删掉，否则会继续被导出进备份", prefs[legacyKey])
    }

    @Test
    fun `existing new value is never touched by the cleanup`() {
        val prefs = mutablePreferencesOf(legacyKey to 24, widthKey to 8.6f)

        migrate(prefs)

        assertEquals(8.6f, prefs[widthKey] ?: -1f, 0.001f)
        assertNull(prefs[legacyKey])
    }

    @Test
    fun `handle width cleanup is idempotent`() {
        val prefs = mutablePreferencesOf(legacyKey to 24)

        migrate(prefs)
        migrate(prefs)

        assertNull(prefs[widthKey])
        assertNull(prefs[legacyKey])
    }

    @Test
    fun `legacy key is removed even when its type is not int`() {
        // 旧备份还原可能把同名键写成别的类型：清理只按名字删，不读值，所以不会抛 CCE。
        val wrongType = floatPreferencesKey("clipboard_history_float_handle_width_dp")
        val prefs = mutablePreferencesOf(wrongType to 12f)

        migrate(prefs)

        assertNull("同名记录必须清掉，不管它是什么类型", prefs.asMap()[wrongType])
        assertNull(prefs[widthKey])
    }
}
