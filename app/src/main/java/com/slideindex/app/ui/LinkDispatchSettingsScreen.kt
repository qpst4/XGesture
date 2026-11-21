package com.slideindex.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.LibraryAdd
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.link.LinkConfigTransfer
import com.slideindex.app.link.LinkDispatchResult
import com.slideindex.app.link.LinkDispatchRule
import com.slideindex.app.link.LinkDispatcher
import com.slideindex.app.link.LinkLauncher
import com.slideindex.app.link.LinkMatchType
import com.slideindex.app.link.LinkResolveStrategy
import com.slideindex.app.link.LinkRuleRepository
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.link.LinkAppIcon
import com.slideindex.app.ui.link.LinkExemptsBottomSheet
import com.slideindex.app.ui.link.LinkExtractsBottomSheet
import com.slideindex.app.ui.link.LinkRuleEditDialog
import com.slideindex.app.ui.link.LinkTemplateImportDialog
import com.slideindex.app.ui.link.linkAppNameCache
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixProgressCard
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffoldWithExpandableSearch
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LinkDispatchSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pageTitle = stringResource(R.string.link_dispatch_title)
    val pageDesc = "按规则把链接送往指定应用。规则按顺序匹配，命中第一条即停止；未命中或域名被豁免时使用系统默认方式打开。"

    val openSmallWinTitle = "小窗打开"
    val openSmallWinDesc = "分发规则触发时用小窗打开链接目标，关闭则全屏打开"
    val radarPointerTitle = "启用链接雷达"
    val radarPointerDesc = "指针靠近屏幕链接时高亮并显示打开气泡，指针点击或松手按规则跳转"
    val radarScreenTitle = "屏幕内容链接雷达"
    val radarScreenDesc = "全局复制或框选悬浮窗中高亮链接，轻点直达目标应用"

    val sandboxTitle = stringResource(R.string.link_dispatch_sandbox_title)
    val sandboxHint = stringResource(R.string.link_dispatch_sandbox_hint)
    val sandboxBtnText = stringResource(R.string.link_dispatch_sandbox_test_btn)
    val sandboxNoMatchText = stringResource(R.string.link_dispatch_sandbox_no_match)

    val importFailedMsg = stringResource(R.string.link_dispatch_import_failed)
    val exportSuccessMsg = stringResource(R.string.link_dispatch_export_success)

    val rules by LinkRuleRepository.observeRules(context).collectAsStateWithLifecycle(initialValue = emptyList())
    val exempts by LinkRuleRepository.observeExemptDomains(context).collectAsStateWithLifecycle(initialValue = emptyList())
    val extracts by LinkRuleRepository.observeExtractPatterns(context).collectAsStateWithLifecycle(initialValue = emptyList())
    val openInSmallWindow by LinkRuleRepository.observeOpenInSmallWindow(context).collectAsStateWithLifecycle(initialValue = true)
    val radarScreenContent by LinkRuleRepository.observeRadarScreenContent(context).collectAsStateWithLifecycle(initialValue = true)
    val radarPointer by LinkRuleRepository.observeRadarPointer(context).collectAsStateWithLifecycle(initialValue = true)
    val radarHoverHoldMs by LinkRuleRepository.observeRadarHoverHoldMs(context).collectAsStateWithLifecycle(
        initialValue = LinkRuleRepository.DEFAULT_RADAR_HOVER_HOLD_MS
    )
    val holdValues = listOf(650, 800, 1000, 1250, 1500, 2000, 3000, 0)
    val holdLabels = listOf("0.65 秒", "0.8 秒", "1 秒", "1.25 秒", "1.5 秒", "2 秒", "3 秒", "始终保持（不自动变黄）")
    val currentHoldIndex = holdValues.indexOf(radarHoverHoldMs).let { if (it < 0) 2 else it }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<LinkDispatchRule?>(null) }
    var showBuiltinImportDialog by remember { mutableStateOf(false) }
    var clipboardParsedBackup by remember { mutableStateOf<com.slideindex.app.link.LinkConfigBackup?>(null) }
    var showExemptsSheet by remember { mutableStateOf(false) }
    var showExtractsSheet by remember { mutableStateOf(false) }
    var appNamesVersion by remember { mutableStateOf(0) }

    // 预解析规则中的目标应用中文名
    LaunchedEffect(rules) {
        var changed = false
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            rules.map { it.targetPackage }.filter { it.isNotBlank() }.distinct().forEach { pkg ->
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
    val rulesHeaderTitle = if (searchQuery.isBlank()) {
        "链接分发规则 (共 ${rules.size} 条 · 已启用 $enabledCount 条)"
    } else {
        "链接分发规则 (找到 ${filteredRules.size} / ${rules.size} 条)"
    }

    SettingsLazyScreenScaffoldWithExpandableSearch(
        title = pageTitle,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onBack = onBack,
        hintResId = R.string.shortcut_test_search_hint,
        extraActions = {
            MiuixTopBarIconButton(
                onClick = {
                    editingRule = null
                    showEditDialog = true
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加规则",
                )
            }
        },
    ) {
        settingsLazyTipCard(
            key = "link_dispatch_intro_tip",
            text = pageDesc,
        )

        // 1. 链接分发规则区
        settingsLazySmallTitle(
            key = "link_dispatch_rules_header",
            title = rulesHeaderTitle,
        )

        groupedCardItems(
            keyPrefix = "dispatch_add_rule_card",
            items = listOf(
                settingsCardScopeItem("add_new_rule_btn") {
                    SettingNavigationRow(
                        title = "添加规则",
                        subtitle = "新建一条自定义链接匹配与打开目标规则",
                        icon = { MiuixIcon(imageVector = Icons.Default.Add, contentDescription = null) },
                        onClick = {
                            editingRule = null
                            showEditDialog = true
                        },
                    )
                },
            ),
        )

        if (filteredRules.isEmpty()) {
            settingsLazyTipCard(
                key = "link_dispatch_rules_empty_tip",
                text = if (rules.isEmpty()) "还没有规则。点击上方「添加规则」或下方「工具 -> 导入常用模板」开始分发链接。"
                    else "没有找到匹配的规则，试试其他名称、域名或应用。",
            )
        } else {
            groupedCardItems(
                keyPrefix = "dispatch_rules",
                items = filteredRules.map { rule: LinkDispatchRule ->
                    settingsCardScopeItem(rule.id) {
                        LinkDispatchRuleItemRow(
                            rule = rule,
                            appName = linkAppNameCache[rule.targetPackage].orEmpty().ifBlank { rule.targetPackage },
                            onClick = {
                                editingRule = rule
                                showEditDialog = true
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
                },
            )
        }

        // 2. 工具区（导入常用模板 / 豁免域名 / 链接提取规则）
        settingsLazySmallTitle(
            key = "link_dispatch_tools_header",
            title = "工具",
        )

        groupedCardItems(
            keyPrefix = "dispatch_tools",
            items = listOf(
                settingsCardScopeItem("import_builtin_templates") {
                    SettingNavigationRow(
                        title = "导入常用模板",
                        subtitle = "一键添加常用站点的深链规则（自动识别已安装应用）",
                        icon = { MiuixIcon(imageVector = Icons.Default.LibraryAdd, contentDescription = null) },
                        onClick = { showBuiltinImportDialog = true },
                    )
                },
                settingsCardScopeItem("exempt_domains") {
                    SettingNavigationRow(
                        title = "豁免域名",
                        subtitle = if (exempts.isEmpty()) "这些域名不会被规则接管"
                            else "这些域名不会被规则接管（当前 ${exempts.size} 项）",
                        icon = { MiuixIcon(imageVector = Icons.Default.Block, contentDescription = null) },
                        onClick = { showExemptsSheet = true },
                    )
                },
                settingsCardScopeItem("extract_patterns") {
                    SettingNavigationRow(
                        title = "链接提取规则",
                        subtitle = "从文本中找出链接的正则（当前 ${extracts.size} 项）",
                        icon = { MiuixIcon(imageVector = Icons.Default.FindInPage, contentDescription = null) },
                        onClick = { showExtractsSheet = true },
                    )
                },
            ),
        )

        // 3. 分发行为区（链接雷达与小窗打开）
        settingsLazySmallTitle(
            key = "link_dispatch_behavior_header",
            title = "分发行为",
        )

        groupedCardItems(
            keyPrefix = "dispatch_behavior",
            items = buildList {
                add(
                    settingsCardScopeItem("radar_pointer") {
                        BasicComponent(
                            modifier = Modifier.fillMaxWidth(),
                            title = radarPointerTitle,
                            summary = radarPointerDesc,
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
                )
                if (radarPointer) {
                    add(
                        settingsCardScopeItem("radar_hover_hold_ms") {
                            SettingDropdownRow(
                                title = "雷达横幅停留时长",
                                subtitle = "悬浮球指针停在链接上时保持显示打开横幅的时间（短链从解析完成后起算），超时未松手则切换为黄框取词",
                                items = holdLabels,
                                selectedIndex = currentHoldIndex,
                                enabled = true,
                                onSelectedIndexChange = { index ->
                                    val targetMs = holdValues.getOrElse(index) { LinkRuleRepository.DEFAULT_RADAR_HOVER_HOLD_MS }
                                    scope.launch { LinkRuleRepository.setRadarHoverHoldMs(context, targetMs) }
                                },
                            )
                        }
                    )
                }
                add(
                    settingsCardScopeItem("radar_screen") {
                        BasicComponent(
                            modifier = Modifier.fillMaxWidth(),
                            title = radarScreenTitle,
                            summary = radarScreenDesc,
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
                )
                add(
                    settingsCardScopeItem("open_small_window") {
                        BasicComponent(
                            modifier = Modifier.fillMaxWidth(),
                            title = openSmallWinTitle,
                            summary = openSmallWinDesc,
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
                )
            },
        )

        // 4. 备份区（导出与导入，自动兼容备份 JSON 与 LinkGo JSON）
        settingsLazySmallTitle(
            key = "link_dispatch_backup_header",
            title = "备份",
        )

        groupedCardItems(
            keyPrefix = "dispatch_backup",
            items = listOf(
                settingsCardScopeItem("export_clipboard") {
                    SettingNavigationRow(
                        title = "导出规则到剪贴板",
                        subtitle = "复制全部规则、豁免域名与提取规则 JSON 到剪贴板",
                        icon = { MiuixIcon(imageVector = Icons.Default.FileUpload, contentDescription = null) },
                        onClick = {
                            scope.launch {
                                val success = LinkConfigTransfer.exportToClipboard(context)
                                if (success) {
                                    Toast.makeText(context, exportSuccessMsg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )
                },
                settingsCardScopeItem("import_clipboard") {
                    SettingNavigationRow(
                        title = "从剪贴板导入规则",
                        subtitle = "识别剪贴板中的备份或 LinkGo 规则 JSON 并勾选导入",
                        icon = { MiuixIcon(imageVector = Icons.Default.Download, contentDescription = null) },
                        onClick = {
                            val parsed = LinkConfigTransfer.parseClipboardBackup(context)
                            if (parsed == null) {
                                Toast.makeText(context, importFailedMsg, Toast.LENGTH_SHORT).show()
                            } else if (parsed.rules.isNotEmpty()) {
                                clipboardParsedBackup = parsed
                            } else {
                                scope.launch {
                                    val result = LinkRuleRepository.importConfig(context, parsed)
                                    Toast.makeText(
                                        context,
                                        "导入完成：豁免 ${result.exemptsAdded} 条，提取 ${result.extractsAdded} 条",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                    )
                },
            ),
        )

        // 5. 规则匹配沙盒测试
        settingsLazySmallTitle(
            key = "link_dispatch_sandbox_header",
            title = sandboxTitle,
        )

        item(key = "link_dispatch_sandbox_card") {
            var testUrl by rememberSaveable { mutableStateOf("") }
            var testTrace by remember { mutableStateOf<com.slideindex.app.link.LinkRouteTrace?>(null) }
            var isTesting by remember { mutableStateOf(false) }

            MiuixProgressCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MiuixLabeledTextField(
                        value = testUrl,
                        onValueChange = { testUrl = it },
                        label = sandboxHint,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            MiuixTextButton(
                                text = "b23.tv",
                                onClick = { testUrl = "https://b23.tv/av170001" },
                            )
                            MiuixTextButton(
                                text = "知乎",
                                onClick = { testUrl = "https://www.zhihu.com/question/19550294" },
                            )
                        }
                        MiuixTextButton(
                            text = if (isTesting) "解析中…" else sandboxBtnText,
                            onClick = {
                                if (testUrl.isNotBlank() && !isTesting) {
                                    isTesting = true
                                    scope.launch {
                                        val trace = LinkDispatcher.routeWithTrace(context, testUrl.trim())
                                        testTrace = trace
                                        isTesting = false
                                    }
                                }
                            },
                        )
                    }
                    testTrace?.let { trace ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            trace.steps.forEachIndexed { idx, step ->
                                val resolvedInfo = step.resolvedUrl ?: "解析失败（保持原链接）"
                                MiuixText(
                                    text = "步骤 ${idx + 1} [短链规则: ${step.ruleName}]\n${step.fromUrl}\n  ↳ $resolvedInfo",
                                    style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            when (val res = trace.finalResult) {
                                is LinkDispatchResult.Routed -> {
                                    MiuixText(
                                        text = stringResource(
                                            R.string.link_dispatch_sandbox_matched,
                                            res.rule.name,
                                            linkAppNameCache[res.rule.targetPackage] ?: res.rule.targetPackage,
                                        ),
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                    MiuixText(
                                        text = "目标: ${res.url}",
                                        style = MiuixTheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace),
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        MiuixTextButton(
                                            text = "立即打开测试",
                                            onClick = {
                                                scope.launch {
                                                    LinkLauncher.launch(
                                                        context = context,
                                                        url = res.url,
                                                        targetPackage = res.rule.targetPackage,
                                                        excludeFromRecents = res.rule.excludeFromRecents,
                                                        component = res.rule.component,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                }
                                is LinkDispatchResult.SystemDefault -> {
                                    MiuixText(
                                        text = if (trace.steps.isNotEmpty()) {
                                            "短链已解析，但解析后的目标网址未匹配其他规则，将交由系统默认浏览器打开：\n${res.url}"
                                        } else {
                                            sandboxNoMatchText
                                        },
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                is LinkDispatchResult.Exempted -> {
                                    MiuixText(
                                        text = "命中豁免域名 (${res.domain})，不接管：\n${res.url}",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                else -> {
                                    MiuixText(
                                        text = sandboxNoMatchText,
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "link_dispatch_bottom_spacer") {
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    LinkRuleEditDialog(
        show = showEditDialog,
        originalRule = editingRule,
        onDismissRequest = { showEditDialog = false },
        onSave = { savedRule ->
            scope.launch {
                LinkRuleRepository.updateRules(context) { list ->
                    val idx = list.indexOfFirst { it.id == savedRule.id }
                    if (idx >= 0) {
                        list.toMutableList().apply {
                            set(
                                idx,
                                savedRule.copy(
                                    hitCount = list[idx].hitCount,
                                    lastHitAt = list[idx].lastHitAt,
                                    priority = list[idx].priority,
                                ),
                            )
                        }
                    } else {
                        val nextPriority = (list.maxOfOrNull { it.priority } ?: -1) + 1
                        list + savedRule.copy(priority = nextPriority)
                    }
                }
                Toast.makeText(context, "规则已保存", Toast.LENGTH_SHORT).show()
            }
        },
        onDelete = { ruleToDelete ->
            scope.launch {
                LinkRuleRepository.updateRules(context) { list ->
                    list.filterNot { it.id == ruleToDelete.id }
                }
                Toast.makeText(context, context.getString(R.string.link_dispatch_rule_deleted, ruleToDelete.name), Toast.LENGTH_SHORT).show()
            }
        },
    )

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

    val activeClipboardBackup = clipboardParsedBackup
    LinkTemplateImportDialog(
        show = activeClipboardBackup != null,
        existingRules = rules,
        customCandidates = activeClipboardBackup?.rules,
        dialogTitle = "从剪贴板导入规则",
        dialogDescription = "已从剪贴板解析出 ${activeClipboardBackup?.rules?.size ?: 0} 条规则，请勾选要导入的规则。",
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

    LinkExemptsBottomSheet(
        show = showExemptsSheet,
        exempts = exempts,
        onDismissRequest = { showExemptsSheet = false },
        onAddExempt = { newExempt ->
            scope.launch {
                LinkRuleRepository.updateExemptDomains(context) { it + newExempt }
            }
        },
        onToggleExempt = { target, enabled ->
            scope.launch {
                LinkRuleRepository.updateExemptDomains(context) { list ->
                    list.map { if (it.id == target.id) it.copy(isEnabled = enabled) else it }
                }
            }
        },
        onDeleteExempt = { target ->
            scope.launch {
                LinkRuleRepository.updateExemptDomains(context) { list ->
                    list.filterNot { it.id == target.id }
                }
            }
        },
    )

    LinkExtractsBottomSheet(
        show = showExtractsSheet,
        extracts = extracts,
        onDismissRequest = { showExtractsSheet = false },
        onAddExtract = { newExtract ->
            scope.launch {
                LinkRuleRepository.updateExtractPatterns(context) { it + newExtract }
            }
        },
        onUpdateExtract = { updated ->
            scope.launch {
                LinkRuleRepository.updateExtractPatterns(context) { list ->
                    list.map { if (it.id == updated.id) updated else it }
                }
            }
        },
        onToggleExtract = { target, enabled ->
            scope.launch {
                LinkRuleRepository.updateExtractPatterns(context) { list ->
                    list.map { if (it.id == target.id) it.copy(isEnabled = enabled) else it }
                }
            }
        },
        onDeleteExtract = { target ->
            scope.launch {
                LinkRuleRepository.updateExtractPatterns(context) { list ->
                    list.filterNot { it.id == target.id }
                }
            }
        },
    )
}

/**
 * 单条链接分发规则展示行（对齐 gevo item_link_rule.xml：左侧 40dp 应用图标、名称、目标与策略详情、等宽 pattern、右侧 Switch）。
 */
@Composable
private fun LinkDispatchRuleItemRow(
    rule: LinkDispatchRule,
    appName: String,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    val targetLine = if (rule.resolveShortLink && rule.resolveStrategy == LinkResolveStrategy.REDISPATCH && rule.targetPackage.isBlank()) {
        "解析后重新分发"
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
    val badges = buildList {
        add(matchLabel)
        if (rule.resolveShortLink && rule.resolveStrategy != LinkResolveStrategy.NONE) {
            add(if (rule.resolveStrategy == LinkResolveStrategy.REDISPATCH) "短链重分发" else "解析短链")
        }
        if (rule.excludeFromRecents) add("无痕")
        if (!rule.template.isNullOrBlank()) add("改写")
        add(if (rule.hitCount > 0) "命中 ${rule.hitCount} 次" else "从未命中")
    }.joinToString(" · ")

    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        title = rule.name.ifBlank { rule.pattern },
        summary = "$targetLine\n${rule.pattern}\n$badges",
        startAction = {
            Box(modifier = Modifier.padding(end = 12.dp)) {
                LinkAppIcon(packageName = rule.targetPackage, size = 40.dp)
            }
        },
        onClick = onClick,
        endActions = {
            Switch(
                checked = rule.isEnabled,
                onCheckedChange = onToggleEnabled,
            )
        },
    )
}
