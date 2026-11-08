package com.slideindex.app.quickmenu

import java.util.Locale

/**
 * 秒表状态机（「快捷菜单 → 秒表」的计算核心）。
 *
 * 参考：QuickDash（https://github.com/balajitechlabs/quickdash，GPL-3.0）的 StopwatchContent
 * 提供过同类能力（`SystemClock.elapsedRealtime()` 驱动、分段计次、最快/最慢圈高亮）。
 * 本实现按其行为规格独立重写为**纯状态机**，理由是：
 * 1. 参考实现把"取当前时间"直接写进 Compose 的 `LaunchedEffect` 里，没法单测；
 *    这里把时钟作为参数传入（[start]/[pause]/[lap]/[elapsedAt]），UI 只负责每帧调用；
 * 2. 参考实现的计时依赖 `elapsedRealtime()`，而本实现不依赖任何 Android API，
 *    因此可以直接覆盖"暂停→继续""连续计次""跨小时"等边界。
 *
 * [State.laps] 存的是**每圈的时长**（不是累计值），与参考实现一致；最快/最慢只在**两圈以上**
 * 才给结论（参考实现同样如此，单圈没有比较对象）。
 *
 * 纯 JVM 逻辑，可脱离 Android 直接单测。
 */
object StopwatchLogic {

    /**
     * @param elapsedMs 已累计时长；运行中该值是**上次同步的快照**，实时值请用 [elapsedAt]。
     * @param startedAtMs 当前这一段的起点（同一时间基准，通常是 `SystemClock.elapsedRealtime()`）；
     *   暂停时为 null。
     * @param laps 每圈时长，单位毫秒。
     * @param lapAnchorMs 上一圈结束时的累计时长，用于计算本圈时长。
     */
    data class State(
        val elapsedMs: Long = 0L,
        val running: Boolean = false,
        val laps: List<Long> = emptyList(),
        val startedAtMs: Long? = null,
        val lapAnchorMs: Long = 0L,
    )

    /** 按当前时刻 [nowMs] 求实时累计时长；暂停/未开始时返回快照值。 */
    fun elapsedAt(state: State, nowMs: Long): Long {
        val startedAt = state.startedAtMs
        return if (state.running && startedAt != null) {
            (nowMs - startedAt).coerceAtLeast(0L)
        } else {
            state.elapsedMs
        }
    }

    /** 开始或继续；已在运行时保持原状。 */
    fun start(state: State, nowMs: Long): State =
        if (state.running) {
            state
        } else {
            state.copy(running = true, startedAtMs = nowMs - state.elapsedMs)
        }

    /** 暂停并冻结累计时长；未在运行时保持原状。 */
    fun pause(state: State, nowMs: Long): State =
        if (!state.running) {
            state
        } else {
            state.copy(elapsedMs = elapsedAt(state, nowMs), running = false, startedAtMs = null)
        }

    /**
     * 记一圈。**仅运行中有效**（与参考实现一致：暂停时那颗按钮的语义是复位）。
     *
     * 本圈时长 = 当前累计时长 − [State.lapAnchorMs]；不足 1ms 的重复点击会被忽略，
     * 避免列表里堆出一串 0ms 的圈。
     */
    fun lap(state: State, nowMs: Long): State {
        if (!state.running) return state
        val elapsed = elapsedAt(state, nowMs)
        val duration = elapsed - state.lapAnchorMs
        if (duration <= 0L) return state
        return state.copy(laps = state.laps + duration, lapAnchorMs = elapsed)
    }

    /** 复位到初始状态（清空计时与全部圈）。 */
    fun reset(): State = State()

    /** 最快圈下标；不足 2 圈返回 null。 */
    fun bestLapIndex(state: State): Int? = extremeLapIndex(state, fastest = true)

    /** 最慢圈下标；不足 2 圈返回 null。 */
    fun worstLapIndex(state: State): Int? = extremeLapIndex(state, fastest = false)

    private fun extremeLapIndex(state: State, fastest: Boolean): Int? {
        if (state.laps.size < 2) return null
        var bestIndex = 0
        for (index in state.laps.indices) {
            val candidate = state.laps[index]
            val current = state.laps[bestIndex]
            if (if (fastest) candidate < current else candidate > current) {
                bestIndex = index
            }
        }
        return bestIndex
    }

    /**
     * 时长格式化：不足 1 小时为 `mm:ss.cc`，达到 1 小时为 `h:mm:ss.cc`（百分秒）。
     *
     * 固定用 [Locale.US]：阿拉伯语等区域默认会输出阿拉伯-印度数字，秒表读数用 ASCII 数字
     * 更符合预期，也让单测跨区域稳定。
     */
    fun format(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val totalSeconds = safe / 1000
        val hundredths = (safe % 1000) / 10
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, hundredths)
        } else {
            String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
        }
    }
}
