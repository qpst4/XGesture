package com.slideindex.app.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * 统一管理“数据驱动”的 MiuixBottomSheet 生命周期。
 *
 * 关闭时只把 show 置为 false，数据保留到退场动画结束，
 * 避免调用方用 if (data != null) 直接把整个 Sheet 从组合中移除。
 */
@Composable
fun <T : Any> MiuixSheetHost(
    value: T?,
    onDismissFinished: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable (T, close: () -> Unit) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var retained by remember { mutableStateOf<T?>(null) }

    LaunchedEffect(value) {
        if (value != null) {
            retained = value
            visible = true
        }
    }

    val data = retained ?: return
    MiuixBottomSheet(
        show = visible,
        modifier = modifier,
        title = title,
        onDismissRequest = { visible = false },
        onDismissFinished = {
            visible = false
            retained = null
            onDismissFinished()
        },
    ) {
        content(data) { visible = false }
    }
}
