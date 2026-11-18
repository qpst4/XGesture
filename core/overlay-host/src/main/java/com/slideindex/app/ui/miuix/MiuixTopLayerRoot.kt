package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * 保证当前 Compose 窗口有一个 [TopLayerHost]。
 *
 * 已经处在宿主里（主界面、浮层面板宿主、上一层 [MiuixTopLayerRoot]）时**原样透传**；
 * 没有宿主时（各类 trampoline Activity 直接 setContent 的场景）在这里补一个，
 * 让里面的玻璃菜单/浮层有地方渲染 —— 否则 `TopLayer` 没有宿主就什么都不画，菜单点了没反应。
 */
@Composable
fun MiuixTopLayerRoot(content: @Composable () -> Unit) {
    val parent = LocalTopLayer.current
    if (parent != null) {
        content()
        return
    }
    val state = remember { TopLayerState() }
    CompositionLocalProvider(LocalTopLayer provides state) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            TopLayerHost(state)
        }
    }
}
