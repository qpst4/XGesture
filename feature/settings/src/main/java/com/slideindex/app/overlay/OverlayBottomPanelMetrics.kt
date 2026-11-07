package com.slideindex.app.overlay

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.settings.PickResultPanelPlacement

private val LandscapePanelMaxWidth = 560.dp
private const val LandscapePanelWidthFraction = 0.72f
private const val PortraitPanelMaxHeightFraction = 0.85f
private const val LandscapePanelMaxHeightFraction = 0.92f
/** 屏幕中间样式：竖屏高度上限与宽度上限（避免整屏宽的居中大卡片）。 */
private const val CenteredPortraitPanelMaxHeightFraction = 0.70f
private const val CenteredPortraitPanelWidthFraction = 0.92f

/** 覆盖层底部面板：宽 > 高视为横屏。 */
@Composable
fun overlayIsLandscape(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return size.width > size.height
}

@Composable
fun overlayContainerWidthDp(): Dp {
    val density = LocalDensity.current
    return with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
}

@Composable
fun overlayContainerHeightDp(): Dp {
    val density = LocalDensity.current
    return with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
}

/** 竖屏不限宽（返回 null）；横屏居中限宽。 */
@Composable
fun overlayBottomPanelMaxWidth(): Dp? {
    if (!overlayIsLandscape()) return null
    val screenWidth = overlayContainerWidthDp()
    return minOf(LandscapePanelMaxWidth, screenWidth * LandscapePanelWidthFraction)
}

@Composable
fun overlayBottomPanelMaxHeightFraction(): Float =
    if (overlayIsLandscape()) LandscapePanelMaxHeightFraction else PortraitPanelMaxHeightFraction

@Composable
fun overlayBottomPanelMaxHeight(): Dp =
    overlayContainerHeightDp() * overlayBottomPanelMaxHeightFraction()

@Composable
fun Modifier.overlayBottomPanelWidth(): Modifier {
    val maxWidth = overlayBottomPanelMaxWidth()
    return if (maxWidth != null) {
        // 必须先夹上限再填满：反过来（fillMaxWidth().widthIn）会被已钉死的宽度约束夹回原值，限宽失效。
        widthIn(max = maxWidth).fillMaxWidth()
    } else {
        fillMaxWidth()
    }
}

@Composable
fun Modifier.overlayBottomPanelHeightCap(): Modifier =
    heightIn(max = overlayBottomPanelMaxHeight())

/** 取词面板落位方式对应的高度上限比例。 */
@Composable
fun overlayPickPanelMaxHeightFraction(placement: PickResultPanelPlacement): Float =
    if (placement == PickResultPanelPlacement.CENTER) {
        if (overlayIsLandscape()) LandscapePanelMaxHeightFraction else CenteredPortraitPanelMaxHeightFraction
    } else {
        overlayBottomPanelMaxHeightFraction()
    }

/** 取词面板落位方式对应的最大宽度（null = 不限宽）。 */
@Composable
fun overlayPickPanelMaxWidth(placement: PickResultPanelPlacement): Dp? =
    if (placement == PickResultPanelPlacement.CENTER) {
        minOf(overlayContainerWidthDp() * CenteredPortraitPanelWidthFraction, LandscapePanelMaxWidth)
    } else {
        overlayBottomPanelMaxWidth()
    }

/** 取词面板落位方式对应的宽度约束：贴底铺满（横屏限宽），居中时两侧留白。 */
@Composable
fun Modifier.overlayPickPanelWidth(placement: PickResultPanelPlacement): Modifier {
    val maxWidth = overlayPickPanelMaxWidth(placement)
    return if (maxWidth != null) {
        widthIn(max = maxWidth).fillMaxWidth()
    } else {
        fillMaxWidth()
    }
}
