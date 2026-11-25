package com.slideindex.app.settings

import com.slideindex.app.overlay.layout.FvIconShape
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchEngineIconShapeTest {
    @Test
    fun fromId_fallsBackToFollowGlobalForUnknownOrMissing() {
        assertEquals(SearchEngineIconShape.FOLLOW_GLOBAL, SearchEngineIconShape.fromId(null))
        assertEquals(SearchEngineIconShape.FOLLOW_GLOBAL, SearchEngineIconShape.fromId(""))
        assertEquals(SearchEngineIconShape.FOLLOW_GLOBAL, SearchEngineIconShape.fromId("LEGACY_ROUNDED"))
    }

    @Test
    fun fromId_readsPersistedNames() {
        SearchEngineIconShape.entries.forEach { shape ->
            assertEquals(shape, SearchEngineIconShape.fromId(shape.name))
        }
    }

    @Test
    fun defaultKeepsHistoricalCornerFraction() {
        // 老用户升级后 dock/网格观感不能变：DEFAULT 必须还是 24% 圆角。
        assertEquals(0.24f, SearchEngineIconShape.DEFAULT.cornerFraction, 0.0001f)
    }

    @Test
    fun resolve_followGlobalUsesGlobalShape() {
        assertEquals(FvIconShape.CIRCLE, SearchEngineIconShape.FOLLOW_GLOBAL.resolve(FvIconShape.CIRCLE))
        assertEquals(FvIconShape.SQUIRCLE, SearchEngineIconShape.FOLLOW_GLOBAL.resolve(FvIconShape.SQUIRCLE))
        assertEquals(FvIconShape.ROUNDED_RECT, SearchEngineIconShape.FOLLOW_GLOBAL.resolve(FvIconShape.ROUNDED_RECT))
        assertEquals(FvIconShape.SQUARE, SearchEngineIconShape.FOLLOW_GLOBAL.resolve(FvIconShape.SQUARE))
        assertEquals(FvIconShape.ORIGINAL, SearchEngineIconShape.FOLLOW_GLOBAL.resolve(FvIconShape.ORIGINAL))

        assertEquals(FvIconShape.CIRCLE, SearchEngineIconShape.CIRCLE.resolve(FvIconShape.SQUARE))
        assertEquals(FvIconShape.ROUNDED_RECT, SearchEngineIconShape.ROUNDED_RECT.resolve(FvIconShape.CIRCLE))
        assertEquals(FvIconShape.ORIGINAL, SearchEngineIconShape.ORIGINAL.resolve(FvIconShape.CIRCLE))
    }
}
