package com.slideindex.app.overlay

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.forLandscapeEditing
import com.slideindex.app.settings.mergeLandscapeEdits
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.settings.withUpdatedTriggerHandle
import com.slideindex.app.settings.withUpdatedTriggerHandleDesign
import com.slideindex.app.settings.withUpdatedTriggerHandleDistances
import com.slideindex.app.settings.withUpdatedTriggerHandleEdgeWidth
import com.slideindex.app.ui.trigger.TriggerSettingsLandscapeSession

fun AppSettings.withOverlayLayoutPreview(): AppSettings {
    val nowMs = android.os.SystemClock.elapsedRealtime()
    val landscapePreview = TriggerSettingsLandscapeSession.active
    var result = if (landscapePreview) forLandscapeEditing() else this
    val preview = OverlayLayoutPreviewStore.activeTriggerHandlePreview(nowMs)
    if (preview != null) {
        val beforePreview = result
        val side = preview.side
        val handleId = preview.handleId
        val edgeWidthDp = preview.edgeWidthDp
        if (edgeWidthDp != null) {
            result = result.withUpdatedTriggerHandleEdgeWidth(side, handleId, edgeWidthDp)
        }
        if (preview.topFraction != null || preview.bottomFraction != null) {
            val handle = result.triggerHandle(side, handleId)
            if (handle != null) {
                val top = preview.topFraction ?: handle.topFraction
                val bottom = preview.bottomFraction ?: handle.bottomFraction
                val height = (bottom - top).coerceAtLeast(0.05f)
                result = result.withUpdatedTriggerHandle(side, handleId, top, height)
            }
        }
        if (preview.shortSwipeDistanceDp != null || preview.longSwipeDistanceDp != null) {
            result = result.withUpdatedTriggerHandleDistances(
                side = side,
                handleId = handleId,
                shortSwipeDistanceDp = preview.shortSwipeDistanceDp,
                longSwipeDistanceDp = preview.longSwipeDistanceDp
            )
        }
        val design = preview.design
        if (design != null) {
            result = result.withUpdatedTriggerHandleDesign(side, handleId, design)
        }
        // 设置已经落盘回流（预览值不再改变任何东西）→ 丢掉"待确认"，恢复正常渲染。
        if (result == beforePreview) {
            OverlayLayoutPreviewStore.dropCommittedTriggerHandle()
        }
    }
    if (landscapePreview) {
        result = mergeLandscapeEdits(result)
    }
    OverlayLayoutPreviewStore.activeIndexHeightFraction(nowMs)?.let { fraction ->
        val beforePreview = result
        result = result.copy(indexHeightFraction = fraction)
        if (result == beforePreview) {
            OverlayLayoutPreviewStore.dropCommittedIndexHeight()
        }
    }
    OverlayLayoutPreviewStore.activeIndexVerticalPositionFraction(nowMs)?.let { fraction ->
        val beforePreview = result
        result = result.copy(indexVerticalPositionFraction = fraction)
        if (result == beforePreview) {
            OverlayLayoutPreviewStore.dropCommittedIndexVerticalPosition()
        }
    }
    return result
}
