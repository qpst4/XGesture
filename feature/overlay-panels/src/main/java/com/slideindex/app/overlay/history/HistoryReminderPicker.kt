package com.slideindex.app.overlay.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.Text

/**
 * 提醒时间选择浮窗（§0.16.9 起；§0.16.10 补"自定义时间"）。
 *
 * 两种形态，照用户给的参考 App（`com.moting.floatwidget`）：
 * - **档位**：5/15/30 分钟后 · 1/3 小时后 · 今天 17:00 · 明天 07:00 · 明天 09:00（**已过去的档位不显示**）
 *   + 一枚「自定义时间…」+（已设提醒时）「清除提醒」；
 * - **自定义时间**：日期 / 时 / 分 三个滚轮（吸附到整项）+ 顶部「← 档位 · 预览 · 确定」。
 *
 * 壳子与其它浮窗**同一套**（96% 宽 / 最大 720dp / 圆角 28 / 投影 18 / 内边距 26·26·26·22），
 * 卡片本体带 [historyConsumeTaps]（否则点在卡片空白处会穿到下面那层"点空白关闭"的遮罩上）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryReminderPickerModal(
    open: Boolean,
    /** 当前已设的提醒（null = 没设）；用来标出选中项并决定要不要显示「清除」。 */
    currentAtMs: Long?,
    imeBottom: Dp,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    // "从现在起 N 分钟"这类档位必须在**打开那一刻**算好，否则浮窗开着不动、值会一直往后漂。
    val openedAtMs = remember(open) { System.currentTimeMillis() }
    var customMode by remember(open) { mutableStateOf(false) }

    // 自定义模式的三个滚轮：默认落在"当前已设的时间"或"明天 09:00"上，分钟吸附到 5 分钟档。
    val initialCustom = remember(open) {
        val base = Calendar.getInstance().apply {
            timeInMillis = currentAtMs ?: historyDefaultReminderAt()
        }
        Triple(
            historyDayOffsetOf(base),
            base.get(Calendar.HOUR_OF_DAY),
            base.get(Calendar.MINUTE) / HistoryReminderMinuteStep,
        )
    }
    var dayIndex by remember(open) { mutableIntStateOf(initialCustom.first) }
    var hour by remember(open) { mutableIntStateOf(initialCustom.second) }
    var minuteIndex by remember(open) { mutableIntStateOf(initialCustom.third) }

    val dayLabels = historyReminderDayLabels(HistoryReminderDayCount)
    val hourLabels = remember { (0..23).map { String.format("%02d", it) } }
    val minuteLabels = remember {
        (0 until 60 step HistoryReminderMinuteStep).map { String.format("%02d", it) }
    }
    val customAtMs = remember(dayIndex, hour, minuteIndex) {
        reminderAtFor(dayIndex, hour, minuteIndex * HistoryReminderMinuteStep)
    }

    AnimatedVisibility(
        visible = open,
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.96f),
    ) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            modifier = modifier
                .padding(bottom = imeBottom)
                .shadow(18.dp, shape)
                .clip(shape)
                .historyConsumeTaps()
                .background(theme.glassSolid)
                .border(1.dp, theme.glassBorder, shape)
                .padding(start = 26.dp, end = 26.dp, top = 26.dp, bottom = 22.dp),
        ) {
            if (!customMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.stash_remind_toggle),
                        style = TextStyle(fontSize = HistoryFontSizes.body),
                        color = theme.text,
                        modifier = Modifier.weight(1f),
                    )
                    HistoryHeaderCircleButton(
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.panel_close),
                        onClick = onDismiss,
                    )
                }
                currentAtMs?.let { at ->
                    Text(
                        text = stringResource(R.string.stash_remind_current, formatReminderTime(at)),
                        style = TextStyle(fontSize = HistoryFontSizes.tiny),
                        color = theme.sub,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    historyReminderPresets(openedAtMs).forEach { preset ->
                        HistoryChip(
                            // 相对档位就说"几分钟后"（参考 App 就是这么写的，也更短）；
                            // 整点档位才用绝对时间（今天 17:00 / 明天 07:00）。
                            label = when {
                                preset.minutes > 0 -> {
                                    stringResource(R.string.stash_remind_in_minutes, preset.minutes)
                                }
                                preset.hours > 0 -> {
                                    stringResource(R.string.stash_remind_in_hours, preset.hours)
                                }
                                else -> formatReminderTime(preset.atMs)
                            },
                            dotColor = null,
                            selected = currentAtMs == preset.atMs,
                            onClick = { onPick(preset.atMs) },
                        )
                    }
                    // 「自定义时间…」：切到滚轮模式（参考 App 就是这条出口）。
                    HistoryChip(
                        label = stringResource(R.string.stash_remind_custom),
                        dotColor = null,
                        selected = false,
                        onClick = { customMode = true },
                    )
                }
                if (currentAtMs != null) {
                    HistoryEditActionButton(
                        label = stringResource(R.string.stash_remind_clear),
                        icon = Icons.Default.Delete,
                        onClick = { onPick(null) },
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    )
                }
            } else {
                // ---- 自定义时间：顶部「← 档位 · 预览 · 确定」+ 三个滚轮 ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "← " + stringResource(R.string.stash_remind_presets),
                        style = TextStyle(fontSize = HistoryFontSizes.meta),
                        color = theme.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(HistoryRadii.pill))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { customMode = false }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    Text(
                        text = formatReminderTime(customAtMs),
                        style = TextStyle(
                            fontSize = HistoryFontSizes.body,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = theme.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.stash_remind_confirm),
                        style = TextStyle(
                            fontSize = HistoryFontSizes.meta,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = theme.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(HistoryRadii.pill))
                            .background(theme.accentSoft)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onPick(customAtMs) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HistoryWheel(
                        items = dayLabels,
                        selectedIndex = dayIndex,
                        onSelected = { dayIndex = it },
                        modifier = Modifier.weight(1.7f),
                        // 日期**不循环**：60 天首尾相接会让人彻底失去"今天是哪一天"的方向感。
                        loop = false,
                    )
                    HistoryWheel(
                        items = hourLabels,
                        selectedIndex = hour,
                        onSelected = { hour = it },
                        modifier = Modifier.weight(1f),
                        // 时/分循环：23 下面接 00、59 下面接 00。
                        loop = true,
                    )
                    Text(
                        text = ":",
                        style = TextStyle(fontSize = HistoryFontSizes.titleS),
                        color = theme.sub,
                    )
                    HistoryWheel(
                        items = minuteLabels,
                        selectedIndex = minuteIndex,
                        onSelected = { minuteIndex = it },
                        modifier = Modifier.weight(1f),
                        loop = true,
                    )
                }
                Text(
                    text = stringResource(R.string.stash_remind_custom_hint),
                    style = TextStyle(fontSize = HistoryFontSizes.tiny),
                    color = theme.sub,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * 滚轮：`snapFlingBehavior` 吸附到整项，**选中项 = 最靠近视口中心的那一行**。
 *
 * ⚠️ 别用 `firstVisibleItemIndex + 半个偏移` 去推选中项：视口高 3 行 + 上下各留 1 行 padding 时，
 * 静止状态下 `firstVisibleItemIndex` 就是选中项（偏移为 0），那个公式反而会额外 +1。
 * 直接按"谁的中心离视口中心最近"算，最稳。
 *
 * [loop] = true（**只给"时"/"分"用**，日期轮不循环）时把列表铺 [HISTORY_WHEEL_COPIES] 份：
 * 用户从 59 继续往下滑会滑进"下一份的 00"，滑到副本边界时静默 `scrollToItem` 到等价的中间项，
 * 视觉上无感。**回写出去的值一律是真实索引**（见下面的 `abs % itemCount`），
 * 否则 `hour` / `minuteIndex` 会跳到 20+ 这种越界值。
 */
@Composable
private fun HistoryWheel(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 是否把列表铺多份做无限循环（时/分 = true；日期 = false）。 */
    loop: Boolean = false,
) {
    val theme = historyTheme()
    val itemCount = items.size
    // 循环模式铺 5 份、从**正中间那份**开始：左右各有 2 份副本当缓冲，单向能连续滑 2×itemCount 项
    // 才会碰到物理边界（碰到边界就静默跳到中间那份）。
    val copies = if (loop && itemCount > 1) HISTORY_WHEEL_COPIES else 1
    val totalCount = itemCount * copies
    // 铺开后的**绝对索引** → 真实索引。所有回写都必须过这一步。
    fun realOf(absoluteIndex: Int): Int =
        if (itemCount <= 0) 0 else ((absoluteIndex % itemCount) + itemCount) % itemCount

    // 初始位置落在中段副本内（循环时），真实值仍然是 0..itemCount-1。
    val startAbs = remember(selectedIndex, itemCount, copies) {
        if (copies > 1) {
            itemCount * (copies / 2) + selectedIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
        } else {
            selectedIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
        }
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startAbs)
    val centerIndex by remember(loop) {
        derivedStateOf {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { abs((it.offset + it.size / 2) - center) }
                ?.index
                ?: startAbs
        }
    }
    // 外部改了选中值（切日期、夹取）→ 把轮子滚到位；相等时不动作，免得和下面的回写打架。
    // ⚠️ 循环模式下必须滚到**中段副本里那个等价位**，否则贴边起步一滑就撞边界。
    //
    // ⚠️ **只在手停下时**才滚（`isScrollInProgress == false`）：滚动本身会一项一项地改变
    // `centerIndex` → 回写 `selectedIndex` → 这个 effect 重启。如果重启时就 `scrollToItem`，
    // 每一次"选到下一项"都会把 `snapFlingBehavior` 的惯性掐死（用户感觉是"一滑就停"）。
    // 停下之后再滚就不会掐任何动画；而循环轮跨过副本接缝时（23 下面接 00）要滚的目标与
    // 当前位置**视觉上完全等价**（同一批 label、同样的相对位置），所以那一下也看不出来。
    LaunchedEffect(selectedIndex, itemCount, copies) {
        val target = if (copies > 1) {
            itemCount * (copies / 2) + selectedIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
        } else {
            selectedIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
        }
        if (!state.isScrollInProgress) {
            if (state.firstVisibleItemIndex != target) state.scrollToItem(target)
            return@LaunchedEffect
        }
        snapshotFlow { state.isScrollInProgress }
            .collect { scrolling ->
                if (scrolling) return@collect
                if (state.firstVisibleItemIndex != target) state.scrollToItem(target)
            }
    }
    // 静默跳回中段：只在"手已经停下"时跳，免得打断吸附/惯性动画（中断会看得出突兀）。
    // 跳的等价项在视觉上完全相同（同一批 label、同样的相对位置），所以用户看不到接缝。
    if (copies > 1) {
        LaunchedEffect(state, totalCount) {
            snapshotFlow { state.firstVisibleItemIndex to state.isScrollInProgress }
                .collect { (first, scrolling) ->
                    if (scrolling) return@collect
                    val buffer = (itemCount / 2).coerceAtLeast(1)
                    val middle = itemCount * (copies / 2)
                    when {
                        first < buffer -> state.scrollToItem(first + middle)
                        first >= totalCount - itemCount - buffer ->
                            state.scrollToItem((first - middle).coerceAtLeast(0))
                    }
                }
        }
    }
    LaunchedEffect(items.size, loop) {
        snapshotFlow { centerIndex }
            .collect { index ->
                val real = realOf(index)
                if (index in 0 until totalCount && real != selectedIndex) onSelected(real)
            }
    }
    Box(
        modifier = modifier.height(HistoryWheelItemHeight * 3),
        contentAlignment = Alignment.Center,
    ) {
        // 中间那行的底 —— "选中的就是这一行"的唯一视觉线索
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HistoryWheelItemHeight)
                .clip(RoundedCornerShape(HistoryRadii.sm))
                .background(theme.fieldBg)
                .border(1.dp, theme.fieldBorder, RoundedCornerShape(HistoryRadii.sm)),
        )
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = HistoryWheelItemHeight),
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count = totalCount) { index ->
                // 铺开的绝对索引取模还原成真实项：5 份里的每一份内容完全一样。
                val realIndex = realOf(index)
                Box(
                    modifier = Modifier.height(HistoryWheelItemHeight).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = items[realIndex],
                        style = TextStyle(
                            fontSize = HistoryFontSizes.sm,
                            fontWeight = if (realIndex == selectedIndex) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                        ),
                        color = if (realIndex == selectedIndex) theme.text else theme.sub,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/** 一档提醒：相对时间（几分钟/几小时后）或绝对整点。 */
private data class HistoryReminderPreset(
    val atMs: Long,
    val minutes: Int = 0,
    val hours: Int = 0,
)

/**
 * 档位：相对时间 + 三枚固定整点，**已经过去的档位不显示**（否则点了等于设到过去）。
 *
 * 相对档位带 `minutes`/`hours`，好让 UI 说"N 分钟后"而不是把绝对时刻怼给用户。
 */
private fun historyReminderPresets(openedAtMs: Long): List<HistoryReminderPreset> {
    val presets = mutableListOf(
        // 「1 分钟后」既是常用档，也是**自助测试入口**：设完盯着看是否弹横幅/响/有「稍后 10 分钟」。
        HistoryReminderPreset(openedAtMs + 60_000L, minutes = 1),
        HistoryReminderPreset(openedAtMs + 5 * 60_000L, minutes = 5),
        HistoryReminderPreset(openedAtMs + 15 * 60_000L, minutes = 15),
        HistoryReminderPreset(openedAtMs + 30 * 60_000L, minutes = 30),
        HistoryReminderPreset(openedAtMs + 60 * 60_000L, hours = 1),
        HistoryReminderPreset(openedAtMs + 3 * 60 * 60_000L, hours = 3),
    )
    listOf(
        // 参考 App 的两枚整点档位
        atClock(dayOffset = 0, hourOfDay = 17, minute = 0),
        atClock(dayOffset = 1, hourOfDay = 7, minute = 0),
        // 设计稿原来的默认：明天 09:00
        atClock(dayOffset = 1, hourOfDay = 9, minute = 0),
    ).forEach { at -> if (at > openedAtMs) presets += HistoryReminderPreset(at) }
    return presets
}

private fun atClock(dayOffset: Int, hourOfDay: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, hourOfDay)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** 距今天几天（0 = 今天）；目标在过去或超出滚动范围时夹到合法区间。 */
private fun historyDayOffsetOf(target: Calendar): Int {
    val today = Calendar.getInstance()
    var offset = 0
    val probe = Calendar.getInstance().apply { timeInMillis = target.timeInMillis }
    while (offset < HistoryReminderDayCount - 1 && probe.after(today)) {
        val sameDay = probe.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            probe.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        if (sameDay) break
        probe.add(Calendar.DAY_OF_YEAR, -1)
        offset += 1
    }
    return offset.coerceIn(0, HistoryReminderDayCount - 1)
}

/** 自定义模式选出的时间：清掉秒/毫秒；**已经过去就挪到 1 分钟后**（别设一个永远不响的提醒）。 */
private fun reminderAtFor(dayOffset: Int, hourOfDay: Int, minute: Int): Long {
    val at = atClock(dayOffset, hourOfDay, minute)
    val earliest = System.currentTimeMillis() + 60_000L
    return if (at < earliest) earliest else at
}

/** 日期滚轮的文案：今天 / 明天 / `M月d日`（非中文用 `MMM d`）。 */
@Composable
private fun historyReminderDayLabels(count: Int): List<String> {
    val locale = LocalLocale.current.platformLocale
    val today = stringResource(R.string.stash_group_today)
    val tomorrow = stringResource(R.string.stash_remind_tomorrow)
    val pattern = if (locale.language == "zh") "M月d日" else "MMM d"
    return remember(count, locale, today, tomorrow) {
        val format = SimpleDateFormat(pattern, locale)
        val base = Calendar.getInstance()
        (0 until count).map { offset ->
            when (offset) {
                0 -> today
                1 -> tomorrow
                else -> format.format(
                    Date(
                        Calendar.getInstance().apply {
                            timeInMillis = base.timeInMillis
                            add(Calendar.DAY_OF_YEAR, offset)
                        }.timeInMillis,
                    ),
                )
            }
        }
    }
}

/** 日期滚轮可选天数（两个月足够"自定义"，再多滚起来反而难找）。 */
private const val HistoryReminderDayCount = 60

/** 分钟滚轮的步进（**1 分钟一档**：用户要求"5 分钟太粗"）。 */
private const val HistoryReminderMinuteStep = 1

/** 滚轮单行高度。 */
private val HistoryWheelItemHeight = 34.dp

/**
 * 循环滚轮把列表铺几份。
 *
 * 取 5（奇数）是为了**正中间那份**左右各有 2 份副本当缓冲：从中间起步单向能连滑
 * `2 × itemCount` 项才碰到物理边界，而碰到边界就静默跳回中段 —— 正常手感下用户永远碰不到真边界。
 */
private const val HISTORY_WHEEL_COPIES = 5
