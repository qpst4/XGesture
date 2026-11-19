package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppCarouselListSource
import com.slideindex.app.settings.AppCarouselSwitcherSettings
import com.slideindex.app.settings.TaskSwitcherCloseMode
import com.slideindex.app.ui.SettingSwitchRow
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingSegmentedRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppCarouselSwitcherSettingsScreen(
    settings: AppCarouselSwitcherSettings,
    onBack: () -> Unit,
    onSettingsChange: (AppCarouselSwitcherSettings) -> Unit,
) {
    var local by remember { mutableStateOf(settings) }
    LaunchedEffect(settings) { local = settings }

    fun update(next: AppCarouselSwitcherSettings) {
        local = next
        onSettingsChange(next)
    }

    val settingsDesc = stringResource(R.string.app_carousel_settings_desc)

    SettingsScreenScaffold(
        title = stringResource(R.string.app_carousel_settings_title),
        onBack = onBack,
    ) {
        settingsLazyTipCard(
            key = "app-carousel-desc",
            text = settingsDesc,
        )
        groupedCardItems(
            keyPrefix = "app-carousel-cancel",
            items = appCarouselSwitcherSettingsCardItems(
                keyPrefix = "app-carousel-cancel",
                local = local,
                onUpdate = ::update,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun appCarouselSwitcherSettingsCardItems(
    keyPrefix: String,
    local: AppCarouselSwitcherSettings,
    onUpdate: (AppCarouselSwitcherSettings) -> Unit,
): List<CardItem> {
    val sliderRange = AppCarouselSwitcherSettings.MIN_CANCEL_DISTANCE_DP.toFloat()..
        AppCarouselSwitcherSettings.MAX_CANCEL_DISTANCE_DP.toFloat()
    val sliderSteps = AppCarouselSwitcherSettings.MAX_CANCEL_DISTANCE_DP -
        AppCarouselSwitcherSettings.MIN_CANCEL_DISTANCE_DP - 1
    return listOfNotNull(
        settingsCardScopeItem("$keyPrefix-list-source") {
            SettingSegmentedRow(
                title = stringResource(R.string.app_carousel_list_source_title),
                subtitle = stringResource(
                    when (local.listSource) {
                        AppCarouselListSource.RECENT_APPS ->
                            R.string.app_carousel_list_source_recent_desc
                        AppCarouselListSource.BACKGROUND_TASKS ->
                            R.string.app_carousel_list_source_background_desc
                    },
                ),
                items = AppCarouselListSource.entries,
                selected = local.listSource,
                labelRes = { source ->
                    when (source) {
                        AppCarouselListSource.RECENT_APPS -> R.string.app_carousel_list_source_recent
                        AppCarouselListSource.BACKGROUND_TASKS ->
                            R.string.app_carousel_list_source_background
                    }
                },
                onSelect = { source -> onUpdate(local.copy(listSource = source)) },
            )
        },
        // 「关闭」胶囊只在后台任务模式下可配：最近任务模式的卡片可能没有真实任务，
        // 划掉无从谈起，因此固定为仅强行停止，不暴露三选一。
        if (local.listSource == AppCarouselListSource.BACKGROUND_TASKS) {
            settingsCardScopeItem("$keyPrefix-close-mode") {
                val closeModes = listOf(
                    TaskSwitcherCloseMode.DISMISS_RECENTS,
                    TaskSwitcherCloseMode.FORCE_STOP,
                    TaskSwitcherCloseMode.BOTH,
                )
                SettingDropdownRow(
                    title = stringResource(R.string.app_carousel_close_mode_title),
                    subtitle = stringResource(R.string.app_carousel_close_mode_desc),
                    items = listOf(
                        stringResource(R.string.task_switcher_close_mode_dismiss_recents),
                        stringResource(R.string.task_switcher_close_mode_force_stop),
                        stringResource(R.string.task_switcher_close_mode_both),
                    ),
                    selectedIndex = closeModes.indexOf(local.closeMode).coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        closeModes.getOrNull(index)?.let { mode ->
                            onUpdate(local.copy(closeMode = mode))
                        }
                    },
                )
            }
        } else {
            null
        },
    ) + listOf(
        settingsCardScopeItem("$keyPrefix-up") {
            SettingsSliderRow(
                title = stringResource(R.string.app_carousel_cancel_distance_up),
                value = local.cancelDistanceUpDp.toFloat(),
                valueRange = sliderRange,
                steps = sliderSteps,
                enabled = true,
                label = stringResource(
                    R.string.corner_gesture_zone_dp_value,
                    local.cancelDistanceUpDp,
                ),
                onValueChange = {
                    onUpdate(local.copy(cancelDistanceUpDp = it.roundToInt()))
                },
            )
        },
        settingsCardScopeItem("$keyPrefix-slide-sensitivity") {
            SettingsSliderRow(
                title = stringResource(R.string.app_carousel_slide_sensitivity),
                value = local.slideSensitivity.toFloat(),
                valueRange = AppCarouselSwitcherSettings.MIN_SLIDE_SENSITIVITY.toFloat()..
                    AppCarouselSwitcherSettings.MAX_SLIDE_SENSITIVITY.toFloat(),
                steps = AppCarouselSwitcherSettings.MAX_SLIDE_SENSITIVITY -
                    AppCarouselSwitcherSettings.MIN_SLIDE_SENSITIVITY - 1,
                enabled = true,
                label = local.slideSensitivity.toString(),
                onValueChange = {
                    onUpdate(local.copy(slideSensitivity = it.roundToInt()))
                },
            )
        },
        settingsCardScopeItem("$keyPrefix-slide-reversed") {
            SettingSwitchRow(
                title = stringResource(R.string.app_carousel_slide_reversed),
                subtitle = stringResource(R.string.app_carousel_slide_reversed_desc),
                checked = local.slideReversed,
                enabled = true,
                onCheckedChange = { onUpdate(local.copy(slideReversed = it)) },
            )
        },
        settingsCardScopeItem("$keyPrefix-down") {
            SettingsSliderRow(
                title = stringResource(R.string.app_carousel_cancel_distance_down),
                value = local.cancelDistanceDownDp.toFloat(),
                valueRange = sliderRange,
                steps = sliderSteps,
                enabled = true,
                label = stringResource(
                    R.string.corner_gesture_zone_dp_value,
                    local.cancelDistanceDownDp,
                ),
                onValueChange = {
                    onUpdate(local.copy(cancelDistanceDownDp = it.roundToInt()))
                },
            )
        },
    )
}
