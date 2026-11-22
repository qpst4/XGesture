package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class TriggerLandscapeSettingsTest {
    private val portrait = listOf(TriggerHandle("portrait", 0.1f, 0.4f))

    @Test
    fun initializedEmptySides_areEmptyWhileEditing() {
        val settings = initialized()
        for (side in PanelSide.entries) {
            assertTrue("已初始化的 $side 不应回退竖屏", settings.forLandscapeEditing().allTriggerHandles(side).isEmpty())
        }
    }

    @Test
    fun partialLandscapeProfile_doesNotBorrowOtherPortraitSides() {
        val handle = TriggerHandle("landscape", 0.2f, 0.2f)
        val settings = initialized().let {
            it.copy(edgeTrigger = it.edgeTrigger.copy(leftTriggerHandlesLandscape = listOf(handle)))
        }
        val editing = settings.forLandscapeEditing()
        assertEquals(listOf(handle), editing.leftTriggerHandles)
        assertTrue(editing.rightTriggerHandles.isEmpty())
        assertTrue(editing.bottomTriggerHandles.isEmpty())
        assertTrue(editing.topTriggerHandles.isEmpty())
    }

    @Test
    fun uninitializedProfile_stillPreviewsPortraitHandles() {
        val settings = initialized().let {
            it.copy(edgeTrigger = it.edgeTrigger.copy(landscapeTriggersInitialized = false))
        }
        for (side in PanelSide.entries) {
            assertEquals(settings.allTriggerHandles(side), settings.forLandscapeEditing().allTriggerHandles(side))
        }
    }

    @Test
    fun removingLastLandscapeHandle_doesNotChangePortraitHandlesOrRules() {
        val handle = TriggerHandle("landscape", 0.2f, 0.2f)
        val rule = GestureRule.slot(
            PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN,
            GestureAction.Screenshot, handleId = handle.id,
        )
        val settings = initialized().let {
            it.copy(edgeTrigger = it.edgeTrigger.copy(
                leftTriggerHandlesLandscape = listOf(handle), gestureRulesLandscape = listOf(rule),
            ))
        }
        val edited = settings.forLandscapeEditing().withRemovedTriggerHandle(PanelSide.LEFT, handle.id)
        val saved = settings.mergeLandscapeEdits(edited)
        assertTrue(saved.leftTriggerHandlesLandscape.isEmpty())
        assertTrue(saved.gestureRulesLandscape.isEmpty())
        assertEquals(settings.leftTriggerHandles, saved.leftTriggerHandles)
        assertEquals(settings.gestureRules, saved.gestureRules)
        assertTrue(saved.forLandscapeEditing().leftTriggerHandles.isEmpty())
    }

    @Test
    fun layoutRepair_doesNotRecreateDeletedSidesFromOverlappingPortraitHandles() {
        val overlapping = portrait + TriggerHandle("portrait-overlap", 0.2f, 0.4f)
        val settings = initialized().let {
            it.copy(edgeTrigger = it.edgeTrigger.copy(
                leftTriggerHandles = overlapping, rightTriggerHandles = overlapping,
                bottomTriggerHandles = overlapping, topTriggerHandles = overlapping,
            ))
        }
        assertEquals(settings, settings.withRepairedLandscapeHandleLayoutIfOverlapping())
    }

    @Test
    fun layoutRepair_stillSeparatesStoredOverlappingHandles_withoutChangingIdentity() {
        val handles = listOf(
            TriggerHandle("first", 0.1f, 0.4f), TriggerHandle("second", 0.2f, 0.4f),
        )
        val settings = initialized().let {
            it.copy(edgeTrigger = it.edgeTrigger.copy(leftTriggerHandlesLandscape = handles))
        }
        val repaired = settings.withRepairedLandscapeHandleLayoutIfOverlapping().leftTriggerHandlesLandscape
        assertEquals(handles.map { it.id }, repaired.map { it.id })
        assertTrue(repaired[0].bottomFraction <= repaired[1].topFraction)
        assertEquals(settings.leftTriggerHandles, settings.withRepairedLandscapeHandleLayoutIfOverlapping().leftTriggerHandles)
    }

    @Test
    fun runtime_keepsDisabledHandlesEditable_butDoesNotEnableThem() {
        val disabled = TriggerHandle("disabled", 0.2f, 0.2f, enabled = false)
        val settings = initialized().let {
            it.copy(edgeTrigger = it.edgeTrigger.copy(leftTriggerHandlesLandscape = listOf(disabled)))
        }
        assertEquals(listOf(disabled), settings.forLandscapeEditing().leftTriggerHandles)
        assertEquals(listOf(disabled), settings.runtimeAllTriggerHandles(PanelSide.LEFT, isLandscape = true))
        assertEquals(disabled, settings.runtimeTriggerHandle(PanelSide.LEFT, disabled.id, isLandscape = true))
        assertTrue(settings.runtimeTriggerHandles(PanelSide.LEFT, isLandscape = true).isEmpty())
        assertEquals(portrait, settings.runtimeTriggerHandles(PanelSide.LEFT, isLandscape = false))
    }

    @Test
    fun layoutRepair_keepsNonOverlappingCustomPositions() {
        val handles = listOf(
            TriggerHandle("first", 0.05f, 0.1f), TriggerHandle("second", 0.8f, 0.1f),
        )
        val settings = initialized().let {
            it.copy(edgeTrigger = it.edgeTrigger.copy(leftTriggerHandlesLandscape = handles))
        }
        assertEquals(settings, settings.withRepairedLandscapeHandleLayoutIfOverlapping())
    }

    private fun initialized(): AppSettings = AppSettings(edgeTrigger = EdgeTriggerSettings(
        landscapeTriggersInitialized = true,
        leftTriggerHandles = portrait, rightTriggerHandles = portrait,
        bottomTriggerHandles = portrait, topTriggerHandles = portrait,
    ))
}
