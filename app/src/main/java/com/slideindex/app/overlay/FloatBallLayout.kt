package com.slideindex.app.overlay

import android.graphics.Rect
import android.util.DisplayMetrics
import com.slideindex.app.gesture.KeyboardTriggerBoundsAdjuster
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionFractions
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSettings
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.settings.FloatBallStyleType
import com.slideindex.app.settings.KeyboardTriggerBehavior
import com.slideindex.app.settings.keyboardTriggerBehavior
import com.slideindex.app.settings.keyboardTriggerNarrowScale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 悬浮球窗口尺寸（px）。
 *
 * 绝大多数样式是**正方形**（宽=高=设置里的球大小）；只有「自定义图片 + 不处理」会按原图宽高比
 * 把宽撑开（高恒等于设置值）。所有布局/命中/拖动公式都必须区分横竖两个方向，不能再当成一个
 * 直径用 —— 以前到处写的 `ballSizePx / 2f` 在非方形球上会算错半个球。
 */
internal data class FloatBallSize(val widthPx: Int, val heightPx: Int) {
    /** 短边：圆形兜底视觉、取词十字偏移等「球体本身有多粗」的场合用它。 */
    val minSidePx: Int get() = minOf(widthPx, heightPx)
}

internal object FloatBallLayout {
    private const val EDGE_MARGIN_DP = 8f
    private const val MIN_STRIP_HEIGHT_DP = 48f
    private const val MIN_POSITION_Y_FRACTION = FloatBallPositionFractions.MIN_Y
    private const val MAX_POSITION_Y_FRACTION = FloatBallPositionFractions.MAX_Y

    fun coerceCustomCenterXFraction(fraction: Float): Float =
        FloatBallPositionFractions.coerceCustomCenterX(fraction)

    fun coercePositionYFraction(fraction: Float): Float =
        FloatBallPositionFractions.coerceY(fraction)

    fun resolvedActiveSide(settings: AppSettings): FloatBallSide =
        when (settings.floatBallPositionMode) {
            FloatBallPositionMode.LEFT -> FloatBallSide.LEFT
            FloatBallPositionMode.RIGHT -> FloatBallSide.RIGHT
            FloatBallPositionMode.BOTH_EDGES,
            FloatBallPositionMode.BOTH_LINES -> settings.floatBallActiveSide
            FloatBallPositionMode.CUSTOM -> {
                if (settings.floatBallCustomCenterXFraction < 0.5f) FloatBallSide.LEFT else FloatBallSide.RIGHT
            }
        }

    /** 两侧都用边缘线呈现的模式（球不再常驻，只有拖动时浮现）。 */
    fun isBothLines(settings: AppSettings): Boolean =
        settings.floatBallPositionMode == FloatBallPositionMode.BOTH_LINES

    /** 需要显示对侧边缘线的模式（双贴边 / 两侧都是线）。 */
    fun usesEdgeLines(settings: AppSettings): Boolean =
        settings.floatBallPositionMode == FloatBallPositionMode.BOTH_EDGES ||
            isBothLines(settings)

    fun panelSideFor(settings: AppSettings): PanelSide = panelSideFor(FloatBallLayout.resolvedActiveSide(settings))

    /** 双贴边模式下，线条在悬浮球对侧，面板应从线条所在侧弹出。 */
    fun panelSideForLineStrip(settings: AppSettings): PanelSide =
        panelSideFor(FloatBallSide.opposite(resolvedActiveSide(settings)))

    private fun panelSideFor(side: FloatBallSide): PanelSide = when (side) {
        FloatBallSide.LEFT -> PanelSide.LEFT
        FloatBallSide.RIGHT -> PanelSide.RIGHT
    }

    fun shouldShowLine(settings: AppSettings): Boolean = usesEdgeLines(settings)

    fun dockCenterY(settings: AppSettings, screenHeight: Int): Float =
        coercePositionYFraction(settings.floatBallPositionYFraction) * screenHeight

    /**
     * 是否「按原图比例显示」：自定义图片 + 「不处理」开关 + 有图片。
     *
     * 宽高比未知（0，通常是老数据还没迁移到本地副本）时仍然算「不处理」：窗口退回正方形，
     * 但渲染改为完整显示（不裁剪、不套圆），保证开关一打开就不会再切图。
     */
    fun usesOriginalImageGeometry(settings: AppSettings): Boolean =
        settings.floatBallStyleType == FloatBallStyleType.CUSTOM_IMAGE &&
            settings.floatBallCustomImageNoCrop &&
            settings.floatBallCustomImageUri.isNotBlank()

