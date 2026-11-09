package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * schema 迁移表的筛选与排序：版本号驱动升级的正确性全在这里。
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
}
