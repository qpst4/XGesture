package com.slideindex.app.ui.link

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.link.LinkDispatchResult
import com.slideindex.app.link.LinkDispatchRule
import com.slideindex.app.link.LinkDispatcher
import com.slideindex.app.link.LinkLauncher
import com.slideindex.app.link.LinkMatchType
import com.slideindex.app.link.LinkResolveStrategy
import com.slideindex.app.link.LinkRouteTrace
import com.slideindex.app.link.LinkRuleRepository
import com.slideindex.app.ui.SegmentedControl
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID

/**
 * 链接分发规则沉浸式全屏编辑页面。
 *
 * 借鉴 LinkGo-X：
 * 1. 沉浸式全屏表单（彻底解决软键盘弹起对弹窗的挤压）；
 * 2. 顶栏「测试」按钮 + 实时仿真 BottomSheet（拿未保存草稿直接仿真推演与拉起验证）；
 * 3. 严格遵循 Miuix 12dp 统一基线与分组卡片体系。
 */
@Composable
fun EditLinkRuleScreen(
    ruleId: String? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rules by remember { mutableStateOf<List<LinkDispatchRule>>(emptyList()) }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        rules = LinkRuleRepository.loadRules(context)
        isLoaded = true
    }

    val originalRule = remember(rules, ruleId) {
        if (ruleId != null) rules.find { it.id == ruleId } else null
    }

    var name by remember(originalRule) { mutableStateOf(originalRule?.name.orEmpty()) }
    var pattern by remember(originalRule) { mutableStateOf(originalRule?.pattern.orEmpty()) }
    var targetPackage by remember(originalRule) { mutableStateOf(originalRule?.targetPackage.orEmpty()) }
    var component by remember(originalRule) { mutableStateOf(originalRule?.component.orEmpty()) }
    var template by remember(originalRule) { mutableStateOf(originalRule?.template.orEmpty()) }
    var extractPattern by remember(originalRule) { mutableStateOf(originalRule?.extractPattern.orEmpty()) }
    var matchType by remember(originalRule) { mutableStateOf(originalRule?.matchType ?: LinkMatchType.CONTAINS) }
    var excludeFromRecents by remember(originalRule) { mutableStateOf(originalRule?.excludeFromRecents ?: false) }

    val initialResolveShort = remember(originalRule) { originalRule?.resolveShortLink == true }
    var resolveShortLink by remember(originalRule) { mutableStateOf(initialResolveShort) }
    var resolveStrategy by remember(originalRule) {
        val strat = originalRule?.resolveStrategy ?: LinkResolveStrategy.NONE
        mutableStateOf(if (strat == LinkResolveStrategy.NONE) LinkResolveStrategy.DIRECT else strat)
    }
    var isEnabled by remember(originalRule) { mutableStateOf(originalRule?.isEnabled ?: true) }

    var showAppPickerSheet by remember { mutableStateOf(false) }
    var showTestBottomSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var targetAppDisplayName by remember(targetPackage) {
        mutableStateOf(linkAppNameCache[targetPackage].orEmpty())
    }

    LaunchedEffect(targetPackage) {
        if (targetPackage.isBlank()) {
            targetAppDisplayName = ""
            return@LaunchedEffect
        }
        val cached = linkAppNameCache[targetPackage]
        if (!cached.isNullOrBlank()) {
            targetAppDisplayName = cached
            return@LaunchedEffect
        }
        val resolved = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(targetPackage, 0)).toString()
            }.getOrNull()
        }
        if (!resolved.isNullOrBlank()) {
            linkAppNameCache[targetPackage] = resolved
            targetAppDisplayName = resolved
        } else {
            targetAppDisplayName = targetPackage
        }
    }

    // 实时构造未保存的草稿规则供仿真测试使用
    val currentCandidateRule = remember(
        originalRule, name, pattern, targetPackage, component, template,
        extractPattern, matchType, excludeFromRecents, resolveShortLink,
        resolveStrategy, isEnabled,
    ) {
        LinkDispatchRule(
            id = originalRule?.id ?: "draft_test",
            name = name.trim().ifEmpty { pattern.trim() },
            pattern = pattern.trim(),
            targetPackage = targetPackage.trim(),
            component = component.trim().takeIf { it.isNotEmpty() },
            template = template.trim().takeIf { it.isNotEmpty() },
            extractPattern = extractPattern.trim().takeIf { it.isNotEmpty() },
            matchType = matchType,
            excludeFromRecents = excludeFromRecents,
            resolveShortLink = resolveShortLink,
            resolveStrategy = if (resolveShortLink) resolveStrategy else LinkResolveStrategy.NONE,
            isEnabled = isEnabled,
        )
    }

    fun handleSave() {
        val trimmedPattern = pattern.trim()
        if (trimmedPattern.isEmpty()) {
            Toast.makeText(context, "请填写匹配内容", Toast.LENGTH_SHORT).show()
            return
        }
        val trimmedPkg = targetPackage.trim()
        val effectiveStrategy = if (resolveShortLink) {
            if (resolveStrategy == LinkResolveStrategy.NONE) LinkResolveStrategy.DIRECT else resolveStrategy
        } else {
            LinkResolveStrategy.NONE
        }
        if (trimmedPkg.isEmpty() && effectiveStrategy != LinkResolveStrategy.REDISPATCH) {
            Toast.makeText(context, "请选择目标应用", Toast.LENGTH_SHORT).show()
            return
        }
        val ruleToSave = (originalRule ?: LinkDispatchRule(id = UUID.randomUUID().toString())).copy(
            name = name.trim().ifEmpty { trimmedPattern },
            pattern = trimmedPattern,
            targetPackage = trimmedPkg,
            component = component.trim().takeIf { it.isNotEmpty() },
            template = template.trim().takeIf { it.isNotEmpty() },
            extractPattern = extractPattern.trim().takeIf { it.isNotEmpty() },
            matchType = matchType,
            excludeFromRecents = excludeFromRecents,
            resolveShortLink = resolveShortLink,
            resolveStrategy = effectiveStrategy,
            isEnabled = isEnabled,
        )
        scope.launch {
            LinkRuleRepository.updateRules(context) { list ->
                val idx = list.indexOfFirst { it.id == ruleToSave.id }
                if (idx >= 0) {
                    list.toMutableList().apply {
                        set(
                            idx,
                            ruleToSave.copy(
                                hitCount = list[idx].hitCount,
                                lastHitAt = list[idx].lastHitAt,
                                priority = list[idx].priority,
                            ),
                        )
                    }
                } else {
                    val nextPriority = (list.maxOfOrNull { it.priority } ?: -1) + 1
                    list + ruleToSave.copy(priority = nextPriority)
                }
            }
            Toast.makeText(context, "规则已保存", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    SettingsScreenScaffold(
        title = if (originalRule == null) "新建规则" else "编辑规则",
        onBack = onBack,
        actions = {
            MiuixTextButton(
                text = "测试",
                onClick = { showTestBottomSheet = true },
            )
            if (originalRule != null) {
                MiuixTextButton(
                    text = "删除",
                    colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                    onClick = { showDeleteConfirmDialog = true },
                )
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                MiuixTextButton(
                    text = "保存规则",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = { handleSave() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        // 1. 匹配条件
        settingsLazySmallTitle(
            key = "edit_rule_title_conditions",
            title = "匹配条件",
        )

        groupedCardItems(
            keyPrefix = "edit_rule_condition",
            items = listOf(
                CardItem("name") {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        MiuixLabeledTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = "规则名称",
                            useLabelAsPlaceholder = false,
                            singleLine = true,
                        )
                    }
                },
                CardItem("match_type") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MiuixText(
                            text = "匹配方式",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        SegmentedControl(
                            items = listOf(LinkMatchType.CONTAINS, LinkMatchType.REGEX, LinkMatchType.EXACT),
                            selected = matchType,
                            labelRes = { type ->
                                when (type) {
                                    LinkMatchType.CONTAINS -> R.string.link_rule_match_contains
                                    LinkMatchType.REGEX -> R.string.link_rule_match_regex
                                    LinkMatchType.EXACT -> R.string.link_rule_match_exact
                                }
                            },
                            onSelect = { matchType = it },
                        )
                    }
                },
                CardItem("pattern") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        MiuixLabeledTextField(
                            value = pattern,
                            onValueChange = { pattern = it },
                            label = "匹配内容",
                            useLabelAsPlaceholder = false,
                            singleLine = true,
                        )
                        MiuixText(
                            text = "填写域名、网址片段或正则表达式，例如 b23.tv 或 ^https?://...",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                },
            ),
        )

        // 2. 打开目标
        settingsLazySmallTitle(
            key = "edit_rule_title_target",
            title = "打开目标",
        )

        val targetCardItems = buildList {
            add(
                CardItem("target_app") {
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = "目标应用",
                        summary = when {
                            targetPackage.isBlank() && resolveShortLink && resolveStrategy == LinkResolveStrategy.REDISPATCH ->
                                "未选择（短链解析后重新匹配其他规则）"
                            targetPackage.isBlank() ->
                                "点击选择应用"
                            targetAppDisplayName.isNotBlank() && targetAppDisplayName != targetPackage ->
                                "$targetAppDisplayName · $targetPackage"
                            else ->
                                targetPackage
                        },
                        startAction = if (targetPackage.isNotBlank()) {
                            {
                                Box(modifier = Modifier.padding(end = 12.dp)) {
                                    LinkAppIcon(packageName = targetPackage, size = 40.dp)
                                }
                            }
                        } else null,
                        onClick = { showAppPickerSheet = true },
                    )
                },
            )
            if (targetPackage.isNotBlank()) {
                add(
                    CardItem("target_component") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            MiuixLabeledTextField(
                                value = component,
                                onValueChange = { component = it },
                                label = "目标活动 (Activity，可选)",
                                useLabelAsPlaceholder = false,
                                singleLine = true,
                            )
                            MiuixText(
                                text = "留空则由系统默认路由；支持指定特定 Activity 类名",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    },
                )
            }
        }

        groupedCardItems(
            keyPrefix = "edit_rule_target",
            items = targetCardItems,
        )

        // 3. 重构模板 (可选)
        settingsLazySmallTitle(
            key = "edit_rule_title_template",
            title = "重构模板 (可选)",
        )

        val templateCardItems = buildList {
            add(
                CardItem("reconstruct_card") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // 1. 正则提取输入框
                        MiuixLabeledTextField(
                            value = extractPattern,
                            onValueChange = { extractPattern = it },
                            label = "正则提取 (留空提取整个)",
                            useLabelAsPlaceholder = false,
                            singleLine = true,
                        )

                        // 2. 重构链接输入框
                        MiuixLabeledTextField(
                            value = template,
                            onValueChange = { template = it },
                            label = "重构链接 (搭配变量使用)",
                            useLabelAsPlaceholder = false,
                            singleLine = true,
                        )

                        // 3. 变换工具选项
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MiuixText(
                                text = "变换选项",
                                style = MiuixTheme.textStyles.footnote2,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            val transforms = listOf(
                                ":url_dec" to "URL解码",
                                ":url_enc" to "URL编码",
                                ":b64_dec" to "B64解码",
                                ":b64_enc" to "B64编码",
                            )
                            transforms.forEach { (tag, label) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MiuixTheme.colorScheme.surface)
                                        .border(
                                            0.5.dp,
                                            MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f),
                                            RoundedCornerShape(6.dp),
                                        )
                                        .clickable {
                                            template = if (template.contains("{1}")) {
                                                template.replace("{1}", "{1$tag}")
                                            } else if (template.contains("{url}")) {
                                                template.replace("{url}", "{url$tag}")
                                            } else {
                                                template + "{url$tag}"
                                            }
                                        }
                                        .padding(horizontal = 7.dp, vertical = 3.dp),
                                ) {
                                    MiuixText(
                                        text = label,
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }

                        // 4. 常用变量说明与点击快速插入
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            MiuixText(
                                text = "常用变量 (点击插入)",
                                style = MiuixTheme.textStyles.footnote2,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val vars = listOf(
                                    "{url}" to "完整链接",
                                    "{1}" to "提取组1",
                                    "{2}" to "提取组2",
                                    "\$1" to "正则组1",
                                    "\$2" to "正则组2",
                                    "{host}" to "域名",
                                    "{path}" to "路径",
                                    "{query}" to "参数",
                                )
                                vars.forEach { (tag, desc) ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MiuixTheme.colorScheme.surface)
                                            .border(
                                                0.5.dp,
                                                MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f),
                                                RoundedCornerShape(6.dp),
                                            )
                                            .clickable { template += tag }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            MiuixText(
                                                text = tag,
                                                style = MiuixTheme.textStyles.footnote2,
                                                fontFamily = FontFamily.Monospace,
                                                color = MiuixTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            MiuixText(
                                                text = desc,
                                                style = MiuixTheme.textStyles.footnote2,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
            )
        }

        groupedCardItems(
            keyPrefix = "edit_rule_template_group",
            items = templateCardItems,
        )

        // 4. 处理方式与高级
        settingsLazySmallTitle(
            key = "edit_rule_title_advanced",
            title = "处理方式与高级",
        )

        val advancedCardItems = buildList {
            add(
                CardItem("resolve_short_link") {
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = "解析短链",
                        summary = "打开前先跟随重定向取得真实地址",
                        onClick = {
                            val next = !resolveShortLink
                            resolveShortLink = next
                            if (next && resolveStrategy == LinkResolveStrategy.NONE) {
                                resolveStrategy = LinkResolveStrategy.DIRECT
                            }
                        },
                        endActions = {
                            Switch(
                                checked = resolveShortLink,
                                onCheckedChange = { checked ->
                                    resolveShortLink = checked
                                    if (checked && resolveStrategy == LinkResolveStrategy.NONE) {
                                        resolveStrategy = LinkResolveStrategy.DIRECT
                                    }
                                },
                            )
                        },
                    )
                },
            )
            if (resolveShortLink) {
                add(
                    CardItem("resolve_strategy") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MiuixText(
                                text = "解析策略",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            SegmentedControl(
                                items = listOf(LinkResolveStrategy.DIRECT, LinkResolveStrategy.REDISPATCH),
                                selected = if (resolveStrategy == LinkResolveStrategy.REDISPATCH) {
                                    LinkResolveStrategy.REDISPATCH
                                } else {
                                    LinkResolveStrategy.DIRECT
                                },
                                labelRes = { strat ->
                                    when (strat) {
                                        LinkResolveStrategy.DIRECT -> R.string.link_rule_resolve_direct
                                        LinkResolveStrategy.REDISPATCH -> R.string.link_rule_resolve_redispatch
                                        else -> R.string.link_rule_resolve_direct
                                    }
                                },
                                onSelect = { resolveStrategy = it },
                            )
                        }
                    },
                )
            }
            add(
                CardItem("exclude_from_recents") {
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = "无痕打开",
                        summary = "打开目标应用时不保留在系统最近任务列表",
                        onClick = { excludeFromRecents = !excludeFromRecents },
                        endActions = {
                            Switch(
                                checked = excludeFromRecents,
                                onCheckedChange = { excludeFromRecents = it },
                            )
                        },
                    )
                },
            )
            add(
                CardItem("is_enabled") {
                    BasicComponent(
                        modifier = Modifier.fillMaxWidth(),
                        title = "启用此规则",
                        onClick = { isEnabled = !isEnabled },
                        endActions = {
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { isEnabled = it },
                            )
                        },
                    )
                },
            )
        }

        groupedCardItems(
            keyPrefix = "edit_rule_advanced",
            items = advancedCardItems,
        )
    }

    // 目标应用选择弹窗
    LinkTargetAppPickerBottomSheet(
        show = showAppPickerSheet,
        selectedPackage = targetPackage,
        allowClearForRedispatch = resolveShortLink && resolveStrategy == LinkResolveStrategy.REDISPATCH,
        onDismissRequest = { showAppPickerSheet = false },
        onSelectApp = { pkg, appLabel ->
            if (pkg != targetPackage) {
                component = ""
            }
            targetPackage = pkg
            targetAppDisplayName = appLabel
            showAppPickerSheet = false
        },
    )

    // 顶栏实时仿真测试弹窗
    LinkRuleLiveTestBottomSheet(
        show = showTestBottomSheet,
        candidateRule = currentCandidateRule,
        onDismissRequest = { showTestBottomSheet = false },
    )

    // 删除确认弹窗
    MiuixConfirmDialog(
        show = showDeleteConfirmDialog && originalRule != null,
        title = "删除规则",
        message = "确定要删除规则「${originalRule?.name}」吗？此操作无法撤销。",
        onDismissRequest = { showDeleteConfirmDialog = false },
        confirmText = "删除",
        dismissText = "取消",
        onConfirm = {
            showDeleteConfirmDialog = false
            originalRule?.let { rule ->
                scope.launch {
                    LinkRuleRepository.updateRules(context) { list ->
                        list.filterNot { it.id == rule.id }
                    }
                    Toast.makeText(context, context.getString(R.string.link_dispatch_rule_deleted, rule.name), Toast.LENGTH_SHORT).show()
                    onBack()
                }
            }
        },
    )
}

