package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 分组圆角卡片行背景：每行独立 LazyColumn item，保持虚拟化。
 *
 * 注意：这是 panels 侧的副本（app 的 `MiuixScaffold.kt` 里也有一份同实现）。
 * 分开命名是为了避免跨模块同 FQN 冲突；面板面板只依赖 `:core:design` 的 squircle 修饰符。
 */
@Composable
fun Modifier.pickerGroupedCardItem(
    index: Int,
    count: Int,
    outerHorizontalPadding: Dp = 12.dp,
    outerTopPadding: Dp = 0.dp,
    outerBottomPadding: Dp = 0.dp,
    color: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.surfaceContainer,
): Modifier {
    val r = CardDefaults.CornerRadius
    val z = 0.dp
    val top = index == 0
    val bottom = index == count - 1
    return fillMaxWidth()
        .padding(
            start = outerHorizontalPadding,
            end = outerHorizontalPadding,
            top = if (top) outerTopPadding else 0.dp,
            bottom = if (bottom) outerBottomPadding else 0.dp,
        )
        .miuixSquircleSurface(
            color = color,
            topStart = if (top) r else z,
            topEnd = if (top) r else z,
            bottomEnd = if (bottom) r else z,
            bottomStart = if (bottom) r else z,
        )
}
