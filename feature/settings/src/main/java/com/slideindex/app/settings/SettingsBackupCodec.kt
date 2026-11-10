package com.slideindex.app.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.serialization.json.Json

internal object SettingsBackupCodec {
    const val CURRENT_FORMAT_VERSION = 3

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val excludedExportKeys = setOf(
        SettingsPreferenceKeys.ONBOARDING_COMPLETED.name,
    )

    fun encode(
        preferences: Preferences,
        appVersionName: String,
        sensitive: SensitiveBackupSections? = null,
        modules: Set<SettingsModule>? = null,
    ): String {
        val entries = preferences.asMap()
            .mapNotNull { (key, value) ->
                if (key.name in excludedExportKeys) return@mapNotNull null
                if (modules != null && SettingsModuleRegistry.moduleOf(key.name) !in modules) {
                    return@mapNotNull null
                }
                encodeEntry(key.name, value)
            }
            .sortedBy { it.key }
        val document = SettingsBackupDocument(
            formatVersion = CURRENT_FORMAT_VERSION,
            exportedAtEpochMs = System.currentTimeMillis(),
            appVersionName = appVersionName,
            preferences = entries,
            includedModules = modules?.map { it.id }?.sorted().orEmpty(),
            otpRecordsJson = sensitive?.otpRecordsJson,
            notificationHistoryJson = sensitive?.notificationHistoryJson,
            notificationFilterRulesJson = sensitive?.notificationFilterRulesJson,
            notificationFilterPreferencesJson = sensitive?.notificationFilterPreferencesJson,
            otpAutoFillStatsJson = sensitive?.otpAutoFillStatsJson,
            shellOutputHistoryJson = sensitive?.shellOutputHistoryJson,
            searchPanelHistoryJson = sensitive?.searchPanelHistoryJson,
            multiTouchGestureJson = sensitive?.multiTouchGestureJson,
            externalStores = sensitive?.externalStores.orEmpty().forModules(modules),
        )
        return json.encodeToString(document)
    }

    fun decode(rawJson: String): SettingsBackupDocument = json.decodeFromString(rawJson)

    fun validate(document: SettingsBackupDocument) {
        require(document.formatVersion in 1..CURRENT_FORMAT_VERSION) {
            "Unsupported backup format version: ${document.formatVersion}"
        }
        require(document.preferences.isNotEmpty() || document.optionalSections.hasAny) {
            "Backup file does not contain any settings"
        }
    }

    private val SettingsBackupDocument.optionalSections: SensitiveBackupSections
        get() = SensitiveBackupSections(
            otpRecordsJson = otpRecordsJson,
            notificationHistoryJson = notificationHistoryJson,
            notificationFilterRulesJson = notificationFilterRulesJson,
            notificationFilterPreferencesJson = notificationFilterPreferencesJson,
            otpAutoFillStatsJson = otpAutoFillStatsJson,
            shellOutputHistoryJson = shellOutputHistoryJson,
            searchPanelHistoryJson = searchPanelHistoryJson,
            multiTouchGestureJson = multiTouchGestureJson,
            externalStores = externalStores,
        )

    fun apply(document: SettingsBackupDocument, prefs: MutablePreferences) {
        document.preferences.forEach { entry ->
            applyEntry(prefs, entry)
        }
    }

    private fun encodeEntry(key: String, value: Any): SettingsPreferenceEntry? = when (value) {
        is Boolean -> SettingsPreferenceEntry(key = key, type = TYPE_BOOLEAN, value = value.toString())
        is Int -> SettingsPreferenceEntry(key = key, type = TYPE_INT, value = value.toString())
        is Long -> SettingsPreferenceEntry(key = key, type = TYPE_LONG, value = value.toString())
        is Float -> SettingsPreferenceEntry(key = key, type = TYPE_FLOAT, value = value.toString())
        is String -> SettingsPreferenceEntry(key = key, type = TYPE_STRING, value = value)
        is Set<*> -> {
            val strings = value.filterIsInstance<String>()
            if (strings.size != value.size) return null
            SettingsPreferenceEntry(
                key = key,
                type = TYPE_STRING_SET,
                value = "",
                values = strings.sorted(),
            )
        }
        else -> null
    }

    private fun applyEntry(prefs: MutablePreferences, entry: SettingsPreferenceEntry) {
        when (entry.type) {
            TYPE_BOOLEAN -> prefs[booleanPreferencesKey(entry.key)] = entry.value.toBooleanStrict()
            TYPE_INT -> prefs[intPreferencesKey(entry.key)] = entry.value.toInt()
            TYPE_LONG -> prefs[longPreferencesKey(entry.key)] = entry.value.toLong()
            TYPE_FLOAT -> prefs[floatPreferencesKey(entry.key)] = entry.value.toFloat()
            TYPE_STRING -> prefs[stringPreferencesKey(entry.key)] = entry.value
            TYPE_STRING_SET -> prefs[stringSetPreferencesKey(entry.key)] = entry.values.toSet()
            else -> error("Unsupported preference type: ${entry.type}")
        }
    }

    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_STRING = "string"
    private const val TYPE_STRING_SET = "string_set"
}
