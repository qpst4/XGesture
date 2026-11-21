package com.slideindex.app.link

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@Serializable
data class LinkDispatchHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val rawUrl: String,
    val finalUrl: String,
    val matchedRuleName: String? = null,
    val targetPackage: String? = null,
    val isSuccess: Boolean = true,
    val isReDispatch: Boolean = false,
)

/**
 * 链接分发历史流水日志仓库（轻量本地持久化，最多保留 200 条）。
 */
object LinkDispatchHistoryRepository {

    private const val STORE_NAME = "slide_index_link_history"
    private const val MAX_HISTORY_ITEMS = 200

    private val KEY_HISTORY = stringPreferencesKey("link_dispatch_history_list")

    private val dataStoreHolder = AtomicReference<DataStore<Preferences>>()

    private val Context.historyDataStore: DataStore<Preferences>
        get() = dataStoreHolder.get() ?: synchronized(dataStoreHolder) {
            dataStoreHolder.get() ?: MultiProcessDataStoreFactory.create(
                serializer = PreferencesFileSerializer,
                produceFile = { preferencesDataStoreFile(STORE_NAME) },
            ).also { dataStoreHolder.set(it) }
        }

    private val jsonSerializer = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun record(
        context: Context,
        rawUrl: String,
        finalUrl: String,
        matchedRuleName: String? = null,
        targetPackage: String? = null,
        isSuccess: Boolean = true,
        isReDispatch: Boolean = false,
    ) {
        val item = LinkDispatchHistoryItem(
            rawUrl = rawUrl,
            finalUrl = finalUrl,
            matchedRuleName = matchedRuleName,
            targetPackage = targetPackage,
            isSuccess = isSuccess,
            isReDispatch = isReDispatch,
        )
        context.historyDataStore.edit { prefs ->
            val current = prefs[KEY_HISTORY]?.let {
                runCatching { jsonSerializer.decodeFromString<List<LinkDispatchHistoryItem>>(it) }.getOrNull()
            }.orEmpty()
            val next = (listOf(item) + current).take(MAX_HISTORY_ITEMS)
            prefs[KEY_HISTORY] = jsonSerializer.encodeToString(next)
        }
    }

    suspend fun loadHistory(context: Context): List<LinkDispatchHistoryItem> {
        val prefs = context.historyDataStore.data.first()
        val json = prefs[KEY_HISTORY].orEmpty()
        if (json.isEmpty()) return emptyList()
        return runCatching {
            jsonSerializer.decodeFromString<List<LinkDispatchHistoryItem>>(json)
        }.getOrDefault(emptyList())
    }

    fun observeHistory(context: Context): Flow<List<LinkDispatchHistoryItem>> =
        context.historyDataStore.data.map { prefs ->
            val json = prefs[KEY_HISTORY].orEmpty()
            if (json.isEmpty()) emptyList()
            else runCatching {
                jsonSerializer.decodeFromString<List<LinkDispatchHistoryItem>>(json)
            }.getOrDefault(emptyList())
        }

    suspend fun clearHistory(context: Context) {
        context.historyDataStore.edit { prefs ->
            prefs.remove(KEY_HISTORY)
        }
    }
}
