package com.slideindex.app.multitouch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 触发条件判定（对齐 GMD）：区域只看起点、键盘按 IME 矩形。
 *
 * 纯函数，不依赖 Android，普通 JVM 单测即可。
 */
class MultiTouchTriggerFilterTest {

    private val width = 1000
    private val height = 2000
    private val border = 50

    @Test
    fun `anywhere accepts every start point`() {
        assertTrue(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_ANYWHERE, 0f, 0f, width, height, border,
            ),
        )
        assertTrue(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_ANYWHERE, 1f, 1f, width, height, border,
            ),
        )
    }

    @Test
    fun `top half uses the start height`() {
        assertTrue(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_TOP, 0.5f, 0.4f, width, height, border,
            ),
        )
        assertTrue(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_TOP, 0.5f, 0.5f, width, height, border,
            ),
        )
        assertFalse(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_TOP, 0.5f, 0.51f, width, height, border,
            ),
        )
    }

    @Test
    fun `bottom half uses the start height`() {
        assertFalse(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_BOTTOM, 0.5f, 0.49f, width, height, border,
            ),
        )
        assertTrue(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_BOTTOM, 0.5f, 0.51f, width, height, border,
            ),
        )
    }

    @Test
    fun `middle means away from every edge`() {
        assertTrue(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_MIDDLE, 0.5f, 0.5f, width, height, border,
            ),
        )
        // 贴左边（x < border）
        assertFalse(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_MIDDLE, 0.04f, 0.5f, width, height, border,
            ),
        )
        // 贴下边（y > height - border）
        assertFalse(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_MIDDLE, 0.5f, 0.98f, width, height, border,
            ),
        )
        // 边缘带内边界：x = width - border 不算「中」
        assertFalse(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_MIDDLE, 0.95f, 0.5f, width, height, border,
            ),
        )
    }

    @Test
    fun `middle does not block when screen size is unknown`() {
        assertTrue(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_MIDDLE, 0f, 0f, 0, 0, border,
            ),
        )
    }

    @Test
    fun `keyboard blocked rejects only while visible`() {
        assertTrue(
            MultiTouchTriggerFilter.keyboardAllows(
                behavior = MultiTouchTriggerFilter.KEYBOARD_BLOCKED,
                startU = 0.5f,
                startV = 0.9f,
                visible = false,
                hasBounds = false,
                left = 0,
                top = 1400,
                right = 1000,
                bottom = 2000,
                displayWidth = 1000,
                displayHeight = 2000,
            ),
        )
        assertFalse(
            MultiTouchTriggerFilter.keyboardAllows(
                behavior = MultiTouchTriggerFilter.KEYBOARD_BLOCKED,
                startU = 0.5f,
                startV = 0.9f,
                visible = true,
                hasBounds = true,
                left = 0,
                top = 1400,
                right = 1000,
                bottom = 2000,
                displayWidth = 1000,
                displayHeight = 2000,
            ),
        )
    }

    @Test
    fun `above keyboard rejects starts inside the ime rect`() {
        fun allows(v: Float, hasBounds: Boolean = true): Boolean =
            MultiTouchTriggerFilter.keyboardAllows(
                behavior = MultiTouchTriggerFilter.KEYBOARD_ABOVE,
                startU = 0.5f,
                startV = v,
                visible = true,
                hasBounds = hasBounds,
                left = 0,
                top = 1400,
                right = 1000,
                bottom = 2000,
                displayWidth = 1000,
                displayHeight = 2000,
            )

        assertTrue(allows(0.5f))
        assertTrue(allows(0.69f))
        assertFalse(allows(0.71f))
        // 键盘可见但拿不到矩形：按 GMD 语义不触发
        assertFalse(allows(0.5f, hasBounds = false))
    }

    @Test
    fun `everywhere ignores the keyboard`() {
        assertTrue(
            MultiTouchTriggerFilter.keyboardAllows(
                behavior = MultiTouchTriggerFilter.KEYBOARD_EVERYWHERE,
                startU = 0.5f,
                startV = 0.9f,
                visible = true,
                hasBounds = true,
                left = 0,
                top = 1400,
                right = 1000,
                bottom = 2000,
                displayWidth = 1000,
                displayHeight = 2000,
            ),
        )
    }

    @Test
    fun `string ids round trip`() {
        assertTrue(MultiTouchTriggerFilter.areaId("middle") == MultiTouchTriggerFilter.AREA_MIDDLE)
        assertTrue(MultiTouchTriggerFilter.areaId("top") == MultiTouchTriggerFilter.AREA_TOP)
        assertTrue(MultiTouchTriggerFilter.areaId("bottom") == MultiTouchTriggerFilter.AREA_BOTTOM)
        assertTrue(
            MultiTouchTriggerFilter.areaId("unknown") == MultiTouchTriggerFilter.AREA_ANYWHERE,
        )
        assertTrue(
            MultiTouchTriggerFilter.keyboardBehaviorId("blocked") ==
                MultiTouchTriggerFilter.KEYBOARD_BLOCKED,
        )
        assertTrue(
            MultiTouchTriggerFilter.keyboardBehaviorId("everywhere") ==
                MultiTouchTriggerFilter.KEYBOARD_EVERYWHERE,
        )
        assertTrue(
            MultiTouchTriggerFilter.keyboardBehaviorId(null) ==
                MultiTouchTriggerFilter.KEYBOARD_ABOVE,
        )
    }
}
