package com.slideindex.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.QuickLauncherDisplaySettings
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.slideindex.app.ui.quicklauncher.QuickLauncherPanelManagementSection
import com.slideindex.app.ui.quicklauncher.quickLauncherAppearanceCardItems
import com.slideindex.app.ui.quicklauncher.quickLauncherAppearanceSettingsSection
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.viewmodel.QuickLauncherEditorUiState
import com.slideindex.app.ui.viewmodel.QuickLauncherEditorViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickLauncherEditorScreen(
    viewModel: QuickLauncherEditorViewModel,
    onBack: () -> Unit,
    onAdd: (panelId: String, folderIndex: Int) -> Unit,
    panelId: String? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(panelId) {
        if (panelId != null) viewModel.selectPanelById(panelId)
    }
    QuickLauncherEditorContent(
        uiState = uiState,
        onBack = onBack,
        onSelectPanel = viewModel::selectPanel,
        onSavePanels = viewModel::setPanels,
        onDisplayChange = viewModel::setDisplaySettings,
        onItemsChange = viewModel::updateCurrentPanelItems,
        onInteractionActiveChange = viewModel::setGridInteractionActive,
        onAdd = { folderIndex -> onAdd(uiState.currentPanel.id, folderIndex) },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickLauncherEditorScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSavePanels: (List<QuickLauncherPanel>) -> Unit,
    onDisplayChange: (QuickLauncherDisplaySettings) -> Unit,
    onAdd: (panelId: String, folderIndex: Int) -> Unit,
) {
    val currentPanels = com.slideindex.app.launcher.QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels)
    val uiState = QuickLauncherEditorUiState(
        panels = currentPanels,
        selectedPanelIndex = 0,
        displaySettings = settings.quickLauncherDisplay,
        defaultColumns = settings.quickLauncherColumnsPerPage,
        defaultRows = settings.quickLauncherRowsPerPage,
        shellCommands = settings.shellCommands,
        activityShortcuts = settings.activityShortcuts,
    )
    QuickLauncherEditorContent(
        uiState = uiState,
        onBack = onBack,
        onSelectPanel = {},
        onSavePanels = onSavePanels,
        onDisplayChange = onDisplayChange,
        onItemsChange = { items ->
            val updated = com.slideindex.app.launcher.QuickLauncherPanelMutator.updatePanelItems(
                currentPanels,
                uiState.currentPanel.id,
                items,
            )
            onSavePanels(updated)
        },
        onInteractionActiveChange = {},
        onAdd = { folderIndex -> onAdd(uiState.currentPanel.id, folderIndex) },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickLauncherEditorContent(
    uiState: QuickLauncherEditorUiState,
    onBack: () -> Unit,
    onSelectPanel: (Int) -> Unit,
    onSavePanels: (List<QuickLauncherPanel>) -> Unit,
    onDisplayChange: (QuickLauncherDisplaySettings) -> Unit,
    onItemsChange: (List<QuickLauncherItem>) -> Unit,
    onInteractionActiveChange: (Boolean) -> Unit,
    onAdd: (folderIndex: Int) -> Unit,
) {
    val appearanceSectionTitle = stringResource(R.string.quick_launcher_appearance_section)
    val appearanceItems = quickLauncherAppearanceCardItems(
        display = uiState.displaySettings,
        enabled = true,
        onDisplayChange = onDisplayChange,
    )

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.quick_launcher_editor_title),
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !uiState.isGridInteractionActive,
    ) {
        settingsLazySmallTitle(
            key = "quick-launcher-appearance",
            title = appearanceSectionTitle,
        )
        quickLauncherAppearanceSettingsSection(
            appearanceItems = appearanceItems,
        )
        item(key = "panel_and_grid") {
            Column(modifier = Modifier.fillMaxWidth()) {
                QuickLauncherPanelManagementSection(
                    panels = uiState.panels,
                    selectedIndex = uiState.selectedPanelIndex,
                    defaultColumns = uiState.defaultColumns,
                    defaultRows = uiState.defaultRows,
                    onPanelsChange = onSavePanels,
                    onSelectedIndexChange = onSelectPanel,
                    modifier = Modifier.fillMaxWidth(),
                    // 面板切换/新建都在列表页，配置卡片里不再出现 TabRow。
                    showPanelSwitcher = false,
                    allowAddPanel = false,
                )
                SmallTitle(
                    stringResource(R.string.quick_launcher_items_section),
                    modifier = Modifier
                        .fillMaxWidth()
                        ,
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 520.dp),
                    ) {
                        key(uiState.currentPanel.id) {
                            val dummySettings = AppSettings(
                                launcher = com.slideindex.app.settings.LauncherSettings(
                                    quickLauncherDisplay = uiState.displaySettings,
                                    // 预览网格要按真实配置解析 Shell 命令 / 应用内直达的图标。
                                    shellCommands = uiState.shellCommands,
                                    activityShortcuts = uiState.activityShortcuts,
                                ),
                                quickLauncherColumnsPerPage = uiState.defaultColumns,
                                quickLauncherRowsPerPage = uiState.defaultRows,
                            )
                            QuickLauncherGridEditor(
                                settings = dummySettings,
                                items = uiState.currentPanelItems,
                                appsByPackage = uiState.appsByPackage,
                                onItemsChange = onItemsChange,
                                onAdd = onAdd,
                                onInteractionActiveChange = onInteractionActiveChange,
                                gridColumnsOverride = uiState.currentPanel.columnsPerPage,
                                gridRowsOverride = uiState.currentPanel.rowsPerPage,
                            )
                        }
                    }
                }
            }
        }
    }
}
