package com.slideindex.app.ui.miuix

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 一段独立 lazy item，靠分角 squircle 背景与相邻段拼成一张视觉连续的 miuix 风格卡片。
 * 对齐 Mishka [CardSegment] / WeKit [groupedCardItem] 布局契约。
 */
@Composable
fun CardSegment(
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surfaceContainer,
    contentColor: Color = MiuixTheme.colorScheme.onSurfaceContainer,
    cornerRadius: Dp = CardSegmentCornerRadius,
    topCornerRadius: Dp = if (isFirst) cornerRadius else 0.dp,
    bottomCornerRadius: Dp = if (isLast) cornerRadius else 0.dp,
    outerHorizontalPadding: Dp = 12.dp,
    outerTopPadding: Dp = 0.dp,
    outerBottomPadding: Dp = 0.dp,
    insidePadding: PaddingValues = PaddingValues(0.dp),
    selectableGroup: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val background = Modifier.miuixSquircleSurface(
        color = color,
        topStart = topCornerRadius,
        topEnd = topCornerRadius,
        bottomEnd = bottomCornerRadius,
        bottomStart = bottomCornerRadius,
    )
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    start = outerHorizontalPadding,
                    end = outerHorizontalPadding,
                    top = outerTopPadding,
                    bottom = outerBottomPadding,
                )
                .then(background)
                .then(if (selectableGroup) Modifier.selectableGroup() else Modifier)
                .padding(insidePadding),
            content = content,
        )
    }
}

/** [groupedCardItems] 的单段定义：稳定 [key] + 段内容。 */
class CardItem(
    val key: String,
    val segmentColor: Color? = null,
    val segmentContentColor: Color? = null,
    val content: @Composable ColumnScope.() -> Unit,
)

private val CardSegmentCornerRadius = 16.dp

/**
 * 把一张卡片的多行拆成独立 lazy item（视觉上仍是一张连续卡片），
 * 替换 `item { Card { row1(); row2(); … } }` 反模式。
 */
fun LazyListScope.groupedCardItems(
    keyPrefix: String,
    items: List<CardItem>,
    outerTopPadding: Dp = 0.dp,
    outerBottomPadding: Dp = 12.dp,
    outerHorizontalPadding: Dp = 12.dp,
    insidePadding: PaddingValues = PaddingValues(0.dp),
    selectableGroup: Boolean = false,
) {
    if (items.isEmpty()) return
    val lastIndex = items.lastIndex
    items.forEachIndexed { index, cardItem ->
        item(key = "$keyPrefix:${cardItem.key}") {
            val scheme = MiuixTheme.colorScheme
            CardSegment(
                isFirst = index == 0,
                isLast = index == lastIndex,
                color = cardItem.segmentColor ?: scheme.surfaceContainer,
                contentColor = cardItem.segmentContentColor ?: scheme.onSurfaceContainer,
                outerHorizontalPadding = outerHorizontalPadding,
                outerTopPadding = if (index == 0) outerTopPadding else 0.dp,
                outerBottomPadding = if (index == lastIndex) outerBottomPadding else 0.dp,
                insidePadding = insidePadding,
                selectableGroup = selectableGroup,
                content = cardItem.content,
            )
        }
    }
}
