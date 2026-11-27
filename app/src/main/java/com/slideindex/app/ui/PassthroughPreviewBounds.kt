package com.slideindex.app.ui

import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.settings.triggerHandleEdgeWidthDp

internal fun focusedSideTriggerBounds(
    settings: AppSettings,
    side: PanelSide,
    handleId: String,
    width: Int,
    height: Int,
    density: Float,
    draftWidth: Float? = null,
    draftSpan: ClosedFloatingPointRange<Float>? = null,
): TriggerPreviewBounds? {
    val handle = settings.triggerHandle(side, handleId) ?: return null
    return triggerPreviewBounds(
        side, width, height, density,
        draftWidth ?: settings.triggerHandleEdgeWidthDp(side, handleId),
        draftSpan?.start ?: handle.topFraction,
        draftSpan?.endInclusive ?: handle.bottomFraction,
    )
}
