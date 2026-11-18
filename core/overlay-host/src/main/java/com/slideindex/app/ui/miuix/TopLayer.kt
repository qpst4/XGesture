package com.slideindex.app.ui.miuix

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 应用/浮层窗口根层之上的全屏覆盖层：让玻璃下拉菜单这类"必须盖住顶栏 / 底栏"的浮层能跨过
 * miuix `Scaffold` 的绘制顺序（Scaffold 先画 body、再画 topBar，content 里的东西永远在顶栏下面）。
 *
 * 宿主分两处：
 * - 主界面：`MainActivity` 的根 `Box` 渲染 [TopLayerHost]；
 * - 浮层窗口：`OverlayFullScreenPanelHost.ensureWindow` 在面板根渲染 [TopLayerHost]。
 *
 * **没有宿主时 [TopLayer] 不渲染、也不占布局**：浮层组件绝不能在没有宿主的窗口里
 * 铺一块全屏 `Box`（那会把调用方的布局撑爆 —— 动作选择网格被撑空就是这么来的）。
 */
@Stable
class TopLayerState {
    internal val entries = mutableStateListOf<TopLayerEntry>()
    private var nextId = 0

    internal fun allocateId(): Int = nextId++
}

internal class TopLayerEntry(
    val id: Int,
    content: @Composable BoxScope.() -> Unit,
) {
    var content by mutableStateOf(content)
}

val LocalTopLayer = staticCompositionLocalOf<TopLayerState?> { null }

/**
 * 把 [content] 提升到 [TopLayerHost] 渲染。
 * 当前窗口没有宿主时**什么都不画**（布局零占用），而不是原地铺满。
 */
@Composable
fun TopLayer(content: @Composable BoxScope.() -> Unit) {
    val layer = LocalTopLayer.current ?: return
    val entry = remember(layer) { TopLayerEntry(layer.allocateId(), content) }
    entry.content = content
    DisposableEffect(layer, entry) {
        layer.entries.add(entry)
        onDispose { layer.entries.remove(entry) }
    }
}

/** 渲染所有 [TopLayer] 内容；应放在页面/窗口内容之后，才能盖住顶栏、底栏与内容。 */
@Composable
fun TopLayerHost(
    state: TopLayerState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        state.entries.forEach { entry ->
            key(entry.id) { entry.content(this) }
        }
    }
}
