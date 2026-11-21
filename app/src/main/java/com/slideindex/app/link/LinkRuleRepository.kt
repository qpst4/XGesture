package com.slideindex.app.link

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicReference

/**
 * 链接分发规则、免分发域名、抓取模式以及雷达偏好的持久化仓库（多进程安全）。
 */
object LinkRuleRepository {

    private const val STORE_NAME = "slide_index_link_dispatch"

    private val KEY_RULES = stringPreferencesKey("link_dispatch_rules_user")
    private val KEY_EXTRACTS = stringPreferencesKey("link_extract_patterns")
    private val KEY_EXEMPTS = stringPreferencesKey("link_exempt_domains")

    val KEY_RADAR_SCREEN_CONTENT = booleanPreferencesKey("link_radar_screen_content")
    val KEY_RADAR_POINTER = booleanPreferencesKey("link_radar_pointer")
    val KEY_OPEN_IN_SMALL_WINDOW = booleanPreferencesKey("link_open_in_small_window")
    val KEY_RADAR_HOVER_HOLD_MS = intPreferencesKey("link_radar_hover_hold_ms")
    const val DEFAULT_RADAR_HOVER_HOLD_MS = 1000

    private val dataStoreHolder = AtomicReference<DataStore<Preferences>>()

    private val Context.linkDataStore: DataStore<Preferences>
        get() = dataStoreHolder.get() ?: synchronized(dataStoreHolder) {
            dataStoreHolder.get() ?: MultiProcessDataStoreFactory.create(
                serializer = PreferencesFileSerializer,
                produceFile = { preferencesDataStoreFile(STORE_NAME) }
            ).also { dataStoreHolder.set(it) }
        }

    private val jsonSerializer = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun loadRules(context: Context): List<LinkDispatchRule> {
        val prefs = context.linkDataStore.data.first()
        val json = prefs[KEY_RULES].orEmpty()
        if (json.isEmpty()) return emptyList()
        return runCatching {
            jsonSerializer.decodeFromString<List<LinkDispatchRule>>(json)
        }.getOrDefault(emptyList()).sortedBy { it.priority }
    }

    fun observeRules(context: Context): Flow<List<LinkDispatchRule>> =
        context.linkDataStore.data.map { prefs ->
            val json = prefs[KEY_RULES].orEmpty()
            if (json.isEmpty()) emptyList()
            else runCatching {
                jsonSerializer.decodeFromString<List<LinkDispatchRule>>(json)
            }.getOrDefault(emptyList()).sortedBy { it.priority }
        }

    suspend fun updateRules(context: Context, update: (List<LinkDispatchRule>) -> List<LinkDispatchRule>) {
        context.linkDataStore.edit { prefs ->
            val current = prefs[KEY_RULES]?.let {
                runCatching { jsonSerializer.decodeFromString<List<LinkDispatchRule>>(it) }.getOrNull()
            }.orEmpty()
            val updated = update(current.sortedBy { it.priority })
            prefs[KEY_RULES] = jsonSerializer.encodeToString(updated)
        }
    }

    suspend fun loadExtractPatterns(context: Context): List<LinkExtractPattern> {
        seedBuiltinExtractsIfNeeded(context)
        val prefs = context.linkDataStore.data.first()
        val json = prefs[KEY_EXTRACTS].orEmpty()
        if (json.isEmpty()) return LinkExtractPattern.builtinDefaults()
        return runCatching {
            jsonSerializer.decodeFromString<List<LinkExtractPattern>>(json)
        }.getOrNull() ?: LinkExtractPattern.builtinDefaults()
    }

    fun observeExtractPatterns(context: Context): Flow<List<LinkExtractPattern>> =
        context.linkDataStore.data.map { prefs ->
            val json = prefs[KEY_EXTRACTS].orEmpty()
            if (json.isEmpty()) LinkExtractPattern.builtinDefaults()
            else runCatching {
                jsonSerializer.decodeFromString<List<LinkExtractPattern>>(json)
            }.getOrNull() ?: LinkExtractPattern.builtinDefaults()
        }

    suspend fun updateExtractPatterns(context: Context, update: (List<LinkExtractPattern>) -> List<LinkExtractPattern>) {
        seedBuiltinExtractsIfNeeded(context)
        context.linkDataStore.edit { prefs ->
            val current = prefs[KEY_EXTRACTS]?.let {
                runCatching { jsonSerializer.decodeFromString<List<LinkExtractPattern>>(it) }.getOrNull()
            } ?: LinkExtractPattern.builtinDefaults()
            val updated = update(current)
            prefs[KEY_EXTRACTS] = jsonSerializer.encodeToString(updated)
        }
    }

    suspend fun loadExemptDomains(context: Context): List<LinkExemptDomain> {
        val prefs = context.linkDataStore.data.first()
        val json = prefs[KEY_EXEMPTS].orEmpty()
        if (json.isEmpty()) return emptyList()
        return runCatching {
            jsonSerializer.decodeFromString<List<LinkExemptDomain>>(json)
        }.getOrDefault(emptyList())
    }

    fun observeExemptDomains(context: Context): Flow<List<LinkExemptDomain>> =
        context.linkDataStore.data.map { prefs ->
            val json = prefs[KEY_EXEMPTS].orEmpty()
            if (json.isEmpty()) emptyList()
            else runCatching {
                jsonSerializer.decodeFromString<List<LinkExemptDomain>>(json)
            }.getOrDefault(emptyList())
        }

