package com.slideindex.app.freezer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezerBootstrapTest {
    @Test
    fun `importable packages skip user excluded apps`() {
        val importable = FreezerBootstrap.importablePackages(
            scanned = setOf("com.example.a", "com.example.b"),
            excluded = setOf("com.example.a"),
        )
        assertEquals(setOf("com.example.b"), importable)
    }

    @Test
    fun `importable packages is empty when all scanned are excluded`() {
        val importable = FreezerBootstrap.importablePackages(
            scanned = setOf("com.example.a"),
            excluded = setOf("com.example.a"),
        )
        assertTrue(importable.isEmpty())
    }

    @Test
    fun `disabled or suspended apps are importable`() {
        assertTrue(FreezerBootstrap.isImportableState(enabled = false, suspended = false))
        assertTrue(FreezerBootstrap.isImportableState(enabled = true, suspended = true))
        assertTrue(FreezerBootstrap.isImportableState(enabled = false, suspended = true))
    }

    @Test
    fun `active apps are not importable`() {
        assertFalse(FreezerBootstrap.isImportableState(enabled = true, suspended = false))
    }

    @Test
    fun `imported state is recorded as intent`() {
        assertEquals(
            com.slideindex.app.settings.FreezerAppIntent.FROZEN,
            FreezerBootstrap.intentForState(FreezerAppState.FROZEN),
        )
        assertEquals(
            com.slideindex.app.settings.FreezerAppIntent.PAUSE,
            FreezerBootstrap.intentForState(FreezerAppState.PAUSED),
        )
        assertEquals(null, FreezerBootstrap.intentForState(FreezerAppState.ACTIVE))
    }
}
