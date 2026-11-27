package com.slideindex.app.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassDialog

/**
 * `GlassDialog` 的项目侧封装：唯一区别是把内容区标记成**玻璃表面**
 * （[LocalMiuixGlassSurface] = true），让里面的 `CardSegment` / `MiuixTabSettingsCard` 等
 * 自动换成半透明玻璃卡片。
 *
 * 业务层需要自定义玻璃对话框（不是确认框）时用这个，不要直接调 `GlassDialog`，
 * 否则内容区的卡片仍是不透明底，和玻璃面叠在一起会发灰。
 */
@Composable
fun MiuixGlassDialog(
    onDismissRequest: () -> Unit,
    show: Boolean,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    GlassDialog(
        onDismissRequest = onDismissRequest,
        show = show,
        backdrop = backdrop,
        modifier = modifier,
        title = title,
        content = { MiuixGlassSurfaceScope(content) },
    )
}
