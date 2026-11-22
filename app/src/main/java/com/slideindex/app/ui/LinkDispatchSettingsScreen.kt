package com.slideindex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.link.BrowserAppOption
import com.slideindex.app.link.BrowserSelectorActivity
import com.slideindex.app.link.LinkConfigBackup
import com.slideindex.app.link.LinkConfigCodec
import com.slideindex.app.link.LinkConfigTransfer
import com.slideindex.app.link.LinkDispatchResult
import com.slideindex.app.link.LinkDispatchRule
import com.slideindex.app.link.LinkDispatcher
import com.slideindex.app.link.LinkExemptDomain
import com.slideindex.app.link.LinkExtractPattern
import com.slideindex.app.link.LinkLauncher
import com.slideindex.app.link.LinkMatchType
import com.slideindex.app.link.LinkResolveStrategy
import com.slideindex.app.link.LinkRouteTrace
import com.slideindex.app.link.LinkRuleRepository
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.link.LinkAppIcon
import com.slideindex.app.ui.link.LinkTemplateImportDialog
import com.slideindex.app.ui.link.linkAppNameCache
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixRadioSelectDialog
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 链接分发与雷达主设置页面。
 *
 * 结合 Gevo 全折叠仪表盘架构 + LinkGo-X 分门别类机制：
 * 1. 所有功能块默认折叠（极简一屏展示所有控制台大块）；
 * 2. 规则列表按「分发规则 (短链重分发)」与「跳转规则 (应用直达)」分栏清晰展示；
 * 3. 规则展示项全面精致化（40dp 图标 + 等宽 Pattern 微标签 + 胶囊徽章 + Switch）；
 * 4. 具备系统默认浏览器与备选兜底浏览器能力；
 * 5. 顶栏提供独立「跳转记录」流水日志入口。
 */
