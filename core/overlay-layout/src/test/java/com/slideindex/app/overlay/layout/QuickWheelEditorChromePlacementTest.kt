package com.slideindex.app.overlay.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编辑层挂件的纵向选位（见 [QuickWheelEditorChromePlacement]）。
 *
 * 背景：药丸组（空白占位 / 删除）与提示条原来固定贴顶贴底，轮盘靠边呼出时会被容器压住。
 * 这里锁定几条关键行为：优先落在横向相交容器之间的空档、没空档时按约定兜底、提示条优先贴顶。
 */
class QuickWheelEditorChromePlacementTest {

    private val safeTop = 100f
    private val safeBottom = 1_900f
    private val center = 540f
    private val gap = 12f
    private val pillsHeight = 40f
    private val hintHeight = 40f

    private fun slot(x: Float, y: Float, size: Float = 100f) = QuickWheelPlacedSlot(
        index = 0,
        centerX = x,
        centerY = y,
        sizePx = size,
        cornerPx = 24f,
        ringIndex = 0,
        indexInRing = 0,
        startAngleDeg = 0f,
        endAngleDeg = 90f,
        minRadiusPx = 0f,
        maxRadiusPx = 0f,
    )

    private fun place(
        slots: List<QuickWheelPlacedSlot>,
        wheelCenterY: Float = 1_000f,
    ) = QuickWheelEditorChromePlacement.place(
        slots = slots,
        horizontalCenterPx = center,
        pillsWidthPx = 200f,
        pillsHeightPx = pillsHeight,
        hintWidthPx = 1_080f,
        hintHeightPx = hintHeight,
        safeTopPx = safeTop,
        safeBottomPx = safeBottom,
        gapPx = gap,
        wheelCenterY = wheelCenterY,
    )

    @Test
    fun `pills sit in the free gap between containers that cross the center band`() {
        // 半圆贴边时上下两段容器都会跨过中线：中间那段才是离轮盘最近的位置。
        val result = place(
            slots = listOf(slot(x = 500f, y = 200f), slot(x = 500f, y = 600f)),
            wheelCenterY = 400f,
        )
        assertEquals(380f, result.pillsTop, 0.5f)
    }

    @Test
    fun `pills stay close to the wheel instead of the screen bottom`() {
        // 容器集中在上半屏：药丸应落在容器下方、紧贴容器，而不是屏幕底部。
        val result = place(
            slots = listOf(slot(x = 540f, y = 300f), slot(x = 540f, y = 420f)),
            wheelCenterY = 360f,
        )
        assertEquals(420f + 50f + gap, result.pillsTop, 0.5f)
    }

    @Test
    fun `containers outside the pill band are ignored`() {
        // 左右两侧的容器与药丸横向范围不相交：药丸可以落在轮盘正中的空心区。
        val result = place(
            slots = listOf(
                slot(x = center - 300f, y = 1_000f),
                slot(x = center + 300f, y = 1_000f),
            ),
            wheelCenterY = 1_000f,
        )
        assertEquals(1_000f - pillsHeight / 2f, result.pillsTop, 0.5f)
    }

    @Test
    fun `no free gap falls back to the bottom`() {
        // 容器铺满整列：没有空档 → 按约定保持现状叠在底部。
        val slots = (100..1_900 step 80).map { y -> slot(x = 540f, y = y.toFloat()) }
        val result = place(slots = slots, wheelCenterY = 1_000f)
        assertEquals(safeBottom - pillsHeight, result.pillsTop, 0.5f)
    }

    @Test
    fun `hint prefers the top edge`() {
        val result = place(slots = listOf(slot(x = 540f, y = 1_000f)), wheelCenterY = 1_000f)
        assertEquals(safeTop, result.hintTop, 0.5f)
        assertFalse(result.hintOverlapping)
    }

    @Test
    fun `hint moves below the containers when the top is occupied`() {
        val result = place(
            slots = listOf(slot(x = 540f, y = 200f), slot(x = 540f, y = 1_200f)),
            wheelCenterY = 700f,
        )
        // 顶部被占用 → 落到第一个空档（两块容器之间）。
        assertEquals(250f + gap, result.hintTop, 0.5f)
        assertFalse(result.hintOverlapping)
    }

    @Test
    fun `hint is marked overlapping when there is no free gap`() {
        val slots = (100..1_900 step 80).map { y -> slot(x = 540f, y = y.toFloat()) }
        val result = place(slots = slots, wheelCenterY = 1_000f)
        assertTrue(result.hintOverlapping)
        assertEquals(safeTop, result.hintTop, 0.5f)
    }

    @Test
    fun `hint never overlaps the pills`() {
        val result = place(
            slots = listOf(slot(x = 540f, y = 500f)),
            wheelCenterY = 500f,
        )
        // 只有一块容器：药丸去下沿、提示条去顶部，两者不重叠。
        assertTrue(result.hintTop + hintHeight <= result.pillsTop - gap + 0.5f)
    }

    @Test
    fun `placement only depends on the slots it is given`() {
        // 编辑层固定用「一级容器」调用本函数（展开二级时不重算），这里锁定"只按传入容器算"。
        val result = place(
            slots = listOf(slot(x = 540f, y = 1_200f, size = 120f)),
            wheelCenterY = 1_200f,
        )
        assertEquals(1_200f + 60f + gap, result.pillsTop, 0.5f)
    }
}
