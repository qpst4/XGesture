package com.slideindex.app.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupSizing

/**
 * 下拉面板里"一屏装得下几项"的门槛。
 *
 * `GlassPopupSizing.maxHeight` 默认 `GlassPopupDefaults.MaxHeight = 416.dp`，
 * 单项最小高 `GlassPopupDefaults.ItemMinHeight = 44.dp` → 416 / 44 ≈ 9.4。
 * 超过这个数，默认面板就必须滚动，此时把面板放到更高的尺寸更好用。
 */
private const val GlassPopupVisibleItemThreshold = 9

/**
 * 长列表下拉的尺寸：高度按「窗口可用高 × [fraction]」算，夹在 240dp..560dp。
 *
 * 为什么需要它：`GlassPopupSizing` 的默认高度是固定 `416.dp`，不随屏幕变化。
 * 「默认搜索引擎」「图标库」「语言」这类列表动辄十几项，固定 416dp 只能露出 9 项左右，
 * 在 6.7 寸屏上浪费了大量空间。
 */
@Composable
fun rememberTallGlassPopupSizing(fraction: Float = 0.6f): GlassPopupSizing {
    val density = LocalDensity.current
    val windowHeightDp = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    return remember(windowHeightDp, fraction) {
        GlassPopupSizing(
            maxHeight = (windowHeightDp * fraction).coerceIn(240.dp, 560.dp),
        )
    }
}

/**
 * 由列表项数自动决定面板尺寸：**超过一屏能装的项数**（[GlassPopupVisibleItemThreshold]）
 * 就用 [rememberTallGlassPopupSizing]，否则保持 miuix 默认。
 *
 * 这样长列表不用逐处写尺寸；需要精确控制时给封装传显式 `sizing`。
 */
@Composable
fun rememberAutoGlassPopupSizing(itemCount: Int): GlassPopupSizing {
    val tallSizing = rememberTallGlassPopupSizing()
    return remember(itemCount, tallSizing) {
        if (itemCount > GlassPopupVisibleItemThreshold) tallSizing else GlassPopupSizing()
    }
}

/** 与 [rememberAutoGlassPopupSizing] 同口径的门槛，供调用方自行判断。 */
val GlassPopupDefaultMaxHeight = GlassPopupDefaults.MaxHeight
