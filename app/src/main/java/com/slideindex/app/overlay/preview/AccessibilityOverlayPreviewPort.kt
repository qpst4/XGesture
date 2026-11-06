package com.slideindex.app.overlay.preview

import com.slideindex.app.gesture.GestureAngles
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.overlay.LayoutPreviewFocus
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.service.SlideIndexAccessibilityService

/**
 * [OverlayPreviewPort] 的真实实现：转发到无障碍服务的静态入口。
 *
 * 这里保持零逻辑——静态函数内部已经做了"服务没连上就静默丢弃"和进程校验，
 * 这一层只负责把调用从接口形态接到那批静态函数上。
 */
object AccessibilityOverlayPreviewPort : OverlayPreviewPort {

    override fun setPreviewMode(
        enabled: Boolean,
        content: LayoutPreviewContent,
        focus: LayoutPreviewFocus?,
    ) = SlideIndexAccessibilityService.setPreviewMode(enabled, content, focus)

    override fun mergeTriggerHandleLayoutPreview(
        side: PanelSide,
        handleId: String,
        edgeWidthDp: Float?,
        topFraction: Float?,
        bottomFraction: Float?,
        shortSwipeDistanceDp: Float?,
        longSwipeDistanceDp: Float?,
        design: TriggerHandleDesign?,
    ) = SlideIndexAccessibilityService.mergeTriggerHandleLayoutPreview(
        side = side,
        handleId = handleId,
        edgeWidthDp = edgeWidthDp,
        topFraction = topFraction,
        bottomFraction = bottomFraction,
        shortSwipeDistanceDp = shortSwipeDistanceDp,
        longSwipeDistanceDp = longSwipeDistanceDp,
        design = design,
    )

    override fun commitTriggerHandleLayoutPreview() =
        SlideIndexAccessibilityService.commitTriggerHandleLayoutPreview()

    override fun previewIndexHeightFraction(fraction: Float) =
        SlideIndexAccessibilityService.previewIndexHeightFraction(fraction)

    override fun clearIndexHeightPreview() =
        SlideIndexAccessibilityService.clearIndexHeightPreview()

    override fun commitIndexHeightPreview() =
        SlideIndexAccessibilityService.commitIndexHeightPreview()

    override fun previewIndexVerticalPositionFraction(fraction: Float) =
        SlideIndexAccessibilityService.previewIndexVerticalPositionFraction(fraction)

    override fun clearIndexVerticalPositionPreview() =
        SlideIndexAccessibilityService.clearIndexVerticalPositionPreview()

    override fun commitIndexVerticalPositionPreview() =
        SlideIndexAccessibilityService.commitIndexVerticalPositionPreview()

    override fun commitIndexSliderPreviews() =
        SlideIndexAccessibilityService.commitIndexSliderPreviews()

    override fun setCornerZonePreviewActive(active: Boolean) =
        SlideIndexAccessibilityService.setCornerZonePreviewActive(active)

    override fun applyCornerZonePreviewDimensions(
        verticalEdgeWidthDp: Float,
        verticalEdgeHeightDp: Float,
        horizontalEdgeWidthDp: Float,
        horizontalEdgeHeightDp: Float,
    ) = SlideIndexAccessibilityService.applyCornerZonePreviewDimensions(
        verticalEdgeWidthDp = verticalEdgeWidthDp,
        verticalEdgeHeightDp = verticalEdgeHeightDp,
        horizontalEdgeWidthDp = horizontalEdgeWidthDp,
        horizontalEdgeHeightDp = horizontalEdgeHeightDp,
    )

    override fun setGestureAnglesPreview(angles: GestureAngles?) =
        SlideIndexAccessibilityService.setGestureAnglesPreview(angles)

    override fun clearOverlayLayoutPreview() =
        SlideIndexAccessibilityService.clearOverlayLayoutPreview()

    override fun setFloatBallStripZonePreview(active: Boolean) =
        SlideIndexAccessibilityService.setFloatBallStripZonePreview(active)

    override fun previewFloatBallPositionYFraction(fraction: Float) =
        SlideIndexAccessibilityService.previewFloatBallPositionYFraction(fraction)

    override fun endFloatBallPositionYPreview(restoreIfNeeded: Boolean) =
        SlideIndexAccessibilityService.endFloatBallPositionYPreview(restoreIfNeeded)

    override fun clearFloatBallPositionYPreviewRestore() =
        SlideIndexAccessibilityService.clearFloatBallPositionYPreviewRestore()

    override fun previewFloatBallAppearance(
        sizeDp: Float?,
        opacity: Float?,
        visibleFraction: Float?,
        lineHeightFraction: Float?,
        lineWidthDp: Float?,
        lineOpacity: Float?,
    ) = SlideIndexAccessibilityService.previewFloatBallAppearance(
        sizeDp = sizeDp,
        opacity = opacity,
        visibleFraction = visibleFraction,
        lineHeightFraction = lineHeightFraction,
        lineWidthDp = lineWidthDp,
        lineOpacity = lineOpacity,
    )

    override fun endFloatBallAppearancePreview(restoreIfNeeded: Boolean) =
        SlideIndexAccessibilityService.endFloatBallAppearancePreview(restoreIfNeeded)

    override fun clearFloatBallAppearancePreviewRestore() =
        SlideIndexAccessibilityService.clearFloatBallAppearancePreviewRestore()
}
