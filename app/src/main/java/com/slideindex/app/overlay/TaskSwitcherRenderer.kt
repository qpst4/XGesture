package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.privilege.PrivilegeUiStrings
import com.slideindex.app.overlay.layout.TaskSwitcherLayoutEngine
import com.slideindex.app.overlay.layout.TaskSwitcherPanelLayout
import com.slideindex.app.util.AppLocaleApplier
import com.slideindex.app.util.CloneBadge
import com.slideindex.app.util.RecentAppEntry

internal data class TaskSwitcherRenderState(
    val loading: Boolean,
    val privilegedAccess: Boolean,
    val recentEntries: List<RecentAppEntry>,
    val rowHighlight: Int,
    val closeHighlight: Int,
    val freeWindowHighlight: Int,
    val closeAllHighlight: Boolean,
    val overscrollOffset: Float,
    val overscrollEnabled: Boolean,
    val contextMenuActive: Boolean,
    val contextMenu: TaskSwitcherContextMenuLayout?,
    val menuHighlight: Int,
    val menuEnterProgress: Float
)

internal class TaskSwitcherRenderer(
    private val host: TaskSwitcherOverlayController.Host
) {
    private fun localizedContext() = AppLocaleApplier.wrapOverlayContext(host.context)

    private val elevatedCardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val elevatedShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val highlightPath = Path()
    private val tmpRect = RectF()

    fun drawPanelContent(
        canvas: Canvas,
        layout: TaskSwitcherPanelLayout,
        state: TaskSwitcherRenderState
    ) {
        val theme = OverlayPanelTheme.colors(host.context)
        val rowHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.rowHighlight }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.divider }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textPrimary
            textSize = host.sp(13.5f)
        }
        val closeAllPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.accent
            textSize = host.sp(13f)
            textAlign = Paint.Align.CENTER
        }
        val closeIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.iconMuted
            strokeWidth = host.dp(1.55f)
            strokeCap = Paint.Cap.ROUND
        }
        val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.grip }

        val panel = layout.panelRect
        val panelCorner = host.dp(13f)
        drawElevationShadow(canvas, panel, panelCorner)
        elevatedCardPaint.color = theme.cardBackground
        canvas.drawRoundRect(panel, panelCorner, panelCorner, elevatedCardPaint)

        if (layout.rows.isEmpty()) {
            val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.textMuted
                textSize = host.sp(13f)
                textAlign = Paint.Align.CENTER
            }
            val hint = when {
                !state.privilegedAccess ->
                    localizedContext().getString(PrivilegeUiStrings.taskSwitcherAccessRequiredRes())
                state.loading ->
                    localizedContext().getString(R.string.task_switcher_loading)
                else ->
                    localizedContext().getString(R.string.task_switcher_empty)
            }
            canvas.drawText(
                hint,
                panel.centerX(),
                panel.top + (panel.height() - layout.closeAllRect.height()) / 2f -
                    (hintPaint.descent() + hintPaint.ascent()) / 2f,
                hintPaint
            )
        }

        canvas.withClip(layout.listRect) {
            val overscroll = state.overscrollOffset
            if (overscroll != 0f && state.overscrollEnabled) {
                val pull = kotlin.math.abs(overscroll)
                val stretch = 1f + (pull / layout.listRect.height().coerceAtLeast(1f)) * TASK_SWITCHER_OVERSCROLL_STRETCH
                val pivotY = if (overscroll > 0f) layout.listRect.top else layout.listRect.bottom
                scale(1f, stretch, layout.listRect.centerX(), pivotY)
                translate(0f, overscroll)
            }
            layout.rows.forEachIndexed { index, row ->
                if (!RectF.intersects(layout.listRect, row.rowRect)) return@forEachIndexed
                val entry = state.recentEntries.getOrNull(index) ?: return@forEachIndexed
                if (index == state.rowHighlight ||
                    (state.contextMenuActive && index == state.contextMenu?.rowIndex)
                ) {
                    drawListHighlight(
                        this,
                        row.rowRect,
                        rowHighlightPaint,
                        layout,
                        panelCorner,
                        roundTopLeading = true,
                        roundTopTrailing = true
                    )
                }
                if (index == state.closeHighlight) {
                    drawListHighlight(
                        this,
                        TaskSwitcherLayoutEngine.closeColumnRect(host, row.rowRect),
                        rowHighlightPaint,
                        layout,
                        panelCorner,
                        roundTopLeading = host.side() == PanelSide.LEFT,
                        roundTopTrailing = host.side() == PanelSide.RIGHT
                    )
                }
                if (index == state.freeWindowHighlight) {
                    drawListHighlight(
                        this,
                        TaskSwitcherLayoutEngine.handleColumnRect(host, row.rowRect),
                        rowHighlightPaint,
                        layout,
                        panelCorner
                    )
                }
                val iconSize = host.dp(30f)
                val iconLeft = TaskSwitcherLayoutEngine.iconLeft(host, row)
                val iconTop = row.rowRect.centerY() - iconSize / 2f
                drawScaledIcon(this, entry.app, iconLeft, iconTop, iconSize)
                if (entry.isClone) {
                    CloneBadge.draw(
                        this,
                        iconLeft + iconSize / 2f,
                        iconTop + iconSize / 2f,
                        iconSize,
                        circular = false,
                    )
                }
                val labelX = iconLeft + iconSize + host.dp(9f)
                val labelMaxWidth = TaskSwitcherLayoutEngine.labelMaxWidth(host, row, labelX)
                val label = ellipsize(entry.app.label, labelMaxWidth, labelPaint)
                val labelBaseline = row.rowRect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f
                drawText(label, labelX, labelBaseline, labelPaint)
                val gripX = TaskSwitcherLayoutEngine.gripX(host, row.rowRect)
                drawGripDots(this, gripX, row.rowRect.centerY(), gripPaint)
                drawCloseOrLockIcon(
                    this,
                    TaskSwitcherLayoutEngine.closeIconRect(host, row.rowRect),
                    entry.isLocked,
                    closeIconPaint
                )
                if (index < layout.rows.lastIndex) {
                    val dividerBottom = row.rowRect.bottom
                    if (dividerBottom <= layout.listRect.bottom && dividerBottom >= layout.listRect.top) {
                        drawLine(
                            row.rowRect.left + host.dp(10f),
                            dividerBottom,
                            row.rowRect.right - host.dp(10f),
                            dividerBottom,
                            dividerPaint
                        )
                    }
                }
            }
            if (layout.listScrollPeekHeight > 0f && layout.scrollOffset < layout.maxScrollOffset - 0.5f) {
                drawListBottomScrollFade(this, layout, theme.cardBackground)
            }
        }

        if (state.closeAllHighlight) {
            drawFooterHighlight(canvas, layout.closeAllRect, rowHighlightPaint, panelCorner)
        }
        val closeAllText = localizedContext().getString(R.string.task_switcher_close_all)
        canvas.drawText(
            closeAllText,
            layout.closeAllRect.centerX(),
            layout.closeAllRect.centerY() - (closeAllPaint.descent() + closeAllPaint.ascent()) / 2f,
            closeAllPaint
        )
        drawContextMenu(canvas, state)
    }

    fun drawContextMenu(canvas: Canvas, state: TaskSwitcherRenderState) {
        val menu = state.contextMenu ?: return
        val theme = OverlayPanelTheme.colors(host.context)
        val progress = state.menuEnterProgress
        if (progress <= 0f) return
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.rowHighlight }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.textPrimary
            textSize = host.sp(14f)
        }
        val corner = host.dp(10f)
        val scale = if (menu.inlineInPanel) 0.94f + 0.06f * progress else 0.88f + 0.12f * progress
        val slideOffset = if (menu.inlineInPanel) {
            0f
        } else {
            when (host.side()) {
                PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> -host.dp(10f) * (1f - progress)
                PanelSide.RIGHT -> host.dp(10f) * (1f - progress)
            }
        }
        val pivotX = if (menu.inlineInPanel) {
            menu.menuRect.centerX()
        } else {
            when (host.side()) {
                PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> menu.menuRect.left
                PanelSide.RIGHT -> menu.menuRect.right
            }
        }
        val pivotY = menu.menuRect.centerY()
        val alpha = (255 * progress).toInt().coerceIn(0, 255)
        canvas.withTranslation(slideOffset, 0f) {
            scale(scale, scale, pivotX, pivotY)
            val layer = saveLayerAlpha(null, alpha)
            drawElevatedRoundRect(this, menu.menuRect, corner, theme.cardBackground)
            menu.items.forEachIndexed { index, item ->
                val rect = menu.itemRects[index]
                if (index == state.menuHighlight) {
                    drawRect(rect, highlightPaint)
                }
                val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                val label = ellipsize(item.label, rect.width() - host.dp(24f), textPaint)
                drawText(label, rect.left + host.dp(16f), baseline, textPaint)
            }
            restoreToCount(layer)
        }
    }

    private fun drawGripDots(canvas: Canvas, x: Float, centerY: Float, paint: Paint) {
        val radius = host.dp(1.65f)
        val gapY = TaskSwitcherLayoutEngine.gripGapY(host)
        val gapX = host.dp(3f)
        for (col in 0..1) {
            for (row in -1..1) {
                canvas.drawCircle(
                    x + col * gapX,
                    centerY + row * gapY,
                    radius,
                    paint
                )
            }
        }
    }

    private fun drawCloseOrLockIcon(canvas: Canvas, rect: RectF, locked: Boolean, paint: Paint) {
        if (locked) drawLockIcon(canvas, rect, paint) else drawCloseIcon(canvas, rect, paint)
    }

    private fun drawCloseIcon(canvas: Canvas, rect: RectF, paint: Paint) {
        val inset = host.dp(9.5f)
        canvas.drawLine(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset, paint)
        canvas.drawLine(rect.right - inset, rect.top + inset, rect.left + inset, rect.bottom - inset, paint)
    }

    private fun drawLockIcon(canvas: Canvas, rect: RectF, paint: Paint) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val bodyHalfWidth = host.dp(5f)
        val bodyHeight = host.dp(6.5f)
        val bodyTop = cy + host.dp(0.5f)
        val bodyBottom = bodyTop + bodyHeight
        val shackleTop = cy - host.dp(5.5f)
        val shackleBottom = bodyTop + host.dp(1f)
        paint.style = Paint.Style.STROKE
        canvas.drawArc(
            cx - bodyHalfWidth,
            shackleTop,
            cx + bodyHalfWidth,
            shackleBottom,
            180f,
            180f,
            false,
            paint
        )
        canvas.drawRoundRect(
            cx - bodyHalfWidth,
            bodyTop,
            cx + bodyHalfWidth,
            bodyBottom,
            host.dp(1.2f),
            host.dp(1.2f),
            paint
        )
    }

    private fun drawListBottomScrollFade(
        canvas: Canvas,
        layout: TaskSwitcherPanelLayout,
        cardBackground: Int,
    ) {
        val fadeHeight = layout.listScrollPeekHeight.coerceAtLeast(1f)
        val bottom = layout.listRect.bottom
        val top = bottom - fadeHeight
        val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                top,
                0f,
                bottom,
                cardBackground and 0x00FFFFFF,
                cardBackground,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(layout.listRect.left, top, layout.listRect.right, bottom, fadePaint)
    }

    private fun drawListHighlight(
        canvas: Canvas,
        rect: RectF,
        paint: Paint,
        layout: TaskSwitcherPanelLayout,
        panelCorner: Float,
        roundTopLeading: Boolean = false,
        roundTopTrailing: Boolean = false
    ) {
        val bounds = RectF()
        if (!bounds.setIntersect(rect, layout.listRect)) return
        val atListTop = layout.scrollOffset <= 0.5f && bounds.top <= layout.listRect.top + 0.5f
        val topLeading = if (atListTop && roundTopLeading) panelCorner else 0f
        val topTrailing = if (atListTop && roundTopTrailing) panelCorner else 0f
        drawRoundedHighlight(canvas, bounds, paint, topLeading, topTrailing, 0f, 0f)
    }

    private fun drawFooterHighlight(
        canvas: Canvas,
        rect: RectF,
        paint: Paint,
        panelCorner: Float
    ) {
        drawRoundedHighlight(
            canvas,
            rect,
            paint,
            topLeading = 0f,
            topTrailing = 0f,
            bottomTrailing = panelCorner,
            bottomLeading = panelCorner
        )
    }

    private fun drawRoundedHighlight(
        canvas: Canvas,
        bounds: RectF,
        paint: Paint,
        topLeading: Float,
        topTrailing: Float,
        bottomTrailing: Float,
        bottomLeading: Float
    ) {
        if (topLeading <= 0f && topTrailing <= 0f && bottomLeading <= 0f && bottomTrailing <= 0f) {
            canvas.drawRect(bounds, paint)
            return
        }
        highlightPath.rewind()
        highlightPath.addRoundRect(
            bounds,
            floatArrayOf(
                topLeading, topLeading,
                topTrailing, topTrailing,
                bottomTrailing, bottomTrailing,
                bottomLeading, bottomLeading
            ),
            Path.Direction.CW
        )
        canvas.drawPath(highlightPath, paint)
    }

    private fun drawElevationShadow(
        canvas: Canvas,
        rect: RectF,
        cornerRadius: Float
    ) {
        val shadowBlur = host.dp(3f)
        val shadowLayers = 3
        val shadowAlpha = 34
        for (layer in shadowLayers downTo 1) {
            val fraction = layer / shadowLayers.toFloat()
            val spread = shadowBlur * fraction * 0.8f
            val alpha = (shadowAlpha * fraction * fraction / shadowLayers).toInt().coerceIn(1, 255)
            elevatedShadowPaint.color = Color.argb(alpha, 0, 0, 0)
            canvas.drawRoundRect(
                rect.left - spread,
                rect.top - spread,
                rect.right + spread,
                rect.bottom + spread,
                cornerRadius + spread * 0.2f,
                cornerRadius + spread * 0.2f,
                elevatedShadowPaint
            )
        }
    }

    private fun drawElevatedRoundRect(
        canvas: Canvas,
        rect: RectF,
        cornerRadius: Float,
        fillColor: Int
    ) {
        drawElevationShadow(canvas, rect, cornerRadius)
        elevatedCardPaint.color = fillColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, elevatedCardPaint)
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "\u2026") > maxWidth) end--
        return text.substring(0, end.coerceAtLeast(1)) + "\u2026"
    }

    private fun drawScaledIcon(canvas: Canvas, app: AppInfo, left: Float, top: Float, size: Float) {
        tmpRect.set(left, top, left + size, top + size)
        canvas.drawBitmap(host.iconFor(app), null, tmpRect, iconBitmapPaint)
    }

    companion object {
        private const val TASK_SWITCHER_OVERSCROLL_STRETCH = 0.22f
    }
}