@Composable
fun LinkDispatchSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onNavigateToEditRule: (String?) -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val pageTitle = stringResource(R.string.link_dispatch_title)

    val rules by LinkRuleRepository.observeRules(context).collectAsStateWithLifecycle(initialValue = emptyList())
    val exempts by LinkRuleRepository.observeExemptDomains(context).collectAsStateWithLifecycle(initialValue = emptyList())
    val extracts by LinkRuleRepository.observeExtractPatterns(context).collectAsStateWithLifecycle(initialValue = emptyList())
    val openInSmallWindow by LinkRuleRepository.observeOpenInSmallWindow(context).collectAsStateWithLifecycle(initialValue = true)
    val radarScreenContent by LinkRuleRepository.observeRadarScreenContent(context).collectAsStateWithLifecycle(initialValue = true)
    val radarPointer by LinkRuleRepository.observeRadarPointer(context).collectAsStateWithLifecycle(initialValue = true)
    val radarHoverHoldMs by LinkRuleRepository.observeRadarHoverHoldMs(context).collectAsStateWithLifecycle(
        initialValue = LinkRuleRepository.DEFAULT_RADAR_HOVER_HOLD_MS,
    )
    val fallbackBrowser by LinkRuleRepository.observeFallbackBrowser(context).collectAsStateWithLifecycle(initialValue = null)

    val holdValues = listOf(650, 800, 1000, 1250, 1500, 2000, 3000, 0)
    val holdLabels = listOf("0.65 秒", "0.8 秒", "1 秒", "1.25 秒", "1.5 秒", "2 秒", "3 秒", "始终保持（不自动变黄）")
    val currentHoldIndex = holdValues.indexOf(radarHoverHoldMs).let { if (it < 0) 2 else it }

    var searchQuery by rememberSaveable { mutableStateOf("") }

    val haptic = LocalHapticFeedback.current
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedRuleIds = remember { mutableStateListOf<String>() }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedRuleIds.clear()
    }

    // Gevo 风格全折叠仪表盘状态（默认全部折叠）
    var rulesExpanded by rememberSaveable { mutableStateOf(false) }
    var behaviorExpanded by rememberSaveable { mutableStateOf(false) }
    var fallbackExpanded by rememberSaveable { mutableStateOf(false) }
    var sandboxExpanded by rememberSaveable { mutableStateOf(false) }
    var toolsExpanded by rememberSaveable { mutableStateOf(false) }

    var showBuiltinImportDialog by remember { mutableStateOf(false) }
    var clipboardParsedBackup by remember { mutableStateOf<LinkConfigBackup?>(null) }
    var showExemptsSheet by remember { mutableStateOf(false) }
    var showExtractsSheet by remember { mutableStateOf(false) }
    var showBrowserPickerSheet by remember { mutableStateOf(false) }
    var showHoldPickerSheet by remember { mutableStateOf(false) }
    var appNamesVersion by remember { mutableStateOf(0) }

    var fallbackAppDisplayName by remember(fallbackBrowser) { mutableStateOf("") }

    // 预解析规则中的目标应用中文名
    LaunchedEffect(rules, fallbackBrowser) {
        var changed = false
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packagesToResolve = (rules.map { it.targetPackage } + listOfNotNull(fallbackBrowser))
                .filter { it.isNotBlank() }
                .distinct()

            packagesToResolve.forEach { pkg ->
                if (!linkAppNameCache.containsKey(pkg)) {
                    val label = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    }.getOrNull()
                    if (!label.isNullOrBlank()) {
                        linkAppNameCache[pkg] = label
                        changed = true
                    }
                }
            }
        }
        if (changed) appNamesVersion++
        if (!fallbackBrowser.isNullOrBlank()) {
            fallbackAppDisplayName = linkAppNameCache[fallbackBrowser].orEmpty().ifBlank { fallbackBrowser!! }
        } else {
            fallbackAppDisplayName = ""
        }
    }

    val filteredRules = remember(rules, searchQuery, appNamesVersion) {
        val q = searchQuery.trim()
        if (q.isEmpty()) rules
        else rules.filter { rule ->
            val appName = linkAppNameCache[rule.targetPackage].orEmpty()
            rule.name.contains(q, ignoreCase = true) ||
                rule.pattern.contains(q, ignoreCase = true) ||
                rule.targetPackage.contains(q, ignoreCase = true) ||
                appName.contains(q, ignoreCase = true)
        }
    }

    val enabledCount = remember(rules) { rules.count { it.isEnabled } }

    // 搜索有输入时自动展开规则
    val isRulesOpen = rulesExpanded || searchQuery.isNotBlank()

    // 区分分发规则与跳转规则
    val dispatchRules = remember(filteredRules) {
        filteredRules.filter { it.resolveStrategy == LinkResolveStrategy.REDISPATCH }
    }
    val redirectRules = remember(filteredRules) {
        filteredRules.filter { it.resolveStrategy != LinkResolveStrategy.REDISPATCH }
    }

    // 沙盒测试状态
    var sandboxUrl by remember { mutableStateOf("") }
    var sandboxResult by remember { mutableStateOf<LinkRouteTrace?>(null) }
    var isSandboxSimulating by remember { mutableStateOf(false) }

    val currentTitle = if (isSelectionMode) "已选 ${selectedRuleIds.size} 项" else pageTitle

    val onToggleSelectAll = {
        if (selectedRuleIds.size == filteredRules.size) {
            selectedRuleIds.clear()
        } else {
            selectedRuleIds.clear()
            selectedRuleIds.addAll(filteredRules.map { it.id })
        }
    }

    val onBatchToggleEnabled = {
        val selected = rules.filter { it.id in selectedRuleIds }
        val targetState = !selected.all { it.isEnabled }
        scope.launch {
            LinkRuleRepository.updateRules(context) { cur ->
                cur.map { if (it.id in selectedRuleIds) it.copy(isEnabled = targetState) else it }
            }
            Toast.makeText(context, if (targetState) "已批量启用" else "已批量停用", Toast.LENGTH_SHORT).show()
        }
    }

    val onBatchShare = {
        val toShare = rules.filter { it.id in selectedRuleIds }
        val backup = LinkConfigBackup(rules = toShare)
        val json = LinkConfigCodec.export(backup.rules, emptyList(), emptyList())
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("LinkGoRules", json))
        Toast.makeText(context, "已复制 ${toShare.size} 条规则到剪贴板", Toast.LENGTH_SHORT).show()
    }

    val onBatchDuplicate = {
        val toDuplicate = rules.filter { it.id in selectedRuleIds }
        scope.launch {
            LinkRuleRepository.updateRules(context) { cur ->
                val nextPrio = (cur.maxOfOrNull { it.priority } ?: -1) + 1
                val copies = toDuplicate.mapIndexed { idx, orig ->
                    orig.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        name = "${orig.name} (副本)",
                        priority = nextPrio + idx,
                    )
                }
                cur + copies
            }
            Toast.makeText(context, "已复刻 ${toDuplicate.size} 条规则", Toast.LENGTH_SHORT).show()
            selectedRuleIds.clear()
            isSelectionMode = false
        }
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = currentTitle,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = {
            if (isSelectionMode) {
                isSelectionMode = false
                selectedRuleIds.clear()
            } else {
                onBack()
            }
        },
        hintResId = R.string.shortcut_test_search_hint,
        extraActions = {
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            isSelectionMode = false
                            selectedRuleIds.clear()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixText(
                        text = "完成",
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            } else {
                MiuixTopBarIconButton(onClick = onNavigateToHistory) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "跳转记录",
                    )
                }
            }
        },
    ) {
        // 页面简述
        item(key = "link_dispatch_desc_tip") {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                MiuixText(
                    text = "全功能链接分发中枢：支持规则精准重定向、短链深度追踪、指针与屏幕雷达感知，并具备系统默认浏览器与备选兜底能力。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        // ==========================================
        // 模块 1：【分发规则】（折叠控制台）
        // ==========================================
        item(key = "collapsible_block_rules") {
            val ruleHeaderSummary = if (searchQuery.isBlank()) {
                "共 ${rules.size} 条规则 · 已启用 $enabledCount 条"
            } else {
                "搜索找到 ${filteredRules.size} / ${rules.size} 条规则"
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = tween(250)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 卡头
                CardSegment(
                    isFirst = true,
                    isLast = !isRulesOpen,
                    outerHorizontalPadding = 12.dp,
                ) {
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = "分发规则",
                        summary = ruleHeaderSummary,
                        onClick = { rulesExpanded = !rulesExpanded },
                        endActions = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onNavigateToEditRule(null) },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "新建规则",
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Icon(
                                    imageVector = if (isRulesOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        },
                    )
                }

                // 展开的规则内容
                AnimatedVisibility(
                    visible = isRulesOpen,
                    enter = expandVertically(tween(250)) + fadeIn(),
                    exit = shrinkVertically(tween(250)) + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // 新建规则引导行
                        CardSegment(
                            isFirst = false,
                            isLast = filteredRules.isEmpty(),
                            outerHorizontalPadding = 12.dp,
                        ) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "新建自定义规则",
                                summary = "添加匹配网址特征与指定打开目标应用",
                                startAction = {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                                onClick = { onNavigateToEditRule(null) },
                            )
                        }

                        if (filteredRules.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                MiuixText(
                                    text = if (rules.isEmpty()) "暂无规则，点击上方添加或从工具导入模板" else "没有匹配的规则",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        } else {
                            // 1. 分发规则（短链重分发）
                            if (dispatchRules.isNotEmpty()) {
                                Box(modifier = Modifier.padding(start = 28.dp, top = 10.dp, bottom = 4.dp)) {
                                    MiuixText(
                                        text = "短链重分发规则 (${dispatchRules.size} 条)",
                                        style = MiuixTheme.textStyles.subtitle,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                }
                                dispatchRules.forEachIndexed { idx, rule ->
                                    val appName = linkAppNameCache[rule.targetPackage].orEmpty()
                                    val isSelected = rule.id in selectedRuleIds
                                    LinkDispatchRuleItemRow(
                                        rule = rule,
                                        appName = appName,
                                        isFirst = idx == 0 && redirectRules.isEmpty(),
                                        isLast = idx == dispatchRules.size - 1 && redirectRules.isEmpty() && !isSelectionMode,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (isSelectionMode) {
                                                if (isSelected) selectedRuleIds.remove(rule.id)
                                                else selectedRuleIds.add(rule.id)
                                            } else {
                                                onNavigateToEditRule(rule.id)
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!isSelectionMode) {
                                                isSelectionMode = true
                                                selectedRuleIds.clear()
                                                selectedRuleIds.add(rule.id)
                                                rulesExpanded = true
                                            }
                                        },
                                        onToggleEnabled = { checked ->
                                            scope.launch {
                                                LinkRuleRepository.updateRules(context) { list ->
                                                    list.map { if (it.id == rule.id) it.copy(isEnabled = checked) else it }
                                                }
                                            }
                                        },
                                    )
                                }
                            }

                            // 2. 跳转规则（直达目标应用）
                            if (redirectRules.isNotEmpty()) {
                                Box(modifier = Modifier.padding(start = 28.dp, top = 10.dp, bottom = 4.dp)) {
                                    MiuixText(
                                        text = "应用直达跳转规则 (${redirectRules.size} 条)",
                                        style = MiuixTheme.textStyles.subtitle,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                }
                                redirectRules.forEachIndexed { idx, rule ->
                                    val appName = linkAppNameCache[rule.targetPackage].orEmpty()
                                    val isSelected = rule.id in selectedRuleIds
                                    LinkDispatchRuleItemRow(
                                        rule = rule,
                                        appName = appName,
                                        isFirst = idx == 0 && dispatchRules.isEmpty(),
                                        isLast = idx == redirectRules.size - 1 && !isSelectionMode,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (isSelectionMode) {
                                                if (isSelected) selectedRuleIds.remove(rule.id)
                                                else selectedRuleIds.add(rule.id)
                                            } else {
                                                onNavigateToEditRule(rule.id)
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!isSelectionMode) {
                                                isSelectionMode = true
                                                selectedRuleIds.clear()
                                                selectedRuleIds.add(rule.id)
                                                rulesExpanded = true
                                            }
                                        },
                                        onToggleEnabled = { checked ->
                                            scope.launch {
                                                LinkRuleRepository.updateRules(context) { list ->
                                                    list.map { if (it.id == rule.id) it.copy(isEnabled = checked) else it }
                                                }
                                            }
                                        },
                                    )
                                }
                            }

                            // 多选模式底部操作工具栏 (对标 LinkGo 原版交互)
                            if (isSelectionMode) {
                                LinkRulesSelectionBottomBar(
                                    selectedCount = selectedRuleIds.size,
                                    totalCount = filteredRules.size,
                                    allSelected = selectedRuleIds.size == filteredRules.size && filteredRules.isNotEmpty(),
                                    onToggleSelectAll = { onToggleSelectAll() },
                                    onBatchToggleEnabled = { onBatchToggleEnabled() },
                                    onBatchShare = { onBatchShare() },
                                    onBatchDuplicate = { onBatchDuplicate() },
                                    onBatchDelete = { showBatchDeleteDialog = true },
                                    onExit = {
                                        isSelectionMode = false
                                        selectedRuleIds.clear()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 模块 2：【分发与雷达行为】（折叠控制台）
        // ==========================================
        item(key = "collapsible_block_behavior") {
            val holdText = if (radarHoverHoldMs == 0) "始终保持" else "${radarHoverHoldMs}ms"
            val behaviorSummary = "指针: ${if (radarPointer) "开" else "关"} · 屏幕: ${if (radarScreenContent) "开" else "关"} · 停留 $holdText · 小窗: ${if (openInSmallWindow) "开" else "关"}"

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .animateContentSize(animationSpec = tween(250)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CardSegment(
                    isFirst = true,
                    isLast = !behaviorExpanded,
                    outerHorizontalPadding = 12.dp,
                ) {
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = "分发与雷达行为",
                        summary = behaviorSummary,
                        onClick = { behaviorExpanded = !behaviorExpanded },
                        endActions = {
                            Icon(
                                imageVector = if (behaviorExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        },
                    )
                }

                AnimatedVisibility(
                    visible = behaviorExpanded,
                    enter = expandVertically(tween(250)) + fadeIn(),
                    exit = shrinkVertically(tween(250)) + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CardSegment(
                            isFirst = false,
                            isLast = false,
                            outerHorizontalPadding = 12.dp,
                        ) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "悬浮指针链接雷达",
                                summary = "指针靠近屏幕链接时高亮并显示打开气泡，点击或松手直接跳转",
                                onClick = {
                                    scope.launch { LinkRuleRepository.setRadarPointerEnabled(context, !radarPointer) }
                                },
                                endActions = {
                                    Switch(
                                        checked = radarPointer,
                                        onCheckedChange = { checked ->
                                            scope.launch { LinkRuleRepository.setRadarPointerEnabled(context, checked) }
                                        },
                                    )
                                },
                            )
                        }

                        CardSegment(
                            isFirst = false,
                            isLast = false,
                            outerHorizontalPadding = 12.dp,
                        ) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "雷达横幅停留时长",
                                summary = "悬浮指针停在链接上保持显示横幅的时间，超时切换为取词（当前：${holdLabels[currentHoldIndex]}）",
                                onClick = { showHoldPickerSheet = true },
                                endActions = {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                },
                            )
                        }

                        CardSegment(
                            isFirst = false,
                            isLast = false,
                            outerHorizontalPadding = 12.dp,
                        ) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "屏幕内容链接雷达",
                                summary = "全局复制或框选悬浮窗中扫描屏幕链接，轻点直达目标应用",
                                onClick = {
                                    scope.launch { LinkRuleRepository.setRadarScreenContentEnabled(context, !radarScreenContent) }
                                },
                                endActions = {
                                    Switch(
                                        checked = radarScreenContent,
                                        onCheckedChange = { checked ->
                                            scope.launch { LinkRuleRepository.setRadarScreenContentEnabled(context, checked) }
                                        },
                                    )
                                },
                            )
                        }

                        CardSegment(
                            isFirst = false,
                            isLast = true,
                            outerHorizontalPadding = 12.dp,
                        ) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "小窗打开",
                                summary = "分发规则触发时优先用自由窗口小窗打开目标应用",
                                onClick = {
                                    scope.launch { LinkRuleRepository.setOpenInSmallWindowEnabled(context, !openInSmallWindow) }
                                },
                                endActions = {
                                    Switch(
                                        checked = openInSmallWindow,
                                        onCheckedChange = { checked ->
                                            scope.launch { LinkRuleRepository.setOpenInSmallWindowEnabled(context, checked) }
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 模块 3：【系统默认与兜底设置】（折叠控制台）
        // ==========================================
        item(key = "collapsible_block_fallback") {
            val fallbackSummary = if (fallbackBrowser.isNullOrBlank()) {
                "未设兜底备选浏览器（未匹配时现场选择）"
            } else {
                "兜底备选: $fallbackAppDisplayName · $fallbackBrowser"
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .animateContentSize(animationSpec = tween(250)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CardSegment(
                    isFirst = true,
                    isLast = !fallbackExpanded,
                    outerHorizontalPadding = 12.dp,
                ) {
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = "系统默认与兜底设置",
                        summary = fallbackSummary,
                        onClick = { fallbackExpanded = !fallbackExpanded },
                        endActions = {
                            Icon(
                                imageVector = if (fallbackExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        },
                    )
                }

                AnimatedVisibility(
                    visible = fallbackExpanded,
                    enter = expandVertically(tween(250)) + fadeIn(),
                    exit = shrinkVertically(tween(250)) + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CardSegment(
                            isFirst = false,
                            isLast = false,
                            outerHorizontalPadding = 12.dp,
                        ) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "设为系统默认浏览器",
                                summary = "接管系统外部链接跳转，未匹配规则的网址交由备选浏览器打开",
                                startAction = {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInBrowser,
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                                onClick = {
                                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                    runCatching { context.startActivity(intent) }.onFailure {
                                        Toast.makeText(context, "请在系统设置中将本应用设为默认浏览器", Toast.LENGTH_LONG).show()
                                    }
                                },
                            )
                        }

                        CardSegment(
                            isFirst = false,
                            isLast = true,
                            outerHorizontalPadding = 12.dp,
                        ) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "兜底备选浏览器",
                                summary = if (fallbackBrowser.isNullOrBlank()) {
                                    "点击选择（未匹配规则时直接由此浏览器打开）"
                                } else {
                                    "$fallbackAppDisplayName · $fallbackBrowser"
                                },
                                startAction = if (!fallbackBrowser.isNullOrBlank()) {
                                    {
                                        Box(modifier = Modifier.padding(end = 12.dp)) {
                                            LinkAppIcon(packageName = fallbackBrowser!!, size = 36.dp)
                                        }
                                    }
                                } else null,
                                endActions = if (!fallbackBrowser.isNullOrBlank()) {
                                    {
                                        MiuixTextButton(
                                            text = "清除",
                                            onClick = {
                                                scope.launch { LinkRuleRepository.setFallbackBrowser(context, null) }
                                            },
                                        )
                                    }
                                } else null,
                                onClick = { showBrowserPickerSheet = true },
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // 模块 4：【规则匹配沙盒测试】（折叠控制台）
        // ==========================================
        item(key = "collapsible_block_sandbox") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .animateContentSize(animationSpec = tween(250)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CardSegment(
                    isFirst = true,
                    isLast = !sandboxExpanded,
                    outerHorizontalPadding = 12.dp,
                ) {
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = "规则匹配沙盒",
                        summary = "输入任意链接模拟规则命中、短链重定向与提取改写流程",
                        onClick = { sandboxExpanded = !sandboxExpanded },
                        endActions = {
                            Icon(
                                imageVector = if (sandboxExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        },
                    )
                }

                AnimatedVisibility(
                    visible = sandboxExpanded,
                    enter = expandVertically(tween(250)) + fadeIn(),
                    exit = shrinkVertically(tween(250)) + fadeOut(),
                ) {
                    CardSegment(
                        isFirst = false,
                        isLast = true,
                        outerHorizontalPadding = 12.dp,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // 输入框与粘贴
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    MiuixLabeledTextField(
                                        value = sandboxUrl,
                                        onValueChange = { sandboxUrl = it },
                                        label = "输入测试链接或短链",
                                        useLabelAsPlaceholder = false,
                                        singleLine = true,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MiuixTheme.colorScheme.surface)
                                        .clickable {
                                            val text = clipboardManager.getText()?.text
                                            if (!text.isNullOrBlank()) sandboxUrl = text.trim()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "粘贴",
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            // 快捷填充 Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val samples = listOf(
                                    "哔哩哔哩 b23" to "https://b23.tv/av170001",
                                    "知乎短链" to "https://link.zhihu.com/?target=https%3A%2F%2Fgithub.com",
                                    "Twitter t.co" to "https://t.co/sample",
                                )
                                samples.forEach { (label, sample) ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MiuixTheme.colorScheme.surface)
                                            .clickable { sandboxUrl = sample }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                    ) {
                                        MiuixText(text = label, style = MiuixTheme.textStyles.footnote2)
                                    }
                                }
                            }

                            // 测试执行按钮
                            MiuixTextButton(
                                text = if (isSandboxSimulating) "正在推演..." else "模拟测试分发",
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                enabled = sandboxUrl.isNotBlank() && !isSandboxSimulating,
                                onClick = {
                                    scope.launch {
                                        isSandboxSimulating = true
                                        sandboxResult = withContext(Dispatchers.IO) {
                                            LinkDispatcher.routeWithTrace(context, sandboxUrl.trim())
                                        }
                                        isSandboxSimulating = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // 结果展示区域
                            val trace = sandboxResult
                            if (trace != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MiuixTheme.colorScheme.surface)
                                        .padding(12.dp),
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        when (val res = trace.finalResult) {
                                            is LinkDispatchResult.Routed -> {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    LinkAppIcon(packageName = res.rule.targetPackage, size = 32.dp)
                                                    Spacer(Modifier.width(8.dp))
                                                    Column {
                                                        MiuixText(
                                                            text = "命中: ${res.rule.name} ➔ ${linkAppNameCache[res.rule.targetPackage].orEmpty().ifBlank { res.rule.targetPackage }}",
                                                            style = MiuixTheme.textStyles.body2,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MiuixTheme.colorScheme.primary,
                                                        )
                                                        MiuixText(
                                                            text = res.url,
                                                            style = MiuixTheme.textStyles.footnote2,
                                                            fontFamily = FontFamily.Monospace,
                                                        )
                                                    }
                                                }
                                            }
                                            is LinkDispatchResult.Exempted -> {
                                                MiuixText(text = "域名被豁免 (${res.domain})，将由系统默认方式打开", style = MiuixTheme.textStyles.body2)
                                            }
                                            is LinkDispatchResult.SystemDefault -> {
                                                MiuixText(text = "重分发未匹配到后续规则，交由系统默认打开", style = MiuixTheme.textStyles.body2)
                                            }
                                            LinkDispatchResult.NoRule -> {
                                                MiuixText(text = "未匹配到任何分发规则（交由备选浏览器打开）", style = MiuixTheme.textStyles.body2)
                                            }
                                            LinkDispatchResult.Failed -> {
                                                MiuixText(text = "分发解析失败", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.error)
                                            }
                                        }

                                        if (trace.steps.isNotEmpty()) {
                                            trace.steps.forEachIndexed { i, step ->
                                                MiuixText(
                                                    text = "↳ [${i + 1}] ${step.ruleName}: ${step.fromUrl} ➔ ${step.resolvedUrl ?: "无法解析"}",
                                                    style = MiuixTheme.textStyles.footnote2,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                )
                                            }
                                        }

                                        val routedRes = trace.finalResult as? LinkDispatchResult.Routed
                                        if (routedRes != null) {
                                            MiuixTextButton(
                                                text = "执行最终跳转验证",
                                                onClick = {
                                                    scope.launch {
                                                        LinkLauncher.launch(
                                                            context = context,
                                                            url = routedRes.url,
                                                            targetPackage = routedRes.rule.targetPackage,
                                                            excludeFromRecents = routedRes.rule.excludeFromRecents,
                                                            component = routedRes.rule.component,
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 模块 5：【工具与数据管理】（折叠控制台）
        // ==========================================
        item(key = "collapsible_block_tools") {
            val toolsSummary = "常用模板 · 豁免域名 (${exempts.size}) · 提取 (${extracts.size}) · 备份与还原"

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .animateContentSize(animationSpec = tween(250)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CardSegment(
                    isFirst = true,
                    isLast = !toolsExpanded,
                    outerHorizontalPadding = 12.dp,
                ) {
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = "工具与数据管理",
                        summary = toolsSummary,
                        onClick = { toolsExpanded = !toolsExpanded },
                        endActions = {
                            Icon(
                                imageVector = if (toolsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        },
                    )
                }

                AnimatedVisibility(
                    visible = toolsExpanded,
                    enter = expandVertically(tween(250)) + fadeIn(),
                    exit = shrinkVertically(tween(250)) + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CardSegment(isFirst = false, isLast = false, outerHorizontalPadding = 12.dp) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "导入常用模板",
                                summary = "一键添加常用站点的深链规则（自动识别已安装应用）",
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Default.LibraryAdd,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                    )
                                },
                                endActions = {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                },
                                onClick = { showBuiltinImportDialog = true },
                            )
                        }

                        CardSegment(isFirst = false, isLast = false, outerHorizontalPadding = 12.dp) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "豁免域名",
                                summary = if (exempts.isEmpty()) "这些域名不会被规则接管" else "当前已配置 ${exempts.size} 项豁免域名",
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                    )
                                },
                                endActions = {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                },
                                onClick = { showExemptsSheet = true },
                            )
                        }

                        CardSegment(isFirst = false, isLast = false, outerHorizontalPadding = 12.dp) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "链接提取规则",
                                summary = "从文本中找出链接的正则（当前 ${extracts.size} 项）",
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Default.FindInPage,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                    )
                                },
                                endActions = {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                },
                                onClick = { showExtractsSheet = true },
                            )
                        }

                        CardSegment(isFirst = false, isLast = false, outerHorizontalPadding = 12.dp) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "导出配置到剪贴板",
                                summary = "复制全部规则、豁免域名与提取规则 JSON 到剪贴板",
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                    )
                                },
                                onClick = {
                                    scope.launch {
                                        val success = LinkConfigTransfer.exportToClipboard(context)
                                        if (success) {
                                            Toast.makeText(context, "配置已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "剪贴板服务不可用", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                            )
                        }

                        CardSegment(isFirst = false, isLast = true, outerHorizontalPadding = 12.dp) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = "从剪贴板导入规则",
                                summary = "识别剪贴板中的备份或 LinkGo 规则 JSON 并勾选导入",
                                startAction = {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                    )
                                },
                                onClick = {
                                    val parsed = LinkConfigTransfer.parseClipboardBackup(context)
                                    if (parsed == null || (parsed.rules.isEmpty() && parsed.exempts.isEmpty() && parsed.extracts.isEmpty())) {
                                        Toast.makeText(context, "剪贴板中未找到有效的规则配置", Toast.LENGTH_SHORT).show()
                                    } else {
                                        clipboardParsedBackup = parsed
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        item(key = "link_dispatch_bottom_spacer") {
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    // 常用模板导入弹窗
    LinkTemplateImportDialog(
        show = showBuiltinImportDialog,
        existingRules = rules,
        onDismissRequest = { showBuiltinImportDialog = false },
        onImport = { selectedRules ->
            scope.launch {
                LinkRuleRepository.updateRules(context) { list ->
                    val existingPatterns = list.map { it.pattern }.toSet()
                    var nextPriority = (list.maxOfOrNull { it.priority } ?: -1) + 1
                    val toAdd = selectedRules
                        .filter { it.pattern !in existingPatterns }
                        .map { it.copy(priority = nextPriority++) }
                    list + toAdd
                }
            }
        },
    )

    // 剪贴板导入弹窗
    val activeClipboardBackup = clipboardParsedBackup
    LinkTemplateImportDialog(
        show = activeClipboardBackup != null,
        existingRules = rules,
        customCandidates = activeClipboardBackup?.rules,
        dialogTitle = "从剪贴板导入规则",
        dialogDescription = "已解析出 ${activeClipboardBackup?.rules?.size ?: 0} 条规则，请勾选要导入的规则。",
        preselectAllAvailable = true,
        onDismissRequest = { clipboardParsedBackup = null },
        onImport = { selectedRules ->
            val backupToImport = activeClipboardBackup?.copy(rules = selectedRules)
            clipboardParsedBackup = null
            if (backupToImport != null) {
                scope.launch {
                    LinkRuleRepository.importConfig(context, backupToImport)
                }
            }
        },
    )

    // 豁免域名弹窗
    LinkExemptsBottomSheet(
        show = showExemptsSheet,
        exempts = exempts,
        onDismissRequest = { showExemptsSheet = false },
        onAdd = { newExempt ->
            scope.launch {
                LinkRuleRepository.updateExemptDomains(context) { it + newExempt }
            }
        },
        onDelete = { toDelete ->
            scope.launch {
                LinkRuleRepository.updateExemptDomains(context) { list ->
                    list.filterNot { it.domain == toDelete.domain }
                }
            }
        },
        onToggle = { target, enabled ->
            scope.launch {
                LinkRuleRepository.updateExemptDomains(context) { list ->
                    list.map { if (it.domain == target.domain) it.copy(isEnabled = enabled) else it }
                }
            }
        },
    )

    // 链接提取正则弹窗
    LinkExtractsBottomSheet(
        show = showExtractsSheet,
        extracts = extracts,
        onDismissRequest = { showExtractsSheet = false },
        onAdd = { newExtract ->
            scope.launch {
                LinkRuleRepository.updateExtractPatterns(context) { it + newExtract }
            }
        },
        onDelete = { toDelete ->
            scope.launch {
                LinkRuleRepository.updateExtractPatterns(context) { list ->
                    list.filterNot { it.id == toDelete.id }
                }
            }
        },
        onToggle = { target, enabled ->
            scope.launch {
                LinkRuleRepository.updateExtractPatterns(context) { list ->
                    list.map { if (it.id == target.id) it.copy(isEnabled = enabled) else it }
                }
            }
        },
        onResetDefaults = {
            scope.launch {
                LinkRuleRepository.updateExtractPatterns(context) { LinkExtractPattern.builtinDefaults() }
            }
        },
    )

    // 雷达横幅停留时长选择弹窗
    MiuixRadioSelectDialog(
        show = showHoldPickerSheet,
        onDismissRequest = { showHoldPickerSheet = false },
        title = "雷达横幅停留时长",
        options = holdValues,
        selected = radarHoverHoldMs,
        optionLabel = { ms -> holdLabels.getOrElse(holdValues.indexOf(ms)) { "${ms}毫秒" } },
        onSelect = { targetMs ->
            scope.launch { LinkRuleRepository.setRadarHoverHoldMs(context, targetMs) }
        },
    )

    // 兜底备选浏览器选择弹窗
    if (showBrowserPickerSheet) {
        var installedBrowsers by remember { mutableStateOf<List<BrowserAppOption>>(emptyList()) }
        LaunchedEffect(Unit) {
            installedBrowsers = withContext(Dispatchers.IO) {
                BrowserSelectorActivity.queryBrowsers(context)
            }
        }
        MiuixBottomSheet(
            show = true,
            title = "选择兜底备选浏览器",
            onDismissRequest = { showBrowserPickerSheet = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (installedBrowsers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        MiuixText(text = "未检测到外部已安装的浏览器", style = MiuixTheme.textStyles.body2)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(items = installedBrowsers, key = { it.packageName }) { browser ->
                            CardSegment(isFirst = true, isLast = true, outerHorizontalPadding = 0.dp) {
                                BasicComponent(
                                    modifier = Modifier.fillMaxWidth(),
                                    title = browser.label,
                                    summary = browser.packageName,
                                    startAction = {
                                        Box(modifier = Modifier.padding(end = 12.dp)) {
                                            LinkAppIcon(packageName = browser.packageName, size = 36.dp)
                                        }
                                    },
                                    onClick = {
                                        scope.launch {
                                            LinkRuleRepository.setFallbackBrowser(context, browser.packageName)
                                            showBrowserPickerSheet = false
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    // 批量删除确认弹窗
    MiuixConfirmDialog(
        show = showBatchDeleteDialog,
        title = "批量删除规则",
        message = "确定要删除选中的 ${selectedRuleIds.size} 条规则吗？此操作无法撤销。",
        onDismissRequest = { showBatchDeleteDialog = false },
        confirmText = "删除",
        dismissText = "取消",
        onConfirm = {
            showBatchDeleteDialog = false
            val toDelete = selectedRuleIds.toSet()
            scope.launch {
                LinkRuleRepository.updateRules(context) { list ->
                    list.filterNot { it.id in toDelete }
                }
                Toast.makeText(context, "已删除 ${toDelete.size} 条规则", Toast.LENGTH_SHORT).show()
                selectedRuleIds.clear()
                isSelectionMode = false
            }
        },
    )
}

/**
 * 规则多选模式底部工具条 (1:1 对标 LinkGo 原版交互：全选、批量启停、批量分享、批量复刻、批量删除红图标)。
 */
@Composable
private fun LinkRulesSelectionBottomBar(
    selectedCount: Int,
    totalCount: Int,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onBatchToggleEnabled: () -> Unit,
    onBatchShare: () -> Unit,
    onBatchDuplicate: () -> Unit,
    onBatchDelete: () -> Unit,
    onExit: () -> Unit,
) {
    CardSegment(
        isFirst = false,
        isLast = true,
        outerHorizontalPadding = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            // 全选 / 取消全选
            IconButton(onClick = onToggleSelectAll) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = if (allSelected) "取消全选" else "全选",
                    tint = if (allSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                )
            }
            // 批量开关
            IconButton(onClick = onBatchToggleEnabled, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.Default.ToggleOn,
                    contentDescription = "批量启停",
                    tint = if (selectedCount > 0) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f),
                )
            }
            // 分享/复制
            IconButton(onClick = onBatchShare, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "批量复制规则",
                    tint = if (selectedCount > 0) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f),
                )
            }
            // 批量复刻
            IconButton(onClick = onBatchDuplicate, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "批量复刻",
                    tint = if (selectedCount > 0) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f),
                )
            }
            // 批量删除 (红图标)
            IconButton(onClick = onBatchDelete, enabled = selectedCount > 0) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "批量删除",
                    tint = if (selectedCount > 0) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f),
                )
            }
        }
    }
}

/**
 * 单条链接分发规则展示行（Miuix 风格高阶卡片：左侧 40dp 高清应用图标、标题加粗、等宽 Pattern 浅底小标签、状态徽章胶囊、右侧 Switch）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinkDispatchRuleItemRow(
    rule: LinkDispatchRule,
    appName: String,
    isFirst: Boolean,
    isLast: Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onToggleEnabled: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val isAppInstalled = remember(rule.targetPackage) {
        rule.targetPackage.isBlank() || runCatching { context.packageManager.getPackageInfo(rule.targetPackage, 0) }.isSuccess
    }

    val targetLine = if (rule.resolveShortLink && rule.resolveStrategy == LinkResolveStrategy.REDISPATCH && rule.targetPackage.isBlank()) {
        "短链重分发（重新匹配后续规则）"
    } else if (appName.isNotBlank() && appName != rule.targetPackage) {
        "$appName · ${rule.targetPackage}"
    } else {
        rule.targetPackage.ifBlank { "未指定目标应用" }
    }

    val matchLabel = when (rule.matchType) {
        LinkMatchType.CONTAINS -> "包含"
        LinkMatchType.REGEX -> "正则"
        LinkMatchType.EXACT -> "精确"
    }

    CardSegment(
        isFirst = isFirst,
        isLast = isLast,
        outerHorizontalPadding = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else MiuixTheme.colorScheme.surface
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // 第一行：40dp 图标 + 规则名称 + 目标说明 + (Switch 或 拖动把手)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelectionMode) {
                    Box(modifier = Modifier.padding(end = 10.dp)) {
                        top.yukonga.miuix.kmp.basic.Checkbox(
                            state = if (isSelected) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off,
                            onClick = onClick,
                        )
                    }
                }
                Box(modifier = Modifier.padding(end = 12.dp)) {
                    LinkAppIcon(packageName = rule.targetPackage, size = 40.dp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiuixText(
                            text = rule.name.ifBlank { rule.pattern },
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!isAppInstalled) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.error.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
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
                        text = targetLine,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "拖动把手",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = onToggleEnabled,
                    )
                }
            }

            // 第二行：Pattern 浅色等宽背景标签 + 状态胶囊徽章
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Pattern 标签
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MiuixTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    MiuixText(
                        text = rule.pattern,
                        style = MiuixTheme.textStyles.footnote2,
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 状态徽章
                val badges = buildList {
                    add(matchLabel)
                    if (rule.resolveShortLink && rule.resolveStrategy != LinkResolveStrategy.NONE) {
                        add(if (rule.resolveStrategy == LinkResolveStrategy.REDISPATCH) "重分发" else "短链")
                    }
                    if (rule.excludeFromRecents) add("无痕")
                    if (!rule.template.isNullOrBlank()) add("改写")
                    if (rule.hitCount > 0) add("${rule.hitCount}次")
                }
                badges.forEach { badge ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.surface)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        MiuixText(
                            text = badge,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkExemptsBottomSheet(
    show: Boolean,
    exempts: List<LinkExemptDomain>,
    onDismissRequest: () -> Unit,
    onAdd: (LinkExemptDomain) -> Unit,
    onDelete: (LinkExemptDomain) -> Unit,
    onToggle: (LinkExemptDomain, Boolean) -> Unit,
) {
    if (!show) return
    var newDomain by remember { mutableStateOf("") }

    MiuixBottomSheet(
        show = true,
        title = "豁免域名",
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiuixText(
                text = "属于豁免域名的网址不会被规则接管，直接交由系统默认方式打开。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    MiuixLabeledTextField(
                        value = newDomain,
                        onValueChange = { newDomain = it },
                        label = "添加豁免域名，例如 example.com",
                        useLabelAsPlaceholder = false,
                        singleLine = true,
                    )
                }
                MiuixTextButton(
                    text = "添加",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = newDomain.trim().isNotBlank(),
                    onClick = {
                        val trimmed = newDomain.trim()
                        if (trimmed.isNotEmpty()) {
                            onAdd(LinkExemptDomain(domain = trimmed))
                            newDomain = ""
                        }
                    },
                )
            }

            if (exempts.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    MiuixText(text = "暂无豁免域名", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(items = exempts, key = { it.domain }) { item ->
                        CardSegment(isFirst = true, isLast = true, outerHorizontalPadding = 0.dp) {
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                title = item.domain,
                                endActions = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = item.isEnabled,
                                            onCheckedChange = { onToggle(item, it) },
                                        )
                                        IconButton(onClick = { onDelete(item) }) {
                                            Icon(
                                                imageVector = Icons.Default.Block,
                                                contentDescription = "删除",
                                                tint = MiuixTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkExtractsBottomSheet(
    show: Boolean,
    extracts: List<LinkExtractPattern>,
    onDismissRequest: () -> Unit,
    onAdd: (LinkExtractPattern) -> Unit,
    onDelete: (LinkExtractPattern) -> Unit,
    onToggle: (LinkExtractPattern, Boolean) -> Unit,
    onResetDefaults: () -> Unit,
) {
    if (!show) return
    var newName by remember { mutableStateOf("") }
    var newRegex by remember { mutableStateOf("") }

    MiuixBottomSheet(
        show = true,
        title = "链接提取规则",
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiuixText(
                text = "从复制的长文本或屏幕内容中提取出真实 URL 的正则表达式。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    MiuixLabeledTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = "规则名称",
                        useLabelAsPlaceholder = false,
                        singleLine = true,
                    )
                }
                Box(modifier = Modifier.weight(1.5f)) {
                    MiuixLabeledTextField(
                        value = newRegex,
                        onValueChange = { newRegex = it },
                        label = "正则表达式",
                        useLabelAsPlaceholder = false,
                        singleLine = true,
                    )
                }
                MiuixTextButton(
                    text = "添加",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = newRegex.trim().isNotBlank(),
                    onClick = {
                        val trimmedRegex = newRegex.trim()
                        val trimmedName = newName.trim().ifBlank { trimmedRegex }
                        if (trimmedRegex.isNotEmpty()) {
                            onAdd(LinkExtractPattern(name = trimmedName, pattern = trimmedRegex))
                            newName = ""
                            newRegex = ""
                        }
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                MiuixTextButton(
                    text = "恢复默认提取规则",
                    onClick = onResetDefaults,
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(items = extracts, key = { it.id }) { item ->
                    CardSegment(isFirst = true, isLast = true, outerHorizontalPadding = 0.dp) {
                        BasicComponent(
                            modifier = Modifier.fillMaxWidth(),
                            title = item.name,
                            summary = item.pattern,
                            endActions = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = item.isEnabled,
                                        onCheckedChange = { onToggle(item, it) },
                                    )
                                    IconButton(onClick = { onDelete(item) }) {
                                        Icon(
                                            imageVector = Icons.Default.Block,
                                            contentDescription = "删除",
                                            tint = MiuixTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
