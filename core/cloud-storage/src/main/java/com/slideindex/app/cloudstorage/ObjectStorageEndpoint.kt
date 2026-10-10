package com.slideindex.app.cloudstorage

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 对象存储端点处理。
 *
 * 核心规则（阿里云官方文档：OSS 兼容 AWS S3 API，使用 S3 兼容 Endpoint 即可用 AWS SDK 访问）：
 * 原生端点 `oss-{region}.aliyuncs.com` → S3 兼容端点 `s3.oss-{region}.aliyuncs.com`，
 * 规律就是给 host 加 `s3.` 前缀。内网 `oss-{region}-internal` 与加速 `oss-accelerate` 同理。
 *
 * 有了这层改写，配置界面不需要"存储类型"下拉（ClipShare 有，是因为它接了两个签名 SDK），
 * 用户从阿里云控制台复制的原生端点也照样能用。
 */
object ObjectStorageEndpoint {
    private const val OSS_SUFFIX = ".aliyuncs.com"
    private const val DEFAULT_SCHEME = "https://"
    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    private val OSS_REGION = Regex("""(?:^|\.)oss-([a-z0-9-]+?)(-internal)?\.aliyuncs\.com$""", RegexOption.IGNORE_CASE)

    /** 把阿里云 OSS 原生端点改写为 S3 兼容端点；其它端点原样返回（仅补 scheme）。 */
    fun toS3Compatible(endpoint: String, bucket: String): String {
        val url = parse(endpoint) ?: return endpoint.trim()
        var host = url.host
        if (!host.endsWith(OSS_SUFFIX, ignoreCase = true)) return url.toString()
        if (host.startsWith("s3.", ignoreCase = true)) return url.toString()

        // 虚拟主机风格的原生端点会把 bucket 写进 host（mybucket.oss-cn-...），
        // 而本模块稍后会按 pathStyle 自行拼 bucket，这里必须先剥掉，否则会拼成 mybucket.mybucket.oss-...
        if (bucket.isNotBlank()) {
            val bucketPrefix = "${bucket.lowercase()}."
            if (host.lowercase().startsWith(bucketPrefix)) {
                host = host.substring(bucketPrefix.length)
            }
        }
        if (!host.startsWith("oss", ignoreCase = true)) return url.toString()
        return url.newBuilder().host("s3.$host").build().toString()
    }

    /**
     * 从端点推断地域（`oss-cn-hangzhou` → `cn-hangzhou`）。
     *
     * SigV4 的凭据作用域里带地域，填错会直接 `SignatureDoesNotMatch`；
     * 阿里云端点的 host 自带地域，用户不必再手填一遍。加速端点没有地域信息，返回 null。
     */
    fun inferRegion(endpoint: String): String? {
        val host = parse(endpoint)?.host ?: return null
        val match = OSS_REGION.find(host) ?: return null
        val region = match.groupValues[1].lowercase()
        return region.takeIf { it.isNotEmpty() && it != "accelerate" }
    }

    /**
     * 是否必须使用 path-style。
     *
     * 虚拟主机风格要把 bucket 当成域名标签（`bucket.host`），而 IP / localhost / IPv6
     * 字面量无法这么解析，只能退化为 path-style（MinIO 自建场景几乎都是这种）。
     */
    fun forcePathStyle(endpoint: String): Boolean {
        val host = parse(endpoint)?.host ?: return true
        if (host.equals("localhost", ignoreCase = true) || host.endsWith(".local", ignoreCase = true)) return true
        if (IPV4.matches(host)) return true
        // IPv6 字面量在 HttpUrl 里会带方括号
        if (host.contains(':')) return true
        return false
    }

    /** 端点是否可用于建连（协议 + 主机名基本合法）。 */
    fun isUsable(endpoint: String): Boolean {
        val url = parse(endpoint) ?: return false
        return (url.scheme == "http" || url.scheme == "https") && url.host.isNotBlank()
    }

    private fun parse(endpoint: String): HttpUrl? {
        val trimmed = endpoint.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            DEFAULT_SCHEME + trimmed
        }
        return runCatching { withScheme.toHttpUrlOrNull() }.getOrNull()
    }
}
