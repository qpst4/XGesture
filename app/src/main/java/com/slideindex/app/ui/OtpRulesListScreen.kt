package com.slideindex.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.otp.OtpKeywords
import com.slideindex.app.otp.OtpMatchRule
import com.slideindex.app.otp.OtpRuleInference
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.MiuixGlassDropdownMenu
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.MiuixIcons

/**
 * 验证码规则页：**一张列表 + 一个测试按钮**。
 *
 * 「我的规则」与「内置规则」合并成同一张表，行内用小标签区分来源；
 * 新增规则走"贴一条真实短信 → 自动生成"（见 [OtpRuleEditorDialog]）。
 */
internal fun LazyListScope.otpRulesListItems(
    embeddedInHub: Boolean,
    officialRules: List<OtpMatchRule>,
    userRules: List<OtpMatchRule>,
    disabledOfficialRuleIds: Set<String>,
    onOfficialRuleEnabledChange: (String, Boolean) -> Unit,
    onUserRulesChange: (List<OtpMatchRule>) -> Unit,
    onShowTestDialog: (() -> Unit)?,
    onEditRule: (OtpMatchRule) -> Unit,
    onCopyOfficialRule: ((OtpMatchRule) -> Unit)? = null,
    /** 「⋮」菜单项；空列表表示不显示这个按钮。菜单走玻璃下拉（本仓统一口径），不再开底部弹窗。 */
    rulesMenuItems: List<DropdownItem> = emptyList(),
) {
    if (embeddedInHub) {
        item(key = "rules_toolbar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 12dp + TextButton 自带的水平内边距 ≈ 28dp，与下方区块标题左对齐。
                    .padding(start = 12.dp, end = 16.dp, top = 4.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = stringResource(R.string.otp_test_title),
                    onClick = { onShowTestDialog?.invoke() },
                )
                Spacer(Modifier.weight(1f))
                if (rulesMenuItems.isNotEmpty()) {
                    // 玻璃下拉菜单：锚点自己接管点击，IconButton 只负责开。
                    var menuExpanded by remember { mutableStateOf(false) }
                    var menuAnchor by remember { mutableStateOf(Rect.Zero) }
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.onGloballyPositioned {
                            menuAnchor = it.boundsInWindow()
                        },
                    ) {
                        Icon(
                            MiuixIcons.Medium.More,
                            contentDescription = stringResource(R.string.otp_rules_more),
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                    MiuixGlassDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        anchorBounds = menuAnchor,
                        items = rulesMenuItems,
                    )
                }
            }
        }
    }

    item(key = "rules_section_title") {
        RulesSectionHeader(
            title = stringResource(R.string.otp_rules_list_section),
            trailing = stringResource(
                R.string.otp_rules_count_summary,
                userRules.size,
                officialRules.size,
            ),
        )
    }

    val items = buildList {
        userRules.forEach { rule ->
            add(
                CardItem(key = rule.id) {
                    OtpRuleRowContent(
                        rule = rule,
                        enabled = rule.enabled,
                        sourceLabel = stringResource(R.string.otp_rules_source_mine),
                        onEnabledChange = { enabled ->
                            onUserRulesChange(
                                userRules.map {
                                    if (it.id == rule.id) it.copy(enabled = enabled) else it
                                },
                            )
                        },
                        onEdit = { onEditRule(rule) },
                        onDelete = { onUserRulesChange(userRules.filterNot { it.id == rule.id }) },
                    )
                },
            )
        }
        officialRules.forEach { rule ->
            add(
                CardItem(key = rule.id) {
                    OtpRuleRowContent(
                        rule = rule,
                        enabled = rule.id !in disabledOfficialRuleIds,
                        sourceLabel = stringResource(R.string.otp_rules_source_builtin),
                        onEnabledChange = { onOfficialRuleEnabledChange(rule.id, it) },
                        onEdit = null,
                        onDelete = null,
                        onCopyToUser = onCopyOfficialRule?.let { copy -> { copy(rule) } },
                    )
                },
            )
        }
    }
    if (items.isEmpty()) {
        item(key = "rules_empty") {
            Text(
                text = stringResource(R.string.otp_rules_user_empty),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
        }
    } else {
        groupedCardItems(keyPrefix = "otp-rules", items = items)
    }
}

/** 区块标题：统一层级，右侧可带计数。 */
@Composable
private fun RulesSectionHeader(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title4,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}

