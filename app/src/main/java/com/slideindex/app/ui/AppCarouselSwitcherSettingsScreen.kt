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
import com.slideindex.app.settings.AppCarouselSwitcherSettings
import com.slideindex.app.ui.SettingSwitchRow
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
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
    return listOf(
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
