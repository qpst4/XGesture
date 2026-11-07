package com.slideindex.app.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * 悬浮窗内禁用系统 [TextToolbar] / ActionMode，改由 [OverlaySelectionToolbarPopup] 自绘选区工具条。
 */
private object OverlayNoOpTextToolbar : TextToolbar {
    override var status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun hide() {
        status = TextToolbarStatus.Hidden
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) = Unit

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
        onAutofillRequested: (() -> Unit)?
    ) = Unit
}

@Composable
fun OverlayTextToolbarProvider(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTextToolbar provides remember { OverlayNoOpTextToolbar }
    ) {
        content()
    }
}