    /**
     * 球窗口尺寸。
     *
     * - 方形样式：[FloatBallSize.widthPx] == [FloatBallSize.heightPx]，等于 `floatBallSizeDp` 换算值。
     * - 不处理模式：**高度** = 该换算值，宽度 = 高度 × 原图宽高比（用户要求不限宽，故不做屏宽夹取）。
     */
    fun ballSize(settings: AppSettings, density: Float): FloatBallSize {
        val heightPx = (settings.floatBallSizeDp.coerceIn(36f, 96f) * density)
            .roundToInt()
            .coerceAtLeast(1)
        if (!usesOriginalImageGeometry(settings)) return FloatBallSize(heightPx, heightPx)
        val aspect = settings.floatBallCustomImageAspect
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(FloatBallSettings.IMAGE_ASPECT_MIN, FloatBallSettings.IMAGE_ASPECT_MAX)
            ?: return FloatBallSize(heightPx, heightPx)
        val widthPx = (heightPx * aspect).roundToInt().coerceAtLeast(1)
        return FloatBallSize(widthPx, heightPx)
    }

    fun marginPx(density: Float): Int = (EDGE_MARGIN_DP * density).roundToInt()

    /**
     * 触发带宽度（px）。
     *
     * 由设置里的 dp 值换算，**不再乘屏宽**：横屏 / 折叠展开时窗口变宽，触发带保持同一物理宽度。
     * 0dp 表示该侧线条只显示、不接收触摸，因此这里允许返回 0（窗口层另有 1px 兜底）。
     */
    fun lineTriggerWidthPx(settings: AppSettings, density: Float): Int {
        val dpWidth = settings.floatBallLineWidthDp.coerceIn(
            FloatBallSettings.MIN_LINE_WIDTH_DP,
            FloatBallSettings.MAX_LINE_WIDTH_DP,
        )
        return (dpWidth * density).roundToInt().coerceAtLeast(0)
    }

    fun lineTriggerHeightPx(settings: AppSettings, screenHeight: Int, density: Float): Int {
        val fractionHeight = (screenHeight * settings.floatBallLineHeightFraction.coerceIn(0.04f, 0.4f)).roundToInt()
        val minHeight = (MIN_STRIP_HEIGHT_DP * density).roundToInt()
        return max(fractionHeight, minHeight)
    }

    fun ballTopLeft(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels
    ): Pair<Int, Int> {
        val density = metrics.density
        val ballSize = ballSize(settings, density)
        val marginPx = marginPx(density)
        val centerY = dockCenterY(settings, screenHeightPx)
        val left = when (settings.floatBallPositionMode) {
            FloatBallPositionMode.CUSTOM -> {
                val centerX = coerceCustomCenterXFraction(settings.floatBallCustomCenterXFraction) * screenWidthPx
                (centerX - ballSize.widthPx / 2f).roundToInt()
            }
            else -> dockedBallLeftPx(
                activeSide = activeSide,
                ballWidthPx = ballSize.widthPx,
                screenWidth = screenWidthPx,
                edgeOffsetPx = edgeInsetPx(settings, density),
            )
        }
        val top = (centerY - ballSize.heightPx / 2f).roundToInt()
            .coerceIn(marginPx, (screenHeightPx - ballSize.heightPx - marginPx).coerceAtLeast(marginPx))
        return left to top
    }

    fun ballCenterPx(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels
    ): Pair<Float, Float> {
        val ballSize = ballSize(settings, metrics.density)
        val (left, top) = ballTopLeft(settings, metrics, activeSide, screenWidthPx, screenHeightPx)
        return (left + ballSize.widthPx / 2f) to (top + ballSize.heightPx / 2f)
    }

    fun ballWindowBounds(
        settings: AppSettings,
        metrics: DisplayMetrics,
        side: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels
    ): Rect {
        val ballSize = ballSize(settings, metrics.density)
        val (left, top) = ballTopLeft(settings, metrics, side, screenWidthPx, screenHeightPx)
        return Rect(left, top, left + ballSize.widthPx, top + ballSize.heightPx)
    }

    fun lineStripBounds(
        settings: AppSettings,
        metrics: DisplayMetrics,
        side: FloatBallSide,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels
    ): Rect {
        val density = metrics.density
        val width = lineTriggerWidthPx(settings, density)
        val height = lineTriggerHeightPx(settings, screenHeightPx, density)
        val centerY = dockCenterY(settings, screenHeightPx).roundToInt()
        val top = (centerY - height / 2).coerceIn(0, screenHeightPx - height)
        val left = when (side) {
            FloatBallSide.LEFT -> 0
            FloatBallSide.RIGHT -> screenWidthPx - width
        }
        return Rect(left, top, left + width, top + height)
    }

    /** After dragging out from the inactive-side line, the ball docks on that side. */
    fun activeSideAfterLineDragSwap(settings: AppSettings): FloatBallSide =
        FloatBallSide.opposite(resolvedActiveSide(settings))

    /** Ball window origin so the docked ball visual tracks [ballCenterX]/[ballCenterY]. */
    fun stripWindowOriginForBallCenter(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        ballCenterX: Float,
        ballCenterY: Float,
        screenHeightPx: Int = metrics.heightPixels
    ): Pair<Int, Int> {
        val ballSize = ballSize(settings, metrics.density)
        val windowX = (ballCenterX - ballSize.widthPx / 2f).roundToInt()
        val windowY = (ballCenterY - ballSize.heightPx / 2f).roundToInt()
        val maxY = (screenHeightPx - ballSize.heightPx).coerceAtLeast(0)
        return windowX to windowY.coerceIn(0, maxY)
    }

