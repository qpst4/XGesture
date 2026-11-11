package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.ui.RingLauncherSettingsScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel

fun NavEntryBuilder.ringLauncherNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.RingLauncherSettings> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        RingLauncherSettingsScreen(
            appSettings = appSettings,
            onBack = { ctx.navigateBack() },
            onSettingsChange = { axis, settings ->
                viewModel.setFvRingLauncherSettings(axis, settings)
            },
            onLinkAppearanceAxesChange = { enabled, axis, mergeDirection ->
                viewModel.setFvRingLauncherLinkAppearanceAxes(enabled, axis, mergeDirection)
            },
            onLinkSlotAxesChange = { enabled, axis, mergeDirection ->
                viewModel.setFvRingLauncherLinkSlotAxes(enabled, axis, mergeDirection)
            },
        )
    }
}
