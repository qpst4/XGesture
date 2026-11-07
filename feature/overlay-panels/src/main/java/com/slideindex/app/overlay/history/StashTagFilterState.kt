package com.slideindex.app.overlay.history

import androidx.compose.runtime.mutableStateOf

/**
 * 闪念页签的**标签筛选状态**（多选 + 匹配方式）。
 *
 * 为什么是**进程级单例**而不是 `remember { ... }`、也不是 ViewModel 里的 state：
 * 面板是一个 overlay 窗，用户关掉它（或被系统摘掉）之后下次打开是**全新的 ComposeView +
 * 全新的 ViewModelStore + 全新的 ViewModel** —— 状态放在组合或 ViewModel 里都会跟着没，
 * 用户实际感受就是"关掉再打开，我选的标签没了"。
 *
 * 这和 [StashComposerDraft] / [EditSessionDraft] 是**同一套做法**（见 `StashComposerDraft` 的 KDoc），
 * 只是它们管"草稿"，本对象管"筛选"：
 * - UI 侧可以直接读写这里的 `MutableState`（读写点本身照旧是 snapshot-aware 的）；
 * - `HistoryPanelViewModel` 侧用 `snapshotFlow { ... }` 把它桥成 Flow，喂给
 *   `filteredStashEntries` 参与 combine。
 *
 * ⚠️ 只保存**筛选条件**，不保存任何条目数据 —— 数据永远是 `StashRepository` / `StashMetaStore` 的。
 */
object StashTagFilterState {

    /**
     * 选中的标签名（多选）。
     *
     * **空集 = 「全部」**：与老的单选实现 `selectedTag == null` 语义完全一致，
     * 所以"没选任何标签"时整条筛选链路的行为与本次改动前**逐字相同**。
     */
    val selectedTags = mutableStateOf<Set<String>>(emptySet())

    /**
     * 多标签的匹配方式：
     * - `true` = **同时含全部**选中标签（AND，**默认**，需求 2）；
     * - `false` = 含**任一**选中标签（OR，「全部 / 任一」开关切到右边）。
     *
     * 只在选中 ≥2 个标签时才有区别（选中 1 个时 AND 与 OR 等价），所以开关也只在 ≥2 时才露面。
     */
    val matchAll = mutableStateOf(true)

    /**
     * 「收藏」筛选：只看**已加星标**的条目。
     *
     * 语义上它就是一枚"内置标签"：卡片加星标 = 自动进「收藏」，取消星标 = 自动移出；
     * 与标签筛选是 **AND 叠加**（"工作标签 + 已星标"），互不排斥。点「全部」/「清除」时一起复位。
     */
    val onlyStarred = mutableStateOf(false)

    /** 点「收藏」= 切换只看已星标。 */
    fun toggleStarred() {
        onlyStarred.value = !onlyStarred.value
    }

    /** 点标签 = 切换选中；再点已选中的 = 取消（需求 1）。 */
    fun toggleTag(name: String) {
        val current = selectedTags.value
        selectedTags.value = if (name in current) current - name else current + name
    }

    /** 一键清空选中（「清除」胶囊 / 「全部」胶囊 / 存下新条目后的收尾都走它）。 */
    fun clear() {
        selectedTags.value = emptySet()
        onlyStarred.value = false
    }

    /** 切「全部（AND）/ 任一（OR）」。 */
    fun setMatchAll(value: Boolean) {
        matchAll.value = value
    }
}
