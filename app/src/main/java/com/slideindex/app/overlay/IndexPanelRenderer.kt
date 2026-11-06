package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.SlideAlongRailSession
import com.slideindex.app.overlay.layout.GridLayoutInfo
import com.slideindex.app.overlay.layout.gridLayoutInfo
import com.slideindex.app.overlay.layout.visualColumn
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.coerceSafe

internal class IndexPanelRenderer(
    private val host: Host
) {
    interface Host {
        val context: Context
        fun settings(): AppSettings
        fun side(): PanelSide
        fun zoneLayout(): GestureZoneLayout
        fun indexSession(): SlideAlongRailSession
        fun gestureSession(): GestureSession
        fun dp(value: Float): Float
        fun sp(value: Float): Float
        fun viewHeight(): Int
        fun panelEnterAdjustedX(localX: Float, panel: RectF): Float
        fun invalidate()
        fun invalidatePanel(rect: RectF)
        fun iconFor(app: AppInfo): Bitmap
        fun onInitiatingEdgeGestureReleased()
    }

    private val railBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val bubbleLetterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
    }
    private val appLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.argb(230, 255, 255, 255)
    }
    private val cellHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellLongPressHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    /** 已冻结应用：图标压暗 + 右下角"禁止"角标，和普通可启动应用区分开。 */
    private val frozenIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 130 }
    private val frozenBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 32, 34, 40)
        style = Paint.Style.FILL
    }
    private val frozenBadgeSlashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = host.dp(1.6f)
        strokeCap = Paint.Cap.ROUND
    }
    /** 暂停（挂起）角标的两条竖杠。 */
    private val pausedBadgeBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = host.dp(1.8f)
        strokeCap = Paint.Cap.ROUND
    }
    /** 分身黄点：与系统分身标识同色。 */
    private val cloneDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFC107.toInt()
    }
    private val cloneDotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 0, 0)
    }

    private val tmpRect = RectF()

    private val cellHeight get() = host.dp(72f)
    private val cellWidth get() = host.dp(68f)
    private val gridIconSize get() = host.dp(44f)
    private val gridPadding get() = host.dp(10f)
    private val gridIconTopInset get() = host.dp(6f)
    private val gridIconLabelGap get() = host.dp(3f)
    private val gridCellInset get() = host.dp(4f)
    private val bubbleRadius get() = host.dp(24f)
    private val bubblePanelGap get() = host.dp(10f)
    private val railCorner get() = host.dp(14f)
    private val panelCorner get() = host.dp(18f)

    private var lastRenderedContentRect: RectF? = null

    private fun invalidateIndexPanelIfChanged(changed: Boolean) {
        if (!changed) return
        val currentRect = indexPanelContentRect()
        val unionRect = lastRenderedContentRect?.let { prev ->
            RectF(
                minOf(currentRect.left, prev.left),
                minOf(currentRect.top, prev.top),
                maxOf(currentRect.right, prev.right),
                maxOf(currentRect.bottom, prev.bottom)
            )
        } ?: currentRect
        lastRenderedContentRect = RectF(currentRect)
        host.invalidatePanel(unionRect)
    }

    fun syncSettings(settings: AppSettings) {
        cellHighlightPaint.color = Color.argb(70, 255, 255, 255)
        cellLongPressHighlightPaint.color = Color.argb(110, 66, 133, 244)
        appLabelPaint.textSize = host.sp(11f)
    }

    fun handleTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        val touchX = host.panelEnterAdjustedX(localX, indexPanelContentRect())
        if (host.indexSession().selectedLetter != null) {
            syncGridCellBounds()
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isInsideIndexInteractiveArea(touchX, localY)) {
                    lastRenderedContentRect = null
                    host.gestureSession().endSession()
                    return false
                }
                invalidateIndexPanelIfChanged(host.indexSession().updateSelection(touchX, localY))
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!host.gestureSession().isMoveTimeActionLocked()) {
                    invalidateIndexPanelIfChanged(host.indexSession().updateSelection(touchX, localY))
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (host.gestureSession().releaseImmediateGestureLock()) {
                    host.onInitiatingEdgeGestureReleased()
                    val currentRect = indexPanelContentRect()
                    val unionRect = lastRenderedContentRect?.let { prev ->
                        RectF(
                            minOf(currentRect.left, prev.left),
                            minOf(currentRect.top, prev.top),
                            maxOf(currentRect.right, prev.right),
                            maxOf(currentRect.bottom, prev.bottom)
                        )
                    } ?: currentRect
                    lastRenderedContentRect = null
                    host.invalidatePanel(unionRect)
                    return true
                }
                if (host.zoneLayout().isInRailZone(touchX)) {
                    invalidateIndexPanelIfChanged(host.indexSession().updateSelection(touchX, localY))
                    return true
                }
                lastRenderedContentRect = null
                val session = host.gestureSession()
                if (session.isActive()) {
                    session.onTouchUp(event.rawX, event.rawY, touchX, localY)
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    // 即时触发：抬指时 releaseImmediateGestureLock 已结束手指跟踪，面板以
                    // leave-open 留在屏上；这次点击不是「按住手势」，直接确认高亮项。
                    // 走 onTouchUp 会被 active 守卫挡掉（表现为只高亮、不启动）。
                    session.confirmIndexSelection()
                }
                return true
            }
        }
        return false
    }

    fun isInsideIndexInteractiveArea(localX: Float, localY: Float): Boolean {
        val zoneLayout = host.zoneLayout()
        if (zoneLayout.isInRailZone(localX)) return true
        val indexSession = host.indexSession()
        if (indexSession.selectedLetter != null) {
            indexSession.gridCellBounds.forEach { (_, rect) ->
                if (rect.contains(localX, localY)) return true
            }
            if (indexSession.filteredApps.isNotEmpty()) {
                val grid = gridPopupRect()
                if (grid.contains(localX, localY)) return true
            }
        }
        return false
    }

    fun indexPanelContentRect(): RectF {
        val zoneLayout = host.zoneLayout()
        val indexSession = host.indexSession()
        val rail = zoneLayout.indexRailRect()
        if (indexSession.selectedLetter == null) {
            return when (host.side()) {
                PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> RectF(rail.left, rail.top, rail.right + host.dp(240f), rail.bottom)
                PanelSide.RIGHT -> RectF(rail.left - host.dp(240f), rail.top, rail.right, rail.bottom)
            }
        }
        val bubble = bubbleCenter()
        if (indexSession.filteredApps.isEmpty()) {
            return RectF(
                minOf(rail.left, bubble.x - bubbleRadius),
                minOf(rail.top, bubble.y - bubbleRadius),
                maxOf(rail.right, bubble.x + bubbleRadius),
                maxOf(rail.bottom, bubble.y + bubbleRadius)
            )
        }
        val grid = gridPopupRect()
        return RectF(
            minOf(rail.left, grid.left, bubble.x - bubbleRadius),
            minOf(rail.top, grid.top, bubble.y - bubbleRadius),
            maxOf(rail.right, grid.right, bubble.x + bubbleRadius),
            maxOf(rail.bottom, grid.bottom, bubble.y + bubbleRadius)
        )
    }

    fun drawLetterRail(canvas: Canvas) {
        val zoneLayout = host.zoneLayout()
        val settings = host.settings()
        val rail = zoneLayout.indexRailRect()
        // 0% = 底板全透明，100% = 实心：与设置页的 0–100% 一一对应。
        val alphaScale = settings.panelOpacity.coerceIn(0f, 1f)
        railBgPaint.color = Color.argb((255 * alphaScale).toInt(), 38, 38, 42)
        canvas.drawRoundRect(rail, railCorner, railCorner, railBgPaint)

        val railLetters = host.indexSession().currentRailLetters()
        if (railLetters.isEmpty()) return

        val slotHeight = rail.height() / railLetters.size
        val letterSize = slotHeight.coerceAtMost(host.dp(11.5f))
        val selectedLetter = host.indexSession().selectedLetter

        railLetters.forEachIndexed { index, letter ->
            val centerY = rail.top + slotHeight * index + slotHeight * 0.65f
            val centerX = rail.centerX()
            val selected = letter == selectedLetter
            if (selected) {
                letterCirclePaint.color = Color.argb(90, 255, 255, 255)
                canvas.drawCircle(centerX, centerY - letterSize * 0.15f, letterSize * 0.85f, letterCirclePaint)
                letterPaint.color = Color.WHITE
                letterPaint.textSize = letterSize * 1.05f
                letterPaint.typeface = Typeface.DEFAULT_BOLD
            } else {
                letterPaint.color = Color.argb(200, 220, 220, 220)
                letterPaint.textSize = letterSize
                letterPaint.typeface = Typeface.DEFAULT
            }
            canvas.drawText(letter.toString(), centerX, centerY, letterPaint)
        }
    }

    fun drawLetterBubble(canvas: Canvas) {
        val letter = host.indexSession().selectedLetter ?: return
        val center = bubbleCenter()
        val settings = host.settings()
        bubblePaint.color = Color.argb(
            (255 * settings.panelOpacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255),
            52,
            52,
            56,
        )
        canvas.drawCircle(center.x, center.y, bubbleRadius, bubblePaint)
        bubbleLetterPaint.textSize = host.sp(22f)
        canvas.drawText(
            letter.toString(),
            center.x,
            center.y + host.sp(22f) * 0.35f,
            bubbleLetterPaint
        )
    }

    fun drawAppGrid(canvas: Canvas) {
        val indexSession = host.indexSession()
        val filteredApps = indexSession.filteredApps
        if (filteredApps.isEmpty()) return
        syncGridCellBounds()
        val appCount = filteredApps.size
        val layout = layoutInfo(appCount)
        val m = layout.appsPerRow
        val grid = gridPopupRect()
        val settings = host.settings()
        panelBgPaint.color = Color.argb(
            (255 * settings.panelOpacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255),
            48,
            48,
            52,
        )
        canvas.drawRoundRect(grid, panelCorner, panelCorner, panelBgPaint)

        indexSession.gridCellBounds.forEach { (app, cell) ->
            if (app == indexSession.highlightedApp) {
                tmpRect.set(cell.left + host.dp(3f), cell.top + host.dp(2f), cell.right - host.dp(3f), cell.bottom - host.dp(2f))
                val paint = if (indexSession.longPressArmed) {
                    cellLongPressHighlightPaint
                } else {
                    cellHighlightPaint
                }
                canvas.drawRoundRect(tmpRect, host.dp(10f), host.dp(10f), paint)
            }

            val icon = host.iconFor(app)
            val iconTop = cell.top + gridIconTopInset
            val label = ellipsize(app.label, cellWidth - gridCellInset * 2)
            val labelBaseline = iconTop + gridIconSize + gridIconLabelGap - appLabelPaint.fontMetrics.ascent
            val iconCenterX = cell.centerX()
            canvas.drawBitmap(
                icon,
                iconCenterX - gridIconSize / 2f,
                iconTop,
                if (app.isFrozen || app.isPaused) frozenIconPaint else null,
            )
            when {
                app.isFrozen -> drawFrozenBadge(canvas, iconCenterX, iconTop)
                app.isPaused -> drawPausedBadge(canvas, iconCenterX, iconTop)
            }
            if (app.isClone) {
                drawCloneDot(canvas, iconCenterX, iconTop)
            }
            canvas.drawText(label, iconCenterX, labelBaseline, appLabelPaint)
        }
    }

    /** 暂停角标：同位置的小圆 + 两条竖杠（暂停符号），与冻结的斜杠区分。 */
    private fun drawPausedBadge(canvas: Canvas, iconCenterX: Float, iconTop: Float) {
        val radius = host.dp(8f)
        val cx = iconCenterX + gridIconSize * 0.42f
        val cy = iconTop + gridIconSize * 0.84f
        canvas.drawCircle(cx, cy, radius, frozenBadgeBgPaint)
        val barHeight = radius * 0.9f
        val barOffset = radius * 0.30f
        canvas.drawLine(cx - barOffset, cy - barHeight / 2f, cx - barOffset, cy + barHeight / 2f, pausedBadgeBarPaint)
        canvas.drawLine(cx + barOffset, cy - barHeight / 2f, cx + barOffset, cy + barHeight / 2f, pausedBadgeBarPaint)
    }

    /** 分身角标：图标右上角一个黄点（与任务切换器 / 系统分身标识同色）。 */
    private fun drawCloneDot(canvas: Canvas, iconCenterX: Float, iconTop: Float) {
        val radius = host.dp(5.5f)
        val cx = iconCenterX + gridIconSize * 0.42f
        val cy = iconTop + gridIconSize * 0.16f
        canvas.drawCircle(cx, cy, radius, cloneDotRingPaint)
        canvas.drawCircle(cx, cy, radius - host.dp(1f), cloneDotPaint)
    }

    /** 冻结角标：图标右下角一个小圆 + 斜杠，语言无关地表示"当前不可直接启动"。 */
    private fun drawFrozenBadge(canvas: Canvas, iconCenterX: Float, iconTop: Float) {
        val radius = host.dp(8f)
        val cx = iconCenterX + gridIconSize * 0.42f
        val cy = iconTop + gridIconSize * 0.84f
        canvas.drawCircle(cx, cy, radius, frozenBadgeBgPaint)
        val offset = radius * 0.52f
        canvas.drawLine(cx - offset, cy + offset, cx + offset, cy - offset, frozenBadgeSlashPaint)
    }

    fun syncGridCellBounds() {
        val indexSession = host.indexSession()
        val filteredApps = indexSession.filteredApps
        if (filteredApps.isEmpty()) {
            indexSession.gridCellBounds.clear()
            return
        }
        val appCount = filteredApps.size
        val layout = layoutInfo(appCount)
        val m = layout.appsPerRow
        val grid = gridPopupRect()

        indexSession.gridCellBounds.clear()
        filteredApps.forEachIndexed { index, app ->
            val row = index / m
            val visualCol = visualColumn(index, m, appCount, host.side())
            val left = grid.left + gridPadding + visualCol * cellWidth
            val top = grid.top + gridPadding + row * cellHeight
            val cell = RectF(left, top, left + cellWidth, top + cellHeight)
            indexSession.gridCellBounds += app to cell
        }
    }

    fun gridPopupRect(): RectF {
        val indexSession = host.indexSession()
        val zoneLayout = host.zoneLayout()
        val filteredApps = indexSession.filteredApps
        val layout = layoutInfo(filteredApps.size)
        val gh = layout.rows * cellHeight + gridPadding * 2
        val gw = layout.panelWidth
        val rail = zoneLayout.indexRailRect()
        val letterY = indexSession.selectedLetterCenterY() ?: rail.centerY()
        val bubbleReserve = bubbleRadius * 2 + bubblePanelGap + host.dp(8f)
        var top = letterY - gh / 2f
        top = top.coerceSafe(bubbleReserve + host.dp(8f), host.viewHeight() - gh - host.dp(16f))
        val gap = host.dp(8f)
        val left = when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> rail.right + gap
            PanelSide.RIGHT -> rail.left - gap - gw
        }
        return RectF(left, top, left + gw, top + gh)
    }

    fun bubbleCenter(): PointF {
        val indexSession = host.indexSession()
        val zoneLayout = host.zoneLayout()
        val filteredApps = indexSession.filteredApps
        if (filteredApps.isNotEmpty()) {
            val grid = gridPopupRect()
            val layout = layoutInfo(filteredApps.size)
            val cx = columnCenterX(grid, anchorColumnIndex(layout))
            val cy = grid.top - bubbleRadius - bubblePanelGap
            return PointF(cx, cy)
        }
        val rail = zoneLayout.indexRailRect()
        val cy = indexSession.selectedLetterCenterY() ?: rail.centerY()
        val cx = when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> rail.right + bubbleRadius + host.dp(4f)
            PanelSide.RIGHT -> rail.left - bubbleRadius - host.dp(4f)
        }
        return PointF(cx, cy)
    }

    private fun appsPerRow(): Int = host.settings().appsPerRow.coerceIn(2, 5)

    private fun layoutInfo(appCount: Int): GridLayoutInfo =
        gridLayoutInfo(appCount, appsPerRow(), cellWidth, gridPadding)

    private fun anchorColumnIndex(layout: GridLayoutInfo): Int {
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> 0
            PanelSide.RIGHT -> layout.panelColumns - 1
        }
    }

    private fun columnCenterX(grid: RectF, columnIndex: Int): Float {
        return grid.left + gridPadding + columnIndex * cellWidth + cellWidth / 2f
    }

    private fun ellipsize(text: String, maxWidth: Float): String {
        if (appLabelPaint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && appLabelPaint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end.coerceAtLeast(1)) + "…"
    }
}
