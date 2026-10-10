package com.slideindex.app.cloudstorage

import java.io.Closeable
import java.io.File

/**
 * 远端存储条目。[path] 是相对 baseDir 的逻辑路径（不带首尾 `/`），
 * 调用方不需要知道 WebDAV 的 `remote.php` 前缀或对象存储的 bucket 前缀。
 *
 * [size] / [lastModifiedEpochMs] 允许为空：WebDAV 的 PROPFIND 与 S3 的 ListObjectsV2
 * 都"通常"能给出这两个字段，但都不是强制项，缺失时界面退化成只显示名字。
 *
 * 排序规则与 ClipShare `StorageItem` 一致：目录优先，其次按名字大小写不敏感排序。
 */
data class CloudStorageEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long? = null,
    val lastModifiedEpochMs: Long? = null,
) : Comparable<CloudStorageEntry> {
    override fun compareTo(other: CloudStorageEntry): Int = when {
        isDirectory && !other.isDirectory -> -1
        !isDirectory && other.isDirectory -> 1
        else -> name.compareTo(other.name, ignoreCase = true)
    }
}

/**
 * 统一的远端存储操作接口，对应 ClipShare 的 `StorageClient` 抽象。
 *
 * 与 ClipShare 的差异（刻意的）：
 * 1. **失败抛异常而不是静默返回 null/false**。ClipShare 每个方法都 `try/catch` 后返回
 *    false，UI 只能提示"导出失败，请查看日志"；这里用 [CloudStorageException] 把
 *    HTTP 状态码与服务端错误码带到界面，用户能直接看到 `SignatureDoesNotMatch` 这类原因。
 * 2. **上传/下载可被协程取消**：OkHttp 的 call 会被 cancel，网络传输真正中断
 *    （ClipShare 的取消只覆盖打包/入库阶段，HTTP 传输无法中止）。
 * 3. 进度回调对上传/下载都有效（ClipShare 的 WebDAV 内存写文件路径只有末尾一次回调）。
 */
interface CloudStorageClient : Closeable {
    val config: CloudStorageConfig

    /** 校验配置可用（凭据 + 目标位置可访问）。失败抛 [CloudStorageException]。 */
    suspend fun testConnection()

    /** 列出 [path] 下的单层内容；[path] 为空表示 baseDir 根。 */
    suspend fun list(path: String = ""): List<CloudStorageEntry>

    /** 确保目录存在（已存在视为成功）；父目录缺失时按需逐级创建。 */
    suspend fun ensureDirectory(path: String)

    /** 上传本地文件到 [remotePath]（相对 baseDir）。 */
    suspend fun upload(
        remotePath: String,
        source: File,
        onProgress: (transferred: Long, total: Long) -> Unit = { _, _ -> },
    )

    /** 下载 [remotePath] 到本地 [target]（父目录会被创建）。 */
    suspend fun download(
        remotePath: String,
        target: File,
        onProgress: (transferred: Long, total: Long) -> Unit = { _, _ -> },
    )

    /** 删除单个文件；不存在视为成功（幂等）。 */
    suspend fun delete(remotePath: String)

    override fun close() = Unit
}
