package com.slideindex.app.cloudstorage.internal

import com.slideindex.app.cloudstorage.CloudStorageClient
import com.slideindex.app.cloudstorage.CloudStorageEntry
import com.slideindex.app.cloudstorage.CloudStorageException
import com.slideindex.app.cloudstorage.CloudStoragePath
import com.slideindex.app.cloudstorage.WebDavStorageConfig
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
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
 * WebDAV 客户端（Nextcloud / ownCloud / 群晖 / 坚果云 / 通用 WebDAV）。
 *
 * 移植自 ClipShare 的 `WebDavClient`（webdav_plus），保留它踩过的所有兼容性妥协：
 * - **Basic 预置鉴权**：流式上传遇到 401 无法重放请求体，所以每个请求都直接带 Authorization
 * - **服务端 URL 自带路径**（Nextcloud `/remote.php/dav/files/user`）必须从返回 href 里剥掉，
 *   否则上层会看到 `/remote.php/...` 这种前缀泄漏进逻辑路径
 * - **PROPFIND 返回的第一条是请求的集合自身**，必须丢弃
 * - **MKCOL 对已存在目录常返回 405**，不算失败；父目录缺失（409）时逐级补齐
 * - 命名空间前缀千奇百怪（`D:` / `d:` / `lp1:`），解析一律按 local name 匹配
 */
