package com.slideindex.app.search

import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文字引擎分派的契约。
 *
 * 回归背景：流转栏曾直接调 [SearchEngineLauncher.launch]，而它对"分享到 App"的引擎
 * 直接返回 false —— 把文字拖到「分享到 QQ」上松手完全没反应。这里保证
 * "能出现在文字入口里的类型"都有明确的执行方式，别再把分享类漏成静默分支。
 */
class SearchEngineTextLaunchDispatchTest {

    @Test
    fun `share to app is dispatched as text share`() {
        assertEquals(
            TextLaunchKind.SHARE_TEXT,
            textLaunchKind(SearchEngineType.SHARE_TO_APP),
        )
    }

    @Test
    fun `every text-capable engine type has a launch kind`() {
        val unsupported = SearchEngineType.entries
            .filter { type ->
                SearchEngineConfig(name = "probe", engineType = type).isTextSearchEngine()
            }
            .filter { type -> textLaunchKind(type) == TextLaunchKind.UNSUPPORTED }

        assertTrue(
            "这些类型能作为文字引擎出现，却没有执行方式，会导致点击/拖拽静默失败：$unsupported",
            unsupported.isEmpty(),
        )
    }

    @Test
    fun `image and file share engines are not text engines`() {
        assertFalse(
            SearchEngineConfig(name = "probe", engineType = SearchEngineType.SHARE_IMAGE_TO_APP)
                .isTextSearchEngine()
        )
        assertFalse(
            SearchEngineConfig(name = "probe", engineType = SearchEngineType.SHARE_FILE_TO_APP)
                .isTextSearchEngine()
        )
        assertEquals(
            TextLaunchKind.UNSUPPORTED,
            textLaunchKind(SearchEngineType.SHARE_IMAGE_TO_APP),
        )
        assertEquals(
            TextLaunchKind.UNSUPPORTED,
            textLaunchKind(SearchEngineType.SHARE_FILE_TO_APP),
        )
    }
}
