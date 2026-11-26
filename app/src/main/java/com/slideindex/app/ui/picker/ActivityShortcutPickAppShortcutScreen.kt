package com.slideindex.app.ui.picker

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.ShortcutIconCapture
import com.slideindex.app.activity.toActivityShortcut
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.service.CreateShortcutTrampoline
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.shortcutKindChip
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.util.AppShortcutLoader

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActivityShortcutPickAppShortcutScreen(
    existingIdentityKeys: Set<String>,
    onBack: () -> Unit,
    onOpenPresetShortcuts: () -> Unit,
    onAddShortcut: (ActivityShortcut) -> Unit,
) {
    val appRepository = rememberAppRepository()
    var apps by remember { mutableStateOf(appRepository.getCachedApps()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (apps.isEmpty()) {
            apps = appRepository.loadApps(force = true)
        }
    }

    val loadedCatalog = rememberLoadedShortcutCatalog(apps = apps, enabled = true)
    val filtered = remember(loadedCatalog.catalog, query) {
        filterShortcutCatalog(
            loadedCatalog.catalog ?: AppShortcutLoader.ShortcutCatalog(emptyList()),
            query,
        )
    }
    val appsByPackage = remember(apps) { apps.associateBy { it.packageName } }
    val context = LocalContext.current
    // 这条路径以前只列"已注册的启动快捷方式"，把"创建快捷方式"入口整个清空了；
    // 手势 / 快速启动器 / 圆环的挑选器都有它，这里补齐：点宿主 → 起宿主自己的创建界面
    // → 结果经 trampoline 回传 → 直接加入列表（重复的不再加）。
    val onCreateHostClick: (AppShortcutLoader.CreateShortcutHost) -> Unit = remember(
        context,
        existingIdentityKeys,
        onAddShortcut,
    ) {
        { host ->
            CreateShortcutTrampoline.launch(
                context = context,
                host = host,
                onPrepare = {},
                onResult = { created ->
                    val capturedIconPath = ShortcutIconCapture.captureFromIntent(
                        context,
                        created?.shortcutIntent,
                    )
                    val base = created?.toActivityShortcutOrNull()
                    val shortcut = if (base != null && capturedIconPath != null) {
                        base.copy(iconPath = capturedIconPath)
                    } else {
                        base
                    }
                    if (shortcut == null) {
                        Toast.makeText(
                            context,
                            R.string.float_ball_action_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else if (shortcut.identityKey() !in existingIdentityKeys) {
                        onAddShortcut(shortcut)
                    }
                },
            )
        }
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.activity_shortcut_pick_app_shortcut_title),
        pageHint = stringResource(R.string.activity_shortcut_pick_app_shortcut_desc),
        searchQuery = query,
        onSearchQueryChange = { query = it },
        onBack = onBack,
        hintResId = R.string.search_hint,
    ) {
        if (query.isBlank()) {
            shortcutFolderCardsSection(
                activityShortcutsCount = 0,
                onOpenMyShortcuts = {},
                onOpenPresetShortcuts = onOpenPresetShortcuts,
                showMyShortcuts = false,
            )
        }
        when {
            loadedCatalog.loading && filtered.groups.isEmpty() && filtered.createHosts.isEmpty() -> {
                systemShortcutCatalogItems(
                    filtered = filtered,
                    appsByPackage = appsByPackage,
                    loading = true,
                    scanProgress = loadedCatalog.scanProgress,
                    searchQuery = query,
                    loadingItemKey = "loading",
                    onCreateHostClick = onCreateHostClick,
                    shortcutRowContent = { _, _, _, _ -> },
                )
            }
            filtered.groups.isEmpty() && filtered.createHosts.isEmpty() -> {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.activity_shortcut_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                    )
                }
            }
            else -> {
                systemShortcutCatalogItems(
                    filtered = filtered,
                    appsByPackage = appsByPackage,
                    loading = false,
                    scanProgress = null,
                    searchQuery = query,
                    onCreateHostClick = onCreateHostClick,
                    shortcutRowContent = { group, shortcut, segmentIndex, segmentCount ->
                        ActivityShortcutPickAppShortcutRow(
                            group = group,
                            shortcut = shortcut,
                            segmentIndex = segmentIndex,
                            segmentCount = segmentCount,
                            existingIdentityKeys = existingIdentityKeys,
                            onAddShortcut = onAddShortcut,
                        )
                    },
                )
            }
        }
    }
}

/**
 * `ACTION_CREATE_SHORTCUT` 回传的结果 → 应用内直达条目。
 *
 * 优先用 intent URI（宿主自己拼好的启动意图，最保真）；没有 URI 时退回组件名。
 * 两个都没有就返回 null（调用方提示失败）。
 */
private fun AppShortcutLoader.CreatedShortcut.toActivityShortcutOrNull(): ActivityShortcut? {
    val intentUri = intentUri?.takeIf { it.isNotBlank() }
    if (intentUri != null) {
        return ActivityShortcut.intent(
            label = label.ifBlank { hostPackageName },
            packageName = hostPackageName,
            intentUris = listOf(intentUri),
        )
    }
    val flat = componentFlat?.takeIf { it.isNotBlank() } ?: return null
    val pkg = flat.substringBefore('/')
    val cls = flat.substringAfter('/', missingDelimiterValue = "")
    if (pkg.isBlank() || cls.isBlank()) return null
    return ActivityShortcut.component(
        label = label.ifBlank { cls },
        packageName = pkg,
        activityClassName = cls,
    )
}

@Composable
private fun ActivityShortcutPickAppShortcutRow(
    group: AppShortcutLoader.AppShortcutGroup,
    shortcut: TaskSwitcherMenuItem,
    segmentIndex: Int,
    segmentCount: Int,
    existingIdentityKeys: Set<String>,
    onAddShortcut: (ActivityShortcut) -> Unit,
) {
    val managed = shortcut.toActivityShortcut(
        packageName = group.app.packageName,
        label = shortcut.label.ifBlank { group.app.label },
    )
    val key = managed.identityKey()
    val alreadyAdded = key.isNotBlank() && key in existingIdentityKeys
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = shortcut.label,
        selected = alreadyAdded,
        onClick = {
            if (!alreadyAdded) {
                onAddShortcut(managed)
            }
        },
        leadingContent = {
            Md3PickerIconLeading(
                icon = Icons.AutoMirrored.Filled.Shortcut,
                selected = alreadyAdded,
            )
        },
        trailingMode = PickerTrailingMode.Toggle,
        trailingChip = shortcutKindChip(shortcut.kind),
    )
}
