package com.slideindex.app.xposed.bridge

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import com.slideindex.app.di.ModuleHookConfigSync
import com.slideindex.app.overlay.OverlayDisplayRotationMonitor
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.EdgeTriggerSettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.settings.clearTestSettings
import com.slideindex.app.settings.testSettingsRepository
import com.slideindex.app.util.OverlaySuppression
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** 横屏复制/编辑后，模块应立刻收到与屏幕触钮一致的接管区域，无需切换开关。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "land")
class LandscapeTriggerRefreshAcceptanceTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun copyingPortraitInLandscape_changesPublishedConfiguration_withoutTogglingAnything() = runBlocking {
        assertTrue("验收环境必须为横屏", OverlaySuppression.isLandscape(context))
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        repository.setServiceEnabled(true).getOrThrow()
        repository.ensureLandscapeTriggerHandlesInitialized().getOrThrow()
        repository.setTriggerVerticalRange(com.slideindex.app.overlay.PanelSide.LEFT,
            TriggerHandle.DEFAULT_ID, 0.67f, 0.82f, landscape = true).getOrThrow()
        val before = repository.settings.first()
        // 故障注入：仓库缓存保持旧帧，真实 DataStore 与变更流照常运行。
        SettingsRepository::class.java.getDeclaredField("cacheScope").apply { isAccessible = true }
            .get(repository).let { (it as CoroutineScope).cancel() }
        SettingsRepository::class.java.getDeclaredField("cachedSettings").apply { isAccessible = true }
            .set(repository, before)
        withSync(repository) { published ->
            withTimeout(5_000) { published.receive() }

            repository.copyPortraitToLandscape().getOrThrow()

            val copied = withTimeoutOrNull(2_000) { published.receive() }
            assertNotNull("复制后应自动广播新区域，无需开关触钮或等待缓存更新", copied)
            assertEquals(before.leftTriggerHandles.single().topFraction, copied!!.side(0)!!.handles.single().topFraction)
            assertEquals(before, repository.readSnapshot())
        }
    }

    @Test
    @Config(qualifiers = "port")
    fun rotatingDisplay_republishesTheOtherLayout_withoutAnySettingsWrite() = runBlocking {
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        repository.ensureLandscapeTriggerHandlesInitialized().getOrThrow()
        repository.setTriggerVerticalRange(com.slideindex.app.overlay.PanelSide.LEFT,
            TriggerHandle.DEFAULT_ID, 0.67f, 0.82f, landscape = true).getOrThrow()
        withSync(repository) { published ->
            withTimeout(5_000) { published.receive() }

            RuntimeEnvironment.setQualifiers("+land")
            shadowOf(context.getSystemService(WindowManager::class.java).defaultDisplay).setRotation(Surface.ROTATION_90)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))

            val rotated = withTimeoutOrNull(2_000) { published.receive() }
            assertNotNull("旋转不是配置写入，也必须重新下发当前方向布局", rotated)
            assertEquals(0.67f, rotated!!.side(0)!!.handles.single().topFraction)
        }
    }

    @Test
    fun landscapeSnapshot_usesLandscapeGeometryAndWidths_onAllFourSides() {
        val snapshot = ModuleHookConfigWriter.buildSnapshot(context, settings())
        for (side in 0..3) {
            val handle = snapshot.side(side)!!.handles.single()
            assertEquals("每一侧都应下发横屏位置", 0.67f, handle.topFraction)
            assertEquals(0.15f, handle.heightFraction)
            assertEquals(31f, handle.widthDp)
        }
    }

    @Test
    fun deletedLandscapeHandles_remainEmptyInPublishedConfiguration() {
        val before = settings()
        val empty = before.copy(edgeTrigger = before.edgeTrigger.copy(
            leftTriggerHandlesLandscape = emptyList(), rightTriggerHandlesLandscape = emptyList(),
            bottomTriggerHandlesLandscape = emptyList(), topTriggerHandlesLandscape = emptyList(),
        ))
        assertTrue(ModuleHookConfigWriter.buildSnapshot(context, empty).sides.all { it.handles.isEmpty() })
    }

    @Test
    @Config(qualifiers = "port")
    fun portraitSnapshot_keepsPortraitGeometry() {
        assertEquals(0.21f, ModuleHookConfigWriter.buildSnapshot(context, settings()).side(0)!!.handles.single().topFraction)
    }

    @Test
    fun uninitializedLandscape_keepsPortraitFallback() {
        val before = settings()
        val uninitialized = before.copy(edgeTrigger = before.edgeTrigger.copy(landscapeTriggersInitialized = false))
        assertEquals(0.21f, ModuleHookConfigWriter.buildSnapshot(context, uninitialized).side(0)!!.handles.single().topFraction)
    }

    private suspend fun withSync(repository: SettingsRepository, block: suspend (Channel<ModuleHookSnapshot>) -> Unit) {
        val published = Channel<ModuleHookSnapshot>(Channel.UNLIMITED)
        val recordingContext = object : ContextWrapper(context) {
            override fun sendBroadcast(intent: Intent) {
                if (intent.action == ModuleHookBridgeContract.ACTION_CONFIG_CHANGED) {
                    published.trySend(ModuleHookSnapshot.parse(intent.getStringExtra(ModuleHookBridgeContract.EXTRA_CONFIG_JSON))!!)
                } else super.sendBroadcast(intent)
            }
        }
        val sync = ModuleHookConfigSync(recordingContext, repository)
        try {
            sync.start()
            block(published)
        } finally {
            ModuleHookConfigSync::class.java.getDeclaredField("scope").apply { isAccessible = true }
                .get(sync).let { (it as CoroutineScope).cancel() }
            runCatching {
                ModuleHookConfigSync::class.java.getDeclaredField("rotationMonitor").apply { isAccessible = true }
                    .get(sync).let { (it as OverlayDisplayRotationMonitor).stop() }
            }
        }
    }

    private fun settings(): AppSettings {
        val portrait = listOf(TriggerHandle(id = "portrait", topFraction = 0.21f, heightFraction = 0.24f, edgeWidthDp = 23f))
        val landscape = listOf(TriggerHandle(id = "landscape", topFraction = 0.67f, heightFraction = 0.15f, edgeWidthDp = 31f))
        return AppSettings(serviceEnabled = true, edgeTrigger = EdgeTriggerSettings(
            landscapeTriggersInitialized = true,
            leftTriggerHandles = portrait, rightTriggerHandles = portrait,
            bottomTriggerHandles = portrait, topTriggerHandles = portrait,
            leftTriggerHandlesLandscape = landscape, rightTriggerHandlesLandscape = landscape,
            bottomTriggerHandlesLandscape = landscape, topTriggerHandlesLandscape = landscape,
        ))
    }
}
