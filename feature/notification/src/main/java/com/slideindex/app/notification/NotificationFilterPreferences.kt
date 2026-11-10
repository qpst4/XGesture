package com.slideindex.app.notification

import android.content.Context
import com.slideindex.app.common.repositoryRunCatching
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationFilterDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_filter_preferences",
)

@Singleton
class NotificationFilterPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext

    @Volatile
    private var cachedSettings: NotificationFilterSettings = NotificationFilterSettings()

    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: Flow<NotificationFilterSettings> = appContext.notificationFilterDataStore.data.map { prefs ->
        NotificationFilterSettings(
            notificationHistoryMaxCount = (prefs[NOTIFICATION_HISTORY_MAX_COUNT]
                ?: NotificationFilterSettings.DEFAULT_NOTIFICATION_HISTORY_MAX_COUNT)
                .coerceIn(
                    NotificationFilterSettings.MIN_NOTIFICATION_HISTORY_MAX_COUNT,
                    NotificationFilterSettings.MAX_NOTIFICATION_HISTORY_MAX_COUNT,
                ),
            groupHistoryByApp = prefs[GROUP_HISTORY_BY_APP] ?: false,
        )
    }

    init {
        cacheScope.launch {
            settings.collect { cachedSettings = it }
        }
    }

    suspend fun setGroupHistoryByApp(enabled: Boolean): Result<Unit> {
        return repositoryRunCatching {
            appContext.notificationFilterDataStore.edit { prefs ->
                prefs[GROUP_HISTORY_BY_APP] = enabled
            }
        }
    }

    suspend fun setNotificationHistoryMaxCount(count: Int): Result<Unit> {
        val clamped = count.coerceIn(
            NotificationFilterSettings.MIN_NOTIFICATION_HISTORY_MAX_COUNT,
            NotificationFilterSettings.MAX_NOTIFICATION_HISTORY_MAX_COUNT,
        )
        return repositoryRunCatching {
            appContext.notificationFilterDataStore.edit { prefs ->
                prefs[NOTIFICATION_HISTORY_MAX_COUNT] = clamped
            }
        }
    }

    fun readSnapshot(): NotificationFilterSettings = cachedSettings

    suspend fun exportRawJson(): String {
        val snapshot = readSnapshot()
        return backupJson.encodeToString(
            NotificationFilterPreferencesBackup(
                notificationHistoryMaxCount = snapshot.notificationHistoryMaxCount,
                groupHistoryByApp = snapshot.groupHistoryByApp,
            ),
        )
    }

    suspend fun importRawJson(raw: String): Result<Unit> = repositoryRunCatching {
        val backup = backupJson.decodeFromString<NotificationFilterPreferencesBackup>(raw)
        setNotificationHistoryMaxCount(backup.notificationHistoryMaxCount).getOrThrow()
        setGroupHistoryByApp(backup.groupHistoryByApp).getOrThrow()
    }

    companion object {
        const val DEFAULT_NOTIFICATION_HISTORY_MAX_COUNT =
            NotificationFilterSettings.DEFAULT_NOTIFICATION_HISTORY_MAX_COUNT
        const val MIN_NOTIFICATION_HISTORY_MAX_COUNT =
            NotificationFilterSettings.MIN_NOTIFICATION_HISTORY_MAX_COUNT
        const val MAX_NOTIFICATION_HISTORY_MAX_COUNT =
            NotificationFilterSettings.MAX_NOTIFICATION_HISTORY_MAX_COUNT
        const val NOTIFICATION_HISTORY_MAX_COUNT_STEP = 50

        private val NOTIFICATION_HISTORY_MAX_COUNT = intPreferencesKey("notification_history_max_count")
        private val GROUP_HISTORY_BY_APP = booleanPreferencesKey("group_history_by_app")

        private val backupJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

@Serializable
private data class NotificationFilterPreferencesBackup(
    val notificationHistoryMaxCount: Int,
    val groupHistoryByApp: Boolean = true,
)
