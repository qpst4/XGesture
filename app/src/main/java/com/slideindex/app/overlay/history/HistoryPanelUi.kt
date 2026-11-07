package com.slideindex.app.overlay.history

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.settings.StashPanelWidthMode
import com.slideindex.app.stash.StashMetaRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.text.TextStyle

/** 顶栏工具图标：与 [MiuixExpandableSearchIconAction] 同尺寸、同色。 */
@Composable
fun HistoryPanelToolbarIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

/**
 * 浮窗卡片本体"吃点击"（**空实现，只消费**）。
 *
 * ⚠️ 没有它，点在卡片上的**空白处**（内边距 / 行间距 / 纯文字区）会穿到下面那层
 * "点空白关闭"的遮罩上 —— 用户实测：手指明明落在弹窗里，弹窗却被关掉了。
 * 面板自己早就这么干了（`HistoryPanelScreen` 里面板根节点那个空 `clickable {}`），
 * 三块浮窗当时漏了这一步。
 */
@Composable
fun Modifier.historyConsumeTaps(): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
) {}

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

/** 收纳面板列表滚动：MIUI 回弹 + 触底触感。 */
fun Modifier.historyPanelListScrollEffects(): Modifier = this
    .scrollEndHaptic()
    .overScrollVertical(nestedScrollToParent = false)

/** 列表参与顶栏毛玻璃采样。 */
fun Modifier.historyPanelListBackdrop(backdrop: LayerBackdrop?): Modifier =
    historyPanelListScrollEffects().then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)

/**
 * 列表尾部留给右下角 FAB 的空白（只有闪念页签有 FAB）。
 *
 * 54dp（FAB 本体）+ 32dp（它现在的底边偏移）+ 10dp 余量 = 96dp —— 之前是 64dp，
 * 那是 FAB 还贴着屏幕底边时定的，结果最后一张卡的 ⋮ 会被 FAB 压住。
 */
val HistoryListFooterPadding = 96.dp

/**
 * 当前面板宽度比例，由 `FloatBallStashPanel.PanelContent` 按设置注入（见 [StashPanelWidthMode]）。
 *
 * 用 CompositionLocal 而不是给 [panelWidthOf] 加参数：吃这个值的地方 —— 面板本体宽度、
 * 图片降采样宽（[historyPreviewWidthPx]）、卡片缩略图宽（`HistoryEntryCards`）—— 散落在两个
 * 没有 settings 的工具函数和一个卡片组件里，一路传参要动一串签名。
 */
val LocalStashPanelWidthFraction = staticCompositionLocalOf {
    StashPanelWidthMode.STANDARD.fraction
}

/**
 * 面板宽度 = 可用宽度 × 当前档位比例，**再用 [PANEL_WIDTH_MAX] 封顶**。
 *
 * ⚠️ 别把它当"已废弃"（老注释就是这么写的，是 §0.16.3 那次"窗口改 78% 宽"实验的残留）：
 * §0.16.3 已被用户整批回退 —— 现在**窗口是满屏的**（`OverlayPanelLayoutParams`），
 * 面板内容由 `HistoryPanelScreen` 用 `BoxWithConstraints` 的 `maxWidth` 乘出这个宽度，
 * 所以这里就是**生产路径**。
 */
@Composable
fun panelWidthOf(available: Dp): Dp =
    minOf(available * LocalStashPanelWidthFraction.current, PANEL_WIDTH_MAX)

/**
 * 面板宽度上限（dp）—— 治"横屏也占 78%"。
 *
 * 竖屏 411dp × 78% = 320dp，够不着上限，**行为与设计稿一致、没有变化**；
 * 横屏窗口宽 891dp，78% 会变成 695dp（几乎占满整屏，用户实测反馈"横屏不该也 78%"），
 * 于是被收到 420dp ≈ **横屏的小半个屏幕**。平板/折叠屏展开态同理受益。
 */
val PANEL_WIDTH_MAX = 420.dp

/**
 * 跟手拉出时"拉多远算拉满" = 面板宽度（px）。
 *
 * 面板侧用的是布局约束（见上），但**把手窗只有 48dp 宽**，它读不到屏幕宽度，
 * 所以把手侧用窗口管理器的**当前窗口度量**（`currentWindowMetrics`，随旋转更新；
 * `resources.displayMetrics` 在 overlay 里可能是陈旧的旋转前尺寸）。
 */
fun historyPanelRevealDistancePx(context: android.content.Context): Float =
    // 设计稿 `revealStream(dx) { const p = clamp(-dx / 130) }`：**手指横向拖 130px 就拉满**，
    // 和面板宽度无关。我原先按"面板宽度(842px)"算，于是要拖满一屏才到位、手感完全不对。
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
    // 预览只用来给图片解码降采样。面板 = **窗口宽 × 78%**（窗口是满屏的，见 `OverlayPanelLayoutParams`）。
    // `containerSize` 在真机上可能返回旋转过的尺寸，取 min(w,h) 兜底；上限 960px。
    val windowWidth = LocalWindowInfo.current.containerSize.let { size ->
        if (size.width <= 0) size.height else minOf(size.width, size.height)
    }
    return with(density) {
        // 与面板宽度同一个算式（含 420dp 上限），图片解码宽度才不会和真实面板宽度跑偏。
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

