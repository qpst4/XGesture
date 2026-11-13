package com.slideindex.app.ui.widgetpanel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixTabSettingsCard
import com.slideindex.app.ui.miuix.MiuixIconDropdownMenu
import com.slideindex.app.widget.WidgetPanelDefaults
import com.slideindex.app.widget.WidgetPanelMutator
import com.slideindex.app.widget.WidgetPanelPage
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.MiuixIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPanelPageManagementSection(
    pages: List<WidgetPanelPage>,
    selectedIndex: Int,
    onPagesChange: (List<WidgetPanelPage>) -> Unit,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestPages by rememberUpdatedState(pages)
    val safeIndex = selectedIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val currentPage = pages.getOrElse(safeIndex) { WidgetPanelDefaults.defaultPage }
    var renameTarget by remember { mutableStateOf<WidgetPanelPage?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<WidgetPanelPage?>(null) }

    fun updatePage(index: Int, updated: WidgetPanelPage) {
        if (index !in latestPages.indices) return
        onPagesChange(WidgetPanelMutator.replacePage(latestPages, index, updated))
    }

    val renameLabel = stringResource(R.string.quick_launcher_panel_rename)
    LaunchedEffect(renameTarget?.id) {
        renameTarget?.let { renameText = it.name }
    }
    val deleteLabel = stringResource(R.string.quick_launcher_panel_delete)
    val panelMenuEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = renameLabel,
                onClick = {
                    renameTarget = currentPage
                    renameText = currentPage.name
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
                text = deleteLabel,
                enabled = pages.size > 1,
                onClick = { deleteTarget = currentPage },
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
        SmallTitle(stringResource(R.string.widget_panel_grid_section))

        val displayName = currentPage.name.ifBlank {
            stringResource(R.string.quick_launcher_panel_default_name, safeIndex + 1)
        }

        MiuixTabSettingsCard(
            tabs = if (pages.size > 1) {
                pages.mapIndexed { index, page ->
                    page.name.ifBlank {
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
                Text(
                    text = displayName,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            renameTarget = currentPage
                            renameText = currentPage.name
                        }
                        .padding(vertical = 8.dp),
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    enabled = pages.size < QuickLauncherPanelDefaults.MAX_PANELS,
                    onClick = {
                        val added = WidgetPanelMutator.addPage(latestPages)
                        onPagesChange(added)
                        onSelectedIndexChange(added.lastIndex)
                    },
                ) {
                    Icon(
                        MiuixIcons.Medium.Add,
                        contentDescription = stringResource(R.string.widget_panel_add_page),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MiuixIconDropdownMenu(entry = panelMenuEntry) {
                    MiuixIcon(
                        MiuixIcons.Medium.More,
                        contentDescription = renameLabel,
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }

    MiuixFormDialog(
        show = renameTarget != null,
        onDismissRequest = { renameTarget = null },
        title = stringResource(R.string.quick_launcher_panel_rename),
        confirmText = stringResource(R.string.shell_panel_save),
        confirmEnabled = renameText.isNotBlank(),
        onConfirm = {
            val target = renameTarget ?: return@MiuixFormDialog
            val index = latestPages.indexOfFirst { it.id == target.id }
            if (index >= 0) {
                updatePage(index, target.copy(name = renameText.trim()))
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
        title = stringResource(R.string.widget_panel_remove_page),
        message = stringResource(
            R.string.widget_panel_remove_page_confirm,
            safeIndex + 1,
        ),
        confirmText = stringResource(R.string.shell_panel_delete),
        onConfirm = {
            val target = deleteTarget ?: return@MiuixConfirmDialog
            val index = latestPages.indexOfFirst { it.id == target.id }
            if (index >= 0) {
                WidgetPanelMutator.removePage(latestPages, index)?.let { removed ->
                    onPagesChange(removed)
                    onSelectedIndexChange(
                        safeIndex.coerceIn(0, (removed.size - 1).coerceAtLeast(0)),
                    )
                }
            }
            deleteTarget = null
        },
    )
}
