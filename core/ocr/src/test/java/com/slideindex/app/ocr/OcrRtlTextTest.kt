package com.slideindex.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PP-OCR 的 RTL 文本是视觉序（字符按从左到右的绘制顺序排），这里验证还原逻辑。
 *
 * 用例里的"视觉序"字符串取自真机取词面板的芯片（issue #54 截图），不是手写构造的。
 */
class OcrRtlTextTest {

    @Test
    fun visualOrderWords_becomeLogicalOrder() {
        // 前三个字符串直接取自真机芯片；「الألوان」芯片截图里末位 alef 看不清，用逻辑序反转构造。
        assertEquals("استخدام", OcrRtlText.toLogicalOrder("مادختسا"))
        assertEquals("لوحة", OcrRtlText.toLogicalOrder("ةحول"))
        assertEquals("نمط", OcrRtlText.toLogicalOrder("طمن"))
        assertEquals("الألوان", OcrRtlText.toLogicalOrder("الألوان".reversed()))
    }

    @Test
    fun wholeVisualSentence_becomeLogical() {
        // 视觉序整行（词序与词内字母都反了）应还原成正序整行。
        val logical = "استخدام نمط لوحة الألوان"
        assertEquals(logical, OcrRtlText.toLogicalOrder(logical.reversed()))
    }

    @Test
    fun ltrText_isUntouched() {
        assertEquals("Hello world", OcrRtlText.toLogicalOrder("Hello world"))
        assertEquals("使用调色板", OcrRtlText.toLogicalOrder("使用调色板"))
        assertEquals("", OcrRtlText.toLogicalOrder(""))
    }

    @Test
    fun isRtlLine_usesFirstStrongCharacter() {
        assertTrue(OcrRtlText.isRtlLine("استخدام"))
        assertTrue(OcrRtlText.isRtlLine(" 123 استخدام"))
        assertFalse(OcrRtlText.isRtlLine("Miui استخدام"))
        assertFalse(OcrRtlText.isRtlLine("123 ，،"))
    }

    @Test
    fun normalizeLines_handlesEachLineSeparately() {
        assertEquals(
            "Hello\nاستخدام",
            OcrRtlText.normalizeLines("Hello\nمادختسا"),
        )
    }
}
