package com.slideindex.app.clipboardfloat

import androidx.compose.runtime.compositionLocalOf

/**
 * 剪贴板浮窗的「多选」状态。
 *
 * 用 CompositionLocal 下发，避免把选择态塞进 6 层 composable 的参数表。
 */
data class ClipboardFloatSelectionState(
    val active: Boolean,
    val ids: Set<String>,
    val allIds: Set<String>,
    val onToggle: (String) -> Unit,
    val onSelectAll: () -> Unit,
    val onClearAll: () -> Unit,
    val onDeleteSelected: () -> Unit,
)

val LocalClipboardFloatSelection = compositionLocalOf<ClipboardFloatSelectionState?> { null }
