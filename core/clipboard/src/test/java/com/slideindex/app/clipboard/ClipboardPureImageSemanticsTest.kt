package com.slideindex.app.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardPureImageSemanticsTest {

    private fun imageEntry(
        text: String = "",
        uri: String = "content://media/external/images/media/42",
        htmlText: String? = null,
        contentBlocks: List<ClipboardContentBlock> = emptyList(),
    ) = ClipboardEntry(
        id = "entry-1",
        type = ClipboardEntryType.URI,
        text = text,
        uri = uri,
        htmlText = htmlText,
        mimeType = "image/png",
        imageFileName = "entry-1.png",
        imageFileNames = listOf("entry-1.png"),
        contentBlocks = contentBlocks,
        createdAtEpochMs = 1L,
    )

    @Test
    fun labelTextIsDroppedFromFallbackBlocksForImageEntries() {
        val blocks = ClipboardBlockParser.buildBlocks(
            text = "IMG_001.png",
            htmlText = null,
            imageFileNames = listOf("entry-1.png"),
            imageSources = listOf("content://media/external/images/media/42"),
        )

        assertEquals(listOf(ClipboardBlockKind.IMAGE), blocks.map { it.kind })
    }

    @Test
    fun textOnlyFallbackStillProducesTextBlock() {
        val blocks = ClipboardBlockParser.buildBlocks(
            text = "hello",
            htmlText = null,
            imageFileNames = emptyList(),
            imageSources = emptyList(),
        )

        assertEquals(listOf(ClipboardBlockKind.TEXT), blocks.map { it.kind })
        assertEquals("hello", blocks.single().text)
    }

    @Test
    fun screenshotEntryWithLabelTextIsPureImage() {
        assertTrue(imageEntry(text = "Screenshot_20260815-120000.png").isPureImageEntry())
    }

    @Test
    fun oldStoredLabelBlocksAreStillPureImage() {
        val entry = imageEntry(
            text = "Screenshot_20260815-120000.png",
            contentBlocks = listOf(
                ClipboardContentBlock.text("Screenshot_20260815-120000.png"),
                ClipboardContentBlock.image("entry-1.png"),
            ),
        )

        assertTrue(entry.isPureImageEntry())
    }

    @Test
    fun richEntryWithHtmlAndRealTextIsNotPureImage() {
        val entry = imageEntry(
            text = "caption",
            htmlText = "<p>caption</p><img src=\"http://example.com/a.png\">",
            contentBlocks = listOf(
                ClipboardContentBlock.text("caption"),
                ClipboardContentBlock.image("entry-1.png"),
            ),
        )

        assertFalse(entry.isPureImageEntry())
    }

    @Test
    fun textOnlyEntryIsNotPureImage() {
        val entry = ClipboardEntry(
            id = "entry-text",
            type = ClipboardEntryType.TEXT,
            text = "hello",
            createdAtEpochMs = 1L,
        )

        assertFalse(entry.isPureImageEntry())
    }
}
