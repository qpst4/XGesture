package com.slideindex.app.overlay.layout

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.util.coerceSafe

object QuickLauncherPanelLayoutEngine {
    const val MAX_COLUMNS = 6
    const val MAX_ROWS = 9

    fun gridLayoutInfo(
        columnsPerPage: Int,
        rowsPerPage: Int,
        cellWidth: Float,
        gridPadding: Float,
    ): GridLayoutInfo {
        val columns = columnsPerPage.coerceIn(2, MAX_COLUMNS)
        val rows = rowsPerPage.coerceIn(2, MAX_ROWS)
        val panelWidth = columns * cellWidth + gridPadding * 2
        return GridLayoutInfo(columns, columns, rows, panelWidth)
    }

    fun contentHeight(
        rows: Int,
        cellHeight: Float,
        gridPadding: Float,
        headerHeight: Float,
    ): Float = rows * cellHeight + gridPadding * 2 + headerHeight

    /**
     * 单格行高。
     *
     * 显示名称时用 [labeledCellHeight]（格子底部要给标签留位置）；关掉名称后行高收到与
     * [cellWidth] 一致，让图标的纵向间隙等于横向间隙。
     *
     * 不这样做的话：列宽 56dp、行高 64dp、图标 38dp，则左右各空 9dp 而上下各空 13dp，
     * 关掉名称后整块面板看起来行距明显偏大、还白白多占一截高度。
     */
    fun cellHeight(
        showLabels: Boolean,
        cellWidth: Float,
        labeledCellHeight: Float,
    ): Float = if (showLabels) labeledCellHeight else cellWidth

    fun anchoredPanelRect(
        host: OverlayPanelLayoutHost,
        panelWidth: Float,
        contentHeight: Float,
        anchorLocalY: Float,
        /**
         * 触发带覆盖（钉住面板用）。
         *
         * 落位公式的横向位置与纵向夹取都由触发带决定，而 [OverlayPanelLayoutHost.activeTriggerZoneRect]
         * 在"手势进行中"返回**当前那一个触钮**的矩形、在"空闲"返回**该侧所有触钮的并集** ——
         * 两者不是同一个矩形。钉住面板必须沿用呼出那一刻的触发带，否则松手后一重算就整体平移。
         * `null` = 不用覆盖（保持历史行为）。
         */
        triggerRectOverride: RectF? = null,
        /**
         * 是否把纵向锚点夹进触发带。
         *
         * - `true`（默认）：面板始终贴着触钮条出现，这是触钮自己触发的历史行为；
         * - `false`：只保留屏幕边距，锚点就是本次触发点。用于**外部触发**
         *   （全局触控 / 角轮盘 / 悬浮球）：手指在屏幕任意高度呼出，面板就出现在那个高度，
         *   而不是被拉回触钮条的纵向范围（真机表现是"最高只到屏幕中间"）。
         */
        clampAnchorToTrigger: Boolean = true,
    ): RectF {
        val trigger = triggerRectOverride ?: host.activeTriggerZoneRect()
        val anchorY = if (clampAnchorToTrigger) {
            anchorLocalY.coerceIn(trigger.top, trigger.bottom)
        } else {
            anchorLocalY
        }
        val gap = host.dp(8f)
        val left = when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> trigger.right + gap
            PanelSide.RIGHT -> trigger.left - gap - panelWidth
        }
        val bottomMargin = host.dp(60f)
        val top = if (host.side() == PanelSide.TOP) {
            (trigger.bottom + gap).coerceSafe(host.dp(16f), host.viewHeight() - contentHeight - bottomMargin)
        } else {
            (anchorY - contentHeight / 2f).coerceSafe(host.dp(16f), host.viewHeight() - contentHeight - bottomMargin)
        }
        return RectF(left, top, left + panelWidth, top + contentHeight)
    }

    fun offsetForToolbar(
        host: OverlayPanelLayoutHost,
        panelRect: RectF,
        reserveWidth: Float,
    ): RectF {
        if (panelRect.isEmpty) return panelRect
        val margin = host.dp(16f)
        var left = panelRect.left
        var right = panelRect.right
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> {
                if (left < margin) {
                    val delta = margin - left
                    left += delta
                    right += delta
                }
                if (right > host.viewWidth() - margin) {
                    val delta = right - (host.viewWidth() - margin)
                    left -= delta
                    right = host.viewWidth() - margin
                    left = left.coerceAtLeast(margin)
                }
                RectF(left, panelRect.top, right, panelRect.bottom)
            }
            PanelSide.RIGHT -> {
                val combinedLeft = left - reserveWidth
                if (combinedLeft < margin) {
                    val delta = margin - combinedLeft
                    left += delta
                    right += delta
                }
                if (right > host.viewWidth() - margin) {
                    val delta = right - (host.viewWidth() - margin)
                    left -= delta
                    right = host.viewWidth() - margin
                    left = left.coerceAtLeast(margin)
                }
                RectF(left, panelRect.top, right, panelRect.bottom)
            }
        }
    }

    fun panelRect(
        host: OverlayPanelLayoutHost,
        columnsPerPage: Int,
        rowsPerPage: Int,
        cellWidth: Float,
        cellHeight: Float,
        gridPadding: Float,
        headerHeight: Float,
        anchorLocalY: Float,
        toolbarReserveWidth: Float,
        /** 触发带覆盖；见 [anchoredPanelRect]。`null` = 用宿主当前的实时触发带。 */
        triggerRectOverride: RectF? = null,
        /** 是否把锚点夹进触发带；见 [anchoredPanelRect]。外部触发传 `false`。 */
        clampAnchorToTrigger: Boolean = true,
    ): RectF {
        val layout = gridLayoutInfo(columnsPerPage, rowsPerPage, cellWidth, gridPadding)
        val height = contentHeight(layout.rows, cellHeight, gridPadding, headerHeight)
        val base = anchoredPanelRect(
            host = host,
            panelWidth = layout.panelWidth,
            contentHeight = height,
            anchorLocalY = anchorLocalY,
            triggerRectOverride = triggerRectOverride,
            clampAnchorToTrigger = clampAnchorToTrigger,
        )
        return offsetForToolbar(host, base, toolbarReserveWidth)
    }
}
