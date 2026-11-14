package com.slideindex.app.overlay.screenshot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeSelectionGeometryTest {

    private val bounds = Rect(0f, 0f, 1080f, 2400f)

    @Test
    fun rectFromDragForward() {
        assertEquals(
            Rect(100f, 200f, 500f, 700f),
            FreeSelectionGeometry.rectFromDrag(
                Offset(100f, 200f), Offset(500f, 700f), bounds
            )
        )
    }

    @Test
    fun rectFromDragBackwardIsNormalized() {
        val rect = FreeSelectionGeometry.rectFromDrag(
            Offset(900f, 1800f), Offset(200f, 300f), bounds
        )
        assertEquals(Rect(200f, 300f, 900f, 1800f), rect)
        assertTrue(rect.width > 0f && rect.height > 0f)
    }

    @Test
    fun rectFromDragClampsToImageBounds() {
        assertEquals(
            Rect(0f, 0f, 1080f, 2400f),
            FreeSelectionGeometry.rectFromDrag(
                Offset(-120f, -300f), Offset(4000f, 5000f), bounds
            )
        )
    }

    @Test
    fun isUsableRejectsTapAndThinStrips() {
        assertFalse(FreeSelectionGeometry.isUsable(Rect.Zero, 48f))
        assertFalse(FreeSelectionGeometry.isUsable(Rect(0f, 0f, 300f, 10f), 48f))
        assertFalse(FreeSelectionGeometry.isUsable(Rect(0f, 0f, 10f, 300f), 48f))
        assertTrue(FreeSelectionGeometry.isUsable(Rect(0f, 0f, 48f, 48f), 48f))
        assertTrue(FreeSelectionGeometry.isUsable(Rect(10f, 10f, 400f, 300f), 48f))
    }

    @Test
    fun isUsableFallsBackToOnePixelWhenThresholdIsZero() {
        assertTrue(FreeSelectionGeometry.isUsable(Rect(0f, 0f, 100f, 100f), 0f))
        assertFalse(FreeSelectionGeometry.isUsable(Rect.Zero, 0f))
    }
}
