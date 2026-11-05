package com.slideindex.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import com.slideindex.app.ui.Md3PickerAppShortcutLeading
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.activityShortcutFromQuickLauncherItem
import com.slideindex.app.activity.toLaunchShortcut
import com.slideindex.app.activity.toQuickLauncherItem
import androidx.compose.ui.focus.FocusRequester
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.miuix.MiuixExpandableSearchBottomContent
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.launchShortcutFromCreated
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.picker.activityShortcutPickerToggleSection
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.picker.shortcutFolderCardsSection
import com.slideindex.app.ui.picker.systemShortcutCatalogItems
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.util.AppShortcutLoader

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CornerSlotSubMenuShortcutPickScreen(
    appSettings: AppSettings,
    existingPayloadKeys: Set<String>,
    onBack: () -> Unit,
    onAddShortcut: (GestureAction.LaunchShortcut) -> Unit,
    onOpenMyShortcuts: () -> Unit,
    onOpenPresetShortcuts: () -> Unit,
    onBrowseActivityShortcut: () -> Unit
) {
    val context = LocalContext.current
    val appRepository = rememberAppRepository()
    var apps by remember { mutableStateOf(appRepository.getCachedApps()) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val handleBack: () -> Unit = {
        if (
            !consumeExpandableSearchBack(
                expanded = searchExpanded,
                query = searchQuery,
                onExpandedChange = { searchExpanded = it },
                onQueryChange = { searchQuery = it }
            )
        ) {
            onBack()
        }
    }

    LaunchedEffect(Unit) {
        if (apps.isEmpty()) {
            apps = appRepository.loadApps(force = true)
        }
    }

    val loadedCatalog = rememberLoadedShortcutCatalog(apps)
    val filteredShortcuts = remember(loadedCatalog.catalog, searchQuery) {
        filterShortcutCatalog(loadedCatalog.catalog, searchQuery)
    }
    val appsByPackage = remember(apps) { apps.associateBy { it.packageName } }
    val activityShortcuts = appSettings.activityShortcuts

    val configuredShortcutKeys = remember(existingPayloadKeys, activityShortcuts) {
        buildSet {
            activityShortcuts.forEach { shortcut ->
                if (shortcut.toLaunchShortcut().payloadKey in existingPayloadKeys) {
                    QuickLauncherItemCodec.shortcutItemKey(shortcut.toQuickLauncherItem())?.let(::add)
                }
            }
        }
    }

    var pendingCreateHost by remember { mutableStateOf<AppShortcutLoader.CreateShortcutHost?>(null) }
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val host = pendingCreateHost
        pendingCreateHost = null
        if (result.resultCode != Activity.RESULT_OK || host == null) return@rememberLauncherForActivityResult
        val created = AppShortcutLoader.parseCreateShortcutResult(host.packageName, result.data)
            ?: return@rememberLauncherForActivityResult
        val shortcut = launchShortcutFromCreated(created)
        if (shortcut.payloadKey !in existingPayloadKeys) {
            onAddShortcut(shortcut)
        }
    }

    val addFromQuickLauncherItem: (QuickLauncherItem) -> Unit = { item ->
        val action = activityShortcutFromQuickLauncherItem(item)?.toLaunchShortcut()
        if (action != null && action.payloadKey !in existingPayloadKeys) {
            onAddShortcut(action)
        }
    }

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.corner_gesture_slot_submenu_add),
        pageHint = stringResource(R.string.corner_gesture_slot_submenu_add_desc),
        onBack = handleBack,
        modifier = Modifier.fillMaxSize(),
        actions = {
            MiuixExpandableSearchIconAction(
                expanded = searchExpanded,
                query = searchQuery,
                onExpandedChange = { searchExpanded = it },
                onQueryChange = { searchQuery = it }
            )
        },
        bottomContent = {
            MiuixExpandableSearchBottomContent(
                searchExpanded = searchExpanded,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                focusRequester = searchFocusRequester,
                hintResId = R.string.search_hint
            )
        }
    ) {
        if (searchQuery.isBlank()) {
            shortcutFolderCardsSection(
                activityShortcutsCount = activityShortcuts.size,
                onOpenMyShortcuts = onOpenMyShortcuts,
                onOpenPresetShortcuts = onOpenPresetShortcuts
            )
        } else if (activityShortcuts.isNotEmpty()) {
            activityShortcutPickerToggleSection(
                activityShortcuts = activityShortcuts,
                configuredShortcutKeys = configuredShortcutKeys,
                onToggle = { item, added ->
                    if (!added) addFromQuickLauncherItem(item)
                },
                onBrowse = onBrowseActivityShortcut,
                searchQuery = searchQuery
            )
        }
        systemShortcutCatalogItems(
            filtered = filteredShortcuts,
            appsByPackage = appsByPackage,
            loading = loadedCatalog.loading,
            scanProgress = loadedCatalog.scanProgress,
            searchQuery = searchQuery,
            onCreateHostClick = { host ->
                pendingCreateHost = host
                runCatching { createLauncher.launch(host.createIntent()) }
                    .onFailure { pendingCreateHost = null }
            },
            shortcutRowContent = { group, shortcut, segmentIndex, segmentCount ->
                CornerSlotSubMenuShortcutRow(
                    app = group.app,
                    shortcut = shortcut,
                    segmentIndex = segmentIndex,
                    segmentCount = segmentCount,
                    alreadyAdded = taskSwitcherItemToLaunchShortcut(shortcut, group.app.packageName)
                        .payloadKey in existingPayloadKeys,
                    onClick = {
                        val action = taskSwitcherItemToLaunchShortcut(shortcut, group.app.packageName)
                        if (action.payloadKey !in existingPayloadKeys) {
                            AppShortcutLoader.cacheShortcutForLaunch(group.app.packageName, shortcut)
                            onAddShortcut(action)
                        }
                    }
                )
            }
        )
    }
}

@Composable
private fun CornerSlotSubMenuShortcutRow(
    app: AppInfo,
    shortcut: TaskSwitcherMenuItem,
    segmentIndex: Int,
    segmentCount: Int,
    alreadyAdded: Boolean,
    onClick: () -> Unit
) {
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = shortcut.label,
        subtitle = shortcut.targetComponent?.takeIf { it.isNotBlank() },
        selected = alreadyAdded,
        onClick = onClick,
        leadingContent = {
            Md3PickerAppShortcutLeading(
                packageName = app.packageName,
                contentDescription = shortcut.label,
                selected = alreadyAdded
            )
        },
        trailingMode = PickerTrailingMode.Toggle,
        onTrailingClick = onClick,
        trailingChip = shortcutKindChip(shortcut.kind)
    )
}
