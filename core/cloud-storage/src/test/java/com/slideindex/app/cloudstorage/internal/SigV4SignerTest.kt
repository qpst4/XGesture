package com.slideindex.app.cloudstorage.internal

import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SigV4 用 **AWS 官方文档给出的期望签名**校验，而不是自算自证——
 * 签名算法写错时"自己和自己一致"是最典型的假通过。
 *
 * 注意：这里用的是 S3 的文档向量，不是通用的 AWS SigV4 测试套件（`aws-sig-v4-test-suite`）。
 * 套件里的用例（如 get-vanilla）只签 `host;x-amz-date`，而 S3 强制要求
 * `x-amz-content-sha256` 参与签名，两者规范请求必然不同，套件向量对 S3 签名器不适用。
 */
class SigV4SignerTest {

    /**
     * AWS S3 文档《Example: PUT Object》的向量。
     *
     * 覆盖面最广：非空 body 哈希、额外的 `date` 头参与签名、路径里的 `%24`（验证不重复编码）。
     */
    @Test
    fun sign_matchesAwsDocumentedPutObjectVector() {
        val signer = SigV4Signer(
            accessKey = "AKIAIOSFODNN7EXAMPLE",
            secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "us-east-1",
        )
        val payloadSha256 = "44ce7dd67c959e0d3524ffac1771dfbba87d2b6b4b4e99e42034a8b803f8b072"

        val signed = signer.sign(
            method = "PUT",
            url = "https://examplebucket.s3.amazonaws.com/test%24file.text".toHttpUrl(),
            headers = mapOf(
                "Date" to "Fri, 24 May 2013 00:00:00 GMT",
                "x-amz-storage-class" to "REDUCED_REDUNDANCY",
            ),
            payloadSha256 = payloadSha256,
            instant = Instant.parse("2013-05-24T00:00:00Z"),
        )

        assertEquals(EXPECTED_PUT_CANONICAL_REQUEST, signed.canonicalRequest)
        // 规范请求自身的哈希也钉住：只测最终签名的话，规范化的错误可能被后续 HMAC 链"吃掉"
        assertEquals(
            "9e0e90d9c76de8fa5b200d8c849cd5b8dc7a3be3951ddb7f6a76b4158342019d",
            sha256Hex(signed.canonicalRequest.toByteArray(Charsets.UTF_8)),
        )
        assertEquals(
            "AWS4-HMAC-SHA256\n" +
                "20130524T000000Z\n" +
                "20130524/us-east-1/s3/aws4_request\n" +
                "9e0e90d9c76de8fa5b200d8c849cd5b8dc7a3be3951ddb7f6a76b4158342019d",
            signed.stringToSign,
        )
        assertEquals("98ad721746da40c64f1a55b78f14c238d841ea1380cd77a1b5971af0ece108bd", signed.signature)
    }

    /** AWS S3 文档《Example: GET Object》的向量（该示例不含 x-amz-storage-class 头）。 */
    @Test
    fun sign_matchesAwsDocumentedGetObjectVector() {
        val signer = SigV4Signer(
            accessKey = "AKIAIOSFODNN7EXAMPLE",
            secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "us-east-1",
        )

        val signed = signer.sign(
            method = "GET",
            url = "https://examplebucket.s3.amazonaws.com/test.txt".toHttpUrl(),
            headers = mapOf("Range" to "bytes=0-9"),
            payloadSha256 = EMPTY_PAYLOAD_SHA256,
            instant = Instant.parse("2013-05-24T00:00:00Z"),
        )

        assertEquals("f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41", signed.signature)
        assertEquals("examplebucket.s3.amazonaws.com", signed.headers["host"])
        assertEquals("20130524T000000Z", signed.headers["x-amz-date"])
        assertEquals(EMPTY_PAYLOAD_SHA256, signed.headers["x-amz-content-sha256"])
        assertEquals(
            "host;range;x-amz-content-sha256;x-amz-date",
            signed.headers["authorization"]!!.substringAfter("SignedHeaders=").substringBefore(","),
        )
        assertEquals(
            "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request",
            signed.headers["authorization"]!!.substringBefore(", SignedHeaders"),
        )
    }

    @Test
    fun sign_alwaysSignsContentSha256Header() {
        // S3 强制要求该头参与签名；这也是通用 SigV4 测试套件向量不适用的原因
        val signed = SigV4Signer("ak", "sk", "eu-west-1").sign(
            method = "GET",
            url = "https://bucket.s3.eu-west-1.amazonaws.com/key".toHttpUrl(),
            headers = emptyMap(),
            payloadSha256 = EMPTY_PAYLOAD_SHA256,
            instant = Instant.parse("2026-02-13T10:00:00Z"),
        )

        assertTrue(
            signed.headers["authorization"]!!.contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date"),
        )
        assertEquals("20260213T100000Z", signed.headers["x-amz-date"])
        assertEquals("20260213/eu-west-1/s3/aws4_request", signed.stringToSign.lines()[2])
    }

    @Test
    fun sign_omitsPortForDefaultScheme() {
        val signed = SigV4Signer("ak", "sk", "us-east-1").sign(
            method = "GET",
            url = "http://127.0.0.1/key".toHttpUrl(),
            headers = emptyMap(),
            payloadSha256 = EMPTY_PAYLOAD_SHA256,
            instant = Instant.parse("2026-02-13T10:00:00Z"),
        )

        // http 默认端口是 80，Host 头不能带端口，否则签名与请求不一致
        assertEquals("127.0.0.1", signed.headers["host"])
    }

    @Test
    fun uriEncode_followsRfc3986Rules() {
        assertEquals("a%20b", uriEncode("a b", encodeSlash = false))
        assertEquals("a%2Fb", uriEncode("a/b", encodeSlash = true))
        assertEquals("a/b", uriEncode("a/b", encodeSlash = false))
        assertEquals("%2A", uriEncode("*", encodeSlash = false))
        assertEquals("%2B", uriEncode("+", encodeSlash = false))
        assertEquals("~-._", uriEncode("~-._", encodeSlash = false))
        assertEquals("%E4%B8%AD", uriEncode("中", encodeSlash = false))
    }

    @Test
    fun sha256Hex_matchesDocumentedDigests() {
        assertEquals(EMPTY_PAYLOAD_SHA256, sha256Hex(ByteArray(0)))
        // AWS S3 文档 PUT Object 示例里 payload 的哈希值
        val payload = "Welcome to Amazon S3."
        assertEquals(
            "44ce7dd67c959e0d3524ffac1771dfbba87d2b6b4b4e99e42034a8b803f8b072",
            sha256Hex(payload.toByteArray()),
        )
        assertEquals(
            "44ce7dd67c959e0d3524ffac1771dfbba87d2b6b4b4e99e42034a8b803f8b072",
            sha256Hex(payload.byteInputStream()),
        )
    }

    private companion object {
        val EXPECTED_PUT_CANONICAL_REQUEST = """
            PUT
            /test%24file.text

            date:Fri, 24 May 2013 00:00:00 GMT
            host:examplebucket.s3.amazonaws.com
            x-amz-content-sha256:44ce7dd67c959e0d3524ffac1771dfbba87d2b6b4b4e99e42034a8b803f8b072
            x-amz-date:20130524T000000Z
            x-amz-storage-class:REDUCED_REDUNDANCY

            date;host;x-amz-content-sha256;x-amz-date;x-amz-storage-class
            44ce7dd67c959e0d3524ffac1771dfbba87d2b6b4b4e99e42034a8b803f8b072
        """.trimIndent()
    }
}
