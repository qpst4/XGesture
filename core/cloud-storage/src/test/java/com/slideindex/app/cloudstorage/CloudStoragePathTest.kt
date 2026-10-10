package com.slideindex.app.cloudstorage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudStoragePathTest {

    @Test
    fun normalize_collapsesSeparatorsAndDropsEdgeSlashes() {
        assertEquals("a/b", CloudStoragePath.normalize("/a/b/"))
        assertEquals("a/b", CloudStoragePath.normalize("a//b"))
        assertEquals("a/b", CloudStoragePath.normalize("\\a\\b\\"))
        assertEquals("a/b", CloudStoragePath.normalize("a/./b"))
        assertEquals("", CloudStoragePath.normalize("/"))
        assertEquals("", CloudStoragePath.normalize(""))
    }

    @Test
    fun normalizeBaseDir_appendsTrailingSlashOnlyWhenNeeded() {
        assertEquals("cebian/", CloudStoragePath.normalizeBaseDir("/cebian"))
        assertEquals("cebian/", CloudStoragePath.normalizeBaseDir("cebian/"))
        assertEquals("", CloudStoragePath.normalizeBaseDir("/"))
    }

    @Test
    fun join_neverProducesDoubleSlash() {
        assertEquals("cebian/backup", CloudStoragePath.join("/cebian/", "/backup/"))
        assertEquals("backup", CloudStoragePath.join("", "backup"))
        assertEquals("cebian", CloudStoragePath.join("/cebian", ""))
        assertEquals("backup", CloudStoragePath.join("/", "backup"))
    }

    @Test
    fun fileNameAndParent_areStable() {
        assertEquals("a.zip", CloudStoragePath.fileName("backup/dir/a.zip"))
        assertEquals("a.zip", CloudStoragePath.fileName("/a.zip"))
        assertEquals("backup/dir", CloudStoragePath.parent("backup/dir/a.zip"))
        assertEquals("", CloudStoragePath.parent("a.zip"))
    }

    @Test
    fun isUnderBaseDir_checksSegmentBoundary() {
        assertTrue(CloudStoragePath.isUnderBaseDir("cebian", "cebian/backup/a.zip"))
        assertTrue(CloudStoragePath.isUnderBaseDir("cebian", "cebian"))
        assertTrue(CloudStoragePath.isUnderBaseDir("", "anything"))
        // 前缀相同但不是同一层级：不能误判为在 baseDir 内
        assertFalse(CloudStoragePath.isUnderBaseDir("cebian", "cebian-other/a.zip"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun requireSafe_rejectsTraversal() {
        CloudStoragePath.requireSafe("backup/../../etc/passwd")
    }
}
