package com.slideindex.app.shake

import org.junit.Assert.assertEquals
import org.junit.Test

class ShakeGestureDetectorTest {

    @Test
    fun effectiveThreshold_higherValueMeansLowerThreshold() {
        assertEquals(16.2f, ShakeGestureDetector.effectiveThreshold(1f), 0.001f)
        assertEquals(6.484f, ShakeGestureDetector.effectiveThreshold(14f), 0.01f)
        assertEquals(9.474f, ShakeGestureDetector.effectiveThreshold(10f), 0.01f)
        assertEquals(2f, ShakeGestureDetector.effectiveThreshold(20f), 0.001f)
    }

    @Test
    fun clampSensitivity_limitsToOneThroughTwenty() {
        assertEquals(1f, ShakeGestureClassifier.clampSensitivity(0.2f))
        assertEquals(20f, ShakeGestureClassifier.clampSensitivity(25f))
    }
}
