package com.slideindex.app.ui.quicklauncher

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.KeyEventPresets
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.ui.AppPackageEntry
import com.slideindex.app.ui.Md3PickerAppEntryLeading
import com.slideindex.app.ui.Md3PickerAppLeading
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.Md3PickerSectionHeader
import com.slideindex.app.ui.shortcutKindChip
import com.slideindex.app.ui.PickerListGroupSpacing
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.ui.PickerListOverlayHorizontalPadding
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.GestureOpenLinkScreen
import com.slideindex.app.ui.GestureSimulateKeyEventScreen
import com.slideindex.app.ui.displayLabelForExecuteShellCommand
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.pickerHorizontalSlideTransitionByDepth
import com.slideindex.app.ui.quicklauncher.quickLauncherAddPickerActionItems
import com.slideindex.app.ui.quicklauncher.rememberQuickLauncherFilteredActions
import com.slideindex.app.ui.picker.activityShortcutPickerToggleSection
import com.slideindex.app.ui.picker.shortcutFolderCardsSection
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.picker.systemShortcutCatalogItems
import com.slideindex.app.ui.gesturepicker.gestureActionDescription
import com.slideindex.app.ui.gesturepicker.gestureActionLabel
import com.slideindex.app.ui.gesturepicker.requestPermissionForAdjustAction
import com.slideindex.app.ui.pickerListSegmentedGap
import com.slideindex.app.ui.pickerSegmentCount
import com.slideindex.app.ui.pickerSegmentIndex
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.CreatedShortcut
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.ShortcutScanPhase
import com.slideindex.app.util.ShortcutScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface QuickLauncherAddSubScreen {
    data object Main : QuickLauncherAddSubScreen
    data object PickApp : QuickLauncherAddSubScreen
    data class PickActivity(val packageName: String) : QuickLauncherAddSubScreen
    data class ShellCommandConfig(val initialCommand: String = "") : QuickLauncherAddSubScreen
    data class OpenLinkConfig(
        val initialUrl: String = "",
        val initialLabel: String = "",
    ) : QuickLauncherAddSubScreen
    data class SimulateKeyEventConfig(
        val initialKeyCode: Int = 82,
        val initialKeyName: String = "",
        val initialIsLongPress: Boolean = false,
    ) : QuickLauncherAddSubScreen
    data object CreateFolder : QuickLauncherAddSubScreen
    data object MyShortcuts : QuickLauncherAddSubScreen
    data object PresetShortcuts : QuickLauncherAddSubScreen
}

internal fun QuickLauncherAddSubScreen.navDepth(): Int = when (this) {
    QuickLauncherAddSubScreen.Main -> 0
    QuickLauncherAddSubScreen.PickApp -> 1
    is QuickLauncherAddSubScreen.PickActivity -> 2
    is QuickLauncherAddSubScreen.ShellCommandConfig -> 1
    is QuickLauncherAddSubScreen.OpenLinkConfig -> 1
    is QuickLauncherAddSubScreen.SimulateKeyEventConfig -> 1
    QuickLauncherAddSubScreen.CreateFolder -> 1
    QuickLauncherAddSubScreen.MyShortcuts -> 1
    QuickLauncherAddSubScreen.PresetShortcuts -> 1
}

