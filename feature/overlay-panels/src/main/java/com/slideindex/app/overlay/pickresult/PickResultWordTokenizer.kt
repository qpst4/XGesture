package com.slideindex.app.overlay.pickresult

/**
 * FV [com.fooview.android.fooview.ocr.ocrresult.WordSelectTextView#K] inspired tokenizer.
 * CJK segments use the system ICU word-boundary breaker.
 */
object PickResultWordTokenizer {
    private val DELIMITERS = charArrayOf(
        '!', '"', '#', '$', '%', '&', '\'',
        '(', ')', '*', '+', ',', '-', '_', '.', '/', ':', ';', '<',
        '=', '>', '?', '@', '[', ']', '\\', '^', '`', '{', '|', '}', '~',
        '。', '，', '、', '；', '：', '？', '！', '「', '」', '『', '』',
        '（', '）', '【', '】', '《', '》', '…', '·', '—', '－',
    )
    private val HYPHEN_KEEP = charArrayOf('-', '\'')

    fun tokenize(source: String?): List<String> {
        if (source.isNullOrEmpty()) return emptyList()
        val result = mutableListOf<String>()
        val buffer = StringBuilder()
        source.forEachIndexed { index, char ->
            val previous = source.getOrElse(index - 1) { ' ' }
            val next = source.getOrElse(index + 1) { ' ' }
            if (isHardDelimiter(char) && !shouldKeepWithinWord(char, previous, next)) {
                if (buffer.isNotEmpty()) {
                    result += buffer.toString()
                    buffer.clear()
                }
                result += char.toString()
            } else if (char.isWhitespace()) {
                if (buffer.isNotEmpty()) {
                    result += buffer.toString()
                    buffer.clear()
                }
            } else {
                buffer.append(char)
            }
        }
        if (buffer.isNotEmpty()) {
            result += buffer.toString()
        }
        return result
    }

    /**
     * Tap-select tokens: CJK uses locale word boundaries; Latin accumulates into words;
     * whitespace is preserved one character per token for blank-chip rendering.
     */
    fun tokenizeSelectableWords(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val tokens = mutableListOf<String>()
        val latinBuffer = StringBuilder()
        var index = 0

        fun flushLatin() {
            if (latinBuffer.isEmpty()) return
            tokens += latinBuffer.toString()
            latinBuffer.clear()
        }

        while (index < text.length) {
            val char = text[index]
            when {
                char.isWhitespace() || char == '\u3000' -> {
                    flushLatin()
                    tokens += char.toString()
                    index++
                }
                isHardDelimiter(char) -> {
                    flushLatin()
                    tokens += char.toString()
                    index++
                }
                isCjk(char) -> {
                    flushLatin()
                    val start = index
                    while (index < text.length && isCjk(text[index])) {
                        index++
                    }
                    tokens += breakCjkWords(text.substring(start, index))
                }
                else -> {
                    latinBuffer.append(char)
                    index++
                }
            }
        }
        flushLatin()
        return tokens
    }

