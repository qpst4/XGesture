package com.slideindex.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * 经典毛玻璃宽屏侧栏 overlay 时，页面 Scaffold 需在起始侧预留的占位（顶栏/列表布局用）。
 * Miuix [NavigationRail] 分列时由 Row 占位，此值为 0。
 */
val LocalMainNavContentStartInset = compositionLocalOf { 0.dp }

/** 限制 Nav 内容绘制在分配区域内，避免栈底页面在 Rail 切换/横屏布局变化时从左侧露出。 */
fun Modifier.mainNavRailContentClip(): Modifier = clipToBounds()

/** 经典 overlay 侧栏：页面全宽绘制，消费 start cutout，避免列表再垫一截。 */
@Composable
fun Modifier.mainNavClassicOverlayContentInsets(): Modifier {
    val railConsumedInsets = WindowInsets.displayCutout
        .union(WindowInsets.navigationBars)
        .only(WindowInsetsSides.Start)
    return consumeWindowInsets(railConsumedInsets)
}

/** 宽屏 Miuix Rail 右侧内容列：吸收 rail 已处理的 start inset，并补 end 侧 systemBars/cutout。 */
@Composable
fun Modifier.mainNavMiuixRailContentInsets(): Modifier {
    val railConsumedInsets = WindowInsets.displayCutout
        .union(WindowInsets.navigationBars)
        .only(WindowInsetsSides.Start)
    return consumeWindowInsets(railConsumedInsets)
        .windowInsetsPadding(
            WindowInsets.systemBars
                .union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.End),
        )
}
