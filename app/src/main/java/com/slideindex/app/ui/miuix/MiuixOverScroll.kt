package com.slideindex.app.ui.miuix

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 带顶栏材质回写的弹性越界：miuix 0.9.5 要求把 overscroll 位移写到
 * `TopAppBarState.overscrollOffset`，否则短内容页面（滚不动）上滑时顶栏材质/按钮胶囊不会出现。
 *
 * 与 Mishka `AdaptiveTopAppBar.kt:156` 的 `overScrollVertical(scrollBehavior)` 同一做法。
 */
@Composable
fun Modifier.miuixOverScrollVertical(scrollBehavior: ScrollBehavior): Modifier {
    val state = scrollBehavior.state
    val onOffsetChanged: (Float) -> Unit = remember(state) { { state.overscrollOffset = it } }
    return overScrollVertical(onOffsetChanged = onOffsetChanged)
}
