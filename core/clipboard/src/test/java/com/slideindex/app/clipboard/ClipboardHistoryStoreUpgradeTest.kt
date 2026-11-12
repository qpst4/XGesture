package com.slideindex.app.clipboard

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 老库升级（v4 → v5）时必须先清掉超大行，不能等第一次 `SELECT entry_json` 才炸。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class ClipboardHistoryStoreUpgradeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun upgradeFromV4_purgesOversizedRowBeforeQuery() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DB_NAME)
        val file = context.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE clipboard_entries (
                    id TEXT PRIMARY KEY NOT NULL,
                    created_at_ms INTEGER NOT NULL,
                    search_blob TEXT NOT NULL,
                    content_key TEXT NOT NULL,
                    fingerprint TEXT NOT NULL,
                    entry_type TEXT NOT NULL DEFAULT 'TEXT',
                    preview_text TEXT NOT NULL DEFAULT '',
                    has_image INTEGER NOT NULL DEFAULT 0,
                    entry_json TEXT NOT NULL
                )
                """.trimIndent()
            )
            val hugeJson = "{\"id\":\"bad\",\"type\":\"TEXT\",\"text\":\"" +
                "x".repeat(2_000_000) +
                "\",\"createdAtEpochMs\":1}"
            db.insert(
                "clipboard_entries",
                null,
                ContentValues().apply {
                    put("id", "bad")
                    put("created_at_ms", 1L)
                    put("search_blob", "bad")
                    put("content_key", "bad")
                    put("fingerprint", "bad")
                    put("entry_type", "TEXT")
                    put("preview_text", "bad")
                    put("has_image", 0)
                    put("entry_json", hugeJson)
                },
            )
            db.version = 4
        }

        val store = ClipboardHistoryStore(context, json)
        assertEquals(emptyList<ClipboardEntry>(), store.queryAll())
        assertEquals(0, store.count())
    }

    private companion object {
        const val DB_NAME = "clipboard_history.db"
    }
}
