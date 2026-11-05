package com.slideindex.app.gesture.multitouch

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 触摸锚点换算：面板自然坐标 → 屏幕坐标。
 *
 * 旋转约定见 [mapTouchAnchorToScreen]（`Display.getRotation()` 是「画面旋转角」，与设备物理旋转相反）。
 * 这里用竖屏自然尺寸 1080×2340 的手机举例。
 */
class TouchAnchorMappingTest {

    private val portraitWidth = 1080
    private val portraitHeight = 2340
    private val landscapeWidth = 2340
    private val landscapeHeight = 1080

    @Test
    fun `rotation 0 maps straight through`() {
        val point = mapTouchAnchorToScreen(
            TouchAnchor(u = 0.25f, v = 0.75f),
            portraitWidth,
            portraitHeight,
            ROTATION_0,
        )
        assertEquals(270f, point.x, 1e-3f)
        assertEquals(1755f, point.y, 1e-3f)
    }

    @Test
    fun `rotation 90 puts the natural top edge on the left`() {
        // 设备逆时针转 90°：自然左上角跑到屏幕左下角。
        val topLeft = mapTouchAnchorToScreen(
            TouchAnchor(u = 0f, v = 0f),
            landscapeWidth,
            landscapeHeight,
            ROTATION_90,
        )
        assertEquals(0f, topLeft.x, 1e-3f)
        assertEquals(1080f, topLeft.y, 1e-3f)

        val point = mapTouchAnchorToScreen(
            TouchAnchor(u = 0.25f, v = 0.75f),
            landscapeWidth,
            landscapeHeight,
            ROTATION_90,
        )
        assertEquals(1755f, point.x, 1e-3f) // v * 宽
        assertEquals(810f, point.y, 1e-3f) // (1 - u) * 高
    }

    @Test
    fun `rotation 180 flips both axes`() {
        val point = mapTouchAnchorToScreen(
            TouchAnchor(u = 0.25f, v = 0.75f),
            portraitWidth,
            portraitHeight,
            ROTATION_180,
        )
        assertEquals(810f, point.x, 1e-3f)
        assertEquals(585f, point.y, 1e-3f)
    }

    @Test
    fun `rotation 270 puts the natural top edge on the right`() {
        val topLeft = mapTouchAnchorToScreen(
            TouchAnchor(u = 0f, v = 0f),
            landscapeWidth,
            landscapeHeight,
            ROTATION_270,
        )
        assertEquals(2340f, topLeft.x, 1e-3f)
        assertEquals(0f, topLeft.y, 1e-3f)

        val point = mapTouchAnchorToScreen(
            TouchAnchor(u = 0.25f, v = 0.75f),
            landscapeWidth,
            landscapeHeight,
            ROTATION_270,
        )
        assertEquals(585f, point.x, 1e-3f) // (1 - v) * 宽
        assertEquals(270f, point.y, 1e-3f) // u * 高
    }

    @Test
    fun `out of range anchors are clamped inside the screen`() {
        val point = mapTouchAnchorToScreen(
            TouchAnchor(u = 1.4f, v = -0.3f),
            portraitWidth,
            portraitHeight,
            ROTATION_0,
        )
        assertEquals(1080f, point.x, 1e-3f)
        assertEquals(0f, point.y, 1e-3f)
    }

    @Test
    fun `recognizer anchor converts into panel space`() {
        // 识别器把 x 也按屏高归一化：rawX = anchorX * maxY，所以 u = anchorX * maxY / maxX。
        val anchor = touchAnchorOf(
            anchorX = 0.25f,
            anchorY = 0.5f,
            panelWidth = 1080f,
            panelHeight = 2340f,
        )
        assertEquals(0.25f * 2340f / 1080f, anchor.u, 1e-4f)
        assertEquals(0.5f, anchor.v, 1e-4f)
    }

    @Test
    fun `unknown panel size falls back to center`() {
        val anchor = touchAnchorOf(anchorX = 0.3f, anchorY = 0.4f, panelWidth = 0f, panelHeight = 0f)
        assertEquals(TouchAnchor.CENTER, anchor)
    }

    private companion object {
        // 与 android.view.Surface.ROTATION_* 取值一致；这里不引 Android 类。
        const val ROTATION_0 = 0
        const val ROTATION_90 = 1
        const val ROTATION_180 = 2
        const val ROTATION_270 = 3
    }
}
