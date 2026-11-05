package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchEngineIconShapeTest {
    @Test
    fun fromId_fallsBackToDefaultForUnknownOrMissing() {
        assertEquals(SearchEngineIconShape.DEFAULT, SearchEngineIconShape.fromId(null))
        assertEquals(SearchEngineIconShape.DEFAULT, SearchEngineIconShape.fromId(""))
        assertEquals(SearchEngineIconShape.DEFAULT, SearchEngineIconShape.fromId("LEGACY_ROUNDED"))
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
}
