package com.slideindex.app.overlay

import android.content.Intent
import android.graphics.Paint
import android.graphics.RectF
import com.slideindex.app.util.ShortcutKind


data class TaskSwitcherContextMenuLayout(
    val rowIndex: Int,
    val packageName: String,
    val menuRect: RectF,
    val items: List<TaskSwitcherMenuItem>,
    val itemRects: List<RectF>,
    val inlineInPanel: Boolean = false
)

object TaskSwitcherContextMenuLayoutFactory {
    fun build(
        side: PanelSide,
        panelRect: RectF,
        listRect: RectF,
        rowIndex: Int,
        packageName: String,
        items: List<TaskSwitcherMenuItem>,
        viewWidth: Int,
        viewHeight: Int,
        density: Float,
        anchorX: Float,
        anchorY: Float,
        inlineInPanel: Boolean = false
    ): TaskSwitcherContextMenuLayout {
        val itemHeight = 44f * density
        val padV = 6f * density
        val padH = 16f * density
        val gap = 8f * density
        val edge = 12f * density
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f * density
        }
        val maxTextWidth = items.maxOfOrNull { textPaint.measureText(it.label) } ?: 0f
        val menuWidth = maxTextWidth + padH * 2f
        val menuHeight = items.size * itemHeight + padV * 2f
        val inset = 8f * density
        val maxInlineWidth = panelRect.width() - inset * 2f
        val resolvedWidth = menuWidth.coerceAtMost(maxInlineWidth)
        val (clampMin, clampMax) = if (inlineInPanel) {
            panelRect.left + inset to panelRect.right - inset - resolvedWidth
        } else {
            edge to viewWidth - edge - resolvedWidth
        }
        val menuLeft = resolveMenuLeftFromAnchor(
            side = side,
            anchorX = anchorX,
            menuWidth = resolvedWidth,
            gap = gap,
            edge = edge,
            viewWidth = viewWidth,
            clampMin = clampMin,
            clampMax = clampMax
        )
        val menuTop = if (inlineInPanel) {
            (anchorY - menuHeight / 2f).coerceIn(
                listRect.top + inset,
                (listRect.bottom - menuHeight - inset).coerceAtLeast(listRect.top + inset)
            )
        } else {
            (anchorY - menuHeight / 2f).coerceIn(
                edge,
                (viewHeight - menuHeight - edge).coerceAtLeast(edge)
            )
        }
        val menuRect = RectF(menuLeft, menuTop, menuLeft + resolvedWidth, menuTop + menuHeight)
        val itemRects = items.mapIndexed { index, _ ->
            val top = menuRect.top + padV + index * itemHeight
            RectF(menuRect.left, top, menuRect.right, top + itemHeight)
        }
        return TaskSwitcherContextMenuLayout(
            rowIndex = rowIndex,
            packageName = packageName,
            menuRect = menuRect,
            items = items,
            itemRects = itemRects,
            inlineInPanel = inlineInPanel
        )
    }

    /** Left-side panel: menu on the right of the finger; right-side panel: menu on the left. */
    private fun resolveMenuLeftFromAnchor(
        side: PanelSide,
        anchorX: Float,
        menuWidth: Float,
        gap: Float,
        edge: Float,
        viewWidth: Int,
        clampMin: Float,
        clampMax: Float
    ): Float {
        val preferred = when (side) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> anchorX + gap
            PanelSide.RIGHT -> anchorX - gap - menuWidth
        }
        val alternate = when (side) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> anchorX - gap - menuWidth
            PanelSide.RIGHT -> anchorX + gap
        }
        var menuLeft = preferred
        if (menuLeft < edge || menuLeft + menuWidth > viewWidth - edge) {
            menuLeft = alternate
        }
        val minLeft = clampMin.coerceAtMost(clampMax)
        val maxLeft = clampMax.coerceAtLeast(clampMin)
        return menuLeft.coerceIn(minLeft, maxLeft)
    }
}
