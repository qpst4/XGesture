package com.slideindex.app.cloudstorage.internal

import com.slideindex.app.cloudstorage.CloudStorageClient
import com.slideindex.app.cloudstorage.CloudStorageEntry
import com.slideindex.app.cloudstorage.CloudStorageException
import com.slideindex.app.cloudstorage.CloudStoragePath
import com.slideindex.app.cloudstorage.ObjectStorageEndpoint
import com.slideindex.app.cloudstorage.S3StorageConfig
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink

/**
 * S3 兼容对象存储客户端（AWS S3 / MinIO / Cloudflare R2 / 七牛 / 阿里云 OSS 的 S3 兼容端点）。
 *
 * 移植自 ClipShare 的 `S3Client`（minio-dart）+ `AliyunOssClient`（dart_aliyun_oss），
 * 但合并成一套 SigV4 实现；协议语义与它的兼容性妥协都被保留：
 * - **目录 = 0 字节 + 尾部 `/` 的伪对象**（对象存储没有真目录）
 * - 列举用 `ListObjectsV2` 的 `prefix` + `delimiter=/`，`CommonPrefixes` 当目录、
 *   `Contents` 当文件，并跳过以 `/` 结尾的目录标记对象
 * - `delimiter` 分页靠 `continuation-token` 循环
 * - 每个请求独立签名；空 body 用 [EMPTY_PAYLOAD_SHA256]
 */
