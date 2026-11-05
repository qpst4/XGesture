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
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

/**
 * 「页面滚动」设置：顶部说明卡（触发方式与两种结果）、默认速度、控制条闲置不透明度。
 *
 * 触发方式不可选——它由该手势槽位解析出来的触发模式决定（「持续」走逐页拖动，其它走控制条），
 * 所以这里只说明、不提供开关。速度在控制条上也能实时改，这里改的是"下次触发时用哪个档"。
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
        pageHint = stringResource(R.string.page_scroll_behavior_hint),
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
