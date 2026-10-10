package com.slideindex.app.freezer

import com.slideindex.app.settings.FreezerAppIntent

/**
 * 意图表上的纯决策：给定「当前状态 + 记录的意图 + 全局工作模式」，决定这次该执行什么。
 *
 * 抽成纯函数是为了能单测——真正的特权调用（`pm disable` / `pm suspend`）在 CI 里跑不了。
 *
 * 规则：
 * 1. **没有意图**的成员（刚加入列表的活跃应用、外来的冻结包）按全局工作模式兜底；
 * 2. **有意图**的成员优先按意图还原；
 * 3. 已经是目标态的成员**不动**——避免为了「还原」反而把暂停态换成冻结（那正是这次要修的坑）。
 */
object FreezerIntentResolution {

    sealed interface Decision {
        /** 什么都不用做。 */
        data object Skip : Decision

        /** 冻结：停用应用，桌面图标消失。 */
        data object Freeze : Decision

        /** 暂停：挂起应用，桌面图标保留但变灰。 */
        data object Pause : Decision
    }

    fun decide(
        state: FreezerAppState,
        intent: FreezerAppIntent?,
        fallbackPause: Boolean,
    ): Decision {
        val targetPause = intent?.isPause ?: fallbackPause
        return if (targetPause) {
            // 已暂停不用动；已冻结的包要变成暂停，得先解冻 —— 那个中间态不值得为「还原意图」去踩。
            if (state.isActive) Decision.Pause else Decision.Skip
        } else {
            if (state.isFrozen) Decision.Skip else Decision.Freeze
        }
    }

    /** 这次动作把成员变成哪种意图；[Decision.Skip] 表示不改状态，因此也不该动意图表。 */
    fun intentFor(decision: Decision): FreezerAppIntent? = when (decision) {
        Decision.Freeze -> FreezerAppIntent.FROZEN
        Decision.Pause -> FreezerAppIntent.PAUSE
        Decision.Skip -> null
    }
}
