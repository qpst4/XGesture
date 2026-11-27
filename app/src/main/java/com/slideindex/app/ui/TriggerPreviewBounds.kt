package com.slideindex.app.ui

import com.slideindex.app.overlay.PanelSide

internal data class TriggerPreviewBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

internal fun triggerPreviewBounds(
    side: PanelSide,
    width: Int,
    height: Int,
    density: Float,
    widthDp: Float,
    startFraction: Float,
    endFraction: Float,
): TriggerPreviewBounds? {
    if (width <= 0 || height <= 0) return null
    val extent = if (side.isHorizontalEdge) height.toFloat() else width.toFloat()
    val depth = if (side.isHorizontalEdge) width.toFloat() else height.toFloat()
    val start = startFraction.coerceIn(0f, 1f) * extent
    val end = endFraction.coerceIn(0f, 1f) * extent
    val thickness = (widthDp * density).coerceIn(0f, depth)
    if (end <= start || thickness <= 0f) return null
    return when (side) {
        PanelSide.LEFT -> TriggerPreviewBounds(0f, start, thickness, end)
        PanelSide.RIGHT -> TriggerPreviewBounds(depth - thickness, start, depth, end)
        PanelSide.TOP -> TriggerPreviewBounds(start, 0f, end, thickness)
        PanelSide.BOTTOM -> TriggerPreviewBounds(start, depth - thickness, end, depth)
    }
}
