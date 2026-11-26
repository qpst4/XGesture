package com.slideindex.app.ui.composite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.ui.miuix.LocalMiuixPageBackdrop
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 「选择组合」弹窗（所有“添加动作”入口共用）。
 *
 * 只列已保存的组合，不提供新建；新建统一在 扩展 → 组合动作库。
 */
@Composable
fun CompositeActionPickDialog(
    onDismissRequest: () -> Unit,
    onPick: (CompositeActionPreset) -> Unit,
) {
    val context = LocalContext.current
    val presets = remember { CompositeActionPresetStore.load(context) }
    val title = stringResource(R.string.gesture_action_composite)

    val body: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (presets.isEmpty()) {
                Text(
                    text = stringResource(R.string.composite_pick_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    presets.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismissRequest()
                                    onPick(preset)
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = preset.name.ifBlank {
                                    stringResource(R.string.composite_library_untitled)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(
                                    R.string.composite_library_action_count,
                                    preset.actions.size,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            top.yukonga.miuix.kmp.basic.TextButton(
                text = stringResource(android.R.string.cancel),
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val backdrop = LocalMiuixPageBackdrop.current
    if (backdrop != null) {
        GlassDialog(
            onDismissRequest = onDismissRequest,
            show = true,
            backdrop = backdrop,
            title = title,
            content = body,
        )
    } else {
        WindowDialog(
            show = true,
            title = title,
            onDismissRequest = onDismissRequest,
        ) {
            body()
        }
    }
}
/**
 * 宿主一行接入：al requestComposite = rememberCompositePickRequester()，
 * 把它当 onRequestComposite 传给动作列表即可；弹窗由本函数自己渲染。
 */
@Composable
fun rememberCompositePickRequester(): ((GestureAction) -> Unit) -> Unit {
    val state = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<((GestureAction) -> Unit)?>(null)
    }
    state.value?.let { deliver ->
        CompositeActionPickDialog(
            onDismissRequest = { state.value = null },
            onPick = { preset ->
                state.value = null
                deliver(GestureAction.CompositeRef(preset.id))
            },
        )
    }
    return { deliver -> state.value = deliver }
}

/** 「打开链接」共享弹窗（所有添加动作入口复用）。 */
@Composable
fun OpenLinkActionDialog(
    initialUrl: String = "",
    initialLabel: String = "",
    onDismissRequest: () -> Unit,
    onConfirm: (url: String, label: String) -> Unit,
) {
    val urlState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialUrl) }
    val labelState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initialLabel) }
    val url = urlState.value
    val label = labelState.value
    val canSave = url.trim().isNotBlank()
    val title = stringResource(R.string.gesture_action_open_link)
    val body: @Composable () -> Unit = {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
            com.slideindex.app.ui.miuix.MiuixLabeledTextField(
                value = url,
                onValueChange = { urlState.value = it },
                label = stringResource(R.string.gesture_open_link_url_field),
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            com.slideindex.app.ui.miuix.MiuixLabeledTextField(
                value = label,
                onValueChange = { labelState.value = it },
                label = stringResource(R.string.gesture_open_link_label_field),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColors(),
                )
                Spacer(modifier = androidx.compose.ui.Modifier.width(20.dp))
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = stringResource(R.string.composite_library_save),
                    onClick = { onConfirm(url.trim(), label.trim()) },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
    val backdrop = LocalMiuixPageBackdrop.current
    if (backdrop != null) {
        GlassDialog(
            onDismissRequest = onDismissRequest,
            show = true,
            backdrop = backdrop,
            title = title,
            content = body,
        )
    } else {
        WindowDialog(
            show = true,
            title = title,
            onDismissRequest = onDismissRequest,
        ) { body() }
    }
}

/** 宿主一行接入：拿到“要配置打开链接”的回调，弹窗由本函数渲染。 */
@Composable
fun rememberOpenLinkRequester(): ((GestureAction) -> Unit) -> Unit {
    val state = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<((GestureAction) -> Unit)?>(null)
    }
    state.value?.let { deliver ->
        OpenLinkActionDialog(
            onDismissRequest = { state.value = null },
            onConfirm = { url, label ->
                state.value = null
                deliver(GestureAction.OpenLink(url = url, label = label))
            },
        )
    }
    return { deliver -> state.value = deliver }
}


/** 「执行 Shell 命令」共享弹窗。 */
@Composable
fun ShellCommandActionDialog(
    initialCommand: String = "",
    onDismissRequest: () -> Unit,
    onConfirm: (command: String) -> Unit,
) {
    val state = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(initialCommand)
    }
    val command = state.value
    val canSave = command.trim().isNotBlank()
    val title = stringResource(R.string.gesture_action_execute_shell_command)
    val body: @Composable () -> Unit = {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
            com.slideindex.app.ui.miuix.MiuixLabeledTextField(
                value = command,
                onValueChange = { state.value = it },
                label = stringResource(R.string.gesture_shell_command_config_title),
                singleLine = false,
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColors(),
                )
                Spacer(modifier = androidx.compose.ui.Modifier.width(20.dp))
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = stringResource(R.string.composite_library_save),
                    onClick = { onConfirm(command.trim()) },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
    val backdrop = LocalMiuixPageBackdrop.current
    if (backdrop != null) {
        GlassDialog(
            onDismissRequest = onDismissRequest,
            show = true,
            backdrop = backdrop,
            title = title,
            content = body,
        )
    } else {
        WindowDialog(
            show = true,
            title = title,
            onDismissRequest = onDismissRequest,
        ) { body() }
    }
}
