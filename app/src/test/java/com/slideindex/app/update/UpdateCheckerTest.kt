package com.slideindex.app.update

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UpdateCheckerTest {
    @Test
    fun pickBetterManifest_prefersHigherVersion() {
        val older = manifest("1.8.2", apkSize = 100L)
        val newer = manifest("1.8.3", apkSize = 0L)
        assertEquals(newer, UpdateChecker.pickBetterManifest(older, newer))
        assertEquals(newer, UpdateChecker.pickBetterManifest(newer, older))
    }

    @Test
    fun pickBetterManifest_sameVersion_prefersNonZeroApkSize() {
        val placeholder = manifest("1.8.3", apkSize = 0L)
        val ready = manifest("1.8.3", apkSize = 49_243_678L)
        assertEquals(ready, UpdateChecker.pickBetterManifest(placeholder, ready))
        assertEquals(ready, UpdateChecker.pickBetterManifest(ready, placeholder))
    }

    @Test
    fun pickBetterManifest_sameVersionBothZero_keepsCurrent() {
        val first = manifest("1.8.3", apkSize = 0L)
        val second = manifest("1.8.3", apkSize = 0L, notes = "other")
        assertEquals(first, UpdateChecker.pickBetterManifest(first, second))
    }

    @Test
    fun formatNotesForDisplay_splitsSemicolonsAndBlankLines() {
        val raw = "第一行；第二行\n\n第三行"
        // 必须显式传 locale：不传就吃 Locale.getDefault()，CI 的 en-US runner 上会渲染成 "1. "，
        // 本地 zh-CN 的 Windows 反而一路绿（这就是这条红只在 CI 出现的原因）。
        assertEquals(
            "一、第一行\n二、第二行\n三、第三行",
            UpdateChecker.formatNotesForDisplay(raw, Locale.SIMPLIFIED_CHINESE),
        )
        // 英文环境走另一支，顺手把序号格式一起锁住。
        assertEquals(
            "1. 第一行\n2. 第二行\n3. 第三行",
            UpdateChecker.formatNotesForDisplay(raw, Locale.US),
        )
    }

    @Test
    fun formatNotesForDisplay_preservesExistingNewlines() {
        assertEquals(
            "一、a\n二、b",
            UpdateChecker.formatNotesForDisplay("a\nb", Locale.SIMPLIFIED_CHINESE),
        )
    }

    @Test
    fun parseUpdateNotes_parsesGroupedHeadersAndItems() {
        val groups = UpdateChecker.parseUpdateNotes(
            "## 新增\n- 功能A\n- 功能B\n## 修复\n- 修复C",
        )
        assertEquals(2, groups.size)
        assertEquals("新增", groups[0].title)
        assertEquals(listOf("功能A", "功能B"), groups[0].items)
        assertEquals("修复", groups[1].title)
        assertEquals(listOf("修复C"), groups[1].items)
    }

    @Test
    fun parseUpdateNotes_mixedLegacyAndGroupedLines() {
        val groups = UpdateChecker.parseUpdateNotes("旧条目\n## 新增\n- 新功能")
        assertEquals(2, groups.size)
        assertEquals(null, groups[0].title)
        assertEquals(listOf("旧条目"), groups[0].items)
        assertEquals("新增", groups[1].title)
        assertEquals(listOf("新功能"), groups[1].items)
    }

    @Test
    fun formatNotesForDisplay_groupedNotes_restartsNumberingPerGroup() {
        assertEquals(
            "新增\n一、功能A\n二、功能B\n修复\n一、修复C",
            UpdateChecker.formatNotesForDisplay(
                "## 新增\n- 功能A\n- 功能B\n## 修复\n- 修复C",
                Locale.SIMPLIFIED_CHINESE,
            ),
        )
    }

    @Test
    fun chineseOrdinal_coversTensAndHundredsStyle() {
        assertEquals("一", UpdateChecker.chineseOrdinal(1))
        assertEquals("九", UpdateChecker.chineseOrdinal(9))
        assertEquals("十", UpdateChecker.chineseOrdinal(10))
        assertEquals("十五", UpdateChecker.chineseOrdinal(15))
        assertEquals("二十", UpdateChecker.chineseOrdinal(20))
        assertEquals("三十四", UpdateChecker.chineseOrdinal(34))
        assertEquals("九十九", UpdateChecker.chineseOrdinal(99))
    }

    @Test
    fun selectNotes_chineseLocalePrefersChineseNotes() {
        val state = state(notes = "中文说明", notesEn = "English notes")
        assertEquals("中文说明", UpdateChecker.selectNotes(state, Locale.SIMPLIFIED_CHINESE))
    }

    @Test
    fun selectNotes_otherLocalesPreferEnglishNotes() {
        val state = state(notes = "中文说明", notesEn = "English notes")
        assertEquals("English notes", UpdateChecker.selectNotes(state, Locale.US))
        assertEquals("English notes", UpdateChecker.selectNotes(state, Locale.JAPAN))
    }

    @Test
    fun selectNotes_fallsBackWhenPreferredLanguageIsMissing() {
        val englishOnly = state(notes = "", notesEn = "English notes")
        assertEquals("English notes", UpdateChecker.selectNotes(englishOnly, Locale.SIMPLIFIED_CHINESE))

        // Legacy manifest written before notesEn existed: Chinese only.
        val chineseOnly = state(notes = "中文说明", notesEn = "")
        assertEquals("中文说明", UpdateChecker.selectNotes(chineseOnly, Locale.US))
    }

    @Test
    fun selectNotes_honoursNotesLangWhenNotesHoldsEnglish() {
        val englishPrimary = state(notes = "English notes", notesEn = "中文说明", notesLang = "en")
        assertEquals("English notes", UpdateChecker.selectNotes(englishPrimary, Locale.US))
        assertEquals("中文说明", UpdateChecker.selectNotes(englishPrimary, Locale.SIMPLIFIED_CHINESE))
    }

    @Test
    fun selectNotes_blankBothLanguagesIsBlank() {
        assertEquals("", UpdateChecker.selectNotes(state(notes = "", notesEn = ""), Locale.US))
    }

    private fun manifest(version: String, apkSize: Long, notes: String = "") =
        UpdateManifest(
            version = version,
            versionCode = 1,
            apkUrl = "https://example.com/xgesture-$version.apk",
            apkSize = apkSize,
            notes = notes,
        )

    private fun state(
        notes: String = "",
        notesEn: String = "",
        notesLang: String = UpdateManifest.DEFAULT_NOTES_LANG,
    ) = UpdateState(notes = notes, notesEn = notesEn, notesLang = notesLang)
}
