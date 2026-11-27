package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withSave
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.primaryTriggerHandle
import com.slideindex.app.settings.triggerHandle

internal object TriggerZonePreviewRenderer {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    fun draw(
        canvas: Canvas,
        side: PanelSide,
        settings: AppSettings,
        zoneLayout: GestureZoneLayout,
        density: Float,
        dp: (Float) -> Float,
        focusHandleId: String? = null,
        showSwipeDistances: Boolean = false
    ) {
        val corner = dp(6f)
        val focused = focusHandleId != null

        if (settings.interceptSystemBackGesture && !focused) {
            val intercept = zoneLayout.interceptZoneRect()
            fillPaint.color = Color.argb(36, 33, 150, 243)
            canvas.drawRoundRect(intercept, corner, corner, fillPaint)
            strokePaint.color = Color.argb(120, 66, 165, 245)
            strokePaint.strokeWidth = dp(1.5f)
            strokePaint.pathEffect = null
            canvas.drawRoundRect(intercept, corner, corner, strokePaint)
        }

        val previewZones = if (focusHandleId != null) {
            settings.triggerHandle(side, focusHandleId)?.let { handle ->
                listOf(handle.id to zoneLayout.triggerZoneRect(handle.id))
            } ?: emptyList()
        } else {
            zoneLayout.triggerZoneRects()
        }
        previewZones.forEach { (handleId, zone) ->
            val handle = settings.triggerHandle(side, handleId) ?: settings.primaryTriggerHandle(side)
            val isFocusedHandle = focusHandleId == null || handleId == focusHandleId

            if (focused && !isFocusedHandle) {
                return@forEach
            }

            drawHandle(canvas, side, handle, zone, density, showBounds = focused)

            if (showSwipeDistances && isFocusedHandle) {
                drawSwipeDistancePreview(canvas, side, zone, handle.shortSwipeDistanceDp, handle.longSwipeDistanceDp, dp)
            }
        }
    }

    private fun drawHandle(
        canvas: Canvas,
        side: PanelSide,
        handle: TriggerHandle,
        zone: RectF,
        density: Float,
        showBounds: Boolean,
    ) {
        val hidden = !handle.design.isVisible
        if (hidden || showBounds) {
            drawZoneWidthPreview(canvas, zone, density, hidden)
        }
        drawHandleDesign(canvas, side, handle.design, zone, density)
    }

    fun drawHandleDesign(
        canvas: Canvas,
        side: PanelSide,
        design: TriggerHandleDesign,
        zone: RectF,
        density: Float,
    ) {
        if (!design.isVisible) return
        val sideEdge = side.isHorizontalEdge
        val width = if (sideEdge) {
            maxOf(zone.width(), design.haloSizeDp * 2f * density).toInt()
        } else {
            zone.width().toInt().coerceAtLeast(1)
        }
        val left = if (side == PanelSide.RIGHT) zone.right - width else zone.left
        canvas.withSave {
            translate(left, zone.top)
            TriggerHandleRenderer.draw(
                canvas = this,
                side = side,
                design = design,
                density = density,
                widthPx = width,
                heightPx = zone.height().toInt().coerceAtLeast(1),
            )
        }
    }

    fun drawLayoutBounds(canvas: Canvas, zone: RectF, density: Float, groupColor: Int? = null) {
        drawZoneWidthPreview(canvas, zone, density, hidden = true, groupColor = groupColor)
    }

    private fun drawZoneWidthPreview(
        canvas: Canvas,
        zone: RectF,
        density: Float,
        hidden: Boolean,
        groupColor: Int? = null,
    ) {
        if (hidden) {
            fillPaint.color = Color.argb(72, 255, 152, 0)
            strokePaint.color = Color.argb(210, 255, 167, 38)
            strokePaint.strokeWidth = 2f * density
        } else {
            fillPaint.color = Color.argb(36, 33, 150, 243)
            strokePaint.color = Color.argb(150, 66, 165, 245)
            strokePaint.strokeWidth = 1.5f * density
        }
        if (groupColor != null) {
            fillPaint.color = ColorUtils.setAlphaComponent(groupColor, fillPaint.alpha)
            strokePaint.color = ColorUtils.setAlphaComponent(groupColor, strokePaint.alpha)
        }
        fillPaint.style = Paint.Style.FILL
        strokePaint.style = Paint.Style.STROKE
        strokePaint.pathEffect = null
        val corner = 6f * density
        canvas.drawRoundRect(zone, corner, corner, fillPaint)
        canvas.drawRoundRect(zone, corner, corner, strokePaint)
    }

    fun drawSwipeDistancePreview(
        canvas: Canvas,
        side: PanelSide,
        zone: RectF,
        shortDistanceDp: Float,
        longDistanceDp: Float,
        dp: (Float) -> Float
    ) {
        val shortR = dp(shortDistanceDp)
        val longR = dp(longDistanceDp)
        if (longR <= shortR) return

        val cx = when (side) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> zone.right
            PanelSide.RIGHT -> zone.left
        }
        val cy = when (side) {
            PanelSide.BOTTOM -> zone.top
            PanelSide.TOP -> zone.bottom
            else -> zone.centerY()
        }
        val startAngle = when (side) {
            PanelSide.LEFT -> -90f
            PanelSide.RIGHT -> 90f
            PanelSide.BOTTOM -> 180f
            PanelSide.TOP -> 0f
        }
        val sweep = 180f

        strokePaint.style = Paint.Style.STROKE
        fillPaint.style = Paint.Style.FILL

        fillPaint.color = Color.argb(28, 186, 104, 200)
        canvas.drawArc(cx - longR, cy - longR, cx + longR, cy + longR, startAngle, sweep, true, fillPaint)
        strokePaint.color = Color.argb(170, 171, 71, 188)
        strokePaint.strokeWidth = dp(2f)
        strokePaint.pathEffect = null
        canvas.drawArc(cx - longR, cy - longR, cx + longR, cy + longR, startAngle, sweep, false, strokePaint)

        fillPaint.color = Color.argb(40, 255, 183, 77)
        canvas.drawArc(cx - shortR, cy - shortR, cx + shortR, cy + shortR, startAngle, sweep, true, fillPaint)
        strokePaint.color = Color.argb(220, 255, 152, 0)
        strokePaint.strokeWidth = dp(2.5f)
        canvas.drawArc(cx - shortR, cy - shortR, cx + shortR, cy + shortR, startAngle, sweep, false, strokePaint)
    }
}
