package com.slideindex.app.ui.notificationrule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.notification.AppMatchMode
import com.slideindex.app.notification.AppTarget
import com.slideindex.app.notification.NotificationAdvancedFilterError
import com.slideindex.app.notification.NotificationAdvancedFilterJsonParser
import com.slideindex.app.notification.NotificationAdvancedFilterValidation
import com.slideindex.app.notification.NotificationAdvancedFilterViolation
import com.slideindex.app.notification.NotificationRuleChargeMask
import com.slideindex.app.notification.ScreenMode
import com.slideindex.app.notification.TextMatchMode
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixDropdownPreference
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NotificationRuleConditionEditor(
    name: String,
    onNameChange: (String) -> Unit,
    channelId: String,
    onChannelIdChange: (String) -> Unit,
    appMode: AppMatchMode,
    onAppModeChange: (AppMatchMode) -> Unit,
    appTargets: List<AppTarget>,
    onPickApps: () -> Unit,
    textMode: TextMatchMode,
    onTextModeChange: (TextMatchMode) -> Unit,
    keywordsText: String,
    onKeywordsTextChange: (String) -> Unit,
    keywordsExcludeText: String,
    onKeywordsExcludeTextChange: (String) -> Unit,
    regex: String,
    onRegexChange: (String) -> Unit,
    advancedJson: String,
    onAdvancedJsonChange: (String) -> Unit,
    timeStart: String,
    onTimeStartChange: (String) -> Unit,
    timeEnd: String,
    onTimeEndChange: (String) -> Unit,
    weekDays: Set<Int>,
    onWeekDaysChange: (Set<Int>) -> Unit,
    screenMode: ScreenMode,
    onScreenModeChange: (ScreenMode) -> Unit,
    chargeBattery: Boolean,
    onChargeBatteryChange: (Boolean) -> Unit,
    chargeWired: Boolean,
    onChargeWiredChange: (Boolean) -> Unit,
    chargeWireless: Boolean,
    onChargeWirelessChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiuixLabeledTextField(
                value = name,
                onValueChange = onNameChange,
                label = stringResource(R.string.notification_rule_name),
            )
            MiuixLabeledTextField(
                value = channelId,
                onValueChange = onChannelIdChange,
                label = stringResource(R.string.notification_rule_channel),
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            // Order and labels come from NotificationRuleModeLabels, which pairs each enum
            // constant with its own label. The previous positional mapping was off by one
            // (AppMatchMode declares INCLUDE, EXCLUDE, ALL while the labels were listed as
            // ALL, INCLUDE, EXCLUDE), so every selection applied the wrong rule:
            // "所有应用" stored INCLUDE, "包含" stored EXCLUDE.
            val appModeEntries = NotificationRuleModeLabels.appModes
                .map { (mode, labelRes) -> mode to stringResource(labelRes) }
            MiuixDropdownPreference(
                title = stringResource(R.string.notification_rule_section_apps),
                items = appModeEntries.map { it.second },
                selectedIndex = appModeEntries.indexOfFirst { it.first == appMode }.coerceAtLeast(0),
                onSelectedIndexChange = { index ->
                    appModeEntries.getOrNull(index)?.let { onAppModeChange(it.first) }
                },
            )
            if (appMode != AppMatchMode.ALL) {
                ArrowPreference(
                    title = stringResource(R.string.notification_rule_pick_app),
                    summary = stringResource(
                        R.string.notification_rule_selected_apps_count,
                        appTargets.size,
                    ),
                    onClick = onPickApps,
                )
            }

            val textModeEntries = NotificationRuleModeLabels.textModes
                .map { (mode, labelRes) -> mode to stringResource(labelRes) }
            MiuixDropdownPreference(
                title = stringResource(R.string.notification_rule_section_text),
                items = textModeEntries.map { it.second },
                selectedIndex = textModeEntries.indexOfFirst { it.first == textMode }.coerceAtLeast(0),
                onSelectedIndexChange = { index ->
                    textModeEntries.getOrNull(index)?.let { onTextModeChange(it.first) }
                },
            )

            when (textMode) {
                TextMatchMode.CONTAIN_ANY, TextMatchMode.NOT_CONTAIN_ANY,
                TextMatchMode.CONTAIN_ALL, TextMatchMode.NOT_CONTAIN_ALL,
                -> {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        MiuixLabeledTextField(
                            value = keywordsText,
                            onValueChange = onKeywordsTextChange,
                            label = stringResource(R.string.notification_rule_keywords_hint),
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                        )
                    }
                }
                TextMatchMode.CONTAIN_AND_NOT_CONTAIN -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MiuixLabeledTextField(
                            value = keywordsText,
                            onValueChange = onKeywordsTextChange,
                            label = stringResource(R.string.notification_rule_keywords_hint),
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                        )
                        MiuixLabeledTextField(
                            value = keywordsExcludeText,
                            onValueChange = onKeywordsExcludeTextChange,
                            label = stringResource(R.string.notification_rule_keywords_exclude_hint),
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                        )
                    }
                }
                TextMatchMode.REGEX -> {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        MiuixLabeledTextField(
                            value = regex,
                            onValueChange = onRegexChange,
                            label = stringResource(R.string.notification_rule_regex_hint),
                        )
                    }
                }
                TextMatchMode.ADVANCED -> {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        MiuixLabeledTextField(
                            value = advancedJson,
                            onValueChange = onAdvancedJsonChange,
                            label = stringResource(R.string.notification_rule_advanced_hint),
                            singleLine = false,
                            minLines = 4,
                            maxLines = 8,
                        )
                        Text(
                            text = stringResource(R.string.notification_rule_advanced_schema),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        advancedFilterErrorMessage(advancedJson)?.let { message ->
                            Text(
                                text = message,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                TextMatchMode.ALL -> Unit
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NotificationRuleSectionHeading(
                text = stringResource(R.string.notification_rule_section_time),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiuixLabeledTextField(
                    value = timeStart,
                    onValueChange = onTimeStartChange,
                    label = stringResource(R.string.notification_rule_time_start),
                    modifier = Modifier.weight(1f),
                )
                MiuixLabeledTextField(
                    value = timeEnd,
                    onValueChange = onTimeEndChange,
                    label = stringResource(R.string.notification_rule_time_end),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.notification_rule_time_all_day),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )

            NotificationRuleSectionHeading(
                text = stringResource(R.string.notification_rule_week_days),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..7).forEach { day ->
                    FilterChip(
                        selected = day in weekDays,
                        onClick = {
                            val next = if (day in weekDays) weekDays - day else weekDays + day
                            // 空集在持久化里会被解码回「全部星期」（NotificationFilterCodec 的
                            // parseIntSet 对空数组回退 ALL），所以「一天都不选」表达不出来：
                            // 允许取消最后一颗只会造成「保存成功、重进又全选」的假失效。
                            if (next.isNotEmpty()) onWeekDaysChange(next)
                        },
                        label = { Text(weekDayLabel(day)) },
                    )
                }
            }

            NotificationRuleSectionHeading(
                text = stringResource(R.string.notification_rule_section_device),
            )
            // 屏幕状态用三选一下拉：亮屏 / 熄屏两个勾选框的「两个都勾」与「两个都不勾」都等于不限，
            // 保存后无法区分，会把没勾的项回显成已勾选。
            val screenModeEntries = NotificationRuleModeLabels.screenModes
                .map { (mode, labelRes) -> mode to stringResource(labelRes) }
            MiuixDropdownPreference(
                title = stringResource(R.string.notification_rule_screen_state),
                items = screenModeEntries.map { it.second },
                selectedIndex = screenModeEntries.indexOfFirst { it.first == screenMode }.coerceAtLeast(0),
                onSelectedIndexChange = { index ->
                    screenModeEntries.getOrNull(index)?.let { onScreenModeChange(it.first) }
                },
            )
            NotificationRuleSectionHeading(
                text = stringResource(R.string.notification_rule_charge_state),
            )
            CheckboxPreference(
                title = stringResource(R.string.notification_rule_charge_battery),
                checked = chargeBattery,
                onCheckedChange = onChargeBatteryChange,
            )
            CheckboxPreference(
                title = stringResource(R.string.notification_rule_charge_wired),
                checked = chargeWired,
                onCheckedChange = onChargeWiredChange,
            )
            CheckboxPreference(
                title = stringResource(R.string.notification_rule_charge_wireless),
                checked = chargeWireless,
                onCheckedChange = onChargeWirelessChange,
            )
            Text(
                text = stringResource(R.string.notification_rule_charge_unrestricted_hint),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
}

@Composable
internal fun advancedFilterViolationMessage(violation: NotificationAdvancedFilterViolation): String {
    val detail = violation.detail
    return when (violation.error) {
        NotificationAdvancedFilterError.INVALID_JSON ->
            stringResource(R.string.notification_rule_advanced_error_invalid_json)
        NotificationAdvancedFilterError.EMPTY_NODES ->
            stringResource(R.string.notification_rule_advanced_error_empty_nodes)
        NotificationAdvancedFilterError.INVALID_MATCH_TYPE ->
            stringResource(R.string.notification_rule_advanced_error_invalid_match_type, detail)
        NotificationAdvancedFilterError.UNKNOWN_KEY ->
            stringResource(R.string.notification_rule_advanced_error_unknown_key, detail)
        NotificationAdvancedFilterError.UNSUPPORTED_FIELD ->
            stringResource(R.string.notification_rule_advanced_error_unsupported_field, detail)
        NotificationAdvancedFilterError.EMPTY_REGEX ->
            stringResource(R.string.notification_rule_advanced_error_empty_regex)
        NotificationAdvancedFilterError.INVALID_REGEX ->
            stringResource(R.string.notification_rule_advanced_error_invalid_regex, detail)
    }
}

/**
 * Returns the reason [json] cannot be used as an advanced filter, or null when it is accepted.
 * Shared by the inline hint and the save-time guard so both stay in step.
 */
internal fun advancedFilterViolation(json: String): NotificationAdvancedFilterViolation? {
    val parsed = NotificationAdvancedFilterJsonParser.parse(json)
    return (parsed as? NotificationAdvancedFilterValidation.Invalid)?.violation
}

@Composable
private fun advancedFilterErrorMessage(json: String): String? {
    val violation = advancedFilterViolation(json) ?: return null
    return advancedFilterViolationMessage(violation)
}

@Composable
internal fun weekDayLabel(day: Int): String = when (day) {
    1 -> stringResource(R.string.weekday_short_sunday)
    2 -> stringResource(R.string.weekday_short_monday)
    3 -> stringResource(R.string.weekday_short_tuesday)
    4 -> stringResource(R.string.weekday_short_wednesday)
    5 -> stringResource(R.string.weekday_short_thursday)
    6 -> stringResource(R.string.weekday_short_friday)
    7 -> stringResource(R.string.weekday_short_saturday)
    else -> day.toString()
}

internal fun parseLines(text: String): List<String> =
    text.lines().map { it.trim() }.filter { it.isNotBlank() }

internal fun parseTimeMs(value: String): Int {
    val parts = value.trim().split(":")
    if (parts.size != 2) return 0
    val hour = parts[0].toIntOrNull() ?: return 0
    val minute = parts[1].toIntOrNull() ?: return 0
    return ((hour * 60) + minute) * 60 * 1000
}

internal fun msToTimeString(ms: Int): String {
    if (ms <= 0) return "00:00"
    val totalMinutes = ms / 60_000
    val hour = totalMinutes / 60
    val minute = totalMinutes % 60
    return "%02d:%02d".format(hour, minute)
}

/**
 * 编辑页「充电状态」三个勾选框的状态。
 *
 * 保存 [toMask] 与回显 [fromMask] 必须成对且可逆，否则会出现「只选了一个手机状态，重进编辑页后
 * 充电三项被自动勾上」：历史版本把「三项都没勾」保存成不限制（位值 15，三位全选），而回显又按位
 * 判断，于是三项全被勾上。这里规定不限制只以「三项都未勾选」呈现，且未勾选与全勾都写回不限制，
 * 于是 `fromMask(toMask(x)) == x` 对每个可表示状态都成立。
 */
internal data class ChargeSelection(
    val battery: Boolean,
    val wired: Boolean,
    val wireless: Boolean,
) {
    fun toMask(): Int = NotificationRuleChargeMask.canonical(
        (if (battery) NotificationRuleChargeMask.BATTERY else 0) or
            (if (wired) NotificationRuleChargeMask.WIRED else 0) or
            (if (wireless) NotificationRuleChargeMask.WIRELESS else 0),
    )

    companion object {
        fun fromMask(mask: Int): ChargeSelection =
            if (NotificationRuleChargeMask.isUnrestricted(mask)) {
                ChargeSelection(battery = false, wired = false, wireless = false)
            } else {
                ChargeSelection(
                    battery = (mask and NotificationRuleChargeMask.BATTERY) != 0,
                    wired = (mask and NotificationRuleChargeMask.WIRED) != 0,
                    wireless = (mask and NotificationRuleChargeMask.WIRELESS) != 0,
                )
            }
    }
}
