package com.slideindex.app.ocr

import java.text.BreakIterator

/**
 * PP-OCR 对 RTL 文本（阿拉伯语 / 希伯来语）输出的是**视觉顺序**字符串。
 *
 * 也就是说：字符按"从左到右画在纸上"的顺序排，而不是 Unicode 要求的逻辑顺序。
 * 于是一个阿拉伯语词会整体倒过来（استخدام → مادختسا），整行的词序也跟着反转，
 * 用户在取词面板里看到的就是"words backwards"（issue #54）。
 *
 * 这里只做两件纯文本的事：
 * - [isRtlLine]：按 Unicode 双向算法取**首个强方向字符**判定行方向，数字/标点/空白跳过；
 * - [toLogicalOrder]：把视觉序按**字素簇**反转回逻辑序，避免把组合符号（ً َ ّ）从词上拆走。
 */
object OcrRtlText {

    fun isRtlLine(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            when (Character.getDirectionality(codePoint)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
                -> return true

                Character.DIRECTIONALITY_LEFT_TO_RIGHT,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
                -> return false
            }
            index += Character.charCount(codePoint)
        }
        return false
    }

    /** 视觉序 → 逻辑序；非 RTL 行原样返回。 */
    fun toLogicalOrder(text: String): String {
        if (text.isEmpty() || !isRtlLine(text)) return text
        val clusters = splitGraphemeClusters(text)
        if (clusters.size <= 1) return text
        return buildString(text.length) {
            for (index in clusters.indices.reversed()) append(clusters[index])
        }
    }

    /** 多行文本逐行还原（OCR 结果可能带换行）。 */
    fun normalizeLines(text: String): String {
        if (text.isEmpty()) return text
        if ('\n' !in text) return toLogicalOrder(text)
        return text.split('\n').joinToString("\n") { toLogicalOrder(it) }
    }

    /**
     * 按阅读顺序重排 OCR 行。
     *
     * 全是 LTR 行时保持引擎给的顺序不动；只要出现 RTL 行，就按 "上 → 下、右 → 左" 排序
     * （视觉序输出时引擎给的行序也可能是反的），并对每行做逻辑序还原。
     */
    fun orderedForReading(lines: List<OcrRecognizedLine>): List<OcrRecognizedLine> {
        if (lines.isEmpty()) return lines
        if (lines.none { isRtlLine(it.text) }) return lines
        return lines
            .sortedWith(
                compareBy<OcrRecognizedLine> { it.bounds.top }
                    .thenByDescending { it.bounds.right },
            )
            .map { line -> line.copy(text = toLogicalOrder(line.text)) }
    }

    private fun splitGraphemeClusters(text: String): List<String> {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        val clusters = ArrayList<String>(text.length)
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            clusters += text.substring(start, end)
            start = end
            end = iterator.next()
        }
        return clusters
    }
}
