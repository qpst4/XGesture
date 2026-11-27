package com.slideindex.app.ui.miuix

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.glass.GlassPopupSizing

/**
 * 设置行里的**玻璃下拉偏好项**（`GlassDropdownPreference` 的项目侧封装）：
 * 标题 + 说明 + 当前值在行尾，点击展开玻璃下拉面板选值。
 *
 * 背板优先取整页层 [LocalMiuixPageBackdrop]，没有时退回页面列表级
 * [LocalMiuixScreenBackdrop]；两者都为 null（浮层窗口 / 模糊不可用）时，组件内部会退化为
 * 纯色面板，行为仍可用。
 */
@Composable
fun MiuixGlassDropdownPreference(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    startAction: (@Composable () -> Unit)? = null,
    /** 浮层尺寸；null 时按项数自动决定（长列表用更高的面板）。 */
    sizing: GlassPopupSizing? = null,
) {
    val backdrop: LayerBackdrop? = LocalMiuixPageBackdrop.current ?: LocalMiuixScreenBackdrop.current
    val resolvedSizing = sizing ?: rememberAutoGlassPopupSizing(items.size)
    GlassDropdownPreference(
        onSelectedIndexChange = onSelectedIndexChange,
        items = items,
        selectedIndex = selectedIndex,
        title = title,
        backdrop = backdrop,
        modifier = modifier,
        enabled = enabled,
        summary = summary,
        startAction = startAction,
        sizing = resolvedSizing,
    )
}
