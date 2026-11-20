package com.slideindex.app.settings

import android.content.Context
import android.content.res.Configuration

/**
 * 某一个方向（竖屏 / 横屏）下的小窗尺寸与位置，四个值都是相对屏幕的百分比。
 *
 * 竖屏与横屏各存一套，运行时按当前显示方向二选一，互不影响。
 */
data class FreeWindowLayoutFractions(
    val widthFraction: Float,
    val heightFraction: Float,
    val leftFraction: Float,
    val topFraction: Float,
)

/** 小窗比例参数的合法区间：设置写入与预览拖拽共用同一套约束。 */
object FreeWindowLayoutLimits {
    const val MIN_WIDTH_FRACTION = 0.35f
    const val MAX_WIDTH_FRACTION = 0.95f
    const val MIN_HEIGHT_FRACTION = 0.35f
    const val MAX_HEIGHT_FRACTION = 0.9f
    const val MIN_LEFT_FRACTION = 0f
    const val MAX_LEFT_FRACTION = 0.65f
    const val MIN_TOP_FRACTION = 0f
    const val MAX_TOP_FRACTION = 0.65f

    fun normalizeWidth(fraction: Float): Float =
        fraction.coerceIn(MIN_WIDTH_FRACTION, MAX_WIDTH_FRACTION)

    fun normalizeHeight(fraction: Float): Float =
        fraction.coerceIn(MIN_HEIGHT_FRACTION, MAX_HEIGHT_FRACTION)

    fun normalizeLeft(fraction: Float): Float =
        fraction.coerceIn(MIN_LEFT_FRACTION, MAX_LEFT_FRACTION)

    fun normalizeTop(fraction: Float): Float =
        fraction.coerceIn(MIN_TOP_FRACTION, MAX_TOP_FRACTION)
}

/** 当前显示方向是否为横屏；小窗预置按此二选一。 */
fun Context.isLandscapeConfiguration(): Boolean =
    resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

/** 按方向取出该方向专属的那套预置参数。 */
fun FreeWindowSettings.layoutFractions(isLandscape: Boolean): FreeWindowLayoutFractions =
    if (isLandscape) {
        FreeWindowLayoutFractions(
            widthFraction = freeWindowLandWidthFraction,
            heightFraction = freeWindowLandHeightFraction,
            leftFraction = freeWindowLandLeftFraction,
            topFraction = freeWindowLandTopFraction,
        )
    } else {
        FreeWindowLayoutFractions(
            widthFraction = freeWindowWidthFraction,
            heightFraction = freeWindowHeightFraction,
            leftFraction = freeWindowLeftFraction,
            topFraction = freeWindowTopFraction,
        )
    }

/** 当前显示方向下实际生效的小窗参数。 */
fun AppSettings.resolvedFreeWindowLayout(isLandscape: Boolean): FreeWindowLayoutFractions =
    freeWindow.layoutFractions(isLandscape)

/**
 * 某一个方向（竖屏 / 横屏）下的小窗物理尺寸与边距（以 dp 为单位，直接对齐 GestureEVO 架构）。
 */
data class FreeWindowLayoutDp(
    val widthDp: Int,
    val heightDp: Int,
    val leftDp: Int,
    val topDp: Int,
    val edgeMarginDp: Int,
)

/**
 * 按方向取出该方向专属的那套物理 dp 预置参数。
 */
fun FreeWindowSettings.layoutDp(isLandscape: Boolean): FreeWindowLayoutDp =
    if (isLandscape) {
        FreeWindowLayoutDp(
            widthDp = freeWindowLandWidthDp,
            heightDp = freeWindowLandHeightDp,
            leftDp = freeWindowLandLeftDp,
            topDp = freeWindowLandTopDp,
            edgeMarginDp = freeWindowLandEdgeMarginDp,
        )
    } else {
        FreeWindowLayoutDp(
            widthDp = freeWindowWidthDp,
            heightDp = freeWindowHeightDp,
            leftDp = freeWindowLeftDp,
            topDp = freeWindowTopDp,
            edgeMarginDp = freeWindowEdgeMarginDp,
        )
    }

/** 当前显示方向下实际生效的物理 dp 参数。 */
fun AppSettings.resolvedFreeWindowLayoutDp(isLandscape: Boolean): FreeWindowLayoutDp =
    freeWindow.layoutDp(isLandscape)
