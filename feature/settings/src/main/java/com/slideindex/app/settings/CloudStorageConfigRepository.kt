package com.slideindex.app.settings

import com.slideindex.app.cloudstorage.CloudStorageConfig
import com.slideindex.app.cloudstorage.CloudStorageConfigCodec
import com.slideindex.app.cloudstorage.CloudStorageSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 云存储配置的读写入口。
 *
 * 持久化落在 DataStore 的一个字符串键上（见 [SettingsPreferenceKeys.CLOUD_STORAGE_SETTINGS_JSON]），
 * 因此它会随 `settings.json` 一起被本地备份带走、也能被恢复回来；
 * 这里只负责"JSON ↔ 领域模型"和配置列表的增删改，协议细节全在 `:core:cloud-storage`。
 *
 * 多进程说明：`SettingsPreferencesEditor` 用的是 MultiProcessDataStore，
 * `:overlay` 进程也能读到同一份配置。
 */
@Singleton
class CloudStorageConfigRepository @Inject constructor(
    private val editor: SettingsPreferencesEditor,
) {
    val settings: Flow<CloudStorageSettings> = editor.cloudStorageSettingsJson
        .map(CloudStorageConfigCodec::decode)

    suspend fun read(): CloudStorageSettings =
        CloudStorageConfigCodec.decode(editor.readRawPreferences()[SettingsPreferenceKeys.CLOUD_STORAGE_SETTINGS_JSON])

    suspend fun save(settings: CloudStorageSettings): Result<Unit> = editor.edit { prefs ->
        prefs[SettingsPreferenceKeys.CLOUD_STORAGE_SETTINGS_JSON] = CloudStorageConfigCodec.encode(settings)
    }

    /** 新增或按 id 覆盖；新配置如果还没有当前选中项，会自动被选为当前项。 */
    suspend fun upsert(config: CloudStorageConfig): Result<Unit> {
        val current = read()
        val configs = current.configs.filterNot { it.id == config.id } + config
        return save(
            current.copy(
                configs = configs,
                activeConfigId = current.activeConfigId ?: config.id,
            ),
        )
    }

    suspend fun remove(configId: String): Result<Unit> {
        val current = read()
        val configs = current.configs.filterNot { it.id == configId }
        val activeId = if (current.activeConfigId == configId) configs.firstOrNull()?.id else current.activeConfigId
        return save(current.copy(configs = configs, activeConfigId = activeId))
    }

    suspend fun setActive(configId: String): Result<Unit> {
        val current = read()
        if (current.configs.none { it.id == configId }) return Result.success(Unit)
        return save(current.copy(activeConfigId = configId))
    }

    /** 保留份数：小于等于 0 表示不限制（与 [CloudStorageSettings.hasRetentionLimit] 一致）。 */
    suspend fun setRetentionCount(count: Int): Result<Unit> {
        val current = read()
        val clamped = count.coerceIn(0, CloudStorageSettings.MAX_RETENTION_COUNT)
        return save(current.copy(retentionCount = clamped))
    }
}
