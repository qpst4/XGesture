package com.slideindex.app.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun MiuixBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    allowDismiss: Boolean = true,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (!show) return

    WindowBottomSheet(
        show = true,
        modifier = modifier,
        title = title,
        allowDismiss = allowDismiss,
        startAction = startAction,
        endAction = endAction,
        onDismissRequest = onDismissRequest,
        content = content,
    )
}