/**
 * 规则实时仿真测试弹窗（拿未保存草稿直接仿真推演与拉起验证）。
 */
@Composable
private fun LinkRuleLiveTestBottomSheet(
    show: Boolean,
    candidateRule: LinkDispatchRule,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var testUrl by remember(candidateRule.pattern) {
        val initial = candidateRule.pattern.trim().trimStart('^')
        mutableStateOf(if (initial.startsWith("http")) initial else "https://$initial")
    }
    var traceResult by remember { mutableStateOf<LinkRouteTrace?>(null) }
    var isSimulating by remember { mutableStateOf(false) }

    LaunchedEffect(show, testUrl, candidateRule) {
        if (!show) return@LaunchedEffect
        val trimmed = testUrl.trim()
        if (trimmed.isBlank()) {
            traceResult = null
            return@LaunchedEffect
        }
        isSimulating = true
        traceResult = withContext(Dispatchers.IO) {
            LinkDispatcher.routeCandidateWithTrace(context, trimmed, candidateRule)
        }
        isSimulating = false
    }

    MiuixBottomSheet(
        show = show,
        title = "规则实时仿真测试",
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 输入区域与粘贴按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    MiuixLabeledTextField(
                        value = testUrl,
                        onValueChange = { testUrl = it },
                        label = "输入测试链接",
                        useLabelAsPlaceholder = false,
                        singleLine = true,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                testUrl = clipText.trim()
                            }
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
                val chips = listOf(
                    "模式" to (if (candidateRule.pattern.startsWith("http")) candidateRule.pattern else "https://${candidateRule.pattern.trimStart('^')}"),
                    "哔哩哔哩" to "https://b23.tv/av170001",
                    "知乎" to "https://link.zhihu.com/?target=https%3A%2F%2Fgithub.com",
                    "Twitter" to "https://t.co/abcxyz",
                )
                chips.forEach { (label, url) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                            .clickable { testUrl = url }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        MiuixText(
                            text = label,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            // 仿真结果卡片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                    .padding(14.dp),
            ) {
                if (isSimulating) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        MiuixText(text = "正在仿真推演...", style = MiuixTheme.textStyles.footnote1)
                    }
                } else {
                    val trace = traceResult
                    if (trace == null) {
                        MiuixText(
                            text = "请输入测试链接查看推演结果",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 1. 命中状态徽章
                            when (val res = trace.finalResult) {
                                is LinkDispatchResult.Routed -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                        ) {
                                            MiuixText(
                                                text = "命中: ${res.rule.name}",
                                                style = MiuixTheme.textStyles.footnote2,
                                                fontWeight = FontWeight.Bold,
                                                color = MiuixTheme.colorScheme.primary,
                                            )
                                        }
                                        if (res.rule.excludeFromRecents) {
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.12f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                            ) {
                                                MiuixText(
                                                    text = "无痕打开",
                                                    style = MiuixTheme.textStyles.footnote2,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                )
                                            }
                                        }
                                    }

                                    // 目标应用
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp),
                                    ) {
                                        LinkAppIcon(packageName = res.rule.targetPackage, size = 32.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            val displayName = linkAppNameCache[res.rule.targetPackage].orEmpty().ifBlank { res.rule.targetPackage }
                                            MiuixText(
                                                text = displayName,
                                                style = MiuixTheme.textStyles.body2,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            MiuixText(
                                                text = res.rule.targetPackage,
                                                style = MiuixTheme.textStyles.footnote2,
                                                fontFamily = FontFamily.Monospace,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            )
                                        }
                                    }

                                    // 最终跳转地址
                                    Column(modifier = Modifier.padding(top = 4.dp)) {
                                        MiuixText(
                                            text = "最终跳转链接",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MiuixTheme.colorScheme.surface)
                                                .padding(8.dp),
                                        ) {
                                            MiuixText(
                                                text = res.url,
                                                style = MiuixTheme.textStyles.footnote1,
                                                fontFamily = FontFamily.Monospace,
                                                color = MiuixTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                                is LinkDispatchResult.Exempted -> {
                                    MiuixText(
                                        text = "域名在豁免名单中 (${res.domain})，将按系统默认方式打开",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                is LinkDispatchResult.SystemDefault -> {
                                    MiuixText(
                                        text = "短链重分发未匹配到后续规则，将按系统默认方式打开",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                LinkDispatchResult.NoRule -> {
                                    MiuixText(
                                        text = "未匹配到当前规则（无规则接管）",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                LinkDispatchResult.Failed -> {
                                    MiuixText(
                                        text = "分发推演失败（重定向循环或改写结果为空）",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.error,
                                    )
                                }
                            }

                            // 重定向步骤 Steps
                            if (trace.steps.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    MiuixText(
                                        text = "短链追踪链路 (${trace.steps.size} 跳)",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                    trace.steps.forEachIndexed { i, step ->
                                        MiuixText(
                                            text = "↳ [${i + 1}] ${step.ruleName}: ${step.fromUrl} ➔ ${step.resolvedUrl ?: "无法解析"}",
                                            style = MiuixTheme.textStyles.footnote2,
                                            fontFamily = FontFamily.Monospace,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 执行真实跳转测试按钮
            val routedResult = traceResult?.finalResult as? LinkDispatchResult.Routed
            MiuixTextButton(
                text = "执行最终跳转测试",
                colors = ButtonDefaults.textButtonColorsPrimary(),
                enabled = routedResult != null,
                onClick = {
                    if (routedResult != null) {
                        scope.launch {
                            LinkLauncher.launch(
                                context = context,
                                url = routedResult.url,
                                targetPackage = routedResult.rule.targetPackage,
                                excludeFromRecents = routedResult.rule.excludeFromRecents,
                                component = routedResult.rule.component,
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
