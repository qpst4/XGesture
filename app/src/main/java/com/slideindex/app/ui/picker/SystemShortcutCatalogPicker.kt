package com.slideindex.app.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.shortcuts.DynamicShortcutsManager
import com.slideindex.app.ui.Md3PickerAppLeading
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.Md3PickerSectionHeader
import com.slideindex.app.ui.PickerListGroupSpacing
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.ShortcutScanProgressContent
import com.slideindex.app.ui.ShortcutTestMode
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.ShortcutScanPhase
import com.slideindex.app.util.ShortcutScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class LoadedShortcutCatalog(
    val catalog: AppShortcutLoader.ShortcutCatalog?,
    val loading: Boolean,
    val scanProgress: ShortcutScanProgress?,
    val source: String = "",
    val total: Int = 0,
    val error: String? = null,
)

data class FilteredShortcutCatalog(
    val createHosts: List<AppShortcutLoader.CreateShortcutHost>,
    val groups: List<AppShortcutLoader.AppShortcutGroup>,
)

@Composable
fun rememberLoadedShortcutCatalog(
    apps: List<AppInfo>,
    enabled: Boolean = true,
): LoadedShortcutCatalog {
    val context = LocalContext.current
    remember(context) {
        DynamicShortcutsManager.ensureInitialized(context)
        true
    }
    val selectedTestMode by DynamicShortcutsManager.testModeFlow.collectAsState()
    val selectedPrivilegeMode by DynamicShortcutsManager.privilegeSubModeFlow.collectAsState()
    val pinnedRecords by DynamicShortcutsManager.pinnedRecordsFlow.collectAsState()

    var catalog by remember { mutableStateOf<AppShortcutLoader.ShortcutCatalog?>(null) }
    var loading by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf<ShortcutScanProgress?>(null) }
    var source by remember { mutableStateOf("") }
    var total by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(apps, enabled, selectedTestMode, selectedPrivilegeMode, pinnedRecords.size) {
        if (!enabled || apps.isEmpty()) {
            loading = false
            scanProgress = null
            return@LaunchedEffect
        }
        loading = true
        when (selectedTestMode) {
            ShortcutTestMode.STATIC -> {
                scanProgress = ShortcutScanProgress(phase = ShortcutScanPhase.APPS)
                try {
                    val staticCatalog = withContext(Dispatchers.IO) {
                        AppShortcutLoader.loadShortcutCatalog(
                            context = context,
                            apps = apps,
                            forceRefresh = true,
                            onProgress = { scanProgress = it },
                        )
                    }
                    catalog = staticCatalog
                    source = "静态"
                    total = staticCatalog.groups.sumOf { it.shortcuts.size }
                    error = null
                } catch (_: Exception) {
                    catalog = AppShortcutLoader.ShortcutCatalog(createHosts = emptyList())
                    error = "静态快捷方式加载失败"
                } finally {
                    loading = false
                    scanProgress = null
                }
            }

            ShortcutTestMode.PRIVILEGE -> {
                val resolvedMode = if (selectedPrivilegeMode == PrivilegeMode.ROOT) {
                    ActionPickerShortcutMode.ROOT
                } else {
                    ActionPickerShortcutMode.SHIZUKU
                }
                scanProgress = ShortcutScanProgress(
                    phase = if (resolvedMode == ActionPickerShortcutMode.ROOT) {
                        ShortcutScanPhase.SYSTEM_XML
                    } else {
                        ShortcutScanPhase.DUMPSYS
                    },
                )
                try {
                    val result = withContext(Dispatchers.IO) {
                        loadActionPickerShortcutService(
                            context = context,
                            apps = apps,
                            mode = resolvedMode,
                        )
                    }
                    catalog = result.catalog
                    source = result.source
                    total = result.total
                    error = result.error
                } catch (_: Exception) {
                    catalog = AppShortcutLoader.ShortcutCatalog(createHosts = emptyList())
                    error = "快捷方式加载失败"
                } finally {
                    loading = false
                    scanProgress = null
                }
            }

            ShortcutTestMode.DYNAMIC -> {
                scanProgress = ShortcutScanProgress(phase = ShortcutScanPhase.FINALIZING)
                try {
                    val result = withContext(Dispatchers.IO) {
                        loadActionPickerDynamicPinnedShortcuts(
                            context = context,
                            apps = apps,
                        )
                    }
                    catalog = result.catalog
                    source = result.source
                    total = result.total
                    error = result.error
                } catch (_: Exception) {
                    catalog = AppShortcutLoader.ShortcutCatalog(createHosts = emptyList())
                    error = "桌面固化快捷方式加载失败"
                } finally {
                    loading = false
                    scanProgress = null
                }
            }
        }
    }

    return LoadedShortcutCatalog(
        catalog = catalog,
        loading = loading,
        scanProgress = scanProgress,
        source = source,
        total = total,
        error = error,
    )
}

@Composable
private fun ShortcutCreateSection(
    hosts: List<AppShortcutLoader.CreateShortcutHost>,
    appsByPackage: Map<String, AppInfo>,
    searchQuery: String,
    onCreateHostClick: (AppShortcutLoader.CreateShortcutHost) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val forceExpanded = searchQuery.isNotBlank()
    val showHosts = expanded || forceExpanded

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !forceExpanded) { expanded = !expanded }
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallTitle(
                text = stringResource(R.string.create_shortcut),
                modifier = Modifier.weight(1f),
                textColor = MiuixTheme.colorScheme.primary,
            )
            Icon(
                imageVector = if (showHosts) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = if (showHosts) "收起创建快捷方式" else "展开创建快捷方式",
            )
        }
        if (showHosts) {
            hosts.forEachIndexed { index, host ->
                val app = appsByPackage[host.packageName]
                Md3PickerListRow(
                    segmentIndex = index,
                    segmentCount = hosts.size,
                    title = host.label,
                    subtitle = stringResource(R.string.create_shortcut_tap_hint),
                    selected = false,
                    onClick = { onCreateHostClick(host) },
                    leadingContent = {
                        if (app != null) {
                            Md3PickerAppLeading(app)
                        } else {
                            Md3PickerIconLeading(
                                icon = Icons.AutoMirrored.Filled.Shortcut,
                                selected = false,
                            )
                        }
                    },
                    trailingMode = PickerTrailingMode.Icon,
                    trailingIcon = Icons.AutoMirrored.Filled.Shortcut,
                    trailingIconDescription = stringResource(R.string.create_shortcut),
                )
            }
            Spacer(modifier = Modifier.height(PickerListGroupSpacing))
        }
    }
}

