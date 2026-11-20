package com.slideindex.app.multitouch

import com.slideindex.app.gesture.multitouch.MultiTouchTriggerArea
import com.slideindex.app.gesture.multitouch.TouchAnchor
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

    @Test
    fun `anywhere accepts every start point`() {
        assertTrue(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_ANYWHERE, 0f, 0f, width, height,
            ),
        )
        assertTrue(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_ANYWHERE, 1f, 1f, width, height,
            ),
        )
    }

    @Test
    fun `top half uses the start height`() {
        assertTrue(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_TOP, 0.5f, 0.4f, width, height,
            ),
        )
        assertTrue(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_TOP, 0.5f, 0.5f, width, height,
            ),
        )
        assertFalse(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_TOP, 0.5f, 0.51f, width, height,
            ),
        )
    }

    @Test
    fun `bottom half uses the start height`() {
        assertFalse(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_BOTTOM, 0.5f, 0.49f, width, height,
            ),
        )
        assertTrue(
            MultiTouchTriggerFilter.areaAllows(
                MultiTouchTriggerFilter.AREA_BOTTOM, 0.5f, 0.51f, width, height,
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

    // ---- 组合判定：绘制手势区域看整条轨迹（GMD 语义），键盘只看起点 ----

    private val keyboardHidden = KeyboardInput(visible = false, hasBounds = false)
    private val keyboardShownAbove = KeyboardInput(visible = true, hasBounds = true)
    private val keyboardShownNoBounds = KeyboardInput(visible = true, hasBounds = false)

    private data class KeyboardInput(val visible: Boolean, val hasBounds: Boolean)

    private fun accepts(
        area: Int,
        behavior: Int = MultiTouchTriggerFilter.KEYBOARD_ABOVE,
        start: TouchAnchor? = null,
        path: List<TouchAnchor>? = null,
        keyboard: KeyboardInput = keyboardHidden,
    ): Boolean = MultiTouchTriggerFilter.accepts(
        area = area,
        keyboardBehavior = behavior,
        start = start,
        path = path,
        widthPx = width,
        heightPx = height,
        keyboardVisible = keyboard.visible,
        keyboardHasBounds = keyboard.hasBounds,
        keyboardBounds = MultiTouchTriggerFilter.KeyboardRect(
            left = 0,
            top = 1400,
            right = 1000,
            bottom = 2000,
        ),
        displayWidth = 1000,
        displayHeight = 2000,
    )

    @Test
    fun `path gesture hits area when any point enters it`() {
        // 起点在下半屏，路径扫进上半屏：GMD 认为命中「上半部分」。
        val path = listOf(
            TouchAnchor(0.5f, 0.8f),
            TouchAnchor(0.5f, 0.6f),
            TouchAnchor(0.5f, 0.4f),
        )
        assertTrue(accepts(MultiTouchTriggerFilter.AREA_TOP, path = path))
    }

    @Test
    fun `path gesture misses area when every point stays below`() {
        val path = listOf(
            TouchAnchor(0.5f, 0.9f),
            TouchAnchor(0.5f, 0.7f),
        )
        assertFalse(accepts(MultiTouchTriggerFilter.AREA_TOP, path = path))
    }

    @Test
    fun `path gesture ignores start for area when path is provided`() {
        // 起点在上半屏，但轨迹全在下半屏：区域按轨迹判 → 不命中。
        val path = listOf(TouchAnchor(0.5f, 0.9f), TouchAnchor(0.5f, 0.8f))
        assertFalse(
            accepts(
                MultiTouchTriggerFilter.AREA_TOP,
                start = TouchAnchor(0.5f, 0.3f),
                path = path,
            ),
        )
    }

    @Test
    fun `keyboard still uses the start point for path gestures`() {
        val path = listOf(TouchAnchor(0.5f, 0.4f))
        // 路径命中「上半部分」，但起点压在键盘里（v=0.8 → y=1600）：不触发。
        assertFalse(
            accepts(
                MultiTouchTriggerFilter.AREA_TOP,
                start = TouchAnchor(0.5f, 0.8f),
                path = path,
                keyboard = keyboardShownAbove,
            ),
        )
        // 起点在键盘外就正常触发。
        assertTrue(
            accepts(
                MultiTouchTriggerFilter.AREA_TOP,
                start = TouchAnchor(0.5f, 0.3f),
                path = path,
                keyboard = keyboardShownAbove,
            ),
        )
    }

    @Test
    fun `blocked keyboard rejects path gestures entirely`() {
        val path = listOf(TouchAnchor(0.5f, 0.4f))
        assertFalse(
            accepts(
                MultiTouchTriggerFilter.AREA_ANYWHERE,
                behavior = MultiTouchTriggerFilter.KEYBOARD_BLOCKED,
                start = TouchAnchor(0.5f, 0.3f),
                path = path,
                keyboard = keyboardShownAbove,
            ),
        )
        // 键盘不可见时照常。
        assertTrue(
            accepts(
                MultiTouchTriggerFilter.AREA_ANYWHERE,
                behavior = MultiTouchTriggerFilter.KEYBOARD_BLOCKED,
                start = TouchAnchor(0.5f, 0.3f),
                path = path,
            ),
        )
    }

    @Test
    fun `path gesture without start still checks area but skips keyboard`() {
        val path = listOf(TouchAnchor(0.5f, 0.4f))
        // 没有起点：区域照判（命中 → 放行），键盘无从判 → 跳过。
        assertTrue(
            accepts(
                MultiTouchTriggerFilter.AREA_TOP,
                start = null,
                path = path,
                keyboard = keyboardShownAbove,
            ),
        )
        // 没有起点且轨迹不在区域里 → 仍然拦。
        assertFalse(
            accepts(
                MultiTouchTriggerFilter.AREA_BOTTOM,
                start = null,
                path = path,
            ),
        )
    }

    @Test
    fun `keyboard with shown but unknown bounds rejects above-keyboard gestures`() {
        assertFalse(
            accepts(
                MultiTouchTriggerFilter.AREA_ANYWHERE,
                start = TouchAnchor(0.5f, 0.5f),
                keyboard = keyboardShownNoBounds,
            ),
        )
    }

    // ---- 每个手势自己的生效区域 ----

    @Test
    fun `any-point area check hits when one sample is inside`() {
        val points = listOf(
            TouchAnchor(0.5f, 0.8f),
            TouchAnchor(0.5f, 0.6f),
            TouchAnchor(0.5f, 0.3f),
        )
        assertTrue(
            MultiTouchTriggerFilter.areaAllowsAny(
                MultiTouchTriggerFilter.AREA_TOP,
                points,
                width,
                height,
            ),
        )
        assertFalse(
            MultiTouchTriggerFilter.areaAllowsAny(
                MultiTouchTriggerFilter.AREA_BOTTOM,
                listOf(TouchAnchor(0.5f, 0.3f)),
                width,
                height,
            ),
        )
    }

    @Test
    fun `trigger area enum maps to filter ids`() {
        assertTrue(
            MultiTouchTriggerFilter.areaId(MultiTouchTriggerArea.ANYWHERE) ==
                MultiTouchTriggerFilter.AREA_ANYWHERE,
        )
        assertTrue(
            MultiTouchTriggerFilter.areaId(MultiTouchTriggerArea.TOP) ==
                MultiTouchTriggerFilter.AREA_TOP,
        )
        assertTrue(
            MultiTouchTriggerFilter.areaId(MultiTouchTriggerArea.BOTTOM) ==
                MultiTouchTriggerFilter.AREA_BOTTOM,
        )
    }

    @Test
    fun `trigger area key round trip falls back to anywhere`() {
        assertTrue(MultiTouchTriggerArea.fromKey("top") == MultiTouchTriggerArea.TOP)
        assertTrue(MultiTouchTriggerArea.fromKey("bottom") == MultiTouchTriggerArea.BOTTOM)
        assertTrue(MultiTouchTriggerArea.fromKey("middle") == MultiTouchTriggerArea.ANYWHERE)
        assertTrue(MultiTouchTriggerArea.fromKey("anywhere") == MultiTouchTriggerArea.ANYWHERE)
        assertTrue(MultiTouchTriggerArea.fromKey("nope") == MultiTouchTriggerArea.ANYWHERE)
        assertTrue(MultiTouchTriggerArea.fromKey(null) == MultiTouchTriggerArea.ANYWHERE)
    }
}
