package com.slideindex.app.gesture

import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.EdgeTriggerSettings
import com.slideindex.app.settings.FreeWindowMode
import com.slideindex.app.settings.FreeWindowSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class GestureZoneLayoutTouchBoundsTest {
    @Test
    fun touchCaptureBounds_useTriggerWidthNotHalo() {
        val handle = TriggerHandle.default().copy(
            edgeWidthDp = 0f,
            design = TriggerHandleDesign(
                kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE,
                sizeDp = 1f,
                haloSizeDp = 10f,
            ),
        )
        val settings = testSettings(handle)
        val density = 3f
        val touch = GestureZoneLayout.computeTouchCaptureWindowBounds(
            settings = settings,
            side = PanelSide.LEFT,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = density,
        )
        val visual = GestureZoneLayout.computeTriggerVisualWindowBounds(
            settings = settings,
            side = PanelSide.LEFT,
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = density,
        )
        assertEquals(0, touch.single().widthPx)
        assertTrue(visual.single().widthPx > touch.single().widthPx)
    }

    @Test
    fun upperPassthroughKeepsItsFullHeightBesideAnOrdinaryHandle() {
        val reserved = TriggerHandle.default(0f, 0.1f).copy(id = "reserved", passthrough = true)
        val ordinary = TriggerHandle.default(0.1f, 0.24f)
        val touch = touchBounds(listOf(reserved, ordinary))

        assertEquals(271, touch.yPx)
        assertEquals(922, touch.yPx + touch.heightPx)
    }

    @Test
    fun adjacentOrdinaryHandlesMeetWithoutOverlappingOnEitherSide() {
        val upper = TriggerHandle.default(0.1f, 0.24f)
        val lower = TriggerHandle("lower", 0.34f, 0.32f)

        for (side in listOf(PanelSide.LEFT, PanelSide.RIGHT)) {
            val (upperBounds, lowerBounds) = captureBounds(listOf(upper, lower), side)
            assertEquals(271, upperBounds.yPx)
            assertEquals(922, upperBounds.yPx + upperBounds.heightPx)
            assertEquals(922, lowerBounds.yPx)
            assertEquals(1790, lowerBounds.yPx + lowerBounds.heightPx)
        }
    }

    @Test
    fun explicitlyOverlappingOrdinaryHandleKeepsItsConfiguredRange() {
        val reserved = TriggerHandle.default(0f, 0.15f).copy(id = "reserved", passthrough = true)
        val ordinary = TriggerHandle.default(0.1f, 0.24f)
        val touch = touchBounds(listOf(reserved, ordinary))

        assertEquals(271, touch.yPx)
        assertEquals(922, touch.yPx + touch.heightPx)
    }

    @Test
    fun topAndBottomKeepTheConfiguredGapBetweenHandles() {
        val first = TriggerHandle("first", 0.2f, 0.2f, edgeWidthDp = 10f)
        val second = TriggerHandle("second", 0.41f, 0.2f, edgeWidthDp = 10f)

        for (side in listOf(PanelSide.TOP, PanelSide.BOTTOM)) {
            val (left, right) = captureBounds(listOf(first, second), side)
            assertEquals(244, left.xPx)
            assertEquals(488, left.xPx + left.widthPx)
            assertEquals(500, right.xPx)
            assertEquals(if (side == PanelSide.TOP) 0 else 2682, left.yPx)
        }
    }

    @Test
    fun zeroLengthHandleHasNoTouchableSpan() {
        val touch = touchBounds(listOf(TriggerHandle.default(0.25f, 0f)))

        assertEquals(0, touch.heightPx)
    }

    private fun touchBounds(handles: List<TriggerHandle>, side: PanelSide = PanelSide.LEFT): CollapsedWindowBounds =
        captureBounds(handles, side).single()

    private fun captureBounds(handles: List<TriggerHandle>, side: PanelSide): List<CollapsedWindowBounds> =
        GestureZoneLayout.computeTouchCaptureWindowBounds(
            settings = testSettings(*handles.toTypedArray()),
            side = side,
            screenWidthPx = 1220,
            screenHeightPx = 2712,
            density = 3f,
        )

    private fun testSettings(vararg handles: TriggerHandle): AppSettings =
        AppSettings(
            freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
            edgeTrigger = EdgeTriggerSettings(
                leftTriggerHandles = handles.toList(),
                rightTriggerHandles = handles.toList(),
                topTriggerHandles = handles.toList(),
                bottomTriggerHandles = handles.toList(),
            ),
        )
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class GestureZoneLayoutHitTest {
    @Test
    fun localHitTestingUsesTheConfiguredSpanEvenForSmallHandles() {
        val handle = TriggerHandle.default(0.25f, 0.01f)
        val settings = AppSettings(edgeTrigger = EdgeTriggerSettings(leftTriggerHandles = listOf(handle)))
        val layout = GestureZoneLayout(PanelSide.LEFT, settings, viewWidth = 60, viewHeight = 500, density = 3f)

        assertNull(layout.findTriggerHandleAt(1f, 124f))
        assertEquals(handle.id, layout.findTriggerHandleAt(1f, 125f))
        assertNull(layout.findTriggerHandleAt(1f, 131f))
    }
}
