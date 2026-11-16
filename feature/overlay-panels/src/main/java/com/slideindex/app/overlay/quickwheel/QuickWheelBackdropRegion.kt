package com.slideindex.app.overlay.quickwheel

import com.slideindex.app.overlay.layout.QuickWheelLayout
import com.slideindex.app.overlay.layout.QuickWheelShape
import kotlin.math.hypot

/**
 * 背景（色调）作用区域的几何描述。
 *
 * 与具体绘制 API 解耦，所以真机浮层、配置页预览可以共用同一份计算 —— 两边不会
 * 出现"预览是一大块、真机是另一块"的偏差。单位一律是 px。
 */
sealed interface QuickWheelBackdropRegion {
    /** 圆形形态：以轮盘圆心为中心的一个圆（已含外扩）。 */
    data class Circle(
        val centerX: Float,
        val centerY: Float,
        val radiusPx: Float,
    ) : QuickWheelBackdropRegion

    /** 矩形形态：覆盖全部容器的包围盒（圆角取容器圆角）。 */
    data class RoundedBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val cornerPx: Float,
    ) : QuickWheelBackdropRegion

    /** 没有任何容器：不画。 */
    data object None : QuickWheelBackdropRegion
}

/**
 * 「仅轮盘区域」时区域向外扩的距离（dp）。
 *
 * 外扩一点，色调不会紧贴容器边缘，收边更自然；也避免圆角容器在圆里露出一圈直角。
 */
const val QUICK_WHEEL_BACKDROP_PADDING_DP = 12f

/**
 * 按布局算出背景色调该覆盖的区域。
 *
 * - 圆形：取所有容器（含已展开的二级）到圆心距离的最大值 + 容器半宽，再外扩 [paddingPx]，
 *   所以展开二级时区域会自动变大，不会"遮不到"；
 * - 矩形：所有容器的包围盒（含二级）再外扩。
 */
fun QuickWheelLayout.backdropRegion(
    shape: QuickWheelShape,
    paddingPx: Float,
): QuickWheelBackdropRegion {
    val slots = primarySlots + secondarySlots
    if (slots.isEmpty() && centerSizePx <= 0f) return QuickWheelBackdropRegion.None
    val pad = paddingPx.coerceAtLeast(0f)
    return if (shape == QuickWheelShape.RECT) {
        val left = slots.minOfOrNull { it.centerX - it.sizePx / 2f }
        val top = slots.minOfOrNull { it.centerY - it.sizePx / 2f }
        val right = slots.maxOfOrNull { it.centerX + it.sizePx / 2f }
        val bottom = slots.maxOfOrNull { it.centerY + it.sizePx / 2f }
        if (left == null || top == null || right == null || bottom == null) {
            QuickWheelBackdropRegion.None
        } else {
            QuickWheelBackdropRegion.RoundedBox(
                left = left - pad,
                top = top - pad,
                right = right + pad,
                bottom = bottom + pad,
                cornerPx = (slots.firstOrNull()?.cornerPx ?: 0f) + pad / 2f,
            )
        }
    } else {
        val farEdge = slots.maxOfOrNull {
            hypot(it.centerX - centerX, it.centerY - centerY) + it.sizePx / 2f
        } ?: 0f
        val radius = farEdge.coerceAtLeast(centerSizePx / 2f) + pad
        QuickWheelBackdropRegion.Circle(centerX = centerX, centerY = centerY, radiusPx = radius)
    }
}
