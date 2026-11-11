package com.slideindex.app.ui

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.slideindex.app.R
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState

/**
 * 宽屏 + 液态玻璃 / Miuix 浮动导航时的侧栏（[NavigationRail] + [NavigationRailItem]）。
 * 经典毛玻璃宽屏仍用 [FloatingSideNavRail]。
 */
@Composable
fun MainMiuixNavigationRail(
    selected: MainBottomNavDestination,
    onDestinationSelected: (MainBottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shakeSelectedIcon = ImageVector.vectorResource(R.drawable.ic_nav_shake)
    val shakeUnselectedIcon = ImageVector.vectorResource(R.drawable.ic_nav_shake_outlined)
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        // 具名传 state 走可展开重载：本栏不提供展开/收起入口，仅用于保持既有布局。
        // 固定紧凑重载（无 state）在 0.9.4 会给选中项加背板，属于视觉变更，需另行评估。
        state = rememberNavigationRailState(),
    ) {
        NavigationRailItem(
            selected = selected == MainBottomNavDestination.Home,
            onClick = { onDestinationSelected(MainBottomNavDestination.Home) },
            icon = navSymbol("Home"),
            label = stringResource(R.string.main_nav_home),
        )
        NavigationRailItem(
            selected = selected == MainBottomNavDestination.Shake,
            onClick = { onDestinationSelected(MainBottomNavDestination.Shake) },
            icon = if (selected == MainBottomNavDestination.Shake) shakeSelectedIcon else shakeUnselectedIcon,
            label = stringResource(R.string.main_nav_shake),
        )
        NavigationRailItem(
            selected = selected == MainBottomNavDestination.Notification,
            onClick = { onDestinationSelected(MainBottomNavDestination.Notification) },
            icon = navSymbol("Notifications"),
            label = stringResource(R.string.main_nav_notification),
        )
        NavigationRailItem(
            selected = selected == MainBottomNavDestination.Extension,
            onClick = { onDestinationSelected(MainBottomNavDestination.Extension) },
            icon = navSymbol("Widgets"),
            label = stringResource(R.string.main_nav_extension),
        )
    }
}

private fun navSymbol(name: String): ImageVector =
    requireNotNull(MaterialSymbolVectors.get(name)) { "unknown Material Symbol: $name" }
