package com.slideindex.app.ui.quicklauncher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.launcher.QuickLauncherPanelMutator
import com.slideindex.app.ui.ActionIconView
import com.slideindex.app.ui.GlobalIconStore
import com.slideindex.app.ui.miuix.MiuixSliderRow
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import com.slideindex.app.ui.miuix.MiuixInsetCardComponentMargin
import com.slideindex.app.ui.miuix.MiuixTabSettingsCard
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.MiuixIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLauncherPanelManagementSection(
    panels: List<QuickLauncherPanel>,
    selectedIndex: Int,
    defaultColumns: Int,
    defaultRows: Int,
    onPanelsChange: (List<QuickLauncherPanel>) -> Unit,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestPanels by rememberUpdatedState(panels)
    val safeIndex = selectedIndex.coerceIn(0, (panels.size - 1).coerceAtLeast(0))
    val currentPanel = panels.getOrElse(safeIndex) { QuickLauncherPanelDefaults.defaultPanel() }
    var renameTarget by remember { mutableStateOf<QuickLauncherPanel?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<QuickLauncherPanel?>(null) }
    var iconTarget by remember { mutableStateOf<QuickLauncherPanel?>(null) }
    val context = LocalContext.current
    // revision 进 key：弹窗里改完图标要立刻换图（prefs 本身不是 Compose 状态）。
    val panelIconKey = remember(currentPanel.id, GlobalIconStore.revision) {
        GlobalIconStore.panelIcon(context, currentPanel.id)
    }

    fun updatePanel(index: Int, updated: QuickLauncherPanel) {
        val panelId = latestPanels.getOrNull(index)?.id ?: return
        onPanelsChange(QuickLauncherPanelMutator.replacePanel(latestPanels, panelId, updated))
    }

    val renameLabel = stringResource(R.string.quick_launcher_panel_rename)
    LaunchedEffect(renameTarget?.id) {
        renameTarget?.let { renameText = it.name }
    }
    val duplicateLabel = stringResource(R.string.quick_launcher_panel_duplicate)
    val deleteLabel = stringResource(R.string.quick_launcher_panel_delete)
    val customIconLabel = stringResource(R.string.quick_launcher_panel_custom_icon)
    val panelMenuEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = renameLabel,
                onClick = {
                    renameTarget = currentPanel
                    renameText = currentPanel.name
                },
                icon = { modifier ->
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = modifier.size(20.dp),
                    )
                },
            ),
            DropdownItem(
                text = customIconLabel,
                onClick = { iconTarget = currentPanel },
                icon = { modifier ->
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = modifier.size(20.dp),
                    )
                },
            ),
            DropdownItem(
                text = duplicateLabel,
                enabled = panels.size < QuickLauncherPanelDefaults.MAX_PANELS,
                onClick = {
                    QuickLauncherPanelMutator.duplicatePanel(latestPanels, currentPanel.id)
                        ?.let { duplicated ->
                            onPanelsChange(duplicated)
                            onSelectedIndexChange(duplicated.lastIndex)
                        }
                },
                icon = { modifier ->
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = modifier.size(20.dp),
                    )
                },
            ),
            DropdownItem(
                text = deleteLabel,
                enabled = panels.size > 1,
                onClick = { deleteTarget = currentPanel },
                icon = { modifier ->
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = modifier.size(20.dp),
                    )
                },
            ),
        ),
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallTitle(stringResource(R.string.quick_launcher_panels_section))

        val displayName = currentPanel.name.ifBlank {
            stringResource(R.string.quick_launcher_panel_default_name, safeIndex + 1)
        }

        MiuixTabSettingsCard(
            tabs = if (panels.size > 1) {
                panels.mapIndexed { index, panel ->
                    panel.name.ifBlank {
                        stringResource(R.string.quick_launcher_panel_default_name, index + 1)
                    }
                }
            } else {
                null
            },
            selectedTabIndex = safeIndex,
            onTabSelected = onSelectedIndexChange,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (panelIconKey != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { iconTarget = currentPanel },
                        contentAlignment = Alignment.Center,
                    ) {
                        ActionIconView(
                            action = GestureAction.QuickLauncher(currentPanel.id),
                            iconKey = panelIconKey,
                            sizeDp = 24,
                            showBackground = false,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = displayName,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            renameTarget = currentPanel
                            renameText = currentPanel.name
                        }
                        .padding(vertical = 8.dp),
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    enabled = panels.size < QuickLauncherPanelDefaults.MAX_PANELS,
                    onClick = {
                        val added = QuickLauncherPanelMutator.addPanel(
                            panels = latestPanels,
                            defaultColumns = defaultColumns,
                            defaultRows = defaultRows,
                        ) ?: return@IconButton
                        onPanelsChange(added)
                        onSelectedIndexChange(added.lastIndex)
                    },
                ) {
                    Icon(
                        MiuixIcons.Medium.Add,
                        contentDescription = stringResource(R.string.quick_launcher_panel_add),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OverlayIconDropdownMenu(entry = panelMenuEntry) {
                    MiuixIcon(
                        MiuixIcons.Medium.More,
                        contentDescription = renameLabel,
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                }
            }
            MiuixSliderRow(
                title = stringResource(R.string.quick_launcher_grid_columns),
                value = currentPanel.columnsPerPage.toFloat(),
                valueRange = 2f..6f,
                steps = 3,
                enabled = true,
                insideMargin = MiuixInsetCardComponentMargin,
                label = stringResource(R.string.quick_launcher_grid_columns_label, currentPanel.columnsPerPage),
                onValueChange = { updatePanel(safeIndex, currentPanel.copy(columnsPerPage = it.toInt())) },
            )
            MiuixSliderRow(
                title = stringResource(R.string.quick_launcher_grid_rows),
                value = currentPanel.rowsPerPage.toFloat(),
                valueRange = 2f..9f,
                steps = 6,
                enabled = true,
                insideMargin = MiuixInsetCardComponentMargin,
                label = stringResource(R.string.quick_launcher_grid_rows_label, currentPanel.rowsPerPage),
                onValueChange = { updatePanel(safeIndex, currentPanel.copy(rowsPerPage = it.toInt())) },
            )
        }
    }

    val iconTargetPanel = iconTarget
    if (iconTargetPanel != null) {
        val targetIndex = latestPanels.indexOfFirst { it.id == iconTargetPanel.id }
        PanelIconPickerDialog(
            panel = iconTargetPanel,
            displayName = iconTargetPanel.name.ifBlank {
                if (targetIndex >= 0) {
                    stringResource(R.string.quick_launcher_panel_default_name, targetIndex + 1)
                } else {
                    customIconLabel
                }
            },
            initialIconKey = GlobalIconStore.panelIcon(context, iconTargetPanel.id),
            onDismissRequest = { iconTarget = null },
            onIconChange = { key -> GlobalIconStore.setPanelIcon(context, iconTargetPanel.id, key) },
        )
    }

    MiuixFormDialog(
        show = renameTarget != null,
        onDismissRequest = { renameTarget = null },
        title = stringResource(R.string.quick_launcher_panel_rename),
        confirmText = stringResource(R.string.shell_panel_save),
        confirmEnabled = renameText.isNotBlank(),
        onConfirm = {
            val target = renameTarget ?: return@MiuixFormDialog
            val index = latestPanels.indexOfFirst { it.id == target.id }
            if (index >= 0) {
                updatePanel(index, target.copy(name = renameText.trim()))
            }
            renameTarget = null
        },
    ) {
        MiuixLabeledTextField(
            value = renameText,
            onValueChange = { renameText = it },
            label = stringResource(R.string.quick_launcher_panel_rename),
        )
    }

    MiuixConfirmDialog(
        show = deleteTarget != null,
        onDismissRequest = { deleteTarget = null },
        title = stringResource(R.string.quick_launcher_panel_delete),
        message = stringResource(R.string.quick_launcher_panel_delete_confirm),
        confirmText = stringResource(R.string.shell_panel_delete),
        onConfirm = {
            val target = deleteTarget ?: return@MiuixConfirmDialog
            val removed = QuickLauncherPanelMutator.removePanel(latestPanels, target.id)
            if (removed != null) {
                onPanelsChange(removed)
                onSelectedIndexChange(
                    safeIndex.coerceIn(0, (removed.size - 1).coerceAtLeast(0)),
                )
                // 面板没了，它的自定义图标也一并清掉（否则 prefs / 图片目录里留孤儿）。
                GlobalIconStore.setPanelIcon(context, target.id, null)
                com.slideindex.app.ui.ActionIconStorage.deleteForGlobalKey(
                    context,
                    panelIconStorageKey(target.id),
                )
            }
            deleteTarget = null
        },
    )
}
