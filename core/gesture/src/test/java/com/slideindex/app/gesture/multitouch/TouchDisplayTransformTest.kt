package com.slideindex.app.gesture.multitouch

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 面板自然坐标 → 屏幕坐标（对齐 GMD parser 的旋转公式）。
 *
 * 用一块 1080×2340 的竖屏面板，横屏时屏幕是 2340×1080。
 */
class TouchDisplayTransformTest {

    private val panelWidth = 1080f
    private val panelHeight = 2340f

    @Test
    fun `rotation 0 keeps coordinates`() {
        val transform = TouchDisplayTransform(rotation = 0, screenWidthPx = 1080, screenHeightPx = 2340)
        assertPoint(0f, 0f, transform, 0f, 0f)
        assertPoint(1080f, 2340f, transform, 1080f, 2340f)
        assertPoint(540f, 1170f, transform, 540f, 1170f)
    }

    @Test
    fun `rotation 90 maps natural top-left to screen bottom-left`() {
        // 设备逆时针转 90°：自然顶边在屏幕左侧，自然左上角落到屏幕左下角。
        val transform = TouchDisplayTransform(rotation = 1, screenWidthPx = 2340, screenHeightPx = 1080)
        assertPoint(0f, 0f, transform, 0f, 1080f)
        assertPoint(1080f, 0f, transform, 0f, 0f)
        assertPoint(0f, 2340f, transform, 2340f, 1080f)
        assertPoint(1080f, 2340f, transform, 2340f, 0f)
    }

    @Test
    fun `rotation 180 flips both axes`() {
        val transform = TouchDisplayTransform(rotation = 2, screenWidthPx = 1080, screenHeightPx = 2340)
        assertPoint(0f, 0f, transform, 1080f, 2340f)
        assertPoint(1080f, 0f, transform, 0f, 2340f)
        assertPoint(0f, 2340f, transform, 1080f, 0f)
    }

    @Test
    fun `rotation 270 maps natural top-left to screen top-right`() {
        val transform = TouchDisplayTransform(rotation = 3, screenWidthPx = 2340, screenHeightPx = 1080)
        assertPoint(0f, 0f, transform, 2340f, 0f)
        assertPoint(1080f, 0f, transform, 2340f, 1080f)
        assertPoint(0f, 2340f, transform, 0f, 0f)
    }

    @Test
    fun `panel range is rescaled to natural screen size first`() {
        // 面板量程是屏幕的两倍：先缩放到自然屏幕尺寸，再做 90° 旋转。
        val transform = TouchDisplayTransform(rotation = 1, screenWidthPx = 2340, screenHeightPx = 1080)
        val (x, y) = rotatePanelPointToScreen(
            x = 2160f,
            y = 0f,
            panelWidth = 2160f,
            panelHeight = 4680f,
            transform = transform,
        )
        assertEquals(0f, x, 0.01f)
        assertEquals(0f, y, 0.01f)
    }

    @Test
    fun `invalid transform returns coordinates unchanged`() {
        val transform = TouchDisplayTransform.IDENTITY
        assertPoint(123f, 456f, transform, 123f, 456f)
    }

    private fun assertPoint(
        x: Float,
        y: Float,
        transform: TouchDisplayTransform,
        expectedX: Float,
        expectedY: Float,
    ) {
        val (actualX, actualY) = rotatePanelPointToScreen(
            x = x,
            y = y,
            panelWidth = panelWidth,
            panelHeight = panelHeight,
            transform = transform,
        )
        assertEquals("x", expectedX, actualX, 0.01f)
        assertEquals("y", expectedY, actualY, 0.01f)
    }
}
