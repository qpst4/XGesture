package com.slideindex.app.stash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 标签增删改的纯逻辑（`docs/capsule-refactor-plan.md` §0.16.4 待办 2）。
 *
 * 重点不是"能改名"，而是**改名必须连带改写所有条目的绑定**：只改 `tags` 不改 `assignments`，
 * 旧名就变成孤儿（筛选行里没有它、卡片脚注上还挂着一枚点不亮的 chip），而且没有任何报错。
 */
class StashTagEditsTest {

    private val work = StashTag("工作", 0xFF5B8DF6, 0)
    private val idea = StashTag("想法", 0xFFF0A93B, 1)
    private val todo = StashTag(StashTagEdits.PROTECTED_TAG_NAME, 0xFF57B87A, 2)

    private fun store(
        tags: List<StashTag> = listOf(work, idea, todo),
        assignments: Map<String, List<String>> = mapOf(
            "a" to listOf("工作", "想法"),
            "b" to listOf("工作"),
            "c" to listOf("待办"),
        ),
    ) = StashMetaStore(tags = tags, assignments = assignments)

    @Test
    fun `rename rewrites every binding so no orphan name is left behind`() {
        val next = StashTagEdits.rename(store(), "工作", "项目")!!
        assertEquals(listOf("项目", "想法", "待办"), next.tags.map { it.name })
        // 排序权重与颜色跟着走（用户只改了名字）。
        assertEquals(0, next.tags.first { it.name == "项目" }.order)
        assertEquals(0xFF5B8DF6, next.tags.first { it.name == "项目" }.colorArgb)
        assertEquals(listOf("项目", "想法"), next.tagsOf("a"))
        assertEquals(listOf("项目"), next.tagsOf("b"))
        // 旧名一个都不剩（否则筛选行会多出一枚空标签）。
        assertTrue(next.assignments.values.none { "工作" in it })
    }

    @Test
    fun `rename onto an existing name merges instead of creating two same name tags`() {
        val next = StashTagEdits.rename(store(), "工作", "想法")!!
        assertEquals(listOf("想法", "待办"), next.tags.map { it.name })
        // 挂过「工作」的条目现在挂「想法」，而且不会出现两个「想法」。
        assertEquals(listOf("想法"), next.tagsOf("a"))
        assertEquals(listOf("想法"), next.tagsOf("b"))
        // 被合并的目标保留自己的颜色与排序（不是被来源覆盖）。
        assertEquals(1, next.tags.first { it.name == "想法" }.order)
        assertEquals(0xFFF0A93B, next.tags.first { it.name == "想法" }.colorArgb)
    }

    @Test
    fun `rename reports no change for blank, identical or unknown names`() {
        assertNull(StashTagEdits.rename(store(), "工作", "工作"))
        assertNull(StashTagEdits.rename(store(), "工作", "  "))
        assertNull(StashTagEdits.rename(store(), "不存在", "项目"))
    }

    @Test
    fun `todo keyword can neither be renamed away nor renamed into`() {
        // 改走 = 悄悄废掉「完成」按钮与把手变色；改进来 = 一批普通条目突然变成待办。
        assertNull(StashTagEdits.rename(store(), "待办", "任务"))
        assertNull(StashTagEdits.rename(store(), "工作", "待办"))
        assertTrue(StashTagEdits.isProtected("待办"))
        assertTrue(StashTagEdits.isProtected(" 待办 "))
        assertFalse(StashTagEdits.isProtected("待办事项"))
    }

    @Test
    fun `remove drops the definition and every binding`() {
        val next = StashTagEdits.remove(store(), "工作")!!
        assertEquals(listOf("想法", "待办"), next.tags.map { it.name })
        assertEquals(listOf("想法"), next.tagsOf("a"))
        // 「b」只挂着被删的标签 → 整个条目从 assignments 里消失，不留空列表。
        assertTrue("b" !in next.assignments)
    }

