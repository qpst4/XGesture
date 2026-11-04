package com.slideindex.app.overlay.layout

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.util.coerceSafe
import kotlin.math.min

object TaskSwitcherLayoutEngine {
    const val MAX_VISIBLE_ROWS = 9
    private const val SCROLL_PEEK_DP = 15f
    private const val SCROLL_PEEK_ROW_FRACTION = 0.35f

    fun scrollPeekHeight(host: TaskSwitcherLayoutHost, rowHeight: Float): Float =
        min(host.dp(SCROLL_PEEK_DP), rowHeight * SCROLL_PEEK_ROW_FRACTION)

    fun compute(
        host: TaskSwitcherLayoutHost,
        rows: List<TaskSwitcherRowEntry>,
        scrollOffset: Float,
        anchorLocalY: Float,
        /**
         * 触发点的本地横坐标（手指按下点）。
         *
         * 上下侧触钮是横条，没有"内侧边"，面板以一个水平锚点居中展开：
         * 有手指坐标时就用手指按下的位置，避免面板永远锁死在屏幕正中；
         * `null`（旧调用 / 没有手指坐标）回退到触钮中心。
         */
        anchorLocalX: Float? = null,
    ): Pair<TaskSwitcherPanelLayout, Float> {
        val rowHeight = host.dp(42f)
        val footerHeight = host.dp(36f)
        val verticalMargin = host.dp(32f)
        val viewWidth = host.viewWidth().toFloat()
        val horizontalMargin = host.dp(16f)
        // 面板宽度不得超过「整屏 - 左右边距」：窄屏 / 分屏下宁可变窄，也不能画到屏幕外。
        val panelWidth = if (viewWidth > 0f) {
            host.dp(226f).coerceAtMost(
                (viewWidth - horizontalMargin * 2f).coerceAtLeast(1f)
            )
        } else {
            host.dp(226f)
        }
        val isEmpty = rows.isEmpty()
        val contentHeight = if (isEmpty) {
            (host.dp(52f) - footerHeight).coerceAtLeast(0f)
        } else {
            rows.size * rowHeight
        }
        val maxListHeightByRows = rowHeight * MAX_VISIBLE_ROWS
        val maxListHeightByScreen =
            (host.viewHeight() - verticalMargin - footerHeight).coerceAtLeast(rowHeight * 2f)
        val rowCapHeight = min(maxListHeightByRows, maxListHeightByScreen)
        val listScrollPeekHeight = if (!isEmpty && contentHeight > maxListHeightByRows) {
            scrollPeekHeight(host, rowHeight)
                .coerceAtMost((maxListHeightByScreen - rowCapHeight).coerceAtLeast(0f))
        } else {
            0f
        }
        val visibleListHeight = if (isEmpty) {
            contentHeight
        } else {
            min(contentHeight, rowCapHeight + listScrollPeekHeight)
        }
        val maxScrollOffset = (contentHeight - visibleListHeight).coerceAtLeast(0f)
        val coercedOffset = scrollOffset.coerceIn(0f, maxScrollOffset)
        val panelHeight = visibleListHeight + footerHeight
        val trigger = host.activeTriggerZoneRect()
        val anchorY = anchorLocalY.coerceIn(trigger.top, trigger.bottom)
        val gap = host.dp(10f)
        // 左右侧触钮是竖条：贴着触钮的"内侧边"展开是对的。
        // 上下侧触钮是横条，`trigger.right` 只是横条右端、不是屏幕边 —— 沿用它向右展开
        // 必然把面板推出屏幕（底部触钮触发时只剩一条边可见）。横条没有"内侧"，
        // 改为以触发点（手指按下位置）为水平中心，再夹进左右边距。
        val preferredLeft = when (host.side()) {
            PanelSide.LEFT -> trigger.right + gap
            PanelSide.RIGHT -> trigger.left - gap - panelWidth
            PanelSide.BOTTOM, PanelSide.TOP -> if (viewWidth > 0f) {
                val centerX = anchorLocalX
                    ?: if (trigger.width() > 0f) trigger.centerX() else viewWidth / 2f
                centerX - panelWidth / 2f
            } else {
                // 尚未测量出宽度：退回旧公式，等真实尺寸到了下一帧会重新居中。
                trigger.right + gap
            }
        }
        val maxLeft = (viewWidth - panelWidth - horizontalMargin).coerceAtLeast(horizontalMargin)
        val left = if (viewWidth > 0f) {
            preferredLeft.coerceSafe(horizontalMargin, maxLeft)
        } else {
            preferredLeft
        }
        val top = if (host.side() == PanelSide.TOP) {
            (trigger.bottom + gap).coerceSafe(host.dp(16f), (host.viewHeight() - panelHeight - host.dp(16f)).coerceAtLeast(host.dp(16f)))
        } else {
            anchorY - min(rowHeight, visibleListHeight.coerceAtLeast(rowHeight)) / 2f
        }.let { value ->
            value.coerceSafe(host.dp(16f), (host.viewHeight() - panelHeight - host.dp(16f)).coerceAtLeast(host.dp(16f)))
        }
        val panelRect = RectF(left, top, left + panelWidth, top + panelHeight)
        val listRect = RectF(panelRect.left, panelRect.top, panelRect.right, panelRect.top + visibleListHeight)
        val closeAllRect = RectF(
            panelRect.left,
            panelRect.bottom - footerHeight,
            panelRect.right,
            panelRect.bottom,
        )
        val rowLayouts = rows.mapIndexed { index, entry ->
            val rowTop = listRect.top + index * rowHeight - coercedOffset
            val rowRect = RectF(panelRect.left, rowTop, panelRect.right, rowTop + rowHeight)
            val closeRect = closeHitRect(host, rowRect)
            val freeWindowRect = handleColumnRect(host, rowRect)
            TaskSwitcherRowLayout(entry, rowRect, closeRect, freeWindowRect)
        }
        val layout = TaskSwitcherPanelLayout(
            panelRect = panelRect,
            listRect = listRect,
            rows = rowLayouts,
            closeAllRect = closeAllRect,
            scrollOffset = coercedOffset,
            maxScrollOffset = maxScrollOffset,
            listScrollPeekHeight = listScrollPeekHeight,
        )
        return layout to coercedOffset
    }

