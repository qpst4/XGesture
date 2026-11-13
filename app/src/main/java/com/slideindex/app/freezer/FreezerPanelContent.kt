package com.slideindex.app.freezer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.AppTarget
import com.slideindex.app.settings.FreezerWorkMode
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.miuix.MiuixExpandableSearchBottomContent
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixNavigationBackHandler
import com.slideindex.app.ui.miuix.MiuixSettingsFab
import com.slideindex.app.ui.miuix.MiuixSettingsFabInset
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import com.slideindex.app.ui.miuix.MiuixIconDropdownMenu
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.MiuixIcons

/**
 * 面板**自己挂**返回处理时走哪条派发链。
 *
 * 三种宿主的返回链路互不相通，混用会出真问题（吞掉预测性返回 / 拦截器收不到），所以做成显式参数。
 * 浮窗那条走 [FreezerPanelContent] 的 `onRegisterBackInterceptor`，不在这个枚举里。
 */
enum class FreezerBackChain {
    /** 独立 Activity（`FreezerPanelActivity`）：用 activity 的 `BackHandler`。 */
    ACTIVITY,

    /** NavDisplay 子页（`FreezerHomeScreen`）：走 navigationevent 链，见 `MiuixNavigationBackHandler`。 */
    NAVIGATION_EVENT,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FreezerPanelContent(
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    onManageApps: (() -> Unit)? = null,
    onAppLaunched: (() -> Unit)? = null,
    overlayMode: Boolean = false,
    /**
     * 由**浮窗宿主**传入：把「面板能不能消费这次返回」注册进去，宿主在关窗之前先问一句。
     *
     * 传了它就不再挂 [MiuixNavigationBackHandler] —— 浮窗的返回在 View 层被
     * `OverlayViewBackHandler` 以 `PRIORITY_OVERLAY` 的 `OnBackInvokedCallback` 吃掉了，
     * 优先级高于 Compose 这层，面板自己挂的 handler 根本收不到（那正是"按返回直接关面板"的原因）。
     * 导航子页与 Activity 传 null，走下面的 [MiuixNavigationBackHandler]。
     */
    onRegisterBackInterceptor: ((backInterceptor: (() -> Boolean)?) -> Unit)? = null,
    /**
     * 面板**自己挂**返回处理时走哪条派发链。三种宿主的返回链路完全不同，不能靠猜：
     * 浮窗走上面的拦截器；剩下两种看这里。
     */
    backChain: FreezerBackChain = FreezerBackChain.ACTIVITY,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = settingsRepository.readSnapshot()
    )
    val appRepository = rememberAppRepository()
    // 冰箱成员的「目标」清单：批量动作 / 按记录还原都按目标走（主实例与分身各自独立）。
    val freezerTargets = remember(settings.freezerAppPackages) {
        settings.freezerAppPackages.map(AppTarget::parse)
    }
    var memberApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var freezeStateRevision by remember { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    // 多选态刻意不进 rememberSaveable：Set<String> 没有默认 Saver，进程重建会当场崩，
    // 而这个状态丢了也只是退出多选，不值得为它写一个 Saver。
    var selectionMode by remember { mutableStateOf(false) }
    var selectedTargets by remember { mutableStateOf(emptySet<String>()) }
    val searchFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val screenTitle = title ?: stringResource(R.string.extension_freezer_title)
    val unfreezeAllLabel = stringResource(R.string.freezer_batch_unfreeze_all)
    val restoreIntentsLabel = stringResource(R.string.freezer_restore_intents)
    val pauseAllLabel = stringResource(R.string.freezer_pause_all)
    val unpauseAllLabel = stringResource(R.string.freezer_unpause_all)
    val importFrozenLabel = stringResource(R.string.freezer_import_frozen_apps)
    val switchWorkModeLabel = stringResource(
        if (settings.freezerWorkMode.isPause) {
            R.string.freezer_work_mode_switch_to_freeze
        } else {
            R.string.freezer_work_mode_switch_to_pause
        }
    )
    val selectionEnterLabel = stringResource(R.string.freezer_selection_enter)
    val selectionDoneLabel = stringResource(R.string.freezer_selection_done)
    val selectAllLabel = stringResource(R.string.freezer_selection_select_all)
    val removeFromListLabel = stringResource(R.string.freezer_remove_from_list)
    val restoreAndRemoveLabel = stringResource(R.string.freezer_batch_restore_and_remove)
    val switchWorkMode = {
        scope.launch {
            settingsRepository.setFreezerWorkMode(
                if (settings.freezerWorkMode.isPause) {
                    FreezerWorkMode.FREEZE.id
                } else {
                    FreezerWorkMode.PAUSE.id
                }
            )
        }
        Unit
    }

    LaunchedEffect(settings.freezerAppPackages) {
        if (settings.freezerAppPackages.isEmpty()) {
            memberApps = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        if (memberApps.isEmpty()) {
            isLoading = true
        }
        memberApps = FreezerMembers.resolve(context, appRepository, settings.freezerAppPackages)
        isLoading = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                freezeStateRevision++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun importFrozenApps() {
        scope.launch {
            val count = withContext(Dispatchers.IO) {
                FreezerListOperations.importFrozenApps(context, settingsRepository)
            }
            val message = if (count > 0) {
                resources.getQuantityString(R.plurals.freezer_import_frozen_done, count, count)
            } else {
                resources.getString(R.string.freezer_import_frozen_none)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (count > 0) {
                freezeStateRevision++
            }
        }
    }

    // 选中集存的是目标键（`包名` 或 `包名\u0002userId`）：主实例与分身同包名，只存包名会互相覆盖。
    val allTargets = memberApps.map { FreezerOperations.keyOf(it) }
    val allSelected = allTargets.isNotEmpty() && selectedTargets.containsAll(allTargets)

    fun exitSelection() {
        selectionMode = false
        selectedTargets = emptySet()
    }

    /**
     * 批量移除。[restoreFirst] = true 时先解冻 / 取消暂停再移出。
     *
     * 冻结与暂停中的成员**不允许直接移出** —— 那会在系统里留下一个没人管的停用应用，
     * 用户以为"从列表删掉就完事了"，其实它还冻着。所以两条路都给，文案上说清差别。
     */
    fun runBatchRemoval(restoreFirst: Boolean) {
        val targets = selectedTargets.map(AppTarget::parse)
        if (targets.isEmpty()) return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (restoreFirst) {
                    FreezerListOperations.restoreAndRemoveManyFromList(
                        context = context,
                        settingsRepository = settingsRepository,
                        targets = targets,
                        appRepository = appRepository,
                    )
                } else {
                    FreezerListOperations.removeManyFromList(
                        context = context,
                        settingsRepository = settingsRepository,
                        targets = targets,
                        appRepository = appRepository,
                    )
                }
            }
            val message = when {
                result.removed > 0 && result.skipped > 0 -> resources.getString(
                    R.string.freezer_batch_remove_partial,
                    result.removed,
                    result.skipped,
                )
                result.removed > 0 -> resources.getQuantityString(
                    R.plurals.freezer_batch_remove_done,
                    result.removed,
                    result.removed,
                )
                else -> resources.getString(R.string.freezer_batch_remove_blocked, result.skipped)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            if (result.removed > 0) {
                freezeStateRevision++
            }
            // 无论成败都清空选中：成功的那部分已经不在列表里了，留着它们会让「全选」
            // 的判定失真（`containsAll` 仍然为真，按钮显示成"取消全选"却什么都取消不掉）。
            selectedTargets = emptySet()
        }
    }

    /**
     * 退掉面板**内层**的一档：多选 → 展开的搜索。返回 `true` = 这次返回被面板消费掉了。
     *
     * 三个调用方共用它（浮窗宿主的返回漏斗、顶栏 ←、导航子页的返回处理），
     * 免得三处各判一遍、判出三种行为。
     */
    fun consumeInnerBack(): Boolean {
        if (selectionMode) {
            exitSelection()
            return true
        }
        return consumeExpandableSearchBack(
            expanded = searchExpanded,
            query = searchQuery,
            onExpandedChange = { searchExpanded = it },
            onQueryChange = { searchQuery = it }
        )
    }

    val handleBack: () -> Unit = {
        if (!consumeInnerBack()) {
            onBack?.invoke()
        }
    }

    // 返回键的接法分两种宿主，**不能混**：
    // - 导航子页（`FreezerHomeScreen`）与 Activity：Compose 收得到返回，面板自己挂一层。
    //   ⚠️ 用 MiuixNavigationBackHandler 而不是 activity 的 BackHandler：这个项目走
    //   `androidx.navigationevent` 分发链，混用会吞掉系统预测性返回的 progress 事件
    //   （见 `MiuixNavigationBackHandler` 的注释）。
    // - 浮窗（`FreezerOverlayWindow`）：返回在 View 层就被吃掉了（见参数注释），
    //   只能把拦截器交给宿主，由宿主在关窗之前先问一句。
    if (onRegisterBackInterceptor != null) {
        // 浮窗：返回在 View 层就被 `OverlayViewBackHandler` 吃掉了（见参数注释），
        // 面板挂什么都收不到 —— 只能把拦截器交给宿主，由它在关窗之前问一句。
        val innerBack = rememberUpdatedState<() -> Boolean>({ consumeInnerBack() })
        DisposableEffect(onRegisterBackInterceptor) {
            onRegisterBackInterceptor.invoke { innerBack.value() }
            onDispose { onRegisterBackInterceptor.invoke(null) }
        }
    } else if (backChain == FreezerBackChain.NAVIGATION_EVENT) {
        // NavDisplay 子页：必须走 navigationevent 链。这里混用 activity 的 BackHandler 会吞掉
        // 系统预测性返回的 progress 事件（见 `MiuixNavigationBackHandler` 的注释）。
        MiuixNavigationBackHandler(enabled = selectionMode || searchExpanded) {
            consumeInnerBack()
        }
    } else {
        // 独立 Activity：没有 NavDisplay 在抢返回，也没装 navigationevent 的 owner，
        // activity 的 BackHandler 才是这条路上对的那个（enabled=false 时交回系统默认 finish）。
        BackHandler(enabled = selectionMode || searchExpanded) {
            consumeInnerBack()
        }
    }

    val batchRestore: suspend () -> Int = {
        // 提示由 FreezerOperations 走 OverlayToastWindow 弹：那是独立覆盖层窗口，
        // 能叠在这个全屏浮窗之上，不像系统 Toast 会被面板自己盖住。
        FreezerOperations.restoreIntents(
            context = context,
            targets = freezerTargets,
            fallbackPause = settings.freezerWorkMode.isPause,
        )
    }

    val overflowMenuEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = importFrozenLabel,
                onClick = { importFrozenApps() }
            ),
            DropdownItem(
                text = switchWorkModeLabel,
                onClick = { switchWorkMode() }
            ),
            DropdownItem(
                text = restoreIntentsLabel,
                onClick = {
                    scope.launch {
                        if (batchRestore() > 0) {
                            freezeStateRevision++
                        }
                    }
                }
            ),
            DropdownItem(
                text = pauseAllLabel,
                onClick = {
                    scope.launch {
                        if (FreezerOperations.pauseAll(context, freezerTargets) > 0) {
                            freezeStateRevision++
                        }
                    }
                }
            ),
            DropdownItem(
                text = unpauseAllLabel,
                onClick = {
                    scope.launch {
                        if (FreezerOperations.unpauseAll(context, freezerTargets) > 0) {
                            freezeStateRevision++
                        }
                    }
                }
            ),
            DropdownItem(
                text = unfreezeAllLabel,
                onClick = {
                    scope.launch {
                        if (FreezerOperations.unfreezeAll(context, freezerTargets) > 0) {
                            freezeStateRevision++
                        }
                    }
                }
            )
        )
    )

