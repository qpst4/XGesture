package com.slideindex.app.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardEntryTest {

    @Test
    fun `matchesQuery 命中正文`() {
        val entry = entry(text = "OpenAI 发布会")
        assertTrue(entry.matchesQuery("openai"))
        assertFalse(entry.matchesQuery("google"))
    }

    @Test
    fun `matchesQuery 命中链接与 intent`() {
        val entry = entry(
            text = "链接",
            uri = "https://example.com/a",
            intentUri = "xgesture://open?x=1",
        )
        assertTrue(entry.matchesQuery("example.com"))
        assertTrue(entry.matchesQuery("xgesture://"))
        assertFalse(entry.matchesQuery("missing"))
    }

    @Test
    fun `contentKey 对相同内容稳定`() {
        val a = entry(text = "同样的内容")
        val b = entry(text = "同样的内容")
        assertEquals(a.contentKey(), b.contentKey())
    }

    private fun entry(
        text: String,
        uri: String? = null,
        intentUri: String? = null,
    ) = ClipboardEntry(
        id = "id-$text",
        text = text,
        uri = uri,
        intentUri = intentUri,
        createdAtEpochMs = 1_700_000_000_000,
    )
}
