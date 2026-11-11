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

    // ---- 同名换类型（int → float）的一次性迁移 ----

    private val legacyKey = SettingsPreferenceKeys.LEGACY_CLIPBOARD_HISTORY_FLOAT_HANDLE_WIDTH_DP
    private val widthKey = SettingsPreferenceKeys.CLIPBOARD_HISTORY_FLOAT_HANDLE_WIDTH_DP

    private fun migrate(prefs: MutablePreferences) {
        SettingsSchemaMigrations.pending(0).forEach { it.block(prefs) }
    }

    @Test
    fun `legacy int handle width moves into the v2 float key`() {
        // 旧版本留下的 int 24dp（旧滑条最细一档）→ 新版量程最细 5dp。
        val prefs = mutablePreferencesOf(legacyKey to 24)

        migrate(prefs)

        assertEquals(5f, prefs[widthKey] ?: -1f, 0.001f)
        assertNull("迁移完必须删掉旧键，否则会继续被导出进备份", prefs[legacyKey])
    }

    @Test
    fun `legacy width maps by relative position, not by clamping`() {
        // 旧量程 24..50 与新量程 5..14 不重叠：直接钳位会把"当年选最细"的人推到"最粗"，
        // 所以按相对位置映射（最粗 → 最粗）。
        val widest = mutablePreferencesOf(legacyKey to 50)
        migrate(widest)
        assertEquals(14f, widest[widthKey] ?: -1f, 0.001f)

        // 旧默认 32dp 应落在新量程中间段（不对新默认做硬约束，只锁住单调性）。
        val defaulted = mutablePreferencesOf(legacyKey to 32)
        migrate(defaulted)
        val mapped = defaulted[widthKey] ?: -1f
        assertTrue("旧默认值应落在新量程中间段，实际 $mapped", mapped > 5f && mapped < 14f)
    }

    @Test
    fun `value already written by the new code wins over the legacy one`() {
        val prefs = mutablePreferencesOf(legacyKey to 24, widthKey to 31f)

        migrate(prefs)

        assertEquals("用户升级后已经调过的宽度不能被旧值盖掉", 31f, prefs[widthKey] ?: -1f, 0.001f)
        assertNull(prefs[legacyKey])
    }

    @Test
    fun `handle width migration is idempotent`() {
        val prefs = mutablePreferencesOf(legacyKey to 24)

        migrate(prefs)
        migrate(prefs)

        assertEquals(5f, prefs[widthKey] ?: -1f, 0.001f)
        assertNull(prefs[legacyKey])
    }

    @Test
    fun `unexpected legacy type does not crash the migration`() {
        // 旧备份还原等场景可能把同名键写成别的类型：迁移必须跳过而不是抛 CCE。
        val wrongType = floatPreferencesKey("clipboard_history_float_handle_width_dp")
        val prefs = mutablePreferencesOf(wrongType to 12f)

        migrate(prefs)

        assertNull("类型不是 int 时不该写出新键", prefs[widthKey])
    }
}
