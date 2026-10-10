package com.slideindex.app.cloudstorage

import java.io.File
import java.time.Instant

/** 远端的一份备份。[createdAtEpochMs] 优先取文件名里的时间戳，取不到才退回远端修改时间。 */
data class CloudBackupEntry(
    val name: String,
    val createdAtEpochMs: Long?,
    val sizeBytes: Long?,
    val lastModifiedEpochMs: Long?,
    val appVersionName: String?,
) {
    /** 排序键：越新越大。缺时间信息时排到最后（而不是当成最新）。 */
    val sortKey: Long
        get() = createdAtEpochMs ?: lastModifiedEpochMs ?: Long.MIN_VALUE
}

/**
 * 云端备份目录的目录学：列出、上传、下载、删除、按保留份数清理。
 *
 * 这一层是 ClipShare 完全没有的：它的远端只有"一堆 zip"，既没有版本列表（靠文件浏览器手点），
 * 也没有删除入口，更没有保留策略，用久了只能拿第三方客户端手工清理。
 */
class CloudBackupCatalog(private val client: CloudStorageClient) {

    /** 确保 `<baseDir>/backup/` 存在（WebDAV 需要显式建集合；对象存储写伪对象）。 */
    suspend fun ensureReady() {
        client.ensureDirectory(CloudBackupNaming.BACKUP_DIR)
    }

    /**
     * 列出远端备份，新的在前。
     *
     * 刻意不做"只认自家命名"的过滤：用户手工放进去或改过名的 zip 也应该能恢复，
     * 否则一个重命名就让人以为备份丢了。时间戳解析不到时用远端修改时间兜底。
     */
    suspend fun listBackups(): List<CloudBackupEntry> =
        client.list(CloudBackupNaming.BACKUP_DIR)
            .asSequence()
            .filter { !it.isDirectory && CloudBackupNaming.isBackupFile(it.name) }
            .map { entry ->
                CloudBackupEntry(
                    name = entry.name,
                    createdAtEpochMs = CloudBackupNaming.parseCreatedAt(entry.name),
                    sizeBytes = entry.size,
                    lastModifiedEpochMs = entry.lastModifiedEpochMs,
                    appVersionName = CloudBackupNaming.parseAppVersion(entry.name),
                )
            }
            .sortedWith(compareByDescending<CloudBackupEntry> { it.sortKey }.thenByDescending { it.name })
            .toList()

    /** 生成文件名并上传；返回的是这次写入的备份条目。 */
    suspend fun uploadBackup(
        localFile: File,
        appVersionName: String?,
        now: Instant = Instant.now(),
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): CloudBackupEntry {
        ensureReady()
        val fileName = CloudBackupNaming.buildFileName(now, appVersionName)
        val remotePath = "${CloudBackupNaming.BACKUP_DIR}/$fileName"
        client.upload(remotePath, localFile, onProgress)
        return CloudBackupEntry(
            name = fileName,
            createdAtEpochMs = now.toEpochMilli(),
            sizeBytes = localFile.length(),
            lastModifiedEpochMs = now.toEpochMilli(),
            appVersionName = CloudBackupNaming.parseAppVersion(fileName),
        )
    }

    suspend fun downloadBackup(
        name: String,
        target: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ) {
        client.download(remotePath(name), target, onProgress)
    }

    suspend fun deleteBackup(name: String) {
        client.delete(remotePath(name))
    }

    /**
     * 按保留份数清理旧备份，返回被删掉的文件名（新 → 旧之外的部分）。
     *
     * [retainCount] 小于等于 0 表示不限制。清理失败不抛异常：备份已经上传成功了，
     * 不能因为"顺手删旧的"失败就让用户以为整次备份失败；调用方拿返回值决定是否提示。
     */
    suspend fun prune(retainCount: Int): List<String> {
        if (retainCount <= 0) return emptyList()
        val backups = listBackups()
        if (backups.size <= retainCount) return emptyList()
        val expired = backups.drop(retainCount)
        val deleted = mutableListOf<String>()
        expired.forEach { entry ->
            runCatching { deleteBackup(entry.name) }.onSuccess { deleted += entry.name }
        }
        return deleted
    }

    private fun remotePath(name: String): String =
        "${CloudBackupNaming.BACKUP_DIR}/${CloudStoragePath.requireSafe(name)}"
}
