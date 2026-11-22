package com.slideindex.app.ui.link

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.link.LinkConfigBackup
import com.slideindex.app.link.LinkConfigCodec
import com.slideindex.app.link.LinkConfigTransfer
import com.slideindex.app.link.LinkDispatchRule
import com.slideindex.app.link.LinkMatchType
import com.slideindex.app.link.LinkResolveStrategy
import com.slideindex.app.link.LinkRuleRepository
import com.slideindex.app.ui.components.reorderable.ReorderableCollectionItemScope
import com.slideindex.app.ui.components.reorderable.ReorderableItem
import com.slideindex.app.ui.components.reorderable.rememberReorderableLazyListState
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.ui.miuix.MiuixGlassIconDropdown
import com.slideindex.app.ui.miuix.MiuixSettingsFab
import com.slideindex.app.ui.miuix.MiuixSettingsFabInset
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 链接分发规则独立管理页面：
 * 1. 严格按「分发规则 (短链重分发)」在最上方与「跳转规则 (应用直达)」在下方两级分类展示；
 * 2. 分组小标题显式指定 textColor = MiuixTheme.colorScheme.primary，恢复纯正天蓝色；
 * 3. 拖拽排序完全对齐 LinkGo：日常长按卡片进入编辑模式后，右侧 Switch 开关变为三道杠拖拽手柄直接拖拽；
 * 4. 每个规则项均为独立的带上下大圆角卡片（isFirst = true, isLast = true）；
 * 5. 彻底消除图标周围的灰暗底色剪影；
 * 6. 顶栏三点菜单采用毛玻璃圆形容器驱动，解决空 IconButton 拦截点击导致无法弹出的 Bug。
 */
