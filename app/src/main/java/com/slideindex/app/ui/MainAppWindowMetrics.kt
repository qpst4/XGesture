package com.slideindex.app.ui

import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.slideindex.app.settings.BottomNavStyle
import com.slideindex.app.ui.miuix.rememberIsWideScreen

val MainNavRailWidth = 80.dp
val MainNavRailIconOnlyWidth = 56.dp

@Composable
fun mainAppWindowAdaptiveInfo(): WindowAdaptiveInfo = currentWindowAdaptiveInfoV2()

/**
 * 宽度 ≥ Medium（600dp）时用侧栏；竖屏 Compact 宽度仍用底栏。
 * 官方 [NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo] 在高度 Compact（手机横屏）时仍会选底栏。
 */
@Composable
fun mainAppPrefersNavigationRail(): Boolean {
    val adaptiveInfo = mainAppWindowAdaptiveInfo()
    return adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    )
}

/** 非 Compact 宽度时限制设置内容最大宽度并居中（用平台 density 量宽，与界面缩放解耦）。 */
@Composable
fun mainAppPrefersWideContentLayout(): Boolean = rememberIsWideScreen()

/**
 * 根级页面的底部内容避让。
 *
 * CLASSIC（及侧栏）用常量：底栏是浮在页面之上的兄弟节点，没人替它量高。
 * LIQUID_GLASS / FLOATING_NAV 走 pager 宿主，底栏是 `Scaffold.bottomBar`，真实高度由
 * Scaffold 量出来回传（`MainTabPagerHost` → `pagerBottomBarClearance`）。这里返回 0，
 * 只作为测量到之前那一帧的兜底；**不要再往这里加手写高度**。
 */
fun mainAppRootBottomContentPadding(
    prefersNavigationRail: Boolean,
    isRootDestination: Boolean,
    bottomNavStyle: BottomNavStyle = BottomNavStyle.CLASSIC,
    showBottomNavLabels: Boolean = true,
): Dp = when {
    !isRootDestination -> 16.dp
    prefersNavigationRail -> MainBottomNavOuterPadding
    bottomNavStyle == BottomNavStyle.LIQUID_GLASS -> 0.dp
    bottomNavStyle == BottomNavStyle.FLOATING_NAV -> 0.dp
    else -> classicBottomNavBarHeight(showBottomNavLabels) + MainBottomNavOuterPadding
}
