package com.slideindex.app.overlay.share

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.shareSheetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "slide_index_share_sheet",
)

/**
 * 钉住的分享目标（键 = `包名/Activity 类名`，按钉住先后排序）。
 *
 * 与 `QuickMenuRecentsStore` 同一套约定：整个列表由一个 JSON 字符串承载，
 * 读取失败一律返回空列表 —— 钉住数据坏了不能连分享面板一起打不开。
 */
object ShareSheetPins {

    private val KEY = stringPreferencesKey("pinned_targets")
    private val json = Json { ignoreUnknownKeys = true }

    /** 最多钉 8 个：面板一行 5 格，再多就要占掉两整行、把普通目标挤走。 */
    const val MAX_PINS = 8

    /** 内存缓存：面板是高频打开的，钉住列表不该每次都读一遍 DataStore。 */
    @Volatile
    private var cached: List<String>? = null

    /** 同步取已加载的钉住列表（面板首帧用）；还没读过就返回 null。 */
    fun peek(): List<String>? = cached

    suspend fun read(context: Context): List<String> {
        cached?.let { return it }
        val loaded = runCatching {
            context.shareSheetDataStore.data.first()[KEY]
                ?.let { raw -> json.decodeFromString<List<String>>(raw) }
                .orEmpty()
                .distinct()
                .take(MAX_PINS)
        }.getOrDefault(emptyList())
        cached = loaded
        return loaded
    }

    /** 钉住 / 取消钉住，返回更新后的列表（钉住的排在前面）。 */
    suspend fun toggle(context: Context, key: String): List<String> {
        var updated: List<String> = emptyList()
        runCatching {
            context.shareSheetDataStore.edit { prefs ->
                val current = cached ?: prefs[KEY]
                    ?.let { raw -> runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList()) }
                    .orEmpty()
                updated = if (key in current) {
                    current - key
                } else {
                    (listOf(key) + current).distinct().take(MAX_PINS)
                }
                prefs[KEY] = json.encodeToString(updated)
            }
        }
        if (updated.isNotEmpty() || cached != null) {
            cached = updated
        }
        return updated
    }
}
