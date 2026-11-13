package com.slideindex.app.clipboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 截图重复入库的判定。
 *
 * 回归背景：同一次截图会从聚合卷 / 主卷两个 uri 别名各入库一次（间隔 40~60ms），
 * 内容指纹对不上，历史里出现两条一模一样的记录。
 */
class ScreenshotIngestDedupTest {

    private val now = 1_700_000_000_000L

    private fun entry(
        text: String,
        createdAt: Long,
        type: ClipboardEntryType = ClipboardEntryType.URI,
    ) = ClipboardEntry(
        id = "id",
        type = type,
        text = text,
        createdAtEpochMs = createdAt,
    )

    @Test
    fun `same display name inside the window is a duplicate`() {
        val latest = entry("Screenshot_20261113-170223.png", now - 55L)
        assertTrue(ScreenshotIngestDedup.isDuplicate(latest, "Screenshot_20261113-170223.png", now))
    }

    @Test
    fun `same display name outside the window is not a duplicate`() {
        val latest = entry("Screenshot_20261113-170223.png", now - ScreenshotIngestDedup.WINDOW_MS - 1)
        assertFalse(ScreenshotIngestDedup.isDuplicate(latest, "Screenshot_20261113-170223.png", now))
    }

    @Test
    fun `different display name is not a duplicate even inside the window`() {
        val latest = entry("Screenshot_20261113-170223.png", now - 55L)
        assertFalse(ScreenshotIngestDedup.isDuplicate(latest, "Screenshot_20261113-170224.png", now))
    }

    @Test
    fun `non image entries are never treated as a screenshot duplicate`() {
        val latest = entry("hello", now - 10L, type = ClipboardEntryType.TEXT)
        assertFalse(ScreenshotIngestDedup.isDuplicate(latest, "hello", now))
    }

    @Test
    fun `empty history is not a duplicate`() {
        assertFalse(ScreenshotIngestDedup.isDuplicate(null, "Screenshot_20261113-170223.png", now))
    }

    @Test
    fun `surrounding whitespace does not matter`() {
        val latest = entry(" Screenshot_20261113-170223.png ", now - 10L)
        assertTrue(ScreenshotIngestDedup.isDuplicate(latest, "Screenshot_20261113-170223.png", now))
    }
}
