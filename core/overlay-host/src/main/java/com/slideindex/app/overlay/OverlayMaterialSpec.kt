package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.Flow

/**
 * 全局悬浮窗材质基准规范。
 *
 * 统一所有悬浮面板（收纳夹、取词、快速启动器、快捷轮盘、应用索引、任务切换器、圆环启动器等）
 * 的毛玻璃模糊半径、面板不透明度、卡片大圆角与底色。
 */
@Immutable
data class OverlayMaterialSpec(
    val blurEnabled: Boolean = true,
    val blurRadiusDp: Int = 40,
    val opacityPercent: Int = 30,
    val cornerRadiusDp: Int = 18,
    val borderShimmerPercent: Int = 0,
    val advancedShimmerEnabled: Boolean = true,
    val borderHighlightPercent: Int = 100,
    val radialGlossPercent: Int = 30,
    val verticalGlossPercent: Int = 0,
    val backdropBlurEnabled: Boolean = true,
    val backdropBlurRadiusDp: Int = 80,
    val backdropDimPercent: Int = 15,
    val searchPanelBlurModeId: Int = 1,
    val expandPanelBlurModeId: Int = 1,
    val pickResultBlurModeId: Int = 0,
    val widgetPanelBlurModeId: Int = 1,
) {
    val isSearchPanelCardBlurEnabled: Boolean
        get() = blurEnabled && (searchPanelBlurModeId == 2 || searchPanelBlurModeId == 3)

    val isSearchPanelBackdropBlurEnabled: Boolean
        get() = backdropBlurEnabled && (searchPanelBlurModeId == 1 || searchPanelBlurModeId == 3)

    val isExpandPanelCardBlurEnabled: Boolean
        get() = blurEnabled && (expandPanelBlurModeId == 2 || expandPanelBlurModeId == 3)

    val isExpandPanelBackdropBlurEnabled: Boolean
        get() = backdropBlurEnabled && (expandPanelBlurModeId == 1 || expandPanelBlurModeId == 3)

    val isPickResultCardBlurEnabled: Boolean
        get() = blurEnabled && (pickResultBlurModeId == 2 || pickResultBlurModeId == 3)

    val isPickResultBackdropBlurEnabled: Boolean
        get() = backdropBlurEnabled && (pickResultBlurModeId == 1 || pickResultBlurModeId == 3)

    val isWidgetPanelCardBlurEnabled: Boolean
        get() = blurEnabled && (widgetPanelBlurModeId == 2 || widgetPanelBlurModeId == 3)

    val isWidgetPanelBackdropBlurEnabled: Boolean
        get() = backdropBlurEnabled && (widgetPanelBlurModeId == 1 || widgetPanelBlurModeId == 3)
    fun blurRadiusPx(density: Float): Int =
        if (blurEnabled) (blurRadiusDp * density).toInt().coerceIn(0, 120) else 0

    fun backdropBlurRadiusPx(density: Float): Int =
        if (backdropBlurEnabled) (backdropBlurRadiusDp * density).toInt().coerceIn(0, 160) else 0

    @ColorInt
    fun backdropDimColor(alphaScale: Float = 1f): Int {
        val alpha = (backdropDimPercent.coerceIn(0, 100) * 255 / 100 * alphaScale).toInt().coerceIn(0, 255)
        return Color.argb(alpha, 0, 0, 0)
    }

    fun cornerRadiusPx(density: Float): Float =
        (cornerRadiusDp * density).coerceAtLeast(0f)

    @ColorInt
    fun tintColor(isDark: Boolean): Int {
        val alpha = (opacityPercent.coerceIn(0, 100) * 255 / 100)
        return if (isDark) {
            Color.argb(alpha, 28, 28, 30)
        } else {
            Color.argb(alpha, 245, 245, 247)
        }
    }

    @ColorInt
    fun solidFallbackColor(isDark: Boolean): Int {
        return if (isDark) {
            Color.argb(245, 28, 28, 30)
        } else {
            Color.argb(250, 245, 245, 247)
        }
    }

    /**
     * 外缘亮白细边颜色（0.75dp 描边）。
     * 未开启高级微调时由主滑块 borderShimmerPercent 驱动；
     * 开启后完全由独立滑块 borderHighlightPercent 绝对控制，两套互斥不连乘。
     */
    @ColorInt
    fun innerBorderColor(isDark: Boolean): Int {
        val effectiveFactor = if (advancedShimmerEnabled) {
            borderHighlightPercent.coerceIn(0, 100) / 50f
        } else {
            borderShimmerPercent.coerceIn(0, 100) / 50f
        }
        if (effectiveFactor <= 0.001f) return Color.TRANSPARENT
        return if (isDark) {
            val alpha = (32 * effectiveFactor).toInt().coerceIn(0, 255)
            Color.argb(alpha, 255, 255, 255)
        } else {
            val alpha = (160 * effectiveFactor).toInt().coerceIn(0, 255)
            Color.argb(alpha, 255, 255, 255)
        }
    }

    /**
     * 左上角定向聚光强度（0f ~ 1f）。
     * 未开启高级微调时由主滑块 borderShimmerPercent 驱动；
     * 开启后完全由独立滑块 radialGlossPercent 绝对控制，两套互斥不连乘。
     */
    fun radialGlossAlpha(isDark: Boolean): Float {
        val effectiveFactor = if (advancedShimmerEnabled) {
            radialGlossPercent.coerceIn(0, 100) / 50f
        } else {
            borderShimmerPercent.coerceIn(0, 100) / 50f
        }
        if (effectiveFactor <= 0.001f) return 0f
        val baseAlpha = if (isDark) 0.13f else 0.55f
        return (baseAlpha * effectiveFactor).coerceIn(0f, 1f)
    }

    /**
     * 顶部垂直微光强度（0f ~ 1f）。
     * 未开启高级微调时由主滑块 borderShimmerPercent 驱动；
     * 开启后完全由独立滑块 verticalGlossPercent 绝对控制，两套互斥不连乘。
     */
    fun verticalGlossAlpha(isDark: Boolean): Float {
        val effectiveFactor = if (advancedShimmerEnabled) {
            verticalGlossPercent.coerceIn(0, 100) / 50f
        } else {
            borderShimmerPercent.coerceIn(0, 100) / 50f
        }
        if (effectiveFactor <= 0.001f) return 0f
        val baseAlpha = if (isDark) 0.13f * 0.55f else 0.55f * 0.55f
        return (baseAlpha * effectiveFactor).coerceIn(0f, 1f)
    }

    @Deprecated("Use radialGlossAlpha or verticalGlossAlpha", ReplaceWith("radialGlossAlpha(isDark)"))
    fun shimmerGlossAlpha(isDark: Boolean): Float = radialGlossAlpha(isDark)

    fun innerBorderWidthPx(density: Float): Float =
        (0.75f * density).coerceAtLeast(1f)

    companion object {
        val DEFAULT = OverlayMaterialSpec()

        fun fromContext(context: Context): OverlayMaterialSpec =
            OverlayHostRuntime.currentOverlayMaterialSpec?.invoke(context) ?: DEFAULT
    }
}

/**
 * 获取当前全局悬浮窗材质规范（Compose 环境）。
 */
@Composable
fun rememberOverlayMaterialSpec(): OverlayMaterialSpec {
    val context = LocalContext.current
    val flow: Flow<OverlayMaterialSpec>? = remember(context) {
        OverlayHostRuntime.overlayMaterialSpecFlow?.invoke(context)
    }
    val fallback = remember(context) {
        OverlayHostRuntime.currentOverlayMaterialSpec?.invoke(context) ?: OverlayMaterialSpec.DEFAULT
    }
    return if (flow != null) {
        val spec by flow.collectAsState(initial = fallback)
        spec
    } else {
        fallback
    }
}
