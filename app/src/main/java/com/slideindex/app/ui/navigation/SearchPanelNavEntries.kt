package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.SearchPanelAppSearchSettingsScreen
import com.slideindex.app.ui.SearchPanelClipboardSearchSettingsScreen
import com.slideindex.app.ui.SearchPanelContactSearchSettingsScreen
import com.slideindex.app.ui.SearchPanelFileSearchSettingsScreen
import com.slideindex.app.ui.SearchPanelPresentationLayoutSettingsScreen
import com.slideindex.app.ui.SearchPanelSettingsScreen
import com.slideindex.app.ui.SearchPanelShortcutSearchSettingsScreen
import com.slideindex.app.ui.SearchPanelSystemSettingsSearchSettingsScreen
import com.slideindex.app.ui.viewmodel.SearchEngineSettingsViewModel

fun NavEntryBuilder.searchPanelNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.SearchPanel> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val searchHistoryEntryCount by viewModel.searchHistoryEntryCount.collectAsStateWithLifecycle()
        val clipboardEntryCount by viewModel.clipboardHistoryEntryCount.collectAsStateWithLifecycle()
        SearchPanelSettingsScreen(
            settings = settings,
            searchHistoryEntryCount = searchHistoryEntryCount,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSetSearchPanelContactSearchEnabled = viewModel::setSearchPanelContactSearchEnabled,
            onSetSearchPanelFileSearchEnabled = viewModel::setSearchPanelFileSearchEnabled,
            onSetSearchPanelAppSearchEnabled = viewModel::setSearchPanelAppSearchEnabled,
            onSetSearchPanelSettingsSearchEnabled = viewModel::setSearchPanelSettingsSearchEnabled,
            onSetSearchPanelShortcutSearchEnabled = viewModel::setSearchPanelShortcutSearchEnabled,
            onSetSearchPanelClipboardSearchEnabled = viewModel::setSearchPanelClipboardSearchEnabled,
            onSetSearchPanelSectionAliases = viewModel::setSearchPanelSectionAliases,
            onOpenAppSearchSettings = { ctx.navigate(AppNavKey.SearchPanelAppSearch) },
            onOpenShortcutSearchSettings = { ctx.navigate(AppNavKey.SearchPanelShortcutSearch) },
            onOpenClipboardSearchSettings = { ctx.navigate(AppNavKey.SearchPanelClipboardSearch) },
            onOpenContactSearchSettings = { ctx.navigate(AppNavKey.SearchPanelContactSearch) },
            onOpenFileSearchSettings = { ctx.navigate(AppNavKey.SearchPanelFileSearch) },
            onOpenSystemSettingsSearchSettings = { ctx.navigate(AppNavKey.SearchPanelSystemSettingsSearch) },
            onSetSearchPanelCalculatorEnabled = viewModel::setSearchPanelCalculatorEnabled,
            onSetSearchPanelWebSuggestionsEnabled = viewModel::setSearchPanelWebSuggestionsEnabled,
            onSetSearchPanelWebSuggestionsCount = viewModel::setSearchPanelWebSuggestionsCount,
            onSetSearchPanelHistoryMaxEntries = viewModel::setSearchPanelHistoryMaxEntries,
            onClearSearchHistory = viewModel::clearSearchHistory,
            onOpenPresentationLayoutSettings = { ctx.navigate(AppNavKey.SearchPanelPresentationLayout) },
            onOpenTextSearchEngines = { ctx.navigate(AppNavKey.FloatBallSearchEngine) },
            onOpenImageSearchEngines = { ctx.navigate(AppNavKey.FloatBallImageSearchEngine) },
            onSetDefaultEngineId = viewModel::setDefaultEngineId,
        )
    }

    hiltEntry<AppNavKey.SearchPanelPresentationLayout> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        SearchPanelPresentationLayoutSettingsScreen(
            settings = overlaySettings.toMinimalAppSettings(),
            onBack = { ctx.navigateBackTo(AppNavKey.SearchPanel) },
            onSetSearchPanelBarPosition = viewModel::setSearchPanelBarPosition,
            onSetSearchPanelListOrder = viewModel::setSearchPanelListOrder,
            onSetSearchPanelAppDisplayStyle = viewModel::setSearchPanelAppDisplayStyle,
            onSetSearchEngineGridColumns = viewModel::setGridColumns,
            onSetSearchEngineGridRows = viewModel::setGridRows,
            onSetSearchEngineShowLabels = viewModel::setShowLabels,
            onSetSearchEngineIconShape = viewModel::setIconShape,
            onSetSearchPanelBackgroundStyle = viewModel::setSearchPanelBackgroundStyle,
            onSetSearchPanelBlurRadiusDp = viewModel::setSearchPanelBlurRadiusDp,
            onSetSearchPanelDimPercent = viewModel::setSearchPanelDimPercent,
            onSetSearchPanelInputBehavior = viewModel::setSearchPanelInputBehavior,
            onSetSearchPanelEnterAction = viewModel::setSearchPanelEnterAction,
        )
    }

    hiltEntry<AppNavKey.SearchPanelAppSearch> {
        SearchPanelAppSearchSettingsScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.SearchPanel) },
        )
    }

    hiltEntry<AppNavKey.SearchPanelShortcutSearch> {
        SearchPanelShortcutSearchSettingsScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.SearchPanel) },
        )
    }

    hiltEntry<AppNavKey.SearchPanelClipboardSearch> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val clipboardEntryCount by viewModel.clipboardHistoryEntryCount.collectAsStateWithLifecycle()
        SearchPanelClipboardSearchSettingsScreen(
            clipboardEntryCount = clipboardEntryCount,
            onClearClipboardHistory = viewModel::clearClipboardHistory,
            onBack = { ctx.navigateBackTo(AppNavKey.SearchPanel) },
        )
    }

    hiltEntry<AppNavKey.SearchPanelContactSearch> {
        SearchPanelContactSearchSettingsScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.SearchPanel) },
        )
    }

    hiltEntry<AppNavKey.SearchPanelSystemSettingsSearch> {
        SearchPanelSystemSettingsSearchSettingsScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.SearchPanel) },
        )
    }

    hiltEntry<AppNavKey.SearchPanelFileSearch> {
        val viewModel: SearchEngineSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        SearchPanelFileSearchSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.SearchPanel) },
            onSetFileTypesEnabled = viewModel::setSearchPanelFileTypesEnabled,
            onSetShowFolders = viewModel::setSearchPanelFileShowFolders,
            onSetShowSystemFiles = viewModel::setSearchPanelFileShowSystemFiles,
            onSetFilePreviewsEnabled = viewModel::setSearchPanelFilePreviewsEnabled,
            onSetFolderWhitelist = viewModel::setSearchPanelFileFolderWhitelist,
            onSetFolderBlacklist = viewModel::setSearchPanelFileFolderBlacklist,
        )
    }
}
