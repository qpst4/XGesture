package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults

/**
 * 与外部 [value] 双向同步的 Miuix 表单输入框，用于替代 Material [androidx.compose.material3.OutlinedTextField]。
 */
@Composable
fun MiuixLabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    insideMargin: DpSize = TextFieldDefaults.InsideMargin,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    useLabelAsPlaceholder: Boolean = true,
) {
    val state = rememberTextFieldState(initialText = value)
    // 组合期的 value / onValueChange 不能直接被下面的长驻协程捕获：LaunchedEffect(state)
    // 只随 state 启动一次，闭包里会一直拿着首次组合时的旧值/旧回调。父级一旦重建自己的
    // remember(key) 状态（例如编辑页在异步数据到达后重建 seed），写入就会落到已废弃的
    // MutableState 上，界面上改了、保存下来还是旧值。这里与 MiuixSearchField 保持一致，
    // 用 rememberUpdatedState 读最新值；同时"把文本改回初值（含清空）"也能正确回调。
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    LaunchedEffect(value) {
        val current = state.text.toString()
        if (current != value) {
            state.edit {
                replace(0, length, value)
            }
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .collect { text ->
                if (text != latestValue) {
                    latestOnValueChange(text)
                }
            }
    }

    val lineLimits = if (singleLine) {
        TextFieldLineLimits.SingleLine
    } else {
        TextFieldLineLimits.MultiLine(
            minHeightInLines = minLines.coerceAtLeast(1),
            maxHeightInLines = maxLines.coerceAtLeast(minLines),
        )
    }

    TextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        label = label,
        useLabelAsPlaceholder = useLabelAsPlaceholder,
        lineLimits = lineLimits,
        insideMargin = insideMargin,
    )
}
