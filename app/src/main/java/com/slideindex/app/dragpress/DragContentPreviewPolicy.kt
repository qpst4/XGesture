package com.slideindex.app.dragpress

import kotlin.math.roundToInt

/**
 * 拖拽跟随缩略卡片的布局定位策略：跟随手指触点上方，自动限制在屏幕安全可视区域内。
 */
internal object DragContentPreviewPolicy {
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)
    data class Point(val x: Int, val y: Int)

    fun summary(text: CharSequence): String = text.take(160).toString()
        .replace(Regex("\\s+"), " ")
        .trim()
        .let { if (text.length > 160) "$it…" else it }

    fun position(x: Float, y: Float, width: Int, height: Int, screen: Box, gap: Int): Point = Point(
        (x - width / 2f).roundToInt().coerceIn(screen.left, (screen.right - width).coerceAtLeast(screen.left)),
        (y - height - gap).roundToInt().coerceIn(screen.top, (screen.bottom - height).coerceAtLeast(screen.top)),
    )
}
