package com.slideindex.app.overlay.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 空状态（设计稿 `ui_demo_capsule.html` 的 `.empty` / `emptyHtml()`）：
 * 一句大标题 + 一行说明 + **一个出口**，全空那种再加一个指向把手的箭头。
 *
 * 三种成因各给不同文案与出口（这是 P3b 里唯一缺的那种空状态）：
 * 搜不到 → 「清除搜索」；标签筛不出 → 「看全部」；一条都没有 → 引导长按把手。
 *
 * 位置照设计稿：**顶部对齐**、距列表顶 46dp（不是屏幕垂直居中）。
 */
@Composable
fun HistoryEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    showArrow: Boolean = false,
) {
    val theme = historyTheme()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 46.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            // `.empty .big { font-size: var(--f-sm); font-weight: 650 }`
            style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.sm),
            fontWeight = FontWeight.SemiBold,
            color = theme.text,
            textAlign = TextAlign.Center,
        )
        if (!hint.isNullOrBlank()) {
            Text(
                text = hint,
                // `.empty .hint { font-size: var(--f-meta); color: var(--sub); line-height: 1.8 }`
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = HistoryFontSizes.meta,
                    lineHeight = 20.7.sp,
                ),
                color = theme.sub,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (actionLabel != null && onAction != null) {
            val shape = RoundedCornerShape(HistoryRadii.pill)
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .height(34.dp)
                    .clip(shape)
                    // `.empty .go { border: 1px solid var(--btn-bd); background: var(--btn-bg) }`
                    .background(theme.btnBg)
                    .border(width = 1.dp, color = theme.btnBd, shape = shape)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actionLabel,
                    style = androidx.compose.ui.text.TextStyle(fontSize = HistoryFontSizes.meta),
                    color = theme.text,
                )
            }
        }
        if (showArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = theme.accentSolid,
                modifier = Modifier
                    .padding(top = 30.dp)
                    .size(22.dp),
            )
        }
    }
}
