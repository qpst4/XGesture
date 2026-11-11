package com.slideindex.app.ui

import top.yukonga.miuix.kmp.basic.SmallTitle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.AppTarget
import com.slideindex.app.ui.compose.collectPickerAppsAsState
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.util.PinyinHelper

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HiddenAppsScreen(
    hiddenPackages: Set<String>,
    onBack: () -> Unit,
    onHideApp: (String) -> Unit,
    onUnhideApp: (String) -> Unit,
    titleRes: Int = R.string.hidden_apps_title,
    descriptionRes: Int = R.string.hidden_apps_desc
) {
    // 隐藏列表按目标存（包名\u0002userId），所以这里要能列出分身。
    val allApps by collectPickerAppsAsState(includeClones = true)
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(allApps) {
        if (allApps.isNotEmpty()) isLoading = false
    }

    val appsByTarget = remember(allApps) {
        allApps.associateBy { AppTarget.encode(it.packageName, it.cloneUserId) }
    }
    // 每个隐藏项带自己的目标键：列表里显示干净包名，删除时用键（主实例 = 裸包名，分身带 userId）。
    val hiddenEntries = remember(hiddenPackages, allApps) {
        hiddenPackages.sorted().map { key ->
            val target = AppTarget.parse(key)
            key to (
                appsByTarget[key]?.let { AppPackageEntry.Installed(it) }
                    ?: AppPackageEntry.Missing(target.packageName)
                )
        }
    }
    val addableApps = remember(allApps, hiddenPackages, searchQuery) {
        val query = searchQuery.trim().lowercase()
        allApps
            .filter { AppTarget.encode(it.packageName, it.cloneUserId) !in hiddenPackages }
            .filter { app ->
                if (query.isEmpty()) return@filter true
                app.label.lowercase().contains(query) ||
                    app.packageName.lowercase().contains(query) ||
                    app.pinyinKey.contains(query)
            }
            .sortedBy { it.pinyinKey }
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(titleRes),
        pageHint = stringResource(descriptionRes),
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack
    ) {
        item(key = "section-hidden") {
            SmallTitle(
                text = stringResource(R.string.hidden_apps_section_hidden),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (hiddenEntries.isEmpty()) {
            item(key = "hidden-empty") {
                Text(
                    text = stringResource(R.string.hidden_apps_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
                )
            }
        } else {
            items(
                hiddenEntries.size,
                key = { hiddenEntries[it].first }
            ) { index ->
                val (targetKey, entry) = hiddenEntries[index]
                AppPackageListRow(
                    entry = entry,
                    segmentIndex = index,
                    segmentCount = hiddenEntries.size,
                    actionIcon = Icons.Default.Close,
                    actionDescription = stringResource(R.string.hidden_apps_unhide),
                    missingIcon = Icons.Default.VisibilityOff,
                    onAction = { onUnhideApp(targetKey) }
                )
            }
        }
        item(key = "section-add") {
            SmallTitle(stringResource(R.string.hidden_apps_section_add), modifier = Modifier.fillMaxWidth())
        }
        when {
            isLoading -> {
                item(key = "loading") {
                    LoadingContent(
                        message = stringResource(R.string.loading), modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            addableApps.isEmpty() -> {
                item(key = "addable-empty") {
                    Text(
                        text = if (searchQuery.isBlank()) {
                            stringResource(R.string.hidden_apps_all_hidden)
                        } else {
                            stringResource(R.string.no_apps)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp)
                    )
                }
            }
            else -> {
                items(
                    addableApps.size,
                    key = { AppTarget.encode(addableApps[it].packageName, addableApps[it].cloneUserId) }
                ) { index ->
                    val app = addableApps[index]
                    AppPackageListRow(
                        entry = AppPackageEntry.Installed(app),
                        segmentIndex = index,
                        segmentCount = addableApps.size,
                        actionIcon = Icons.Default.Add,
                        actionDescription = stringResource(R.string.hidden_apps_hide),
                        missingIcon = Icons.Default.VisibilityOff,
                        onAction = { onHideApp(AppTarget.encode(app.packageName, app.cloneUserId)) }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCardScope.HiddenAppsEntryCard(
    hiddenCount: Int,
    onClick: () -> Unit,
    titleRes: Int = R.string.hidden_apps_entry_title,
    descriptionRes: Int = R.string.hidden_apps_entry_desc
) {
    val subtitle = if (hiddenCount > 0) {
        stringResource(R.string.hidden_apps_entry_count, hiddenCount)
    } else {
        stringResource(descriptionRes)
    }
    SettingNavigationRow(
        icon = { label -> SymbolsIcon("VisibilityOff", contentDescription = label) },
        title = stringResource(titleRes),
        subtitle = subtitle,
        onClick = onClick
    )
}
