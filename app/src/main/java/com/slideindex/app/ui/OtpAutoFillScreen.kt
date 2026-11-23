package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import kotlin.math.roundToInt

/** 验证码自动填充：开关、自动回车、输入延迟与间隔、系统注入。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@android.annotation.SuppressLint("LocalContextGetResourceValueCall")
fun OtpAutoFillScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    accessibilityGranted: Boolean,
    onRequestAccessibility: () -> Unit,
    onAutoInputChange: (Boolean) -> Unit,
    onAutoConfirmChange: (Boolean) -> Unit,
    onDelayChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onLsposedSystemInjectChange: (Boolean) -> Unit,
    onLsposedSmsCaptureChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    // 资源读取要跟随配置变化（切语言/改字号/转屏）：用配置感知的派生 Context。
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val resourceContext = androidx.compose.runtime.remember(configuration) {
        context.createConfigurationContext(configuration)
    }
    val enabled = accessibilityGranted && settings.otpAutoInputEnabled
    val items = listOf(
        settingsCardScopeItem("auto-input-enabled") {
            SettingSwitchRow(
                title = stringResource(R.string.otp_auto_input_enabled_title),
                subtitle = stringResource(R.string.otp_auto_input_enabled_desc),
                checked = settings.otpAutoInputEnabled,
                enabled = accessibilityGranted,
                onCheckedChange = { value ->
                    if (!accessibilityGranted) onRequestAccessibility() else onAutoInputChange(value)
                },
            )
        },
        settingsCardScopeItem("auto-confirm") {
            SettingSwitchRow(
                title = stringResource(R.string.otp_auto_confirm_title),
                subtitle = stringResource(R.string.otp_auto_confirm_desc),
                checked = settings.otpAutoConfirmEnabled,
                enabled = enabled,
                onCheckedChange = onAutoConfirmChange,
            )
        },
        settingsCardScopeItem("delay") {
            SettingsSliderRow(
                title = stringResource(R.string.otp_auto_input_delay_title),
                value = settings.otpAutoInputDelayMs.toFloat(),
                valueRange = 0f..3000f,
                steps = 29,
                enabled = enabled,
                label = if (settings.otpAutoInputDelayMs <= 0) {
                    stringResource(R.string.otp_auto_input_delay_zero)
                } else {
                    stringResource(R.string.otp_auto_input_delay_value, settings.otpAutoInputDelayMs)
                },
                formatLabel = { value ->
                    val ms = value.roundToInt()
                    if (ms <= 0) {
                        resourceContext.getString(R.string.otp_auto_input_delay_zero)
                    } else {
                        resourceContext.getString(R.string.otp_auto_input_delay_value, ms)
                    }
                },
                snapValue = { value -> (value / 100f).roundToInt() * 100f },
                onValueChange = { value -> onDelayChange(((value / 100f).roundToInt() * 100).coerceIn(0, 3000)) },
            )
        },
        settingsCardScopeItem("interval") {
            SettingsSliderRow(
                title = stringResource(R.string.otp_auto_input_interval_title),
                value = settings.otpAutoInputIntervalMs.toFloat(),
                valueRange = 0f..500f,
                steps = 24,
                enabled = enabled,
                label = stringResource(R.string.otp_auto_input_interval_value, settings.otpAutoInputIntervalMs),
                formatLabel = { value ->
                    resourceContext.getString(R.string.otp_auto_input_interval_value, value.roundToInt())
                },
                snapValue = { value -> (value / 20f).roundToInt() * 20f },
                onValueChange = { value -> onIntervalChange(((value / 20f).roundToInt() * 20).coerceIn(0, 500)) },
            )
        },
        settingsCardScopeItem("lsposed-inject") {
            SettingSwitchRow(
                title = stringResource(R.string.otp_lsposed_inject_title),
                subtitle = stringResource(R.string.otp_lsposed_inject_desc_short),
                checked = settings.otpLsposedSystemInjectEnabled,
                enabled = enabled,
                onCheckedChange = onLsposedSystemInjectChange,
            )
        },
        settingsCardScopeItem("lsposed-sms-capture") {
            SettingSwitchRow(
                title = stringResource(R.string.otp_lsposed_sms_title),
                subtitle = stringResource(R.string.otp_lsposed_sms_desc_short),
                checked = settings.otpLsposedSmsCaptureEnabled,
                enabled = enabled,
                onCheckedChange = onLsposedSmsCaptureChange,
            )
        },
    )

    val autoFillTitle = stringResource(R.string.otp_auto_fill_section)
    val lsposedTitle = stringResource(R.string.otp_lsposed_enhancements_section)
    val lsposedDesc = stringResource(R.string.otp_lsposed_enhancements_desc)
    val a11yRequiredText = stringResource(R.string.otp_auto_input_service_setup_desc)
    SettingsLazyScreenScaffold(
        title = stringResource(R.string.otp_auto_input_entry_title),
        subtitle = stringResource(R.string.otp_auto_input_entry_desc),
        onBack = onBack,
    ) {
        if (!accessibilityGranted) {
            settingsLazyTipCard(
                key = "otp_a11y_required",
                text = a11yRequiredText,
            )
        }
        settingsLazySmallTitle(key = "otp_auto_fill_section", title = autoFillTitle)
        groupedCardItems(keyPrefix = "otp-auto-fill", items = items.take(4))

        settingsLazySmallTitle(key = "otp_lsposed_section", title = lsposedTitle)
        settingsLazyTipCard(key = "otp_lsposed_desc", text = lsposedDesc)
        groupedCardItems(keyPrefix = "otp-lsposed", items = items.drop(4))
    }
}
