package com.slideindex.app.settings



import android.content.Context

import androidx.datastore.core.DataStore

import androidx.datastore.preferences.core.MutablePreferences

import androidx.datastore.preferences.core.Preferences

import androidx.datastore.preferences.core.edit

import androidx.datastore.core.MultiProcessDataStoreFactory

import androidx.datastore.preferences.core.PreferencesFileSerializer

import androidx.datastore.preferences.preferencesDataStoreFile

import com.slideindex.app.message.MessageSettings

import dagger.hilt.android.qualifiers.ApplicationContext

import javax.inject.Inject

import javax.inject.Singleton

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map



private const val SETTINGS_STORE_NAME = "slide_index_settings"

/**
 * 多进程安全的设置存储。
 *
 * 目标架构下无障碍与浮层运行在 `:overlay` 进程，主进程与它都要读写设置。
 * 默认的 `preferencesDataStore` 委托只支持单进程（两个进程同时打开同一文件会互相看不到写入，
 * 甚至报 "multiple DataStores active for the same file"），因此这里显式使用
 * [MultiProcessDataStoreFactory]，并且保证每个进程内只有一个实例。
 */
private val dataStoreHolder = java.util.concurrent.atomic.AtomicReference<DataStore<Preferences>>()

private val Context.dataStore: DataStore<Preferences>
    get() = dataStoreHolder.get() ?: synchronized(dataStoreHolder) {
        dataStoreHolder.get() ?: MultiProcessDataStoreFactory.create<Preferences>(
            serializer = PreferencesFileSerializer,
            produceFile = { preferencesDataStoreFile(SETTINGS_STORE_NAME) },
        ).also { dataStoreHolder.set(it) }
    }



@Singleton

class SettingsPreferencesEditor @Inject constructor(

    @ApplicationContext private val context: Context,

) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        SettingsSnapshotReader.read(prefs, context)
    }

    val themeSettings: Flow<ThemeSettings> = context.dataStore.data.map { prefs ->
        ThemeSettings(
            themeColorArgb = prefs[SettingsPreferenceKeys.THEME_COLOR] ?: 0xFF6750A4.toInt(),
            dynamicColorEnabled = prefs[SettingsPreferenceKeys.DYNAMIC_COLOR_ENABLED] ?: false,
            themePaletteStyleId = prefs[SettingsPreferenceKeys.THEME_PALETTE_STYLE]
                ?: ThemePaletteStyle.TONAL_SPOT.id,
        )
    }.distinctUntilChanged()

    val appRootSettings: Flow<AppRootSettings> = context.dataStore.data.map { prefs ->
        AppRootSettings(
            themeColorArgb = prefs[SettingsPreferenceKeys.THEME_COLOR] ?: 0xFF6750A4.toInt(),
            dynamicColorEnabled = prefs[SettingsPreferenceKeys.DYNAMIC_COLOR_ENABLED] ?: false,
            themePaletteStyleId = prefs[SettingsPreferenceKeys.THEME_PALETTE_STYLE]
                ?: ThemePaletteStyle.TONAL_SPOT.id,
            onboardingCompleted = prefs[SettingsPreferenceKeys.ONBOARDING_COMPLETED] ?: false,
            hideFromRecents = prefs[SettingsPreferenceKeys.HIDE_FROM_RECENTS] ?: false,
            predictiveBackEnabled = prefs[SettingsPreferenceKeys.PREDICTIVE_BACK_ENABLED] ?: false,
            privilegeMode = PrivilegeMode.fromStorage(prefs[SettingsPreferenceKeys.PRIVILEGE_MODE]),
        )
    }.distinctUntilChanged()

    val gestureSettings: Flow<GestureSettings> = context.dataStore.data
        .map { prefs -> GestureSettings.from(SettingsSnapshotReader.read(prefs, context)) }
        .distinctUntilChanged()

    val overlaySettings: Flow<OverlaySettings> = context.dataStore.data
        .map { prefs -> OverlaySettings.from(SettingsSnapshotReader.read(prefs, context)) }
        .distinctUntilChanged()

    val homeMainSettings: Flow<HomeMainSettings> = context.dataStore.data
        .map { prefs -> HomeMainSettings.from(SettingsSnapshotReader.read(prefs, context)) }
        .distinctUntilChanged()

    val extensionHubSettings: Flow<ExtensionHubSettings> = context.dataStore.data
        .map { prefs -> ExtensionHubSettings.from(SettingsSnapshotReader.read(prefs, context)) }
        .distinctUntilChanged()

    val keepAliveUiSettings: Flow<KeepAliveUiSettings> = context.dataStore.data
        .map { prefs -> KeepAliveUiSettings.from(SettingsSnapshotReader.read(prefs, context)) }
        .distinctUntilChanged()

    val shakeUiSettings: Flow<ShakeUiSettings> = context.dataStore.data
        .map { prefs -> ShakeUiSettings.from(SettingsSnapshotReader.read(prefs, context)) }
        .distinctUntilChanged()

    val freeWindowUiSettings: Flow<FreeWindowUiSettings> = context.dataStore.data
        .map { prefs -> FreeWindowUiSettings.from(SettingsSnapshotReader.read(prefs, context)) }
        .distinctUntilChanged()

    val otpUiSettings: Flow<OtpUiSettings> = context.dataStore.data
        .map { prefs -> OtpUiSettings.from(SettingsSnapshotReader.read(prefs, context)) }
        .distinctUntilChanged()

    val messageReminderSettings: Flow<MessageSettings> = context.dataStore.data
        .map { prefs -> SettingsSnapshotReader.read(prefs, context).messageReminderSettings }
        .distinctUntilChanged()

    /**
     * 云存储配置的原始 JSON。
     *
     * 走 DataStore 的原始键而不是塞进 [AppSettings] 快照：它只在云备份设置页用到，
     * 没必要让每次设置快照构造都解析一遍。放在 DataStore 里则能随备份包自动往返。
     */
    val cloudStorageSettingsJson: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[SettingsPreferenceKeys.CLOUD_STORAGE_SETTINGS_JSON] }
        .distinctUntilChanged()



    suspend fun edit(block: (MutablePreferences) -> Unit): Result<Unit> = runCatching {
        context.dataStore.edit { prefs ->
            block(prefs)
        }
    }

    suspend fun readRawPreferences(): Preferences = context.dataStore.data.first()

    /**
     * 一次性清理旧版剪贴板白名单方案的遗留键（旧 LSPosed 模式，当前代码不再读写）。
     *
     * 用标记键保证只跑一次，避免每次启动都写一遍 DataStore。
     */
    suspend fun cleanupLegacyClipboardKeysOnce() = runCatching {
        context.dataStore.edit { prefs ->
            if (prefs[SettingsPreferenceKeys.LEGACY_CLIPBOARD_KEYS_CLEANED] == true) return@edit
            prefs.remove(SettingsPreferenceKeys.CLIPBOARD_LSPOSED_EXTRA_WHITELIST)
            prefs[SettingsPreferenceKeys.LEGACY_CLIPBOARD_KEYS_CLEANED] = true
        }
    }
}


