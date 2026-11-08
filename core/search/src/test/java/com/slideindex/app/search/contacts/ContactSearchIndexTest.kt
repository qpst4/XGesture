package com.slideindex.app.search.contacts

import com.slideindex.app.util.PinyinHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSearchIndexTest {

    @Test
    fun testPinyinAndInitialExtraction() {
        val name = "张三"
        val fullPinyin = PinyinHelper.sortKey(name)
        val initialPinyin = PinyinHelper.initialKey(name)

        assertEquals("zhangsan", fullPinyin)
        assertEquals("zs", initialPinyin)
    }

    @Test
    fun testContactSearchScoring() {
        val entry1 = contact("张三", "13800001234", "138 0000 1234", id = 1L)
        val entry2 = contact("李四", "13988885678", "139 8888 5678", id = 2L)

        assertNotNull(ContactSearchIndex.score(entry1, "zs", ""))
        assertNull(ContactSearchIndex.score(entry2, "zs", ""))

        assertNotNull(ContactSearchIndex.score(entry1, "138", "138"))
        assertNull(ContactSearchIndex.score(entry2, "138", "138"))
    }

    @Test
    fun songGuang_pinyinPrefixMatches_butMidLetterDoesNot() {
        val entry = contact("宋广", "13600001111", "136 0000 1111")

        assertEquals("songguang", entry.fullPinyin)
        assertEquals("sg", entry.initialPinyin)

        // 合理前缀：全拼 / 首字母缩写
        for (query in listOf("s", "so", "song", "songg", "songguang", "sg")) {
            assertNotNull("expected match for query=$query", ContactSearchIndex.score(entry, query, ""))
        }

        // 中间字母不应命中（旧逻辑 fullPinyin.contains("u") 会误匹配）
        assertNull(ContactSearchIndex.score(entry, "u", ""))
        assertNull(ContactSearchIndex.score(entry, "guang", ""))
    }

    @Test
    fun chineseMidNameStillMatchesViaDisplayName() {
        val entry = contact("张三丰", "13700001111", "137 0000 1111")
        assertTrue(entry.name.contains("三"))
        assertNotNull(ContactSearchIndex.score(entry, "三", ""))
    }

    private fun contact(
        name: String,
        phoneNumber: String,
        formattedPhone: String,
        id: Long = 1L,
    ) = ContactSearchEntry(
        id = id,
        lookupKey = "lookup-$id",
        name = name,
        phoneNumber = phoneNumber,
        formattedPhone = formattedPhone,
        fullPinyin = PinyinHelper.sortKey(name),
        initialPinyin = PinyinHelper.initialKey(name),
    )
}