    /**
     * 不分词模式：每个 code point 一个 token（代理对不拆），空白/换行也各占一个 token。
     * 选择逻辑沿用现有的 token 下标，因此点按、拖选、自动全选都不用重写。
     */
    fun tokenizeSelectableChars(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val tokens = ArrayList<String>(text.length)
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            val next = offset + Character.charCount(codePoint)
            tokens += text.substring(offset, next)
            offset = next
        }
        return tokens
    }

    fun splitSelectedTokensToChars(
        tokens: List<String>,
        selectedIndices: Set<Int>,
    ): SplitSelectedResult? {
        if (selectedIndices.isEmpty()) return null
        var changed = false
        val result = ArrayList<String>(tokens.size)
        val newSelected = linkedSetOf<Int>()
        tokens.forEachIndexed { index, token ->
            val selected = selectedIndices.contains(index)
            if (!selected || isDelimiterToken(token) || isWhitespaceToken(token) || token.length <= 1) {
                val resultIndex = result.size
                result += token
                if (selected) {
                    newSelected += resultIndex
                }
                return@forEachIndexed
            }
            changed = true
            var offset = 0
            while (offset < token.length) {
                val codePoint = token.codePointAt(offset)
                val next = offset + Character.charCount(codePoint)
                result += token.substring(offset, next)
                newSelected += result.lastIndex
                offset = next
            }
        }
        return if (changed) SplitSelectedResult(result, newSelected) else null
    }

    /** Long-press split: explode one multi-char token at [index] into single-character chips. */
    fun splitTokenAtIndex(tokens: List<String>, index: Int): SplitSelectedResult? {
        if (index !in tokens.indices) return null
        return splitSelectedTokensToChars(tokens, setOf(index))
    }

    /**
     * Keep selections on other tokens after splitting [splitIndex]; [splitCharSelected]
     * are indices of the newly split character chips.
     */
    fun mergeSelectionAfterSplitAt(
        splitIndex: Int,
        expandedTokenCount: Int,
        oldSelected: Set<Int>,
        splitCharSelected: Set<Int>,
    ): Set<Int> {
        if (expandedTokenCount <= 0) return splitCharSelected
        val merged = linkedSetOf<Int>()
        merged += splitCharSelected
        val indexShift = expandedTokenCount - 1
        oldSelected.forEach { oldIdx ->
            if (oldIdx == splitIndex) return@forEach
            val newIdx = if (oldIdx < splitIndex) oldIdx else oldIdx + indexShift
            merged += newIdx
        }
        return merged
    }

    data class SplitSelectedResult(
        val tokens: List<String>,
        val selectedIndices: Set<Int>,
    )

    fun isDelimiterToken(token: String): Boolean {
        return token.length == 1 && isHardDelimiter(token[0])
    }

    fun isWhitespaceToken(token: String): Boolean {
        return token.length == 1 && (token[0].isWhitespace() || token[0] == '\u3000')
    }

    fun breakCjkWords(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val words = runCatching { breakWordsWithAndroidIcu(text) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: breakWordsWithJavaLocale(text)
        if (words.isEmpty()) return listOf(text)
        if (words.size == 1 && words.single().length > 1) {
            return text.map(Char::toString)
        }
        return words
    }

    private fun breakWordsWithAndroidIcu(text: String): List<String> {
        val iterator = android.icu.text.BreakIterator.getWordInstance(android.icu.util.ULocale.CHINESE)
        return collectBreakIteratorWords(text, iterator)
    }

    private fun breakWordsWithJavaLocale(text: String): List<String> {
        val iterator = java.text.BreakIterator.getWordInstance(java.util.Locale.CHINESE)
        return collectBreakIteratorWords(text, iterator)
    }

    private fun collectBreakIteratorWords(text: String, iterator: java.text.BreakIterator): List<String> {
        iterator.setText(text)
        val words = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != java.text.BreakIterator.DONE) {
            val word = text.substring(start, end).trim()
            if (word.isNotEmpty()) {
                words += word
            }
            start = end
            end = iterator.next()
        }
        return words
    }

    private fun collectBreakIteratorWords(
        text: String,
        iterator: android.icu.text.BreakIterator,
    ): List<String> {
        iterator.setText(text)
        val words = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != android.icu.text.BreakIterator.DONE) {
            val word = text.substring(start, end).trim()
            if (word.isNotEmpty()) {
                words += word
            }
            start = end
            end = iterator.next()
        }
        return words
    }

    private fun isHardDelimiter(char: Char): Boolean = DELIMITERS.any { it == char }

    private fun shouldKeepWithinWord(char: Char, previous: Char, next: Char): Boolean {
        if (!HYPHEN_KEEP.any { it == char }) return false
        if (previous.isWhitespace() || next.isWhitespace()) return false
        if (isHardDelimiter(previous) || isHardDelimiter(next)) return false
        return true
    }

    private fun isCjk(char: Char): Boolean {
        val code = char.code
        return code in 0x4E00..0x9FFF ||
            code in 0x3400..0x4DBF ||
            code in 0x20000..0x2A6DF ||
            code in 0x3040..0x30FF ||
            code in 0x31F0..0x31FF ||
            code in 0xAC00..0xD7AF ||
            code in 0xFF66..0xFF9D
    }
}

enum class PickResultTextMode {
    SELECT,
    WORD_TAP,
    EDIT,
}
