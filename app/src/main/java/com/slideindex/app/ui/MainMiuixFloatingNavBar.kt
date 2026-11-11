package com.slideindex.app.ui

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Miuix 官方浮动底栏内容区留白（栏高 + 底部悬浮间距）。 */
val MainFloatingNavBarHeight = 52.dp
val MainFloatingNavBarBottomOffset = 26.dp
val MainFloatingNavBarContentClearance = MainFloatingNavBarHeight + MainFloatingNavBarBottomOffset

/** 悬浮栏胶囊圆角（对齐 Mishka）。 */
private val MainFloatingNavCornerRadius = 50.dp

/** 与 [top.yukonga.miuix.kmp.basic.FloatingNavigationBarDefaults] 一致。 */
private const val FloatingNavUnselectedAlpha = 0.4f
private const val FloatingNavSelectedPressedAlpha = 0.5f
private const val FloatingNavUnselectedPressedAlpha = 0.6f

@Composable
fun MiuixOfficialFloatingBottomNavBar(
    selectedIndex: Int,
    onTabSelected: (MainBottomNavDestination) -> Unit,
    onTabReselected: (MainBottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    backdrop: LayerBackdrop? = null,
    blurEnabled: Boolean = false,
    blurRadiusDp: Float = 25f,
) {
    val haptic = LocalHapticFeedback.current
    val destinations = MainBottomNavDestination.entries
    val isDark = LocalAppDarkTheme.current
    val shape = RoundedCornerShape(MainFloatingNavCornerRadius)
    // 毛玻璃降级：API < 33 时 rememberMiuixBlurBackdrop 返回 null，退回纯色 surfaceContainer。
    val blurActive = blurEnabled && backdrop != null

    val blurModifier = if (blurActive) {
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadiusDp,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(
                        color = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                    ),
                ),
            ),
            highlight = if (isDark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight,
        )
    } else {
        Modifier
    }

    FloatingNavigationBar(
        modifier = modifier.then(blurModifier),
        color = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
        cornerRadius = MainFloatingNavCornerRadius,
        defaultWindowInsetsPadding = true,
    ) {
        destinations.forEachIndexed { index, destination ->
            val selected = index == selectedIndex
            MiuixFloatingNavItem(
                selected = selected,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    if (selected) {
                        onTabReselected(destination)
                    } else {
                        onTabSelected(destination)
                    }
                },
                icon = mainFloatingNavIcon(destination),
                label = mainFloatingNavLabel(destination),
                showLabel = showLabel,
            )
        }
    }
}

/** 悬浮栏 Item：支持「图标+文字 / 仅图标」两种模式（对齐 Mishka）。 */
@Composable
private fun MiuixFloatingNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    showLabel: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseColor = MiuixTheme.colorScheme.onSurfaceContainer
    val tint = when {
        isPressed -> baseColor.copy(
            alpha = if (selected) FloatingNavSelectedPressedAlpha else FloatingNavUnselectedPressedAlpha,
        )
        selected -> baseColor
        else -> baseColor.copy(alpha = FloatingNavUnselectedAlpha)
    }

    Column(
        modifier = Modifier
            .defaultMinSize(
                minWidth = if (showLabel) 56.dp else 48.dp,
                minHeight = 48.dp,
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .padding(horizontal = if (showLabel) 8.dp else 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            modifier = Modifier.size(22.dp),
            imageVector = icon,
            contentDescription = if (showLabel) null else label,
            tint = tint,
        )
        if (showLabel) {
            Text(
                text = label,
                color = tint,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun mainFloatingNavIcon(destination: MainBottomNavDestination): ImageVector = when (destination) {
    // 与设置页行首图标同一套 Material Symbols（wght=350）；「晃动」只有自绘线稿，保持原样。
    MainBottomNavDestination.Home -> navSymbol("Home")
    MainBottomNavDestination.Shake -> ImageVector.vectorResource(R.drawable.ic_nav_shake_outlined)
    MainBottomNavDestination.Notification -> navSymbol("Notifications")
    MainBottomNavDestination.Extension -> navSymbol("Widgets")
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
