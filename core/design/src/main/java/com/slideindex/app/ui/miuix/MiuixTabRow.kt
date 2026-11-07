package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowColors
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** TabRowWithContour 所在容器的底色类型，用于自动选择可见的轮廓配色。 */
enum class MiuixTabRowContourHost {
    /** Miuix Scaffold 等 `surface` 底色页面（App 内默认）。 */
    AppScaffold,
    /** Overlay / 卡片等 `surfaceContainer` 底色容器。 */
    SurfaceContainer,
}

@Composable
fun miuixTabRowContourColors(host: MiuixTabRowContourHost): TabRowColors {
    val isDark = LocalAppDarkTheme.current
    return when (host) {
        MiuixTabRowContourHost.AppScaffold -> TabRowDefaults.tabRowColors(
            backgroundColor = if (isDark) Color(0xFF242426) else Color(0xFFF0F0F0),
            selectedBackgroundColor = if (isDark) Color(0xFF3A3A3C) else Color.White,
            contentColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93),
            selectedContentColor = if (isDark) Color.White else Color(0xFF1C1C1E),
        )
        MiuixTabRowContourHost.SurfaceContainer -> TabRowDefaults.tabRowColors(
            backgroundColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF0F0F0),
            selectedBackgroundColor = if (isDark) Color(0xFF3A3A3C) else Color.White,
            contentColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93),
            selectedContentColor = if (isDark) Color.White else Color(0xFF1C1C1E),
        )
    }
}

private fun Modifier.miuixTabRowPadding(
    contentHorizontalPadding: Dp,
    verticalPadding: Dp,
): Modifier = fillMaxWidth().padding(
    horizontal = contentHorizontalPadding,
    vertical = verticalPadding,
)

@Composable
fun MiuixTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    contentHorizontalPadding: Dp = 0.dp,
    verticalPadding: Dp = 4.dp,
) {
    TabRow(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        listState = listState,
        modifier = modifier.miuixTabRowPadding(contentHorizontalPadding, verticalPadding),
    )
}

/** 带轮廓动效的 Tab 行，适合 Tab + Pager 等场景。 */
@Composable
fun MiuixTabRowWithContour(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    contentHorizontalPadding: Dp = 0.dp,
    verticalPadding: Dp = 4.dp,
    contourHost: MiuixTabRowContourHost = MiuixTabRowContourHost.AppScaffold,
    colors: TabRowColors = miuixTabRowContourColors(contourHost),
) {
    TabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        listState = listState,
        colors = colors,
        modifier = modifier.miuixTabRowPadding(contentHorizontalPadding, verticalPadding),
    )
}

/** Card 内 TabRow：依赖 Card `insideMargin = 16.dp` 提供四边留白，与官方 TabRowSection 一致。 */
@Composable
fun MiuixTabRowWithContourInCard(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    contourHost: MiuixTabRowContourHost = MiuixTabRowContourHost.SurfaceContainer,
    colors: TabRowColors = miuixTabRowContourColors(contourHost),
) {
    MiuixTabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        modifier = modifier,
        listState = listState,
        contentHorizontalPadding = 0.dp,
        verticalPadding = 0.dp,
        contourHost = contourHost,
        colors = colors,
    )
}
