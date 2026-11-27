package com.slideindex.app.ui.miuix

import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
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
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.glass.GlassTextFieldDefaults

/**
 * 与外部 [value] 双向同步的 Miuix 表单输入框，用于替代 Material [androidx.compose.material3.OutlinedTextField]。
 */
@Composable
fun MiuixLabeledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    insideMargin: DpSize? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    useLabelAsPlaceholder: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
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

    LabeledTextFieldImpl(
        state = state,
        label = label,
        modifier = modifier,
        insideMargin = insideMargin,
        useLabelAsPlaceholder = useLabelAsPlaceholder,
        lineLimits = lineLimits,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
            isError = isError,
        )
}

/**
 * 直接接收 [TextFieldState] 的 Miuix 表单输入框，支持精确光标控制与原子编辑操作。
 */
@Composable
fun MiuixLabeledTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    insideMargin: DpSize? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    useLabelAsPlaceholder: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val lineLimits = if (singleLine) {
        TextFieldLineLimits.SingleLine
    } else {
        TextFieldLineLimits.MultiLine(
            minHeightInLines = minLines.coerceAtLeast(1),
            maxHeightInLines = maxLines.coerceAtLeast(minLines),
        )
    }

    LabeledTextFieldImpl(
        state = state,
        label = label,
        modifier = modifier,
        insideMargin = insideMargin,
        useLabelAsPlaceholder = useLabelAsPlaceholder,
        lineLimits = lineLimits,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
            isError = isError,
        )
}

/**
 * 玻璃浮层内、且**不在卡片里**时自动换成 rc01 的 `GlassTextField`（半透明胶囊输入框）；
 * 其余情况保持 miuix `TextField`。
 *
 * 依据：`miuix docs/components/glass/glasstextfield.md` 与示例 `GlassHalfBottomSheetDemo`
 * 都把它直接放在抽屉 body 上；卡片里的输入框再叠一层玻璃会发灰，所以用 [LocalMiuixInCard] 排除。
 */
@Composable
private fun LabeledTextFieldImpl(
    state: TextFieldState,
    label: String,
    modifier: Modifier,
    insideMargin: DpSize?,
    useLabelAsPlaceholder: Boolean,
    supportingText: String?,
    isError: Boolean,
    lineLimits: TextFieldLineLimits,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
) {
    val useGlass = LocalMiuixGlassSurface.current && !LocalMiuixInCard.current
    Column(modifier = Modifier.fillMaxWidth()) {
    if (useGlass) {
        GlassTextField(
            state = state,
            modifier = modifier.fillMaxWidth(),
            insideMargin = insideMargin ?: GlassTextFieldDefaults.InsideMargin,
            label = label,
            useLabelAsPlaceholder = useLabelAsPlaceholder,
            lineLimits = lineLimits,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
    } else {
        TextField(
            state = state,
            modifier = modifier.fillMaxWidth(),
            insideMargin = insideMargin ?: TextFieldDefaults.InsideMargin,
            label = label,
            useLabelAsPlaceholder = useLabelAsPlaceholder,
            lineLimits = lineLimits,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
    }
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MiuixTheme.textStyles.footnote1,
                color = if (isError) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}