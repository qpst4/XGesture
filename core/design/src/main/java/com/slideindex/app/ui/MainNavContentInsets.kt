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
import androidx.compose.ui.unit.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/**
 * 经典毛玻璃宽屏侧栏 overlay 时，页面 Scaffold 需在起始侧预留的占位（顶栏/列表布局用）。
 * Miuix [NavigationRail] 分列时由 Row 占位，此值为 0。
 */
val LocalMainNavContentStartInset = compositionLocalOf<() -> Dp> { { 0.dp } }

/**
 * 在**布局阶段**应用起始缩进：读 [LocalMainNavContentStartInset] 传进来的 lambda，
 * 并同步收窄子节点可用宽度（子节点 `maxWidth` 已扣除缩进，居中/侧边留白不用再减一次）。
 *
 * 这样宽屏侧栏展开/收起时，进度只在 measure 阶段被读取，不会触发重组
 * （Mishka 迁移指南 §6.2 / 其 `LocalTopBarStartInset: () -> Dp` 的写法）。
 */
fun Modifier.mainNavStartInset(inset: () -> Dp): Modifier = layout { measurable, constraints ->
    val start = inset().roundToPx().coerceIn(0, constraints.maxWidth)
    if (start == 0) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    } else {
        // 与 Mishka 的 topBarStartInset 相同：约束加宽后整体右移，不缩窄宽度，
        // 顶栏标题在 morph 期间不会重新折行。
        val placeable = measurable.measure(constraints.offset(horizontal = -start))
        layout(placeable.width + start, placeable.height) {
            placeable.placeRelative(start, 0)
        }
    }
}

/**
 * 宽屏侧栏顶栏标题透明度（0=隐藏，1=常显）。侧栏收起成顶部胶囊时会压住标题，
 * 由宿主按侧栏展开进度下发 lambda（Mishka 迁移指南 §4.2）。
 */
val LocalMainNavTitleAlpha = compositionLocalOf<() -> Float> { { 1f } }

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
