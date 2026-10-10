package com.slideindex.app.cloudstorage

import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 上传 / 列表 / 删除必须落在**同一个远端目录与同一套 object key** 上。
 *
 * 逐个方法单测都绿，也可能出这种错位：写的时候落到 `backup/`，列的时候查了别的前缀
 * （或 baseDir 拼错、目录标记少一个斜杠）。用户看到的现象是"备份提示成功了，
 * 远端列表里却永远没有这份备份"——只有跨方法跑一遍才会暴露。
 *
 * 这里用公开 API（client 工厂 + catalog）串起真实 HTTP 请求，MockWebServer 负责回放响应，
 * 断言的是请求路径本身，而不是内部实现细节。
 */
class CloudBackupRoundTripTest {

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

    @Test
    fun uploadAndListUseTheSameRemoteDirectory(): Unit = runBlocking {
        val catalog = catalog()
        val file = File(Files.createTempDirectory("cloud-roundtrip").toFile(), "pending.zip")
            .apply { writeText("zip-bytes") }

        server.enqueue(MockResponse().setResponseCode(200)) // 建 backup 目录（空对象标记）
        server.enqueue(MockResponse().setResponseCode(200)) // 上传备份文件
        val entry = catalog.uploadBackup(
            localFile = file,
            appVersionName = "1.36.0",
            now = Instant.parse("2026-02-13T07:30:12Z"),
        )

        val markerRequest = server.takeRequest()
        val uploadRequest = server.takeRequest()
        assertEquals("PUT", markerRequest.method)
        assertEquals("/mybucket/cebian/backup/", markerRequest.path)
        assertEquals("PUT", uploadRequest.method)
        assertEquals("/mybucket/cebian/backup/${entry.name}", uploadRequest.path)

        server.enqueue(MockResponse().setResponseCode(200).setBody(listResponse(listOf(entry.name))))
        val listed = catalog.listBackups()

        val listRequest = server.takeRequest()
        assertTrue(
            "列表前缀必须和上传目录一致，实际 path=${listRequest.path}",
            listRequest.path!!.contains("prefix=cebian%2Fbackup%2F"),
        )
        assertEquals(listOf(entry.name), listed.map { it.name })
        assertEquals("1.36.0", listed.single().appVersionName)
        assertEquals(file.length(), listed.single().sizeBytes)

        file.parentFile?.deleteRecursively()
    }

    @Test
    fun pruneDeletesExactlyTheKeysThatListReturned(): Unit = runBlocking {
        val catalog = catalog()
        server.enqueue(MockResponse().setResponseCode(200).setBody(listResponse(listOf(NEWER, OLDER))))
        server.enqueue(MockResponse().setResponseCode(204)) // 删除被淘汰的那份

        val deleted = catalog.prune(retainCount = 1)

        val listRequest = server.takeRequest()
        assertTrue(listRequest.path!!.contains("prefix=cebian%2Fbackup%2F"))
        val deleteRequest = server.takeRequest()
        assertEquals("DELETE", deleteRequest.method)
        assertEquals("/mybucket/cebian/backup/$OLDER", deleteRequest.path)
        assertEquals(listOf(OLDER), deleted)
    }

    @Test
    fun deleteBackupTargetsTheKeyThatListReturned(): Unit = runBlocking {
        val catalog = catalog()
        server.enqueue(MockResponse().setResponseCode(200).setBody(listResponse(listOf(NEWER))))
        val entry = catalog.listBackups().single()

        server.enqueue(MockResponse().setResponseCode(204))
        catalog.deleteBackup(entry.name)

        val listRequest = server.takeRequest()
        assertEquals("GET", listRequest.method)
        assertTrue(listRequest.path!!.contains("prefix=cebian%2Fbackup%2F"))
        val deleteRequest = server.takeRequest()
        assertEquals("DELETE", deleteRequest.method)
        assertEquals("/mybucket/cebian/backup/$NEWER", deleteRequest.path)
    }

    /** baseDir 非空也必须一致：错位最常见的原因就是一侧漏拼/多拼了 baseDir。 */
    @Test
    fun customBaseDirIsAppliedToBothUploadAndList(): Unit = runBlocking {
        val catalog = catalog(baseDir = "apps/xgesture")
        val file = File(Files.createTempDirectory("cloud-basedir").toFile(), "pending.zip")
            .apply { writeText("zip") }

        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        catalog.uploadBackup(file, appVersionName = null, now = Instant.parse("2026-02-13T07:30:12Z"))

        assertEquals("/mybucket/apps/xgesture/backup/", server.takeRequest().path)
        assertEquals("PUT", server.takeRequest().method)

        server.enqueue(MockResponse().setResponseCode(200).setBody(listResponse(emptyList())))
        catalog.listBackups()
        assertTrue(server.takeRequest().path!!.contains("prefix=apps%2Fxgesture%2Fbackup%2F"))

        file.parentFile?.deleteRecursively()
    }

    private fun catalog(baseDir: String = "/cebian"): CloudBackupCatalog {
        val config = S3StorageConfig(
            id = "cfg",
            displayName = "test",
            baseDir = baseDir,
            endpoint = server.url("/").toString(),
            accessKey = "AKIAIOSFODNN7EXAMPLE",
            secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            bucket = "mybucket",
            region = "us-east-1",
        )
        return CloudBackupCatalog(CloudStorageClientFactory.create(config))
    }

    private fun listResponse(names: List<String>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><ListBucketResult>")
        append("<IsTruncated>false</IsTruncated>")
        names.forEach { name ->
            append("<Contents>")
            append("<Key>cebian/backup/").append(name).append("</Key>")
            append("<Size>9</Size>")
            append("<LastModified>2026-02-13T10:10:10.000Z</LastModified>")
            append("</Contents>")
        }
        append("</ListBucketResult>")
    }

    private companion object {
        const val NEWER = "cebian-backup-20260213-153012.zip"
        const val OLDER = "cebian-backup-20260212-101010.zip"
    }
}
