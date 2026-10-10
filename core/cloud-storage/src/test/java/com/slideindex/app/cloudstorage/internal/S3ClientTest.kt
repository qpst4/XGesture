package com.slideindex.app.cloudstorage.internal

import com.slideindex.app.cloudstorage.CloudStorageException
import com.slideindex.app.cloudstorage.S3StorageConfig
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class S3ClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /**
     * endpoint 用 127.0.0.1 → 会命中 [com.slideindex.app.cloudstorage.ObjectStorageEndpoint.forcePathStyle]，
     * 即 path-style（MinIO 自建场景的典型形态）。
     */
    private fun client(
        baseDir: String = "cebian",
        bucket: String = "mybucket",
        pathStyle: Boolean = false,
        secretKey: String = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
        region: String? = "us-east-1",
    ) = S3Client(
        config = S3StorageConfig(
            id = "cfg",
            displayName = "test",
            baseDir = baseDir,
            endpoint = server.url("/").toString(),
            accessKey = "AKIAIOSFODNN7EXAMPLE",
            secretKey = secretKey,
            bucket = bucket,
            region = region,
            pathStyle = pathStyle,
        ),
        http = OkHttpClient(),
    )

    @Test
    fun list_parsesFilesAndCommonPrefixesAndSkipsDirectoryMarkers() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(LIST_RESPONSE))

        val entries = client().list("backup")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue("path=${request.path}", request.path!!.startsWith("/mybucket?"))
        assertTrue("path=${request.path}", request.path!!.contains("list-type=2"))
        assertTrue("path=${request.path}", request.path!!.contains("prefix=cebian%2Fbackup%2F"))
        assertTrue("path=${request.path}", request.path!!.contains("delimiter=%2F"))
        assertTrue(
            "必须带 SigV4 授权头",
            request.getHeader("Authorization")!!.startsWith("AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/"),
        )
        assertEquals(EMPTY_PAYLOAD_SHA256, request.getHeader("x-amz-content-sha256"))

        assertEquals(2, entries.size)
        val directory = entries.first { it.isDirectory }
        assertEquals("backup/sub", directory.path)
        assertEquals("sub", directory.name)
        val file = entries.first { !it.isDirectory }
        assertEquals("backup/cebian-backup-20260213-101010.zip", file.path)
        assertEquals("cebian-backup-20260213-101010.zip", file.name)
        assertEquals(1234L, file.size)
        assertEquals(Instant.parse("2026-02-13T10:10:10Z").toEpochMilli(), file.lastModifiedEpochMs)
    }

    @Test
    fun list_followsContinuationTokenUntilNotTruncated() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(LIST_PAGE_1))
        server.enqueue(MockResponse().setResponseCode(200).setBody(LIST_PAGE_2))

        val entries = client().list("backup")

        val first = server.takeRequest()
        val second = server.takeRequest()
        assertFalse("首页不该带 continuation-token", first.path!!.contains("continuation-token"))
        assertTrue("下一页必须带 continuation-token", second.path!!.contains("continuation-token=t1"))
        assertEquals(2, entries.size)
        assertEquals(
            listOf(
                "backup/cebian-backup-20260212-101010.zip",
                "backup/cebian-backup-20260213-101010.zip",
            ),
            entries.map { it.path },
        )
    }

    @Test
    fun ensureDirectory_putsEmptyMarkerWithTrailingSlash() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        client().ensureDirectory("backup")

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/mybucket/cebian/backup/", request.path)
        assertEquals("0", request.getHeader("Content-Length"))
        assertEquals(EMPTY_PAYLOAD_SHA256, request.getHeader("x-amz-content-sha256"))
    }

    // 显式 `: Unit`：末句是 deleteRecursively()（返回 Boolean），
    // 不声明的话 JUnit4 会以 "Method ... should be void" 拒绝整个测试类。
    @Test
    fun upload_streamsFileAndSignsWithRealContentHash(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        val payload = "Welcome to Amazon S3."
        val file = File(Files.createTempDirectory("s3-upload").toFile(), "backup.zip").apply { writeText(payload) }
        var lastTransferred = 0L
        var reportedTotal = 0L

        client().upload("backup/backup.zip", file) { transferred, total ->
            lastTransferred = transferred
            reportedTotal = total
        }

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/mybucket/cebian/backup/backup.zip", request.path)
        assertEquals(
            "44ce7dd67c959e0d3524ffac1771dfbba87d2b6b4b4e99e42034a8b803f8b072",
            request.getHeader("x-amz-content-sha256"),
        )
        assertEquals(payload, request.body.readUtf8())
        assertEquals(file.length(), lastTransferred)
        assertEquals(file.length(), reportedTotal)
        file.parentFile?.deleteRecursively()
    }

    @Test
    fun download_writesBodyAndReportsProgress(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("hello world"))
        val target = File(Files.createTempDirectory("s3-download").toFile(), "nested/out.zip")
        var lastTransferred = 0L

        client().download("backup/x.zip", target) { transferred, _ -> lastTransferred = transferred }

        assertEquals("hello world", target.readText())
        assertEquals(11L, lastTransferred)
        assertEquals("GET", server.takeRequest().method)
        target.parentFile?.deleteRecursively()
    }

    @Test
    fun delete_isIdempotentOnNotFound() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        client().delete("backup/gone.zip")

        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun failures_surfaceHttpStatusAndServerCode() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody(
                """<?xml version="1.0" encoding="UTF-8"?>
                   <Error><Code>SignatureDoesNotMatch</Code>
                   <Message>The request signature we calculated does not match.</Message></Error>""".trimIndent(),
            ),
        )

        val error = runCatching { client().list("backup") }.exceptionOrNull()

        assertNotNull(error)
        assertTrue("实际类型：${error!!::class}", error is CloudStorageException)
        error as CloudStorageException
        assertEquals(403, error.httpStatus)
        assertEquals("SignatureDoesNotMatch", error.serverCode)
        assertTrue(error.message!!.contains("SignatureDoesNotMatch"))
        assertTrue(error.isConfigurationProblem)
    }

    @Test
    fun testConnection_validatesBucketWithCheapListCall() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<ListBucketResult/>"))

        client().testConnection()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue("path=${request.path}", request.path!!.contains("max-keys=1"))
    }

    // region URL 构造（不依赖网络）

    @Test
    fun urlFor_pathStylePutsBucketIntoPath() {
        val client = S3Client(
            config = S3StorageConfig(
                id = "cfg",
                displayName = "test",
                baseDir = "/cebian",
                endpoint = "https://s3.us-east-1.amazonaws.com",
                accessKey = "ak",
                secretKey = "sk",
                bucket = "mybucket",
                pathStyle = true,
            ),
            http = OkHttpClient(),
        )

        assertEquals(
            "https://s3.us-east-1.amazonaws.com/mybucket/cebian/backup/a%20b.zip",
            client.urlFor("backup/a b.zip").toString(),
        )
    }

    @Test
    fun urlFor_virtualHostedStylePutsBucketIntoHost() {
        val client = S3Client(
            config = S3StorageConfig(
                id = "cfg",
                displayName = "test",
                baseDir = "/cebian",
                endpoint = "https://s3.us-east-1.amazonaws.com",
                accessKey = "ak",
                secretKey = "sk",
                bucket = "mybucket",
                pathStyle = false,
            ),
            http = OkHttpClient(),
        )

        assertEquals(
            "https://mybucket.s3.us-east-1.amazonaws.com/cebian/backup/a.zip",
            client.urlFor("backup/a.zip").toString(),
        )
    }

    /** 列举请求的 query 必须"参数名排序 + %XX 编码"，否则与签名里的规范查询串不一致。 */
    @Test
    fun list_sortsAndEncodesQueryParameters() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("<ListBucketResult><IsTruncated>false</IsTruncated></ListBucketResult>"),
        )

        client(baseDir = "/cebian").list("backup")

        assertEquals(
            "/mybucket?delimiter=%2F&list-type=2&max-keys=1000&prefix=cebian%2Fbackup%2F",
            server.takeRequest().path,
        )
    }

    @Test
    fun urlFor_aliyunNativeEndpointIsRewrittenToS3CompatibleHost() {
        val client = S3Client(
            config = S3StorageConfig(
                id = "cfg",
                displayName = "oss",
                baseDir = "cebian",
                endpoint = "https://oss-cn-hangzhou.aliyuncs.com",
                accessKey = "ak",
                secretKey = "sk",
                bucket = "mybucket",
                region = null,
                pathStyle = false,
            ),
            http = OkHttpClient(),
        )

        assertEquals(
            "https://mybucket.s3.oss-cn-hangzhou.aliyuncs.com/cebian/backup/a.zip",
            client.urlFor("backup/a.zip").toString(),
        )
    }

    // endregion

    private companion object {
        val LIST_RESPONSE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
              <Name>mybucket</Name>
              <Prefix>cebian/backup/</Prefix>
              <IsTruncated>false</IsTruncated>
              <Contents>
                <Key>cebian/backup/cebian-backup-20260213-101010.zip</Key>
                <LastModified>2026-02-13T10:10:10.000Z</LastModified>
                <Size>1234</Size>
              </Contents>
              <Contents>
                <Key>cebian/backup/sub/</Key>
                <LastModified>2026-02-13T10:10:10.000Z</LastModified>
                <Size>0</Size>
              </Contents>
              <CommonPrefixes><Prefix>cebian/backup/sub/</Prefix></CommonPrefixes>
            </ListBucketResult>
        """.trimIndent()

        val LIST_PAGE_1 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ListBucketResult>
              <IsTruncated>true</IsTruncated>
              <NextContinuationToken>t1</NextContinuationToken>
              <Contents>
                <Key>cebian/backup/cebian-backup-20260213-101010.zip</Key>
                <Size>10</Size>
              </Contents>
            </ListBucketResult>
        """.trimIndent()

        val LIST_PAGE_2 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ListBucketResult>
              <IsTruncated>false</IsTruncated>
              <Contents>
                <Key>cebian/backup/cebian-backup-20260212-101010.zip</Key>
                <Size>20</Size>
              </Contents>
            </ListBucketResult>
        """.trimIndent()
    }
}
