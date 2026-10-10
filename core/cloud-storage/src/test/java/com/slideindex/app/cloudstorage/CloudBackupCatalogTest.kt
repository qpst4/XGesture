package com.slideindex.app.cloudstorage

import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBackupCatalogTest {

    private val epoch = Instant.parse("2026-02-13T10:00:00Z").toEpochMilli()

    private fun entry(name: String, minutesAgo: Long, size: Long = 100L): CloudStorageEntry =
        CloudStorageEntry(
            path = "backup/$name",
            name = name,
            isDirectory = false,
            size = size,
            lastModifiedEpochMs = epoch - minutesAgo * 60_000,
        )

    private fun nameAt(secondsFromSample: Long, version: String = "1.36.0"): String =
        CloudBackupNaming.buildFileName(Instant.ofEpochMilli(SAMPLE_EPOCH).plusSeconds(secondsFromSample), version)

    @Test
    fun listBackups_filtersNonZipAndDirectories_andSortsNewestFirst() = runBlocking {
        val client = FakeClient()
        client.entries += listOf(
            entry(nameAt(0), minutesAgo = 100),
            entry(nameAt(60), minutesAgo = 90),
            entry(nameAt(120), minutesAgo = 80),
            CloudStorageEntry("backup/sub", "sub", isDirectory = true),
            CloudStorageEntry("backup/notes.txt", "notes.txt", isDirectory = false, size = 1, lastModifiedEpochMs = epoch),
        )

        val backups = CloudBackupCatalog(client).listBackups()

        assertEquals(listOf(nameAt(120), nameAt(60), nameAt(0)), backups.map { it.name })
        assertEquals("1.36.0", backups.first().appVersionName)
    }

    @Test
    fun listBackups_fallsBackToRemoteModifiedTimeForForeignNames() = runBlocking {
        val client = FakeClient()
        client.entries += CloudStorageEntry(
            path = "backup/manual-copy.zip",
            name = "manual-copy.zip",
            isDirectory = false,
            size = 10,
            lastModifiedEpochMs = epoch,
        )

        val backups = CloudBackupCatalog(client).listBackups()

        assertEquals(1, backups.size)
        assertEquals(null, backups.single().createdAtEpochMs)
        assertEquals(epoch, backups.single().sortKey)
    }

    @Test
    fun prune_deletesOldestBeyondRetention() = runBlocking {
        val client = FakeClient()
        client.entries += listOf(
            entry(nameAt(0), 100),
            entry(nameAt(60), 90),
            entry(nameAt(120), 80),
            entry(nameAt(180), 70),
            entry(nameAt(240), 60),
        )

        val deleted = CloudBackupCatalog(client).prune(retainCount = 2)

        // 最新的两份（nameAt(240)、nameAt(180)）保留，其余按新→旧顺序删除
        assertEquals(listOf(nameAt(0), nameAt(60), nameAt(120)), deleted.sorted())
        // 删的是远端路径，且保留最新两份
        assertEquals(
            setOf("backup/${nameAt(0)}", "backup/${nameAt(60)}", "backup/${nameAt(120)}"),
            client.deletedPaths.toSet(),
        )
        assertEquals(2, client.entries.size)
    }

    @Test
    fun prune_doesNothingWhenRetentionDisabledOrNotExceeded() = runBlocking {
        val client = FakeClient()
        client.entries += listOf(entry(nameAt(0), 10), entry(nameAt(60), 5))

        assertEquals(emptyList<String>(), CloudBackupCatalog(client).prune(retainCount = 0))
        assertEquals(emptyList<String>(), CloudBackupCatalog(client).prune(retainCount = -1))
        assertEquals(emptyList<String>(), CloudBackupCatalog(client).prune(retainCount = 2))
        assertTrue(client.deletedPaths.isEmpty())
    }

    @Test
    fun prune_toleratesPerFileDeleteFailures() = runBlocking {
        val client = FakeClient()
        client.entries += listOf(entry(nameAt(0), 30), entry(nameAt(60), 20), entry(nameAt(120), 10))
        client.failDeleteFor = nameAt(0)

        // 备份已经上传成功：顺手清理旧文件失败不能让整次操作失败
        val deleted = CloudBackupCatalog(client).prune(retainCount = 1)

        assertEquals(listOf(nameAt(60)), deleted)
    }

    // 显式声明 `: Unit`：JUnit4 要求测试方法返回 void，而块的最后一句
    // （file.delete()）会返回 Boolean，让编译器推断出非 void 返回值会被 JUnit 拒绝。
    @Test
    fun uploadBackup_writesIntoBackupDirectoryWithTimestampedName(): Unit = runBlocking {
        val client = FakeClient()
        val file = File.createTempFile("cebian-backup", ".zip").apply { writeText("zip") }

        val entry = CloudBackupCatalog(client).uploadBackup(
            localFile = file,
            appVersionName = "1.36.0",
            now = Instant.ofEpochMilli(SAMPLE_EPOCH),
        )

        assertEquals("cebian-backup-20260213-153012-1.36.0.zip", entry.name)
        assertEquals(listOf("backup/${entry.name}"), client.uploadedPaths)
        assertEquals("backup", client.createdDirectories.single())
        assertEquals(SAMPLE_EPOCH, entry.createdAtEpochMs)
        file.delete()
    }

    @Test
    fun downloadBackup_readsFromBackupDirectory() = runBlocking {
        val client = FakeClient()

        CloudBackupCatalog(client).downloadBackup(
            "cebian-backup-20260213-153012.zip",
            File.createTempFile("cebian", ".zip"),
        )

        assertEquals(listOf("backup/cebian-backup-20260213-153012.zip"), client.downloadedPaths)
    }

    private class FakeClient(
        override val config: CloudStorageConfig = WebDavStorageConfig(
            id = "fake",
            displayName = "fake",
            baseDir = "/cebian",
            server = "https://example.com/dav",
            username = "u",
            password = "p",
        ),
    ) : CloudStorageClient {
        val entries = mutableListOf<CloudStorageEntry>()
        val uploadedPaths = mutableListOf<String>()
        val downloadedPaths = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()
        val createdDirectories = mutableListOf<String>()
        var failDeleteFor: String? = null

        override suspend fun testConnection() = Unit

        override suspend fun list(path: String): List<CloudStorageEntry> = entries.toList()

        override suspend fun ensureDirectory(path: String) {
            createdDirectories += path
        }

        override suspend fun upload(remotePath: String, source: File, onProgress: (Long, Long) -> Unit) {
            uploadedPaths += remotePath
            onProgress(source.length(), source.length())
        }

        override suspend fun download(remotePath: String, target: File, onProgress: (Long, Long) -> Unit) {
            downloadedPaths += remotePath
            target.writeText("zip")
            onProgress(3, 3)
        }

        override suspend fun delete(remotePath: String) {
            val name = remotePath.substringAfterLast('/')
            if (name == failDeleteFor) throw CloudStorageException("boom")
            deletedPaths += remotePath
            entries.removeAll { it.name == name }
        }
    }

    private companion object {
        /** 2026-02-13 15:30:12 本地时间，与 CloudBackupNamingTest 的样例对齐。 */
        val SAMPLE_EPOCH: Long = java.time.ZonedDateTime.of(
            2026,
            2,
            13,
            15,
            30,
            12,
            0,
            java.time.ZoneId.systemDefault(),
        ).toInstant().toEpochMilli()
    }
}