@Composable
fun LinkRulesScreen(
    onBack: () -> Unit,
    onNavigateToEditRule: (String?) -> Unit,
    onNavigateToHistory: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val rules by LinkRuleRepository.observeRules(context).collectAsStateWithLifecycle(initialValue = emptyList())
    var isReorderMode by rememberSaveable { mutableStateOf(false) }

    // 规范化：短链重分发规则置顶，应用直达规则在后
    fun normalizeRuleOrder(raw: List<LinkDispatchRule>): List<LinkDispatchRule> {
        val (dispatch, redirect) = raw.partition { it.resolveStrategy == LinkResolveStrategy.REDISPATCH }
        return dispatch + redirect
    }

    var localRules by remember(rules) { mutableStateOf(normalizeRuleOrder(rules)) }
    LaunchedEffect(rules) {
        // 排序模式下禁止外部异步更新冲刷打断当前的拖拽内存队列
        if (!isReorderMode) {
            localRules = normalizeRuleOrder(rules)
        }
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val lazyListState = rememberLazyListState()

    // 采用工程内置的 sh.calvin.reorderable 拖拽重排控制器（同组内平滑重排，不跨组乱序）
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState = lazyListState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey = to.key as? String ?: return@rememberReorderableLazyListState
        val fromRule = localRules.firstOrNull { it.id == fromKey } ?: return@rememberReorderableLazyListState
        val toRule = localRules.firstOrNull { it.id == toKey } ?: return@rememberReorderableLazyListState
        val fromIsDispatch = fromRule.resolveStrategy == LinkResolveStrategy.REDISPATCH
        val toIsDispatch = toRule.resolveStrategy == LinkResolveStrategy.REDISPATCH
        // 严格限制：短链重分发只在短链内部换位，应用直达只在直达内部换位
        if (fromIsDispatch != toIsDispatch) return@rememberReorderableLazyListState
        val fromIdx = localRules.indexOfFirst { it.id == fromKey }
        val toIdx = localRules.indexOfFirst { it.id == toKey }
        if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
            val mutable = localRules.toMutableList()
            val item = mutable.removeAt(fromIdx)
            mutable.add(toIdx, item)
            localRules = mutable
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val onDragSave: () -> Unit = {
        val reordered = localRules.mapIndexed { idx, r -> r.copy(priority = idx) }
        scope.launch {
            LinkRuleRepository.updateRules(context) { reordered }
        }
    }

    var showBuiltinImportDialog by remember { mutableStateOf(false) }
    var clipboardParsedBackup by remember { mutableStateOf<LinkConfigBackup?>(null) }
    var showLiveTestSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = isReorderMode) {
        isReorderMode = false
        onDragSave()
    }

    val filteredRules = remember(localRules, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) localRules
        else localRules.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.pattern.contains(q, ignoreCase = true) ||
                it.targetPackage.contains(q, ignoreCase = true)
        }
    }

    // 两级分类：1. 分发规则（短链重分发）在最上方；2. 跳转规则（应用直达）在下方
    val dispatchRules = remember(filteredRules) {
        filteredRules.filter { it.resolveStrategy == LinkResolveStrategy.REDISPATCH }
    }
    val redirectRules = remember(filteredRules) {
        filteredRules.filter { it.resolveStrategy != LinkResolveStrategy.REDISPATCH }
    }

    val moreMenuEntry = remember {
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = "从剪贴板导入规则",
                    onClick = {
                        val parsed = LinkConfigTransfer.parseClipboardBackup(context)
                        if (parsed != null && parsed.rules.isNotEmpty()) {
                            clipboardParsedBackup = parsed
                        } else {
                            scope.launch {
                                val res = LinkConfigTransfer.importFromClipboard(context)
                                if (res.isSuccess) {
                                    Toast.makeText(context, "成功导入 ${res.rulesAdded} 条规则", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "未从剪贴板找到有效规则", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                ),
                DropdownItem(
                    text = "导出全部规则到剪贴板",
                    onClick = {
                        scope.launch {
                            val ok = LinkConfigTransfer.exportToClipboard(context)
                            Toast.makeText(context, if (ok) "已导出全部规则到剪贴板" else "导出失败", Toast.LENGTH_SHORT).show()
                        }
                    },
                ),
                DropdownItem(
                    text = "内置应用规则库推荐",
                    onClick = { showBuiltinImportDialog = true },
                ),
            ),
        )
    }

    val pageTitle = if (isReorderMode) "调整规则顺序" else "分发规则"

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = pageTitle,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        listState = lazyListState,
        onBack = {
            if (isReorderMode) {
                isReorderMode = false
                onDragSave()
            } else {
                onBack()
            }
        },
        hintResId = R.string.shortcut_test_search_hint,
        extraActions = {
            if (isReorderMode) {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = {
                                isReorderMode = false
                                onDragSave()
                            },
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixText(
                        text = "完成",
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            } else {
                // 1. 全局仿真推演测试（毛玻璃胶囊）
                GlassIconButton(onClick = { showLiveTestSheet = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_science_lab),
                        contentDescription = "仿真推演",
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
                // 2. 更多选项（使用专为顶栏设计的毛玻璃下拉菜单组件，带毛玻璃胶囊包裹）
                MiuixGlassIconDropdown(
                    entries = listOf(moreMenuEntry),
                    icon = Icons.Default.MoreVert,
                    contentDescription = "更多选项",
                    capsule = true,
                )
            }
        },
        floatingActionButton = {
            if (!isReorderMode) {
                MiuixSettingsFab(
                    onClick = { onNavigateToEditRule(null) },
                    icon = Icons.Default.Add,
                    contentDescription = "新建规则",
                )
            }
        },
        fabInset = if (!isReorderMode) MiuixSettingsFabInset else 0.dp,
    ) {
        // 顶部简述卡片
        item(key = "rule_summary_card") {
            CardSegment(
                isFirst = true,
                isLast = true,
                outerHorizontalPadding = 12.dp,
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                BasicComponent(
                    modifier = Modifier.fillMaxWidth(),
                    title = if (searchQuery.isBlank()) "规则概览" else "搜索结果",
                    summary = if (searchQuery.isBlank()) {
                        if (isReorderMode) "已进入排序模式：按住右侧三道杠手柄可直接拖动换位，点击右上角「完成」保存"
                        else "共 ${rules.size} 条规则 · 短链分发 ${dispatchRules.size} 条 · 应用直达 ${redirectRules.size} 条 · 长按卡片进入排序"
                    } else {
                        "找到 ${filteredRules.size} / ${rules.size} 条规则"
                    },
                )
            }
        }

        if (filteredRules.isEmpty()) {
            item(key = "rule_empty_tip") {
                CardSegment(
                    isFirst = true,
                    isLast = true,
                    outerHorizontalPadding = 12.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MiuixText(
                            text = if (rules.isEmpty()) "暂无规则，点击右下角「+」或通过菜单导入模板" else "没有找到匹配的规则",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        } else {
            // 1. 分发规则（短链重分发）—— 在最上方，显式指定 Miuix 天蓝色小标题
            if (dispatchRules.isNotEmpty()) {
                item(key = "header_dispatch_rules") {
                    SmallTitle(
                        text = "短链重分发规则 (${dispatchRules.size} 条)",
                        textColor = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                items(
                    items = dispatchRules,
                    key = { it.id },
                ) { rule ->
                    val appName = linkAppNameCache[rule.targetPackage].orEmpty().ifBlank { rule.targetPackage }

                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = rule.id,
                    ) { isDragging ->
                        LinkDispatchRuleItemRow(
                            rule = rule,
                            appName = appName,
                            isFirst = true,
                            isLast = true,
                            isReorderMode = isReorderMode,
                            isDragging = isDragging,
                            reorderItemScope = this,
                            onClick = {
                                if (!isReorderMode) {
                                    onNavigateToEditRule(rule.id)
                                }
                            },
                            onLongClick = {
                                if (!isReorderMode) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isReorderMode = true
                                }
                            },
                            onToggleEnabled = { enabled ->
                                scope.launch {
                                    LinkRuleRepository.updateRules(context) { cur ->
                                        cur.map { if (it.id == rule.id) it.copy(isEnabled = enabled) else it }
                                    }
                                }
                            },
                        )
                    }
                }
            }

            // 2. 跳转规则（应用直达）—— 在下方，显式指定 Miuix 天蓝色小标题
            if (redirectRules.isNotEmpty()) {
                item(key = "header_redirect_rules") {
                    SmallTitle(
                        text = "应用直达跳转规则 (${redirectRules.size} 条)",
                        textColor = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                items(
                    items = redirectRules,
                    key = { it.id },
                ) { rule ->
                    val appName = linkAppNameCache[rule.targetPackage].orEmpty().ifBlank { rule.targetPackage }

                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = rule.id,
                    ) { isDragging ->
                        LinkDispatchRuleItemRow(
                            rule = rule,
                            appName = appName,
                            isFirst = true,
                            isLast = true,
                            isReorderMode = isReorderMode,
                            isDragging = isDragging,
                            reorderItemScope = this,
                            onClick = {
                                if (!isReorderMode) {
                                    onNavigateToEditRule(rule.id)
                                }
                            },
                            onLongClick = {
                                if (!isReorderMode) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isReorderMode = true
                                }
                            },
                            onToggleEnabled = { enabled ->
                                scope.launch {
                                    LinkRuleRepository.updateRules(context) { cur ->
                                        cur.map { if (it.id == rule.id) it.copy(isEnabled = enabled) else it }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // 内置模板导入选择弹窗
    if (showBuiltinImportDialog) {
        LinkTemplateImportDialog(
            show = true,
            existingRules = rules,
            dialogTitle = "导入内置推荐规则",
            dialogDescription = "精选各主流 App（微信、B站、知乎、小红书、微博、京东等）的高速分发规则",
            onDismissRequest = { showBuiltinImportDialog = false },
            onImport = { chosen ->
                showBuiltinImportDialog = false
                scope.launch {
                    val res = LinkRuleRepository.importConfig(context, LinkConfigBackup(rules = chosen))
                    Toast.makeText(context, "成功导入 ${res.rulesAdded} 条规则", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    // 剪贴板冲突/覆盖导入选择弹窗
    clipboardParsedBackup?.let { backup ->
        LinkTemplateImportDialog(
            show = true,
            existingRules = rules,
            customCandidates = backup.rules,
            dialogTitle = "导入剪贴板规则",
            dialogDescription = "从剪贴板解析到 ${backup.rules.size} 条规则，请勾选您需要导入的项目",
            onDismissRequest = { clipboardParsedBackup = null },
            onImport = { chosen ->
                clipboardParsedBackup = null
                scope.launch {
                    val res = LinkRuleRepository.importConfig(context, LinkConfigBackup(rules = chosen))
                    Toast.makeText(context, "成功导入 ${res.rulesAdded} 条规则", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    // 顶栏实时仿真推演弹窗（全局规则库沙盒）
    if (showLiveTestSheet) {
        LinkRuleLiveTestBottomSheet(
            show = true,
            candidateRule = null,
            onDismissRequest = { showLiveTestSheet = false },
        )
    }
}

/**
 * 单条规则行：
 * 1. 采用解耦后的 [LinkRuleIcon] 驱动图标展示，彻底消除外露底色剪影；
 * 2. 对齐 LinkGo：长按卡片进入排序模式后，右侧 Switch 开关直接变为三道杠拖拽手柄 [Icons.Default.DragHandle]；
 * 3. 每个卡片均为独立卡片（isFirst = true, isLast = true），间距匀称。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinkDispatchRuleItemRow(
    rule: LinkDispatchRule,
    appName: String,
    isFirst: Boolean,
    isLast: Boolean,
    isReorderMode: Boolean,
    isDragging: Boolean,
    reorderItemScope: ReorderableCollectionItemScope,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    val matchLabel = when (rule.matchType) {
        LinkMatchType.CONTAINS -> "包含"
        LinkMatchType.REGEX -> "正则"
        LinkMatchType.EXACT -> "精确"
    }

    val context = LocalContext.current
    var isAppInstalled by remember(rule.targetPackage) { mutableStateOf(true) }
    LaunchedEffect(rule.targetPackage) {
        if (rule.targetPackage.isNotBlank()) {
            isAppInstalled = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getPackageInfo(rule.targetPackage, 0)
                    true
                }.getOrDefault(false)
            }
        }
    }

    val itemColor = when {
        isDragging -> MiuixTheme.colorScheme.surfaceContainerHigh
        else -> MiuixTheme.colorScheme.surfaceContainer
    }

    CardSegment(
        isFirst = isFirst,
        isLast = isLast,
        color = itemColor,
        outerHorizontalPadding = 12.dp,
        modifier = modifier.padding(bottom = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 1. 头像区（纯净渲染 LinkRuleIcon，彻底拿掉灰色背景 Box，解决剪影脏角）
                LinkRuleIcon(
                    iconType = rule.iconType,
                    iconValue = rule.iconValue,
                    targetPackage = rule.targetPackage,
                    size = 40.dp,
                    shape = RoundedCornerShape(10.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                // 2. 标题与目标应用名
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MiuixText(
                            text = rule.name.ifBlank { "未命名规则" },
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!isAppInstalled && rule.targetPackage.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MiuixTheme.colorScheme.error.copy(alpha = 0.12f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            ) {
                                MiuixText(
                                    text = "未安装",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    MiuixText(
                        text = if (rule.resolveStrategy == LinkResolveStrategy.REDISPATCH) {
                            if (appName.isNotBlank() && appName != rule.targetPackage) "$appName · 短链分发" else "短链分发"
                        } else {
                            appName.ifBlank { "未指定目标应用" }
                        },
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 3. 右侧状态操作区：
                // 排序模式：开关直接变为 LinkGo 风格三道杠拖拽手柄 [Icons.Default.DragHandle]，按住直接拖动；
                // 常规模式：Switch 启停开关
                if (isReorderMode) {
                    with(reorderItemScope) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .draggableHandle(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "拖拽排序手柄",
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                } else {
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = onToggleEnabled,
                    )
                }
            }

            // 4. 第二行：Pattern 浅底等宽代码微标签 + 特征胶囊
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 等宽 Pattern 浅底小标签
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    MiuixText(
                        text = rule.pattern.ifBlank { "未配置匹配式" },
                        style = MiuixTheme.textStyles.footnote2,
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 匹配模式胶囊
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    MiuixText(
                        text = matchLabel,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                // 短链重定向特性
                if (rule.resolveShortLink) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        MiuixText(
                            text = "短链",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                // 模板改写特性
                if (!rule.template.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        MiuixText(
                            text = "改写",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                // 触发计数胶囊
                if (rule.hitCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        MiuixText(
                            text = "${rule.hitCount}次",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}