    Box(modifier = modifier.fillMaxSize()) {
        SettingsLazyScreenScaffold(
            modifier = Modifier.fillMaxSize(),
            title = screenTitle,
            pageHint = stringResource(R.string.extension_freezer_subtitle),
            onBack = onBack?.let { handleBack },
            userScrollEnabled = false,
            actions = {
                if (selectionMode) {
                    MiuixTopBarIconButton(
                        onClick = {
                            selectedTargets = if (allSelected) {
                                emptySet()
                            } else {
                                allTargets.toSet()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                            contentDescription = selectAllLabel,
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                    MiuixTopBarIconButton(onClick = ::exitSelection) {
                        Icon(
                            imageVector = MiuixIcons.Medium.Close,
                            contentDescription = selectionDoneLabel,
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    // ⚠️ 下面这一大块刻意保持原有缩进：只为套一层 if/else 就整体重排，
                    // 会造出上百行纯缩进 diff，把真正的改动埋在里面。
                MiuixExpandableSearchIconAction(
                    expanded = searchExpanded,
                    query = searchQuery,
                    onExpandedChange = { searchExpanded = it },
                    onQueryChange = { searchQuery = it }
                )
                if (onManageApps != null) {
                    MiuixTopBarIconButton(onClick = onManageApps) {
                        Icon(
                            imageVector = MiuixIcons.Medium.Add,
                            contentDescription = stringResource(R.string.freezer_manage_apps),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
                MiuixTopBarIconButton(
                    onClick = {
                        // 进多选前先收起搜索：展开态的搜索框会占满 actions 行，把"选择/全选/完成"
                        // 这些按钮整个盖掉 —— 那时候多选态是进得去出不来的。
                        // 筛选关键字**保留**（不清空）：用户多半就是想"把筛出来的这批一起移出"。
                        searchExpanded = false
                        selectedTargets = emptySet()
                        selectionMode = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = selectionEnterLabel,
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
                if (overlayMode) {
                    Box {
                        MiuixTopBarIconButton(onClick = { overflowMenuExpanded = true }) {
                            Icon(
                                imageVector = MiuixIcons.Medium.More,
                                contentDescription = stringResource(R.string.freezer_batch_menu),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                        DropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(importFrozenLabel) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    importFrozenApps()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(switchWorkModeLabel) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    switchWorkMode()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(restoreIntentsLabel) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    scope.launch {
                                        if (batchRestore() > 0) {
                                            freezeStateRevision++
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(pauseAllLabel) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    scope.launch {
                                        if (FreezerOperations.pauseAll(context, freezerTargets) > 0) {
                                            freezeStateRevision++
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(unpauseAllLabel) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    scope.launch {
                                        if (FreezerOperations.unpauseAll(context, freezerTargets) > 0) {
                                            freezeStateRevision++
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(unfreezeAllLabel) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    scope.launch {
                                        if (FreezerOperations.unfreezeAll(context, freezerTargets) > 0) {
                                            freezeStateRevision++
                                        }
                                    }
                                },
                            )
                        }
                    }
                } else {
                    MiuixIconDropdownMenu(entry = overflowMenuEntry) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.freezer_batch_menu),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                }
                }
            },
            bottomContent = {
                MiuixExpandableSearchBottomContent(
                    searchExpanded = searchExpanded,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    focusRequester = searchFocusRequester,
                    hintResId = R.string.freezer_grid_search_hint
                )
            },
            floatingActionButton = {
                // 多选态收起 FAB：它悬在列表右下角，会和底部操作栏叠在一起，也容易误触。
                if (!selectionMode) {
                    MiuixSettingsFab(
                        onClick = {
                            scope.launch {
                                // 与「重冻应用」手势同一套语义：按每个成员记录的档位收回，
                                // 没记录的才落到工作模式。否则面板按钮会把用户设成暂停的成员一起冻成图标消失。
                                if (batchRestore() > 0) {
                                    freezeStateRevision++
                                }
                            }
                        },
                        icon = Icons.Default.AcUnit,
                        contentDescription = stringResource(R.string.freezer_restore_intents)
                    )
                }
            },
            // 网格占满视口，最底一行会被悬浮按钮压掉一截：给列表尾部预留按钮的高度。
            fabInset = MiuixSettingsFabInset,
        ) {
            if (isLoading) {
                item(key = "freezer-loading") {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        top.yukonga.miuix.kmp.basic.CircularProgressIndicator()
                    }
                }
            } else {
                LazySettingsItem(key = "freezer-grid", fillParentMaxSize = true) {
                    FreezerGridUi(
                        settings = settings,
                        memberApps = memberApps,
                        appRepository = appRepository,
                        settingsRepository = settingsRepository,
                        searchQuery = searchQuery,
                        freezeStateRevision = freezeStateRevision,
                        onFreezeStateRevisionBump = { freezeStateRevision++ },
                        modifier = Modifier.fillMaxSize(),
                        // 网格是嵌套滚动，外层留白顶不上来：inset 必须加在网格自己的滚动区上。
                        bottomInset = MiuixSettingsFabInset,
                        onAppLaunched = onAppLaunched,
                        onManageApps = onManageApps,
                        overlayMode = overlayMode,
                        selectionMode = selectionMode,
                        selectedTargets = selectedTargets,
                        onToggleSelection = { target ->
                            selectedTargets = if (target in selectedTargets) {
                                selectedTargets - target
                            } else {
                                selectedTargets + target
                            }
                        },
                        // 多选态给列表尾部留出操作栏的高度，否则最后一行会被压在栏下面点不到。
                        contentPadding = if (selectionMode) {
                            PaddingValues(bottom = FreezerSelectionBarHeight)
                        } else {
                            PaddingValues(0.dp)
                        },
                    )
                }
            }
        }
        if (selectionMode) {
            FreezerSelectionBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedCount = selectedTargets.size,
                removeLabel = removeFromListLabel,
                restoreAndRemoveLabel = restoreAndRemoveLabel,
                onRemove = { runBatchRemoval(restoreFirst = false) },
                onRestoreAndRemove = { runBatchRemoval(restoreFirst = true) },
            )
        }
    }
}

/** 多选操作栏占的高度：列表尾部要留出这么多，否则最后一行会被压在栏下面。 */
private val FreezerSelectionBarHeight = 88.dp

/**
 * 多选态底部的批量操作栏。
 *
 * 两个动作并排摊开，而不是合成一个「移出」再弹确认框：冻结 / 暂停中的成员**不能**直接移出
 * （移出会在系统里留下一个没人管的停用应用），差别必须让用户一眼看到，而不是藏在二级弹窗里。
 */
@Composable
private fun FreezerSelectionBar(
    selectedCount: Int,
    removeLabel: String,
    restoreAndRemoveLabel: String,
    onRemove: () -> Unit,
    onRestoreAndRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.freezer_selection_count, selectedCount),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRemove, enabled = selectedCount > 0) {
                Text(removeLabel)
            }
            TextButton(onClick = onRestoreAndRemove, enabled = selectedCount > 0) {
                Text(restoreAndRemoveLabel)
            }
        }
    }
}
