package com.slideindex.app.cloudstorage.internal

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import okhttp3.HttpUrl

/**
 * AWS Signature Version 4 签名器。
 *
 * 单一实现覆盖 AWS S3 / MinIO / Cloudflare R2 / 七牛 / 阿里云 OSS 的 S3 兼容端点：
 * ClipShare 为阿里云 OSS 单独接了一套 OSS 自有签名的 SDK（配置界面因此多了个"存储类型"下拉），
 * 而 OSS 官方声明其 S3 兼容端点完整支持 SigV4，所以这里只需要一套算法
 * （见 https://help.aliyun.com/zh/oss/developer-reference/use-aws-sdks-to-access-oss）。
 *
 * 唯一已知的 OSS 差异：OSS 不支持 `aws-chunked` 传输编码。本实现固定计算真实 body 哈希
 * 并显式设置 Content-Length，天然不会触发 chunked 编码，所以这个坑绕开了。
 */
internal class SigV4Signer(
    private val accessKey: String,
    private val secretKey: String,
    private val region: String,
    private val service: String = "s3",
) {
    /**
     * [headers] 是除 `host` / `x-amz-date` / `authorization` 之外、真正要随请求发送的头；
     * 返回值里已经把它们小写化，调用方直接全部写入请求即可。
     */
    fun sign(
        method: String,
        url: HttpUrl,
        headers: Map<String, String>,
        payloadSha256: String,
        instant: Instant,
    ): SignedRequest {
        val amzDate = AMZ_DATE.format(instant)
        val dateStamp = DATE_STAMP.format(instant)

        val allHeaders = LinkedHashMap<String, String>()
        headers.forEach { (name, value) -> allHeaders[name.lowercase()] = value.trim() }
        allHeaders["host"] = hostHeader(url)
        allHeaders["x-amz-content-sha256"] = payloadSha256
        allHeaders["x-amz-date"] = amzDate

        val signedHeaderNames = allHeaders.keys.sorted()
        val canonicalHeaders = buildString {
            signedHeaderNames.forEach { name -> append(name).append(':').append(allHeaders.getValue(name)).append('\n') }
        }
        val signedHeaders = signedHeaderNames.joinToString(";")

        val canonicalRequest = buildString {
            append(method.uppercase()).append('\n')
            append(canonicalUri(url)).append('\n')
            append(canonicalQuery(url)).append('\n')
            append(canonicalHeaders).append('\n')
            append(signedHeaders).append('\n')
            append(payloadSha256)
        }

        val scope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = buildString {
            append("AWS4-HMAC-SHA256").append('\n')
            append(amzDate).append('\n')
            append(scope).append('\n')
            append(sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8)))
        }

        val signature = hmacSha256Hex(signingKey(dateStamp), stringToSign)
        val authorization =
            "AWS4-HMAC-SHA256 Credential=$accessKey/$scope, " +
                "SignedHeaders=$signedHeaders, Signature=$signature"

        val outHeaders = LinkedHashMap(allHeaders)
        outHeaders["authorization"] = authorization
        return SignedRequest(
            headers = outHeaders,
            canonicalRequest = canonicalRequest,
            stringToSign = stringToSign,
            signature = signature,
        )
    }

    private fun signingKey(dateStamp: String): ByteArray {
        val dateKey = hmacSha256("AWS4$secretKey".toByteArray(Charsets.UTF_8), dateStamp)
        val regionKey = hmacSha256(dateKey, region)
        val serviceKey = hmacSha256(regionKey, service)
        return hmacSha256(serviceKey, "aws4_request")
    }

    /**
     * 规范 URI 直接用 OkHttp 已编码的 path。
     *
     * 前提：本模块构造的所有 key 只含 `A-Za-z0-9-_.~/`，OkHttp 的编码结果与 SigV4 的
     * RFC3986 编码在这些字符上完全一致；一旦将来允许任意字符的 key，这里需要改成
     * 逐段 `uriEncode(segment, encodeSlash = false)`。
     */
    private fun canonicalUri(url: HttpUrl): String = url.encodedPath.ifEmpty { "/" }

    private fun canonicalQuery(url: HttpUrl): String {
        val pairs = (0 until url.querySize).map { index ->
            val name = uriEncode(url.queryParameterName(index), encodeSlash = true)
            val value = url.queryParameterValue(index)?.let { uriEncode(it, encodeSlash = true) } ?: ""
            name to value
        }.sortedWith(compareBy({ it.first }, { it.second }))
        return pairs.joinToString("&") { (name, value) -> "$name=$value" }
    }

    /** host 头必须与实际连接的主机一致；非默认端口要带上端口。 */
    private fun hostHeader(url: HttpUrl): String {
        val defaultPort = when (url.scheme) {
            "https" -> 443
            "http" -> 80
            else -> -1
        }
        return if (url.port == defaultPort) url.host else "${url.host}:${url.port}"
    }

    internal data class SignedRequest(
        val headers: Map<String, String>,
        val canonicalRequest: String,
        val stringToSign: String,
        val signature: String,
    )

    private companion object {
        val AMZ_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
        val DATE_STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
    }
}
