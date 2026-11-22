package com.slideindex.app.overlay

import android.content.Context
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.EdgeTriggerSettings
import com.slideindex.app.settings.clearTestSettings
import com.slideindex.app.settings.testSettingsRepository
import com.slideindex.app.settings.runtimeAllTriggerHandles
import com.slideindex.app.settings.withLandscapeCopiedFromPortrait
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class OverlaySettingsFlowTest {
    @Test
    fun copyingLandscape_emitsTheNewCompleteSnapshot_withoutAnExtraToggle() = runBlocking {
        val before = settings()
        val copied = before.withLandscapeCopiedFromPortrait()
        val applied = flowOf(before, copied).distinctOverlaySettings().toList()
        assertEquals(2, applied.size)
        assertSame(copied, applied.last())
        assertEquals(before.leftTriggerHandles, applied.last().leftTriggerHandlesLandscape)
        assertTrue(applied.all { it.serviceEnabled })
    }

    @Test
    fun gestureAndAppearanceChangedTogether_emitOneCompleteFrame() = runBlocking {
        val before = settings()
        val changed = before.withLandscapeCopiedFromPortrait().copy(themeColorArgb = 0xFF123456.toInt())
        val applied = flowOf(before, changed).distinctOverlaySettings().toList()
        assertEquals(listOf(before, changed), applied)
    }

    @Test
    fun unrelatedChangesAndDuplicates_doNotReapplyOverlays() = runBlocking {
        val before = settings()
        val unrelated = before.copy(onboardingCompleted = !before.onboardingCompleted)
        assertEquals(listOf(before), flowOf(before, before.copy(), unrelated).distinctOverlaySettings().toList())
    }

    @Test
    fun nextRelevantChange_carriesLatestOtherSettingsAsWell() = runBlocking {
        val before = settings()
        val unrelated = before.copy(onboardingCompleted = !before.onboardingCompleted)
        val changed = unrelated.withLandscapeCopiedFromPortrait()
        assertEquals(listOf(before, changed), flowOf(before, unrelated, changed).distinctOverlaySettings().toList())
    }

    @Test
    fun deletingAllLandscapeHandles_emitsAnEmptyLayout() = runBlocking {
        val before = settings()
        val empty = before.copy(edgeTrigger = before.edgeTrigger.copy(leftTriggerHandlesLandscape = emptyList()))
        assertEquals(listOf(before, empty), flowOf(before, empty).distinctOverlaySettings().toList())
    }

    @Test
    fun repositoryCopy_reachesRuntimeSubscriber_withoutAnotherSettingsWrite() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        clearTestSettings(context)
        val repository = testSettingsRepository(context)
        repository.setServiceEnabled(true).getOrThrow()
        repository.ensureLandscapeTriggerHandlesInitialized().getOrThrow()
        repository.setTriggerVerticalRange(PanelSide.LEFT, TriggerHandle.DEFAULT_ID, 0.67f, 0.82f, landscape = true).getOrThrow()
        val before = repository.settings.first()
        val applied = async(start = CoroutineStart.UNDISPATCHED) {
            repository.settings.distinctOverlaySettings().first {
                it.leftTriggerHandlesLandscape == before.leftTriggerHandles
            }
        }

        repository.copyPortraitToLandscape().getOrThrow()

        val copied = withTimeout(5_000) { applied.await() }
        assertTrue(copied.serviceEnabled)
        for (side in PanelSide.entries) {
            assertEquals(before.runtimeAllTriggerHandles(side, false), copied.runtimeAllTriggerHandles(side, true))
        }
    }

    private fun settings(): AppSettings = AppSettings(serviceEnabled = true, edgeTrigger = EdgeTriggerSettings(
        landscapeTriggersInitialized = true,
        leftTriggerHandles = listOf(TriggerHandle("portrait", 0.21f, 0.24f)),
        leftTriggerHandlesLandscape = listOf(TriggerHandle("landscape", 0.67f, 0.15f)),
    ))
}
