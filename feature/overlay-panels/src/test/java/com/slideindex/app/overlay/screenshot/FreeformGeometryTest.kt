package com.slideindex.app.overlay.screenshot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeformGeometryTest {

    private fun points(vararg coords: Float): List<Offset> =
        coords.toList().chunked(2).map { Offset(it[0], it[1]) }

    @Test
    fun boundsOfReturnsTightBounds() {
        val bounds = FreeformGeometry.boundsOf(
            points(120f, 240f, 40f, 300f, 200f, 100f)
        )
        assertEquals(Rect(40f, 100f, 200f, 300f), bounds)
    }

    @Test
    fun boundsOfEmptyIsZero() {
        assertEquals(Rect.Zero, FreeformGeometry.boundsOf(emptyList()))
    }

    @Test
    fun pointInsideSquare() {
        val square = points(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f)
        assertTrue(FreeformGeometry.isPointInside(square, Offset(50f, 50f)))
        assertFalse(FreeformGeometry.isPointInside(square, Offset(150f, 50f)))
        assertFalse(FreeformGeometry.isPointInside(square, Offset(-1f, 50f)))
    }

    @Test
    fun pointInsideConcaveShape() {
        // 一个"U"形：上方中间是缺口，落在缺口里的点不算命中。
        val uShape = points(
            0f, 0f,
            30f, 0f,
            30f, 70f,
            70f, 70f,
            70f, 0f,
            100f, 0f,
            100f, 100f,
            0f, 100f
        )
        assertTrue(FreeformGeometry.isPointInside(uShape, Offset(15f, 50f)))
        assertTrue(FreeformGeometry.isPointInside(uShape, Offset(50f, 85f)))
        assertFalse(FreeformGeometry.isPointInside(uShape, Offset(50f, 30f)))
    }

    @Test
    fun degeneratePolygonNeverHits() {
        assertFalse(FreeformGeometry.isPointInside(emptyList(), Offset(1f, 1f)))
        assertFalse(FreeformGeometry.isPointInside(points(0f, 0f, 10f, 10f), Offset(5f, 5f)))
    }
}
