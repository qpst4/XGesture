package com.slideindex.app.pagescroll

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_01
import com.slideindex.app.ui.settings.components.SettingRadioRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

/**
 * 「页面滚动」设置：滚动方式、默认速度、控制条配色与闲置不透明度。
 *
 * 速度在控制条上也能实时改，这里改的是"下次触发时用哪个档"。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PageScrollSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(PageScrollPrefs.read(context)) }
    // 分节标题要先在组合期取好：settingsLazySmallTitle 的接收者是 LazyListScope（非组合作用域）。
    val behaviorSectionTitle = stringResource(R.string.page_scroll_prefs_behavior)
    val appearanceSectionTitle = stringResource(R.string.page_scroll_prefs_appearance)

    SettingsScreenScaffold(
        title = stringResource(R.string.page_scroll_prefs_title),
        pageHint = null,
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "page-scroll-behavior-section",
            title = behaviorSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "page-scroll-behavior",
            items = buildList {
                add(
                    settingsCardScopeItem("mode-auto") {
                        SettingRadioRow(
                            title = stringResource(R.string.page_scroll_mode_auto),
                            subtitle = stringResource(R.string.page_scroll_mode_auto_description),
                            selected = settings.mode == PageScrollMode.AUTO,
                            onClick = {
                                settings = settings.copy(mode = PageScrollMode.AUTO)
                                PageScrollPrefs.saveMode(context, PageScrollMode.AUTO)
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("mode-hold") {
                        SettingRadioRow(
                            title = stringResource(R.string.page_scroll_mode_hold_drag),
                            subtitle = stringResource(R.string.page_scroll_mode_hold_drag_description),
                            selected = settings.mode == PageScrollMode.HOLD_AND_DRAG,
                            onClick = {
                                settings = settings.copy(mode = PageScrollMode.HOLD_AND_DRAG)
                                PageScrollPrefs.saveMode(context, PageScrollMode.HOLD_AND_DRAG)
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("speed") {
                        SettingsSliderRow(
                            title = stringResource(R.string.page_scroll_speed_title),
                            value = settings.speedLevel.toFloat(),
                            valueRange = PageScrollSettings.MIN_SPEED_LEVEL.toFloat()..
                                PageScrollSettings.MAX_SPEED_LEVEL.toFloat(),
                            steps = PageScrollSettings.MAX_SPEED_LEVEL - PageScrollSettings.MIN_SPEED_LEVEL - 1,
                            enabled = true,
                            label = stringResource(
                                R.string.page_scroll_speed_value,
                                settings.speedLevel,
                                PageScrollSettings.MAX_SPEED_LEVEL,
                            ),
                            onValueChange = { value ->
                                val level = value.roundToInt().coerceIn(
                                    PageScrollSettings.MIN_SPEED_LEVEL,
                                    PageScrollSettings.MAX_SPEED_LEVEL,
                                )
                                if (level != settings.speedLevel) {
                                    settings = settings.copy(speedLevel = level)
                                    PageScrollPrefs.saveSpeedLevel(context, level)
                                }
                            },
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "page-scroll-appearance-section",
            title = appearanceSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "page-scroll-appearance",
            items = buildList {
                add(
                    settingsCardScopeItem("use-accent") {
                        SettingSwitchRow(
                            title = stringResource(R.string.page_scroll_bar_use_accent_title),
                            subtitle = stringResource(R.string.page_scroll_bar_use_accent_subtitle),
                            checked = settings.controlBarUseAccentColor,
                            enabled = true,
                            onCheckedChange = { enabled ->
                                settings = settings.copy(controlBarUseAccentColor = enabled)
                                PageScrollPrefs.saveControlBarUseAccentColor(context, enabled)
                            },
                        )
                    },
                )
                for (preset in BAR_COLOR_PRESETS) {
                    add(
                        settingsCardScopeItem("bar-color-${preset.argb}") {
                            SettingRadioRow(
                                title = stringResource(preset.titleRes),
                                selected = settings.controlBarColorArgb == preset.argb,
                                enabled = !settings.controlBarUseAccentColor,
                                onClick = {
                                    settings = settings.copy(controlBarColorArgb = preset.argb)
                                    PageScrollPrefs.saveControlBarColor(context, preset.argb)
                                },
                            )
                        },
                    )
                }
                add(
                    settingsCardScopeItem("bar-idle-alpha") {
                        SettingsSliderRow(
                            title = stringResource(R.string.page_scroll_bar_idle_alpha_title),
                            summary = stringResource(R.string.page_scroll_bar_idle_alpha_summary),
                            value = settings.controlBarIdleAlpha,
                            valueRange = PageScrollSettings.MIN_CONTROL_BAR_IDLE_ALPHA..
                                PageScrollSettings.MAX_CONTROL_BAR_IDLE_ALPHA,
                            enabled = true,
                            label = idleAlphaPercentLabel(settings.controlBarIdleAlpha),
                            formatLabel = ::idleAlphaPercentLabel,
                            keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_01,
                            onValueChange = { value ->
                                val alpha = value.coerceIn(
                                    PageScrollSettings.MIN_CONTROL_BAR_IDLE_ALPHA,
                                    PageScrollSettings.MAX_CONTROL_BAR_IDLE_ALPHA,
                                )
                                if (alpha != settings.controlBarIdleAlpha) {
                                    settings = settings.copy(controlBarIdleAlpha = alpha)
                                    PageScrollPrefs.saveControlBarIdleAlpha(context, alpha)
                                }
                            },
                        )
                    },
                )
            },
        )
    }
}

/** 闲置不透明度的显示值：0~100%，按整数百分比取整（与滑块吸附粒度一致）。 */
private fun idleAlphaPercentLabel(alpha: Float): String =
    "${(alpha.coerceIn(0f, 1f) * 100f).roundToInt()}%"

/** 控制条底色的几个预设（跟随强调色打开时这几项不可选）。 */
private val BAR_COLOR_PRESETS = listOf(
    BarColorPreset(R.string.page_scroll_bar_color_dark, PageScrollSettings.DEFAULT_BAR_COLOR_ARGB),
    BarColorPreset(R.string.page_scroll_bar_color_black, 0xFF000000.toInt()),
    BarColorPreset(R.string.page_scroll_bar_color_white, 0xFFFFFFFF.toInt()),
)

private data class BarColorPreset(
    val titleRes: Int,
    val argb: Int,
)