    /**
     * 贴边时球窗口的 X。
     *
     * [edgeOffsetPx] 是"距边缘距离"：0 = 贴边；正数往屏幕内挪；负数往屏幕外挪
     * （球被屏幕边缘裁掉一部分，看起来变小，实际尺寸不变）。
     */
    fun dockedBallLeftPx(
        activeSide: FloatBallSide,
        ballWidthPx: Int,
        screenWidth: Int,
        edgeOffsetPx: Int,
    ): Int = when (activeSide) {
        FloatBallSide.LEFT -> edgeOffsetPx
        FloatBallSide.RIGHT -> screenWidth - ballWidthPx - edgeOffsetPx
    }

    /** 设置里的"距边缘距离"换算成 px（已夹取 −48…48dp）。 */
    fun edgeInsetPx(settings: AppSettings, density: Float): Int =
        (FloatBallPositionFractions.coerceEdgeInsetDp(settings.floatBallEdgeInsetDp) * density)
            .roundToInt()

    /**
     * 键盘「收窄」时把球往外再挪一段：等价旧版"露出比例 × 收窄系数"。
     *
     * 旧模型是露出比例变小；新模型是距离往外偏 `(1 - 收窄系数) × 球宽`。
     */
    fun keyboardAdjustedEdgeOffsetPx(
        settings: AppSettings,
        isLandscape: Boolean,
        ballWidthPx: Int,
        density: Float,
    ): Int {
        val base = edgeInsetPx(settings, density)
        val behavior = settings.keyboardTriggerBehavior(isLandscape)
        val imeTop = KeyboardTriggerImeState.imeTopOrNull()
        if (imeTop == null || behavior != KeyboardTriggerBehavior.NARROW) {
            return base
        }
        val narrowedPx = ((1f - settings.keyboardTriggerNarrowScale(isLandscape)) * ballWidthPx)
            .roundToInt()
        return base - narrowedPx
    }

    fun keyboardAdjustedLineStripBounds(
        settings: AppSettings,
        metrics: DisplayMetrics,
        side: FloatBallSide,
        isLandscape: Boolean,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels,
    ): Rect {
        val base = lineStripBounds(settings, metrics, side, screenWidthPx, screenHeightPx)
        return KeyboardTriggerBoundsAdjuster.adjustRect(
            rect = base,
            behavior = settings.keyboardTriggerBehavior(isLandscape),
            imeTop = KeyboardTriggerImeState.imeTopOrNull(),
            density = metrics.density,
            narrowScale = settings.keyboardTriggerNarrowScale(isLandscape),
            narrowFromRightEdge = side == FloatBallSide.RIGHT,
        )
    }

    fun keyboardAdjustedBallTopLeft(
        settings: AppSettings,
        metrics: DisplayMetrics,
        activeSide: FloatBallSide,
        isLandscape: Boolean,
        screenWidthPx: Int = metrics.widthPixels,
        screenHeightPx: Int = metrics.heightPixels,
    ): Pair<Int, Int> {
        val density = metrics.density
        val ballSize = ballSize(settings, density)
        val marginPx = marginPx(density)
        val centerY = dockCenterY(settings, screenHeightPx)
        val edgeOffsetPx = keyboardAdjustedEdgeOffsetPx(
            settings = settings,
            isLandscape = isLandscape,
            ballWidthPx = ballSize.widthPx,
            density = density,
        )
        val left = when (settings.floatBallPositionMode) {
            FloatBallPositionMode.CUSTOM -> {
                val centerX = coerceCustomCenterXFraction(settings.floatBallCustomCenterXFraction) * screenWidthPx
                (centerX - ballSize.widthPx / 2f).roundToInt()
            }
            else -> dockedBallLeftPx(
                activeSide = activeSide,
                ballWidthPx = ballSize.widthPx,
                screenWidth = screenWidthPx,
                edgeOffsetPx = edgeOffsetPx,
            )
        }
        var top = (centerY - ballSize.heightPx / 2f).roundToInt()
            .coerceIn(marginPx, (screenHeightPx - ballSize.heightPx - marginPx).coerceAtLeast(marginPx))
        val behavior = settings.keyboardTriggerBehavior(isLandscape)
        val imeTop = KeyboardTriggerImeState.imeTopOrNull()
        if (imeTop != null && behavior != KeyboardTriggerBehavior.OVERLAY) {
            val ballRect = Rect(left, top, left + ballSize.widthPx, top + ballSize.heightPx)
            val adjusted = KeyboardTriggerBoundsAdjuster.adjustRect(
                rect = ballRect,
                behavior = behavior,
                imeTop = imeTop,
                density = density,
                narrowScale = settings.keyboardTriggerNarrowScale(isLandscape),
            )
            top = adjusted.top
        }
        return left to top
    }

    fun isKeyboardTriggerDisabled(settings: AppSettings, isLandscape: Boolean): Boolean {
        val imeTop = KeyboardTriggerImeState.imeTopOrNull() ?: return false
        return settings.keyboardTriggerBehavior(isLandscape) == KeyboardTriggerBehavior.DISABLE &&
            imeTop > 0
    }
}
