package com.slideindex.app.ui.picker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.ui.Md3PickerActivityLeading
import com.slideindex.app.ui.Md3PickerAppLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerChipSpec
import com.slideindex.app.ui.PickerListHorizontalPadding
import com.slideindex.app.ui.PickerSearchListHeader
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.SearchBar
import com.slideindex.app.ui.compose.collectPickerAppsAsState
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.pickerListSegmentedGap
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.util.ExportedActivityInfo
import com.slideindex.app.util.PackageActivityResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 挑应用列表里如何处理应用分身条目。 */
enum class AppClonePickMode {
    /** 不显示分身（默认，保持旧行为）。 */
    HIDDEN,

    /** 显示分身但不可选，并说明「按包名生效」——用于按包名存储的设置页。 */
    DISABLED,

    /** 显示且可选：调用方能处理 cloneUserId（绑定动作/启动目标）。 */
    SELECTABLE,
}

/**
 * 列表 key：只写类名会在「同一个 Activity 被枚举两次」时撞 key，LazyColumn 直接抛
 * 「Key ... was already used」。带上包名与其他选择器（分享目标、小组件）口径一致。
 */
private fun ExportedActivityInfo.componentKey(): String = "$packageName/$className"

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ActivityShortcutPickAppScreen(
    onBack: () -> Unit,
    onSelectApp: (AppInfo) -> Unit,
    titleResId: Int = R.string.activity_shortcut_pick_app_title,
    selectedPackageName: String? = null,
    excludePackageNames: Set<String> = emptySet(),
    embedInParentChrome: Boolean = false,
    enableBackHandler: Boolean = true,
    showSystemAppsOption: Boolean = true,
    cloneMode: AppClonePickMode = AppClonePickMode.HIDDEN,
) {
    val appRepository = rememberAppRepository()
    val apps by collectPickerAppsAsState(includeClones = cloneMode != AppClonePickMode.HIDDEN)
    val loading = apps.isEmpty()
    var query by remember { mutableStateOf("") }
    // 系统应用默认不显示：打开开关才枚举（仓库内每进程缓存一次），仅会话级，不落设置
    var showSystemApps by remember { mutableStateOf(false) }
    var systemApps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var loadingSystemApps by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (apps.isEmpty()) {
            appRepository.loadApps()
        }
    }

    LaunchedEffect(showSystemApps) {
        if (showSystemApps && systemApps == null) {
            loadingSystemApps = true
            try {
                systemApps = appRepository.loadActivityTargetApps()
            } finally {
                // 开关快速来回切会取消本次加载，这里要保证指示器不会卡住
                loadingSystemApps = false
            }
        }
    }

    val candidateApps = remember(apps, systemApps, showSystemApps) {
        if (showSystemApps) {
            appRepository.mergeActivityTargets(apps, systemApps.orEmpty())
        } else {
            apps
        }
    }

    val filtered = remember(candidateApps, query, excludePackageNames) {
        val matched = appRepository.searchApps(candidateApps, query)
            .filter { it.packageName !in excludePackageNames }
        // 空查询没有相关度可依，统一按「首字母 → 拼音」排序；有查询保留相关度排序
        if (query.isBlank()) appRepository.sortedByLetter(matched) else matched
    }

    val systemAppsLoading = showSystemApps && loadingSystemApps

    if (embedInParentChrome) {
        EmbeddedActivityPickAppList(
            query = query,
            onQueryChange = { query = it },
            loading = loading,
            filtered = filtered,
            selectedPackageName = selectedPackageName,
            onSelectApp = onSelectApp,
            showSystemAppsOption = showSystemAppsOption,
            showSystemApps = showSystemApps,
            onToggleSystemApps = { showSystemApps = !showSystemApps },
            systemAppsLoading = systemAppsLoading,
        )
        return
    }

    val emptyAppsText = stringResource(R.string.no_apps)
    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(titleResId),
        pageHint = stringResource(R.string.activity_shortcut_pick_app_desc),
        searchQuery = query,
        onSearchQueryChange = { query = it },
        onBack = onBack,
        enableBackHandler = enableBackHandler,
        hintResId = R.string.notification_rule_app_search_hint,
        extraActions = {
            if (showSystemAppsOption) {
                SystemAppsMenuAction(
                    showSystemApps = showSystemApps,
                    onToggle = { showSystemApps = !showSystemApps },
                )
            }
        },
    ) {
        activityPickerAppListBody(
            loading = loading,
            filtered = filtered,
            selectedPackageName = selectedPackageName,
            onSelectApp = onSelectApp,
            emptyText = emptyAppsText,
            systemAppsLoading = systemAppsLoading,
            cloneMode = cloneMode,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ActivityShortcutPickActivityScreen(
    packageName: String,
    onBack: () -> Unit,
    onSelectActivity: (ExportedActivityInfo) -> Unit,
    selectedClassName: String? = null,
    embedInParentChrome: Boolean = false,
    enableBackHandler: Boolean = true,
) {
    val context = LocalContext.current
    var activities by remember(packageName) { mutableStateOf<List<ExportedActivityInfo>>(emptyList()) }
    var loading by remember(packageName) { mutableStateOf(true) }
    var query by remember(packageName) { mutableStateOf("") }
    var appLabel by remember(packageName) { mutableStateOf(packageName) }

    LaunchedEffect(packageName) {
        loading = true
        val result = withContext(Dispatchers.IO) {
            val loadedActivities = PackageActivityResolver.listActivities(context, packageName)
            val label = runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            }.getOrDefault(packageName)
            loadedActivities to label
        }
        activities = result.first
        appLabel = result.second
        loading = false
    }

    val filtered = remember(activities, query) {
        PackageActivityResolver.searchActivities(activities, query)
    }

    val chipStyle = rememberActivityChipStyle()

    if (embedInParentChrome) {
        EmbeddedActivityPickActivityList(
            query = query,
            onQueryChange = { query = it },
            loading = loading,
            filtered = filtered,
            chipStyle = chipStyle,
            selectedClassName = selectedClassName,
            onSelectActivity = onSelectActivity,
        )
        return
    }

    val emptyActivitiesText = stringResource(R.string.search_engine_activity_empty)
    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.search_engine_pick_activity_title),
        subtitle = appLabel,
        searchQuery = query,
        onSearchQueryChange = { query = it },
        onBack = onBack,
        enableBackHandler = enableBackHandler,
        hintResId = R.string.search_engine_activity_search_hint,
    ) {
        activityPickerActivityListBody(
            loading = loading,
            filtered = filtered,
            chipStyle = chipStyle,
            selectedClassName = selectedClassName,
            onSelectActivity = onSelectActivity,
            emptyText = emptyActivitiesText,
        )
    }
}

