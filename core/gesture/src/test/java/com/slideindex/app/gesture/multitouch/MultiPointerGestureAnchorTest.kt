package com.slideindex.app.gesture.multitouch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 多指手势的**锚点**（手指摆好时的质心）。
 *
 * 外部浮层（快速启动器这类只能贴边画的面板）靠它决定从哪一侧、哪个高度出来：
 * 取「基准质心」而不是「触发那一帧的质心」，否则滑动/张开过程中质心漂过半屏，面板会跟着翻边。
 */
class MultiPointerGestureAnchorTest {

    private val recognizer = MultiPointerGestureRecognizer()

    /** 三指水平排开，质心 = (cx, cy)；半径 0.1（按屏高归一化）。 */
    private fun threeFingers(cx: Float, cy: Float): List<TouchPointer> = listOf(
        TouchPointer(id = 0, x = cx - 0.10f, y = cy),
        TouchPointer(id = 1, x = cx, y = cy),
        TouchPointer(id = 2, x = cx + 0.10f, y = cy),
    )

    @Test
    fun `touch anchor is the centroid of the first frame`() {
        val results = recognizer.onFrame(threeFingers(0.30f, 0.60f), 1_000L)
        val touch = results.firstOrNull { it.gesture == MultiPointerGesture.TOUCH }
        assertNotNull(touch)
        assertEquals(0.30f, touch!!.anchorX, 1e-4f)
        assertEquals(0.60f, touch.anchorY, 1e-4f)
    }

    @Test
    fun `swipe anchor stays where the hand was, not where it ended`() {
        recognizer.onFrame(threeFingers(0.20f, 0.50f), 1_000L)

        var time = 1_000L
        var swipe: RecognizedMultiPointerGesture? = null
        for (step in 1..20) {
            time += 16L
            // 质心一路右移 0.02/帧；触发时质心已经离起点很远。
            val results = recognizer.onFrame(threeFingers(0.20f + step * 0.02f, 0.50f), time)
            swipe = results.firstOrNull { it.gesture == MultiPointerGesture.SWIPE_RIGHT }
            if (swipe != null) break
        }
        assertNotNull(swipe)
        // 锚点仍是按下时的 0.20，而不是触发那一帧的 0.20 + 步数 * 0.02。
        assertEquals(0.20f, swipe!!.anchorX, 1e-4f)
        assertEquals(0.50f, swipe.anchorY, 1e-4f)
    }

    @Test
    fun `anchor follows the full hand when a finger joins later`() {
        val two = listOf(
            TouchPointer(id = 0, x = 0.10f, y = 0.50f),
            TouchPointer(id = 1, x = 0.20f, y = 0.50f),
        )
        recognizer.onFrame(two, 1_000L)

        // 第三根手指加入：基准重建，质心变成三指的 0.30。
        val three = listOf(
            TouchPointer(id = 0, x = 0.10f, y = 0.50f),
            TouchPointer(id = 1, x = 0.20f, y = 0.50f),
            TouchPointer(id = 2, x = 0.60f, y = 0.50f),
        )
        recognizer.onFrame(three, 1_050L)

        // 三指绕质心捏合（质心恒为 0.30）：结果里的锚点必须是「三指都摆好」的位置。
        var time = 1_050L
        var radius = 0.20f
        var pinch: RecognizedMultiPointerGesture? = null
        repeat(10) {
            time += 20L
            radius -= 0.01f
            val pointers = listOf(
                TouchPointer(id = 0, x = 0.30f - radius, y = 0.50f),
                TouchPointer(id = 1, x = 0.30f, y = 0.50f),
                TouchPointer(id = 2, x = 0.30f + radius, y = 0.50f),
            )
            if (pinch == null) {
                pinch = recognizer.onFrame(pointers, time)
                    .firstOrNull { it.gesture == MultiPointerGesture.PINCH }
            }
        }
        assertNotNull(pinch)
        assertEquals(0.30f, pinch!!.anchorX, 1e-4f)
        assertEquals(0.50f, pinch.anchorY, 1e-4f)
    }

    @Test
    fun `tap anchor is where the fingers were`() {
        val down = listOf(
            TouchPointer(id = 0, x = 0.70f, y = 0.30f),
            TouchPointer(id = 1, x = 0.80f, y = 0.30f),
        )
        recognizer.onFrame(down, 1_000L)
        val lift = recognizer.onFrame(emptyList(), 1_100L)
        val tap = lift.firstOrNull { it.gesture == MultiPointerGesture.TAP }
        assertNotNull(tap)
        assertEquals(0.75f, tap!!.anchorX, 1e-4f)
        assertEquals(0.30f, tap.anchorY, 1e-4f)
    }
}
