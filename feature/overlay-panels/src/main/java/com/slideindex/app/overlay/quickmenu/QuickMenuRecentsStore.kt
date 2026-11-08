package com.slideindex.app.overlay.quickmenu

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.quickMenuDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "slide_index_quick_menu",
)

/**
 * 「最近使用」的序列化规则。
 *
 * 抽成纯函数是为了可单测：这里要处理的正是持久化最容易出事的两点——
 * 枚举改名/删除后旧数据怎么办、以及重复/超量条目怎么收敛。
 */
object QuickMenuRecentsCodec {

    const val MAX_ENTRIES = 4

    /** 存的是枚举名（不是序号：序号会在插入新工具时整体错位）。 */
    fun encode(tools: List<QuickMenuTool>): List<String> = tools.map { it.name }

    /**
     * 反序列化。
     *
     * 未知名字**直接丢弃**而不是抛异常：工具被删掉或改名后，旧设置不能让面板打不开。
     * 同时去重并截断到 [MAX_ENTRIES]。
     */
    fun decode(names: List<String>): List<QuickMenuTool> =
        names.asSequence()
            .mapNotNull { name -> QuickMenuTool.entries.firstOrNull { it.name == name } }
            .distinct()
            .take(MAX_ENTRIES)
            .toList()

    /** 把 [tool] 置顶；已存在则先移除再插到最前，保持"最近在前、无重复"。 */
    fun record(current: List<QuickMenuTool>, tool: QuickMenuTool): List<QuickMenuTool> =
        (listOf(tool) + current.filterNot { it == tool }).take(MAX_ENTRIES)
}

/**
 * 「最近使用」的持久化（功能级 DataStore，遵循本仓库 `UpdatePreferencesStore` 的既有约定）。
 *
 * 数据由一个 JSON 字符串承载，避免为 4 个条目声明一堆键。
 * 所有读取都做了兜底：DataStore 抛错或数据损坏时返回空列表，面板照常可用（只是没有"最近使用"）。
 */
object QuickMenuRecentsStore {

    private val KEY = stringPreferencesKey("quick_menu_recents")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(context: Context): List<QuickMenuTool> {
        val raw = runCatching { context.quickMenuDataStore.data.first()[KEY] }.getOrNull() ?: return emptyList()
        val names = runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        return QuickMenuRecentsCodec.decode(names)
    }

    /** 记录一次使用并返回更新后的列表（供 UI 立即刷新）。 */
    suspend fun record(context: Context, tool: QuickMenuTool): List<QuickMenuTool> {
        var updated: List<QuickMenuTool> = emptyList()
        runCatching {
            context.quickMenuDataStore.edit { prefs ->
                val current = prefs[KEY]
                    ?.let { raw -> runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList()) }
                    .orEmpty()
                updated = QuickMenuRecentsCodec.record(QuickMenuRecentsCodec.decode(current), tool)
                prefs[KEY] = json.encodeToString(QuickMenuRecentsCodec.encode(updated))
            }
        }
        return updated
    }
}
