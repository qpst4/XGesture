package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PickResultBarButtonsTest {

    @Test
    fun defaultOrders_coverEveryButtonExactlyOnce() {
        assertEquals(
            PickResultTextBarButton.entries.map { it.id }.toSet(),
            PickResultBarButtons.defaultTextOrder.toSet(),
        )
        assertEquals(
            PickResultTextBarButton.entries.size,
            PickResultBarButtons.defaultTextOrder.size,
        )
        assertEquals(
            PickResultImageBarButton.entries.map { it.id }.toSet(),
            PickResultBarButtons.defaultImageOrder.toSet(),
        )
        assertEquals(
            PickResultImageBarButton.entries.size,
            PickResultBarButtons.defaultImageOrder.size,
        )
    }

    @Test
    fun emptySaved_fallsBackToDefaultOrder() {
        assertEquals(
            PickResultTextBarButton.entries.toList(),
            PickResultBarButtons.normalizeText(emptyList()),
        )
        assertEquals(
            PickResultImageBarButton.entries.toList(),
            PickResultBarButtons.normalizeImage(emptyList()),
        )
    }

    @Test
    fun savedOrder_keepsUserOrder_andAppendsNewButtonsAtEnd() {
        val saved = listOf(
            PickResultTextBarButton.SHARE.id,
            PickResultTextBarButton.COPY.id,
        )
        assertEquals(
            listOf(
                PickResultTextBarButton.SHARE,
                PickResultTextBarButton.COPY,
                PickResultTextBarButton.OPEN_LINK,
                PickResultTextBarButton.SEARCH,
                PickResultTextBarButton.TRANSLATE,
                PickResultTextBarButton.SPEAK,
                PickResultTextBarButton.PIN,
                PickResultTextBarButton.STASH,
                PickResultTextBarButton.QR,
                PickResultTextBarButton.PROCESS_TEXT,
            ),
            PickResultBarButtons.normalizeText(saved),
        )
    }

    @Test
    fun unknownAndDuplicateIds_areDropped() {
        val saved = listOf(
            "ghost_button",
            PickResultImageBarButton.SHARE.id,
            PickResultImageBarButton.SHARE.id,
            PickResultImageBarButton.SAVE.id,
        )
        assertEquals(
            listOf(
                PickResultImageBarButton.SHARE,
                PickResultImageBarButton.SAVE,
                PickResultImageBarButton.IMAGE_SEARCH,
                PickResultImageBarButton.PIN,
                PickResultImageBarButton.STASH,
            ),
            PickResultBarButtons.normalizeImage(saved),
        )
    }

    @Test
    fun legacyMoreId_isDroppedByNormalization() {
        // 旧版本（去掉三点菜单之前）落盘过 "more"，升级后必须被安静丢弃。
        val saved = listOf("more", PickResultTextBarButton.COPY.id)
        assertEquals(
            listOf(
                PickResultTextBarButton.COPY,
                PickResultTextBarButton.OPEN_LINK,
                PickResultTextBarButton.SEARCH,
                PickResultTextBarButton.TRANSLATE,
                PickResultTextBarButton.SHARE,
                PickResultTextBarButton.SPEAK,
                PickResultTextBarButton.PIN,
                PickResultTextBarButton.STASH,
                PickResultTextBarButton.QR,
                PickResultTextBarButton.PROCESS_TEXT,
            ),
            PickResultBarButtons.normalizeText(saved),
        )
        assertTrue("more" !in PickResultBarButtons.defaultImageOrder)
    }

    @Test
    fun orderCodec_roundTripsAndSkipsBlankEntries() {
        val ids = PickResultBarButtons.defaultTextOrder
        val raw = PickResultBarButtons.encodeOrder(ids)
        assertEquals(ids, PickResultBarButtons.decodeOrder(raw))
        assertEquals(
            listOf("copy", "share"),
            PickResultBarButtons.decodeOrder("\u001Fcopy\u001F\u001F  \u001Fshare\u001F"),
        )
        assertEquals(emptyList<String>(), PickResultBarButtons.decodeOrder(null))
    }

    @Test
    fun hiddenSets_dropUnknownIds_only() {
        val hidden = setOf("copy", "ghost", PickResultImageBarButton.SAVE.id)
        assertEquals(setOf("copy"), PickResultBarButtons.sanitizeHiddenText(hidden))
        assertEquals(
            setOf(PickResultImageBarButton.SAVE.id),
            PickResultBarButtons.sanitizeHiddenImage(hidden),
        )
        assertTrue(PickResultBarButtons.sanitizeHiddenText(emptySet()).isEmpty())
    }
}
