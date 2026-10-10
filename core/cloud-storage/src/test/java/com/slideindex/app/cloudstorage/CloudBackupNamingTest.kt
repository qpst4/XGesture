package com.slideindex.app.cloudstorage

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBackupNamingTest {

    private val sample = ZonedDateTime.of(2026, 2, 13, 15, 30, 12, 0, ZoneId.systemDefault()).toInstant()

    @Test
    fun buildFileName_isLexicographicallySortableByTime() {
        val name = CloudBackupNaming.buildFileName(sample, "1.36.0")
        assertEquals("cebian-backup-20260213-153012-1.36.0.zip", name)

        val earlier = CloudBackupNaming.buildFileName(sample.minusSeconds(3600), "1.36.0")
        assertTrue("较早的备份文件名必须排在前面", earlier < name)
    }

    @Test
    fun buildFileName_omitsMissingVersionAndSanitizesIt() {
        assertEquals("cebian-backup-20260213-153012.zip", CloudBackupNaming.buildFileName(sample, null))
        assertEquals("cebian-backup-20260213-153012.zip", CloudBackupNaming.buildFileName(sample, "  "))
        assertEquals(
            "cebian-backup-20260213-153012-1.36.0_beta.zip",
            CloudBackupNaming.buildFileName(sample, "1.36.0/beta"),
        )
    }

    @Test
    fun parseCreatedAt_roundTripsFilenameTimestamp() {
        assertEquals(
            sample.toEpochMilli(),
            CloudBackupNaming.parseCreatedAt("cebian-backup-20260213-153012.zip"),
        )
        assertEquals(
            sample.toEpochMilli(),
            CloudBackupNaming.parseCreatedAt("cebian-backup-20260213-153012-1.36.0.zip"),
        )
    }

    @Test
    fun parseCreatedAt_returnsNullForForeignNames() {
        assertNull(CloudBackupNaming.parseCreatedAt("my-manual-backup.zip"))
        assertNull(CloudBackupNaming.parseCreatedAt("cebian-backup-2026.zip"))
        assertNull(CloudBackupNaming.parseCreatedAt("cebian-backup-20260213-153012.zip.bak"))
    }

    @Test
    fun parseAppVersion_extractsOptionalSuffix() {
        assertEquals("1.36.0", CloudBackupNaming.parseAppVersion("cebian-backup-20260213-153012-1.36.0.zip"))
        assertNull(CloudBackupNaming.parseAppVersion("cebian-backup-20260213-153012.zip"))
    }

    @Test
    fun isBackupFile_acceptsOnlyZip() {
        assertTrue(CloudBackupNaming.isBackupFile("a.zip"))
        assertTrue(CloudBackupNaming.isBackupFile("a.ZIP"))
        assertFalse(CloudBackupNaming.isBackupFile("a.tar.gz"))
        assertFalse(CloudBackupNaming.isBackupFile("a.zip.tmp"))
    }

    @Test
    fun epochOfSample_isStableAcrossZones() {
        // 防回归：文件名用本地时区格式化/解析，跨时区运行必须自洽
        assertEquals(sample.toEpochMilli(), Instant.ofEpochMilli(CloudBackupNaming.parseCreatedAt(
            CloudBackupNaming.buildFileName(sample, null),
        )!!).toEpochMilli())
    }
}
