package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.freezer.FreezerListOperations
import com.slideindex.app.freezer.FreezerOperations
import com.slideindex.app.overlay.CloneAppsIndex
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「管理应用」的默认视图：**冰箱内**。
 *
 * 原先默认是「全部」（174 个应用里 173 个和冰箱无关），用户进来是为了挑成员，
 * 第一屏却全是无关应用。四个 Tab 也砍成两个：「已冻结 / 已暂停 / 使用中」的分法
 * 在「冰箱内」这个视图里已经由每行的状态文案回答了，不需要再占一整行宽度。
 */
private enum class FreezerPickerFilter {
    IN_LIST,
    ALL,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FreezerAppsPickerScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    /**
     * 资源必须走 LocalResources：`LocalContext.current.getString()` 不会随 Configuration
     * （语言 / 深色 / 字体缩放）失效，Compose lint 会直接判 ERROR（LocalContextGetResourceValueCall）。
     * context 仍然保留给状态查询与对话框使用。
     */
    val resources = LocalResources.current
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = settingsRepository.readSnapshot()
    )
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var listFilter by remember { mutableStateOf(FreezerPickerFilter.IN_LIST) }
    var privilegedAccessGranted by remember { mutableStateOf<Boolean?>(null) }
    // 冻结 / 暂停中的成员不能直接移出（会在系统里留一个没人管的停用应用），
    // 所以点「×」先弹确认，问一句要不要恢复后再移出。
    var pendingRemove by remember { mutableStateOf<AppInfo?>(null) }
    val scope = rememberCoroutineScope()
    val frozenLabel = stringResource(R.string.freezer_status_frozen)
    val pausedLabel = stringResource(R.string.freezer_status_paused)
    val activeLabel = stringResource(R.string.freezer_status_active)

    /**
     * 候选列表 = 启动器应用 + 分身条目。
     *
     * 分身与主实例同包名、同图标，只差 cloneUserId 与标签后缀；不把分身列出来，
     * 就等于「分身永远进不了冰箱」，那是我们要消掉的特例。
     */
    suspend fun loadPickerApps(): List<AppInfo> {
        val base = appRepository.loadFreezerApps(force = true)
        val clones = CloneAppsIndex.enrich(base) { label, kind ->
            resources.getString(CloneAppsIndex.labelRes(kind), label)
        }.filter { it.isClone }
        android.util.Log.i("FreezerPicker", "apps base=${base.size} clones=${clones.size}")
        return base + clones
    }

    fun reloadApps() {
        scope.launch {
            isLoading = true
            allApps = withContext(Dispatchers.IO) {
                loadPickerApps()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        privilegedAccessGranted = withContext(Dispatchers.IO) {
            TaskManagerUtil.hasPrivilegedAccess()
        }
        allApps = withContext(Dispatchers.IO) {
            loadPickerApps()
        }
        isLoading = false
    }

    val memberPackages = settings.freezerAppPackages

    // 「显示系统应用」只管**候选**，不该把已经是成员的系统应用藏起来：
    // 关掉它以后「冰箱内」会少算这些人，用户看着 1 个、实际冻着 3 个，而且在这个页面里再也找不到它们。
    val searchFilteredApps = remember(allApps, searchQuery, settings.freezerShowSystemApps, memberPackages) {
        val query = searchQuery.trim().lowercase()
        allApps
            .filter {
                settings.freezerShowSystemApps || !it.isSystem || FreezerOperations.keyOf(it) in memberPackages
            }
            .filter { app ->
                query.isBlank() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true) ||
                    app.pinyinKey.contains(query)
            }
    }

    // 两个 Tab 的计数都取**搜索过滤之后**的数字：与列表同源，不会出现「Tab 显示 5、列表只有 1 个」的错位。
    val inListCount = remember(searchFilteredApps, memberPackages) {
        searchFilteredApps.count { FreezerOperations.keyOf(it) in memberPackages }
    }
    val allCount = searchFilteredApps.size

    val displayedApps = remember(searchFilteredApps, memberPackages, listFilter) {
        searchFilteredApps
            .filter { app -> listFilter == FreezerPickerFilter.ALL || FreezerOperations.keyOf(app) in memberPackages }
            .sortedWith(
                compareByDescending<AppInfo> { FreezerOperations.keyOf(it) in memberPackages }
                    .thenBy { it.pinyinKey }
            )
    }

    val filterTabs = listOf(
        stringResource(R.string.freezer_tab_in_list_count, inListCount),
        stringResource(R.string.freezer_tab_all_count, allCount),
    )
    val permissionBannerText = stringResource(R.string.freezer_permission_banner)
    val selectedFilterTabIndex = when (listFilter) {
        FreezerPickerFilter.IN_LIST -> 0
        FreezerPickerFilter.ALL -> 1
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = stringResource(R.string.freezer_manage_apps),
        pageHint = stringResource(R.string.freezer_picker_hint),
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack,
        extraActions = {
            MiuixTopBarIconButton(onClick = onOpenSettings) {
                MiuixIcon(
                    imageVector = MiuixIcons.Medium.Settings,
                    contentDescription = stringResource(R.string.freezer_settings_title),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
    ) {
        // 只在真的没授权时出现：原来「Shizuku 已授权」这种正常状态也常驻一行灰字，属于没信息量的日志。
        if (privilegedAccessGranted == false) {
            settingsLazyTipCard(
                key = "freezer-picker-permission",
                text = permissionBannerText,
            )
        }
        item(key = "freezer-picker-filter-tabs") {
            MiuixTabRowWithContour(
                tabs = filterTabs,
                selectedTabIndex = selectedFilterTabIndex,
                onTabSelected = { index ->
                    listFilter = when (index) {
                        0 -> FreezerPickerFilter.IN_LIST
                        else -> FreezerPickerFilter.ALL
                    }
                },
                contentHorizontalPadding = 12.dp
            )
        }
        when {
            isLoading -> {
                item(key = "freezer-picker-loading") {
                    LoadingContent(
                        message = stringResource(R.string.loading),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            displayedApps.isEmpty() -> {
                item(key = "freezer-picker-empty") {
                    val emptyText = when {
                        searchQuery.isNotBlank() -> stringResource(R.string.no_apps)
                        listFilter == FreezerPickerFilter.IN_LIST ->
                            stringResource(R.string.freezer_list_empty_in_list)
                        else -> stringResource(R.string.no_apps)
                    }
                    Text(
                        text = emptyText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp)
                    )
                }
            }
            else -> {
                items(displayedApps.size, key = { FreezerOperations.keyOf(displayedApps[it]) }) { index ->
                    val app = displayedApps[index]
                    val inList = FreezerOperations.keyOf(app) in memberPackages
                    val state = FreezerOperations.stateOf(context, app)
                    // 在列表里 → 副标题回答「它现在什么状态」；不在列表里 → 回答「这是哪个包」。
                    val subtitle = when {
                        !inList -> app.packageName
                        state.isFrozen -> frozenLabel
                        state.isPaused -> pausedLabel
                        else -> activeLabel
                    }
                    AppPackageListRow(
                        entry = AppPackageEntry.Installed(app),
                        segmentIndex = index,
                        segmentCount = displayedApps.size,
                        actionIcon = if (inList) Icons.Default.Close else Icons.Default.Add,
                        actionDescription = when {
                            !inList -> stringResource(R.string.freezer_add_to_list)
                            state.isFrozen -> stringResource(R.string.freezer_unfreeze_and_remove)
                            state.isPaused -> stringResource(R.string.freezer_unpause_and_remove)
                            else -> stringResource(R.string.freezer_remove_from_list)
                        },
                        missingIcon = Icons.Default.TouchApp,
                        subtitle = subtitle,
                        onAction = {
                            if (!inList) {
                                scope.launch { settingsRepository.addFreezerApp(FreezerOperations.keyOf(app)) }
                            } else if (state.isActive) {
                                scope.launch {
                                    FreezerListOperations.removeFromList(
                                        context = context,
                                        settingsRepository = settingsRepository,
                                        target = FreezerOperations.targetOf(app),
                                        appRepository = appRepository
                                    )
                                }
                            } else {
                                // 冻结 / 暂停中的成员：先问要不要恢复再移出，而不是把按钮直接置灰
                                // （那样在这一页根本删不掉一个冻着的成员）。
                                pendingRemove = app
                            }
                        }
                    )
                }
            }
        }
    }

    pendingRemove?.let { app ->
        val state = FreezerOperations.stateOf(context, app)
        val frozen = state.isFrozen
        MiuixConfirmDialog(
            show = true,
            onDismissRequest = { pendingRemove = null },
            title = app.label,
            message = stringResource(
                if (frozen) R.string.freezer_remove_frozen_confirm
                else R.string.freezer_remove_paused_confirm
            ),
            confirmText = stringResource(
                if (frozen) R.string.freezer_unfreeze_and_remove
                else R.string.freezer_unpause_and_remove
            ),
            onConfirm = {
                scope.launch {
                    if (FreezerListOperations.restoreAndRemoveFromList(
                            context = context,
                            settingsRepository = settingsRepository,
                            target = FreezerOperations.targetOf(app),
                            appRepository = appRepository
                        )
                    ) {
                        reloadApps()
                    }
                }
            },
        )
    }
}
