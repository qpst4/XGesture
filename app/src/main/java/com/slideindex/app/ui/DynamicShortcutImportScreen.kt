package com.slideindex.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.shortcuts.DynamicShortcutsManager
import com.slideindex.app.shortcuts.DynamicShortcutsManager.DiscoveredDynamicShortcut
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DynamicShortcutImportScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSaveShortcuts: (List<ActivityShortcut>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val pageTitle = stringResource(R.string.dynamic_shortcuts_title)
    val permissionTitle = stringResource(R.string.dynamic_shortcuts_permission_card_title)
    val grantHomeTitle = stringResource(R.string.dynamic_shortcuts_grant_home)
    val permissionNeededDesc = stringResource(R.string.dynamic_shortcuts_permission_needed_desc)
    val scanTitle = stringResource(R.string.dynamic_shortcuts_scan)
    val scanningTitle = stringResource(R.string.dynamic_shortcuts_scanning)
    val permissionGrantedDesc = stringResource(R.string.dynamic_shortcuts_permission_granted_desc)
    val restoreHomeTitle = stringResource(R.string.dynamic_shortcuts_restore_home)
    val alreadyImportedText = stringResource(R.string.dynamic_shortcuts_already_imported)
    val emptyTipText = stringResource(R.string.dynamic_shortcuts_empty)

    var hasPermission by remember { mutableStateOf(DynamicShortcutsManager.hasPermission(context)) }
    var isScanning by remember { mutableStateOf(false) }
    var discoveredShortcuts by remember { mutableStateOf<List<DiscoveredDynamicShortcut>>(emptyList()) }
    val selectedShortcuts = remember { mutableStateListOf<DiscoveredDynamicShortcut>() }

    val homeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasPermission = DynamicShortcutsManager.hasPermission(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = DynamicShortcutsManager.hasPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun startScan() {
        if (!hasPermission) return
        isScanning = true
        scope.launch {
            val list = DynamicShortcutsManager.queryDynamicShortcuts(context)
            val deduplicated = list.distinctBy { "${it.packageName}_${it.userHandle.hashCode()}_${it.shortcutId}" }
            discoveredShortcuts = deduplicated
            selectedShortcuts.clear()
            val existingIds = settings.activityShortcuts.map { it.shortcutId }.toSet()
            deduplicated.filter { it.shortcutId !in existingIds }.forEach { selectedShortcuts.add(it) }
            isScanning = false
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && discoveredShortcuts.isEmpty()) {
            startScan()
        }
    }

    fun importSelected() {
        if (selectedShortcuts.isEmpty()) {
            Toast.makeText(context, R.string.dynamic_shortcuts_no_selection, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            DynamicShortcutsManager.pinShortcuts(context, selectedShortcuts)
            val newShortcuts = selectedShortcuts.map { DynamicShortcutsManager.toActivityShortcut(it) }
            val existingKeys = settings.activityShortcuts.map { it.identityKey() }.toSet()
            val merged = settings.activityShortcuts + newShortcuts.filter { it.identityKey() !in existingKeys }
            onSaveShortcuts(merged)
            Toast.makeText(
                context,
                context.getString(R.string.dynamic_shortcuts_import_success, selectedShortcuts.size),
                Toast.LENGTH_LONG,
            ).show()
            onBack()
        }
    }

    val existingShortcutIds = remember(settings.activityShortcuts) {
        settings.activityShortcuts.map { it.shortcutId }.toSet()
    }

    SettingsLazyScreenScaffold(
        title = pageTitle,
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "dynamic_shortcuts_permission_title",
            title = permissionTitle,
        )

        groupedCardItems(
            keyPrefix = "dynamic_shortcuts_permission",
            items = buildList {
                if (!hasPermission) {
                    add(
                        settingsCardScopeItem("grant_home") {
                            SettingNavigationRow(
                                title = grantHomeTitle,
                                subtitle = permissionNeededDesc,
                                icon = { label -> MiuixIcon(Icons.Default.Home, contentDescription = label) },
                                onClick = {
                                    runCatching {
                                        homeLauncher.launch(DynamicShortcutsManager.createRequestHomeIntent(context))
                                    }.onFailure {
                                        Toast.makeText(context, R.string.dynamic_shortcuts_home_unavailable, Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                        }
                    )
                } else {
                    add(
                        settingsCardScopeItem("scan") {
                            SettingNavigationRow(
                                title = if (isScanning) scanningTitle else scanTitle,
                                subtitle = permissionGrantedDesc,
                                icon = { label -> MiuixIcon(Icons.Default.Refresh, contentDescription = label) },
                                onClick = { startScan() },
                            )
                        }
                    )
                }
                add(
                    settingsCardScopeItem("restore_home") {
                        SettingNavigationRow(
                            title = restoreHomeTitle,
                            subtitle = if (hasPermission) "导入完成后请点击此处，切回原本的系统桌面" else "",
                            icon = { label -> MiuixIcon(Icons.Default.Restore, contentDescription = label) },
                            onClick = {
                                runCatching {
                                    context.startActivity(DynamicShortcutsManager.openHomeSettingsIntent())
                                }.onFailure {
                                    Toast.makeText(context, R.string.dynamic_shortcuts_home_unavailable, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                )
            }
        )

        if (discoveredShortcuts.isNotEmpty()) {
            settingsLazySmallTitle(
                key = "dynamic_shortcuts_list_title",
                title = "已发现的动态快捷方式 (${discoveredShortcuts.size})",
            )

            groupedCardItems(
                keyPrefix = "dynamic_shortcuts_action",
                items = listOf(
                    settingsCardScopeItem("commit") {
                        SettingNavigationRow(
                            title = context.getString(R.string.dynamic_shortcuts_import_selected, selectedShortcuts.size),
                            subtitle = "勾选后点击导入，将在底层永久固定（Pin）这些快捷方式",
                            icon = { label -> MiuixIcon(Icons.Default.Check, contentDescription = label, tint = MiuixTheme.colorScheme.primary) },
                            onClick = { importSelected() },
                        )
                    }
                )
            )

            val groupedByApp = discoveredShortcuts.groupBy { it.packageName }
            for ((packageName, items) in groupedByApp) {
                val displayLabel = items.firstOrNull()?.appLabel ?: packageName
                settingsLazySmallTitle(
                    key = "app_group_$packageName",
                    title = displayLabel,
                )
                groupedCardItems(
                    keyPrefix = "shortcuts_$packageName",
                    items = items.mapIndexed { index, item ->
                        settingsCardScopeItem("${item.userHandle.hashCode()}_${item.shortcutId}_$index") {
                            val alreadyImported = item.shortcutId in existingShortcutIds
                            val isChecked = item in selectedShortcuts
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = item.label,
                                summary = if (alreadyImported) alreadyImportedText else item.packageName,
                                onClick = {
                                    if (isChecked) selectedShortcuts.remove(item) else selectedShortcuts.add(item)
                                },
                                startAction = {
                                    MiuixIcon(
                                        imageVector = Icons.AutoMirrored.Filled.Shortcut,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                    )
                                },
                                endActions = {
                                    Checkbox(
                                        state = if (isChecked) ToggleableState.On else ToggleableState.Off,
                                        onClick = {
                                            if (isChecked) selectedShortcuts.remove(item) else selectedShortcuts.add(item)
                                        },
                                    )
                                },
                            )
                        }
                    }
                )
            }
        } else if (hasPermission && !isScanning) {
            settingsLazyTipCard(
                key = "empty_shortcuts_tip",
                text = emptyTipText,
            )
        }
    }
}
