package com.slideindex.app.physicalkey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PhysicalKeyGestureRecognizerTest {

    private lateinit var recognizer: PhysicalKeyGestureRecognizer

    @Before
    fun setUp() {
        recognizer = PhysicalKeyGestureRecognizer(
            longPressTimeoutMs = 500L,
            doubleClickTimeoutMs = 350L,
            chordWindowMs = 150L,
            doublePressKeyCodes = setOf(
                PhysicalKeyGestureRecognizer.KEY_POWER,
                PhysicalKeyGestureRecognizer.KEY_VOLUMEUP,
                PhysicalKeyGestureRecognizer.KEY_VOLUMEDOWN,
            ),
        )
    }

    @Test
    fun testLongPressPower() {
        // T=0: 按下电源键
        val r0 = recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_POWER, isDown = true, nowMs = 0L)
        assertTrue(r0.isEmpty())

        // T=400: 心跳未达 500ms
        val r400 = recognizer.onTick(400L)
        assertTrue(r400.isEmpty())

        // T=500: 心跳达到 500ms，触发长按
        val r500 = recognizer.onTick(500L)
        assertEquals(listOf(PhysicalKeyGesture.POWER_LONG_PRESS), r500)

        // T=600: 继续按住不重复触发
        val r600 = recognizer.onTick(600L)
        assertTrue(r600.isEmpty())

        // T=800: 松开，已被长按消费，不触发任何按键
        val r800 = recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_POWER, isDown = false, nowMs = 800L)
        assertTrue(r800.isEmpty())
    }

    @Test
    fun testLongPressVolumeDown() {
        recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_VOLUMEDOWN, isDown = true, nowMs = 1000L)
        val gestures = recognizer.onTick(1500L)
        assertEquals(listOf(PhysicalKeyGesture.VOLUME_DOWN_LONG_PRESS), gestures)

        val up = recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_VOLUMEDOWN, isDown = false, nowMs = 1600L)
        assertTrue(up.isEmpty())
    }

    @Test
    fun testDoubleClickPower() {
        // 第一次按击
        recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_POWER, isDown = true, nowMs = 100L)
        val up1 = recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_POWER, isDown = false, nowMs = 180L)
        assertTrue(up1.isEmpty())

        // 200ms 后第二次按下并松开 (时间差 200ms <= 350ms)
        recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_POWER, isDown = true, nowMs = 300L)
        val up2 = recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_POWER, isDown = false, nowMs = 380L)
        assertEquals(listOf(PhysicalKeyGesture.POWER_DOUBLE_PRESS), up2)

        // 第三次按击不应与之前的结合
        recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_POWER, isDown = true, nowMs = 500L)
        val up3 = recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_POWER, isDown = false, nowMs = 580L)
        assertTrue(up3.isEmpty())
    }

    @Test
    fun testClickTooSlowNotDoublePress() {
        recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_VOLUMEUP, isDown = true, nowMs = 100L)
        recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_VOLUMEUP, isDown = false, nowMs = 180L)

        // 间隔 400ms > 350ms
        recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_VOLUMEUP, isDown = true, nowMs = 600L)
        val up = recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_VOLUMEUP, isDown = false, nowMs = 680L)
        assertTrue(up.isEmpty())
    }

    @Test
    fun testVolumeBothPressChord() {
        // T=1000: 音量减按下
        val r1 = recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_VOLUMEDOWN, isDown = true, nowMs = 1000L)
        assertTrue(r1.isEmpty())

        // T=1050: 音量加紧接着按下（时间差 50ms <= 150ms 窗口）
        val r2 = recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_VOLUMEUP, isDown = true, nowMs = 1050L)
        assertEquals(listOf(PhysicalKeyGesture.VOLUME_BOTH_PRESS), r2)

        // 持续按住，不应再判定为长按
        val tick = recognizer.onTick(1600L)
        assertTrue(tick.isEmpty())

        // 相继松开，不应再触发单击/双击
        val up1 = recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_VOLUMEDOWN, isDown = false, nowMs = 1700L)
        val up2 = recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_VOLUMEUP, isDown = false, nowMs = 1750L)
        assertTrue(up1.isEmpty())
        assertTrue(up2.isEmpty())
    }

    @Test
    fun testReleaseBeforeLongPressDoesNotTriggerLongPress() {
        recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_POWER, isDown = true, nowMs = 100L)
        recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_POWER, isDown = false, nowMs = 300L)

        val tick = recognizer.onTick(700L)
        assertTrue(tick.isEmpty())
    }

    @Test
    fun testSinglePressImmediateWhenNoDoubleClick() {
        recognizer.doublePressKeyCodes = emptySet()
        recognizer.singlePressKeyCodes = setOf(PhysicalKeyGestureRecognizer.KEY_VOLUMEUP)

        recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_VOLUMEUP, isDown = true, nowMs = 100L)
        val up = recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_VOLUMEUP, isDown = false, nowMs = 180L)
        assertEquals(listOf(PhysicalKeyGesture.VOLUME_UP_SINGLE_PRESS), up)
    }

    @Test
    fun testSinglePressAfterDoubleClickTimeout() {
        recognizer.doublePressKeyCodes = setOf(PhysicalKeyGestureRecognizer.KEY_POWER)
        recognizer.singlePressKeyCodes = setOf(PhysicalKeyGestureRecognizer.KEY_POWER)

        recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_POWER, isDown = true, nowMs = 100L)
        val up1 = recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_POWER, isDown = false, nowMs = 180L)
        assertTrue(up1.isEmpty())

        // 350ms 内无第二次按键
        val tickEarly = recognizer.onTick(300L)
        assertTrue(tickEarly.isEmpty())

        // 超过 350ms (180 + 351 = 531)
        val tickTimeout = recognizer.onTick(550L)
        assertEquals(listOf(PhysicalKeyGesture.POWER_SINGLE_PRESS), tickTimeout)
    }

    @Test
    fun testDoubleClickSuppressesSinglePress() {
        recognizer.doublePressKeyCodes = setOf(PhysicalKeyGestureRecognizer.KEY_POWER)
        recognizer.singlePressKeyCodes = setOf(PhysicalKeyGestureRecognizer.KEY_POWER)

        recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_POWER, isDown = true, nowMs = 100L)
        val up1 = recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_POWER, isDown = false, nowMs = 180L)
        assertTrue(up1.isEmpty())

        recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_POWER, isDown = true, nowMs = 300L)
        val up2 = recognizer.onKeyEvent(PhysicalKeyGestureRecognizer.KEY_POWER, isDown = false, nowMs = 380L)
        assertEquals(listOf(PhysicalKeyGesture.POWER_DOUBLE_PRESS), up2)

        val tick = recognizer.onTick(800L)
        assertTrue(tick.isEmpty())
    }
}
