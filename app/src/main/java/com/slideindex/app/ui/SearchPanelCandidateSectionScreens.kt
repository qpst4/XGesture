package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.searchpanel.ContactPermissionTrampolineActivity
import com.slideindex.app.search.contacts.ContactSearchIndex
import com.slideindex.app.search.shortcuts.ShortcutSearchIndex
import com.slideindex.app.search.settings.SystemSettingsSearchIndex
import com.slideindex.app.util.AppShortcutLoader
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsHintText
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Settings

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchPanelAppSearchSettingsScreen(
    onBack: () -> Unit,
) {
    val desc = stringResource(R.string.search_panel_app_search_desc)
    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_section_apps),
        subtitle = desc,
        onBack = onBack,
    ) {
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchPanelContactSearchSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContactSearchIndex.hasPermission(context))
    }
    val desc = stringResource(R.string.search_panel_contact_search_desc)
    val permissionSectionTitle = stringResource(R.string.search_panel_contact_permission_title)

    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_section_contacts),
        subtitle = desc,
        onBack = onBack,
    ) {
        item(key = "contact-permission-title") {
            SmallTitle(permissionSectionTitle)
        }
        groupedCardItems(
            keyPrefix = "contact-permission",
            items = buildList {
                add(
                    settingsCardScopeItem("permission") {
                        SettingNavigationRow(
                            icon = { label -> Icon(MiuixIcons.Contacts, contentDescription = label) },
                            title = if (hasPermission) {
                                stringResource(R.string.search_panel_contact_permission_granted)
                            } else {
                                stringResource(R.string.search_panel_contact_permission_prompt)
                            },
                            subtitle = permissionSectionTitle,
                            onClick = {
                                ContactPermissionTrampolineActivity.launch(context) {
                                    hasPermission = ContactSearchIndex.hasPermission(context)
                                }
                            },
                        )
                    },
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchPanelShortcutSearchSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var indexCount by remember { mutableStateOf<Int?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val desc = stringResource(R.string.search_panel_shortcut_search_desc)
    val indexSectionTitle = stringResource(R.string.search_panel_shortcut_index_title)
    val indexHint = stringResource(R.string.search_panel_shortcut_index_hint)

    LaunchedEffect(refreshKey) {
        indexCount = null
        indexCount = withContext(Dispatchers.IO) {
            val apps = OverlayDependencyAccess.overlayDependencies(context)
                ?.appRepository
                ?.getCachedAppsForSearch()
                .orEmpty()
            ShortcutSearchIndex.ensureLoaded(context, apps).size
        }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_section_shortcuts),
        subtitle = desc,
        onBack = onBack,
    ) {
        item(key = "shortcut-index-title") {
            SmallTitle(indexSectionTitle)
        }
        groupedCardItems(
            keyPrefix = "shortcut-index",
            items = buildList {
                add(
                    settingsCardScopeItem("index-refresh") {
                        SettingNavigationRow(
                            icon = { label -> Icon(Icons.Default.Bolt, contentDescription = label) },
                            title = stringResource(R.string.search_panel_shortcut_index_refresh),
                            subtitle = indexCount?.let {
                                pluralStringResource(R.plurals.search_panel_shortcut_index_count, it, it)
                            } ?: stringResource(R.string.search_panel_settings_index_loading),
                            onClick = {
                                ShortcutSearchIndex.invalidate()
                                AppShortcutLoader.invalidateShortcutCatalog()
                                refreshKey++
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("index-hint") {
                        SettingsHintText(indexHint)
                    },
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchPanelClipboardSearchSettingsScreen(
    clipboardEntryCount: Int,
    onClearClipboardHistory: () -> Unit,
    onBack: () -> Unit,
) {
    var showClearDialog by remember { mutableStateOf(false) }
    val desc = stringResource(R.string.search_panel_clipboard_search_desc)
    val indexSectionTitle = stringResource(R.string.search_panel_clipboard_index_title)
    val indexHint = stringResource(R.string.search_panel_clipboard_index_hint)

    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_section_clipboard),
        subtitle = desc,
        onBack = onBack,
    ) {
        item(key = "clipboard-index-title") {
            SmallTitle(indexSectionTitle)
        }
        groupedCardItems(
            keyPrefix = "clipboard-index",
            items = buildList {
                add(
                    settingsCardScopeItem("clear-history") {
                        SettingLinkRow(
                            title = stringResource(R.string.search_panel_clipboard_clear),
                            subtitle = pluralStringResource(
                                R.plurals.search_panel_clipboard_index_count,
                                clipboardEntryCount,
                                clipboardEntryCount,
                            ),
                            enabled = clipboardEntryCount > 0,
                            onClick = { showClearDialog = true },
                        )
                    },
                )
            },
        )
        settingsLazyHint(key = "clipboard-index-hint", text = indexHint)
    }

    MiuixConfirmDialog(
        show = showClearDialog,
        onDismissRequest = { showClearDialog = false },
        title = stringResource(R.string.search_panel_clipboard_clear_confirm_title),
        message = stringResource(R.string.search_panel_clipboard_clear_confirm_message),
        onConfirm = {
            showClearDialog = false
            onClearClipboardHistory()
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchPanelSystemSettingsSearchSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var indexCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        indexCount = withContext(Dispatchers.IO) {
            SystemSettingsSearchIndex.ensureLoaded(context).size
        }
    }
    val desc = stringResource(R.string.search_panel_settings_search_desc)
    val indexSectionTitle = stringResource(R.string.search_panel_settings_index_title)

    LaunchedEffect(indexCount) {
        if (indexCount != null) return@LaunchedEffect
        indexCount = withContext(Dispatchers.IO) {
            SystemSettingsSearchIndex.ensureLoaded(context).size
        }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_settings_search_title),
        subtitle = desc,
        onBack = onBack,
    ) {
        item(key = "settings-index-title") {
            SmallTitle(indexSectionTitle)
        }
        groupedCardItems(
            keyPrefix = "settings-index",
            items = buildList {
                add(
                    settingsCardScopeItem("index-refresh") {
                        SettingNavigationRow(
                            icon = { label -> Icon(MiuixIcons.Settings, contentDescription = label) },
                            title = stringResource(R.string.search_panel_settings_index_refresh),
                            subtitle = indexCount?.let {
                                pluralStringResource(R.plurals.search_panel_settings_index_count, it, it)
                            } ?: stringResource(R.string.search_panel_settings_index_loading),
                            onClick = {
                                SystemSettingsSearchIndex.invalidate()
                                indexCount = null
                            },
                        )
                    },
                )
            },
        )
    }
}
