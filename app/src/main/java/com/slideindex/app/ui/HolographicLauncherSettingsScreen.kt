package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.overlay.SystemWallpaperBlurHelper
import com.slideindex.app.overlay.WallpaperPermissionLauncher
import com.slideindex.app.settings.HolographicAppMode
import com.slideindex.app.settings.HolographicLauncherSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HolographicLauncherSettingsScreen(
    settings: HolographicLauncherSettings,
    onBack: () -> Unit,
    onTimeoutSecondsChange: (Int) -> Unit,
    onRotationSensitivityChange: (Float) -> Unit,
    onHapticLevelChange: (Int) -> Unit,
    onBackgroundStyleChange: (Int) -> Unit,
    onBlurDpChange: (Int) -> Unit,
    onDimPercentChange: (Int) -> Unit,
    onAppModeChange: (HolographicAppMode) -> Unit,
    onOpenApps: () -> Unit
) {
    val context = LocalContext.current
    var localSettings by remember { mutableStateOf(settings) }
    LaunchedEffect(settings) { localSettings = settings }
    fun ensureWallpaperPermission() {
        SystemWallpaperBlurHelper.requestWallpaperPermission(context)
    }

    val interactionSectionTitle = stringResource(R.string.holographic_settings_section_interaction)
    val backgroundSectionTitle = stringResource(R.string.holographic_settings_section_background)
    val appsSectionTitle = stringResource(R.string.holographic_settings_section_apps)
    val sensitivityRange = HolographicLauncherSettings.MIN_ROTATION_SENSITIVITY..
        HolographicLauncherSettings.MAX_ROTATION_SENSITIVITY
    val sensitivitySteps = 49
    val hapticLabels = listOf(
        stringResource(R.string.holographic_settings_haptic_off),
        stringResource(R.string.holographic_settings_haptic_light),
        stringResource(R.string.holographic_settings_haptic_medium),
        stringResource(R.string.holographic_settings_haptic_strong)
    )
    val backgroundStyles = listOf(
        HolographicLauncherSettings.BACKGROUND_BLUR,
        HolographicLauncherSettings.BACKGROUND_WALLPAPER_BLUR,
        HolographicLauncherSettings.BACKGROUND_BLACK
    )
    val blurEnabled = localSettings.backgroundStyle != HolographicLauncherSettings.BACKGROUND_BLACK

    SettingsScreenScaffold(
        title = stringResource(R.string.holographic_launcher_entry_title),
        pageHint = stringResource(R.string.holographic_launcher_entry_desc),
        onBack = onBack
    ) {
        settingsLazySmallTitle(
            key = "section-interaction",
            title = interactionSectionTitle
        )
        groupedCardItems(
            keyPrefix = "holographic-interaction",
            items = buildList {
                add(
                    settingsCardScopeItem("timeout") {
                        SettingsSliderRow(
                            title = stringResource(R.string.holographic_settings_timeout),
                            value = localSettings.timeoutSeconds.toFloat(),
                            valueRange = HolographicLauncherSettings.MIN_TIMEOUT_SECONDS.toFloat()..
                                HolographicLauncherSettings.MAX_TIMEOUT_SECONDS.toFloat(),
                            steps = 59,
                            enabled = true,
                            label = stringResource(
                                R.string.holographic_settings_timeout_value,
                                localSettings.timeoutSeconds
                            ),
                            onValueChange = { value ->
                                val next = value.roundToInt()
                                localSettings = localSettings.copy(timeoutSeconds = next)
                                onTimeoutSecondsChange(next)
                            }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("sensitivity") {
                        SettingsSliderRow(
                            title = stringResource(R.string.holographic_settings_sensitivity),
                            value = localSettings.rotationSensitivity,
                            valueRange = sensitivityRange,
                            steps = sensitivitySteps,
                            enabled = true,
                            label = stringResource(
                                R.string.holographic_settings_sensitivity_value,
                                (localSettings.rotationSensitivity * 1000).roundToInt()
                            ),
                            onValueChange = { value ->
                                localSettings = localSettings.copy(rotationSensitivity = value)
                                onRotationSensitivityChange(value)
                            }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("haptic") {
                        SettingDropdownRow(
                            title = stringResource(R.string.holographic_settings_haptic),
                            items = hapticLabels,
                            selectedIndex = localSettings.hapticLevel.coerceIn(0, hapticLabels.lastIndex),
                            onSelectedIndexChange = { index ->
                                localSettings = localSettings.copy(hapticLevel = index)
                                onHapticLevelChange(index)
                            }
                        )
                    }
                )
            }
        )
        settingsLazySmallTitle(
            key = "section-background",
            title = backgroundSectionTitle
        )
        groupedCardItems(
            keyPrefix = "holographic-background",
            items = buildList {
                add(
                    settingsCardScopeItem("background-style") {
                        SettingDropdownRow(
                            title = stringResource(R.string.holographic_settings_background_style),
                            items = listOf(
                                stringResource(R.string.honeycomb_background_blur),
                                stringResource(R.string.honeycomb_background_wallpaper_blur),
                                stringResource(R.string.honeycomb_background_black)
                            ),
                            selectedIndex = backgroundStyles.indexOf(localSettings.backgroundStyle).coerceAtLeast(0),
                            onSelectedIndexChange = { index ->
                                val style = backgroundStyles[index]
                                localSettings = localSettings.copy(backgroundStyle = style)
                                onBackgroundStyleChange(style)
                                if (style == HolographicLauncherSettings.BACKGROUND_WALLPAPER_BLUR &&
                                    !SystemWallpaperBlurHelper.hasWallpaperAccessPermission(context)
                                ) {
                                    ensureWallpaperPermission()
                                }
                            }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("blur-strength") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_blur_strength),
                            value = localSettings.blurDp.toFloat(),
                            valueRange = HolographicLauncherSettings.MIN_BLUR_DP.toFloat()..
                                HolographicLauncherSettings.MAX_BLUR_DP.toFloat(),
                            steps = 16,
                            enabled = blurEnabled,
                            label = stringResource(R.string.corner_gesture_zone_dp_value, localSettings.blurDp),
                            onValueChange = { value ->
                                val next = value.roundToInt()
                                localSettings = localSettings.copy(blurDp = next)
                                onBlurDpChange(next)
                            }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("dim-percent") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_dim_percent),
                            value = localSettings.dimPercent.toFloat(),
                            valueRange = HolographicLauncherSettings.MIN_DIM_PERCENT.toFloat()..
                                HolographicLauncherSettings.MAX_DIM_PERCENT.toFloat(),
                            steps = 12,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_percent_value,
                                localSettings.dimPercent
                            ),
                            onValueChange = { value ->
                                val next = value.roundToInt()
                                localSettings = localSettings.copy(dimPercent = next)
                                onDimPercentChange(next)
                            }
                        )
                    }
                )
            }
        )
        settingsLazySmallTitle(
            key = "section-apps",
            title = appsSectionTitle
        )
        // 模式单选与应用列表放在同一个页面（「球面应用」页，版式照 Mishka 的「分应用代理」）：
        // 只有一个选中集合，「模式」决定它是白名单还是黑名单，因此不存在两个列表打架的情况。
        // 旧的「隐藏应用」入口已删除 —— 它和「排除选中应用」是同一件事，
        // 老数据由 `migrateHolographicHiddenAppsOnce` 一次性并入选中列表。
        groupedCardItems(
            keyPrefix = "holographic-apps",
            items = listOf(
                settingsCardScopeItem("sphere-apps") {
                    val selectedCount = localSettings.selectedAppPackages.size
                    SettingNavigationRow(
                        icon = { label ->
                            Icon(
                                HubLeadingIcons.holographicLauncher(outlined = true),
                                contentDescription = label,
                            )
                        },
                        title = stringResource(R.string.holographic_apps_entry_title),
                        subtitle = "${stringResource(localSettings.appMode.titleRes())} · " +
                            if (selectedCount > 0) {
                                pluralStringResource(
                                    R.plurals.holographic_apps_entry_count,
                                    selectedCount,
                                    selectedCount,
                                )
                            } else {
                                stringResource(R.string.holographic_apps_entry_pick_desc)
                            },
                        onClick = onOpenApps
                    )
                }
            )
        )
    }
}

@Composable
fun SettingsCardScope.HolographicLauncherEntryCard(
    selectedAppCount: Int,
    appMode: HolographicAppMode,
    enabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit
) {
    val subtitle = when {
        selectedAppCount > 0 && appMode != HolographicAppMode.ALL -> pluralStringResource(
            R.plurals.holographic_apps_entry_count, selectedAppCount, selectedAppCount
        )
        else -> stringResource(R.string.holographic_launcher_entry_desc)
    }
    SettingNavigationRow(
        icon = { label ->
            Icon(HubLeadingIcons.holographicLauncher(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.holographic_launcher_entry_title),
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick
    )
}
