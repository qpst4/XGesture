package com.slideindex.app.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.slideindex.app.ui.miuix.LocalMiuixScreenBackdrop
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

/**
 * 设置行的下拉：有页面 backdrop 时用官方玻璃版 [GlassDropdownPreference]，
 * 否则回退 [OverlayDropdownPreference]。
 *
 * 两者都是"行内自包含"组件（自己管弹层与位置），所以调用点只需换名，
 * 不需要像图标下拉那样搬 anchor。
 */
@Composable
fun MiuixDropdownPreference(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    summary: String? = null,
    showValue: Boolean = true,
    startAction: @Composable (() -> Unit)? = null,
    maxHeight: Dp? = null,
    popupInRootScaffold: Boolean = true,
) {
    if (isRuntimeShaderSupported()) {
        GlassDropdownPreference(
            onSelectedIndexChange = onSelectedIndexChange,
            items = items,
            selectedIndex = selectedIndex,
            title = title,
            backdrop = LocalMiuixScreenBackdrop.current,
            modifier = modifier,
            enabled = enabled,
            showValue = showValue,
            renderInRootScaffold = popupInRootScaffold,
            summary = summary,
            startAction = startAction,
        )
    } else {
        OverlayDropdownPreference(
            items = items,
            selectedIndex = selectedIndex,
            title = title,
            modifier = modifier,
            enabled = enabled,
            summary = summary,
            showValue = showValue,
            startAction = startAction,
            maxHeight = maxHeight,
            renderInRootScaffold = popupInRootScaffold,
            onSelectedIndexChange = onSelectedIndexChange,
        )
    }
}
