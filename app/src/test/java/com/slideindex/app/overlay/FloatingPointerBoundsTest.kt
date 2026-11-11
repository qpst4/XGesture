package com.slideindex.app.overlay

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class FloatingPointerBoundsTest {
    @Test
    fun travelFractionFromSpeed_higherSpeedMeansShorterTravel() {
        assertEquals(0.2f, FloatingPointerBounds.travelFractionFromSpeed(0.75f), 0.001f)
        assertEquals(0.75f, FloatingPointerBounds.travelFractionFromSpeed(0.2f), 0.001f)
    }

    @Test
    fun travelFractionFromSpeed_hundredPercentIsTenTimes() {
        // 100% → 手指行程 10% 屏宽（10 倍）；75% 仍是 20%（5 倍），旧档位手感不变。
        assertEquals(0.1f, FloatingPointerBounds.travelFractionFromSpeed(1f), 0.001f)
        assertEquals(0.2f, FloatingPointerBounds.travelFractionFromSpeed(0.75f), 0.001f)
    }

    @Test
    fun travelFractionFromSpeed_zeroPercentIsSlowest() {
        // 取词「加号」速度滑杆下限已放开到 0%，对应约 95% 屏幕的指针行程。
        val slowest =
            FloatingPointerBounds.travelFractionFromSpeed(FloatingPointerBounds.PICK_POINTER_SPEED_MIN)
        assertEquals(0.95f, slowest, 0.001f)
        assertTrue(
            slowest >
                FloatingPointerBounds.travelFractionFromSpeed(FloatingPointerBounds.SENSITIVITY_MIN)
        )
    }

    @Test
    fun effectivePointerTravel_75PercentIsFasterThan20Percent() {
        val screenW = 1080f
        val screenH = 2400f
        val fast = FloatingPointerBounds.effectivePointerTravel(
            AppSettings(floatingPointer = FloatingPointerSettings(floatingPointerSensitivityFraction = 0.75f)),
            screenW,
            screenH,
        )
        val slow = FloatingPointerBounds.effectivePointerTravel(
            AppSettings(floatingPointer = FloatingPointerSettings(floatingPointerSensitivityFraction = 0.2f)),
            screenW,
            screenH,
        )
        assertTrue(fast.first < slow.first)
        assertTrue(fast.second < slow.second)
    }
}
