package com.slideindex.app.ui

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.HomeOutline
import top.yukonga.miuix.kmp.icon.glass.NotificationOutline
import top.yukonga.miuix.kmp.icon.glass.Widget
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem

/**
 * Miuix 官方 OS4 玻璃底栏（0.9.5 的 `GlassNavigationBar`）。
 *
 * 与 Mishka 一致：胶囊宽度、指示器、按压/拖拽预览全部由组件内部动画驱动，这里只做
 * 「图标 + 文案 + 重复点击回顶」的项目侧接线。`backdrop` 为 null 或未开启毛玻璃时，
 * 组件退化为纯材质胶囊。
 */
@Composable
fun MiuixOfficialFloatingBottomNavBar(
    selectedIndex: Int,
    onTabSelected: (MainBottomNavDestination) -> Unit,
    onTabReselected: (MainBottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    backdrop: LayerBackdrop? = null,
    blurEnabled: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val destinations = MainBottomNavDestination.entries
    val labels = destinations.map { mainFloatingNavLabel(it) }
    val items = destinations.mapIndexed { index, destination ->
        GlassNavigationItem(
            icon = mainFloatingNavIcon(destination),
            label = labels[index].takeIf { showLabel },
            contentDescription = labels[index],
        )
    }

    GlassNavigationBar(
        items = items,
        selectedIndex = selectedIndex,
        onSelect = { index ->
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            val destination = destinations[index]
            if (index == selectedIndex) {
                onTabReselected(destination)
            } else {
                onTabSelected(destination)
            }
        },
        backdrop = backdrop?.takeIf { blurEnabled },
        // ? Mishka ??:????? 24dp ???,?????????
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    )
}

@Composable
private fun mainFloatingNavIcon(destination: MainBottomNavDestination): ImageVector = when (destination) {
    // 与设置页行首图标同一套 Material Symbols（wght=350）；「晃动」只有自绘线稿，保持原样。
    MainBottomNavDestination.Home -> MiuixIcons.Glass.HomeOutline
    MainBottomNavDestination.Shake -> ImageVector.vectorResource(R.drawable.ic_nav_shake_outlined)
    MainBottomNavDestination.Notification -> MiuixIcons.Glass.NotificationOutline
    MainBottomNavDestination.Extension -> MiuixIcons.Glass.Widget
}

private fun navSymbol(name: String): ImageVector =
    requireNotNull(MaterialSymbolVectors.get(name)) { "unknown Material Symbol: $name" }

@Composable
private fun mainFloatingNavLabel(destination: MainBottomNavDestination): String = when (destination) {
    MainBottomNavDestination.Home -> stringResource(R.string.main_nav_home)
    MainBottomNavDestination.Shake -> stringResource(R.string.main_nav_shake)
    MainBottomNavDestination.Notification -> stringResource(R.string.main_nav_notification)
    MainBottomNavDestination.Extension -> stringResource(R.string.main_nav_extension)
}
