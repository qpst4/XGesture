package com.slideindex.app.overlay.preview

import com.slideindex.app.gesture.GestureAngles
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.overlay.LayoutPreviewFocus
import com.slideindex.app.overlay.PanelSide

/**
 * 设置页"实时预览"与浮层宿主之间的最小接口。
 *
 * 背景：这些操作以前是 `SlideIndexAccessibilityService` 伴生对象上的 20 多个静态函数，
 * 设置层直接点静态入口 → 想给预览逻辑写单测必须真的有 AccessibilityService 实例。
 * 抽成接口后，调用方（[com.slideindex.app.service.OverlayServiceController]）只依赖这层，
 * 测试里换一个假实现即可。
 *
 * 命名刻意与原来的静态函数逐字一致，方便 `SlideIndexAccessibilityService.` 整体替换；
 * 真实实现见 [AccessibilityOverlayPreviewPort]。
 *
 * 后续 `:feature:overlay-panels` 落地时，面板也会通过这个口子拿预览能力，
 * 而不是再回头点 AccessibilityService 的静态成员。
 */
interface OverlayPreviewPort {

    // —— 布局预览（触钮 / 索引高度 / 手势角度） ——

    fun setPreviewMode(
        enabled: Boolean,
        content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
        focus: LayoutPreviewFocus? = null,
    )

    fun mergeTriggerHandleLayoutPreview(
        side: PanelSide,
        handleId: String,
        edgeWidthDp: Float? = null,
        topFraction: Float? = null,
        bottomFraction: Float? = null,
        shortSwipeDistanceDp: Float? = null,
        longSwipeDistanceDp: Float? = null,
        design: TriggerHandleDesign? = null,
    )

    fun commitTriggerHandleLayoutPreview()

    fun previewIndexHeightFraction(fraction: Float)

    fun clearIndexHeightPreview()

    fun commitIndexHeightPreview()

    fun previewIndexVerticalPositionFraction(fraction: Float)

    fun clearIndexVerticalPositionPreview()

    fun commitIndexVerticalPositionPreview()

    fun commitIndexSliderPreviews()

    fun setCornerZonePreviewActive(active: Boolean)

    fun applyCornerZonePreviewDimensions(
        verticalEdgeWidthDp: Float,
        verticalEdgeHeightDp: Float,
        horizontalEdgeWidthDp: Float,
        horizontalEdgeHeightDp: Float,
    )

    fun setGestureAnglesPreview(angles: GestureAngles?)

    fun clearOverlayLayoutPreview()

    // —— 悬浮球（触钮条 / 位置 / 外观） ——

    fun setFloatBallStripZonePreview(active: Boolean)

    fun previewFloatBallPositionYFraction(fraction: Float)

    fun endFloatBallPositionYPreview(restoreIfNeeded: Boolean)

    fun clearFloatBallPositionYPreviewRestore()

    fun previewFloatBallAppearance(
        sizeDp: Float? = null,
        opacity: Float? = null,
        visibleFraction: Float? = null,
        lineHeightFraction: Float? = null,
        lineWidthDp: Float? = null,
        lineOpacity: Float? = null,
    )

    fun endFloatBallAppearancePreview(restoreIfNeeded: Boolean)

    fun clearFloatBallAppearancePreviewRestore()
}