fun filterShortcutCatalog(
    catalog: AppShortcutLoader.ShortcutCatalog?,
    searchQuery: String,
): FilteredShortcutCatalog {
    val query = searchQuery.trim().lowercase()
    val createHosts = catalog?.createHosts.orEmpty()
    val shortcutGroups = catalog?.groups.orEmpty()
    val filteredCreateHosts = createHosts.filter { host ->
        query.isEmpty() ||
            host.label.lowercase().contains(query) ||
            host.packageName.lowercase().contains(query) ||
            host.pinyinKey.contains(query)
    }.sortedBy { it.pinyinKey }
    val filteredGroups = shortcutGroups.mapNotNull { group ->
        val appMatches = query.isEmpty() ||
            group.app.label.lowercase().contains(query) ||
            group.app.packageName.lowercase().contains(query) ||
            group.app.pinyinKey.contains(query)
        val matchedShortcuts = group.shortcuts.filter { shortcut ->
            query.isEmpty() ||
                appMatches ||
                shortcut.label.lowercase().contains(query) ||
                (shortcut.shortcutId?.lowercase()?.contains(query) == true)
        }
        if (matchedShortcuts.isEmpty()) null else group.copy(shortcuts = matchedShortcuts)
    }
    return FilteredShortcutCatalog(
        createHosts = filteredCreateHosts,
        groups = filteredGroups,
    )
}

fun LazyListScope.systemShortcutCatalogItems(
    filtered: FilteredShortcutCatalog,
    appsByPackage: Map<String, AppInfo>,
    loading: Boolean,
    scanProgress: ShortcutScanProgress?,
    loadingItemKey: String = "shortcut-catalog-loading",
    emptyItemKey: String = "shortcut-catalog-empty",
    searchQuery: String = "",
    onCreateHostClick: (AppShortcutLoader.CreateShortcutHost) -> Unit,
    shortcutRowContent: @Composable (
        group: AppShortcutLoader.AppShortcutGroup,
        shortcut: TaskSwitcherMenuItem,
        segmentIndex: Int,
        segmentCount: Int,
    ) -> Unit,
) {
    if (loading) {
        item(key = loadingItemKey) {
            ShortcutScanProgressContent(
                progress = scanProgress,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }
    if (filtered.createHosts.isEmpty() && filtered.groups.isEmpty()) {
        item(key = emptyItemKey) {
            Text(
                text = stringResource(R.string.shortcut_kind_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }
    if (filtered.createHosts.isNotEmpty()) {
        item(key = "shortcut-catalog-create-section") {
            ShortcutCreateSection(
                hosts = filtered.createHosts,
                appsByPackage = appsByPackage,
                searchQuery = searchQuery,
                onCreateHostClick = onCreateHostClick,
            )
        }
    }
    item(key = "shortcut-catalog-header-launch") {
        Md3PickerSectionHeader(stringResource(R.string.launch_shortcut))
    }
    if (filtered.groups.isEmpty()) {
        item(key = "shortcut-catalog-launch-empty") {
            val activeSourceMode by DynamicShortcutsManager.testModeFlow.collectAsState()
            val emptyHint = when {
                searchQuery.isNotBlank() -> stringResource(R.string.shortcut_kind_empty)
                activeSourceMode == ShortcutTestMode.DYNAMIC ->
                    "当前为「桌面固化」模式，尚未导入任何固化的快捷方式。请先前往「扩展 -> 测试快捷方式 -> 桌面固化」完成导入，或切换为静态清单/提权扫描模式。"
                activeSourceMode == ShortcutTestMode.PRIVILEGE ->
                    "当前为「提权扫描」模式，暂无可用的快捷方式。请先在「扩展 -> 测试快捷方式 -> 提权扫描」中确保 Root/Shizuku 授权正常，或切换为静态清单模式。"
                else -> stringResource(R.string.shortcut_kind_empty)
            }
            Text(
                text = emptyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    } else {
        filtered.groups.forEach { group ->
            val segmentCount = group.shortcuts.size + 1
            item(key = "shortcut-catalog-app-${group.app.packageName}") {
                Md3PickerListRow(
                    segmentIndex = 0,
                    segmentCount = segmentCount,
                    title = group.app.label,
                    subtitle = group.app.packageName,
                    selected = false,
                    onClick = null,
                    enabled = false,
                    leadingContent = { Md3PickerAppLeading(group.app) },
                )
            }
            items(
                count = group.shortcuts.size,
                key = { idx ->
                    val shortcut = group.shortcuts[idx]
                    "${group.app.packageName}:${shortcut.shortcutId ?: shortcut.label}"
                },
            ) { index ->
                shortcutRowContent(
                    group,
                    group.shortcuts[index],
                    index + 1,
                    segmentCount,
                )
            }
            item(key = "shortcut-catalog-gap-${group.app.packageName}") {
                Spacer(modifier = Modifier.height(PickerListGroupSpacing))
            }
        }
    }
}
