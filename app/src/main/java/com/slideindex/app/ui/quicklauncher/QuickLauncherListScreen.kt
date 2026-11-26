package com.slideindex.app.ui.quicklauncher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.ui.SymbolsIcon
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 快速启动器列表：多面板选择/新建/改名/排序/删除。
 *
 * 和快捷轮盘一样“先选对象、再进编辑器”，不再在配置卡片里用 TabRow 切面板。
 */
@Composable
fun QuickLauncherListScreen(
    panels: List<QuickLauncherPanel>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, Int) -> Unit,
) {
    var renameTarget by remember { mutableStateOf<QuickLauncherPanel?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<QuickLauncherPanel?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsScreenScaffold(
            title = stringResource(R.string.quick_launcher_editor_title),
            subtitle = stringResource(R.string.quick_launcher_list_subtitle),
            onBack = onBack,
        ) {
            groupedCardItems(
                keyPrefix = "quick-launcher-panels",
                items = panels.mapIndexed { index, panel ->
                    settingsCardScopeItem("quick-launcher-panel-${panel.id}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(panel.id) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SymbolsIcon(
                                "Apps",
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            ) {
                                Text(
                                    text = panel.name.ifBlank {
                                        stringResource(
                                            R.string.quick_launcher_panel_default_name,
                                            index + 1,
                                        )
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.quick_launcher_panel_item_count,
                                        panel.items.size,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            PanelRowMenu(
                                canMoveUp = index > 0,
                                canMoveDown = index < panels.lastIndex,
                                canDelete = panels.size > 1,
                                onRename = {
                                    renameTarget = panel
                                    renameText = panel.name
                                },
                                onMoveUp = { onMove(panel.id, -1) },
                                onMoveDown = { onMove(panel.id, 1) },
                                onDelete = { deleteTarget = panel },
                            )
                        }
                    }
                },
            )
        }
        FloatingActionButton(
            onClick = onCreate,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.quick_launcher_panel_add))
        }
    }

    renameTarget?.let { target ->
        WindowDialog(
            show = true,
            title = stringResource(R.string.quick_launcher_panel_rename),
            onDismissRequest = { renameTarget = null },
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                MiuixLabeledTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = stringResource(R.string.composite_library_name),
                    singleLine = true,
                    useLabelAsPlaceholder = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                ) {
                    TextButton(onClick = { renameTarget = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(
                        enabled = renameText.isNotBlank(),
                        onClick = {
                            onRename(target.id, renameText.trim())
                            renameTarget = null
                        },
                    ) {
                        Text(stringResource(R.string.composite_library_save))
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.quick_launcher_panel_delete)) },
            text = {
                Text(
                    stringResource(
                        R.string.quick_launcher_panel_delete_confirm_named,
                        target.name.ifBlank {
                            stringResource(R.string.quick_launcher_panel_default_name, 1)
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target.id)
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.composite_library_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PanelRowMenu(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.quick_launcher_panel_rename)) },
                onClick = { expanded = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.quick_launcher_panel_move_up)) },
                enabled = canMoveUp,
                onClick = { expanded = false; onMoveUp() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.quick_launcher_panel_move_down)) },
                enabled = canMoveDown,
                onClick = { expanded = false; onMoveDown() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.quick_launcher_panel_delete)) },
                enabled = canDelete,
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}
