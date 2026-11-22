package com.slideindex.app.ui.link

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.slideindex.app.link.LinkRuleIconStorage
import com.slideindex.app.ui.miuix.MiuixGlassDropdownMenu
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.util.ExportedActivityInfo
import com.slideindex.app.util.PackageActivityResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ImageOutline
import top.yukonga.miuix.kmp.icon.glass.Ok
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
    val templateState = rememberTextFieldState(originalRule?.template.orEmpty())
    LaunchedEffect(originalRule) {
        val initialTemplate = originalRule?.template.orEmpty()
        if (templateState.text.toString() != initialTemplate) {
            templateState.edit {
                replace(0, length, initialTemplate)
            }
        }
    }
    val template = templateState.text.toString()
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
    var showActivityPickerSheet by remember { mutableStateOf(false) }
    var showTestBottomSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var iconType by remember(originalRule) { mutableStateOf(originalRule?.iconType ?: LinkDispatchRule.ICON_TYPE_DEFAULT) }
    var iconValue by remember(originalRule) { mutableStateOf(originalRule?.iconValue.orEmpty()) }
    var showAppIconPickerSheet by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val savedPath = LinkRuleIconStorage.saveIconFromUri(context, uri)
            if (savedPath != null) {
                iconType = LinkDispatchRule.ICON_TYPE_CUSTOM
                iconValue = savedPath
            }
        }
    }

    val iconMenuEntry = remember(iconType, iconValue) {
        val isCustomized = iconType != LinkDispatchRule.ICON_TYPE_DEFAULT || iconValue.isNotBlank()
        DropdownEntry(
            items = buildList {
                add(
                    DropdownItem(
                        text = "选择应用图标",
                        onClick = { showAppIconPickerSheet = true },
                    )
                )
                add(
                    DropdownItem(
                        text = "从相册选择图片",
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    )
                )
                if (isCustomized) {
                    add(
                        DropdownItem(
                            text = "恢复默认",
                            onClick = {
                                iconType = LinkDispatchRule.ICON_TYPE_DEFAULT
                                iconValue = ""
                            },
                        )
                    )
                }
            }
        )
    }

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
        resolveStrategy, isEnabled, iconType, iconValue,
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
            iconType = iconType,
            iconValue = iconValue,
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
            iconType = iconType,
            iconValue = iconValue,
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
            GlassIconButton(
                onClick = { showTestBottomSheet = true },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_science_lab),
                    contentDescription = "测试规则",
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
            GlassIconButton(
                onClick = { handleSave() },
            ) {
                Icon(
                    imageVector = MiuixIcons.Glass.Normal.Ok,
                    contentDescription = "保存规则",
                    tint = MiuixTheme.colorScheme.primary,
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MiuixGlassDropdownMenu(entry = iconMenuEntry) {
                            LinkRuleIcon(
                                iconType = iconType,
                                iconValue = iconValue,
                                targetPackage = targetPackage,
                                size = 56.dp,
                                shape = RoundedCornerShape(14.dp),
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            MiuixLabeledTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = "规则名称",
                                useLabelAsPlaceholder = false,
                                singleLine = true,
                            )
                        }
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
                            singleLine = false,
                            minLines = 1,
                            maxLines = 8,
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        MiuixLabeledTextField(
                            value = targetPackage,
                            onValueChange = { targetPackage = it },
                            label = "目标应用包名",
                            useLabelAsPlaceholder = false,
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { showAppPickerSheet = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Adjust,
                                        contentDescription = "选择应用",
                                        tint = MiuixTheme.colorScheme.primary,
                                    )
                                }
                            },
                        )
                        MiuixText(
                            text = if (targetPackage.isBlank() && resolveShortLink && resolveStrategy == LinkResolveStrategy.REDISPATCH) {
                                "未指定目标应用：短链解析后将重新匹配后续规则库"
                            } else {
                                "留空为短链重分发或系统默认路由；点击右侧靶心图标可选取应用"
                            },
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                },
            )
            add(
                CardItem("target_component") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        MiuixLabeledTextField(
                            value = component,
                            onValueChange = { component = it },
                            label = "目标活动 (Activity 类名，可选)",
                            useLabelAsPlaceholder = false,
                            singleLine = false,
                            minLines = 1,
                            maxLines = 4,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (targetPackage.isNotBlank()) {
                                             showActivityPickerSheet = true
                                        } else {
                                             showAppPickerSheet = true
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Adjust,
                                        contentDescription = "选择活动",
                                        tint = MiuixTheme.colorScheme.primary,
                                    )
                                }
                            },
                        )
                        MiuixText(
                            text = "留空则由系统默认路由；点击右侧靶心图标可从活动列表中选取特定 Activity",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                },
            )
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
                            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // 1. 正则提取输入框
                        MiuixLabeledTextField(
                            value = extractPattern,
                            onValueChange = { extractPattern = it },
                            label = "正则提取 (留空提取整个)",
                            useLabelAsPlaceholder = false,
                            singleLine = false,
                            minLines = 1,
                            maxLines = 6,
                        )

                        // 2. 重构链接输入框
                        MiuixLabeledTextField(
                            state = templateState,
                            label = "重构链接 (搭配变量使用)",
                            useLabelAsPlaceholder = false,
                            singleLine = false,
                            minLines = 1,
                            maxLines = 8,
                        )

                        // 3. 变换后缀（LinkGo 原生规范）
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            MiuixText(
                                text = "变换选项 (光标处原位追加)",
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
                                val transforms = listOf(
                                    "_url_enc" to "URL编码",
                                    "_url_dec" to "URL解码",
                                    "_b64_enc" to "B64编码",
                                    "_b64_dec" to "B64解码",
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
                                                templateState.edit {
                                                    val sel = selection
                                                    replace(sel.min, sel.max, tag)
                                                    selection = TextRange(sel.min + tag.length)
                                                }
                                            }
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
                                                text = label,
                                                style = MiuixTheme.textStyles.footnote2,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4. 常用变量说明与点击快速插入
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            MiuixText(
                                text = "常用变量 (点击插入并定位在括号内)",
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
                                            .clickable {
                                                templateState.edit {
                                                    val sel = selection
                                                    replace(sel.min, sel.max, tag)
                                                    val cursor = if (tag.startsWith("{") && tag.endsWith("}")) {
                                                        sel.min + tag.length - 1
                                                    } else {
                                                        sel.min + tag.length
                                                    }
                                                    selection = TextRange(cursor)
                                                }
                                            }
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

                        // 5. 变量与变换详细说明面板
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.5f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            MiuixText(
                                text = "说明：点击常用变量将在光标处插入并将光标留在花括号内，此时点击变换选项可直接追加后缀；支持多级链式管道：_url_enc (编码)、_url_dec (解码)、_b64_enc (Base64编)、_b64_dec (Base64解)。未指定提取正则时 {1} 自动匹配主正则捕获组。",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                lineHeight = 16.sp,
                            )
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
                        title = "不显示在最近任务",
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

    // 规则头像：应用图标选择弹窗
    LinkTargetAppPickerBottomSheet(
        show = showAppIconPickerSheet,
        selectedPackage = if (iconType == LinkDispatchRule.ICON_TYPE_APP) iconValue else "",
        title = "选择应用图标",
        onDismissRequest = { showAppIconPickerSheet = false },
        onSelectApp = { pkg, _ ->
            iconType = LinkDispatchRule.ICON_TYPE_APP
            iconValue = pkg
            showAppIconPickerSheet = false
        },
    )

    // 目标活动选择弹窗
    LinkTargetActivityPickerBottomSheet(
        show = showActivityPickerSheet,
        packageName = targetPackage,
        selectedClassName = component,
        onDismissRequest = { showActivityPickerSheet = false },
        onSelectActivity = { chosenClass ->
            component = chosenClass
            showActivityPickerSheet = false
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
internal fun LinkRuleLiveTestBottomSheet(
    show: Boolean,
    candidateRule: LinkDispatchRule? = null,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var testUrl by remember(candidateRule?.pattern) {
        val initial = candidateRule?.pattern?.trim()?.trimStart('^') ?: ""
        mutableStateOf(
            if (initial.isBlank()) ""
            else if (initial.startsWith("http")) initial
            else "https://$initial"
        )
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
            if (candidateRule != null) {
                LinkDispatcher.routeCandidateWithTrace(context, trimmed, candidateRule)
            } else {
                LinkDispatcher.routeWithTrace(context, trimmed)
            }
        }
        isSimulating = false
    }

    MiuixBottomSheet(
        show = show,
        title = if (candidateRule != null) "规则实时测试" else "全局仿真推演",
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. 输入待测试的链接（支持多行自动撑大展开）
            MiuixLabeledTextField(
                value = testUrl,
                onValueChange = { testUrl = it },
                label = "输入待测试的链接...",
                useLabelAsPlaceholder = false,
                singleLine = false,
                minLines = 1,
                maxLines = 5,
            )

            // 2. 双操作按钮（清空内容 / 粘贴最新，对标 LinkGo 12dp 大圆角与主次对比）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 清空内容（次级按钮：白底 + 细边框 + 蓝色内容）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { testUrl = "" },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "清空内容",
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        MiuixText(
                            text = "清空内容",
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }

                // 粘贴最新（主级按钮：Primary 实心蓝底 + 白色内容）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.primary)
                        .clickable {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                testUrl = clipText.trim()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "粘贴最新",
                            tint = MiuixTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        MiuixText(
                            text = "粘贴最新",
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            // 3. 仿真结果卡片流
            if (isSimulating) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    MiuixText(text = "正在仿真推演...", style = MiuixTheme.textStyles.footnote1)
                }
            } else {
                val trace = traceResult
                if (trace == null || testUrl.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MiuixText(
                            text = "请输入待测试的链接查看推演结果",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else if (trace.finalResult is LinkDispatchResult.NoRule) {
                    // 完全未命中任何规则
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                            .padding(14.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MiuixTheme.colorScheme.error.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    MiuixText(
                                        text = "未命中",
                                        style = MiuixTheme.textStyles.footnote2,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.error,
                                    )
                                }
                                MiuixText(
                                    text = candidateRule?.name?.ifBlank { "当前规则" } ?: "规则库",
                                    style = MiuixTheme.textStyles.body2,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            MiuixText(
                                text = if (candidateRule != null) {
                                    "当前链接未匹配规则模式「${candidateRule.pattern}」，本条规则不会触发。"
                                } else {
                                    "当前链接未匹配已启用的任何分发规则，将使用系统默认浏览器打开。"
                                },
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                } else {
                    val activeRule = candidateRule ?: (trace.finalResult as? LinkDispatchResult.Routed)?.rule
                    val firstStep = trace.steps.firstOrNull()

                    // 卡片 1：推演过程卡片（若有短链解析或命中规则）
                    if (activeRule != null || firstStep?.resolvedUrl != null || trace.steps.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                                .padding(14.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // 头部
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MiuixTheme.colorScheme.onSurface)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        MiuixText(
                                            text = if (activeRule != null) (if (candidateRule != null) "当前规则" else "命中规则") else "短链解析",
                                            style = MiuixTheme.textStyles.footnote2,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.surface,
                                        )
                                    }
                                    MiuixText(
                                        text = activeRule?.name?.ifBlank { "匹配规则" } ?: "短链重定向",
                                        style = MiuixTheme.textStyles.body2,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    val actionTag = when {
                                        activeRule?.resolveShortLink == true && activeRule.resolveStrategy == LinkResolveStrategy.REDISPATCH -> "解析并重分发"
                                        activeRule?.resolveShortLink == true -> "解析"
                                        !activeRule?.template.isNullOrBlank() -> "改写"
                                        else -> "直达"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MiuixTheme.colorScheme.surface)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        MiuixText(
                                            text = actionTag,
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }

                                // 原始链接
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    MiuixText(text = "原始链接", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    MiuixText(text = testUrl, style = MiuixTheme.textStyles.footnote1, fontFamily = FontFamily.Monospace)
                                }

                                // HTTP 解析结果（若有）
                                if (firstStep?.resolvedUrl != null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MiuixText(text = "HTTP 解析结果", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                        ) {
                                            MiuixText(
                                                text = firstStep.resolvedUrl,
                                                style = MiuixTheme.textStyles.footnote1,
                                                fontFamily = FontFamily.Monospace,
                                                color = MiuixTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }

                                // 模板改写指示（若有）
                                if (!activeRule?.template.isNullOrBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            MiuixText(
                                                text = "● ${activeRule.template}",
                                                style = MiuixTheme.textStyles.footnote2,
                                                color = MiuixTheme.colorScheme.primary,
                                                fontFamily = FontFamily.Monospace,
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(MiuixTheme.colorScheme.surface),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                        val candidateOutputUrl = when (val res = trace.finalResult) {
                                            is LinkDispatchResult.Routed -> if (activeRule.resolveStrategy == LinkResolveStrategy.REDISPATCH) {
                                                res.inputUrl
                                            } else {
                                                res.url
                                            }
                                            is LinkDispatchResult.SystemDefault -> if (activeRule.resolveStrategy == LinkResolveStrategy.REDISPATCH) {
                                                res.inputUrl
                                            } else {
                                                res.url
                                            }
                                            else -> firstStep?.resolvedUrl ?: testUrl
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MiuixTheme.colorScheme.surface)
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                        ) {
                                            MiuixText(
                                                text = candidateOutputUrl,
                                                style = MiuixTheme.textStyles.footnote1,
                                                fontFamily = FontFamily.Monospace,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 卡片 2：最终匹配 / 落地结果（独立卡片，无论命中 App 还是默认浏览器均展示）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                            .padding(14.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            when (val res = trace.finalResult) {
                                is LinkDispatchResult.Routed -> {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MiuixTheme.colorScheme.onSurface)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            MiuixText(
                                                text = "最终匹配",
                                                style = MiuixTheme.textStyles.footnote2,
                                                fontWeight = FontWeight.Bold,
                                                color = MiuixTheme.colorScheme.surface,
                                            )
                                        }
                                        val appName = linkAppNameCache[res.rule.targetPackage].orEmpty().ifBlank { res.rule.targetPackage }
                                        MiuixText(text = appName, style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Bold)
                                    }

                                    // 输入链接
                                    val isUrlChanged = res.inputUrl.isNotBlank() && res.inputUrl != testUrl
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        MiuixText(
                                            text = if (isUrlChanged) "输入链接（短链解析后）" else "输入链接",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                        MiuixText(
                                            text = if (res.inputUrl.isNotBlank()) res.inputUrl else testUrl,
                                            style = MiuixTheme.textStyles.footnote1,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }

                                    // 目标应用卡片行
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp),
                                    ) {
                                        LinkAppIcon(packageName = res.rule.targetPackage, size = 38.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                val displayName = linkAppNameCache[res.rule.targetPackage].orEmpty().ifBlank { res.rule.targetPackage }
                                                MiuixText(
                                                    text = displayName,
                                                    style = MiuixTheme.textStyles.body1,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                if (res.rule.excludeFromRecents) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                            .padding(horizontal = 5.dp, vertical = 1.dp),
                                                    ) {
                                                        MiuixText(
                                                            text = "不显示在最近任务",
                                                            style = MiuixTheme.textStyles.footnote2,
                                                            color = MiuixTheme.colorScheme.primary,
                                                        )
                                                    }
                                                }
                                            }
                                            MiuixText(
                                                text = res.rule.targetPackage,
                                                style = MiuixTheme.textStyles.footnote2,
                                                fontFamily = FontFamily.Monospace,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            )
                                        }
                                    }

                                    // 最终跳转地址
                                    Column(modifier = Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                is LinkDispatchResult.SystemDefault -> {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MiuixTheme.colorScheme.onSurface)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            MiuixText(
                                                text = "最终匹配",
                                                style = MiuixTheme.textStyles.footnote2,
                                                fontWeight = FontWeight.Bold,
                                                color = MiuixTheme.colorScheme.surface,
                                            )
                                        }
                                        MiuixText(text = "默认浏览器", style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Bold)
                                    }

                                    // 输入链接
                                    val isUrlChanged = res.inputUrl.isNotBlank() && res.inputUrl != testUrl
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        MiuixText(
                                            text = if (isUrlChanged) "输入链接（短链解析后）" else "输入链接",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                        MiuixText(
                                            text = if (res.inputUrl.isNotBlank()) res.inputUrl else testUrl,
                                            style = MiuixTheme.textStyles.footnote1,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }

                                    // 浅蓝卡片（1:1 还原 LinkGo 提示框）
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(androidx.compose.ui.graphics.Color(0xFFEFF4FE))
                                            .padding(12.dp),
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(
                                                    imageVector = Icons.Default.HelpOutline,
                                                    contentDescription = null,
                                                    tint = androidx.compose.ui.graphics.Color(0xFF2E63FF),
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                MiuixText(
                                                    text = "后续路径未命中规则库",
                                                    style = MiuixTheme.textStyles.footnote1,
                                                    fontWeight = FontWeight.Bold,
                                                    color = androidx.compose.ui.graphics.Color(0xFF2E63FF),
                                                )
                                            }
                                            MiuixText(
                                                text = "分发链接在规则库中找不到后续接管者，将进入系统默认打开流程。",
                                                style = MiuixTheme.textStyles.footnote2,
                                                color = androidx.compose.ui.graphics.Color(0xFF334A70),
                                                lineHeight = 16.sp,
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
                                LinkDispatchResult.Failed -> {
                                    MiuixText(
                                        text = "分发推演失败（重定向循环或改写结果为空）",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.error,
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }

            // 4. 执行最终跳转测试大按钮（对标 LinkGo 底部纯蓝圆角矩形 + 外部跳转图标）
            val canLaunch = testUrl.isNotBlank() && traceResult != null
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (canLaunch) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.primary.copy(alpha = 0.4f))
                    .clickable(enabled = canLaunch) {
                        val finalResult = traceResult?.finalResult
                        if (finalResult is LinkDispatchResult.Routed) {
                            scope.launch {
                                LinkLauncher.launch(
                                    context = context,
                                    url = finalResult.url,
                                    targetPackage = finalResult.rule.targetPackage,
                                    excludeFromRecents = finalResult.rule.excludeFromRecents,
                                    component = finalResult.rule.component,
                                )
                            }
                        } else {
                            val finalUrl = when (finalResult) {
                                is LinkDispatchResult.SystemDefault -> finalResult.url
                                else -> traceResult?.steps?.firstOrNull()?.resolvedUrl ?: testUrl
                            }
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }.onFailure {
                                Toast.makeText(context, "未找到可处理此链接的应用", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = "执行最终跳转测试",
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    MiuixText(
                        text = "执行最终跳转测试",
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

/**
 * 目标 Activity (活动) 选择弹窗（带「未导出 / 需提权」警示胶囊）。
 */
@Composable
private fun LinkTargetActivityPickerBottomSheet(
    show: Boolean,
    packageName: String,
    selectedClassName: String,
    onDismissRequest: () -> Unit,
    onSelectActivity: (String) -> Unit,
) {
    val context = LocalContext.current
    var activities by remember(packageName) { mutableStateOf<List<ExportedActivityInfo>>(emptyList()) }
    var loading by remember(packageName) { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(packageName, show) {
        if (show && packageName.isNotBlank()) {
            loading = true
            activities = withContext(Dispatchers.IO) {
                PackageActivityResolver.listActivities(context, packageName)
            }
            loading = false
        }
    }

    val filtered = remember(activities, query) {
        if (query.isBlank()) activities
        else PackageActivityResolver.searchActivities(activities, query)
    }

    MiuixBottomSheet(
        show = show,
        title = "选择目标活动",
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 搜索框
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                MiuixLabeledTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "搜索活动名称或完整类名",
                    useLabelAsPlaceholder = false,
                    singleLine = true,
                )
            }

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            } else if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixText(
                        text = if (activities.isEmpty()) "该应用未声明任何 Activity" else "没有找到匹配的 Activity",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f),
                ) {
                    items(filtered, key = { it.className }) { act ->
                        val isSelected = act.className == selectedClassName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    else MiuixTheme.colorScheme.surface
                                )
                                .clickable {
                                    onSelectActivity(act.className)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    MiuixText(
                                        text = act.label.ifBlank { act.className.substringAfterLast('.') },
                                        style = MiuixTheme.textStyles.body1,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    // 未导出标签（需 Shizuku/Root 提权）
                                    if (!act.exported) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MiuixTheme.colorScheme.error.copy(alpha = 0.12f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        ) {
                                            MiuixText(
                                                text = "未导出 / 需提权",
                                                style = MiuixTheme.textStyles.footnote2,
                                                color = MiuixTheme.colorScheme.error,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    } else if (act.launcherEntry) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        ) {
                                            MiuixText(
                                                text = "主入口",
                                                style = MiuixTheme.textStyles.footnote2,
                                                color = MiuixTheme.colorScheme.primary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                }
                                MiuixText(
                                    text = act.className,
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
