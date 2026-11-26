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
import androidx.compose.material3.TextButton
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
import com.slideindex.app.R
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.ui.SymbolsIcon
import com.slideindex.app.ui.gesturepicker.gestureActionLabel
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.picker.GestureActionCatalog
import com.slideindex.app.ui.picker.GestureActionCatalogScope
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 组合动作库：命名保存、步骤编辑、删除；动作选择器里可直接引用（内联快照）。
 */
@Composable
fun CompositeActionPresetsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var revision by remember { mutableStateOf(0) }
    val presets = remember(revision) { CompositeActionPresetStore.load(context) }
    var editing by remember { mutableStateOf<CompositeActionPreset?>(null) }
    var creating by remember { mutableStateOf(false) }

    SettingsScreenScaffold(
        title = stringResource(R.string.composite_library_title),
        subtitle = stringResource(R.string.composite_library_subtitle),
        onBack = onBack,
    ) {
        groupedCardItems(
            keyPrefix = "composite-presets",
            items = buildList {
                add(
                    settingsCardScopeItem("composite-add") {
                        SettingNavigationRow(
                            icon = { label -> SymbolsIcon("Bolt", contentDescription = label) },
                            title = stringResource(R.string.composite_library_add),
                            subtitle = "",
                            onClick = { creating = true },
                        )
                    },
                )
            },
        )
        if (presets.isEmpty()) {
            item(key = "composite-empty") {
                Text(
                    text = stringResource(R.string.composite_library_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                )
            }
        } else {
            groupedCardItems(
                keyPrefix = "composite-preset-items",
                items = presets.map { preset ->
                    settingsCardScopeItem("composite-preset-${preset.id}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editing = preset }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name.ifBlank {
                                        stringResource(R.string.composite_library_untitled)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.composite_library_action_count,
                                        preset.actions.size,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    CompositeActionPresetStore.delete(context, preset.id)
                                    revision++
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(
                                        R.string.composite_library_delete,
                                    ),
                                )
                            }
                        }
                    }
                },
            )
        }
    }

    if (creating || editing != null) {
        CompositePresetEditorDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { name, actions ->
                val id = editing?.id ?: CompositeActionPresetStore.newId()
                CompositeActionPresetStore.upsert(
                    context,
                    CompositeActionPreset(id = id, name = name, actions = actions),
                )
                creating = false
                editing = null
                revision++
            },
        )
    }
}

/** 内置模板：新建组合时一键生成步骤。 */
private val COMPOSITE_TEMPLATES: List<Pair<Int, List<GestureAction>>> = listOf(
    R.string.composite_template_freeze_lock to
        listOf(GestureAction.FreezerPanel, GestureAction.LockScreen),
    R.string.composite_template_screenshot_lock to
        listOf(GestureAction.Screenshot, GestureAction.LockScreen),
    R.string.composite_template_home_lock to
        listOf(GestureAction.Home, GestureAction.LockScreen),
)

@Composable
private fun CompositePresetEditorDialog(
    initial: CompositeActionPreset?,
    onDismiss: () -> Unit,
    onSave: (name: String, actions: List<GestureAction>) -> Unit,
) {
    val isNew = initial == null
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var steps by remember { mutableStateOf(initial?.actions.orEmpty()) }
    // false = 步骤列表，true = 添加动作（带搜索的列表）
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

    WindowDialog(
        show = true,
        title = stringResource(
            when {
                picking -> R.string.composite_library_pick_action
                isNew -> R.string.composite_library_new_title
                else -> R.string.composite_library_edit_title
            },
        ),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (picking) {
                MiuixLabeledTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.composite_library_search_hint),
                    singleLine = true,
                    useLabelAsPlaceholder = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
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
                                .padding(horizontal = 20.dp, vertical = 12.dp),
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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { picking = false; query = "" }) {
                        Text(stringResource(R.string.composite_library_back))
                    }
                }
            } else {
                MiuixLabeledTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.composite_library_name),
                    singleLine = true,
                    useLabelAsPlaceholder = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.composite_library_actions),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
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
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}. ${gestureActionLabel(action)}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp, end = 4.dp),
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
                                .padding(horizontal = 20.dp, vertical = 12.dp),
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
                if (isNew && steps.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        COMPOSITE_TEMPLATES.forEach { (titleRes, actions) ->
                            TextButton(onClick = { steps = actions }) {
                                Text(stringResource(titleRes))
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = { onSave(name.trim(), steps) },
                        enabled = name.isNotBlank() && steps.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.composite_library_save))
                    }
                }
            }
        }
    }
}
