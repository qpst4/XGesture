package com.slideindex.app.clipboard

import java.nio.charset.StandardCharsets
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 单条剪贴板记录的存储上限。
 *
 * 为什么需要它：历史库把整条 [ClipboardEntry] 序列化进 `entry_json`，而 SQLite 的
 * `CursorWindow` 对**单行**有上限（常见 2MB，部分 ROM 更小）。一行超过窗口时，
 * 读表会抛 `SQLiteBlobTooBigException`，且这个异常发生在 `moveToNext()` 里，
 * 调用方无法把 cursor 救回来（issue #63）。
 *
 * 所以写入前就要把记录压到窗口以下；已经写进去的坏行由
 * [ClipboardHistoryStore] 在开库/读表时清掉或过滤。
 */
object ClipboardEntrySizeLimit {

    /**
     * `entry_json` 的 UTF-8 字节上限。
     *
     * 1MB 是给 2MB 的 CursorWindow 留一半余量：JSON 之外还有 cursor 自身的元数据，
     * 而且不同厂商的窗口大小并不统一。
     */
    const val MAX_ENTRY_JSON_BYTES = 1_000_000

    /**
     * 纯文本字符上限。和 ClipShare 的 `recordMaxLength` 默认值一致（20 万字符）。
     *
     * 注意按 UTF-8 字节判断的 [MAX_ENTRY_JSON_BYTES] 才是硬约束；这个上限是为了
     * 避免把几十万字的代码塞进历史列表，导致列表/搜索也变慢。
     */
    const val MAX_TEXT_CHARS = 200_000

    fun isTextTooLarge(text: String): Boolean = text.length > MAX_TEXT_CHARS

    fun isTextTooLarge(payload: ClipboardPayload): Boolean =
        isTextTooLarge(payload.text)

    /**
     * 返回可以入库的条目；返回 null 表示这条记录应该直接跳过。
     *
     * 顺序很重要：先丢 HTML（大段 HTML/base64 是 JSON 膨胀的主因），再丢文字块，
     * 最后还超限就整条不存。不做“截断正文”是因为截断后的代码粘出去是错的，
     * 比不记录更糟。
     */
    fun sanitize(entry: ClipboardEntry, json: Json): ClipboardEntry? {
        if (isTextTooLarge(entry.text)) return null

        if (fits(entry, json)) return entry

        val withoutHtml = if (entry.htmlText != null) entry.copy(htmlText = null) else entry
        if (fits(withoutHtml, json)) return withoutHtml

        val withoutTextBlocks = withoutHtml.copy(
            contentBlocks = withoutHtml.contentBlocks.filterNot { it.kind == ClipboardBlockKind.TEXT }
        )
        if (fits(withoutTextBlocks, json)) return withoutTextBlocks

        return null
    }

    fun fits(entry: ClipboardEntry, json: Json): Boolean =
        json.encodeToString(entry).toByteArray(StandardCharsets.UTF_8).size <= MAX_ENTRY_JSON_BYTES
}
