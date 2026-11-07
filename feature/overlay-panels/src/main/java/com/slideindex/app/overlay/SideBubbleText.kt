package com.slideindex.app.overlay

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// 标准档对应当前项目默认字号（微泡泡 font_size_type=1 运行时：tv_name=10sp、tv_content=12sp）。
private val SideBubbleTitleFontSize = 10.sp
private val SideBubbleTitleLineHeight = 11.sp
private val SideBubbleContentFontSize = 12.sp
private val SideBubbleContentLineHeight = 14.sp

private val SideBubbleCompactPlatformStyle = PlatformTextStyle(includeFontPadding = false)

private val BracketCountPattern = Regex("""\[\d+条\]""")

@Composable
fun SideBubbleTitleText(
    text: String,
    color: Color,
    fontSize: TextUnit = SideBubbleTitleFontSize,
    lineHeight: TextUnit = SideBubbleTitleLineHeight
) {
    Text(
        text = text,
        color = color,
        style = TextStyle(
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = FontWeight.Normal,
            platformStyle = SideBubbleCompactPlatformStyle
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun SideBubbleContentText(
    title: String,
    text: String,
    titleColor: Color,
    contentColor: Color,
    maxLines: Int,
    fontSize: TextUnit = SideBubbleContentFontSize,
    lineHeight: TextUnit = SideBubbleContentLineHeight
) {
    val displayText = resolveSideBubbleContent(title, text)
    Text(
        text = buildSideBubbleContentText(displayText, titleColor, contentColor, fontSize),
        style = TextStyle(
            lineHeight = lineHeight,
            platformStyle = SideBubbleCompactPlatformStyle
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

fun resolveSideBubbleContent(title: String, content: String): String {
    val colonIndex = content.indexOf(':')
    if (colonIndex <= 0 || colonIndex >= content.lastIndex) {
        return content.replace(BracketCountPattern, "")
    }

    val senderPart = content.substring(0, colonIndex)
        .replace(BracketCountPattern, "")
        .trim()
    val normalizedTitle = title.trim()
    if (senderPart.isNotEmpty() &&
        (normalizedTitle.equals(senderPart, ignoreCase = true) ||
            normalizedTitle.contains(senderPart, ignoreCase = true))
    ) {
        return content.substring(colonIndex + 1).trimStart()
    }
    return content.replace(BracketCountPattern, "")
}

fun buildSideBubbleContentText(
    text: String,
    titleColor: Color,
    contentColor: Color,
    fontSize: TextUnit = SideBubbleContentFontSize
): AnnotatedString {
    val contentLineStyle = SpanStyle(
        color = titleColor,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold
    )
    val colonIndex = text.indexOf(':')
    if (colonIndex <= 0 || colonIndex >= text.lastIndex) {
        return AnnotatedString(text, contentLineStyle)
    }

    val sender = text.substring(0, colonIndex + 1)
    val body = text.substring(colonIndex + 1).trimStart()
    return buildAnnotatedString {
        withStyle(contentLineStyle) {
            append(sender)
            if (body.isNotEmpty()) {
                append(' ')
                append(body)
            }
        }
    }
}
