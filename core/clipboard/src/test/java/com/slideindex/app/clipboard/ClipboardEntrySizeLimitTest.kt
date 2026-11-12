package com.slideindex.app.clipboard

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardEntrySizeLimitTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun entry(
        text: String = "hello",
        htmlText: String? = null,
        contentBlocks: List<ClipboardContentBlock> = emptyList(),
    ) = ClipboardEntry(
        id = "id",
        text = text,
        htmlText = htmlText,
        contentBlocks = contentBlocks,
        createdAtEpochMs = 1L,
    )

    @Test
    fun textOverCharacterLimit_isRejected() {
        val tooLarge = entry(text = "x".repeat(ClipboardEntrySizeLimit.MAX_TEXT_CHARS + 1))
        assertNull(ClipboardEntrySizeLimit.sanitize(tooLarge, json))
    }

    @Test
    fun oversizedHtml_isDroppedBeforeText() {
        val oversizedHtml = entry(
            text = "keep me",
            htmlText = "<p>" + "x".repeat(2_000_000) + "</p>",
        )
        val safe = ClipboardEntrySizeLimit.sanitize(oversizedHtml, json)
        assertNotNull(safe)
        assertEquals("keep me", safe!!.text)
        assertNull(safe.htmlText)
    }

    @Test
    fun oversizedTextBlocksAreDroppedButImageBlocksRemain() {
        val oversized = entry(
            text = "caption",
            contentBlocks = listOf(
                ClipboardContentBlock.text("x".repeat(1_000_000)),
                ClipboardContentBlock.image("photo.png"),
            ),
        )
        val safe = ClipboardEntrySizeLimit.sanitize(oversized, json)
        assertNotNull(safe)
        assertEquals(1, safe!!.contentBlocks.size)
        assertEquals(ClipboardBlockKind.IMAGE, safe.contentBlocks.single().kind)
    }

    @Test
    fun normalEntry_isUntouched() {
        val normal = entry(htmlText = "<p>hello</p>")
        assertEquals(normal, ClipboardEntrySizeLimit.sanitize(normal, json))
    }
}
