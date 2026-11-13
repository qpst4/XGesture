package com.slideindex.app.ui.miuix

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.IconButtonDefaults
import top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu

/**
 * 图标触发的下拉菜单：有可用页面 backdrop 时用官方 OS4 玻璃菜单，否则回退窗口版。
 *
 * 保持 [WindowIconDropdownMenu] 的自包含语义（点内容即展开、自己管状态），
 * 调用点只需要换组件名。
 *
 * backdrop 必须取 `LocalMiuixScreenBackdrop`（Scaffold 喂给顶栏的那份：记录的是列表内容，
 * 不含弹层自身）。用页面宿主的 backdrop 会让弹层采样到自己，面板直接不绘制。
 */
@Composable
fun MiuixIconDropdownMenu(
    entry: DropdownEntry,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxHeight: Dp? = null,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    collapseOnSelection: Boolean = true,
    backgroundColor: Color = Color.Unspecified,
    cornerRadius: Dp = IconButtonDefaults.CornerRadius,
    minHeight: Dp = IconButtonDefaults.MinHeight,
    minWidth: Dp = IconButtonDefaults.MinWidth,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalMiuixScreenBackdrop.current
    // 玻璃菜单必须放进页面 Scaffold 的 overlay 槽（见 MiuixListScaffold），否则会被顶栏盖住。
    // 逐页迁移完成前一律回退窗口版，保证菜单可用。
    val useGlass = false && backdrop != null && isRuntimeShaderSupported() && enabled

    if (!useGlass) {
        WindowIconDropdownMenu(
            entry = entry,
            modifier = modifier,
            enabled = enabled,
            maxHeight = maxHeight,
            dropdownColors = dropdownColors,
            collapseOnSelection = collapseOnSelection,
            backgroundColor = backgroundColor,
            cornerRadius = cornerRadius,
            minHeight = minHeight,
            minWidth = minWidth,
            content = content,
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val anchor = rememberGlassPopupAnchor()
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .glassPopupAnchor(anchor = anchor, cornerRadius = cornerRadius)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
            ) { expanded = true },
    ) {
        content()

        GlassIconDropdownMenu(
            show = expanded,
            onDismissRequest = { expanded = false },
            anchor = anchor,
            anchorContent = { content() },
            entries = listOf(entry),
            backdrop = backdrop,
            visuals = GlassPopupDefaults.visuals(style = GlassDefaults.style(LocalAppDarkTheme.current)),
            collapseOnSelection = collapseOnSelection,
        )
    }
}