package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.shake.ShakeGestureType
import com.slideindex.app.ui.compose.collectPickerAppsAsState
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShakeIndependentAppSettingsScreen(
    perAppActions: Map<String, Map<ShakeGestureType, GestureAction>>,
    onBack: () -> Unit,
    onOpenAddApp: () -> Unit,
    onOpenConfiguredApp: (String) -> Unit,
    onRemoveAppConfig: (String) -> Unit,
) {
    val appRepository = rememberAppRepository()
    val allApps by collectPickerAppsAsState(includeClones = false)
    val isLoading = allApps.isEmpty()

    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }
    val configuredEntries = remember(perAppActions, allApps) {
        perAppActions.keys.sorted().map { packageName ->
            // 系统应用不在启动器列表里，回退按包名查询（仓库内缓存，重复调用无开销）
            (appsByPackage[packageName] ?: appRepository.lookupApp(packageName))
                ?.let { AppPackageEntry.Installed(it) }
                ?: AppPackageEntry.Missing(packageName)
        }
    }

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.shake_gestures_independent_app),
        onBack = onBack,
    ) {
        managedAppListSectionTitle(
            key = "section-configured",
            title = { stringResource(R.string.shake_gestures_per_app_configured) },
        )
        when {
            isLoading -> {
                item(key = "loading") {
                    LoadingContent(message = stringResource(R.string.loading))
                }
            }
            configuredEntries.isEmpty() -> {
                managedAppListEmpty(key = "configured-empty") {
                    stringResource(R.string.shake_gestures_per_app_empty)
                }
            }
            else -> {
                managedAppPackageRows(
                    keyPrefix = "configured",
                    entries = configuredEntries,
                    actionIcon = Icons.Default.Close,
                    actionDescription = { stringResource(R.string.shake_gestures_per_app_remove) },
                    missingIcon = Icons.Default.Block,
                    onAction = { onRemoveAppConfig(it.packageName) },
                    onRowClick = { onOpenConfiguredApp(it.packageName) },
                )
            }
        }
        managedAppListAddRow(
            title = { stringResource(R.string.shake_gestures_per_app_add) },
            subtitle = { stringResource(R.string.shake_gestures_per_app_add_desc) },
            onClick = onOpenAddApp,
        )
    }
}
