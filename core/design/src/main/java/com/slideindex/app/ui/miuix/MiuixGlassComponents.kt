package com.slideindex.app.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton

/** 当前顶栏是否为玻璃样式；[MiuixTopBarIconButton] 据此切换图标按钮的材质。 */
val LocalMiuixGlassTopBar = staticCompositionLocalOf { false }

/**
 * 当前页面的 backdrop（用于弹层玻璃化）。
 *
 * 由 Scaffold 注入；悬浮窗、无模糊页面为 null，此时玻璃组件自动回退为实色。
 */
val LocalMiuixScreenBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 页面级 backdrop 持有者：由主题注入，Scaffold 写入，弹层读取。
 *
 * 之所以不用 [LocalMiuixScreenBackdrop]：弹层的调用点在 Scaffold **之外**（同一页面的兄弟节点），
 * CompositionLocal 只沿子树下发，兄弟拿不到。这里用一个组合作用域内的可变持有者，
 * 让"页面创建、Scaffold 写入、弹层读取"这条链在同一组合里成立。
 * 每个窗口/Activity 各自 remember 一份，不跨窗口共享。
 */
@Stable
class PageBackdropHolder {
    var backdrop: LayerBackdrop? by mutableStateOf(null)
    var isPageLevel: Boolean by mutableStateOf(false)
}

val LocalPageBackdropHolder = staticCompositionLocalOf<PageBackdropHolder?> { null }

@Composable
fun rememberPageBackdropHolder(): PageBackdropHolder = remember { PageBackdropHolder() }

/** 弹层当前可用的 backdrop：优先页面持有者，其次 Scaffold 提供的 CompositionLocal。 */
@Composable
fun currentPageBackdrop(): LayerBackdrop? =
    LocalPageBackdropHolder.current?.backdrop ?: LocalMiuixScreenBackdrop.current


/** 顶栏图标按钮：玻璃栏里是玻璃胶囊，普通栏里退回 Miuix `IconButton`。 */
@Composable
fun MiuixTopBarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (LocalMiuixGlassTopBar.current) {
        GlassIconButton(
            onClick = { if (enabled) onClick() },
            modifier = if (enabled) modifier else modifier.alpha(0.4f),
        ) { content() }
    } else {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled) { content() }
    }
}
