package com.slideindex.app.overlay.floatnav

import com.slideindex.app.overlay.PanelSide
import kotlin.math.roundToInt

/** 浮条上的可命中区域。Grip 只用于拖动，Close 用于关闭，其余三个执行导航。 */
internal enum class FloatNavKey {
    GRIP,
    BACK,
    HOME,
    RECENTS,
    CLOSE,
    NONE,
}

internal data class FloatNavPosition(val x: Int, val y: Int)

internal data class FloatNavKeyBounds(
    val key: FloatNavKey,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * 浮条的纯几何：窗口贴边位置与按键命中。
 *
 * 不依赖 Android View，方便 JVM 单测；真正的按键矩形由 [FloatNavView] 按子 View 实际布局生成。
 */
internal object FloatNavGeometry {

    /**
     * 计算窗口左上角。
     *
     * - 左右边缘：浮条贴在该侧、竖直居中于触发点；
     * - 上下边缘：浮条贴在底/顶、水平居中于触发点；
     * - `edgeInsetPx` 同时作为左右安全边距，保证浮条不压住边缘触钮条。
     */
    fun computePosition(
        screenWidthPx: Int,
        screenHeightPx: Int,
        barWidthPx: Int,
        barHeightPx: Int,
        side: PanelSide,
        anchorX: Float,
        anchorY: Float,
        edgeInsetPx: Int,
        topInsetPx: Int,
        bottomInsetPx: Int,
    ): FloatNavPosition {
        val inset = edgeInsetPx.coerceAtLeast(0)
        val minX = inset
        val maxX = (screenWidthPx - barWidthPx - inset).coerceAtLeast(minX)
        val minY = topInsetPx.coerceAtLeast(0)
        val maxY = (screenHeightPx - barHeightPx - bottomInsetPx).coerceAtLeast(minY)
        return when (side) {
            PanelSide.LEFT -> FloatNavPosition(
                x = minX,
                y = (anchorY - barHeightPx / 2f).roundToInt().coerceIn(minY, maxY),
            )
            PanelSide.RIGHT -> FloatNavPosition(
                x = maxX,
                y = (anchorY - barHeightPx / 2f).roundToInt().coerceIn(minY, maxY),
            )
            PanelSide.BOTTOM -> FloatNavPosition(
                x = (anchorX - barWidthPx / 2f).roundToInt().coerceIn(minX, maxX),
                y = maxY,
            )
            PanelSide.TOP -> FloatNavPosition(
                x = (anchorX - barWidthPx / 2f).roundToInt().coerceIn(minX, maxX),
                y = minY,
            )
        }
    }

    /** 命中按键；[slopPx] 给手指一点容差，避免正好落在边界时选不中。 */
    fun hitTest(
        bounds: List<FloatNavKeyBounds>,
        rawX: Float,
        rawY: Float,
        slopPx: Float = 0f,
    ): FloatNavKey {
        val slop = slopPx.coerceAtLeast(0f)
        return bounds.firstOrNull { b ->
            rawX >= b.left - slop && rawX <= b.right + slop &&
                rawY >= b.top - slop && rawY <= b.bottom + slop
        }?.key ?: FloatNavKey.NONE
    }
}
