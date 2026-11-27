package com.slideindex.app.settings.macro

import com.slideindex.app.macro.Macro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 宏列表页的纯逻辑：
 * - 搜索过滤（按宏名、忽略大小写、子串匹配、空关键字全要）
 * - 空态判定（"还没有宏" vs "没搜到"由 [MacroListState.sourceIsEmpty] 区分）
 * - 必填名字校验（去掉首尾空白后不能为空）
 */
class MacroListStateTest {

    private fun macro(id: String, name: String) = Macro(
        id = id,
        name = name,
        targetPackage = "com.example.target",
    )

    private val macros = listOf(
        macro("1", "Daily check-in"),
        macro("2", "阅读签到"),
        macro("3", "Shopping Cart"),
    )

    @Test
    fun `空关键字返回全部并保持原顺序`() {
        val state = MacroListState(macros, "")
        assertEquals(listOf("1", "2", "3"), state.visible.map { it.id })
        assertFalse(state.isEmpty)
    }

    @Test
    fun `只有空白的关键字等同于空`() {
        val state = MacroListState(macros, "   ")
        assertEquals(3, state.visible.size)
    }

    @Test
    fun `忽略大小写匹配`() {
        val state = MacroListState(macros, "shopping")
        assertEquals(listOf("3"), state.visible.map { it.id })
    }

    @Test
    fun `子串匹配且匹配中文`() {
        assertEquals(listOf("2"), MacroListState(macros, "签到").visible.map { it.id })
        assertEquals(listOf("1"), MacroListState(macros, "check").visible.map { it.id })
    }

    @Test
    fun `关键字首尾空白被忽略`() {
        assertEquals(listOf("3"), MacroListState(macros, "  Cart  ").visible.map { it.id })
    }

    @Test
    fun `一条都没搜到时为空态但不是空列表`() {
        val state = MacroListState(macros, "zzz")
        assertTrue(state.visible.isEmpty())
        assertTrue(state.isEmpty)
        assertFalse(state.sourceIsEmpty)
    }

    @Test
    fun `全量为空时是还没有宏的空态`() {
        val state = MacroListState(emptyList(), "")
        assertTrue(state.isEmpty)
        assertTrue(state.sourceIsEmpty)
    }

    @Test
    fun `copy 语义保留另一半字段`() {
        val state = MacroListState(macros, "cart")
        assertEquals("cart", state.withMacros(emptyList()).query)
        // 空关键字 = 全都要（与 `空关键字返回全部并保持原顺序` 一致）：withQuery 只换关键字，
        // 原全量列表必须原样留着。这里原先写的是 emptyList()，与它自己的语义相反。
        assertEquals(macros, state.withQuery("").macros)
    }

    @Test
    fun `名字非空即可保存`() {
        assertTrue(MacroValidation.isNameValid("签到"))
        assertTrue(MacroValidation.isNameValid("  a  "))
        assertFalse(MacroValidation.isNameValid(""))
        assertFalse(MacroValidation.isNameValid("   "))
    }

    @Test
    fun `保存前去掉名字首尾空白`() {
        assertEquals("签到", MacroValidation.normalizeName("  签到  "))
        assertEquals("", MacroValidation.normalizeName("   "))
    }

    @Test
    fun `编辑页保存与开始录制都只要名字`() {
        val fresh = MacroEditState(macroId = "id")
        assertFalse(fresh.canSave)
        assertFalse(fresh.canStartRecording)

        val named = fresh.copy(name = "签到")
        assertTrue(named.canSave)
        // 目标应用可选：没选 = 全局宏，一样能开始录制（从当前界面开始）。
        assertTrue(named.canStartRecording)
        assertFalse(named.hasTargetPackage)

        val ready = named.copy(targetPackage = "com.example.target")
        assertTrue(ready.canSave)
        assertTrue(ready.canStartRecording)
        assertTrue(ready.hasTargetPackage)
    }

    @Test
    fun `编辑页只有空白名字不算填好`() {
        val state = MacroEditState(name = "   ", targetPackage = "com.example.target")
        assertFalse(state.hasName)
        assertFalse(state.canSave)
        assertFalse(state.canStartRecording)
    }
}
