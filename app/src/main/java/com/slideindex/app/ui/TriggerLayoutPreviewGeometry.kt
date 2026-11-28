package com.slideindex.app.ui

import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.allTriggerHandles
import com.slideindex.app.settings.triggerCollectionEntries
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.settings.triggerHandleEdgeWidthDp

internal data class TriggerLayoutPreviewDraft(
    val side: PanelSide,
    val handleId: String,
    val widthDp: Float?,
    val span: ClosedFloatingPointRange<Float>?,
)

internal data class TriggerLayoutPreviewBand(
    val side: PanelSide,
    val handleId: String,
    val colorIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val configurationNumber: Int get() = colorIndex + 1
}

internal fun triggerLayoutPreviewBands(
    settings: AppSettings,
    width: Int,
    height: Int,
    density: Float,
    draft: TriggerLayoutPreviewDraft? = null,
): List<TriggerLayoutPreviewBand> = buildList {
    if (width <= 0 || height <= 0) return@buildList
    val pairIndices = settings.triggerCollectionEntries().mapIndexed { index, entry -> entry.handleId to index }.toMap()
    val aligned = draft != null && settings.triggerHandle(draft.side, draft.handleId)?.alignOppositeSide == true
    for (side in PanelSide.entries) {
        val sideEnabled = when (side) {
            PanelSide.LEFT -> settings.leftEdgeEnabled
            PanelSide.RIGHT -> settings.rightEdgeEnabled
            else -> true
        }
        if (!sideEnabled) continue
        settings.allTriggerHandles(side).forEachIndexed { index, handle ->
            if (!handle.enabled) return@forEachIndexed
            val edit = draft?.takeIf {
                it.handleId == handle.id &&
                    (it.side == side || aligned && side.isHorizontalEdge && it.side.isHorizontalEdge)
            }
            val bounds = triggerPreviewBounds(
                side, width, height, density,
                edit?.widthDp ?: settings.triggerHandleEdgeWidthDp(side, handle.id),
                edit?.span?.start ?: handle.topFraction,
                edit?.span?.endInclusive ?: handle.bottomFraction,
            ) ?: return@forEachIndexed
            add(
                TriggerLayoutPreviewBand(
                    side = side,
                    handleId = handle.id,
                    colorIndex = if (side.isHorizontalEdge) pairIndices.getValue(handle.id) else index,
                    left = bounds.left,
                    top = bounds.top,
                    right = bounds.right,
                    bottom = bounds.bottom,
                ),
            )
        }
    }
}
