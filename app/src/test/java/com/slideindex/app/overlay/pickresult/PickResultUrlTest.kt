package com.slideindex.app.overlay.pickresult

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickResultUrlTest {
    @Test
    fun normalize_httpsUrl() {
        assertEquals(
            "https://example.com/path",
            PickResultUrl.normalizeOpenableUrl("https://example.com/path"),
        )
    }

    @Test
    fun normalize_wwwUrl() {
        assertEquals(
            "https://www.example.com",
            PickResultUrl.normalizeOpenableUrl("www.example.com"),
        )
    }

    @Test
    fun normalize_bareHost() {
        assertEquals(
            "https://example.com/foo",
            PickResultUrl.normalizeOpenableUrl("example.com/foo"),
        )
    }

    @Test
    fun normalize_trimsTrailingPunctuation() {
        assertEquals(
            "https://example.com",
            PickResultUrl.normalizeOpenableUrl("https://example.com."),
        )
    }

    @Test
    fun normalize_excludesAndroidPackageNames() {
        assertNull(PickResultUrl.normalizeOpenableUrl("com.android.settings"))
        assertNull(PickResultUrl.normalizeOpenableUrl("org.example.app"))
        assertNull(PickResultUrl.normalizeOpenableUrl("android.app.Activity"))
        assertNull(PickResultUrl.normalizeOpenableUrl("androidx.core.content.ContextCompat"))
        assertNull(PickResultUrl.normalizeOpenableUrl("io.github.foo.bar"))
    }

    @Test
    fun extract_multipleUrls() {
        val urls = PickResultUrl.extractOpenableUrls(
            "see https://a.com and www.b.com/path",
        )
        assertEquals(
            listOf("https://a.com", "https://www.b.com/path"),
            urls,
        )
    }

    @Test
    fun resolve_selectedUrl() {
        val action = PickResultUrl.resolveOpenLinkAction(
            fullText = "visit https://a.com and https://b.com",
            activeText = "https://b.com",
            hasSelection = true,
        )
        assertTrue(action is PickResultOpenLinkAction.Open)
        assertEquals("https://b.com", (action as PickResultOpenLinkAction.Open).url)
    }

    @Test
    fun resolve_fullTextSingleUrl() {
        val action = PickResultUrl.resolveOpenLinkAction(
            fullText = "https://example.com",
            activeText = "https://example.com",
            hasSelection = false,
        )
        assertTrue(action is PickResultOpenLinkAction.Open)
    }

    @Test
    fun resolve_fullTextMultipleUrls() {
        val action = PickResultUrl.resolveOpenLinkAction(
            fullText = "https://a.com https://b.com",
            activeText = "https://a.com https://b.com",
            hasSelection = false,
        )
        assertTrue(action is PickResultOpenLinkAction.Choose)
        assertEquals(2, (action as PickResultOpenLinkAction.Choose).urls.size)
    }

    @Test
    fun resolve_invalidSelection_returnsNull() {
        assertNull(
            PickResultUrl.resolveOpenLinkAction(
                fullText = "https://a.com https://b.com",
                activeText = "hello",
                hasSelection = true,
            ),
        )
    }

    @Test
    fun normalize_customScheme() {
        assertEquals(
            "weixin://dl/scan",
            PickResultUrl.normalizeOpenableUrl("weixin://dl/scan"),
        )
    }

    @Test
    fun normalize_telUri() {
        assertEquals(
            "tel:10086",
            PickResultUrl.normalizeOpenableUrl("tel:10086"),
        )
    }

    @Test
    fun normalize_blocksJavascript() {
        assertNull(PickResultUrl.normalizeOpenableUrl("javascript:alert(1)"))
    }

    @Test
    fun extract_customScheme() {
        val urls = PickResultUrl.extractOpenableUrls("open weixin://dl/scan now")
        assertEquals(listOf("weixin://dl/scan"), urls)
    }

    @Test
    fun linkDisplayLabel_customScheme() {
        assertEquals("weixin", PickResultUrl.linkDisplayLabel("weixin://dl/scan"))
    }

    @Test
    fun linkDisplayLabel_tel() {
        assertEquals("10086", PickResultUrl.linkDisplayLabel("tel:10086"))
    }

    @Test
    fun extract_urlsSeparatedByChinesePunctuation() {
        val text = "调查记者，主持人。油管频道地址：https://t.co/WfO3vZ0ygY?amp=1，" +
            "Facebook地址：https://t.co/QO4mprdzUL，TikTok地址：https://t.co/chl5sh7Cfa"

        assertEquals(
            listOf(
                "https://t.co/WfO3vZ0ygY?amp=1",
                "https://t.co/QO4mprdzUL",
                "https://t.co/chl5sh7Cfa",
            ),
            PickResultUrl.extractOpenableUrls(text),
        )
    }

    @Test
    fun extract_urlsSeparatedByAsciiComma() {
        assertEquals(
            listOf("https://a.com/x", "https://b.com/y"),
            PickResultUrl.extractOpenableUrls("https://a.com/x,https://b.com/y"),
        )
    }

    @Test
    fun extract_urlStopsAtChineseText() {
        assertEquals(
            listOf("https://a.com/path"),
            PickResultUrl.extractOpenableUrls("官网https://a.com/path然后是微博"),
        )
    }

    @Test
    fun extract_urlStopsAtTrailingChinesePunctuation() {
        assertEquals(
            listOf("https://a.com"),
            PickResultUrl.extractOpenableUrls("链接：https://a.com。"),
        )
    }

    @Test
    fun extract_wwwHostAfterChinesePunctuation() {
        assertEquals(
            listOf("https://www.b.com/x"),
            PickResultUrl.extractOpenableUrls("地址：www.b.com/x，还有别的"),
        )
    }

    @Test
    fun extract_keepsHostPort() {
        assertEquals(
            listOf("https://a.com:8080/x"),
            PickResultUrl.extractOpenableUrls("服务：https://a.com:8080/x，谢谢"),
        )
    }

    @Test
    fun normalize_rejectsWholeParagraph() {
        assertNull(
            PickResultUrl.normalizeOpenableUrl(
                "油管：https://t.co/WfO3vZ0ygY?amp=1，微博：https://t.co/QO4mprdzUL",
            ),
        )
    }

    @Test
    fun resolve_fullTextChinesePunctuationSeparatedUrls_offersChooser() {
        val text = "油管：https://t.co/aaa，微博：https://t.co/bbb，B站：https://t.co/ccc"

        val action = PickResultUrl.resolveOpenLinkAction(
            fullText = text,
            activeText = text,
            hasSelection = false,
        )

        assertTrue(action is PickResultOpenLinkAction.Choose)
        assertEquals(3, (action as PickResultOpenLinkAction.Choose).urls.size)
    }

    @Test
    fun resolve_selectionHoldingMultipleUrls_offersChooser() {
        val text = "https://t.co/aaa，https://t.co/bbb"

        val action = PickResultUrl.resolveOpenLinkAction(
            fullText = text,
            activeText = text,
            hasSelection = true,
        )

        assertTrue(action is PickResultOpenLinkAction.Choose)
        assertEquals(2, (action as PickResultOpenLinkAction.Choose).urls.size)
    }

    @Test
    fun linkDisplayLabels_disambiguatesSameHost() {
        assertEquals(
            listOf("t.co/WfO3vZ0ygY", "t.co/QO4mprdzUL", "t.co/chl5sh7Cfa"),
            PickResultUrl.linkDisplayLabels(
                listOf(
                    "https://t.co/WfO3vZ0ygY?amp=1",
                    "https://t.co/QO4mprdzUL",
                    "https://t.co/chl5sh7Cfa",
                ),
            ),
        )
        assertEquals(
            listOf("a.com", "www.b.com"),
            PickResultUrl.linkDisplayLabels(listOf("https://a.com", "https://www.b.com")),
        )
    }

    @Test
    fun normalize_intentUriWithComponent() {
        val uri = "intent:#Intent;component=com.geekholt.snapnote/.shortcut.ShortcutLaunchActivity;" +
            "S.shortcut_action=search;end"
        assertEquals(uri, PickResultUrl.normalizeOpenableUrl(uri))
    }

    @Test
    fun normalize_intentUriWithData() {
        val uri = "intent://m.quark.cn/s?q=%s#Intent;scheme=https;package=com.quark.browser;end"
        assertEquals(uri, PickResultUrl.normalizeOpenableUrl(uri))
    }

    @Test
    fun extract_intentUriWithComponent() {
        val uri = "intent:#Intent;component=com.example/.Main;S.action=search;end"
        assertEquals(
            listOf(uri),
            PickResultUrl.extractOpenableUrls("打开 $uri 试试"),
        )
    }

    @Test
    fun extract_intentUriWithData_keepsWholeUri() {
        val uri = "intent://m.quark.cn/s?q=%s#Intent;scheme=https;package=com.quark.browser;end"
        assertEquals(
            listOf(uri),
            PickResultUrl.extractOpenableUrls("go $uri now"),
        )
    }

    @Test
    fun extract_intentUri_doesNotAddTruncatedInnerLink() {
        val uri = "intent://example.com/path#Intent;scheme=https;package=com.example;end"
        assertEquals(listOf(uri), PickResultUrl.extractOpenableUrls(uri))
    }

    @Test
    fun resolve_intentUri() {
        val uri = "intent:#Intent;component=com.example/.Main;S.action=search;end"
        val action = PickResultUrl.resolveOpenLinkAction(
            fullText = uri,
            activeText = uri,
            hasSelection = false,
        )
        assertTrue(action is PickResultOpenLinkAction.Open)
        assertEquals(uri, (action as PickResultOpenLinkAction.Open).url)
    }

    @Test
    fun ranges_returnRawSubstring_notNormalizedUrl() {
        val text = "地址：www.b.com/x，还有别的"
        val ranges = PickResultUrl.findOpenableUrlRanges(text)
        assertEquals(1, ranges.size)
        assertEquals("www.b.com/x", text.substring(ranges[0].first, ranges[0].last + 1))
    }

    @Test
    fun ranges_doNotDuplicateHttpLinks() {
        // `https://` 同时满足「自定义协议」正则：只应留下最外层一段。
        val text = "see https://a.com and https://b.com/y"
        assertEquals(2, PickResultUrl.findOpenableUrlRanges(text).size)
    }

    @Test
    fun ranges_leaveTrailingPunctuationOutside() {
        val text = "打开 https://a.com/x. 吧"
        val ranges = PickResultUrl.findOpenableUrlRanges(text)
        assertEquals(1, ranges.size)
        val link = text.substring(ranges[0].first, ranges[0].last + 1)
        assertEquals("https://a.com/x", link)
    }

    @Test
    fun ranges_skipIntentInnerLink() {
        val uri = "intent://example.com/path#Intent;scheme=https;package=com.example;end"
        val ranges = PickResultUrl.findOpenableUrlRanges("打开 $uri 试试")
        assertEquals(1, ranges.size)
        assertEquals(uri, "打开 $uri 试试".substring(ranges[0].first, ranges[0].last + 1))
    }
}
