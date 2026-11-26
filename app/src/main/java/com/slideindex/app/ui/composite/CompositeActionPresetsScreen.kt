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
    var deleteTarget by remember { mutableStateOf<CompositeActionPreset?>(null) }

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
                                onClick = { deleteTarget = preset },
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

    deleteTarget?.let { target ->
        com.slideindex.app.ui.miuix.MiuixConfirmDialog(
            show = true,
            onDismissRequest = { deleteTarget = null },
            title = stringResource(R.string.composite_library_delete),
            message = stringResource(R.string.composite_library_delete_confirm),
            confirmText = stringResource(R.string.composite_library_delete),
            onConfirm = {
                CompositeActionPresetStore.delete(context, target.id)
                deleteTarget = null
                revision++
            },
            dismissText = stringResource(android.R.string.cancel),
        )
    }
}