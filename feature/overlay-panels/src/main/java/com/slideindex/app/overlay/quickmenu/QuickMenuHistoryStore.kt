package com.slideindex.app.overlay.quickmenu

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.quickMenuHistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "slide_index_quick_menu_history",
)

/**
 * 一条计时历史（秒表或倒计时）。
 *
 * 字段**只放数据**，不放需要本地化的文案：「秒表 / 倒计时」「N 段」这类描述由 UI 组织，
 * 这样加语言不需要动存储格式。
 */
@Serializable
data class QuickMenuHistoryEntry(
    val kind: Kind,
    val totalMs: Long,
    /** 仅秒表有：每圈时长。 */
    val lapsMs: List<Long> = emptyList(),
    val atEpochMs: Long,
) {
    @Serializable
    enum class Kind {
        STOPWATCH,
        TIMER,
    }
}

/**
 * 历史记录的收敛规则。
 *
 * 抽成纯函数是为了可单测：历史是"看一眼"的东西，损坏或超量都不该让页面打不开。
 */
object QuickMenuHistoryCodec {

    const val MAX_ENTRIES = 20

    /** 新条目插到最前，并顺手做一次 [sanitize]。 */
    fun add(
        current: List<QuickMenuHistoryEntry>,
        entry: QuickMenuHistoryEntry,
    ): List<QuickMenuHistoryEntry> = sanitize(listOf(entry) + current)

    /**
     * 丢弃无效值（时长为 0/负、时间戳为 0）、按时间倒序、截断到 [MAX_ENTRIES]。
     *
     * `sortedByDescending` 而不是依赖插入顺序：DataStore 的数据可能被手工改过或被备份恢复，
     * 排序保证展示顺序始终是"最近的在前"。
     */
    fun sanitize(entries: List<QuickMenuHistoryEntry>): List<QuickMenuHistoryEntry> =
        entries.asSequence()
            .filter { it.totalMs > 0L && it.atEpochMs > 0L }
            .sortedByDescending { it.atEpochMs }
            .take(MAX_ENTRIES)
            .toList()
}

/**
 * 计时历史的持久化（功能级 DataStore，遵循本仓库 `UpdatePreferencesStore` 的既有约定）。
 *
 * 读取一律兜底：DataStore 抛错或数据损坏时返回空列表，面板照常可用（只是历史为空）。
 */
object QuickMenuHistoryStore {

    private val KEY = stringPreferencesKey("quick_menu_history")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(context: Context): List<QuickMenuHistoryEntry> {
        val raw = runCatching { context.quickMenuHistoryDataStore.data.first()[KEY] }.getOrNull()
            ?: return emptyList()
        val entries = runCatching { json.decodeFromString<List<QuickMenuHistoryEntry>>(raw) }
            .getOrDefault(emptyList())
        return QuickMenuHistoryCodec.sanitize(entries)
    }

    /** 追加一条并返回更新后的列表（供 UI 立即刷新）。 */
    suspend fun add(
        context: Context,
        entry: QuickMenuHistoryEntry,
    ): List<QuickMenuHistoryEntry> {
        var updated: List<QuickMenuHistoryEntry> = emptyList()
        runCatching {
            context.quickMenuHistoryDataStore.edit { prefs ->
                val current = prefs[KEY]
                    ?.let { raw ->
                        runCatching { json.decodeFromString<List<QuickMenuHistoryEntry>>(raw) }
                            .getOrDefault(emptyList())
                    }
                    .orEmpty()
                updated = QuickMenuHistoryCodec.add(current, entry)
                prefs[KEY] = json.encodeToString(updated)
            }
        }
        return updated
    }

    suspend fun clear(context: Context) {
        runCatching { context.quickMenuHistoryDataStore.edit { it.remove(KEY) } }
    }
}
