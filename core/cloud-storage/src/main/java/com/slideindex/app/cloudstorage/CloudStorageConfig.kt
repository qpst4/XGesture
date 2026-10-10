package com.slideindex.app.cloudstorage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 云存储后端配置。
 *
 * 移植自 ClipShare 的 `WebDAVConfig` / `S3Config`，但做了两点结构性调整：
 * 1. 对象存储不再区分「阿里云 OSS / 标准 S3」两种类型——OSS 兼容 S3 API 且支持 SigV4，
 *    原生端点由 [ObjectStorageEndpoint] 自动改写为 S3 兼容端点，因此只需要一套签名实现，
 *    用户也不必在配置界面里选存储类型（选错就连不上）。
 * 2. 单份配置扩展为配置列表 + 当前选中项，见 [CloudStorageSettings]。
 *
 * 不变量：[baseDir] 是远端逻辑根目录，备份文件写在 `<baseDir>/backup/` 下。
 */
@Serializable
sealed class CloudStorageConfig {
    abstract val id: String

    abstract val displayName: String

    abstract val baseDir: String
}

/**
 * WebDAV 后端（Nextcloud / 群晖 / 坚果云 / 通用 WebDAV）。
 *
 * 凭据按 HTTP Basic 预置鉴权发送：流式上传一旦遇到 401 无法重放请求体，
 * 所以首个请求就必须带 `Authorization`（ClipShare 用 `isPreemptive: true` 表达同一件事）。
 */
@Serializable
@SerialName("webdav")
data class WebDavStorageConfig(
    override val id: String,
    override val displayName: String,
    override val baseDir: String,
    val server: String,
    val username: String,
    val password: String,
    val userAgent: String? = null,
) : CloudStorageConfig() {
    /** 口令绝不能进日志：与 ClipShare 的 `toString()` 脱敏策略保持一致。 */
    override fun toString(): String =
        "WebDavStorageConfig(id=$id, displayName=$displayName, baseDir=$baseDir, " +
            "server=<redacted>, username=<redacted>, userAgent=$userAgent)"
}

/**
 * S3 兼容对象存储（AWS S3 / MinIO / Cloudflare R2 / 七牛 / 阿里云 OSS 的 S3 兼容端点）。
 *
 * [endpoint] 允许填写阿里云 OSS 的原生端点（`oss-cn-hangzhou.aliyuncs.com`），
 * 请求前会被 [ObjectStorageEndpoint] 自动改写为 `s3.oss-cn-hangzhou.aliyuncs.com`。
 * [region] 留空时同样尝试从端点推断（`oss-cn-hangzhou` → `cn-hangzhou`）。
 */
@Serializable
@SerialName("s3")
data class S3StorageConfig(
    override val id: String,
    override val displayName: String,
    override val baseDir: String,
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val region: String? = null,
    val pathStyle: Boolean = false,
    val userAgent: String? = null,
) : CloudStorageConfig() {
    /** 只脱敏密钥；endpoint / bucket 会出现在请求 URL 里，保留它们便于排查。 */
    override fun toString(): String =
        "S3StorageConfig(id=$id, displayName=$displayName, baseDir=$baseDir, " +
            "endpoint=$endpoint, bucket=$bucket, region=$region, pathStyle=$pathStyle, " +
            "accessKey=<redacted>, secretKey=<redacted>, userAgent=$userAgent)"
}
