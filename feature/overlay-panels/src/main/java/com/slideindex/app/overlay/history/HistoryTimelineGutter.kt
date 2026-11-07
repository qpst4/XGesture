package com.slideindex.app.overlay.history

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.stash.StashEntry
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 左侧时间轴槽（设计稿 `ui_demo_capsule.html` 的 `.grp` / `.vline` / `.item .node`）。
 *
 * 几何全部照设计稿：
 * - `.grp { padding-left: 62px }` → 内容整体右移 [HistoryTimelineGutterWidth]
 * - `.grp .vline { left: 54px; width: 1.5px }` → 竖线
 * - `.grp > .glabel { left: 0; width: 48px; text-align: right }` → 分组名
 * - `.item .node { left: -8.5px; top: 17px; 7×7 }` → 节点圆点
 * - `.item .when { margin: 0 0 6px 2px }` → 时间行在**卡片外**、卡片左缘再进 2dp
 *
 * ⚠️ 设计稿里节点是 `left:-8.5px`、竖线在 54px（两者中心差 2.25dp，是设计稿自己的
 * 取整）。这里把节点**中心对齐到竖线**，比照抄坐标更整齐。
 */
val HistoryTimelineGutterWidth = 62.dp

private val TimelineLineX = 54.dp
private val TimelineLineWidth = 1.5.dp
private val TimelineNodeSize = 7.dp
private val TimelineNodeTop = 17.dp

/** 设计稿 `.item.fresh .node { box-shadow: 0 0 0 4px var(--accent-soft) }`。 */
private val TimelineNodeHaloSpread = 4.dp
private const val TimelineNodeHaloAlpha = 0.14f

/** 设计稿 `.item .node { background: color-mix(in srgb, var(--text) 26%, transparent) }`。 */
private const val TimelineNodeAlpha = 0.26f

/** 设计稿 `.grp > .glabel { width: 48px }`。 */
private val TimelineGroupLabelWidth = 48.dp

/** 设计稿 `.grp { margin-top: 18px }` + `.glabel { top: 1px }` / `.vline { top: 8px }`。 */
private val TimelineGroupTopGap = 18.dp
private val TimelineGroupLabelTop = TimelineGroupTopGap + 1.dp
private val TimelineGroupLineTop = TimelineGroupTopGap + 8.dp

/** 设计稿 `.item { margin-bottom: 10px }`（竖线要穿过这段空隙，所以做进行高而不是行间距）。 */
private val TimelineEntryRowGap = 10.dp

/** 设计稿 `.item .when { margin: 0 0 6px 2px }`。 */
private val TimelineTimeRowStart = 2.dp
private val TimelineTimeRowGap = 6.dp
private val TimelineTimeRowItemGap = 7.dp

/**
 * 分组表头：分组名 + 竖线的起始段。
 *
 * 分组名**不吸顶** —— 设计稿里它就在左侧槽里（`docs/capsule-refactor-plan.md` §0 已作废吸顶那条）。
 *
 * [staggerDelayMs] = 首屏错开淡入的延迟（0 = 不播，见 [rememberTimelineReveal]）。
 */
@Composable
fun HistoryTimelineGroupHeader(
    group: HistoryDayGroup,
    lineAlpha: Float,
    staggerDelayMs: Int = 0,
) {
    val theme = historyTheme()
    val reveal = rememberTimelineReveal(staggerDelayMs)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = reveal
                translationY = (1f - reveal) * TIMELINE_REVEAL_OFFSET.toPx()
            },
    ) {
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawTimelineLine(
                        color = theme.text,
                        alpha = lineAlpha,
                        topPx = TimelineGroupLineTop.toPx(),
                    )
                },
        )
        // 右对齐、右缘钉在 48dp：宽度写死后长译文（如 Yesterday）会换行，所以单行 +
        // softWrap=false，宽出去的部分往左溢出（槽左侧本来就没有别的东西）。
        Text(
            text = stringResource(historyDayGroupLabelRes(group)),
            // 轴槽里的字专门提一档（10.5sp/灰 -> 11.5sp/正文色 88%）：它们直接浮在玻璃上，最吃对比。
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            ),
            color = theme.text.copy(alpha = 0.88f),
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .padding(top = TimelineGroupLabelTop)
                .width(TimelineGroupLabelWidth),
        )
    }
}

/**
 * 一条闪念的时间轴行：竖线段 + 节点圆点 + **卡片外的时间行** + 卡片本身。
 *
 * [card] 由调用方（`HistoryPanelScreen`）传入，这样这里不需要知道卡片的一大堆回调。
 * [staggerDelayMs] = 首屏错开淡入的延迟（0 = 不播）。
 * [reminderAtMs] = 已设的提醒时间（设计稿 `.item .remind`，跟时间同一行）。
 * [reminderOverdue] = 这条提醒**已经响过**了（§0.16.15）：这时**不隐藏**那一行，
 * 而是换成灰色 + 前缀「已提醒」—— 用户实测的抱怨正是"设了提醒的条目后来那行 ⏰ 不见了"，
 * 而那其实只说明"它响过了、你还没处理"。默认 false 让既有调用点不用改。
 */