private fun LazyListScope.activityPickerAppListBody(
    loading: Boolean,
    filtered: List<AppInfo>,
    selectedPackageName: String?,
    onSelectApp: (AppInfo) -> Unit,
    emptyText: String,
    systemAppsLoading: Boolean = false,
    cloneMode: AppClonePickMode = AppClonePickMode.HIDDEN,
) {
    when {
        loading -> activityPickerLoadingItem()
        filtered.isEmpty() -> activityPickerEmptyItem(emptyText)
        else -> {
            if (cloneMode == AppClonePickMode.DISABLED && filtered.any { it.isClone }) {
                item(key = "clone-package-only-hint") {
                    Text(
                        text = stringResource(R.string.app_clone_package_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            val segmentCount = filtered.size
            itemsIndexed(
                items = filtered,
                // 主实例与分身同包名：key 必须带 userId，否则 LazyColumn 直接崩。
                key = { _, app ->
                    com.slideindex.app.launcher.AppTarget.encode(app.packageName, app.cloneUserId)
                },
            ) { index, app ->
                val disabledClone = cloneMode == AppClonePickMode.DISABLED && app.isClone
                val selected = selectedPackageName != null && app.packageName == selectedPackageName
                Md3PickerListRow(
                    segmentIndex = index,
                    segmentCount = segmentCount,
                    title = app.label,
                    subtitle = if (disabledClone) {
                        app.packageName + " · " + stringResource(R.string.app_clone_package_only_short)
                    } else {
                        app.packageName
                    },
                    selected = selected,
                    onClick = if (disabledClone) null else ({ onSelectApp(app) }),
                    leadingContent = { Md3PickerAppLeading(app) },
                    enabled = !disabledClone,
                    trailingMode = if (selectedPackageName != null) {
                        PickerTrailingMode.Radio
                    } else {
                        PickerTrailingMode.None
                    },
                )
            }
        }
    }
    if (systemAppsLoading && !loading) activityPickerSystemAppsLoadingItem()
}

private fun LazyListScope.activityPickerActivityListBody(
    loading: Boolean,
    filtered: List<ExportedActivityInfo>,
    chipStyle: ActivityChipStyle,
    selectedClassName: String?,
    onSelectActivity: (ExportedActivityInfo) -> Unit,
    emptyText: String,
) {
    when {
        loading -> activityPickerLoadingItem()
        filtered.isEmpty() -> activityPickerEmptyItem(emptyText)
        else -> {
            val segmentCount = filtered.size
            itemsIndexed(
                items = filtered,
                key = { _, activity -> activity.componentKey() },
            ) { index, activity ->
                val selected = selectedClassName != null && activity.className == selectedClassName
                Md3PickerListRow(
                    segmentIndex = index,
                    segmentCount = segmentCount,
                    title = activity.label,
                    subtitle = activity.className,
                    selected = selected,
                    onClick = { onSelectActivity(activity) },
                    trailingChip = activityChipSpec(activity, chipStyle),
                    leadingContent = {
                        Md3PickerActivityLeading(
                            packageName = activity.packageName,
                            className = activity.className,
                            contentDescription = activity.label,
                        )
                    },
                    trailingMode = if (selectedClassName != null) {
                        PickerTrailingMode.Radio
                    } else {
                        PickerTrailingMode.None
                    },
                )
            }
        }
    }
}

/**
 * 行尾胶囊的文案与配色。
 *
 * 「需特权」用琥珀而不是错误红：未导出的 Activity 我们仍能通过 Shizuku/Root 拉起，
 * 它不是故障；红色在这套 UI 里代表失败，用错了比不上色更误导。
 * 深色主题下要整体提亮，否则琥珀压在深底上读不出来。
 */
private data class ActivityChipStyle(
    val needsPrivilegeLabel: String,
    val needsPrivilegeColor: Color,
    val entryLabel: String,
    val entryColor: Color,
)

@Composable
private fun rememberActivityChipStyle(): ActivityChipStyle {
    val needsPrivilegeColor = if (isSystemInDarkTheme()) {
        Color(0xFFFFB74D)
    } else {
        Color(0xFFB26A00)
    }
    return ActivityChipStyle(
        needsPrivilegeLabel = stringResource(R.string.search_engine_activity_needs_privilege),
        needsPrivilegeColor = needsPrivilegeColor,
        entryLabel = stringResource(R.string.search_engine_activity_entry),
        entryColor = MiuixTheme.colorScheme.primary,
    )
}

/**
 * 每行最多一个胶囊：「未导出」压过「入口」——能不能点开比「这是哪个入口」更要紧。
 *
 * 「已导出」这种恒真状态刻意不标：满屏同色胶囊会互相稀释，扫视时反而找不到重点。
 */
private fun activityChipSpec(
    activity: ExportedActivityInfo,
    style: ActivityChipStyle,
): PickerChipSpec? = when {
    !activity.exported -> PickerChipSpec(style.needsPrivilegeLabel, style.needsPrivilegeColor)
    activity.launcherEntry -> PickerChipSpec(style.entryLabel, style.entryColor)
    else -> null
}

private fun LazyListScope.activityPickerLoadingItem() {
    item(key = "loading") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

private fun LazyListScope.activityPickerSystemAppsLoadingItem() {
    item(key = "system-apps-loading") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }
    }
}

private fun LazyListScope.activityPickerEmptyItem(text: String) {
    item(key = "empty") {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun EmbeddedActivityPickAppList(
    query: String,
    onQueryChange: (String) -> Unit,
    loading: Boolean,
    filtered: List<AppInfo>,
    selectedPackageName: String?,
    onSelectApp: (AppInfo) -> Unit,
    showSystemAppsOption: Boolean,
    showSystemApps: Boolean,
    onToggleSystemApps: () -> Unit,
    systemAppsLoading: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PickerSearchListHeader(
            query = query,
            onQueryChange = onQueryChange,
            hintResId = R.string.notification_rule_app_search_hint,
            trailing = if (showSystemAppsOption) {
                {
                    SystemAppsMenuAction(
                        showSystemApps = showSystemApps,
                        onToggle = onToggleSystemApps,
                    )
                }
            } else {
                null
            },
        )
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            filtered.isEmpty() -> {
                Text(
                    text = stringResource(R.string.no_apps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            else -> {
                EmbeddedActivityPickerLazyList(
                    itemCount = filtered.size,
                    footer = if (systemAppsLoading && !loading) {
                        { activityPickerSystemAppsLoadingItem() }
                    } else {
                        null
                    },
                ) { index ->
                    val app = filtered[index]
                    val selected = selectedPackageName != null &&
                        app.packageName == selectedPackageName
                    Md3PickerListRow(
                        segmentIndex = index,
                        segmentCount = filtered.size,
                        title = app.label,
                        subtitle = app.packageName,
                        selected = selected,
                        onClick = { onSelectApp(app) },
                        leadingContent = { Md3PickerAppLeading(app) },
                        trailingMode = if (selectedPackageName != null) {
                            PickerTrailingMode.Radio
                        } else {
                            PickerTrailingMode.None
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun EmbeddedActivityPickActivityList(
    query: String,
    onQueryChange: (String) -> Unit,
    loading: Boolean,
    filtered: List<ExportedActivityInfo>,
    chipStyle: ActivityChipStyle,
    selectedClassName: String?,
    onSelectActivity: (ExportedActivityInfo) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            hintResId = R.string.search_engine_activity_search_hint,
            modifier = Modifier.padding(horizontal = PickerListHorizontalPadding, vertical = 8.dp),
        )
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            filtered.isEmpty() -> {
                Text(
                    text = stringResource(R.string.search_engine_activity_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            else -> {
                EmbeddedActivityPickerLazyList(
                    itemCount = filtered.size,
                    itemKey = { filtered[it].componentKey() },
                ) { index ->
                    val activity = filtered[index]
                    val selected = selectedClassName != null &&
                        activity.className == selectedClassName
                    Md3PickerListRow(
                        segmentIndex = index,
                        segmentCount = filtered.size,
                        title = activity.label,
                        subtitle = activity.className,
                        selected = selected,
                        onClick = { onSelectActivity(activity) },
                        trailingChip = activityChipSpec(activity, chipStyle),
                        leadingContent = {
                            Md3PickerActivityLeading(
                                packageName = activity.packageName,
                                className = activity.className,
                                contentDescription = activity.label,
                            )
                        },
                        trailingMode = if (selectedClassName != null) {
                            PickerTrailingMode.Radio
                        } else {
                            PickerTrailingMode.None
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.EmbeddedActivityPickerLazyList(
    itemCount: Int,
    itemKey: (Int) -> Any = { it },
    footer: (LazyListScope.() -> Unit)? = null,
    itemContent: @Composable (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .selectableGroup(),
        contentPadding = PaddingValues(
            start = PickerListHorizontalPadding,
            end = PickerListHorizontalPadding,
            bottom = 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
    ) {
        items(
            count = itemCount,
            key = itemKey,
            contentType = { "picker_row" },
        ) { index ->
            itemContent(index)
        }
        footer?.invoke(this)
    }
}

/**
 * 顶栏/内嵌头部右上的「显示系统应用」菜单，与分应用代理页一致的入口形态。
 * 浮层内嵌时也用它——Miuix 的 OverlayIconDropdownMenu 在 WindowManager 浮层里可用
 * （冷冻库浮层就是同一用法）。
 */
@Composable
private fun SystemAppsMenuAction(
    showSystemApps: Boolean,
    onToggle: () -> Unit,
) {
    val showLabel = stringResource(R.string.app_picker_show_system_apps)
    val hideLabel = stringResource(R.string.app_picker_hide_system_apps)
    val entry = remember(showSystemApps, showLabel, hideLabel) {
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = if (showSystemApps) hideLabel else showLabel,
                    selected = showSystemApps,
                    onClick = onToggle,
                ),
            ),
        )
    }
    OverlayIconDropdownMenu(entry = entry) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.app_picker_more_menu),
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}
