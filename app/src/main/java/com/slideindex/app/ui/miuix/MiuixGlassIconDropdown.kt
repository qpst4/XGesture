package com.slideindex.app.ui.miuix

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupSizing
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「玻璃图标 + 玻璃下拉菜单」的一体封装：锚点、展开状态、根层提升、页面背板全部内聚。
 *
 * 浮层通过 [TopLayer] 提到应用根层渲染，因此能盖住顶栏 / TabRow（`Scaffold` 的 body 永远画在
 * topBar 底下）；背板取 [LocalMiuixScreenBackdrop]，null 时菜单退化为纯色面板。
 *
 * 需要在 `Scaffold`（`MiuixListScaffold` / `MiuixSettingsScreenScaffold` / `MiuixContentScaffold`）
 * 或它们的内容区里调用，才能拿到背板与顶栏材质上下文。
 *
 * @param capsule 触发器是否用玻璃胶囊。**只有顶栏 / 工具栏里的按钮才该为 true**；列表行、卡片
 *   actions 里的菜单保持普通 `IconButton`（只有浮层是玻璃），避免页面里到处是胶囊。
 * @param horizontalAlignment 浮层相对锚点的水平对齐（rc01 新增）。默认 [Alignment.End]：
 *   菜单右边缘贴锚点右边缘，这是顶栏动作键的位置。触发器贴近屏幕左侧时传 [Alignment.Start]，
 *   否则菜单会被推出左边界再被安全边距压回来，看起来像"错位"。
 */
@Composable
fun MiuixGlassIconDropdown(
    entries: List<DropdownEntry>,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MiuixTheme.colorScheme.onSurface,
    capsule: Boolean = true,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
    /** 浮层尺寸；null 时按项数自动决定（长列表用更高的面板）。 */
    sizing: GlassPopupSizing? = null,
) {
    val anchor = rememberGlassPopupAnchor()
    var expanded by remember { mutableStateOf(false) }
    val pageBackdrop = LocalMiuixPageBackdrop.current
    val resolvedSizing = sizing ?: rememberAutoGlassPopupSizing(entries.sumOf { it.items.size })
    val triggerModifier = modifier.glassPopupAnchor(
        anchor = anchor,
        cornerRadius = GlassTopAppBarDefaults.ButtonSize / 2,
    )
    val triggerIconSize = if (capsule) TopBarIconSize else 24.dp

    if (capsule) {
        GlassIconButton(
            onClick = { expanded = true },
            modifier = triggerModifier,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier
                    .size(TopBarIconSize)
                    .glassPopupAnchorContent(anchor),
            )
        }
    } else {
        IconButton(
            onClick = { expanded = true },
            modifier = triggerModifier,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier
                    .size(triggerIconSize)
                    .glassPopupAnchorContent(anchor),
            )
        }
    }
    TopLayer {
        GlassIconDropdownMenu(
            show = expanded,
            onDismissRequest = { expanded = false },
            anchor = anchor,
            // 副本只画图标、不再上报 anchor 位置：反过来写会把 anchor.contentBounds 改成副本自己，
            // 副本目标逐帧跟着自己跑 → 关闭时"飞老远再闪现回位"（Mishka 同样只给 size）。
            anchorContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(triggerIconSize),
                )
            },
            entries = entries,
            backdrop = pageBackdrop,
            horizontalAlignment = horizontalAlignment,
            sizing = resolvedSizing,
        )
    }
}

/** 顶栏 / 工具栏玻璃按钮里的图标尺寸（对齐 Mishka 的 28dp）。 */
private val TopBarIconSize = 28.dp
