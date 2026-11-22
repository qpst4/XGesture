package com.slideindex.app.xposed.bridge

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 更新后的真实模块代优先于“本次开机更新过”以及滞留旧接收器的回执。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ModuleGenerationStatusAcceptanceTest {
    private lateinit var context: Context
    private val code = ModuleHookBridgeContract.MODULE_CODE_VERSION
    private val healthy = "ready:hook=ok,code=$code,clip=ok,wl=1"

    @Before
    fun installUpdatedApk() {
        context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime = System.currentTimeMillis()
        shadowOf(context.packageManager).installPackage(info)
        assertTrue(ModuleStatusFields.apkInstalledAfterBoot(context))
    }

    @Test
    fun currentApkGeneration_canBeReadyAfterUpdate_withoutReboot() {
        assertEquals(ModuleStatusFields.CodeState.Current,
            ModuleStatusFields.codeStateOfDetail(context, current(healthy)))
    }

    @Test
    fun oldApkGeneration_remainsStale_evenWhenModuleCodeVersionMatches() {
        assertEquals(ModuleStatusFields.CodeState.Stale,
            ModuleStatusFields.codeStateOfDetail(context, "$healthy,apk=/old/base.apk"))
    }

    @Test
    fun delayedLegacyReply_doesNotReplaceCurrentGenerationStatus() {
        val current = current(healthy)
        ModuleBridgeStatusStore.write(context, true, "ready", current)
        ModuleBridgeStatusStore.write(context, true, "ready", healthy)
        assertEquals(current, ModuleBridgeStatusStore.read(context).detail)
    }

    @Test
    fun currentGenerationFailure_isNotHiddenByPreviousHealthyStatus() {
        ModuleBridgeStatusStore.write(context, true, "ready", current(healthy))
        val failed = current("not-ready:hook=fail,code=$code,clip=failed")
        ModuleBridgeStatusStore.write(context, false, "not-ready", failed)
        val snapshot = ModuleBridgeStatusStore.read(context)
        assertEquals(failed, snapshot.detail)
        assertEquals("not-ready", snapshot.state)
    }

    private fun current(detail: String) = "$detail,apk=${context.applicationInfo.sourceDir}"
}
