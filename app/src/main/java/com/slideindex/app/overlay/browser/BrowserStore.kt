package com.slideindex.app.overlay.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 浏览器的历史与书签存储（JSON 文件，简单可靠）。
 *
 * - 历史：最多 [HISTORY_LIMIT] 条，超出淘汰最旧；同 URL 30 秒内去重。
 * - 书签：按 URL 去重，后写覆盖标题。
 */
internal object BrowserStore {

    private const val HISTORY_LIMIT = 500
    private const val DEDUPE_WINDOW_MS = 30_000L

    data class Entry(val url: String, val title: String, val timeMs: Long)

    private val history = mutableListOf<Entry>()
    private val bookmarks = mutableListOf<Entry>()
    private var loaded = false

    private fun historyFile(context: Context) = File(context.filesDir, "browser_history.json")
    private fun bookmarkFile(context: Context) = File(context.filesDir, "browser_bookmarks.json")

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        read(historyFile(context)).forEach { history.add(it) }
        read(bookmarkFile(context)).forEach { bookmarks.add(it) }
    }

    private fun read(file: File): List<Entry> {
        return runCatching {
            if (!file.exists()) return emptyList()
            val array = JSONArray(file.readText())
            List(array.length()) { index ->
                val obj = array.getJSONObject(index)
                Entry(
                    url = obj.optString("url"),
                    title = obj.optString("title"),
                    timeMs = obj.optLong("time"),
                )
            }.filter { it.url.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun write(file: File, items: List<Entry>) {
        runCatching {
            val array = JSONArray()
            items.forEach { entry ->
                array.put(
                    JSONObject()
                        .put("url", entry.url)
                        .put("title", entry.title)
                        .put("time", entry.timeMs),
                )
            }
            file.writeText(array.toString())
        }
    }

    @Synchronized
    fun record(context: Context, url: String, title: String) {
        if (url.isBlank() || url == "about:blank" || url.startsWith("xg://")) return
        ensureLoaded(context)
        val now = System.currentTimeMillis()
        val last = history.firstOrNull()
        if (last != null && last.url == url && now - last.timeMs < DEDUPE_WINDOW_MS) {
            // 同一个页面重复触发：只补一下标题
            if (title.isNotBlank() && last.title != title) {
                history[0] = last.copy(title = title)
                write(historyFile(context), history)
            }
            return
        }
        if (title.isNotBlank()) {
            val idx = history.indexOfFirst { it.url == url }
            if (idx >= 0) history.removeAt(idx)
        }
        history.removeAll { it.url == url }
        history.add(0, Entry(url, title, now))
        while (history.size > HISTORY_LIMIT) history.removeAt(history.size - 1)
        write(historyFile(context), history)
    }

    @Synchronized
    fun history(context: Context): List<Entry> {
        ensureLoaded(context)
        return history.toList()
    }

    @Synchronized
    fun clearHistory(context: Context) {
        ensureLoaded(context)
        history.clear()
        write(historyFile(context), history)
    }

    @Synchronized
    fun bookmarks(context: Context): List<Entry> {
        ensureLoaded(context)
        return bookmarks.toList()
    }

    @Synchronized
    fun isBookmarked(context: Context, url: String): Boolean {
        ensureLoaded(context)
        return bookmarks.any { it.url == url }
    }

    @Synchronized
    fun toggleBookmark(context: Context, url: String, title: String): Boolean {
        if (url.isBlank()) return false
        ensureLoaded(context)
        val existing = bookmarks.indexOfFirst { it.url == url }
        val added = if (existing >= 0) {
            bookmarks.removeAt(existing)
            false
        } else {
            bookmarks.add(0, Entry(url, title, System.currentTimeMillis()))
            true
        }
        write(bookmarkFile(context), bookmarks)
        return added
    }

    @Synchronized
    fun removeBookmark(context: Context, url: String) {
        ensureLoaded(context)
        bookmarks.removeAll { it.url == url }
        write(bookmarkFile(context), bookmarks)
    }
}
