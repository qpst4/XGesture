package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.miuix.MiuixDialogActionsRow
import com.slideindex.app.ui.miuix.MiuixDialogAction
import com.slideindex.app.ui.miuix.MiuixAdaptiveDialog
import com.slideindex.app.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 通用取色对话框（Miuix 色轮 + 确认 / 取消）。
 *
 * 与「主题种子色」用的是同一个 [ColorPicker] 组件，只是标题与初始色由调用方给，
 * 因此不限定用途（文字图标 / 动画颜色等都能直接用）。
 * 只负责"选一个 ARGB"，**不保存**：调用方拿到 [onConfirm] 的返回值后自行落盘。
 */
@Composable
fun MiuixColorPickerDialog(
    show: Boolean = true,
    title: String,
    initialColorArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var picked by remember(initialColorArgb) { mutableStateOf(Color(initialColorArgb)) }

    MiuixAdaptiveDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column {
            ColorPicker(
                color = picked,
                onColorChanged = { picked = it },
            )
            Spacer(Modifier.height(20.dp))
            MiuixDialogActionsRow(
                actions = listOf(
                    MiuixDialogAction(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                    ),
                    MiuixDialogAction(
                        text = stringResource(R.string.confirm),
                        onClick = { onConfirm(picked.toArgb()) },
                        primary = true,
                    ),
                ),
            )
        }
    }
}
