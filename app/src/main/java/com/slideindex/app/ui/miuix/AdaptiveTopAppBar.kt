package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slideindex.app.settings.TopAppBarBlurStyle
import com.slideindex.app.ui.mainAppPrefersWideContentLayout
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统一顶栏封装：默认走 [GlassTopAppBar]，`band` 渐变 + 渐进模糊（`progressiveBlur`）；
 * [backdrop] 为 null（浮层 / 低版本设备）时退化为纯 band 渐变。
 * 导航与动作图标应使用 `GlassIconButton` 才会拿到同一套玻璃胶囊。
 *
 * @param contentUnderBar 正文是否已经滚到顶栏下方（`listState.canScrollBackward`）。
 *   传了就改用 `isContentScrolled` 重载：胶囊材质/按钮阴影/背景带**只由这个布尔值**驱动，
 *   不再随拖拽位移闪进闪出（那是 `scrollBehavior` 偏移驱动的老问题），也不会常驻阴影。
 *   传 null 时退回偏移驱动（没有列表状态的页面用）。
 */
@Composable
fun AdaptiveTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    expandable: Boolean = !mainAppPrefersWideContentLayout(),
    progressiveBlur: Boolean = LocalTopAppBarBlurStyle.current == TopAppBarBlurStyle.PROGRESSIVE,
    scrollBehavior: ScrollBehavior? = null,
    contentUnderBar: Boolean? = null,
    visualContentOffset: (() -> Float)? = null,
    defaultWindowInsetsPadding: Boolean = true,
    titleAlpha: () -> Float = { 1f },
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    val bandBrush = remember(surfaceColor) { GlassTopAppBarDefaults.bandBrush(surfaceColor) }
    // 显式 overlap 状态优先（对齐 miuix 示例 PageUtils / IconPage：用 listState.canScrollBackward）。
    // 程序化滚动（如"跳转动作"页进页面自动定位到当前动作）不会产生 nestedScroll 事件，
    // 只靠 contentOffset 会一直读到 0 → 顶栏永远透明；这里让页面能直接声明"正文已压在栏下"。
    val contentScrolled by remember(scrollBehavior, contentUnderBar) {
        derivedStateOf {
            contentUnderBar ?: (scrollBehavior?.state?.contentOffset?.let { it < 0f } ?: true)
        }
    }

    GlassTopAppBar(
        title = title,
        isContentScrolled = contentScrolled,
        modifier = modifier,
        contentModifier = contentModifier,
        expandable = expandable,
        progressiveBlur = progressiveBlur && backdrop != null,
        backdrop = backdrop,
        bandBrush = bandBrush,
        bandOverhang = GlassTopAppBarDefaults.BandOverhang,
        scrollBehavior = scrollBehavior,
        visualContentOffset = visualContentOffset,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        titleAlpha = titleAlpha,
        navigationIcon = navigationIcon,
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions()
            }
        },
        bottomContent = bottomContent,
    )
}