internal class S3Client(
    override val config: S3StorageConfig,
    private val http: OkHttpClient,
    private val clock: () -> Instant = { Instant.now() },
) : CloudStorageClient {

    private val endpoint: HttpUrl
    private val region: String
    private val pathStyle: Boolean
    private val signer: SigV4Signer
    private val baseDir: String = CloudStoragePath.normalize(config.baseDir)

    init {
        val rewritten = ObjectStorageEndpoint.toS3Compatible(config.endpoint, config.bucket).trimEnd('/')
        endpoint = rewritten.toHttpUrlOrNull()
            ?: throw CloudStorageException("对象存储 Endpoint 无法解析：${config.endpoint}")
        // region 留空时优先从 OSS 端点推断：SigV4 的凭据作用域带地域，填错必然 SignatureDoesNotMatch。
        region = config.region?.trim()?.takeIf { it.isNotEmpty() }
            ?: ObjectStorageEndpoint.inferRegion(config.endpoint)
            ?: DEFAULT_REGION
        pathStyle = config.pathStyle || ObjectStorageEndpoint.forcePathStyle(rewritten)
        signer = SigV4Signer(config.accessKey.trim(), config.secretKey, region)
    }

    override suspend fun testConnection(): Unit = withContext(Dispatchers.IO) {
        // 只取 1 个 key：验证凭据 + bucket 可访问，不关心内容。
        val url = urlForObjectKey("", listOf("list-type" to "2", "max-keys" to "1"))
        val request = signedRequest("GET", url, EMPTY_PAYLOAD_SHA256, body = null)
        http.newCall(request).awaitResponse("测试连接") { response ->
            requireSuccess(response, "测试连接")
        }
    }

    override suspend fun list(path: String): List<CloudStorageEntry> = withContext(Dispatchers.IO) {
        val prefix = objectPrefix(path)
        val result = mutableListOf<CloudStorageEntry>()
        var continuationToken: String? = null
        var page = 0
        do {
            val query = buildList {
                add("list-type" to "2")
                add("delimiter" to "/")
                add("max-keys" to MAX_KEYS_PER_PAGE.toString())
                if (prefix.isNotEmpty()) add("prefix" to prefix)
                continuationToken?.let { add("continuation-token" to it) }
            }
            val url = urlForObjectKey("", query)
            val request = signedRequest("GET", url, EMPTY_PAYLOAD_SHA256, body = null)
            val parsed = http.newCall(request).awaitResponse("列出目录") { response ->
                requireSuccess(response, "列出目录")
                parseListResponse(response.body.string(), path)
            }
            result += parsed.entries
            continuationToken = parsed.nextToken
            page += 1
        } while (continuationToken != null && page < MAX_PAGES)
        result.sort()
        result
    }

    override suspend fun ensureDirectory(path: String): Unit = withContext(Dispatchers.IO) {
        if (objectKey(path).isEmpty()) return@withContext
        // 空对象 + 尾部斜杠，与 ClipShare 的目录标记完全一致。
        val url = urlFor(path, trailingSlash = true)
        val request = signedRequest("PUT", url, EMPTY_PAYLOAD_SHA256, body = EMPTY_BODY)
        http.newCall(request).awaitResponse("创建目录") { response ->
            requireSuccess(response, "创建目录")
        }
    }

    override suspend fun upload(
        remotePath: String,
        source: File,
        onProgress: (Long, Long) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val total = source.length()
        // 先算 body 哈希再上传：多读一遍本地文件，换取对全部 S3 兼容服务的兼容性
        // （不使用 UNSIGNED-PAYLOAD，也不用 OSS 不支持的 aws-chunked 编码）。
        val payloadSha256 = withContext(Dispatchers.IO) { sha256Hex(source.inputStream()) }
        coroutineContext.ensureActive()

        val url = urlFor(remotePath)
        val body = object : RequestBody() {
            override fun contentType() = OCTET_STREAM
            override fun contentLength() = total
            override fun writeTo(sink: BufferedSink) {
                var transferred = 0L
                source.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                        transferred += read
                        onProgress(transferred, total)
                    }
                }
            }
        }
        val request = signedRequest("PUT", url, payloadSha256, body)
        http.newCall(request).awaitResponse("上传") { response ->
            requireSuccess(response, "上传")
        }
    }

    override suspend fun download(
        remotePath: String,
        target: File,
        onProgress: (Long, Long) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val url = urlFor(remotePath)
        val request = signedRequest("GET", url, EMPTY_PAYLOAD_SHA256, body = null)
        http.newCall(request).awaitResponse("下载") { response ->
            requireSuccess(response, "下载")
            val declared = response.body.contentLength()
            target.parentFile?.mkdirs()
            var transferred = 0L
            target.outputStream().use { output ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        transferred += read
                        onProgress(transferred, if (declared > 0) declared else transferred)
                    }
                }
            }
        }
    }

    override suspend fun delete(remotePath: String): Unit = withContext(Dispatchers.IO) {
        val url = urlFor(remotePath)
        val request = signedRequest("DELETE", url, EMPTY_PAYLOAD_SHA256, body = null)
        http.newCall(request).awaitResponse("删除") { response ->
            // 404 视为成功：删除是幂等的，重复清理旧备份不应该报错。
            if (response.code == 404) return@awaitResponse Unit
            requireSuccess(response, "删除")
        }
    }

    // region URL / 请求构造

    /** 业务路径 → object key（带 baseDir 前缀）。 */
    private fun objectKey(path: String): String = CloudStoragePath.join(baseDir, path)

    /** 列举用前缀：必须以 `/` 结尾，否则 `backup2` 会被 `backup` 前缀误命中。 */
    private fun objectPrefix(path: String): String {
        val key = objectKey(path)
        return if (key.isEmpty()) "" else "$key/"
    }

    /**
     * 逻辑路径（相对 baseDir）→ URL。internal 而非 private：URL 构造
     * （path-style / 虚拟主机风格 / baseDir 前缀）需要能脱离网络单测。
     */
    internal fun urlFor(
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        trailingSlash: Boolean = false,
    ): HttpUrl = urlForObjectKey(objectKey(path), query, trailingSlash)

    /**
     * 完整 object key → URL。
     *
     * 列举目录时传空 key：此时要看的是 bucket 根，目录前缀通过 `prefix` query 表达，
     * 不能再拼一遍 baseDir（否则会变成列举 `baseDir` 自身）。
     */
    private fun urlForObjectKey(
        key: String,
        query: List<Pair<String, String>> = emptyList(),
        trailingSlash: Boolean = false,
    ): HttpUrl {
        val host = if (pathStyle) endpoint.host else "${config.bucket}.${endpoint.host}"
        val defaultPort = if (endpoint.scheme == "https") 443 else 80
        val port = if (endpoint.port == defaultPort) "" else ":${endpoint.port}"
        val basePath = endpoint.encodedPath.trimEnd('/')

        val segments = buildList {
            if (pathStyle) add(config.bucket)
            addAll(CloudStoragePath.requireSafe(key).split('/').filter { it.isNotEmpty() })
        }
        val path = buildString {
            append(basePath)
            segments.forEach { append('/').append(uriEncode(it, encodeSlash = true)) }
            if (trailingSlash) append('/')
        }.ifEmpty { "/" }

        val queryString = if (query.isEmpty()) {
            ""
        } else {
            query
                .map { (name, value) -> uriEncode(name, true) to uriEncode(value, true) }
                .sortedWith(compareBy({ it.first }, { it.second }))
                .joinToString("&") { (name, value) -> "$name=$value" }
                .let { "?$it" }
        }
        return "${endpoint.scheme}://$host$port$path$queryString".toHttpUrl()
    }

    private fun signedRequest(
        method: String,
        url: HttpUrl,
        payloadSha256: String,
        body: RequestBody?,
    ): Request {
        val headers = LinkedHashMap<String, String>()
        body?.contentType()?.let { headers["content-type"] = it.toString() }
        config.userAgent?.trim()?.takeIf { it.isNotEmpty() }?.let { headers["user-agent"] = it }

        val signed = signer.sign(method, url, headers, payloadSha256, clock())
        val builder = Request.Builder().url(url).method(method, body)
        signed.headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }

    // endregion

    // region 响应解析

    private fun parseListResponse(xml: String, requestedPath: String): ListPage {
        val document = XmlDocuments.parse(xml.toByteArray(Charsets.UTF_8))
            ?: throw CloudStorageException("列出目录失败：服务端返回的 XML 无法解析")
        val root = document.documentElement

        val entries = mutableListOf<CloudStorageEntry>()
        XmlDocuments.descendants(root, "CommonPrefixes").forEach { node ->
            val prefix = XmlDocuments.childText(node, "Prefix") ?: return@forEach
            val relative = publicPath(prefix)
            if (relative.isEmpty()) return@forEach
            entries += CloudStorageEntry(
                path = relative,
                name = relative.substringAfterLast('/'),
                isDirectory = true,
            )
        }
        XmlDocuments.descendants(root, "Contents").forEach { node ->
            val key = XmlDocuments.childText(node, "Key") ?: return@forEach
            // 目录标记对象（尾部斜杠）不是文件，不能出现在列表里。
            if (key.endsWith("/")) return@forEach
            val relative = publicPath(key)
            if (relative.isEmpty()) return@forEach
            entries += CloudStorageEntry(
                path = relative,
                name = relative.substringAfterLast('/'),
                isDirectory = false,
                size = XmlDocuments.childText(node, "Size")?.toLongOrNull(),
                lastModifiedEpochMs = parseIso8601(XmlDocuments.childText(node, "LastModified")),
            )
        }

        val truncated = XmlDocuments.childText(root, "IsTruncated")?.equals("true", ignoreCase = true) == true
        val nextToken = XmlDocuments.childText(root, "NextContinuationToken")?.takeIf { it.isNotEmpty() }
        return ListPage(entries = entries, nextToken = if (truncated) nextToken else null)
    }

    /** object key → 相对 baseDir 的逻辑路径；不属于 baseDir 时返回空串。 */
    private fun publicPath(key: String): String {
        val normalized = CloudStoragePath.normalize(key)
        return when {
            baseDir.isEmpty() -> normalized
            normalized == baseDir -> ""
            normalized.startsWith("$baseDir/") -> normalized.removePrefix("$baseDir/")
            else -> ""
        }
    }

    private fun requireSuccess(response: Response, action: String) {
        if (response.isSuccessful) return
        val detail = response.readErrorDetail()
        val serverCode = extractErrorCode(detail)
        val serverMessage = extractErrorMessage(detail)
        val suffix = buildString {
            if (serverCode != null) append(" [").append(serverCode).append(']')
            if (!serverMessage.isNullOrBlank()) append(' ').append(serverMessage)
            if (serverCode == null && serverMessage.isNullOrBlank() && detail.isNotBlank()) {
                append(' ').append(detail.take(200))
            }
        }
        throw CloudStorageException(
            message = "$action 失败：HTTP ${response.code}$suffix",
            httpStatus = response.code,
            serverCode = serverCode,
        )
    }

    private fun extractErrorCode(xml: String): String? = parseErrorField(xml, "Code")

    private fun extractErrorMessage(xml: String): String? = parseErrorField(xml, "Message")

    private fun parseErrorField(xml: String, field: String): String? {
        if (xml.isBlank()) return null
        val document = XmlDocuments.parse(xml.toByteArray(Charsets.UTF_8)) ?: return null
        return XmlDocuments.descendants(document.documentElement, field).firstOrNull()?.textContent?.trim()
    }

    private fun parseIso8601(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(value).toEpochMilli() }
            .getOrNull()
    }

    // endregion

    private data class ListPage(val entries: List<CloudStorageEntry>, val nextToken: String?)

    private companion object {
        const val DEFAULT_REGION = "us-east-1"
        const val MAX_KEYS_PER_PAGE = 1000

        /** 分页保护上限：正常情况下备份目录只有几十个对象，20 页足够且能防服务端异常导致的死循环。 */
        const val MAX_PAGES = 20

        val OCTET_STREAM = "application/octet-stream".toMediaType()
        val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(OCTET_STREAM)
    }
}