private fun QuickLauncherAddSubScreen.contentKey(): Any = when (this) {
    QuickLauncherAddSubScreen.Main -> "main"
    QuickLauncherAddSubScreen.PickApp -> "pickApp"
    is QuickLauncherAddSubScreen.PickActivity -> "pickActivity:$packageName"
    is QuickLauncherAddSubScreen.ShellCommandConfig -> "shellConfig"
    is QuickLauncherAddSubScreen.OpenLinkConfig -> "openLinkConfig"
    is QuickLauncherAddSubScreen.SimulateKeyEventConfig -> "simulateKeyConfig"
    QuickLauncherAddSubScreen.CreateFolder -> "createFolder"
    QuickLauncherAddSubScreen.MyShortcuts -> "myShortcuts"
    QuickLauncherAddSubScreen.PresetShortcuts -> "presetShortcuts"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun QuickLauncherAddOverlaySheetBody(
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    nestedScrollConnection: NestedScrollConnection?,
    searchQuery: String,
    apps: List<AppInfo>,
    addedAppPackages: Set<String>,
    addedShortcutKeys: Set<String>,
    addedActionKeys: Set<String>,
    activityShortcuts: List<ActivityShortcut>,
    shellCommands: List<com.slideindex.app.shell.ShellCommand> = emptyList(),
    onToggle: (QuickLauncherItem, Boolean) -> Unit,
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit,
    subScreen: QuickLauncherAddSubScreen,
    onSubScreenChange: (QuickLauncherAddSubScreen) -> Unit,
    selectedTab: Int,
    singleSelect: Boolean = false,
    pinNoneAtTop: Boolean = false,
    slotEmpty: Boolean = false,
    onClearSlot: () -> Unit = {},
    folderName: String = "",
    folderItems: List<QuickLauncherItem> = emptyList(),
    folderPickerActive: Boolean = false,
    onFolderNameChange: (String) -> Unit = {},
    onToggleFolderItem: (QuickLauncherItem, Boolean) -> Unit = { _, _ -> },
    onEnterFolderLibrary: () -> Unit = {},
    onClearFolderDraft: () -> Unit = {},
    onReportEmbedParentConfirm: (QuickLauncherEmbedParentConfirm?) -> Unit = {},
    /** 「手势宏」挑选钩子（透传到动作列表）。 */
    onRequestRunMacro: ((GestureAction) -> Unit) -> Unit = {},
) {
    var visitedTabs by remember { mutableStateOf(setOf(selectedTab)) }
    LaunchedEffect(selectedTab) {
        visitedTabs = visitedTabs + selectedTab
    }

    AnimatedContent(
        targetState = subScreen,
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
        transitionSpec = { pickerHorizontalSlideTransitionByDepth(QuickLauncherAddSubScreen::navDepth) },
        contentKey = { it.contentKey() },
        label = "quickLauncherAddSubNav",
    ) { screen ->
        Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            is QuickLauncherAddSubScreen.ShellCommandConfig -> {
                GestureExecuteShellCommandScreen(
                    initialCommand = screen.initialCommand,
                    shellCommands = shellCommands,
                    embedInParentChrome = true,
                    overlayMode = true,
                    enableBackHandler = false,
                    onRegisterEmbedParentConfirm = onReportEmbedParentConfirm,
                    onBack = {
                        onSubScreenChange(
                            if (folderPickerActive) {
                                QuickLauncherAddSubScreen.CreateFolder
                            } else {
                                QuickLauncherAddSubScreen.Main
                            },
                        )
                    },
                    onConfirm = { command ->
                        val label = displayLabelForExecuteShellCommand(command, shellCommands)
                        val action = com.slideindex.app.gesture.GestureAction.ExecuteShellCommand(command)
                        val item = QuickLauncherItem.action(action, label)
                        if (folderPickerActive) {
                            onToggleFolderItem(item, false)
                            onSubScreenChange(QuickLauncherAddSubScreen.CreateFolder)
                        } else {
                            onToggle(item, false)
                            onSubScreenChange(QuickLauncherAddSubScreen.Main)
                        }
                    },
                )
            }
            is QuickLauncherAddSubScreen.OpenLinkConfig -> {
                GestureOpenLinkScreen(
                    initialUrl = screen.initialUrl,
                    initialLabel = screen.initialLabel,
                    embedInParentChrome = true,
                    overlayMode = true,
                    enableBackHandler = false,
                    onRegisterEmbedParentConfirm = onReportEmbedParentConfirm,
                    onBack = {
                        onSubScreenChange(
                            if (folderPickerActive) {
                                QuickLauncherAddSubScreen.CreateFolder
                            } else {
                                QuickLauncherAddSubScreen.Main
                            },
                        )
                    },
                    onConfirm = { url, label ->
                        val trimmedUrl = url.trim()
                        val trimmedLabel = label.trim()
                        val action = GestureAction.OpenLink(url = trimmedUrl, label = trimmedLabel)
                        val itemLabel = trimmedLabel.ifBlank { trimmedUrl }
                        val item = QuickLauncherItem.action(action, itemLabel)
                        if (folderPickerActive) {
                            onToggleFolderItem(item, false)
                            onSubScreenChange(QuickLauncherAddSubScreen.CreateFolder)
                        } else {
                            onToggle(item, false)
                            onSubScreenChange(QuickLauncherAddSubScreen.Main)
                        }
                    },
                )
            }
            is QuickLauncherAddSubScreen.SimulateKeyEventConfig -> {
                val context = LocalContext.current
                GestureSimulateKeyEventScreen(
                    initialAction = GestureAction.SimulateKeyEvent(
                        keyCode = screen.initialKeyCode,
                        keyName = screen.initialKeyName,
                        isLongPress = screen.initialIsLongPress,
                    ),
                    embedInParentChrome = true,
                    overlayMode = true,
                    enableBackHandler = false,
                    onRegisterEmbedParentConfirm = onReportEmbedParentConfirm,
                    onBack = {
                        onSubScreenChange(
                            if (folderPickerActive) {
                                QuickLauncherAddSubScreen.CreateFolder
                            } else {
                                QuickLauncherAddSubScreen.Main
                            },
                        )
                    },
                    onConfirm = { keyEventAction ->
                        val label = keyEventAction.keyName.ifBlank {
                            KeyEventPresets.getDisplayName(context, keyEventAction.keyCode)
                        }
                        val item = QuickLauncherItem.action(keyEventAction, label)
                        if (folderPickerActive) {
                            onToggleFolderItem(item, false)
                            onSubScreenChange(QuickLauncherAddSubScreen.CreateFolder)
                        } else {
                            onToggle(item, false)
                            onSubScreenChange(QuickLauncherAddSubScreen.Main)
                        }
                    },
                )
            }
            QuickLauncherAddSubScreen.PickApp -> {
                ActivityShortcutPickAppScreen(
                    embedInParentChrome = true,
                    onBack = {
                        onSubScreenChange(
                            if (folderPickerActive) {
                                QuickLauncherAddSubScreen.CreateFolder
                            } else {
                                QuickLauncherAddSubScreen.Main
                            },
                        )
                    },
                    onSelectApp = { app ->
                        onSubScreenChange(QuickLauncherAddSubScreen.PickActivity(app.packageName))
                    },
                )
            }
            is QuickLauncherAddSubScreen.PickActivity -> {
                ActivityShortcutPickActivityScreen(
                    packageName = screen.packageName,
                    embedInParentChrome = true,
                    onBack = { onSubScreenChange(QuickLauncherAddSubScreen.PickApp) },
                    onSelectActivity = { activity ->
                        val item = QuickLauncherItem.shortcut(
                            "${activity.packageName}/${activity.className}",
                            activity.label,
                        )
                        if (folderPickerActive) {
                            onToggleFolderItem(item, false)
                            onSubScreenChange(QuickLauncherAddSubScreen.CreateFolder)
                        } else {
                            onToggle(item, false)
                            onSubScreenChange(QuickLauncherAddSubScreen.Main)
                        }
                    },
                )
            }
            QuickLauncherAddSubScreen.CreateFolder -> {
                QuickLauncherCreateFolderScreen(
                    apps = apps,
                    activityShortcuts = activityShortcuts,
                    shellCommands = shellCommands,
                    folderName = folderName,
                    folderItems = folderItems,
                    onFolderNameChange = onFolderNameChange,
                    onToggleFolderItem = onToggleFolderItem,
                    onCreateFolder = { name, items ->
                        onToggle(QuickLauncherItem.folder(name, items), false)
                        onClearFolderDraft()
                        onSubScreenChange(QuickLauncherAddSubScreen.Main)
                    },
                    onBack = {
                        onClearFolderDraft()
                        onSubScreenChange(QuickLauncherAddSubScreen.Main)
                    },
                    launchCreateShortcut = launchCreateShortcut,
                    onRequestRunMacro = onRequestRunMacro,
                    onBrowseActivityShortcut = {
                        onEnterFolderLibrary()
                        onSubScreenChange(QuickLauncherAddSubScreen.PickApp)
                    },
                    selectedTab = selectedTab,
                    searchQuery = searchQuery,
                    onSubScreenChange = { next ->
                        if (next !is QuickLauncherAddSubScreen.CreateFolder) {
                            onEnterFolderLibrary()
                        }
                        onSubScreenChange(next)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            QuickLauncherAddSubScreen.MyShortcuts -> {
                val shortcutKeys = if (folderPickerActive) {
                    folderItems.filter { it.type == QuickLauncherItemType.SHORTCUT }
                        .mapNotNull { QuickLauncherItemCodec.shortcutItemKey(it) }
                        .toSet()
                } else {
                    addedShortcutKeys
                }
                com.slideindex.app.ui.picker.MyShortcutsFolderScreen(
                    activityShortcuts = activityShortcuts,
                    onBack = {
                        onSubScreenChange(
                            if (folderPickerActive) {
                                QuickLauncherAddSubScreen.CreateFolder
                            } else {
                                QuickLauncherAddSubScreen.Main
                            },
                        )
                    },
                    onBrowseNewShortcut = {
                        onEnterFolderLibrary()
                        onSubScreenChange(QuickLauncherAddSubScreen.PickApp)
                    },
                    configuredShortcutKeys = shortcutKeys,
                    onToggle = if (folderPickerActive) onToggleFolderItem else onToggle,
                    overlayMode = true,
                    embedInParentChrome = true,
                    searchQuery = searchQuery,
                )
            }
            QuickLauncherAddSubScreen.PresetShortcuts -> {
                val shortcutKeys = if (folderPickerActive) {
                    folderItems.filter { it.type == QuickLauncherItemType.SHORTCUT }
                        .mapNotNull { QuickLauncherItemCodec.shortcutItemKey(it) }
                        .toSet()
                } else {
                    addedShortcutKeys
                }
                com.slideindex.app.ui.picker.PresetShortcutsFolderScreen(
                    onBack = {
                        onSubScreenChange(
                            if (folderPickerActive) {
                                QuickLauncherAddSubScreen.CreateFolder
                            } else {
                                QuickLauncherAddSubScreen.Main
                            },
                        )
                    },
                    configuredShortcutKeys = shortcutKeys,
                    onToggle = if (folderPickerActive) onToggleFolderItem else onToggle,
                    overlayMode = true,
                    embedInParentChrome = true,
                    searchQuery = searchQuery,
                )
            }
            QuickLauncherAddSubScreen.Main -> {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        val tabModifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .then(
                if (nestedScrollConnection != null) {
                    Modifier.nestedScroll(nestedScrollConnection)
                } else {
                    Modifier
                },
            )
        Box(modifier = tabModifier) {
            if (0 in visitedTabs) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pickerTabPageVisible(selectedTab == 0),
                ) {
                    QuickLauncherAddActionsTab(
                        searchQuery = searchQuery,
                        configuredActionKeys = addedActionKeys,
                        onToggleItem = onToggle,
                        onOpenExecuteShellCommand = {
                            onSubScreenChange(QuickLauncherAddSubScreen.ShellCommandConfig())
                        },
                        onOpenOpenLink = {
                            onSubScreenChange(QuickLauncherAddSubScreen.OpenLinkConfig())
                        },
                        onOpenSimulateKeyEvent = {
                            onSubScreenChange(QuickLauncherAddSubScreen.SimulateKeyEventConfig())
                        },
                        onRequestRunMacro = onRequestRunMacro,
                        singleSelect = singleSelect,
                        pinNoneAtTop = pinNoneAtTop,
                        noneSelected = slotEmpty,
                        onSelectNone = onClearSlot,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (1 in visitedTabs) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pickerTabPageVisible(selectedTab == 1),
                ) {
                    QuickLauncherAddAppsTab(
                        searchQuery = searchQuery,
                        apps = apps,
                        configuredAppPackages = addedAppPackages,
                        onToggle = { app, added ->
                            onToggle(QuickLauncherItem.app(app.packageName, app.label, app.cloneUserId), added)
                        },
                        singleSelect = singleSelect,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (2 in visitedTabs) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pickerTabPageVisible(selectedTab == 2),
                ) {
                    QuickLauncherAddShortcutsTab(
                        apps = apps,
                        searchQuery = searchQuery,
                        configuredShortcutKeys = addedShortcutKeys,
                        activityShortcuts = activityShortcuts,
                        onToggle = onToggle,
                        onBrowseActivityShortcut = {
                            onSubScreenChange(QuickLauncherAddSubScreen.PickApp)
                        },
                        onSubScreenChange = onSubScreenChange,
                        launchCreateShortcut = launchCreateShortcut,
                        singleSelect = singleSelect,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
            }
        }
        }
    }
}

@Composable
fun QuickLauncherToggleRow(
    entry: AppPackageEntry,
    segmentIndex: Int,
    segmentCount: Int,
    added: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    showAction: Boolean = true,
    singleSelect: Boolean = false,
) {
    val resolvedTitle = title ?: when (entry) {
        is AppPackageEntry.Installed -> entry.app.label
        is AppPackageEntry.Missing -> entry.packageName
    }
    val resolvedSubtitle = subtitle ?: when (entry) {
        is AppPackageEntry.Installed -> entry.app.packageName
        is AppPackageEntry.Missing -> null
    }
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = resolvedTitle,
        subtitle = resolvedSubtitle,
        selected = if (singleSelect) false else added,
        onClick = if (showAction) onToggle else null,
        modifier = modifier,
        leadingContent = {
            Md3PickerAppEntryLeading(
                entry = entry,
                missingIcon = Icons.AutoMirrored.Outlined.Shortcut,
            )
        },
        trailingMode = if (singleSelect) PickerTrailingMode.None else if (showAction) PickerTrailingMode.Toggle else PickerTrailingMode.None,
        onTrailingClick = if (singleSelect) null else if (showAction) onToggle else null,
    )
}

@Composable
fun QuickLauncherShellCommandActionRow(
    action: GestureAction,
    segmentIndex: Int,
    segmentCount: Int,
    label: String,
    subtitle: String?,
    onOpenConfig: () -> Unit,
) {
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = label,
        subtitle = subtitle,
        selected = false,
        onClick = onOpenConfig,
        leadingContent = {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            com.slideindex.app.ui.ActionIconSlot(action = action, sizeDp = 24)
        }
        },
        trailingMode = PickerTrailingMode.None,
    )
}

@Composable
fun QuickLauncherActionRow(
    action: GestureAction,
    segmentIndex: Int,
    segmentCount: Int,
    label: String,
    subtitle: String?,
    added: Boolean,
    singleSelect: Boolean = false,
    onToggle: () -> Unit,
) {
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = label,
        subtitle = subtitle,
        selected = if (singleSelect) false else added,
        onClick = onToggle,
        leadingContent = {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            com.slideindex.app.ui.ActionIconSlot(action = action, sizeDp = 24)
        }
        },
        trailingMode = if (singleSelect) PickerTrailingMode.None else PickerTrailingMode.Toggle,
        onTrailingClick = if (singleSelect) null else onToggle,
    )
}

@Composable
private fun QuickLauncherAddActionsTab(
    searchQuery: String,
    configuredActionKeys: Set<String>,
    onToggleItem: (QuickLauncherItem, Boolean) -> Unit,
    onOpenExecuteShellCommand: () -> Unit,
    onOpenOpenLink: () -> Unit,
    onOpenSimulateKeyEvent: () -> Unit,
    /** 「手势宏」的挑选钩子：弹选择框挑一条宏，拿到带 macroId 的动作再落库。 */
    onRequestRunMacro: ((GestureAction) -> Unit) -> Unit = {},
    modifier: Modifier,
    singleSelect: Boolean = false,
    pinNoneAtTop: Boolean = false,
    noneSelected: Boolean = false,
    onSelectNone: () -> Unit = {},
) {
    val filtered = rememberQuickLauncherFilteredActions(searchQuery)
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
    ) {
        quickLauncherAddPickerActionItems(
            filtered = filtered,
            configuredActionKeys = configuredActionKeys,
            onToggleItem = onToggleItem,
            onOpenExecuteShellCommand = onOpenExecuteShellCommand,
            onOpenOpenLink = onOpenOpenLink,
            onOpenSimulateKeyEvent = onOpenSimulateKeyEvent,
            onRequestRunMacro = onRequestRunMacro,
            singleSelect = singleSelect,
            pinNoneAtTop = pinNoneAtTop,
            noneSelected = noneSelected,
            onSelectNone = onSelectNone,
            searchQuery = searchQuery,
        )
    }
}

