package com.slideindex.app.overlay.pickresult

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 词块排版方向的内容级判定：只看首个“强方向”字符，避免中文系统下把阿拉伯语按 LTR 排（issue #54）。
 */
class PickResultTextDirectionTest {

    @Test
    fun arabicText_isRtl() {
        assertTrue(
            PickResultTextDirection.isRtl(
                listOf("استخدام", " ", "نمط", " ", "لوحة", " ", "الألوان"),
            ),
        )
    }

    @Test
    fun chineseText_isLtr() {
        assertFalse(PickResultTextDirection.isRtl(listOf("使用", " ", "调色板")))
    }

    @Test
    fun englishText_isLtr() {
        assertFalse(PickResultTextDirection.isRtl(listOf("Use", " ", "a", " ", "color", " ", "palette")))
    }

    @Test
    fun leadingDigitsAndSpaces_areSkipped() {
        assertTrue(PickResultTextDirection.isRtl(listOf(" ", "123", " ", "النص")))
    }

    @Test
    fun firstStrongCharWins() {
        // 首个强方向字符是拉丁字母 → 整段按 LTR 排，即使后面有阿拉伯语。
        assertFalse(PickResultTextDirection.isRtl(listOf("Miui", " ", "لـ")))
    }

    @Test
    fun emptyOrWeakOnly_isLtr() {
        assertFalse(PickResultTextDirection.isRtl(emptyList()))
        assertFalse(PickResultTextDirection.isRtl(listOf("", " ", "123", "،")))
    }
}
