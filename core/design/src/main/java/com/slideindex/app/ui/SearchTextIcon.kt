package com.slideindex.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import java.text.BreakIterator
import java.util.Locale

/** 文字图标最多支持的字符数。 */
const val SearchTextIconMaxGraphemes = 4

/**
 * 按 grapheme 截断文字图标，避免 emoji / 组合字符被 UTF-16 长度切坏。
 */
fun searchTextIconGraphemes(text: String, max: Int = SearchTextIconMaxGraphemes): List<String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || max <= 0) return emptyList()
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(trimmed)
    val result = ArrayList<String>(max)
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE && result.size < max) {
        val grapheme = trimmed.substring(start, end)
        if (grapheme.isNotBlank()) result += grapheme
        start = end
        end = iterator.next()
    }
    return result
}

/**
 * 搜索框/引擎格子共用的文字图标：
 *
 * - 1 字：最大
 * - 2 字：次之，单行
 * - 3–4 字：自动两行（2+1 / 2+2）
 */
@Composable
fun SearchTextIcon(
    text: String,
    iconSize: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val graphemes = remember(text) { searchTextIconGraphemes(text) }
    if (graphemes.isEmpty()) return
    val lines = when (graphemes.size) {
        1 -> graphemes[0] to (iconSize.value * 0.62f)
        2 -> graphemes.joinToString("") to (iconSize.value * 0.46f)
        else -> graphemes.chunked(2).joinToString("\n") { it.joinToString("") } to
            (iconSize.value * 0.34f)
    }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = lines.first,
            color = color,
            fontSize = lines.second.sp,
            lineHeight = (lines.second * 1.02f).sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = if (graphemes.size > 2) 2 else 1,
            overflow = TextOverflow.Clip,
            softWrap = graphemes.size > 2,
        )
    }
}