    fun closeColumnRect(host: TaskSwitcherLayoutHost, rowRect: RectF): RectF {
        val size = actionSize(host)
        val pad = closeEdgePadding(host)
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> RectF(rowRect.left + pad, rowRect.top, rowRect.left + pad + size, rowRect.bottom)
            PanelSide.RIGHT -> RectF(rowRect.right - pad - size, rowRect.top, rowRect.right - pad, rowRect.bottom)
        }
    }

    fun handleColumnRect(host: TaskSwitcherLayoutHost, rowRect: RectF): RectF {
        val size = actionSize(host)
        val centerX = gripCenterX(host, rowRect)
        return RectF(centerX - size / 2f, rowRect.top, centerX + size / 2f, rowRect.bottom)
    }

    fun closeIconRect(host: TaskSwitcherLayoutHost, rowRect: RectF): RectF {
        val size = actionSize(host)
        val column = closeColumnRect(host, rowRect)
        val cy = rowRect.centerY()
        return RectF(column.left, cy - size / 2f, column.right, cy + size / 2f)
    }

    fun closeHitRect(host: TaskSwitcherLayoutHost, rowRect: RectF): RectF {
        val column = closeColumnRect(host, rowRect)
        val slop = hitSlop(host)
        val gapReach = host.dp(10f)
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> RectF(column.left - slop - gapReach, column.top, column.right + slop, column.bottom)
            PanelSide.RIGHT -> RectF(column.left - slop, column.top, column.right + slop + gapReach, column.bottom)
        }
    }

    fun gripX(host: TaskSwitcherLayoutHost, rowRect: RectF): Float {
        val inset = actionIconInset(host)
        val radius = gripDotRadius(host)
        val gapX = gripGapX(host)
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> rowRect.right - inset - gapX - radius
            PanelSide.RIGHT -> rowRect.left + inset + radius
        }
    }

    fun gripCenterX(host: TaskSwitcherLayoutHost, rowRect: RectF): Float =
        gripX(host, rowRect) + gripGapX(host) / 2f

    fun iconLeft(host: TaskSwitcherLayoutHost, row: TaskSwitcherRowLayout): Float {
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> closeColumnRect(host, row.rowRect).right + host.dp(4f)
            PanelSide.RIGHT -> handleColumnRect(host, row.rowRect).right + host.dp(4f)
        }
    }

    fun labelMaxWidth(host: TaskSwitcherLayoutHost, row: TaskSwitcherRowLayout, labelX: Float): Float {
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> handleColumnRect(host, row.rowRect).left - labelX - host.dp(8f)
            PanelSide.RIGHT -> closeColumnRect(host, row.rowRect).left - labelX - host.dp(6f)
        }.coerceAtLeast(host.dp(24f))
    }

    private fun actionIconInset(host: TaskSwitcherLayoutHost): Float = host.dp(9.5f)

    private fun actionSize(host: TaskSwitcherLayoutHost): Float = host.dp(30f)

    private fun closeEdgePadding(host: TaskSwitcherLayoutHost): Float = host.dp(5.5f)

    private fun hitSlop(host: TaskSwitcherLayoutHost): Float = host.dp(2f)

    private fun gripDotRadius(host: TaskSwitcherLayoutHost): Float = host.dp(1.65f)

    private fun gripGapX(host: TaskSwitcherLayoutHost): Float = host.dp(3f)

    fun gripGapY(host: TaskSwitcherLayoutHost): Float = host.dp(3.6f)
}
