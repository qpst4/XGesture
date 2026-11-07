package com.slideindex.app.overlay

import android.graphics.RectF

/**
 * 外部触发面板时的"合成触发带"。
 *
 * 面板布局引擎（`QuickLauncherPanelLayoutEngine` / `TaskSwitcherLayoutEngine`）把触发带当成两件事用：
 * **贴哪条边**（读 `trigger.left/right/top/bottom`）与**锚点可落的高度区间**
 *（把锚点 `coerceIn(trigger.top, trigger.bottom)`）。
 *
 * 而"没有任何触钮"的侧没有真实触发带：`GestureZoneLayout.triggerZoneUnionRect()` 返回空矩形 (0,0,0,0)，
 * 锚点于是被夹成 0 —— 面板会贴到屏幕最上沿，而不是手指那一行。
 *
 * 这里按**本次触发点**合成一条窄带：贴 [side] 那条边、纵向正好落在触发高度。
 * 横向厚度只决定"面板与屏幕边留出与触钮条相当的空隙"，不参与别的判定。
 */
object PanelTriggerBand {
    fun synthetic(
        side: PanelSide,
        anchorLocalY: Float,
        viewWidthPx: Int,
        viewHeightPx: Int,
        thicknessPx: Float,
    ): RectF {
        val width = viewWidthPx.coerceAtLeast(1).toFloat()
        val height = viewHeightPx.coerceAtLeast(1).toFloat()
        val thickness = thicknessPx.coerceAtLeast(0f)
        val y = anchorLocalY.coerceIn(0f, height)
        return when (side) {
            // 左右两侧的带沿竖直方向铺：零高 = 锚点恒等于触发高度。
            PanelSide.LEFT -> RectF(0f, y, thickness.coerceAtMost(width), y)
            PanelSide.RIGHT -> RectF((width - thickness).coerceAtLeast(0f), y, width, y)
            // 顶/底不会成为轮盘或悬浮球的触发侧（它们只有左右），这里只做兜底，保证不是空矩形。
            PanelSide.BOTTOM -> RectF(0f, (height - thickness).coerceAtLeast(0f), 0f, height)
            PanelSide.TOP -> RectF(0f, 0f, 0f, thickness.coerceAtMost(height))
        }
    }
}