@Composable
fun HistoryTimelineEntryRow(
    entry: StashEntry,
    group: HistoryDayGroup,
    lineAlpha: Float,
    staggerDelayMs: Int = 0,
    reminderAtMs: Long? = null,
    reminderOverdue: Boolean = false,
    card: @Composable () -> Unit,
) {
    val theme = historyTheme()
    val fresh = group == HistoryDayGroup.Today
    val reveal = rememberTimelineReveal(staggerDelayMs)
    // 设计稿：今天 = --accent（还带一圈 accent-soft 光晕），星标 = --accent-solid，其余 = text 26%。
    val nodeColor = when {
        fresh -> theme.accent
        entry.starred -> theme.accentSolid
        else -> theme.text.copy(alpha = TimelineNodeAlpha)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = reveal
                translationY = (1f - reveal) * TIMELINE_REVEAL_OFFSET.toPx()
            },
    ) {
        Spacer(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawTimelineLine(
                        color = theme.text,
                        alpha = lineAlpha,
                        topPx = 0f,
                    )
                    val radius = TimelineNodeSize.toPx() / 2f
                    val center = Offset(
                        x = TimelineLineX.toPx() + TimelineLineWidth.toPx() / 2f,
                        y = TimelineNodeTop.toPx() + radius,
                    )
                    if (fresh) {
                        drawCircle(
                            color = theme.accentSoft,
                            radius = radius + TimelineNodeHaloSpread.toPx(),
                            center = center,
                        )
                    }
                    drawCircle(color = nodeColor, radius = radius, center = center)
                },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = HistoryTimelineGutterWidth, bottom = TimelineEntryRowGap),
        ) {
            Row(
                modifier = Modifier.padding(start = TimelineTimeRowStart, bottom = TimelineTimeRowGap),
                horizontalArrangement = Arrangement.spacedBy(TimelineTimeRowItemGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatHistoryRelativeTime(entry.createdAtEpochMs),
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.3.sp,
                    ),
                    color = theme.text.copy(alpha = 0.80f),
                )
                // 已设提醒：设计稿 `.item .remind`（时钟 + 主题色粗体）。
                //
                // §0.16.15：**过期也显示** —— 只是换成灰调 + 前缀「已提醒」。以前这里虽然没写
                // "只画未来时间"的过滤，但过期提醒会被数据层收尾删掉（`clearExpiredReminders`），
                // 于是这一行整行消失，看起来就像"提醒没了"。现在数据层会留下 `firedAt`，
                // 传进来的是"曾经响过的时间"，这里负责把它画成"已提醒 昨天 21:30"那种过去式的样子。
                reminderAtMs?.let { at ->
                    val reminderColor = if (reminderOverdue) {
                        // 灰掉用 `theme.sub`（和"今天/昨天"那行小字同一档），而不是再定义一种颜色：
                        // 语义就是"这条不再醒目了"，和"次要信息"是同一个视觉档位。
                        theme.sub
                    } else {
                        theme.accentSolid
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = reminderColor,
                            modifier = Modifier.size(TimelineReminderIconSize),
                        )
                        if (reminderOverdue) {
                            // 「已提醒」在**时间前面**：中文/日文读起来是"已提醒 昨天 21:30"，
                            // 阿拉伯语等 RTL 语言由布局自己镜像，前缀仍然贴着图标。
                            Text(
                                text = stringResource(R.string.stash_remind_overdue_label),
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = HistoryFontSizes.tiny,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.3.sp,
                                ),
                                color = reminderColor,
                            )
                        }
                        Text(
                            text = formatReminderTime(at),
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = HistoryFontSizes.tiny,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.3.sp,
                            ),
                            color = reminderColor,
                        )
                    }
                }
            }
            card()
        }
    }
}

@StringRes
private fun historyDayGroupLabelRes(group: HistoryDayGroup): Int = when (group) {
    HistoryDayGroup.Today -> R.string.stash_group_today
    HistoryDayGroup.Yesterday -> R.string.stash_group_yesterday
    HistoryDayGroup.Earlier -> R.string.stash_group_earlier
}

/** 首屏错开淡入：一行的步长（列表里第 n 行延迟 n × 这个值）。 */
const val HistoryRowStaggerStepMs = 40

/** 只对首屏这么多行做错开（设计稿列表只 stagger 首屏；再多会和 Lazy 虚拟化打架）。 */
const val HistoryRowStaggerMaxCount = 8

/** 设计稿 `.item .remind svg { width: 11px }`。 */
private val TimelineReminderIconSize = 11.dp

private const val TimelineRevealDurationMs = 280
private val TIMELINE_REVEAL_OFFSET = 24.dp

/**
 * 首屏错开淡入的进度（0 → 1）：[delayMs] <= 0 的行恒为 1（不播）。
 *
 * ⚠️ `LaunchedEffect` 的 key 必须是 `Unit`，**不能是 delayMs**：错开窗口结束后
 * `staggerPlaying` 翻成 false、参数变成 0，key 一变就会**取消**正在跑的动画，
 * 那一行会永远停在 alpha 0（我踩过）。
 */
@Composable
private fun rememberTimelineReveal(delayMs: Int): Float {
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (delayMs <= 0) return@LaunchedEffect
        alpha.snapTo(0f)
        delay(delayMs.toLong())
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = TimelineRevealDurationMs),
        )
    }
    return alpha.value
}

private fun DrawScope.drawTimelineLine(color: Color, alpha: Float, topPx: Float) {
    if (alpha <= 0f) return
    val height = size.height - topPx
    if (height <= 0f) return
    drawRect(
        color = color.copy(alpha = color.alpha * alpha * HistoryTimelineLineAlpha),
        topLeft = Offset(TimelineLineX.toPx(), topPx),
        size = Size(TimelineLineWidth.toPx(), height),
    )
}
