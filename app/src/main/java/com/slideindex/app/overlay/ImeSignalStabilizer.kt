package com.slideindex.app.overlay

/**
 * IME 可见性信号的"防抖 + 自身回声抑制 + 到点复核"。
 *
 * ## 它解决什么
 *
 * 检测 IME 只能靠在**窗口事件**里采一次样（[ImeBoundsDetector]）。但"窗口事件"里有一大批
 * 是我们**自己**造成的：只要协调器按采样结果去显示/隐藏/重排自己的浮窗，就会产生新的
 * `TYPE_WINDOWS_CHANGED`，于是下一次采样看到的是自己刚才那一下的余波。这是典型的
 * 测量回路自激 —— 2026-10-10 在真机上实测到的"胶囊每秒闪 3 次"就是它。
 *
 * 三道闸门：
 * 1. **防抖**：新状态必须连续保持 [confirmMs]（默认 200ms）才提交；信号在真/假之间来回跳
 *    （我们自己的窗口引起的抖动正长这样）时永远不提交，浮窗就地不动。
 * 2. **自身回声抑制**：每次提交后 [quietMs]（默认 400ms）内一律不看采样，挡掉自己增删窗口
 *    引发的那一串事件。
 * 3. **到点复核**（[nextWakeAtMs] + [tick]）：⚠️ 采样只在窗口事件到来时发生，而"键盘收起"
 *    之后系统**不再产生任何窗口事件**。若只靠"下一次采样"来确认候选，最后一跳会永远卡住：
 *    真机实测（短信输入框）按返回收起键盘后，`mInputShown=false`、无障碍窗口表也空了，
 *    但我们**一条 HIDE 都没发**，胶囊一直挂着；点它一下（点击带来窗口事件）才立刻消失。
 *    所以调用方必须按 [nextWakeAtMs] 定时回来调 [tick]。
 *
 * 纯逻辑、可单测：时间由调用方以 `nowMs`（`SystemClock.uptimeMillis()`）传入，本类不读时钟。
 */
internal class ImeSignalStabilizer(
    private val confirmMs: Long = DEFAULT_CONFIRM_MS,
    private val quietMs: Long = DEFAULT_QUIET_MS,
) {

    /** 已提交的 IME 可见性。调用方只应信这个值，不要信单次采样。 */
    var committedVisible: Boolean = false
        private set

    /** 正在等待确认的候选状态；与 [committedVisible] 一致时清空。 */
    private var pendingVisible: Boolean? = null
    private var pendingSinceMs: Long = 0L

    /** 自身回声静默期截止时刻。 */
    private var quietUntilMs: Long = 0L

    /**
     * 喂一次采样。
     *
     * 候选在**静默期内也会记账**（只是提交被推迟到静默期结束）——否则"提交后 400ms 内键盘就被
     * 收起、之后再无事件"这种情况又会卡住（见 [tick] 的注释）。
     *
     * @return true 表示 [committedVisible] 刚刚翻转，调用方**此刻才**应该真正显示/隐藏浮窗。
     */
    fun onSample(sampleVisible: Boolean, nowMs: Long): Boolean {
        if (sampleVisible == committedVisible) {
            // 与已提交状态一致：丢掉候选（顶缘跟随仍可走别的分支）。
            pendingVisible = null
            return false
        }
        if (pendingVisible != sampleVisible) {
            pendingVisible = sampleVisible
            pendingSinceMs = nowMs
        }
        return tick(nowMs)
    }

    /**
     * 到点复核：不依赖"下一次窗口事件"就能把候选提交掉。
     *
     * 调用方应在 [nextWakeAtMs] 给出的时刻回来调本方法（见类注释第 3 条）。
     *
     * @return true 表示 [committedVisible] 刚刚翻转。
     */
    fun tick(nowMs: Long): Boolean {
        val pending = pendingVisible ?: return false
        if (pending == committedVisible) {
            pendingVisible = null
            return false
        }
        if (nowMs < quietUntilMs) return false
        if (nowMs - pendingSinceMs < confirmMs) return false
        committedVisible = pending
        pendingVisible = null
        quietUntilMs = nowMs + quietMs
        return true
    }

    /**
     * 下一次需要回来 [tick] 的时刻；没有候选时为 null（调用方据此取消已排的定时）。
     *
     * 只需关心两个时刻里较晚的那个：候选满 [confirmMs]，或自身回声静默期结束。
     */
    fun nextWakeAtMs(): Long? {
        val pending = pendingVisible ?: return null
        if (pending == committedVisible) return null
        return maxOf(pendingSinceMs + confirmMs, quietUntilMs)
    }

    /**
     * 复位（设置回流、前台被屏蔽、服务重建）。不产生提交，不进入静默期。
     *
     * ⚠️ 调用方在复位后若要"确保已隐藏"，请自行发一次隐藏；
     * 本方法只负责让状态机和真实世界重新对齐。
     */
    fun reset(visible: Boolean, nowMs: Long) {
        committedVisible = visible
        pendingVisible = null
        pendingSinceMs = nowMs
        quietUntilMs = 0L
    }

    companion object {
        /** 新状态需连续保持的时长，覆盖键盘弹出/收回动画与 100ms 级事件合并。 */
        const val DEFAULT_CONFIRM_MS = 200L

        /** 提交后的静默期：我们自己的窗口增删所引发的窗口事件大约在 100~250ms 后到达。 */
        const val DEFAULT_QUIET_MS = 400L
    }
}
