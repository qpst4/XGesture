package com.slideindex.app.clipboard

import android.app.Application
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class ClipboardHistoryStoreSizeLimitTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var store: ClipboardHistoryStore

    @Before
    fun setUp() {
        store = ClipboardHistoryStore(RuntimeEnvironment.getApplication(), json)
        store.deleteAll()
    }

    @Test
    fun oversizedText_isNotStored() {
        store.insert(
            ClipboardEntry(
                id = "too-large",
                text = "x".repeat(ClipboardEntrySizeLimit.MAX_TEXT_CHARS + 1),
                createdAtEpochMs = 1L,
            )
        )
        assertEquals(0, store.count())
        assertEquals(emptyList<ClipboardEntry>(), store.queryAll())
    }

    @Test
    fun oversizedHtml_isStoredWithoutHtml() {
        store.insert(
            ClipboardEntry(
                id = "rich",
                text = "keep",
                htmlText = "<p>" + "x".repeat(2_000_000) + "</p>",
                createdAtEpochMs = 1L,
            )
        )
        val saved = store.queryById("rich")
        assertNotNull(saved)
        assertEquals("keep", saved!!.text)
        assertNull(saved.htmlText)
    }
}
