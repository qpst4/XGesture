package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.ui.compose.collectPickerAppsAsState
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShakeGestureBlacklistScreen(
    blacklistedPackages: Set<String>,
    onBack: () -> Unit,
    onOpenAddApp: () -> Unit,
    onRemoveBlacklistedApp: (String) -> Unit,
    titleRes: Int = R.string.shake_gestures_app_blacklist,
    descriptionRes: Int = R.string.shake_gestures_app_blacklist_desc,
    blockedSectionTitleRes: Int = R.string.shake_gestures_blacklist_section_blocked,
    emptyRes: Int = R.string.shake_gestures_blacklist_empty,
    removeActionDescriptionRes: Int = R.string.shake_gestures_blacklist_remove,
    addSectionTitleRes: Int = R.string.shake_gestures_blacklist_section_add,
    addSectionSubtitleRes: Int? = null,
    showPageHint: Boolean = true,
) {
    val appRepository = rememberAppRepository()
    val allApps by collectPickerAppsAsState(includeClones = false)
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(allApps) {
        if (allApps.isNotEmpty()) isLoading = false
    }

    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }
    val blacklistedEntries = remember(blacklistedPackages, allApps) {
        blacklistedPackages.sorted().map { packageName ->
            // 系统应用不在启动器列表里，回退按包名查询（仓库内缓存，重复调用无开销）
            (appsByPackage[packageName] ?: appRepository.lookupApp(packageName))
                ?.let { AppPackageEntry.Installed(it) }
                ?: AppPackageEntry.Missing(packageName)
        }
    }

    SettingsLazyScreenScaffold(
        title = stringResource(titleRes),
        pageHint = if (showPageHint) stringResource(descriptionRes) else null,
        onBack = onBack,
    ) {
        managedAppListSectionTitle(
            key = "section-blacklisted",
            title = { stringResource(blockedSectionTitleRes) },
        )
        when {
            isLoading -> {
                item(key = "loading") {
                    LoadingContent(message = stringResource(R.string.loading))
                }
            }
            blacklistedEntries.isEmpty() -> {
                managedAppListEmpty(key = "blacklisted-empty") {
                    stringResource(emptyRes)
                }
            }
            else -> {
                managedAppPackageRows(
                    keyPrefix = "blacklisted",
                    entries = blacklistedEntries,
                    actionIcon = Icons.Default.Close,
                    actionDescription = { stringResource(removeActionDescriptionRes) },
                    missingIcon = Icons.Default.Block,
                    onAction = { onRemoveBlacklistedApp(it.packageName) },
                )
            }
        }
        managedAppListAddRow(
            title = { stringResource(addSectionTitleRes) },
            subtitle = {
                addSectionSubtitleRes?.let { stringResource(it) } ?: ""
            },
            onClick = onOpenAddApp,
        )
    }
}
