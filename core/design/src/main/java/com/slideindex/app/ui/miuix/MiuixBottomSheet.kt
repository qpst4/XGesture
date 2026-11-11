package com.slideindex.app.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
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
    /** 内容区四边留白；null 用 miuix 默认。sheet 内放带自身外边距的行（如 settingsCardItems）时传小值避免双层缩进。 */
    insideMargin: DpSize? = null,
    content: @Composable () -> Unit,
) {
    WindowBottomSheet(
        show = show,
        modifier = modifier,
        title = title,
        allowDismiss = allowDismiss,
        startAction = startAction,
        endAction = endAction,
        onDismissRequest = onDismissRequest,
        insideMargin = insideMargin ?: BottomSheetDefaults.insideMargin,
        content = content,
    )
}
