package com.slideindex.app.overlay.history

import java.util.concurrent.atomic.AtomicReference

/**
 * Deep link 打开收纳面板时的待应用搜索。
 *
 * 不用 StateFlow.collect：面板退出动画期间旧组合仍可能在 collect，会把新 pending 偷走并 clear，
 * 导致新进入的 [HistoryPanelScreen] 拿不到 query。
 */
object StashPanelLaunchState {
    private val pending = AtomicReference<HistorySearchBootstrap?>(null)

    /** show() 时递增；组合侧用它触发一次性 consume。 */
    @Volatile
    var epoch: Int = 0
        private set

    fun setPendingSearch(tabOrdinal: Int, query: String) {
        val q = query.trim().takeIf { it.isNotEmpty() } ?: return
        pending.set(HistorySearchBootstrap(tabOrdinal = tabOrdinal, query = q))
        epoch++
    }

    fun consumePendingSearch(): HistorySearchBootstrap? = pending.getAndSet(null)

    fun clearPendingSearch() {
        pending.set(null)
    }
}
