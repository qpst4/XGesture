package com.slideindex.app.ui

import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.backtap.BackTapMode
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.gesturepicker.gestureActionLabelText
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingRadioRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackTapSettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onOpenActionPick: () -> Unit
) {
    val context = LocalContext.current
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = settingsRepository.readSnapshot()
    )
    val backTap = settings.backTapSettings
    val scope = rememberCoroutineScope()
    val modeSectionTitle = stringResource(R.string.back_tap_mode)
    val modeBatteryHint = stringResource(R.string.back_tap_mode_battery_hint)
    val sensitivityHint = stringResource(R.string.shake_gestures_sensitivity_hint)

    SettingsScreenScaffold(
        title = stringResource(R.string.extension_back_tap_title),
        pageHint = stringResource(R.string.extension_back_tap_subtitle),
        onBack = onBack
    ) {
        groupedCardItems(
            keyPrefix = "back-tap-main",
            items = buildList {
                add(
                    settingsCardScopeItem("enabled") {
                        SettingSwitchRow(
                            title = stringResource(R.string.back_tap_enabled),
                            subtitle = stringResource(R.string.extension_back_tap_subtitle),
                            icon = { label -> SymbolsIcon("TouchApp", contentDescription = label) },
                            checked = backTap.enabled,
                            enabled = true,
                            onCheckedChange = { scope.launch { settingsRepository.setBackTapEnabled(it) } }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("action") {
                        SettingNavigationRow(
                            icon = { label -> SymbolsIcon("TouchApp", contentDescription = label) },
                            title = stringResource(R.string.back_tap_action),
                            subtitle = gestureActionLabelText(context, backTap.action),
                            enabled = backTap.enabled,
                            onClick = onOpenActionPick
                        )
                    }
                )
            }
        )
        groupedCardItems(
            keyPrefix = "back-tap-tuning",
            items = buildList {
                add(
                    settingsCardScopeItem("sensitivity") {
                        SettingsSliderRow(
                            title = stringResource(R.string.back_tap_sensitivity),
                            value = backTap.sensitivity.toFloat(),
                            valueRange = 1f..10f,
                            steps = 8,
                            enabled = backTap.enabled,
                            label = backTap.sensitivity.toString(),
                            startLabel = stringResource(R.string.shake_gestures_sensitivity_hard),
                            endLabel = stringResource(R.string.shake_gestures_sensitivity_easy),
                            onValueChange = { scope.launch { settingsRepository.setBackTapSensitivity(it.toInt()) } }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("range") {
                        val intervalMs = 250 + backTap.range * 60
                        SettingsSliderRow(
                            title = stringResource(R.string.back_tap_range),
                            value = backTap.range.toFloat(),
                            valueRange = 1f..10f,
                            steps = 8,
                            enabled = backTap.enabled,
                            label = "${intervalMs} ms",
                            formatLabel = { "${250 + it.roundToInt() * 60} ms" },
                            onValueChange = { scope.launch { settingsRepository.setBackTapRange(it.toInt()) } }
                        )
                    }
                )
            }
        )
        settingsLazyHint(key = "back-tap-sensitivity-hint", text = sensitivityHint)
        settingsLazySmallTitle(
            key = "section-back-tap-mode",
            title = modeSectionTitle
        )
        groupedCardItems(
            keyPrefix = "back-tap-mode",
            selectableGroup = true,
            items = buildList {
                add(
                    settingsCardScopeItem("mode-always") {
                        SettingRadioRow(
                            title = stringResource(R.string.back_tap_mode_always),
                            selected = backTap.mode == BackTapMode.ALWAYS,
                            enabled = backTap.enabled,
                            onClick = { scope.launch { settingsRepository.setBackTapMode(BackTapMode.ALWAYS) } }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("mode-screen-on") {
                        SettingRadioRow(
                            title = stringResource(R.string.back_tap_mode_screen_on),
                            selected = backTap.mode == BackTapMode.SCREEN_ON,
                            enabled = backTap.enabled,
                            onClick = { scope.launch { settingsRepository.setBackTapMode(BackTapMode.SCREEN_ON) } }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("mode-screen-off") {
                        SettingRadioRow(
                            title = stringResource(R.string.back_tap_mode_screen_off),
                            selected = backTap.mode == BackTapMode.SCREEN_OFF,
                            enabled = backTap.enabled,
                            onClick = { scope.launch { settingsRepository.setBackTapMode(BackTapMode.SCREEN_OFF) } }
                        )
                    }
                )
            }
        )
        settingsLazyHint(
            key = "back-tap-mode-battery-hint",
            text = modeBatteryHint
        )
        groupedCardItems(
            keyPrefix = "back-tap-options",
            items = buildList {
                add(
                    settingsCardScopeItem("vibration-feedback") {
                        SettingSwitchRow(
                            title = stringResource(R.string.back_tap_vibration_feedback),
                            checked = backTap.vibrationFeedbackEnabled,
                            enabled = backTap.enabled,
                            onCheckedChange = { scope.launch { settingsRepository.setBackTapVibrationFeedbackEnabled(it) } }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("pause-charging") {
                        SettingSwitchRow(
                            title = stringResource(R.string.back_tap_pause_charging),
                            checked = backTap.pauseWhileCharging,
                            enabled = backTap.enabled,
                            onCheckedChange = { scope.launch { settingsRepository.setBackTapPauseWhileCharging(it) } }
                        )
                    }
                )
            }
        )
    }
}
