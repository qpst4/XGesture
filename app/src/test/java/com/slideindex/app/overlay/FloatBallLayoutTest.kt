package com.slideindex.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `FloatBallLayout` 的纯函数：分数钳制。
 */
@RunWith(RobolectricTestRunner::class)
class FloatBallLayoutTest {

    @Test
    fun `visible fraction stays within unit range and is monotonic`() {
        val low = FloatBallLayout.coerceVisibleFraction(-3f)
        val mid = FloatBallLayout.coerceVisibleFraction(0.5f)
        val high = FloatBallLayout.coerceVisibleFraction(9f)

        assertTrue(low in 0f..1f)
        assertTrue(mid in 0f..1f)
        assertTrue(high in 0f..1f)
        assertTrue(low <= mid)
        assertTrue(mid <= high)
    }

    @Test
    fun `position y fraction clamps to stable bounds`() {
        val low = FloatBallLayout.coercePositionYFraction(-5f)
        val high = FloatBallLayout.coercePositionYFraction(5f)

        assertTrue(low >= 0f)
        assertTrue(high <= 1f)
        assertTrue(low <= high)
    }

    @Test
    fun `custom center fraction keeps in-unit value untouched`() {
        assertEquals(0.5f, FloatBallLayout.coerceCustomCenterXFraction(0.5f), 0.0001f)
    }
}
