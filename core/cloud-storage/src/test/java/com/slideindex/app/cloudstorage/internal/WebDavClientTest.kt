package com.slideindex.app.cloudstorage.internal

import com.slideindex.app.cloudstorage.CloudStorageException
import com.slideindex.app.cloudstorage.WebDavStorageConfig
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebDavClientTest {

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

    /** 服务端 URL 采用 Nextcloud 形态（自带 /remote.php/... 路径前缀）。 */
    private fun client(baseDir: String = "cebian") = WebDavClient(
        config = WebDavStorageConfig(
            id = "cfg",
            displayName = "nextcloud",
            baseDir = baseDir,
            server = server.url("/remote.php/dav/files/alice/").toString(),
            username = "alice",
            password = "secret",
        ),
        http = OkHttpClient(),
    )

    private val expectedAuth: String =
        "Basic " + Base64.getEncoder().encodeToString("alice:secret".toByteArray())

    @Test
    fun list_parsesMultistatusAndStripsNextcloudPathPrefix() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody(MULTISTATUS))

        val entries = client().list("backup")

        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("1", request.getHeader("Depth"))
        assertEquals(expectedAuth, request.getHeader("Authorization"))
        assertEquals("/remote.php/dav/files/alice/cebian/backup/", request.path)

        // 请求的集合自身必须被丢弃，否则列表里会多一个"backup"目录
        assertEquals(2, entries.size)
        val directory = entries.first { it.isDirectory }
        assertEquals("backup/old", directory.path)
        val file = entries.first { !it.isDirectory }
        assertEquals("backup/cebian-backup-20260213-101010.zip", file.path)
        assertEquals(4096L, file.size)
        assertEquals(Instant.parse("2026-02-13T10:10:10Z").toEpochMilli(), file.lastModifiedEpochMs)
    }

    @Test
    fun list_percentDecodesHrefs() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody(MULTISTATUS_WITH_ENCODED_HREF))

        val entries = client().list("backup")

        assertEquals("backup/my backup.zip", entries.single().path)
        assertEquals("my backup.zip", entries.single().name)
    }

    @Test
    fun list_returnsEmptyWhenDirectoryDoesNotExistYet() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        assertEquals(emptyList<Any>(), client().list("backup"))
    }

    @Test
    fun ensureDirectory_treatsMethodNotAllowedAsAlreadyExisting() = runBlocking {
        // MKCOL 在目录已存在时返回 405，这不算失败
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(MockResponse().setResponseCode(207).setBody(existsResponse("cebian/backup/")))

        client().ensureDirectory("backup")

        val mkcol = server.takeRequest()
        assertEquals("MKCOL", mkcol.method)
        assertEquals("/remote.php/dav/files/alice/cebian/backup/", mkcol.path)
        val check = server.takeRequest()
        assertEquals("PROPFIND", check.method)
        assertEquals("0", check.getHeader("Depth"))
    }

    @Test
    fun ensureDirectory_cascadesThroughBaseDirItself() = runBlocking {
        // baseDir（cebian）本身还没创建时：MKCOL backup 会 409，
        // 级联必须继续补 baseDir 自己，否则首次备份永远建不出目录。
        server.enqueue(MockResponse().setResponseCode(409)) // 父目录缺失
        server.enqueue(MockResponse().setResponseCode(404)) // /cebian/backup 不存在
        server.enqueue(MockResponse().setResponseCode(201)) // 建 /cebian
        server.enqueue(MockResponse().setResponseCode(201)) // 重试建 /cebian/backup

        client().ensureDirectory("backup")

        val paths = generateSequence { server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS) }
            .map { "${it.method} ${it.path}" }
            .toList()
        assertEquals(
            listOf(
                "MKCOL /remote.php/dav/files/alice/cebian/backup/",
                "PROPFIND /remote.php/dav/files/alice/cebian/backup/",
                "MKCOL /remote.php/dav/files/alice/cebian/",
                "MKCOL /remote.php/dav/files/alice/cebian/backup/",
            ),
            paths,
        )
    }

    @Test
    fun upload_putsFileWithBasicAuth(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        val payload = "zip-bytes"
        val file = File(Files.createTempDirectory("dav-upload").toFile(), "b.zip").apply { writeText(payload) }
        var lastTransferred = 0L

        client().upload("backup/b.zip", file) { transferred, _ -> lastTransferred = transferred }

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/remote.php/dav/files/alice/cebian/backup/b.zip", request.path)
        assertEquals(expectedAuth, request.getHeader("Authorization"))
        assertEquals(payload, request.body.readUtf8())
        assertEquals(file.length(), lastTransferred)
        file.parentFile?.deleteRecursively()
    }

    @Test
    fun download_writesBodyToDisk(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))
        val target = File(Files.createTempDirectory("dav-download").toFile(), "out.zip")

        client().download("backup/out.zip", target)

        assertEquals("hello", target.readText())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/remote.php/dav/files/alice/cebian/backup/out.zip", request.path)
        target.parentFile?.deleteRecursively()
    }

    @Test
    fun testConnection_usesServerRootSoMissingBaseDirIsNotAFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody(existsResponse("")))

        client().testConnection()

        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("0", request.getHeader("Depth"))
        // 配置里 baseDir=cebian，但连通性测试打的是服务器根
        assertEquals("/remote.php/dav/files/alice", request.path)
    }

    @Test
    fun failures_surfaceHttpStatus() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val error = runCatching { client().testConnection() }.exceptionOrNull()

        assertNotNull(error)
        assertTrue("实际类型：${error!!::class}", error is CloudStorageException)
        assertEquals(401, (error as CloudStorageException).httpStatus)
        assertTrue(error.isConfigurationProblem)
    }

    @Test
    fun urlFor_buildsLogicalPathUnderBaseDir() {
        assertEquals(
            "http://${server.hostName}:${server.port}/remote.php/dav/files/alice/cebian/backup/",
            client().urlFor("backup", isDirectory = true).toString(),
        )
        assertEquals(
            "http://${server.hostName}:${server.port}/remote.php/dav/files/alice/cebian/backup/a.zip",
            client().urlFor("backup/a.zip", isDirectory = false).toString(),
        )
    }

    private fun existsResponse(href: String): String = """
        <?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:">
          <d:response>
            <d:href>/remote.php/dav/files/alice/$href</d:href>
            <d:propstat>
              <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
        </d:multistatus>
    """.trimIndent()

    private companion object {
        val MULTISTATUS = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/alice/cebian/backup/</d:href>
                <d:propstat>
                  <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/alice/cebian/backup/cebian-backup-20260213-101010.zip</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype/>
                    <d:getcontentlength>4096</d:getcontentlength>
                    <d:getlastmodified>Fri, 13 Feb 2026 10:10:10 GMT</d:getlastmodified>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
                <d:propstat>
                  <d:prop><d:getcontenttype/></d:prop>
                  <d:status>HTTP/1.1 404 Not Found</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/alice/cebian/backup/old/</d:href>
                <d:propstat>
                  <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val MULTISTATUS_WITH_ENCODED_HREF = """
            <?xml version="1.0" encoding="utf-8"?>
            <lp1:multistatus xmlns:lp1="DAV:">
              <lp1:response>
                <lp1:href>/remote.php/dav/files/alice/cebian/backup/my%20backup.zip</lp1:href>
                <lp1:propstat>
                  <lp1:prop><lp1:resourcetype/><lp1:getcontentlength>12</lp1:getcontentlength></lp1:prop>
                  <lp1:status>HTTP/1.1 200 OK</lp1:status>
                </lp1:propstat>
              </lp1:response>
            </lp1:multistatus>
        """.trimIndent()
    }
}
