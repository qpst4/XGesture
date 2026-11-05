package com.slideindex.app.service

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ShareImageOcrCoordinatorTest {

    @Test
    fun resolveImageUri_send_returnsExtraStream() {
        val uri = Uri.parse("content://example/image.png")
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        assertEquals(uri, ShareImageOcrCoordinator.resolveImageUri(intent))
    }

    @Test
    fun resolveImageUri_sendMultiple_returnsFirstStream() {
        val first = Uri.parse("content://example/first.png")
        val second = Uri.parse("content://example/second.png")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
        }
        assertEquals(first, ShareImageOcrCoordinator.resolveImageUri(intent))
    }

    @Test
    fun resolveImageUri_view_returnsDataUri() {
        val uri = Uri.parse("content://example/view.png")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = uri
        }
        assertEquals(uri, ShareImageOcrCoordinator.resolveImageUri(intent))
    }

    @Test
    fun resolveImageUri_viewWithoutData_usesClipData() {
        val uri = Uri.parse("content://example/clip.png")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            clipData = ClipData.newRawUri("image", uri)
        }
        assertEquals(uri, ShareImageOcrCoordinator.resolveImageUri(intent))
    }

    @Test
    fun resolveImageUri_edit_returnsDataUri() {
        val uri = Uri.parse("content://example/edit.png")
        val intent = Intent(Intent.ACTION_EDIT).apply {
            data = uri
        }
        assertEquals(uri, ShareImageOcrCoordinator.resolveImageUri(intent))
    }

    @Test
    fun resolveImageUri_unknownAction_returnsNull() {
        val intent = Intent("com.example.UNKNOWN")
        assertNull(ShareImageOcrCoordinator.resolveImageUri(intent))
    }

    @Test
    fun isImageIntent_acceptsShareOpenAndEdit() {
        assertTrue(ShareImageOcrCoordinator.isImageIntent(Intent(Intent.ACTION_SEND)))
        assertTrue(ShareImageOcrCoordinator.isImageIntent(Intent(Intent.ACTION_SEND_MULTIPLE)))
        assertTrue(ShareImageOcrCoordinator.isImageIntent(Intent(Intent.ACTION_VIEW)))
        assertTrue(ShareImageOcrCoordinator.isImageIntent(Intent(Intent.ACTION_EDIT)))
        assertFalse(ShareImageOcrCoordinator.isImageIntent(Intent("com.example.UNKNOWN")))
        assertFalse(ShareImageOcrCoordinator.isImageIntent(null))
    }
}
