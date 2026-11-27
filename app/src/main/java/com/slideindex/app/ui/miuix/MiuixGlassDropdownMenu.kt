package com.slideindex.app.ui.miuix

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDropdownPopup
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassPopupSizing

/**
 * 行/块触发的**玻璃**下拉菜单（替代 miuix `WindowIconDropdownMenu`）。
 *
 * 与旧实现一样只负责「点触发器 → 弹菜单」，但浮层走 [GlassDropdownPopup] + [TopLayer]：
 * - 菜单在应用根层渲染，能盖住顶栏/底栏，且不会自采样；
 * - 采样 [LocalMiuixPageBackdrop]（整页）或页面列表级 [LocalMiuixScreenBackdrop]；
 * - 触发器自身的绘制完全保留，浮层只借它的窗口矩形定位。
 *
 * 浮层窗口（独立的 WindowManager ComposeView）里没有根层宿主与背板，请继续用
 * miuix 的 `WindowIconDropdownMenu`。
 */
@Composable
fun MiuixGlassDropdownMenu(
    entry: DropdownEntry,
    modifier: Modifier = Modifier,
    sizing: GlassPopupSizing? = null,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf(Rect.Zero) }
    val backdrop: LayerBackdrop? = LocalMiuixPageBackdrop.current ?: LocalMiuixScreenBackdrop.current
    val resolvedSizing = sizing ?: rememberAutoGlassPopupSizing(entry.items.size)

    Box(
        modifier = modifier.onGloballyPositioned { anchorBounds = it.boundsInWindow() },
    ) {
        Box(modifier = Modifier.clickable { expanded = true }) {
            content()
        }
    }

    TopLayer {
        GlassDropdownPopup(
            show = expanded,
            onDismissRequest = { expanded = false },
            anchorBounds = anchorBounds,
            backdrop = backdrop,
            sizing = resolvedSizing,
        ) {
            entry.items.forEach { item ->
                GlassPopupItem(
                    text = item.text,
                    onClick = {
                        expanded = false
                        item.onClick?.invoke()
                    },
                    enabled = item.enabled,
                    selected = item.selected,
                    summary = item.summary,
                )
            }
        }
    }
}

/**
 * 外部持有展开状态的版本：供「触发器与菜单分离」的行/行内元素使用
 * （原 material3 `DropdownMenu(expanded = …)` 的调用点）。
 *
 * [anchorBounds] 由调用方在触发节点上 `onGloballyPositioned { it.boundsInWindow() }` 捕获。
 */
@Composable
fun MiuixGlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: Rect,
    items: List<top.yukonga.miuix.kmp.basic.DropdownItem>,
    sizing: GlassPopupSizing? = null,
) {
    val backdrop: LayerBackdrop? = LocalMiuixPageBackdrop.current ?: LocalMiuixScreenBackdrop.current
    val resolvedSizing = sizing ?: rememberAutoGlassPopupSizing(items.size)
    TopLayer {
        GlassDropdownPopup(
            show = expanded,
            onDismissRequest = onDismissRequest,
            anchorBounds = anchorBounds,
            backdrop = backdrop,
            sizing = resolvedSizing,
        ) {
            items.forEach { item ->
                GlassPopupItem(
                    text = item.text,
                    onClick = {
                        onDismissRequest()
                        item.onClick?.invoke()
                    },
                    enabled = item.enabled,
                    selected = item.selected,
                    summary = item.summary,
                )
            }
        }
    }
}
