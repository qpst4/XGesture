package com.slideindex.app.overlay.history

/**
 * 「上次停留在哪个页签」—— 只服务「指示条打开面板」那条设置里的 "记住上次页签" 一档。
 *
 * ⚠️ 为什么是**进程级单例**，而不是 ViewModel 里的状态：面板是 overlay 窗，关掉 / 宿主窗被系统
 * 摘掉之后，下次打开是**全新的 ViewModelStore + 全新的 ViewModel**（同 `HistoryPanelViewModel`
 * 里 `selectedTab` 那条注释），状态放 VM 里会跟着没。与 `StashTagFilterState` /
 * `StashComposerDraft` 是同一套做法。
 *
 * 只记**进程内**：App 进程被杀 / 重启后回到「闪念」。要跨重启就得落到 settings
 * （`StashPanelHandleOpenTab` 所在的偏好里加一个 last-tab 键），那是另一件事。
 */
internal object StashPanelTabMemory {
    @Volatile
    var tab: HistoryPanelTab = HistoryPanelTab.Stash
        private set

    fun remember(value: HistoryPanelTab) {
        tab = value
    }
}
