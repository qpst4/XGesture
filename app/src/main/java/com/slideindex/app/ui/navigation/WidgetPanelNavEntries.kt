package com.slideindex.app.ui.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.ui.WidgetPanelSettingsScreen
import com.slideindex.app.ui.viewmodel.WidgetPanelEditorViewModel

fun NavEntryBuilder.widgetPanelNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.WidgetPanel> {
        val viewModel: WidgetPanelEditorViewModel = hiltViewModel()
        WidgetPanelSettingsScreen(
            viewModel = viewModel,
            onBack = { ctx.navigateBack() },
            onPreviewWidget = { ctx.showWidgetPanelPreview() },
        )
    }
}
