package com.slideindex.app.shake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShakeGestureClassifierTest {

    @Test
    fun clampSensitivity_limitsToOneThroughTwenty() {
        assertEquals(1f, ShakeGestureClassifier.clampSensitivity(0.2f))
        assertEquals(20f, ShakeGestureClassifier.clampSensitivity(99f))
        assertEquals(14f, ShakeGestureClassifier.clampSensitivity(14f))
    }

    @Test
    fun effectiveThreshold_higherValueMeansLowerThreshold() {
        assertEquals(16.2f, ShakeGestureClassifier.effectiveThreshold(1f), 0.001f)
        assertEquals(6.484f, ShakeGestureClassifier.effectiveThreshold(14f), 0.01f)
        assertEquals(9.474f, ShakeGestureClassifier.effectiveThreshold(10f), 0.01f)
        assertEquals(2f, ShakeGestureClassifier.effectiveThreshold(20f), 0.001f)
    }

    @Test
    fun detectDirection_rightFlip_whenAxisYExceedsThreshold() {
        val direction = ShakeGestureClassifier.detectDirection(
            axisX = 0f,
            axisY = 8f,
            axisZ = 0f,
            absX = 0f,
            absY = 8f,
            absZ = 0f,
            globalSensitivity = 17f,
            independentEnabled = false,
            perDirectionSensitivity = emptyMap(),
        )

        assertEquals(ShakeGestureType.RIGHT_FLIP, direction)
    }

    @Test
    fun detectDirection_leftFlip_whenAxisYNegativeExceedsThreshold() {
        val direction = ShakeGestureClassifier.detectDirection(
            axisX = 0f,
            axisY = -8f,
            axisZ = 0f,
            absX = 0f,
            absY = 8f,
            absZ = 0f,
            globalSensitivity = 17f,
            independentEnabled = false,
            perDirectionSensitivity = emptyMap(),
        )

        assertEquals(ShakeGestureType.LEFT_FLIP, direction)
    }

    @Test
    fun detectDirection_returnsNullWhenBelowThreshold() {
        val direction = ShakeGestureClassifier.detectDirection(
            axisX = 0.5f,
            axisY = 0.5f,
            axisZ = 0.5f,
            absX = 0.5f,
            absY = 0.5f,
            absZ = 0.5f,
            globalSensitivity = 17f,
            independentEnabled = false,
            perDirectionSensitivity = emptyMap(),
        )

        assertNull(direction)
    }

    @Test
    fun detectDirection_independentMode_emptyPerDirection_usesEffectiveThreshold() {
        // 重标定后默认档阈值约 4.68，用 3 rad/s 才在「低于阈值」这一侧。
        val direction = ShakeGestureClassifier.detectDirection(
            axisX = 0f,
            axisY = 3f,
            axisZ = 0f,
            absX = 0f,
            absY = 3f,
            absZ = 0f,
            globalSensitivity = ShakeSensitivityScale.DEFAULT_UI,
            independentEnabled = true,
            perDirectionSensitivity = emptyMap(),
        )

        assertNull(direction)
    }

    @Test
    fun detectDirection_independentMode_minimumSensitivityIsHardest() {
        val atMin = ShakeGestureClassifier.detectDirection(
            axisX = 0f,
            axisY = 9f,
            axisZ = 0f,
            absX = 0f,
            absY = 9f,
            absZ = 0f,
            globalSensitivity = 1f,
            independentEnabled = true,
            perDirectionSensitivity = mapOf(ShakeGestureType.RIGHT_FLIP to 1f),
        )

        assertNull(atMin)

        val atMax = ShakeGestureClassifier.detectDirection(
            axisX = 0f,
            axisY = 2.6f,
            axisZ = 0f,
            absX = 0f,
            absY = 2.6f,
            absZ = 0f,
            globalSensitivity = 20f,
            independentEnabled = true,
            perDirectionSensitivity = mapOf(ShakeGestureType.RIGHT_FLIP to 20f),
        )

        assertEquals(ShakeGestureType.RIGHT_FLIP, atMax)
    }
}