@Composable
private fun QuickLauncherAddAppsTab(
    searchQuery: String,
    apps: List<AppInfo>,
    configuredAppPackages: Set<String>,
    onToggle: (AppInfo, Boolean) -> Unit,
    modifier: Modifier,
    singleSelect: Boolean = false,
) {
    val query = searchQuery.trim().lowercase()
    val filtered = remember(apps, query) {
        apps.filter { app ->
            query.isEmpty() ||
                app.label.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query) ||
                app.pinyinKey.contains(query)
        }.sortedBy { it.pinyinKey }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
    ) {
        quickLauncherAddPickerAppItems(
            filtered = filtered,
            configuredAppPackages = configuredAppPackages,
            onToggle = onToggle,
            singleSelect = singleSelect,
        )
    }
}

@Composable
private fun QuickLauncherAddShortcutsTab(
    apps: List<AppInfo>,
    searchQuery: String,
    configuredShortcutKeys: Set<String>,
    activityShortcuts: List<ActivityShortcut>,
    onToggle: (QuickLauncherItem, Boolean) -> Unit,
    onBrowseActivityShortcut: () -> Unit,
    onSubScreenChange: (QuickLauncherAddSubScreen) -> Unit,
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit,
    modifier: Modifier,
    singleSelect: Boolean = false,
) {
    val loadedCatalog = rememberLoadedShortcutCatalog(apps)
    val filtered = remember(loadedCatalog.catalog, searchQuery) {
        filterShortcutCatalog(loadedCatalog.catalog, searchQuery)
    }
    val appsByPackage = remember(apps) { apps.associateBy { it.packageName } }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
    ) {
        if (searchQuery.isBlank()) {
            shortcutFolderCardsSection(
                activityShortcutsCount = activityShortcuts.size,
                onOpenMyShortcuts = { onSubScreenChange(QuickLauncherAddSubScreen.MyShortcuts) },
                onOpenPresetShortcuts = { onSubScreenChange(QuickLauncherAddSubScreen.PresetShortcuts) },
                horizontalPadding = PickerListOverlayHorizontalPadding,
            )
        } else if (activityShortcuts.isNotEmpty()) {
            activityShortcutPickerToggleSection(
                activityShortcuts = activityShortcuts,
                configuredShortcutKeys = configuredShortcutKeys,
                onToggle = onToggle,
                onBrowse = onBrowseActivityShortcut,
                searchQuery = searchQuery,
                singleSelect = singleSelect,
            )
        }
        systemShortcutCatalogItems(
            filtered = filtered,
            appsByPackage = appsByPackage,
            loading = loadedCatalog.loading,
            scanProgress = loadedCatalog.scanProgress,
            searchQuery = searchQuery,
            loadingItemKey = "shortcut-loading",
            emptyItemKey = "shortcut-empty",
            onCreateHostClick = { host ->
                launchCreateShortcut(host) { created ->
                    created?.let { shortcut ->
                        onToggle(shortcut.toQuickLauncherItem(), false)
                    }
                }
            },
            shortcutRowContent = { group, shortcut, segmentIndex, segmentCount ->
                val item = shortcut.toQuickLauncherItem(group.app.packageName)
                val added = QuickLauncherItemCodec.shortcutItemKey(item) in configuredShortcutKeys
                QuickLauncherShortcutToggleRow(
                    app = group.app,
                    shortcut = shortcut,
                    segmentIndex = segmentIndex,
                    segmentCount = segmentCount,
                    added = added,
                    singleSelect = singleSelect,
                    onToggle = {
                        if (!added) {
                            AppShortcutLoader.cacheShortcutForLaunch(group.app.packageName, shortcut)
                        }
                        onToggle(item, if (singleSelect) false else added)
                    },
                )
            },
        )
    }
}

