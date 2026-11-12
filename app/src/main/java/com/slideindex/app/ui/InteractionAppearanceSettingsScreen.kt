package com.slideindex.app.ui

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import com.slideindex.app.ui.miuix.MiuixTabRowWithContourInCard
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import com.slideindex.app.ui.settings.components.MiuixNavigationRow
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.HomeMainSettings
import com.slideindex.app.settings.KeyboardTriggerBehavior
import com.slideindex.app.settings.TopAppBarBlurStyle
import com.slideindex.app.ui.HomeLeadingIcons
import com.slideindex.app.ui.miuix.MiuixBackNavigationIcon
import com.slideindex.app.ui.miuix.MiuixListScaffold
import com.slideindex.app.ui.miuix.MiuixListSettingsCard
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.appLanguageSettingsCardItems
import com.slideindex.app.ui.miuix.themeAppearanceSettingsCardItems
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_100
import com.slideindex.app.ui.settings.components.SettingExpandableDropdownRow
import com.slideindex.app.ui.settings.components.SettingExpandableSwitchRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsCardScopeContent
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.util.HapticHelper
import kotlin.math.roundToInt

@Composable
fun InteractionAppearanceSettingsScreen(
    settings: HomeMainSettings,
    gestureActive: Boolean,
    keyboardTriggerBehavior: KeyboardTriggerBehavior,
    keyboardTriggerNarrowPercent: Int,
    onKeyboardTriggerBehaviorChange: (KeyboardTriggerBehavior) -> Unit,
    onKeyboardTriggerNarrowPercentChange: (Int) -> Unit,
    onBack: () -> Unit,
    onIconPlateModeChange: (com.slideindex.app.settings.IconPlateMode) -> Unit = {},
    iconPlateMode: com.slideindex.app.settings.IconPlateMode = com.slideindex.app.settings.IconPlateMode.SYSTEM_DARK,
    onOpenHapticSettings: () -> Unit,
    onSwipeDismissEnabledChange: (Boolean) -> Unit,
    onPredictiveBackEnabledChange: (Boolean) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onThemeColorChange: (Int) -> Unit,
    onThemePaletteStyleChange: (com.slideindex.app.settings.ThemePaletteStyle) -> Unit,
    onThemeModeChange: (com.slideindex.app.settings.AppThemeMode) -> Unit,
    onCustomColorChange: (Boolean) -> Unit,
    onDarkBackgroundStyleChange: (com.slideindex.app.settings.DarkBackgroundStyle) -> Unit,
    onThemeColorSpecChange: (com.slideindex.app.settings.AppColorSpec) -> Unit,
    onBottomNavStyleChange: (com.slideindex.app.settings.BottomNavStyle) -> Unit,
    onBottomNavModeChange: (com.slideindex.app.settings.BottomNavMode) -> Unit,
    onBottomNavGlassEnabledChange: (Boolean) -> Unit,
    onBottomNavBlurRadiusChange: (Float) -> Unit,
    onTopAppBarBlurStyleChange: (TopAppBarBlurStyle) -> Unit,
    onUiDensityScaleChange: (Float) -> Unit,
    onAppUiLanguageChange: (com.slideindex.app.settings.AppUiLanguage) -> Unit,
    onBottomNavBlurPreviewChange: (Float) -> Unit = {},
    onBottomNavBlurPreviewStop: () -> Unit = {},
    overlayBlurEnabled: Boolean = true,
    onOverlayBlurEnabledChange: (Boolean) -> Unit = {},
) {

    // LazyListScope 里不能调用 stringResource，先在这里取好
    val iconPlateSectionTitle = stringResource(R.string.icon_plate_mode_section)
    val iconPlateModeDescText = stringResource(R.string.icon_plate_mode_desc)
    val iconPlateTabLabels = listOf(
        stringResource(R.string.icon_plate_mode_dark),
        stringResource(R.string.icon_plate_mode_light),
        stringResource(R.string.icon_plate_mode_system),
    )


    val themeAppearanceItems = themeAppearanceSettingsCardItems(
        outlinedPreferenceIcons = true,
        themeModeId = settings.themeModeId,
        customColorEnabled = settings.customColorEnabled,
        darkBackgroundStyleId = settings.darkBackgroundStyleId,
        dynamicColorEnabled = settings.dynamicColorEnabled,
        themeColorArgb = settings.themeColorArgb,
        paletteStyleId = settings.themePaletteStyleId,
        themeColorSpecId = settings.themeColorSpecId,
        bottomNavStyleId = settings.bottomNavStyleId,
        bottomNavModeId = settings.bottomNavModeId,
        bottomNavGlassEnabled = settings.bottomNavGlassEnabled,
        bottomNavBlurRadiusDp = settings.bottomNavBlurRadiusDp,
        topAppBarBlurStyleId = settings.topAppBarBlurStyleId,
        uiDensityScale = settings.uiDensityScale,
        onThemeModeChange = onThemeModeChange,
        onCustomColorChange = onCustomColorChange,
        onDarkBackgroundStyleChange = onDarkBackgroundStyleChange,
        onDynamicColorChange = onDynamicColorChange,
        onThemeColorChange = onThemeColorChange,
        onPaletteStyleChange = onThemePaletteStyleChange,
        onThemeColorSpecChange = onThemeColorSpecChange,
        onBottomNavStyleChange = onBottomNavStyleChange,
        onBottomNavModeChange = onBottomNavModeChange,
        onBottomNavGlassEnabledChange = onBottomNavGlassEnabledChange,
        onBottomNavBlurRadiusChange = onBottomNavBlurRadiusChange,
        onTopAppBarBlurStyleChange = onTopAppBarBlurStyleChange,
        onUiDensityScaleChange = onUiDensityScaleChange,
        onBottomNavBlurPreviewChange = onBottomNavBlurPreviewChange,
        onBottomNavBlurPreviewStop = onBottomNavBlurPreviewStop,
    )

    val languageItems = appLanguageSettingsCardItems(
        appUiLanguageTag = settings.appUiLanguageTag,
        onAppUiLanguageChange = onAppUiLanguageChange,
    )
    val keyboardBehaviorEntries = KeyboardTriggerBehavior.selectable
    val keyboardBehaviorIndex = keyboardBehaviorEntries.indexOf(keyboardTriggerBehavior).coerceAtLeast(0)

    MiuixListScaffold(
        title = stringResource(R.string.interaction_appearance_settings_title),
        pageHint = stringResource(R.string.interaction_appearance_settings_entry_desc),
        showPageHintCard = true,
        navigationIcon = { MiuixBackNavigationIcon(onBack) },
    ) {
        item(key = "theme_section") {
            SmallTitle(
                text = stringResource(R.string.settings_section_theme_appearance),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        groupedCardItems(
            keyPrefix = "interaction_appearance_general",
            items = languageItems,
        )
        groupedCardItems(
            keyPrefix = "interaction_appearance_theme",
            items = themeAppearanceItems,
        )

        item(key = "overlay_visual_section") {
            SmallTitle(
                text = stringResource(R.string.settings_section_overlay_visual),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MiuixListSettingsCard(
            keyPrefix = "interaction-overlay-visual",
            items = listOf(
                settingsCardScopeItem("overlay-blur") {
                    SettingSwitchRow(
                        title = stringResource(R.string.overlay_blur_enabled),
                        subtitle = stringResource(R.string.overlay_blur_enabled_desc),
                        checked = overlayBlurEnabled,
                        enabled = true,
                        onCheckedChange = onOverlayBlurEnabledChange,
                    )
                },
                settingsCardScopeItem("icon-plate") {
                    OverlayDropdownPreference(
                        title = iconPlateSectionTitle,
                        items = iconPlateTabLabels,
                        selectedIndex = com.slideindex.app.settings.IconPlateMode
                            .entries
                            .indexOf(iconPlateMode)
                            .coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            onIconPlateModeChange(
                                com.slideindex.app.settings.IconPlateMode.entries[index],
                            )
                        },
                    )
                },
            ),
        )

        item(key = "interaction_feedback_section") {
            SmallTitle(
                text = stringResource(R.string.settings_section_interaction_feedback),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MiuixListSettingsCard(
            keyPrefix = "interaction-feedback-motion",
            items = buildList {
                add(
                    settingsCardScopeItem("haptic") {
                        SettingNavigationRow(
                            icon = { label -> Icon(HomeLeadingIcons.haptic(true), contentDescription = label) },
                            title = stringResource(R.string.haptic_settings_title),
                            subtitle = stringResource(R.string.haptic_settings_desc),
                            onClick = onOpenHapticSettings,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("swipe-dismiss") {
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_swipe_dismiss),
                            subtitle = stringResource(R.string.settings_swipe_dismiss_summary),
                            checked = settings.swipeDismissEnabled,
                            enabled = true,
                            onCheckedChange = onSwipeDismissEnabledChange,
                        )
                    },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    add(
                        settingsCardScopeItem("predictive-back") {
                            SettingSwitchRow(
                                title = stringResource(R.string.settings_predictive_back),
                                subtitle = stringResource(R.string.settings_predictive_back_summary),
                                checked = settings.predictiveBackEnabled,
                                enabled = true,
                                onCheckedChange = onPredictiveBackEnabledChange,
                            )
                        },
                    )
                }
            },
        )

    }
}

@Composable
fun InteractionAppearanceEntryCard(
    onClick: () -> Unit,
) {
    SettingsCardScopeContent {
        SettingNavigationRow(
            icon = { label ->
                androidx.compose.material3.Icon(
                    HomeLeadingIcons.themeMode(outlined = true),
                    contentDescription = label,
                )
            },
            title = stringResource(R.string.interaction_appearance_settings_title),
            subtitle = stringResource(R.string.interaction_appearance_settings_entry_desc),
            onClick = onClick,
        )
    }
}
