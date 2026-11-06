package com.slideindex.app.translate

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TranslateTargetResolverTest {
    @Test
    fun followApp_withArabicUiTag_resolvesArabic() {
        assertEquals(
            "ar",
            TranslateTargetResolver.resolve(
                storedCode = TranslateTargetLanguages.FOLLOW_APP,
                appUiLanguageTag = "ar",
            ),
        )
    }

    @Test
    fun followApp_withTurkishUiTags_resolvesTurkish() {
        listOf("tr", "tr-TR", "tr-CY").forEach { languageTag ->
            assertEquals(
                "tr",
                TranslateTargetResolver.resolve(
                    storedCode = TranslateTargetLanguages.FOLLOW_APP,
                    appUiLanguageTag = languageTag,
                ),
            )
        }
    }

    @Test
    fun followApp_withSystemUiTag_unsupportedLocale_fallsBackToEnglish() {
        // 别再用 pt-BR 当"不支持"的样本：巴葡在 323b4519（2026-10-11）已进目录，
        // 这里换一个确实不在 TranslateLanguageCatalog 里的语言标签。
        assertEquals(
            "en",
            TranslateTargetResolver.resolve(
                storedCode = TranslateTargetLanguages.FOLLOW_APP,
                appUiLanguageTag = "",
                locale = Locale.forLanguageTag("xx-YY"),
            ),
        )
    }

    @Test
    fun fixedJapanese_ignoresAppUiLanguage() {
        assertEquals(
            "ja",
            TranslateTargetResolver.resolve(
                storedCode = "ja",
                appUiLanguageTag = "ar",
            ),
        )
    }

    @Test
    fun followApp_systemLocaleTraditionalChinese_mapsZhTw() {
        assertEquals(
            "zh-TW",
            TranslateTargetResolver.resolve(
                storedCode = TranslateTargetLanguages.FOLLOW_APP,
                appUiLanguageTag = "",
                locale = Locale.TAIWAN,
            ),
        )
    }

    @Test
    fun blankStoredCode_treatedAsFollowApp() {
        assertEquals(
            "en",
            TranslateTargetResolver.resolve(
                storedCode = "   ",
                appUiLanguageTag = "en",
            ),
        )
    }
}
