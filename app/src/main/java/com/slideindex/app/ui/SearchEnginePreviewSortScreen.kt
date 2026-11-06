@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.ui.searchengine.SearchEngineSortableGrid
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazyTipCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchEnginePreviewSortScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onReorder: (List<SearchEngineConfig>) -> Unit,
) {
    val panelEngines = remember(settings.searchEngines) {
        SearchEngineStore.textPickPanelEngines(settings.searchEngines)
    }
    val emptyHint = stringResource(R.string.search_engine_preview_sort_empty)
    val sortHint = stringResource(R.string.search_engine_preview_sort_hint)

    val tipText = "${stringResource(R.string.search_engine_settings_preview_mode_summary)}\n$sortHint"

    SettingsScreenScaffold(
        title = stringResource(R.string.search_engine_preview_sort_title),
        onBack = onBack,
    ) {
        settingsLazyTipCard(key = "search-preview-sort-hint", text = tipText)
        if (panelEngines.isEmpty()) {
            settingsLazyHint(key = "search-preview-sort-empty", text = emptyHint)
        } else {
            LazySettingsItem(key = "search-preview-sort-grid") {
                SearchEngineSortableGrid(
                    engines = panelEngines,
                    columns = settings.searchEngineGridColumns,
                    rows = settings.searchEngineGridRows,
                    showLabels = settings.searchEngineShowLabels,
                    onOrderChange = onReorder,
                    modifier = Modifier.padding(vertical = 8.dp),
                    iconShape = settings.searchEngineIconShape,
                )
            }
        }
    }
}