internal class WebDavClient(
    override val config: WebDavStorageConfig,
    private val http: OkHttpClient,
) : CloudStorageClient {

    private val server: HttpUrl
    private val serverSegments: List<String>
    private val baseDir: String = CloudStoragePath.normalize(config.baseDir)
    private val authorization: String = Credentials.basic(config.username, config.password)

    init {
        val normalized = config.server.trim().trimEnd('/')
        server = normalized.toHttpUrlOrNull()
            ?: throw CloudStorageException("WebDAV 服务器地址无法解析：${config.server}")
        serverSegments = server.pathSegments.filter { it.isNotEmpty() }
    }

    override suspend fun testConnection(): Unit = withContext(Dispatchers.IO) {
        // 用服务器根（而不是 baseDir）做连通性测试：baseDir 可能还没创建，
        // 不应该因为"目录不存在"就判定配置错误。
        val request = propfindRequest(server, depth = 0)
        http.newCall(request).awaitResponse("测试连接") { response ->
            if (!response.isSuccessful) throw davError(response, "测试连接")
            // 必须消费/关闭响应体，否则连接无法回收到连接池
            response.body.close()
        }
    }

    override suspend fun list(path: String): List<CloudStorageEntry> = withContext(Dispatchers.IO) {
        val normalizedPath = CloudStoragePath.normalize(path)
        val request = propfindRequest(urlFor(normalizedPath, isDirectory = true), depth = 1)
        val entries = http.newCall(request).awaitResponse("列出目录") { response ->
            when {
                // 目录尚未创建：这是首次备份前的正常状态，不该当成错误。
                response.code == 404 -> emptyList()
                !response.isSuccessful -> throw davError(response, "列出目录")
                else -> parseMultiStatus(response.body.bytes())
            }
        }
        entries
            .filter { it.path != normalizedPath && it.path.isNotEmpty() }
            .map {
                CloudStorageEntry(
                    path = it.path,
                    name = it.path.substringAfterLast('/'),
                    isDirectory = it.isDirectory,
                    size = it.size,
                    lastModifiedEpochMs = it.lastModifiedEpochMs,
                )
            }
            .sorted()
    }

    // 显式写 `: Unit` 是必要的：方法体里递归补齐父目录，
    // 让编译器推断返回类型会撞上 "recursive problem"。
    override suspend fun ensureDirectory(path: String): Unit = withContext(Dispatchers.IO) {
        val fullPath = CloudStoragePath.requireSafe(CloudStoragePath.join(baseDir, path))
        if (fullPath.isEmpty()) return@withContext
        ensureFullPath(fullPath)
    }

    /**
     * 逐级确保 [fullPath]（已含 baseDir）存在。
     *
     * 级联必须覆盖 baseDir 自己的层级，不能只按"公开路径"级联：
     * 用户在 WebDAV 上选了尚未创建的 baseDir（如 `/cebian`）时，
     * MKCOL `/cebian/backup` 会因父集合缺失返回 409，而按公开路径级联永远补不到 `/cebian`。
     */
    private suspend fun ensureFullPath(fullPath: String) {
        if (createCollection(fullPath)) return
        // 失败可能是"已存在"（405）也可能是"父目录缺失"（409），用 PROPFIND 区分，
        // 而不是像 ClipShare 那样把所有失败都当成"可能已存在"再无条件级联。
        if (directoryExists(fullPath)) return
        val parent = fullPath.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) ensureFullPath(parent)
        if (createCollection(fullPath) || directoryExists(fullPath)) return
        throw CloudStorageException("创建 WebDAV 目录失败：$fullPath")
    }

    override suspend fun upload(
        remotePath: String,
        source: File,
        onProgress: (Long, Long) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val total = source.length()
        val url = urlFor(remotePath, isDirectory = false)
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
        val request = baseRequest(url).put(body).build()
        http.newCall(request).awaitResponse("上传") { response ->
            if (!response.isSuccessful) throw davError(response, "上传")
            response.body.close()
        }
    }

    override suspend fun download(
        remotePath: String,
        target: File,
        onProgress: (Long, Long) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val url = urlFor(remotePath, isDirectory = false)
        val request = baseRequest(url).get().build()
        http.newCall(request).awaitResponse("下载") { response ->
            if (!response.isSuccessful) throw davError(response, "下载")
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
        val url = urlFor(remotePath, isDirectory = false)
        // OkHttp 要求 DELETE 也带 body（permitRequestBody 对非 GET/HEAD 为 true），所以给空体。
        val request = baseRequest(url).delete(EMPTY_BODY).build()
        http.newCall(request).awaitResponse("删除") { response ->
            if (response.code == 404) return@awaitResponse Unit
            if (!response.isSuccessful) throw davError(response, "删除")
            response.body.close()
        }
    }

    // region 目录操作

    /** 发送一次 MKCOL；返回 false 表示"目标已存在或父目录缺失"，由调用方决定后续动作。 */
    private suspend fun createCollection(fullPath: String): Boolean {
        val url = urlForFull(fullPath, isDirectory = true)
        val request = baseRequest(url).method("MKCOL", EMPTY_BODY).build()
        return http.newCall(request).awaitResponse("创建目录") { response ->
            when {
                response.isSuccessful -> true
                // 405 是"已存在"最常见的表达；301/409 交给上层用 PROPFIND / 级联兜底。
                response.code == 405 || response.code == 301 || response.code == 409 -> false
                else -> throw davError(response, "创建目录")
            }
        }
    }

    private suspend fun directoryExists(fullPath: String): Boolean {
        val request = propfindRequest(urlForFull(fullPath, isDirectory = true), depth = 0)
        return runCatching {
            http.newCall(request).awaitResponse("确认目录") { response ->
                if (!response.isSuccessful) return@awaitResponse false
                parseMultiStatus(response.body.bytes()).firstOrNull()?.isDirectory == true
            }
        }.getOrDefault(false)
    }

    // endregion

    // region 请求构造与解析

    private fun baseRequest(url: HttpUrl): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", authorization)
            .apply {
                config.userAgent?.trim()?.takeIf { it.isNotEmpty() }?.let { header("User-Agent", it) }
            }

    private fun propfindRequest(url: HttpUrl, depth: Int): Request =
        baseRequest(url)
            .method("PROPFIND", PROPFIND_BODY)
            .header("Depth", depth.toString())
            .build()

    /** 逻辑路径（相对 baseDir）→ URL。internal 而非 private：路径处理需要能脱离网络单测。 */
    internal fun urlFor(path: String, isDirectory: Boolean): HttpUrl =
        urlForFull(CloudStoragePath.join(baseDir, path), isDirectory)

    /** 完整远端路径（已含 baseDir）→ URL。 */
    private fun urlForFull(fullPath: String, isDirectory: Boolean): HttpUrl {
        val key = CloudStoragePath.requireSafe(fullPath)
        val pathString = buildString {
            append(server.encodedPath.trimEnd('/'))
            key.split('/').filter { it.isNotEmpty() }.forEach { append('/').append(uriEncode(it, encodeSlash = true)) }
            if (isDirectory && key.isNotEmpty()) append('/')
        }.ifEmpty { "/" }
        val defaultPort = if (server.scheme == "https") 443 else 80
        val port = if (server.port == defaultPort) "" else ":${server.port}"
        return "${server.scheme}://${server.host}$port$pathString".toHttpUrl()
    }

    private fun parseMultiStatus(xml: ByteArray): List<DavEntry> {
        val document = XmlDocuments.parse(xml)
            ?: throw CloudStorageException("列出目录失败：WebDAV 返回的 XML 无法解析")
        return XmlDocuments.descendants(document.documentElement, "response").mapNotNull { response ->
            val href = XmlDocuments.childText(response, "href") ?: return@mapNotNull null
            val path = logicalPath(href) ?: return@mapNotNull null
            val prop = successfulProp(response)
            DavEntry(
                path = path,
                isDirectory = XmlDocuments.descendants(prop, "collection").isNotEmpty(),
                size = XmlDocuments.descendants(prop, "getcontentlength").firstOrNull()
                    ?.textContent?.trim()?.toLongOrNull(),
                lastModifiedEpochMs = parseHttpDate(
                    XmlDocuments.descendants(prop, "getlastmodified").firstOrNull()?.textContent?.trim(),
                ),
            )
        }
    }

    /**
     * 取 200 那段 propstat 里的 prop。
     *
     * PROPFIND 会对不存在的属性返回第二段 `propstat`（404），先命中它会得到空的
     * resourcetype，把目录误判成文件。
     */
    private fun successfulProp(response: org.w3c.dom.Element): org.w3c.dom.Element {
        val propStats = XmlDocuments.descendants(response, "propstat")
        val ok = propStats.firstOrNull { propStat ->
            XmlDocuments.childText(propStat, "status")?.contains(" 200") == true
        }
        val chosen = ok ?: propStats.firstOrNull() ?: response
        return XmlDocuments.descendants(chosen, "prop").firstOrNull() ?: chosen
    }

    /**
     * href → 相对 baseDir 的逻辑路径。
     *
     * 用 **路径段** 而不是字符串前缀来剥离服务端路径（`/remote.php/dav/files/user`）与 baseDir：
     * 字符串前缀剥离在只配置了部分前缀、或服务端返回大小写/编码差异的 href 时会留下脏前缀。
     */
    private fun logicalPath(href: String): String? {
        val absolute = if (href.startsWith("http://") || href.startsWith("https://")) {
            href
        } else {
            server.resolve(href)?.toString() ?: return null
        }
        val url = absolute.toHttpUrlOrNull() ?: return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }

        if (segments.size < serverSegments.size || segments.take(serverSegments.size) != serverSegments) return null
        var rest = segments.drop(serverSegments.size)

        val baseSegments = baseDir.split('/').filter { it.isNotEmpty() }
        if (baseSegments.isNotEmpty()) {
            if (rest.size < baseSegments.size || rest.take(baseSegments.size) != baseSegments) return null
            rest = rest.drop(baseSegments.size)
        }
        return rest.joinToString("/")
    }

    private fun davError(response: Response, action: String): CloudStorageException {
        val detail = response.readErrorDetail().replace(Regex("\\s+"), " ").trim()
        val suffix = if (detail.isBlank()) "" else " ${detail.take(200)}"
        return CloudStorageException(
            message = "$action 失败：WebDAV HTTP ${response.code}$suffix",
            httpStatus = response.code,
        )
    }

    private fun parseHttpDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        // WebDAV 规范要求 RFC1123，但实际实现里 ISO8601 也很常见，两种都认。
        return runCatching { Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value)).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(value).toEpochMilli() }
            .getOrNull()
    }

    private data class DavEntry(
        val path: String,
        val isDirectory: Boolean,
        val size: Long?,
        val lastModifiedEpochMs: Long?,
    )

    // endregion

    private companion object {
        val OCTET_STREAM = "application/octet-stream".toMediaType()
        val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(OCTET_STREAM)

        /** 只要 resourcetype / 长度 / 修改时间三项：备份列表与保留策略只需要这些。 */
        val PROPFIND_BODY: RequestBody = (
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<d:propfind xmlns:d=\"DAV:\"><d:prop>" +
                "<d:resourcetype/><d:getcontentlength/><d:getlastmodified/>" +
                "</d:prop></d:propfind>"
            ).toRequestBody("application/xml; charset=utf-8".toMediaType())
    }
}
