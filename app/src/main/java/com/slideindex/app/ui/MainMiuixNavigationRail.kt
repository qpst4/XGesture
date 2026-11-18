package com.slideindex.app.ui

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.slideindex.app.R
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassNavigationRail
import top.yukonga.miuix.kmp.glass.GlassNavigationRailDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.HomeOutline
import top.yukonga.miuix.kmp.icon.glass.NotificationOutline
import top.yukonga.miuix.kmp.icon.glass.Widget

/** 宽屏玻璃侧栏展开面板占用的起始空间（面板宽 + 外边距）。 */
val GlassRailContentStartInset = GlassNavigationRailDefaults.ExpandedWidth + GlassNavigationRailDefaults.Margin

/**
 * 宽屏 + Miuix 浮动导航时的**玻璃**侧栏（对齐 Mishka 的 `GlassNavigationRail`）。
 *
 * 形态：浮在内容之上的折叠胶囊，点开后是 250dp 玻璃面板（自带 toggle）；
 * 内容不随展开位移，只用 [GlassRailContentStartInset] 给顶栏/列表预留起始缩进，
 * 避免标题压在折叠胶囊上。层级由 `MainNavHost` 保证：内容在下、侧栏在上、
 * `TopLayerHost` 再在侧栏之上（Mishka 迁移指南 §4.1）。
 */
@Composable
fun MainMiuixNavigationRail(
    selected: MainBottomNavDestination,
    onDestinationSelected: (MainBottomNavDestination) -> Unit,
    backdrop: LayerBackdrop?,
    state: GlassNavigationRailState,
    modifier: Modifier = Modifier,
) {
    val shakeSelectedIcon = ImageVector.vectorResource(R.drawable.ic_nav_shake)
    val shakeUnselectedIcon = ImageVector.vectorResource(R.drawable.ic_nav_shake_outlined)
    val destinations = MainBottomNavDestination.entries

    val items = listOf(
        GlassNavigationItem(
            icon = MiuixIcons.Glass.HomeOutline,
            label = stringResource(R.string.main_nav_home),
            contentDescription = stringResource(R.string.main_nav_home),
        ),
        GlassNavigationItem(
            icon = if (selected == MainBottomNavDestination.Shake) shakeSelectedIcon else shakeUnselectedIcon,
            label = stringResource(R.string.main_nav_shake),
            contentDescription = stringResource(R.string.main_nav_shake),
        ),
        GlassNavigationItem(
            icon = MiuixIcons.Glass.NotificationOutline,
            label = stringResource(R.string.main_nav_notification),
            contentDescription = stringResource(R.string.main_nav_notification),
        ),
        GlassNavigationItem(
            icon = MiuixIcons.Glass.Widget,
            label = stringResource(R.string.main_nav_extension),
            contentDescription = stringResource(R.string.main_nav_extension),
        ),
    )

    GlassNavigationRail(
        items = items,
        selectedIndex = destinations.indexOf(selected).coerceAtLeast(0),
        onSelect = { index -> destinations.getOrNull(index)?.let(onDestinationSelected) },
        backdrop = backdrop,
        modifier = modifier,
        state = state,
    )
}
