package com.slideindex.app.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleReloadTargetsTest {
    private val systemLoader = object : ClassLoader() {}
    private val phoneLoader = object : ClassLoader() {}
    private val providerLoader = object : ClassLoader() {}

    @Test
    fun emptyOutgoingState_usesFrameworkOwnedCollection_soTheFrameworkCanAcceptIt() {
        val saved = ModuleReloadTargets().save()
        assertSame(java.util.LinkedHashMap::class.java.classLoader, saved.javaClass.classLoader)
    }

    @Test
    fun newGeneration_restoresSystemTarget_andBothPackagesSharingAPhoneProcess() {
        val old = ModuleReloadTargets().apply {
            remember(ModuleReloadTargets.SYSTEM, systemLoader)
            remember(ModuleReloadTargets.PHONE, phoneLoader)
            remember(ModuleReloadTargets.TELEPHONY, providerLoader)
        }
        val replacement = ModuleReloadTargets().apply { restore(old.save()) }
        assertSame(systemLoader, replacement.systemLoader(emptyList()))
        assertEquals(mapOf(
            ModuleReloadTargets.PHONE to phoneLoader,
            ModuleReloadTargets.TELEPHONY to providerLoader,
        ), replacement.packageLoaders(ModuleReloadTargets.PHONE))
    }

    @Test
    fun outgoingSnapshot_isNotChangedByLaterPackageCallbacks() {
        val old = ModuleReloadTargets().apply { remember(ModuleReloadTargets.PHONE, phoneLoader) }
        val captured = old.save()
        old.remember(ModuleReloadTargets.TELEPHONY, providerLoader)
        val replacement = ModuleReloadTargets().apply { restore(captured) }
        assertEquals(mapOf(ModuleReloadTargets.PHONE to phoneLoader), replacement.packageLoaders("unused"))
    }

    @Test
    fun legacyOrMalformedState_doesNotCrash_orLoadUnrelatedPackages() {
        for (saved in listOf(null, "old-version-state", listOf(systemLoader), mapOf(
            "unrelated.app" to phoneLoader,
            ModuleReloadTargets.PHONE to "not-a-loader",
            123 to providerLoader,
        ))) {
            val replacement = ModuleReloadTargets().apply { restore(saved) }
            assertTrue(replacement.packageLoaders("unrelated.app").isEmpty())
        }
    }
}
