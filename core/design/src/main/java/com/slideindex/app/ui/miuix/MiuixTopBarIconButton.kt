package com.slideindex.app.ui.miuix

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import top.yukonga.miuix.kmp.glass.GlassIconButton

/**
 * 顶栏动作键：`GlassIconButton` 版的 `IconButton`。
 *
 * Miuix 0.9.5 的 `GlassTopAppBar` 只提供槽位和 `LocalGlassTopAppBarContext`，**不会**替槽位里的
 * 内容画背景（见 miuix 源码 `GlassTopAppBar.kt` 对 `navigationIcon` 的 KDoc：Use [GlassIconButton]）。
 * Mishka 的做法是把每个动作键都换成 `GlassIconButton`；这里沿用同一套，且参数与 miuix
 * `IconButton` 对齐（`onClick` / `modifier` / `enabled` / `content`），调用点只需换函数名。
 *
 * 胶囊的材质、描边、阴影、尺寸（44dp）全部从玻璃顶栏的上下文继承；页面未滚动时材质
 * alpha 为 0（只剩图标），滚动 / 折叠后才浮现——这是 OS4 顶栏的既定行为。
 *
 * @param enabled 为 false 时内容淡化且不触发 [onClick]（`GlassIconButton` 本身没有 enabled 参数）。
 */
@Composable
fun MiuixTopBarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    GlassIconButton(
        onClick = { if (enabled) onClick() },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.alpha(if (enabled) 1f else DisabledContentAlpha),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/** 与 `IconButtonDefaults` 一致的禁用态内容透明度。 */
private const val DisabledContentAlpha = 0.38f
