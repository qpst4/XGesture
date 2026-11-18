package com.slideindex.app.ui.miuix

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.blur.layerBackdrop

/**
 * 整页采样宿主：在导航宿主外面铺一层 `LayerBackdrop`，录制**整屏**（正文 + 顶栏 + 底栏）内容，
 * 并通过 [LocalMiuixPageBackdrop] 下发给全屏浮层（玻璃对话框 / 底部弹层 / 下拉菜单）。
 *
 * 约定（见 Mishka 迁移指南 §2.1/§2.2）：
 * - 录制前先画不透明底色（由 `rememberMiuixBlurBackdrop` 完成），否则透明区会采样到黑底；
 * - 消费这层的浮层必须渲染在**本 Box 之外**：下拉菜单走 `TopLayerHost`，
 *   对话框/底部弹层走根部 `Scaffold` 的 popup 层（两者都在本 Box 之外），否则会自采样。
 */
@Composable
fun MiuixPageBackdropBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // ???????(????/??)?????,?????????
    if (LocalMiuixPageBackdrop.current != null) {
        Box(modifier = modifier.fillMaxSize()) {
            content()
        }
        return
    }

    val pageBackdrop = rememberMiuixBlurBackdrop()
    CompositionLocalProvider(LocalMiuixPageBackdrop provides pageBackdrop) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .then(pageBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier),
        ) {
            content()
        }
    }
}
