package com.slideindex.app.cloudbackup

import android.content.Context
import com.slideindex.app.BuildConfig
import com.slideindex.app.cloudstorage.CloudBackupCatalog
import com.slideindex.app.cloudstorage.CloudBackupEntry
import com.slideindex.app.cloudstorage.CloudStorageClientFactory
import com.slideindex.app.cloudstorage.CloudStorageConfig
import com.slideindex.app.cloudstorage.CloudStorageException
import com.slideindex.app.cloudstorage.CloudStorageSettings
import com.slideindex.app.settings.CloudStorageConfigRepository
import com.slideindex.app.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 备份过程中的阶段，供界面显示进度。 */
sealed interface CloudBackupStage {
    /** 本地打包（zip 生成中，无百分比可报）。 */
    data object Packing : CloudBackupStage

    data class Uploading(val transferred: Long, val total: Long) : CloudBackupStage

    data class Downloading(val transferred: Long, val total: Long) : CloudBackupStage
}

data class CloudBackupOutcome(
    val entry: CloudBackupEntry,
    /** 因保留策略被清理掉的旧备份文件名。 */
    val pruned: List<String>,
)

/**
 * 云端备份/恢复的流程编排。
 *
 * 与 ClipShare 的实现相比，这里做对了三件它没做的事：
 * 1. **远端删除与保留策略**：ClipShare 只会上传，旧备份只能拿第三方客户端手工删；
 * 2. **同名覆盖**：它的文件名精确到天，同一天备份两次会静默覆盖；这里精确到秒；
 * 3. **临时文件收尾**：无论成功失败都清掉本地临时包，不留垃圾。
 *
 * 全局互斥（[gate]）：备份与恢复互斥，避免用户连点两次把两个 zip 同时写进同一目录。
 */
@Singleton
class CloudBackupCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val configRepository: CloudStorageConfigRepository,
    private val sectionsProvider: SettingsBackupSectionsProvider,
) {
    private val gate = Mutex()

    suspend fun readSettings(): CloudStorageSettings = configRepository.read()

    /** 校验配置可用；失败抛 [CloudStorageException]。 */
    suspend fun testConnection(config: CloudStorageConfig) = withContext(Dispatchers.IO) {
        CloudStorageClientFactory.create(config).use { it.testConnection() }
    }

    suspend fun listBackups(config: CloudStorageConfig): List<CloudBackupEntry> = withContext(Dispatchers.IO) {
        CloudStorageClientFactory.create(config).use { CloudBackupCatalog(it).listBackups() }
    }

    /**
     * 打包 → 上传 →（按保留策略）清理。
     *
     * 上传成功后顺手清理失败不会让整次备份失败：备份已经躺在远端了，
     * 用户不该因为"删旧文件失败"而以为备份没成功。
     */
    suspend fun backupNow(
        config: CloudStorageConfig,
        includeSensitiveData: Boolean,
        retentionCount: Int,
        onStage: (CloudBackupStage) -> Unit = {},
    ): CloudBackupOutcome = gate.withLock {
        onStage(CloudBackupStage.Packing)
        val zipFile = File(freshWorkDir(), PENDING_ZIP_NAME)
        try {
            val sections = sectionsProvider.build(includeSensitiveData)
            withContext(Dispatchers.IO) {
                zipFile.outputStream().use { output ->
                    settingsRepository
                        .exportSettings(BuildConfig.VERSION_NAME, sections, output)
                        .getOrThrow()
                }
            }

            withContext(Dispatchers.IO) {
                CloudStorageClientFactory.create(config).use { client ->
                    val catalog = CloudBackupCatalog(client)
                    val entry = catalog.uploadBackup(
                        localFile = zipFile,
                        appVersionName = BuildConfig.VERSION_NAME,
                        now = Instant.now(),
                        onProgress = { transferred, total ->
                            onStage(CloudBackupStage.Uploading(transferred, total))
                        },
                    )
                    val pruned = runCatching { catalog.prune(retentionCount) }.getOrDefault(emptyList())
                    CloudBackupOutcome(entry = entry, pruned = pruned)
                }
            }
        } finally {
            zipFile.delete()
        }
    }

    /** 下载一份备份到缓存目录；返回的文件由调用方在恢复完成后删除。 */
    suspend fun downloadBackup(
        config: CloudStorageConfig,
        entry: CloudBackupEntry,
        onStage: (CloudBackupStage) -> Unit = {},
    ): File = gate.withLock {
        val target = File(freshWorkDir(), entry.name)
        withContext(Dispatchers.IO) {
            CloudStorageClientFactory.create(config).use { client ->
                CloudBackupCatalog(client).downloadBackup(
                    name = entry.name,
                    target = target,
                    onProgress = { transferred, total ->
                        onStage(CloudBackupStage.Downloading(transferred, total))
                    },
                )
            }
        }
        target
    }

    suspend fun deleteBackup(config: CloudStorageConfig, entry: CloudBackupEntry) = withContext(Dispatchers.IO) {
        CloudStorageClientFactory.create(config).use { CloudBackupCatalog(it).deleteBackup(entry.name) }
    }

    suspend fun prune(config: CloudStorageConfig, retentionCount: Int): List<String> = withContext(Dispatchers.IO) {
        CloudStorageClientFactory.create(config).use { CloudBackupCatalog(it).prune(retentionCount) }
    }

    /**
     * 每次操作前清空工作目录。
     *
     * 备份包可能几十 MB，中断过的残留会一直占着缓存；这里直接整目录重建，
     * 不用去猜哪些文件是"上次没删掉的"。
     */
    private fun freshWorkDir(): File {
        val dir = File(context.cacheDir, WORK_DIR_NAME)
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()
        return dir
    }

    private companion object {
        const val WORK_DIR_NAME = "cloud-backup"
        const val PENDING_ZIP_NAME = "pending-backup.zip"
    }
}
