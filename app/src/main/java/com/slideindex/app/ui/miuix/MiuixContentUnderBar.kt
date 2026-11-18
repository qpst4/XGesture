package com.slideindex.app.ui.miuix

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 「正文是否已经滚到顶栏下面」——miuix 示例用它决定顶栏内玻璃组件（例如
 * `GlassSegmentedTabRow`）的 `surfaceAlpha`：
 *
 * ```kotlin
 * val contentUnderTopBar by remember { derivedStateOf { listState.canScrollBackward } }
 * val tabSurfaceAlpha = if (contentUnderTopBar) 1f else 0f
 * ```
 *
 * 空列表 / 内容不足以滚动时该值为 false，此时栏内玻璃组件应退回纯色（`surfaceAlpha = 0f`），
 * 避免在没有可采样内容时走 shader 出现采样越界伪影。
 *
 * 传的是 [State]，消费方只在需要的地方读 `.value`，不会让整条顶栏跟着滚动重组。
 */
val LocalMiuixContentUnderBar = staticCompositionLocalOf<State<Boolean>> { mutableStateOf(false) }
