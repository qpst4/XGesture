package com.slideindex.app.overlay.floatnav

import com.slideindex.app.overlay.PanelSide
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatNavGeometryTest {

    @Test
    fun leftEdge_offsetsFromEdgeAndCentersOnAnchorY() {
        val position = FloatNavGeometry.computePosition(
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            barWidthPx = 600,
            barHeightPx = 168,
            side = PanelSide.LEFT,
            anchorX = 500f,
            anchorY = 1200f,
            edgeInsetPx = 80,
            topInsetPx = 72,
            bottomInsetPx = 120,
        )
        assertEquals(80, position.x)
        assertEquals(1116, position.y)
    }

    @Test
    fun rightEdge_offsetsFromRightAndClamps() {
        val position = FloatNavGeometry.computePosition(
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            barWidthPx = 600,
            barHeightPx = 168,
            side = PanelSide.RIGHT,
            anchorX = 500f,
            anchorY = 2390f,
            edgeInsetPx = 80,
            topInsetPx = 72,
            bottomInsetPx = 120,
        )
        assertEquals(400, position.x)
        assertEquals(2112, position.y)
    }

    @Test
    fun bottomEdge_centersOnAnchorXAndSitsAboveInset() {
        val position = FloatNavGeometry.computePosition(
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            barWidthPx = 600,
            barHeightPx = 168,
            side = PanelSide.BOTTOM,
            anchorX = 500f,
            anchorY = 2000f,
            edgeInsetPx = 80,
            topInsetPx = 72,
            bottomInsetPx = 120,
        )
        assertEquals(200, position.x)
        assertEquals(2112, position.y)
    }

    @Test
    fun topEdge_centersOnAnchorXAndSitsBelowInset() {
        val position = FloatNavGeometry.computePosition(
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            barWidthPx = 600,
            barHeightPx = 168,
            side = PanelSide.TOP,
            anchorX = 100f,
            anchorY = 50f,
            edgeInsetPx = 80,
            topInsetPx = 72,
            bottomInsetPx = 120,
        )
        assertEquals(80, position.x)
        assertEquals(72, position.y)
    }

    @Test
    fun hitTest_returnsKeyWithSlopAndNoneOutside() {
        val bounds = listOf(
            FloatNavKeyBounds(FloatNavKey.GRIP, 0f, 0f, 40f, 40f),
            FloatNavKeyBounds(FloatNavKey.BACK, 40f, 0f, 80f, 40f),
            FloatNavKeyBounds(FloatNavKey.CLOSE, 80f, 0f, 120f, 40f),
        )
        assertEquals(FloatNavKey.GRIP, FloatNavGeometry.hitTest(bounds, 39f, 20f))
        assertEquals(FloatNavKey.BACK, FloatNavGeometry.hitTest(bounds, 41f, 20f))
        assertEquals(FloatNavKey.CLOSE, FloatNavGeometry.hitTest(bounds, 122f, 20f, slopPx = 4f))
        assertEquals(FloatNavKey.NONE, FloatNavGeometry.hitTest(bounds, 200f, 20f))
    }
}
