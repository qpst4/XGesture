package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.miuix.MiuixDialogActionsRow
import com.slideindex.app.ui.miuix.MiuixDialogActionButton
import com.slideindex.app.ui.miuix.MiuixDialogAction
import com.slideindex.app.ui.miuix.MiuixAdaptiveDialog
import com.slideindex.app.R
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.pickerSegmentCount
import com.slideindex.app.ui.pickerSegmentIndex
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun MiuixPickerDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    dismissText: String = stringResource(R.string.cancel),
    showDismissButton: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // show 直接透传：提前 return 会让对话框在关闭瞬间就离开组合，退出动画丢失。
    MiuixAdaptiveDialog(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                content = content,
            )
            if (showDismissButton) {
                Spacer(Modifier.height(16.dp))
                MiuixDialogActionButton(
                    text = dismissText,
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun <T> MiuixRadioSelectDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    options: List<T>,
    selected: T,
    areOptionsEqual: (T, T) -> Boolean = { a, b -> a == b },
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    dismissOnSelect: Boolean = true,
    dismissText: String = stringResource(R.string.cancel),
    showDismissButton: Boolean = true,
    optionLeading: @Composable (T) -> Unit = {},
) {
    MiuixPickerDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        dismissText = dismissText,
        showDismissButton = showDismissButton,
    ) {
        options.forEachIndexed { index, option ->
            Md3PickerListRow(
                segmentIndex = pickerSegmentIndex(index, options.size),
                segmentCount = pickerSegmentCount(options.size),
                title = optionLabel(option),
                selected = areOptionsEqual(option, selected),
                onClick = {
                    onSelect(option)
                    if (dismissOnSelect) {
                        onDismissRequest()
                    }
                },
                leadingContent = { optionLeading(option) },
                trailingMode = PickerTrailingMode.Radio,
            )
        }
    }
}