/** 单条规则：第一行 `名称 · 触发词` + 来源标签，第二行正则（单行截断）。 */
@Composable
private fun OtpRuleRowContent(
    rule: OtpMatchRule,
    enabled: Boolean,
    sourceLabel: String,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onCopyToUser: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onEdit != null) Modifier.clickable(onClick = onEdit) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${rule.name} · ${rule.keyword}",
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sourceLabel,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(end = 6.dp),
                )
                if (onCopyToUser != null) {
                    IconButton(onClick = onCopyToUser) {
                        Icon(
                            MiuixIcons.Medium.Copy,
                            contentDescription = stringResource(R.string.otp_rules_copy_to_user),
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            MiuixIcons.Medium.Delete,
                            contentDescription = stringResource(R.string.otp_rules_delete),
                            tint = MiuixTheme.colorScheme.error,
                        )
                    }
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
        }
        Text(
            text = rule.regex,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 规则编辑弹窗。
 *
 * 新增时**先贴一条真实短信**，[OtpRuleInference] 自动推断触发词与正则并回填；
 * 推断不出来（或需要手改）时展开"高级"，直接编辑触发词 / 正则 / 限定包名。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@android.annotation.SuppressLint("LocalContextGetResourceValueCall")
internal fun OtpRuleEditorDialog(
    initialRule: OtpMatchRule?,
    onDismiss: () -> Unit,
    onSave: (OtpMatchRule) -> Unit,
    keywordsRegex: String = OtpKeywords.DEFAULT_KEYWORDS_REGEX,
) {
    val context = LocalContext.current
    // 同上：资源读取跟随配置变化。
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val resourceContext = androidx.compose.runtime.remember(configuration) {
        context.createConfigurationContext(configuration)
    }
    var sample by remember(initialRule) { mutableStateOf("") }
    var name by remember(initialRule) { mutableStateOf(initialRule?.name.orEmpty()) }
    var keyword by remember(initialRule) { mutableStateOf(initialRule?.keyword.orEmpty()) }
    var regex by remember(initialRule) { mutableStateOf(initialRule?.regex.orEmpty()) }
    var packageName by remember(initialRule) { mutableStateOf(initialRule?.packageName.orEmpty()) }
    var advanced by remember(initialRule) { mutableStateOf(initialRule != null) }

    val inference = remember(sample, keywordsRegex) {
        if (initialRule == null && sample.isNotBlank()) {
            OtpRuleInference.infer(sample, keywordsRegex)
        } else {
            null
        }
    }
    LaunchedEffect(inference) {
        inference?.let { result ->
            keyword = result.keyword
            regex = result.regex
            if (name.isBlank()) name = resourceContext.getString(R.string.otp_rules_default_name)
        }
    }

    val invalidMessage = stringResource(R.string.otp_rules_invalid)
    MiuixFormDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(
            if (initialRule != null) R.string.otp_rules_edit else R.string.otp_rules_add,
        ),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            val trimmedKeyword = keyword.trim()
            val trimmedRegex = regex.trim()
            if (name.trim().isEmpty() || trimmedKeyword.isEmpty() || trimmedRegex.isEmpty()) {
                Toast.makeText(context, invalidMessage, Toast.LENGTH_SHORT).show()
                return@MiuixFormDialog
            }
            if (runCatching { Regex(trimmedRegex) }.isFailure) {
                Toast.makeText(context, invalidMessage, Toast.LENGTH_SHORT).show()
                return@MiuixFormDialog
            }
            onSave(
                OtpMatchRule(
                    id = initialRule?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name.trim(),
                    keyword = trimmedKeyword,
                    regex = trimmedRegex,
                    packageName = packageName.trim().ifEmpty { null },
                    isOfficial = false,
                    enabled = initialRule?.enabled ?: true,
                ),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (initialRule == null) {
                MiuixLabeledTextField(
                    value = sample,
                    onValueChange = { sample = it },
                    label = stringResource(R.string.otp_rules_sample_label),
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                )
                when {
                    sample.isBlank() -> Unit
                    inference != null -> Text(
                        text = stringResource(R.string.otp_test_result_success, inference.code),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    else -> Text(
                        text = stringResource(R.string.otp_rules_sample_unrecognized),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { advanced = !advanced },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
                Text(
                    text = stringResource(R.string.otp_rules_advanced),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
            }
            if (advanced) {
                MiuixLabeledTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.otp_rules_name_label),
                )
                MiuixLabeledTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = stringResource(R.string.otp_rules_keyword_field_label),
                )
                MiuixLabeledTextField(
                    value = regex,
                    onValueChange = { regex = it },
                    label = stringResource(R.string.otp_rules_regex_label),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                )
                MiuixLabeledTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = stringResource(R.string.otp_rules_package_label),
                )
            }
        }
    }
}
