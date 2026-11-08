package com.slideindex.app.quickmenu

/**
 * 倒计时状态机（「快捷菜单 → 倒计时」的计算核心）。
 *
 * 与 [StopwatchLogic] 同一套设计：把"当前时间"作为参数传入，状态本身不读时钟，
 * 因此暂停/继续/到点这些边界都能直接单测，UI 只负责每帧调 [remainingAt]。
 *
 * 参考：QuickDash（https://github.com/balajitechlabs/quickdash，GPL-3.0）的 CountdownContent
 * 提供过同类能力（预设时长 + 自定义 + 到点闹钟）。本实现按其行为规格独立重写成纯状态机。
 *
 * 纯 JVM 逻辑，可脱离 Android 直接单测。
 */
object CountdownLogic {

    /** 预设时长（分钟）；与参考实现的预设集合一致，另加 25 分钟（番茄钟常用）。 */
    val PRESET_MINUTES: List<Int> = listOf(1, 3, 5, 10, 25)

    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 120

    /**
     * @param durationMs 本轮设定的总时长。
     * @param remainingMs 已累计的剩余量；运行中它是**上次同步的快照**，实时值用 [remainingAt]。
     * @param endsAtMs 结束时刻（与 [nowMs] 同一时间基准，通常是 `SystemClock.elapsedRealtime()`）；
     *   暂停时为 null。
     */
    data class State(
        val durationMs: Long = 0L,
        val remainingMs: Long = 0L,
        val running: Boolean = false,
        val endsAtMs: Long? = null,
    )

    /** 设定一个时长（不自动开始）。 */
    fun preset(durationMs: Long): State {
        val safe = durationMs.coerceAtLeast(0L)
        return State(durationMs = safe, remainingMs = safe)
    }

    fun presetMinutes(minutes: Int): State = preset(clampMinutes(minutes) * 60_000L)

    fun clampMinutes(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)

    /** 开始或继续；已跑完（剩余为 0）或已在运行时保持原状。 */
    fun start(state: State, nowMs: Long): State =
        if (state.running || state.remainingMs <= 0L) {
            state
        } else {
            state.copy(running = true, endsAtMs = nowMs + state.remainingMs)
        }

    /** 暂停并冻结剩余量；未运行时保持原状。 */
    fun pause(state: State, nowMs: Long): State =
        if (!state.running) {
            state
        } else {
            state.copy(remainingMs = remainingAt(state, nowMs), running = false, endsAtMs = null)
        }

    /** 复位到本轮设定的时长（不自动开始）。 */
    fun reset(state: State): State =
        state.copy(remainingMs = state.durationMs, running = false, endsAtMs = null)

    /** 实时剩余量；暂停/未开始时返回快照值。 */
    fun remainingAt(state: State, nowMs: Long): Long {
        val endsAt = state.endsAtMs
        return if (state.running && endsAt != null) {
            (endsAt - nowMs).coerceAtLeast(0L)
        } else {
            state.remainingMs
        }
    }

    /** 是否刚好走到 0（用于"到点只记一次历史/只响一次"）。 */
    fun isExpired(state: State, nowMs: Long): Boolean =
        state.running && remainingAt(state, nowMs) <= 0L

    /** 进度（0f~1f），供进度条使用；时长设为 0 时返回 0。 */
    fun progress(state: State, nowMs: Long): Float {
        if (state.durationMs <= 0L) return 0f
        val remaining = remainingAt(state, nowMs)
        return (1f - remaining.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    }
}
