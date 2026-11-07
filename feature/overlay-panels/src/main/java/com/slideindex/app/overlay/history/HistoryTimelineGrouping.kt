package com.slideindex.app.overlay.history

import com.slideindex.app.stash.StashEntry
import java.util.Calendar
import java.util.TimeZone

/**
 * 时间轴分组（设计稿 `ui_demo_capsule.html` 的 `.grp`：今天 / 昨天 / 更早）。
 *
 * 这是纯逻辑，不碰 Compose —— 分组边界（尤其夏令时）由 `HistoryTimelineGroupingTest` 覆盖。
 */
enum class HistoryDayGroup {
    Today,
    Yesterday,
    Earlier,
}

/**
 * 扁平化的时间轴行：**每组一个表头 + 各条目**，全部平铺在同一个 `LazyColumn` 里。
 *
 * 为什么不给每组套一个 `Column`：`docs/capsule-refactor-plan.md` §3.5 与
 * `docs/ui-guidelines.md` 要求列表必须走 Lazy 虚拟化，嵌套列表会让「一组」变成
 * 一整块不可拆分的项。竖线因此**逐行绘制**（每行画自己那一段），这样懒加载把行
 * 拆到不同时间合成也不会断口（这正是 §6.4 里"竖线在懒加载条目边界能否不断口"的答案）。
 */
sealed interface HistoryTimelineRow {
    val key: String

    /**
     * 竖线在这一行上的强度系数（0..1，实际色值还要乘 [HistoryTimelineLineAlpha]）。
     *
     * 设计稿的竖线是整组一根渐变（`linear-gradient(180deg, …14%, transparent)`）。逐行
     * 画时如果每行都自己做一次渐变，行与行之间会出现"重启"的接缝；所以改成**按行在组内
     * 的位置预先算出强度**，单调递减、跨行连续。
     */
    val lineAlpha: Float

    data class GroupHeader(
        val group: HistoryDayGroup,
        /** 同一个组理论上只出现一次；非连续出现时（列表被改乱）靠它保证 key 唯一。 */
        val runIndex: Int,
        override val lineAlpha: Float,
    ) : HistoryTimelineRow {
        override val key: String = "history_group_${group.name}_$runIndex"
    }

    data class Entry(
        val entry: StashEntry,
        val group: HistoryDayGroup,
        override val lineAlpha: Float,
    ) : HistoryTimelineRow {
        override val key: String = "history_entry_${entry.id}"
    }
}

/** 竖线在分组起始处的强度：设计稿 `color-mix(in srgb, var(--text) 14%, transparent)`。 */
const val HistoryTimelineLineAlpha = 0.14f

/**
 * 某条记录属于哪一组。
 *
 * 用 [Calendar] 取"当天零点"再比较，而不是减 86400000 —— 夏令时切换那天
 * 「昨天零点」并不等于「今天零点减一天」，直接减会把午夜前后一小时算错组。
 */
fun historyDayGroupOf(
    epochMs: Long,
    nowMs: Long,
    zone: TimeZone = TimeZone.getDefault(),
): HistoryDayGroup {
    val todayStart = startOfDayMs(nowMs, zone)
    if (epochMs >= todayStart) return HistoryDayGroup.Today
    val yesterdayStart = startOfDayMs(todayStart - 1L, zone)
    return if (epochMs >= yesterdayStart) HistoryDayGroup.Yesterday else HistoryDayGroup.Earlier
}

private fun startOfDayMs(epochMs: Long, zone: TimeZone): Long =
    Calendar.getInstance(zone).apply {
        timeInMillis = epochMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/**
 * 把（仓储按时间倒序给出的）条目摊平成 [HistoryTimelineRow]。
 *
 * 分组按**相邻段**（run）判定：列表顺序就是用户看到的顺序，这里不重排 —— 数据层没承诺
 * 排序，强行重排会打乱未来可能的顺序语义（例如"钉在顶部"）。同一组被拆成不连续的多段时，
 * 每段都会再出一个表头，[HistoryTimelineRow.GroupHeader.runIndex] 保证 key 不重复 ——
 * 重复 key 会让 `LazyColumn` 直接抛异常。
 */
fun buildHistoryTimelineRows(
    entries: List<StashEntry>,
    nowMs: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.getDefault(),
): List<HistoryTimelineRow> {
    if (entries.isEmpty()) return emptyList()
    val groups = entries.map { historyDayGroupOf(it.createdAtEpochMs, nowMs, zone) }
    val rows = ArrayList<HistoryTimelineRow>(entries.size + groups.size)
    val runCount = IntArray(HistoryDayGroup.entries.size)
    var index = 0
    while (index < entries.size) {
        val group = groups[index]
        var last = index
        while (last + 1 < entries.size && groups[last + 1] == group) last++
        val runLength = last - index + 1
        rows += HistoryTimelineRow.GroupHeader(
            group = group,
            runIndex = runCount[group.ordinal],
            lineAlpha = 1f,
        )
        runCount[group.ordinal]++
        for (i in index..last) {
            // 竖线强度按在**本段内**的位置递减：表头最亮，段尾最暗。
            rows += HistoryTimelineRow.Entry(
                entry = entries[i],
                group = group,
                lineAlpha = (1f - (i - index + 1).toFloat() / (runLength + 1)).coerceIn(0f, 1f),
            )
        }
        index = last + 1
    }
    return rows
}
