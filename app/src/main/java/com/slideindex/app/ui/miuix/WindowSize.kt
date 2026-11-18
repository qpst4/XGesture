package com.slideindex.app.ui.miuix

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.LocalMainNavContentStartInset
import com.slideindex.app.ui.mainNavStartInset
import com.slideindex.app.ui.theme.LocalPlatformDensity

/** 宽屏阈值：与 Material [androidx.window.core.layout.WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND] 一致。 */
private val WideScreenMinWidth: Dp = 600.dp

/** 宽屏阈值：与 Material [androidx.window.core.layout.WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND] 一致。 */

/**
 * 是否宽屏：用缩放前的 [LocalPlatformDensity] 量宽，避免界面缩放与外壳/内容居中判定不一致。
 */
@Composable
fun rememberIsWideScreen(): Boolean {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalPlatformDensity.current ?: LocalDensity.current
    return with(density) { containerSize.width.toDp() >= WideScreenMinWidth }
}

/**
 * 二级页 LazyColumn 根节点：补水平方向 displayCutout ∪ navigationBars inset，
 * 以及宽屏玻璃侧栏的起始缩进（对齐 Mishka：其 `horizontalCutoutPadding` 同样内含 `sidebarStartInset`）。
 * 竖屏 / 侧栏收起时增量为 0。
 */
@Composable
fun Modifier.horizontalCutoutPadding(applySidebarInset: Boolean = true): Modifier = this
    .windowInsetsPadding(
        WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal),
    )
    .then(
        if (applySidebarInset) {
            Modifier.mainNavStartInset(LocalMainNavContentStartInset.current)
        } else {
            Modifier
        },
    )
