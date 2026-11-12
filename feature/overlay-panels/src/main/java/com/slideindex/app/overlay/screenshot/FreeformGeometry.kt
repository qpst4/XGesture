package com.slideindex.app.overlay.screenshot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * 自由形状选区的纯几何工具。
 *
 * 只依赖 Compose 的 [Offset]/[Rect] 值类型、不碰 Canvas，方便单独验证与复用。
 * 采样点按**触点顺序**保存（不抽稀成多边形顶点），所以这里的"内部"判断是针对折线的射线法。
 */
internal object FreeformGeometry {

    /** 少于三个点围不出面，视为无效笔画。 */
    const val MIN_POINT_COUNT = 3

    fun boundsOf(points: List<Offset>): Rect {
        if (points.isEmpty()) {
            return Rect.Zero
        }
        var left = points[0].x
        var top = points[0].y
        var right = left
        var bottom = top
        for (index in 1 until points.size) {
            val point = points[index]
            if (point.x < left) left = point.x
            if (point.x > right) right = point.x
            if (point.y < top) top = point.y
            if (point.y > bottom) bottom = point.y
        }
        return Rect(left, top, right, bottom)
    }

    /**
     * 射线法判断点是否在闭合折线内。
     *
     * 只用于"拖动整体移动"的命中判断，精度足够；自交的笔画按奇偶规则算，
     * 与 Skia 的 Winding 填充可能略有差异，但拖动命中不要求与裁剪像素完全一致。
     */
    fun isPointInside(points: List<Offset>, point: Offset): Boolean {
        if (points.size < MIN_POINT_COUNT) {
            return false
        }
        var inside = false
        var previousIndex = points.lastIndex
        for (index in points.indices) {
            val current = points[index]
            val previous = points[previousIndex]
            if ((current.y > point.y) != (previous.y > point.y)) {
                val crossX = (previous.x - current.x) * (point.y - current.y) /
                    (previous.y - current.y) + current.x
                if (point.x < crossX) {
                    inside = !inside
                }
            }
            previousIndex = index
        }
        return inside
    }
}
