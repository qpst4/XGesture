package com.slideindex.app.overlay.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.settings.StashPanelWidthMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 对齐设计稿：正文 `--f-sm` 12.5px、行高 1.6；元信息 `--f-tiny` 10.5px。 */
object HistoryPanelTypography {
    @Composable
    fun content(): TextStyle = MiuixTheme.textStyles.body2.copy(
        fontSize = 13.sp,
        lineHeight = 21.sp,
    )

    @Composable
    fun meta(): TextStyle = MiuixTheme.textStyles.footnote2

    @Composable
    fun hint(): TextStyle = MiuixTheme.textStyles.footnote1
}

@Composable
fun Modifier.historyConsumeTaps(): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
) {}

/**
 * 面板宽度档位比例（由设置页写入；历史面板与图片解码共用）。
 */
val LocalStashPanelWidthFraction = staticCompositionLocalOf {
    StashPanelWidthMode.STANDARD.fraction
}

/** 面板宽度 = 可用宽度 × 当前档位比例，再用 [PANEL_WIDTH_MAX] 封顶。 */
@Composable
fun panelWidthOf(available: Dp): Dp =
    minOf(available * LocalStashPanelWidthFraction.current, PANEL_WIDTH_MAX)

/** 面板宽度上限（dp）—— 治"横屏也占 78%"。 */
val PANEL_WIDTH_MAX = 420.dp

/** 跟手拉出时"拉多远算拉满" = 130dp 的手指位移。 */
fun historyPanelRevealDistancePx(context: android.content.Context): Float =
    (PANEL_REVEAL_DRAG_DP * context.resources.displayMetrics.density)

/** 设计稿里"拉满"所需的手指位移（`-dx / 130`）。 */
const val PANEL_REVEAL_DRAG_DP = 130

/** 设计稿里侧栏的圆角（`--r-2xl` = 26dp）。 */
val HistoryPanelCornerRadius = 26.dp

@Composable
fun historyClipboardCardPreviewHeightPx(): Int {
    val density = LocalDensity.current
    return with(density) { 120.dp.roundToPx() }
}

@Composable
fun historyPreviewWidthPx(): Int {
    val density = LocalDensity.current
    val windowWidth = LocalWindowInfo.current.containerSize.let { size ->
        if (size.width <= 0) size.height else minOf(size.width, size.height)
    }
    return with(density) {
        (panelWidthOf(with(density) { windowWidth.toDp() }) - 24.dp)
            .roundToPx()
            .coerceAtMost(960)
    }
}

@Composable
fun historyStashPreviewHeightPx(): Int {
    val density = LocalDensity.current
    return with(density) { 150.dp.roundToPx() }
}

@Composable
fun historyExpandedImageMaxSidePx(): Int {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenHeightPx = windowInfo.containerSize.height
    return with(density) {
        maxOf(
            historyPreviewWidthPx(),
            (screenHeightPx * 0.75f).toInt(),
        ).coerceAtMost(2048)
    }
}

// ---- 浮窗把手常量（原 app 的 HistoryFloatContent；面板与 Handle/SavePeek 窗共用）----

const val HANDLE_EDGE_GAP_DP = 3
const val HANDLE_BOX_DP = 48
const val HANDLE_BAR_WIDTH_DP = 9
const val HANDLE_BAR_ACTIVE_WIDTH_DP = 11
const val HANDLE_TOUCH_INNER_BAND_DP = 10
const val HANDLE_TOUCH_WIDTH_DP =
    HANDLE_EDGE_GAP_DP + HANDLE_BAR_ACTIVE_WIDTH_DP + HANDLE_TOUCH_INNER_BAND_DP
const val HANDLE_BAR_HEIGHT_DP = 28
const val HANDLE_BAR_BORDER_DP = 1f
const val HANDLE_BAR_CORNER_DP = 5