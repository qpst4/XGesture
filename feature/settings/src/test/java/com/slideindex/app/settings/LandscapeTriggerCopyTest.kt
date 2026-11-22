package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerDesignKind
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class LandscapeTriggerCopyTest {
    @Test
    fun copy_preservesCustomPositionAndLengthFractions_onEverySide() {
        val handles = listOf(TriggerHandle("custom", 0.73f, 0.21f))
        val source = AppSettings(edgeTrigger = EdgeTriggerSettings(
            leftTriggerHandles = handles, rightTriggerHandles = handles,
            bottomTriggerHandles = handles, topTriggerHandles = handles,
        ))
        val copied = source.withLandscapeCopiedFromPortrait()
        for (side in PanelSide.entries) {
            assertEquals(handles, copied.storedLandscapeTriggerHandles(side))
        }
    }

    @Test
    fun copy_preservesTopDefaultFullWidthAndDisabledState() {
        val source = AppSettings()
        val copied = source.withLandscapeCopiedFromPortrait()
        assertEquals(source.topTriggerHandles, copied.topTriggerHandlesLandscape)
        assertEquals(1f, copied.topTriggerHandlesLandscape.single().heightFraction)
    }

    @Test
    fun copy_includesAllHandleFields_andKeepsIdentityAndOrder() {
        val custom = TriggerHandle(
            "custom", 0.1f, 0.2f, enabled = false,
            alignOppositeSide = false, alignOppositeDesign = false, alignOppositeGestures = false,
            shortSwipeDistanceDp = 35f, longSwipeDistanceDp = 175f, edgeWidthDp = null,
            design = TriggerHandleDesign(
                kind = TriggerDesignKind.CUSTOM_IMAGE, sizeDp = 8f,
                customImageUri = "content://test/trigger-image",
            ),
        )
        val handles = listOf(custom, custom.copy(id = "second", topFraction = 0.6f, enabled = true, edgeWidthDp = 0f))
        val source = AppSettings(edgeTrigger = EdgeTriggerSettings(leftTriggerHandles = handles))
        val copied = source.withLandscapeCopiedFromPortrait().leftTriggerHandlesLandscape
        assertEquals(handles.map { it.id }, copied.map { it.id })
        assertEquals(custom.copy(topFraction = copied[0].topFraction, heightFraction = copied[0].heightFraction), copied[0])
        assertEquals(handles[1].copy(topFraction = copied[1].topFraction, heightFraction = copied[1].heightFraction), copied[1])
    }

    @Test
    fun copy_clearsEveryLandscapeSide_whenPortraitListsAreEmpty() {
        val old = listOf(TriggerHandle("landscape-only", 0.2f, 0.2f))
        val source = AppSettings(edgeTrigger = EdgeTriggerSettings(
            leftTriggerHandles = emptyList(), rightTriggerHandles = emptyList(),
            bottomTriggerHandles = emptyList(), topTriggerHandles = emptyList(),
            leftTriggerHandlesLandscape = old, rightTriggerHandlesLandscape = old,
            bottomTriggerHandlesLandscape = old, topTriggerHandlesLandscape = old,
            landscapeTriggersInitialized = true,
        ))
        val copied = source.withLandscapeCopiedFromPortrait()
        assertTrue(copied.landscapeTriggersInitialized)
        for (side in PanelSide.entries) {
            assertTrue(copied.storedLandscapeTriggerHandles(side).isEmpty())
        }
    }

    @Test
    fun copy_replacesRulesCompletely_includingNoneDisabledPriorityAndTriggerMode() {
        val rules = listOf(
            GestureRule.slot(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN,
                GestureAction.None, GestureTriggerMode.IMMEDIATE).copy(priority = 7),
            GestureRule.slot(PanelSide.RIGHT, GestureTriggerType.SHORT_SWIPE_IN,
                GestureAction.Screenshot, GestureTriggerMode.ON_RELEASE).copy(enabled = false),
        )
        val source = AppSettings(
            launcher = LauncherSettings(gestureRules = rules),
            edgeTrigger = EdgeTriggerSettings(gestureRulesLandscape = listOf(GestureRule.slot(
                PanelSide.BOTTOM, GestureTriggerType.SHORT_SWIPE_IN, GestureAction.Home,
                handleId = "landscape-only",
            ))),
        )
        assertEquals(rules, source.withLandscapeCopiedFromPortrait().gestureRulesLandscape)
        assertEquals(source.gestureRules, source.withLandscapeCopiedFromPortrait().gestureRules)
    }

    @Test
    fun copy_copiesEachSideDefaultTriggerMode() {
        val source = AppSettings(edgeTrigger = EdgeTriggerSettings(
            leftDefaultTriggerMode = GestureTriggerMode.IMMEDIATE,
            rightDefaultTriggerMode = GestureTriggerMode.CONTINUOUS,
            bottomDefaultTriggerMode = GestureTriggerMode.ON_RELEASE,
            topDefaultTriggerMode = GestureTriggerMode.IMMEDIATE,
        ))
        val copied = source.withLandscapeCopiedFromPortrait()
        for (side in PanelSide.entries) {
            assertEquals(source.defaultTriggerModeFor(side), copied.runtimeDefaultTriggerMode(side, isLandscape = true))
        }
    }

    @Test
    fun copy_preservesOtherPagesAndPortraitConfiguration_andIsIdempotent() {
        val source = AppSettings(edgeTrigger = EdgeTriggerSettings(
            keyboardTriggerBehaviorPortrait = KeyboardTriggerBehavior.MOVE_UP,
            keyboardTriggerBehaviorLandscape = KeyboardTriggerBehavior.DISABLE,
            keyboardTriggerNarrowPercentPortrait = 21, keyboardTriggerNarrowPercentLandscape = 73,
        ))
        val copied = source.withLandscapeCopiedFromPortrait()
        assertEquals(source, copied.copy(edgeTrigger = source.edgeTrigger))
        assertEquals(source.leftTriggerHandles, copied.leftTriggerHandles)
        assertEquals(source.keyboardTriggerBehaviorLandscape, copied.keyboardTriggerBehaviorLandscape)
        assertEquals(source.keyboardTriggerNarrowPercentLandscape, copied.keyboardTriggerNarrowPercentLandscape)
        assertEquals(copied, copied.withLandscapeCopiedFromPortrait())
    }
}
