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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn



private const val SETTINGS_STORE_NAME = "slide_index_settings"

/** 最后一个订阅者走后，共享快照再保持热的时长；超时后上游停止，下次订阅重新解析一次。 */
private const val SNAPSHOT_STOP_TIMEOUT_MS = 5_000L

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

    /**
     * 共享快照用的作用域。设置对象是进程级单例，`WhileSubscribed` 让它在"有收集者"期间
     * 保持热、全部取消 5 秒后自动停——测试里反复构造仓库时不会留下一堆永不停歇的协程。
     */
    private val snapshotScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 全量设置快照的**唯一**解析入口。
     *
     * 以前每个切片各自 `dataStore.data.map { SettingsSnapshotReader.read(...) }`：一次写盘，
     * 收集它的每个 flow 都会把整份 Preferences 完整解析一遍（530 次键查 + 43 次 codec 解码）。
     * 设置页 + 浮层宿主同时在线时，改一个开关要重复解析 3–5 遍。现在只解析一次再 shareIn，
     * 切片全部从这份快照派生。
     */
    private val snapshot: Flow<AppSettings> = context.dataStore.data
        .map { prefs -> SettingsSnapshotReader.read(prefs, context) }
        .distinctUntilChanged()
        .shareIn(
            scope = snapshotScope,
            started = SharingStarted.WhileSubscribed(SNAPSHOT_STOP_TIMEOUT_MS),
            replay = 1,
        )

    /** 把一个切片从共享快照里切出来，变化时才发射。 */
    private fun <T> slice(selector: (AppSettings) -> T): Flow<T> =
        snapshot.map(selector).distinctUntilChanged()

    /**
     * 冷流：每次订阅都从 DataStore 读当前值再解析。
     *
     * 必须保留"订阅即最新"的语义——`.settings.first()` 在写完某个键之后立刻读，必须拿到刚写入的
     * 值；全仓有 30 多个 trampoline / 浮层入口是这么用的。共享快照 [snapshot] 只服务切片与仓库缓存，
     * 不替换这条路径（否则 `.first()` 会先拿到 replay 的旧快照，出现"写完读不到"的竞态）。
     */
    val settings: Flow<AppSettings> = context.dataStore.data
        .map { prefs -> SettingsSnapshotReader.read(prefs, context) }

    /** 进程内共享的快照（切片与仓库缓存用）：同一份 Preferences 只解析一次。 */
    internal val snapshotFlow: Flow<AppSettings> = snapshot

    val themeSettings: Flow<ThemeSettings> = slice { settings ->
        ThemeSettings(
            themeColorArgb = settings.themeColorArgb,
            dynamicColorEnabled = settings.dynamicColorEnabled,
            themePaletteStyleId = settings.themePaletteStyleId,
        )
    }

    val appRootSettings: Flow<AppRootSettings> = slice { settings ->
        AppRootSettings(
            themeColorArgb = settings.themeColorArgb,
            dynamicColorEnabled = settings.dynamicColorEnabled,
            themePaletteStyleId = settings.themePaletteStyleId,
            onboardingCompleted = settings.onboardingCompleted,
            hideFromRecents = settings.hideFromRecents,
            predictiveBackEnabled = settings.predictiveBackEnabled,
            privilegeMode = settings.privilegeMode,
        )
    }

    val gestureSettings: Flow<GestureSettings> = slice(GestureSettings::from)

    val overlaySettings: Flow<OverlaySettings> = slice(OverlaySettings::from)

    val homeMainSettings: Flow<HomeMainSettings> = slice(HomeMainSettings::from)

    val extensionHubSettings: Flow<ExtensionHubSettings> = slice(ExtensionHubSettings::from)

    val keepAliveUiSettings: Flow<KeepAliveUiSettings> = slice(KeepAliveUiSettings::from)

    val shakeUiSettings: Flow<ShakeUiSettings> = slice(ShakeUiSettings::from)

    val freeWindowUiSettings: Flow<FreeWindowUiSettings> = slice(FreeWindowUiSettings::from)

    val otpUiSettings: Flow<OtpUiSettings> = slice(OtpUiSettings::from)

    val messageReminderSettings: Flow<MessageSettings> =
        slice { settings -> settings.messageReminderSettings }

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


