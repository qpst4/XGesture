package com.slideindex.app.ui

import com.slideindex.app.ui.miuix.MiuixFormDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import com.slideindex.app.ui.HomeLeadingIcons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ExcludedAppScopes
import com.slideindex.app.ui.compose.collectPickerAppsAsState
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.PermissionCard
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExcludedAppsScreen(
    settings: AppSettings,
    usageAccessGranted: Boolean,
    onBack: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onOpenAddApp: () -> Unit,
    onRemoveExcludedApp: (String) -> Unit,
    onExcludedAppScopesChange: (String, ExcludedAppScopes) -> Unit
) {
    val appRepository = rememberAppRepository()
    val allApps by collectPickerAppsAsState(includeClones = false)
    var editingEntry by remember { mutableStateOf<EditingExcludedApp?>(null) }
    val isLoading = allApps.isEmpty()

    val excludedPackages = settings.excludedAppScopes.keys
    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }
    val excludedEntries = remember(excludedPackages, allApps) {
        excludedPackages.sorted().map { packageName ->
            // 系统应用不在启动器列表里，回退按包名查询（仓库内缓存，重复调用无开销）
            (appsByPackage[packageName] ?: appRepository.lookupApp(packageName))
                ?.let { AppPackageEntry.Installed(it) }
                ?: AppPackageEntry.Missing(packageName)
        }
    }

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.excluded_apps_title),
        pageHint = stringResource(R.string.excluded_apps_desc),
        onBack = onBack
    ) {
        if (!usageAccessGranted) {
            item(key = "usage-permission") {
                PermissionCard(
                    title = stringResource(R.string.permission_usage_title),
                    description = stringResource(
                        R.string.permission_usage_desc_excluded_apps,
                        stringResource(R.string.app_name)
                    ),
                    onGrant = onRequestUsageAccess
                )
            }
        }
        when {
            isLoading -> {
                item(key = "loading") {
                    LoadingContent(message = stringResource(R.string.loading))
                }
            }
            excludedEntries.isEmpty() -> {
                groupedCardItems(
                    keyPrefix = "excluded-empty",
                    items = listOf(
                        CardItem("placeholder") {
                            Text(
                                text = stringResource(R.string.excluded_apps_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                    )
                )
            }
            else -> {
                managedAppListSectionTitle(
                    key = "section-excluded",
                    title = { stringResource(R.string.excluded_apps_section_excluded) }
                )
                managedAppListDescription(key = "excluded-hint") {
                    stringResource(R.string.excluded_apps_excluded_hint)
                }
                managedAppPackageRows(
                    keyPrefix = "excluded",
                    entries = excludedEntries,
                    actionIcon = Icons.Default.Close,
                    actionDescription = { stringResource(R.string.excluded_apps_remove) },
                    missingIcon = Icons.Default.Block,
                    subtitle = { entry ->
                        val scopes = settings.excludedAppScopes[entry.packageName]
                            ?: ExcludedAppScopes(
                                suppressTriggers = false,
                                suppressCornerWheel = false,
                                suppressFloatBall = false
                            )
                        formatExcludedAppScopesSummary(scopes)
                    },
                    onAction = { onRemoveExcludedApp(it.packageName) },
                    onRowClick = { entry ->
                        editingEntry = EditingExcludedApp(
                            packageName = entry.packageName,
                            label = entryLabel(entry),
                            scopes = settings.excludedAppScopes[entry.packageName] ?: ExcludedAppScopes.ALL
                        )
                    }
                )
            }
        }
        managedAppListAddRow(
            title = { stringResource(R.string.excluded_apps_section_add) },
            subtitle = { stringResource(R.string.excluded_apps_section_add_desc) },
            onClick = onOpenAddApp
        )
    }

    editingEntry?.let { editing ->
        ExcludedAppScopesEditorDialog(
            appLabel = editing.label,
            scopes = editing.scopes,
            onDismiss = { editingEntry = null },
            onConfirm = { scopes ->
                onExcludedAppScopesChange(editing.packageName, scopes)
                editingEntry = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExcludedAppPickScreen(
    excludedPackages: Set<String>,
    onBack: () -> Unit,
    onConfirmAdd: (String, ExcludedAppScopes) -> Unit
) {
    var pending by remember { mutableStateOf<PendingExcludeApp?>(null) }

    ActivityShortcutPickAppScreen(
        titleResId = R.string.excluded_apps_section_add,
        excludePackageNames = excludedPackages,
        // 按应用禁用按包名匹配前台，无法区分分身：显示但不可选。
        cloneMode = com.slideindex.app.ui.picker.AppClonePickMode.DISABLED,
        onBack = onBack,
        onSelectApp = { app ->
            pending = PendingExcludeApp(
                packageName = app.packageName,
                label = app.label,
                scopes = ExcludedAppScopes.ALL
            )
        }
    )

    pending?.let { target ->
        ExcludedAppAddScopesDialog(
            appLabel = target.label,
            scopes = target.scopes,
            onDismiss = { pending = null },
            onConfirm = { scopes ->
                onConfirmAdd(target.packageName, scopes)
                pending = null
                onBack()
            }
        )
    }
}

@Composable
fun SettingsCardScope.ExcludedAppsEntryCard(
    excludedCount: Int,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit
) {
    val subtitle = if (excludedCount > 0) {
        stringResource(R.string.excluded_apps_entry_count, excludedCount)
    } else {
        stringResource(R.string.excluded_apps_entry_desc)
    }
    SettingNavigationRow(
        icon = { label ->
            Icon(HomeLeadingIcons.excludedApps(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.excluded_apps_entry_title),
        subtitle = subtitle,
        onClick = onClick
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExcludedAppScopeChipPicker(
    scopes: ExcludedAppScopes,
    onSuppressTriggersChange: (Boolean) -> Unit,
    onSuppressCornerWheelChange: (Boolean) -> Unit,
    onSuppressFloatBallChange: (Boolean) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ExcludedAppScopeChip(
            label = stringResource(R.string.excluded_apps_scope_triggers_short),
            selected = scopes.suppressTriggers,
            onClick = { onSuppressTriggersChange(!scopes.suppressTriggers) }
        )
        ExcludedAppScopeChip(
            label = stringResource(R.string.excluded_apps_scope_corner_wheel_short),
            selected = scopes.suppressCornerWheel,
            onClick = { onSuppressCornerWheelChange(!scopes.suppressCornerWheel) }
        )
        ExcludedAppScopeChip(
            label = stringResource(R.string.excluded_apps_scope_float_ball_short),
            selected = scopes.suppressFloatBall,
            onClick = { onSuppressFloatBallChange(!scopes.suppressFloatBall) }
        )
    }
}

@Composable
private fun ExcludedAppScopeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
private fun formatExcludedAppScopesSummary(scopes: ExcludedAppScopes): String {
    val parts = buildList {
        if (scopes.suppressTriggers) add(stringResource(R.string.excluded_apps_scope_triggers_short))
        if (scopes.suppressCornerWheel) add(stringResource(R.string.excluded_apps_scope_corner_wheel_short))
        if (scopes.suppressFloatBall) add(stringResource(R.string.excluded_apps_scope_float_ball_short))
    }
    return if (parts.isEmpty()) {
        stringResource(R.string.excluded_apps_scope_none)
    } else {
        parts.joinToString(" · ")
    }
}

@Composable
private fun ExcludedAppScopesEditorDialog(
    appLabel: String,
    scopes: ExcludedAppScopes,
    onDismiss: () -> Unit,
    onConfirm: (ExcludedAppScopes) -> Unit
) {
    var localScopes by remember(scopes, appLabel) { mutableStateOf(scopes) }
    MiuixFormDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = appLabel,
        confirmEnabled = localScopes.hasAny(),
        onConfirm = { onConfirm(localScopes) }
    ) {
        ExcludedAppScopesDialogBody(
            scopes = localScopes,
            onScopesChange = { localScopes = it }
        )
    }
}

@Composable
private fun ExcludedAppAddScopesDialog(
    appLabel: String,
    scopes: ExcludedAppScopes,
    onDismiss: () -> Unit,
    onConfirm: (ExcludedAppScopes) -> Unit
) {
    var localScopes by remember(appLabel) { mutableStateOf(scopes) }
    MiuixFormDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.excluded_apps_confirm_add_title),
        confirmEnabled = localScopes.hasAny(),
        onConfirm = { onConfirm(localScopes) }
    ) {
        Column {
            Text(
                text = appLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExcludedAppScopesDialogBody(
                scopes = localScopes,
                onScopesChange = { localScopes = it }
            )
        }
    }
}

@Composable
private fun ExcludedAppScopesDialogBody(
    scopes: ExcludedAppScopes,
    onScopesChange: (ExcludedAppScopes) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.excluded_apps_scope_dialog_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        ExcludedAppScopeChipPicker(
            scopes = scopes,
            onSuppressTriggersChange = { onScopesChange(scopes.copy(suppressTriggers = it)) },
            onSuppressCornerWheelChange = { onScopesChange(scopes.copy(suppressCornerWheel = it)) },
            onSuppressFloatBallChange = { onScopesChange(scopes.copy(suppressFloatBall = it)) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (scopes.hasAny()) {
                stringResource(
                    R.string.excluded_apps_template_summary,
                    formatExcludedAppScopesSummary(scopes)
                )
            } else {
                stringResource(R.string.excluded_apps_template_empty)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (scopes.hasAny()) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}

private data class EditingExcludedApp(
    val packageName: String,
    val label: String,
    val scopes: ExcludedAppScopes
)

private data class PendingExcludeApp(
    val packageName: String,
    val label: String,
    val scopes: ExcludedAppScopes
)

private fun entryLabel(entry: AppPackageEntry): String = when (entry) {
    is AppPackageEntry.Installed -> entry.app.label
    is AppPackageEntry.Missing -> entry.packageName
}
