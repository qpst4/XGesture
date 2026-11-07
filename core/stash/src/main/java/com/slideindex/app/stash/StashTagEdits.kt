package com.slideindex.app.stash

/**
 * 标签定义与标签绑定的**纯逻辑**（`docs/capsule-refactor-plan.md` §0.16.4 待办 2）。
 *
 * 为什么单独拎出来：标签改名必须**连带改写所有条目的绑定**，否则旧名会变成孤儿
 * （`tags` 里没有它、`assignments` 里还留着它，筛选行和卡片 chip 就对不上了）。
 * 这种"两份数据必须一起动"的逻辑放在 [StashMetaRepository] 里只能靠 Robolectric 测，
 * 放在这里就是纯函数，单测直接跑（见 `StashTagEditsTest`）。
 *
 * 约定：每个函数都返回**新的 store**；返回 `null` = **什么都没变**（不该写盘、也不该广播），
 * 调用方据此给 UI 反馈（例如"已有同名标签"）。
 */
object StashTagEdits {

    /** 「待办」是硬编码关键字（完成态 / `isTodo` / 把手 `pendingTodoCount` 全靠它），改名与删除一律拒绝。 */
    const val PROTECTED_TAG_NAME: String = StashMetaRepository.TODO_TAG_NAME

    fun isProtected(name: String): Boolean = name.trim() == PROTECTED_TAG_NAME

    /** 新增一枚标签定义。重名 / 空名 → `null`。 */
    fun add(store: StashMetaStore, name: String, colorArgb: Long): StashMetaStore? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || store.tags.any { it.name == trimmed }) return null
        return store.copy(tags = store.tags + StashTag(trimmed, colorArgb, store.tags.size))
    }

    /**
     * 删除标签定义，**同时清掉所有条目对它的绑定**（否则筛选行没了、卡片上还挂着一枚孤儿 chip）。
     * 「待办」拒绝删除。
     */
    fun remove(store: StashMetaStore, name: String): StashMetaStore? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || isProtected(trimmed)) return null
        if (store.tags.none { it.name == trimmed }) return null
        return store.copy(
            tags = store.tags.filterNot { it.name == trimmed },
            assignments = store.assignments
                .mapValues { (_, names) -> names - trimmed }
                .filterValues { it.isNotEmpty() },
        )
    }

    /**
     * 改名 + **改写全部绑定**。
     *
     * - 目标名已存在 → 视为**合并**：旧定义删掉，绑定并到目标标签上（保留目标标签的颜色与排序）；
     *   不这么处理的话，两条同名标签会同时存在于 `tags` 里，筛选行会出现两枚一模一样的 chip。
     * - 新旧名只要沾到「待办」就拒绝（改名过去 = 把一批条目变成待办；从「待办」改走 = 把
     *   `pendingTodoCount` 的语义悄悄废掉）。
     */
    fun rename(store: StashMetaStore, oldName: String, newName: String): StashMetaStore? {
        val from = oldName.trim()
        val to = newName.trim()
        if (from.isEmpty() || to.isEmpty() || from == to) return null
        if (isProtected(from) || isProtected(to)) return null
        if (store.tags.none { it.name == from }) return null
        val mergeInto = store.tags.any { it.name == to }
        val tags = if (mergeInto) {
            store.tags.filterNot { it.name == from }
        } else {
            // 就地改名：排序权重与颜色都留着（用户只改了名字）。
            store.tags.map { if (it.name == from) it.copy(name = to) else it }
        }
        val assignments = store.assignments
            .mapValues { (_, names) ->
                // 就地替换 + 去重：改名不该顺手把卡片上的标签顺序也改了（合并时两份同名会并成一个）。
                names.map { if (it == from) to else it }.distinct()
            }
            .filterValues { it.isNotEmpty() }
        return store.copy(tags = tags, assignments = assignments)
    }

    /** 改色。**「待办」也允许**：颜色只影响观感，不参与关键字判定。 */
    fun setColor(store: StashMetaStore, name: String, colorArgb: Long): StashMetaStore? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val target = store.tags.firstOrNull { it.name == trimmed } ?: return null
        if (target.colorArgb == colorArgb) return null
        return store.copy(
            tags = store.tags.map { if (it.name == trimmed) it.copy(colorArgb = colorArgb) else it },
        )
    }

    /**
     * 把一枚标签挪到 [targetIndex]（**排序**用，下一步的长按拖拽就落在这里）。
     *
     * - 先按 `order` 排好再动，最后**原子地重写 0..n-1**：只改"被移动那一枚"的 order 会留下
     *   重复或空洞的权重（比如两枚都是 2），之后 `sortedBy` 的先后就成了实现细节，
     *   UI 上表现为"拖了但没动"或"顺序自己跳"。顺带也把历史遗留的脏 order 修好。
     * - [targetIndex] 越界就夹到两端；夹完等于原位 = 没变化 → `null`。
     * - 「待办」**可以**移动：只读只针对改名与删除，位置不影响关键字语义。
     */
    fun move(store: StashMetaStore, name: String, targetIndex: Int): StashMetaStore? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val ordered = store.tags.sortedBy { it.order }
        val from = ordered.indexOfFirst { it.name == trimmed }
        if (from < 0) return null
        val to = targetIndex.coerceIn(0, ordered.size - 1)
        if (to == from) return null
        val reordered = ordered.toMutableList().apply { add(to, removeAt(from)) }
        return store.copy(tags = reordered.mapIndexed { index, tag -> tag.copy(order = index) })
    }
}
