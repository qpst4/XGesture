package com.slideindex.app.clipboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardEntryUriSemanticsTest {

    @Test
    fun localContentUriIsFileEntry() {
        val entry = ClipboardEntry(
            id = "file-1",
            type = ClipboardEntryType.URI,
            text = "content://com.example.provider/document/1",
            uri = "content://com.example.provider/document/1",
            mimeType = "application/pdf",
            createdAtEpochMs = 1L,
        )

        assertTrue(entry.isLocalFileUriEntry())
    }

    @Test
    fun remoteUriIsNotFileEntry() {
        val entry = ClipboardEntry(
            id = "link-1",
            type = ClipboardEntryType.URI,
            text = "https://example.com/article",
            uri = "https://example.com/article",
            mimeType = "text/uri-list",
            createdAtEpochMs = 1L,
        )

        assertFalse(entry.isLocalFileUriEntry())
    }

    @Test
    fun textEntryIsNotFileEntry() {
        val entry = ClipboardEntry(
            id = "text-1",
            type = ClipboardEntryType.TEXT,
            text = "hello",
            createdAtEpochMs = 1L,
        )

        assertFalse(entry.isLocalFileUriEntry())
    }
}
