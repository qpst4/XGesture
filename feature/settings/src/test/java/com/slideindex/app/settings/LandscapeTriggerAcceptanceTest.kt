package com.slideindex.app.settings

import android.content.Context
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.PanelSide
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 通过设置页使用的持久化入口验证横屏编辑行为，包含再次进入设置的过程。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class LandscapeTriggerAcceptanceTest {
    private lateinit var context: Context
    private lateinit var editor: SettingsPreferencesEditor
    private lateinit var edge: EdgeSettingsMutator

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        editor = SettingsPreferencesEditor(context)
        edge = EdgeSettingsMutator(editor, context)
        editor.edit { it.clear() }.getOrThrow()
    }

    @After
    fun tearDown() = runBlocking {
        editor.edit { it.clear() }.getOrThrow()
    }

    @Test
    fun deletingLastHandle_staysEmptyInEditorAndRuntime_afterReenteringSettings() = runBlocking {
        val initial = profile()
        seed(initial)
        edge.removeTriggerHandle(PanelSide.LEFT, "landscape-left", landscape = true).getOrThrow()

        assertTrue(read().forLandscapeEditing().allTriggerHandles(PanelSide.LEFT).isEmpty())
        edge.ensureLandscapeTriggerHandlesInitialized().getOrThrow()
        val reopened = SettingsPreferencesEditor(context).settings.first()
        assertTrue(reopened.forLandscapeEditing().allTriggerHandles(PanelSide.LEFT).isEmpty())
        assertTrue(reopened.runtimeAllTriggerHandles(PanelSide.LEFT, isLandscape = true).isEmpty())
        assertEquals(initial.leftTriggerHandles, reopened.leftTriggerHandles)
        assertEquals(initial.gestureRules, reopened.gestureRules)
    }

    @Test
    fun deletingAllFourSides_doesNotBringBackPreviouslyDeletedSides() = runBlocking {
        val initial = profile()
        seed(initial)
        for (side in PanelSide.entries) {
            val id = initial.storedLandscapeTriggerHandles(side).single().id
            edge.removeTriggerHandle(side, id, landscape = true).getOrThrow()
        }

        val saved = read()
        for (side in PanelSide.entries) {
            assertTrue("横屏 $side 应保持为空", saved.storedLandscapeTriggerHandles(side).isEmpty())
            assertEquals(initial.allTriggerHandles(side), saved.allTriggerHandles(side))
        }
        edge.ensureLandscapeTriggerHandlesInitialized().getOrThrow()
        val reopened = read()
        for (side in PanelSide.entries) {
            assertTrue(reopened.forLandscapeEditing().allTriggerHandles(side).isEmpty())
        }
    }

    @Test
    fun deletingTopDefaultHandle_remainsDisallowed() = runBlocking {
        val initial = profile().let {
            it.copy(edgeTrigger = it.edgeTrigger.copy(
                topTriggerHandlesLandscape = listOf(TriggerHandle.topDefault()),
            ))
        }
        seed(initial)
        edge.removeTriggerHandle(PanelSide.TOP, TriggerHandle.DEFAULT_ID, landscape = true).getOrThrow()
        assertEquals(initial.topTriggerHandlesLandscape, read().topTriggerHandlesLandscape)
    }

    private fun profile(): AppSettings {
        val portrait = listOf(
            TriggerHandle("portrait-a", 0.1f, 0.4f),
            TriggerHandle("portrait-b", 0.2f, 0.4f),
        )
        return AppSettings(edgeTrigger = EdgeTriggerSettings(
            leftTriggerHandles = portrait,
            rightTriggerHandles = portrait,
            bottomTriggerHandles = portrait,
            topTriggerHandles = portrait,
            landscapeTriggersInitialized = true,
            leftTriggerHandlesLandscape = listOf(TriggerHandle("landscape-left", 0.2f, 0.2f)),
            rightTriggerHandlesLandscape = listOf(TriggerHandle("landscape-right", 0.2f, 0.2f)),
            bottomTriggerHandlesLandscape = listOf(TriggerHandle("landscape-bottom", 0.2f, 0.2f)),
            topTriggerHandlesLandscape = listOf(TriggerHandle("landscape-top", 0.2f, 0.2f)),
            gestureRulesLandscape = listOf(GestureRule.slot(
                PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN,
                GestureAction.Screenshot, handleId = "landscape-left",
            )),
        ))
    }

    private suspend fun seed(settings: AppSettings) {
        editor.edit { prefs ->
            SettingsTriggerStore.writeTriggerHandles(prefs, settings)
            SettingsTriggerStore.writeLandscapeSettings(prefs, settings)
        }.getOrThrow()
    }

    private suspend fun read(): AppSettings = editor.settings.first()
}
