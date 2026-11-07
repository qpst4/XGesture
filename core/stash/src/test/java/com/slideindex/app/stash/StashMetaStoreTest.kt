package com.slideindex.app.stash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `stash_meta.json` 的兼容红线（`docs/capsule-refactor-plan.md` §3.3）：
 * 这个文件**读失败会让所有写入被禁用**（见 `StashMetaRepository.writeToDisk`），
 * 所以"老版本能读懂新文件、新版本能读懂老文件"必须有用例兜着。
 */
class StashMetaStoreTest {

    @Test
    fun `all fields survive a round trip`() {
        val store = StashMetaStore(
            tags = listOf(StashTag("工作", 0xFF5B8DF6, 0)),
            assignments = mapOf("a" to listOf("工作", "待办")),
            doneAt = mapOf("a" to 1_700_000_000_000L),
            appends = mapOf("a" to listOf(StashAppend("补一句", 1_700_000_000_001L))),
            sources = mapOf("b" to StashMetaRepository.SOURCE_CLIPBOARD),
            reminders = mapOf("c" to 1_800_000_000_000L),
        )
        val decoded = StashMetaJson.decodeFromString<StashMetaStore>(
            StashMetaJson.encodeToString(store),
        )
        assertEquals(store, decoded)
        assertTrue(decoded.isDone("a"))
        assertEquals(listOf("工作", "待办"), decoded.tagsOf("a"))
        assertEquals("补一句", decoded.appendsOf("a").single().text)
        assertEquals(StashMetaRepository.SOURCE_CLIPBOARD, decoded.sourceOf("b"))
        assertEquals(1_800_000_000_000L, decoded.reminderOf("c"))
    }

    @Test
    fun `a file written before reminders existed still decodes`() {
        // 更老的形状：没有 reminders（也没有 sources）这个键。
        val legacy = """
            {"version":1,"tags":[],"assignments":{},"doneAt":{},"appends":{},
             "sources":{"a":"pick"}}
        """.trimIndent()
        val decoded = StashMetaJson.decodeFromString<StashMetaStore>(legacy)
        assertTrue(decoded.reminders.isEmpty())
        assertNull(decoded.reminderOf("a"))
        assertEquals("pick", decoded.sourceOf("a"))
    }

    @Test
    fun `a file written before sources existed still decodes`() {
        // v1.36 及以前落盘的形状：没有 sources 这个键。
        val legacy = """
            {"version":1,"tags":[{"name":"待办","colorArgb":134,"order":2}],
             "assignments":{"a":["待办"]},"doneAt":{"a":1},"appends":{}}
        """.trimIndent()
        val decoded = StashMetaJson.decodeFromString<StashMetaStore>(legacy)
        assertEquals(listOf("待办"), decoded.tagsOf("a"))
        assertEquals(1L, decoded.doneAt.getValue("a"))
        assertTrue(decoded.sources.isEmpty())
        assertNull(decoded.sourceOf("a"))
    }

    @Test
    fun `unknown keys and unknown source values do not break decoding`() {
        // 新版本写了我们还不认识的字段 / 来源键时，老版本必须能读下去（读不懂就当没有）。
        val future = """
            {"version":2,"tags":[],"assignments":{},"doneAt":{},"appends":{},
             "sources":{"a":"web"},"reminders":{"a":123},"colors":{"x":"#fff"}}
        """.trimIndent()
        val decoded = StashMetaJson.decodeFromString<StashMetaStore>(future)
        assertEquals("web", decoded.sourceOf("a"))
        assertEquals(2, decoded.version)
    }

    @Test
    fun `defaults are written out so the file is self describing`() {
        val encoded = StashMetaJson.encodeToString(StashMetaStore())
        assertTrue(encoded.contains("\"sources\""))
        assertTrue(encoded.contains("\"appends\""))
        assertTrue(encoded.contains("\"doneAt\""))
    }
}
