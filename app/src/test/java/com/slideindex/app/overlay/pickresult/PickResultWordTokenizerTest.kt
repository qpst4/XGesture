package com.slideindex.app.overlay.pickresult

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PickResultWordTokenizerTest {
    @Test
    fun `tokenizeSelectableWords splits latin words`() {
        val tokens = PickResultWordTokenizer.tokenizeSelectableWords("hello world")
        assertEquals(listOf("hello", " ", "world"), tokens)
    }

    @Test
    fun `tokenizeSelectableWords preserves consecutive spaces`() {
        val tokens = PickResultWordTokenizer.tokenizeSelectableWords("a  b")
        assertEquals(listOf("a", " ", " ", "b"), tokens)
    }

    @Test
    fun `tokenizeSelectableChars keeps every code point and whitespace`() {
        val text = "a 我😀\n"
        val tokens = PickResultWordTokenizer.tokenizeSelectableChars(text)
        assertEquals(listOf("a", " ", "我", "😀", "\n"), tokens)
        assertEquals(text, tokens.joinToString(""))
    }

    @Test
    fun `isWhitespaceToken detects whitespace tokens`() {
        assertTrue(PickResultWordTokenizer.isWhitespaceToken(" "))
        assertTrue(PickResultWordTokenizer.isWhitespaceToken("\u3000"))
        assertTrue(!PickResultWordTokenizer.isWhitespaceToken("hello"))
    }

    @Test
    fun `tokenizeSelectableWords preserves chinese text`() {
        val source = "北京大学"
        val tokens = PickResultWordTokenizer.tokenizeSelectableWords(source)
        assertTrue(tokens.isNotEmpty())
        assertEquals(source, tokens.joinToString(""))
    }

    @Test
    fun `breakCjkWords falls back to single characters when no dictionary split`() {
        val words = PickResultWordTokenizer.breakCjkWords("北京大学")
        assertEquals("北京大学", words.joinToString(""))
        assertTrue(words.size >= 2)
    }

    @Test
    fun `tokenizeSelectableWords handles mixed chinese and latin`() {
        val tokens = PickResultWordTokenizer.tokenizeSelectableWords("我有10个video")
        assertTrue(tokens.contains("10"))
        assertTrue(tokens.any { it.contains("video", ignoreCase = true) })
        assertEquals("我有10个video", tokens.filterNot {
            PickResultWordTokenizer.isDelimiterToken(it) ||
                PickResultWordTokenizer.isWhitespaceToken(it)
        }.joinToString(""))
    }

    @Test
    fun `splitSelectedTokensToChars splits multi char selections`() {
        val result = PickResultWordTokenizer.splitSelectedTokensToChars(
            tokens = listOf("北京", "大学"),
            selectedIndices = setOf(0),
        )
        requireNotNull(result)
        assertEquals(listOf("北", "京", "大学"), result.tokens)
        assertEquals(setOf(0, 1), result.selectedIndices)
    }

    @Test
    fun `splitTokenAtIndex splits one token for long press`() {
        val result = PickResultWordTokenizer.splitTokenAtIndex(
            tokens = listOf("北", "京", "大学"),
            index = 2,
        )
        requireNotNull(result)
        assertEquals(listOf("北", "京", "大", "学"), result.tokens)
        assertEquals(setOf(2, 3), result.selectedIndices)
    }

    @Test
    fun `splitTokenAtIndex returns null for single char token`() {
        val result = PickResultWordTokenizer.splitTokenAtIndex(
            tokens = listOf("北", "京"),
            index = 0,
        )
        assertEquals(null, result)
    }

    @Test
    fun `mergeSelectionAfterSplitAt preserves other selected tokens`() {
        val merged = PickResultWordTokenizer.mergeSelectionAfterSplitAt(
            splitIndex = 1,
            expandedTokenCount = 2,
            oldSelected = setOf(0, 1, 3),
            splitCharSelected = setOf(1, 2),
        )
        assertEquals(setOf(0, 1, 2, 4), merged)
    }

    @Test
    fun `sequential splitTokenAtIndex preserves prior splits`() {
        val first = PickResultWordTokenizer.splitTokenAtIndex(
            tokens = listOf("北大", "清华"),
            index = 0,
        )
        requireNotNull(first)
        val second = PickResultWordTokenizer.splitTokenAtIndex(
            tokens = first.tokens,
            index = 2,
        )
        requireNotNull(second)
        assertEquals(listOf("北", "大", "清", "华"), second.tokens)
    }

    @Test
    fun `tokenizeSelectableWords keeps a link as one token when merging`() {
        val text = "看这个 https://a.com/x?q=1 吧"
        val tokens = PickResultWordTokenizer.tokenizeSelectableWords(text, mergeLinks = true)
        assertEquals(1, tokens.count { it == "https://a.com/x?q=1" })
        assertEquals(text, tokens.joinToString(""))
    }

    @Test
    fun `tokenizeSelectableWords splits a link at punctuation when merging is off`() {
        val text = "https://a.com"
        val tokens = PickResultWordTokenizer.tokenizeSelectableWords(text, mergeLinks = false)
        assertEquals(listOf("https", ":", "/", "/", "a", ".", "com"), tokens)
    }

    @Test
    fun `tokenizeSelectableWords keeps sentence punctuation outside the merged link`() {
        val text = "官网https://a.com/x。"
        val tokens = PickResultWordTokenizer.tokenizeSelectableWords(text, mergeLinks = true)
        assertTrue(tokens.contains("https://a.com/x"))
        assertTrue(tokens.contains("。"))
        assertEquals(text, tokens.joinToString(""))
    }

    @Test
    fun `tokenizeSelectableWords merges tel and www links too`() {
        val text = "tel:13800138000 和 www.b.com/y"
        val tokens = PickResultWordTokenizer.tokenizeSelectableWords(text, mergeLinks = true)
        assertTrue(tokens.contains("tel:13800138000"))
        assertTrue(tokens.contains("www.b.com/y"))
        assertEquals(text, tokens.joinToString(""))
    }
}
