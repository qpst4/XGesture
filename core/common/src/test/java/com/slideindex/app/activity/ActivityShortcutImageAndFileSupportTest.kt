package com.slideindex.app.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityShortcutImageAndFileSupportTest {

    @Test
    fun imageSupport_encodeAndDecode() {
        val config = ImageShortcutConfig(
            uri = "content://media/external/images/media/12345",
            sourceType = ImageSourceType.FILE,
            openInDialog = true,
            clickToDismiss = false,
            orientation = 1,
        )
        val encoded = ActivityShortcutImageSupport.encodeUri(config)
        assertTrue(ActivityShortcutImageSupport.isImageUri(encoded))

        val decoded = ActivityShortcutImageSupport.decodeConfig(encoded)
        assertNotNull(decoded)
        assertEquals(config.uri, decoded!!.uri)
        assertEquals(config.sourceType, decoded.sourceType)
        assertEquals(config.openInDialog, decoded.openInDialog)
        assertEquals(config.clickToDismiss, decoded.clickToDismiss)
        assertEquals(config.orientation, decoded.orientation)

        val shortcut = ActivityShortcutImageSupport.toShortcut("我的图片", config)
        assertTrue(ActivityShortcutImageSupport.isImageShortcut(shortcut))
        val fromShortcut = ActivityShortcutImageSupport.configFrom(shortcut)
        assertEquals(config, fromShortcut)
    }

    @Test
    fun imageSupport_urlDecode() {
        val config = ImageShortcutConfig(
            uri = "https://example.com/test.png?param=1&foo=bar",
            sourceType = ImageSourceType.URL,
            openInDialog = false,
            clickToDismiss = true,
            orientation = 2,
        )
        val encoded = ActivityShortcutImageSupport.encodeUri(config)
        val decoded = ActivityShortcutImageSupport.decodeConfig(encoded)
        assertNotNull(decoded)
        assertEquals(config.uri, decoded!!.uri)
        assertEquals(config.sourceType, decoded.sourceType)
        assertEquals(config.openInDialog, decoded.openInDialog)
        assertEquals(config.clickToDismiss, decoded.clickToDismiss)
        assertEquals(config.orientation, decoded.orientation)
    }

    @Test
    fun fileSupport_encodeAndDecode() {
        val config = FileShortcutConfig(
            fileUri = "content://com.android.providers.downloads.documents/document/123",
            mimeType = "application/pdf",
            isShareMode = true,
            isTextMode = false,
            targetPackage = "com.duokan.reader",
            targetActivity = "com.duokan.reader.DkReaderActivity",
        )
        val encoded = ActivityShortcutFileSupport.encodeUri(config)
        assertTrue(ActivityShortcutFileSupport.isFileUri(encoded))

        val decoded = ActivityShortcutFileSupport.decodeConfig(encoded)
        assertNotNull(decoded)
        assertEquals(config.fileUri, decoded!!.fileUri)
        assertEquals(config.mimeType, decoded.mimeType)
        assertEquals(config.isShareMode, decoded.isShareMode)
        assertEquals(config.isTextMode, decoded.isTextMode)
        assertEquals(config.targetPackage, decoded.targetPackage)
        assertEquals(config.targetActivity, decoded.targetActivity)

        val shortcut = ActivityShortcutFileSupport.toShortcut("电子书", config)
        assertTrue(ActivityShortcutFileSupport.isFileShortcut(shortcut))
        val fromShortcut = ActivityShortcutFileSupport.configFrom(shortcut)
        assertEquals(config, fromShortcut)
    }
}
