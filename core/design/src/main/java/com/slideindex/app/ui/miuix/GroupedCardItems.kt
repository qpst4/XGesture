package com.slideindex.app.ui.miuix

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.glass.GlassCardDefaults
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 一段独立 lazy item，靠分角 squircle 背景与相邻段拼成一张视觉连续的 miuix 风格卡片。
 * 对齐 Mishka [CardSegment] / WeKit [groupedCardItem] 布局契约。
 *
 * **玻璃浮层内自动玻璃化**：当 [LocalMiuixGlassSurface] 为 true（即处在 `GlassBottomSheet` /
 * `GlassDialog` 里）时，填充色换成 `GlassCardDefaults.color()` 的半透明色，透出下层玻璃材质；
 * 普通页面上仍是 [MiuixTheme.colorScheme.surfaceContainer] 的不透明底。
 * 见 `miuix docs/components/glass/glasscard.md`。
 *
 * @param glass 强制指定是否按玻璃卡片画；null（默认）跟随所在表面。
 */
@Composable
fun CardSegment(
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    cornerRadius: Dp = CardSegmentCornerRadius,
    topCornerRadius: Dp = Dp.Unspecified,
    bottomCornerRadius: Dp = Dp.Unspecified,
    outerHorizontalPadding: Dp = 12.dp,
    outerTopPadding: Dp = 0.dp,
    outerBottomPadding: Dp = 0.dp,
    insidePadding: PaddingValues = PaddingValues(0.dp),
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = 0.dp,
    selectableGroup: Boolean = false,
    glass: Boolean? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    val isGlass = glass ?: LocalMiuixGlassSurface.current
    val resolvedColor = when {
        color != Color.Unspecified -> color
        isGlass -> GlassCardDefaults.color()
        else -> scheme.surfaceContainer
    }
    val resolvedContentColor = if (contentColor != Color.Unspecified) {
        contentColor
    } else {
        scheme.onSurfaceContainer
    }
    // 玻璃面上圆角跟 `GlassCard` 一致（`GlassCardDefaults.CornerRadius` = 20dp），
    // 普通页面仍是全站统一的 16dp；显式传值优先。
    val baseCornerRadius = if (isGlass && cornerRadius == CardSegmentCornerRadius) {
        GlassCardDefaults.CornerRadius
    } else {
        cornerRadius
    }
    val resolvedTopCorner = when {
        topCornerRadius != Dp.Unspecified -> topCornerRadius
        isFirst -> baseCornerRadius
        else -> 0.dp
    }
    val resolvedBottomCorner = when {
        bottomCornerRadius != Dp.Unspecified -> bottomCornerRadius
        isLast -> baseCornerRadius
        else -> 0.dp
    }
    val background = Modifier.miuixSquircleSurface(
        color = resolvedColor,
        topStart = resolvedTopCorner,
        topEnd = resolvedTopCorner,
        bottomEnd = resolvedBottomCorner,
        bottomStart = resolvedBottomCorner,
    )
    CompositionLocalProvider(
        LocalContentColor provides resolvedContentColor,
        LocalMiuixInCard provides true,
    ) {
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
                .then(
                    if (borderWidth > 0.dp && borderColor != Color.Transparent) {
                        Modifier.border(
                            width = borderWidth,
                            color = borderColor,
                            // 必须用解析后的圆角：Dp.Unspecified 的内部值是 NaN，
                            // 直接塞进 shape 会在 draw 时抛 CornerBasedShape 的非法参数异常
                            // （分发规则页长按进入排序模式、加 2dp 边框时就崩在这里）。
                            shape = RoundedCornerShape(
                                topStart = resolvedTopCorner,
                                topEnd = resolvedTopCorner,
                                bottomEnd = resolvedBottomCorner,
                                bottomStart = resolvedBottomCorner,
                            ),
                        )
                    } else {
                        Modifier
                    },
                )
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
            CardSegment(
                isFirst = index == 0,
                isLast = index == lastIndex,
                // 不在这里兜底成 surfaceContainer：交给 CardSegment 决定是否走玻璃填充。
                color = cardItem.segmentColor ?: Color.Unspecified,
                contentColor = cardItem.segmentContentColor ?: Color.Unspecified,
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