    @Test
    fun `remove refuses the todo keyword and unknown names`() {
        assertNull(StashTagEdits.remove(store(), "待办"))
        assertNull(StashTagEdits.remove(store(), "不存在"))
    }

    @Test
    fun `add appends with the next order and refuses duplicates`() {
        val next = StashTagEdits.add(store(), " 读书 ", 0xFF4BC0C8)!!
        assertEquals(listOf("工作", "想法", "待办", "读书"), next.tags.map { it.name })
        assertEquals(3, next.tags.last().order)
        assertNull(StashTagEdits.add(store(), "想法", 0xFF000000))
        assertNull(StashTagEdits.add(store(), "   ", 0xFF000000))
    }

    @Test
    fun `set color is allowed for the todo keyword and reports no-ops`() {
        val next = StashTagEdits.setColor(store(), StashTagEdits.PROTECTED_TAG_NAME, 0xFF8A93A8)!!
        assertEquals(0xFF8A93A8, next.tags.first { it.name == "待办" }.colorArgb)
        // 颜色只影响观感，关键字语义由名字决定 —— 所以「待办」可以改色。
        assertNull(StashTagEdits.setColor(store(), "工作", 0xFF5B8DF6))
        assertNull(StashTagEdits.setColor(store(), "不存在", 0xFF8A93A8))
    }

    /* ---------------- 排序（§0.16.5，UI 拖拽的落点） ---------------- */

    private fun namesInOrder(store: StashMetaStore) =
        store.tags.sortedBy { it.order }.map { it.name }

    @Test
    fun `move rewrites every order so the sequence stays 0 to n-1`() {
        // 起始 order = [工作 0, 想法 1, 待办 2]
        val toFront = StashTagEdits.move(store(), "待办", 0)!!
        assertEquals(listOf("待办", "工作", "想法"), namesInOrder(toFront))
        assertEquals(listOf(0, 1, 2), toFront.tags.sortedBy { it.order }.map { it.order })

        val toBack = StashTagEdits.move(store(), "工作", 2)!!
        assertEquals(listOf("想法", "待办", "工作"), namesInOrder(toBack))
        assertEquals(listOf(0, 1, 2), toBack.tags.sortedBy { it.order }.map { it.order })
    }

    @Test
    fun `move into the middle and out of range both land somewhere sane`() {
        assertEquals(
            listOf("工作", "待办", "想法"),
            namesInOrder(StashTagEdits.move(store(), "待办", 1)!!),
        )
        // 越界夹到两端，越界后等于原位 = 没变化。
        assertEquals(
            listOf("待办", "工作", "想法"),
            namesInOrder(StashTagEdits.move(store(), "待办", -5)!!),
        )
        assertEquals(
            listOf("想法", "待办", "工作"),
            namesInOrder(StashTagEdits.move(store(), "工作", 99)!!),
        )
        assertNull(StashTagEdits.move(store(), "工作", 0))
    }

    @Test
    fun `move reports no change for unknown names and heals dirty orders`() {
        assertNull(StashTagEdits.move(store(), "不存在", 0))
        // 历史遗留：两枚标签都是 order 2（合并/手改过文件）。重排一次就修好成 0..n-1。
        val dirty = StashMetaStore(
            tags = listOf(
                StashTag("工作", 0xFF5B8DF6, 2),
                StashTag("想法", 0xFFF0A93B, 2),
                StashTag("灵感", 0xFFC77DF0, 2),
            ),
        )
        val fixed = StashTagEdits.move(dirty, "灵感", 0)!!
        assertEquals(listOf("灵感", "工作", "想法"), namesInOrder(fixed))
        assertEquals(listOf(0, 1, 2), fixed.tags.sortedBy { it.order }.map { it.order })
    }

    @Test
    fun `move is allowed for the todo keyword`() {
        // 只读只针对改名与删除；位置不影响 pendingTodoCount 的关键字语义。
        assertEquals(
            listOf("待办", "工作", "想法"),
            namesInOrder(StashTagEdits.move(store(), StashTagEdits.PROTECTED_TAG_NAME, 0)!!),
        )
    }
}
