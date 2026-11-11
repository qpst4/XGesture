package com.slideindex.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.overlay.honeycombRuntimeItems
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.viewmodel.HoneycombLauncherEditorViewModel
import com.slideindex.app.ui.viewmodel.HoneycombLauncherUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HoneycombLauncherEditorScreen(
    viewModel: HoneycombLauncherEditorViewModel,
    onBack: () -> Unit,
    onOpenDisplaySettings: () -> Unit,
    onAdd: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HoneycombLauncherEditorContent(
        uiState = uiState,
        onBack = onBack,
        onSaveItems = viewModel::setItems,
        onOpenDisplaySettings = onOpenDisplaySettings,
        onAdd = onAdd,
        onInteractionActiveChange = viewModel::setLayoutEditing
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HoneycombLauncherEditorScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSaveItems: (List<QuickLauncherItem>) -> Unit,
    onOpenDisplaySettings: () -> Unit,
    onAdd: () -> Unit
) {
    val uiState = HoneycombLauncherUiState(
        items = settings.honeycombLauncher.honeycombRuntimeItems(),
        displaySettings = settings.honeycombDisplay
    )
    HoneycombLauncherEditorContent(
        uiState = uiState,
        onBack = onBack,
        onSaveItems = onSaveItems,
        onOpenDisplaySettings = onOpenDisplaySettings,
        onAdd = onAdd,
        onInteractionActiveChange = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HoneycombLauncherEditorContent(
    uiState: HoneycombLauncherUiState,
    onBack: () -> Unit,
    onSaveItems: (List<QuickLauncherItem>) -> Unit,
    onOpenDisplaySettings: () -> Unit,
    onAdd: () -> Unit,
    onInteractionActiveChange: (Boolean) -> Unit
) {
    val displaySectionTitle = stringResource(R.string.honeycomb_display_settings_entry)
    val layoutSectionTitle = stringResource(R.string.honeycomb_layout_editor_title)

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.honeycomb_launcher_editor_title),
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !uiState.isLayoutEditing
    ) {
        settingsLazySmallTitle(
            key = "honeycomb-display-section",
            title = displaySectionTitle,
        )
        groupedCardItems(
            keyPrefix = "honeycomb-display-entry",
            items = listOf(
                settingsCardScopeItem("display-settings") {
                    SettingNavigationRow(
                        icon = { label -> SymbolsIcon("Tune", contentDescription = label)
                        },
                        title = stringResource(R.string.honeycomb_display_settings_entry),
                        subtitle = stringResource(R.string.honeycomb_display_settings_entry_desc),
                        onClick = onOpenDisplaySettings
                    )
                }
            )
        )
        settingsLazySmallTitle(
            key = "honeycomb-layout-section",
            title = layoutSectionTitle,
        )
        LazySettingsItem(key = "honeycomb-launcher-items") {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                HoneycombLauncherItemsSection(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp),
                    nestedScrollEnabled = false,
                    items = uiState.items,
                    display = uiState.displaySettings,
                    appsByPackage = uiState.appsByPackage,
                    onItemsChange = onSaveItems,
                    onAdd = onAdd,
                    onInteractionActiveChange = onInteractionActiveChange,
                    showSectionHeader = false,
                )
            }
        }
    }
}
