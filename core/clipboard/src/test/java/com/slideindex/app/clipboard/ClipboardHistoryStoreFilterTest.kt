package com.slideindex.app.clipboard

import android.app.Application
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 剪贴板固定筛选（`docs/capsule-refactor-plan.md` §0.16.4 待办 3）。
 *
 * 筛选是在 **SQL 层**做的（`ClipboardHistoryStore.count/queryPageBefore` 的 `sqlWhere`），
 * 因为库容量可以是"无限"，而面板只加载了前几页 —— 在内存里过滤「已加载的那几页」会让
 * 条数随滚动变化（就是这轮要修的病）。所以这里拿**真库**跑：既能测分类，也能测"条数与列表同源"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class ClipboardHistoryStoreFilterTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var store: ClipboardHistoryStore

    @Before
    fun setUp() {
        store = ClipboardHistoryStore(context, json)
        seed()
    }

    /**
     * 10 条覆盖各种形态。id 固定 + `CONFLICT_REPLACE`，所以重复 seed 是幂等的。
     *
     * 每条的 `entry_type` / `has_image` 都按 `ClipboardReader` 真实产出的组合来写
     * （见 `ClipboardHistoryFilter` 的文档表），不是随手编的。
     */
    private fun seed() {
        var clock = 1_700_000_000_000L
        fun insert(entry: ClipboardEntry) {
            store.insert(entry)
            clock += 1_000L
        }
        fun at() = clock

        insert(
            ClipboardEntry(
                id = "plain-text", type = ClipboardEntryType.TEXT, text = "hello world",
                createdAtEpochMs = at(),
            )
        )
        insert(
            ClipboardEntry(
                id = "link-text", type = ClipboardEntryType.TEXT, text = "https://example.com/a",
                createdAtEpochMs = at(),
            )
        )
        insert(
            ClipboardEntry(
                id = "link-www", type = ClipboardEntryType.TEXT, text = "www.example.com",
                createdAtEpochMs = at(),
            )
        )
        insert(
            ClipboardEntry(
                id = "html-only", type = ClipboardEntryType.HTML, text = "一段正文",
                htmlText = "<p>一段<b>正文</b></p>", createdAtEpochMs = at(),
            )
        )
        insert(
            // 浏览器复制链接：HTML + 文本都是链接 → 算「链接」，不算「富文本」。
            ClipboardEntry(
                id = "link-html", type = ClipboardEntryType.HTML, text = "https://example.com/b",
                htmlText = "<a href=\"https://example.com/b\">b</a>", createdAtEpochMs = at(),
            )
        )
        insert(
            ClipboardEntry(
                id = "image", type = ClipboardEntryType.URI, text = "",
                uri = "content://media/external/images/1", mimeType = "image/png",
                imageFileName = "1.png", imageFileNames = listOf("1.png"), createdAtEpochMs = at(),
            )
        )
        insert(
            // 图文混排（HTML + 图）：仍是「图片」，不能同时出现在「富文本」里。
            ClipboardEntry(
                id = "image-html", type = ClipboardEntryType.HTML, text = "配图说明",
                htmlText = "<p>配图<img src=\"x.png\"></p>", uri = "content://media/external/images/2",
                mimeType = "image/png", imageFileName = "2.png", imageFileNames = listOf("2.png"),
                createdAtEpochMs = at(),
            )
        )
        insert(
            ClipboardEntry(
                id = "file", type = ClipboardEntryType.URI, text = "report.pdf",
                uri = "content://downloads/1", mimeType = "application/pdf", createdAtEpochMs = at(),
            )
        )
        insert(
            ClipboardEntry(
                id = "intent", type = ClipboardEntryType.INTENT, text = "intent://scan/#Intent;end",
                intentUri = "intent://scan/#Intent;end", createdAtEpochMs = at(),
            )
        )
        insert(
            // 链接夹在长文中间：**刻意**不归入「链接」（SQL 侧做不到逐条跑正则，
            // 硬做就得回内存过滤已加载页，条数又会随滚动变）。这条也记录了该偏差。
            ClipboardEntry(
                id = "text-mention-link", type = ClipboardEntryType.TEXT,
                text = "看看这个 https://example.com 挺好", createdAtEpochMs = at(),
            )
        )
    }

    private fun ids(filter: ClipboardHistoryFilter): List<String> =
        store.queryPageBefore(createdBeforeMs = null, limit = 100, filter = filter)
            .map { it.id }
            .sorted()

    @Test
    fun `all returns everything`() {
        assertEquals(10, store.count(ClipboardHistoryFilter.All))
    }

    @Test
    fun `image matches by has_image so mixed rich entries stay in one bucket`() {
        assertEquals(2, store.count(ClipboardHistoryFilter.Image))
        assertEquals(listOf("image", "image-html"), ids(ClipboardHistoryFilter.Image))
    }

    @Test
    fun `file matches non image uri payloads`() {
        assertEquals(1, store.count(ClipboardHistoryFilter.File))
        assertEquals(listOf("file"), ids(ClipboardHistoryFilter.File))
    }

    @Test
    fun `rich text excludes link only html and image entries`() {
        assertEquals(1, store.count(ClipboardHistoryFilter.RichText))
        assertEquals(listOf("html-only"), ids(ClipboardHistoryFilter.RichText))
    }

    @Test
    fun `link matches whole entry links only`() {
        assertEquals(3, store.count(ClipboardHistoryFilter.Link))
        assertEquals(
            listOf("link-html", "link-text", "link-www"),
            ids(ClipboardHistoryFilter.Link),
        )
        // 长文里夹带的链接不算（见 seed 里的说明）。
        assertTrue("text-mention-link" !in ids(ClipboardHistoryFilter.Link))
    }

    @Test
    fun `the four categories never overlap`() {
        val buckets = listOf(
            ClipboardHistoryFilter.Image,
            ClipboardHistoryFilter.File,
            ClipboardHistoryFilter.RichText,
            ClipboardHistoryFilter.Link,
        ).map(::ids)
        val flat = buckets.flatten()
        assertEquals(flat.size, flat.toSet().size)
        // 未分类的只有「纯文本 / 夹带链接的文本 / intent」三条：分类是"看某一类"，不是分区。
        assertEquals(
            setOf("plain-text", "text-mention-link", "intent"),
            (ids(ClipboardHistoryFilter.All) - flat.toSet()).toSet(),
        )
    }

    @Test
    fun `intent entries only show up under all`() {
        val categorised = listOf(
            ClipboardHistoryFilter.Image,
            ClipboardHistoryFilter.File,
            ClipboardHistoryFilter.RichText,
            ClipboardHistoryFilter.Link,
        ).flatMap(::ids).toSet()
        assertTrue("intent" !in categorised)
        assertTrue("intent" in ids(ClipboardHistoryFilter.All))
    }

    @Test
    fun `count and page always agree`() {
        // 头部那个数字与列表必须是**同一个谓词**算出来的，否则用户会看到"写了 3 条却只列出 2 条"。
        ClipboardHistoryFilter.entries.forEach { filter ->
            assertEquals(filter.name, store.count(filter), ids(filter).size)
        }
    }

    @Test
    fun `keyset pagination keeps the filter while walking pages`() {
        val firstPage = store.queryPageBefore(
            createdBeforeMs = null,
            limit = 1,
            filter = ClipboardHistoryFilter.Link,
        )
        val cursor = firstPage.single().createdAtEpochMs
        val secondPage = store.queryPageBefore(
            createdBeforeMs = cursor,
            limit = 1,
            filter = ClipboardHistoryFilter.Link,
        )
        assertEquals(listOf("link-html"), firstPage.map { it.id })
        assertEquals(listOf("link-www"), secondPage.map { it.id })
    }

    @Test
    fun `deleting an entry drops it from the filtered count`() {
        store.delete("image")
        assertEquals(1, store.count(ClipboardHistoryFilter.Image))
        assertEquals(9, store.count(ClipboardHistoryFilter.All))
    }
}