@Composable
private fun QuickLauncherShortcutToggleRow(
    app: AppInfo,
    shortcut: TaskSwitcherMenuItem,
    segmentIndex: Int,
    segmentCount: Int,
    added: Boolean,
    singleSelect: Boolean = false,
    onToggle: () -> Unit,
) {
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = shortcut.label,
        subtitle = shortcut.targetComponent?.takeIf { it.isNotBlank() },
        selected = if (singleSelect) false else added,
        onClick = onToggle,
        leadingContent = { Md3PickerAppLeading(app) },
        trailingMode = if (singleSelect) PickerTrailingMode.None else PickerTrailingMode.Toggle,
        onTrailingClick = if (singleSelect) null else onToggle,
        trailingChip = shortcutKindChip(shortcut.kind),
    )
}

@Composable
internal fun QuickLauncherCreateFolderScreen(
    apps: List<AppInfo>,
    activityShortcuts: List<ActivityShortcut>,
    shellCommands: List<com.slideindex.app.shell.ShellCommand>,
    folderName: String,
    folderItems: List<QuickLauncherItem>,
    onFolderNameChange: (String) -> Unit,
    onToggleFolderItem: (QuickLauncherItem, Boolean) -> Unit,
    onCreateFolder: (String, List<QuickLauncherItem>) -> Unit,
    onBack: () -> Unit,
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit,
    onBrowseActivityShortcut: () -> Unit,
    /** 「手势宏」挑选钩子（透传到文件夹内的动作列表）。 */
    onRequestRunMacro: ((GestureAction) -> Unit) -> Unit = {},
    modifier: Modifier = Modifier,
    selectedTab: Int = 0,
    searchQuery: String = "",
    onSubScreenChange: (QuickLauncherAddSubScreen) -> Unit = {},
) {
    val defaultFolderName = stringResource(R.string.quick_launcher_default_folder_name)
    val configuredActionKeys = remember(folderItems) {
        folderItems.filter { it.type == QuickLauncherItemType.ACTION }
            .mapNotNull { QuickLauncherItemCodec.parseActionPayload(it.payload)?.let(QuickLauncherItemCodec::actionKey) }
            .toSet()
    }
    val configuredAppPackages = remember(folderItems) {
        folderItems.filter { it.type == QuickLauncherItemType.APP }
            .map { it.payload }
            .toSet()
    }
    val configuredShortcutKeys = remember(folderItems) {
        folderItems.filter { it.type == QuickLauncherItemType.SHORTCUT }
            .mapNotNull { QuickLauncherItemCodec.shortcutItemKey(it) }
            .toSet()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            MiuixLabeledTextField(
                value = folderName,
                onValueChange = onFolderNameChange,
                label = stringResource(R.string.quick_launcher_folder_name),
                modifier = Modifier.fillMaxWidth(),
            )
            if (folderItems.isNotEmpty()) {
                Text(
                    text = pluralStringResource(
                        R.plurals.quick_launcher_folder_items_count,
                        folderItems.size,
                        folderItems.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (selectedTab) {
                0 -> QuickLauncherAddActionsTab(
                    searchQuery = searchQuery,
                    configuredActionKeys = configuredActionKeys,
                    onToggleItem = onToggleFolderItem,
                    onOpenExecuteShellCommand = {
                        onSubScreenChange(QuickLauncherAddSubScreen.ShellCommandConfig())
                    },
                    onOpenOpenLink = {
                        onSubScreenChange(QuickLauncherAddSubScreen.OpenLinkConfig())
                    },
                    onOpenSimulateKeyEvent = {
                        onSubScreenChange(QuickLauncherAddSubScreen.SimulateKeyEventConfig())
                    },
                    onRequestRunMacro = onRequestRunMacro,
                    singleSelect = false,
                    modifier = Modifier.fillMaxSize(),
                )
                1 -> QuickLauncherAddAppsTab(
                    searchQuery = searchQuery,
                    apps = apps,
                    configuredAppPackages = configuredAppPackages,
                    onToggle = { app, added ->
                        onToggleFolderItem(QuickLauncherItem.app(app.packageName, app.label, app.cloneUserId), added)
                    },
                    singleSelect = false,
                    modifier = Modifier.fillMaxSize(),
                )
                2 -> QuickLauncherAddShortcutsTab(
                    apps = apps,
                    searchQuery = searchQuery,
                    configuredShortcutKeys = configuredShortcutKeys,
                    activityShortcuts = activityShortcuts,
                    onToggle = onToggleFolderItem,
                    onBrowseActivityShortcut = onBrowseActivityShortcut,
                    onSubScreenChange = onSubScreenChange,
                    launchCreateShortcut = launchCreateShortcut,
                    singleSelect = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            androidx.compose.material3.Button(
                onClick = {
                    val finalName = folderName.trim().ifBlank { defaultFolderName }
                    onCreateFolder(finalName, folderItems)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.quick_launcher_create_folder),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
        }
    }
}