    suspend fun updateExemptDomains(context: Context, update: (List<LinkExemptDomain>) -> List<LinkExemptDomain>) {
        context.linkDataStore.edit { prefs ->
            val current = prefs[KEY_EXEMPTS]?.let {
                runCatching { jsonSerializer.decodeFromString<List<LinkExemptDomain>>(it) }.getOrNull()
            }.orEmpty()
            val updated = update(current)
            prefs[KEY_EXEMPTS] = jsonSerializer.encodeToString(updated)
        }
    }

    suspend fun snapshot(context: Context): LinkConfigBackup {
        seedBuiltinExtractsIfNeeded(context)
        val prefs = context.linkDataStore.data.first()
        val rules = prefs[KEY_RULES]?.let {
            runCatching { jsonSerializer.decodeFromString<List<LinkDispatchRule>>(it) }.getOrNull()
        }.orEmpty()
        val exempts = prefs[KEY_EXEMPTS]?.let {
            runCatching { jsonSerializer.decodeFromString<List<LinkExemptDomain>>(it) }.getOrNull()
        }.orEmpty()
        val extracts = prefs[KEY_EXTRACTS]?.let {
            runCatching { jsonSerializer.decodeFromString<List<LinkExtractPattern>>(it) }.getOrNull()
        } ?: LinkExtractPattern.builtinDefaults()
        return LinkConfigBackup(rules = rules, exempts = exempts, extracts = extracts)
    }

    suspend fun importConfig(context: Context, incoming: LinkConfigBackup): LinkImportResult {
        seedBuiltinExtractsIfNeeded(context)
        var result = LinkImportResult(0, 0, 0)
        context.linkDataStore.edit { prefs ->
            val curRules = prefs[KEY_RULES]?.let {
                runCatching { jsonSerializer.decodeFromString<List<LinkDispatchRule>>(it) }.getOrNull()
            }.orEmpty()
            val curExempts = prefs[KEY_EXEMPTS]?.let {
                runCatching { jsonSerializer.decodeFromString<List<LinkExemptDomain>>(it) }.getOrNull()
            }.orEmpty()
            val curExtracts = prefs[KEY_EXTRACTS]?.let {
                runCatching { jsonSerializer.decodeFromString<List<LinkExtractPattern>>(it) }.getOrNull()
            } ?: LinkExtractPattern.builtinDefaults()

            val (merged, added) = LinkConfigCodec.merge(curRules, curExempts, curExtracts, incoming)
            prefs[KEY_RULES] = jsonSerializer.encodeToString(merged.rules)
            prefs[KEY_EXEMPTS] = jsonSerializer.encodeToString(merged.exempts)
            prefs[KEY_EXTRACTS] = jsonSerializer.encodeToString(merged.extracts)
            result = added
        }
        return result
    }

    suspend fun isRadarScreenContentEnabled(context: Context): Boolean =
        context.linkDataStore.data.first()[KEY_RADAR_SCREEN_CONTENT] ?: true

    fun observeRadarScreenContent(context: Context): Flow<Boolean> =
        context.linkDataStore.data.map { it[KEY_RADAR_SCREEN_CONTENT] ?: true }

    suspend fun setRadarScreenContentEnabled(context: Context, enabled: Boolean) {
        context.linkDataStore.edit { it[KEY_RADAR_SCREEN_CONTENT] = enabled }
    }

    suspend fun isRadarPointerEnabled(context: Context): Boolean =
        context.linkDataStore.data.first()[KEY_RADAR_POINTER] ?: true

    fun observeRadarPointer(context: Context): Flow<Boolean> =
        context.linkDataStore.data.map { it[KEY_RADAR_POINTER] ?: true }

    suspend fun setRadarPointerEnabled(context: Context, enabled: Boolean) {
        context.linkDataStore.edit { it[KEY_RADAR_POINTER] = enabled }
    }

    suspend fun isOpenInSmallWindowEnabled(context: Context): Boolean =
        context.linkDataStore.data.first()[KEY_OPEN_IN_SMALL_WINDOW] ?: true

    fun observeOpenInSmallWindow(context: Context): Flow<Boolean> =
        context.linkDataStore.data.map { it[KEY_OPEN_IN_SMALL_WINDOW] ?: true }

    suspend fun setOpenInSmallWindowEnabled(context: Context, enabled: Boolean) {
        context.linkDataStore.edit { it[KEY_OPEN_IN_SMALL_WINDOW] = enabled }
    }

    /**
     * 悬浮球指针停留在链接上时，保持显示雷达横幅的时长（毫秒）；0 表示始终保持横幅、不自动变黄切换为取词。
     */
    suspend fun getRadarHoverHoldMs(context: Context): Int =
        context.linkDataStore.data.first()[KEY_RADAR_HOVER_HOLD_MS] ?: DEFAULT_RADAR_HOVER_HOLD_MS

    fun observeRadarHoverHoldMs(context: Context): Flow<Int> =
        context.linkDataStore.data.map { it[KEY_RADAR_HOVER_HOLD_MS] ?: DEFAULT_RADAR_HOVER_HOLD_MS }

    suspend fun setRadarHoverHoldMs(context: Context, holdMs: Int) {
        context.linkDataStore.edit { it[KEY_RADAR_HOVER_HOLD_MS] = holdMs }
    }

    private suspend fun seedBuiltinExtractsIfNeeded(context: Context) {
        val prefs = context.linkDataStore.data.first()
        if (prefs[KEY_EXTRACTS].isNullOrEmpty()) {
            context.linkDataStore.edit { p ->
                if (p[KEY_EXTRACTS].isNullOrEmpty()) {
                    p[KEY_EXTRACTS] = jsonSerializer.encodeToString(LinkExtractPattern.builtinDefaults())
                }
            }
        }
    }
}
