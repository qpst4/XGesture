package com.slideindex.app.ui.composite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.miuix.MiuixGlassSurfaceScope
import com.slideindex.app.ui.miuix.MiuixDialogActionsRow
import com.slideindex.app.ui.miuix.MiuixDialogAction
import com.slideindex.app.R
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.ui.gesturepicker.gestureActionLabel
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.LocalMiuixPageBackdrop
import com.slideindex.app.ui.picker.GestureActionCatalog
import com.slideindex.app.ui.picker.GestureActionCatalogScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.window.WindowDialog

/** 内置模板：新建组合时一键生成步骤。 */
private val COMPOSITE_TEMPLATES: List<Pair<Int, List<GestureAction>>> = listOf(
    R.string.composite_template_freeze_lock to
        listOf(GestureAction.FreezerPanel, GestureAction.LockScreen),
    R.string.composite_template_screenshot_lock to
        listOf(GestureAction.Screenshot, GestureAction.LockScreen),
    R.string.composite_template_home_lock to
        listOf(GestureAction.Home, GestureAction.LockScreen),
)

/**
 * 组合步骤编辑器（库页和动作选择器共用）。
 *
 * 两步：步骤列表（增删/上下移）与添加动作（带搜索的完整动作列表）。
 */
@Composable
internal fun CompositePresetEditorDialog(
    initial: CompositeActionPreset?,
    onDismiss: () -> Unit,
    onSave: (name: String, actions: List<GestureAction>) -> Unit,
) {
    val isNew = initial == null
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var steps by remember { mutableStateOf(initial?.actions.orEmpty()) }
    var picking by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val candidates = remember {
        GestureActionCatalog.build(
            scope = GestureActionCatalogScope.GesturePicker,
            trigger = GestureTriggerType.SHORT_SINGLE_TAP,
            includePointerGestureActions = false,
            includeCornerInnerZoneActions = false,
            catalogPolicy = ActionPickerCatalogPolicy.EdgeGesture,
        ).filter { it.type != GestureActionType.NONE && it !is GestureAction.Composite }
    }
    val filterContext = LocalContext.current
    val visible = remember(query, candidates, filterContext) {
        if (query.isBlank()) {
            candidates
        } else {
            GestureActionCatalog.filter(
                context = filterContext,
                actions = candidates,
                query = query.trim(),
            )
        }
    }

    val dialogTitle = stringResource(
        when {
            picking -> R.string.composite_library_pick_action
            isNew -> R.string.composite_library_new_title
            else -> R.string.composite_library_edit_title
        },
    )
    val body: @Composable () -> Unit = {
        // 弹窗内容区自带左右内边距（与 MiuixFormDialog 的调用方一致）：
        // 这里再叠 padding 会变成 12/16/20 三种缩进混排，视觉上左边参差不齐。
        Column(modifier = Modifier.fillMaxWidth()) {
            if (picking) {
                MiuixLabeledTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.composite_library_search_hint),
                    singleLine = true,
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    items(visible, key = { it.type.id.toString() + ":" + it.payload }) { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    steps = steps + candidate
                                    query = ""
                                    picking = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = gestureActionLabel(candidate),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = stringResource(R.string.composite_library_back),
                        onClick = { picking = false; query = "" },
                    )
                }
            } else {
                MiuixLabeledTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.composite_library_name),
                    singleLine = true,
                    useLabelAsPlaceholder = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.composite_library_actions),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                ) {
                    itemsIndexed(steps, key = { index, action ->
                        index.toString() + ":" + action.type.id + ":" + action.payload
                    }) { index, action ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}. ${gestureActionLabel(action)}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        steps = steps.toMutableList().also {
                                            val item = it.removeAt(index)
                                            it.add(index - 1, item)
                                        }
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                            }
                            IconButton(
                                onClick = {
                                    if (index < steps.lastIndex) {
                                        steps = steps.toMutableList().also {
                                            val item = it.removeAt(index)
                                            it.add(index + 1, item)
                                        }
                                    }
                                },
                                enabled = index < steps.lastIndex,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                            }
                            IconButton(
                                onClick = {
                                    steps = steps.toMutableList().also { it.removeAt(index) }
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    }
                    item(key = "composite-add-step") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { picking = true }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.composite_library_add_step),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                MiuixDialogActionsRow(
                    actions = listOf(
                        MiuixDialogAction(
                            text = stringResource(android.R.string.cancel),
                            onClick = onDismiss,
                        ),
                        MiuixDialogAction(
                            text = stringResource(R.string.composite_library_save),
                            onClick = { onSave(name.trim(), steps) },
                            enabled = name.isNotBlank() && steps.isNotEmpty(),
                            primary = true,
                        ),
                    ),
                )
            }
        }
    }
    val backdrop = LocalMiuixPageBackdrop.current
    if (backdrop != null) {
        GlassDialog(
            onDismissRequest = onDismiss,
            show = true,
            backdrop = backdrop,
            title = dialogTitle,
            content = { MiuixGlassSurfaceScope(body) },
        )
    } else {
        WindowDialog(
            show = true,
            title = dialogTitle,
            onDismissRequest = onDismiss,
        ) {
            body()
        }
    }
}
