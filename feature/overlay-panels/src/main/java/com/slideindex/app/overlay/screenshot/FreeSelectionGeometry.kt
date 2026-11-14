package com.slideindex.app.overlay.screenshot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * 拖拽框选的纯几何逻辑，与 Android / Compose UI 无关，便于 JVM 单测直接覆盖。
 *
 * 坐标沿用 [SmartScreenshotState] 的约定：视图坐标与位图像素 1:1。
 */
object FreeSelectionGeometry {

    /**
     * 由起点与当前点构造矩形并夹到图片边界内。
     *
     * 归一化处理了「从右下往左上拖」的情况，调用方拿到的永远是 left <= right、top <= bottom。
     */
    fun rectFromDrag(start: Offset, current: Offset, bounds: Rect): Rect {
        val leftBound = min(bounds.left, bounds.right)
        val rightBound = max(bounds.left, bounds.right)
        val topBound = min(bounds.top, bounds.bottom)
        val bottomBound = max(bounds.top, bounds.bottom)
        val x1 = start.x.coerceIn(leftBound, rightBound)
        val y1 = start.y.coerceIn(topBound, bottomBound)
        val x2 = current.x.coerceIn(leftBound, rightBound)
        val y2 = current.y.coerceIn(topBound, bottomBound)
        return Rect(
            left = min(x1, x2),
            top = min(y1, y2),
            right = max(x1, x2),
            bottom = max(y1, y2)
        )
    }

    /** 是否构成可导出的选区：宽高都要达到最小尺寸（防止一次轻点就提交 0x0 截图）。 */
    fun isUsable(rect: Rect, minSize: Float): Boolean {
        // 下限至少 1px：没设置最小尺寸时也不能让一次轻点提交 0x0 截图。
        val threshold = max(1f, minSize)
        return rect.width >= threshold && rect.height >= threshold
    }
}
