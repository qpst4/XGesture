package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.otp.OtpMatchRule
import com.slideindex.app.otp.VerificationCodeExtractor
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.MiuixSettingsFab
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsHintText
import com.slideindex.app.ui.settings.components.SettingsLabeledTextFieldRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 验证码提取规则：测试入口 + 统一规则表 + 高级兜底词表。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtpRulesScreen(
    settings: AppSettings,
    officialRules: List<OtpMatchRule>,
    onBack: () -> Unit,
    onRefreshOfficialRules: () -> Unit,
    onOfficialRuleEnabledChange: (String, Boolean) -> Unit,
    onUserRulesChange: (List<OtpMatchRule>) -> Unit,
    onKeywordsRegexChange: (String) -> Unit,
    onExportRules: () -> Unit,
    onImportRules: () -> Unit,
) {
    var showTestDialog by rememberSaveable { mutableStateOf(false) }
    var showRulesMenu by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<OtpMatchRule?>(null) }
    var keywordsExpanded by rememberSaveable { mutableStateOf(false) }
    val keywordsText = remember(settings.otpKeywordsRegex) { mutableStateOf(settings.otpKeywordsRegex) }

    val advancedItems = buildList {
        add(
            settingsCardScopeItem("fallback-keywords-entry") {
                SettingLinkRow(
                    title = stringResource(R.string.otp_keywords_fallback_entry_title),
                    subtitle = stringResource(R.string.otp_keywords_fallback_entry_desc),
                    onClick = { keywordsExpanded = !keywordsExpanded },
                )
            },
        )
        if (keywordsExpanded) {
            add(
                settingsCardScopeItem("fallback-keywords-field") {
                    SettingsLabeledTextFieldRow(
                        key = "fallback-keywords-input",
                        label = stringResource(R.string.otp_keywords_regex_label),
                        value = keywordsText.value,
                        onValueChange = { keywordsText.value = it },
                        singleLine = false,
                        minLines = 2,
                        maxLines = 6,
                    )
                },
            )
            add(
                settingsCardScopeItem("fallback-keywords-hint") {
                    SettingsHintText(stringResource(R.string.otp_keywords_regex_hint))
                },
            )
            add(
                settingsCardScopeItem("fallback-keywords-save") {
                    SettingLinkRow(
                        title = stringResource(R.string.otp_keywords_save),
                        subtitle = stringResource(R.string.otp_keywords_save_desc),
                        onClick = { onKeywordsRegexChange(keywordsText.value) },
                    )
                },
            )
            add(
                settingsCardScopeItem("fallback-keywords-reset") {
                    SettingLinkRow(
                        title = stringResource(R.string.otp_keywords_reset),
                        subtitle = stringResource(R.string.otp_keywords_reset_desc),
                        onClick = {
                            val defaultRegex = VerificationCodeExtractor.DEFAULT_KEYWORDS_REGEX
                            keywordsText.value = defaultRegex
                            onKeywordsRegexChange(defaultRegex)
                        },
                    )
                },
            )
        }
    }
    val advancedTitle = stringResource(R.string.otp_advanced_section)

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.otp_match_rules_entry_title),
        subtitle = stringResource(R.string.otp_match_rules_entry_desc),
        onBack = onBack,
        floatingActionButton = {
            MiuixSettingsFab(
                onClick = {
                    editingRule = null
                    showEditor = true
                },
                icon = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.otp_rules_add),
            )
        },
    ) {
        otpRulesListItems(
            embeddedInHub = true,
            officialRules = officialRules,
            userRules = settings.otpUserMatchRules,
            disabledOfficialRuleIds = settings.otpDisabledOfficialRuleIds,
            onOfficialRuleEnabledChange = onOfficialRuleEnabledChange,
            onUserRulesChange = onUserRulesChange,
            onShowTestDialog = { showTestDialog = true },
            onEditRule = { rule ->
                editingRule = rule
                showEditor = true
            },
            onCopyOfficialRule = { rule ->
                onUserRulesChange(
                    settings.otpUserMatchRules +
                        rule.copy(id = java.util.UUID.randomUUID().toString(), isOfficial = false),
                )
            },
            onOpenRulesMenu = { showRulesMenu = true },
        )
        settingsLazySmallTitle(key = "otp_advanced_section", title = advancedTitle)
        groupedCardItems(keyPrefix = "otp-advanced", items = advancedItems)
    }

    if (showTestDialog) {
        OtpTestDialogHost(
            settings = settings,
            officialRules = officialRules,
            keywordsRegex = keywordsText.value,
            onDismiss = { showTestDialog = false },
        )
    }
    if (showEditor) {
        OtpRuleEditorDialog(
            initialRule = editingRule,
            keywordsRegex = keywordsText.value,
            onDismiss = {
                showEditor = false
                editingRule = null
            },
            onSave = { saved ->
                val updated = if (editingRule != null) {
                    settings.otpUserMatchRules.map { if (it.id == saved.id) saved else it }
                } else {
                    settings.otpUserMatchRules + saved
                }
                onUserRulesChange(updated)
                showEditor = false
                editingRule = null
            },
        )
    }
    MiuixBottomSheet(
        show = showRulesMenu,
        title = stringResource(R.string.otp_rules_more),
        onDismissRequest = { showRulesMenu = false },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            RulesMenuOption(
                label = stringResource(R.string.otp_rules_export),
                onClick = {
                    showRulesMenu = false
                    onExportRules()
                },
            )
            RulesMenuOption(
                label = stringResource(R.string.otp_rules_import),
                onClick = {
                    showRulesMenu = false
                    onImportRules()
                },
            )
            RulesMenuOption(
                label = stringResource(R.string.otp_rules_refresh),
                onClick = {
                    showRulesMenu = false
                    onRefreshOfficialRules()
                },
            )
        }
    }
}

/** 规则页「⋮」菜单项。 */
@Composable
internal fun RulesMenuOption(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MiuixTheme.textStyles.body1,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 0.dp, vertical = 16.dp),
    )
}
